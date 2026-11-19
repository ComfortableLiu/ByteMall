package com.bytemall.orderservice.orders;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemall.orderservice.client.ProductSkuClient;
import com.bytemall.orderservice.client.StockReservationClient;
import com.bytemall.orderservice.client.UserAddressClient;
import com.bytemall.orderservice.entity.OrderIdempotency;
import com.bytemall.orderservice.entity.OrderItem;
import com.bytemall.orderservice.entity.OrderMain;
import com.bytemall.orderservice.entity.OrderStatusHistory;
import com.bytemall.orderservice.mapper.OrderIdempotencyMapper;
import com.bytemall.orderservice.mapper.OrderItemMapper;
import com.bytemall.orderservice.mapper.OrderMainMapper;
import com.bytemall.orderservice.mapper.OrderStatusHistoryMapper;
import com.bytemall.product.proto.ProductSkuInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int IDEMPOTENCY_RETENTION_HOURS = 24;
    private static final int PAYMENT_WINDOW_MINUTES = 30;

    private final OrderMainMapper orderMainMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;
    private final OrderIdempotencyMapper orderIdempotencyMapper;
    private final UserAddressClient userAddressClient;
    private final ProductSkuClient productSkuClient;
    private final StockReservationClient stockReservationClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 重新定价、整体预扣库存并创建待支付订单。
     * 外部RPC在数据库事务之外完成，事务只覆盖订单、明细、历史和幂等记录的落库。
     */
    public OrderResponses.Order create(Long userId, String idempotencyKey, OrderRequests.CreateOrder request) {
        // 幂等指纹只依赖请求体；同键同指纹走重放，同键不同指纹按冲突拒绝。
        String requestHash = fingerprint(request);
        OrderIdempotency previous = orderIdempotencyMapper.selectOne(new QueryWrapper<OrderIdempotency>()
                .eq("user_id", userId)
                .eq("idempotency_key", idempotencyKey));
        if (previous != null) {
            return replayPrevious(previous, requestHash);
        }

        // 同一SKU多行合并数量；地址必须属于当前买家，快照落库后不再受地址簿变化影响。
        Map<Long, Integer> mergedItems = mergeItems(request.items());
        OrderResponses.AddressSnapshot address = userAddressClient.getAddress(request.addressId(), userId);

        // 价格一律取商品服务现价（重新定价），不接受客户端传入的价格或状态。
        List<String> skuIds = mergedItems.keySet().stream().map(String::valueOf).toList();
        Map<String, ProductSkuInfo> skuInfos = productSkuClient.getSkuBatch(skuIds);
        List<OrderLine> lines = assembleLines(mergedItems, skuInfos);

        Instant now = Instant.now();
        String orderNo = generateOrderNo();
        // 待支付截止与预扣过期一致，超时后由库存侧定时任务自动释放。
        Instant expireAt = now.plus(PAYMENT_WINDOW_MINUTES, ChronoUnit.MINUTES);
        String reservationNo = "RSV-" + orderNo;

        // 先整体预扣库存再落库；预扣失败直接终止，不产生订单记录。
        stockReservationClient.reserve(reservationNo, orderNo, expireAt, lines.stream()
                .map(line -> new StockReservationClient.Item(line.skuId(), line.quantity()))
                .toList());
        try {
            // 事务只覆盖订单/明细/历史/幂等落库，外部RPC都在事务之外完成。
            return transactionTemplate.execute(status -> persistOrder(
                    userId, idempotencyKey, requestHash, request, address, lines, orderNo, expireAt, now));
        } catch (DuplicateKeyException exception) {
            // 并发同键请求已抢先落库：回补自己的预扣后返回对方订单或幂等冲突。
            stockReservationClient.releaseQuietly(reservationNo, "ORDER_CREATE_FAILED");
            return replayConcurrent(userId, idempotencyKey, requestHash);
        } catch (RuntimeException exception) {
            // 落库失败必须补偿回补预扣，否则库存会被无主占用直到超时释放。
            stockReservationClient.releaseQuietly(reservationNo, "ORDER_CREATE_FAILED");
            throw exception;
        }
    }

    /** 同一事务写入订单、明细、创建历史和幂等记录，并把首次响应快照写回幂等记录。 */
    private OrderResponses.Order persistOrder(
            Long userId,
            String idempotencyKey,
            String requestHash,
            OrderRequests.CreateOrder request,
            OrderResponses.AddressSnapshot address,
            List<OrderLine> lines,
            String orderNo,
            Instant expireAt,
            Instant now
    ) {
        // 金额全部服务端计算：商品金额=Σ单价×数量，当前运费和优惠固定为0。
        long itemAmount = 0L;
        for (OrderLine line : lines) {
            itemAmount = Math.addExact(itemAmount, Math.multiplyExact(line.unitPrice(), (long) line.quantity()));
        }
        long freightAmount = 0L;
        long discountAmount = 0L;
        long payableAmount = itemAmount + freightAmount - discountAmount;

        OrderMain orderMain = new OrderMain();
        orderMain.setOrderNo(orderNo);
        orderMain.setUserId(userId);
        orderMain.setMerchantId(lines.get(0).merchantId());
        orderMain.setStatus(OrderStatus.PENDING_PAYMENT);
        orderMain.setItemAmount(itemAmount);
        orderMain.setFreightAmount(freightAmount);
        orderMain.setDiscountAmount(discountAmount);
        orderMain.setPayableAmount(payableAmount);
        orderMain.setAddressSnapshot(toJson(address));
        orderMain.setRemark(request.remark());
        orderMain.setExpireAt(LocalDateTime.ofInstant(expireAt, ZoneOffset.UTC));
        orderMain.setVersion(0L);
        orderMain.setCreatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        orderMain.setUpdatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        orderMainMapper.insert(orderMain);

        LocalDateTime createdAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        for (OrderLine line : lines) {
            OrderItem item = new OrderItem();
            item.setOrderId(orderMain.getId());
            item.setOrderNo(orderNo);
            item.setSpuId(line.spuId());
            item.setSkuId(line.skuId());
            item.setMerchantId(line.merchantId());
            item.setProductTitle(line.productTitle());
            item.setProductImage(line.productImage());
            item.setSpecSnapshot(line.specJson());
            item.setUnitPrice(line.unitPrice());
            item.setQuantity(line.quantity());
            item.setTotalPrice(Math.multiplyExact(line.unitPrice(), (long) line.quantity()));
            item.setCreatedAt(createdAt);
            orderItemMapper.insert(item);
        }

        // 创建历史记录“无来源状态 → 待支付”，与前端mock的事件语义保持一致。
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orderMain.getId());
        history.setFromStatus("");
        history.setToStatus(OrderStatus.PENDING_PAYMENT.name());
        history.setOperatorType(OrderResponses.OperatorType.BUYER.name());
        history.setOperatorId(userId);
        history.setReason("创建订单并完成库存预扣");
        history.setCreatedAt(createdAt);
        orderStatusHistoryMapper.insert(history);

        // 幂等记录最后插入：(user_id, idempotency_key)唯一键是并发同键请求的最终屏障，冲突则整单回滚。
        OrderIdempotency idempotency = new OrderIdempotency();
        idempotency.setUserId(userId);
        idempotency.setIdempotencyKey(idempotencyKey);
        idempotency.setRequestHash(requestHash);
        idempotency.setResourceId(orderNo);
        idempotency.setResponseStatus(HttpStatus.CREATED.value());
        idempotency.setCreatedAt(createdAt);
        idempotency.setExpiresAt(createdAt.plusHours(IDEMPOTENCY_RETENTION_HOURS));
        orderIdempotencyMapper.insert(idempotency);

        List<OrderResponses.OrderItem> responseItems = lines.stream().map(this::toResponseItem).toList();
        OrderResponses.OrderEvent createdEvent = new OrderResponses.OrderEvent(
                null,
                OrderStatus.PENDING_PAYMENT,
                OrderResponses.OperatorType.BUYER,
                "创建订单并完成库存预扣",
                now
        );
        // 首次响应快照写回幂等记录，供审计与排障；重放以数据库当前订单状态为准。
        idempotency.setResponseSnapshot(toJson(buildOrder(orderMain, responseItems, List.of(createdEvent))));
        orderIdempotencyMapper.updateById(idempotency);

        return buildOrder(orderMain, responseItems, List.of(createdEvent));
    }

    /** 幂等键重复但指纹一致时返回已有订单；指纹不一致说明同键复用于不同请求。 */
    private OrderResponses.Order replayPrevious(OrderIdempotency previous, String requestHash) {
        if (!requestHash.equals(previous.getRequestHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相同幂等键不能用于不同的订单内容");
        }
        OrderResponses.Order existing = loadOrder(previous.getResourceId());
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "幂等记录对应的订单不存在");
        }
        return existing;
    }

    /** 并发同键提交后读取抢先方的幂等记录；读到按重放处理，读不到按处理中冲突返回。 */
    private OrderResponses.Order replayConcurrent(Long userId, String idempotencyKey, String requestHash) {
        OrderIdempotency winner = orderIdempotencyMapper.selectOne(new QueryWrapper<OrderIdempotency>()
                .eq("user_id", userId)
                .eq("idempotency_key", idempotencyKey));
        if (winner == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "重复请求处理中，请稍后重试");
        }
        return replayPrevious(winner, requestHash);
    }

    /** 按订单号从库里重建完整订单响应（主表+明细+历史），用于幂等重放；不存在返回null。 */
    private OrderResponses.Order loadOrder(String orderNo) {
        OrderMain orderMain = orderMainMapper.selectOne(
                new QueryWrapper<OrderMain>().eq("order_no", orderNo));
        if (orderMain == null) {
            return null;
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", orderMain.getId()).orderByAsc("id"));
        List<OrderStatusHistory> history = orderStatusHistoryMapper.selectList(
                new QueryWrapper<OrderStatusHistory>().eq("order_id", orderMain.getId()).orderByAsc("id"));
        return buildOrder(orderMain, items.stream().map(this::toResponseItem).toList(),
                history.stream().map(OrderService::toResponseEvent).toList());
    }

    /** 把合并后的SKU数量与商品快照组装成订单明细行，并校验可售状态与单商家约束。 */
    private List<OrderLine> assembleLines(Map<Long, Integer> mergedItems, Map<String, ProductSkuInfo> skuInfos) {
        List<OrderLine> lines = new ArrayList<>();
        // 当前版本一个订单只属于一个商家，多商家需由调用方拆单。
        Long merchantId = null;
        for (Map.Entry<Long, Integer> entry : mergedItems.entrySet()) {
            ProductSkuInfo info = skuInfos.get(String.valueOf(entry.getKey()));
            if (info == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在: " + entry.getKey());
            }
            if (!"ON_SALE".equals(info.getStatus()) || !info.getEnabled()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, info.getTitle() + " 已下架或规格不可售");
            }
            long lineMerchantId = parsePositiveId(info.getMerchantId(), "merchantId");
            if (merchantId == null) {
                merchantId = lineMerchantId;
            } else if (merchantId != lineMerchantId) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "每个订单只能包含同一商家的商品");
            }
            lines.add(new OrderLine(
                    parsePositiveId(info.getSpuId(), "spuId"),
                    entry.getKey(),
                    lineMerchantId,
                    info.getTitle(),
                    info.getCoverImage(),
                    info.getSpecJson(),
                    info.getPrice(),
                    entry.getValue()
            ));
        }
        return lines;
    }

    /** 合并重复SKU的数量并校验数量范围（1–999），SKU ID统一解析为正整数。 */
    private Map<Long, Integer> mergeItems(List<OrderRequests.CreateOrderItem> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderRequests.CreateOrderItem item : items) {
            long skuId = parsePositiveId(item.skuId(), "skuId");
            int quantity = item.quantity() == null ? 0 : item.quantity();
            if (quantity < 1 || quantity > 999) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品数量应为 1–999 的整数");
            }
            merged.merge(skuId, quantity, Integer::sum);
        }
        return merged;
    }

    /** 把字符串ID解析为正整数，非数字或越界按400拒绝。 */
    private static long parsePositiveId(String value, String field) {
        if (value == null || !value.matches("\\d{1,19}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "必须为正整数");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "必须为正整数");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + "超出支持范围", exception);
        }
    }

    /** 把下单明细行转换为响应DTO，规格JSON解析为Map供前端展示。 */
    private OrderResponses.OrderItem toResponseItem(OrderLine line) {
        return new OrderResponses.OrderItem(
                String.valueOf(line.spuId()),
                String.valueOf(line.skuId()),
                line.productTitle(),
                line.productImage(),
                parseSpecs(line.specJson()),
                line.unitPrice(),
                line.quantity(),
                Math.multiplyExact(line.unitPrice(), (long) line.quantity())
        );
    }

    /** 把订单明细表记录转换为响应DTO，供幂等重放时使用。 */
    private OrderResponses.OrderItem toResponseItem(OrderItem item) {
        return new OrderResponses.OrderItem(
                String.valueOf(item.getSpuId()),
                String.valueOf(item.getSkuId()),
                item.getProductTitle(),
                item.getProductImage(),
                parseSpecs(item.getSpecSnapshot()),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getTotalPrice()
        );
    }

    /** 把状态历史表记录转换为响应事件；创建订单的空来源状态映射为null。 */
    private static OrderResponses.OrderEvent toResponseEvent(OrderStatusHistory history) {
        return new OrderResponses.OrderEvent(
                history.getFromStatus() == null || history.getFromStatus().isBlank()
                        ? null : OrderStatus.valueOf(history.getFromStatus()),
                OrderStatus.valueOf(history.getToStatus()),
                OrderResponses.OperatorType.valueOf(history.getOperatorType()),
                history.getReason(),
                history.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    /** 组装完整订单响应：主表字段 + 明细列表 + 状态历史。 */
    private OrderResponses.Order buildOrder(
            OrderMain orderMain,
            List<OrderResponses.OrderItem> items,
            List<OrderResponses.OrderEvent> history
    ) {
        return new OrderResponses.Order(
                orderMain.getOrderNo(),
                String.valueOf(orderMain.getUserId()),
                String.valueOf(orderMain.getMerchantId()),
                orderMain.getStatus(),
                orderMain.getItemAmount(),
                orderMain.getFreightAmount(),
                orderMain.getDiscountAmount(),
                orderMain.getPayableAmount(),
                fromSnapshot(orderMain.getAddressSnapshot()),
                items,
                history,
                orderMain.getVersion(),
                orderMain.getRemark(),
                orderMain.getCarrier(),
                orderMain.getTrackingNo(),
                orderMain.getCancelReason(),
                toInstant(orderMain.getExpireAt()),
                toInstant(orderMain.getPaidAt()),
                toInstant(orderMain.getShippedAt()),
                toInstant(orderMain.getCompletedAt()),
                toInstant(orderMain.getCancelledAt()),
                toInstant(orderMain.getCreatedAt())
        );
    }

    /** 解析规格快照JSON为Map；无法解析时返回空Map并记录告警，不阻断订单读取。 */
    private Map<String, String> parseSpecs(String specJson) {
        if (specJson == null || specJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(specJson, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException exception) {
            log.warn("订单明细规格快照无法解析: {}", specJson, exception);
            return Map.of();
        }
    }

    /** 反序列化订单主表中的地址快照JSON；损坏数据按500拒绝。 */
    private OrderResponses.AddressSnapshot fromSnapshot(String addressSnapshot) {
        try {
            return objectMapper.readValue(addressSnapshot, OrderResponses.AddressSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "订单地址快照无法解析", exception);
        }
    }

    /** 计算请求体的SHA-256指纹（十六进制），用于同幂等键不同请求的冲突检测。 */
    private String fingerprint(OrderRequests.CreateOrder request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(request));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("无法计算下单请求指纹", exception);
        }
    }

    /** 把对象序列化为JSON字符串，失败按500拒绝。 */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "订单数据序列化失败", exception);
        }
    }

    /** 生成「BM+UTC日期+12位随机」订单号，唯一性由 uk_order_no 兜底。 */
    private static String generateOrderNo() {
        return "BM"
                + LocalDate.now(ZoneOffset.UTC).format(ORDER_NO_DATE)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /** UTC的LocalDateTime转Instant；空值保留空，不做当前时间替换。 */
    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /** 下单校验后的明细行：价格取商品服务现价，不接受客户端价格。 */
    private record OrderLine(
            long spuId,
            long skuId,
            long merchantId,
            String productTitle,
            String productImage,
            String specJson,
            long unitPrice,
            int quantity
    ) {
    }

    /** 按当前买家和状态分页查询订单，创建时间倒序。尚未实现。 */
    public OrderResponses.OrderPage list(Long userId, OrderStatus status, int page, int size) {
        // TODO 按当前买家和状态查询订单，并按创建时间倒序分页。
        return new OrderResponses.OrderPage(page, size, 0, new ArrayList<>());
    }

    /** 查询本人订单详情，非本人订单不可见。尚未实现。 */
    public OrderResponses.Order get(Long userId, String orderNo) {
        // TODO 查询订单并校验只能读取当前买家的订单。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "订单详情功能尚未实现");
    }

    /** 取消待支付订单并回补预扣库存。尚未实现。 */
    public OrderResponses.Order cancel(Long userId, String orderNo, OrderRequests.CancelOrder request) {
        // TODO 校验待支付状态，条件更新订单并释放预扣库存。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "取消订单功能尚未实现");
    }

    /** 已发货订单确认收货并记录历史。尚未实现。 */
    public OrderResponses.Order confirm(Long userId, String orderNo) {
        // TODO 校验已发货状态，条件更新订单并记录确认收货历史。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "确认收货功能尚未实现");
    }
}

package com.bytemall.orderservice.merchant;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemall.orderservice.client.UserContextClient;
import com.bytemall.orderservice.entity.OrderItem;
import com.bytemall.orderservice.entity.OrderMain;
import com.bytemall.orderservice.entity.OrderStatusHistory;
import com.bytemall.orderservice.mapper.OrderItemMapper;
import com.bytemall.orderservice.mapper.OrderMainMapper;
import com.bytemall.orderservice.mapper.OrderStatusHistoryMapper;
import com.bytemall.orderservice.orders.OrderResponseAssembler;
import com.bytemall.orderservice.orders.OrderResponses;
import com.bytemall.orderservice.orders.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantOrderService {

    private final OrderMainMapper orderMainMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusHistoryMapper orderStatusHistoryMapper;
    private final UserContextClient userContextClient;
    private final OrderResponseAssembler orderResponseAssembler;

    /**
     * 商家订单分页：先解析商家归属做数据隔离，再按状态、关键词和创建时间倒序查询。
     * 关键词命中订单号、收件人或任一商品名称；明细和历史按页批量装配。
     */
    public OrderResponses.OrderPage list(
            Long userId,
            OrderStatus status,
            String keyword,
            int page,
            int size
    ) {
        Long merchantId = userContextClient.requireActiveMerchant(userId);
        String trimmed = keyword == null ? "" : keyword.trim();

        long total = orderMainMapper.selectCount(buildQuery(merchantId, status, trimmed, false));
        if (total == 0) {
            return new OrderResponses.OrderPage(page, size, 0, List.of());
        }

        long offset = (long) (page - 1) * size;
        List<OrderMain> orders = offset >= total
                ? List.of()
                : orderMainMapper.selectList(buildQuery(merchantId, status, trimmed, true)
                        .last("LIMIT " + size + " OFFSET " + offset));
        if (orders.isEmpty()) {
            return new OrderResponses.OrderPage(page, size, total, List.of());
        }

        List<Long> orderIds = orders.stream().map(OrderMain::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrder = orderItemMapper
                .selectList(new QueryWrapper<OrderItem>().in("order_id", orderIds).orderByAsc("id"))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, List<OrderStatusHistory>> historyByOrder = orderStatusHistoryMapper
                .selectList(new QueryWrapper<OrderStatusHistory>().in("order_id", orderIds).orderByAsc("id"))
                .stream()
                .collect(Collectors.groupingBy(OrderStatusHistory::getOrderId));

        List<OrderResponses.Order> items = orders.stream()
                .map(order -> orderResponseAssembler.toOrder(
                        order,
                        itemsByOrder.getOrDefault(order.getId(), List.of()),
                        historyByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
        return new OrderResponses.OrderPage(page, size, total, items);
    }

    /** 共用筛选条件；排序只在分页查询时追加，避免污染count查询。 */
    private static QueryWrapper<OrderMain> buildQuery(
            Long merchantId,
            OrderStatus status,
            String keyword,
            boolean withOrder
    ) {
        QueryWrapper<OrderMain> query = new QueryWrapper<OrderMain>()
                .eq("merchant_id", merchantId)
                .eq(status != null, "status", status);
        if (!keyword.isEmpty()) {
            String like = "%" + keyword + "%";
            query.and(wrapper -> wrapper
                    .like("order_no", keyword)
                    .or().apply("JSON_UNQUOTE(JSON_EXTRACT(address_snapshot, '$.receiverName')) LIKE {0}", like)
                    .or().apply("EXISTS (SELECT 1 FROM order_item i WHERE i.order_id = order_main.id AND i.product_title LIKE {0})", like));
        }
        if (withOrder) {
            query.orderByDesc("created_at", "id");
        }
        return query;
    }

    /** 商家订单详情：订单必须属于当前商家，不存在或跨商家统一返回404隐藏。 */
    public OrderResponses.Order get(Long userId, String orderNo) {
        Long merchantId = userContextClient.requireActiveMerchant(userId);
        OrderMain order = orderMainMapper.selectOne(new QueryWrapper<OrderMain>()
                .eq("merchant_id", merchantId)
                .eq("order_no", orderNo));
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        // 明细和历史固定按id升序，保证规格顺序与状态时间线稳定。
        return orderResponseAssembler.toOrder(order,
                orderItemMapper.selectList(new QueryWrapper<OrderItem>()
                        .eq("order_id", order.getId()).orderByAsc("id")),
                orderStatusHistoryMapper.selectList(new QueryWrapper<OrderStatusHistory>()
                        .eq("order_id", order.getId()).orderByAsc("id")));
    }

    /**
     * 商家发货：仅待发货订单可发，写入物流、发货时间和状态历史。
     * 同一物流单号重复发货幂等返回，版本不匹配或状态不符按409拒绝。
     */
    @Transactional
    public OrderResponses.Order ship(
            Long userId,
            String orderNo,
            MerchantOrderRequests.Shipment request
    ) {
        Long merchantId = userContextClient.requireActiveMerchant(userId);
        OrderMain order = orderMainMapper.selectOne(new QueryWrapper<OrderMain>()
                .eq("merchant_id", merchantId)
                .eq("order_no", orderNo));
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }

        String carrier = request.carrier().trim();
        String trackingNo = request.trackingNo().trim();
        // 重复请求只有同一物流单号才幂等返回；不同单号视为冲突，不能悄悄改物流。
        if (order.getStatus() == OrderStatus.SHIPPED) {
            if (Objects.equals(order.getCarrier(), carrier) && Objects.equals(order.getTrackingNo(), trackingNo)) {
                return get(userId, orderNo);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已发货，不能重复填写不同物流单号");
        }
        if (request.version() != null && !Objects.equals(request.version(), order.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单版本已变化，请刷新后重试");
        }
        if (order.getStatus() != OrderStatus.PENDING_SHIPMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有待发货订单可以发货");
        }

        LocalDateTime shippedAt = LocalDateTime.now(ZoneOffset.UTC);
        // 条件更新把并发的状态迁移挡在状态机上，0行说明状态已被其他流程改掉。
        if (orderMainMapper.shipOrder(orderNo, carrier, trackingNo, shippedAt) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试");
        }
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setFromStatus(OrderStatus.PENDING_SHIPMENT.name());
        history.setToStatus(OrderStatus.SHIPPED.name());
        history.setOperatorType(OrderResponses.OperatorType.MERCHANT.name());
        history.setOperatorId(userId);
        history.setReason(carrier + " " + trackingNo);
        history.setCreatedAt(shippedAt);
        orderStatusHistoryMapper.insert(history);
        return get(userId, orderNo);
    }
}

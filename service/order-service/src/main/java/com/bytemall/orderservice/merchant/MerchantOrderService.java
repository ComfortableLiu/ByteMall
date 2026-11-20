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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
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

    public OrderResponses.Order get(Long userId, String orderNo) {
        // TODO 根据userId解析商家归属，并校验订单属于当前商家。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "商家订单详情功能尚未实现");
    }

    public OrderResponses.Order ship(
            Long userId,
            String orderNo,
            MerchantOrderRequests.Shipment request
    ) {
        // TODO 校验商家归属、待发货状态和乐观锁版本，再写入物流及状态历史。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "订单发货功能尚未实现");
    }
}

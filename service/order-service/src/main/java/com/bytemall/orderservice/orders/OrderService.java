package com.bytemall.orderservice.orders;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;

@Service
public class OrderService {

    public OrderResponses.Order create(
            Long userId,
            String idempotencyKey,
            OrderRequests.CreateOrder request
    ) {
        // TODO 实现买家校验、重新定价、库存预扣、幂等落库和订单事件。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "创建订单功能尚未实现");
    }

    public OrderResponses.OrderPage list(Long userId, OrderStatus status, int page, int size) {
        // TODO 按当前买家和状态查询订单，并按创建时间倒序分页。
        return new OrderResponses.OrderPage(page, size, 0, new ArrayList<>());
    }

    public OrderResponses.Order get(Long userId, String orderNo) {
        // TODO 查询订单并校验只能读取当前买家的订单。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "订单详情功能尚未实现");
    }

    public OrderResponses.Order cancel(Long userId, String orderNo, OrderRequests.CancelOrder request) {
        // TODO 校验待支付状态，条件更新订单并释放预扣库存。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "取消订单功能尚未实现");
    }

    public OrderResponses.Order confirm(Long userId, String orderNo) {
        // TODO 校验已发货状态，条件更新订单并记录确认收货历史。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "确认收货功能尚未实现");
    }
}

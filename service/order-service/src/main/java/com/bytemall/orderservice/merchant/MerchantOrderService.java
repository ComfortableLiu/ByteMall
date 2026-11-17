package com.bytemall.orderservice.merchant;

import com.bytemall.orderservice.orders.OrderResponses;
import com.bytemall.orderservice.orders.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantOrderService {

    public OrderResponses.OrderPage list(
            Long userId,
            OrderStatus status,
            String keyword,
            int page,
            int size
    ) {
        // TODO 根据userId解析商家归属，再按状态、关键词和创建时间查询本商家订单。
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "商家订单分页功能尚未实现");
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

package com.bytemall.stockservice.internal;

import java.security.Timestamp;
import java.util.List;

/**
 * 创建库存预约请求
 * @param reservationNo 预约号
 * @param orderNo 订单号
 * @param expireAt 过期时间
 * @param items 预约商品列表
 */
public record CreateStocksReservationDto(
        String reservationNo,
        String orderNo,
        Timestamp expireAt,
        List<Item> items
) {
    public record Item(String skuId, Integer quantity) {
    }
}

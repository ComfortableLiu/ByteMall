package com.bytemall.orderservice.orders;

/**
 * 订单状态机：PENDING_PAYMENT → PENDING_SHIPMENT → SHIPPED → COMPLETED 为正常履约主线，
 * PENDING_PAYMENT → CANCELLED 为取消支线，已发货后的售后走 REFUNDING → REFUNDED。
 */
public enum OrderStatus {

    /** 待支付：已下单并预扣库存，等待买家付款，超时未付可取消。 */
    PENDING_PAYMENT,

    /** 待发货：支付成功，等待商家发货。 */
    PENDING_SHIPMENT,

    /** 已发货：商家已填写物流并发出，运输中。 */
    SHIPPED,

    /** 已完成：买家确认收货，正常履约终态。 */
    COMPLETED,

    /** 已取消：待支付阶段取消，预扣库存已回补，终态。 */
    CANCELLED,

    /** 退款中：买家发起售后，退款流程进行中。 */
    REFUNDING,

    /** 已退款：退款完成，售后终态。 */
    REFUNDED
}

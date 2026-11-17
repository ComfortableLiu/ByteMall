package com.bytemall.orderservice.orders;

public enum OrderStatus {
    PENDING_PAYMENT,
    PENDING_SHIPMENT,
    SHIPPED,
    COMPLETED,
    CANCELLED,
    REFUNDING,
    REFUNDED
}

package com.bytemall.orderservice.orders;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OrderResponses {

    private OrderResponses() {
    }

    public enum OperatorType {
        BUYER,
        MERCHANT,
        SYSTEM,
        EVENT
    }

    /** 下单后的地址按快照返回，不随用户地址簿后续修改而变化。 */
    public record AddressSnapshot(
            String receiverName,
            String receiverMobile,
            String province,
            String city,
            String district,
            String detailAddress,
            String postalCode,
            boolean isDefault
    ) {
    }

    public record OrderItem(
            String spuId,
            String skuId,
            String productTitle,
            String productImage,
            Map<String, String> specs,
            long unitPrice,
            int quantity,
            long totalPrice
    ) {
    }

    public record OrderEvent(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            OperatorType operatorType,
            String reason,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
    ) {
    }

    public record Order(
            String orderNo,
            String userId,
            String merchantId,
            OrderStatus status,
            long itemAmount,
            long freightAmount,
            long discountAmount,
            long payableAmount,
            AddressSnapshot address,
            List<OrderItem> items,
            List<OrderEvent> history,
            long version,
            String remark,
            String carrier,
            String trackingNo,
            String cancelReason,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expireAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant paidAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant shippedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant completedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant cancelledAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
    ) {
    }

    /** 前端分页契约固定为 items/page/size/total，不能使用 list/pageSize。 */
    public record OrderPage(
            int page,
            int size,
            long total,
            List<Order> items
    ) {
    }
}

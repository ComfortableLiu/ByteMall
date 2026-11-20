package com.bytemall.orderservice.orders;

import com.bytemall.orderservice.entity.OrderItem;
import com.bytemall.orderservice.entity.OrderMain;
import com.bytemall.orderservice.entity.OrderStatusHistory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** 订单响应装配器：把订单主表、明细和状态历史映射为对外DTO，供买家与商家接口共用。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderResponseAssembler {

    private final ObjectMapper objectMapper;

    public OrderResponses.Order toOrder(
            OrderMain orderMain,
            List<OrderItem> items,
            List<OrderStatusHistory> history
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
                items.stream().map(this::toResponseItem).toList(),
                history.stream().map(OrderResponseAssembler::toResponseEvent).toList(),
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
    public Map<String, String> parseSpecs(String specJson) {
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

    /** 创建订单的空来源状态映射为null，与前端展示语义一致。 */
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

    private OrderResponses.AddressSnapshot fromSnapshot(String addressSnapshot) {
        try {
            return objectMapper.readValue(addressSnapshot, OrderResponses.AddressSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "订单地址快照无法解析", exception);
        }
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}

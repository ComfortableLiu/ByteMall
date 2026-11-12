package com.bytemall.payservice.payments;

import com.bytemall.payment.proto.CloseOrderResponse;
import com.bytemall.payservice.entity.PaymentOrder;
import com.bytemall.payservice.entity.PaymentTransaction;
import com.bytemall.payservice.mapper.PaymentOrderMapper;
import com.bytemall.payservice.mapper.PaymentTransactionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCloseService {

    private static final Set<String> CLOSE_REASONS = Set.of(
            "ORDER_CANCELLED",
            "PAYMENT_TIMEOUT",
            "RISK_REJECTED"
    );

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public CloseOrderResponse closeOrder(String orderNo, String reason) {
        if (orderNo == null || orderNo.isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription("orderNo不能为空").asRuntimeException();
        }
        if (!CLOSE_REASONS.contains(reason)) {
            throw Status.INVALID_ARGUMENT.withDescription("不支持的支付关闭原因").asRuntimeException();
        }

        PaymentOrder paymentOrder = paymentOrderMapper.selectLatestByOrderNoForUpdate(orderNo);
        if (paymentOrder == null) {
            throw Status.NOT_FOUND.withDescription("订单没有支付单: " + orderNo).asRuntimeException();
        }
        if (paymentOrder.getStatus() == PaymentStatus.CLOSED) {
            return closeResponse(paymentOrder);
        }
        if (paymentOrder.getStatus() != PaymentStatus.PENDING) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("当前支付状态不允许关闭: " + paymentOrder.getStatus())
                    .asRuntimeException();
        }
        if (paymentOrder.getChannel() != PaymentChannel.MOCK) {
            throw Status.UNAVAILABLE
                    .withDescription("支付渠道关单适配器尚未接入: " + paymentOrder.getChannel())
                    .asRuntimeException();
        }

        LocalDateTime closedAt = LocalDateTime.now(ZoneOffset.UTC);
        if (paymentOrderMapper.closePendingPayment(paymentOrder.getPayNo(), closedAt) != 1) {
            throw Status.ABORTED.withDescription("支付单状态已变化，请查询后重试").asRuntimeException();
        }
        saveCloseTransaction(paymentOrder, orderNo, reason, closedAt);

        paymentOrder.setStatus(PaymentStatus.CLOSED);
        paymentOrder.setUpdatedAt(closedAt);
        log.info("Closed payment {} for order {}, reason {}", paymentOrder.getPayNo(), orderNo, reason);
        return closeResponse(paymentOrder);
    }

    private void saveCloseTransaction(
            PaymentOrder paymentOrder,
            String orderNo,
            String reason,
            LocalDateTime closedAt
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayNo(paymentOrder.getPayNo());
        transaction.setTransactionType("CLOSE");
        transaction.setRequestPayload(toJson(Map.of("orderNo", orderNo, "reason", reason)));
        transaction.setCreatedAt(closedAt);
        paymentTransactionMapper.insert(transaction);
    }

    private String toJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw Status.INTERNAL.withDescription("无法记录支付关闭流水").withCause(exception).asRuntimeException();
        }
    }

    private CloseOrderResponse closeResponse(PaymentOrder paymentOrder) {
        LocalDateTime closedAt = paymentOrder.getUpdatedAt();
        if (closedAt == null) {
            throw Status.INTERNAL.withDescription("已关闭支付单缺少关闭时间").asRuntimeException();
        }
        return CloseOrderResponse.newBuilder()
                .setOrderNo(paymentOrder.getOrderNo())
                .setPayNo(paymentOrder.getPayNo())
                .setStatus(PaymentStatus.CLOSED.name())
                .setClosedAt(closedAt.toInstant(ZoneOffset.UTC).toString())
                .build();
    }
}

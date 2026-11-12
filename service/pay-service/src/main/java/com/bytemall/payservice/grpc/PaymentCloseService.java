package com.bytemall.payservice.grpc;

import com.bytemall.payment.proto.CloseOrderResponse;
import com.bytemall.payservice.entity.PaymentOrder;
import com.bytemall.payservice.entity.PaymentTransaction;
import com.bytemall.payservice.mapper.PaymentOrderMapper;
import com.bytemall.payservice.mapper.PaymentTransactionMapper;
import com.bytemall.payservice.payments.PaymentChannel;
import com.bytemall.payservice.payments.PaymentStatus;
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

/**
 * 支付关单服务，在事务中完成状态校验、关单和流水记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCloseService {

    /** 允许调用方传入的业务关单原因。 */
    private static final Set<String> CLOSE_REASONS = Set.of(
            "ORDER_CANCELLED",
            "PAYMENT_TIMEOUT",
            "RISK_REJECTED"
    );

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 关闭订单当前的待支付支付单，只允许 PENDING 迁移到 CLOSED。
     */
    @Transactional
    public CloseOrderResponse closeOrder(String orderNo, String reason) {
        if (orderNo == null || orderNo.isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription("orderNo不能为空").asRuntimeException();
        }
        if (!CLOSE_REASONS.contains(reason)) {
            throw Status.INVALID_ARGUMENT.withDescription("不支持的支付关闭原因").asRuntimeException();
        }

        // 通过行锁串行化同一支付单的关单与支付回调处理。
        PaymentOrder paymentOrder = paymentOrderMapper.selectLatestByOrderNoForUpdate(orderNo);
        if (paymentOrder == null) {
            throw Status.NOT_FOUND.withDescription("订单没有支付单: " + orderNo).asRuntimeException();
        }
        // 重复关单直接返回首次结果，不重复更新状态或写入流水。
        if (paymentOrder.getStatus() == PaymentStatus.CLOSED) {
            return closeResponse(paymentOrder);
        }
        if (paymentOrder.getStatus() != PaymentStatus.PENDING) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("当前支付状态不允许关闭: " + paymentOrder.getStatus())
                    .asRuntimeException();
        }
        // 真实渠道必须确认未支付后才能关单，当前仅开放 MOCK 流程。
        if (paymentOrder.getChannel() != PaymentChannel.MOCK) {
            throw Status.UNAVAILABLE
                    .withDescription("支付渠道关单适配器尚未接入: " + paymentOrder.getChannel())
                    .asRuntimeException();
        }

        LocalDateTime closedAt = LocalDateTime.now(ZoneOffset.UTC);
        // 更新时再次检查 PENDING，避免并发状态变化后被覆盖。
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

package com.bytemall.payservice.grpc;

import com.bytemall.payment.proto.CreatePaymentRequest;
import com.bytemall.payment.proto.CreatePaymentResponse;
import com.bytemall.payservice.entity.PaymentOrder;
import com.bytemall.payservice.mapper.PaymentOrderMapper;
import com.bytemall.payservice.payments.PaymentChannel;
import com.bytemall.payservice.payments.PaymentRequests;
import com.bytemall.payservice.payments.PaymentResponses;
import com.bytemall.payservice.payments.PaymentStatus;
import com.bytemall.payservice.payments.PaymentsService;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCreateService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentsService paymentsService;

    /**
     * 为订单创建支付单；存在同渠道待支付单时直接复用，避免重复支付
     */
    @Transactional
    public CreatePaymentResponse createPayment(CreatePaymentRequest request) {
        String orderNo = request.getOrderNo();
        if (orderNo.isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription("orderNo不能为空").asRuntimeException();
        }

        PaymentChannel channel = parseChannel(request.getChannel());
        // 锁定订单最新的支付单，后续复用或创建都在同一事务中完成
        PaymentOrder existing = paymentOrderMapper.selectLatestByOrderNoForUpdate(orderNo);
        if (existing != null) {
            // 已成功是终态，不能通过重新创建支付单再次收款
            if (existing.getStatus() == PaymentStatus.SUCCESS) {
                throw Status.FAILED_PRECONDITION.withDescription("订单已支付，不能重复创建支付单").asRuntimeException();
            }
            if (existing.getStatus() == PaymentStatus.PENDING) {
                // 切换渠道前必须先关闭原支付单，防止两个渠道同时有效
                if (existing.getChannel() != channel) {
                    throw Status.ALREADY_EXISTS
                            .withDescription("订单已有其他渠道的有效支付单，请先关闭原支付单")
                            .asRuntimeException();
                }
                log.info("Reusing pending payment {} for order {}", existing.getPayNo(), orderNo);
                return response(paymentsService.get(existing.getPayNo()));
            }
        }

        // 没有支付单，或最新支付单已关闭/失败时，才创建新的支付单
        PaymentResponses.Payment payment = paymentsService.create(
                new PaymentRequests.CreatePayment(orderNo, channel),
                ""
        );
        return response(payment);
    }

    private PaymentChannel parseChannel(String value) {
        try {
            return PaymentChannel.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("channel仅支持MOCK、ALIPAY或WECHAT")
                    .withCause(exception)
                    .asRuntimeException();
        }
    }

    private CreatePaymentResponse response(PaymentResponses.Payment payment) {
        return CreatePaymentResponse.newBuilder()
                .setPayNo(payment.payNo())
                .setOrderNo(payment.orderNo())
                .setUserId(payment.userId().toString())
                .setChannel(payment.channel().name())
                .setAmount(payment.amount())
                .setStatus(payment.status().name())
                .setCashierToken(payment.cashierToken() == null ? "" : payment.cashierToken())
                .setExpireAt(payment.expireAt().toString())
                .setPaidAt(payment.paidAt() == null ? "" : payment.paidAt().toString())
                .setCreatedAt(payment.createdAt().toString())
                .build();
    }
}

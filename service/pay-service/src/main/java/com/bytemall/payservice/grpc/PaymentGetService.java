package com.bytemall.payservice.grpc;

import com.bytemall.payment.proto.GetPaymentRequest;
import com.bytemall.payment.proto.GetPaymentResponse;
import com.bytemall.payservice.payments.PaymentResponses;
import com.bytemall.payservice.payments.PaymentsService;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGetService {

    private final PaymentsService paymentsService;

    public GetPaymentResponse getPayment(GetPaymentRequest request) {
        // 内部RPC只按支付单号查询可信状态；面向买家的归属校验在REST层完成。
        PaymentResponses.Payment payment = paymentsService.find(request.getPayNo());
        if (payment == null) {
            log.error("支付单不存在");
            throw Status.NOT_FOUND
                    .withDescription("支付单不存在")
                    .asRuntimeException();
        }
        return response(payment);
    }

    private GetPaymentResponse response(PaymentResponses.Payment payment) {
        return GetPaymentResponse.newBuilder()
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

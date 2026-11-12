package com.bytemall.payservice.payments;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bytemall.payment.proto.CloseOrderRequest;
import com.bytemall.payment.proto.CloseOrderResponse;
import com.bytemall.payment.proto.PaymentServiceGrpc;
import com.bytemall.payservice.entity.PaymentOrder;
import com.bytemall.payservice.mapper.PaymentOrderMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final PaymentsService paymentsService;
    private final PaymentOrderMapper paymentOrderMapper;

    @Override
    public void closeOrder(CloseOrderRequest request, StreamObserver<CloseOrderResponse> responseObserver) {
        String payNo = request.getPayNo();
        log.info("closeOrder: {}", payNo);

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPayNo(payNo);
        paymentOrder.setStatus(PaymentStatus.CLOSED);
        paymentOrder.setUpdatedAt(LocalDateTime.now());
        paymentOrder.setVersion(paymentOrderMapper.selectByPayNoForUpdate(payNo).getVersion());
        paymentOrderMapper.update(paymentOrder, new UpdateWrapper<PaymentOrder>().set("pay_no", payNo));
        PaymentOrder paymentOrderUpdated = paymentOrderMapper.selectByPayNoForUpdate(payNo);
        responseObserver.onNext(CloseOrderResponse.newBuilder()
                        .setAmount(paymentOrderUpdated.getAmount().intValue())
                        .setOrderNo(paymentOrderUpdated.getOrderNo())
                        .setUserId(paymentOrderUpdated.getUserId())
                        .setChannel(paymentOrderUpdated.getChannel().name())
                        .setStatus(paymentOrderUpdated.getStatus().name())
                        .setPayNo(paymentOrderUpdated.getPayNo())
                        .setCashierToken(paymentOrderUpdated.getCashierToken())
                        .setPaidAt(paymentOrderUpdated.getPaidAt())
                        .setExpireAt(paymentOrderUpdated.getExpireAt())
                        .setCreatedAt(paymentOrderUpdated.getCreatedAt())

                .build());
        responseObserver.onCompleted();
    }
}

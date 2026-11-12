package com.bytemall.payservice.payments;

import com.bytemall.payment.proto.CloseOrderRequest;
import com.bytemall.payment.proto.CloseOrderResponse;
import com.bytemall.payment.proto.PaymentServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final PaymentCloseService paymentCloseService;

    @Override
    public void closeOrder(CloseOrderRequest request, StreamObserver<CloseOrderResponse> responseObserver) {
        try {
            responseObserver.onNext(paymentCloseService.closeOrder(request.getOrderNo(), request.getReason()));
            responseObserver.onCompleted();
        } catch (StatusRuntimeException exception) {
            responseObserver.onError(exception);
        } catch (Exception exception) {
            log.error("Failed to close payment for order {}", request.getOrderNo(), exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("支付单关闭失败")
                    .withCause(exception)
                    .asRuntimeException());
        }
    }
}

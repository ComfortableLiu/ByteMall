package com.bytemall.payservice.grpc;

import com.bytemall.payment.proto.*;
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
    private final PaymentCreateService paymentCreateService;
    private final PaymentGetService paymentGetService;

    /**
     * 关闭支付单；业务层抛出的 StatusRuntimeException 会保留原状态码返回调用方
     */
    @Override
    public void closeOrder(CloseOrderRequest request, StreamObserver<CloseOrderResponse> responseObserver) {
        log.info("closeOrder OrderNo:{}", request.getOrderNo());
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

    @Override
    public void createPayment(CreatePaymentRequest request, StreamObserver<CreatePaymentResponse> responseObserver) {
        log.info("createPayment OrderNo:{}", request.getOrderNo());
        try {
            responseObserver.onNext(paymentCreateService.createPayment(request));
            responseObserver.onCompleted();
        } catch (StatusRuntimeException exception) {
            responseObserver.onError(exception);
        } catch (Exception exception) {
            log.error("Failed to create payment for order {}", request.getOrderNo(), exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("支付单创建失败")
                    .withCause(exception)
                    .asRuntimeException());
        }
    }

    @Override
    public void getPayment(GetPaymentRequest request, StreamObserver<GetPaymentResponse> responseObserver) {
        log.info("getPayment PayNo: {}", request.getPayNo());
        try {
            responseObserver.onNext(paymentGetService.getPayment(request, 0L));
            responseObserver.onCompleted();
        } catch (StatusRuntimeException exception) {
            responseObserver.onError(exception);
        } catch (Exception exception) {
            log.error("Failed to get payment for payNo {}", request.getPayNo(), exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("支付单获取失败")
                    .withCause(exception)
                    .asRuntimeException());
        }
    }
}

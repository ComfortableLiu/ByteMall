package com.bytemail.payservice.payments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentsService {

    public PaymentResponses.Payment create(PaymentRequests.CreatePayment request, String idempotencyKey) {
        log.info("Creating payment for order {} via {}", request.orderNo(), request.channel());
        Instant now = Instant.now();
        return new PaymentResponses.Payment(
                "PAY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                request.orderNo(),
                "demo-user",
                request.channel(),
                0,
                PaymentStatus.PENDING,
                request.channel() == PaymentChannel.MOCK ? "mock-cashier-token" : null,
                now.plus(30, ChronoUnit.MINUTES),
                null,
                now
        );
    }

    public PaymentResponses.Payment get(String payNo) {
        log.info("Getting payment {}", payNo);
        return response(payNo, PaymentStatus.PENDING);
    }

    public PaymentResponses.Payment close(String payNo) {
        log.info("Closing payment {}", payNo);
        return response(payNo, PaymentStatus.CLOSED);
    }

    public PaymentResponses.Payment completeMock(String payNo) {
        log.info("Completing mock payment {}", payNo);
        return response(payNo, PaymentStatus.SUCCESS);
    }

    private PaymentResponses.Payment response(String payNo, PaymentStatus status) {
        Instant now = Instant.now();
        return new PaymentResponses.Payment(
                payNo,
                "ORDER-DEMO",
                "demo-user",
                PaymentChannel.MOCK,
                0,
                status,
                "mock-cashier-token",
                now.plus(30, ChronoUnit.MINUTES),
                status == PaymentStatus.SUCCESS ? now : null,
                now
        );
    }
}

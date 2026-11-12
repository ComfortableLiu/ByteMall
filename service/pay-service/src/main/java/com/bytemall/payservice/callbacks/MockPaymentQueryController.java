package com.bytemall.payservice.callbacks;

import com.bytemall.common.model.dto.ApiResponse;
import com.bytemall.payservice.payments.PaymentStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("!prod")
@RequestMapping("/v1/internal/mock-payments")
public class MockPaymentQueryController {

    @GetMapping("/query")
    public ApiResponse<PaymentCallbackRequests.PaymentNotification> query(
            @RequestParam String orderNo,
            @RequestParam(defaultValue = "0") Long amount,
            @RequestParam(defaultValue = "SUCCESS") PaymentStatus status
    ) {
        if (status != PaymentStatus.SUCCESS && status != PaymentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status仅支持SUCCESS或FAILED");
        }
        return ApiResponse.ok(new PaymentCallbackRequests.PaymentNotification(
                "MOCK-NOTIFY-" + orderNo,
                orderNo,
                amount,
                status.name(),
                "mock-signature"
        ));
    }
}

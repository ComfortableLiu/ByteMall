package com.bytemall.payservice.payments;

import com.bytemall.common.auth.CurrentUserId;
import com.bytemall.common.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentsController {

    private final PaymentsService paymentsService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponses.Payment> create(
            @Valid @RequestBody PaymentRequests.CreatePayment request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @CurrentUserId Long userId
    ) {
        return ApiResponse.ok(paymentsService.create(request, idempotencyKey));
    }

    @GetMapping("/{payNo}")
    public ApiResponse<PaymentResponses.Payment> get(@PathVariable String payNo) {
        return ApiResponse.ok(paymentsService.get(payNo));
    }
}

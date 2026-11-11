package com.bytemall.payservice.payments;

import com.bytemall.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!prod")
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class MockPaymentsController {

    private final PaymentsService paymentsService;

    @PostMapping("/{payNo}/mock-complete")
    public ApiResponse<PaymentResponses.Payment> complete(@PathVariable String payNo) {
        return ApiResponse.ok(paymentsService.completeMock(payNo));
    }
}

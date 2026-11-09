package com.bytemall.payservice.callbacks;

import com.bytemall.payservice.payments.PaymentChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/internal/payment-callbacks")
@RequiredArgsConstructor
public class PaymentCallbacksController {

    private final PaymentCallbacksService paymentCallbacksService;

    @PostMapping("/{channel}")
    public ResponseEntity<String> accept(
            @PathVariable PaymentChannel channel,
            @RequestBody PaymentCallbackRequests.PaymentNotification notification
    ) {
        return ResponseEntity.ok(paymentCallbacksService.accept(channel, notification));
    }
}

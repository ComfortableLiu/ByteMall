package com.bytemail.payservice.callbacks;

import com.bytemail.payservice.payments.PaymentChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbacksService {

    public String accept(PaymentChannel channel, PaymentCallbackRequests.PaymentNotification notification) {
        log.info("Received payment callback from {} for order {}", channel, notification.orderNo());
        return "success";
    }
}

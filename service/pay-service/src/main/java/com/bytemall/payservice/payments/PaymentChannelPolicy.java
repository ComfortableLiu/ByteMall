package com.bytemall.payservice.payments;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 支付渠道环境策略，生产环境禁止使用模拟支付。 */
@Component
@RequiredArgsConstructor
public class PaymentChannelPolicy {

    private final Environment environment;

    public void validate(PaymentChannel channel) {
        if (channel == PaymentChannel.MOCK && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "生产环境禁止使用MOCK支付渠道");
        }
    }
}

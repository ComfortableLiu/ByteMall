package com.bytemall.payservice.payments;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bytemall.payservice.entity.PaymentOrder;
import com.bytemall.payservice.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentsService {

    private final PaymentOrderMapper paymentOrderMapper;

    public PaymentResponses.Payment create(PaymentRequests.CreatePayment request, String idempotencyKey) {
        log.info("Creating payment for order {} via {}", request.orderNo(), request.channel());
        Instant now = Instant.now();
        // TODO 去订单服务校验订单归属、状态、金额和有效期
        // validateOrder(request.orderNo(), request.amount());
        // 假装去三方支付渠道进行
        String cashierToken = null;
        String payNo = "PAY-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        if (request.channel() == PaymentChannel.MOCK) {
            // 调用三方支付渠道的接口，进行支付
            cashierToken = "mock-cashier-token";
            log.info("发起Mock渠道支付 支付号：{} 订单号：{}", payNo, request.orderNo());
        } else if (request.channel() == PaymentChannel.ALIPAY) {
            // 假装调用支付宝的接口，进行支付
            cashierToken = "alipay-cashier-token";
            log.info("发起支付宝支付 支付号：{} 订单号：{}", payNo, request.orderNo());
        } else if (request.channel() == PaymentChannel.WECHAT) {
            // 假装调用微信的接口，进行支付
            cashierToken = "wechat-cashier-token";
            log.info("发起微信支付 支付号：{} 订单号：{}", payNo, request.orderNo());
        }
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPayNo(payNo);
        paymentOrder.setOrderNo(request.orderNo());
        // TODO 用户信息
        paymentOrder.setUserId(123L);
        paymentOrder.setChannel(request.channel());
        // TODO 金额
        paymentOrder.setAmount(0L);
        paymentOrder.setStatus(PaymentStatus.PENDING);
        paymentOrder.setChannelTradeNo(cashierToken);
        paymentOrder.setExpireAt(LocalDateTime.ofInstant(now.plus(30, ChronoUnit.MINUTES), ZoneOffset.UTC));
        paymentOrder.setPaidAt(null);
        paymentOrder.setCreatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));

        paymentOrderMapper.insert(paymentOrder);
        return new PaymentResponses.Payment(
                paymentOrder.getPayNo(),
                paymentOrder.getOrderNo(),
                paymentOrder.getUserId(),
                paymentOrder.getChannel(),
                paymentOrder.getAmount(),
                paymentOrder.getStatus(),
                paymentOrder.getChannelTradeNo(),
                paymentOrder.getExpireAt().toInstant(ZoneOffset.UTC),
                toInstant(paymentOrder.getPaidAt()),
                paymentOrder.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    public PaymentResponses.Payment get(String payNo) {
        log.info("Getting payment {}", payNo);
        return response(payNo);
    }

    public PaymentResponses.Payment completeMock(String payNo) {
        log.info("Completing mock payment {}", payNo);
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPayNo(payNo);
        paymentOrder.setStatus(PaymentStatus.SUCCESS);
        paymentOrder.setUpdatedAt(LocalDateTime.now());
        paymentOrder.setVersion(paymentOrderMapper.selectByPayNoForUpdate(payNo).getVersion());
        paymentOrderMapper.update(paymentOrder, new UpdateWrapper<PaymentOrder>().set("pay_no", payNo));
        return response(payNo);
    }

    private PaymentResponses.Payment response(String payNo) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectByPayNoForUpdate(payNo);
        return new PaymentResponses.Payment(
                paymentOrder.getPayNo(),
                paymentOrder.getOrderNo(),
                paymentOrder.getUserId(),
                paymentOrder.getChannel(),
                paymentOrder.getAmount(),
                paymentOrder.getStatus(),
                paymentOrder.getChannelTradeNo(),
                paymentOrder.getExpireAt().toInstant(ZoneOffset.UTC),
                toInstant(paymentOrder.getPaidAt()),
                paymentOrder.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    /**
     * 未支付时 paidAt 为 null，响应中保留空值而不是执行时间转换。
     */
    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}

package com.bytemall.payservice.callbacks;

import com.bytemall.payservice.entity.PaymentOrder;
import com.bytemall.payservice.entity.PaymentTransaction;
import com.bytemall.payservice.mapper.PaymentOrderMapper;
import com.bytemall.payservice.mapper.PaymentTransactionMapper;
import com.bytemall.payservice.payments.PaymentChannel;
import com.bytemall.payservice.payments.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

/**
 * 支付渠道回调服务，负责验签、通知去重、支付状态更新和回调流水记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbacksService {

    private static final String MOCK_SIGNATURE = "mock-signature";

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 处理渠道通知。只有数据库事务提交后，Controller 才会向渠道返回 success。
     */
    @Transactional
    public String accept(PaymentChannel channel, PaymentCallbackRequests.PaymentNotification notification) {
        validateNotification(notification);
        verifySignature(channel, notification);
        String requestPayload = toJson(notification);

        PaymentTransaction existingTransaction = paymentTransactionMapper.selectByNotifyId(notification.notifyId());
        if (existingTransaction != null) {
            if (!samePayload(existingTransaction.getRequestPayload(), requestPayload)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "通知ID已对应其他报文");
            }
            return "success";
        }

        // 当前 MOCK 报文通过订单号定位支付单，并加行锁防止回调与关单并发覆盖。
        PaymentOrder paymentOrder = paymentOrderMapper.selectLatestByOrderNoForUpdate(notification.orderNo());
        if (paymentOrder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单没有支付单");
        }
        if (paymentOrder.getChannel() != channel) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "通知渠道与支付单不一致");
        }
        if (!Objects.equals(paymentOrder.getAmount(), notification.amount())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "通知金额与支付单不一致");
        }

        LocalDateTime processedAt = LocalDateTime.now(ZoneOffset.UTC);
        if ("SUCCESS".equals(notification.status())) {
            applySuccess(paymentOrder, processedAt);
        } else {
            applyFailure(paymentOrder, processedAt);
        }
        saveCallbackTransaction(paymentOrder, notification, requestPayload, processedAt);

        log.info("Processed {} callback for payment {}, status {}",
                channel, paymentOrder.getPayNo(), notification.status());
        return "success";
    }

    /** 校验当前 MOCK 报文的必填字段和状态范围。 */
    private void validateNotification(PaymentCallbackRequests.PaymentNotification notification) {
        if (notification == null
                || notification.notifyId() == null || notification.notifyId().isBlank()
                || notification.orderNo() == null || notification.orderNo().isBlank()
                || notification.amount() == null || notification.amount() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回调通知参数不完整或金额非法");
        }
        if (!"SUCCESS".equals(notification.status()) && !"FAILED".equals(notification.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回调状态仅支持SUCCESS或FAILED");
        }
    }

    /** 验证通知来源，真实渠道不能用固定签名进入生产流程。 */
    private void verifySignature(PaymentChannel channel, PaymentCallbackRequests.PaymentNotification notification) {
        if (channel != PaymentChannel.MOCK) {
            // TODO 接入支付宝/微信支付证书验签，并校验商户号、时间戳和防重放参数。
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "真实支付渠道验签尚未接入");
        }
        if (!MOCK_SIGNATURE.equals(notification.signature())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MOCK回调签名错误");
        }
    }

    /** 支付成功只允许从待支付迁移；已经成功则视为幂等通知。 */
    private void applySuccess(PaymentOrder paymentOrder, LocalDateTime paidAt) {
        if (paymentOrder.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }
        requirePending(paymentOrder);
        if (paymentOrderMapper.markPaymentSucceeded(
                paymentOrder.getPayNo(),
                paymentOrder.getChannelTradeNo(),
                paidAt
        ) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付单状态已变化，请重试");
        }

        // TODO 在同一事务写入 PaymentSucceeded Outbox，由 relay 投递 Kafka，驱动订单和库存后续流程。
    }

    /** 支付失败只结束待支付单，不发送支付成功事件。 */
    private void applyFailure(PaymentOrder paymentOrder, LocalDateTime failedAt) {
        if (paymentOrder.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        requirePending(paymentOrder);
        if (paymentOrderMapper.markPaymentFailed(paymentOrder.getPayNo(), failedAt) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付单状态已变化，请重试");
        }
    }

    /** 已关闭、已成功等终态不能被另一条通知直接覆盖。 */
    private void requirePending(PaymentOrder paymentOrder) {
        if (paymentOrder.getStatus() != PaymentStatus.PENDING) {
            // TODO 对成功通知与CLOSED等终态的冲突增加对账告警和人工核查入口。
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前支付状态不允许处理该通知: " + paymentOrder.getStatus()
            );
        }
    }

    /** 保存已验签的通知报文，供通知去重、审计和后续对账使用。 */
    private void saveCallbackTransaction(
            PaymentOrder paymentOrder,
            PaymentCallbackRequests.PaymentNotification notification,
            String requestPayload,
            LocalDateTime processedAt
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayNo(paymentOrder.getPayNo());
        transaction.setNotifyId(notification.notifyId());
        transaction.setTransactionType("PAY");
        transaction.setRequestPayload(requestPayload);
        transaction.setResponsePayload(toJson(Map.of("result", "success")));
        transaction.setVerifyResult(true);
        transaction.setCreatedAt(processedAt);
        paymentTransactionMapper.insert(transaction);
    }

    private boolean samePayload(String existingPayload, String currentPayload) {
        if (existingPayload == null) {
            return false;
        }
        try {
            return objectMapper.readTree(existingPayload).equals(objectMapper.readTree(currentPayload));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "历史回调报文无法解析",
                    exception
            );
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "回调报文序列化失败",
                    exception
            );
        }
    }
}

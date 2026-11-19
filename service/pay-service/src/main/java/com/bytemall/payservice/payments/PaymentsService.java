package com.bytemall.payservice.payments;

import com.bytemall.payservice.entity.PaymentOrder;
import com.bytemall.payservice.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentsService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentChannelPolicy paymentChannelPolicy;

    /**
     * 创建支付单；同订单已有有效支付单时复用现有单，避免重复扣款。
     */
    @Transactional
    public PaymentResponses.Payment create(PaymentRequests.CreatePayment request, String idempotencyKey, Long userId) {
        // 必须在生成渠道凭证和写入支付单前拒绝生产环境的模拟支付。
        paymentChannelPolicy.validate(request.channel());
        log.info("Creating payment for order {} via {}, idempotencyKey {}",
                request.orderNo(), request.channel(), idempotencyKey);

        PaymentOrder existing = paymentOrderMapper.selectLatestByOrderNoForUpdate(request.orderNo());
        if (existing != null) {
            if (existing.getStatus() == PaymentStatus.SUCCESS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已支付，不能重复创建支付单");
            }
            if (existing.getStatus() == PaymentStatus.PENDING) {
                if (existing.getChannel() != request.channel()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已有其他渠道的有效支付单，请先关闭原支付单");
                }
                log.info("Reusing pending payment {} for order {}", existing.getPayNo(), request.orderNo());
                return toResponse(existing);
            }
        }

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
        paymentOrder.setUserId(userId);
        paymentOrder.setChannel(request.channel());
        // TODO 订单上下文接入前金额仍为占位值，客户端不能传入金额。
        paymentOrder.setAmount(0L);
        paymentOrder.setStatus(PaymentStatus.PENDING);
        paymentOrder.setChannelTradeNo(cashierToken);
        paymentOrder.setExpireAt(LocalDateTime.ofInstant(now.plus(30, ChronoUnit.MINUTES), ZoneOffset.UTC));
        paymentOrder.setPaidAt(null);
        paymentOrder.setCreatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));

        paymentOrderMapper.insert(paymentOrder);
        return toResponse(paymentOrder);
    }

    /** 查询支付单，不存在时返回null，供内部RPC定位使用。 */
    public PaymentResponses.Payment find(String payNo) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectByPayNo(payNo);
        return paymentOrder == null ? null : toResponse(paymentOrder);
    }

    /** 查询支付单，不存在时返回404。 */
    public PaymentResponses.Payment get(String payNo) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectByPayNo(payNo);
        if (paymentOrder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付单不存在");
        }
        return toResponse(paymentOrder);
    }

    /** 买家查询自己的支付单；不属于当前用户时按404隐藏资源是否存在。 */
    public PaymentResponses.Payment getForBuyer(String payNo, Long userId) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectByPayNo(payNo);
        if (paymentOrder == null || !Objects.equals(paymentOrder.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付单不存在");
        }
        return toResponse(paymentOrder);
    }

    /** 模拟支付完成，仅演示渠道可用；成功单幂等返回，其他终态拒绝。 */
    @Transactional
    public PaymentResponses.Payment completeMock(String payNo, Long userId) {
        PaymentOrder paymentOrder = paymentOrderMapper.selectByPayNo(payNo);
        if (paymentOrder == null || !Objects.equals(paymentOrder.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付单不存在");
        }
        if (paymentOrder.getChannel() != PaymentChannel.MOCK && paymentOrder.getChannel() != PaymentChannel.ALIPAY) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅模拟支付渠道支持此操作");
        }
        if (paymentOrder.getStatus() == PaymentStatus.SUCCESS) {
            return toResponse(paymentOrder);
        }
        if (paymentOrder.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付单当前不可完成");
        }
        LocalDateTime paidAt = LocalDateTime.now(ZoneOffset.UTC);
        if (paymentOrderMapper.markPaymentSucceeded(
                paymentOrder.getPayNo(), paymentOrder.getChannelTradeNo(), paidAt) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付单状态已变化，请重试");
        }
        paymentOrder.setStatus(PaymentStatus.SUCCESS);
        paymentOrder.setPaidAt(paidAt);
        return toResponse(paymentOrder);
    }

    private static PaymentResponses.Payment toResponse(PaymentOrder paymentOrder) {
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

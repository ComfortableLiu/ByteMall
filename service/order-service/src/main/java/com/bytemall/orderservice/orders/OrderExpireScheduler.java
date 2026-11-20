package com.bytemall.orderservice.orders;

import com.bytemall.orderservice.mapper.OrderMainMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时订单调度器：每轮限量扫描过期的待支付订单，逐单调用关单逻辑独立提交。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireScheduler {
    private static final int MAX_BATCH_SIZE = 500;

    private final OrderMainMapper orderMainMapper;
    private final OrderService orderService;

    @Value("${order.expire-scan.batch-size:100}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${order.expire-scan.fixed-delay-ms:60000}",
            initialDelayString = "${order.expire-scan.initial-delay-ms:60000}"
    )
    public void cancelExpiredOrders() {
        int limit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        List<String> orderNos;
        try {
            orderNos = orderMainMapper.selectExpiredOrderNos(limit);
        } catch (Exception exception) {
            log.error("扫描超时订单失败", exception);
            return;
        }

        for (String orderNo : orderNos) {
            try {
                orderService.expireOverdueOrder(orderNo);
            } catch (Exception exception) {
                log.error("超时订单{}自动关单失败", orderNo, exception);
            }
        }
    }
}

package com.bytemall.stockservice.reservation;

import com.bytemall.stockservice.mapper.StockReservationMapper;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 独立调度Bean：每轮限量扫描，每张预扣单通过公共release方法开启独立事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutScheduler {
    private static final int MAX_BATCH_SIZE = 500;

    private final StockReservationMapper reservationMapper;
    private final ReservationService reservationService;

    @Value("${stock.reservation.timeout-scan.batch-size:100}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${stock.reservation.timeout-scan.fixed-delay-ms:60000}",
            initialDelayString = "${stock.reservation.timeout-scan.initial-delay-ms:60000}"
    )
    public void releaseExpiredReservations() {
        int limit = Math.max(1, Math.min(batchSize, MAX_BATCH_SIZE));
        List<String> reservationNos;
        try {
            reservationNos = reservationMapper.selectExpiredReservationNos(limit);
        } catch (Exception exception) {
            log.error("扫描超时库存预扣单失败", exception);
            return;
        }

        for (String reservationNo : reservationNos) {
            try {
                reservationService.release(reservationNo, "TIMEOUT");
            } catch (ReservationException exception) {
                if (exception.getCode() == Status.Code.NOT_FOUND) {
                    log.debug("超时预扣单已不存在: {}", reservationNo);
                } else {
                    log.warn("超时预扣单未释放，code={}, reservationNo={}, message={}",
                            exception.getCode(), reservationNo, exception.getMessage());
                }
            } catch (Exception exception) {
                log.error("释放超时预扣单异常: {}", reservationNo, exception);
            }
        }
    }
}

package com.bytemall.stockservice.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalService {

    /**
     * 创建库存单
     * @param createStocksReservationDto 创建库存单请求
     * @return 库存现状
     */
    public InternalResponses.StockResponse createStockReservation(CreateStocksReservationDto createStocksReservationDto) {
        log.info("Creating stock reservation for {}", createStocksReservationDto);
        return new InternalResponses.StockResponse(
                "reservationNo",
                "orderNo",
                LocalDateTime.now(),
                createStocksReservationDto.items()
        );
    }

    /**
     * 确认库存单
     * @param reservationNo 库存单号
     * @return 库存现状
     */
    public InternalResponses.StockResponse confirm(String reservationNo) {
        log.info("Confirming stock reservation {}", reservationNo);
        return new InternalResponses.StockResponse(
                "reservationNo",
                "orderNo",
                LocalDateTime.now(),
                null
        );
    }
    /**
     * 释放库存单
     * @param reservationNo 库存单号
     * @return 库存现状
     */
    public InternalResponses.StockResponse release(String reservationNo) {
        log.info("Releasing stock reservation {}", reservationNo);
        return new InternalResponses.StockResponse(
                "reservationNo",
                "orderNo",
                LocalDateTime.now(),
                null
        );
    }
}

package com.bytemall.stockservice.internal;

import com.bytemall.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/internal")
@RequiredArgsConstructor
public class InternalController {
    private final InternalService internalService;

    /**
     * 创建库存扣除单，即锁单
     *
     * @param createStocksDto 创建库存扣除单请求
     * @return 库存现状
     */
    @PostMapping("/stocks/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InternalResponses.StockResponse> reservations(@RequestBody CreateStocksReservationDto createStocksDto) {
        return ApiResponse.ok(internalService.createStockReservation(createStocksDto));
    }

    /**
     * 确认库存扣除单
     *
     * @param reservationNo 库存单号
     * @return 库存现状
     */
    @PostMapping("/stocks/reservations/{reservationNo}/confirm")
    public ApiResponse<InternalResponses.StockResponse> confirm(@PathVariable String reservationNo) {
        return ApiResponse.ok(internalService.confirm(reservationNo));
    }

    /**
     * 释放库存扣除单
     *
     * @param reservationNo 库存单号
     * @return 库存现状
     */
    @PostMapping("/stocks/reservations/{reservationNo}/release")
    public ApiResponse<InternalResponses.StockResponse> release(@PathVariable String reservationNo) {
        return ApiResponse.ok(internalService.release(reservationNo));
    }
}

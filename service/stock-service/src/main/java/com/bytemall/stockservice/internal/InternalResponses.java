package com.bytemall.stockservice.internal;

import java.time.LocalDateTime;
import java.util.List;

public class InternalResponses {
    public static record StockResponse(
            String reservationNo,
            String orderNo,
            LocalDateTime expireAt,
            List<CreateStocksReservationDto.Item> items
    ) {
    }
}

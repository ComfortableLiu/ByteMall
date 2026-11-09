package com.bytemail.stockservice.internal;

import java.sql.Timestamp;
import java.util.List;

public class InternalResponses {
    public static record StockResponse(
            String reservationNo,
            String orderNo,
            Timestamp expireAt,
            List<CreateStocksReservationDto.Item> items
    ) {
    }
}

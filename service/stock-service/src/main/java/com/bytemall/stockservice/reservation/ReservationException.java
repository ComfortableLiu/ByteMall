package com.bytemall.stockservice.reservation;

import io.grpc.Status;
import lombok.Getter;

@Getter
public class ReservationException extends RuntimeException {
    private final Status.Code code;

    public ReservationException(Status.Code code, String message) {
        super(message);
        this.code = code;
    }
}

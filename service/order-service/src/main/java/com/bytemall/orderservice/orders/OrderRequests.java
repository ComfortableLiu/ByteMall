package com.bytemall.orderservice.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class OrderRequests {

    private OrderRequests() {
    }

    public record CreateOrder(
            @NotBlank String addressId,
            @NotEmpty List<@NotNull @Valid CreateOrderItem> items,
            @Size(max = 200) String remark
    ) {
    }

    public record CreateOrderItem(
            @NotBlank String skuId,
            @NotNull @Min(1) @Max(999) Integer quantity
    ) {
    }

    public record CancelOrder(
            @Size(max = 200) String reason
    ) {
    }
}

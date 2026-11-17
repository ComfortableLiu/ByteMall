package com.bytemall.cartservice.cart;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class CartRequests {

    private CartRequests() {
    }

    public record AddItem(
            @NotBlank(message = "skuId不能为空") String skuId,
            @NotNull(message = "quantity不能为空")
            @Min(value = 1, message = "quantity不能小于1")
            @Max(value = 999, message = "quantity不能大于999") Integer quantity
    ) {
    }

    public record UpdateItem(
            @Min(value = 1, message = "quantity不能小于1")
            @Max(value = 999, message = "quantity不能大于999") Integer quantity,
            Boolean selected
    ) {
        // 两个字段均可选，但不能同时缺省或显式传null。
        @AssertTrue(message = "quantity和selected至少需要提供一项")
        public boolean isValid() {
            return quantity != null || selected != null;
        }
    }

    public record UpdateSelection(
            @NotNull(message = "skuIds不能为空") List<@NotBlank(message = "skuId不能为空") String> skuIds,
            @NotNull(message = "selected不能为空") Boolean selected
    ) {
    }
}

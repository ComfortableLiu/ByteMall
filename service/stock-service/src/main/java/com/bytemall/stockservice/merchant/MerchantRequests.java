package com.bytemall.stockservice.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class MerchantRequests {

    public record AdjustStockRequest(
            @NotNull(message = "availableDelta不能为空")
            Integer availableDelta,
            @NotBlank(message = "reason不能为空")
            @Size(max = 200, message = "reason长度不能超过200个字符")
            String reason,
            @NotNull(message = "version不能为空")
            @PositiveOrZero(message = "version不能小于0")
            Long version
    ) {
    }
}

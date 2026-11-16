package com.bytemall.productservice.merchant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import com.bytemall.productservice.merchant.CreateProductRequest.ImageRequest;

public record UpdateProductRequest(
        @NotBlank @Size(min = 2, max = 120) String title,
        @Size(max = 200) String subtitle,
        @NotBlank @Pattern(regexp = "[1-9][0-9]*") String categoryId,
        @NotBlank @Size(max = 60) String brand,
        @NotNull String descriptionHtml,
        @NotNull List<@NotNull @Valid ImageRequest> images,
        @NotEmpty List<@NotNull @Valid SkuUpdate> skus,
        @NotNull @PositiveOrZero Long version
) {
    public record SkuUpdate(
            @Pattern(regexp = "[1-9][0-9]*") String skuId,
            @NotBlank @Size(min = 2, max = 64) String skuCode,
            @NotNull Map<@NotBlank String, @NotNull String> specs,
            @NotNull @PositiveOrZero Long price,
            @PositiveOrZero Long originalPrice,
            @NotNull Boolean enabled
    ) {}
}
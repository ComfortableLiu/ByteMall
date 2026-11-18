package com.bytemall.stockservice.merchant;

import com.bytemall.common.auth.CurrentUserId;
import com.bytemall.common.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/merchant")
public class MerchantController {
    private final MerchantService merchantService;

    @GetMapping({"/stocks", "/stock"})
    public ApiResponse<List<MerchantResponses.MerchantStockItem>> listStocks(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "false") boolean lowStock
    ) {
        return ApiResponse.ok(merchantService.listStocks(userId, lowStock));
    }

    @PatchMapping({"/stocks/{skuId}", "/stock/{skuId}"})
    public ApiResponse<MerchantResponses.MerchantStockItem> adjustStock(
            @CurrentUserId Long userId,
            @PathVariable String skuId,
            @Valid @RequestBody MerchantRequests.AdjustStockRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId
    ) {
        return ApiResponse.ok(merchantService.adjustStock(userId, skuId, request, requestId));
    }
}

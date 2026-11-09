package com.bytemail.stockservice.merchant;

import com.bytemail.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/merchant")
public class MerchantController {
    private final MerchantService merchantService;

    @GetMapping("/stock")
    // TODO 这里需要登录态判断用户
    public ApiResponse<MerchantResponses.Stock> stock() {
        return ApiResponse.ok(merchantService.stock());
    }

    @PatchMapping("/stock/{skuId}")
    // TODO 这里需要登录态判断用户
    public ApiResponse<MerchantResponses.Stock> stockBySkuId(@PathVariable String skuId, @RequestBody Integer availableDelta) {
        return ApiResponse.ok(merchantService.stockBySkuId(skuId, availableDelta));
    }
}

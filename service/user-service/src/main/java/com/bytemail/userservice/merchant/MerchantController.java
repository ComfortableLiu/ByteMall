package com.bytemail.userservice.merchant;

import com.bytemall.common.auth.CurrentUserId;
import com.bytemall.common.model.dto.ApiResponse;
import com.bytemail.userservice.merchants.MerchantShopRequest;
import com.bytemail.userservice.merchants.MerchantShopResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @GetMapping("/shop")
    public ApiResponse<MerchantShopResponse> getMerchantShop(@CurrentUserId Long userId) {
        return ApiResponse.ok(merchantService.getMerchantShop(userId));
    }

    @PatchMapping("/shop")
    public ApiResponse<MerchantShopResponse> updateMerchantShop(
            @CurrentUserId Long userId,
            @Valid @RequestBody MerchantShopRequest request
    ) {
        log.info("Merchant shop updated by user: {}", userId);
        return ApiResponse.ok(merchantService.updateMerchantShop(userId, request));
    }
}

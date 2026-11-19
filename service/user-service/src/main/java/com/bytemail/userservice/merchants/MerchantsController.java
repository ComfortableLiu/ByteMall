package com.bytemail.userservice.merchants;

import com.bytemall.common.auth.CurrentUserId;
import com.bytemall.common.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/merchants")
@RequiredArgsConstructor
public class MerchantsController {

    private final MerchantsService merchantsService;

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MerchantApplicationResponse> applyMerchant(
            @CurrentUserId Long userId,
            @Valid @RequestBody MerchantApplicationRequest request
    ) {
        log.info("Merchant application submitted by user: {}", userId);
        return ApiResponse.ok(merchantsService.applyMerchant(userId, request));
    }

    @GetMapping("/applications/current")
    public ApiResponse<MerchantApplicationResponse> getCurrentMerchantApplication(@CurrentUserId Long userId) {
        return ApiResponse.ok(merchantsService.getCurrentMerchantApplication(userId));
    }

    /** 一键审核通过并开通店铺，重复审核幂等。 */
    @PostMapping("/applications/{applicationId}/approve")
    public ApiResponse<MerchantApplicationResponse> approveApplication(
            @CurrentUserId Long userId,
            @PathVariable Long applicationId
    ) {
        log.info("Merchant application {} approved by user: {}", applicationId, userId);
        return ApiResponse.ok(merchantsService.approveApplication(userId, applicationId));
    }

    /** 一键驳回入驻申请，需写明驳回原因。 */
    @PostMapping("/applications/{applicationId}/reject")
    public ApiResponse<MerchantApplicationResponse> rejectApplication(
            @CurrentUserId Long userId,
            @PathVariable Long applicationId,
            @Valid @RequestBody MerchantApplicationRejectRequest request
    ) {
        log.info("Merchant application {} rejected by user: {}", applicationId, userId);
        return ApiResponse.ok(merchantsService.rejectApplication(userId, applicationId, request.reason()));
    }
}

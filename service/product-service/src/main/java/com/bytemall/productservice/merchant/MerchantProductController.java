package com.bytemall.productservice.merchant;

import com.bytemall.common.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/merchant/products")
@RequiredArgsConstructor
public class MerchantProductController {

    private final MerchantProductService merchantProductService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponses.CreatedProduct> createDraft(
            @Valid @RequestBody CreateProductRequest request
//            Authentication authentication
    ) {
//        if (authentication == null || !authentication.isAuthenticated()) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
//        }
//
//        boolean merchantRole = authentication.getAuthorities().stream()
//                .anyMatch(authority -> "ROLE_MERCHANT".equals(authority.getAuthority()));
//
//        if (!merchantRole
//                || !(authentication.getPrincipal() instanceof MerchantPrincipal merchant)) {
//            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
//        }

//        Long spuId = productService.createDraft(merchant.merchantId(), request);
//
//        return ApiResponse.ok(new ProductResponses.CreatedProduct(spuId.toString()));
//        测试先写死1L
        Long spuId = merchantProductService.createDraft(1L, request);
        return ApiResponse.ok(new ProductResponses.CreatedProduct(spuId.toString()));
    }

    public record MerchantPrincipal(Long merchantId) {
    }
}

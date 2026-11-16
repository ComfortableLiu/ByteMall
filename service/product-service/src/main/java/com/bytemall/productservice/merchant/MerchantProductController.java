package com.bytemall.productservice.merchant;

import com.bytemall.common.model.dto.ApiResponse;
import com.bytemall.productservice.products.ProductController;
import com.bytemall.productservice.products.ProductResponses;
import io.grpc.StatusRuntimeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/v1/merchant/products")
@RequiredArgsConstructor
public class MerchantProductController {

    private final MerchantProductService merchantProductService;
    private final MerchantProductImageUploadService merchantProductImageUploadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MerchantProductResponses.CreatedProduct> createDraft(
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
        return ApiResponse.ok(new MerchantProductResponses.CreatedProduct(spuId.toString()));
    }

    public record MerchantPrincipal(Long merchantId) {
    }

    @GetMapping
    public ApiResponse<ProductResponses.ProductPage> listMerchantProducts(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "size", defaultValue = "1") @Min(1) int size,
            @RequestParam(name = "status", required = false) String status
    ) {
        return ApiResponse.ok(merchantProductService.getMerchantProductList(1L, page, size, status));
    }

    @GetMapping("/{spuId}")
    public ResponseEntity<?> getProductDetail(@PathVariable("spuId") String rawSpuId) {
        long spuId;
        try {
            spuId = Long.parseLong(rawSpuId);
        } catch (NumberFormatException e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_SPU_ID", "商品 ID 必须是正整数");
        }
        if (spuId <= 0) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_SPU_ID", "商品 ID 必须是正整数");
        }

        try {
            var product = merchantProductService.getProductDetail(1L, spuId);
            if (product.isEmpty()) {
                return error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品不存在");
            }
            return ResponseEntity.ok(ApiResponse.ok(product.get()));
        } catch (StatusRuntimeException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "STOCK_UNAVAILABLE", "暂时无法查询商品库存");
        }
    }

    private static ResponseEntity<ProductController.ProductError> error(
            HttpStatus status, String code, String message
    ) {
        return ResponseEntity.status(status)
                .body(new ProductController.ProductError(code, message, UUID.randomUUID().toString()));
    }


    @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//这条接口接收 Content-Type: multipart/form-data 的请求。
    @ResponseStatus(HttpStatus.CREATED)//告诉 Spring，这个 Controller 方法正常返回时，把 HTTP 状态码设为 201 Created
    public ApiResponse<MerchantProductResponses.ImageUploadResult> uploadImageToOSS(
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.ok(merchantProductImageUploadService.UploadImage(file));
    }


}

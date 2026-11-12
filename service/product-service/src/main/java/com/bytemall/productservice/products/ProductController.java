package com.bytemall.productservice.products;

import com.bytemall.common.model.dto.ApiResponse;
import io.grpc.StatusRuntimeException;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @GetMapping
    public ApiResponse<ProductResponses.ProductPage> productList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "1") @Min(1) int size
    ) {
        return ApiResponse.ok(productService.getProductsList(page, size));
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
            var product = productService.getProductDetail(spuId);
            if (product.isEmpty()) {
                return error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品不存在");
            }
            return ResponseEntity.ok(ApiResponse.ok(product.get()));
        } catch (StatusRuntimeException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "STOCK_UNAVAILABLE", "暂时无法查询商品库存");
        }
    }

    private static ResponseEntity<ProductError> error(
            HttpStatus status, String code, String message
    ) {
        return ResponseEntity.status(status)
                .body(new ProductError(code, message, UUID.randomUUID().toString()));
    }

    public record ProductError(String code, String message, String requestId) {
    }
}



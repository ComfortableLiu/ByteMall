package com.bytemall.orderservice.merchant;

import com.bytemall.common.auth.CurrentUserId;
import com.bytemall.common.model.dto.ApiResponse;
import com.bytemall.orderservice.orders.OrderResponses;
import com.bytemall.orderservice.orders.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant/orders")
public class MerchantOrderController {

    private final MerchantOrderService merchantOrderService;

    public MerchantOrderController(MerchantOrderService merchantOrderService) {
        this.merchantOrderService = merchantOrderService;
    }

    @GetMapping
    public ApiResponse<OrderResponses.OrderPage> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(merchantOrderService.list(userId, status, keyword, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponses.Order> get(
            @CurrentUserId Long userId,
            @PathVariable String orderNo
    ) {
        return ApiResponse.ok(merchantOrderService.get(userId, orderNo));
    }

    @PostMapping("/{orderNo}/shipments")
    public ApiResponse<OrderResponses.Order> ship(
            @CurrentUserId Long userId,
            @PathVariable String orderNo,
            @Valid @RequestBody MerchantOrderRequests.Shipment request
    ) {
        return ApiResponse.ok(merchantOrderService.ship(userId, orderNo, request));
    }
}

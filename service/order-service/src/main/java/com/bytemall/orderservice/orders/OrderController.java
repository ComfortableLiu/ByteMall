package com.bytemall.orderservice.orders;

import com.bytemall.common.auth.CurrentUserId;
import com.bytemall.common.model.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponses.Order> create(
            @CurrentUserId Long userId,
            // 幂等键用于防止客户端重试造成重复下单。
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 64) String idempotencyKey,
            @Valid @RequestBody OrderRequests.CreateOrder request
    ) {
        return ApiResponse.ok(orderService.create(userId, idempotencyKey, request));
    }

    @GetMapping
    public ApiResponse<OrderResponses.OrderPage> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(orderService.list(userId, status, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponses.Order> get(
            @CurrentUserId Long userId,
            @PathVariable String orderNo
    ) {
        return ApiResponse.ok(orderService.get(userId, orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderResponses.Order> cancel(
            @CurrentUserId Long userId,
            @PathVariable String orderNo,
            @Valid @RequestBody(required = false) OrderRequests.CancelOrder request
    ) {
        return ApiResponse.ok(orderService.cancel(userId, orderNo, request));
    }

    @PostMapping("/{orderNo}/confirm")
    public ApiResponse<OrderResponses.Order> confirm(
            @CurrentUserId Long userId,
            @PathVariable String orderNo
    ) {
        return ApiResponse.ok(orderService.confirm(userId, orderNo));
    }
}

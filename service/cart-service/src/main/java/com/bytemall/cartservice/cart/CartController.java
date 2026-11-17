package com.bytemall.cartservice.cart;

import com.bytemall.common.auth.CurrentUserId;
import com.bytemall.common.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponses.Cart> getCart(@CurrentUserId Long userId) {
        return ApiResponse.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CartResponses.Cart> addItem(
            @Valid @RequestBody CartRequests.AddItem request,
            @CurrentUserId Long userId
    ) {
        return ApiResponse.ok(cartService.addItem(userId, request));
    }

    @PatchMapping("/items/{skuId}")
    public ApiResponse<CartResponses.Cart> updateItem(
            @PathVariable("skuId") String skuId,
            @Valid @RequestBody CartRequests.UpdateItem request,
            @CurrentUserId Long userId
    ) {
        return ApiResponse.ok(cartService.updateItem(userId, skuId, request));
    }

    @DeleteMapping("/items/{skuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @PathVariable("skuId") String skuId,
            @CurrentUserId Long userId
    ) {
        cartService.deleteItem(userId, skuId);
    }

    @PostMapping("/selection")
    public ApiResponse<CartResponses.Cart> updateSelection(
            @Valid @RequestBody CartRequests.UpdateSelection request,
            @CurrentUserId Long userId
    ) {
        return ApiResponse.ok(cartService.updateSelection(userId, request));
    }
}

package com.bytemail.userservice.user;

import com.bytemail.userservice.user.dto.UserResponse;
import com.bytemall.common.auth.CurrentUserId;
import com.bytemall.common.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/users")
public class UserController {
    private final UserService userService;
    private final AddressService addressService;

    @GetMapping("/me")
    public ApiResponse<UserResponse.User> getCurrentUser(
            @CurrentUserId Long userId
    ) {
        return ApiResponse.ok(userService.getCurrentUser(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<UserResponse.User> updateCurrentUser(
            @CurrentUserId Long userId,
            @Valid @RequestBody UserResponse.User user
    ) {
        return ApiResponse.ok(userService.updateCurrentUser(userId, user));
    }

    @GetMapping("/me/addresses")
    public ApiResponse<List<UserResponse.Address>> meAddresses(@CurrentUserId Long userId) {
        return ApiResponse.ok(addressService.meAddresses(userId));
    }

    @PostMapping("/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse.Address> createAddress(
            @CurrentUserId Long userId,
            @Valid @RequestBody UserResponse.Address address
    ) {
        return ApiResponse.ok(addressService.createAddress(userId, address));
    }

    @PatchMapping("/me/addresses/{addressId}")
    public ApiResponse<UserResponse.Address> updateAddress(
            @CurrentUserId Long userId,
            @PathVariable Long addressId,
            @Valid @RequestBody UserResponse.Address address
    ) {
        return ApiResponse.ok(addressService.updateAddress(userId, addressId, address));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteAddress(
            @CurrentUserId Long userId,
            @PathVariable Long addressId
    ) {
        addressService.deleteAddress(userId, addressId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/me/addresses/{addressId}/default")
    public ApiResponse<UserResponse.Address> setDefaultAddress(
            @CurrentUserId Long userId,
            @PathVariable Long addressId
    ) {
        return ApiResponse.ok(addressService.setDefaultAddress(userId, addressId));
    }
}

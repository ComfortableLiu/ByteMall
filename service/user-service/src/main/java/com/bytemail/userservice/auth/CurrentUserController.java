package com.bytemail.userservice.auth;

import com.bytemall.common.auth.CurrentUserId;
import com.bytemall.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/** 当前登录用户查询接口。 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class CurrentUserController {

    private final AuthService authService;

    @GetMapping("/me")
    public ApiResponse<AuthResponse.User> me(@CurrentUserId Long userId) {
        return ApiResponse.ok(authService.currentUser(userId));
    }

    @GetMapping("/me/addresses")
    public ApiResponse<List> meAddresses(@CurrentUserId Long userId) {
        return ApiResponse.ok(new ArrayList());
    }
}

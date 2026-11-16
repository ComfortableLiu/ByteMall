package com.bytemail.userservice.auth;

import jakarta.validation.constraints.NotBlank;

/** 刷新令牌请求，访问令牌不能当作刷新令牌使用。 */
public record RefreshTokenRequest(
        @NotBlank(message = "刷新令牌不能为空") String refreshToken
) {
}

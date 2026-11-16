package com.bytemail.userservice.auth;

import java.util.List;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        int expiresIn,
        String tokenType,
        User user
) {
    /** 与前端User领域模型保持一致，merchantId为空表示没有有效商家身份。 */
    public record User(
            String userId,
            String nickname,
            String avatarUrl,
            List<String> roles,
            String merchantId
    ) {
    }
}

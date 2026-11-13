package com.bytemail.userservice.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        int expiresIn,
        String tokenType,
        User user
) {
    public record User(
            long userId,
            String nickname,
            String avatarUrl,
            String roles,
            long merchantId
    ) {
    }
}

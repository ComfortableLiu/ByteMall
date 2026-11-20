package com.bytemail.userservice.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        int expiresIn,
        String tokenType,
        User user
) {
    /** merchantId为空或-1表示没有有效商家身份。 */
    public record User(
            String userId,
            String nickname,
            String avatarUrl,
            String merchantId
    ) {
    }
}

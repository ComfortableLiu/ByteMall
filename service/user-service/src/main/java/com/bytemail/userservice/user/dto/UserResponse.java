package com.bytemail.userservice.user.dto;

public record UserResponse(
) {
    /** merchantId为空或-1表示没有有效商家身份。 */
    public record User(
            long userId,
            String nickname,
            String avatarUrl,
            String merchantId
    ){

    }
    public record Address(
            String receiverName,
            String receiverMobile,
            String province,
            String city,
            String district,
            String detailAddress,
            String postalCode,
            boolean isDefault,
            long addressId,
            long userId
    ){
    }
}

package com.bytemail.userservice.user.dto;

public record UserResponse(
) {
    public record User(
            long userId,
            String nickname,
            String avatarUrl,
            long merchantId
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

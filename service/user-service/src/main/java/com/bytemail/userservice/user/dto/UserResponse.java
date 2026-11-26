package com.bytemail.userservice.user.dto;

public record UserResponse(
) {
    /** merchantId为空或-1表示没有有效商家身份。 */
    /** 对外ID统一序列化为字符串，前端按字符串比较。 */
    public record User(
            String userId,
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
            String addressId,
            String userId
    ){
    }
}

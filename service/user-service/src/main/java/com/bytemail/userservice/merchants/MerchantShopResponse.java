package com.bytemail.userservice.merchants;

public record MerchantShopResponse(
        String merchantId,
        String shopName,
        String logoUrl,
        String contactName,
        String contactMobile,
        String status
) {
}

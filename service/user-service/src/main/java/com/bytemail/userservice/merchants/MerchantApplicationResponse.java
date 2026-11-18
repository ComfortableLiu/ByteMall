package com.bytemail.userservice.merchants;

import java.time.LocalDateTime;

/** 商家入驻申请回显。licenseNo 属敏感信息，不得完整回显。 */
public record MerchantApplicationResponse(
        String applicationId,
        String userId,
        String shopName,
        String contactName,
        String contactMobile,
        String licenseNo,
        String status,
        String rejectReason,
        LocalDateTime createdAt
) {
}

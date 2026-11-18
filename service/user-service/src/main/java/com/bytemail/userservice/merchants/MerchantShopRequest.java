package com.bytemail.userservice.merchants;

import jakarta.validation.constraints.Size;

/** 店铺资料更新入参，全部字段可选，只修改资料不修改审核状态。 */
public record MerchantShopRequest(
        @Size(min = 2, max = 80, message = "店铺名称长度必须在2到80个字符之间")
        String shopName,

        String logoUrl,

        @Size(min = 1, max = 60, message = "联系人长度必须在1到60个字符之间")
        String contactName,

        @Size(min = 5, max = 20, message = "联系电话长度必须在5到20个字符之间")
        String contactMobile
) {
}

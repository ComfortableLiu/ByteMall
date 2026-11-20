package com.bytemail.userservice.merchants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantApplicationRequest(
        @NotBlank(message = "店铺名称不能为空")
        @Size(min = 2, max = 80, message = "店铺名称长度必须在2到80个字符之间")
        String shopName,

        @NotBlank(message = "联系人不能为空")
        @Size(min = 1, max = 60, message = "联系人长度必须在1到60个字符之间")
        String contactName,

        @NotBlank(message = "联系电话不能为空")
        @Size(min = 5, max = 20, message = "联系电话长度必须在5到20个字符之间")
        String contactMobile,

        @NotBlank(message = "资质证件编号不能为空")
        @Size(min = 5, max = 64, message = "资质证件编号长度必须在5到64个字符之间")
        String licenseNo
) {
}

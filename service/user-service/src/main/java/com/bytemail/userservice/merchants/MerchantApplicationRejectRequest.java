package com.bytemail.userservice.merchants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 入驻申请驳回入参，驳回必须写明原因。 */
public record MerchantApplicationRejectRequest(
        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 200, message = "驳回原因长度不能超过200个字符")
        String reason
) {
}

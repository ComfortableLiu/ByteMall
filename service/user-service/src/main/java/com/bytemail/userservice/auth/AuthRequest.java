package com.bytemail.userservice.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
        @NotBlank(message = "手机号不能为空") String mobile,
        @NotBlank(message = "密码不能为空") String password
) {
}

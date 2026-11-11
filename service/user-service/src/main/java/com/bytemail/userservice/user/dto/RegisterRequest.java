package com.bytemail.userservice.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest{

    @NotBlank(message = "手机号不能为空")
    @Size(min = 11, max = 13, message = "手机号应该在11位到13位")
    private String mobile;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 13, message = "手机号应该在6位到13位")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 60, message = "昵称长度必须在1到60个字符之间")
    private String nickname;
}

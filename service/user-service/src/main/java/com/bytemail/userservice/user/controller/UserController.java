package com.bytemail.userservice.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.dto.RegisterRequest;
import com.bytemail.userservice.user.dto.UserResponse;
import com.bytemail.userservice.user.entity.UserAccount;
import com.bytemail.userservice.user.mapper.UserAccountMapper;
import com.bytemail.userservice.user.service.UserService;
import com.bytemall.common.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    private final UserAccountMapper userAccountMapper;

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getById (
            @PathVariable("id") Long id
    ) {
        UserAccount user = userService.getById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }

        UserResponse response = new UserResponse(
          user.getId().toString(),user.getNickname(), user.getAvatarUrl()
        );

        return ApiResponse.ok(response);
    }

    @PostMapping("/create/user")
    public ApiResponse<UserAccount> createUser (
            @Valid @RequestBody RegisterRequest request
    ) {
        UserAccount response = userService.createUser(request);
        return ApiResponse.ok(response);
    }
}

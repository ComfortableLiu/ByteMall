package com.bytemail.userservice.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.dto.RegisterRequest;
import com.bytemail.userservice.user.entity.UserAccount;
import com.bytemail.userservice.user.mapper.UserAccountMapper;
import com.bytemall.common.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAccount getById(Long id) {
        return userAccountMapper.selectById(id);
    }

    public UserAccount createUser (
            RegisterRequest request
    ) {
        log.info(request.toString());
        // 1. 获取手机号，去掉两端空格
        String mobile = request.getMobile().trim();

        // 2. 检查手机号是否已经注册
        QueryWrapper<UserAccount> query = new QueryWrapper<>();
        query.eq("mobile", mobile);

        if (userAccountMapper.selectCount(query) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "手机号已经注册"
            );
        }

        // 3. BCrypt 最多处理 72 字节，不能直接截断密码
        String password = request.getPassword();

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "密码的 UTF-8 编码长度不能超过72字节"
            );
        }

        UserAccount userAccount = new UserAccount();

        userAccount.setNickname(request.getNickname().trim());
        userAccount.setMobile(mobile);
        userAccount.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        // 账号初始状态由服务端决定
        userAccount.setStatus("ACTIVE");
        userAccount.setTokenVersion(0L);

        try {
            userAccountMapper.insert(userAccount);
        } catch (Exception e) {
            // 两个请求可能同时通过第2步检查，
            // 最终仍需要数据库唯一索引阻止重复手机号
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "手机号已经注册"
            );
        }

        return userAccount;
    }
}

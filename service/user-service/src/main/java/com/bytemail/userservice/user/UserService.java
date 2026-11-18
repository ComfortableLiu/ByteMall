package com.bytemail.userservice.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.dto.RegisterRequest;
import com.bytemail.userservice.user.dto.UserResponse;
import com.bytemail.userservice.user.entity.Merchant;
import com.bytemail.userservice.user.entity.UserAccount;
import com.bytemail.userservice.merchants.MerchantMapper;
import com.bytemail.userservice.user.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserAccountMapper userAccountMapper;
    private final MerchantMapper merchantMapper;
    private final PasswordEncoder passwordEncoder;

    public UserResponse.User getCurrentUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        UserAccount userAccount = userAccountMapper.selectById(userId);
        Merchant merchant = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("owner_user_id", userId));
        if (userAccount != null) {
            return new UserResponse.User(
                    userAccount.getId(),
                    userAccount.getNickname(),
                    userAccount.getAvatarUrl(),
                    merchant == null ? "-1" : String.valueOf(merchant.getId())
            );
        }
        return null;
    }

    @Transactional
    public UserResponse.User updateCurrentUser(Long userId, UserResponse.User user) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        UserAccount userAccount = userAccountMapper.selectById(userId);
        Merchant merchant = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("owner_user_id", userId));
        userAccount.setAvatarUrl(user.avatarUrl());
        userAccount.setNickname(user.nickname());
        userAccountMapper.updateById(userAccount);
        return new UserResponse.User(
                userAccount.getId(),
                userAccount.getNickname(),
                userAccount.getAvatarUrl(),
                merchant == null ? "-1" : String.valueOf(merchant.getId())
        );
    }

    public UserAccount createUser(
            RegisterRequest request
    ) {
        // 只记录手机号，不能把包含明文密码的请求对象输出到日志。
        log.info("Registering user with mobile {}", request.getMobile());
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

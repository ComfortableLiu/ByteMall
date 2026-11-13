package com.bytemail.userservice.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.entity.Merchant;
import com.bytemail.userservice.user.entity.UserAccount;
import com.bytemail.userservice.user.mapper.MerchantMapper;
import com.bytemail.userservice.user.mapper.UserAccountMapper;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.util.Date;

import static io.jsonwebtoken.Jwts.SIG.RS256;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // token过期时间
    private static final int TOKEN_EXPIRES_IN = 7 * 24 * 60 * 60;
    // 刷新token过期时间
    private static final int REFRESH_TOKEN_EXPIRES_IN = 30 * 24 * 60 * 60;

    private final UserAccountMapper userAccountMapper;
    private final MerchantMapper merchantMapper;
    private final PasswordEncoder passwordEncoder;
    private final KeyPair jwtKeyPair;

    public AuthResponse login(AuthRequest authRequest) {
        UserAccount userAccount = userAccountMapper.selectOne(new QueryWrapper<UserAccount>().eq("mobile", authRequest.mobile()));
        if (userAccount == null || passwordEncoder.matches(userAccount.getPasswordHash(), authRequest.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在或密码错误");
        }

        Merchant merchant = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("owner_user_id", userAccount.getId()));

        return new AuthResponse(
                createToken(userAccount),
                createRefreshToken(userAccount),
                TOKEN_EXPIRES_IN,
                "Bearer",
                new AuthResponse.User(
                        userAccount.getId(),
                        userAccount.getNickname(),
                        userAccount.getAvatarUrl(),
                        "",
                        merchant != null ? merchant.getId() : 0));
    }

    // 创建token
    public String createToken(UserAccount userAccount) {
        return buildJwt(userAccount, TOKEN_EXPIRES_IN);
    }

    // 创建刷新token
    public String createRefreshToken(UserAccount userAccount) {
        return buildJwt(userAccount, REFRESH_TOKEN_EXPIRES_IN);
    }

    // 用RSA私钥按RS256算法签发JWT
    private String buildJwt(UserAccount userAccount, int expiresInSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userAccount.getId()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInSeconds * 1000L))
                .signWith(jwtKeyPair.getPrivate(), RS256)
                .compact();
    }
}

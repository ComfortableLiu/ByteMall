package com.bytemail.userservice.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bytemail.userservice.user.dto.RegisterRequest;
import com.bytemail.userservice.user.entity.Merchant;
import com.bytemail.userservice.user.entity.UserAccount;
import com.bytemail.userservice.user.mapper.MerchantMapper;
import com.bytemail.userservice.user.mapper.UserAccountMapper;
import com.bytemail.userservice.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.util.Date;
import java.util.List;

import static io.jsonwebtoken.Jwts.SIG.RS256;

/**
 * 自有账号认证服务，负责登录、注册、刷新令牌和注销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // 资源服务离线验签无法即时撤销访问令牌，短有效期将注销后的撤销窗口限制在15分钟内。
    private static final int TOKEN_EXPIRES_IN = 15 * 60;
    private static final int REFRESH_TOKEN_EXPIRES_IN = 30 * 24 * 60 * 60;

    private final UserAccountMapper userAccountMapper;
    private final MerchantMapper merchantMapper;
    private final PasswordEncoder passwordEncoder;
    private final KeyPair jwtKeyPair;
    private final UserService userService;

    /** 使用手机号和BCrypt密码哈希登录，失败时返回统一的401避免账号枚举。 */
    public AuthResponse login(AuthRequest authRequest) {
        if (authRequest.mobile() == null || authRequest.password() == null) {
            throw unauthorized();
        }
        String mobile = authRequest.mobile().trim();
        UserAccount userAccount = userAccountMapper.selectOne(
                new QueryWrapper<UserAccount>().eq("mobile", mobile)
        );
        if (userAccount == null
                || !"ACTIVE".equals(userAccount.getStatus())
                || userAccount.getPasswordHash() == null
                || !passwordEncoder.matches(authRequest.password(), userAccount.getPasswordHash())) {
            throw unauthorized();
        }
        return issueTokens(userAccount);
    }

    /** 注册成功后直接签发访问令牌和刷新令牌。 */
    public AuthResponse register(RegisterRequest request) {
        return issueTokens(userService.createUser(request));
    }

    /** 校验刷新令牌后重新签发两种Token。 */
    public AuthResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(jwtKeyPair.getPublic())
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();
            if (!"REFRESH".equals(claims.get("tokenType", String.class))) {
                throw new IllegalArgumentException("Token type must be REFRESH");
            }
        } catch (JwtException | IllegalArgumentException exception) {
            throw unauthorized();
        }

        long userId = Long.parseLong(claims.getSubject());
        UserAccount userAccount = userAccountMapper.selectById(userId);
        Number tokenVersion = claims.get("tokenVersion", Number.class);
        if (userAccount == null
                || !"ACTIVE".equals(userAccount.getStatus())
                || tokenVersion == null
                || userAccount.getTokenVersion() == null
                || userAccount.getTokenVersion().longValue() != tokenVersion.longValue()) {
            throw unauthorized();
        }

        // TODO 生产环境保存刷新令牌哈希，增加单会话旋转和旧令牌重放检测。
        return issueTokens(userAccount);
    }

    /** 递增令牌版本，使当前用户之前签发的访问和刷新Token全部失效。 */
    public void logout(Long userId) {
        if (userId == null || userAccountMapper.incrementTokenVersion(userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
    }

    /** 返回当前登录用户及有效的商家身份。 */
    public AuthResponse.User currentUser(Long userId) {
        UserAccount userAccount = userAccountMapper.selectById(userId);
        if (userAccount == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        Merchant merchant = findActiveMerchant(userId);
        return userView(userAccount, merchant);
    }

    private AuthResponse issueTokens(UserAccount userAccount) {
        Merchant merchant = findActiveMerchant(userAccount.getId());
        return new AuthResponse(
                createToken(userAccount),
                createRefreshToken(userAccount),
                TOKEN_EXPIRES_IN,
                "Bearer",
                userView(userAccount, merchant)
        );
    }

    private Merchant findActiveMerchant(Long userId) {
        Merchant merchant = merchantMapper.selectOne(
                new QueryWrapper<Merchant>().eq("owner_user_id", userId)
        );
        return merchant != null && "ACTIVE".equals(merchant.getStatus()) ? merchant : null;
    }

    private AuthResponse.User userView(UserAccount userAccount, Merchant merchant) {
        List<String> roles = merchant == null
                ? List.of("BUYER")
                : List.of("BUYER", "MERCHANT");
        return new AuthResponse.User(
                String.valueOf(userAccount.getId()),
                userAccount.getNickname(),
                userAccount.getAvatarUrl(),
                roles,
                merchant == null ? null : String.valueOf(merchant.getId())
        );
    }

    public String createToken(UserAccount userAccount) {
        return buildJwt(userAccount, "ACCESS", TOKEN_EXPIRES_IN);
    }

    public String createRefreshToken(UserAccount userAccount) {
        return buildJwt(userAccount, "REFRESH", REFRESH_TOKEN_EXPIRES_IN);
    }

    /** 使用RSA私钥签发RS256 Token，并写入类型和版本用于后续校验。 */
    private String buildJwt(UserAccount userAccount, String tokenType, int expiresInSeconds) {
        Date now = new Date();
        long tokenVersion = userAccount.getTokenVersion() == null ? 0L : userAccount.getTokenVersion();
        return Jwts.builder()
                .subject(String.valueOf(userAccount.getId()))
                .claim("tokenType", tokenType)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInSeconds * 1000L))
                .signWith(jwtKeyPair.getPrivate(), RS256)
                .compact();
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在、密码错误或令牌无效");
    }
}

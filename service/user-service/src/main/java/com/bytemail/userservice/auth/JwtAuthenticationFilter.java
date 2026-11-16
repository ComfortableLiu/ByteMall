package com.bytemail.userservice.auth;

import com.bytemail.userservice.user.entity.UserAccount;
import com.bytemail.userservice.user.mapper.UserAccountMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Collections;

/**
 * 在Spring Security授权前验证访问Token，并检查用户状态和令牌版本。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final KeyPair jwtKeyPair;
    private final UserAccountMapper userAccountMapper;

    /**
     * 登录、注册和刷新接口公开；注销仍必须经过JWT过滤器。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/refresh")
                || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            // 没有Token时由后续授权规则决定是公开接口还是401。
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            writeUnauthorized(response);
            return;
        }

        try {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            Claims claims = Jwts.parser()
                    .verifyWith(jwtKeyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"ACCESS".equals(claims.get("tokenType", String.class))) {
                throw new IllegalArgumentException("Token type must be ACCESS");
            }

            long userId = Long.parseLong(claims.getSubject());
            UserAccount user = userAccountMapper.selectById(userId);
            Number tokenVersion = claims.get("tokenVersion", Number.class);
            if (user == null
                    || !"ACTIVE".equals(user.getStatus())
                    || tokenVersion == null
                    || user.getTokenVersion() == null
                    || user.getTokenVersion().longValue() != tokenVersion.longValue()) {
                throw new IllegalArgumentException("User or token version is invalid");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            String.valueOf(userId),
                            null,
                            Collections.emptyList()
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
        }
    }

    /** 不使用默认登录页或HTTP Basic质询，统一返回JSON 401。 */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"访问令牌无效或已过期\",\"data\":null,\"success\":false}");
    }
}

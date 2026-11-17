package com.bytemall.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Collections;

/**
 * 资源服务器JWT过滤器：验证签名、有效期和Token类型，再建立Spring Security登录态。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final KeyPair jwtKeyPair;

    public JwtAuthenticationFilter(KeyPair jwtKeyPair) {
        this.jwtKeyPair = jwtKeyPair;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            // 匿名请求是否可访问由后面的接口白名单决定。
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            writeUnauthorized(response);
            return;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtKeyPair.getPublic())
                    .build()
                    .parseSignedClaims(authorization.substring(BEARER_PREFIX.length()).trim())
                    .getPayload();
            if (!"ACCESS".equals(claims.get("tokenType", String.class))) {
                throw new IllegalArgumentException("Token type must be ACCESS");
            }
            long userId = Long.parseLong(claims.getSubject());
            if (userId <= 0) {
                throw new IllegalArgumentException("User ID must be positive");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            String.valueOf(userId), null, Collections.emptyList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
            return;
        }

        // 业务异常必须继续由全局异常处理器处理，不能被误判成Token无效。
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"访问令牌无效或已过期\",\"data\":null,\"success\":false}");
    }
}

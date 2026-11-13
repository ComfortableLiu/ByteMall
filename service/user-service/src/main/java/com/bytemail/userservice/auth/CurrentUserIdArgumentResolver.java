package com.bytemail.userservice.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;

/**
 * 解析带@CurrentUserId注解的方法参数，从Bearer JWT中读取用户ID。
 */
@Component
@RequiredArgsConstructor
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final KeyPair jwtKeyPair;

    /**
     * 仅处理带注解的Long或long参数，其他参数继续交给Spring默认解析器。
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && (parameter.getParameterType() == Long.class
                || parameter.getParameterType() == long.class);
    }

    /**
     * 使用RSA公钥验证JWT签名和有效期，再将sub声明转换为用户ID。
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String authorization = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw unauthorized("缺少Bearer访问令牌", null);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw unauthorized("访问令牌不能为空", null);
        }

        try {
            String subject = Jwts.parser()
                    .verifyWith(jwtKeyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw unauthorized("访问令牌中的用户ID无效", null);
            }
            return userId;
        } catch (JwtException | IllegalArgumentException exception) {
            // 不向客户端返回具体验签失败原因，也不记录完整Token。
            throw unauthorized("访问令牌无效或已过期", exception);
        }
    }

    private ResponseStatusException unauthorized(String message, Exception cause) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message, cause);
    }
}

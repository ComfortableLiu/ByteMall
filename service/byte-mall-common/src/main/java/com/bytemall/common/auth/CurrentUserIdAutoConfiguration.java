package com.bytemall.common.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.security.KeyPair;
import java.util.List;

/**
 * 在提供jwtKeyPair的Web服务中自动注册@CurrentUserId参数解析器。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(name = "jwtKeyPair")
public class CurrentUserIdAutoConfiguration implements WebMvcConfigurer {

    private final CurrentUserIdArgumentResolver argumentResolver;

    public CurrentUserIdAutoConfiguration(@Qualifier("jwtKeyPair") KeyPair jwtKeyPair) {
        this.argumentResolver = new CurrentUserIdArgumentResolver(jwtKeyPair);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(argumentResolver);
    }
}

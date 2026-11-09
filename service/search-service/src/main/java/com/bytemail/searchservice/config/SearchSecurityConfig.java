package com.bytemail.searchservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SearchSecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .authorizeHttpRequests(auth -> auth
                        // 游客也可以使用的搜索接口
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/search/**"
                        ).permitAll()

                        // 其他接口必须登录
                        .anyRequest().authenticated()
                )
                // 暂时保留默认登录能力，后续接入 JWT 时再移除
                .formLogin(Customizer.withDefaults());

        return http.build();
    }
}

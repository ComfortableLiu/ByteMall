package com.bytemall.common.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * 为没有自定义SecurityFilterChain的REST服务提供统一JWT认证。
 */
@AutoConfiguration(before = {SecurityAutoConfiguration.class, CurrentUserIdAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnMissingBean(SecurityFilterChain.class)
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtSecurityAutoConfiguration {

    @Bean(name = "jwtKeyPair")
    @ConditionalOnMissingBean(name = "jwtKeyPair")
    public KeyPair jwtKeyPair(
            @Value("${jwt.rsa.private-key:${JWT_RSA_PRIVATE_KEY:}}") String privateKeyPem,
            @Value("${jwt.rsa.public-key:${JWT_RSA_PUBLIC_KEY:}}") String publicKeyPem
    ) throws Exception {
        if (privateKeyPem.isBlank() && publicKeyPem.isBlank()) {
            throw new IllegalStateException(
                    "请配置jwt.rsa.public-key/JWT_RSA_PUBLIC_KEY，或使用start-all.sh生成共享开发密钥");
        }

        PrivateKey privateKey = privateKeyPem.isBlank() ? null : parsePrivateKey(privateKeyPem);
        PublicKey publicKey;
        if (!publicKeyPem.isBlank()) {
            publicKey = parsePublicKey(publicKeyPem);
        } else if (privateKey instanceof RSAPrivateCrtKey crtKey) {
            publicKey = KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        } else {
            throw new IllegalStateException("无法从JWT私钥推导RSA公钥");
        }
        return new KeyPair(publicKey, privateKey);
    }

    @Bean
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            KeyPair jwtKeyPair,
            JwtSecurityProperties properties
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED", "请先登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN", "没有访问权限"))
                )
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers("/actuator/health", "/error").permitAll();
                    permitAll(auth, null, properties.getPublicPaths());
                    permitAll(auth, HttpMethod.GET, properties.getPublicGetPaths());
                    permitAll(auth, HttpMethod.POST, properties.getPublicPostPaths());
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtKeyPair),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void permitAll(
            org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<
                    HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
            HttpMethod method,
            List<String> paths
    ) {
        String[] patterns = paths.stream().map(String::trim).filter(path -> !path.isEmpty()).toArray(String[]::new);
        if (patterns.length == 0) {
            return;
        }
        if (method == null) {
            auth.requestMatchers(patterns).permitAll();
        } else {
            auth.requestMatchers(method, patterns).permitAll();
        }
    }

    private static PublicKey parsePublicKey(String pem) throws Exception {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodePem(pem)));
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decodePem(pem)));
    }

    private static byte[] decodePem(String pem) {
        String content = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(content);
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"data\":null,\"success\":false}");
    }
}

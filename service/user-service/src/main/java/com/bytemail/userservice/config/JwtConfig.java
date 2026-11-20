package com.bytemail.userservice.config;

import com.bytemall.common.auth.DevJwtKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Configuration
public class JwtConfig {

    // RSA私钥（PKCS#8 PEM格式，即-----BEGIN PRIVATE KEY-----），为空时生成临时密钥对（仅限本地开发）
    @Value("${jwt.rsa.private-key:${JWT_RSA_PRIVATE_KEY:}}")
    private String privateKeyPem;

    // RSA公钥（X.509 PEM格式，即-----BEGIN PUBLIC KEY-----），用于网关或其他服务验签，可省略
    @Value("${jwt.rsa.public-key:${JWT_RSA_PUBLIC_KEY:}}")
    private String publicKeyPem;

    @Bean
    public KeyPair jwtKeyPair(Environment environment) throws Exception {
        if (privateKeyPem.isEmpty()) {
            // 回退到内置开发密钥，保证与其他服务共享同一验签公钥；生产环境必须显式配置。
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "生产环境必须配置jwt.rsa.private-key/JWT_RSA_PRIVATE_KEY，禁止使用内置开发密钥");
            }
            log.warn("未配置jwt.rsa.private-key，回退到内置开发密钥（仅限本地开发，禁止用于生产）");
            privateKeyPem = DevJwtKeys.PRIVATE_KEY_PEM;
            if (publicKeyPem.isEmpty()) {
                publicKeyPem = DevJwtKeys.PUBLIC_KEY_PEM;
            }
        }

        PrivateKey privateKey = parsePrivateKey(privateKeyPem);
        PublicKey publicKey = null;
        if (!publicKeyPem.isEmpty()) {
            publicKey = parsePublicKey(publicKeyPem);
        } else if (privateKey instanceof RSAPrivateCrtKey crtKey) {
            // 从CRT私钥推导公钥，方便其他服务拿到公钥验签
            publicKey = KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        }
        return new KeyPair(publicKey, privateKey);
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = decodePem(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private PublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = decodePem(pem);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    // 去掉PEM头尾和空白后做Base64解码得到DER字节
    private byte[] decodePem(String pem) {
        String content = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(content);
    }
}

package com.bytemail.userservice.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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
    @Value("${jwt.rsa.private-key:}")
    private String privateKeyPem;

    // RSA公钥（X.509 PEM格式，即-----BEGIN PUBLIC KEY-----），用于网关或其他服务验签，可省略
    @Value("${jwt.rsa.public-key:}")
    private String publicKeyPem;

    @Bean
    public KeyPair jwtKeyPair() throws Exception {
        if (privateKeyPem.isEmpty()) {
            log.warn("未配置jwt.rsa.private-key，生成临时RSA密钥对，服务重启后已签发的token将失效");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
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

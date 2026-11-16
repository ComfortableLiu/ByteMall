package com.bytemall.common.cors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置。环境变量BYTE_MALL_CORS_ALLOWED_ORIGINS可覆盖允许的前端来源，多个来源用逗号分隔。
 */
@ConfigurationProperties(prefix = "bytemall.cors")
public class ByteMallCorsProperties {

    /** 默认允许Rspack开发服务器和本地预览页面访问。 */
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:4173",
            "http://127.0.0.1:4173"
    ));

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}

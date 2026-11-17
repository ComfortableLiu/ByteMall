package com.bytemall.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 各资源服务器无需登录的HTTP路径；未列入白名单的请求默认需要JWT。
 */
@ConfigurationProperties(prefix = "bytemall.security")
public class JwtSecurityProperties {

    private List<String> publicPaths = new ArrayList<>();
    private List<String> publicGetPaths = new ArrayList<>();
    private List<String> publicPostPaths = new ArrayList<>();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getPublicGetPaths() {
        return publicGetPaths;
    }

    public void setPublicGetPaths(List<String> publicGetPaths) {
        this.publicGetPaths = publicGetPaths;
    }

    public List<String> getPublicPostPaths() {
        return publicPostPaths;
    }

    public void setPublicPostPaths(List<String> publicPostPaths) {
        this.publicPostPaths = publicPostPaths;
    }
}

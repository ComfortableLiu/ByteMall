package com.bytemail.userservice.config;

import com.bytemail.userservice.user.grpc.UserGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 用户服务的gRPC服务端配置，独立于Spring MVC的HTTP端口运行。
 */
@Configuration
public class GrpcServerConfig {

    /**
     * 注册用户gRPC服务并启动监听，Spring容器关闭时同步关闭Server。
     */
    @Bean(destroyMethod = "shutdown")
    public Server grpcServer(
            UserGrpcService userGrpcService,
            @Value("${grpc.server.port:9093}") int port
    ) throws IOException {
        return ServerBuilder.forPort(port)
                .addService(userGrpcService)
                .build()
                .start();
    }
}

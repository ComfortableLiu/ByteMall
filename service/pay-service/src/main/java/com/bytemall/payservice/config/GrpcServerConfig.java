package com.bytemall.payservice.config;

import com.bytemall.payservice.payments.PaymentGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GrpcServerConfig {

    @Bean(destroyMethod = "shutdown")
    public Server grpcServer(
            PaymentGrpcService paymentGrpcService,
            @Value("${grpc.server.port:9092}") int port
    ) throws IOException {
        return ServerBuilder.forPort(port)
                .addService(paymentGrpcService)
                .build()
                .start();
    }
}

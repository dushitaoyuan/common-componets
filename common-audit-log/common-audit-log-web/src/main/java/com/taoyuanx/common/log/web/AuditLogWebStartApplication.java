package com.taoyuanx.common.log.web;

import com.feiniaojin.gracefulresponse.EnableGracefulResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.taoyuanx")
@EnableGracefulResponse
public class AuditLogWebStartApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditLogWebStartApplication.class, args);
    }
}

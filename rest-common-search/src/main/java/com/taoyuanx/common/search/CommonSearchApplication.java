package com.taoyuanx.common.search;

import com.feiniaojin.gracefulresponse.EnableGracefulResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableGracefulResponse
public class CommonSearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommonSearchApplication.class, args);
    }
}

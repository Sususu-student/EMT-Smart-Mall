package com.smartmall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SmartMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartMallApplication.class, args);
    }
}

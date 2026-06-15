package com.yunmo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.yunmo.common.config")
@EnableAsync
public class YunMoApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunMoApplication.class, args);
    }
}

package com.dam.app.appmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.dam.app.appmarket", "com.dam.platform.common"})
public class AppMarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppMarketApplication.class, args);
    }
}

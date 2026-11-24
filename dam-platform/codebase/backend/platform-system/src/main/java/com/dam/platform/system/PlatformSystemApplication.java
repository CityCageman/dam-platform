package com.dam.platform.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.dam.platform.system", "com.dam.platform.common"})
@MapperScan("com.dam.platform.system.repository")
public class PlatformSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformSystemApplication.class, args);
    }
}

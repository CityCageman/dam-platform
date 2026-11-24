package com.dam.processing.model;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.dam.processing", "com.dam.platform.common"})
@MapperScan({"com.dam.processing.model.repository", "com.dam.asset.repository", "com.dam.asset.catalog.repository"})
public class ProcessingModelApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcessingModelApplication.class, args);
    }
}

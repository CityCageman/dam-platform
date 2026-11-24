package com.dam.asset.mdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.dam.asset.mdm", "com.dam.platform.common"})
public class AssetMdmApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssetMdmApplication.class, args);
    }
}

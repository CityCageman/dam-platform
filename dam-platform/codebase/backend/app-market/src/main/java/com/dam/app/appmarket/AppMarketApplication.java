package com.dam.app.appmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class}, scanBasePackages = {"com.dam.app.appmarket", "com.dam.platform.common"})
public class AppMarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppMarketApplication.class, args);
    }
}

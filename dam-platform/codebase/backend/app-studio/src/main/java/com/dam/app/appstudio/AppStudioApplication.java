package com.dam.app.appstudio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class}, scanBasePackages = {"com.dam.app.appstudio", "com.dam.platform.common"})
public class AppStudioApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppStudioApplication.class, args);
    }
}

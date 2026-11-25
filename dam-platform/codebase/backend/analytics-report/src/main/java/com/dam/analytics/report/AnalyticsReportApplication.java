package com.dam.analytics.report;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.dam.analytics.report", "com.dam.platform.common"})
@MapperScan({"com.dam.analytics.report.repository"})
public class AnalyticsReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsReportApplication.class, args);
    }
}

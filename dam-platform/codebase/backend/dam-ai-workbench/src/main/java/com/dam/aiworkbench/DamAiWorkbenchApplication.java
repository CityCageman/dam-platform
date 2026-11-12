package com.dam.aiworkbench;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 工作台启动类。
 * <p>
 * 负责 AI 任务中心、Prompt/RAG 编排、报表草稿和审核；
 * 领域数据工具通过 catalog 内部接口调用，模型调用通过 dam-model-gateway。
 */
@SpringBootApplication(scanBasePackages = {"com.dam.aiworkbench", "com.dam.common"})
@MapperScan("com.dam.aiworkbench.repository")
@EnableAsync
public class DamAiWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DamAiWorkbenchApplication.class, args);
    }
}

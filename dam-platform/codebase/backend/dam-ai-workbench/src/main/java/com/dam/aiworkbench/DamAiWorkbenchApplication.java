package com.dam.aiworkbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 工作台启动类。
 * <p>
 * 只负责 AI 编排：对话、任务、Prompt、RAG、报表草稿和审核；
 * 模型调用走 dam-model-gateway，元数据/SQL 执行走 dam-catalog。
 */
@SpringBootApplication(scanBasePackages = {"com.dam.aiworkbench", "com.dam.common"})
public class DamAiWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(DamAiWorkbenchApplication.class, args);
    }
}

package com.dam.ai.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 知识库（Knowledge）服务启动类。
 * <p>
 * 从 asset-catalog 中拆出的独立领域服务，独占 PostgreSQL 的 {@code dam_ai} schema：
 * {@code knowledge_category}、{@code knowledge_entry}、{@code knowledge_sync_config}
 * 三张表只允许本服务读写。
 * <p>
 * 指标口径不直接读 {@code dam_dan} 表，而是通过
 * {@link com.dam.ai.knowledge.client.MetricReadClient} 调用 analytics-metric 内部只读接口获取；
 * WeKnora 外部知识库同步能力保持不变。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.ai.knowledge", "com.dam.platform.common"})
@MapperScan("com.dam.ai.knowledge.repository")
@EnableScheduling
public class AiKnowledgeApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiKnowledgeApplication.class, args);
    }
}

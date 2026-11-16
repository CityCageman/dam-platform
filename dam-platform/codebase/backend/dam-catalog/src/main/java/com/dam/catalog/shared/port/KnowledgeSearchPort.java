package com.dam.catalog.shared.port;

import java.util.Map;

/**
 * 知识库检索端口。
 * <p>
 * 知识库数据由 dam-knowledge 服务独占，实现为
 * {@link com.dam.catalog.shared.adapter.KnowledgeSearchHttpAdapter}（HTTP 调用 dam-knowledge），
 * catalog 不再包含知识库实现，也不直接访问 dam_knowledge 表。
 *
 * @author DAM Platform
 */
public interface KnowledgeSearchPort {

    /**
     * 检索业务知识库。
     *
     * @param query 自然语言查询内容
     * @param limit 最大返回条数
     * @return 知识检索结果，保持 {@code results} 结构：{@code {source, results:[{title, content, reportSpec, ...}]}}
     */
    Map<String, Object> search(String query, int limit);
}

package com.dam.access.integration.service;

import com.dam.access.integration.model.ConnectorDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据集成连接器目录服务
 * <p>
 * 只维护平台支持的静态连接器类型目录；集成任务的分页、增删改与状态管理
 * 由 {@link IntegrationJobService} 负责。
 *
 * @author DAM Platform
 */
@Service
public class IntegrationCatalogService {

    /** 连接器类型目录（静态，按常用接入顺序排列） */
    private static final List<ConnectorDefinition> CONNECTORS = List.of(
            new ConnectorDefinition("JDBC", "关系型数据库",
                    "通过 JDBC 读取 MySQL、PostgreSQL、Oracle、SQL Server 等关系型数据库表数据。",
                    ConnectorDefinition.STATUS_AVAILABLE),
            new ConnectorDefinition("CDC", "变更数据捕获",
                    "基于数据库日志捕获增量变更，用于实时/准实时数据同步场景。",
                    ConnectorDefinition.STATUS_AVAILABLE),
            new ConnectorDefinition("FILE", "文件",
                    "读取 CSV、Excel、JSON 等文件数据，支持批量文件接入。",
                    ConnectorDefinition.STATUS_AVAILABLE),
            new ConnectorDefinition("API", "REST API",
                    "通过 HTTP 接口拉取第三方系统数据，支持分页与鉴权配置。",
                    ConnectorDefinition.STATUS_AVAILABLE),
            new ConnectorDefinition("KAFKA", "Kafka 消息",
                    "订阅 Kafka 主题，实现流式数据的持续接入。",
                    ConnectorDefinition.STATUS_AVAILABLE));

    /**
     * 获取全部连接器类型
     *
     * @return 连接器定义列表
     */
    public List<ConnectorDefinition> listConnectors() {
        return CONNECTORS;
    }
}

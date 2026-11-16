package com.dam.integration.service;

import com.dam.common.exception.BusinessException;
import com.dam.integration.model.ConnectorDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 数据集成目录与任务查询服务
 * <p>
 * 阶段 1 只维护静态连接器目录，并提供任务查询骨架：
 * 不读取数据库、不调用真实数据源、不执行同步逻辑。
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
     * 阶段 1 暂无集成任务，恒返回空列表。
     * TODO 接入 dam_integration 任务表后改为分页查询，并返回任务状态、最近运行时间等字段。
     */
    private static final List<Map<String, Object>> JOBS = List.of();

    /** 任务列表说明信息，来源于 application.yml */
    private final String skeletonNote;

    /**
     * 构造服务
     *
     * @param skeletonNote 任务列表说明信息
     */
    public IntegrationCatalogService(
            @Value("${dam.integration.skeleton-note:本阶段仅提供数据集成骨架，任务列表为空}") String skeletonNote) {
        this.skeletonNote = skeletonNote;
    }

    /**
     * 获取全部连接器类型
     *
     * @return 连接器定义列表
     */
    public List<ConnectorDefinition> listConnectors() {
        return CONNECTORS;
    }

    /**
     * 获取集成任务列表
     * <p>
     * 阶段 1 恒为空列表；调用方可通过 {@link #jobsNote()} 获取说明信息。
     * TODO 接入任务存储与调度服务后返回真实任务数据。
     *
     * @return 空任务列表
     */
    public List<Map<String, Object>> listJobs() {
        return JOBS;
    }

    /**
     * 任务列表说明信息
     *
     * @return 说明文案
     */
    public String jobsNote() {
        return skeletonNote;
    }

    /**
     * 按 ID 获取集成任务详情
     * <p>
     * 阶段 1 没有任何任务，任意 ID 均视为不存在，抛出 404 业务异常。
     * TODO 接入任务存储后按 ID 查询，不存在时抛出同样的 404。
     *
     * @param id 任务 ID
     * @return 任务详情（当前实现不会返回）
     */
    public Map<String, Object> requireJob(String id) {
        throw new BusinessException(404, "集成任务不存在: " + id);
    }
}

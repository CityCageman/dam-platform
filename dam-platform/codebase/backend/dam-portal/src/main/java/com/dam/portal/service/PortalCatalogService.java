package com.dam.portal.service;

import com.dam.common.exception.BusinessException;
import com.dam.portal.model.PortalDomain;
import com.dam.portal.model.PortalModule;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 门户目录服务
 * <p>
 * 维护阶段 1 的 8 个能力域与 23 个模块目录（静态配置，不落库），
 * 并提供能力域查询与首页统计能力。目录一经构建即不可变，可安全并发读取。
 *
 * @author DAM Platform
 */
@Service
public class PortalCatalogService {

    /** 模块状态：可用 */
    private static final String STATUS_AVAILABLE = PortalModule.STATUS_AVAILABLE;

    /** 能力域目录（按 sortOrder 排列） */
    private static final List<PortalDomain> DOMAINS = buildDomains();

    /** 能力域编码索引，用于 O(1) 查询 */
    private static final Map<String, PortalDomain> DOMAIN_INDEX = buildIndex(DOMAINS);

    /**
     * 获取全部能力域（含模块）
     *
     * @return 能力域列表
     */
    public List<PortalDomain> listDomains() {
        return DOMAINS;
    }

    /**
     * 按编码获取能力域，不存在时抛出 404 业务异常
     *
     * @param code 能力域编码（大小写不敏感）
     * @return 能力域
     */
    public PortalDomain requireDomain(String code) {
        String key = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        PortalDomain domain = DOMAIN_INDEX.get(key);
        if (domain == null) {
            throw new BusinessException(404, "能力域不存在: " + code);
        }
        return domain;
    }

    /**
     * 首页统计：能力域数量与模块数量
     *
     * @return 统计结果
     */
    public Map<String, Object> homeSummary() {
        int moduleCount = DOMAINS.stream().mapToInt(domain -> domain.modules().size()).sum();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("domainCount", DOMAINS.size());
        summary.put("moduleCount", moduleCount);
        return summary;
    }

    /**
     * 构建阶段 1 能力域与模块目录
     *
     * @return 能力域列表
     */
    private static List<PortalDomain> buildDomains() {
        return List.of(
                domain("asset", "数据资产与主数据",
                        "统一管理数据资产目录与主数据，提供资产盘点、资产详情与主数据分发能力。",
                        "Coin", 1,
                        module("asset", "DAM", "数据资产管理",
                                "数据资产目录、资产检索、资产详情与资产盘点。",
                                "DataBoard", 1),
                        module("asset", "MDM", "主数据管理",
                                "主数据模型、主数据维护、分发与共享。",
                                "Collection", 2)),
                domain("standard", "标准与模型",
                        "建设数据标准与数据模型，支撑标准落标检查与模型设计。",
                        "Document", 2,
                        module("standard", "STD", "数据标准",
                                "数据标准维护、落标检查与标准执行评估。",
                                "DocumentChecked", 1),
                        module("standard", "MDL", "数据建模",
                                "逻辑模型/物理模型设计与模型版本管理。",
                                "Share", 2)),
                domain("engineering", "数据工程",
                        "覆盖数据集成、数据开发、调度与存储计算的一体化数据工程能力。",
                        "SetUp", 3,
                        module("engineering", "INT", "数据集成",
                                "数据源接入、离线/实时同步与集成任务管理。",
                                "Link", 1),
                        module("engineering", "DEV", "数据开发",
                                "SQL 开发、脚本编排与开发任务调试。",
                                "EditPen", 2),
                        module("engineering", "SCH", "调度中心",
                                "调度任务编排、依赖管理与运行监控。",
                                "Timer", 3),
                        module("engineering", "STO", "数据存储与计算",
                                "存储资源管理、计算引擎配置与容量分析。",
                                "Coin", 4)),
                domain("governance", "数据治理",
                        "围绕数据质量、血缘、安全与生命周期，构建全链路数据治理体系。",
                        "Management", 4,
                        module("governance", "DQ", "数据质量",
                                "质量规则配置、质量稽核与质量问题闭环。",
                                "CircleCheck", 1),
                        module("governance", "LIN", "数据血缘",
                                "表级/字段级血缘采集、血缘分析与影响评估。",
                                "Share", 2),
                        module("governance", "SEC", "数据安全",
                                "数据分级分类、脱敏加密与访问权限管控。",
                                "Lock", 3),
                        module("governance", "LIF", "数据生命周期",
                                "数据归档、冷热分层与到期清理策略。",
                                "Refresh", 4)),
                domain("analytics", "指标与分析",
                        "沉淀统一指标口径与报表分析能力，支撑经营决策与自助分析。",
                        "DataAnalysis", 5,
                        module("analytics", "MET", "指标中心",
                                "指标定义、口径管理与指标加工。",
                                "Odometer", 1),
                        module("analytics", "RPT", "报表分析",
                                "自助报表、可视化分析与报表订阅。",
                                "TrendCharts", 2)),
                domain("service", "数据服务与市场",
                        "提供数据服务发布、订阅授权与数据市场运营能力。",
                        "Connection", 6,
                        module("service", "DS", "数据服务",
                                "API 服务发布、订阅授权与调用配额管理。",
                                "Promotion", 1),
                        module("service", "MKT", "数据市场",
                                "数据产品上架、订阅交易与市场运营。",
                                "Shop", 2)),
                domain("ai", "AI 与知识",
                        "汇聚知识中心、AI 工作台与模型网关，提供智能化数据应用能力。",
                        "MagicStick", 7,
                        module("ai", "KNO", "知识中心",
                                "知识分类、知识条目与文档检索。",
                                "Reading", 1),
                        module("ai", "AI", "AI 工作台",
                                "AI 任务编排、Prompt 管理与结果审核。",
                                "ChatDotRound", 2),
                        module("ai", "MGW", "模型网关",
                                "模型配置、路由分发与调用限流。",
                                "Cpu", 3)),
                domain("platform", "平台与运维",
                        "提供任务调度、运维、系统管理与可观测告警等平台支撑能力。",
                        "Monitor", 8,
                        module("platform", "TC", "任务中心",
                                "任务实例管理、运行日志与失败重试。",
                                "List", 1),
                        module("platform", "OPS", "运维中心",
                                "服务运维、配置管理与巡检。",
                                "Tools", 2),
                        module("platform", "SYS", "系统管理",
                                "用户、角色、租户、菜单与字典管理。",
                                "Setting", 3),
                        module("platform", "OBS", "可观测与告警",
                                "指标监控、链路追踪与告警通知。",
                                "Bell", 4)));
    }

    /**
     * 创建能力域定义
     *
     * @param code        能力域编码
     * @param name        能力域名称
     * @param description 能力域简介
     * @param icon        前端图标
     * @param sortOrder   展示顺序
     * @param modules     模块列表
     * @return 能力域
     */
    private static PortalDomain domain(String code, String name, String description,
                                       String icon, int sortOrder, PortalModule... modules) {
        return new PortalDomain(code, name, description, icon,
                "/domains/" + code, sortOrder, List.of(modules));
    }

    /**
     * 创建模块定义，路由统一按 /domains/{domainCode}/{moduleCode 小写} 生成
     *
     * @param domainCode  所属能力域编码
     * @param code        模块编码
     * @param name        模块名称
     * @param description 模块简介
     * @param icon        前端图标
     * @param sortOrder   展示顺序
     * @return 模块
     */
    private static PortalModule module(String domainCode, String code, String name,
                                       String description, String icon, int sortOrder) {
        String route = "/domains/" + domainCode + "/" + code.toLowerCase(Locale.ROOT);
        // 阶段 1 尚未建设的模块明确标记为规划中，避免前端误以为可进入
        Set<String> planned = Set.of("MDM", "DEV", "SCH", "STO", "DS", "MKT", "KNO", "OBS");
        String status = planned.contains(code) ? PortalModule.STATUS_PLANNED : STATUS_AVAILABLE;
        return new PortalModule(code, name, description, icon, route, sortOrder, status);
    }

    /**
     * 构建能力域编码索引，并校验编码唯一性
     *
     * @param domains 能力域列表
     * @return 编码索引
     */
    private static Map<String, PortalDomain> buildIndex(List<PortalDomain> domains) {
        Map<String, PortalDomain> index = new LinkedHashMap<>();
        for (PortalDomain domain : domains) {
            if (index.putIfAbsent(domain.code(), domain) != null) {
                throw new IllegalStateException("能力域编码重复: " + domain.code());
            }
        }
        return Map.copyOf(index);
    }
}

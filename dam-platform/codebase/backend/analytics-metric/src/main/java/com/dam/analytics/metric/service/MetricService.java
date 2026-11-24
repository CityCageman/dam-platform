package com.dam.analytics.metric.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.security.DataScopeContext;
import com.dam.platform.common.vo.PageResult;
import com.dam.analytics.metric.client.CatalogMetricAiClient;
import com.dam.analytics.metric.entity.MetricDefinition;
import com.dam.analytics.metric.entity.MetricValue;
import com.dam.analytics.metric.repository.MetricDefinitionMapper;
import com.dam.analytics.metric.repository.MetricValueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 指标中心领域服务：指标定义 + 手工填报值 + AI 生成指标 SQL。
 * <p>
 * {@code dam_metric.metric_definition}、{@code dam_metric.metric_value} 归 analytics-metric 独占；
 * 本服务是这两张表的唯一读写入口。catalog 的指标读取需求通过内部只读接口
 * {@code /api/metrics/internal/**} 满足，指标 SQL 生成通过 {@link CatalogMetricAiClient}
 * 调用 catalog 内部 AI 接口，不再依赖 catalog 的 ReportService。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
public class MetricService {

    /** 指标定义 Mapper */
    private final MetricDefinitionMapper metricMapper;

    /** 指标值 Mapper */
    private final MetricValueMapper valueMapper;

    /** catalog 指标 AI 客户端 */
    private final CatalogMetricAiClient catalogMetricAiClient;

    /**
     * 分页查询指标定义。
     *
     * @param keyword        关键字（指标名称/编码/定义）
     * @param metricType     指标类型，可空
     * @param businessDomain 业务域，可空
     * @param status         状态，可空
     * @param page           页码，从 1 开始
     * @param pageSize       每页条数，最大 200
     * @return 分页结果
     */
    public PageResult<MetricDefinition> page(String keyword, String metricType, String businessDomain,
                                             Integer status, Integer page, Integer pageSize) {
        int p = Math.max(1, page == null ? 1 : page);
        int size = Math.clamp(pageSize == null ? 20 : pageSize, 1, 200);
        LambdaQueryWrapper<MetricDefinition> w = new LambdaQueryWrapper<>();
        // 逻辑删除：deleted_at 非空即已删除，所有查询显式排除
        w.isNull(MetricDefinition::getDeletedAt);
        if (StrUtil.isNotBlank(keyword)) {
            w.and(q -> q.like(MetricDefinition::getMetricName, keyword)
                    .or().like(MetricDefinition::getMetricCode, keyword)
                    .or().like(MetricDefinition::getDefinition, keyword));
        }
        if (StrUtil.isNotBlank(metricType)) {
            w.eq(MetricDefinition::getMetricType, metricType.toUpperCase());
        }
        if (StrUtil.isNotBlank(businessDomain)) {
            w.eq(MetricDefinition::getBusinessDomain, businessDomain);
        }
        if (status != null) {
            w.eq(MetricDefinition::getStatus, status);
        }
        if (DataScopeContext.isDepartment()) {
            String dept = DataScopeContext.department();
            if (dept.isBlank()) w.eq(MetricDefinition::getId, -1L);
            else w.and(q -> q.eq(MetricDefinition::getDepartment, dept)
                    .or().isNull(MetricDefinition::getDepartment));
        } else if (DataScopeContext.isSelf()) {
            String username = DataScopeContext.username();
            if (username.isBlank()) w.eq(MetricDefinition::getId, -1L);
            else w.and(q -> q.eq(MetricDefinition::getCreatedBy, username)
                    .or().isNull(MetricDefinition::getDepartment));
        }
        w.orderByDesc(MetricDefinition::getUpdatedAt);
        return PageResult.of(metricMapper.selectPage(new Page<>(p, size), w));
    }

    /**
     * 按 ID 查询未删除的指标定义。
     *
     * @param id 指标 ID
     * @return 指标定义
     */
    public MetricDefinition get(Long id) {
        MetricDefinition metric = id == null ? null : metricMapper.selectById(id);
        if (metric == null || metric.getDeletedAt() != null) {
            throw new BusinessException("指标不存在");
        }
        return metric;
    }

    /**
     * 新增或更新指标定义（body 带 id 时更新）。更新时 version 自增。
     *
     * @param body 指标字段
     * @return 保存后的指标定义
     */
    @Transactional
    public MetricDefinition save(Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        String code = StrUtil.trimToEmpty(str(body.get("metricCode")));
        String name = StrUtil.trimToEmpty(str(body.get("metricName")));
        String type = StrUtil.blankToDefault(str(body.get("metricType")), "COMPUTED").toUpperCase();
        if (StrUtil.isBlank(code)) {
            throw new BusinessException("请填写指标编码");
        }
        if (StrUtil.isBlank(name)) {
            throw new BusinessException("请填写指标名称");
        }
        if (!List.of("MANUAL", "COMPUTED").contains(type)) {
            throw new BusinessException("指标类型只支持 MANUAL/COMPUTED");
        }

        MetricDefinition metric = id != null ? get(id) : new MetricDefinition();
        Long duplicated = metricMapper.selectCount(new LambdaQueryWrapper<MetricDefinition>()
                .eq(MetricDefinition::getMetricCode, code)
                .isNull(MetricDefinition::getDeletedAt)
                .ne(id != null, MetricDefinition::getId, id));
        if (duplicated != null && duplicated > 0) {
            throw new BusinessException("指标编码已存在: " + code);
        }

        String user = currentUser();
        LocalDateTime now = LocalDateTime.now();
        if (StrUtil.isBlank(metric.getDepartment())) metric.setDepartment(DataScopeContext.department());
        if (id == null) {
            metric.setCreatedAt(now);
            metric.setCreatedBy(user);
            metric.setVersion(1);
        } else {
            metric.setVersion((metric.getVersion() == null ? 1 : metric.getVersion()) + 1);
        }
        metric.setMetricCode(cut(code, 100));
        metric.setMetricName(cut(name, 200));
        metric.setMetricType(type);
        metric.setBusinessDomain(cut(str(body.get("businessDomain")), 100));
        metric.setDefinition(cut(str(body.get("definition")), 1000));
        metric.setFormula(cut(str(body.get("formula")), 2000));
        metric.setUnit(cut(str(body.get("unit")), 50));
        metric.setDataSourceId(toLong(body.get("dataSourceId")));
        metric.setSourceTables(json(body.get("sourceTables")));
        metric.setSqlExpression(str(body.get("sqlExpression")));
        metric.setReportSpec(json(body.get("reportSpec")));
        metric.setDimensions(json(body.get("dimensions")));
        metric.setTimeGrain(cut(str(body.get("timeGrain")), 30));
        metric.setOwner(cut(str(body.get("owner")), 100));
        metric.setStatus(normalizeStatus(body.get("status"), metric.getStatus()));
        metric.setUpdatedAt(now);
        metric.setUpdatedBy(user);
        if (id == null) {
            metricMapper.insert(metric);
        } else {
            metricMapper.updateById(metric);
        }
        return metric;
    }

    /**
     * 逻辑删除指标定义。
     *
     * @param id 指标 ID
     */
    @Transactional
    public void delete(Long id) {
        MetricDefinition metric = get(id);
        metric.setDeletedAt(LocalDateTime.now());
        metric.setUpdatedAt(LocalDateTime.now());
        metric.setUpdatedBy(currentUser());
        metricMapper.updateById(metric);
    }

    /**
     * AI 根据指标定义/公式生成 SQL，成功后写回 sql_expression，version+1。
     * <p>
     * 实际生成由 catalog 内部 AI 接口完成，本服务只负责透传指标和 token、写回结果。
     *
     * @param id    指标 ID
     * @param token 当前登录 token
     * @return AI 返回的 SQL、使用表、参数等信息，并附带新版本号
     */
    @Transactional
    public Map<String, Object> aiGenerateSql(Long id, String token) {
        MetricDefinition metric = get(id);
        if (!"COMPUTED".equalsIgnoreCase(metric.getMetricType())) {
            throw new BusinessException("手工填报指标不需要生成 SQL");
        }
        if (metric.getDataSourceId() == null) {
            throw new BusinessException("请先为指标选择数据源");
        }
        Map<String, Object> result = catalogMetricAiClient.generateMetricSql(metric, token);
        String sql = String.valueOf(result.getOrDefault("sql", ""));
        if (StrUtil.isBlank(sql)) {
            throw new BusinessException("AI 未生成有效 SQL");
        }
        metric.setSqlExpression(sql);
        metric.setVersion((metric.getVersion() == null ? 1 : metric.getVersion()) + 1);
        metric.setUpdatedAt(LocalDateTime.now());
        metric.setUpdatedBy(currentUser());
        metricMapper.updateById(metric);
        result.put("version", metric.getVersion());
        return result;
    }

    /**
     * 查询指标的手工填报值（按期间倒序）。
     *
     * @param metricId 指标 ID
     * @return 指标值列表
     */
    public List<MetricValue> values(Long metricId) {
        get(metricId);
        return valueMapper.selectList(new LambdaQueryWrapper<MetricValue>()
                .eq(MetricValue::getMetricId, metricId)
                .isNull(MetricValue::getDeletedAt)
                .orderByDesc(MetricValue::getPeriod));
    }

    /**
     * 新增或更新手工填报值（同一指标 + 期间 + 维度键唯一）。
     *
     * @param metricId 指标 ID
     * @param body     指标值字段
     * @return 保存后的指标值
     */
    @Transactional
    public MetricValue saveValue(Long metricId, Map<String, Object> body) {
        MetricDefinition metric = get(metricId);
        if (!"MANUAL".equalsIgnoreCase(metric.getMetricType())) {
            throw new BusinessException("只有手工填报指标可以维护指标值");
        }
        String period = StrUtil.trimToEmpty(str(body.get("period")));
        if (StrUtil.isBlank(period)) {
            throw new BusinessException("请填写期间，如 2026-10");
        }
        if (period.length() > 30) {
            period = period.substring(0, 30);
        }
        String dimensionKey = StrUtil.blankToDefault(str(body.get("dimensionKey")), "");
        if (dimensionKey.length() > 500) {
            dimensionKey = dimensionKey.substring(0, 500);
        }
        MetricValue value = valueMapper.selectOne(new LambdaQueryWrapper<MetricValue>()
                .eq(MetricValue::getMetricId, metricId)
                .eq(MetricValue::getPeriod, period)
                .eq(MetricValue::getDimensionKey, dimensionKey)
                .isNull(MetricValue::getDeletedAt)
                .last("LIMIT 1"));
        boolean isNew = value == null;
        if (isNew) {
            value = new MetricValue();
        }
        String user = currentUser();
        LocalDateTime now = LocalDateTime.now();
        value.setMetricId(metricId);
        value.setPeriod(period);
        value.setDimensionKey(dimensionKey);
        value.setDimensionValues(json(body.get("dimensionValues")));
        Object rawValue = body.get("metricValue");
        if (rawValue == null || StrUtil.isBlank(String.valueOf(rawValue))) {
            throw new BusinessException("请填写指标值");
        }
        try {
            value.setMetricValue(new BigDecimal(String.valueOf(rawValue)));
        } catch (NumberFormatException e) {
            throw new BusinessException("指标值必须是数字");
        }
        value.setNote(cut(str(body.get("note")), 500));
        value.setStatus(1);
        value.setUpdatedAt(now);
        value.setUpdatedBy(user);
        if (isNew) {
            value.setCreatedAt(now);
            value.setCreatedBy(user);
            valueMapper.insert(value);
        } else {
            valueMapper.updateById(value);
        }
        return value;
    }

    /**
     * 逻辑删除手工填报值。
     *
     * @param metricId 指标 ID
     * @param valueId  指标值 ID
     */
    @Transactional
    public void deleteValue(Long metricId, Long valueId) {
        get(metricId);
        MetricValue value = valueMapper.selectById(valueId);
        if (value == null || !metricId.equals(value.getMetricId()) || value.getDeletedAt() != null) {
            throw new BusinessException("指标值不存在");
        }
        value.setDeletedAt(LocalDateTime.now());
        value.setUpdatedAt(LocalDateTime.now());
        value.setUpdatedBy(currentUser());
        valueMapper.updateById(value);
    }

    /**
     * 内部只读接口：查询未删除指标，供 catalog 知识库/报表读取。
     *
     * @param limit 最大返回条数，默认 500，最大 500
     * @return 指标列表
     */
    public List<MetricDefinition> listActive(Integer limit) {
        int size = Math.clamp(limit == null ? 500 : limit, 1, 500);
        return metricMapper.selectList(new LambdaQueryWrapper<MetricDefinition>()
                .isNull(MetricDefinition::getDeletedAt)
                .orderByAsc(MetricDefinition::getId)
                .last("LIMIT " + size));
    }

    /**
     * 内部只读接口：查询指定数据源下启用的指标，供 catalog 报表/知识库读取。
     *
     * @param dataSourceId 数据源 ID
     * @return 指标列表，最多 50 条
     */
    public List<MetricDefinition> listActiveByDataSource(Long dataSourceId) {
        if (dataSourceId == null) {
            throw new BusinessException("请指定数据源");
        }
        return metricMapper.selectList(new LambdaQueryWrapper<MetricDefinition>()
                .eq(MetricDefinition::getDataSourceId, dataSourceId)
                .eq(MetricDefinition::getStatus, 1)
                .isNull(MetricDefinition::getDeletedAt)
                .orderByAsc(MetricDefinition::getId)
                .last("LIMIT 50"));
    }

    /**
     * 规范化指标状态：0-停用，1-启用；为空时保留旧值，新记录默认 1。
     *
     * @param raw      请求中的状态值
     * @param fallback 旧状态
     * @return 合法状态
     */
    private Integer normalizeStatus(Object raw, Integer fallback) {
        if (raw == null) {
            return fallback == null ? 1 : fallback;
        }
        Integer status;
        if (raw instanceof Number number) {
            status = number.intValue();
        } else {
            try {
                status = Integer.valueOf(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new BusinessException("指标状态只支持 0(停用)/1(启用)");
            }
        }
        if (status != 0 && status != 1) {
            throw new BusinessException("指标状态只支持 0(停用)/1(启用)");
        }
        return status;
    }

    /**
     * 把 JSON 对象/数组序列化为 JSONB 字符串；字符串原样保留。
     *
     * @param value 原始值
     * @return JSON 字符串
     */
    private String json(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return StrUtil.isBlank(text) ? null : text;
        }
        return JSONUtil.toJsonStr(value);
    }

    /**
     * 获取当前登录用户，未登录或非 Web 上下文时返回 system。
     *
     * @return 当前用户标识
     */
    private String currentUser() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? "system" : String.valueOf(loginId);
        } catch (Exception e) {
            return "system";
        }
    }

    /**
     * 任意对象转字符串。
     *
     * @param value 原始值
     * @return 字符串或 null
     */
    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 任意对象转 Long。
     *
     * @param value 原始值
     * @return Long 或 null
     */
    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
            try {
                return Long.valueOf(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // 非数字按 null 处理，与拆分前 catalog 行为保持一致
            }
        }
        return null;
    }

    /**
     * 按数据库字段长度截断字符串。
     *
     * @param value 原始字符串
     * @param max   最大长度
     * @return 截断后的字符串
     */
    private String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

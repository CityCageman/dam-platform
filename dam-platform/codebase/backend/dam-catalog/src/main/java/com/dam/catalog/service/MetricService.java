package com.dam.catalog.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.catalog.entity.MetricDefinition;
import com.dam.catalog.entity.MetricValue;
import com.dam.catalog.repository.MetricDefinitionMapper;
import com.dam.catalog.repository.MetricValueMapper;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 指标中心：指标定义 + 手工填报值。
 */
@Service
@RequiredArgsConstructor
public class MetricService {

    private final MetricDefinitionMapper metricMapper;
    private final MetricValueMapper valueMapper;
    private final ReportService reportService;

    public PageResult<MetricDefinition> page(String keyword, String metricType, String domain, Integer status,
                                             Integer page, Integer pageSize) {
        int p = Math.max(1, page == null ? 1 : page);
        int size = Math.clamp(pageSize == null ? 20 : pageSize, 1, 200);
        LambdaQueryWrapper<MetricDefinition> w = new LambdaQueryWrapper<>();
        w.isNull(MetricDefinition::getDeletedAt);
        if (StrUtil.isNotBlank(keyword)) {
            w.and(q -> q.like(MetricDefinition::getMetricName, keyword)
                    .or().like(MetricDefinition::getMetricCode, keyword)
                    .or().like(MetricDefinition::getDefinition, keyword));
        }
        if (StrUtil.isNotBlank(metricType)) w.eq(MetricDefinition::getMetricType, metricType.toUpperCase());
        if (StrUtil.isNotBlank(domain)) w.eq(MetricDefinition::getBusinessDomain, domain);
        if (status != null) w.eq(MetricDefinition::getStatus, status);
        w.orderByDesc(MetricDefinition::getUpdatedAt);
        return PageResult.of(metricMapper.selectPage(new Page<>(p, size), w));
    }

    public MetricDefinition get(Long id) {
        MetricDefinition m = id == null ? null : metricMapper.selectById(id);
        if (m == null || m.getDeletedAt() != null) throw new BusinessException("指标不存在");
        return m;
    }

    @Transactional
    public MetricDefinition save(Map<String, Object> body) {
        Long id = toLong(body.get("id"));
        String code = StrUtil.trimToEmpty(str(body.get("metricCode")));
        String name = StrUtil.trimToEmpty(str(body.get("metricName")));
        String type = StrUtil.blankToDefault(str(body.get("metricType")), "COMPUTED").toUpperCase();
        if (StrUtil.isBlank(code)) throw new BusinessException("请填写指标编码");
        if (StrUtil.isBlank(name)) throw new BusinessException("请填写指标名称");
        if (!List.of("MANUAL", "COMPUTED").contains(type)) throw new BusinessException("指标类型只支持 MANUAL/COMPUTED");

        MetricDefinition m = id != null ? get(id) : new MetricDefinition();
        Long dup = metricMapper.selectCount(new LambdaQueryWrapper<MetricDefinition>()
                .eq(MetricDefinition::getMetricCode, code)
                .isNull(MetricDefinition::getDeletedAt)
                .ne(id != null, MetricDefinition::getId, id));
        if (dup != null && dup > 0) throw new BusinessException("指标编码已存在: " + code);

        String user = currentUser();
        LocalDateTime now = LocalDateTime.now();
        if (id == null) {
            m.setCreatedAt(now);
            m.setCreatedBy(user);
            m.setVersion(1);
        } else {
            m.setVersion((m.getVersion() == null ? 1 : m.getVersion()) + 1);
        }
        m.setMetricCode(cut(code, 100));
        m.setMetricName(cut(name, 200));
        m.setMetricType(type);
        m.setBusinessDomain(cut(str(body.get("businessDomain")), 100));
        m.setDefinition(cut(str(body.get("definition")), 1000));
        m.setFormula(cut(str(body.get("formula")), 2000));
        m.setUnit(cut(str(body.get("unit")), 50));
        m.setDataSourceId(toLong(body.get("dataSourceId")));
        m.setSourceTables(json(body.get("sourceTables")));
        m.setSqlExpression(str(body.get("sqlExpression")));
        m.setDimensions(json(body.get("dimensions")));
        m.setTimeGrain(cut(str(body.get("timeGrain")), 30));
        m.setOwner(cut(str(body.get("owner")), 100));
        m.setStatus(body.get("status") instanceof Number n ? n.intValue() : (m.getStatus() == null ? 1 : m.getStatus()));
        m.setUpdatedAt(now);
        m.setUpdatedBy(user);
        if (id == null) metricMapper.insert(m);
        else metricMapper.updateById(m);
        return m;
    }

    @Transactional
    public void delete(Long id) {
        MetricDefinition m = get(id);
        m.setDeletedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        m.setUpdatedBy(currentUser());
        metricMapper.updateById(m);
    }

    /** AI 根据指标定义/公式生成 SQL，成功后写回 sql_expression，version+1 */
    @Transactional
    public Map<String, Object> aiGenerateSql(Long id, String token) {
        MetricDefinition m = get(id);
        Map<String, Object> result = reportService.generateMetricSql(m, token);
        String sql = String.valueOf(result.getOrDefault("sql", ""));
        if (StrUtil.isBlank(sql)) throw new BusinessException("AI 未生成有效 SQL");
        m.setSqlExpression(sql);
        m.setVersion((m.getVersion() == null ? 1 : m.getVersion()) + 1);
        m.setUpdatedAt(LocalDateTime.now());
        m.setUpdatedBy(currentUser());
        metricMapper.updateById(m);
        result.put("version", m.getVersion());
        return result;
    }

    public List<MetricValue> values(Long metricId) {
        get(metricId);
        return valueMapper.selectList(new LambdaQueryWrapper<MetricValue>()
                .eq(MetricValue::getMetricId, metricId)
                .isNull(MetricValue::getDeletedAt)
                .orderByDesc(MetricValue::getPeriod));
    }

    @Transactional
    public MetricValue saveValue(Long metricId, Map<String, Object> body) {
        MetricDefinition m = get(metricId);
        if (!"MANUAL".equalsIgnoreCase(m.getMetricType())) {
            throw new BusinessException("只有手工填报指标可以维护指标值");
        }
        String period = StrUtil.trimToEmpty(str(body.get("period")));
        if (StrUtil.isBlank(period)) throw new BusinessException("请填写期间，如 2026-10");
        if (period.length() > 30) period = period.substring(0, 30);
        String dimensionKey = StrUtil.blankToDefault(str(body.get("dimensionKey")), "");
        if (dimensionKey.length() > 500) dimensionKey = dimensionKey.substring(0, 500);
        MetricValue v = valueMapper.selectOne(new LambdaQueryWrapper<MetricValue>()
                .eq(MetricValue::getMetricId, metricId)
                .eq(MetricValue::getPeriod, period)
                .eq(MetricValue::getDimensionKey, dimensionKey)
                .isNull(MetricValue::getDeletedAt)
                .last("LIMIT 1"));
        boolean isNew = v == null;
        if (isNew) v = new MetricValue();
        String user = currentUser();
        LocalDateTime now = LocalDateTime.now();
        v.setMetricId(metricId);
        v.setPeriod(period);
        v.setDimensionKey(dimensionKey);
        v.setDimensionValues(json(body.get("dimensionValues")));
        Object value = body.get("metricValue");
        if (value == null || StrUtil.isBlank(String.valueOf(value))) throw new BusinessException("请填写指标值");
        try { v.setMetricValue(new BigDecimal(String.valueOf(value))); }
        catch (NumberFormatException e) { throw new BusinessException("指标值必须是数字"); }
        v.setNote(cut(str(body.get("note")), 500));
        v.setStatus(1);
        v.setUpdatedAt(now);
        v.setUpdatedBy(user);
        if (isNew) { v.setCreatedAt(now); v.setCreatedBy(user); valueMapper.insert(v); }
        else valueMapper.updateById(v);
        return v;
    }

    @Transactional
    public void deleteValue(Long metricId, Long valueId) {
        get(metricId);
        MetricValue v = valueMapper.selectById(valueId);
        if (v == null || !metricId.equals(v.getMetricId()) || v.getDeletedAt() != null) {
            throw new BusinessException("指标值不存在");
        }
        v.setDeletedAt(LocalDateTime.now());
        v.setUpdatedAt(LocalDateTime.now());
        v.setUpdatedBy(currentUser());
        valueMapper.updateById(v);
    }

    private String json(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return StrUtil.isBlank(s) ? null : s;
        return JSONUtil.toJsonStr(value);
    }

    private String currentUser() {
        try {
            Object id = cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull();
            return id == null ? "system" : String.valueOf(id);
        } catch (Exception e) { return "system"; }
    }

    private String str(Object value) { return value == null ? null : String.valueOf(value); }
    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
            try { return Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ignored) { }
        }
        return null;
    }
    private String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

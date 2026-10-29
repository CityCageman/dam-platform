package com.dam.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamConfig;
import com.dam.catalog.entity.DamDataSourceType;
import com.dam.catalog.repository.DamConfigMapper;
import com.dam.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据源类型服务
 * <p>
 * 数据源类型统一由系统配置中心维护（{@code dam_sys.sys_config}，{@code config_type=source_type}），
 * 每条配置的 {@code config_value} 以 JSON 承载连接参数：
 * <pre>
 * {"driverClass":"...","urlTemplate":"...","defaultPort":5432,
 *  "catalogMode":"database","pkStrategy":"pg_catalog","systemSchemas":"..."}
 * </pre>
 * 前端下拉只需 config_code / config_name；后端建连时通过 {@link #getByTypeKey(String)} 取完整连接参数。
 * 当配置中心尚无 source_type 数据时，回退到内置默认配置，保证功能可用。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceTypeService {

    /** 配置中心中数据源类型的 config_type */
    private static final String CONFIG_TYPE = "source_type";

    /** JSON 解析器 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 内置默认配置（仅当配置中心 src_type 为空时兜底） */
    private static final List<DamDataSourceType> BUILTIN_TYPES = buildBuiltinTypes();

    /** 系统配置 Mapper（sys_config 表） */
    private final DamConfigMapper configMapper;

    private static List<DamDataSourceType> buildBuiltinTypes() {
        List<DamDataSourceType> list = new ArrayList<>();
        list.add(build("POSTGRESQL", "PostgreSQL", "org.postgresql.Driver",
                "jdbc:postgresql://%s:%d/%s", 5432, "database", "pg_catalog", "information_schema,pg_catalog"));
        list.add(build("MYSQL", "MySQL", "com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai", 3306, "database", "jdbc_get_primary_keys", "information_schema,mysql,performance_schema,sys"));
        list.add(build("ORACLE", "Oracle", "oracle.jdbc.OracleDriver",
                "jdbc:oracle:thin:@//%s:%d/%s", 1521, "schema", "jdbc_get_primary_keys", "SYS,SYSTEM"));
        list.add(build("SQLSERVER", "SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                "jdbc:sqlserver://%s:%d;databaseName=%s", 1433, "database", "jdbc_get_primary_keys", "sys"));
        list.add(build("IRIS", "IRIS", "com.intersystems.jdbc.IRISDriver",
                "jdbc:IRIS://%s:%d/%s", 1972, "schema", "jdbc_get_primary_keys", "IRISSYS"));
        list.add(build("DM8", "达梦", "dm.jdbc.driver.DmDriver",
                "jdbc:dm://%s:%d/%s", 5236, "schema", "jdbc_get_primary_keys", "SYSAUDITOR,SYSDBA"));
        list.add(build("KINGBASE", "人大金仓", "com.kingbase8.Driver",
                "jdbc:kingbase8://%s:%d/%s", 54321, "database", "pg_catalog", "information_schema,pg_catalog"));
        return list;
    }

    private static DamDataSourceType build(String key, String name, String driver, String urlTemplate,
                                           int port, String catalogMode, String pkStrategy, String systemSchemas) {
        DamDataSourceType t = new DamDataSourceType();
        t.setTypeKey(key);
        t.setTypeName(name);
        t.setDriverClass(driver);
        t.setUrlTemplate(urlTemplate);
        t.setDefaultPort(port);
        t.setCatalogMode(catalogMode);
        t.setPkStrategy(pkStrategy);
        t.setSystemSchemas(systemSchemas);
        t.setSortOrder(0);
        t.setStatus(1);
        return t;
    }

    /**
     * 获取所有数据源类型（含禁用）。优先读取配置中心，配置为空时回退内置默认。
     */
    public List<DamDataSourceType> getAllTypes() {
        List<DamConfig> configs = configMapper.selectList(
                new LambdaQueryWrapper<DamConfig>()
                        .eq(DamConfig::getConfigType, CONFIG_TYPE)
                        .orderByAsc(DamConfig::getSortOrder));
        if (configs == null || configs.isEmpty()) {
            return new ArrayList<>(BUILTIN_TYPES);
        }
        List<DamDataSourceType> list = new ArrayList<>();
        for (DamConfig c : configs) {
            DamDataSourceType t = toType(c);
            if (t != null) list.add(t);
        }
        return list.isEmpty() ? new ArrayList<>(BUILTIN_TYPES) : list;
    }

    /**
     * 获取所有启用的数据源类型
     */
    public List<DamDataSourceType> getEnabledTypes() {
        return getAllTypes().stream()
                .filter(t -> t.getStatus() != null && t.getStatus() == 1)
                .collect(Collectors.toList());
    }

    /**
     * 获取类型 key → 类型配置 的映射
     */
    public Map<String, DamDataSourceType> getTypeMap() {
        return getEnabledTypes().stream().collect(Collectors.toMap(
                DamDataSourceType::getTypeKey, t -> t, (a, b) -> a));
    }

    /**
     * 按 typeKey 获取数据源类型配置（忽略大小写）
     */
    public DamDataSourceType getByTypeKey(String typeKey) {
        if (typeKey == null) return null;
        return getEnabledTypes().stream()
                .filter(t -> typeKey.equalsIgnoreCase(t.getTypeKey()))
                .findFirst().orElse(null);
    }

    /**
     * 仅返回 typeKey / typeName 列表（用于前端下拉框）
     */
    public List<Map<String, String>> getTypeSelectList() {
        return getEnabledTypes().stream().map(t -> Map.of(
                "configCode", t.getTypeKey(),
                "configName", t.getTypeName()
        )).collect(Collectors.toList());
    }

    /**
     * 将 sys_config 记录转换为数据源类型（解析 config_value JSON 连接参数）
     */
    private DamDataSourceType toType(DamConfig c) {
        if (c.getConfigCode() == null) return null;
        DamDataSourceType t = new DamDataSourceType();
        t.setId(c.getId());
        t.setTypeKey(c.getConfigCode());
        t.setTypeName(c.getConfigName());
        t.setSortOrder(c.getSortOrder());
        t.setStatus(c.getStatus());
        t.setRemark(c.getDescription());
        String json = c.getConfigValue();
        if (json != null && !json.isBlank()) {
            try {
                Map<String, Object> m = JSON.readValue(json, Map.class);
                t.setDriverClass(asString(m.get("driverClass")));
                t.setUrlTemplate(asString(m.get("urlTemplate")));
                t.setDefaultPort(asInt(m.get("defaultPort")));
                t.setCatalogMode(asString(m.get("catalogMode")));
                t.setPkStrategy(asString(m.get("pkStrategy")));
                t.setSystemSchemas(asString(m.get("systemSchemas")));
            } catch (Exception e) {
                log.warn("解析数据源类型连接参数失败[{}]: {}", c.getConfigCode(), e.getMessage());
            }
        }
        return t;
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 新增：数据源类型统一在系统配置中心维护
     */
    public DamDataSourceType create(DamDataSourceType type) {
        throw new BusinessException("数据源类型请在“系统配置-数据源类型”中维护");
    }

    /**
     * 更新：数据源类型统一在系统配置中心维护
     */
    public DamDataSourceType update(DamDataSourceType type) {
        throw new BusinessException("数据源类型请在“系统配置-数据源类型”中维护");
    }

    /**
     * 删除：数据源类型统一在系统配置中心维护
     */
    public void delete(Long id) {
        throw new BusinessException("数据源类型请在“系统配置-数据源类型”中维护");
    }
}

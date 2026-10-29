package com.dam.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.entity.DamDataSource;
import com.dam.catalog.entity.DamDataSourceType;
import com.dam.catalog.entity.DamField;
import com.dam.catalog.entity.DamMetadataCollection;
import com.dam.catalog.entity.DamSourceSystem;
import com.dam.catalog.entity.MetadataSchema;
import com.dam.catalog.repository.*;
import com.dam.catalog.service.adapter.*;
import com.dam.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据源扫描发现服务
 * <p>
 * 负责连接外部数据源（PostgreSQL/MySQL/Oracle/SQLServer/IRIS/国产库等），
 * 通过 {@link DataSourceAdapter} 适配器体系扫描 Schema/表/字段/主键信息。
 * <p>
 * 职责分离：本服务只负责扫描发现，不直接写资产/字段表；
 * 技术元数据落库由 {@link TechnicalMetadataService}、资产注册由
 * {@link CatalogService#registerAsset} 完成。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscoveryService {

    /** 资产 Mapper（跨模块） */
    private final DamAssetMapper assetMapper;

    /** 字段 Mapper */
    private final DamFieldMapper fieldMapper;

    /** 采集记录 Mapper */
    private final DamMetadataCollectionMapper collectionMapper;

    /** 数据源 Mapper */
    private final DamDataSourceMapper dataSourceMapper;

    /** 来源系统 Mapper（用于扫描结果回填 sourceSystem 编码） */
    private final DamSourceSystemMapper sourceSystemMapper;

    /** 技术元数据 Schema Mapper（用于解析采集记录的 schema_id） */
    private final MetadataSchemaMapper schemaMapper;

    /** 资产注册入口（职责分离） */
    private final CatalogService catalogService;

    /** 数据源类型配置服务 */
    private final DataSourceTypeService dataSourceTypeService;

    /** 技术元数据落库服务 */
    private final TechnicalMetadataService technicalMetadataService;

    /** 密码加解密服务 */
    private final CryptoService cryptoService;

    /** dam-lineage 服务地址（用于外键血缘自动落库） */
    @Value("${dam.lineage-url:http://localhost:8093}")
    private String lineageUrl;

    // 扫描结果缓存：scanId -> {tables, timestamp}
    private static final Map<String, ScanCacheEntry> scanCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;

    /** 数据库标识符合法性正则（字母/数字/下划线/$/中文，拦截 % _ 通配符、引号、分号、注释符等注入字符） */
    private static final String VALID_IDENTIFIER_REGEX = "^[A-Za-z0-9_$\\u4e00-\\u9fa5]+$";

    /**
     * 外键血缘推送 HTTP 客户端。
     * java.net.http.HttpClient 线程安全、可复用，close() 为空实现；作为类级单例复用底层连接，
     * 避免每次请求新建/关闭客户端（同时规避 try-with-resources 对各 JDK 版本 AutoCloseable 支持的差异）。
     */
    private static final HttpClient LINEAGE_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 已注册的适配器列表（按优先级排序，DomesticAdapter 作为兜底放最后） */
    private final List<DataSourceAdapter> adapters = new ArrayList<>();

    /** 扫描缓存条目（表列表 + 缓存时间戳） */
    private record ScanCacheEntry(List<Map<String, Object>> tables, long timestamp) {}

    /**
     * 服务启动后注册各数据库适配器（PostgreSQL/MySQL/Oracle/SQLServer/IRIS + 国产兜底）
     */
    @PostConstruct
    public void init() {
        // 注册各数据库适配器（按优先级从高到低）
        adapters.add(new PostgresqlAdapter());
        adapters.add(new MysqlAdapter());
        adapters.add(new OracleAdapter());
        adapters.add(new SqlServerAdapter());
        adapters.add(new IrisAdapter());
        adapters.add(new DomesticAdapter()); // 兜底
        log.info("数据源适配器初始化完成, 共 {} 个适配器", adapters.size());
    }

    // ============================================================
    //  扫描入口
    // ============================================================

    public Map<String, Object> scanDataSource(String host, Integer port, String databaseName,
                                               String username, String password, String sourceType) {
        return scanDataSource(host, port, databaseName, username, password, sourceType, null, null);
    }

    public Map<String, Object> scanDataSource(String host, Integer port, String databaseName,
                                               String username, String password, String sourceType,
                                               List<String> schemas, Long dataSourceId) {
        String typeKey = sourceType.toUpperCase();
        DamDataSourceType typeConfig = getTypeConfig(typeKey);

        int actualPort = (port != null && port > 0) ? port : typeConfig.getDefaultPort();
        String url = buildUrl(typeConfig, host, actualPort, databaseName);

        // 加载驱动
        try {
            Class.forName(typeConfig.getDriverClass());
        } catch (ClassNotFoundException e) {
            log.warn("JDBC驱动未找到: {}, 尝试直接连接", typeConfig.getDriverClass());
        }

        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", username != null ? username : "");
        props.setProperty("password", cryptoService.decryptPassword(password));
        props.setProperty("loginTimeout", "30");
        props.setProperty("connectTimeout", "30000");

        List<Map<String, Object>> tables = new ArrayList<>();
        List<Map<String, Object>> foreignKeys = new ArrayList<>();
        // schemaId 在 try 块末尾统一赋值（resolveSchemaId），异常路径在 catch 中抛业务异常退出，故无需先置 null
        Long schemaId;

        long t0 = System.currentTimeMillis();

        try (Connection conn = DriverManager.getConnection(url, props)) {
            conn.setNetworkTimeout(java.util.concurrent.Executors.newSingleThreadExecutor(), 300000);
            DatabaseMetaData metaData = conn.getMetaData();

            // 获取对应适配器
            DataSourceAdapter adapter = resolveAdapter(conn, typeKey);
            String catalog = adapter.resolveCatalog(databaseName);
            log.info("连接+适配器解析耗时: {}ms", System.currentTimeMillis() - t0);

            // 确定待扫描 schemas
            List<String> targetSchemas;
            if (schemas != null && !schemas.isEmpty()) {
                targetSchemas = schemas;
            } else {
                long t1 = System.currentTimeMillis();
                targetSchemas = adapter.listSchemas(conn, catalog);
                log.info("  listSchemas 查询耗时: {}ms, 共 {} 个", System.currentTimeMillis() - t1, targetSchemas.size());
            }

            log.info("开始扫描数据源 [{}] {}:{}/{} 适配器={}, schema数={}",
                    typeKey, host, actualPort, databaseName,
                    adapter.getClass().getSimpleName(), targetSchemas.size());

            long tScan = System.currentTimeMillis();
            for (String schema : targetSchemas) {
                try {
                    scanTablesInSchema(conn, metaData, adapter, catalog, schema, tables);
                    // 收集外键关系（自动生成业务表 → 主数据表的引用血缘）
                    try {
                        foreignKeys.addAll(adapter.fetchForeignKeys(conn, metaData, catalog, schema));
                    } catch (SQLException fke) {
                        log.warn("获取 schema [{}] 外键信息失败: {}, 跳过", schema, fke.getMessage());
                    }
                } catch (SQLException e) {
                    log.warn("扫描 schema [{}] 失败: {}, 跳过继续", schema, e.getMessage());
                }
            }
            log.info("扫描表完成: {} 张表, 外键 {} 条, 耗时 {}ms", tables.size(), foreignKeys.size(), System.currentTimeMillis() - tScan);
            // 采集记录需要 schema_id：仅在单一 schema 且已落库时可解析，否则用 0 兑底
            schemaId = resolveSchemaId(dataSourceId, targetSchemas);
        } catch (SQLException e) {
            log.error("扫描数据源失败 [{}]: {}", typeKey, e.getMessage());
            throw new BusinessException("连接数据源失败: " + e.getMessage());
        }

        // 外键血缘自动落库（调用 dam-lineage 服务，失败不影响扫描主流程）
        if (!foreignKeys.isEmpty()) {
            pushForeignKeyLineage(foreignKeys, typeKey, dataSourceId);
        }

        // 预填已有的资产信息
        long t2 = System.currentTimeMillis();
        enrichWithExistingAssets(tables);
        log.info("  enrichWithExistingAssets 耗时: {}ms", System.currentTimeMillis() - t2);

        // 缓存 + 快照比对 + 记录采集日志
        long t3 = System.currentTimeMillis();
        Map<String, Object> result = buildScanResult(tables, host, actualPort, databaseName, typeKey,
                dataSourceId, schemaId);
        log.info("  buildScanResult 耗时: {}ms", System.currentTimeMillis() - t3);

        log.info("扫描总耗时: {}ms", System.currentTimeMillis() - t0);

        result.put("totalCount", tables.size());
        return result;
    }

    /**
     * 按数据源ID扫描（推荐：凭据由后端从数据库安全读取，前端不传密码）
     *
     * @param dataSourceId 数据源ID
     * @param schemas      待扫描的 schema 列表（为空则扫描全部）
     */
    public Map<String, Object> scanDataSource(Long dataSourceId, List<String> schemas) {
        DamDataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) throw new BusinessException("数据源不存在");
        if (ds.getStatus() == null || ds.getStatus() != 1) throw new BusinessException("数据源已停用");
        String typeKey = ds.getDsType();
        String databaseName = parseDbNameFromJdbc(ds.getJdbcUrl());
        String username = ds.getUsername();
        String password = cryptoService.decryptStorage(ds.getPassword());
        Integer port = parsePort(ds.getDsPort());
        return scanDataSource(ds.getDsIp(), port, databaseName, username, password, typeKey, schemas, dataSourceId);
    }

    /** 安全解析端口字符串，解析失败返回 null */
    private Integer parsePort(String portStr) {
        if (StrUtil.isBlank(portStr)) return null;
        try {
            return Integer.parseInt(portStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析采集记录的 schema_id（仅在恰好扫描单一 schema 且 metadata_schema 已落库时返回，否则返回 null 由调用方兑底）
     */
    private Long resolveSchemaId(Long dataSourceId, List<String> targetSchemas) {
        if (dataSourceId == null || targetSchemas == null || targetSchemas.size() != 1) return null;
        MetadataSchema ms = schemaMapper.selectOne(
                new LambdaQueryWrapper<MetadataSchema>()
                        .eq(MetadataSchema::getDatasourceId, dataSourceId)
                        .eq(MetadataSchema::getSchemaName, targetSchemas.getFirst())
                        .last("LIMIT 1"));
        return ms != null ? ms.getId() : null;
    }

    // ============================================================
    //  核心扫描逻辑
    // ============================================================

    /**
     * 扫描指定 schema 下的所有表
     */
    private void scanTablesInSchema(Connection conn, DatabaseMetaData metaData,
                                     DataSourceAdapter adapter, String catalog,
                                     String schema, List<Map<String, Object>> tables) throws SQLException {
        // 第1步: 获取所有表（由适配器决定使用 JDBC 还是直接 SQL）
        List<String> tableNames = adapter.listTableNames(conn, catalog, schema);
        if (tableNames.isEmpty()) return;

        // 构建 schemaTables 列表
        List<Map<String, Object>> schemaTables = new ArrayList<>();
        for (String name : tableNames) {
            Map<String, Object> table = new HashMap<>();
            table.put("tableName", name);
            table.put("tableType", "table");
            table.put("remarks", "");
            table.put("schemaName", schema);
            table.put("columns", new ArrayList<Map<String, Object>>());
            schemaTables.add(table);
        }

        // 第2步: 获取列信息（由适配器决定批量或逐表）
        Map<String, List<Map<String, Object>>> columnsMap = adapter.fetchColumns(metaData, catalog, schema, schemaTables);

        // 第3步: 批量获取主键信息（由适配器优化）
        Map<String, Set<String>> pkMap = adapter.fetchPrimaryKeys(conn, metaData, catalog, schema);

        // 组装
        for (Map<String, Object> table : schemaTables) {
            String tableName = (String) table.get("tableName");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cols = columnsMap.getOrDefault(tableName, Collections.emptyList());
            Set<String> pks = pkMap.getOrDefault(tableName, Collections.emptySet());
            for (Map<String, Object> col : cols) {
                col.put("isPrimaryKey", pks.contains((String) col.get("name")));
            }
            table.put("columns", cols);
            tables.add(table);
        }
    }

    // ============================================================
    //  外键血缘落库（调用 dam-lineage 服务）
    // ============================================================

    /**
     * 将扫描到的外键关系推送到 dam-lineage 服务，自动生成业务表 → 主数据表的引用血缘。
     * 失败仅记录日志，不影响扫描主流程。
     *
     * @param foreignKeys  外键列表 [{fromTable, fromColumn, toTable, toColumn, fkName}]
     * @param sourceSystem 来源系统（数据源类型）
     * @param dataSourceId 数据源ID
     */
    private void pushForeignKeyLineage(List<Map<String, Object>> foreignKeys, String sourceSystem, Long dataSourceId) {
        if (StrUtil.isBlank(lineageUrl)) return;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("foreignKeys", foreignKeys);
            body.put("sourceSystem", sourceSystem);
            body.put("collectionId", dataSourceId);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);

            // 复用类级单例客户端（线程安全），直接发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(lineageUrl + "/api/lineage/fk-lineage"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = LINEAGE_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("外键血缘落库: status={}, 响应={}", resp.statusCode(),
                    resp.body() != null && resp.body().length() > 200 ? resp.body().substring(0, 200) : resp.body());
        } catch (Exception e) {
            log.warn("外键血缘落库失败（不影响扫描主流程）: {}", e.getMessage());
        }
    }

    // ============================================================
    //  适配器与配置解析
    // ============================================================

    private DataSourceAdapter resolveAdapter(Connection conn, String typeKey) {
        for (DataSourceAdapter adapter : adapters) {
            if (adapter.supports(conn, typeKey)) {
                return adapter;
            }
        }
        // 不应走到这里，DomesticAdapter 总会返回 true
        return adapters.getLast();
    }

    private DamDataSourceType getTypeConfig(String typeKey) {
        DamDataSourceType config = dataSourceTypeService.getByTypeKey(typeKey);
        if (config == null) {
            throw new BusinessException("不支持的数据源类型: " + typeKey + "，请先在系统配置中添加该类型");
        }
        return config;
    }

    private String buildUrl(DamDataSourceType typeConfig, String host, int port, String databaseName) {
        return String.format(typeConfig.getUrlTemplate(), host, port, databaseName);
    }

    // ============================================================
    //  Schema 列表获取
    // ============================================================

    public List<String> listSchemas(String host, Integer port, String databaseName,
                                     String username, String password, String sourceType) {
        String typeKey = sourceType.toUpperCase();
        DamDataSourceType typeConfig = getTypeConfig(typeKey);
        int actualPort = (port != null && port > 0) ? port : typeConfig.getDefaultPort();
        // IRIS 的 URL 第三段为 Namespace，缺失时直接连到 %SYS 会返回 Access Denied，提前给出明确提示
        if ("IRIS".equals(typeKey) && StrUtil.isBlank(databaseName)) {
            throw new BusinessException("IRIS 数据源需填写 Namespace（如 USER、SAMPLES），请先在数据源管理中补全");
        }
        String url = buildUrl(typeConfig, host, actualPort, databaseName);

        try {
            Class.forName(typeConfig.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new BusinessException("JDBC驱动未找到: " + typeConfig.getDriverClass());
        }

        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", username != null ? username : "");
        props.setProperty("password", cryptoService.decryptPassword(password));
        props.setProperty("loginTimeout", "15");
        props.setProperty("connectTimeout", "15000");

        try (Connection conn = DriverManager.getConnection(url, props)) {
            DataSourceAdapter adapter = resolveAdapter(conn, typeKey);
            String catalog = adapter.resolveCatalog(databaseName);
            return adapter.listSchemas(conn, catalog);
        } catch (SQLException e) {
            log.error("获取 schema 列表失败 [{}]: {}", typeKey, e.getMessage());
            throw new BusinessException("获取 schema 列表失败: " + e.getMessage());
        }
    }

    /**
     * 按数据源ID获取 schema 列表（推荐：凭据由后端从数据库安全读取，前端不传密码）
     */
    public List<String> listSchemas(Long dataSourceId) {
        DamDataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) throw new BusinessException("数据源不存在");
        String typeKey = ds.getDsType();
        String databaseName = parseDbNameFromJdbc(ds.getJdbcUrl());
        String username = ds.getUsername();
        String password = cryptoService.decryptStorage(ds.getPassword());
        Integer port = parsePort(ds.getDsPort());
        return listSchemas(ds.getDsIp(), port, databaseName, username, password, typeKey);
    }

    // ============================================================
    //  连接测试
    // ============================================================

    public void testConnection(String sourceType, String host, Integer port,
                                String databaseName, String username, String password) {
        String typeKey = sourceType.toUpperCase();
        DamDataSourceType typeConfig = getTypeConfig(typeKey);
        int actualPort = (port != null && port > 0) ? port : typeConfig.getDefaultPort();
        // IRIS 的 URL 第三段为 Namespace，缺失时直接连到 %SYS 会返回 Access Denied，提前给出明确提示
        if ("IRIS".equals(typeKey) && StrUtil.isBlank(databaseName)) {
            throw new BusinessException("IRIS 数据源需填写 Namespace（如 USER、SAMPLES）");
        }
        String url = buildUrl(typeConfig, host, actualPort, databaseName);

        try {
            Class.forName(typeConfig.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new BusinessException("JDBC驱动未找到: " + typeConfig.getDriverClass());
        }

        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", username != null ? username : "");
        props.setProperty("password", cryptoService.decryptPassword(password));
        props.setProperty("loginTimeout", "10");
        props.setProperty("connectTimeout", "10000");

        try (Connection conn = DriverManager.getConnection(url, props)) {
            conn.isValid(5);
        } catch (SQLException e) {
            throw new BusinessException("连接失败: " + e.getMessage());
        }
    }

    /**
     * 按数据源ID测试连接（推荐：凭据由后端安全读取，前端不传密码）
     */
    public void testConnection(Long dataSourceId) {
        DamDataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) throw new BusinessException("数据源不存在");
        String typeKey = ds.getDsType();
        String databaseName = parseDbNameFromJdbc(ds.getJdbcUrl());
        String username = ds.getUsername();
        String password = cryptoService.decryptStorage(ds.getPassword());
        Integer port = parsePort(ds.getDsPort());
        testConnection(typeKey, ds.getDsIp(), port, databaseName, username, password);
    }

    // ============================================================
    //  支持的数据库类型列表（从配置表读取）
    // ============================================================

    /**
     * 返回数据源类型列表（用于前端下拉框）
     * 格式同 getConfig('source_type') 兼容: [{configCode, configName}]
     */
    public List<Map<String, String>> getSupportedTypes() {
        return dataSourceTypeService.getTypeSelectList();
    }

    /**
     * 返回数据源类型完整配置列表（用于管理端）
     */
    public List<DamDataSourceType> getDataSourceTypeList() {
        return dataSourceTypeService.getAllTypes();
    }

    // ============================================================
    //  结果处理与缓存
    // ============================================================

    private void enrichWithExistingAssets(List<Map<String, Object>> tables) {
        if (tables == null || tables.isEmpty()) return;
        List<String> tableNames = tables.stream()
                .map(t -> (String) t.get("tableName"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (tableNames.isEmpty()) return;

        // 新表 asset_info 无 table_name 列，资产名(asset_name)承载表名语义，直接按资产名匹配已注册资产
        // （tableName/sourceSystem 均为 exist=false 过渡字段，不能用于 LambdaQueryWrapper）
        List<DamAsset> existingList = assetMapper.selectList(
                new LambdaQueryWrapper<DamAsset>()
                        .in(DamAsset::getAssetName, tableNames)
                        .select(DamAsset::getAssetName, DamAsset::getCategoryId, DamAsset::getSourceSystemId));
        if (existingList.isEmpty()) return;

        // 批量解析来源系统编码（source_system_id → systemCode）
        Set<Long> systemIds = existingList.stream()
                .map(DamAsset::getSourceSystemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> systemCodeMap = new HashMap<>();
        if (!systemIds.isEmpty()) {
            for (DamSourceSystem sys : sourceSystemMapper.selectByIds(systemIds)) {
                if (sys != null) systemCodeMap.put(sys.getId(), sys.getSystemCode());
            }
        }

        Map<String, DamAsset> existingMap = new HashMap<>();
        for (DamAsset a : existingList) existingMap.put(a.getAssetName(), a);

        for (Map<String, Object> t : tables) {
            DamAsset existing = existingMap.get((String) t.get("tableName"));
            if (existing != null) {
                t.put("categoryId", existing.getCategoryId());
                if (existing.getSourceSystemId() != null) {
                    t.put("sourceSystem", systemCodeMap.getOrDefault(
                            existing.getSourceSystemId(), String.valueOf(existing.getSourceSystemId())));
                }
                t.put("existing", true);
            }
        }
    }

    private Map<String, Object> buildScanResult(List<Map<String, Object>> tables,
                                                  String host, int port, String databaseName, String typeKey,
                                                  Long dataSourceId, Long schemaId) {
        // 生成 scanId 并缓存
        String scanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        scanCache.put(scanId, new ScanCacheEntry(new ArrayList<>(tables), System.currentTimeMillis()));
        evictExpiredCache();

        // 轻量响应（不含 columns）
        List<Map<String, Object>> lightTables = new ArrayList<>();
        for (Map<String, Object> t : tables) {
            Map<String, Object> light = new HashMap<>(t);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cols = (List<Map<String, Object>>) light.remove("columns");
            light.put("columnCount", cols != null ? cols.size() : 0);
            lightTables.add(light);
        }

        int totalFields = tables.stream()
                .mapToInt(t -> ((List<?>) t.getOrDefault("columns", Collections.emptyList())).size())
                .sum();

        // 生成快照 JSON
        String snapshotJson = null;
        try {
            snapshotJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tables);
        } catch (Exception e) {
            log.warn("生成快照JSON失败", e);
        }

        // 与上一次采集记录比对
        boolean hasChanges = true;
        try {
            DamMetadataCollection prevCollection = collectionMapper.selectOne(
                    new LambdaQueryWrapper<DamMetadataCollection>()
                            .eq(DamMetadataCollection::getJobName, databaseName)
                            .eq(DamMetadataCollection::getStatus, 1)
                            .orderByDesc(DamMetadataCollection::getCreatedAt)
                            .last("LIMIT 1"));
            if (prevCollection != null && StrUtil.isNotBlank(prevCollection.getDetailLog())) {
                // compareWithPrevious 返回 null 表示无变更
                hasChanges = compareWithPrevious(prevCollection.getDetailLog(), snapshotJson, tables) != null;
            }
        } catch (Exception e) {
            log.warn("快照比对失败", e);
        }

        if (hasChanges) {
            recordCollection(typeKey, databaseName, host, port, databaseName, dataSourceId, schemaId,
                    tables.size(), totalFields, "success", null, snapshotJson);
        } else {
            DamMetadataCollection latest = collectionMapper.selectOne(
                    new LambdaQueryWrapper<DamMetadataCollection>()
                            .eq(DamMetadataCollection::getJobName, databaseName)
                            .eq(DamMetadataCollection::getStatus, 1)
                            .orderByDesc(DamMetadataCollection::getCreatedAt)
                            .last("LIMIT 1"));
            if (latest != null) {
                latest.setEndTime(LocalDateTime.now());
                collectionMapper.updateById(latest);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("scanId", scanId);
        result.put("tables", lightTables);
        result.put("hasChanges", hasChanges);
        return result;
    }

    // ============================================================
    //  快照比对（保持不变）
    // ============================================================

    private String compareWithPrevious(String prevSnapshotJson, String curSnapshotJson,
                                        List<Map<String, Object>> curTables) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> prevTables = mapper.readValue(prevSnapshotJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Map<String, Map<String, Object>> prevMap = new LinkedHashMap<>();
            for (Map<String, Object> t : prevTables) prevMap.put((String) t.get("tableName"), t);
            Map<String, Map<String, Object>> curMap = new LinkedHashMap<>();
            for (Map<String, Object> t : curTables) curMap.put((String) t.get("tableName"), t);

            List<Map<String, Object>> changes = new ArrayList<>();
            Set<String> allNames = new LinkedHashSet<>();
            allNames.addAll(prevMap.keySet());
            allNames.addAll(curMap.keySet());

            for (String name : allNames) {
                boolean inPrev = prevMap.containsKey(name);
                boolean inCur = curMap.containsKey(name);
                if (!inPrev && inCur) {
                    changes.add(Map.of("tableName", name, "changeType", "新增表", "detail", "新增表 " + name));
                } else if (inPrev && !inCur) {
                    changes.add(Map.of("tableName", name, "changeType", "删除表", "detail", "删除表 " + name));
                } else {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> prevCols = (List<Map<String, Object>>) prevMap.get(name).getOrDefault("columns", Collections.emptyList());
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> curCols = (List<Map<String, Object>>) curMap.get(name).getOrDefault("columns", Collections.emptyList());
                    Map<String, Map<String, Object>> prevColMap = new LinkedHashMap<>();
                    for (Map<String, Object> c : prevCols) prevColMap.put((String) c.get("name"), c);
                    Map<String, Map<String, Object>> curColMap = new LinkedHashMap<>();
                    for (Map<String, Object> c : curCols) curColMap.put((String) c.get("name"), c);

                    Set<String> allCols = new LinkedHashSet<>();
                    allCols.addAll(prevColMap.keySet());
                    allCols.addAll(curColMap.keySet());
                    for (String col : allCols) {
                        if (!prevColMap.containsKey(col)) {
                            changes.add(Map.of("tableName", name, "changeType", "新增字段",
                                    "fieldName", col, "detail", name + " 新增字段 " + col));
                        } else if (!curColMap.containsKey(col)) {
                            changes.add(Map.of("tableName", name, "changeType", "删除字段",
                                    "fieldName", col, "detail", name + " 删除字段 " + col));
                        } else {
                            Map<String, Object> pc = prevColMap.get(col);
                            Map<String, Object> cc = curColMap.get(col);
                            for (String prop : new String[]{"type", "size", "nullable", "isPrimaryKey", "comment"}) {
                                if (!Objects.equals(pc.get(prop), cc.get(prop))) {
                                    changes.add(Map.of("tableName", name, "changeType", "修改属性",
                                            "fieldName", col, "property", prop,
                                            "oldValue", String.valueOf(pc.get(prop)),
                                            "newValue", String.valueOf(cc.get(prop)),
                                            "detail", name + "." + col + " " + prop + ": " + pc.get(prop) + " → " + cc.get(prop)));
                                }
                            }
                        }
                    }
                }
            }
            if (changes.isEmpty()) return null;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("changes", changes);
            result.put("changeCount", changes.size());
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("快照比对过程异常", e);
            return null;
        }
    }

    // ============================================================
    //  批量注册
    // ============================================================

    @Transactional
    public List<DamAsset> batchRegister(List<Map<String, Object>> discoveredTables,
                                         String sourceSystem, String databaseName, String securityLevel) {
        return batchRegister(discoveredTables, sourceSystem, databaseName, securityLevel, null, null);
    }

    @Transactional
    public List<DamAsset> batchRegister(List<Map<String, Object>> discoveredTables,
                                         String sourceSystem, String databaseName, String securityLevel,
                                         String scanId) {
        return batchRegister(discoveredTables, sourceSystem, databaseName, securityLevel, scanId, null);
    }

    @Transactional
    public List<DamAsset> batchRegister(List<Map<String, Object>> discoveredTables,
                                         String sourceSystem, String databaseName, String securityLevel,
                                         String scanId, Long dataSourceId) {
        List<Map<String, Object>> fullTables = getFullTables(discoveredTables, scanId);

        // 解析数据源ID：优先使用显式传入的（推荐），其次按 database_name 匹配 dam_data_source（兼容旧逻辑）
        if (dataSourceId == null) {
            dataSourceId = resolveDataSourceId(databaseName);
        }

        // 1. 技术元数据落库（职责分离：采集数据先落 metadata_schema/metadata_table/metadata_column + dam_scan_job）
        Long scanJobId = null;
        if (dataSourceId != null) {
            // job_type 记录真实的数据源类型（缺失时由 createScanJob 兑底为 SCAN）
            String typeKey = null;
            DamDataSource ds = dataSourceMapper.selectById(dataSourceId);
            if (ds != null) typeKey = ds.getDsType();
            scanJobId = technicalMetadataService.createScanJob(dataSourceId, databaseName, null, null, typeKey, null);
            Set<String> schemas = new LinkedHashSet<>();
            for (Map<String, Object> t : fullTables) {
                if (t.get("schemaName") != null) schemas.add((String) t.get("schemaName"));
            }
            int saved = technicalMetadataService.persistScanResult(dataSourceId, fullTables, new ArrayList<>(schemas));
            int totalFields = fullTables.stream()
                    .mapToInt(t -> ((List<?>) t.getOrDefault("columns", Collections.emptyList())).size())
                    .sum();
            technicalMetadataService.finishScanJob(scanJobId, "success", fullTables.size(), totalFields,
                    schemas.size(), null, null, null);
            log.info("技术元数据落库完成: {} 张表, 数据源ID={}", saved, dataSourceId);
        } else {
            log.warn("未匹配到数据源(databaseName={})，跳过技术元数据落库，仅注册资产", databaseName);
        }

        // 2. 通过资产注册入口完成资产落地（职责分离：采集服务不直接操作资产表）
        return catalogService.registerAssets(fullTables, dataSourceId, scanJobId,
                sourceSystem, databaseName, securityLevel);
    }

    /**
     * 按 database_name 匹配数据源ID（弱关联退化路径，用于未显式传 dataSourceId 的旧接口）
     */
    private Long resolveDataSourceId(String databaseName) {
        if (StrUtil.isBlank(databaseName)) return null;
        List<DamDataSource> dsList = dataSourceMapper.selectList(
                new LambdaQueryWrapper<DamDataSource>()
                        .eq(DamDataSource::getDsName, databaseName)
                        .eq(DamDataSource::getStatus, 1)
                        .last("LIMIT 1"));
        return dsList.isEmpty() ? null : dsList.getFirst().getId();
    }

    // ============================================================
    //  采集记录
    // ============================================================

    public DamMetadataCollection recordCollection(String sourceType, String sourceName,
                                                    String host, Integer port, String databaseName,
                                                    Long dataSourceId, Long schemaId,
                                                    int tableCount, int fieldCount, String status,
                                                    String errorMsg, String snapshotJson) {
        DamMetadataCollection collection = new DamMetadataCollection();
        collection.setJobType(sourceType);
        collection.setJobName(sourceName);
        // metadata_collection 表 datasource_id/schema_id 为 NOT NULL，无法确定单一 schema 时用 0 兑底
        collection.setDatasourceId(dataSourceId != null ? dataSourceId : 0L);
        collection.setSchemaId(schemaId != null ? schemaId : 0L);
        collection.setTotalTables(tableCount);
        collection.setTotalColumns(fieldCount);
        collection.setStartTime(LocalDateTime.now().minusMinutes(5));
        collection.setEndTime(LocalDateTime.now());
        collection.setErrorMessage(errorMsg);
        collection.setDetailLog(snapshotJson);
        collection.setStatus(1);
        java.time.LocalDateTime now = LocalDateTime.now();
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);
        collection.setCreatedBy("system");
        collection.setUpdatedBy("system");
        collectionMapper.insert(collection);
        return collection;
    }

    // ============================================================
    //  缓存管理
    // ============================================================

    private List<Map<String, Object>> getFullTables(List<Map<String, Object>> userTables, String scanId) {
        if (scanId == null) return userTables;
        ScanCacheEntry entry = scanCache.get(scanId);
        if (entry == null) throw new BusinessException("扫描结果已过期，请重新扫描");
        Map<String, Map<String, Object>> userMap = new LinkedHashMap<>();
        for (Map<String, Object> t : userTables) userMap.put((String) t.get("tableName"), t);
        List<Map<String, Object>> fullList = new ArrayList<>();
        for (Map<String, Object> cached : entry.tables()) {
            Map<String, Object> full = new HashMap<>(cached);
            Map<String, Object> user = userMap.get((String) full.get("tableName"));
            if (user != null) {
                if (user.containsKey("categoryId")) full.put("categoryId", user.get("categoryId"));
                if (user.containsKey("sourceSystem")) full.put("sourceSystem", user.get("sourceSystem"));
            }
            fullList.add(full);
        }
        return fullList;
    }

    private void evictExpiredCache() {
        long now = System.currentTimeMillis();
        scanCache.values().removeIf(e -> (now - e.timestamp()) > CACHE_TTL_MS);
    }

    // ============================================================
    //  按需同步表字段
    // ============================================================

    /**
     * 按需同步单表的字段信息（不扫描时获取，用于"同步字段"按钮）
     * @param dataSourceId 数据源ID
     * @param schemaName 模式名
     * @param tableName 表名
     * @param assetId 资产ID（可选，如果传入则直接用，不用再查）
     * @return Map: {columns, changes, hasChanges}
     */
    public Map<String, Object> syncTableFields(Long dataSourceId, String schemaName, String tableName, Long assetId) {
        // 表名/schema 为前端可控输入，先做标识符合法性白名单校验，
        // 防止未受控输入注入 JDBC 元数据查询（DatabaseMetaData 按模式匹配、驱动内部构造 SQL）
        if (StrUtil.isBlank(tableName)) throw new BusinessException("表名不能为空");
        tableName = validateIdentifier(tableName, "表");
        schemaName = validateIdentifier(schemaName, "schema");

        // 从数据库查询数据源连接信息
        DamDataSource ds = catalogService.getDataSourceById(dataSourceId);
        if (ds == null) throw new BusinessException("数据源不存在");
    
        // 优先使用前端传入的 assetId 查找资产，避免 databaseName 映射不上的问题
        DamAsset asset = null;
        if (assetId != null) {
            asset = assetMapper.selectById(assetId);
        }
        if (asset == null) {
            // 新表 asset_info 无 table_name 列，资产名(asset_name)承载表名语义
            asset = assetMapper.selectOne(
                    new LambdaQueryWrapper<DamAsset>()
                            .eq(DamAsset::getDataSourceId, ds.getId())
                            .eq(DamAsset::getAssetName, tableName));
        }
    
        Long resolvedAssetId = asset != null ? asset.getId() : null;
    
        // 如果前端没传 schemaName，从资产记录中获取（资产表内值为系统落库数据，仍按白名单校验兜底）
        if (StrUtil.isBlank(schemaName) && asset != null && StrUtil.isNotBlank(asset.getSchemaName())) {
            schemaName = validateIdentifier(asset.getSchemaName(), "schema");
        }
    
        String typeKey = ds.getDsType();
        String host = ds.getDsIp();
        String databaseName = parseDbNameFromJdbc(ds.getJdbcUrl());
        String username = ds.getUsername();
        String password = cryptoService.decryptStorage(ds.getPassword());
    
        DamDataSourceType typeConfig = getTypeConfig(typeKey);
        String url = buildUrl(typeConfig, host, ds.getDsPort() != null ? Integer.parseInt(ds.getDsPort()) : 0, databaseName);
    
        try {
            Class.forName(typeConfig.getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new BusinessException("JDBC驱动未找到: " + typeConfig.getDriverClass());
        }
    
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("loginTimeout", "10");
        props.setProperty("connectTimeout", "10000");
    
        try (Connection conn = DriverManager.getConnection(url, props)) {
            // 获取适配器
            DataSourceAdapter adapter = resolveAdapter(conn, typeKey);
    
            // 获取当前数据库的列和主键
            List<Map<String, Object>> remoteColumns;
            Set<String> remotePks;
    
            if (adapter instanceof IrisAdapter iris) {
                remoteColumns = iris.fetchColumnsForTable(conn, schemaName, tableName);
                remotePks = iris.fetchPrimaryKeysForTable(conn, schemaName, tableName);
            } else {
                // 非 IRIS 数据库：用默认适配器获取
                remoteColumns = fetchColumnsForTableGeneric(conn, schemaName, tableName);
                remotePks = fetchPksForTableGeneric(conn, schemaName, tableName);
            }
    
            // 标记主键
            for (Map<String, Object> col : remoteColumns) {
                col.put("isPrimaryKey", remotePks.contains((String) col.get("name")));
            }
            
            // 同步字段的同时自动检测该表的外键引用，推送 dam-lineage 生成字段级引用血缘
            // （业务表字段 → 主数据表字段，幂等落库：节点 → hierarchy → column_lineage → metadata_lineage）
            List<Map<String, Object>> fks = fetchForeignKeysForTable(
                    conn, adapter.resolveCatalog(databaseName), schemaName, tableName);
            if (!fks.isEmpty()) {
                pushForeignKeyLineage(fks, typeKey, dataSourceId);
                log.info("同步字段 [{}] 检测到外键 {} 条，已推送血缘", tableName, fks.size());
            }
            // 把外键引用表名合并到字段（fromColumn → toTable），供落库记录 reference_table_name
            Map<String, String> refTableMap = new HashMap<>();
            for (Map<String, Object> fk : fks) {
                String fromCol = (String) fk.get("fromColumn");
                String toTable = (String) fk.get("toTable");
                if (StrUtil.isNotBlank(fromCol) && StrUtil.isNotBlank(toTable)) {
                    refTableMap.put(fromCol, toTable);
                }
            }
            if (!refTableMap.isEmpty()) {
                for (Map<String, Object> col : remoteColumns) {
                    String ref = refTableMap.get((String) col.get("name"));
                    if (StrUtil.isNotBlank(ref)) {
                        col.put("referenceTable", ref);
                    }
                }
            }
            
            List<Map<String, Object>> changes = new ArrayList<>();
            // resolvedAssetId != null 已隐含 asset != null（见上方三元赋值），无需重复判空
            if (resolvedAssetId != null && asset.getSourceMetadataId() != null) {
                // 已有资产：对比字段变更（新表字段按多态外键 metadata_table.id 关联，不再使用 asset_id）
                List<DamField> existingFields = fieldMapper.selectList(
                        new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, asset.getSourceMetadataId()));
                Map<String, DamField> existingMap = new HashMap<>();
                for (DamField f : existingFields) existingMap.put(f.getFieldName(), f);
    
                for (Map<String, Object> col : remoteColumns) {
                    String colName = (String) col.get("name");
                    DamField existing = existingMap.remove(colName);
                    if (existing == null) {
                        changes.add(Map.of("type", "新增", "fieldName", colName,
                                "detail", "新增字段 " + colName));
                    } else {
                        String oldType = existing.getDataType();
                        String newType = (String) col.get("type");
                        if (!Objects.equals(oldType, newType)) {
                            changes.add(Map.of("type", "修改", "fieldName", colName,
                                    "property", "type", "oldValue", oldType, "newValue", newType,
                                    "detail", colName + " 类型: " + oldType + " → " + newType));
                        }
                    }
                }
                // 剩余的 existingMap 中的字段已被删除
                for (DamField removed : existingMap.values()) {
                    changes.add(Map.of("type", "删除", "fieldName", removed.getFieldName(),
                            "detail", "删除字段 " + removed.getFieldName()));
                }
            }
    
            Map<String, Object> result = new HashMap<>();
            result.put("columns", remoteColumns);
            // IRIS 使用 INFORMATION_SCHEMA.INDEXES 查询（JDBC getIndexInfo 对 IRIS 驱动支持不全），其余库走通用 JDBC
            List<Map<String, Object>> indexes;
            if (adapter instanceof IrisAdapter iris) {
                indexes = iris.fetchIndexesForTable(conn, schemaName, tableName);
            } else {
                indexes = fetchIndexes(conn, schemaName, tableName);
            }
            result.put("indexes", indexes);
            result.put("changes", changes);
            result.put("hasChanges", !changes.isEmpty());
            result.put("assetId", resolvedAssetId);
            result.put("tableName", tableName);
            result.put("fkCount", fks.size());
            return result;
        } catch (SQLException e) {
            log.error("同步字段失败 [{}]: {}", tableName, e.getMessage());
            throw new BusinessException("同步字段失败: " + e.getMessage());
        }
    }

    /**
     * 确认同步字段变更（保存字段到数据库）
     * 职责分离：通过 CatalogService.syncAssetFields 完成资产字段落库
     */
    @Transactional
    public void confirmSyncFields(Long assetId, List<Map<String, Object>> columns) {
        catalogService.syncAssetFields(assetId, columns);
    }

    /** 通用方式获取单表列信息（非 IRIS 数据库） */
    private List<Map<String, Object>> fetchColumnsForTableGeneric(Connection conn, String schema, String tableName) {
        // 未受控输入防护：JDBC DatabaseMetaData 内部构造 SQL，先经白名单正则守卫（仅合法标识符）才进入查询，
        // 拦截 % _ 通配符、引号、分号、注释符等注入字符；非法输入直接返回空结果
        if (tableName == null || !tableName.matches(VALID_IDENTIFIER_REGEX)) return new ArrayList<>();
        if (schema != null && !schema.matches(VALID_IDENTIFIER_REGEX)) return new ArrayList<>();
        List<Map<String, Object>> cols = new ArrayList<>();
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, schema, tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> col = new HashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("TYPE_NAME"));
                    col.put("size", safeGetLong(rs, "COLUMN_SIZE"));
                    col.put("nullable", safeGetInt(rs, "NULLABLE") == 1);
                    col.put("defaultValue", rs.getString("COLUMN_DEF"));
                    col.put("comment", rs.getString("REMARKS"));
                    cols.add(col);
                }
            }
        } catch (SQLException e) {
            log.warn("获取表 [{}] 字段失败: {}", tableName, e.getMessage());
        }
        return cols;
    }

    /** 通用方式获取单表主键（非 IRIS 数据库） */
    private Set<String> fetchPksForTableGeneric(Connection conn, String schema, String tableName) {
        // 未受控输入防护：JDBC DatabaseMetaData 内部构造 SQL，先经白名单正则守卫（仅合法标识符）才进入查询，
        // 拦截 % _ 通配符、引号、分号、注释符等注入字符；非法输入直接返回空结果
        if (tableName == null || !tableName.matches(VALID_IDENTIFIER_REGEX)) return new HashSet<>();
        if (schema != null && !schema.matches(VALID_IDENTIFIER_REGEX)) return new HashSet<>();
        Set<String> pks = new HashSet<>();
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getPrimaryKeys(null, schema, tableName)) {
                while (rs.next()) {
                    pks.add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (SQLException e) {
            log.warn("获取表 [{}] 主键失败: {}", tableName, e.getMessage());
        }
        return pks;
    }

    /**
     * 获取单表的外键引用（业务表字段 → 主数据表字段）
     * 返回 [{fromTable, fromColumn, toTable, toColumn, fkName}]，
     * 供同步字段时自动生成字段级引用血缘（与扫描时 schema 级获取逻辑一致，仅限定单表）。
     * 注意：getImportedKeys 必须显式传 schema，传 null 时部分驱动查不到结果。
     *
     * @param conn      数据库连接
     * @param catalog   catalog（适配器解析后的库名，如 PostgreSQL 为 databaseName）
     * @param schema    schema 名
     * @param tableName 表名
     * @return 外键引用列表（获取失败返回空列表，不影响同步字段主流程）
     */
    private List<Map<String, Object>> fetchForeignKeysForTable(Connection conn, String catalog,
                                                               String schema, String tableName) {
        // 未受控输入防护：JDBC DatabaseMetaData 内部构造 SQL，先经白名单正则守卫（仅合法标识符）才进入查询，
        // 拦截 % _ 通配符、引号、分号、注释符等注入字符；非法输入直接返回空结果
        if (tableName == null || !tableName.matches(VALID_IDENTIFIER_REGEX)) return new ArrayList<>();
        if (schema != null && !schema.matches(VALID_IDENTIFIER_REGEX)) return new ArrayList<>();
        List<Map<String, Object>> fks = new ArrayList<>();
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getImportedKeys(catalog, schema, tableName)) {
                while (rs.next()) {
                    Map<String, Object> fk = new HashMap<>();
                    fk.put("fkName", rs.getString("FK_NAME"));
                    fk.put("fromTable", rs.getString("FKTABLE_NAME"));
                    fk.put("fromColumn", rs.getString("FKCOLUMN_NAME"));
                    fk.put("toTable", rs.getString("PKTABLE_NAME"));
                    fk.put("toColumn", rs.getString("PKCOLUMN_NAME"));
                    fks.add(fk);
                }
            }
        } catch (SQLException e) {
            log.warn("获取表 [{}] 外键信息失败，跳过血缘同步: {}", tableName, e.getMessage());
        }
        return fks;
    }

    /** 获取表的索引信息 */
    private List<Map<String, Object>> fetchIndexes(Connection conn, String schema, String tableName) {
        // 未受控输入防护：JDBC DatabaseMetaData 内部构造 SQL，先经白名单正则守卫（仅合法标识符）才进入查询，
        // 拦截 % _ 通配符、引号、分号、注释符等注入字符；非法输入直接返回空结果
        if (tableName == null || !tableName.matches(VALID_IDENTIFIER_REGEX)) return new ArrayList<>();
        if (schema != null && !schema.matches(VALID_IDENTIFIER_REGEX)) return new ArrayList<>();
        List<Map<String, Object>> indexes = new ArrayList<>();
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getIndexInfo(null, schema, tableName, false, false)) {
                // 先收集所有索引行，再组装
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName == null) continue;
                    Map<String, Object> row = new HashMap<>();
                    row.put("indexName", indexName);
                    row.put("unique", !rs.getBoolean("NON_UNIQUE"));
                    row.put("type", indexTypeName(rs.getShort("TYPE")));
                    row.put("columnName", rs.getString("COLUMN_NAME"));
                    rows.add(row);
                }
                // 按索引名分组
                Map<String, Map<String, Object>> indexMap = new LinkedHashMap<>();
                for (Map<String, Object> row : rows) {
                    String idxName = (String) row.get("indexName");
                    indexMap.computeIfAbsent(idxName, k -> {
                        Map<String, Object> idx = new HashMap<>();
                        idx.put("indexName", idxName);
                        idx.put("unique", row.get("unique"));
                        idx.put("type", row.get("type"));
                        idx.put("columns", new ArrayList<String>());
                        return idx;
                    });
                    @SuppressWarnings("unchecked")
                    List<String> cols = (List<String>) indexMap.get(idxName).get("columns");
                    cols.add((String) row.get("columnName"));
                }
                indexes.addAll(indexMap.values());
            }
        } catch (SQLException e) {
            log.warn("获取表 [{}] 索引失败: {}", tableName, e.getMessage());
        }
        return indexes;
    }

    private String indexTypeName(short type) {
        return switch (type) {
            case java.sql.DatabaseMetaData.tableIndexStatistic -> "统计";
            case java.sql.DatabaseMetaData.tableIndexClustered -> "聚集";
            case java.sql.DatabaseMetaData.tableIndexHashed -> "哈希";
            case java.sql.DatabaseMetaData.tableIndexOther -> "其他";
            default -> "";
        };
    }

    // ============================================================
    //  安全工具方法
    // ============================================================

    /**
     * 校验数据库标识符（schema/表名）合法性。
     * <p>
     * 数据库标识符无法使用 PreparedStatement 参数占位（JDBC DatabaseMetaData 按模式匹配、驱动内部构造 SQL），
     * 按 SQL 注入防护规范以白名单正则校验：仅允许字母/数字/下划线/$/中文，
     * 拦截 % _ 通配符、引号、分号、注释符等注入字符。null 或空白视为"不限定"（如 schema 缺省），返回 null。
     *
     * @param name      待校验标识符
     * @param paramName 参数名（用于异常提示，如 "表"/"schema"）
     * @return 清洗后的标识符（trim）；null/空白返回 null
     */
    private String validateIdentifier(String name, String paramName) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.matches(VALID_IDENTIFIER_REGEX)) {
            throw new BusinessException("非法" + paramName + "标识符: " + name);
        }
        return trimmed;
    }

    /** 从 JDBC URL 解析数据库名 */
    private String parseDbNameFromJdbc(String url) {
        if (StrUtil.isBlank(url)) return null;
        int idx = url.lastIndexOf('/');
        if (idx < 0) return null;
        String rest = url.substring(idx + 1);
        int q = rest.indexOf('?');
        return q > 0 ? rest.substring(0, q) : rest;
    }

    private int safeGetInt(ResultSet rs, String columnLabel) {
        try {
            return rs.getInt(columnLabel);
        } catch (SQLException e) {
            return 0;
        }
    }

    private Long safeGetLong(ResultSet rs, String columnLabel) {
        try {
            long val = rs.getLong(columnLabel);
            return rs.wasNull() ? null : val;
        } catch (SQLException e) {
            try {
                Object obj = rs.getObject(columnLabel);
                if (obj instanceof Number) return ((Number) obj).longValue();
            } catch (SQLException ignored) {}
            return null;
        }
    }
}

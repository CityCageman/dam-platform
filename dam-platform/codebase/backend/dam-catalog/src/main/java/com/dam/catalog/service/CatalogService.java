package com.dam.catalog.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dam.catalog.entity.*;
import com.dam.catalog.repository.*;
import com.dam.catalog.service.ConfigService;
import com.dam.common.exception.BusinessException;
import com.dam.common.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;

/**
 * 数据目录（Catalog）核心服务
 * <p>
 * 负责资产目录的注册/检索/详情、分类/标签/收藏/评价管理、
 * 数据源与来源系统维护、采集记录与版本对比等业务逻辑。
 * <p>
 * 职责分离约定：数据源扫描的落库统一经由本服务的
 * {@link #registerAsset} / {@link #syncAssetFields} 注册入口完成，
 * 采集服务不直接操作资产/字段表。
 * <p>
 * 注意：新表 asset_info 无 table_name/database_name/security_level 等旧列，
 * 资产与技术元数据通过 source_metadata_type/source_metadata_id 多态外键关联。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    /** 表名白名单（防 SQL 注入） */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 安全等级优先级：从 sys_config 的 security_level 配置加载（排序号越大等级越高）
     *
     * @return configCode → sortOrder 映射
     */
    private Map<String, Integer> getSecurityLevelPriority() {
        return configService.getConfigPriority("security_level");
    }

    private final DamCategoryMapper categoryMapper;
    /** 资产-主题域-类目归属 */
    private final AssetCategoryRelMapper assetCategoryRelMapper;
    /** 角色可见主题域 */
    private final SysRoleSubjectDomainMapper roleSubjectDomainMapper;
    /** 用户/角色轻量查询 */
    private final SysUserRefMapper sysUserRefMapper;
    private final DamAssetMapper assetMapper;
    private final DamFieldMapper fieldMapper;
    private final DamTagMapper tagMapper;
    private final DamAssetTagMapper assetTagMapper;
    private final DamFavoriteMapper favoriteMapper;
    private final DamRatingMapper ratingMapper;
    private final DamMetadataCollectionMapper metadataCollectionMapper;
    private final DamMetadataVersionMapper metadataVersionMapper;
    private final DamSourceSystemMapper sourceSystemMapper;
    private final DamDataSourceMapper dataSourceMapper;
    private final MetadataTableMapper metadataTableMapper;
    private final MetadataSchemaMapper schemaMapper;
    private final MetadataManagementMapper metadataManagementMapper;
    private final DataSourceTypeService dataSourceTypeService;
    private final DictService dictService;
    private final ConfigService configService;
    /** 密码加解密服务 */
    private final CryptoService cryptoService;

    // ==================== 分类管理 ====================

    /**
     * 获取分类标准类型列表
     */
    public List<Map<String, String>> getCategoryTypes() {
        List<Map<String, String>> types = new ArrayList<>();
        // 从数据库中查询所有已使用的分类（按 sort_order 排序）
        List<DamCategory> list = categoryMapper.selectList(
                new LambdaQueryWrapper<DamCategory>().orderByAsc(DamCategory::getSortOrder));
        for (DamCategory c : list) {
            Map<String, String> item = new HashMap<>();
            item.put("type", String.valueOf(c.getId()));
            item.put("name", c.getCategoryName());
            types.add(item);
        }
        return types;
    }

    /**
     * 获取分类树（可按类型过滤），返回前填充前端兼容字段 name
     */
    public List<DamCategory> getCategoryTree(String type) {
        LambdaQueryWrapper<DamCategory> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(type)) {
            wrapper.eq(DamCategory::getId, Long.valueOf(type));
        }
        wrapper.orderByAsc(DamCategory::getSortOrder);
        List<DamCategory> list = categoryMapper.selectList(wrapper);
        // 填充前端兼容字段 name（= categoryName）
        for (DamCategory c : list) {
            c.setName(c.getCategoryName());
            c.setType(c.getCategoryCode());
        }
        return list;
    }

    // ==================== 主题域 / 类目（两级结构） ====================

    /** “未分类”兑底分类编码（不属于任何主题域） */
    private static final String UNCATEGORIZED_CODE = "UNCATEGORIZED";

    /**
     * 全部主题域与类目（平铺：tree_level=1 主题域、2 类目），排除“未分类”，供分类管理使用
     */
    public List<DamCategory> listCategoryDomains() {
        List<DamCategory> list = categoryMapper.selectList(
                new LambdaQueryWrapper<DamCategory>()
                        .le(DamCategory::getTreeLevel, 2L)
                        .orderByAsc(DamCategory::getSortOrder)
                        .orderByAsc(DamCategory::getId));
        return list.stream()
                .filter(c -> !UNCATEGORIZED_CODE.equalsIgnoreCase(c.getCategoryCode()))
                .collect(Collectors.toList());
    }

    /**
     * 当前登录用户可见的主题域+类目（按角色授权过滤）
     * <p>admin 或未做角色授权的用户可见全部。</p>
     */
    public List<DamCategory> listVisibleCategoryDomains() {
        List<DamCategory> all = listCategoryDomains();
        Set<Long> visible = resolveVisibleDomainIds(currentUserId());
        if (visible == null) return all;
        return all.stream().filter(c -> {
            boolean isDomain = c.getTreeLevel() != null && c.getTreeLevel() == 1L;
            return isDomain ? visible.contains(c.getId())
                    : c.getParentId() != null && visible.contains(c.getParentId());
        }).collect(Collectors.toList());
    }

    /** 当前登录用户可见的主题域ID（供报表等其他模块复用）
     *
     * @return null 表示不受限（admin / 角色未配置）
     */
    public Set<Long> currentVisibleDomainIds() {
        return resolveVisibleDomainIds(currentUserId());
    }

    /** 当前登录用户ID（未登录返回 null） */
    private Long currentUserId() {
        try {
            Object id = StpUtil.getLoginIdDefaultNull();
            return id == null ? null : Long.valueOf(String.valueOf(id));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析用户可见的主题域ID集合
     *
     * @return null 表示不受限（admin / 角色未配置）
     */
    private Set<Long> resolveVisibleDomainIds(Long userId) {
        try {
            if (userId == null) return null;
            String username = sysUserRefMapper.selectUsernameById(userId);
            if ("admin".equalsIgnoreCase(username)) return null;
            List<Long> roleIds = sysUserRefMapper.selectRoleIdsByUserId(userId);
            if (roleIds == null || roleIds.isEmpty()) return null;
            List<SysRoleSubjectDomain> mappings = roleSubjectDomainMapper.selectList(
                    new LambdaQueryWrapper<SysRoleSubjectDomain>().in(SysRoleSubjectDomain::getRoleId, roleIds));
            if (mappings.isEmpty()) return null;
            return mappings.stream().map(SysRoleSubjectDomain::getSubjectDomainId).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("解析用户可见主题域失败，降级为全部可见: {}", e.getMessage());
            return null;
        }
    }

    /** 启用角色列表（用于配置角色可见主题域） */
    public List<Map<String, Object>> listRoles() {
        return sysUserRefMapper.selectRoles();
    }

    /** 角色的可见主题域ID列表 */
    public List<Long> listRoleSubjectDomainIds(Long roleId) {
        return roleSubjectDomainMapper.selectList(
                        new LambdaQueryWrapper<SysRoleSubjectDomain>()
                                .eq(SysRoleSubjectDomain::getRoleId, roleId))
                .stream().map(SysRoleSubjectDomain::getSubjectDomainId).collect(Collectors.toList());
    }

    /** 保存角色可见主题域（全量覆盖） */
    @Transactional
    public void saveRoleSubjectDomains(Long roleId, List<Long> domainIds) {
        roleSubjectDomainMapper.delete(new LambdaQueryWrapper<SysRoleSubjectDomain>()
                .eq(SysRoleSubjectDomain::getRoleId, roleId));
        if (domainIds == null || domainIds.isEmpty()) return;
        java.time.LocalDateTime now = LocalDateTime.now();
        String user = currentUser();
        for (Long did : new LinkedHashSet<>(domainIds)) {
            if (did == null) continue;
            SysRoleSubjectDomain m = new SysRoleSubjectDomain();
            m.setRoleId(roleId);
            m.setSubjectDomainId(did);
            m.setCreatedAt(now);
            m.setUpdatedAt(now);
            m.setCreatedBy(user);
            m.setUpdatedBy(user);
            roleSubjectDomainMapper.insert(m);
        }
    }

    /**
     * 保存资产在某个主题域下的类目归属（同一主题域只保留一个类目）
     *
     * @param assetId         资产ID
     * @param subjectDomainId 主题域ID（一级）；为空时由类目反推
     * @param categoryId      类目ID（二级）；为空表示取消该主题域归属
     */
    @Transactional
    public void saveAssetCategory(Long assetId, Long subjectDomainId, Long categoryId) {
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null) throw new BusinessException("资产不存在");
        java.time.LocalDateTime now = LocalDateTime.now();
        String user = currentUser();

        if (categoryId == null) {
            if (subjectDomainId != null) {
                assetCategoryRelMapper.delete(new LambdaQueryWrapper<AssetCategoryRel>()
                        .eq(AssetCategoryRel::getAssetId, assetId)
                        .eq(AssetCategoryRel::getSubjectDomainId, subjectDomainId));
            }
            return;
        }

        DamCategory cat = categoryMapper.selectById(categoryId);
        if (cat == null) throw new BusinessException("类目不存在");
        Long domainId = cat.getParentId() != null ? cat.getParentId() : subjectDomainId;
        if (domainId == null) throw new BusinessException("请选择类目所属主题域");

        AssetCategoryRel rel = assetCategoryRelMapper.selectOne(
                new LambdaQueryWrapper<AssetCategoryRel>()
                        .eq(AssetCategoryRel::getAssetId, assetId)
                        .eq(AssetCategoryRel::getSubjectDomainId, domainId)
                        .last("LIMIT 1"));
        if (rel == null) {
            rel = new AssetCategoryRel();
            rel.setAssetId(assetId);
            rel.setSubjectDomainId(domainId);
            rel.setCategoryId(categoryId);
            rel.setCreatedAt(now);
            rel.setUpdatedAt(now);
            rel.setCreatedBy(user);
            rel.setUpdatedBy(user);
            assetCategoryRelMapper.insert(rel);
        } else {
            rel.setCategoryId(categoryId);
            rel.setUpdatedAt(now);
            rel.setUpdatedBy(user);
            assetCategoryRelMapper.updateById(rel);
        }

        // 主分类同步（兼容旧逻辑：asset_info.category_id）
        if (!Objects.equals(asset.getCategoryId(), categoryId)) {
            asset.setCategoryId(categoryId);
            asset.setUpdatedAt(now);
            asset.setUpdatedBy(user);
            assetMapper.updateById(asset);
        }
    }

    /** 资产的多主题域归属 */
    public List<Map<String, Object>> listAssetCategories(Long assetId) {
        List<AssetCategoryRel> rels = assetCategoryRelMapper.selectList(
                new LambdaQueryWrapper<AssetCategoryRel>().eq(AssetCategoryRel::getAssetId, assetId));
        if (rels.isEmpty()) {
            // 兼容历史数据：尚无归属记录时用主分类回填展示
            DamAsset a = assetMapper.selectById(assetId);
            if (a != null && a.getCategoryId() != null) {
                DamCategory c = categoryMapper.selectById(a.getCategoryId());
                if (c != null) return List.of(categoryItem(c));
            }
            return Collections.emptyList();
        }
        Set<Long> catIds = rels.stream().map(AssetCategoryRel::getCategoryId).collect(Collectors.toSet());
        Map<Long, DamCategory> cats = categoryMapper.selectByIds(catIds).stream()
                .collect(Collectors.toMap(DamCategory::getId, c -> c, (x, y) -> x));
        List<Map<String, Object>> result = new ArrayList<>();
        for (AssetCategoryRel r : rels) {
            DamCategory c = cats.get(r.getCategoryId());
            if (c == null) continue;
            Map<String, Object> item = categoryItem(c);
            item.put("subjectDomainId", r.getSubjectDomainId());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> categoryItem(DamCategory c) {
        Map<String, Object> item = new LinkedHashMap<>();
        Long domainId = c.getParentId();
        String domainName = "";
        if (domainId != null) {
            DamCategory d = categoryMapper.selectById(domainId);
            if (d != null) domainName = d.getCategoryName();
        }
        item.put("subjectDomainId", domainId);
        item.put("subjectDomainName", domainName);
        item.put("categoryId", c.getId());
        item.put("categoryName", c.getCategoryName());
        return item;
    }

    /** 批量填充资产的多主题域归属（列表用，避免 N+1） */
    private void fillBatchAssetCategories(List<DamAsset> assets) {
        if (assets == null || assets.isEmpty()) return;
        List<Long> assetIds = assets.stream().map(DamAsset::getId).collect(Collectors.toList());
        List<AssetCategoryRel> rels = assetCategoryRelMapper.selectList(
                new LambdaQueryWrapper<AssetCategoryRel>().in(AssetCategoryRel::getAssetId, assetIds));
        Map<Long, List<AssetCategoryRel>> byAsset = rels.stream()
                .collect(Collectors.groupingBy(AssetCategoryRel::getAssetId));
        Set<Long> catIds = new HashSet<>();
        rels.forEach(r -> catIds.add(r.getCategoryId()));
        assets.forEach(a -> { if (a.getCategoryId() != null) catIds.add(a.getCategoryId()); });
        if (catIds.isEmpty()) return;
        Map<Long, DamCategory> cats = categoryMapper.selectByIds(catIds).stream()
                .collect(Collectors.toMap(DamCategory::getId, c -> c, (x, y) -> x));
        Set<Long> domainIds = cats.values().stream().map(DamCategory::getParentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DamCategory> domains = domainIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectByIds(domainIds).stream()
                        .collect(Collectors.toMap(DamCategory::getId, c -> c, (x, y) -> x));

        for (DamAsset a : assets) {
            List<AssetCategoryRel> list = byAsset.getOrDefault(a.getId(), Collections.emptyList());
            List<Map<String, Object>> items = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            if (list.isEmpty()) {
                DamCategory c = a.getCategoryId() != null ? cats.get(a.getCategoryId()) : null;
                if (c != null) {
                    appendCategoryItem(items, texts, c, c.getParentId(), domains);
                }
            } else {
                for (AssetCategoryRel r : list) {
                    DamCategory c = cats.get(r.getCategoryId());
                    if (c != null) appendCategoryItem(items, texts, c, r.getSubjectDomainId(), domains);
                }
            }
            a.setCategories(items);
            a.setCategoryPathText(String.join("；", texts));
        }
    }

    private void appendCategoryItem(List<Map<String, Object>> items, List<String> texts, DamCategory c,
                                    Long domainId, Map<Long, DamCategory> domains) {
        DamCategory d = domainId != null ? domains.get(domainId) : null;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("subjectDomainId", domainId);
        item.put("subjectDomainName", d != null ? d.getCategoryName() : "");
        item.put("categoryId", c.getId());
        item.put("categoryName", c.getCategoryName());
        items.add(item);
        texts.add((d != null ? d.getCategoryName() + "/" : "") + c.getCategoryName());
    }

    @Transactional
    public DamCategory createCategory(DamCategory category) {
        // 兼容前端字段：name → categoryName
        if (StrUtil.isBlank(category.getCategoryName()) && StrUtil.isNotBlank(category.getName())) {
            category.setCategoryName(category.getName());
        }
        // 分类编码为空时自动生成
        if (StrUtil.isBlank(category.getCategoryCode())) {
            category.setCategoryCode("CAT_" + System.currentTimeMillis() % 1000000);
        }
        // 父分类：树层级 = 父层级 + 1
        if (category.getParentId() != null && category.getParentId() > 0) {
            DamCategory parent = categoryMapper.selectById(category.getParentId());
            if (parent == null) throw new BusinessException("上级分类不存在");
            long parentLevel = parent.getTreeLevel() == null ? 1L : parent.getTreeLevel();
            if (parentLevel >= 2L) {
                throw new BusinessException("资产分类最多两级：主题域 → 类目");
            }
            category.setTreeLevel(2L);
            String basePath = StrUtil.isNotBlank(parent.getCategoryPath())
                    ? parent.getCategoryPath() : "/" + parent.getCategoryName();
            category.setCategoryPath(basePath + "/" + category.getCategoryName());
        } else {
            category.setTreeLevel(1L);
            category.setParentId(null);
        }
        if (StrUtil.isBlank(category.getCategoryPath())) {
            category.setCategoryPath("/" + category.getCategoryName());
        }
        if (category.getStatus() == null) category.setStatus(1);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        if (category.getCreatedBy() == null) category.setCreatedBy("system");
        if (category.getUpdatedBy() == null) category.setUpdatedBy("system");
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public DamCategory updateCategory(DamCategory category) {
        categoryMapper.updateById(category);
        return category;
    }

    @Transactional
    public void deleteCategory(Long id) {
        // 递归删除所有子分类
        deleteChildCategories(id);
        // 检查是否有资产关联
        long assetCount = assetMapper.selectCount(
                new LambdaQueryWrapper<DamAsset>().eq(DamAsset::getCategoryId, id));
        if (assetCount > 0) {
            throw new BusinessException("该分类下有资产，无法删除");
        }
        // 清理归属关系与角色可见配置，避免脏数据
        assetCategoryRelMapper.delete(new LambdaQueryWrapper<AssetCategoryRel>()
                .eq(AssetCategoryRel::getCategoryId, id)
                .or().eq(AssetCategoryRel::getSubjectDomainId, id));
        roleSubjectDomainMapper.delete(new LambdaQueryWrapper<SysRoleSubjectDomain>()
                .eq(SysRoleSubjectDomain::getSubjectDomainId, id));
        categoryMapper.deleteById(id);
    }

    /**
     * 递归删除子分类
     */
    private void deleteChildCategories(Long parentId) {
        List<DamCategory> children = categoryMapper.selectList(
                new LambdaQueryWrapper<DamCategory>().eq(DamCategory::getParentId, parentId));
        for (DamCategory child : children) {
            // 先递归删除子分类的子分类
            deleteChildCategories(child.getId());
            // 检查被删除的子分类是否有资产关联
            long assetCount = assetMapper.selectCount(
                    new LambdaQueryWrapper<DamAsset>().eq(DamAsset::getCategoryId, child.getId()));
            if (assetCount > 0) {
                throw new BusinessException("该分类下有资产，无法删除");
            }
            categoryMapper.deleteById(child.getId());
        }
    }

    // ==================== 资产管理 ====================

    /**
     * 资产注册入口（职责分离核心接口）
     * <p>
     * 采集服务（DiscoveryService）不得直接操作资产表，必须通过本方法完成数据落地。
     * 资产通过 data_source_id + scan_job_id 与数据源、扫描作业强关联。
     *
     * @param table         扫描得到的单表信息（tableName/tableType/schemaName/remarks/columns/categoryId）
     * @param dataSourceId  数据源ID（可为空，空则退化为按 table_name 匹配）
     * @param scanJobId     扫描作业ID
     * @param sourceSystem  来源系统
     * @param databaseName  数据库名
     * @param securityLevel 安全等级
     * @return 注册后的资产
     */
    @Transactional
    public DamAsset registerAsset(Map<String, Object> table, Long dataSourceId, Long scanJobId,
                                  String sourceSystem, String databaseName, String securityLevel) {
        String tableName = (String) table.get("tableName");
        String tableType = (String) table.get("tableType");
        String schemaName = (String) table.get("schemaName");
        String remark = (String) table.get("remarks");
        Long categoryId = table.get("categoryId") != null
                ? ((Number) table.get("categoryId")).longValue() : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = (List<Map<String, Object>>) table.get("columns");

        // 优先按强关联(data_source_id + 资产名)查找，退化为按资产名匹配
        // 注意：新表 asset_info 无 table_name 列，用 asset_name 承载表名语义
        LambdaQueryWrapper<DamAsset> qw = new LambdaQueryWrapper<DamAsset>()
                .eq(DamAsset::getAssetName, tableName);
        if (dataSourceId != null) {
            qw.eq(DamAsset::getDataSourceId, dataSourceId);
        } else if (StrUtil.isNotBlank(databaseName)) {
            qw.eq(DamAsset::getAssetName, tableName);
        }
        DamAsset asset = assetMapper.selectOne(qw.last("LIMIT 1"));
        boolean isNew = asset == null;
        if (isNew) {
            asset = new DamAsset();
        } else {
            // 仅当本次携带字段信息时才重建字段，避免空同步（按需扫描）误删已有字段（含手动补充的注释）
            if (columns != null && !columns.isEmpty()) {
                fieldMapper.delete(new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, asset.getSourceMetadataId()));
            }
        }

        asset.setAssetName(tableName);
        asset.setAssetCode(StrUtil.isNotBlank((String) table.get("assetCode"))
                ? (String) table.get("assetCode") : "AUTO_" + tableName);
        asset.setAssetType(tableType != null ? tableType : "table");
        // asset_type_id 为 NOT NULL：tableType 映射 asset_type 字典，映射不到时兑底取第一个启用项
        asset.setAssetTypeId(resolveAssetTypeId(tableType));
        // asset_info.category_id 为 NOT NULL：新增未指定分类时归入"未分类"默认分类；更新时保留原分类
        if (categoryId == null) {
            categoryId = isNew ? resolveDefaultCategoryId() : asset.getCategoryId();
        }
        asset.setCategoryId(categoryId);
        asset.setSourceSystem(sourceSystem);
        asset.setDatabaseName(databaseName);
        asset.setSchemaName(schemaName);
        asset.setTableName(tableName);
        asset.setDataSourceId(dataSourceId);
        asset.setSourceMetadataType("TABLE");
        // 确保技术元数据表存在并建立多态外键关联（source_metadata_id=metadata_table.id）
        asset.setSourceMetadataId(ensureMetadataTable(tableName, tableType, schemaName, dataSourceId, remark));
        asset.setSecurityLevel(StrUtil.isNotBlank(securityLevel) ? securityLevel : "internal");
        asset.setStatus(1);
        asset.setDescription(StrUtil.isNotBlank(remark) ? remark : asset.getDescription());
        // asset_info 审计字段（created_by/updated_by）为 NOT NULL，必须显式赋值
        if (isNew) {
            java.time.LocalDateTime now = LocalDateTime.now();
            asset.setCreatedAt(now);
            asset.setUpdatedAt(now);
            asset.setCreatedBy(currentUser());
            asset.setUpdatedBy(currentUser());
            assetMapper.insert(asset);
        } else {
            asset.setUpdatedAt(LocalDateTime.now());
            asset.setUpdatedBy(currentUser());
            assetMapper.updateById(asset);
        }

        // 分类归属：指定类目时同步主题域归属关系（同域唯一）
        syncAssetCategoryRel(asset.getId(), categoryId);

        if (columns != null) {
            int sort = 1;
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            // 字段按 metadata_table.id 落库到 metadata_column（新表无 asset_id 列）
            Long tableId = asset.getSourceMetadataId();
            // metadata_column 的 datasource_id/schema_id 为 NOT NULL，需显式赋值（schema_id 无法定位时用 0 兑底）
            Long schemaId = 0L;
            MetadataTable mt = metadataTableMapper.selectById(tableId);
            if (mt != null && mt.getSchemaId() != null) schemaId = mt.getSchemaId();
            for (Map<String, Object> col : columns) {
                DamField field = new DamField();
                field.setTableId(tableId);
                field.setDatasourceId(dataSourceId);
                field.setSchemaId(schemaId);
                field.setFieldName(truncate((String) col.get("name"), 100));
                field.setDataType(truncate((String) col.get("type"), 100));
                field.setDataLength(col.get("size") instanceof Number ? ((Number) col.get("size")).intValue() : null);
                field.setIsPrimaryKey(Boolean.TRUE.equals(col.get("isPrimaryKey")));
                field.setIsNullable(Boolean.TRUE.equals(col.get("nullable")));
                field.setDefaultValue(truncate((String) col.get("defaultValue"), 300));
                field.setComment(truncate((String) col.get("comment"), 200));
                field.setSortOrder(sort++);
                field.setCreatedAt(now);
                field.setUpdatedAt(now);
                field.setCreatedBy("system");
                field.setUpdatedBy("system");
                fieldMapper.insert(field);
            }
        }
        calculateAssetSecurityLevel(asset.getId());
        return asset;
    }

    /**
     * 资产分类归属同步：categoryId 为二级类目时，写入（或更新）对应主题域的归属记录
     * <p>一级主题域/未分类不建归属；同一主题域已存在归属时不重复插入。</p>
     */
    private void syncAssetCategoryRel(Long assetId, Long categoryId) {
        if (assetId == null || categoryId == null) return;
        DamCategory cat = categoryMapper.selectById(categoryId);
        if (cat == null || cat.getParentId() == null) return;
        boolean exists = assetCategoryRelMapper.exists(new LambdaQueryWrapper<AssetCategoryRel>()
                .eq(AssetCategoryRel::getAssetId, assetId)
                .eq(AssetCategoryRel::getSubjectDomainId, cat.getParentId()));
        if (exists) return;
        java.time.LocalDateTime now = LocalDateTime.now();
        String user = currentUser();
        AssetCategoryRel rel = new AssetCategoryRel();
        rel.setAssetId(assetId);
        rel.setSubjectDomainId(cat.getParentId());
        rel.setCategoryId(categoryId);
        rel.setCreatedAt(now);
        rel.setUpdatedAt(now);
        rel.setCreatedBy(user);
        rel.setUpdatedBy(user);
        try {
            assetCategoryRelMapper.insert(rel);
        } catch (Exception e) {
            // 并发下唯一索引冲突：忽略
            log.debug("资产分类归属已存在，跳过: assetId={}, domainId={}", assetId, cat.getParentId());
        }
    }

    /**
     * 将表类型（table/view/file/api）映射为 asset_type 字典项ID
     * 映射不到时兑底取字典第一个启用项，保证 asset_info.asset_type_id 非空
     */
    private Long resolveAssetTypeId(String tableType) {
        List<SysDictData> dicts = dictService.getDictDataByTypeCode("asset_type");
        if (dicts.isEmpty()) return null;
        if (StrUtil.isNotBlank(tableType)) {
            for (SysDictData d : dicts) {
                if (tableType.equalsIgnoreCase(d.getDictValue())) return d.getId();
            }
        }
        return dicts.getFirst().getId();
    }

    /**
     * 获取"未分类"默认分类ID（不存在则自动创建）
     * <p>支持先扫描注册、后续再补充分类的场景：注册时未指定分类的资产统一归入"未分类"</p>
     */
    private Long resolveDefaultCategoryId() {
        DamCategory def = categoryMapper.selectOne(
                new LambdaQueryWrapper<DamCategory>()
                        .eq(DamCategory::getCategoryCode, "UNCATEGORIZED")
                        .last("LIMIT 1"));
        if (def != null) return def.getId();
        DamCategory cat = new DamCategory();
        cat.setCategoryCode("UNCATEGORIZED");
        cat.setCategoryName("未分类");
        cat.setTreeLevel(1L);
        cat.setCategoryPath("/未分类");
        cat.setSortOrder(0L);
        cat.setStatus(1);
        java.time.LocalDateTime now = LocalDateTime.now();
        cat.setCreatedAt(now);
        cat.setUpdatedAt(now);
        cat.setCreatedBy("system");
        cat.setUpdatedBy("system");
        try {
            categoryMapper.insert(cat);
        } catch (Exception e) {
            // 并发重复创建时回查已有记录（asset_category.category_code 有唯一索引）
            DamCategory existing = categoryMapper.selectOne(
                    new LambdaQueryWrapper<DamCategory>()
                            .eq(DamCategory::getCategoryCode, "UNCATEGORIZED")
                            .last("LIMIT 1"));
            if (existing != null) return existing.getId();
            throw e;
        }
        return cat.getId();
    }

    /** 获取当前登录用户（未登录/异常时返回 system） */
    private String currentUser() {
        try {
            Object id = StpUtil.getLoginIdDefaultNull();
            return id != null ? String.valueOf(id) : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    /** 字符串按长度截断（null 安全），防止超过数据库列长度限制 */
    private String truncate(String s, int maxLen) {
        return (s != null && s.length() > maxLen) ? s.substring(0, maxLen) : s;
    }

    /**
     * 确保技术元数据表记录存在（metadata_table），返回其ID
     * <p>
     * 职责分离：资产通过 source_metadata_type/source_metadata_id 多态外键
     * 关联技术元数据实体，字段（metadata_column）按 table_id 归属该表。
     *
     * @param tableName     表名
     * @param tableType     表类型（TABLE/VIEW）
     * @param schemaName    Schema 名
     * @param dataSourceId  数据源ID
     * @param tableComment  表注释
     * @return metadata_table.id（新建或复用）
     */
    private Long ensureMetadataTable(String tableName, String tableType, String schemaName,
                                     Long dataSourceId, String tableComment) {
        // 1. 尝试按 数据源ID + 表名 查找已存在的技术元数据表
        MetadataTable mt = metadataTableMapper.selectOne(
                new LambdaQueryWrapper<MetadataTable>()
                        .eq(MetadataTable::getTableName, tableName)
                        .eq(dataSourceId != null, MetadataTable::getDatasourceId, dataSourceId)
                        .last("LIMIT 1"));
        // 2. 不存在则新建
        if (mt == null) {
            mt = new MetadataTable();
            mt.setDatasourceId(dataSourceId);
            mt.setTableName(tableName);
            mt.setTableType(tableType != null ? tableType.toUpperCase() : "TABLE");
            mt.setTableComment(tableComment);
            // 补全时间与审计字段
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            mt.setCreatedTime(now);
            mt.setLastModified(now);
            mt.setLastSyncTime(now);
            mt.setStatus(1);
            mt.setCreatedAt(now);
            mt.setUpdatedAt(now);
            mt.setCreatedBy("system");
            mt.setUpdatedBy("system");
            metadataTableMapper.insert(mt);
        }
        return mt.getId();
    }

    /**
     * 批量资产注册入口
     */
    @Transactional
    public List<DamAsset> registerAssets(List<Map<String, Object>> tables, Long dataSourceId, Long scanJobId,
                                         String sourceSystem, String databaseName, String securityLevel) {
        List<DamAsset> created = new ArrayList<>();
        for (Map<String, Object> table : tables) {
            created.add(registerAsset(table, dataSourceId, scanJobId, sourceSystem, databaseName, securityLevel));
        }
        return created;
    }

    /**
     * 确认同步资产字段（按需同步字段的落库入口，职责分离：采集服务不直接操作 dam_field）
     *
     * @param assetId 资产ID
     * @param columns 远程扫描到的字段列表
     */
    @Transactional
    public void syncAssetFields(Long assetId, List<Map<String, Object>> columns) {
        // 通过资产的多态外键定位技术元数据表ID（metadata_table.id）
        DamAsset asset = assetMapper.selectById(assetId);
        Long tableId = asset != null ? asset.getSourceMetadataId() : null;
        if (tableId == null) {
            throw new BusinessException("资产未关联技术元数据表，无法同步字段");
        }
        // metadata_column 的 datasource_id/schema_id 为 NOT NULL，需从资产与技术元数据解析（schema_id 无法定位时用 0 兑底）
        Long dataSourceId = asset.getDataSourceId();
        Long schemaId = 0L;
        MetadataTable mt = metadataTableMapper.selectById(tableId);
        if (mt != null && mt.getSchemaId() != null) schemaId = mt.getSchemaId();
        // 先加载已有字段：重新同步时保留手动补充的注释/默认值，避免被远程空值覆盖
        List<DamField> existingList = fieldMapper.selectList(
                new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, tableId));
        Map<String, DamField> existingMap = existingList.stream()
                .filter(f -> f.getFieldName() != null)
                .collect(Collectors.toMap(DamField::getFieldName, f -> f, (a, b) -> a));
        // 清空该表原有字段（metadata_column 按 table_id 关联）
        fieldMapper.delete(new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, tableId));
        int sort = 1;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (Map<String, Object> col : columns) {
            DamField field = new DamField();
            field.setTableId(tableId);
            field.setDatasourceId(dataSourceId);
            field.setSchemaId(schemaId);
            field.setFieldName(truncate((String) col.get("name"), 100));
            field.setDataType(truncate((String) col.get("type"), 100));
            field.setDataLength(col.get("size") instanceof Number ? ((Number) col.get("size")).intValue() : null);
            field.setIsPrimaryKey(Boolean.TRUE.equals(col.get("isPrimaryKey")));
            field.setIsNullable(Boolean.TRUE.equals(col.get("nullable")));
            field.setDefaultValue(truncate((String) col.get("defaultValue"), 300));
            field.setComment(truncate((String) col.get("comment"), 200));
            field.setReferenceTableName(truncate((String) col.get("referenceTable"), 200));
            // 远程未提供注释/默认值时，保留该字段已存在的手动维护值（防止重同步覆盖人工补充内容）
            DamField old = field.getFieldName() != null ? existingMap.get(field.getFieldName()) : null;
            if (old != null) {
                if (StrUtil.isBlank(field.getComment()) && old.getComment() != null) {
                    field.setComment(old.getComment());
                }
                if (StrUtil.isBlank(field.getDefaultValue()) && old.getDefaultValue() != null) {
                    field.setDefaultValue(old.getDefaultValue());
                }
            }
            field.setSortOrder(sort++);
            field.setCreatedAt(now);
            field.setUpdatedAt(now);
            field.setCreatedBy("system");
            field.setUpdatedBy("system");
            fieldMapper.insert(field);
        }
        calculateAssetSecurityLevel(assetId);
    }

    public PageResult<DamAsset> searchAssets(String keyword, String assetType, Long categoryId,
                                              String securityLevel, Long tagId, String databaseName,
                                              Long dataSourceId, String subjectDomain, Long subjectDomainId,
                                              String sortBy,
                                              Integer page, Integer pageSize) {
        return searchAssets(keyword, assetType, categoryId, securityLevel, tagId, databaseName,
                dataSourceId, subjectDomain, subjectDomainId, sortBy, page, pageSize, false);
    }

    /**
     * 资产检索
     *
     * @param enforceDomainPermission 是否按当前角色可见主题域收敛结果（资产目录页传 true；元数据浏览等场景传 false）
     */
    public PageResult<DamAsset> searchAssets(String keyword, String assetType, Long categoryId,
                                              String securityLevel, Long tagId, String databaseName,
                                              Long dataSourceId, String subjectDomain, Long subjectDomainId,
                                              String sortBy,
                                              Integer page, Integer pageSize,
                                              boolean enforceDomainPermission) {
        Page<DamAsset> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<DamAsset> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(DamAsset::getAssetName, keyword)
                    .or().like(DamAsset::getDescription, keyword));
        }
        // 按资产类型过滤（新表用 source_metadata_type 承载 TABLE/VIEW/FILE/API 类型）
        if (StrUtil.isNotBlank(assetType)) {
            wrapper.eq(DamAsset::getSourceMetadataType, assetType.toUpperCase());
        }
        // 按数据源过滤：优先 dataSourceId，其次按 databaseName 解析数据源后按 data_source_id 过滤
        if (dataSourceId != null) {
            wrapper.eq(DamAsset::getDataSourceId, dataSourceId);
        } else if (StrUtil.isNotBlank(databaseName)) {
            List<DamDataSource> dsList = dataSourceMapper.selectList(
                    new LambdaQueryWrapper<DamDataSource>()
                            .and(w -> w.eq(DamDataSource::getDsName, databaseName)
                                    .or().like(DamDataSource::getJdbcUrl, "%" + databaseName))
                            .last("LIMIT 20"));
            if (dsList.isEmpty()) {
                // 未匹配到数据源：按资产名模糊匹配兑底（兼容旧逻辑）
                wrapper.like(DamAsset::getAssetName, databaseName);
            } else {
                List<Long> dsIds = dsList.stream().map(DamDataSource::getId).collect(Collectors.toList());
                wrapper.in(DamAsset::getDataSourceId, dsIds);
            }
        }
        // 按主题域过滤：subject_domain 为 metadata_table 列，先查表ID集合再按 source_metadata_id 过滤资产；
        // 特殊值 NONE 表示“未设置主题域”
        if (StrUtil.isNotBlank(subjectDomain)) {
            if ("NONE".equals(subjectDomain)) {
                List<MetadataTable> withDomain = metadataTableMapper.selectList(
                        new LambdaQueryWrapper<MetadataTable>().isNotNull(MetadataTable::getSubjectDomain));
                if (!withDomain.isEmpty()) {
                    Set<Long> excludedIds = withDomain.stream().map(MetadataTable::getId).collect(Collectors.toSet());
                    wrapper.notIn(DamAsset::getSourceMetadataId, excludedIds);
                }
            } else {
                List<MetadataTable> mts = metadataTableMapper.selectList(
                        new LambdaQueryWrapper<MetadataTable>().eq(MetadataTable::getSubjectDomain, subjectDomain));
                if (mts.isEmpty()) {
                    return PageResult.of(new Page<DamAsset>().setRecords(Collections.emptyList()));
                }
                Set<Long> mtIds = mts.stream().map(MetadataTable::getId).collect(Collectors.toSet());
                wrapper.in(DamAsset::getSourceMetadataId, mtIds);
            }
        }
        // 按资产主题域过滤（归属关系表，一表可属多个主题域）
        if (subjectDomainId != null) {
            List<AssetCategoryRel> domainRels = assetCategoryRelMapper.selectList(
                    new LambdaQueryWrapper<AssetCategoryRel>()
                            .eq(AssetCategoryRel::getSubjectDomainId, subjectDomainId));
            Set<Long> domainAssetIds = domainRels.stream().map(AssetCategoryRel::getAssetId).collect(Collectors.toSet());
            if (domainAssetIds.isEmpty()) {
                return PageResult.of(new Page<DamAsset>().setRecords(Collections.emptyList()));
            }
            wrapper.in(DamAsset::getId, domainAssetIds);
        }
        // 按分类过滤：一级主题域→按主题域归属；二级类目→按类目归属（兼容主分类历史数据）
        if (categoryId != null) {
            DamCategory node = categoryMapper.selectById(categoryId);
            boolean isDomain = node != null && node.getTreeLevel() != null && node.getTreeLevel() == 1L;
            if (isDomain) {
                List<AssetCategoryRel> domainRels = assetCategoryRelMapper.selectList(
                        new LambdaQueryWrapper<AssetCategoryRel>()
                                .eq(AssetCategoryRel::getSubjectDomainId, categoryId));
                Set<Long> domainAssetIds = domainRels.stream().map(AssetCategoryRel::getAssetId).collect(Collectors.toSet());
                List<Long> catIds = getCategoryAndDescendantIds(categoryId);
                wrapper.and(w -> {
                    w.in(DamAsset::getCategoryId, catIds);
                    if (!domainAssetIds.isEmpty()) w.or().in(DamAsset::getId, domainAssetIds);
                });
            } else {
                List<AssetCategoryRel> catRels = assetCategoryRelMapper.selectList(
                        new LambdaQueryWrapper<AssetCategoryRel>()
                                .eq(AssetCategoryRel::getCategoryId, categoryId));
                Set<Long> catAssetIds = catRels.stream().map(AssetCategoryRel::getAssetId).collect(Collectors.toSet());
                wrapper.and(w -> {
                    w.eq(DamAsset::getCategoryId, categoryId);
                    if (!catAssetIds.isEmpty()) w.or().in(DamAsset::getId, catAssetIds);
                });
            }
        }
        // 安全等级过滤：新表 asset_info 无 security_level 列，暂不支持（可基于元数据管理扩展）
        // if (StrUtil.isNotBlank(securityLevel)) { wrapper.eq(DamAsset::getSecurityLevel, securityLevel); }
        // 标签过滤
        if (tagId != null) {
            List<DamAssetTag> relations = assetTagMapper.selectList(
                    new LambdaQueryWrapper<DamAssetTag>().eq(DamAssetTag::getTagId, tagId));
            Set<Long> assetIds = relations.stream().map(DamAssetTag::getAssetId).collect(Collectors.toSet());
            if (assetIds.isEmpty()) {
                // 无匹配资产，返回空
                return PageResult.of(new Page<DamAsset>().setRecords(Collections.emptyList()));
            }
            wrapper.in(DamAsset::getId, assetIds);
        }
        // 角色可见主题域限制：仅资产目录场景生效（受限用户只能看到被授权主题域下的资产）
        Set<Long> visibleDomainIds = enforceDomainPermission ? resolveVisibleDomainIds(currentUserId()) : null;
        if (visibleDomainIds != null) {
            if (subjectDomainId != null && !visibleDomainIds.contains(subjectDomainId)) {
                return PageResult.of(new Page<DamAsset>().setRecords(Collections.emptyList()));
            }
            if (visibleDomainIds.isEmpty()) {
                return PageResult.of(new Page<DamAsset>().setRecords(Collections.emptyList()));
            }
            Set<Long> visibleAssetIds = assetCategoryRelMapper.selectList(
                            new LambdaQueryWrapper<AssetCategoryRel>()
                                    .in(AssetCategoryRel::getSubjectDomainId, visibleDomainIds))
                    .stream().map(AssetCategoryRel::getAssetId).collect(Collectors.toSet());
            if (visibleAssetIds.isEmpty()) {
                return PageResult.of(new Page<DamAsset>().setRecords(Collections.emptyList()));
            }
            wrapper.in(DamAsset::getId, visibleAssetIds);
        }
        // 排序
        if ("name".equals(sortBy)) {
            wrapper.orderByAsc(DamAsset::getAssetName);
        } else if ("updated".equals(sortBy)) {
            wrapper.orderByDesc(DamAsset::getUpdatedAt);
        } else {
            wrapper.orderByDesc(DamAsset::getCreatedAt);
        }
        // 查询并填充前端展示兼容字段
        PageResult<DamAsset> result = PageResult.of(assetMapper.selectPage(p, wrapper));
        for (DamAsset a : result.getRecords()) {
            fillAssetCompat(a);
        }
        fillBatchAssetCategories(result.getRecords());
        return result;
    }

    /**
     * 填充资产前端展示兼容字段（assetType/sourceSystem/securityLevel/tableName/databaseName/schemaName）
     * <p>
     * 这些字段在新表 asset_info 中无对应列（exist=false），由关联表实时拼装：
     * assetType←source_metadata_type、sourceSystem←source_system、
     * securityLevel←metadata_management.security_classification、
     * tableName/schemaName←metadata_table、databaseName←数据源 JDBC URL。
     *
     * @param asset 资产实体（兼容字段不持久化）
     */
    private void fillAssetCompat(DamAsset asset) {
        if (asset == null) return;
        // 1. 资产类型：source_metadata_type 转小写（TABLE→table）
        if (asset.getAssetType() == null && asset.getSourceMetadataType() != null) {
            asset.setAssetType(asset.getSourceMetadataType().toLowerCase());
        }
        // 2. 来源系统编码
        if (asset.getSourceSystem() == null && asset.getSourceSystemId() != null) {
            DamSourceSystem sys = sourceSystemMapper.selectById(asset.getSourceSystemId());
            if (sys != null) asset.setSourceSystem(sys.getSystemCode());
        }
        // 3. 安全等级：从 metadata_management.security_classification 读取
        if (asset.getSecurityLevel() == null && asset.getSourceMetadataId() != null) {
            MetadataManagement mgmt = metadataManagementMapper.selectOne(
                    new LambdaQueryWrapper<MetadataManagement>()
                            .eq(MetadataManagement::getTableId, asset.getSourceMetadataId())
                            .last("LIMIT 1"));
            if (mgmt != null && mgmt.getSecurityClassification() != null) {
                asset.setSecurityLevel(mgmt.getSecurityClassification());
            }
        }
        // 4. 表名/Schema/主题域/表注释：从 metadata_table 读取（metadata_table 无 schema_name 列，schema 名经 schema_id 关联 metadata_schema）
        //    表注释以 AI 标注的 metadata_table.table_comment 为权威值，资产描述为空时回填，避免“AI 已标注但页面无注释”
        if ((asset.getTableName() == null || asset.getSchemaName() == null || asset.getSubjectDomain() == null
                || StrUtil.isBlank(asset.getDescription()))
                && asset.getSourceMetadataId() != null) {
            MetadataTable mt = metadataTableMapper.selectById(asset.getSourceMetadataId());
            if (mt != null) {
                if (asset.getTableName() == null) asset.setTableName(mt.getTableName());
                if (asset.getSchemaName() == null && mt.getSchemaId() != null) {
                    MetadataSchema ms = schemaMapper.selectById(mt.getSchemaId());
                    if (ms != null) asset.setSchemaName(ms.getSchemaName());
                }
                if (asset.getSubjectDomain() == null) asset.setSubjectDomain(mt.getSubjectDomain());
                if (StrUtil.isBlank(asset.getDescription())) asset.setDescription(mt.getTableComment());
            }
        }
        // 5. 数据库名：从数据源 JDBC URL 解析
        if (asset.getDatabaseName() == null && asset.getDataSourceId() != null) {
            DamDataSource ds = dataSourceMapper.selectById(asset.getDataSourceId());
            if (ds != null) asset.setDatabaseName(parseDbNameFromJdbc(ds.getJdbcUrl()));
        }
    }

    /**
     * 递归获取指定分类及其所有子分类的ID列表
     */
    private List<Long> getCategoryAndDescendantIds(Long categoryId) {
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        collectChildCategoryIds(categoryId, ids);
        return ids;
    }

    private void collectChildCategoryIds(Long parentId, List<Long> ids) {
        List<DamCategory> children = categoryMapper.selectList(
                new LambdaQueryWrapper<DamCategory>().eq(DamCategory::getParentId, parentId));
        for (DamCategory child : children) {
            ids.add(child.getId());
            collectChildCategoryIds(child.getId(), ids);
        }
    }

    public Map<String, Object> getAssetDetail(Long id) {
        DamAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        // 角色可见主题域校验：受限用户只能查看被授权主题域下的资产
        Set<Long> visibleDomainIds = resolveVisibleDomainIds(currentUserId());
        if (visibleDomainIds != null) {
            List<AssetCategoryRel> rels = assetCategoryRelMapper.selectList(
                    new LambdaQueryWrapper<AssetCategoryRel>().eq(AssetCategoryRel::getAssetId, id));
            boolean allowed = rels.stream().anyMatch(r -> visibleDomainIds.contains(r.getSubjectDomainId()));
            if (!allowed) throw new BusinessException("无权查看该资产（不在当前角色可见主题域内）");
        }
        // 填充前端展示兼容字段
        fillAssetCompat(asset);
        // 多主题域归属
        List<Map<String, Object>> catList = listAssetCategories(id);
        asset.setCategories(catList);
        asset.setCategoryPathText(catList.stream()
                .map(c -> (StrUtil.isNotBlank((String) c.get("subjectDomainName"))
                        ? c.get("subjectDomainName") + "/" : "") + c.get("categoryName"))
                .collect(Collectors.joining("；")));
        // 记录访问次数
        if (asset.getRecordCount() == null) {
            asset.setRecordCount(1L);
        } else {
            asset.setRecordCount(asset.getRecordCount() + 1);
        }
        assetMapper.updateById(asset);

        Map<String, Object> result = new HashMap<>();
        result.put("asset", asset);
        // 获取字段列表
        result.put("fields", getFieldsByAssetId(id));
        // 获取标签
        result.put("tags", getTagsByAssetId(id));
        // 获取平均评分
        LambdaQueryWrapper<DamRating> ratingQuery = new LambdaQueryWrapper<>();
        ratingQuery.eq(DamRating::getAssetId, id);
        List<DamRating> ratings = ratingMapper.selectList(ratingQuery);
        double avgScore = ratings.stream().mapToInt(DamRating::getRatingScore).average().orElse(0);
        result.put("avgRating", Math.round(avgScore * 10) / 10.0);
        result.put("ratingCount", ratings.size());
        // 检查是否已收藏
        try {
            long userId = StpUtil.getLoginIdAsLong();
            long favCount = favoriteMapper.selectCount(
                    new LambdaQueryWrapper<DamFavorite>()
                            .eq(DamFavorite::getUserId, userId)
                            .eq(DamFavorite::getAssetId, id));
            result.put("isFavorited", favCount > 0);
        } catch (Exception e) {
            result.put("isFavorited", false);
        }
        // 使用记录
        result.put("viewCount", asset.getRecordCount());
        result.put("lastAccessed", asset.getUpdatedAt());
        return result;
    }

    @Transactional
    public DamAsset createAsset(DamAsset asset) {
        // 兼容前端字段：assetType → sourceMetadataType（table → TABLE）
        if (StrUtil.isBlank(asset.getSourceMetadataType()) && StrUtil.isNotBlank(asset.getAssetType())) {
            asset.setSourceMetadataType(asset.getAssetType().toUpperCase());
        }
        // 兼容前端字段：sourceSystem 编码 → sourceSystemId
        if (asset.getSourceSystemId() == null && StrUtil.isNotBlank(asset.getSourceSystem())) {
            DamSourceSystem sys = sourceSystemMapper.selectOne(
                    new LambdaQueryWrapper<DamSourceSystem>()
                            .eq(DamSourceSystem::getSystemCode, asset.getSourceSystem())
                            .last("LIMIT 1"));
            if (sys != null) asset.setSourceSystemId(sys.getId());
        }
        // 兼容前端字段：tableName/databaseName → 关联 metadata_table（多态外键）
        if (asset.getSourceMetadataId() == null && StrUtil.isNotBlank(asset.getTableName())) {
            asset.setSourceMetadataId(ensureMetadataTable(
                    asset.getTableName(), asset.getSourceMetadataType(), asset.getSchemaName(),
                    asset.getDataSourceId(), asset.getDescription()));
        }
        assetMapper.insert(asset);
        calculateAssetSecurityLevel(asset.getId());
        return assetMapper.selectById(asset.getId());
    }

    @Transactional
    public DamAsset updateAsset(DamAsset asset) {
        // 兼容前端字段（与 createAsset 相同的适配逻辑）
        if (StrUtil.isBlank(asset.getSourceMetadataType()) && StrUtil.isNotBlank(asset.getAssetType())) {
            asset.setSourceMetadataType(asset.getAssetType().toUpperCase());
        }
        if (asset.getSourceSystemId() == null && StrUtil.isNotBlank(asset.getSourceSystem())) {
            DamSourceSystem sys = sourceSystemMapper.selectOne(
                    new LambdaQueryWrapper<DamSourceSystem>()
                            .eq(DamSourceSystem::getSystemCode, asset.getSourceSystem())
                            .last("LIMIT 1"));
            if (sys != null) asset.setSourceSystemId(sys.getId());
        }
        if (asset.getSourceMetadataId() == null && StrUtil.isNotBlank(asset.getTableName())) {
            asset.setSourceMetadataId(ensureMetadataTable(
                    asset.getTableName(), asset.getSourceMetadataType(), asset.getSchemaName(),
                    asset.getDataSourceId(), asset.getDescription()));
        }
        // subjectDomain 为 metadata_table 列（资产侧为 exist=false 派生字段，不落 asset_info），
        // 仅在本次显式携带时同步更新关联的技术元数据表（空白表示清除主题域）。
        // 多路径定位 metadata_table：本次携带的 sourceMetadataId → 资产库中 sourceMetadataId → 按数据源+表名兑底。
        if (asset.getId() != null && asset.getSubjectDomain() != null) {
            try {
                Long mtId = asset.getSourceMetadataId();
                if (mtId == null) {
                    DamAsset db = assetMapper.selectById(asset.getId());
                    if (db != null) mtId = db.getSourceMetadataId();
                }
                MetadataTable mt = mtId != null ? metadataTableMapper.selectById(mtId) : null;
                if (mt == null && StrUtil.isNotBlank(asset.getTableName())) {
                    mt = metadataTableMapper.selectOne(new LambdaQueryWrapper<MetadataTable>()
                            .eq(MetadataTable::getTableName, asset.getTableName())
                            .eq(asset.getDataSourceId() != null, MetadataTable::getDatasourceId, asset.getDataSourceId())
                            .last("LIMIT 1"));
                }
                if (mt != null) {
                    mt.setSubjectDomain(StrUtil.isBlank(asset.getSubjectDomain()) ? null : asset.getSubjectDomain());
                    metadataTableMapper.updateById(mt);
                    log.info("同步主题域成功: assetId={}, table={}, subjectDomain={}",
                            asset.getId(), mt.getTableName(), asset.getSubjectDomain());
                } else {
                    log.warn("同步主题域失败：未找到资产关联的 metadata_table, assetId={}, tableName={}",
                            asset.getId(), asset.getTableName());
                }
            } catch (Exception e) {
                // 主题域同步异常不影响资产其他字段保存
                log.warn("同步主题域异常（不影响资产保存）: {}", e.getMessage());
            }
        }
        assetMapper.updateById(asset);
        calculateAssetSecurityLevel(asset.getId());
        return assetMapper.selectById(asset.getId());
    }

    @Transactional
    public void deleteAsset(Long id) {
        // 同时删除关联的字段、标签、收藏、评价
        // 字段按多态外键（metadata_table.id）级联删除
        DamAsset asset = assetMapper.selectById(id);
        if (asset != null && asset.getSourceMetadataId() != null) {
            fieldMapper.delete(new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, asset.getSourceMetadataId()));
        }
        assetTagMapper.delete(new LambdaQueryWrapper<DamAssetTag>().eq(DamAssetTag::getAssetId, id));
        favoriteMapper.delete(new LambdaQueryWrapper<DamFavorite>().eq(DamFavorite::getAssetId, id));
        ratingMapper.delete(new LambdaQueryWrapper<DamRating>().eq(DamRating::getAssetId, id));
        assetMapper.deleteById(id);
    }

    // ==================== 记录数查询 ====================

    /**
     * 从 information_schema 快速获取表的估算记录数（毫秒级，InnoDB估算值）
     */
    public long refreshRecordCount(Long assetId) {
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null) throw new BusinessException("资产不存在");
        return refreshRecordCountFromDb(asset);
    }

    private long refreshRecordCountFromDb(DamAsset asset) {
        if (asset.getDataSourceId() == null) {
            return 0;
        }

        DamDataSource ds = dataSourceMapper.selectById(asset.getDataSourceId());
        if (ds == null || ds.getStatus() == null || ds.getStatus() != 1) return asset.getRecordCount() != null ? asset.getRecordCount() : 0;

        String url = ds.getJdbcUrl();
        String dbName = parseDbNameFromJdbc(url);
        if (StrUtil.isBlank(url)) return asset.getRecordCount() != null ? asset.getRecordCount() : 0;

        long count = 0;
        String sql = "SELECT TABLE_ROWS FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), cryptoService.decryptStorage(ds.getPassword()));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            ps.setString(2, resolveTableName(asset));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            return asset.getRecordCount() != null ? asset.getRecordCount() : 0;
        }

        if (count >= 0 && count != (asset.getRecordCount() != null ? asset.getRecordCount() : 0)) {
            asset.setRecordCount(count);
            assetMapper.updateById(asset);
        }
        return count;
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

    /** 资产关联的表名（通过 source_metadata_type=TABLE 时取 tableName 过渡字段，否则空） */
    private String resolveTableName(DamAsset asset) {
        return asset.getTableName();
    }

    /**
     * 使用 SELECT COUNT(*) 获取精确记录数（大表可能较慢，手动触发）
     */
    public long getExactRecordCount(Long assetId) {
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null) throw new BusinessException("资产不存在");
        if (asset.getDataSourceId() == null) {
            return 0;
        }

        String tableName = resolveTableName(asset);
        if (StrUtil.isBlank(tableName) || !SAFE_TABLE_NAME.matcher(tableName).matches()) {
            log.warn("非法的表名格式, 跳过精确计数: {}", tableName);
            return 0;
        }

        DamDataSource ds = dataSourceMapper.selectById(asset.getDataSourceId());
        if (ds == null || ds.getStatus() == null || ds.getStatus() != 1) return asset.getRecordCount() != null ? asset.getRecordCount() : 0;

        String url = ds.getJdbcUrl();
        long count = 0;
        // PostgreSQL 不使用反引号，表名已通过白名单校验（仅字母数字下划线）
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), cryptoService.decryptStorage(ds.getPassword()));
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                count = rs.getLong(1);
            }
        } catch (SQLException e) {
            return asset.getRecordCount() != null ? asset.getRecordCount() : 0;
        }

        asset.setRecordCount(count);
        assetMapper.updateById(asset);
        return count;
    }

    /**
     * 批量刷新所有资产的记录数（使用估算值，速度极快）
     */
    public int refreshAllRecordCounts() {
        // 新表 asset_info 无 table_name 列，所有资产均以 asset_name 命名，无需再按该过渡字段过滤
        List<DamAsset> allAssets = assetMapper.selectList(
                new LambdaQueryWrapper<DamAsset>().isNotNull(DamAsset::getAssetName));
        int updated = 0;
        for (DamAsset asset : allAssets) {
            try {
                refreshRecordCountFromDb(asset);
                updated++;
            } catch (Exception e) {
                // 单个失败不影响其他
            }
        }
        return updated;
    }

    // ==================== 安全等级自动计算 ====================

    /**
     * 根据字段安全等级自动计算资产总分级（取最高级别）
     */
    @Transactional
    public void calculateAssetSecurityLevel(Long assetId) {
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getSourceMetadataId() == null) return;
        // 新表字段按多态外键（metadata_table.id）关联，不再使用 asset_id
        List<DamField> fields = fieldMapper.selectList(
                new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, asset.getSourceMetadataId()));
        if (fields.isEmpty()) return;

        int maxPriority = 0;
        String highestLevel = "internal";
        for (DamField field : fields) {
            String level = field.getSecurityLevel();
            if (level == null) continue;
            int priority = getSecurityLevelPriority().getOrDefault(level, 0);
            if (priority > maxPriority) {
                maxPriority = priority;
                highestLevel = level;
            }
        }

        if (!highestLevel.equals(asset.getSecurityLevel())) {
            asset.setSecurityLevel(highestLevel);
            asset.setUpdatedAt(LocalDateTime.now());
            assetMapper.updateById(asset);
        }
    }

    /**
     * 重新计算所有资产的安全等级
     */
    @Transactional
    public int recalculateAllSecurityLevels() {
        List<DamAsset> allAssets = assetMapper.selectList(null);
        int count = 0;
        for (DamAsset asset : allAssets) {
            if (asset.getSourceMetadataId() == null) continue;
            List<DamField> fields = fieldMapper.selectList(
                    new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, asset.getSourceMetadataId()));
            if (fields.isEmpty()) continue;

            int maxPriority = 0;
            String highestLevel = "internal";
            for (DamField field : fields) {
                String level = field.getSecurityLevel();
                if (level == null) continue;
                int priority = getSecurityLevelPriority().getOrDefault(level, 0);
                if (priority > maxPriority) {
                    maxPriority = priority;
                    highestLevel = level;
                }
            }

            if (!highestLevel.equals(asset.getSecurityLevel())) {
                asset.setSecurityLevel(highestLevel);
                asset.setUpdatedAt(LocalDateTime.now());
                assetMapper.updateById(asset);
                count++;
            }
        }
        return count;
    }

    // ==================== 字段管理 ====================

    /**
     * 查询资产关联的字段列表（metadata_column，按字段ID升序）
     * <p>
     * 新表字段通过资产多态外键（source_metadata_id=metadata_table.id）关联，
     * 不再使用 asset_id 列。
     *
     * @param assetId 资产ID
     * @return 字段列表
     */
    public List<DamField> getFieldsByAssetId(Long assetId) {
        // 通过资产定位技术元数据表ID
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getSourceMetadataId() == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<DamField> wrapper = new LambdaQueryWrapper<>();
        // 按 metadata_table.id 过滤字段
        wrapper.eq(DamField::getTableId, asset.getSourceMetadataId());
        wrapper.orderByAsc(DamField::getId);
        return fieldMapper.selectList(wrapper);
    }

    /**
     * 获取字段中文注释字典（字段名 → 已有中文注释）
     * <p>
     * 供 AI 生成字段注释时做「关联联想翻译」：同名字段复用最高频已有注释，
     * 相似字段参考已有术语，保证全平台字段注释口径一致。
     *
     * @return 字段名 → 中文注释 映射
     */
    public Map<String, String> getFieldCommentDict() {
        List<DamField> fields = fieldMapper.selectList(
                new LambdaQueryWrapper<DamField>()
                        .isNotNull(DamField::getFieldName)
                        .isNotNull(DamField::getComment)
                        .ne(DamField::getComment, ""));
        // 同名字段统计各注释出现次数，取最高频注释
        Map<String, Map<String, Integer>> counter = new LinkedHashMap<>();
        for (DamField f : fields) {
            String name = f.getFieldName() == null ? "" : f.getFieldName().trim();
            String comment = f.getComment() == null ? "" : f.getComment().trim();
            if (name.isEmpty() || comment.isEmpty()) continue;
            counter.computeIfAbsent(name, k -> new LinkedHashMap<>())
                    .merge(comment, 1, Integer::sum);
        }
        Map<String, String> dict = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : counter.entrySet()) {
            String best = e.getValue().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
            if (!best.isEmpty()) dict.put(e.getKey(), best);
        }
        return dict;
    }

    /**
     * 更新字段信息（手动补充/修正注释、默认值、长度、可空性、主键等）
     *
     * @param field 字段更新信息（id 必填，其余字段非 null 才更新；comment 允许传空串清空）
     * @return 更新后的字段
     */
    @Transactional
    public DamField updateField(DamField field) {
        if (field.getId() == null) throw new BusinessException("字段ID不能为空");
        DamField existing = fieldMapper.selectById(field.getId());
        if (existing == null) throw new BusinessException("字段不存在");
        // 仅更新传入的非空字段
        if (StrUtil.isNotBlank(field.getFieldName())) existing.setFieldName(field.getFieldName());
        if (StrUtil.isNotBlank(field.getDataType())) existing.setDataType(field.getDataType());
        if (field.getDataLength() != null) existing.setDataLength(field.getDataLength());
        if (field.getIsPrimaryKey() != null) existing.setIsPrimaryKey(field.getIsPrimaryKey());
        if (field.getIsNullable() != null) existing.setIsNullable(field.getIsNullable());
        if (field.getDefaultValue() != null) existing.setDefaultValue(field.getDefaultValue());
        if (field.getSortOrder() != null) existing.setSortOrder(field.getSortOrder());
        // 注释允许清空（前端清空时传空串，而非 null）
        if (field.getComment() != null) existing.setComment(field.getComment());
        // 安全等级/敏感类型允许清空（前端清空时传空串）
        if (field.getSecurityLevel() != null) existing.setSecurityLevel(field.getSecurityLevel());
        if (field.getSensitiveType() != null) existing.setSensitiveType(field.getSensitiveType());
        if (field.getReferenceTableName() != null) existing.setReferenceTableName(field.getReferenceTableName());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy(currentUser());
        fieldMapper.updateById(existing);
        return existing;
    }

    // ==================== 标签管理 ====================

    public List<DamTag> getAllTags() {
        return tagMapper.selectList(null);
    }

    @Transactional
    public DamTag createTag(DamTag tag) {
        // 检查重名
        long count = tagMapper.selectCount(
                new LambdaQueryWrapper<DamTag>().eq(DamTag::getTagName, tag.getTagName()));
        if (count > 0) {
            throw new BusinessException("标签名称已存在");
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        if (tag.getCreatedBy() == null) tag.setCreatedBy("system");
        if (tag.getUpdatedBy() == null) tag.setUpdatedBy("system");
        tagMapper.insert(tag);
        return tag;
    }

    @Transactional
    public void deleteTag(Long id) {
        // 解除所有资产与这个标签的关联
        assetTagMapper.delete(new LambdaQueryWrapper<DamAssetTag>().eq(DamAssetTag::getTagId, id));
        tagMapper.deleteById(id);
    }

    @Transactional
    public void assignTag(Long assetId, Long tagId) {
        long count = assetTagMapper.selectCount(
                new LambdaQueryWrapper<DamAssetTag>()
                        .eq(DamAssetTag::getAssetId, assetId)
                        .eq(DamAssetTag::getTagId, tagId));
        if (count == 0) {
            DamAssetTag relation = new DamAssetTag();
            relation.setAssetId(assetId);
            relation.setTagId(tagId);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            relation.setCreatedAt(now);
            relation.setUpdatedAt(now);
            relation.setCreatedBy("system");
            relation.setUpdatedBy("system");
            assetTagMapper.insert(relation);
        }
    }

    @Transactional
    public void removeTag(Long assetId, Long tagId) {
        assetTagMapper.delete(new LambdaQueryWrapper<DamAssetTag>()
                .eq(DamAssetTag::getAssetId, assetId)
                .eq(DamAssetTag::getTagId, tagId));
    }

    public List<DamTag> getTagsByAssetId(Long assetId) {
        List<DamAssetTag> relations = assetTagMapper.selectList(
                new LambdaQueryWrapper<DamAssetTag>().eq(DamAssetTag::getAssetId, assetId));
        if (relations.isEmpty()) return Collections.emptyList();
        Set<Long> tagIds = relations.stream().map(DamAssetTag::getTagId).collect(Collectors.toSet());
        return tagMapper.selectByIds(tagIds);
    }

    // ==================== 收藏管理 ====================

    @Transactional
    public void addFavorite(Long assetId) {
        long userId = StpUtil.getLoginIdAsLong();
        long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<DamFavorite>()
                        .eq(DamFavorite::getUserId, userId)
                        .eq(DamFavorite::getAssetId, assetId));
        if (count == 0) {
            DamFavorite fav = new DamFavorite();
            fav.setUserId(userId);
            fav.setAssetId(assetId);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            fav.setCreatedAt(now);
            fav.setUpdatedAt(now);
            fav.setCreatedBy("system");
            fav.setUpdatedBy("system");
            favoriteMapper.insert(fav);
        }
    }

    @Transactional
    public void removeFavorite(Long assetId) {
        long userId = StpUtil.getLoginIdAsLong();
        favoriteMapper.delete(new LambdaQueryWrapper<DamFavorite>()
                .eq(DamFavorite::getUserId, userId)
                .eq(DamFavorite::getAssetId, assetId));
    }

    public List<DamAsset> getMyFavorites() {
        long userId = StpUtil.getLoginIdAsLong();
        List<DamFavorite> favs = favoriteMapper.selectList(
                new LambdaQueryWrapper<DamFavorite>().eq(DamFavorite::getUserId, userId));
        if (favs.isEmpty()) return Collections.emptyList();
        Set<Long> assetIds = favs.stream().map(DamFavorite::getAssetId).collect(Collectors.toSet());
        return assetMapper.selectByIds(assetIds);
    }

    // ==================== 评价管理 ====================

    @Transactional
    public DamRating rateAsset(DamRating rating) {
        // 兼容前端字段：score → ratingScore、comment → ratingComment
        if (rating.getRatingScore() == null && rating.getScore() != null) rating.setRatingScore(rating.getScore());
        if (rating.getRatingComment() == null && rating.getComment() != null) rating.setRatingComment(rating.getComment());
        long userId = StpUtil.getLoginIdAsLong();
        rating.setUserId(String.valueOf(userId));
        // 检查是否已评价过，更新或新增
        DamRating existing = ratingMapper.selectOne(
                new LambdaQueryWrapper<DamRating>()
                        .eq(DamRating::getUserId, String.valueOf(userId))
                        .eq(DamRating::getAssetId, rating.getAssetId()));
        if (existing != null) {
            existing.setRatingScore(rating.getRatingScore());
            existing.setRatingComment(rating.getRatingComment());
            ratingMapper.updateById(existing);
            return existing;
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        rating.setCreatedAt(now);
        rating.setUpdatedAt(now);
        if (rating.getCreatedBy() == null) rating.setCreatedBy("system");
        if (rating.getUpdatedBy() == null) rating.setUpdatedBy("system");
        ratingMapper.insert(rating);
        return rating;
    }

    public List<DamRating> getAssetRatings(Long assetId) {
        List<DamRating> list = ratingMapper.selectList(
                new LambdaQueryWrapper<DamRating>()
                        .eq(DamRating::getAssetId, assetId)
                        .orderByDesc(DamRating::getCreatedAt));
        // 填充前端兼容字段 score/comment
        for (DamRating r : list) {
            r.setScore(r.getRatingScore());
            r.setComment(r.getRatingComment());
        }
        return list;
    }

    // ==================== 元数据版本 ====================

    public List<DamMetadataVersion> getMetadataVersions(Long assetId) {
        return null;
    }

    // ==================== 元数据采集 ====================

    public List<DamMetadataCollection> getCollections(String sourceType) {
        LambdaQueryWrapper<DamMetadataCollection> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(sourceType)) {
            wrapper.eq(DamMetadataCollection::getJobType, sourceType);
        }
        wrapper.orderByDesc(DamMetadataCollection::getCreatedAt);
        return metadataCollectionMapper.selectList(wrapper);
    }

    @Transactional
    public DamMetadataCollection createCollection(DamMetadataCollection collection) {
        // metadata_collection 表 datasource_id/schema_id 为 NOT NULL，未传时用 0 兑底
        if (collection.getDatasourceId() == null) collection.setDatasourceId(0L);
        if (collection.getSchemaId() == null) collection.setSchemaId(0L);
        collection.setStatus(0);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);
        if (collection.getCreatedBy() == null) collection.setCreatedBy("system");
        if (collection.getUpdatedBy() == null) collection.setUpdatedBy("system");
        metadataCollectionMapper.insert(collection);
        return collection;
    }

    /**
     * 获取采集详情（含快照解析）
     */
    public Map<String, Object> getCollectionDetail(Long id) {
        DamMetadataCollection col = metadataCollectionMapper.selectById(id);
        if (col == null) throw new BusinessException("采集记录不存在");
        Map<String, Object> result = new HashMap<>();
        result.put("collection", col);
        if (StrUtil.isNotBlank(col.getDetailLog())) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<Map<String, Object>> tables = mapper.readValue(col.getDetailLog(),
                        new TypeReference<>() {});
                result.put("tables", tables);
            } catch (Exception e) {
                result.put("tables", Collections.emptyList());
            }
        } else {
            result.put("tables", Collections.emptyList());
        }
        return result;
    }

    /**
     * 对比两次采集的变更
     */
    public Map<String, Object> compareCollections(Long idA, Long idB) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> a = getCollectionDetail(idA);
        Map<String, Object> b = getCollectionDetail(idB);
        result.put("collectionA", a.get("collection"));
        result.put("collectionB", b.get("collection"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tablesA = (List<Map<String, Object>>) a.get("tables");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tablesB = (List<Map<String, Object>>) b.get("tables");

        Map<String, Map<String, Object>> mapA = new LinkedHashMap<>();
        for (Map<String, Object> t : tablesA) mapA.put((String) t.get("tableName"), t);
        Map<String, Map<String, Object>> mapB = new LinkedHashMap<>();
        for (Map<String, Object> t : tablesB) mapB.put((String) t.get("tableName"), t);

        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> modified = new ArrayList<>();

        Set<String> allNames = new LinkedHashSet<>();
        allNames.addAll(mapA.keySet());
        allNames.addAll(mapB.keySet());

        for (String name : allNames) {
            boolean inA = mapA.containsKey(name);
            boolean inB = mapB.containsKey(name);
            if (!inA && inB) {
                added.add(Map.of("tableName", name, "change", "新增", "fields", mapB.get(name).get("columns")));
            } else if (inA && !inB) {
                removed.add(Map.of("tableName", name, "change", "删除", "fields", mapA.get(name).get("columns")));
            } else {
                // 比较字段
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> colsA = (List<Map<String, Object>>) mapA.get(name).getOrDefault("columns", Collections.emptyList());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> colsB = (List<Map<String, Object>>) mapB.get(name).getOrDefault("columns", Collections.emptyList());
                Map<String, Map<String, Object>> colMapA = new LinkedHashMap<>();
                for (Map<String, Object> c : colsA) colMapA.put((String) c.get("name"), c);
                Map<String, Map<String, Object>> colMapB = new LinkedHashMap<>();
                for (Map<String, Object> c : colsB) colMapB.put((String) c.get("name"), c);

                List<String> addedCols = new ArrayList<>();
                List<String> removedCols = new ArrayList<>();
                List<Map<String, Object>> changedCols = new ArrayList<>();
                Set<String> allCols = new LinkedHashSet<>();
                allCols.addAll(colMapA.keySet());
                allCols.addAll(colMapB.keySet());
                for (String col : allCols) {
                    if (!colMapA.containsKey(col)) {
                        addedCols.add(col);
                    } else if (!colMapB.containsKey(col)) {
                        removedCols.add(col);
                    } else {
                        Map<String, Object> colA = colMapA.get(col);
                        Map<String, Object> colB = colMapB.get(col);
                        // 逐个属性比较，记录具体变更
                        List<Map<String, Object>> props = new ArrayList<>();
                        for (String prop : new String[]{"type", "size", "nullable", "isPrimaryKey", "comment", "defaultValue"}) {
                            Object valA = colA.get(prop);
                            Object valB = colB.get(prop);
                            if (!Objects.equals(valA, valB)) {
                                Map<String, Object> change = new LinkedHashMap<>();
                                change.put("property", prop);
                                change.put("oldValue", valA != null ? valA.toString() : "");
                                change.put("newValue", valB != null ? valB.toString() : "");
                                props.add(change);
                            }
                        }
                        if (!props.isEmpty()) {
                            Map<String, Object> cf = new LinkedHashMap<>();
                            cf.put("fieldName", col);
                            cf.put("changes", props);
                            changedCols.add(cf);
                        }
                    }
                }
                if (!addedCols.isEmpty() || !removedCols.isEmpty() || !changedCols.isEmpty()) {
                    Map<String, Object> mod = new LinkedHashMap<>();
                    mod.put("tableName", name);
                    mod.put("change", "变更");
                    mod.put("addedFields", addedCols);
                    mod.put("removedFields", removedCols);
                    mod.put("changedFields", changedCols);
                    modified.add(mod);
                }
            }
        }
        result.put("added", added);
        result.put("removed", removed);
        result.put("modified", modified);

        // 汇总统计
        int addedFields = added.stream().mapToInt(t -> ((List<?>) t.getOrDefault("fields", Collections.emptyList())).size()).sum();
        int removedFields = removed.stream().mapToInt(t -> ((List<?>) t.getOrDefault("fields", Collections.emptyList())).size()).sum();
        int changedFieldCount = modified.stream().mapToInt(m -> ((List<?>) m.getOrDefault("changedFields", Collections.emptyList())).size()).sum();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("addedTables", added.size());
        summary.put("removedTables", removed.size());
        summary.put("modifiedTables", modified.size());
        summary.put("addedFields", addedFields);
        summary.put("removedFields", removedFields);
        summary.put("changedFields", changedFieldCount);
        result.put("summary", summary);
        return result;
    }

    // ==================== 来源系统管理 ====================

    public List<DamSourceSystem> getSourceSystems() {
        try {
            List<DamSourceSystem> list = sourceSystemMapper.selectList(
                    new LambdaQueryWrapper<DamSourceSystem>().orderByAsc(DamSourceSystem::getId));
            // 填充前端兼容字段 code/name/sortOrder
            for (DamSourceSystem s : list) {
                s.setCode(s.getSystemCode());
                s.setName(s.getSystemName());
                s.setSortOrder(0);
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Transactional
    public DamSourceSystem createSourceSystem(DamSourceSystem sys) {
        if (StrUtil.isBlank(sys.getSystemCode()) || StrUtil.isBlank(sys.getSystemName())) {
            throw new BusinessException("编码和名称不能为空");
        }
        long count = sourceSystemMapper.selectCount(
                new LambdaQueryWrapper<DamSourceSystem>().eq(DamSourceSystem::getSystemCode, sys.getSystemCode()));
        if (count > 0) {
            throw new BusinessException("编码已存在");
        }
        if (sys.getStatus() == null) sys.setStatus(1);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        sys.setCreatedAt(now);
        sys.setUpdatedAt(now);
        if (sys.getCreatedBy() == null) sys.setCreatedBy("system");
        if (sys.getUpdatedBy() == null) sys.setUpdatedBy("system");
        sourceSystemMapper.insert(sys);
        return sys;
    }

    @Transactional
    public DamSourceSystem updateSourceSystem(DamSourceSystem sys) {
        sourceSystemMapper.updateById(sys);
        return sys;
    }

    @Transactional
    public void deleteSourceSystem(Long id) {
        // 检查是否有资产使用该来源系统
        long assetCount = assetMapper.selectCount(
                new LambdaQueryWrapper<DamAsset>().eq(DamAsset::getSourceSystemId, id));
        if (assetCount > 0) {
            throw new BusinessException("该来源系统下有资产引用，无法删除");
        }
        sourceSystemMapper.deleteById(id);
    }

    // ==================== 数据源管理 ====================

    public List<DamDataSource> getDataSources() {
        List<DamDataSource> list = dataSourceMapper.selectList(
                new LambdaQueryWrapper<DamDataSource>().orderByAsc(DamDataSource::getCreatedAt));
        // 填充前端兼容字段（name/sourceType/host/port/databaseName）
        for (DamDataSource ds : list) {
            fillDataSourceCompat(ds);
            // 脱敏：不回传数据源密码
            ds.setPassword(null);
        }
        return list;
    }

    public Map<String, Object> getDataSourceDetail(Long id) {
        DamDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) throw new BusinessException("数据源不存在");
        // 填充前端兼容字段
        fillDataSourceCompat(ds);
        // 脱敏：不回传数据源密码
        ds.setPassword(null);
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", ds);
        // 获取该数据源的采集记录
        List<DamMetadataCollection> collections = metadataCollectionMapper.selectList(
                new LambdaQueryWrapper<DamMetadataCollection>()
                        .eq(DamMetadataCollection::getDatasourceId, id)
                        .orderByDesc(DamMetadataCollection::getCreatedAt));
        result.put("collections", collections);
        return result;
    }

    /**
     * 填充数据源前端兼容字段（name/sourceType/host/port/databaseName）
     *
     * @param ds 数据源实体（兼容字段不持久化）
     */
    private void fillDataSourceCompat(DamDataSource ds) {
        if (ds == null) return;
        // 从 JDBC URL 解析数据库名/namespace（派生字段不落库）
        ds.setDatabaseName(parseDbNameFromJdbc(ds.getJdbcUrl()));
        // 密码存在性标记（不泄露真实密码，供前端展示"已设置密码"掩码）
        ds.setHasPassword(StrUtil.isNotBlank(ds.getPassword()));
    }

    /**
     * 创建数据源（兼容前端字段：name/sourceType/host/port/databaseName，自动组装 jdbcUrl）
     */
    @Transactional
    public DamDataSource createDataSource(DamDataSource ds) {
        // 适配器默认与类型一致
        if (StrUtil.isBlank(ds.getDsAdapter())) ds.setDsAdapter(ds.getDsType());
        // 自动生成数据源代码
        if (StrUtil.isBlank(ds.getDsCode())) {
            ds.setDsCode((ds.getDsType() == null ? "DS" : ds.getDsType()) + "_" + System.currentTimeMillis() % 1000000);
        }
        // 根据数据源类型配置补全驱动类与 JDBC URL（类型配置由配置中心维护）
        DamDataSourceType typeCfg = StrUtil.isNotBlank(ds.getDsType())
                ? dataSourceTypeService.getByTypeKey(ds.getDsType()) : null;
        // driver_class 为 NOT NULL 列，必须先落库：优先使用类型配置中的驱动类
        if (typeCfg != null && StrUtil.isBlank(ds.getDriverClass())) {
            ds.setDriverClass(typeCfg.getDriverClass());
        }
        // 自动组装 JDBC URL（前端只提交 host/port/databaseName 时）
        if (StrUtil.isBlank(ds.getJdbcUrl()) && typeCfg != null) {
            int port = 0;
            try { port = Integer.parseInt(ds.getDsPort()); } catch (Exception ignored) {}
            if (port <= 0) port = typeCfg.getDefaultPort();
            ds.setJdbcUrl(buildJdbcUrl(typeCfg, ds.getDsIp(), port, ds.getDatabaseName()));
        }
        // 前端传输密码为 RSA 密文 → 解密后 AES 加密存储
        if (StrUtil.isNotBlank(ds.getPassword())) {
            ds.setPassword(cryptoService.encryptStorage(cryptoService.decryptPassword(ds.getPassword())));
        }
        if (ds.getStatus() == null) ds.setStatus(1);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        ds.setCreatedAt(now);
        ds.setUpdatedAt(now);
        if (ds.getCreatedBy() == null) ds.setCreatedBy("system");
        if (ds.getUpdatedBy() == null) ds.setUpdatedBy("system");
        dataSourceMapper.insert(ds);
        // 脱敏：不回传密码，仅标记是否存在
        ds.setHasPassword(StrUtil.isNotBlank(ds.getPassword()));
        ds.setPassword(null);
        return ds;
    }

    /**
     * 按类型模板组装 JDBC URL（占位符按顺序：%s=host、%d=port、%s=databaseName）
     */
    private String buildJdbcUrl(DamDataSourceType typeCfg, String host, int port, String databaseName) {
        String template = typeCfg.getUrlTemplate();
        if (StrUtil.isBlank(template)) return null;
        // 1. 替换第一个 %s 为 host
        int hostIdx = template.indexOf("%s");
        String url;
        if (hostIdx >= 0) {
            url = template.substring(0, hostIdx) + (host == null ? "localhost" : host) + template.substring(hostIdx + 2);
        } else {
            url = template;
        }
        // 2. 替换 %d 为 port
        int portIdx = url.indexOf("%d");
        if (portIdx >= 0) {
            url = url.substring(0, portIdx) + port + url.substring(portIdx + 2);
        }
        // 3. 替换剩余的 %s 为 databaseName（无占位则追加）
        if (databaseName != null) {
            int dbIdx = url.indexOf("%s");
            if (dbIdx >= 0) {
                url = url.substring(0, dbIdx) + databaseName + url.substring(dbIdx + 2);
            } else {
                url = url + databaseName;
            }
        }
        return url;
    }

    public DamDataSource getDataSourceById(Long id) {
        return dataSourceMapper.selectById(id);
    }

    @Transactional
    public DamDataSource updateDataSource(DamDataSource ds) {
        // 记录更新前是否有密码（脱敏展示用）
        DamDataSource old = ds.getId() != null ? dataSourceMapper.selectById(ds.getId()) : null;
        boolean hadPassword = old != null && StrUtil.isNotBlank(old.getPassword());
        if (StrUtil.isBlank(ds.getDsAdapter())) ds.setDsAdapter(ds.getDsType());
        DamDataSourceType typeCfg = StrUtil.isNotBlank(ds.getDsType())
                ? dataSourceTypeService.getByTypeKey(ds.getDsType()) : null;
        // 类型可能变更，同步驱动类，避免 driver_class 为空（NOT NULL 列）
        if (typeCfg != null) {
            ds.setDriverClass(typeCfg.getDriverClass());
        }
        // databaseName 为兼容字段不落库，前端提交时重新组装 JDBC URL（同步 host/port/databaseName 变更）
        if (StrUtil.isNotBlank(ds.getDatabaseName()) && typeCfg != null) {
            int port = 0;
            try { port = Integer.parseInt(ds.getDsPort()); } catch (Exception ignored) {}
            if (port <= 0) port = typeCfg.getDefaultPort();
            ds.setJdbcUrl(buildJdbcUrl(typeCfg, ds.getDsIp(), port, ds.getDatabaseName()));
        }
        // 密码：空表示不修改（保留原密码）；非空为 RSA 密文 → 解密后 AES 加密存储
        if (StrUtil.isNotBlank(ds.getPassword())) {
            ds.setPassword(cryptoService.encryptStorage(cryptoService.decryptPassword(ds.getPassword())));
        } else {
            ds.setPassword(null); // 防止 updateById 用空串覆盖已有密码
        }
        dataSourceMapper.updateById(ds);
        // 脱敏：不回传密码，仅标记是否存在（新密码或原有密码）
        ds.setHasPassword(hadPassword || StrUtil.isNotBlank(ds.getPassword()));
        ds.setPassword(null);
        return ds;
    }

    @Transactional
    public void deleteDataSource(Long id) {
        dataSourceMapper.deleteById(id);
    }
}

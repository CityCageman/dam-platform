package com.dam.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.entity.DamField;
import com.dam.catalog.entity.DamConfig;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.catalog.repository.DamFieldMapper;
import com.dam.catalog.repository.DamConfigMapper;
import com.dam.common.exception.BusinessException;
import com.dam.security.entity.DamSensitiveRule;
import com.dam.security.repository.DamSensitiveRuleMapper;
import com.dam.security.repository.DynamicQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.util.StrUtil;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 安全分类分级服务
 * <p>
 * 提供两类能力：
 * <ul>
 *   <li>autoClassify：按字段名匹配敏感规则（security_sensitive_rule）自动识别敏感资产/字段</li>
 *   <li>scanSensitiveData：对指定数据表采样扫描内容中的敏感数据</li>
 * </ul>
 * 注意：敏感规则匹配基于新表真实列 rule_pattern（匹配模式）/rule_name（规则名称），
 * 旧字段 fieldNamePattern/regexPattern/sensitiveType 已废弃。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClassificationService {

    /** 表名白名单（仅允许字母/下划线开头，字母数字下划线组合，防注入） */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** 敏感规则 Mapper */
    private final DamSensitiveRuleMapper sensitiveRuleMapper;

    /** 资产 Mapper（跨模块复用 catalog） */
    private final DamAssetMapper assetMapper;

    /** 字段 Mapper（metadata_column 表） */
    private final DamFieldMapper fieldMapper;

    /** 动态 SQL 查询 Mapper（采样扫描） */
    private final DynamicQueryMapper dynamicQueryMapper;

    /** 系统配置 Mapper（读取敏感级别排序） */
    private final DamConfigMapper configMapper;

    /**
     * 自动分类分级：扫描所有资产的字段名，匹配敏感规则
     * <p>
     * 通过资产的多态外键（source_metadata_type=TABLE，source_metadata_id=metadata_table.id）
     * 定位技术元数据表，遍历其字段与启用中的敏感规则匹配，返回匹配明细。
     *
     * @return 分类结果 { matchedAssets, matchedFields, totalAssets, matches }
     */
    public Map<String, Object> autoClassify() {
        // 加载启用中的敏感规则与全部资产
        List<DamSensitiveRule> rules = sensitiveRuleMapper.selectList(
                new LambdaQueryWrapper<DamSensitiveRule>().eq(DamSensitiveRule::getStatus, 1));
        List<DamAsset> allAssets = assetMapper.selectList(null);

        int matchedAssets = 0;
        int matchedFields = 0;
        List<Map<String, Object>> matches = new ArrayList<>();

        for (DamAsset asset : allAssets) {
            // 通过多态外键：source_metadata_type=TABLE 时，source_metadata_id 即 metadata_table.id
            Long tableId = "TABLE".equalsIgnoreCase(asset.getSourceMetadataType()) ? asset.getSourceMetadataId() : null;
            if (tableId == null) continue;

            // 查询该技术元数据表下的全部字段
            List<DamField> fields = fieldMapper.selectList(
                    new LambdaQueryWrapper<DamField>().eq(DamField::getTableId, tableId));

            boolean assetMatched = false;

            for (DamField field : fields) {
                for (DamSensitiveRule rule : rules) {
                    if (matchField(field, rule)) {
                        matchedFields++;
                        assetMatched = true;

                        // 新表 metadata_column 无安全等级列，仅记录匹配结果
                        Map<String, Object> match = new HashMap<>();
                        match.put("assetId", asset.getId());
                        match.put("assetName", asset.getAssetName());
                        match.put("tableId", tableId);
                        match.put("fieldId", field.getId());
                        match.put("fieldName", field.getFieldName());
                        match.put("sensitiveType", rule.getSensitiveType());
                        match.put("level", rule.getLevel());
                        match.put("ruleName", rule.getRuleName());
                        matches.add(match);
                    }
                }
            }

            if (assetMatched) {
                matchedAssets++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("matchedAssets", matchedAssets);
        result.put("matchedFields", matchedFields);
        result.put("totalAssets", allAssets.size());
        result.put("matches", matches);
        return result;
    }

    /**
     * 敏感数据扫描：对指定资产的数据内容进行采样扫描
     * <p>
     * 表名必须显式传入（asset_info 无 table_name 列），
     * 采样行数默认 100，扫描结果中的命中值自动脱敏展示。
     *
     * @param assetId   资产ID（可选）
     * @param tableName 待扫描的数据表名（必填）
     * @param sampleSize 采样行数
     * @return 扫描结果 { tableName, scannedRows, findings, totalFindings }
     */
    public Map<String, Object> scanSensitiveData(Long assetId, String tableName, int sampleSize) {
        // 新表 asset_info 无 table_name 列，表名必须由调用方显式传入（或从资产元数据上下文获取）
        if (StrUtil.isBlank(tableName)) {
            throw new BusinessException("请指定待扫描的数据表名 tableName");
        }

        // 加载启用中的敏感规则
        List<DamSensitiveRule> rules = sensitiveRuleMapper.selectList(
                new LambdaQueryWrapper<DamSensitiveRule>().eq(DamSensitiveRule::getStatus, 1));
        List<Map<String, Object>> findings = new ArrayList<>();
        int scannedRows = 0;

        // 表名白名单校验（防 SQL 注入）
        if (!SAFE_TABLE_NAME.matcher(tableName).matches()) {
            log.warn("非法的表名格式: {}", tableName);
            throw new BusinessException("非法的表名格式: " + tableName);
        }

        // LIMIT 值用字符串形式传入 DynamicQueryMapper（int 类型安全）；PostgreSQL 不使用反引号
        String limitStr = String.valueOf(sampleSize);
        List<Map<String, Object>> sampleRows = dynamicQueryMapper.executeQuery(
                "SELECT * FROM " + tableName + " LIMIT " + limitStr);

        for (Map<String, Object> row : sampleRows) {
            scannedRows++;
            // 遍历每行的每个字段值，与敏感规则的正则模式匹配
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String colName = entry.getKey();
                String colValue = entry.getValue() != null ? entry.getValue().toString() : "";
                if (colValue.isEmpty()) continue;

                for (DamSensitiveRule rule : rules) {
                    // 优先使用真实列 rule_pattern 作为匹配正则
                    if (rule.getRulePattern() != null && !rule.getRulePattern().isEmpty()) {
                        try {
                            Pattern pattern = Pattern.compile(rule.getRulePattern());
                            if (pattern.matcher(colValue).matches()) {
                                Map<String, Object> finding = new HashMap<>();
                                finding.put("fieldName", colName);
                                finding.put("sampleValue", maskValue(colValue));
                                finding.put("sensitiveType", rule.getSensitiveType());
                                finding.put("level", rule.getLevel());
                                finding.put("ruleName", rule.getRuleName());
                                findings.add(finding);
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("tableName", tableName);
        result.put("scannedRows", scannedRows);
        result.put("findings", findings);
        result.put("totalFindings", findings.size());
        return result;
    }

    /**
     * 判断字段是否命中敏感规则
     * <p>
     * 命中依据：规则匹配模式（rule_pattern）对字段名做包含/正则匹配；
     * 旧字段 fieldNamePattern/regexPattern 兼容为空。
     *
     * @param field 字段实体
     * @param rule  敏感规则
     * @return true-命中
     */
    private boolean matchField(DamField field, DamSensitiveRule rule) {
        // 规则匹配模式（新表真实列 rule_pattern）对字段名做包含匹配
        if (StrUtil.isNotBlank(rule.getRulePattern()) && StrUtil.isNotBlank(field.getFieldName())) {
            if (field.getFieldName().toLowerCase().contains(rule.getRulePattern().toLowerCase())) {
                return true;
            }
            // 兼容旧字段名模式（fieldNamePattern 旧字段，实际为 null）
            String[] patterns = rule.getFieldNamePattern() == null
                    ? new String[0] : rule.getFieldNamePattern().split("[,，]");
            for (String p : patterns) {
                String trimmed = p.trim();
                if (trimmed.isEmpty()) continue;
                if (field.getFieldName().toLowerCase().contains(trimmed.toLowerCase())) {
                    return true;
                }
                if (field.getFieldAlias() != null &&
                        field.getFieldAlias().toLowerCase().contains(trimmed.toLowerCase())) {
                    return true;
                }
            }
        }
        // 正则模式匹配（旧字段 regexPattern 兼容为空）
        if (StrUtil.isNotBlank(rule.getRegexPattern()) && StrUtil.isNotBlank(field.getFieldName())) {
            try {
                return Pattern.compile(rule.getRegexPattern()).matcher(field.getFieldName()).find();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /**
     * 判断新密级是否高于当前密级（按 sys_config 中 sensitive_level 的排序号比较）
     *
     * @param newLevel     新密级
     * @param currentLevel 当前密级
     * @return true-新密级更高
     */
    private boolean isHigherLevel(String newLevel, String currentLevel) {
        // 从 sys_config 读取敏感级别的排序映射
        Map<String, Integer> levelOrder = new HashMap<>();
        List<DamConfig> dictList = configMapper.selectList(
                new LambdaQueryWrapper<DamConfig>()
                        .eq(DamConfig::getConfigType, "sensitive_level")
                        .eq(DamConfig::getStatus, 1));
        for (DamConfig d : dictList) {
            levelOrder.put(d.getConfigCode(), d.getSortOrder());
        }
        return levelOrder.getOrDefault(newLevel, 0) > levelOrder.getOrDefault(currentLevel, 0);
    }

    /**
     * 脱敏值（保留首尾2位，中间打码；长度不足4位整体打码）
     *
     * @param value 原始值
     * @return 脱敏后的值
     */
    private String maskValue(String value) {
        if (value.length() <= 4) return "****";
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}

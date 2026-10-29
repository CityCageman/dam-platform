package com.dam.catalog.service;

import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.entity.DamField;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.catalog.repository.DamFieldMapper;
import com.dam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入服务
 * <p>
 * 支持 CSV/Excel 两种格式导入资产与字段元数据；
 * 逐行容错（单行失败不影响整体），并返回每行的导入结果供前端展示。
 * 注意：字段导入通过资产多态外键（source_metadata_id=metadata_table.id）
 * 关联到 metadata_column，新表无 asset_id 列。
 *
 * @author DAM Platform
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    /** 资产 Mapper */
    private final DamAssetMapper assetMapper;

    /** 字段 Mapper */
    private final DamFieldMapper fieldMapper;

    /**
     * 从CSV导入资产
     * <p>
     * 列格式：资产名称, 资产类型, 描述, [来源系统], [数据库名], [表名]
     *
     * @param file         上传的 CSV 文件
     * @param categoryId   资产分类ID
     * @param sourceSystem 来源系统
     * @return 每行导入结果描述列表
     */
    @Transactional
    public List<String> importFromCsv(MultipartFile file, Long categoryId, String sourceSystem) {
        List<String> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine(); // 跳过标题行
            if (headerLine == null) {
                throw new BusinessException("CSV文件为空");
            }
            String line;
            int lineNum = 1;
            // 逐行解析导入（单行失败不中断）
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String[] fields = parseCsvLine(line);
                // 至少需要 资产名称/类型/描述 三列
                if (fields.length < 3) {
                    results.add("第" + lineNum + "行: 字段不足，跳过");
                    continue;
                }
                try {
                    DamAsset asset = buildAsset(
                        fields[0].trim(),
                        fields[1].trim(),
                        fields[2].trim(),
                        sourceSystem,
                        fields.length > 4 ? fields[4].trim() : "",
                        fields.length > 5 ? fields[5].trim() : "",
                        categoryId
                    );
                    assetMapper.insert(asset);
                    results.add("第" + lineNum + "行: " + asset.getAssetName() + " 导入成功");
                } catch (Exception e) {
                    results.add("第" + lineNum + "行: 导入失败 - " + e.getMessage());
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("CSV解析失败: " + e.getMessage());
        }
        return results;
    }

    /**
     * 从Excel导入资产（列顺序与CSV一致）
     *
     * @param file         上传的 Excel 文件
     * @param categoryId   资产分类ID
     * @param sourceSystem 来源系统
     * @return 每行导入结果描述列表
     */
    @Transactional
    public List<String> importFromExcel(MultipartFile file, Long categoryId, String sourceSystem) {
        List<String> results = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            // 从第2行（索引1）开始，跳过标题行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    DamAsset asset = buildAsset(
                        getCellValue(row.getCell(0)),
                        getCellValue(row.getCell(1)),
                        getCellValue(row.getCell(2)),
                        sourceSystem,
                        getCellValue(row.getCell(4)),
                        getCellValue(row.getCell(5)),
                        categoryId
                    );
                    assetMapper.insert(asset);
                    results.add("第" + (i + 1) + "行: " + asset.getAssetName() + " 导入成功");
                } catch (Exception e) {
                    results.add("第" + (i + 1) + "行: 导入失败 - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new BusinessException("Excel解析失败: " + e.getMessage());
        }
        return results;
    }

    /**
     * 从CSV导入字段元数据
     * CSV格式: fieldName, fieldAlias, dataType, dataLength, isPrimaryKey, isNullable, defaultValue, comment
     *
     * @param file    上传的 CSV 文件
     * @param assetId 目标资产ID
     * @return 每行导入结果描述列表
     */
    @Transactional
    public List<String> importFieldsFromCsv(MultipartFile file, Long assetId) {
        List<String> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException("CSV文件为空");
            }
            String line;
            int lineNum = 1;
            int sortOrder = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                String[] fields = parseCsvLine(line);
                // 至少需要 字段名/别名/类型 三列
                if (fields.length < 3) {
                    results.add("第" + lineNum + "行: 字段不足，跳过");
                    continue;
                }
                try {
                    DamField damField = buildField(
                        assetId,
                        fields[0].trim(),
                        fields[1].trim(),
                        fields[2].trim(),
                        fields.length > 3 ? parseIntSafe(fields[3].trim()) : null,
                        fields.length > 4 ? parseIntSafe(fields[4].trim()) : 0,
                        fields.length > 5 ? parseIntSafe(fields[5].trim()) : 1,
                        fields.length > 6 ? fields[6].trim() : "",
                        fields.length > 7 ? fields[7].trim() : "",
                        sortOrder++
                    );
                    fieldMapper.insert(damField);
                    results.add("第" + lineNum + "行: 字段 " + damField.getFieldName() + " 导入成功");
                } catch (Exception e) {
                    results.add("第" + lineNum + "行: 导入失败 - " + e.getMessage());
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("CSV解析失败: " + e.getMessage());
        }
        return results;
    }

    /**
     * 从Excel导入字段元数据
     * Excel列: fieldName, fieldAlias, dataType, dataLength, isPrimaryKey, isNullable, defaultValue, comment
     *
     * @param file    上传的 Excel 文件
     * @param assetId 目标资产ID
     * @return 每行导入结果描述列表
     */
    @Transactional
    public List<String> importFieldsFromExcel(MultipartFile file, Long assetId) {
        List<String> results = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int sortOrder = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    DamField damField = buildField(
                        assetId,
                        getCellValue(row.getCell(0)),
                        getCellValue(row.getCell(1)),
                        getCellValue(row.getCell(2)),
                        parseIntSafe(getCellValue(row.getCell(3))),
                        parseIntSafe(getCellValue(row.getCell(4))),
                        parseIntSafe(getCellValue(row.getCell(5))),
                        getCellValue(row.getCell(6)),
                        getCellValue(row.getCell(7)),
                        sortOrder++
                    );
                    fieldMapper.insert(damField);
                    results.add("第" + (i + 1) + "行: 字段 " + damField.getFieldName() + " 导入成功");
                } catch (Exception e) {
                    results.add("第" + (i + 1) + "行: 导入失败 - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new BusinessException("Excel解析失败: " + e.getMessage());
        }
        return results;
    }

    /**
     * 组装字段实体（通过资产多态外键解析技术元数据表ID，metadata_column 按 table_id 关联）
     */
    private DamField buildField(Long assetId, String fieldName, String fieldAlias, String dataType,
                                Integer dataLength, Integer isPrimaryKey, Integer isNullable,
                                String defaultValue, String comment, Integer sortOrder) {
        DamField damField = new DamField();
        // 通过资产多态外键定位 metadata_table.id（新表无 asset_id 列）
        DamAsset asset = assetMapper.selectById(assetId);
        if (asset != null && asset.getSourceMetadataId() != null) {
            damField.setTableId(asset.getSourceMetadataId());
        }
        damField.setFieldName(fieldName);
        damField.setFieldAlias(fieldAlias);
        damField.setDataType(dataType);
        damField.setDataLength(dataLength);
        damField.setIsPrimaryKey(isPrimaryKey != null && isPrimaryKey == 1);
        damField.setIsNullable(isNullable != null && isNullable == 1);
        damField.setDefaultValue(defaultValue);
        damField.setComment(comment);
        damField.setSortOrder(sortOrder);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        damField.setCreatedAt(now);
        damField.setUpdatedAt(now);
        damField.setCreatedBy("system");
        damField.setUpdatedBy("system");
        return damField;
    }

    /**
     * 安全解析整数（兼容科学计数法等格式，失败返回 null）
     */
    private Integer parseIntSafe(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 组装资产实体（自动补全审计字段与默认状态）
     */
    private DamAsset buildAsset(String assetName, String assetType, String description,
                                    String sourceSystem, String databaseName, String tableName,
                                    Long categoryId) {
        DamAsset asset = new DamAsset();
        asset.setAssetName(assetName);
        asset.setAssetType(assetType);
        asset.setDescription(description);
        asset.setSourceSystem(sourceSystem);
        asset.setDatabaseName(databaseName);
        asset.setTableName(tableName);
        asset.setCategoryId(categoryId);
        asset.setSecurityLevel("internal");
        asset.setStatus(1);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        asset.setCreatedBy("system");
        asset.setUpdatedBy("system");
        return asset;
    }

    /**
     * 读取 Excel 单元格值（字符串/数值/布尔统一转字符串）
     */
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /**
     * 解析 CSV 行（支持双引号包裹的字段与引号内逗号）
     */
    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        // 逐字符解析，引号内逗号不作为分隔符
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }
}

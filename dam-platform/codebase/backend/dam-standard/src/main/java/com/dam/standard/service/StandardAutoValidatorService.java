package com.dam.standard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.entity.DamField;
import com.dam.catalog.repository.DamAssetMapper;
import com.dam.catalog.repository.DamFieldMapper;
import com.dam.operations.entity.DamTicket;
import com.dam.operations.repository.DamTicketMapper;
import com.dam.standard.entity.DamDeviationReport;
import com.dam.standard.entity.DamStandard;
import com.dam.standard.entity.DamStandardMapping;
import com.dam.standard.entity.DamStandardValue;
import com.dam.standard.repository.DamDeviationReportMapper;
import com.dam.standard.repository.DamStandardMapper;
import com.dam.standard.repository.DamStandardMappingMapper;
import com.dam.standard.repository.DamStandardValueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 标准自动校验服务
 * 定时扫描资产字段，对比标准值域，生成偏差报告
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StandardAutoValidatorService {

    private final DamStandardMapper standardMapper;
    private final DamStandardValueMapper standardValueMapper;
    private final DamStandardMappingMapper mappingMapper;
    private final DamDeviationReportMapper deviationReportMapper;
    private final DamAssetMapper assetMapper;
    private final DamFieldMapper fieldMapper;
    private final DamTicketMapper ticketMapper;

    /**
     * 执行自动校验，返回偏差报告数量
     */
    @Transactional
    public int executeAutoValidation() {
        log.info("开始标准自动校验...");
        List<DamStandard> standards = standardMapper.selectList(
                new LambdaQueryWrapper<DamStandard>().eq(DamStandard::getStatus, 1));
        if (standards.isEmpty()) {
            log.info("无可用的已发布标准");
            return 0;
        }

        List<DamDeviationReport> newReports = new ArrayList<>();

        for (DamStandard standard : standards) {
            // 获取已有映射
            List<DamStandardMapping> existingMappings = mappingMapper.selectList(
                    new LambdaQueryWrapper<DamStandardMapping>().eq(DamStandardMapping::getStandardCodeTableId, standard.getId()));

            // 获取标准值域
            List<DamStandardValue> values = standardValueMapper.selectList(
                    new LambdaQueryWrapper<DamStandardValue>().eq(DamStandardValue::getCodeTableId, standard.getId()));

            // 如果没有值域定义，跳过
            if (values.isEmpty()) continue;

            // 获取已映射的资产
            for (DamStandardMapping mapping : existingMappings) {
                List<DamDeviationReport> reports = checkAssetCompliance(standard, mapping, values);
                newReports.addAll(reports);
            }
        }

        // 批量保存偏差报告
        int count = 0;
        for (DamDeviationReport report : newReports) {
            deviationReportMapper.insert(report);
            count++;
        }

        // 为严重偏差自动创建整改工单
        for (DamDeviationReport report : newReports) {
            if ("high".equals(report.getSeverity())) {
                createRemediationTicket(report);
            }
        }

        log.info("标准自动校验完成，发现{}个偏差，已创建{}个整改工单", count, newReports.stream()
                .filter(r -> "high".equals(r.getSeverity())).count());
        return count;
    }

    /**
     * 检查单个资产映射的合规性
     */
    private List<DamDeviationReport> checkAssetCompliance(DamStandard standard, DamStandardMapping mapping,
                                                          List<DamStandardValue> validValues) {
        List<DamDeviationReport> reports = new ArrayList<>();

        // 检查字段值是否匹配标准值域
        if (mapping.getSourceColumn() != null) {
            String fieldName = mapping.getSourceColumn();
            String actualValue = mapping.getSourceValue();
            boolean matched = validValues.stream()
                    .anyMatch(v -> actualValue != null && (actualValue.equals(v.getCodeValue())
                            || actualValue.equals(v.getCodeName())));
            if (!matched) {
                DamDeviationReport report = createReport(standard, mapping, fieldName,
                        "字段值不匹配标准值域", actualValue,
                        validValues.stream().map(DamStandardValue::getCodeValue).reduce((a, b) -> a + "," + b).orElse(""),
                        "value_mismatch", "medium");
                reports.add(report);
            }
        }

        return reports;
    }

    /**
     * 组装标准偏差报告实体（自动生成报告编号并补全审计字段）
     *
     * @param standard      关联的标准码表
     * @param mapping       触发偏差的标准映射
     * @param fieldName     偏差字段名
     * @param type          检查类型
     * @param actual        实际值
     * @param expected      期望值（标准值域列表）
     * @param deviationType 偏差描述
     * @param severity      严重程度
     * @return 偏差报告实体
     */
    private DamDeviationReport createReport(DamStandard standard, DamStandardMapping mapping,
                                            String fieldName, String type,
                                            String actual, String expected, String deviationType, String severity) {
        DamDeviationReport report = new DamDeviationReport();
        report.setReportNo("DR-" + System.currentTimeMillis() % 1000000);
        report.setCheckType(type);
        report.setStandardCodeTableId(standard.getId());
        report.setColumnName(fieldName);
        report.setExpectedValue(expected);
        report.setActualValue(actual);
        report.setDeviationDescription(deviationType);
        report.setSeverity(severity);
        report.setStatus(0);
        report.setCheckTime(LocalDateTime.now());
        java.time.LocalDateTime now = LocalDateTime.now();
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        report.setCreatedBy("system");
        report.setUpdatedBy("system");
        return report;
    }

    /**
     * 为严重偏差创建整改工单
     */
    private void createRemediationTicket(DamDeviationReport report) {
        try {
            DamTicket ticket = new DamTicket();
            ticket.setTicketNo("SD-" + System.currentTimeMillis() % 1000000);
            ticket.setTicketType("change_request");
            ticket.setTitle(String.format("[标准偏差] %s - 标准[%s]", report.getColumnName(), report.getReportNo()));
            ticket.setDescription(String.format(
                    "偏差类型: %s\n严重级别: %s\n期望值: %s\n实际值: %s\n检查时间: %s",
                    report.getDeviationDescription(), report.getSeverity(),
                    report.getExpectedValue(), report.getActualValue(), report.getCheckTime()));
            ticket.setPriority(report.getSeverity());
            ticket.setStatus(0);
            ticket.setRelatedAssetId(report.getTableId());
            java.time.LocalDateTime now = LocalDateTime.now();
            ticket.setCreatedAt(now);
            ticket.setUpdatedAt(now);
            ticket.setCreatedBy("system");
            ticket.setUpdatedBy("system");
            ticketMapper.insert(ticket);
            log.info("已为标准偏差创建整改工单: ticketNo={}", ticket.getTicketNo());
        } catch (Exception e) {
            log.error("创建标准偏差整改工单失败: {}", e.getMessage());
        }
    }
}

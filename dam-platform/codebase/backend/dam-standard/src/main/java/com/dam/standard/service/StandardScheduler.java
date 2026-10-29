package com.dam.standard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 标准自动校验定时调度器
 * <p>
 * 每天凌晨 2 点自动执行一次标准合规性校验，
 * 由 {@link StandardAutoValidatorService} 生成偏差报告并自动创建整改工单。
 *
 * @author DAM Platform
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StandardScheduler {

    /** 标准自动校验服务 */
    private final StandardAutoValidatorService autoValidatorService;

    /**
     * 每天凌晨2点执行一次标准自动校验
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledValidation() {
        log.info("开始定时标准自动校验...");
        // 执行校验并统计偏差数量
        int deviationCount = autoValidatorService.executeAutoValidation();
        log.info("定时标准自动校验完成，发现{}个偏差", deviationCount);
    }
}

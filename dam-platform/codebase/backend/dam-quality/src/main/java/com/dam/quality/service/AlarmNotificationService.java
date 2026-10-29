package com.dam.quality.service;

import com.dam.quality.entity.DamQualityRule;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 质量告警通知服务
 * 支持邮件和企业微信通知
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmNotificationService {

    /** 邮件发送器（Spring Boot 自动装配） */
    private final JavaMailSender mailSender;

    /** 发件人邮箱（从配置 spring.mail.username 读取，默认为空） */
    @Value("${spring.mail.username:}")
    private String mailFrom;

    /**
     * 发送告警通知
     * <p>
     * 告警渠道取规则 alarmChannel 字段（旧字段兼容，新表无此列，实际为 null，默认仅记日志）；
     * 渠道包含 email/mail 时发送邮件，包含 wechat/wecom 时走企业微信（待配置 Webhook）。
     *
     * @param rule   触发告警的质量规则
     * @param score  当前质量得分
     * @param detail 告警详情
     */
    public void sendAlarm(DamQualityRule rule, double score, String detail) {
        // 解析告警渠道（旧字段 alarmChannel 不可用时默认仅记录日志）
        String channel = rule.getAlarmChannel();
        if (channel == null || channel.isEmpty()) {
            channel = "log"; // 默认仅记录日志
        }

        String title = String.format("[质量告警] 规则[%s]触发告警", rule.getRuleName());
        String content = String.format(
                "规则名称: %s\n规则编码: %s\n当前得分: %.2f\n告警阈值: %s\n检测详情: %s\n触发时间: %s",
                rule.getRuleName(), rule.getRuleCode(), score,
                rule.getAlarmThreshold() != null ? rule.getAlarmThreshold().toString() : "-",
                detail, java.time.LocalDateTime.now()
        );

        log.warn("告警通知: {} - {}", title, content);

        // 按渠道分发通知
        if (channel.contains("email") || channel.contains("mail")) {
            sendEmail(title, content);
        }
        if (channel.contains("wechat") || channel.contains("wecom")) {
            sendWeChat(title, content);
        }
    }

    /**
     * 发送邮件通知
     */
    private void sendEmail(String title, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(mailFrom);
            // 告警接收人建议从配置读取，这里暂时发送给自己
            helper.setTo(mailFrom);
            helper.setSubject(title);
            helper.setText(content.replace("\n", "<br/>"), true);
            mailSender.send(message);
            log.info("邮件告警发送成功: {}", title);
        } catch (Exception e) {
            log.error("邮件告警发送失败: {}", e.getMessage());
        }
    }

    /**
     * 发送企业微信通知
     * 通过 Webhook URL 发送 Markdown 消息
     */
    private void sendWeChat(String title, String content) {
        try {
            // Webhook URL 建议从配置读取，这里保持扩展性
            log.info("微信通知待配置Webhook: {} - {}", title, content);
        } catch (Exception e) {
            log.error("微信通知发送失败: {}", e.getMessage());
        }
    }
}

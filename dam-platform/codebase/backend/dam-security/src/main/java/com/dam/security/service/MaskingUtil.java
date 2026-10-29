package com.dam.security.service;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 脱敏算法工具类
 * <p>
 * 支持多种脱敏算法：掩码（mask）/替换（replace）/哈希（hash）/截断（shuffle）/身份证（id_card）/
 * 手机号（phone）/邮箱（email）/姓名（name）；
 * 算法选择基于字段名推断，脱敏策略请配置在 security_masking_policy 表供查询展示。
 *
 * @author DAM Platform
 */
@Component
@Slf4j
public class MaskingUtil {

    /** 脱敏算法注册表：算法名称 → 处理函数 */
    private static final Map<String, java.util.function.Function<String, String>> ALGORITHMS = new HashMap<>();

    static {
        // 掩码: 保留前2后2，中间用****代替
        ALGORITHMS.put("mask", value -> {
            if (value == null) return null;
            if (value.length() <= 4) return "****";
            return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
        });

        // 替换: 用固定字符替换
        ALGORITHMS.put("replace", value -> {
            if (value == null) return null;
            return "******";
        });

        // 哈希: SHA-256 取前16位
        ALGORITHMS.put("hash", value -> {
            if (value == null) return null;
            return DigestUtil.sha256Hex(value).substring(0, 16);
        });

        // 截断: 只保留前4位
        ALGORITHMS.put("shuffle", value -> {
            if (value == null) return null;
            return value.length() > 4 ? value.substring(0, 4) + "****" : value;
        });

        // 身份证掩码: 保留前6后4
        ALGORITHMS.put("id_card", value -> {
            if (value == null) return null;
            if (value.length() <= 10) return "****";
            return value.substring(0, 6) + "********" + value.substring(value.length() - 4);
        });

        // 手机号掩码: 保留前3后4
        ALGORITHMS.put("phone", value -> {
            if (value == null) return null;
            if (value.length() <= 7) return "****";
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        });

        // 邮箱掩码: 保留前2和域名
        ALGORITHMS.put("email", value -> {
            if (value == null) return null;
            int atIdx = value.indexOf('@');
            if (atIdx <= 1) return "***" + value.substring(atIdx);
            return value.substring(0, 2) + "***" + value.substring(atIdx);
        });

        // 姓名掩码: 保留姓
        ALGORITHMS.put("name", value -> {
            if (value == null) return null;
            if (value.length() <= 1) return value;
            return value.charAt(0) + "**";
        });
    }

    /**
     * 按算法名称执行脱敏
     *
     * @param value     原始值
     * @param algorithm 算法名称（mask/replace/hash/phone/email 等）
     * @return 脱敏后的值
     */
    public String mask(String value, String algorithm) {
        // 空值直接返回
        if (value == null || value.isEmpty()) return value;
        // 默认掩码算法
        if (algorithm == null) algorithm = "mask";

        // 取算法函数，未知算法回退到 mask
        java.util.function.Function<String, String> func = ALGORITHMS.getOrDefault(algorithm, ALGORITHMS.get("mask"));
        return func.apply(value);
    }

    /**
     * 根据敏感类型和字段名自动匹配最佳脱敏算法
     * <p>
     * 新表 security_masking_policy 无 sensitive_type/masking_algorithm 列，
     * 此处直接按字段名推断算法（兜底逻辑）。
     *
     * @param sensitiveType 敏感类型（预留）
     * @param fieldName     字段名（用于推断算法）
     * @return 算法名称，默认 mask
     */
    public String getBestAlgorithm(String sensitiveType, String fieldName) {
        // 按字段名推断（兜底逻辑）：手机/邮箱/姓名/证件号/卡号等关键字
        if (fieldName != null) {
            String lower = fieldName.toLowerCase();
            if (lower.contains("phone") || lower.contains("mobile")) return "phone";
            if (lower.contains("email")) return "email";
            if (lower.contains("name") || lower.contains("姓名")) return "name";
            if (lower.contains("id_card") || lower.contains("idno") || lower.contains("cert_no")) return "id_card";
            if (lower.contains("bank") || lower.contains("card")) return "mask";
        }
        return "mask";
    }

    /**
     * 获取所有可用的脱敏算法名称
     */
    public Set<String> getAvailableAlgorithms() {
        return ALGORITHMS.keySet();
    }

    /**
     * 根据正则匹配数据内容，判断是否需要脱敏
     */
    public boolean isSensitiveValue(String value, String sensitiveType) {
        if (value == null || value.isEmpty()) return false;
        return switch (sensitiveType) {
            case "id_card" -> value.matches(".*[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx].*");
            case "phone" -> value.matches(".*1[3-9]\\d{9}.*");
            case "email" -> value.contains("@");
            default -> true;
        };
    }
}

package com.dam.security.interceptor;

import com.dam.catalog.entity.DamAsset;
import com.dam.catalog.entity.DamField;
import com.dam.security.entity.DamSensitiveRule;
import com.dam.security.repository.DamSensitiveRuleMapper;
import com.dam.security.service.AbacPermissionService;
import com.dam.security.service.MaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.Setter;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 动态脱敏拦截器
 * 拦截MyBatis查询结果，对敏感字段实时脱敏
 * 
 * 工作流程：
 * 1. 拦截 ResultSetHandler.handleResultSets
 * 2. 获取当前用户角色和部门
 * 3. 对返回结果中的敏感字段应用脱敏策略
 */
@Intercepts({
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {java.sql.Statement.class})
})
@Slf4j
public class DynamicMaskingInterceptor implements Interceptor {

    @Setter
    private MaskingUtil maskingUtil;
    @Setter
    private AbacPermissionService abacPermissionService;
    @Setter
    private DamSensitiveRuleMapper sensitiveRuleMapper;

    // 缓存字段敏感信息：entityClass -> fieldName -> isSensitive
    private final Map<Class<?>, Map<String, Boolean>> sensitiveFieldCache = new ConcurrentHashMap<>();
    // 缓存字段敏感类型：entityClass -> fieldName -> sensitiveType
    private final Map<Class<?>, Map<String, String>> fieldSensitiveTypeCache = new ConcurrentHashMap<>();
    // 缓存字段安全等级：entityClass -> fieldName -> securityLevel
    private final Map<Class<?>, Map<String, String>> fieldSecurityLevelCache = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();

        // 获取当前用户信息
        String role = getCurrentUserRole();
        String department = getCurrentUserDepartment();

        // 管理员不过滤
        if ("admin".equals(role) || role == null) {
            return result;
        }

        // 处理返回结果
        if (result instanceof List) {
            for (Object item : (List<?>) result) {
                if (item != null) {
                    maskSensitiveFields(item, role, department);
                }
            }
        } else if (result != null) {
            maskSensitiveFields(result, role, department);
        }

        return result;
    }

    /**
     * 对对象中的敏感字段进行脱敏
     */
    private void maskSensitiveFields(Object obj, String role, String department) {
        Class<?> clazz = obj.getClass();

        // 检查是否是已知的实体类型
        Map<String, Boolean> sensitiveFields = getSensitiveFields(clazz);
        if (sensitiveFields.isEmpty()) return;

        try {
            for (Map.Entry<String, Boolean> entry : sensitiveFields.entrySet()) {
                String fieldName = entry.getKey();
                if (!entry.getValue()) continue;

                Field field = getField(clazz, fieldName);
                if (field == null) continue;

                field.setAccessible(true);
                Object value = field.get(obj);
                if (!(value instanceof String strValue) || strValue.isEmpty()) continue;

                // 检查ABAC权限 - 用户是否有权限查看原始值
                String securityLevel = getSecurityLevel(clazz, fieldName);
                boolean canView = abacPermissionService.checkPermission(role, department, securityLevel, "view");

                if (!canView) {
                    // 获取最佳脱敏算法并执行脱敏
                    String algorithm = getBestAlgorithm(clazz, fieldName);
                    String maskedValue = maskingUtil.mask(strValue, algorithm);
                    field.set(obj, maskedValue);
                    log.debug("动态脱敏: {}.{} -> {}", clazz.getSimpleName(), fieldName, maskedValue);
                }
            }
        } catch (Exception e) {
            log.warn("动态脱敏异常: {}", e.getMessage());
        }
    }

    /**
     * 判断实体类型的哪些字段需要脱敏
     */
    private Map<String, Boolean> getSensitiveFields(Class<?> clazz) {
        return sensitiveFieldCache.computeIfAbsent(clazz, c -> {
            Map<String, Boolean> result = new HashMap<>();
            Map<String, String> sensitiveTypes = new HashMap<>();
            Map<String, String> securityLevels = new HashMap<>();

            // 对 DamAsset 类型，按 securityLevel 决定整个对象的脱敏
            if (c == DamAsset.class) {
                fieldSensitiveTypeCache.put(c, sensitiveTypes);
                fieldSecurityLevelCache.put(c, securityLevels);
                return result;
            }

            // 对 DamField 类型，根据 isSensitive 标记脱敏 fieldName/comment
            if (c == DamField.class) {
                result.put("fieldName", true);
                result.put("comment", true);
                sensitiveTypes.put("fieldName", "field_name");
                sensitiveTypes.put("comment", "field_comment");
                securityLevels.put("fieldName", "medium");
                securityLevels.put("comment", "medium");
                fieldSensitiveTypeCache.put(c, sensitiveTypes);
                fieldSecurityLevelCache.put(c, securityLevels);
                return result;
            }

            // 通用规则：从数据库敏感规则表加载字段匹配规则（基于真实列 rule_pattern）
            List<DamSensitiveRule> rules = sensitiveRuleMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DamSensitiveRule>()
                            .eq(DamSensitiveRule::getStatus, 1)
                            .isNotNull(DamSensitiveRule::getRulePattern));

            // 构建 pattern -> rule 映射，每个 pattern 关联到对应规则的敏感类型和等级
            Map<String, String> patternToSensitiveType = new HashMap<>();
            Map<String, String> patternToLevel = new HashMap<>();
            for (DamSensitiveRule rule : rules) {
                // 规则匹配模式按逗号分隔（新表真实列 rule_pattern）
                String[] patterns = rule.getRulePattern() == null
                        ? new String[0] : rule.getRulePattern().split("[,，]");
                for (String p : patterns) {
                    String trimmed = p.trim();
                    if (!trimmed.isEmpty()) {
                        patternToSensitiveType.put(trimmed.toLowerCase(), rule.getSensitiveType());
                        patternToLevel.put(trimmed.toLowerCase(), rule.getLevel());
                    }
                }
            }

            if (!patternToSensitiveType.isEmpty()) {
                for (Field field : getAllFields(c)) {
                    String name = field.getName();
                    String lowerName = name.toLowerCase();
                    for (Map.Entry<String, String> entry : patternToSensitiveType.entrySet()) {
                        if (lowerName.contains(entry.getKey())) {
                            result.put(name, true);
                            sensitiveTypes.put(name, entry.getValue());
                            securityLevels.put(name, patternToLevel.get(entry.getKey()));
                            break;
                        }
                    }
                }
            }

            fieldSensitiveTypeCache.put(c, sensitiveTypes);
            fieldSecurityLevelCache.put(c, securityLevels);
            return result;
        });
    }

    /**
     * 获取字段的安全等级
     */
    private String getSecurityLevel(Class<?> clazz, String fieldName) {
        return fieldSecurityLevelCache
                .getOrDefault(clazz, Map.of())
                .getOrDefault(fieldName, "medium");
    }

    /**
     * 获取字段的最佳脱敏算法
     */
    private String getBestAlgorithm(Class<?> clazz, String fieldName) {
        String sensitiveType = fieldSensitiveTypeCache
                .getOrDefault(clazz, Map.of())
                .get(fieldName);
        return maskingUtil.getBestAlgorithm(sensitiveType, fieldName);
    }

    /**
     * 递归获取所有字段（含父类）
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * 获取指定名称的字段（含父类）
     */
    private Field getField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 从请求上下文中获取当前用户角色
     */
    private String getCurrentUserRole() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String role = request.getHeader("X-User-Role");
                if (role != null) return role;
                // 尝试从attribute获取（由认证过滤器设置）
                Object roleAttr = request.getAttribute("userRole");
                if (roleAttr != null) return roleAttr.toString();
            }
        } catch (Exception e) {
            log.debug("获取用户角色失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从请求上下文中获取当前用户部门
     */
    private String getCurrentUserDepartment() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String dept = request.getHeader("X-User-Department");
                if (dept != null) return dept;
                Object deptAttr = request.getAttribute("userDepartment");
                if (deptAttr != null) return deptAttr.toString();
            }
        } catch (Exception e) {
            log.debug("获取用户部门失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从请求上下文获取当前 HttpServletRequest
     *
     * @return 当前请求（无请求上下文时返回 null）
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return attrs.getRequest();
        }
        return null;
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof ResultSetHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }
}

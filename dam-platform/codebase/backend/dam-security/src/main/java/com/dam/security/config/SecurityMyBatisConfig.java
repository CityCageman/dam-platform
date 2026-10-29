package com.dam.security.config;

import com.dam.security.interceptor.DynamicMaskingInterceptor;
import com.dam.security.repository.DamSensitiveRuleMapper;
import com.dam.security.service.AbacPermissionService;
import com.dam.security.service.MaskingUtil;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 拦截器配置
 * 在SqlSessionFactory就绪后注册动态脱敏拦截器
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class SecurityMyBatisConfig {

    private final SqlSessionFactory sqlSessionFactory;
    private final MaskingUtil maskingUtil;
    private final AbacPermissionService abacPermissionService;
    private final DamSensitiveRuleMapper sensitiveRuleMapper;

    @PostConstruct
    public void init() {
        try {
            DynamicMaskingInterceptor interceptor = new DynamicMaskingInterceptor();
            interceptor.setMaskingUtil(maskingUtil);
            interceptor.setAbacPermissionService(abacPermissionService);
            interceptor.setSensitiveRuleMapper(sensitiveRuleMapper);
            sqlSessionFactory.getConfiguration().addInterceptor(interceptor);
            log.info("动态脱敏拦截器注册成功");
        } catch (Exception e) {
            log.warn("动态脱敏拦截器注册失败(不影响启动): {}", e.getMessage());
        }
    }
}

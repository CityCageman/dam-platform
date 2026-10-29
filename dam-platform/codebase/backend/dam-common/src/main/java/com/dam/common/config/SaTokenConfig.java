package com.dam.common.config;

import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 全局配置
 * <p>
 * 注册 JWT（Stateless 无状态模式）的 StpLogic，使 token 采用 JWT 无状态签发与校验：
 * 校验时直接从 JWT 解析 loginId，不依赖 SaTokenDao 共享存储，各服务仅凭一致的
 * {@code jwt-secret-key} 即可跨服务鉴权。
 * <p>
 * 注意：
 * <ul>
 *   <li>不要使用 StpLogicJwtForSimple —— 它校验时仍会查 SaTokenDao，跨服务会报"token 无效"；</li>
 *   <li>Stateless 模式下 logout 不会立即使已签发 token 失效（无状态），token 到期自然失效；</li>
 *   <li>本项目所有业务服务均扫描 {@code com.dam.common}，故此配置对全部业务服务生效，
 *       网关不扫描该包（仅做 token 非空校验），不受影响。</li>
 * </ul>
 */
@Configuration
public class SaTokenConfig {

    /**
     * 以 JWT（Stateless 模式）替换 sa-token 默认的 UUID + 内存会话存储逻辑。
     *
     * @return JWT 无状态鉴权逻辑
     */
    @Bean
    public StpLogic stpLogic() {
        return new StpLogicJwtForStateless();
    }
}

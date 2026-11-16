package com.dam.portal.service;

import com.dam.common.exception.BusinessException;
import com.dam.portal.model.PortalDomain;
import com.dam.portal.model.PortalModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PortalCatalogService} 纯单元测试。
 * <p>
 * 目录为静态配置，无需启动 Spring 容器或数据库；直接 new 出服务实例即可校验。
 */
@DisplayName("PortalCatalogService 静态目录")
class PortalCatalogServiceTest {

    /** 被测服务，无外部依赖，可直接构造 */
    private final PortalCatalogService service = new PortalCatalogService();

    @Test
    @DisplayName("暴露 8 个能力域与 23 个模块")
    void shouldExposeEightDomainsAndTwentyThreeModules() {
        List<PortalDomain> domains = service.listDomains();

        assertEquals(8, domains.size(), "能力域数量应为 8");
        assertEquals(
                Set.of("asset", "standard", "engineering", "governance",
                        "analytics", "service", "ai", "platform"),
                domains.stream().map(PortalDomain::code).collect(Collectors.toSet()),
                "能力域编码不符合预期");

        int moduleCount = domains.stream().mapToInt(domain -> domain.modules().size()).sum();
        assertEquals(23, moduleCount, "模块总数应为 23");

        Map<String, Object> summary = service.homeSummary();
        assertEquals(8, summary.get("domainCount"), "首页统计能力域数量应为 8");
        assertEquals(23, summary.get("moduleCount"), "首页统计模块数量应为 23");
    }

    @Test
    @DisplayName("能力域编码与模块编码唯一")
    void codesShouldBeUnique() {
        Set<String> domainCodes = new HashSet<>();
        Set<String> moduleCodes = new HashSet<>();

        for (PortalDomain domain : service.listDomains()) {
            assertTrue(domainCodes.add(domain.code()), "重复的能力域编码: " + domain.code());
            for (PortalModule module : domain.modules()) {
                assertTrue(moduleCodes.add(module.code()), "重复的模块编码: " + module.code());
            }
        }

        assertEquals(8, domainCodes.size(), "能力域编码数量应为 8");
        assertEquals(23, moduleCodes.size(), "模块编码数量应为 23");
    }

    @Test
    @DisplayName("每个模块都有路由与状态")
    void everyModuleShouldHaveRouteAndStatus() {
        for (PortalDomain domain : service.listDomains()) {
            assertNotNull(domain.route(), "能力域缺少路由: " + domain.code());
            assertEquals("/domains/" + domain.code(), domain.route(),
                    "能力域路由格式不正确: " + domain.code());

            for (PortalModule module : domain.modules()) {
                String prefix = "/domains/" + domain.code() + "/";

                assertNotNull(module.route(), "模块缺少路由: " + module.code());
                assertTrue(module.route().startsWith(prefix),
                        "模块路由未归属能力域: " + module.code() + " -> " + module.route());
                assertEquals(prefix + module.code().toLowerCase(Locale.ROOT), module.route(),
                        "模块路由格式不正确: " + module.code());

                assertNotNull(module.status(), "模块缺少状态: " + module.code());
                assertTrue(
                        Set.of(PortalModule.STATUS_AVAILABLE, PortalModule.STATUS_PLANNED)
                                .contains(module.status()),
                        "模块状态非法: " + module.code() + " -> " + module.status());
            }
        }
    }

    @Test
    @DisplayName("PLANNED 集合与当前目录一致")
    void plannedModulesShouldMatchCurrentCatalog() {
        Set<String> planned = service.listDomains().stream()
                .flatMap(domain -> domain.modules().stream())
                .filter(module -> PortalModule.STATUS_PLANNED.equals(module.status()))
                .map(PortalModule::code)
                .collect(Collectors.toSet());

        // 与 PortalCatalogService 当前目录保持一致；模块正式上线后同步更新此期望值
        assertEquals(
                Set.of("MDM", "STO", "MKT", "OBS"),
                planned,
                "PLANNED 模块集合与当前目录不一致");
    }

    @Test
    @DisplayName("能力域查询大小写不敏感，不存在时抛 404")
    void requireDomainShouldBeCaseInsensitiveAndRejectUnknown() {
        assertEquals("asset", service.requireDomain(" ASSET ").code(),
                "能力域查询应忽略大小写与首尾空白");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.requireDomain("not-exists"),
                "不存在的能力域应抛出 BusinessException");
        assertEquals(404, exception.getCode(), "不存在的能力域应返回 404");
    }
}

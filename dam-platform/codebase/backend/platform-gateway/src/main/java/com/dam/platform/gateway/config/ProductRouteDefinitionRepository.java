package com.dam.platform.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 /etc/dam-products/routes/*.yaml 加载已安装产品的网关路由。
 * 未安装任何产品时返回空，不影响 application.yml 中的静态路由。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dam.product-routes-enabled", havingValue = "true")
public class ProductRouteDefinitionRepository implements RouteDefinitionRepository {

    @Value("${dam.product-routes-dir:/etc/dam-products/routes}")
    private String routesDir;

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        Path dir = Path.of(routesDir);
        if (!Files.isDirectory(dir)) return Flux.empty();
        List<RouteDefinition> definitions = new ArrayList<>();
        try (var files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".yaml")
                            || p.getFileName().toString().endsWith(".yml"))
                    .forEach(file -> definitions.addAll(load(file)));
        } catch (Exception e) {
            log.warn("加载产品网关路由失败: {}", e.getMessage());
        }
        log.info("已加载产品网关路由 {} 条，目录 {}", definitions.size(), routesDir);
        return Flux.fromIterable(definitions);
    }

    @SuppressWarnings("unchecked")
    private List<RouteDefinition> load(Path file) {
        try {
            Object root = new Yaml().load(Files.readString(file));
            if (!(root instanceof Map<?, ?> map)) return List.of();
            Object routes = map.get("routes");
            if (!(routes instanceof List<?> list)) return List.of();
            List<RouteDefinition> result = new ArrayList<>();
            for (Object row : list) {
                if (!(row instanceof Map<?, ?> r)) continue;
                RouteDefinition def = new RouteDefinition();
                def.setId(String.valueOf(r.get("id")));
                def.setUri(URI.create(String.valueOf(r.get("uri"))));
                if (r.get("order") instanceof Number n) def.setOrder(n.intValue());
                Object predicates = r.get("predicates");
                List<org.springframework.cloud.gateway.handler.predicate.PredicateDefinition> pds = new ArrayList<>();
                if (predicates instanceof List<?> ps) {
                    for (Object p : ps) {
                        String text = String.valueOf(p);
                        if (text.startsWith("Path=")) {
                            var pd = new org.springframework.cloud.gateway.handler.predicate.PredicateDefinition();
                            pd.setName("Path");
                            pd.addArg("_genkey_0", text.substring(5));
                            pds.add(pd);
                        }
                    }
                }
                if (pds.isEmpty()) continue;
                def.setPredicates(pds);
                result.add(def);
            }
            return result;
        } catch (Exception e) {
            log.warn("解析产品路由失败 {}: {}", file, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return Mono.error(new UnsupportedOperationException("产品路由由安装目录管理"));
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return Mono.error(new UnsupportedOperationException("产品路由由安装目录管理"));
    }
}

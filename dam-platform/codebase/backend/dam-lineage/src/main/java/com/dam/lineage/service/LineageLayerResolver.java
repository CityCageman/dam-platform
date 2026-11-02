package com.dam.lineage.service;

import com.dam.lineage.repository.LineageNodeMetaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 血缘层级解析器
 * <p>
 * 把“表名”解析成数仓层级：优先数据源 data_layer，其次 Schema，最后表名前缀。
 * 供解析落库与图谱查询共用，保证写入和展示口径一致。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineageLayerResolver {

    private final LineageNodeMetaMapper nodeMetaMapper;

    /**
     * 批量解析表名层级
     *
     * @param names 表名集合
     * @return 表名 → 层级（保证每个入参都有值）
     */
    public Map<String, String> resolve(Collection<String> names) {
        Map<String, String> out = new HashMap<>();
        if (names == null || names.isEmpty()) {
            return out;
        }
        List<String> list = new LinkedHashSet<>(names).stream()
                .filter(n -> n != null && !n.isBlank())
                .toList();
        if (list.isEmpty()) {
            return out;
        }
        try {
            for (Map<String, Object> row : nodeMetaMapper.selectTableLayers(list)) {
                String name = str(row.get("table_name"));
                if (name != null) {
                    out.put(name, LineageLayer.of("TABLE", name,
                            str(row.get("data_layer")), str(row.get("schema_name"))));
                }
            }
        } catch (Exception e) {
            log.debug("表层级查询失败，降级按名称推断: {}", e.getMessage());
        }
        for (String n : list) {
            out.putIfAbsent(n, LineageLayer.of("TABLE", n));
        }
        return out;
    }

    /**
     * 解析单表层级
     *
     * @param name 表名
     * @return 层级
     */
    public String resolveOne(String name) {
        return resolve(List.of(name)).get(name);
    }

    /**
     * 对象转字符串
     *
     * @param o 对象
     * @return 字符串或 null
     */
    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}

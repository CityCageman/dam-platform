package com.dam.lineage.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 血缘层级工具
 * <p>
 * 将血缘节点归入数仓分层：SOURCE（源库）/ ODS / DWD / DIM / DWS / ADS / REPORT。
 * 优先按节点类型判断（报表 → REPORT），其余按节点编码/表名前缀推断；
 * 未匹配到任何层级时归为 SOURCE，保证跨层判断始终有值。
 *
 * @author DAM Platform
 */
public final class LineageLayer {

    public static final String SOURCE = "SOURCE";
    public static final String ODS = "ODS";
    public static final String DWD = "DWD";
    public static final String DIM = "DIM";
    public static final String DWS = "DWS";
    public static final String ADS = "ADS";
    public static final String REPORT = "REPORT";

    /** 层级顺序（源库 → ODS → DWD/DIM → DWS → ADS → 报表） */
    private static final Map<String, Integer> RANK = Map.of(
            SOURCE, 0, ODS, 1, DWD, 2, DIM, 2, DWS, 3, ADS, 4, REPORT, 5
    );

    private LineageLayer() {
    }

    /**
     * 推断节点层级
     *
     * @param type  节点类型（REPORT/TABLE/VIEW/...）
     * @param label 节点编码或名称（如表名）
     * @return 层级编码
     */
    public static String of(String type, String label) {
        if (type != null && REPORT.equalsIgnoreCase(type)) {
            return REPORT;
        }
        String name = normalize(label);
        if (name.isEmpty()) {
            return SOURCE;
        }
        if (name.startsWith("ads_") || name.startsWith("ads.")) {
            return ADS;
        }
        if (name.startsWith("dws_") || name.startsWith("dws.")) {
            return DWS;
        }
        if (name.startsWith("dwd_") || name.startsWith("dwd.")) {
            return DWD;
        }
        if (name.startsWith("dim_") || name.startsWith("dim.") || name.startsWith("dict_")) {
            return DIM;
        }
        if (name.startsWith("ods_") || name.startsWith("ods.")) {
            return ODS;
        }
        return SOURCE;
    }

    /**
     * 是否跨层（层级不同即视为跨层）
     *
     * @param upstream   上游层级
     * @param downstream 下游层级
     * @return true-跨层
     */
    public static boolean isCross(String upstream, String downstream) {
        return rank(upstream) != rank(downstream);
    }

    /**
     * 层级顺序值，用于前端泳道排序
     *
     * @param layer 层级
     * @return 顺序值（未知层级按 0）
     */
    public static int rank(String layer) {
        return RANK.getOrDefault(layer == null ? "" : layer.toUpperCase(Locale.ROOT), 0);
    }

    /**
     * 解析前端传入的层级过滤串（逗号分隔）
     *
     * @param layers 如 "ADS,DWS,REPORT"
     * @return 合法的层级集合（空集合表示不过滤）
     */
    public static Set<String> parseLayers(String layers) {
        Set<String> out = new LinkedHashSet<>();
        if (layers == null || layers.isBlank()) {
            return out;
        }
        for (String s : layers.split(",")) {
            String v = s.trim().toUpperCase(Locale.ROOT);
            if (RANK.containsKey(v)) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * 规范化名称：去 schema 前缀、去引号、转小写
     *
     * @param label 原始名称
     * @return 规范化名称
     */
    private static String normalize(String label) {
        if (label == null) {
            return "";
        }
        String s = label.trim();
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && dot < s.length() - 1) {
            s = s.substring(dot + 1);
        }
        return s.replace("\"", "").replace("`", "").toLowerCase(Locale.ROOT);
    }
}

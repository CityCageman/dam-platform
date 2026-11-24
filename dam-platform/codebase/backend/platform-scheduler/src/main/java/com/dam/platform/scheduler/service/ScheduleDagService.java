package com.dam.platform.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.security.CredentialCipher;
import com.dam.platform.scheduler.dto.ScheduleDagRequest;
import com.dam.platform.scheduler.dto.ScheduleDagView;
import com.dam.platform.scheduler.entity.ScheduleDag;
import com.dam.platform.scheduler.repository.ScheduleDagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 调度 DAG 定义管理服务。
 * <p>
 * 提供基础顺序 SQL DAG 的真实 CRUD。DAG 定义以 jsonb 原文落库，写入前将
 * nodes[].datasource.password 加密为 {@code ENC1:} 密文（历史明文保持兼容），
 * 执行器按 nodes 顺序读取；所有响应在返回前将 datasource.password 替换为
 * {@code ***}。
 *
 * @author DAM Platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleDagService {

    /** 描述最大长度 */
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    /** DAG 状态集合 */
    private static final Set<String> DAG_STATUSES = Set.of("ENABLED", "DISABLED");

    /** DAG 默认状态 */
    private static final String DEFAULT_STATUS = "ENABLED";

    private final ScheduleDagMapper dagMapper;

    /** 凭据加解密组件，负责节点密码落库前加密 */
    private final CredentialCipher credentialCipher;

    /**
     * 查询 DAG 列表（按更新时间/编码倒序），定义中的密码已脱敏。
     *
     * @return DAG 列表
     */
    @Transactional(readOnly = true)
    public List<ScheduleDagView> listDags() {
        List<ScheduleDag> dags = dagMapper.selectList(new LambdaQueryWrapper<ScheduleDag>()
                .orderByDesc(ScheduleDag::getUpdatedAt)
                .orderByDesc(ScheduleDag::getDagCode));
        return dags.stream().map(this::toView).toList();
    }

    /**
     * 查询 DAG 详情，定义中的密码已脱敏。
     *
     * @param code DAG 编码
     * @return DAG 详情
     */
    @Transactional(readOnly = true)
    public ScheduleDagView getDag(String code) {
        return toView(requireDag(code));
    }

    /**
     * 创建 DAG。
     * <p>
     * 校验编码、名称、定义与状态；dagCode 主键全局唯一。definition 接受 JSON 对象
     * 或 JSON 字符串，响应会脱敏密码。
     *
     * @param request 创建请求
     * @return 创建后的 DAG（密码已脱敏）
     */
    @Transactional
    public ScheduleDagView createDag(ScheduleDagRequest request) {
        if (request == null) {
            throw new BusinessException(400, "DAG 定义不能为空");
        }
        String code = ScheduleDagValidator.requireDagCode(request.getDagCode());
        String name = requireName(request.getDagName());
        String description = optionalText(request.getDescription());
        String status = normalizeStatus(request.getStatus(), DEFAULT_STATUS);
        ObjectNode normalized = ScheduleDagValidator.normalizeDefinition(request.getDefinition());
        encryptNodePasswords(normalized);
        String definition = ScheduleDagValidator.toJson(normalized);

        if (dagMapper.selectById(code) != null) {
            throw new BusinessException(409, "DAG 编码已存在: " + code);
        }

        LocalDateTime now = LocalDateTime.now();
        ScheduleDag dag = new ScheduleDag();
        dag.setDagCode(code);
        dag.setDagName(name);
        dag.setDefinition(definition);
        dag.setDescription(description);
        dag.setStatus(status);
        dag.setCreatedAt(now);
        dag.setUpdatedAt(now);
        try {
            dagMapper.insert(dag);
        } catch (DuplicateKeyException e) {
            // 并发创建时主键兜底，避免把数据库异常暴露为 500
            throw new BusinessException(409, "DAG 编码已存在: " + code);
        }
        log.info("创建调度 DAG: dagCode={}, status={}", code, status);
        return toView(dag);
    }

    /**
     * 更新 DAG。
     * <p>
     * dagCode 不允许修改；dagName/definition/description/status 为空时保留原值，
     * definition 一旦传入则重新完整校验。响应会脱敏密码。
     *
     * @param code    DAG 编码
     * @param request 更新请求
     * @return 更新后的 DAG（密码已脱敏）
     */
    @Transactional
    public ScheduleDagView updateDag(String code, ScheduleDagRequest request) {
        if (request == null) {
            throw new BusinessException(400, "DAG 定义不能为空");
        }
        ScheduleDag dag = requireDag(code);
        if (StringUtils.hasText(request.getDagCode())
                && !request.getDagCode().trim().equals(dag.getDagCode())) {
            throw new BusinessException(400, "DAG 编码不允许修改");
        }
        if (request.getDagName() != null) {
            dag.setDagName(requireName(request.getDagName()));
        }
        if (request.getDefinition() != null) {
            ObjectNode normalized = ScheduleDagValidator.normalizeDefinition(request.getDefinition());
            encryptNodePasswords(normalized);
            dag.setDefinition(ScheduleDagValidator.toJson(normalized));
        }
        if (request.getDescription() != null) {
            dag.setDescription(optionalText(request.getDescription()));
        }
        if (request.getStatus() != null) {
            dag.setStatus(normalizeStatus(request.getStatus(), null));
        }
        dag.setUpdatedAt(LocalDateTime.now());
        dagMapper.updateById(dag);
        log.info("更新调度 DAG: dagCode={}, status={}", dag.getDagCode(), dag.getStatus());
        return toView(dag);
    }

    /**
     * 加密定义中所有节点的 datasource.password。
     * <p>
     * 仅加密非空且未带 {@link CredentialCipher#PREFIX} 前缀的明文；空值与已加密
     * 密文保持原样，避免重复加密。任一节点加密失败立即抛出业务异常并回滚事务，
     * 保证失败时不会以明文落库；异常消息与日志均不包含密码内容。
     *
     * @param definition 已校验并规范化的 DAG 定义
     */
    private void encryptNodePasswords(ObjectNode definition) {
        JsonNode nodes = definition.get("nodes");
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        int index = 0;
        for (JsonNode item : nodes) {
            try {
                JsonNode datasource = item.get("datasource");
                if (datasource instanceof ObjectNode datasourceObject) {
                    JsonNode passwordNode = datasourceObject.get("password");
                    if (passwordNode != null && passwordNode.isString()) {
                        String password = passwordNode.asString();
                        if (StringUtils.hasText(password)
                                && !password.startsWith(CredentialCipher.PREFIX)) {
                            datasourceObject.put("password", credentialCipher.encrypt(password));
                        }
                    }
                }
            } catch (RuntimeException e) {
                // 仅记录节点下标与异常类型，禁止输出密码或定义内容
                log.error("DAG 节点密码加密失败: nodeIndex={}, errorType={}",
                        index, e.getClass().getSimpleName());
                throw new BusinessException(500, "DAG 节点密码加密失败，已拒绝保存");
            }
            index++;
        }
    }

    /**
     * 删除 DAG（物理删除；历史 schedule_run 不依赖该行）。
     *
     * @param code DAG 编码
     */
    @Transactional
    public void deleteDag(String code) {
        ScheduleDag dag = requireDag(code);
        dagMapper.deleteById(dag.getDagCode());
        log.info("删除调度 DAG: dagCode={}", dag.getDagCode());
    }

    /**
     * 查询并校验 DAG 存在。
     *
     * @param code DAG 编码
     * @return DAG 实体（包含明文定义，仅供服务内部使用）
     */
    private ScheduleDag requireDag(String code) {
        String normalized = ScheduleDagValidator.requireDagCode(code);
        ScheduleDag dag = dagMapper.selectById(normalized);
        if (dag == null) {
            throw new BusinessException(404, "DAG 不存在: " + normalized);
        }
        return dag;
    }

    /**
     * 转换为对外响应，密码统一替换为 {@code ***}。
     *
     * @param dag DAG 实体
     * @return 脱敏后的响应
     */
    private ScheduleDagView toView(ScheduleDag dag) {
        JsonNode definition = ScheduleDagValidator.maskPasswords(dag.getDefinition());
        return new ScheduleDagView(dag.getDagCode(), dag.getDagName(), definition,
                dag.getDescription(), dag.getStatus(), dag.getCreatedAt(), dag.getUpdatedAt());
    }

    /**
     * 校验 DAG 名称。
     *
     * @param raw 原始名称
     * @return 裁剪后的名称
     */
    private static String requireName(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(400, "DAG 名称不能为空");
        }
        String name = raw.trim();
        if (name.length() > ScheduleDagValidator.MAX_NAME_LENGTH) {
            throw new BusinessException(400,
                    "DAG 名称不能超过 " + ScheduleDagValidator.MAX_NAME_LENGTH + " 个字符");
        }
        return name;
    }

    /**
     * 校验可选描述，空白值归一为 null。
     *
     * @param raw 原始描述
     * @return 裁剪后的描述或 null
     */
    private static String optionalText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException(400, "DAG 描述不能超过 " + MAX_DESCRIPTION_LENGTH + " 个字符");
        }
        return value;
    }

    /**
     * 归一化并校验状态。
     *
     * @param raw          原始状态
     * @param defaultValue 缺省状态，null 表示必填
     * @return 大写状态
     */
    private static String normalizeStatus(String raw, String defaultValue) {
        if (!StringUtils.hasText(raw)) {
            if (defaultValue == null) {
                throw new BusinessException(400, "DAG 状态不能为空");
            }
            return defaultValue;
        }
        String status = raw.trim().toUpperCase(Locale.ROOT);
        if (!DAG_STATUSES.contains(status)) {
            throw new BusinessException(400, "DAG 状态仅支持 ENABLED/DISABLED: " + raw);
        }
        return status;
    }
}

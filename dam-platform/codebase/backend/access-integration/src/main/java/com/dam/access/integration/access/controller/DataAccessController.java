package com.dam.access.integration.access.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.dam.platform.common.exception.BusinessException;
import com.dam.platform.common.vo.Result;
import com.dam.access.integration.access.entity.DataAccessSource;
import com.dam.access.integration.access.model.DataSourceTypeDefinition;
import com.dam.access.integration.access.service.AccessCryptoService;
import com.dam.access.integration.access.service.DataAccessJdbcService;
import com.dam.access.integration.access.service.DataAccessSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据接入数据源接口
 * <p>
 * 数据源管理的统一写入口（原 catalog 数据源 CRUD/连接测试迁移至此）：
 * <ul>
 *   <li>列表/详情：脱敏，不返回密码；</li>
 *   <li>创建/更新/删除/连接测试：要求登录（Sa-Token），不要求 governor 角色；</li>
 *   <li>公钥/类型目录：供前端表单与连接表单使用。</li>
 * </ul>
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
public class DataAccessController {

    /** 数据源管理服务 */
    private final DataAccessSourceService sourceService;

    /** JDBC 类型目录服务 */
    private final DataAccessJdbcService jdbcService;

    /** 密码加解密服务（公钥下发） */
    private final AccessCryptoService cryptoService;

    /**
     * 查询数据源列表（脱敏）
     *
     * @param keyword 关键字（名称/编码/URL），可为空
     * @param dsType  类型，可为空
     * @param status  状态 0/1，可为空
     * @return 数据源列表
     */
    @GetMapping("/data-sources")
    public Result<List<DataAccessSource>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dsType,
            @RequestParam(required = false) Integer status) {
        return Result.success(sourceService.list(keyword, dsType, status));
    }

    /**
     * 查询数据源详情（脱敏）
     *
     * @param id 数据源 ID
     * @return 数据源详情
     */
    @GetMapping("/data-sources/{id}")
    public Result<DataAccessSource> detail(@PathVariable Long id) {
        return Result.success(sourceService.detail(id));
    }

    /**
     * 创建数据源
     *
     * @param source 数据源配置（password 为 RSA 密文，兼容历史明文）
     * @return 创建后的数据源（脱敏）
     */
    @PostMapping("/data-sources")
    public Result<DataAccessSource> create(@RequestBody DataAccessSource source) {
        requireLogin();
        return Result.success(sourceService.create(source));
    }

    /**
     * 更新数据源（兼容旧前端：ID 放在请求体）
     *
     * @param source 待更新字段
     * @return 更新后的数据源（脱敏）
     */
    @PutMapping("/data-sources")
    public Result<DataAccessSource> update(@RequestBody DataAccessSource source) {
        requireLogin();
        return Result.success(sourceService.update(null, source));
    }

    /**
     * 更新数据源（REST 风格：ID 在路径）
     *
     * @param id     数据源 ID
     * @param source 待更新字段
     * @return 更新后的数据源（脱敏）
     */
    @PutMapping("/data-sources/{id}")
    public Result<DataAccessSource> updateById(@PathVariable Long id,
                                               @RequestBody DataAccessSource source) {
        requireLogin();
        return Result.success(sourceService.update(id, source));
    }

    /**
     * 逻辑删除数据源
     *
     * @param id 数据源 ID
     * @return 空结果
     */
    @DeleteMapping("/data-sources/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireLogin();
        sourceService.delete(id);
        return Result.success();
    }

    /**
     * 测试数据源连接
     * <p>请求体含 id/dataSourceId 时按已存凭据测试；否则使用临时参数。
     * 密码只经 JDBC 传递，响应不含密码。
     *
     * @param params 连接参数
     * @return ok/databaseProduct/databaseVersion/driverName/elapsedMs
     */
    @PostMapping("/data-sources/test-connection")
    public Result<Map<String, Object>> testConnection(
            @RequestBody(required = false) Map<String, Object> params) {
        requireLogin();
        return Result.success(sourceService.testConnection(params));
    }

    /**
     * 获取前端密码加密使用的 RSA 公钥
     *
     * @return RSA 公钥（Base64 / X.509 编码）
     */
    @GetMapping("/public-key")
    public Result<String> publicKey() {
        return Result.success(cryptoService.getPublicKey());
    }

    /**
     * 获取内置数据源类型目录
     *
     * @return code/name/driver/urlTemplate/defaultPort 列表
     */
    @GetMapping("/data-source-types")
    public Result<List<Map<String, Object>>> dataSourceTypes() {
        List<Map<String, Object>> types = jdbcService.listTypes().stream()
                .map(DataSourceTypeDefinition::toMap)
                .toList();
        return Result.success(types);
    }

    /**
     * 校验当前请求已登录；NotLoginException 统一转换为 401 业务码
     */
    private static void requireLogin() {
        try {
            StpUtil.checkLogin();
        } catch (NotLoginException e) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
    }
}

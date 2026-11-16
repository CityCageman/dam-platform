package com.dam.develop.controller;

import com.dam.common.vo.PageResult;
import com.dam.common.vo.Result;
import com.dam.develop.dto.DevTaskCreateRequest;
import com.dam.develop.dto.DevTaskUpdateRequest;
import com.dam.develop.entity.DevTask;
import com.dam.develop.entity.DevTaskVersion;
import com.dam.develop.service.DevTaskService;
import jakarta.validation.Valid;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据开发任务管理接口
 * <p>
 * 提供 SQL/Python/Shell 任务定义的增删改查、版本查询与发布/下线。任务内容变更时
 * 自动生成新版本；所有响应统一使用 {@link Result} 包装。本服务不执行任务脚本，
 * 仅管理任务定义、版本与发布状态。
 *
 * @author DAM Platform
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevTaskController {

    /** 数据开发任务服务 */
    private final DevTaskService devTaskService;

    /**
     * 数据开发服务运行状态
     *
     * @return 服务名、运行状态与角色
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "dam-develop");
        status.put("status", "UP");
        status.put("role", "data development");
        // 明确能力边界：只管理任务定义/版本/发布状态，不执行真实脚本
        status.put("executionEngine", "not-implemented");
        return Result.success(status);
    }

    /**
     * 分页查询任务列表
     *
     * @param keyword  关键字，模糊匹配任务编码或任务名称
     * @param taskType 任务类型（SQL/PYTHON/SHELL）
     * @param status   发布状态（DRAFT/PUBLISHED/OFFLINE）
     * @param page     页码，从 1 开始，默认 1
     * @param pageSize 每页条数，默认 20，最大 200
     * @return 任务分页结果
     */
    @GetMapping("/tasks")
    public Result<PageResult<DevTask>> pageTasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(devTaskService.page(keyword, taskType, status, page, pageSize));
    }

    /**
     * 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务定义
     */
    @GetMapping("/tasks/{id}")
    public Result<DevTask> getTask(@PathVariable Long id) {
        return Result.success(devTaskService.getTask(id));
    }

    /**
     * 创建任务
     * <p>
     * taskCode/taskName/taskType/content 必填；创建后状态为 DRAFT、版本号为 1。
     *
     * @param request 创建请求
     * @return 创建后的任务
     */
    @PostMapping("/tasks")
    public Result<DevTask> createTask(@Valid @RequestBody DevTaskCreateRequest request) {
        return Result.success(devTaskService.create(request));
    }

    /**
     * 更新任务（taskCode 不可修改）
     * <p>
     * content 与当前内容不同时，版本号加 1 并写入版本记录。
     *
     * @param id      任务 ID
     * @param request 更新请求
     * @return 更新后的任务
     */
    @PutMapping("/tasks/{id}")
    public Result<DevTask> updateTask(@PathVariable Long id,
                                      @Valid @RequestBody DevTaskUpdateRequest request) {
        return Result.success(devTaskService.update(id, request));
    }

    /**
     * 逻辑删除任务
     *
     * @param id 任务 ID
     * @return 空结果
     */
    @DeleteMapping("/tasks/{id}")
    public Result<Void> deleteTask(@PathVariable Long id) {
        devTaskService.delete(id);
        return Result.success();
    }

    /**
     * 发布任务（DRAFT/OFFLINE → PUBLISHED）
     *
     * @param id 任务 ID
     * @return 发布后的任务
     */
    @PostMapping("/tasks/{id}/publish")
    public Result<DevTask> publishTask(@PathVariable Long id) {
        return Result.success(devTaskService.publish(id));
    }

    /**
     * 下线任务（PUBLISHED → OFFLINE）
     *
     * @param id 任务 ID
     * @return 下线后的任务
     */
    @PostMapping("/tasks/{id}/offline")
    public Result<DevTask> offlineTask(@PathVariable Long id) {
        return Result.success(devTaskService.offline(id));
    }

    /**
     * 查询任务的版本列表（最新版本在前）
     *
     * @param id 任务 ID
     * @return 版本列表
     */
    @GetMapping("/tasks/{id}/versions")
    public Result<List<DevTaskVersion>> listVersions(@PathVariable Long id) {
        return Result.success(devTaskService.listVersions(id));
    }

    /**
     * 查询任务指定版本详情
     *
     * @param id        任务 ID
     * @param versionNo 版本号
     * @return 版本快照
     */
    @GetMapping("/tasks/{id}/versions/{versionNo}")
    public Result<DevTaskVersion> getVersion(@PathVariable Long id,
                                             @PathVariable Integer versionNo) {
        return Result.success(devTaskService.getVersion(id, versionNo));
    }
}

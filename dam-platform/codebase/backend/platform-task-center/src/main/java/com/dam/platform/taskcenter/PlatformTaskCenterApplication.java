package com.dam.platform.taskcenter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 统一任务中心启动类。
 * <p>
 * 任务定义、任务实例与任务日志统一持久化到 PostgreSQL 的 {@code dam_task} schema。
 * 本服务只提供任务元数据、运行记录与日志的数据库支撑，不实现任务执行器：
 * 触发接口只创建 PENDING 实例，真实执行与状态回填由独立执行器负责。
 * 开启定时任务（@EnableScheduling）：TaskInstanceService 周期回收长期停留在
 * PENDING/RUNNING 的超时实例。
 * 复用 dam-common 的统一响应、异常处理、分页与 JSONB 类型处理器。
 *
 * @author DAM Platform
 */
@SpringBootApplication(scanBasePackages = {"com.dam.platform.taskcenter", "com.dam.common"})
@MapperScan("com.dam.platform.taskcenter.repository")
@EnableScheduling
public class PlatformTaskCenterApplication {

    /**
     * 服务入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PlatformTaskCenterApplication.class, args);
    }
}

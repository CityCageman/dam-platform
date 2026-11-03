# 本地一键启动

```bash
./scripts/dev-local.sh
```

首次运行会交互询问数据库信息，并生成 `~/.dam-local.env`（权限 600）。
之后直接运行脚本即可：自动 Maven 打包 → 启动 10 个后端服务 → 启动 Vite 前端 → 打开浏览器。

## 常用命令

```bash
./scripts/dev-local.sh              # 启动（首次会引导配置）
./scripts/dev-local.sh --no-build   # 跳过打包，快速重启
./scripts/dev-local.sh --migrate    # 启动前执行 v4/v5/v7 迁移（需 psql）
./scripts/dev-local.sh --migrate --yes
./scripts/dev-local.sh --no-open    # 不自动打开浏览器
./scripts/dev-local.sh status       # 查看端口/进程/网关
./scripts/dev-local.sh logs         # 查看日志尾部
./scripts/dev-local.sh stop         # 停止
./scripts/dev-local.sh restart      # 重启
./scripts/dev-local.sh --setup      # 重新生成环境变量
```

## 端口

| 服务 | 端口 |
|---|---|
| 前端 Vite | 3000 |
| 网关 | 8080 |
| auth | 8081 |
| catalog | 8092 |
| lineage | 8093 |
| standard | 8084 |
| quality | 8085 |
| security | 8086 |
| lifecycle | 8087 |
| operations | 8088 |
| agent | 8089 |

## 说明

- 仅管理本脚本启动的进程（PID 在 `/tmp/dam-local/pids`），不会动 systemd；
- 日志：`/tmp/dam-local/logs/`；
- 默认连接内网库，建议使用测试库/快照；
- 如果 8080 被占用，脚本会提示释放端口，不会自动改端口；
- 当前版本本地库需要先执行 `db/v4_lineage_layers.sql`，AI 任务中心需要 `v5`；
  可用 `--migrate` 自动执行 v4/v5/v7，v6 请按 `db/v6_lineage_identity_README.md` 单独处理；
- 环境变量文件不要提交 git。

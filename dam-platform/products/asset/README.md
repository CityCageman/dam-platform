# DAM 数据资产与主数据产品包

## 目标服务

```text
asset-catalog
asset-mdm
```

当前仍由旧 `dam-catalog` 过渡，拆出后使用新名称。

## 目录

```text
backend/
  asset-catalog/
  asset-mdm/
frontend/
  asset-ui/
db/
  bootstrap/
  migrations/
deploy/
  docker-compose.yml
  helm/
  install.sh
  uninstall.sh
tests/
  contract/
  smoke/
```

## 当前硬拆顺序

```text
1. 从 dam-catalog 拆出 report -> analytics-report
2. 从 dam-catalog 拆出 model -> processing-model
3. 从 dam-catalog 拆出 system -> platform-system
4. 剩余 asset/metadata -> asset-catalog
5. 新建 asset-mdm
6. 独立数据库 dam_dam
7. 独立前端 asset-ui
8. 独立部署包
```

## 命名

不再使用：

```text
dam-catalog
dam-mdm
dam-ui
```

目标：

```text
asset-catalog
asset-mdm
asset-ui
```

## 验收

```text
只安装 platform-base + product-asset 可启动
asset-catalog / asset-mdm 独立 jar
独立数据库迁移
asset-ui 独立构建
单独升级、回滚、卸载
不直接读写其他产品数据库
```

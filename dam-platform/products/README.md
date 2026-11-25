# 产品独立部署包

每个目录对应一个产品，`product.yaml` 是唯一清单。

## 构建

```bash
bash products/build-product.sh DAM 1.0.0
```

产物：

```text
products/asset/dist/1.0.0/          发布目录
products/asset/dist/DAM-1.0.0.tar.gz 可分发压缩包
```

包内只包含本产品服务 jar，不包含其他产品服务。

## 安装

```bash
tar -xzf DAM-1.0.0.tar.gz
sudo ./1.0.0/bin/install-product.sh /opt/dam-products/asset
```

安装后：

```text
/opt/dam-products/asset/current -> releases/1.0.0
/etc/dam-products/asset.env
```

## 数据库

```bash
psql "$SPRING_DATASOURCE_URL" -f current/db/migrate.sql
```

每个产品只迁移自己的 schema。

## 前提

产品包默认使用共享 `platform-base`：

```text
platform-gateway
platform-auth
platform-common
```

产品包会提供 `deploy/gateway-routes.yaml`，安装时合并到共享网关。

前端 remote、独立 product-ui 尚未打包进当前后端产品包。

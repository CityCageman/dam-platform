# platform-base

所有产品包默认依赖此底座：

```text
platform-gateway  8080
platform-auth     8081
```

安装：

```bash
bash products/build-product.sh BASE 1.0.0
```

产品包通过 `requiresPlatform` 声明依赖。

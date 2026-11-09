# 外部 JDBC 驱动目录

人大金仓、达梦等厂商驱动通常不在 Maven 中央仓库，也没有打进主包。把官方 JDBC 驱动 jar 放到本目录，重启 dam-catalog 即可。

## 人大金仓

文件示例：

```text
jdbc-drivers/kingbase8-9.0.0.jar
```

驱动类：

```text
com.kingbase8.Driver
```

JDBC URL：

```text
jdbc:kingbase8://主机:54321/数据库名
```

放入后重启 `dam-catalog`。可以用下面的命令确认 jar 里是否有驱动类：

```bash
unzip -l kingbase8-*.jar | grep com/kingbase8/Driver.class
```

如果目录不在应用工作目录下，设置环境变量：

```text
DAM_JDBC_DRIVERS_DIR=D:\dam\jdbc-drivers
```

或启动参数：

```text
-Ddam.jdbc.drivers-dir=D:\dam\jdbc-drivers
```

达梦驱动同理：`dm.jdbc.driver.DmDriver`，放到同一目录即可。

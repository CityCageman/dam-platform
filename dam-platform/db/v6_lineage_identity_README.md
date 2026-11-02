# v6 血缘节点 schema 化迁移说明

## 目标

为 `metadata_lineage_source` 增加统一节点键 `node_key`（`schema.table`），
为后续“同名表跨 Schema 不冲突”做准备。

## 对用户的影响

**设计为不改变现有功能**：

- 不修改 `source_code`、节点 ID、`metadata_lineage`、`metadata_lineage_hierarchy`、字段级血缘；
- 只新增 `node_key` 列并回填，旧应用不读该列，页面/图谱/检索行为不变；
- 不合并、不删除任何节点；
- 不建唯一索引，重复节点保留，避免误合并；
- 回填按 5000 条分批提交，索引 `CONCURRENTLY`，尽量减少锁；
- `ADD COLUMN` 有短暂排他锁，已设 `lock_timeout=5s`，拿不到锁会中止而不是阻塞业务。

**仍未生效的部分**：当前应用还没有读写 `node_key`，所以迁移本身不会改变图谱展示；
需要随后发布“双读/双写 node_key”的代码版本，才真正启用 schema 限定身份。

## 执行顺序

### 1. 备份

```bash
pg_dump -h <host> -p <port> -U dam -d damplatform \
  -t dam_metadata.metadata_lineage_source \
  -t dam_metadata.metadata_lineage_hierarchy \
  -t dam_metadata.metadata_lineage \
  -t dam_metadata.metadata_column_lineage \
  -Fc -f /backup/dam_lineage_before_v6.dump
```

### 2. 只读预检

```bash
psql -h <host> -p <port> -U dam -d damplatform \
  -v ON_ERROR_STOP=1 -f db/v6_lineage_identity_precheck.sql
```

确认：

- `metadata_lineage_source` 存在，且已有 `schema_name/datasource_id/table_id`（v4 已跑）；
- `matchable_nodes` 数量符合预期；
- `node_key` 预测重复列表里，是否只有“同名表跨 Schema”或历史重复；
- 同名表跨 Schema 列表是否可接受（这些表不会被猜测映射）。

### 3. 记录旧身份指纹

```bash
psql ... -f db/v6_lineage_identity_verify.sql
```

记下 `total_nodes` 和 `identity_checksum`。

### 4. 执行迁移

```bash
psql -h <host> -p <port> -U dam -d damplatform \
  -v ON_ERROR_STOP=1 -f db/v6_lineage_identity.sql
```

**不要**把脚本包在 `BEGIN/COMMIT` 里；`CALL` 内部需要分批提交，
`CREATE INDEX CONCURRENTLY` 也不能在事务块中执行。

### 5. 校验

再次执行 `v6_lineage_identity_verify.sql`：

- `identity_checksum` 与迁移前**完全一致** → 旧数据未被修改；
- `keyed_nodes = total_nodes`；
- `mapped_nodes` 合理；
- `v_lineage_node_dup` 中的重复项逐一确认。

### 6. 唯一索引（延后）

只有确认重复项已处理后才可执行：

```sql
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_lineage_source_node_key
    ON dam_metadata.metadata_lineage_source (node_key);
```

### 7. 回滚

```bash
psql ... -v ON_ERROR_STOP=1 -f db/v6_lineage_identity_rollback.sql
```

回滚只删视图/索引；`node_key` 列默认保留（旧应用不读，无副作用）。
如果新代码已使用 `node_key`，必须先回退代码再删列。

## 已知边界

- 同名表跨 Schema 的节点**不会**被自动映射，保持 `schema_name` 为空，`node_key` 退化为裸表名；
- 报表/非表节点不做表映射，`node_key = lower(source_code)`；
- 迁移不改变现有图谱节点 ID，因此不会导致已有血缘断链；
- 后续代码版本需要：
  1. `MetadataLineageSource` 增加 `nodeKey` 映射；
  2. upsert 时计算 `node_key`；
  3. 查询/检索优先按 `node_key`，找不到时回退 `source_code`；
  4. 报表 SQL 解析保留 schema 前缀。

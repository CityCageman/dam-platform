const { Client } = require('pg')

// 新 schema（init-all.sql 新建，需清空测试数据）
const NEW_SCHEMAS = ['public', 'dam_sys', 'dam_security', 'dam_ticket', 'dam_service', 'dam_sched', 'hospital_demo', 'dim', 'dwd']
// 共用 schema 中 init-all.sql 新增的表（需清空）
const EXTRA_TABLES = ['dam_metadata.collect_task', 'dam_metadata.collect_task_log', 'dam_standard.standard_category', 'dam_standard.data_standard', 'dam_standard.code_crosswalk', 'dam_asset.asset_statistics']
// 待删除的旧 schema（与 dam_security / dam_ticket 本质一致）
const DROP_SCHEMAS = ['dam_security_management', 'dam_ticket_center']

async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()

  // 1. 清空新 schema 所有表
  for (const s of NEW_SCHEMAS) {
    const r = await c.query(`SELECT table_name FROM information_schema.tables WHERE table_schema=$1 AND table_type='BASE TABLE'`, [s])
    const names = r.rows.map(x => x.table_name)
    if (names.length === 0) continue
    const list = names.map(n => `${s}."${n}"`).join(', ')
    try { await c.query('TRUNCATE ' + list + ' CASCADE'); console.log('[清空] ' + s + ' (' + names.length + ' 张表)') }
    catch (e) { console.log('[清空失败] ' + s + ': ' + e.message) }
  }

  // 2. 清空新增表
  for (const t of EXTRA_TABLES) {
    try { await c.query('TRUNCATE ' + t + ' CASCADE'); console.log('[清空] ' + t) }
    catch (e) { console.log('[清空失败] ' + t + ': ' + e.message) }
  }

  // 3. 删除旧 schema
  for (const s of DROP_SCHEMAS) {
    try { await c.query('DROP SCHEMA IF EXISTS ' + s + ' CASCADE'); console.log('[删除] DROP SCHEMA ' + s) }
    catch (e) { console.log('[删除失败] ' + s + ': ' + e.message) }
  }

  // 4. 验证
  const r = await c.query("SELECT count(*) AS n FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema NOT LIKE 'pg_%' AND table_schema <> 'information_schema'")
  console.log('\n[验证] 当前总表数 = ' + r.rows[0].n)
  const r2 = await c.query("SELECT table_schema, count(*) n FROM information_schema.tables WHERE table_type='BASE TABLE' AND table_schema NOT LIKE 'pg_%' AND table_schema <> 'information_schema' GROUP BY table_schema ORDER BY table_schema")
  for (const x of r2.rows) console.log('  ' + x.table_schema + ' | ' + x.n)
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })

const { Client } = require('pg')

async function main() {
  const client = new Client({
    host: '10.162.3.16', port: 11001, database: 'damplatform',
    user: 'dam', password: 'Cuhkszmc_2026', connectionTimeoutMillis: 15000
  })
  await client.connect()

  // 测试1：建 schema
  try {
    await client.query('CREATE SCHEMA IF NOT EXISTS dam_sys')
    console.log('[OK] CREATE SCHEMA dam_sys')
  } catch (e) { console.log('[FAIL] CREATE SCHEMA:', e.message) }

  // 测试2：建 public 无分布键的表
  try {
    await client.query(`CREATE TABLE IF NOT EXISTS data_source (
      id BIGSERIAL PRIMARY KEY, name VARCHAR(128) NOT NULL, type VARCHAR(32) NOT NULL)`)
    console.log('[OK] CREATE TABLE data_source (无 DISTRIBUTE BY)')
  } catch (e) { console.log('[FAIL] CREATE TABLE data_source:', e.message) }

  // 测试3：建 dam_sys 下的表
  try {
    await client.query(`CREATE TABLE IF NOT EXISTS dam_sys.sys_role (
      id BIGSERIAL PRIMARY KEY, role_code VARCHAR(64) NOT NULL)`)
    console.log('[OK] CREATE TABLE dam_sys.sys_role')
  } catch (e) { console.log('[FAIL] CREATE TABLE dam_sys.sys_role:', e.message) }

  // 测试4：查询 TBase 版本信息
  try {
    const v = await client.query('SELECT version() AS v')
    console.log('[版本]', v.rows[0].v)
  } catch (e) { console.log('[FAIL] version:', e.message) }

  // 测试5：DROP SCHEMA IF EXISTS dam（检查是否存在 dam schema）
  try {
    const r = await client.query(`SELECT nspname FROM pg_namespace WHERE nspname = 'dam'`)
    console.log('[INFO] 是否存在 dam schema:', r.rows.length > 0 ? '是' : '否（DROP 语句无影响）')
  } catch (e) { console.log('[FAIL] 检查 dam schema:', e.message) }

  await client.end()
}

main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })

const { Client } = require('pg')
async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()

  // 1. 查 dim/dwd 相关 schema 实际名（不区分大小写）
  const r = await c.query(`SELECT nspname FROM pg_namespace WHERE nspname ILIKE 'dim' OR nspname ILIKE 'dwd'`)
  console.log('dim/dwd 相关 schema: ' + r.rows.map(x => x.nspname).join(', '))

  // 2. 清空这些 schema 的表
  for (const x of r.rows) {
    const s = x.nspname
    const t = await c.query(`SELECT table_name FROM information_schema.tables WHERE table_schema=$1 AND table_type='BASE TABLE'`, [s])
    const names = t.rows.map(y => y.table_name)
    if (names.length === 0) { console.log('[跳过] ' + s + ' 无表'); continue }
    const list = names.map(n => `"${s}"."${n}"`).join(', ')
    try { await c.query('TRUNCATE ' + list + ' CASCADE'); console.log('[清空] ' + s + ' (' + names.length + ' 张表)') }
    catch (e) { console.log('[清空失败] ' + s + ': ' + e.message) }
  }

  // 3. 验证各 schema 是否有数据残留（抽查行数）
  console.log('\n=== 行数抽查（应为 0）===')
  const checks = ['public.data_source', 'public.sys_user', 'dam_sys.sys_user', 'dam_security.security_sensitive_rule', 'dam_ticket.ticket_order', 'hospital_demo.biz_patient_master']
  for (const t of checks) {
    try {
      const rr = await c.query('SELECT count(*) AS n FROM ' + t)
      console.log('  ' + t + ' = ' + rr.rows[0].n)
    } catch (e) { console.log('  ' + t + ' 查询失败: ' + e.message) }
  }
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })

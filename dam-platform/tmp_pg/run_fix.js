const { Client } = require('pg')
const fs = require('fs')

async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()

  const sql = fs.readFileSync('../db/fix-init-all.sql', 'utf8')
  // 按分号拆分（补丁内无 DO 块、无字符串内分号风险）
  const stmts = sql.split(';').map(s => s.trim()).filter(s => s && !s.startsWith('--'))
  console.log('执行修复补丁：共 ' + stmts.length + ' 条语句')
  for (const s of stmts) {
    try { await c.query(s); console.log('[OK] ' + s.slice(0, 70).replace(/\n/g, ' ')) }
    catch (e) { console.log('[FAIL] ' + s.slice(0, 70).replace(/\n/g, ' ') + ' -> ' + e.message) }
  }

  // 验证：PROD.CIS_Model schema 注释是否已补上
  const r = await c.query(`SELECT obj_description('"PROD.CIS_Model"'::regnamespace) AS comment`)
  console.log('\n[验证] PROD.CIS_Model 注释: ' + (r.rows[0].comment || '(空)'))
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })

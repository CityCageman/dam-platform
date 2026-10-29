const { Client } = require('pg')
const fs = require('fs')

// 简单拆分：按分号（该 SQL 文件无 DO 块、注释里无分号）
function split(sql) {
  const out = []
  let cur = ''
  let inStr = false
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i]
    if (ch === "'" && !inStr) { inStr = true; cur += ch; continue }
    if (ch === "'" && inStr) { inStr = false; cur += ch; continue }
    if (ch === ';' && !inStr) { const s = cur.trim(); if (s) out.push(s); cur = ''; continue }
    cur += ch
  }
  const tail = cur.trim(); if (tail) out.push(tail)
  return out
}

async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()
  const sql = fs.readFileSync('../db/create_dam_data_source_type.sql', 'utf8')
  const stmts = split(sql)
  console.log('共 ' + stmts.length + ' 条语句')
  for (const s of stmts) {
    try { await c.query(s); console.log('[OK] ' + s.slice(0, 60).replace(/\n/g, ' ')) }
    catch (e) { console.log('[FAIL] ' + s.slice(0, 60).replace(/\n/g, ' ') + ' -> ' + e.message) }
  }
  // 验证
  const r = await c.query(`SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_schema='dam_metadata' AND table_name='dam_data_source_type' ORDER BY ordinal_position`)
  console.log('\n[验证] 表结构（' + r.rows.length + ' 列）:')
  for (const x of r.rows) console.log('  ' + x.column_name + ' | ' + x.data_type + ' | null=' + x.is_nullable)
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })

const { Client } = require('pg')
const fs = require('fs')

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
  const sql = fs.readFileSync('../db/init_dam_data_source_type.sql', 'utf8')
  const stmts = split(sql)
  console.log('共 ' + stmts.length + ' 条语句')
  for (const s of stmts) {
    try { await c.query(s); console.log('[OK] ' + s.slice(0, 50).replace(/\n/g, ' ')) }
    catch (e) { console.log('[FAIL] ' + s.slice(0, 50).replace(/\n/g, ' ') + ' -> ' + e.message) }
  }
  // 验证
  const r = await c.query(`SELECT id, type_key, type_name, default_port, catalog_mode, pk_strategy FROM dam_metadata.dam_data_source_type ORDER BY sort_order`)
  console.log('\n[验证] 共 ' + r.rows.length + ' 条:')
  for (const x of r.rows) console.log('  ' + x.type_key + ' | ' + x.type_name + ' | 端口' + x.default_port + ' | ' + x.catalog_mode + ' | ' + x.pk_strategy)
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })

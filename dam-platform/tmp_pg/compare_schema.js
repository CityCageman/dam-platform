const { Client } = require('pg')

const PAIRS = [
  ['dam_security_management', 'dam_security'],
  ['dam_ticket_center', 'dam_ticket'],
  ['dam_system', 'dam_sys']
]

async function main() {
  const c = new Client({ host: '10.162.3.16', port: 11001, database: 'damplatform', user: 'dam', password: 'Cuhkszmc_2026' })
  await c.connect()

  for (const [oldS, newS] of PAIRS) {
    console.log('\n===== 对比 ' + oldS + ' vs ' + newS + ' =====')
    // 表清单
    const tables = async (s) => {
      const r = await c.query(`SELECT table_name FROM information_schema.tables WHERE table_schema=$1 AND table_type='BASE TABLE' ORDER BY table_name`, [s])
      return r.rows.map(x => x.table_name)
    }
    const oldT = await tables(oldS)
    const newT = await tables(newS)
    const oldSet = new Set(oldT), newSet = new Set(newT)
    const onlyOld = oldT.filter(t => !newSet.has(t))
    const onlyNew = newT.filter(t => !oldSet.has(t))
    const common = oldT.filter(t => newSet.has(t))
    console.log('旧表数=' + oldT.length + ' 新表数=' + newT.length)
    if (onlyOld.length) console.log('  仅旧有: ' + onlyOld.join(', '))
    if (onlyNew.length) console.log('  仅新有: ' + onlyNew.join(', '))

    // 列结构对比（共同表）
    for (const t of common) {
      const cols = async (s) => {
        const r = await c.query(`SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_schema=$1 AND table_name=$2 ORDER BY ordinal_position`, [s, t])
        return r.rows.map(x => x.column_name + ':' + x.data_type)
      }
      const oc = await cols(oldS), nc = await cols(newS)
      if (JSON.stringify(oc) !== JSON.stringify(nc)) {
        console.log('  [列差异] ' + t)
        console.log('    旧: ' + oc.join(', '))
        console.log('    新: ' + nc.join(', '))
      }
    }
    console.log('  ' + (onlyOld.length === 0 && onlyNew.length === 0 ? '表清单完全一致' : '表清单有差异'))
  }
  await c.end()
}
main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })

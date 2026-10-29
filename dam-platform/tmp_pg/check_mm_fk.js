const { Client } = require('pg')

;(async () => {
  const c = new Client({
    host: '10.162.3.16',
    port: 11001,
    database: 'damplatform',
    user: 'dam',
    password: 'Cuhkszmc_2026',
    connectionTimeoutMillis: 15000
  })
  await c.connect()

  const fk = await c.query(
    "SELECT conname, pg_get_constraintdef(oid) AS def FROM pg_constraint WHERE conrelid = 'dam_metadata.metadata_management'::regclass AND contype = 'f'"
  )
  console.log('FK', JSON.stringify(fk.rows))

  const src = await c.query('SELECT id, system_code FROM dam_metadata.source_system ORDER BY id LIMIT 3')
  console.log('SOURCE_SYSTEM', JSON.stringify(src.rows))

  await c.end()
})().catch(e => { console.error('ERR', e.message); process.exit(1) })

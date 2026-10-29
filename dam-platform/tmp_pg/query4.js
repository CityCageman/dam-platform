const { Client } = require('pg')

async function main() {
  const client = new Client({
    host: '10.162.3.16', port: 11001, database: 'damplatform',
    user: 'dam', password: 'Cuhkszmc_2026', connectionTimeoutMillis: 15000
  })
  await client.connect()
  const r = await client.query(
    `SELECT password FROM dam_metadata.metadata_datasource WHERE id = 1`)
  console.log(r.rows[0].password)
  await client.end()
}

main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })

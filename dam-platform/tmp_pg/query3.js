const { Client } = require('pg')

async function main() {
  const client = new Client({
    host: '10.162.3.16', port: 11001, database: 'damplatform',
    user: 'dam', password: 'Cuhkszmc_2026', connectionTimeoutMillis: 15000
  })
  await client.connect()
  const r = await client.query(
    `SELECT id, ds_name, ds_type, jdbc_url, username, password, connection_props
     FROM dam_metadata.metadata_datasource WHERE id = 1`)
  for (const row of r.rows) {
    console.log('id=', row.id)
    console.log('ds_name=', row.ds_name)
    console.log('ds_type=', row.ds_type)
    console.log('jdbc_url=', row.jdbc_url)
    console.log('username=', row.username)
    console.log('password=', row.password ? row.password.substring(0, 30) + '...' : '(空)')
    console.log('connection_props=', row.connection_props)
  }
  await client.end()
}

main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })

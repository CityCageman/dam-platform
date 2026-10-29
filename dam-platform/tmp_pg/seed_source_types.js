const { Client } = require('pg')

const CONFIG_TYPE = 'source_type'

const types = [
  {
    code: 'POSTGRESQL', name: 'PostgreSQL', sort: 1, desc: 'PostgreSQL 数据库',
    value: '{"driverClass":"org.postgresql.Driver","urlTemplate":"jdbc:postgresql://%s:%d/%s","defaultPort":5432,"catalogMode":"database","pkStrategy":"pg_catalog","systemSchemas":"information_schema,pg_catalog"}'
  },
  {
    code: 'MYSQL', name: 'MySQL', sort: 2, desc: 'MySQL 数据库',
    value: '{"driverClass":"com.mysql.cj.jdbc.Driver","urlTemplate":"jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai","defaultPort":3306,"catalogMode":"database","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"information_schema,mysql,performance_schema,sys"}'
  },
  {
    code: 'ORACLE', name: 'Oracle', sort: 3, desc: 'Oracle 数据库',
    value: '{"driverClass":"oracle.jdbc.OracleDriver","urlTemplate":"jdbc:oracle:thin:@//%s:%d/%s","defaultPort":1521,"catalogMode":"schema","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"SYS,SYSTEM"}'
  },
  {
    code: 'SQLSERVER', name: 'SQL Server', sort: 4, desc: 'SQL Server 数据库',
    value: '{"driverClass":"com.microsoft.sqlserver.jdbc.SQLServerDriver","urlTemplate":"jdbc:sqlserver://%s:%d;databaseName=%s","defaultPort":1433,"catalogMode":"database","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"sys"}'
  },
  {
    code: 'IRIS', name: 'IRIS', sort: 5, desc: 'InterSystems IRIS 数据库',
    value: '{"driverClass":"com.intersystems.jdbc.IRISDriver","urlTemplate":"jdbc:IRIS://%s:%d/%s","defaultPort":1972,"catalogMode":"schema","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"IRISSYS"}'
  },
  {
    code: 'DM8', name: '达梦', sort: 6, desc: '达梦数据库',
    value: '{"driverClass":"dm.jdbc.driver.DmDriver","urlTemplate":"jdbc:dm://%s:%d/%s","defaultPort":5236,"catalogMode":"schema","pkStrategy":"jdbc_get_primary_keys","systemSchemas":"SYSAUDITOR,SYSDBA"}'
  },
  {
    code: 'KINGBASE', name: '人大金仓', sort: 7, desc: '人大金仓数据库',
    value: '{"driverClass":"com.kingbase8.Driver","urlTemplate":"jdbc:kingbase8://%s:%d/%s","defaultPort":54321,"catalogMode":"database","pkStrategy":"pg_catalog","systemSchemas":"information_schema,pg_catalog"}'
  }
]

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
  try {
    await c.query('BEGIN')
    for (const t of types) {
      await c.query(
        `INSERT INTO dam_sys.sys_config (config_type, config_code, config_name, config_value, description, sort_order, status)
         SELECT $1, $2, $3, $4, $5, $6, 1
         WHERE NOT EXISTS (
           SELECT 1 FROM dam_sys.sys_config WHERE config_type = $1 AND config_code = $2
         )`,
        [CONFIG_TYPE, t.code, t.name, t.value, t.desc, t.sort]
      )
    }
    await c.query('COMMIT')
  } catch (e) {
    await c.query('ROLLBACK')
    throw e
  }

  const res = await c.query(
    "SELECT config_code, config_name, config_value, sort_order, status FROM dam_sys.sys_config WHERE config_type = 'source_type' ORDER BY sort_order"
  )
  console.log('SOURCE_TYPE_COUNT', res.rows.length)
  for (const r of res.rows) {
    console.log(r.config_code, '|', r.config_name, '|', 'status=' + r.status, '|', String(r.config_value).slice(0, 40) + '...')
  }
  await c.end()
})().catch(e => { console.error('ERR', e.message); process.exit(1) })

const { Client } = require('pg')

const users = [
  { username: 'admin',   password: '$2a$10$8DBgEYtarH9NsXmQ7olXbuYoOhszYQmeyPlmskJXxtJb/TZ77fLYG', realName: '系统管理员', email: 'admin@dam.local',   phone: '13800000000', department: '信息中心',       role: 'governor' },
  { username: 'steward', password: '$2a$10$9Kv27Z/WJOmDuUHdoItke.b6izEkMeNA/iNkPC47qae5nZJmvaYK.', realName: '数据管家',   email: 'steward@dam.local', phone: '13800000001', department: '数据治理办公室', role: 'steward' },
  { username: 'analyst', password: '$2a$10$lAB1aGc6NM.CNYY4mb0koO.kM9.X9hG7gbsPOpxnQgY3//YjiXMz.', realName: '数据分析师', email: 'analyst@dam.local', phone: '13800000002', department: '数据分析中心',   role: 'analyst' }
]

const roles = [
  ['governor', '数据管理员', '元数据维护、标准制定、质量规则配置'],
  ['developer', '数据开发者', '资产注册、血缘梳理、工单处理'],
  ['business', '业务用户', '资产浏览、检索申请、数据使用'],
  ['security', '安全管理员', '分级管控、审计日志、合规检查'],
  ['management', '管理层', '运营看板、资产价值评估、治理成效报告'],
  ['steward', '数据管家', '数据质量日常监控、标准执行与资产治理'],
  ['analyst', '数据分析师', '资产检索、数据查看与分析使用']
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

    for (const [code, name, desc] of roles) {
      await c.query(
        `INSERT INTO dam_sys.sys_role (role_code, role_name, description, status)
         SELECT $1, $2, $3, 1
         WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_role WHERE role_code = $1)`,
        [code, name, desc]
      )
    }

    for (const u of users) {
      await c.query(
        `INSERT INTO dam_sys.sys_user (username, password, password_hash, salt, real_name, email, phone, department, last_login_at, last_login_ip, status)
         SELECT $1, $2, $2, '', $3, $4, $5, $6, CURRENT_TIMESTAMP, '127.0.0.1', 1
         WHERE NOT EXISTS (SELECT 1 FROM dam_sys.sys_user WHERE username = $1)`,
        [u.username, u.password, u.realName, u.email, u.phone, u.department]
      )
      await c.query(
        `INSERT INTO dam_sys.sys_user_role (user_id, role_id, default_role, status)
         SELECT u.id, r.id, 'Y', 1
         FROM dam_sys.sys_user u, dam_sys.sys_role r
         WHERE u.username = $1 AND r.role_code = $2
           AND NOT EXISTS (SELECT 1 FROM dam_sys.sys_user_role x WHERE x.user_id = u.id AND x.role_id = r.id)`,
        [u.username, u.role]
      )
    }

    await c.query('COMMIT')
  } catch (e) {
    await c.query('ROLLBACK')
    throw e
  }

  const res = await c.query(
    `SELECT u.id, u.username, u.real_name, u.status, r.role_code
     FROM dam_sys.sys_user u
     LEFT JOIN dam_sys.sys_user_role ur ON ur.user_id = u.id
     LEFT JOIN dam_sys.sys_role r ON r.id = ur.role_id
     ORDER BY u.id`
  )
  console.log('USERS', JSON.stringify(res.rows, null, 2))
  const rc = await c.query('SELECT count(*) AS n FROM dam_sys.sys_role')
  console.log('ROLE_COUNT', rc.rows[0].n)
  const uc = await c.query('SELECT count(*) AS n FROM dam_sys.sys_user')
  console.log('USER_COUNT', uc.rows[0].n)
  await c.end()
})().catch(e => { console.error('ERR', e.message); process.exit(1) })

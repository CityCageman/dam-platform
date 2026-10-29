const crypto = require('crypto')
const net = require('net')
const base = 'http://localhost:8080'

function toPem(b64) {
  return '-----BEGIN PUBLIC KEY-----\n' + String(b64).match(/.{1,64}/g).join('\n') + '\n-----END PUBLIC KEY-----'
}
async function waitPort(p, tries = 25) {
  for (let i = 0; i < tries; i++) {
    const ok = await new Promise(r => {
      const s = net.connect(p, '127.0.0.1')
      s.on('connect', () => { s.destroy(); r(true) })
      s.on('error', () => r(false))
    })
    if (ok) return true
    await new Promise(r => setTimeout(r, 1000))
  }
  return false
}

;(async () => {
  console.log('PORT_READY', await waitPort(8092))
  const pk = await (await fetch(base + '/api/auth/public-key')).json()
  const enc = crypto.publicEncrypt(
    { key: toPem(pk.data), padding: crypto.constants.RSA_PKCS1_PADDING },
    Buffer.from('admin123')
  ).toString('base64')
  const login = await (await fetch(base + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: enc })
  })).json()
  const token = login.data && login.data.token
  console.log('LOGIN_OK', !!token)

  const list = await (await fetch(base + '/api/catalog/data-sources', { headers: { token } })).json()
  const ds = (list.data || []).find(d => String(d.id) === '7') || (list.data || [])[0]
  console.log('DS', ds && ds.id, ds && ds.dsName)

  const r = await (await fetch(base + '/api/catalog/metadata/ai-annotate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', token },
    body: JSON.stringify({ dataSourceId: ds ? ds.id : null })
  })).json()
  console.log('AI_CODE', r.code, 'MSG', r.message)
  console.log('AI_DATA', JSON.stringify(r.data))
})().catch(e => { console.error('ERR', e.message); process.exit(1) })

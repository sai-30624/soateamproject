// End-to-end smoke test for PayVault. Runs the whole demo script against the API Gateway
// and checks every expected output. Needs Node 18+ (built-in fetch), no npm packages.
//
//   node scripts/smoke-test.mjs                    # gateway on http://localhost:8080
//   GATEWAY=http://localhost:3000 node scripts/smoke-test.mjs   # through the Docker UI's nginx
//
// Exit code 0 = every check passed.

const BASE = process.env.GATEWAY || 'http://localhost:8080'
const run = Date.now().toString().slice(-6)
let passed = 0
let failed = 0

async function call(method, path, { token, body, key } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (key) headers['Idempotency-Key'] = key
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const text = await res.text()
  let data
  try { data = JSON.parse(text) } catch { data = text }
  return { status: res.status, data }
}

function check(name, ok, detail = '') {
  if (ok) { passed++; console.log(`  PASS  ${name}`) }
  else { failed++; console.log(`  FAIL  ${name} ${detail}`) }
}

const money = (v) => Number(v).toFixed(2)

async function waitForGateway() {
  // Eureka needs a few seconds after start-up before the Gateway can route to every service.
  for (let i = 0; i < 60; i++) {
    try {
      const r = await call('GET', '/api/transactions/circuit')
      const w = await call('GET', '/api/auth/login') // reaches auth-service only when routing works (405)
      if (r.status === 401 && w.status === 405) return
    } catch { /* not up yet */ }
    await new Promise((r) => setTimeout(r, 2000))
  }
  throw new Error(`Gateway at ${BASE} is not routing to auth-service - are all services registered in Eureka?`)
}

async function main() {
  console.log(`PayVault smoke test against ${BASE} (run ${run})\n`)
  await waitForGateway()

  console.log('1. Register two users')
  const a = { fullName: 'Asha Rao', email: `asha${run}@demo.in`, phone: `90000${run.slice(-5)}`, password: 'secret123' }
  const b = { fullName: 'Vikram Shah', email: `vikram${run}@demo.in`, phone: `80000${run.slice(-5)}`, password: 'secret123' }
  const ra = await call('POST', '/api/auth/register', { body: a })
  const rb = await call('POST', '/api/auth/register', { body: b })
  check('register A -> 201 with accessToken', ra.status === 201 && !!ra.data.accessToken, JSON.stringify(ra.data))
  check('register B -> 201 with accessToken', rb.status === 201 && !!rb.data.accessToken, JSON.stringify(rb.data))
  const dup = await call('POST', '/api/auth/register', { body: a })
  check('duplicate email -> 409', dup.status === 409, JSON.stringify(dup.data))
  const bad = await call('POST', '/api/auth/register', { body: { ...a, email: 'x', phone: '12' } })
  check('invalid input -> 400', bad.status === 400, JSON.stringify(bad.data))

  console.log('2. Login')
  const la = await call('POST', '/api/auth/login', { body: { email: a.email, password: a.password } })
  check('login -> 200 with role USER', la.status === 200 && la.data.role === 'USER', JSON.stringify(la.data))
  const wrong = await call('POST', '/api/auth/login', { body: { email: a.email, password: 'nope123' } })
  check('wrong password -> 401', wrong.status === 401)
  const ta = la.data.accessToken
  const tb = rb.data.accessToken
  const ref = await call('POST', '/api/auth/refresh', { body: { refreshToken: la.data.refreshToken } })
  check('refresh token -> new access token', ref.status === 200 && !!ref.data.accessToken)

  console.log('3. Profile and wallet')
  const me = await call('GET', '/api/users/me', { token: ta })
  check('GET /users/me -> own profile', me.status === 200 && me.data.email === a.email, JSON.stringify(me.data))
  const w0 = await call('GET', '/api/wallets/me', { token: ta })
  check('new wallet balance is 0.00', w0.status === 200 && money(w0.data.balance) === '0.00', JSON.stringify(w0.data))

  console.log('4. Deposit 500 to A')
  const dep = await call('POST', '/api/transactions/deposit', { token: ta, key: `dep-${run}`, body: { amount: 500 } })
  check('deposit -> 200 SUCCESS', dep.status === 200 && dep.data.status === 'SUCCESS', JSON.stringify(dep.data))

  console.log('5. Look up receiver and transfer 150 A -> B')
  const look = await call('GET', `/api/users/lookup?query=${b.phone}`, { token: ta })
  check('lookup by phone -> masked phone', look.status === 200 && look.data.maskedPhone === '******' + b.phone.slice(-4), JSON.stringify(look.data))
  const tKey = `tr-${run}`
  const tr = await call('POST', '/api/transactions/transfer', { token: ta, key: tKey, body: { receiverId: look.data.id, amount: 150, note: 'lunch' } })
  check('transfer -> 200 SUCCESS', tr.status === 200 && tr.data.status === 'SUCCESS', JSON.stringify(tr.data))
  const wa = await call('GET', '/api/wallets/me', { token: ta })
  const wb = await call('GET', '/api/wallets/me', { token: tb })
  check('A balance 500.00 -> 350.00', money(wa.data.balance) === '350.00', JSON.stringify(wa.data))
  check('B balance 0.00 -> 150.00', money(wb.data.balance) === '150.00', JSON.stringify(wb.data))

  console.log('6. Ledger and reconciliation')
  const lg = await call('GET', '/api/wallets/me/ledger', { token: ta })
  const top = lg.data.content?.[0] || {}
  check('A ledger: DEBIT 150.00 before 500.00 after 350.00',
    top.entryType === 'DEBIT' && money(top.amount) === '150.00' && money(top.balanceBefore) === '500.00' && money(top.balanceAfter) === '350.00',
    JSON.stringify(top))
  const lgb = await call('GET', '/api/wallets/me/ledger', { token: tb })
  const topB = lgb.data.content?.[0] || {}
  check('B ledger: CREDIT 150.00 with the same txnRef', topB.entryType === 'CREDIT' && topB.txnRef === tr.data.txnRef, JSON.stringify(topB))
  const rc = await call('GET', '/api/wallets/me/reconcile', { token: ta })
  check('A reconcile: consistent, 2 entries, net 350.00',
    rc.data.consistent === true && rc.data.entries === 2 && money(rc.data.ledgerNet) === '350.00', JSON.stringify(rc.data))

  console.log('7. Idempotency')
  const again = await call('POST', '/api/transactions/transfer', { token: ta, key: tKey, body: { receiverId: look.data.id, amount: 150 } })
  check('same Idempotency-Key -> same txnRef', again.data.txnRef === tr.data.txnRef, JSON.stringify(again.data))
  const wa2 = await call('GET', '/api/wallets/me', { token: ta })
  check('money moved only once (A still 350.00)', money(wa2.data.balance) === '350.00')
  const noKey = await call('POST', '/api/transactions/transfer', { token: ta, body: { receiverId: look.data.id, amount: 1 } })
  check('missing Idempotency-Key -> 400', noKey.status === 400)

  console.log('8. Business rules')
  const big = await call('POST', '/api/transactions/transfer', { token: ta, key: `big-${run}`, body: { receiverId: look.data.id, amount: 9999 } })
  check('insufficient balance -> 422 FAILED', big.status === 422 && big.data.status === 'FAILED' && /Insufficient/.test(big.data.failureReason), JSON.stringify(big.data))
  const wa3 = await call('GET', '/api/wallets/me', { token: ta })
  check('nothing moved after failure (A still 350.00)', money(wa3.data.balance) === '350.00')
  const self = await call('POST', '/api/transactions/transfer', { token: ta, key: `self-${run}`, body: { receiverId: me.data.id, amount: 1 } })
  check('transfer to yourself -> 400', self.status === 400)
  const hist = await call('GET', '/api/transactions?size=10', { token: ta })
  check('history shows 3 transactions (deposit, transfer, failed)', hist.data.totalElements === 3, JSON.stringify(hist.data.totalElements))
  const one = await call('GET', `/api/transactions/${tr.data.txnRef}`, { token: tb })
  check('receiver sees transfer as RECEIVED', one.data.direction === 'RECEIVED', JSON.stringify(one.data))

  console.log('9. Security')
  const anon = await call('GET', '/api/wallets/me')
  check('no token -> 401', anon.status === 401)
  const forged = await call('GET', '/api/wallets/me', { token: 'abc.def.ghi' })
  check('forged token -> 401', forged.status === 401)
  const internal = await call('GET', `/internal/wallets/${me.data.id}`)
  check('/internal/** through Gateway -> 403', internal.status === 403)
  const admin = await call('PUT', `/api/users/${me.data.id}/status?value=BLOCKED`, { token: ta })
  check('non-admin block user -> 403', admin.status === 403)

  console.log('10. Circuit breaker')
  const cb = await call('GET', '/api/transactions/circuit', { token: ta })
  check('circuit state is CLOSED (Wallet healthy)', cb.data.state === 'CLOSED', JSON.stringify(cb.data))

  console.log(`\n${passed} passed, ${failed} failed`)
  process.exit(failed ? 1 : 0)
}

main().catch((e) => { console.error(e.message); process.exit(2) })

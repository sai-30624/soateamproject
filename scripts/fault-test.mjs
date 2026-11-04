// Fault-tolerance test: Wallet Service goes down in the middle of transfers and comes back.
// Checks retry + circuit breaker + PENDING (202) + RecoveryJob finishing the transfer, with no money lost.
//
// It needs a way to stop and start wallet-service. Give it as shell commands:
//   Docker:      STOP_CMD="docker compose stop wallet-service"  START_CMD="docker compose start wallet-service"  node scripts/fault-test.mjs
//   Git Bash:    STOP_CMD='kill $(sed -n 4p logs/pids.txt)' \
//                START_CMD='nohup java -jar wallet-service/target/wallet-service.jar --spring.profiles.active=h2 > logs/wallet-service.log 2>&1 & sed -i "4s/.*/$!/" logs/pids.txt' \
//                node scripts/fault-test.mjs
// Without them, the script pauses and asks you to stop / start wallet-service yourself.
//
// Takes about 1-2 minutes (RecoveryJob looks at transactions that have been stuck for 30 s, every 15 s).

import { execSync } from 'node:child_process'
import readline from 'node:readline/promises'

const BASE = process.env.GATEWAY || 'http://localhost:8080'
const run = Date.now().toString().slice(-5)
let passed = 0
let failed = 0
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const money = (v) => Number(v).toFixed(2)

async function call(method, path, { token, body, key } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (key) headers['Idempotency-Key'] = key
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const text = await res.text()
  try { return { status: res.status, data: JSON.parse(text) } } catch { return { status: res.status, data: text } }
}

function check(name, ok, detail = '') {
  if (ok) { passed++; console.log(`  PASS  ${name}`) } else { failed++; console.log(`  FAIL  ${name} ${detail}`) }
}

async function step(envName, prompt) {
  const cmd = process.env[envName]
  if (cmd) {
    console.log(`  $ ${cmd}`)
    execSync(cmd, { stdio: 'inherit', shell: process.env.SHELL || true })
  } else {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
    await rl.question(`  >> ${prompt}, then press Enter `)
    rl.close()
  }
}

async function main() {
  console.log(`PayVault fault-tolerance test against ${BASE}\n`)
  console.log('1. Set up two users, A has 500.00')
  const reg = async (n, p) => (await call('POST', '/api/auth/register', {
    body: { fullName: n, email: `${n.toLowerCase().replace(' ', '')}${run}@demo.in`, phone: `${p}${run}`, password: 'secret123' } })).data
  const a = await reg('Fault A', '61111')
  const b = await reg('Fault B', '62222')
  const dep = await call('POST', '/api/transactions/deposit', { token: a.accessToken, key: `fd-${run}`, body: { amount: 500 } })
  check('deposit 500 -> SUCCESS', dep.data.status === 'SUCCESS', JSON.stringify(dep.data))

  console.log('2. Wallet Service goes down')
  await step('STOP_CMD', 'Stop wallet-service now')
  await sleep(3000)
  const t0 = Date.now()
  const tr = await call('POST', '/api/transactions/transfer', { token: a.accessToken, key: `ft-${run}`, body: { receiverId: b.userId, amount: 150 } })
  check('transfer while Wallet is down -> 202 PENDING (retried, then parked)', tr.status === 202 && tr.data.status === 'PENDING', JSON.stringify(tr.data))
  console.log(`        (took ${Date.now() - t0} ms: Feign timeout + 3 Resilience4j attempts)`)
  for (let i = 0; i < 4; i++) {
    await call('POST', '/api/transactions/transfer', { token: a.accessToken, key: `fx-${run}-${i}`, body: { receiverId: b.userId, amount: 1 } })
  }
  const cb = await call('GET', '/api/transactions/circuit', { token: a.accessToken })
  check('circuit breaker -> OPEN after repeated failures', cb.data.state === 'OPEN', JSON.stringify(cb.data))
  const t1 = Date.now()
  const fast = await call('POST', '/api/transactions/transfer', { token: a.accessToken, key: `ff-${run}`, body: { receiverId: b.userId, amount: 1 } })
  const ms = Date.now() - t1
  check(`open circuit fails fast (${ms} ms) and still parks the transfer as PENDING`, fast.status === 202 && ms < 1500, JSON.stringify(fast.data))

  console.log('3. Wallet Service comes back; RecoveryJob should finish every PENDING transfer')
  await step('START_CMD', 'Start wallet-service again')
  let status = ''
  for (let i = 0; i < 36 && status !== 'SUCCESS'; i++) {
    await sleep(5000)
    const t = await call('GET', `/api/transactions/${tr.data.txnRef}`, { token: a.accessToken })
    status = t.data.status
    process.stdout.write(`        t+${(i + 1) * 5}s ${tr.data.txnRef} ${status}\n`)
  }
  check('stuck 150.00 transfer recovered -> SUCCESS', status === 'SUCCESS')
  await sleep(20000) // let the job finish the small ones too
  const hist = await call('GET', '/api/transactions?size=20', { token: a.accessToken })
  const transfers = hist.data.content.filter((t) => t.type === 'TRANSFER')
  check('all 6 transfers ended SUCCESS', transfers.length === 6 && transfers.every((t) => t.status === 'SUCCESS'),
    transfers.map((t) => `${t.amount}:${t.status}`).join(' '))
  const wa = await call('GET', '/api/wallets/me', { token: a.accessToken })
  const wb = await call('GET', '/api/wallets/me', { token: b.accessToken })
  check('A = 500 - 150 - 5x1 = 345.00 (no money lost or moved twice)', money(wa.data.balance) === '345.00', JSON.stringify(wa.data))
  check('B = 155.00', money(wb.data.balance) === '155.00', JSON.stringify(wb.data))
  const rc = await call('GET', '/api/wallets/me/reconcile', { token: a.accessToken })
  check('A ledger reconciles (balance = credits - debits)', rc.data.consistent === true, JSON.stringify(rc.data))

  console.log(`\n${passed} passed, ${failed} failed`)
  process.exit(failed ? 1 : 0)
}

main().catch((e) => { console.error(e.message); process.exit(2) })

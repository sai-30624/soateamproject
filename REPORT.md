# PayVault — Project Report

**Fault-Tolerant Digital Wallet & Real-Time Financial Ledger**
Spring Boot 3.3 · Spring Cloud 2023 (Eureka, Gateway, OpenFeign) · Resilience4j · JPA · MySQL / H2 · JWT · React 18

| | |
|---|---|
| Build | `mvnw clean package` → **BUILD SUCCESS** (6 modules), `npm run build` → **OK** |
| End-to-end smoke test | `scripts/smoke-test.mjs` → **30 / 30 passed** |
| Fault-tolerance test | `scripts/fault-test.mjs` → **9 / 9 passed** |
| React UI | Register → Add money → Send → History → Ledger checked in a browser, no console errors |
| Tested on | Windows 11, Java 21 (Temurin 21.0.10), Node 24, H2 profile, 4 Nov 2026 |

---

## 1. Description

PayVault is a digital wallet built as six microservices plus a React front end. A user registers, logs in (JWT),
adds money to a wallet and sends money to other users by phone number or email. Every change to a balance is written
to an **append-only ledger** that records the balance before and after, so the wallet balance can always be checked
against the history (*reconciliation*).

The project focuses on **fault tolerance** for money movement. The sender's and receiver's wallets live in Wallet
Service, but the transfer record lives in Transaction Service. One SQL transaction can't cover both services, so a
transfer runs as a **saga**. Each step is saved, retried safely, protected by a circuit breaker, and finished
automatically by a recovery job after a crash or outage. **Money is never lost or moved twice.**

## 2. Architecture

```
                    Browser — React UI  (:5173 dev, :3000 Docker)
                                  │  /api/**
                                  ▼
                 API Gateway :8080   — routes, validates JWT, sets X-User-Id,
                                  │    blocks /internal/** and ../ paths
        ┌───────────────┬─────────┴─────────┬───────────────────────┐
        ▼               ▼                   ▼                       ▼
   Auth :8081      User :8082         Wallet :8083         Transaction :8084
   credentials,    profiles,          balance + ledger,    deposits, transfers (saga),
   BCrypt, JWT     lookup, block      row locks, recon.    idempotency, retry, circuit
        │  Feign ──────▲  ▲───────────────── Feign ◄──────────┘     breaker, RecoveryJob
        └──────────────┴──────► Wallet (create wallet on sign-up)

            Eureka :8761 — every service registers by name; Feign and the Gateway use lb://name
```

| Service | Port | Responsibility | Database |
|---|---|---|---|
| eureka-server | 8761 | Service discovery | — |
| api-gateway | 8080 | Single entry point, routing, JWT check, CORS, blocks `/internal/**` | — |
| auth-service | 8081 | Register, login, refresh; BCrypt passwords; issues access (15 min) + refresh (7 days) JWTs | `payvault_auth` |
| user-service | 8082 | Profiles, receiver lookup (masked phone), admin block/unblock | `payvault_user` |
| wallet-service | 8083 | Balance, idempotent debit/credit, ledger, reconciliation | `payvault_wallet` |
| transaction-service | 8084 | Deposit, transfer saga, Idempotency-Key, Resilience4j, recovery job | `payvault_txn` |
| payvault-ui | 5173 / 3000 | React + Vite single-page app | — |

## 3. Implementation

### 3.1 API Gateway (`api-gateway`)
- **Routes** (`application.yml`): `/api/auth/**`, `/api/users/**`, `/api/wallets/**` and `/api/transactions/**` go to the matching service through Eureka (`lb://…`).
- **`JwtAuthFilter`** (GlobalFilter): public paths (`register`, `login`, `refresh`) pass through. Every other `/api/**` call needs a valid
  `Bearer` access token. The filter **removes any client-supplied `X-User-Id` / `X-User-Role`** and sets them from the verified token, so a client can't pretend to be another user.
- **`InternalPathBlocker`** (WebFilter, *added in this revision*): returns `403` for `/internal/**` and for any path with `..` (raw or URL-encoded), so service-to-service endpoints can't be reached from outside.

### 3.2 Auth Service (`auth-service`)
- `POST /api/auth/register` validates input (`@Valid`: email, 10-digit phone, password 6–64). It creates the profile in User Service (Feign), stores a **BCrypt** hash locally, asks Wallet Service to create an empty wallet, and returns tokens.
  If Wallet Service is down, registration still succeeds and the wallet is created lazily later.
- `POST /api/auth/login` checks BCrypt and returns `accessToken`, `refreshToken`, `userId`, `role`.
- `POST /api/auth/refresh` exchanges a refresh token for new tokens. `JwtService` signs HS384 tokens carrying `sub`, `email`, `role` and `type`.

### 3.3 User Service (`user-service`)
- `GET/PUT /api/users/me`: own profile (phone uniqueness checked).
- `GET /api/users/lookup?query=` finds a receiver by phone or email and returns **only id, name and masked phone** (`******1886`).
- `PUT /api/users/{id}/status?value=BLOCKED|ACTIVE` is **ADMIN only** (role comes from the JWT through the Gateway).
- `/internal/users` (create and get) is used by Auth and Transaction services.

### 3.4 Wallet Service (`wallet-service`), the ledger
- `WalletService.apply()` is the core of the system. In **one local `@Transactional`** it:
  1. locks the wallet row with `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`), plus `@Version` optimistic locking;
  2. returns the existing entry if this `(wallet, txnRef, DEBIT|CREDIT)` was already applied (**idempotent**), also enforced by the unique key `uk_ledger_once`;
  3. rejects a debit larger than the balance with `422 Insufficient balance`;
  4. updates the balance **and** inserts a `LedgerEntry(amount, balanceBefore, balanceAfter)`. Both are saved or neither is.
- `GET /api/wallets/me`, `/me/ledger` (paged, newest first), `/me/reconcile` (balance compared with Σcredits − Σdebits).

### 3.5 Transaction Service (`transaction-service`), the saga
```
PENDING ──debit sender──► DEBITED ──credit receiver──► SUCCESS
   │ debit rejected (4xx)    │ credit rejected (4xx)
   ▼                         ▼
 FAILED                  REVERSING ──refund sender (txnRef-REV)──► REVERSED
```
- **Idempotency:** each deposit/transfer needs an `Idempotency-Key` header, stored as `"<userId>:<key>"` with a unique constraint. Sending the same request again returns the same transaction.
- **Validation:** amount must be positive, have at most 2 decimals, and be within the limits (transfer ≤ 50,000, deposit ≤ 100,000). You can't send money to yourself, and blocked users can't send or receive.
- **`WalletGateway`:** all Wallet calls are wrapped with Resilience4j **Retry** (3 attempts, 400 ms apart) and a **CircuitBreaker** (window 10, 50 % failure rate, 15 s open). Business 4xx errors are never retried and never trip the breaker.
  If Wallet is unreachable, the transaction stays `PENDING`/`DEBITED` and the API answers **`202 Accepted`**.
- **`RecoveryJob`** (`@Scheduled` every 15 s) picks up transactions stuck for more than 30 s in `PENDING`, `DEBITED` or `REVERSING` and moves them forward. This is safe because wallet operations are idempotent.
- HTTP status mapping: `SUCCESS`/`REVERSED` → 200, `FAILED` → 422, still in progress → 202.

### 3.6 React UI (`payvault-ui`)
Pages: Login, Register, Overview (balance + recent activity), Add money, Send (lookup → amount → confirm dialog → result),
History, Ledger (with a *Reconciled* badge), Profile. `api.js` attaches the JWT, **refreshes it automatically on 401**, and creates one
Idempotency-Key per attempt, so a double-click never sends money twice.

---

## 4. Changes made in this revision

The original project compiled, but on a fresh machine it **couldn't be run or tested** without a MySQL server whose root password is `root`.
A real run then exposed the problems below. Each one is fixed and verified:

| # | Problem found | Fix | Files |
|---|---|---|---|
| 1 | Services couldn't start without a MySQL server using `root`/`root` | Added an **`h2` Spring profile** (file database, MySQL mode) and the H2 dependency to the 4 database services. MySQL is still the default and is used by Docker. | `*/pom.xml`, `*/src/main/resources/application-h2.yml` |
| 2 | **Lost money with H2 after a crash**: in the fault test, a ₹500 deposit that had already been confirmed disappeared when wallet-service was killed, because H2 delays writing commits to disk | `WRITE_DELAY=0` in the H2 URL, so every commit is flushed. Re-test: no loss. | `application-h2.yml` |
| 3 | The Gateway's `/internal/**` block **never ran**: a GlobalFilter only runs for matched routes, and no route matches `/internal`, so the client got a generic 404 | Moved the check to a new `InternalPathBlocker` **WebFilter** that runs for every request. It also rejects `..` / `%2e%2e` traversal paths. Now returns `403 Internal endpoint`. | `api-gateway/.../InternalPathBlocker.java`, `JwtAuthFilter.java` |
| 4 | A long error message from another service (> 255 chars) would make saving the transaction fail and break the saga | `failureReason` is cut to 255 characters before saving | `TransactionService.move()` |
| 5 | `docker compose build` of the UI breaks after a local `npm install`, because Windows `node_modules` was copied into the Linux image | Added `payvault-ui/.dockerignore`. The root `.dockerignore` now also skips `data/`, `logs/` and `scripts/`. | `.dockerignore`, `payvault-ui/.dockerignore` |
| 6 | No `mvn` on PATH means you can't build | Added the **Maven wrapper** (`mvnw`, `mvnw.cmd`, `.mvn/`, Maven 3.9.9) | root |
| 7 | Starting 6 services by hand is slow and error-prone | `scripts/start-local.ps1|.sh` and `stop-local.ps1|.sh` start everything in order and wait for each port | `scripts/` |
| 8 | No automated tests | `scripts/smoke-test.mjs` (30 checks) and `scripts/fault-test.mjs` (9 checks). Both use plain Node 18+ with no packages. | `scripts/` |
| 9 | Docs | README: new run options, troubleshooting (`PKIX`, `JAVA_HOME`), plus this report | `README.md`, `REPORT.md` |

---

## 5. How to run

**Windows (no MySQL, no Maven install needed):**
```powershell
.\mvnw.cmd clean package -DskipTests
.\scripts\start-local.ps1
node scripts\smoke-test.mjs
cd payvault-ui; npm install; npm run dev       # open http://localhost:5173
.\scripts\stop-local.ps1
```
**Docker (MySQL):** `docker compose up --build`, then open http://localhost:3000 (Eureka: http://localhost:8761).

---

## 6. Expected outputs

### 6.1 Smoke test — `node scripts/smoke-test.mjs` (actual output)
```
1. Register two users
  PASS  register A -> 201 with accessToken
  PASS  register B -> 201 with accessToken
  PASS  duplicate email -> 409
  PASS  invalid input -> 400
2. Login
  PASS  login -> 200 with role USER
  PASS  wrong password -> 401
  PASS  refresh token -> new access token
3. Profile and wallet
  PASS  GET /users/me -> own profile
  PASS  new wallet balance is 0.00
4. Deposit 500 to A
  PASS  deposit -> 200 SUCCESS
5. Look up receiver and transfer 150 A -> B
  PASS  lookup by phone -> masked phone
  PASS  transfer -> 200 SUCCESS
  PASS  A balance 500.00 -> 350.00
  PASS  B balance 0.00 -> 150.00
6. Ledger and reconciliation
  PASS  A ledger: DEBIT 150.00 before 500.00 after 350.00
  PASS  B ledger: CREDIT 150.00 with the same txnRef
  PASS  A reconcile: consistent, 2 entries, net 350.00
7. Idempotency
  PASS  same Idempotency-Key -> same txnRef
  PASS  money moved only once (A still 350.00)
  PASS  missing Idempotency-Key -> 400
8. Business rules
  PASS  insufficient balance -> 422 FAILED
  PASS  nothing moved after failure (A still 350.00)
  PASS  transfer to yourself -> 400
  PASS  history shows 3 transactions (deposit, transfer, failed)
  PASS  receiver sees transfer as RECEIVED
9. Security
  PASS  no token -> 401
  PASS  forged token -> 401
  PASS  /internal/** through Gateway -> 403
  PASS  non-admin block user -> 403
10. Circuit breaker
  PASS  circuit state is CLOSED (Wallet healthy)

30 passed, 0 failed
```

### 6.2 Fault-tolerance test — `node scripts/fault-test.mjs` (actual output)
```
1. Set up two users, A has 500.00
  PASS  deposit 500 -> SUCCESS
2. Wallet Service goes down
  PASS  transfer while Wallet is down -> 202 PENDING (retried, then parked)
        (took 11762 ms: Feign timeout + 3 Resilience4j attempts)
  PASS  circuit breaker -> OPEN after repeated failures
  PASS  open circuit fails fast (80 ms) and still parks the transfer as PENDING
3. Wallet Service comes back; RecoveryJob should finish every PENDING transfer
        t+5s  … t+35s TXN20261104053219-822015 PENDING
        t+40s TXN20261104053219-822015 DEBITED
        t+45s TXN20261104053219-822015 SUCCESS
  PASS  stuck 150.00 transfer recovered -> SUCCESS
  PASS  all 6 transfers ended SUCCESS
  PASS  A = 500 - 150 - 5x1 = 345.00 (no money lost or moved twice)
  PASS  B = 155.00
  PASS  A ledger reconciles (balance = credits - debits)

9 passed, 0 failed
```

### 6.3 Sample API responses (captured from the running system, through the Gateway on :8080)

| Request | Status | Response |
|---|---|---|
| `GET /api/wallets/me` | 200 | `{"userId":5,"balance":750.00,"updatedAt":"2026-11-04T05:41:23Z"}` |
| `GET /api/wallets/me/ledger` | 200 | `{"content":[{"txnRef":"TXN20261104054123-B510A6","entryType":"DEBIT","amount":250.00,"balanceBefore":1000.00,"balanceAfter":750.00}, {"txnRef":"TXN20261104054036-17CDDE","entryType":"CREDIT","amount":1000.00,"balanceBefore":0.00,"balanceAfter":1000.00}],"totalElements":2,…}` |
| `GET /api/wallets/me/reconcile` | 200 | `{"userId":5,"walletBalance":750.00,"ledgerNet":750.00,"entries":2,"consistent":true}` |
| `GET /api/users/lookup?query=8000071886` | 200 | `{"id":2,"fullName":"Vikram Shah","maskedPhone":"******1886"}` |
| `POST /api/transactions/transfer` (₹9,999, balance 750) | **422** | `{"txnRef":"TXN…-E3E5AD","status":"FAILED","failureReason":"Insufficient balance",…}` |
| `POST /api/transactions/transfer` (Wallet down) | **202** | `{"status":"PENDING","failureReason":"Wallet Service … - will retry automatically",…}` |
| `GET /api/transactions/circuit` | 200 | `{"circuitBreaker":"wallet-service","state":"CLOSED","failureRatePercent":0.0,…}` (→ `"OPEN"` while Wallet is down) |
| `GET /api/wallets/me` without a token | **401** | `{"status":401,"error":"Missing bearer token"}` |
| `GET /internal/wallets/1` | **403** | `{"status":403,"error":"Internal endpoint"}` |
| `POST /api/auth/register` with bad input | **400** | `{"status":400,"error":"phone must be 10 digits, email must be a well-formed email address, password size must be between 6 and 64"}` |
| Same email registered twice | **409** | `{"status":409,"error":"Email already registered"}` |

### 6.4 UI walkthrough (checked in a browser)
1. **Register** "UI Tester" → redirected to Overview: *Available balance ₹0.00*, "No transactions yet."
2. **Add money** → ₹1,000 chip → *"₹1,000.00 added. TXN20261104054036-17CDDE"*
3. **Send** → lookup `8000071886` → *Vikram Shah ******1886* → ₹250 → confirm dialog *"Send ₹250.00 to Vikram Shah?"* → *"Sent ₹250.00 to Vikram Shah — Status SUCCESS"*
4. **History** → *To Vikram Shah −₹250.00 SUCCESS*, *Added to wallet +₹1,000.00 SUCCESS*
5. **Ledger** → badge *Reconciled · 2 entries*; rows `DEBIT ₹250.00 | ₹1,000.00 → ₹750.00` and `CREDIT ₹1,000.00 | ₹0.00 → ₹1,000.00`

---

## 7. Limitations and notes
- **Docker Compose wasn't run in this test** (Docker Desktop wasn't running on the test machine). The compose file and Dockerfiles were reviewed and the UI `.dockerignore` bug fixed, but run `docker compose up --build` once to confirm.
- **MySQL mode wasn't run in this test** (the local MySQL root password isn't `root`). The SQL is plain JPA and the H2 profile runs in MySQL compatibility mode. Set `DB_USER`/`DB_PASS` and start with `-MySql` / `mysql` to use it.
- There are no JUnit unit tests yet. Behaviour is covered by the two end-to-end scripts above.
- The JWT secret in `application.yml` / `docker-compose.yml` is a development default. Set `JWT_SECRET` in any real deployment.
- Deposits are simulated top-ups: no payment gateway is connected.

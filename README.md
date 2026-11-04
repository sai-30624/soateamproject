# PayVault — Fault-Tolerant Digital Wallet & Real-Time Financial Ledger

Full-stack microservices project: **Spring Boot 3 + Spring Cloud** backend (6 services) and a **React** frontend.
Users register, log in with JWT, deposit money, transfer to other users, and every balance change is written to an
append-only ledger with `balanceBefore` and `balanceAfter`.

```
                    Client (React :5173 / :3000)
                              |
                              v
                     API Gateway  :8080   (routes /api/**, validates JWT, adds X-User-Id)
                              |
          +-------------------+--------------------+
          |                   |                    |
          v                   v                    v
     Auth :8081          User :8082        Transaction :8084  (saga orchestrator)
     payvault_auth       payvault_user     payvault_txn
                                                   |  Feign + Resilience4j
                                                   v
                                           Wallet :8083   (balance + ledger)
                                           payvault_wallet

             Eureka Server :8761 — every service registers here and finds the others by name
```

| Service | Port | Responsibility | Database |
|---|---|---|---|
| eureka-server | 8761 | Service discovery | — |
| api-gateway | 8080 | Single entry point, routing, JWT check, blocks `/internal/**` | — |
| auth-service | 8081 | Register, login, refresh, issues JWT (BCrypt passwords) | `payvault_auth` |
| user-service | 8082 | Profiles, receiver lookup, admin block/unblock | `payvault_user` |
| wallet-service | 8083 | Balance, debit/credit, **financial ledger**, reconciliation | `payvault_wallet` |
| transaction-service | 8084 | Deposits, transfers (saga), idempotency, recovery job, circuit breaker | `payvault_txn` |
| payvault-ui | 5173 (dev) / 3000 (Docker) | React app | — |

---

## Run it

### Option A — Docker (easiest, one command)

Needs Docker Desktop.

```bash
docker compose up --build
```

First build takes a few minutes (Maven downloads dependencies). Then open:

- App: http://localhost:3000
- Eureka dashboard: http://localhost:8761 (wait until all 5 services show as UP, about 30–60 s)

### Option B — Run locally with one script, no MySQL needed (H2)

Needs **Java 21** and **Node 18+**. Maven is not needed: the project ships the Maven wrapper (`mvnw` / `mvnw.cmd`).

```powershell
# Windows PowerShell
.\mvnw.cmd clean package -DskipTests
.\scripts\start-local.ps1                 # starts Eureka, 4 services (H2 profile) and the Gateway
node scripts\smoke-test.mjs               # 30 automated end-to-end checks
cd payvault-ui; npm install; npm run dev  # http://localhost:5173
.\scripts\stop-local.ps1
```

```bash
# Linux / macOS / Git Bash
./mvnw clean package -DskipTests
./scripts/start-local.sh
node scripts/smoke-test.mjs
cd payvault-ui && npm install && npm run dev
./scripts/stop-local.sh
```

The `h2` Spring profile stores each service's data in `./data/*.mv.db`. Add `-MySql` (PowerShell) or `mysql` (bash) to use
MySQL instead. See [REPORT.md](REPORT.md) for what each test checks and the expected output.

If Maven fails with `PKIX path building failed` (a corporate proxy or antivirus intercepting HTTPS), use the Windows
certificate store: `$env:MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"`. If it says `JAVA_HOME is not defined
correctly`, point `JAVA_HOME` at your JDK 21 folder.

### Option C — Run each service by hand with MySQL (IntelliJ / terminal)

Needs **Java 21**, **Node 18+**, and **MySQL 8** running on `localhost:3306` with user `root` / password `root`
(change with env vars `DB_USER`, `DB_PASS`, `DB_HOST`). Databases are created automatically.

Start in this order, each in its own terminal (or run each `*Application` class in IntelliJ;
add `--spring.profiles.active=h2` to the four business services to skip MySQL):

```bash
./mvnw clean package -DskipTests       # once, from the project root

java -jar eureka-server/target/eureka-server.jar
java -jar auth-service/target/auth-service.jar
java -jar user-service/target/user-service.jar
java -jar wallet-service/target/wallet-service.jar
java -jar transaction-service/target/transaction-service.jar
java -jar api-gateway/target/api-gateway.jar

cd payvault-ui && npm install && npm run dev   # http://localhost:5173
```

Swagger UI for each business service: `http://localhost:8081/swagger-ui.html` (8082, 8083, 8084 likewise — local run only).

---

## Demo script (for the presentation)

Use the UI, or `demo.http` (IntelliJ / VS Code REST Client / Postman).

1. **Register** two users, WWW and XYZ.
2. **Deposit** ₹500 to WWW.
3. **Transfer** ₹150 WWW → XYZ. Balance goes 500.00 → 350.00.
4. Open **Ledger**: `DEBIT 150.00 | before 500.00 | after 350.00`, and XYZ sees the matching `CREDIT` with the same reference. The badge shows **Reconciled** (balance = credits − debits).
5. **Idempotency**: send the same transfer twice with the same `Idempotency-Key` (request 7 in `demo.http`) → same `txnRef`, money moves once.
6. **Insufficient balance**: transfer ₹9,999 → `422 FAILED`, nothing moved.
7. **Fault tolerance**: stop `wallet-service` (`docker compose stop wallet-service`), try a transfer:
   - Resilience4j retries 3 times, then the transfer is saved as `PENDING` and the API returns `202 Accepted` — no money is lost or moved twice.
   - After repeated failures the circuit opens — `GET /api/transactions/circuit` shows `OPEN`, and calls fail fast.
   - Start it again (`docker compose start wallet-service`). Within ~30–45 s the **RecoveryJob** finishes the stuck transfer → `SUCCESS`.
   - Automated version: `scripts/fault-test.mjs` (9 checks; see the header of the file for the stop/start commands).
8. **Security**: call `/api/wallets/me` without a token → 401. Call `/internal/...` through the Gateway → blocked.

---

## How a transfer works (saga)

The sender and receiver wallets live in Wallet Service, but the transaction record lives in Transaction Service,
so one SQL transaction can't cover everything. Transaction Service runs a **saga** and saves each step:

```
PENDING --debit sender--> DEBITED --credit receiver--> SUCCESS
   |                         |
   | debit rejected (422)    | credit rejected
   v                         v
 FAILED                  REVERSING --refund sender--> REVERSED
```

| Problem | How PayVault handles it | Where |
|---|---|---|
| Double click / client retry | `Idempotency-Key` header, unique in `transactions` | `TransactionService.transfer` |
| Same debit applied twice on retry | Unique key `(wallet_id, txn_ref, entry_type)` + check before applying | `WalletService.apply`, `LedgerEntry` |
| Two transfers from one wallet at the same time | `SELECT … FOR UPDATE` (pessimistic lock) + `@Version` | `WalletRepository.findByUserIdForUpdate` |
| Balance + ledger getting out of sync | Both written in one local `@Transactional` | `WalletService.apply` |
| Negative balance | Checked before debit → 422 | `WalletService.apply` |
| Wallet Service slow or down | Feign timeouts + Resilience4j retry (3×) + circuit breaker | `WalletGateway` |
| Crash in the middle of a transfer | `RecoveryJob` resumes PENDING / DEBITED / REVERSING every 15 s | `RecoveryJob` |
| Credit to receiver fails permanently | Compensating refund to sender (`txnRef-REV`) | `TransactionService.runSaga` |
| Audit | `/api/wallets/me/reconcile` compares balance with ledger sum | `WalletService.reconcile` |
| Spoofed user id | Gateway strips client `X-User-Id`, sets it from the verified JWT | `JwtAuthFilter` |

---

## API (all through the Gateway, `http://localhost:8080`)

| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/register` | public |
| POST | `/api/auth/login` | public |
| POST | `/api/auth/refresh` | refresh token |
| GET / PUT | `/api/users/me` | JWT |
| GET | `/api/users/lookup?query=<phone or email>` | JWT |
| PUT | `/api/users/{id}/status?value=BLOCKED` | JWT, ADMIN |
| GET | `/api/wallets/me` | JWT |
| GET | `/api/wallets/me/ledger?page=0&size=20` | JWT |
| GET | `/api/wallets/me/reconcile` | JWT |
| POST | `/api/transactions/deposit` | JWT + `Idempotency-Key` |
| POST | `/api/transactions/transfer` | JWT + `Idempotency-Key` |
| GET | `/api/transactions?page=0&size=20` | JWT |
| GET | `/api/transactions/{txnRef}` | JWT |
| GET | `/api/transactions/circuit` | JWT |

Service-to-service only (blocked at the Gateway): `/internal/users/**`, `/internal/wallets/**`.

To make a user ADMIN: `UPDATE payvault_auth.credentials SET role='ADMIN' WHERE email='...';` then log in again.

---

## Project structure

```
payvault/
├── pom.xml                  parent POM (Spring Boot 3.3.5, Spring Cloud 2023.0.3, Java 21)
├── docker-compose.yml
├── Dockerfile               builds any service: --build-arg MODULE=<service>
├── demo.http                ready-made demo requests
├── REPORT.md                implementation report, test results and expected outputs
├── mvnw, mvnw.cmd, .mvn/    Maven wrapper (no Maven install needed)
├── scripts/                 start-local / stop-local (.ps1 + .sh), smoke-test.mjs, fault-test.mjs
├── eureka-server/
├── api-gateway/             filter/JwtAuthFilter.java, routes in application.yml
├── auth-service/            controller, service (AuthService, JwtService), client (Feign), entity, repository
├── user-service/
├── wallet-service/          entity/Wallet (@Version), entity/LedgerEntry, service/WalletService
├── transaction-service/     service/TransactionService (saga), service/RecoveryJob, client/WalletGateway (Resilience4j)
└── payvault-ui/             React + Vite: pages/, components/, context/AuthContext.jsx, api.js
```

## Tech stack

Java 21 · Spring Boot 3.3 · Spring Cloud Netflix Eureka · Spring Cloud Gateway · OpenFeign · Resilience4j ·
Spring Data JPA · MySQL 8 · JJWT · BCrypt · springdoc-openapi · React 18 · Vite · Axios · Docker Compose

## Ideas to extend

- Kafka events (`TransferCompleted`) + a Notification Service for email/SMS
- Spring Cloud Config Server for central configuration
- Zipkin tracing, Prometheus + Grafana dashboards
- Rate limiting at the Gateway (Redis `RequestRateLimiter`)
- Unit tests with JUnit 5 + Mockito, integration tests with Testcontainers

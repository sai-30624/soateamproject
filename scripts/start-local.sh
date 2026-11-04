#!/usr/bin/env bash
# Starts all PayVault back-end services in the background (Linux / macOS / Git Bash).
#
#   ./scripts/start-local.sh          # H2 file databases - no MySQL needed
#   ./scripts/start-local.sh mysql    # MySQL on localhost:3306 (export DB_USER / DB_PASS first)
#   ./scripts/stop-local.sh
#
# Build first:  ./mvnw clean package -DskipTests      Logs: ./logs/<service>.log
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p logs
: > logs/pids.txt

PROFILE_ARG="--spring.profiles.active=h2"
[ "${1:-}" = "mysql" ] && PROFILE_ARG=""

start() {
  local name=$1; shift
  [ -f "$name/target/$name.jar" ] || { echo "$name.jar missing - run ./mvnw clean package -DskipTests"; exit 1; }
  nohup java -jar "$name/target/$name.jar" "$@" > "logs/$name.log" 2>&1 &
  echo $! >> logs/pids.txt
  echo "started $name (pid $!)"
}

wait_port() {
  for _ in $(seq 1 90); do
    if (echo > /dev/tcp/localhost/$1) 2>/dev/null; then echo "  $2 is up on :$1"; return; fi
    sleep 1
  done
  echo "$2 did not open port $1 - see logs/$2.log"; exit 1
}

start eureka-server
wait_port 8761 eureka-server
for s in auth-service user-service wallet-service transaction-service; do start "$s" $PROFILE_ARG; done
start api-gateway
wait_port 8081 auth-service; wait_port 8082 user-service; wait_port 8083 wallet-service
wait_port 8084 transaction-service; wait_port 8080 api-gateway

echo
echo "All services started. Give Eureka ~30 s to register everything (http://localhost:8761)."
echo "Then: cd payvault-ui && npm install && npm run dev   ->  http://localhost:5173"
echo "Smoke test: node scripts/smoke-test.mjs"

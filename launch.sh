#!/usr/bin/env bash
#
# launch.sh: start the whole microservices shop locally, one component at a time.
#
#   ./launch.sh [start]      start infrastructure, backend services, then the frontend
#   ./launch.sh stop         stop the frontend and backend services (infrastructure keeps running)
#   ./launch.sh stop --all   also stop the Docker infrastructure (docker compose down)
#   ./launch.sh status       show what is running
#
# Startup order (each step waits until the previous one responds):
#   1. Infrastructure (Docker Compose): MongoDB, PostgreSQL (order), PostgreSQL (inventory), Keycloak
#   2. inventory-service :8082
#   3. product-service   :8080
#   4. order-service     :8081   (calls inventory-service)
#   5. api-gateway       :9000   (routes to the three services, validates JWTs)
#   6. frontend          :4200   (always last)
#
# Logs: .run/logs/<name>.log   PIDs: .run/pids/<name>.pid
# Written for the bash 3.2 that ships with macOS.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT/.run"
LOG_DIR="$RUN_DIR/logs"
PID_DIR="$RUN_DIR/pids"

# Backend services in start order, as name:port.
BACKEND_SERVICES="inventory-service:8082 product-service:8080 order-service:8081 api-gateway:9000"
FRONTEND_PORT=4200
COMPOSE_DIRS="product-service order-service inventory-service api-gateway"

# Timeouts in seconds. Keycloak and the first start of each database (initdb) take the longest.
INFRA_TIMEOUT="${INFRA_TIMEOUT:-240}"
SERVICE_TIMEOUT="${SERVICE_TIMEOUT:-240}"
FRONTEND_TIMEOUT="${FRONTEND_TIMEOUT:-300}"

KEYCLOAK_REALM_URL="http://localhost:8181/realms/spring-microservices-security-realm/.well-known/openid-configuration"

# ---------- output helpers ----------
if [ -t 1 ]; then
  C_BLUE=$'\033[34m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_OFF=$'\033[0m'
else
  C_BLUE=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_OFF=""
fi
step() { printf '\n%s==> %s%s\n' "$C_BLUE" "$*" "$C_OFF"; }
ok()   { printf '%s  ✔ %s%s\n' "$C_GREEN" "$*" "$C_OFF"; }
warn() { printf '%s  ! %s%s\n' "$C_YELLOW" "$*" "$C_OFF"; }
die()  { printf '%s  ✘ %s%s\n' "$C_RED" "$*" "$C_OFF" >&2; exit 1; }

# ---------- generic helpers ----------
port_owner() {   # prints "command (pid N)" of the process listening on a TCP port, or nothing
  # lsof exits 1 when nothing listens; that must not trip `set -e -o pipefail`.
  local owner container
  owner="$({ lsof -nP -iTCP:"$1" -sTCP:LISTEN 2>/dev/null || true; } | awk 'NR==2 {print $1" (pid "$2")"}')"
  case "$owner" in
    com.docke*)   # Docker Desktop's proxy holds published ports; name the container instead
      container="$(docker ps --filter "publish=$1" --format '{{.Names}}' 2>/dev/null | head -n 1 || true)"
      [ -n "$container" ] && owner="Docker container '$container'" ;;
  esac
  printf '%s' "$owner"
}

pid_alive() {    # true if the pid file for $1 exists and that process is running
  local f="$PID_DIR/$1.pid"
  [ -f "$f" ] && kill -0 "$(cat "$f")" 2>/dev/null
}

http_up() {      # true if the URL answers with any HTTP status (401 counts: the server is up)
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$1" 2>/dev/null || true)"
  [ -n "$code" ] && [ "$code" != "000" ]
}

wait_until() {   # wait_until <label> <timeout> <check...>
  # Returns 0 when the check succeeds, 1 on timeout, 2 as soon as the check reports
  # (with exit code 2) that the container/process it waits for has died.
  local label="$1" timeout="$2" waited=0 rc
  shift 2
  printf '  … waiting for %s ' "$label"
  while true; do
    if "$@" >/dev/null 2>&1; then
      printf ' (%ss)\n' "$waited"
      return 0
    else
      rc=$?
    fi
    if [ "$rc" -eq 2 ]; then printf '\n'; return 2; fi
    if [ "$waited" -ge "$timeout" ]; then printf '\n'; return 1; fi
    printf '.'
    sleep 3
    waited=$((waited + 3))
  done
}

container_running() {
  [ "$(docker inspect -f '{{.State.Status}}' "$1" 2>/dev/null || true)" = "running" ]
}

kill_tree() {    # kill a process and all of its descendants (Maven forks the app JVM as a child)
  local pid="$1" child
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do
    kill_tree "$child"
  done
  kill "$pid" 2>/dev/null || true
}

show_log_tail() {
  warn "last lines of $1:"
  tail -n 30 "$1" | sed 's/^/      /' >&2 || true
}

# ---------- readiness checks (exit code 2 = the thing we wait for has died) ----------
mongo_ready() {
  container_running mongodb || return 2
  docker exec mongodb mongosh --quiet --eval 'db.runCommand({ping: 1}).ok' 2>/dev/null | grep -q 1
}
# pg_isready over TCP (-h 127.0.0.1): on first start the image runs initdb on a socket-only
# temporary server, so a socket check could pass before the real server is listening.
# pg_isready exits 2 for "no response yet", which wait_until would read as "container died",
# so any failure is reported as 1 (not ready yet).
order_db_ready() {
  container_running order-service-postgres || return 2
  docker exec order-service-postgres pg_isready -q -h 127.0.0.1 -U postgres -d order_service || return 1
}
inventory_db_ready() {
  container_running inventory-service-postgres || return 2
  docker exec inventory-service-postgres pg_isready -q -h 127.0.0.1 -U postgres -d inventory_service || return 1
}
keycloak_ready() {   # the realm URL returns 200 only once the realm is imported
  container_running keycloak || return 2
  curl -sf --max-time 3 "$KEYCLOAK_REALM_URL" >/dev/null
}
process_ready() {    # process_ready <name> <url>: the app's process must be alive and answer HTTP
  pid_alive "$1" || return 2
  http_up "$2"
}

wait_for_container() {   # wait_for_container <label> <container> <check>
  local rc=0
  wait_until "$1" "$INFRA_TIMEOUT" "$3" || rc=$?
  case "$rc" in
    0) ;;
    2) warn "container '$2' stopped during startup. Last log lines:"
       docker logs --tail 12 "$2" 2>&1 | cut -c1-220 | sed 's/^/      /' >&2 || true
       die "'$2' exited. If its data folder is corrupt, see the Troubleshooting section in docs/RUNNING.md." ;;
    *) die "$1 was not ready after ${INFRA_TIMEOUT}s (check: docker logs $2)" ;;
  esac
}

# ---------- preflight ----------
preflight() {
  step "Preflight checks"
  local cmd
  for cmd in docker curl lsof java node npm; do
    command -v "$cmd" >/dev/null 2>&1 || die "'$cmd' is not installed or not on PATH"
  done
  docker info >/dev/null 2>&1 || die "Docker is not running. Start Docker Desktop and try again."

  local java_major
  java_major="$(java -version 2>&1 | awk -F'"' '/version/ {split($2, v, "."); print v[1]; exit}')"
  if [ -n "$java_major" ] && [ "$java_major" -lt 21 ] 2>/dev/null; then
    die "Java 21 or newer is required (found $java_major)"
  fi
  ok "docker, java ${java_major:-?}, node $(node -v) available"

  # Every app port must be free, unless this script already started that component.
  local entry name port owner conflicts=""
  for entry in $BACKEND_SERVICES "frontend:$FRONTEND_PORT"; do
    name="${entry%%:*}"; port="${entry##*:}"
    pid_alive "$name" && continue
    owner="$(port_owner "$port")"
    if [ -n "$owner" ]; then
      conflicts="$conflicts\n      port $port ($name) is used by $owner"
    fi
  done
  if [ -n "$conflicts" ]; then
    printf '%s  ✘ These ports are already in use:%b%s\n' "$C_RED" "$conflicts" "$C_OFF" >&2
    printf '      Free them (e.g. stop the container or process) and run again. Nothing was started.\n' >&2
    exit 1
  fi
  ok "ports 8080, 8081, 8082, 9000 and 4200 are free or already used by this stack"
}

# ---------- start steps ----------
start_infrastructure() {
  step "1/6 Infrastructure (Docker Compose)"
  local dir
  for dir in $COMPOSE_DIRS; do
    (cd "$ROOT/$dir" && docker compose up -d) >"$LOG_DIR/compose-$dir.log" 2>&1 \
      || { show_log_tail "$LOG_DIR/compose-$dir.log"; die "docker compose up failed in $dir"; }
    ok "compose up: $dir"
  done
  wait_for_container "MongoDB :27017"        mongodb                 mongo_ready
  wait_for_container "PostgreSQL order :5434"     order-service-postgres     order_db_ready
  wait_for_container "PostgreSQL inventory :5435" inventory-service-postgres inventory_db_ready
  wait_for_container "Keycloak realm :8181"  keycloak                keycloak_ready
  ok "infrastructure is ready"
}

start_backend_service() {   # start_backend_service <step-label> <name> <port>
  local label="$1" name="$2" port="$3" log="$LOG_DIR/$2.log"
  step "$label $name :$port"
  if pid_alive "$name"; then
    ok "already running (pid $(cat "$PID_DIR/$name.pid"))"
    return
  fi
  # Only some services ship a Maven wrapper (and it is not executable in git), so every
  # service is run through product-service's wrapper with -f pointing at its own pom.
  ( cd "$ROOT/product-service" && exec nohup sh ./mvnw -B -q -f "$ROOT/$name/pom.xml" spring-boot:run ) >"$log" 2>&1 &
  echo $! >"$PID_DIR/$name.pid"
  local rc=0
  wait_until "$name to answer on :$port" "$SERVICE_TIMEOUT" process_ready "$name" "http://localhost:$port/api-docs" || rc=$?
  if [ "$rc" -ne 0 ]; then
    show_log_tail "$log"
    [ "$rc" -eq 2 ] && die "$name exited during startup. Full log: $log"
    die "$name did not answer within ${SERVICE_TIMEOUT}s. Full log: $log"
  fi
  ok "$name is up (log: .run/logs/$name.log)"
}

start_frontend() {
  step "6/6 frontend :$FRONTEND_PORT"
  if pid_alive frontend; then
    ok "already running (pid $(cat "$PID_DIR/frontend.pid"))"
    return
  fi
  if [ ! -d "$ROOT/frontend/node_modules" ]; then
    printf '  … installing npm dependencies (first run only)\n'
    (cd "$ROOT/frontend" && npm ci --no-audit --no-fund) >"$LOG_DIR/frontend-install.log" 2>&1 \
      || { show_log_tail "$LOG_DIR/frontend-install.log"; die "npm ci failed"; }
  fi
  ( cd "$ROOT/frontend" && exec nohup npx ng serve --port "$FRONTEND_PORT" ) >"$LOG_DIR/frontend.log" 2>&1 &
  echo $! >"$PID_DIR/frontend.pid"
  local rc=0
  wait_until "Angular dev server on :$FRONTEND_PORT" "$FRONTEND_TIMEOUT" process_ready frontend "http://localhost:$FRONTEND_PORT" || rc=$?
  if [ "$rc" -ne 0 ]; then
    show_log_tail "$LOG_DIR/frontend.log"
    [ "$rc" -eq 2 ] && die "the frontend exited during startup. Full log: $LOG_DIR/frontend.log"
    die "the frontend did not answer within ${FRONTEND_TIMEOUT}s. Full log: $LOG_DIR/frontend.log"
  fi
  ok "frontend is up (log: .run/logs/frontend.log)"
}

cmd_start() {
  mkdir -p "$LOG_DIR" "$PID_DIR"
  preflight
  start_infrastructure
  local entry n=2
  for entry in $BACKEND_SERVICES; do
    start_backend_service "$n/6" "${entry%%:*}" "${entry##*:}"
    n=$((n + 1))
  done
  start_frontend

  step "All components are running"
  cat <<EOF
  Frontend          http://localhost:$FRONTEND_PORT
  API gateway       http://localhost:9000   (Swagger: http://localhost:9000/swagger-ui.html)
  Keycloak admin    http://localhost:8181   (admin / admin)
  Test login        testuser (password in api-gateway/docker/keycloak/realms/realm-export.json)

  Logs:   .run/logs/
  Stop:   ./launch.sh stop        (add --all to stop the Docker infrastructure too)
EOF
}

# ---------- stop / status ----------
stop_one() {
  local name="$1" f="$PID_DIR/$1.pid"
  if pid_alive "$name"; then
    kill_tree "$(cat "$f")"
    ok "stopped $name"
  fi
  rm -f "$f"
}

cmd_stop() {
  step "Stopping applications"
  stop_one frontend
  local entry i list=""
  for entry in $BACKEND_SERVICES; do list="${entry%%:*} $list"; done   # reverse start order
  for i in $list; do stop_one "$i"; done
  if [ "${1:-}" = "--all" ]; then
    step "Stopping infrastructure"
    local dir
    for dir in $COMPOSE_DIRS; do
      (cd "$ROOT/$dir" && docker compose down) >/dev/null 2>&1 && ok "compose down: $dir"
    done
  else
    printf '  Infrastructure is still running. Use "./launch.sh stop --all" to stop it as well.\n'
  fi
}

cmd_status() {
  step "Applications"
  local entry name port state
  for entry in $BACKEND_SERVICES "frontend:$FRONTEND_PORT"; do
    name="${entry%%:*}"; port="${entry##*:}"
    if pid_alive "$name"; then state="${C_GREEN}running${C_OFF} (pid $(cat "$PID_DIR/$name.pid"))"; else state="stopped"; fi
    printf '  %-18s :%-5s %s\n' "$name" "$port" "$state"
  done
  step "Infrastructure containers"
  docker ps --filter name='^mongodb$' --filter name='^order-service-postgres$' --filter name='^inventory-service-postgres$' \
    --filter name='^keycloak$' --filter name='^keycloak-mysql$' \
    --format '  {{.Names}}\t{{.Status}}' 2>/dev/null || warn "Docker is not reachable"
}

case "${1:-start}" in
  start)  cmd_start ;;
  stop)   cmd_stop "${2:-}" ;;
  status) cmd_status ;;
  -h|--help|help) sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' ;;
  *) die "unknown command '$1' (use start, stop [--all], status)" ;;
esac

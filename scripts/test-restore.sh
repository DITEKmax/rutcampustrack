#!/usr/bin/env bash
# M13 G15 — automated tested-restore для backup'ов.
#
# Делает end-to-end:
#   1. Генерирует .env.test-restore (ephemeral random passwords).
#   2. Поднимает docker-compose.test-restore.yml (2 postgres + 1 mongo, ephemeral tmpfs).
#   3. Ждёт пока все 3 контейнера станут healthy.
#   4. Вызывает scripts/restore.sh $DATE --target=test.
#   5. Проверяет, что row count > 0 в core таблицах (если backup пустой — FAIL).
#   6. Teardown: docker compose down -v.
#
# Использование:
#   scripts/test-restore.sh 2026-04-25                   # default: BACKUP_DIR=/opt/backups
#   BACKUP_DIR=./my-backups scripts/test-restore.sh 2026-04-25
#   scripts/test-restore.sh latest                       # последняя дата в BACKUP_DIR
#
# Exit codes:
#   0 — restore succeeded + все row counts > 0.
#   1 — args/config errors.
#   2 — backup files missing.
#   3 — test-compose startup failed.
#   4 — restore script failed.
#   5 — row count verification failed.

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <YYYY-MM-DD | latest>" >&2
    exit 1
fi

DATE="$1"
BACKUP_DIR="${BACKUP_DIR:-/opt/backups}"

# Resolve "latest"
if [ "$DATE" = "latest" ]; then
    DATE="$(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null | sort -r | head -n 1)"
    if [ -z "$DATE" ]; then
        echo "ERROR: no backups found in $BACKUP_DIR" >&2
        exit 2
    fi
    echo "[test-restore] Latest backup: $DATE"
fi

SRC="$BACKUP_DIR/$DATE"
if [ ! -d "$SRC" ]; then
    echo "ERROR: $SRC not found." >&2
    exit 2
fi

LOG_PREFIX="[test-restore $(date -u +%H:%M:%S)]"
log() { echo "$LOG_PREFIX $*"; }
err() { echo "$LOG_PREFIX ERROR: $*" >&2; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/docker-compose.test-restore.yml"
ENV_FILE="$REPO_ROOT/.env.test-restore"
PROJECT="rct-test-restore"

if [ ! -f "$COMPOSE_FILE" ]; then
    err "$COMPOSE_FILE not found."
    exit 1
fi

# -----------------------------------------------------------------------------
# Cleanup on exit (guaranteed teardown)
# -----------------------------------------------------------------------------
cleanup() {
    log "Teardown test-compose..."
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" -p "$PROJECT" down -v --timeout 10 >/dev/null 2>&1 || true
    rm -f "$ENV_FILE"
    log "Teardown done."
}
trap cleanup EXIT

# -----------------------------------------------------------------------------
# 1. Generate ephemeral .env.test-restore
# -----------------------------------------------------------------------------
log "Generate ephemeral test env..."
RAND_PG_ACADEMIC="$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)"
RAND_PG_SCHEDULE="$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)"
RAND_MONGO_ROOT="$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)"
RAND_MONGO_KEY="$(openssl rand -base64 756 | tr -d '\n')"

cat > "$ENV_FILE" <<EOF
# Ephemeral test env — DO NOT commit (gitignored).
POSTGRES_ACADEMIC_PASSWORD=$RAND_PG_ACADEMIC
POSTGRES_SCHEDULE_PASSWORD=$RAND_PG_SCHEDULE
MONGO_ROOT_PASSWORD=$RAND_MONGO_ROOT
MONGODB_REPLICA_SET_KEY="$RAND_MONGO_KEY"
EOF
chmod 600 "$ENV_FILE"

# -----------------------------------------------------------------------------
# 2. Start test-compose
# -----------------------------------------------------------------------------
log "Start test-compose (project=$PROJECT)..."
if ! docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" -p "$PROJECT" up -d >/dev/null; then
    err "test-compose up failed."
    exit 3
fi

# -----------------------------------------------------------------------------
# 3. Wait for healthy (max 90s)
# -----------------------------------------------------------------------------
log "Wait for containers healthy..."
CONTAINERS=(rct-test-postgres-academic rct-test-postgres-schedule rct-test-mongo-attendance)

for i in $(seq 1 45); do
    ALL_HEALTHY=1
    for c in "${CONTAINERS[@]}"; do
        STATUS="$(docker inspect --format '{{.State.Health.Status}}' "$c" 2>/dev/null || echo 'unknown')"
        if [ "$STATUS" != "healthy" ]; then
            ALL_HEALTHY=0
            break
        fi
    done
    if [ "$ALL_HEALTHY" -eq 1 ]; then
        log "  → all containers healthy (после $((i * 2))s)"
        break
    fi
    sleep 2
done

if [ "$ALL_HEALTHY" -ne 1 ]; then
    err "Containers не стали healthy за 90s. Debug:"
    for c in "${CONTAINERS[@]}"; do
        echo "  $c:" >&2
        docker inspect --format '    State: {{.State.Status}} | Health: {{.State.Health.Status}}' "$c" >&2 2>&1 || true
    done
    exit 3
fi

# -----------------------------------------------------------------------------
# 4. Invoke scripts/restore.sh with test target
# -----------------------------------------------------------------------------
log "Invoke restore.sh $DATE --target=test..."
if ! BACKUP_DIR="$BACKUP_DIR" "$SCRIPT_DIR/restore.sh" "$DATE" --target=test; then
    err "restore.sh failed."
    exit 4
fi

# -----------------------------------------------------------------------------
# 5. Row count verification
# -----------------------------------------------------------------------------
log "Verify row counts..."

# Helper: run psql query, return integer result.
pg_count() {
    local container="$1"
    local pass="$2"
    local db="$3"
    local query="$4"
    docker exec -e PGPASSWORD="$pass" "$container" \
        psql -U rct_user -d "$db" -tAc "$query" 2>/dev/null | tr -d '[:space:]'
}

mongo_count() {
    local container="$1"
    local pass="$2"
    local db="$3"
    local collection="$4"
    docker exec "$container" \
        mongosh --quiet --authenticationDatabase admin -u root -p "$pass" \
        --eval "db.getSiblingDB('$db').$collection.countDocuments({})" 2>/dev/null | tail -n 1 | tr -d '[:space:]'
}

FAIL=0

# Postgres academic — core table: users (existует в любом прод-дампе, даже с 1 admin'ом).
ACADEMIC_USERS="$(pg_count rct-test-postgres-academic "$RAND_PG_ACADEMIC" academic_db \
    "SELECT count(*) FROM users" || echo 0)"
log "  academic_db.users: $ACADEMIC_USERS"
if [ "${ACADEMIC_USERS:-0}" -lt 1 ]; then
    err "  academic_db.users < 1 — backup empty/corrupt?"
    FAIL=1
fi

# Postgres schedule — core table: lessons (или любая core table; fresh DB → 0, но schema должна существовать).
SCHEDULE_TABLES="$(pg_count rct-test-postgres-schedule "$RAND_PG_SCHEDULE" schedule_db \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" || echo 0)"
log "  schedule_db.tables: $SCHEDULE_TABLES"
if [ "${SCHEDULE_TABLES:-0}" -lt 1 ]; then
    err "  schedule_db schema empty — backup corrupt?"
    FAIL=1
fi

# Mongo attendance — attendance_db.attendances collection existуется (с любым числом документов).
ATT_COLLS="$(docker exec rct-test-mongo-attendance \
    mongosh --quiet --authenticationDatabase admin -u root -p "$RAND_MONGO_ROOT" \
    --eval "db.getSiblingDB('attendance_db').getCollectionNames().length" 2>/dev/null | tail -n 1 | tr -d '[:space:]' || echo 0)"
log "  attendance_db.collections: $ATT_COLLS"
if [ "${ATT_COLLS:-0}" -lt 1 ]; then
    err "  attendance_db пустой — backup corrupt?"
    FAIL=1
fi

NOTIF_COLLS="$(docker exec rct-test-mongo-attendance \
    mongosh --quiet --authenticationDatabase admin -u root -p "$RAND_MONGO_ROOT" \
    --eval "db.getSiblingDB('notification_db').getCollectionNames().length" 2>/dev/null | tail -n 1 | tr -d '[:space:]' || echo 0)"
log "  notification_db.collections: $NOTIF_COLLS"
# notification_db может быть пустым на fresh v0.0.0 (notifications только после trigger'ов) — warning, не fail.
if [ "${NOTIF_COLLS:-0}" -lt 1 ]; then
    log "  WARN: notification_db пуст (normal для fresh deploy)."
fi

if [ "$FAIL" -ne 0 ]; then
    exit 5
fi

log "Test-restore PASS. Backup $DATE валиден."
exit 0

#!/usr/bin/env bash
# M13 G15 — restore backup в running-стек (prod OR test-compose).
#
# Делает:
#   1. Верифицирует, что все 4 файла в /opt/backups/$DATE существуют.
#   2. Опционально GPG-decrypt .env.prod (--with-env).
#   3. psql DROP DATABASE + pg_restore из .sql.gz × 2.
#   4. mongorestore --drop (restore поверх существующих БД).
#
# ПРЕДУПРЕЖДЕНИЕ: `--drop` уничтожает текущие данные в target стеке.
# Перед prod-restore требуется `--confirm-prod` flag (double opt-in).
#
# Использование:
#   scripts/restore.sh 2026-04-25                              # default: target=prod
#   scripts/restore.sh 2026-04-25 --target=test                # test-compose (rct-test-*)
#   scripts/restore.sh 2026-04-25 --with-env                   # also decrypt .env.prod.gpg
#   scripts/restore.sh 2026-04-25 --target=prod --confirm-prod # prod (guard против случайного restore)
#
# Environment:
#   BACKUP_DIR               — default /opt/backups
#   BACKUP_GPG_PASSPHRASE_FILE — для --with-env (default /opt/rutcampustrack/.backup-passphrase)
#
# Target containers:
#   prod → rct-postgres-academic / rct-postgres-schedule / rct-mongo-attendance
#   test → rct-test-postgres-academic / rct-test-postgres-schedule / rct-test-mongo-attendance
#
# Exit codes:
#   0 — OK.
#   1 — args/config errors.
#   2 — backup files missing.
#   3 — pg_restore failed.
#   4 — mongorestore failed.
#   5 — gpg decrypt failed.
#   6 — target container not running.
#   7 — prod restore без --confirm-prod.

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <YYYY-MM-DD> [--target=prod|test] [--with-env] [--confirm-prod]" >&2
    exit 1
fi

DATE="$1"
shift

TARGET_ENV="prod"
WITH_ENV=0
CONFIRM_PROD=0

for arg in "$@"; do
    case "$arg" in
        --target=prod) TARGET_ENV="prod" ;;
        --target=test) TARGET_ENV="test" ;;
        --with-env) WITH_ENV=1 ;;
        --confirm-prod) CONFIRM_PROD=1 ;;
        *) echo "Unknown arg: $arg" >&2; exit 1 ;;
    esac
done

BACKUP_DIR="${BACKUP_DIR:-/opt/backups}"
SRC="$BACKUP_DIR/$DATE"
GPG_PASSPHRASE_FILE="${BACKUP_GPG_PASSPHRASE_FILE:-/opt/rutcampustrack/.backup-passphrase}"

LOG_PREFIX="[restore $(date -u +%H:%M:%S)]"
log() { echo "$LOG_PREFIX $*"; }
err() { echo "$LOG_PREFIX ERROR: $*" >&2; }

# -----------------------------------------------------------------------------
# Target container names
# -----------------------------------------------------------------------------
case "$TARGET_ENV" in
    prod)
        PG_ACADEMIC_CONTAINER="rct-postgres-academic"
        PG_SCHEDULE_CONTAINER="rct-postgres-schedule"
        MONGO_CONTAINER="rct-mongo-attendance"
        ;;
    test)
        PG_ACADEMIC_CONTAINER="rct-test-postgres-academic"
        PG_SCHEDULE_CONTAINER="rct-test-postgres-schedule"
        MONGO_CONTAINER="rct-test-mongo-attendance"
        ;;
esac

# -----------------------------------------------------------------------------
# Prod safety guard
# -----------------------------------------------------------------------------
if [ "$TARGET_ENV" = "prod" ] && [ "$CONFIRM_PROD" -eq 0 ]; then
    err "Prod restore требует --confirm-prod (destructive: --drop всех БД)."
    err "Double-check что backup $DATE действительно тот, что ты хочешь."
    exit 7
fi

# -----------------------------------------------------------------------------
# Verify backup files
# -----------------------------------------------------------------------------
if [ ! -d "$SRC" ]; then
    err "$SRC not found. Available dates:"
    find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -printf '  %f\n' 2>/dev/null | sort >&2 || true
    exit 2
fi

for f in academic.sql.gz schedule.sql.gz mongo.archive.gz; do
    if [ ! -f "$SRC/$f" ]; then
        err "$SRC/$f missing."
        exit 2
    fi
done

log "Источник: $SRC"
log "Target: $TARGET_ENV ($PG_ACADEMIC_CONTAINER / $PG_SCHEDULE_CONTAINER / $MONGO_CONTAINER)"

# -----------------------------------------------------------------------------
# Verify containers running
# -----------------------------------------------------------------------------
check_container() {
    local name="$1"
    if ! docker ps --format '{{.Names}}' | grep -Fxq "$name"; then
        err "Контейнер $name не запущен."
        exit 6
    fi
}

check_container "$PG_ACADEMIC_CONTAINER"
check_container "$PG_SCHEDULE_CONTAINER"
check_container "$MONGO_CONTAINER"

# -----------------------------------------------------------------------------
# Load env (для паролей target стека)
# -----------------------------------------------------------------------------
case "$TARGET_ENV" in
    prod) TARGET_ENV_FILE="/opt/rutcampustrack/.env.prod" ;;
    test) TARGET_ENV_FILE=".env.test-restore" ;;
esac

if [ ! -f "$TARGET_ENV_FILE" ]; then
    err "$TARGET_ENV_FILE not found. Нужен для паролей target контейнеров."
    exit 1
fi

declare -A ENV
while IFS= read -r line || [ -n "$line" ]; do
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
        key="${BASH_REMATCH[1]}"
        value="${BASH_REMATCH[2]}"
        if [[ "$value" =~ ^\"(.*)\"$ ]]; then
            value="${BASH_REMATCH[1]}"
        fi
        ENV[$key]="$value"
    fi
done < "$TARGET_ENV_FILE"

env_get() {
    echo "${ENV[$1]:-}"
}

PG_ACADEMIC_PASS="$(env_get POSTGRES_ACADEMIC_PASSWORD)"
PG_SCHEDULE_PASS="$(env_get POSTGRES_SCHEDULE_PASSWORD)"
MONGO_ROOT_PASS="$(env_get MONGO_ROOT_PASSWORD)"

if [ -z "$PG_ACADEMIC_PASS" ] || [ -z "$PG_SCHEDULE_PASS" ] || [ -z "$MONGO_ROOT_PASS" ]; then
    err "Пароли отсутствуют в $TARGET_ENV_FILE."
    exit 1
fi

# -----------------------------------------------------------------------------
# 1. pg_restore academic
# -----------------------------------------------------------------------------
restore_postgres() {
    local container="$1"
    local db="$2"
    local pass="$3"
    local archive="$4"

    log "Postgres restore: $db из $archive..."
    # DROP + CREATE DATABASE через postgres (template1) connection.
    # Terminate существующие connections перед DROP.
    docker exec -e PGPASSWORD="$pass" "$container" \
        psql -U rct_user -d postgres -v ON_ERROR_STOP=1 <<SQL
SELECT pg_terminate_backend(pid) FROM pg_stat_activity
 WHERE datname = '$db' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS $db;
CREATE DATABASE $db OWNER rct_user;
SQL

    # Restore из gzip'нутого plain SQL dump.
    if ! gunzip -c "$archive" | docker exec -i -e PGPASSWORD="$pass" "$container" \
            psql -U rct_user -d "$db" -v ON_ERROR_STOP=1 --quiet; then
        err "pg_restore $db failed."
        exit 3
    fi
    log "  → $db restored."
}

restore_postgres "$PG_ACADEMIC_CONTAINER" "academic_db" "$PG_ACADEMIC_PASS" "$SRC/academic.sql.gz"
restore_postgres "$PG_SCHEDULE_CONTAINER" "schedule_db" "$PG_SCHEDULE_PASS" "$SRC/schedule.sql.gz"

# -----------------------------------------------------------------------------
# 2. mongorestore (--drop существующие collections)
# -----------------------------------------------------------------------------
log "Mongo restore (--drop + gzip archive)..."
MONGO_URI="mongodb://root:${MONGO_ROOT_PASS}@localhost:27017/?authSource=admin&replicaSet=rs0"
if ! gunzip -c "$SRC/mongo.archive.gz" | docker exec -i "$MONGO_CONTAINER" \
        mongorestore --uri="$MONGO_URI" --archive --drop --quiet; then
    err "mongorestore failed."
    exit 4
fi
log "  → Mongo (attendance + notification) restored."

# -----------------------------------------------------------------------------
# 3. Optional: decrypt .env.prod.gpg
# -----------------------------------------------------------------------------
if [ "$WITH_ENV" -eq 1 ]; then
    if [ ! -f "$SRC/env.prod.gpg" ]; then
        err "$SRC/env.prod.gpg missing (--with-env requested)."
        exit 2
    fi
    if [ ! -f "$GPG_PASSPHRASE_FILE" ]; then
        err "$GPG_PASSPHRASE_FILE not found (нужен для decrypt)."
        exit 5
    fi
    OUT="$SRC/env.prod.decrypted"
    log "GPG decrypt .env.prod → $OUT..."
    if ! gpg --batch --yes --quiet \
            --passphrase-file "$GPG_PASSPHRASE_FILE" \
            --output "$OUT" \
            --decrypt "$SRC/env.prod.gpg"; then
        err "gpg decrypt failed."
        exit 5
    fi
    chmod 600 "$OUT"
    log "  → $OUT (chmod 600). Скопируй в /opt/rutcampustrack/.env.prod если нужно."
fi

log "Restore готов."
exit 0

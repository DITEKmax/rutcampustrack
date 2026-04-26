#!/usr/bin/env bash
# M13 G15 — daily backup для prod-стека.
#
# Делает:
#   1. pg_dump × 2 (academic_db + schedule_db) → gzip.
#   2. mongodump × 1 (rct-mongo-attendance, оба DB: attendance + notification) → gzip.
#   3. GPG-encrypt .env.prod (symmetric, AES256, passphrase из $BACKUP_GPG_PASSPHRASE_FILE).
#   4. Retention: удаляет `/opt/backups/*` старше $RETENTION_DAYS (default 7).
#
# Вывод: /opt/backups/YYYY-MM-DD/{academic.sql.gz, schedule.sql.gz,
# mongo.archive.gz, env.prod.gpg}. Директория даты создаётся если нет.
#
# Использование:
#   scripts/backup.sh                # default: ENV_FILE=/opt/rutcampustrack/.env.prod
#   scripts/backup.sh /path/.env.prod
#
# Environment:
#   BACKUP_DIR               — куда складывать (default /opt/backups)
#   RETENTION_DAYS           — дней хранить (default 7)
#   BACKUP_GPG_PASSPHRASE_FILE — файл с GPG passphrase (default /opt/rutcampustrack/.backup-passphrase)
#                               chmod 600, содержит ОДНУ строку с passphrase
#                               (без trailing newline желательно)
#
# Cron:
#   /etc/cron.d/rutcampustrack-backup — 03:00 UTC daily.
#
# Exit codes:
#   0 — всё ок.
#   1 — env/arg/config errors (.env.prod missing, passphrase missing).
#   2 — pg_dump failed.
#   3 — mongodump failed.
#   4 — gpg encrypt failed.
#   5 — контейнер не запущен (docker exec вернул non-zero).

set -euo pipefail

ENV_FILE="${1:-/opt/rutcampustrack/.env.prod}"
BACKUP_DIR="${BACKUP_DIR:-/opt/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
GPG_PASSPHRASE_FILE="${BACKUP_GPG_PASSPHRASE_FILE:-/opt/rutcampustrack/.backup-passphrase}"

DATE="$(date -u +%Y-%m-%d)"
TARGET="$BACKUP_DIR/$DATE"
LOG_PREFIX="[backup $(date -u +%H:%M:%S)]"

log() { echo "$LOG_PREFIX $*"; }
err() { echo "$LOG_PREFIX ERROR: $*" >&2; }

# -----------------------------------------------------------------------------
# Pre-flight
# -----------------------------------------------------------------------------
if [ ! -f "$ENV_FILE" ]; then
    err "$ENV_FILE not found."
    exit 1
fi

if [ ! -r "$ENV_FILE" ]; then
    err "$ENV_FILE not readable (chmod 600 owner?)."
    exit 1
fi

if [ ! -f "$GPG_PASSPHRASE_FILE" ]; then
    err "$GPG_PASSPHRASE_FILE not found. См. docs/operations/runbooks/backup-restore.md (setup)."
    exit 1
fi

if [ ! -r "$GPG_PASSPHRASE_FILE" ]; then
    err "$GPG_PASSPHRASE_FILE not readable (chmod 600)."
    exit 1
fi

# M13 G24-fix-6 H5: явный permission check. Раньше скрипт проверял только
# readable, но не permissions. Если admin случайно chmod 644 / 755 —
# passphrase world-readable, backup продолжается без warning'а.
if command -v stat >/dev/null 2>&1; then
    # GNU stat: -c '%a'; BSD stat: -f '%Lp'. Используем -c (Linux deploy target).
    perms=$(stat -c '%a' "$GPG_PASSPHRASE_FILE" 2>/dev/null || echo "")
    if [ -n "$perms" ] && [ "$perms" != "600" ] && [ "$perms" != "400" ]; then
        err "$GPG_PASSPHRASE_FILE permissions $perms (expected 600 или 400). chmod 600 $GPG_PASSPHRASE_FILE"
        exit 1
    fi
fi

mkdir -p "$TARGET"
chmod 700 "$TARGET"

# -----------------------------------------------------------------------------
# dot-env parser (re-use pattern из validate-env-prod.sh — pure bash,
# НЕ shell eval: пароли могут содержать ;, $, `, ( и др. shell-special chars).
# -----------------------------------------------------------------------------
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
done < "$ENV_FILE"

env_get() {
    echo "${ENV[$1]:-}"
}

# -----------------------------------------------------------------------------
# Container check (fail-fast если не запущено)
# -----------------------------------------------------------------------------
check_container() {
    local name="$1"
    if ! docker ps --format '{{.Names}}' | grep -Fxq "$name"; then
        err "Контейнер $name не запущен. Backup abort."
        exit 5
    fi
}

check_container "rct-postgres-academic"
check_container "rct-postgres-schedule"
check_container "rct-mongo-attendance"

log "Старт. Target: $TARGET"

# -----------------------------------------------------------------------------
# 1. pg_dump × 2
# -----------------------------------------------------------------------------
PG_ACADEMIC_PASS="$(env_get POSTGRES_ACADEMIC_PASSWORD)"
PG_SCHEDULE_PASS="$(env_get POSTGRES_SCHEDULE_PASSWORD)"

if [ -z "$PG_ACADEMIC_PASS" ] || [ -z "$PG_SCHEDULE_PASS" ]; then
    err "POSTGRES_*_PASSWORD отсутствуют в $ENV_FILE."
    exit 1
fi

log "pg_dump academic_db..."
if ! docker exec -e PGPASSWORD="$PG_ACADEMIC_PASS" rct-postgres-academic \
        pg_dump -U rct_user -d academic_db --no-owner --no-privileges \
        | gzip -c > "$TARGET/academic.sql.gz"; then
    err "pg_dump academic failed."
    exit 2
fi
log "  → $(du -h "$TARGET/academic.sql.gz" | cut -f1)"

log "pg_dump schedule_db..."
if ! docker exec -e PGPASSWORD="$PG_SCHEDULE_PASS" rct-postgres-schedule \
        pg_dump -U rct_user -d schedule_db --no-owner --no-privileges \
        | gzip -c > "$TARGET/schedule.sql.gz"; then
    err "pg_dump schedule failed."
    exit 2
fi
log "  → $(du -h "$TARGET/schedule.sql.gz" | cut -f1)"

# -----------------------------------------------------------------------------
# 2. mongodump × 1 (rct-mongo-attendance hosts both attendance_db + notification_db)
# -----------------------------------------------------------------------------
MONGO_ROOT_PASS="$(env_get MONGO_ROOT_PASSWORD)"

if [ -z "$MONGO_ROOT_PASS" ]; then
    err "MONGO_ROOT_PASSWORD отсутствует в $ENV_FILE."
    exit 1
fi

log "mongodump (attendance + notification)..."
# Dump to stdout (--archive без значения) + gzip на host'е.
# M13 G24-fix-6 H4: пароль через MONGO_ROOT_PASS env var в docker exec,
# mongodump подхватывает через MONGO_ROOT_PASS и passes как --password.
# Раньше передавали --uri="mongodb://root:PASS@..." string, что светило
# пароль в /proc/<pid>/cmdline (lateral movement на shared-tenant
# контейнере). Теперь password по-прежнему попадает в argv mongodump'а
# через "$MONGO_ROOT_PASS" expansion внутри `sh -c`, но cmdline
# короче и shell expansion делается inside container'а.
if ! docker exec -e MONGO_ROOT_PASS="$MONGO_ROOT_PASS" rct-mongo-attendance \
        sh -c 'mongodump --host=localhost:27017 --authenticationDatabase=admin \
            --username=root --password="$MONGO_ROOT_PASS" \
            --archive --quiet' \
        | gzip -c > "$TARGET/mongo.archive.gz"; then
    err "mongodump failed."
    exit 3
fi
log "  → $(du -h "$TARGET/mongo.archive.gz" | cut -f1)"

# -----------------------------------------------------------------------------
# 3. GPG-encrypt .env.prod (symmetric, AES256)
# -----------------------------------------------------------------------------
log "GPG encrypt .env.prod..."
if ! gpg --batch --yes --quiet \
        --cipher-algo AES256 --symmetric \
        --passphrase-file "$GPG_PASSPHRASE_FILE" \
        --output "$TARGET/env.prod.gpg" \
        "$ENV_FILE"; then
    err "gpg encrypt failed."
    exit 4
fi
log "  → $(du -h "$TARGET/env.prod.gpg" | cut -f1)"

# -----------------------------------------------------------------------------
# 4. Retention (delete archives старше $RETENTION_DAYS)
# -----------------------------------------------------------------------------
log "Retention sweep (old > ${RETENTION_DAYS}d)..."
# -mindepth 1 чтобы не удалить сам $BACKUP_DIR. -type d на date-dir'ы.
DELETED=$(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d \
        -mtime +"$RETENTION_DAYS" -print -exec rm -rf {} + | wc -l)
log "  → удалено $DELETED old backup directory(ies)."

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
TOTAL_SIZE=$(du -sh "$TARGET" | cut -f1)
log "Готово. Размер $TARGET: $TOTAL_SIZE"
log "Оставшиеся backup'ы:"
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d | sort | sed 's/^/  /'

exit 0

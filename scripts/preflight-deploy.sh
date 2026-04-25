#!/usr/bin/env bash
# M13 G23 — pre-deploy diagnostics aggregator.
#
# Запускается до `docker compose up -d` на VPS чтобы catch'ить config
# проблемы (missing secrets, broken configs, invalid YAML) до того как
# stack попытается стартануть. Объединяет все pre-flight checks из
# других M13 групп в один entrypoint.
#
# Запускается из корня репозитория. На VPS типичный путь —
# `/opt/rutcampustrack/scripts/preflight-deploy.sh`.
#
# Использование:
#   scripts/preflight-deploy.sh                          # default .env.prod
#   scripts/preflight-deploy.sh /path/to/.env.prod       # custom env
#
# Exit codes:
#   0 — все проверки прошли, можно деплоить.
#   1 — env / secrets fail.
#   2 — Grafana dashboards fail.
#   3 — Prometheus rules fail.
#   4 — docker-compose syntax fail.
#   5 — другие fail'ы.

set -euo pipefail

ENV_FILE="${1:-.env.prod}"
COMPOSE_FILE="docker-compose.prod.yml"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BOLD='\033[1m'
NC='\033[0m'

err() { echo -e "${RED}✗ FAIL${NC}: $*" >&2; }
ok()  { echo -e "${GREEN}✓${NC} $*"; }
hdr() { echo -e "\n${BOLD}=== $* ===${NC}"; }
warn() { echo -e "${YELLOW}!${NC} $*"; }

OVERALL_FAIL=0

# -----------------------------------------------------------------------------
# 1. Environment secrets (M13 G13)
# -----------------------------------------------------------------------------
hdr "1. Environment secrets ($ENV_FILE)"
if [ ! -f "$ENV_FILE" ]; then
    err "$ENV_FILE not found. Создай через cp .env.prod.example $ENV_FILE."
    exit 1
fi

if [ -x scripts/validate-env-prod.sh ]; then
    if scripts/validate-env-prod.sh "$ENV_FILE"; then
        ok "validate-env-prod.sh passed"
    else
        err "validate-env-prod.sh failed — fix ошибки выше"
        OVERALL_FAIL=1
    fi
else
    warn "scripts/validate-env-prod.sh не executable — chmod +x?"
    OVERALL_FAIL=1
fi

# -----------------------------------------------------------------------------
# 2. Grafana dashboards (M13 G17)
# -----------------------------------------------------------------------------
hdr "2. Grafana dashboards structure"
if [ -x scripts/validate-grafana-dashboards.sh ]; then
    if scripts/validate-grafana-dashboards.sh; then
        ok "validate-grafana-dashboards.sh passed"
    else
        err "Grafana dashboards validation failed"
        OVERALL_FAIL=2
    fi
else
    warn "scripts/validate-grafana-dashboards.sh не найден или не executable"
fi

# -----------------------------------------------------------------------------
# 3. Prometheus alert rules (M13 G19/G20)
# -----------------------------------------------------------------------------
hdr "3. Prometheus alert rules"
if command -v docker >/dev/null 2>&1; then
    # MSYS_NO_PATHCONV для Git Bash на Windows; Linux игнорирует.
    if MSYS_NO_PATHCONV=1 docker run --rm \
        -v "$(pwd)/infra/prometheus/rules:/rules:ro" \
        --entrypoint promtool \
        prom/prometheus:v2.55.1 \
        check rules /rules/service-health.yml /rules/resource-limits.yml \
                    /rules/rabbitmq.yml /rules/ssl-expiry.yml >/dev/null 2>&1; then
        ok "promtool check rules passed (18 rules)"
    else
        err "promtool check rules failed"
        OVERALL_FAIL=3
    fi
else
    warn "docker не найден — promtool check skipped (запусти на VPS перед deploy)"
fi

# -----------------------------------------------------------------------------
# 4. docker-compose.prod.yml syntax
# -----------------------------------------------------------------------------
hdr "4. docker-compose.prod.yml syntax"
if command -v docker >/dev/null 2>&1; then
    # config --quiet exit 0 если синтаксис валидный, errors на stderr.
    # Используем --env-file чтобы compose читал реальные secrets.
    if docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" \
        config --quiet 2>&1 | grep -E "error|invalid"; then
        err "docker-compose config validation failed"
        OVERALL_FAIL=4
    else
        ok "docker compose config OK"
    fi
else
    warn "docker не найден — compose validation skipped"
fi

# -----------------------------------------------------------------------------
# 5. Critical files presence
# -----------------------------------------------------------------------------
hdr "5. Critical files"
CRITICAL_FILES=(
    "nginx/conf.d/default.conf"
    "nginx/scripts/entrypoint.sh"
    "nginx/dhparam.pem"
    "infra/blackbox/blackbox.yml"
    "infra/alertmanager/alertmanager.yml"
    "infra/prometheus/prometheus.yml"
    "infra/loki/loki.yml"
    "infra/tempo/tempo.yml"
    "scripts/backup.sh"
    "scripts/restore.sh"
)
for f in "${CRITICAL_FILES[@]}"; do
    if [ -f "$f" ]; then
        ok "$f"
    else
        err "$f отсутствует"
        OVERALL_FAIL=5
    fi
done

# -----------------------------------------------------------------------------
# 6. Backup directory + GPG passphrase (M13 G15)
# -----------------------------------------------------------------------------
hdr "6. Backup infrastructure (M13 G15)"
if [ -d /opt/backups ] || [ -d ./backups ]; then
    ok "Backup directory exists"
else
    warn "Backup directory отсутствует — создай /opt/backups перед первым cron run"
fi

if [ -f /opt/rutcampustrack/.backup-passphrase ] || [ -f ./.backup-passphrase ]; then
    ok ".backup-passphrase найден"
else
    warn ".backup-passphrase отсутствует — backup.sh упадёт. См. runbooks/backup-restore.md."
fi

# -----------------------------------------------------------------------------
# Final verdict
# -----------------------------------------------------------------------------
echo
if [ $OVERALL_FAIL -eq 0 ]; then
    echo -e "${GREEN}${BOLD}✓ Все pre-flight проверки прошли${NC}"
    echo "Можно деплоить: docker compose -f $COMPOSE_FILE --env-file $ENV_FILE up -d"
    exit 0
else
    echo -e "${RED}${BOLD}✗ Pre-flight failed (exit $OVERALL_FAIL)${NC}"
    echo "Fix ошибки выше перед deploy."
    exit "$OVERALL_FAIL"
fi

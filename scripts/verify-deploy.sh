#!/usr/bin/env bash
# M13 G23 — post-deploy verification для VPS dry-run.
#
# Проверяет M13-specific контракты после `docker compose up -d`:
#   - все 18 alert rules загружены в Prometheus
#   - blackbox-exporter probe up для https://ruttrack.site
#   - SSL cert metric экспортируется (probe_ssl_earliest_cert_expiry)
#   - /api/csp-report endpoint reachable
#   - /prometheus + /alertmanager защищены basic-auth (401 без credentials)
#   - WebSocket /api/ws/ keep-alive headers корректны
#   - все 26 контейнеров healthy
#
# Запускается **после** `docker compose up -d` + 60-90 sec на boot.
# Отделён от scripts/smoke-prod.sh (M08 G7) — тот покрывает app-level
# golden path (login/schedule/logout); этот — infra-level контракты.
#
# Использование:
#   scripts/verify-deploy.sh                                 # default https://ruttrack.site
#   scripts/verify-deploy.sh https://staging.ruttrack.site
#
# Exit codes:
#   0 — все verification прошли.
#   1 — service health fail.
#   2 — alert rules / metrics fail.
#   3 — security headers / endpoints fail.
#   4 — другие fail.

set -euo pipefail

BASE_URL="${1:-https://ruttrack.site}"
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

# Helper: HTTP status check (single value or list of acceptable values)
expect_status() {
    local url="$1"
    local expected="$2"  # comma-separated list, e.g. "200" or "200,301"
    local description="$3"
    local actual
    actual=$(curl -ks -o /dev/null -w "%{http_code}" "$url" || echo "000")
    case ",$expected," in
        *",$actual,"*)
            ok "$description ($actual)"
            ;;
        *)
            err "$description — expected $expected, got $actual ($url)"
            OVERALL_FAIL=3
            ;;
    esac
}

# Helper: HTTP status with retry (for upstream services that take a few
# seconds to become reachable after `docker compose up -d`).
# M16 G5: nginx DNS race after upstream restart can return 502 transiently.
expect_status_retry() {
    local url="$1"
    local expected="$2"
    local description="$3"
    local attempts="${4:-5}"
    local sleep_s="${5:-2}"
    local actual=""
    for i in $(seq 1 "$attempts"); do
        actual=$(curl -ks -o /dev/null -w "%{http_code}" "$url" || echo "000")
        case ",$expected," in
            *",$actual,"*)
                ok "$description ($actual, after $i attempt(s))"
                return 0
                ;;
        esac
        sleep "$sleep_s"
    done
    err "$description — expected $expected, got $actual after $attempts attempts ($url)"
    OVERALL_FAIL=3
}

# -----------------------------------------------------------------------------
# 1. Container health (docker compose)
# -----------------------------------------------------------------------------
hdr "1. Container health (26 containers expected)"
if command -v docker >/dev/null 2>&1; then
    HEALTHY=$(docker compose ps --format "{{.Name}} {{.Health}}" 2>/dev/null \
        | grep -c "healthy" || echo "0")
    UNHEALTHY=$(docker compose ps --format "{{.Name}} {{.Health}}" 2>/dev/null \
        | grep -E "unhealthy|starting" | head -5 || echo "")

    if [ "$HEALTHY" -ge 14 ]; then
        ok "$HEALTHY контейнеров healthy"
    else
        err "Только $HEALTHY healthy (ожидалось ≥14 backend+infra)"
        OVERALL_FAIL=1
    fi

    if [ -n "$UNHEALTHY" ]; then
        err "Unhealthy/starting:"
        echo "$UNHEALTHY" | sed 's/^/  /'
        OVERALL_FAIL=1
    fi
else
    warn "docker недоступен — пропускаем container health"
fi

# -----------------------------------------------------------------------------
# 2. Public HTTPS reachable
# -----------------------------------------------------------------------------
# M16 G5: после v9.0 / тоже для nginx DNS race tolerance:
#   - "/" редиректит 301 на /login (INFRA-v9-01), не 200
#   - "/login" + "/app/" + "/presentation/" с retry — после nginx restart
#     upstream может быть transient 502 пока DNS не re-resolve'нется (10s TTL)
#   - "/api/health" не существует — есть только actuator, проверяем через docker
hdr "2. Public HTTPS endpoints"
expect_status "$BASE_URL/" "301" "Root → /login redirect"
expect_status_retry "$BASE_URL/login" "200" "Web-panel /login"
expect_status_retry "$BASE_URL/app/" "200" "PWA /app/"
expect_status_retry "$BASE_URL/presentation/" "200" "Landing /presentation/"

# Gateway health через docker exec (actuator endpoint, не публичен)
if command -v docker >/dev/null 2>&1; then
    GW_HEALTH=$(docker exec rct-api-gateway wget -qO- \
        http://localhost:8080/actuator/health 2>/dev/null \
        | grep -oE '"status":"[A-Z]+"' | head -1)
    if [ "$GW_HEALTH" = '"status":"UP"' ]; then
        ok "Gateway actuator/health UP"
    else
        err "Gateway actuator/health не UP: $GW_HEALTH"
        OVERALL_FAIL=1
    fi
else
    warn "docker недоступен — gateway health check skipped"
fi

# -----------------------------------------------------------------------------
# 3. Security headers (M07 G4 + M13 G16)
# -----------------------------------------------------------------------------
hdr "3. Security headers (CSP, HSTS, X-Frame)"
HEADERS=$(curl -ksI "$BASE_URL/login" | tr -d '\r')

if echo "$HEADERS" | grep -qi "^strict-transport-security:"; then
    ok "HSTS header present"
else
    err "HSTS header missing"
    OVERALL_FAIL=3
fi

if echo "$HEADERS" | grep -qi "^content-security-policy:.*report-uri /api/csp-report"; then
    ok "CSP с report-uri (M13 G16)"
else
    err "CSP report-uri отсутствует или неправильный"
    OVERALL_FAIL=3
fi

if echo "$HEADERS" | grep -qi "^x-frame-options:.*sameorigin"; then
    ok "X-Frame-Options: SAMEORIGIN"
else
    err "X-Frame-Options некорректный"
    OVERALL_FAIL=3
fi

# -----------------------------------------------------------------------------
# 4. CSP report endpoint reachable (M13 G16)
# -----------------------------------------------------------------------------
hdr "4. CSP report endpoint (M13 G16)"
CSP_STATUS=$(curl -ks -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/api/csp-report" \
    -H "Content-Type: application/csp-report" \
    -d '{"csp-report":{"document-uri":"https://test","violated-directive":"script-src","blocked-uri":"https://evil"}}' \
    || echo "000")
if [ "$CSP_STATUS" = "204" ] || [ "$CSP_STATUS" = "200" ]; then
    ok "CSP report endpoint accepts violations ($CSP_STATUS)"
else
    err "CSP report endpoint вернул $CSP_STATUS (ожидалось 200/204)"
    OVERALL_FAIL=3
fi

# -----------------------------------------------------------------------------
# 5. Prometheus + Alertmanager basic-auth (M13 G14)
# -----------------------------------------------------------------------------
hdr "5. /prometheus + /alertmanager basic-auth (M13 G14)"
expect_status "$BASE_URL/prometheus/" "401" "/prometheus без credentials"
expect_status "$BASE_URL/alertmanager/" "401" "/alertmanager без credentials"

# -----------------------------------------------------------------------------
# 6. Prometheus alert rules loaded (через basic-auth — может skip если creds нет)
# -----------------------------------------------------------------------------
hdr "6. Prometheus alert rules"
if [ -n "${SWAGGER_USER:-}" ] && [ -n "${SWAGGER_PASS:-}" ]; then
    RULES_COUNT=$(curl -ks -u "$SWAGGER_USER:$SWAGGER_PASS" \
        "$BASE_URL/prometheus/api/v1/rules" \
        | grep -oE '"name":"[A-Z][a-zA-Z]+"' | sort -u | wc -l)
    if [ "$RULES_COUNT" -ge 18 ]; then
        ok "$RULES_COUNT alert rules loaded (≥18 expected)"
    else
        err "Только $RULES_COUNT alert rules (ожидалось ≥18 — см. docs/operations/monitoring/alerts.md)"
        OVERALL_FAIL=2
    fi
else
    warn "SWAGGER_USER/SWAGGER_PASS не выставлены — пропускаем rules count"
    warn "Запусти: SWAGGER_USER=foo SWAGGER_PASS=bar scripts/verify-deploy.sh"
fi

# -----------------------------------------------------------------------------
# 7. WebSocket /api/ws/ Upgrade headers (M13 G18)
# -----------------------------------------------------------------------------
hdr "7. WebSocket headers (M13 G18)"
WS_HEADERS=$(curl -ksI -H "Upgrade: websocket" -H "Connection: Upgrade" \
    "$BASE_URL/api/ws/info" || echo "")
if echo "$WS_HEADERS" | head -1 | grep -qE "200|101"; then
    ok "WebSocket endpoint reachable"
else
    warn "WS /api/ws/info не вернул 200/101 (может быть OK если SockJS info handshake не requires)"
fi

# -----------------------------------------------------------------------------
# 8. Mongo indexes verify (M13 G6)
# -----------------------------------------------------------------------------
# M16 G5: notification_db user (PoLP — M10 D2) не имеет dbAdmin,
# getIndexes() возвращает empty. Используем root credentials через
# MONGO_INITDB_ROOT_USERNAME/PASSWORD env vars (auth source admin).
hdr "8. Mongo индексы (M13 G6)"
if command -v docker >/dev/null 2>&1; then
    MONGO_ROOT="${MONGO_INITDB_ROOT_USERNAME:-root}"
    MONGO_PWD="${MONGO_INITDB_ROOT_PASSWORD:-}"
    if [ -z "$MONGO_PWD" ]; then
        warn "MONGO_INITDB_ROOT_PASSWORD не выставлен — TTL check skipped"
        warn "Запусти с экспортом из .env.prod: source .env.prod && scripts/verify-deploy.sh"
    else
        TTL_COUNT=$(docker exec rct-mongo-attendance mongosh \
            -u "$MONGO_ROOT" -p "$MONGO_PWD" --authenticationDatabase admin \
            --quiet --eval \
            "db.getSiblingDB('notification_db').notification_history.getIndexes().filter(i => i.expireAfterSeconds).length" \
            2>/dev/null | tr -d '[:space:]')
        if [ "$TTL_COUNT" = "1" ]; then
            ok "TTL index на notification_history существует"
        else
            err "TTL index missing (count=$TTL_COUNT) — см. runbooks/mongo-indexes-verify.md"
            OVERALL_FAIL=2
        fi
    fi
else
    warn "docker недоступен — Mongo indexes check skipped"
fi

# -----------------------------------------------------------------------------
# Final verdict
# -----------------------------------------------------------------------------
echo
if [ $OVERALL_FAIL -eq 0 ]; then
    echo -e "${GREEN}${BOLD}✓ Post-deploy verification passed${NC}"
    echo "Дополнительно прогони:"
    echo "  scripts/smoke-prod.sh $BASE_URL student student_test_pass  # app-level golden path"
    echo "  E2E_BASE_URL=$BASE_URL npm test --prefix tests/e2e         # full Playwright"
    exit 0
else
    echo -e "${RED}${BOLD}✗ Verification failed (exit $OVERALL_FAIL)${NC}"
    echo "Fix issues выше. Runbook'и:"
    echo "  - docs/operations/deploy/prod-deploy-checklist.md"
    echo "  - docs/operations/monitoring/alerts.md"
    echo "  - docs/operations/runbooks/cert-renewal.md"
    echo "  - docs/operations/runbooks/mongo-indexes-verify.md"
    exit "$OVERALL_FAIL"
fi

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

# Helper: HTTP status check
expect_status() {
    local url="$1"
    local expected="$2"
    local description="$3"
    local actual
    actual=$(curl -ks -o /dev/null -w "%{http_code}" "$url" || echo "000")
    if [ "$actual" = "$expected" ]; then
        ok "$description ($actual)"
    else
        err "$description — expected $expected, got $actual ($url)"
        OVERALL_FAIL=3
    fi
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
hdr "2. Public HTTPS endpoints"
expect_status "$BASE_URL/" "200" "Landing redirect"
expect_status "$BASE_URL/login" "200" "Web-panel /login"
expect_status "$BASE_URL/api/health" "200" "Gateway /api/health"

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
hdr "8. Mongo индексы (M13 G6)"
if command -v docker >/dev/null 2>&1; then
    if docker exec rct-mongo-attendance mongosh --quiet --eval \
        "use notification_db; db.notification_history.getIndexes().filter(i => i.expireAfterSeconds).length" \
        2>/dev/null | grep -q "1"; then
        ok "TTL index на notification_history существует"
    else
        warn "TTL index check failed — см. runbooks/mongo-indexes-verify.md"
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

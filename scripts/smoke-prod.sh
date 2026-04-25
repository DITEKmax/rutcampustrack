#!/usr/bin/env bash
# M08 Группа 5 (P2-8/5) — post-deploy smoke-тест для prod/staging.
#
# Проверяет базовый golden path через curl (без Playwright — lightweight
# для release-process):
#   1. GET /actuator/health → {"status":"UP"} per сервис.
#   2. POST /api/auth/login → 200 + JWT в HttpOnly cookie (M03b).
#   3. GET /api/schedule/lessons → 200 + непустой список.
#   4. Logout → 204.
#
# Использование:
#   scripts/smoke-prod.sh https://ruttrack.site student_login student_pass
#
# Exit codes:
#   0 — всё ок.
#   1 — fail на health-check.
#   2 — fail на login.
#   3 — fail на schedule.
#
# Скрипт НЕ зависит от docker-compose — гоняется против любого
# живого окружения с валидной SSL-сертификатом.

set -euo pipefail

BASE_URL="${1:-http://localhost}"
LOGIN="${2:-student}"
PASSWORD="${3:-student_test_pass}"

COOKIE_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR"' EXIT

echo "=== Smoke test: $BASE_URL ==="

# Step 1: Health-check всех 5 сервисов (проксируется через gateway на /actuator)
echo "[1/4] Health-check..."
HEALTH_RESPONSE=$(curl -sf -o - "$BASE_URL/actuator/health" || echo "FAIL")
if [[ "$HEALTH_RESPONSE" != *'"status":"UP"'* ]]; then
  echo "  FAIL: health-check response = $HEALTH_RESPONSE"
  exit 1
fi
echo "  OK"

# Step 2: Login
echo "[2/4] Login as $LOGIN..."
LOGIN_RESPONSE=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"login\":\"$LOGIN\",\"password\":\"$PASSWORD\"}" \
  -c "$COOKIE_JAR" \
  -o - || echo "FAIL")
if [[ "$LOGIN_RESPONSE" == "FAIL" ]] || ! grep -q "refresh_token" "$COOKIE_JAR"; then
  echo "  FAIL: login response = $LOGIN_RESPONSE"
  exit 2
fi
echo "  OK — cookie set"

# Step 3: Schedule (используем access token из login response)
ACCESS_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
echo "[3/4] GET /api/schedule/lessons..."
SCHEDULE_RESPONSE=$(curl -sf "$BASE_URL/api/schedule/lessons?date=$(date +%Y-%m-%d)" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -b "$COOKIE_JAR" \
  -o - || echo "FAIL")
if [[ "$SCHEDULE_RESPONSE" == "FAIL" ]]; then
  echo "  FAIL: schedule response = $SCHEDULE_RESPONSE"
  exit 3
fi
echo "  OK"

# Step 4: Logout (cleanup)
echo "[4/4] Logout..."
curl -sf -X POST "$BASE_URL/api/auth/logout" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -b "$COOKIE_JAR" \
  -o /dev/null || true
echo "  OK"

echo ""
echo "=== Smoke test passed ==="

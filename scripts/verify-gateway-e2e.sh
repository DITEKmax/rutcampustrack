#!/usr/bin/env bash
#
# Gateway E2E verification script
# Verifies auth flow works end-to-end through API Gateway routing.
#
# Prerequisites:
#   docker compose up -d
#   ./gradlew.bat :services:auth-service:bootRun
#   AUTH_SERVICE_URL=http://localhost:9090 ./gradlew.bat :services:api-gateway:bootRun

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
PASSED=0
FAILED=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "  ${GREEN}PASS${NC}: $1"; PASSED=$((PASSED + 1)); }
fail() { echo -e "  ${RED}FAIL${NC}: $1 — $2"; FAILED=$((FAILED + 1)); }

echo ""
echo "=== Gateway E2E Verification ==="
echo "Gateway: $GATEWAY_URL"
echo ""

# --- Prerequisite check ---
echo "Checking prerequisites..."
if ! curl -s --max-time 3 "$GATEWAY_URL" > /dev/null 2>&1; then
    echo -e "${YELLOW}Gateway is not reachable at $GATEWAY_URL${NC}"
    echo ""
    echo "Start the services first:"
    echo "  docker compose up -d"
    echo "  ./gradlew.bat :services:auth-service:bootRun"
    echo "  AUTH_SERVICE_URL=http://localhost:9090 ./gradlew.bat :services:api-gateway:bootRun"
    exit 1
fi
echo "Gateway reachable."
echo ""

# --- Test 1: Login through Gateway ---
echo "Test 1: Login through Gateway (POST /api/auth/login)"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$GATEWAY_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"login":"student","password":"password"}')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    TOKEN=$(echo "$BODY" | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ -n "$TOKEN" ]; then
        pass "Login returned 200 with accessToken"
    else
        fail "Login returned 200 but no accessToken in response" "body: $BODY"
    fi
else
    fail "Login through Gateway" "expected 200, got $HTTP_CODE"
fi

# --- Test 2: Public-key through Gateway ---
echo "Test 2: Public-key through Gateway (GET /api/auth/public-key)"
RESPONSE=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/auth/public-key")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    if echo "$BODY" | grep -q "BEGIN PUBLIC KEY"; then
        pass "Public-key returned 200 with PEM key"
    else
        fail "Public-key returned 200 but no PEM content" "body: $BODY"
    fi
else
    fail "Public-key through Gateway" "expected 200, got $HTTP_CODE"
fi

# --- Test 3: Protected route WITH JWT ---
echo "Test 3: Protected route with JWT (GET /api/academic/groups)"
if [ -n "$TOKEN" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer $TOKEN" \
        "$GATEWAY_URL/api/academic/groups")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)

    if [ "$HTTP_CODE" != "401" ]; then
        pass "Protected route with valid JWT did not return 401 (got $HTTP_CODE — JWT accepted)"
    else
        fail "Protected route with valid JWT" "got 401 — JWT filter rejected valid token"
    fi
else
    fail "Protected route with JWT" "skipped — no token from Test 1"
fi

# --- Test 4: Protected route WITHOUT JWT ---
echo "Test 4: Protected route without JWT (GET /api/academic/groups)"
RESPONSE=$(curl -s -w "\n%{http_code}" "$GATEWAY_URL/api/academic/groups")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)

if [ "$HTTP_CODE" = "401" ]; then
    pass "Unauthenticated request returned 401"
else
    fail "Unauthenticated request" "expected 401, got $HTTP_CODE"
fi

# --- Summary ---
echo ""
echo "=== Summary ==="
TOTAL=$((PASSED + FAILED))
echo -e "  Total: $TOTAL  ${GREEN}Passed: $PASSED${NC}  ${RED}Failed: $FAILED${NC}"
echo ""

if [ "$FAILED" -gt 0 ]; then
    echo -e "${RED}SOME TESTS FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}ALL TESTS PASSED${NC}"
    exit 0
fi

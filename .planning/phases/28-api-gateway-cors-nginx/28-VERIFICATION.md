---
phase: 28-api-gateway-cors-nginx
verified: 2026-04-06T00:14:00Z
status: human_needed
score: 3/3 must-haves verified
gaps: []
human_verification:
  - test: "Start docker compose up pwa-nginx, open http://localhost:80 in browser, verify page loads"
    expected: "RutTrack PWA placeholder page displays with 200 status"
    why_human: "Requires running Docker container and browser; cannot verify HTTP response headers programmatically without live server"
  - test: "curl -I http://localhost:80/sw.js and curl -I http://localhost:80/index.html"
    expected: "Both return Cache-Control: no-cache, no-store, must-revalidate header"
    why_human: "Requires running nginx container to verify cache header behavior"
  - test: "Send OPTIONS preflight from browser DevTools or curl to http://localhost:8080/api/academic/groups with Origin: http://localhost:5173"
    expected: "Response includes Access-Control-Allow-Origin: http://localhost:5173 with no duplicate headers"
    why_human: "Requires running Gateway to verify end-to-end CORS behavior including header deduplication"
---

# Phase 28: API Gateway CORS + nginx Verification Report

**Phase Goal:** API Gateway accepts cross-origin requests from the PWA origin, and the nginx container serves the PWA static build
**Verified:** 2026-04-06T00:14:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A preflight OPTIONS request from localhost:5173 to the Gateway returns Access-Control-Allow-Origin without duplicate headers | VERIFIED (code) | application.yml has globalcors with explicit origins (lines 14-16), DedupeResponseHeader filter (line 28); JwtAuthenticationFilter bypasses OPTIONS at line 50 before JWT check at line 60; 2 unit tests confirm OPTIONS passes through filter |
| 2 | GET /api/push/vapid-public-key is routable through the Gateway | VERIFIED | notification-push route exists (application.yml lines 65-70) with Path=/api/push/** and StripPrefix=1; added in Phase 27 commit 976173d, still present |
| 3 | docker compose up starts an nginx container serving static HTML at localhost:80; sw.js and index.html served with Cache-Control: no-cache | VERIFIED (config) | docker-compose.yml has pwa-nginx service (lines 178-188) with nginx:1.27-alpine, port 80:80, bind mounts; nginx.conf has no-cache rule for sw.js and index.html (line 7-10); docker compose config validates successfully |

**Score:** 3/3 truths verified (at code/config level; runtime behavior needs human verification)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/api-gateway/src/main/resources/application.yml` | Global CORS configuration | VERIFIED | Contains globalcors with add-to-simple-url-handler-mapping: true (line 11), explicit origins localhost:5173 and localhost:80 (lines 15-16), allow-credentials: true (line 25), DedupeResponseHeader default filter (line 28) |
| `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java` | OPTIONS bypass in JWT filter | VERIFIED | HttpMethod.OPTIONS check at line 50, BEFORE isPublicRoute(path) at line 56; import at line 13 |
| `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java` | OPTIONS bypass tests | VERIFIED | 11 test methods total; Test 10 (optionsRequest_passesThroughWithoutJwt, line 208) and Test 11 (optionsRequest_pushRoute_passesThroughWithoutJwt, line 221) verify OPTIONS bypass |
| `frontends/pwa/nginx.conf` | nginx config with cache rules | VERIFIED | no-cache for sw.js/index.html (line 7), aggressive cache for assets (line 14), SPA fallback try_files (line 20) |
| `frontends/pwa/dist/index.html` | Placeholder PWA entry point | VERIFIED | Contains `<title>RutTrack</title>` (line 6), 13 lines |
| `frontends/pwa/dist/sw.js` | Placeholder service worker | VERIFIED | Contains self.addEventListener('install', ...) (line 2), 4 lines |
| `docker-compose.yml` | pwa-nginx service definition | VERIFIED | nginx:1.27-alpine (line 179), port 80:80 (line 182), bind mounts dist:ro and nginx.conf:ro (lines 184-185), private_net (line 187) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| application.yml globalcors | Spring Cloud Gateway CORS handler | `add-to-simple-url-handler-mapping: true` | WIRED | Pattern found at line 11 of application.yml |
| JwtAuthenticationFilter.filter() | chain.filter(exchange) | OPTIONS method check before JWT validation | WIRED | HttpMethod.OPTIONS at line 50, chain.filter at line 51, isPublicRoute at line 56 (after OPTIONS) |
| docker-compose.yml pwa-nginx volumes | frontends/pwa/dist/ | bind mount ro | WIRED | `./frontends/pwa/dist:/usr/share/nginx/html:ro` at line 184 |
| docker-compose.yml pwa-nginx volumes | frontends/pwa/nginx.conf | bind mount config | WIRED | `./frontends/pwa/nginx.conf:/etc/nginx/conf.d/default.conf:ro` at line 185 |

### Data-Flow Trace (Level 4)

Not applicable -- this phase is infrastructure/configuration only, no dynamic data rendering.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| All 11 gateway tests pass | `./gradlew.bat :services:api-gateway:test` | BUILD SUCCESSFUL, 4 tasks executed | PASS |
| docker-compose.yml is valid YAML | `docker compose config --quiet` | Exit 0 (warning about obsolete version attribute only) | PASS |
| No wildcard in allowed-origins | grep for `"*"` in allowed-origins section | Only `allowed-headers: "*"` (correct -- headers can use wildcard with credentials), origins are explicit | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| INFRA-01 | 28-01-PLAN.md | API Gateway CORS configured for PWA origin | SATISFIED | globalcors in application.yml with explicit origins, OPTIONS bypass in JwtAuthenticationFilter, DedupeResponseHeader, 2 unit tests |
| INFRA-03 | 28-02-PLAN.md | PWA served via nginx container in docker-compose | SATISFIED | pwa-nginx service in docker-compose.yml, nginx.conf with cache rules, placeholder dist files |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| frontends/pwa/dist/index.html | 10 | "Placeholder" text | Info | Intentional -- explicitly a placeholder to be replaced by Phase 29 React build |
| frontends/pwa/dist/sw.js | 1 | "Placeholder" comment | Info | Intentional -- to be replaced by vite-plugin-pwa in Phase 29 |

No blockers or warnings found. The placeholder mentions are by design for this infrastructure-only phase.

### Human Verification Required

### 1. nginx Container Serves PWA

**Test:** Run `docker compose up pwa-nginx -d`, then open http://localhost:80 in a browser
**Expected:** The RutTrack PWA placeholder page displays with HTTP 200
**Why human:** Requires running Docker container; static file analysis confirms config correctness but not runtime behavior

### 2. Cache Headers for sw.js and index.html

**Test:** With pwa-nginx running, execute `curl -I http://localhost:80/sw.js` and `curl -I http://localhost:80/index.html`
**Expected:** Both responses include `Cache-Control: no-cache, no-store, must-revalidate`
**Why human:** Requires running nginx to verify header behavior; nginx.conf regex correctness cannot be fully confirmed without runtime

### 3. End-to-End CORS Preflight

**Test:** With Gateway running, execute `curl -X OPTIONS -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" http://localhost:8080/api/academic/groups -I`
**Expected:** Response includes `Access-Control-Allow-Origin: http://localhost:5173` with no duplicate headers; status 200
**Why human:** Requires running Gateway with full Spring Cloud context to verify CORS filter chain including DedupeResponseHeader

### Gaps Summary

No gaps found. All artifacts exist, are substantive, and are correctly wired. All 11 unit tests pass. Both requirements (INFRA-01, INFRA-03) are satisfied at the code/config level. Three runtime behaviors require human verification with a running Docker environment.

---

_Verified: 2026-04-06T00:14:00Z_
_Verifier: Claude (gsd-verifier)_

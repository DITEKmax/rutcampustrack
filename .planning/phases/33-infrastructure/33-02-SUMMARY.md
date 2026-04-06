---
phase: 33-infrastructure
plan: 02
subsystem: api-gateway
tags: [cors, jwt-filter, gateway, mini-app, web-panel]
dependency_graph:
  requires: []
  provides: [CORS-for-mini-app, CORS-for-web-panel, PUBLIC_PATHS-tma, PUBLIC_PATHS-refresh-body]
  affects: [services/api-gateway]
tech_stack:
  added: []
  patterns: [spring-cloud-gateway-cors, jwt-filter-public-paths]
key_files:
  modified:
    - services/api-gateway/src/main/resources/application.yml
    - services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java
decisions:
  - "Added 3 new CORS origins (5174, 3000, 4200) for Mini App dev/prod and Web Panel; no wildcard per allow-credentials requirement"
  - "Whitelisted /api/auth/tma and /api/auth/refresh-body in PUBLIC_PATHS for Phase 34 Mini App auth endpoints"
metrics:
  duration: 4m
  completed: "2026-04-06T18:52:39Z"
  tasks_completed: 2
  files_modified: 2
---

# Phase 33 Plan 02: Gateway CORS + PUBLIC_PATHS Expansion Summary

Gateway CORS expanded to 5 explicit origins (Mini App dev/prod + Web Panel added) and JwtAuthenticationFilter.PUBLIC_PATHS extended with 2 new unauthenticated auth paths for Telegram Mini App and body-based refresh.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Expand CORS origins and PUBLIC_PATHS | fe9a425 | application.yml, JwtAuthenticationFilter.java |
| 2 | Verify Gateway compiles with updated config | fe9a425 | (build verification only) |

## Changes Made

### application.yml — allowed-origins

Before (2 origins):
- `http://localhost:5173` (PWA dev)
- `http://localhost:80` (PWA prod)

After (5 origins):
- `http://localhost:5173` (PWA dev)
- `http://localhost:80` (PWA prod)
- `http://localhost:5174` (Mini App dev — Vite on port 5174)
- `http://localhost:3000` (Mini App prod — mini-app-nginx container)
- `http://localhost:4200` (Web Panel dev/prod — Angular CLI + web-panel-nginx)

No wildcard used. `allow-credentials: true` and `DedupeResponseHeader` filter unchanged.

### JwtAuthenticationFilter.java — PUBLIC_PATHS

Before (3 entries):
- `/api/auth/login`
- `/api/auth/refresh`
- `/api/auth/public-key`

After (5 entries):
- `/api/auth/login`
- `/api/auth/refresh`
- `/api/auth/public-key`
- `/api/auth/tma` (Telegram Mini App initData authentication — Phase 34)
- `/api/auth/refresh-body` (Body-based token refresh for non-cookie environments — Phase 34)

OPTIONS bypass, PUBLIC_PREFIXES, and all other filter logic unchanged.

## Verification

- `./gradlew.bat :services:api-gateway:build` — BUILD SUCCESSFUL (7 tasks executed, all existing tests pass)
- `grep -c "localhost:5174\|localhost:3000\|localhost:4200" application.yml` returns 3
- Both new PUBLIC_PATHS entries confirmed present in filter file

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

No new threat surface beyond what the plan's threat model covers (T-33-04 through T-33-07). The two new PUBLIC_PATHS are additive-only; downstream security (HMAC-SHA256 initData validation for /tma, refresh token cryptographic validation for /refresh-body) will be implemented in Phase 34.

## Self-Check: PASSED

- services/api-gateway/src/main/resources/application.yml — modified and verified
- services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java — modified and verified
- Commit fe9a425 exists
- BUILD SUCCESSFUL with all tests passing

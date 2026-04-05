---
phase: 28-api-gateway-cors-nginx
plan: 01
subsystem: infra
tags: [cors, spring-cloud-gateway, preflight, options, jwt-filter]

requires:
  - phase: 01-auth-gateway
    provides: JwtAuthenticationFilter, API Gateway routes
provides:
  - OPTIONS preflight bypass in JwtAuthenticationFilter
  - Global CORS configuration for PWA origins (localhost:5173, localhost:80)
  - DedupeResponseHeader default filter for CORS header dedup
affects: [29-pwa-client, notification-web]

tech-stack:
  added: []
  patterns: [globalcors YAML config, OPTIONS bypass before JWT validation, DedupeResponseHeader RETAIN_UNIQUE]

key-files:
  created: []
  modified:
    - services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java
    - services/api-gateway/src/main/resources/application.yml
    - services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java

key-decisions:
  - "OPTIONS bypass placed before isPublicRoute check -- all preflight requests pass through regardless of path"
  - "Explicit origins list (not wildcard) because allow-credentials: true is incompatible with wildcard per CORS spec"

patterns-established:
  - "CORS: globalcors YAML with add-to-simple-url-handler-mapping: true for OPTIONS preflight"
  - "Header dedup: DedupeResponseHeader default filter prevents duplicate CORS headers from backend services"

requirements-completed: [INFRA-01]

duration: 2min
completed: 2026-04-05
---

# Phase 28 Plan 01: API Gateway CORS + OPTIONS Bypass Summary

**Spring Cloud Gateway globalcors with explicit PWA origins, OPTIONS preflight bypass in JwtAuthenticationFilter, and DedupeResponseHeader for header deduplication**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-05T21:08:26Z
- **Completed:** 2026-04-05T21:10:19Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- OPTIONS preflight requests bypass JwtAuthenticationFilter without requiring Authorization header
- Global CORS configured with explicit origins (localhost:5173 for Vite dev, localhost:80 for nginx)
- DedupeResponseHeader prevents duplicate Access-Control-Allow-Origin headers from backend services
- 2 new unit tests verify OPTIONS bypass on both academic and push routes

## Task Commits

Each task was committed atomically:

1. **Task 1: Add OPTIONS bypass tests (TDD red)** - `e54876e` (test)
2. **Task 2: Implement OPTIONS bypass + globalcors YAML + DedupeResponseHeader** - `1acc829` (feat)

## Files Created/Modified
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java` - Added HttpMethod.OPTIONS bypass as first check in filter() method
- `services/api-gateway/src/main/resources/application.yml` - Added globalcors config with explicit origins, default-filters with DedupeResponseHeader
- `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java` - Added 2 OPTIONS bypass tests (academic + push routes)

## Decisions Made
- OPTIONS bypass placed before isPublicRoute check so all preflight requests pass through regardless of path
- Used explicit origins list (not wildcard *) because allow-credentials: true is incompatible with wildcard per CORS spec
- Did not create CorsFilterTest.java -- CORS is YAML-only config requiring @SpringBootTest integration test, out of scope

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- CORS configuration ready for PWA development (Phase 29)
- Vite dev server at localhost:5173 can make cross-origin API calls through Gateway at localhost:8080
- nginx at localhost:80 also allowed as origin for production-like serving

---
*Phase: 28-api-gateway-cors-nginx*
*Completed: 2026-04-05*

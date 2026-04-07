---
phase: 47-unified-swagger-ui
plan: 01
subsystem: api-gateway, swagger-ui
tags: [openapi, swagger, springdoc, documentation, nginx]
dependency_graph:
  requires: []
  provides: [unified-swagger-ui, springdoc-2.8.6]
  affects: [api-gateway, auth-service, academic-service, schedule-service, attendance-service, notification-web, nginx]
tech_stack:
  added: [springdoc-openapi-starter-webflux-ui:2.8.6]
  patterns: [swagger-ui-aggregation, openapi-proxy-routes]
key_files:
  created:
    - services/api-gateway/src/main/java/ru/rutcampustrack/gateway/config/OpenApiConfig.java
  modified:
    - services/api-gateway/build.gradle.kts
    - services/api-gateway/src/main/resources/application.yml
    - services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java
    - services/auth-service/build.gradle.kts
    - services/academic-service/academic-app/build.gradle.kts
    - services/schedule-service/schedule-app/build.gradle.kts
    - services/attendance-service/attendance-app/build.gradle.kts
    - services/notification-service/notification-app/build.gradle.kts
    - nginx/conf.d/default.conf
decisions:
  - Used springdoc-openapi-starter-webflux-ui (not webmvc-ui) for WebFlux-based Gateway
  - Proxy routes use RewritePath (not StripPrefix) to map /openapi/{service} to /api-docs
  - JWT filter whitelist uses exact path for /swagger-ui.html and prefix-based matching for /swagger-ui/, /v3/api-docs, /openapi/
metrics:
  duration: 5m 39s
  completed: "2026-04-08T02:36:00Z"
  tasks: 2/2
  files_changed: 10
---

# Phase 47 Plan 01: Unified Swagger UI Summary

Unified Swagger UI at Gateway aggregating OpenAPI specs from 4 REST services via springdoc-webflux-ui:2.8.6 with RewritePath proxy routes and nginx production proxy rules.

## Tasks Completed

### Task 1: Add springdoc-webflux-ui to Gateway, configure aggregation, whitelist JWT filter, upgrade all services to 2.8.6
**Commit:** 290aad9

- Added `springdoc-openapi-starter-webflux-ui:2.8.6` to Gateway (WebFlux variant per DOC-03)
- Configured `springdoc.swagger-ui.urls` with 4 service entries (Auth, Academic, Schedule, Attendance)
- Added 4 Gateway routes with `RewritePath` filter to proxy `/openapi/{service}` to upstream `/api-docs`
- Created `OpenApiConfig.java` with `RutCampusTrack API` metadata bean
- Extended `JwtAuthenticationFilter` whitelist: `/swagger-ui.html` in PUBLIC_PATHS, `/swagger-ui/`, `/v3/api-docs`, `/openapi/` in PUBLIC_PREFIXES
- Upgraded springdoc from 2.7.0 to 2.8.6 in all 5 services (auth, academic, schedule, attendance, notification-web)
- Compilation successful across all services

### Task 2: Add nginx proxy rules for Swagger UI paths and run full test suite
**Commit:** ea279ee

- Added 4 nginx location blocks: `/swagger-ui.html`, `/swagger-ui/`, `/v3/api-docs`, `/openapi/`
- All blocks placed before PWA catch-all (`location /`) to ensure correct routing
- Each block proxies to `rct-api-gateway:8080` with standard proxy headers
- Full test suite passed (BUILD SUCCESSFUL, 51 tasks, 0 failures)

## Deviations from Plan

None - plan executed exactly as written.

## Verification Results

All acceptance criteria met:
- Gateway has `springdoc-openapi-starter-webflux-ui:2.8.6` dependency
- All 5 services upgraded to 2.8.6, zero instances of 2.7.0 remaining
- Gateway application.yml has swagger-ui.urls with 4 entries + 4 api-docs proxy routes
- JWT filter whitelists swagger-ui, v3/api-docs, and openapi paths
- Nginx proxies swagger paths to Gateway before PWA catch-all
- Full test suite passes with no regressions

## Self-Check: PASSED

- OpenApiConfig.java: FOUND
- SUMMARY.md: FOUND
- PLAN.md: FOUND (preserved)
- Commit 290aad9: FOUND (Task 1)
- Commit ea279ee: FOUND (Task 2)
- springdoc 2.8.6 in Gateway: 1 match
- springdoc 2.7.0 remaining: 0 across all 5 services
- swagger-ui.html in JwtAuthenticationFilter: FOUND
- nginx swagger-ui locations: 2 matches

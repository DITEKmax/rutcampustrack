---
phase: 27-web-push-backend
plan: "01"
subsystem: infra
tags: [web-push, vapid, bouncy-castle, mongodb, spring-boot, gradle, api-gateway, stomp]

requires:
  - phase: 20-26 (notification-web)
    provides: existing STOMP WebSocket service that is being restructured

provides:
  - notification-service/notification-api-contract java-library module with PushApi contract (3 endpoints)
  - notification-service/notification-app Spring Boot module with VAPID config + existing WebSocket functionality
  - WebPushConfig bean wired with VAPID env vars using nl.martijndwars.webpush.PushService
  - Gateway route notification-push forwarding /api/push/** to notification-web:9094
  - Docker-compose VAPID env vars and MongoDB dependency for notification-web service

affects:
  - 27-02 (PushController implements PushApi, @RequireRole security)
  - 27-03 (PushService uses WebPushConfig bean for delivery)

tech-stack:
  added:
    - nl.martijndwars:web-push:5.1.2
    - org.bouncycastle:bcprov-jdk15on:1.70
    - spring-boot-starter-data-mongodb
    - spring-boot-starter-aop
    - spring-boot-starter-hateoas
    - spring-boot-starter-validation
    - springdoc-openapi-starter-webmvc-ui:2.7.0
  patterns:
    - Contract-first: PushApi interface in notification-api-contract, controller implements it in notification-app
    - VAPID keys from env vars only — never hardcoded (T-27-03 mitigation)
    - Bean name 'webPushService' disambiguates from custom PushService class (Plan 03)
    - BouncyCastle LoaderImplementation.CLASSIC fixes Spring Boot 3.2+ signed-JAR conflict

key-files:
  created:
    - services/notification-service/notification-api-contract/build.gradle.kts
    - services/notification-service/notification-api-contract/src/main/java/ru/rutcampustrack/notification/contract/api/PushApi.java
    - services/notification-service/notification-api-contract/src/main/java/ru/rutcampustrack/notification/contract/dto/push/SubscribeRequest.java
    - services/notification-service/notification-api-contract/src/main/java/ru/rutcampustrack/notification/contract/dto/push/UnsubscribeRequest.java
    - services/notification-service/notification-api-contract/src/main/java/ru/rutcampustrack/notification/contract/dto/push/VapidPublicKeyResponse.java
    - services/notification-service/notification-api-contract/src/main/java/ru/rutcampustrack/notification/contract/enums/UserRole.java
    - services/notification-service/notification-app/build.gradle.kts
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebPushConfig.java
  modified:
    - settings.gradle.kts
    - docker-compose.yml
    - services/api-gateway/src/main/resources/application.yml
    - services/notification-service/notification-app/src/main/resources/application.yml

key-decisions:
  - "BouncyCastle changed from runtimeOnly to implementation — needed at compile time for WebPushConfig"
  - "Package nl.martijndwars.webpush (no underscore) — plan spec had incorrect nl.martijndwars.web_push"
  - "WebPushConfig bean named 'webPushService' to avoid future clash with custom PushService in Plan 03"

patterns-established:
  - "Contract-first push API: PushApi in api-contract, controller in app module"
  - "VAPID keys in env vars with empty default — service starts but push delivery requires configured keys"

requirements-completed:
  - PUSH-01
  - INFRA-02

duration: 9min
completed: "2026-04-05"
---

# Phase 27 Plan 01: Module Restructure and API Contract Summary

**notification-web restructured into notification-service (contract + app) with PushApi contract, VAPID WebPushConfig bean, and Gateway push route — enabling Web Push feature development in Plans 02-03**

## Performance

- **Duration:** 9 min
- **Started:** 2026-04-05T19:57:03Z
- **Completed:** 2026-04-05T20:06:09Z
- **Tasks:** 2
- **Files modified:** 22 (21 Task 1, 1 Task 2)

## Accomplishments

- Created `notification-api-contract` java-library module with `PushApi` contract interface defining 3 endpoints (getVapidPublicKey, subscribe, unsubscribe) plus DTOs (SubscribeRequest, UnsubscribeRequest, VapidPublicKeyResponse) and UserRole enum
- Migrated all `notification-web` sources to `notification-service/notification-app` preserving package structure; added WebPushConfig bean, MongoDB, AOP, HATEOAS, validation, OpenAPI, BouncyCastle dependencies
- Added Gateway route `notification-push` forwarding `/api/push/**` to `notification-web:9094` with StripPrefix=1; updated docker-compose with VAPID env vars and MongoDB dependency

## Task Commits

Each task was committed atomically:

1. **Task 1: Module restructure and API contract** - `636b002` (feat)
2. **Task 2: API Gateway push route (INFRA-02)** - `976173d` (feat)

## Files Created/Modified

- `services/notification-service/notification-api-contract/build.gradle.kts` - java-library module declaration
- `services/notification-service/notification-api-contract/.../contract/api/PushApi.java` - Push REST contract (3 endpoints)
- `services/notification-service/notification-api-contract/.../contract/dto/push/SubscribeRequest.java` - Subscribe request record with nested Keys
- `services/notification-service/notification-api-contract/.../contract/dto/push/UnsubscribeRequest.java` - Unsubscribe request record
- `services/notification-service/notification-api-contract/.../contract/dto/push/VapidPublicKeyResponse.java` - Response class extending RepresentationModel
- `services/notification-service/notification-api-contract/.../contract/enums/UserRole.java` - UserRole enum (STUDENT, TEACHER, ADMIN)
- `services/notification-service/notification-app/build.gradle.kts` - Spring Boot app with all deps + BouncyCastle CLASSIC loader fix
- `services/notification-service/notification-app/.../config/WebPushConfig.java` - VAPID PushService bean
- `services/notification-service/notification-app/src/main/resources/application.yml` - Added vapid + mongodb config sections
- `settings.gradle.kts` - Replaced notification-web include with two notification-service submodule includes
- `docker-compose.yml` - Updated build context, added VAPID env vars + MongoDB URI + mongo-attendance depends_on
- `services/api-gateway/src/main/resources/application.yml` - Added notification-push route for /api/push/**

## Decisions Made

- **BouncyCastle scope**: Changed from `runtimeOnly` to `implementation` — `WebPushConfig.java` imports `BouncyCastleProvider` at compile time, so `runtimeOnly` caused compilation failure. This is a Rule 1 auto-fix.
- **Package correction**: Plan spec referenced `nl.martijndwars.web_push.PushService` (underscore) but actual library package is `nl.martijndwars.webpush` (no underscore). Fixed during compilation verification.
- **Bean name**: `webPushService` chosen to avoid future name clash with the custom `PushService` application service planned in Plan 03.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed BouncyCastle runtimeOnly scope causing compile error**
- **Found during:** Task 1 (first compilation attempt)
- **Issue:** `bcprov-jdk15on:1.70` was `runtimeOnly` but `WebPushConfig.java` imports `BouncyCastleProvider` at compile time
- **Fix:** Changed `runtimeOnly` to `implementation` in `notification-app/build.gradle.kts`
- **Files modified:** `services/notification-service/notification-app/build.gradle.kts`
- **Verification:** `./gradlew.bat :services:notification-service:notification-app:compileJava` exits 0
- **Committed in:** `636b002` (Task 1 commit)

**2. [Rule 1 - Bug] Corrected web-push package name from web_push to webpush**
- **Found during:** Task 1 (first compilation attempt)
- **Issue:** Plan spec used `nl.martijndwars.web_push.PushService` (underscore) but actual jar package is `nl.martijndwars.webpush.PushService` (no underscore)
- **Fix:** Updated `WebPushConfig.java` to use correct `import nl.martijndwars.webpush.PushService`
- **Files modified:** `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebPushConfig.java`
- **Verification:** `./gradlew.bat :services:notification-service:notification-app:compileJava` exits 0
- **Committed in:** `636b002` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs)
**Impact on plan:** Both auto-fixes required for successful compilation. No scope creep.

## Issues Encountered

None beyond the two compilation errors caught and fixed inline.

## User Setup Required

**VAPID keys must be generated and configured before Web Push delivery works.** The service starts without them (empty defaults) but push sending will fail at runtime.

To generate VAPID keys:
```bash
# Install web-push CLI or use openssl
# Add to .env or docker-compose override:
VAPID_PUBLIC_KEY=<base64url-encoded-uncompressed-public-key>
VAPID_PRIVATE_KEY=<base64url-encoded-private-key>
VAPID_SUBJECT=mailto:admin@rut.ru
```

GET `/api/push/vapid-public-key` endpoint (Plan 02) will return the public key for PWA subscription.

## Next Phase Readiness

- Plan 02 can now implement `PushController implements PushApi` with `@RequireRole(STUDENT)` security and MongoDB subscription persistence
- Plan 03 can implement push delivery using the `webPushService` bean
- Gateway route is live — `GET /api/push/vapid-public-key` will route correctly once PushController is implemented

---

## Self-Check: PASSED

All created files exist on disk. Both task commits (636b002, 976173d) verified in git log. settings.gradle.kts contains new includes and removed old one. Gateway has notification-push route. Docker-compose has VAPID env vars, MongoDB URI, and updated build context. services/notification-web/ directory does not exist.

---
*Phase: 27-web-push-backend*
*Completed: 2026-04-05*

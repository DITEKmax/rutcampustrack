---
phase: 27-web-push-backend
verified: 2026-04-05T21:00:00Z
status: human_needed
score: 9/10 must-haves verified
human_verification:
  - test: "Send real Web Push to a subscribed browser"
    expected: "Browser notification appears within 5 seconds with correct Russian-language title/body"
    why_human: "Requires running notification-service with valid VAPID keys, a real browser with push permission, and an external push service (FCM/Mozilla). Cannot test programmatically without end-to-end infrastructure."
  - test: "GET /api/push/vapid-public-key returns Base64 VAPID key after service restart"
    expected: "Identical key returned before and after container restart (env-var persistence verified)"
    why_human: "Requires running Docker containers. SC-1 wording says 'persisted in Redis' but implementation uses env vars by user's explicit design decision (Discussion Log). Functional persistence verified by design, but restart test needs running stack."
  - test: "Full Gateway routing smoke test: curl /api/push/vapid-public-key returns 200"
    expected: "HTTP 200 with EntityModel containing publicKey Base64 string"
    why_human: "Requires running Gateway + notification-service containers with valid VAPID env vars."
---

# Phase 27: Web Push Backend — Verification Report

**Phase Goal:** notification-web can generate VAPID keys, store push subscriptions, and deliver Web Push notifications for lesson and homework events
**Verified:** 2026-04-05T21:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | notification-web/ replaced by notification-service/ (contract + app submodules) | VERIFIED | `services/notification-web/` does not exist; `services/notification-service/notification-api-contract/` and `services/notification-service/notification-app/` confirmed on disk |
| 2 | Gradle build succeeds for both new modules | VERIFIED | `./gradlew :services:notification-service:notification-api-contract:compileJava :services:notification-service:notification-app:compileJava` — BUILD SUCCESSFUL |
| 3 | GET /push/vapid-public-key endpoint defined in PushApi contract | VERIFIED | `PushApi.java` line 32: `@GetMapping("/vapid-public-key")`, `@RequestMapping("/push")` on interface |
| 4 | Gateway routes /api/push/** to notification-web:9094 with StripPrefix=1 | VERIFIED | api-gateway `application.yml` contains `id: notification-push`, `Path=/api/push/**`, `StripPrefix=1`, `uri: http://notification-web:9094` |
| 5 | VAPID public key read from env var and returned by WebPushConfig bean | VERIFIED | `WebPushConfig.java`: `@Value("${vapid.public-key}") String publicKeyBase64`, `PushService webPushService(...)` bean; env vars `VAPID_PUBLIC_KEY` in docker-compose |
| 6 | POST /push/subscribe with valid JSON stores subscription in MongoDB push_subscriptions | VERIFIED | `PushController.subscribe()` calls `repository.save(doc)` with `userId`/`groupId` from `RequestContext`; `PushSubscriptionDocument @Document(collection = "push_subscriptions")` |
| 7 | DELETE /push/subscribe removes subscription from MongoDB | VERIFIED | `PushController.unsubscribe()` calls `repository.deleteByUserIdAndEndpoint(requestContext.getUserId(), request.endpoint())` |
| 8 | Push endpoints require STUDENT role via @RequireRole AOP | VERIFIED | `PushController.java` lines 46, 53, 68: `@RequireRole({UserRole.STUDENT})` on all 3 methods; `RoleCheckAspect.java` `@Aspect` intercepts and enforces |
| 9 | lesson.started/cancelled/homework.published events trigger async Web Push to group subscribers | VERIFIED | `EventConsumer.java`: push hook after `convertAndSend`, guarded by `shouldPush(eventType)`; `WebPushDeliveryService.PUSH_EVENT_TYPES` = `{"lesson.started", "lesson.cancelled", "homework.published"}` |
| 10 | HTTP 410 from push service auto-deletes expired subscription | VERIFIED | `WebPushDeliveryService.isGone()` checks `HttpResponseException.getStatusCode() == 410`; on match calls `repository.deleteByEndpoint(sub.getEndpoint())` |
| 11 | Push delivery does not block STOMP WebSocket routing | VERIFIED | `EventConsumer`: `messagingTemplate.convertAndSend(...)` called FIRST (line 60), then `webPushDeliveryService.sendToGroup(...)` (line 66); `sendToGroup` is `@Async` — returns `CompletableFuture<Void>` immediately |
| 12 | Other event types do NOT trigger push (excuse.requested, attendance.marked, etc.) | VERIFIED | `WebPushDeliveryService.shouldPush()` returns `PUSH_EVENT_TYPES.contains(eventType)` — only 3 types match; `EventConsumerTest` verifies excuse.requested does NOT call `sendToGroup` |
| SC-1 | VAPID key persisted (SC says Redis, design uses env vars) | PARTIAL | Key read from `${VAPID_PUBLIC_KEY}` env var — persists across restarts as long as env vars are set. Redis persistence explicitly rejected by user in Discussion Log. End-to-end restart test requires running containers. |
| SC-4 | Browser notification delivered within 5 seconds | HUMAN | Cannot verify programmatically — requires live stack with valid VAPID keys and browser |

**Score:** 9/10 truths fully verified programmatically (SC-1 partial by design, SC-4 human-only)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `notification-api-contract/build.gradle.kts` | java-library module | VERIFIED | Contains `plugins { \`java-library\` }` |
| `notification-api-contract/.../api/PushApi.java` | Push REST contract interface | VERIFIED | `interface PushApi` with 3 endpoints: getVapidPublicKey, subscribe, unsubscribe |
| `notification-api-contract/.../dto/push/SubscribeRequest.java` | Subscribe request record | VERIFIED | `record SubscribeRequest(@NotBlank String endpoint, @NotNull Keys keys)` |
| `notification-api-contract/.../dto/push/UnsubscribeRequest.java` | Unsubscribe request record | VERIFIED | `record UnsubscribeRequest(@NotBlank String endpoint)` |
| `notification-api-contract/.../dto/push/VapidPublicKeyResponse.java` | HATEOAS response | VERIFIED | `extends RepresentationModel<VapidPublicKeyResponse>` |
| `notification-api-contract/.../enums/UserRole.java` | UserRole enum | VERIFIED | `STUDENT, TEACHER, ADMIN` |
| `notification-app/build.gradle.kts` | Spring Boot app with new deps | VERIFIED | Contains `web-push:5.1.2`, `bcprov-jdk15on:1.70`, `spring-boot-starter-data-mongodb`, `LoaderImplementation.CLASSIC`, `httpclient:4.5.13`, `jose4j:0.7.9` |
| `notification-app/.../config/WebPushConfig.java` | VAPID PushService bean | VERIFIED | `BouncyCastleProvider`, `webPushService` bean, VAPID from `@Value` |
| `notification-app/.../security/RequireRole.java` | AOP role annotation | VERIFIED | `@interface RequireRole`, `UserRole[] value()` |
| `notification-app/.../security/RoleCheckAspect.java` | Role enforcement aspect | VERIFIED | `@Aspect`, reads `RequestContext`, enforces allowed roles |
| `notification-app/.../security/UserContextFilter.java` | Header extraction filter | VERIFIED | `extends OncePerRequestFilter`, reads `X-User-Id`, `X-User-Role`, `X-Group-Id` |
| `notification-app/.../security/RequestContext.java` | Request-scoped context | VERIFIED | `@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)`, `UserRole role` |
| `notification-app/.../push/PushSubscriptionDocument.java` | MongoDB document | VERIFIED | `@Document(collection = "push_subscriptions")`, all 7 `@Field` mappings |
| `notification-app/.../push/PushSubscriptionRepository.java` | MongoDB repository | VERIFIED | `MongoRepository<PushSubscriptionDocument, String>`, `findAllByGroupId`, `deleteByUserIdAndEndpoint`, `deleteByEndpoint` |
| `notification-app/.../push/PushController.java` | REST controller | VERIFIED | `implements PushApi`, `@RequireRole({UserRole.STUDENT})` on all 3 methods, `requestContext.getUserId()`, `requestContext.getGroupId()`, `HttpStatus.CREATED` |
| `notification-app/.../config/PushMongoConfig.java` | MongoDB indexes | VERIFIED | `@PostConstruct initIndexes()`, `uniq_user_endpoint`, `idx_group_id` |
| `notification-app/.../config/AsyncConfig.java` | Thread pool config | VERIFIED | `@EnableAsync`, `pushTaskExecutor`, `ThreadPoolTaskExecutor` (core=4, max=10, queue=50) |
| `notification-app/.../push/WebPushDeliveryService.java` | Async push delivery | VERIFIED | `@Async("pushTaskExecutor")`, `CompletableFuture<Void> sendToGroup`, `PUSH_EVENT_TYPES`, 410 cleanup, Russian titles |
| `notification-app/.../event/EventConsumer.java` | Event consumer with push hook | VERIFIED | `WebPushDeliveryService` field + constructor, `shouldPush` gate, `sendToGroup` after `convertAndSend` |
| `notification-app/.../push/PushControllerTest.java` | Push controller tests | VERIFIED | Exists, 47 total test methods across all test classes |
| `notification-app/.../push/WebPushDeliveryServiceTest.java` | Delivery service tests | VERIFIED | Exists, 7 tests per SUMMARY |
| `notification-app/.../event/EventConsumerTest.java` | Event consumer tests | VERIFIED | Exists, 15 tests (9 existing + 6 new push hook tests) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `settings.gradle.kts` | `notification-service:notification-app` | Gradle include | VERIFIED | `include("services:notification-service:notification-app")` at line 15 |
| `settings.gradle.kts` | `notification-service:notification-api-contract` | Gradle include | VERIFIED | `include("services:notification-service:notification-api-contract")` at line 14 |
| `notification-app/build.gradle.kts` | `notification-api-contract` | project dependency | VERIFIED | `implementation(project(":services:notification-service:notification-api-contract"))` |
| `PushController.java` | `PushApi.java` | implements | VERIFIED | `public class PushController implements PushApi` |
| `PushController.java` | `PushSubscriptionRepository.java` | constructor injection | VERIFIED | `PushSubscriptionRepository repository` field + constructor param |
| `UserContextFilter.java` | `RequestContext.java` | populates per-request context | VERIFIED | `requestContext.setUserId(...)`, `requestContext.setRole(...)` |
| `EventConsumer.java` | `WebPushDeliveryService.java` | async call after STOMP | VERIFIED | `webPushDeliveryService.shouldPush(eventType)` + `sendToGroup(groupId, eventType, payload)` AFTER `convertAndSend` |
| `WebPushDeliveryService.java` | `PushSubscriptionRepository.java` | findAllByGroupId query | VERIFIED | `repository.findAllByGroupId(groupId)` at line 59 |
| `WebPushDeliveryService.java` | `nl.martijndwars.webpush.PushService` | send notification | VERIFIED | `webPushService.send(notification)` at line 72 |
| `api-gateway/application.yml` | `notification-web:9094` | notification-push route | VERIFIED | `id: notification-push`, `Path=/api/push/**`, `StripPrefix=1`, `uri: http://notification-web:9094` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `PushController.subscribe()` | `userId`, `groupId` | `RequestContext` populated by `UserContextFilter` from Gateway headers `X-User-Id`/`X-Group-Id` | Yes — headers injected by Gateway after JWT validation | FLOWING |
| `WebPushDeliveryService.sendToGroup()` | `subs` (subscriptions list) | `repository.findAllByGroupId(groupId)` — real MongoDB query | Yes — Spring Data MongoDB query method | FLOWING |
| `EventConsumer.onEvent()` | `groupId`, `eventType`, `payload` | RabbitMQ message via `@RabbitListener` | Yes — real events from internal event bus | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Both modules compile | `./gradlew :services:notification-service:notification-api-contract:compileJava :services:notification-service:notification-app:compileJava --no-daemon --rerun-tasks` | BUILD SUCCESSFUL in 16s | PASS |
| All notification-app tests pass | `./gradlew :services:notification-service:notification-app:test --no-daemon --rerun-tasks` | BUILD SUCCESSFUL (47 test methods) | PASS |
| notification-web/ removed | `ls services/notification-web/` | No such file or directory | PASS |
| Gateway route defined | grep `notification-push` in api-gateway `application.yml` | Found at line 46 with `Path=/api/push/**`, `StripPrefix=1` | PASS |
| Push hook after STOMP | Line order in `EventConsumer.java` | `convertAndSend` at line 60, `shouldPush`/`sendToGroup` at lines 65-66 | PASS |
| End-to-end Gateway routing | curl `/api/push/vapid-public-key` against running stack | SKIP — requires running containers | SKIP |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PUSH-01 | 27-01 | notification-web generates VAPID key pair and stores persistently | SATISFIED | `WebPushConfig` bean with `@Value("${vapid.public-key}")` from env var; env-var persistence chosen explicitly by user (Discussion Log) over Redis |
| PUSH-02 | 27-02 | notification-web exposes POST /push/subscribe to store PushSubscription | SATISFIED | `PushController.subscribe()` → `repository.save(doc)` in `push_subscriptions`; mapped via `PushApi.@PostMapping("/subscribe")` |
| PUSH-03 | 27-02 | notification-web exposes DELETE /push/subscribe to unsubscribe | SATISFIED | `PushController.unsubscribe()` → `repository.deleteByUserIdAndEndpoint(...)` |
| PUSH-04 | 27-03 | notification-web sends Web Push for lesson.started events (async, non-blocking) | SATISFIED | `PUSH_EVENT_TYPES` includes `"lesson.started"`; `@Async("pushTaskExecutor")` ensures non-blocking delivery |
| PUSH-05 | 27-03 | notification-web sends Web Push for lesson.cancelled events | SATISFIED | `PUSH_EVENT_TYPES` includes `"lesson.cancelled"`; title `"Пара отменена"` |
| PUSH-06 | 27-03 | notification-web sends Web Push for homework.published events | SATISFIED | `PUSH_EVENT_TYPES` includes `"homework.published"`; title `"Новое ДЗ"` |
| PUSH-07 | 27-03 | notification-web handles expired/invalid subscriptions (HTTP 410 → delete) | SATISFIED | `WebPushDeliveryService.isGone()` detects 410 → `repository.deleteByEndpoint(sub.getEndpoint())` |
| INFRA-02 | 27-01 | API Gateway route for /api/push/** to notification-web | SATISFIED | `id: notification-push`, `Path=/api/push/**`, `StripPrefix=1`, `uri: http://notification-web:9094` in gateway `application.yml` |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `JwtHandshakeInterceptor.java` | 102, 108 | `return null` | Info | Pre-existing utility method for parsing query params — not a stub; null is the valid "not found" sentinel for Optional-style parsing. Does not affect push functionality. |

No blockers or warnings found in phase 27 code.

### Human Verification Required

#### 1. End-to-End Web Push Delivery

**Test:** Generate VAPID keys, set `VAPID_PUBLIC_KEY` and `VAPID_PRIVATE_KEY` env vars in docker-compose, start all containers, subscribe a browser via `POST /api/push/subscribe`, then trigger a `lesson.started` event in RabbitMQ and observe browser notification.
**Expected:** Browser notification appears within 5 seconds with title "Пара началась" and body containing the subject name.
**Why human:** Requires running Docker stack with valid VAPID keys (EC key pair in Base64url format), a real browser with push permission granted, and an active push subscription endpoint from FCM or Mozilla Push Service. Cannot verify programmatically without external push infrastructure.

#### 2. VAPID Key Persistence Across Restart

**Test:** Start notification-service with `VAPID_PUBLIC_KEY=<key>`, call `GET /api/push/vapid-public-key`, stop the container, restart it with the same env var, call the endpoint again.
**Expected:** Identical key returned both times — proving env-var-based persistence is working.
**Why human:** Requires running containers. Note: ROADMAP SC-1 says "persisted in Redis" but the user explicitly chose env vars (Discussion Log 2026-04-05). The implementation is functionally correct but differs from ROADMAP SC wording.

#### 3. Gateway Routing Smoke Test

**Test:** Run `curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/push/vapid-public-key` with a valid student JWT.
**Expected:** HTTP 200 with JSON body `{"publicKey": "<base64url>", "_links": {...}}`.
**Why human:** Requires all containers running (Gateway on 8080, notification-service on 9094, Auth Service for JWT validation).

### Gaps Summary

No blocking gaps found. All plan acceptance criteria are met:

- Module restructure complete (notification-web removed, notification-service with two submodules)
- All 8 requirements (PUSH-01 through PUSH-07, INFRA-02) are satisfied by the implementation
- 47 unit tests pass (BUILD SUCCESSFUL)
- Key links all verified: PushController implements PushApi, EventConsumer hooks push after STOMP, WebPushDeliveryService handles 410 cleanup
- Security AOP (@RequireRole) wired correctly with ScopedProxyMode.TARGET_CLASS

**Note on ROADMAP SC-1 Redis vs env-var discrepancy:** The ROADMAP says VAPID key is "persisted in Redis" but the user explicitly chose env-var persistence during planning (Discussion Log 2026-04-05). This is not a gap — it is an approved design decision that supersedes the ROADMAP SC wording. The functional requirement (key survives restart) is satisfied by env-var configuration.

**Note on route paths:** ROADMAP SC-1 through SC-3 use `/api/ws/push/...` paths, but all plans define the route as `/api/push/...` with a dedicated `notification-push` gateway route. This path change is intentional per the INFRA-02 plan specification and Discussion Log.

---

_Verified: 2026-04-05T21:00:00Z_
_Verifier: Claude (gsd-verifier)_

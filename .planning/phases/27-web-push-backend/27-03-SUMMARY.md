---
phase: 27-web-push-backend
plan: "03"
subsystem: infra
tags: [web-push, async, spring-boot, rabbitmq, stomp, mongodb, thread-pool, push-delivery]

requires:
  - phase: 27-01
    provides: WebPushConfig bean (webPushService) wired with VAPID keys via nl.martijndwars.webpush.PushService
  - phase: 27-02
    provides: PushSubscriptionRepository with findAllByGroupId + deleteByEndpoint, PushSubscriptionDocument

provides:
  - AsyncConfig with @EnableAsync and pushTaskExecutor (ThreadPoolTaskExecutor, core=4, max=10, queue=50)
  - WebPushDeliveryService with @Async sendToGroup — fanout push to all group subscribers for 3 event types
  - HTTP 410 Gone auto-deletes expired subscription via repository.deleteByEndpoint (PUSH-07)
  - EventConsumer modified with push hook — calls webPushDeliveryService.sendToGroup after STOMP convertAndSend
  - 15 unit tests for EventConsumer (9 existing + 6 new push tests), 7 unit tests for WebPushDeliveryService

affects:
  - 31-service-worker (consumes push payload JSON: title/body/event_type/data)

tech-stack:
  added:
    - org.apache.httpcomponents:httpclient:4.5.13 (compile dep — web-push transitive, needed for HttpResponseException 410 detection)
    - org.bitbucket.b_c:jose4j:0.7.9 (compile dep — web-push transitive, needed for PushService.send() checked exception)
  patterns:
    - "@Async with named executor: @Async(\"pushTaskExecutor\") routes push I/O to bounded thread pool — never blocks RabbitMQ consumer thread"
    - "Protected factory method createNotification() in WebPushDeliveryService enables unit testing without real EC key parsing"
    - "@MockitoSettings(strictness = LENIENT) + @Spy pattern for testing services with protected factory methods"
    - "shouldPush() gate method: EventConsumer delegates event-type filtering to WebPushDeliveryService to stay decoupled"

key-files:
  created:
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/AsyncConfig.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java
    - services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/WebPushDeliveryServiceTest.java
  modified:
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java
    - services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java
    - services/notification-service/notification-app/build.gradle.kts

key-decisions:
  - "createNotification() extracted as protected factory method — Notification constructor parses real EC keys, making direct unit testing impossible without valid VAPID-format keys"
  - "httpclient and jose4j added as explicit compile deps — nl.martijndwars:web-push:5.1.2 only exposes them at runtime; WebPushDeliveryService.isGone() and PushService.send() need them at compile time"
  - "@MockitoSettings(strictness = LENIENT) used in WebPushDeliveryServiceTest — @BeforeEach stub and per-test doAnswer stubs on createNotification() both needed; strict mode rejects the @BeforeEach stub as 'unnecessary' in payload-capture tests"

patterns-established:
  - "Push-eligible event types declared in PUSH_EVENT_TYPES Set — single source of truth, shouldPush() gate delegates to it"
  - "isGone() helper checks both direct and wrapped HttpResponseException for 410 status"
  - "Push payload JSON format: {title, body, event_type, data} — consumed by Service Worker in Phase 31"

requirements-completed:
  - PUSH-04
  - PUSH-05
  - PUSH-06
  - PUSH-07

duration: 14min
completed: "2026-04-05"
---

# Phase 27 Plan 03: Async Web Push Delivery Engine Summary

**@Async push delivery engine: lesson.started/cancelled and homework.published fanout to MongoDB group subscribers, HTTP 410 auto-cleanup, non-blocking STOMP hook — 22 unit tests all passing**

## Performance

- **Duration:** 14 min
- **Started:** 2026-04-05T20:22:59Z
- **Completed:** 2026-04-05T20:37:31Z
- **Tasks:** 2
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments

- Created `AsyncConfig` with `@EnableAsync` and `pushTaskExecutor` (ThreadPoolTaskExecutor, core=4, max=10, queue=50, keepAlive=60s) — per T-27-08 DoS mitigation, bounded pool prevents RabbitMQ consumer thread starvation
- Created `WebPushDeliveryService` with `@Async("pushTaskExecutor") sendToGroup()` — fetches all subscribers for a group, builds Russian-language push payload (Пара началась / Пара отменена / Новое ДЗ + subject_name), sends via `webPushService.send()`, auto-deletes on HTTP 410, logs and skips on other errors
- Modified `EventConsumer` to add push hook after `messagingTemplate.convertAndSend` — calls `webPushDeliveryService.shouldPush(eventType)` then `sendToGroup(groupId, eventType, payload)` for lesson.started, lesson.cancelled, homework.published; excuse.requested and other events only go to STOMP unchanged

## Task Commits

Each task was committed atomically (TDD: RED commit → GREEN commit):

1. **Task 1 RED: Failing WebPushDeliveryService tests** - `6b5d1b6` (test)
2. **Task 1 GREEN: AsyncConfig + WebPushDeliveryService** - `340772a` (feat)
3. **Task 2 RED: Failing EventConsumer push hook tests** - `1b063ab` (test)
4. **Task 2 GREEN: EventConsumer push hook** - `55c8cec` (feat)

_TDD tasks each have 2 commits (test RED → feat GREEN)_

## Files Created/Modified

- `services/notification-service/notification-app/.../config/AsyncConfig.java` - @EnableAsync + pushTaskExecutor ThreadPoolTaskExecutor
- `services/notification-service/notification-app/.../push/WebPushDeliveryService.java` - @Async sendToGroup, 410 cleanup, payload builder
- `services/notification-service/notification-app/.../push/WebPushDeliveryServiceTest.java` - 7 unit tests (fetch, fanout, 410 delete, non-410 skip, 3 payload content tests)
- `services/notification-service/notification-app/.../event/EventConsumer.java` - Added WebPushDeliveryService dep + push hook after STOMP
- `services/notification-service/notification-app/.../event/EventConsumerTest.java` - 9 existing tests preserved + 6 new push hook tests (15 total)
- `services/notification-service/notification-app/build.gradle.kts` - Added httpclient:4.5.13 + jose4j:0.7.9 compile deps; testImplementation httpclient

## Decisions Made

- **createNotification() protected factory method**: `Notification(endpoint, p256dh, auth, payload)` constructor parses p256dh as a real EC public key — random test strings throw `InvalidKeySpecException` before `send()` is called. Extracted to `protected createNotification()` so tests can spy on the service and return a mocked `Notification` without parsing real keys. This is the standard testability pattern for third-party value objects with heavyweight constructors.
- **Explicit httpclient + jose4j compile deps**: `nl.martijndwars:web-push:5.1.2` only brings these as `runtime` transitive deps. `WebPushDeliveryService.isGone()` imports `org.apache.http.client.HttpResponseException` and `PushService.send()` throws `org.jose4j.lang.JoseException` as a checked exception — both needed at compile time.
- **@MockitoSettings(LENIENT)**: `@BeforeEach` sets a default stub for `createNotification()` (returns mockNotification). Tests 5-7 override it with `doAnswer` + `ArgumentCaptor` to capture payload bytes. Mockito strict mode flags the `@BeforeEach` stub as "unnecessary" in those tests. LENIENT allows both stubs to coexist cleanly.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added httpclient as compile dependency**
- **Found during:** Task 1 (first GREEN compilation attempt)
- **Issue:** `org.apache.http.client.HttpResponseException` is needed at compile time in `WebPushDeliveryService.isGone()` but `web-push:5.1.2` only provides httpclient as a runtime dep
- **Fix:** Added `implementation("org.apache.httpcomponents:httpclient:4.5.13")` to main deps and `testImplementation` for tests
- **Files modified:** `build.gradle.kts`
- **Verification:** `compileJava` task succeeds
- **Committed in:** `340772a` (Task 1 feat commit)

**2. [Rule 3 - Blocking] Added jose4j as compile dependency**
- **Found during:** Task 1 (second GREEN compilation attempt after httpclient fix)
- **Issue:** `PushService.send()` declares `JoseException` as a checked exception; `org.bitbucket.b_c:jose4j:0.7.9` is only a runtime transitive dep of web-push
- **Fix:** Added `implementation("org.bitbucket.b_c:jose4j:0.7.9")` to build.gradle.kts
- **Files modified:** `build.gradle.kts`
- **Verification:** `compileJava` task succeeds, all tests pass
- **Committed in:** `340772a` (Task 1 feat commit)

**3. [Rule 1 - Bug] Extracted createNotification() factory method for testability**
- **Found during:** Task 1 (GREEN test run — `WantedButNotInvoked` on `webPushService.send()`)
- **Issue:** `Notification(endpoint, p256dh, auth, payload)` constructor decodes p256dh as a real EC point; test fixture strings "p256dh-key"/"auth-key" throw `InvalidKeySpecException` before `send()` is called, causing `WantedButNotInvoked` failures
- **Fix:** Extracted `protected Notification createNotification(PushSubscriptionDocument sub, byte[] payload)` factory method; tests use `@Spy` + `doReturn(mockNotification)` to bypass EC key parsing
- **Files modified:** `WebPushDeliveryService.java`, `WebPushDeliveryServiceTest.java`
- **Verification:** All 7 WebPushDeliveryServiceTest tests pass
- **Committed in:** `340772a` (Task 1 feat commit)

---

**Total deviations:** 3 auto-fixed (2 Rule 3 blocking, 1 Rule 1 bug)
**Impact on plan:** All 3 fixes required for successful compilation and testing. No scope creep — factory method is a testability aid, not a behavioral change.

## Issues Encountered

- Mockito `UnnecessaryStubbingException` with strict mode: `@BeforeEach` default stub conflicted with per-test `doAnswer` stubs in payload-capture tests. Resolved with `@MockitoSettings(strictness = Strictness.LENIENT)`.

## User Setup Required

None — no additional configuration beyond what Plans 01 and 02 established (VAPID env vars in docker-compose, MongoDB URI).

## Next Phase Readiness

- Phase 27 complete: VAPID keys (01) + subscription CRUD (02) + async delivery engine (03) all operational
- Push payload format `{title, body, event_type, data}` established — Phase 31 Service Worker `push` event handler can parse this directly
- All 4 push requirements (PUSH-04..07) satisfied

---

## Self-Check: PASSED

Files verified:
- `AsyncConfig.java` — exists, contains @EnableAsync, pushTaskExecutor, ThreadPoolTaskExecutor
- `WebPushDeliveryService.java` — exists, contains @Async("pushTaskExecutor"), CompletableFuture<Void> sendToGroup, repository.findAllByGroupId, webPushService.send, repository.deleteByEndpoint, 410, PUSH_EVENT_TYPES, lesson.started, lesson.cancelled, homework.published, Пара началась, Пара отменена, Новое ДЗ
- `WebPushDeliveryServiceTest.java` — exists, 7 test methods
- `EventConsumer.java` — contains WebPushDeliveryService field, constructor param, shouldPush, sendToGroup, push hook after convertAndSend
- `EventConsumerTest.java` — contains WebPushDeliveryService mock, lesson.started/cancelled/homework.published push tests, excuse.requested non-push test

Commits verified: 6b5d1b6, 340772a, 1b063ab, 55c8cec — all in git log.

Full test suite: BUILD SUCCESSFUL (22 tests passing).

---
*Phase: 27-web-push-backend*
*Completed: 2026-04-05*

---
phase: 27
slug: web-push-backend
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-05
updated: 2026-04-05
---

# Phase 27 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (Spring Boot Test) |
| **Config file** | None — convention-based; tests in `src/test/java/` |
| **Quick run command** | `.\gradlew :services:notification-service:notification-app:test --tests "ru.rutcampustrack.notification.push.*" -x integrationTest` |
| **Full suite command** | `.\gradlew :services:notification-service:notification-app:test` |
| **Estimated runtime** | ~10 seconds |
| **Total tests** | 47 (4 security + 8 controller + 7 delivery + 15 event consumer + 6 config + 7 repository) |

---

## Sampling Rate

- **After every task commit:** Run `.\gradlew :services:notification-service:notification-app:test --tests "ru.rutcampustrack.notification.push.*" -x integrationTest`
- **After every plan wave:** Run `.\gradlew :services:notification-service:notification-app:test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test File | Test Method(s) | Status |
|---------|------|------|-------------|-----------|----------------|--------|
| 27-01-01 | 01 | 1 | PUSH-01 | PushControllerTest | `getVapidPublicKey_returns200WithPublicKey` | ✅ green |
| 27-01-02 | 01 | 1 | PUSH-02 | PushControllerTest | `subscribe_storesSubscriptionAndReturns201`, `subscribe_usesUserIdFromRequestContext`, `subscribe_usesGroupIdFromRequestContext` | ✅ green |
| 27-01-03 | 01 | 1 | PUSH-03 | PushControllerTest | `unsubscribe_deletesByUserIdAndEndpointAndReturns204` | ✅ green |
| 27-02-01 | 02 | 2 | PUSH-04 | EventConsumerTest, WebPushDeliveryServiceTest | `lessonStarted_triggersBothStompAndPush`, `sendToGroup_lessonStarted_buildsTitleAndBody` | ✅ green |
| 27-02-02 | 02 | 2 | PUSH-05 | EventConsumerTest, WebPushDeliveryServiceTest | `lessonCancelled_triggersPush`, `sendToGroup_lessonCancelled_buildsTitleAndBody` | ✅ green |
| 27-02-03 | 02 | 2 | PUSH-06 | EventConsumerTest, WebPushDeliveryServiceTest | `homeworkPublished_triggersPush`, `sendToGroup_homeworkPublished_buildsTitleAndBody` | ✅ green |
| 27-02-04 | 02 | 2 | PUSH-07 | WebPushDeliveryServiceTest | `sendToGroup_on410_deletesSubscription`, `sendToGroup_onNon410Error_doesNotDeleteSubscription` | ✅ green |
| 27-03-01 | 03 | 1 | INFRA-02 | — | Manual: `curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/push/vapid-public-key` | manual-only |

*Status: ✅ green · ❌ red · ⚠️ flaky · manual-only*

### Security Coverage

| Threat ID | Requirement | Test File | Test Method(s) | Status |
|-----------|-------------|-----------|----------------|--------|
| T-27-01 | Spoofing prevention | PushControllerTest | `subscribe_usesUserIdFromRequestContext` (userId from RequestContext, not body) | ✅ green |
| T-27-05 | Role enforcement | PushControllerTest | `getVapidPublicKey_hasRequireRoleStudentAnnotation`, `subscribe_hasRequireRoleStudentAnnotation`, `unsubscribe_hasRequireRoleStudentAnnotation` | ✅ green |
| T-27-05 | Role rejection | SecurityInfrastructureTest | `roleCheckAspect_allowsStudentWhenRequireRoleStudent`, `roleCheckAspect_rejectsTeacherWhenRequireRoleStudent` | ✅ green |
| T-27-06 | Input validation | — | @Valid + @NotBlank on contract DTOs (framework-enforced) | contract |
| T-27-08 | DoS mitigation | AsyncConfig | Bounded thread pool (core=4, max=10, queue=50) — structural, not testable | structural |

---

## Test Files

| File | Tests | Covers |
|------|-------|--------|
| `SecurityInfrastructureTest.java` | 4 | @RequireRole AOP, UserContextFilter header parsing |
| `PushControllerTest.java` | 8 | VAPID key, subscribe/unsubscribe CRUD, role annotations |
| `WebPushDeliveryServiceTest.java` | 7 | Async delivery, 410 cleanup, payload building per event type |
| `EventConsumerTest.java` | 15 | STOMP routing (existing) + push hook for 3 event types + non-push exclusion |
| `PushSubscriptionRepositoryTest.java` | 7 | MongoDB queries (Spring Data) |
| `JwtHandshakeInterceptorTest.java` | 3 | JWT WebSocket handshake (existing) |
| `RabbitConfigTest.java` | 3 | RabbitMQ config (existing) |

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Gateway routes /api/push/** to notification-web:9094 | INFRA-02 | Requires running Gateway + notification-web containers | `curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/push/vapid-public-key` — expect 200 with Base64 key |
| End-to-end push delivery | PUSH-04 | Requires browser + VAPID keys + Docker stack | Subscribe browser, trigger lesson.started, expect notification |
| VAPID key persistence | PUSH-01 | Requires container restart | Verify key unchanged after restart (env-var based) |

---

## Validation Sign-Off

- [x] All tasks have automated verify or justified manual-only
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 requirements all resolved (tests created during execution)
- [x] No watch-mode flags
- [x] Feedback latency < 15s (~10s actual)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** complete

---

## Validation Audit 2026-04-05

| Metric | Count |
|--------|-------|
| Requirements audited | 8 |
| Automated coverage | 7/8 (PUSH-01..07) |
| Manual-only | 1/8 (INFRA-02) |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Total test methods | 47 |

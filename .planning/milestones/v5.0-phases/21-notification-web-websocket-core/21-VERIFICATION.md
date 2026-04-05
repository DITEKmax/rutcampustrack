---
phase: 21-notification-web-websocket-core
verified: 2026-04-05T00:00:00Z
status: human_needed
score: 5/5 must-haves verified
human_verification:
  - test: "Verify non-headman cannot receive headman-only events in practice"
    expected: "A student (is_headman=false) who subscribes to /topic/group/{groupId}/headman should NOT receive excuse.requested or late_checkin.requested events — server should block unauthorized subscriptions"
    why_human: "The implementation routes headman-only events to /topic/group/{groupId}/headman but does NOT enforce subscription access control on that topic. The is_headman session attribute is extracted and stored at handshake but never consulted when deciding who may subscribe. A non-headman client that knowingly subscribes to /topic/group/42/headman will receive those events. Whether this is an acceptable architecture trade-off (RESEARCH.md acknowledges it) or a WS-05/WS-06 requirement gap requires a human decision."
  - test: "Confirm group isolation works at the broker level for WS-02 SC-2"
    expected: "Clients of group 43 receive no events when a lesson.started fires for group 42 — topic routing must provide actual isolation"
    why_human: "Unit tests verify correct destination string construction. Cannot verify actual broker-level isolation (subscriber fan-out to /topic/group/42 only reaching group 42 subscribers) without a running broker and two connected clients from different groups."
---

# Phase 21: Notification Web — WebSocket Core Verification Report

**Phase Goal:** Web panel users can receive real-time event pushes over WebSocket — authenticated at handshake via JWT, routed exclusively to their group's topic, receiving structured messages for all 5 event types
**Verified:** 2026-04-05
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A client providing a valid JWT at handshake gets session attributes set (user_id, group_id, role, is_headman) and connection is accepted | ✓ VERIFIED | `JwtHandshakeInterceptor.beforeHandshake` puts user_id/group_id/role/is_headman into attributes map and returns true; 2 passing unit tests confirm (validJwt_setsSessionAttributes, validJwt_headmanTrue) |
| 2 | A client providing an invalid or missing JWT is rejected at HTTP Upgrade level with 401 | ✓ VERIFIED | `beforeHandshake` sets `HttpStatus.UNAUTHORIZED` and returns false on null token or JwtException; 4 unit tests confirm (missingToken, invalidToken, expiredToken, nullQuery) |
| 3 | STOMP endpoint /ws is registered with SockJS fallback and simple broker on /topic | ✓ VERIFIED | `WebSocketConfig.java` has `@EnableWebSocketMessageBroker`, `.addEndpoint("/ws").withSockJS()`, `config.enableSimpleBroker("/topic")`; `setApplicationDestinationPrefixes` absent (correct) |
| 4 | When lesson.started, lesson.cancelled, homework.published events arrive for group 42, they are pushed to /topic/group/42 | ✓ VERIFIED | `EventConsumer` routes non-headman events to `/topic/group/{groupId}`; 3 unit tests confirm routing and {type, payload} envelope format |
| 5 | When excuse.requested or late_checkin.requested arrive for group 42, they are pushed to /topic/group/42/headman (not /topic/group/42) | ✓ VERIFIED | `HEADMAN_ONLY_EVENTS = Set.of("excuse.requested", "late_checkin.requested")`, routes to `/topic/group/{groupId}/headman`; 2 unit tests confirm; 2 tests verify NOT routed to `/topic/group/42` |

**Score:** 5/5 truths verified

---

### Roadmap Success Criteria

| # | Success Criterion | Status | Evidence |
|---|------------------|--------|----------|
| SC-1 | A client can connect to the STOMP WebSocket endpoint by providing a valid JWT at handshake — an invalid or missing token is rejected before the connection is established | ✓ VERIFIED | HandshakeInterceptor rejects at HTTP Upgrade level (returns false, sets 401); 6 JwtHandshakeInterceptorTest tests pass |
| SC-2 | When a lesson.started event arrives for group 42, connected clients of group 42 receive a WebSocket push and clients of group 43 receive nothing | ? HUMAN NEEDED | Routing to `/topic/group/42` is verified; broker-level isolation between groups needs live connection test |
| SC-3 | A client whose JWT expires while connected continues to receive pushes — group_id and user_id extracted at handshake, not re-validated | ✓ VERIFIED | `afterHandshake` is a no-op (D-03); no re-validation in EventConsumer; session attributes store claims at handshake only |
| SC-4 | When a lesson is cancelled, all connected group members receive the cancellation push in real time | ✓ VERIFIED | `lesson.cancelled` routes to `/topic/group/{groupId}`; unit test `lessonCancelled_routesToGroupTopic` passes |
| SC-5 | When homework is published, all connected group members receive it; when an excuse is requested, only the headman's session receives the excuse push | ? HUMAN NEEDED | Routing to correct topics verified; headman-only enforcement relies on client subscribing to the right topic — server does NOT block non-headman from subscribing to `/headman` topic (see note below) |

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/notification-web/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java` | STOMP endpoint registration with SockJS and HandshakeInterceptor | ✓ VERIFIED | Contains `@EnableWebSocketMessageBroker`, `implements WebSocketMessageBrokerConfigurer`, `.addEndpoint("/ws")`, `.addInterceptors(jwtHandshakeInterceptor)`, `.withSockJS()`, `enableSimpleBroker("/topic")` |
| `services/notification-web/src/main/java/ru/rutcampustrack/notification/config/JwtHandshakeInterceptor.java` | JWT validation at WebSocket handshake, claims extraction to session attributes | ✓ VERIFIED | Contains `implements HandshakeInterceptor`, `@PostConstruct` key loading, `attributes.put("user_id", Long.parseLong(...))`, `((Number) claims.get("group_id")).longValue()`, `response.setStatusCode(HttpStatus.UNAUTHORIZED)` |
| `services/notification-web/src/test/java/ru/rutcampustrack/notification/config/JwtHandshakeInterceptorTest.java` | Unit tests for JWT handshake interceptor | ✓ VERIFIED | Contains `class JwtHandshakeInterceptorTest`, 6 `@Test` methods covering valid JWT, headman flag, missing token, invalid token, expired token, null query |
| `services/notification-web/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java` | RabbitMQ-to-WebSocket routing with headman filtering | ✓ VERIFIED | Contains `SimpMessagingTemplate`, `HEADMAN_ONLY_EVENTS`, `@RabbitListener(queues = "notification-web.events")`, `convertAndSend(destination, wsMessage)` |
| `services/notification-web/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java` | Unit tests for all 5 event types + error cases | ✓ VERIFIED | Contains `class EventConsumerTest`, 9 `@Test` methods covering all 5 event types, 3 error cases, and unknown event type |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `WebSocketConfig.java` | `JwtHandshakeInterceptor.java` | `addInterceptors(jwtHandshakeInterceptor)` | ✓ WIRED | Line 35 of WebSocketConfig: `.addInterceptors(jwtHandshakeInterceptor)` |
| `JwtHandshakeInterceptor.java` | `application.yml` | `notification.jwt.public-key-path` property | ✓ WIRED | `@Value("${notification.jwt.public-key-path}")` in interceptor; `notification.jwt.public-key-path: ${JWT_PUBLIC_KEY_PATH:/keys/public.key}` in application.yml |
| `EventConsumer.java` | `SimpMessagingTemplate` | `convertAndSend` to `/topic/group/...` | ✓ WIRED | Line 56: `messagingTemplate.convertAndSend(destination, wsMessage)` where destination is built from groupId |
| `EventConsumer.java` | RabbitMQ queue `notification-web.events` | `@RabbitListener` | ✓ WIRED | Line 26: `@RabbitListener(queues = "notification-web.events")` |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `EventConsumer.java` | `envelope` (Map from RabbitMQ) | `@RabbitListener` consumer from queue `notification-web.events` | Yes — live RabbitMQ events; no hardcoded/empty data; fallback is null-guard + log+return | ✓ FLOWING |
| `JwtHandshakeInterceptor.java` | `claims` from JWT | `Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload()` | Yes — real JWT claims from token; public key loaded from file at `@PostConstruct` | ✓ FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — service requires RabbitMQ connection and a running WebSocket client to exercise live behavior. All code paths verified via unit tests instead.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| WS-01 | 21-01-PLAN.md | User can connect to STOMP WebSocket endpoint with JWT authentication at handshake | ✓ SATISFIED | JwtHandshakeInterceptor validates JWT, rejects at HTTP Upgrade; 6 unit tests pass |
| WS-02 | 21-02-PLAN.md | User receives real-time push when a lesson starts for their group | ✓ SATISFIED | EventConsumer routes `lesson.started` to `/topic/group/{groupId}`; unit test passes |
| WS-03 | 21-02-PLAN.md | User receives real-time push when a lesson is cancelled for their group | ✓ SATISFIED | EventConsumer routes `lesson.cancelled` to `/topic/group/{groupId}`; unit test passes |
| WS-04 | 21-02-PLAN.md | User receives real-time push when homework is published/updated for their group | ✓ SATISFIED | EventConsumer routes `homework.published` to `/topic/group/{groupId}`; unit test passes |
| WS-05 | 21-02-PLAN.md | Headman receives real-time push when a student requests an excuse in their group | ? NEEDS HUMAN | EventConsumer routes to `/topic/group/{groupId}/headman` — server does NOT enforce that only headman can subscribe to this topic; subscription access control is absent |
| WS-06 | 21-02-PLAN.md | Headman receives real-time push when a student requests late check-in in their group | ? NEEDS HUMAN | Same issue as WS-05 — routing to headman topic is correct but access control is not enforced |
| WS-07 | 21-01-PLAN.md | WebSocket messages are routed only to users of the relevant group (privacy) | ? NEEDS HUMAN | Topic naming provides group-level isolation in theory (simple broker delivers to topic subscribers); actual isolation between groups needs live broker test |

**Orphaned Requirements:** None — all 7 WS-0x requirements declared in plan frontmatter match REQUIREMENTS.md Phase 21 mapping.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `WebSocketConfig.java` | 34 | `setAllowedOriginPatterns("*")` | ⚠️ Warning | CORS wildcard — acceptable for development, must be restricted to web panel origin in production (acknowledged threat flag in SUMMARY.md) |
| `JwtHandshakeInterceptor.java` | 57 | `void setPublicKey(RSAPublicKey)` — package-private test backdoor | ℹ️ Info | Testing pattern; not a runtime concern; follows established project convention for injectable test mocks |

No stub indicators, empty returns, or placeholder comments found in any implementation file.

---

### Human Verification Required

#### 1. Headman-Only Subscription Enforcement (WS-05, WS-06)

**Test:** Connect two WebSocket clients to `/ws?token=<jwt>`:
- Client A: JWT with `is_headman=false`, subscribes to `/topic/group/42` AND `/topic/group/42/headman`
- Client B: JWT with `is_headman=true`, subscribes to `/topic/group/42` AND `/topic/group/42/headman`

Publish an `excuse.requested` event for group 42 to RabbitMQ.

**Expected per WS-05:** Only Client B (headman) should receive the event push. Client A should NOT receive it on `/topic/group/42/headman`.

**What the code actually does:** Both clients will receive the event on `/topic/group/42/headman` because the simple broker has no subscription-level access control. The `is_headman` session attribute is stored but never consulted to block non-headman subscriptions.

**Why human:** Requires a live WebSocket connection with two clients and a running RabbitMQ broker. Also requires a team decision: is the current architecture (honor-system client-side subscription) acceptable, or must server-side subscription interception (`ChannelInterceptor` or `SubscriptionRegistry` hook) be added to enforce this?

**Note:** RESEARCH.md (Pitfall 2, Pattern 4) explicitly discusses this trade-off and recommends the separate `/headman` topic approach as sufficient for the current architecture. If the team accepts this, WS-05/WS-06 are satisfied at the routing level. If strict enforcement is required, a `ChannelInterceptor` must be added to block subscription to `/topic/*/headman` for non-headman sessions.

#### 2. Group Isolation — Broker-Level Verification (WS-07, SC-2)

**Test:** Connect two WebSocket clients — Client A subscribed to `/topic/group/42`, Client B subscribed to `/topic/group/43`. Publish a `lesson.started` event for group 42.

**Expected:** Only Client A receives the push. Client B receives nothing.

**Why human:** Requires a running broker with real subscribers. Unit tests only verify the destination string passed to `convertAndSend`; they do not verify that the in-memory simple broker correctly routes to only group 42 subscribers.

---

### Gaps Summary

No automated gaps found. All 5 must-have truths verified. All 5 artifacts exist and are substantive. All 4 key links confirmed wired. No stubs or placeholder patterns detected in implementation files.

Two items require human verification before phase can be marked fully passed:

1. **WS-05/WS-06 headman enforcement** — the architecture uses a separate headman topic instead of per-session is_headman checks. This is documented in RESEARCH.md as an intentional design choice (Pitfall 2). However, the requirement text says "headman receives" implying non-headman should NOT receive these events. Whether the separate-topic approach satisfies this without subscription enforcement requires team sign-off.

2. **Broker-level group isolation** — topic naming implies isolation but requires a live test to confirm.

---

_Verified: 2026-04-05_
_Verifier: Claude (gsd-verifier)_

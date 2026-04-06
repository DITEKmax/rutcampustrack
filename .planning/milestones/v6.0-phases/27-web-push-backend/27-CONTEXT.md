# Phase 27: Web Push Backend - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

notification-web can generate VAPID keys, store push subscriptions, and deliver Web Push notifications for lesson and homework events. This phase adds Web Push infrastructure to the existing notification-web service. No frontend changes — PWA subscription UI is a later phase.

</domain>

<decisions>
## Implementation Decisions

### VAPID Key Management
- **D-01:** VAPID keys stored as environment variables (VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT) in docker-compose.yml
- **D-02:** Keys pre-generated externally (openssl/script), not auto-generated at startup
- **D-03:** application.yml reads via `${VAPID_PUBLIC_KEY}`, `${VAPID_PRIVATE_KEY}`, `${VAPID_SUBJECT:mailto:noreply@rut.ru}`

### Push Subscription Storage
- **D-04:** Push subscriptions stored in MongoDB, reusing existing attendance_db (same MongoDB container, same database)
- **D-05:** Collection `push_subscriptions` with fields: userId, endpoint, keys.p256dh, keys.auth, groupId, createdAt
- **D-06:** Spring Data MongoDB dependency added to notification-web (notification-app after restructure)

### Push Delivery
- **D-07:** EventConsumer calls PushService.sendToGroup() asynchronously (@Async / CompletableFuture) after STOMP routing
- **D-08:** Push errors do not block STOMP delivery — parallel execution
- **D-09:** Only 3 event types trigger Web Push: lesson.started, lesson.cancelled, homework.published (PUSH-04/05/06)
- **D-10:** HTTP 410 Gone from push service triggers automatic subscription deletion from MongoDB (PUSH-07)

### REST API & Module Structure
- **D-11:** Create notification-api-contract module (java-library) with PushApi interface, request/response DTOs, Swagger annotations
- **D-12:** Restructure: notification-web/ becomes notification-service/notification-app/ + notification-service/notification-api-contract/
- **D-13:** Update settings.gradle.kts, docker-compose.yml build path, Dockerfile path accordingly
- **D-14:** notification-bot/ stays as-is (no changes)

### Authorization
- **D-15:** Push endpoints go through API Gateway — JWT validated by Gateway, X-User-Id/X-User-Role/X-Group-Id headers injected
- **D-16:** @RequireRole(STUDENT) on all push endpoints (subscribe, unsubscribe, vapid-public-key)
- **D-17:** AOP @RequireRole infrastructure added to notification-app (same pattern as academic/schedule/attendance services)
- **D-18:** Add Gateway route for /api/push/** to notification-web (INFRA-02)

### Claude's Discretion
- Web Push Java library choice (webpush-java, bouncy-castle raw, etc.)
- Push notification payload format (title, body, icon, action buttons)
- MongoDB indexes on push_subscriptions collection
- @Async thread pool configuration
- Error retry strategy for transient push failures (non-410)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Notification-web service (current state)
- `services/notification-web/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java` — Current RabbitMQ event consumer, add push delivery hook here
- `services/notification-web/src/main/java/ru/rutcampustrack/notification/config/RabbitConfig.java` — Queue/exchange setup
- `services/notification-web/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java` — STOMP config, CORS, JWT handshake
- `services/notification-web/src/main/java/ru/rutcampustrack/notification/config/JwtHandshakeInterceptor.java` — JWT claims extraction pattern
- `services/notification-web/build.gradle.kts` — Current dependencies, add MongoDB + Web Push lib
- `services/notification-web/src/main/resources/application.yml` — Add MongoDB URI + VAPID config

### Contract-first pattern (follow this)
- `services/academic-service/academic-api-contract/` — Reference api-contract module structure
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/` — @RequireRole AOP pattern to replicate

### API Gateway routing
- `services/api-gateway/src/main/resources/application.yml` — Add /api/push/** route

### Infrastructure
- `docker-compose.yml` — Update notification-web build path, add VAPID env vars
- `settings.gradle.kts` — Register new notification-api-contract and notification-app modules

### Event schemas
- `event-schemas/lesson.started.json` — Payload for PUSH-04
- `event-schemas/lesson.cancelled.json` — Payload for PUSH-05
- `event-schemas/homework.published.json` — Payload for PUSH-06

### Requirements
- `.planning/REQUIREMENTS.md` §Web Push Backend — PUSH-01..07, INFRA-02

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **EventConsumer**: Already receives all RabbitMQ events and routes by groupId — hook push delivery here
- **JwtHandshakeInterceptor**: JWT parsing/validation pattern — but REST endpoints use Gateway headers instead
- **@RequireRole AOP**: Exists in academic/schedule/attendance services — copy to notification-app
- **RabbitConfig**: DLQ pattern already set up — no changes needed for push
- **MongoDB Spring Data**: attendance-service already uses it — same dependency pattern

### Established Patterns
- **Contract-first**: api-contract (java-library) + app (Spring Boot) in all business services
- **Gateway auth**: JWT → X-User-Id/X-User-Role headers — all services read headers, not tokens
- **@RequireRole**: AOP annotation checking X-User-Role header — replicate from attendance-service
- **MongoDB entities**: @Document, @Id with String type, Spring Data repositories

### Integration Points
- **EventConsumer.onEvent()**: Add async PushService call after STOMP convertAndSend
- **API Gateway routes**: Add /api/push/** → notification-web:9094 route
- **docker-compose.yml**: Update build context path, add VAPID env vars, add MONGODB_URI env var
- **settings.gradle.kts**: Register notification-api-contract and notification-app subprojects

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 27-web-push-backend*
*Context gathered: 2026-04-05*

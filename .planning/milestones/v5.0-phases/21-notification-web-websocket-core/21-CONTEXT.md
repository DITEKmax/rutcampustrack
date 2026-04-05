# Phase 21: Notification Web — WebSocket Core - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Real-time event push to web panel users over STOMP WebSocket. JWT authentication at handshake, group-based topic routing, structured messages for all 5 event types (lesson.started, lesson.cancelled, homework.published, excuse.requested, late_checkin.requested). notification-web stays stateless — no gRPC calls, no enrichment.

</domain>

<decisions>
## Implementation Decisions

### JWT Authentication at Handshake
- **D-01:** Public key for JWT validation obtained via shared file/environment variable — same pattern as API Gateway (`PublicKeyConfig.java`). No JWKS endpoint, no runtime dependency on Auth Service.
- **D-02:** Client passes JWT token as query parameter `?token=xxx` during WebSocket connection. Intercepted in `HandshakeInterceptor`. Browser WebSocket API doesn't support custom headers, so query param is standard.
- **D-03:** At handshake, extract `user_id`, `group_id`, `role`, `is_headman` from JWT claims into WebSocket session attributes. These are NOT re-validated — a client whose JWT expires mid-session continues receiving pushes (per success criteria #3).

### STOMP Topic Structure
- **D-04:** Single topic per group: `/topic/group/{groupId}`. All 5 event types pushed to this topic. Client subscribes to one topic based on their group.
- **D-05:** Headman-only events (`excuse.requested`, `late_checkin.requested`) are filtered server-side before sending. Server checks `is_headman` from session attributes. Non-headman clients never see these events. No separate headman topic.

### WebSocket Message Format
- **D-06:** Forward RabbitMQ event payload as-is to WebSocket clients. Wrap in `{type: "event_type", payload: {...}}` envelope. No enrichment (no subject name resolution, no teacher name lookup). notification-web is a stateless event forwarder.
- **D-07:** Message format matches event-schemas/ JSON Schema definitions. Client-side already knows the schemas.

### Session & Connection Lifecycle
- **D-08:** No limit on concurrent WebSocket sessions per user. Multiple tabs/devices all receive events. For ~500-5000 university users, connection count is not a concern.
- **D-09:** STOMP endpoint uses SockJS fallback (`withSockJS()`). Provides transport fallback for proxies that block WebSocket. Endpoint path: `/ws`.
- **D-10:** Heartbeat config: use Spring's default STOMP heartbeat (10s server, 10s client). No custom heartbeat tuning needed for this scale.

### Claude's Discretion
- WebSocketConfig class structure and bean naming
- GroupSessionRegistry implementation details (ConcurrentHashMap vs other)
- EventConsumer → SimpMessagingTemplate routing implementation
- Error handling for malformed events
- Test strategy (unit vs integration, embedded broker vs mock)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### JWT Authentication (reference implementation)
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/config/PublicKeyConfig.java` — Public key loading pattern to replicate
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java` — JWT validation and claim extraction
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/JwtService.java` — JWT claims structure (role, group_id, is_headman)

### RabbitMQ Consumer (already implemented in Phase 20)
- `services/notification-web/src/main/java/ru/rutcampustrack/notification/config/RabbitConfig.java` — Queue/exchange/DLQ beans
- `services/notification-web/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java` — Placeholder consumer to enhance with routing

### Event Schemas (all 5 event types this phase handles)
- `event-schemas/lesson.started.json` — group_id, subject_id, teacher_id, lesson_number, start_time, end_time, room
- `event-schemas/lesson.cancelled.json` — group_id, subject_id, date, cancel_reason
- `event-schemas/homework.published.json` — group_id, subject_id, title, has_link
- `event-schemas/excuse.requested.json` — user_id, group_id, excuse_type, ticket_id, lesson_ids, has_attachments
- `event-schemas/late_checkin.requested.json` — user_id, group_id, lesson_id, student_name, lesson_date

### Existing notification-web
- `services/notification-web/build.gradle.kts` — Already has starter-websocket, starter-amqp, starter-actuator
- `services/notification-web/src/main/resources/application.yml` — RabbitMQ config, actuator, port 9094

### Architecture
- `docs/architecture.md` — Service topology, communication patterns
- `docs/phases-plan.md` — Detailed phase descriptions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PublicKeyConfig.java` (API Gateway): Public key loading from file/env — copy pattern for notification-web
- `JwtAuthenticationFilter.java` (API Gateway): JWT parsing with jjwt library — adapt for HandshakeInterceptor
- `RabbitConfig.java` (notification-web): Already configured queue/exchange beans
- `EventConsumer.java` (notification-web): Placeholder `@RabbitListener` — enhance to route events to WebSocket topics

### Established Patterns
- RS256 JWT signing (Auth Service) with public key verification (API Gateway)
- JWT claims: `role` (String), `group_id` (Long), `is_headman` (boolean)
- RabbitMQ: Fanout exchange `rut-uit.events`, per-service durable queues
- Event envelope: `{event_type, event_id, occurred_at, payload}`

### Integration Points
- `EventConsumer.onEvent()` — Currently logs events. Phase 21 adds `SimpMessagingTemplate.convertAndSend()` to route to `/topic/group/{groupId}`
- `build.gradle.kts` — May need `jjwt` dependency for JWT parsing (check if API Gateway's dependency can be referenced)
- `application.yml` — Add JWT public key path config

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follow established patterns from API Gateway (JWT) and Spring WebSocket STOMP documentation.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 21-notification-web-websocket-core*
*Context gathered: 2026-04-05*

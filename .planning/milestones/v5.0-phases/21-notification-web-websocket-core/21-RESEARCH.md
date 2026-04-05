# Phase 21: Notification Web — WebSocket Core - Research

**Researched:** 2026-04-05
**Domain:** Spring Boot 3.4 STOMP WebSocket, JWT HandshakeInterceptor, RabbitMQ-to-WebSocket routing
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Public key for JWT validation obtained via shared file/environment variable — same pattern as API Gateway (`PublicKeyConfig.java`). No JWKS endpoint, no runtime dependency on Auth Service.
- **D-02:** Client passes JWT token as query parameter `?token=xxx` during WebSocket connection. Intercepted in `HandshakeInterceptor`. Browser WebSocket API doesn't support custom headers, so query param is standard.
- **D-03:** At handshake, extract `user_id`, `group_id`, `role`, `is_headman` from JWT claims into WebSocket session attributes. These are NOT re-validated — a client whose JWT expires mid-session continues receiving pushes.
- **D-04:** Single topic per group: `/topic/group/{groupId}`. All 5 event types pushed to this topic. Client subscribes to one topic based on their group.
- **D-05:** Headman-only events (`excuse.requested`, `late_checkin.requested`) are filtered server-side before sending. Server checks `is_headman` from session attributes. Non-headman clients never see these events. No separate headman topic.
- **D-06:** Forward RabbitMQ event payload as-is to WebSocket clients. Wrap in `{type: "event_type", payload: {...}}` envelope. No enrichment. notification-web is a stateless event forwarder.
- **D-07:** Message format matches event-schemas/ JSON Schema definitions.
- **D-08:** No limit on concurrent WebSocket sessions per user. Multiple tabs/devices all receive events.
- **D-09:** STOMP endpoint uses SockJS fallback (`withSockJS()`). Endpoint path: `/ws`.
- **D-10:** Heartbeat: use Spring's default STOMP heartbeat (10s server, 10s client). No custom tuning.

### Claude's Discretion

- WebSocketConfig class structure and bean naming
- GroupSessionRegistry implementation details (ConcurrentHashMap vs other)
- EventConsumer → SimpMessagingTemplate routing implementation
- Error handling for malformed events
- Test strategy (unit vs integration, embedded broker vs mock)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WS-01 | User can connect to STOMP WebSocket endpoint with JWT authentication at handshake | HandshakeInterceptor pattern documented; JWT parsing via jjwt 0.12.6 (already in API Gateway) |
| WS-02 | User receives real-time push when a lesson starts for their group | SimpMessagingTemplate.convertAndSend to `/topic/group/{groupId}`; EventConsumer routes `lesson.started` events |
| WS-03 | User receives real-time push when a lesson is cancelled for their group | Same routing path as WS-02; `lesson.cancelled` event schema has `group_id` in payload |
| WS-04 | User receives real-time push when homework is published/updated for their group | `homework.published` event schema has `group_id` in payload; standard routing |
| WS-05 | Headman receives real-time push when a student requests an excuse in their group | `excuse.requested` payload has `group_id`; filter by `is_headman=true` from session attributes before sending |
| WS-06 | Headman receives real-time push when a student requests late check-in in their group | `late_checkin.requested` payload has `group_id`; same headman filter as WS-05 |
| WS-07 | WebSocket messages are routed only to users of the relevant group (privacy) | Simple broker delivers to `/topic/group/{groupId}` subscribers only — group isolation is inherent in topic naming |
</phase_requirements>

---

## Summary

Phase 21 adds Spring STOMP WebSocket support to `notification-web`, turning the existing RabbitMQ consumer placeholder into a live event forwarder. The work breaks into three orthogonal pieces: (1) WebSocket infrastructure config (`@EnableWebSocketMessageBroker` with SockJS and in-memory simple broker), (2) JWT authentication at HTTP upgrade via `HandshakeInterceptor` that rejects invalid tokens and stores claims in session attributes, and (3) the `EventConsumer` routing logic that reads `group_id` from each RabbitMQ event and calls `SimpMessagingTemplate.convertAndSend("/topic/group/{groupId}", envelope)` — with a headman guard for `excuse.requested` and `late_checkin.requested`.

All libraries are already present in the project. `spring-boot-starter-websocket` is already declared in `notification-web/build.gradle.kts`. The jjwt dependency (`io.jsonwebtoken:jjwt-api:0.12.6` + impl + jackson) is already used by `api-gateway` and needs to be copied to `notification-web`. The public key loading must follow the **file-based** pattern from `JwtService.java` (Auth Service), not the HTTP-based pattern from `PublicKeyConfig.java` (API Gateway) — notification-web mounts the same key file, not calls Auth Service.

**Primary recommendation:** Use `HandshakeInterceptor` (not `ChannelInterceptor` on CONNECT frame) for authentication — this rejects at the HTTP Upgrade level before any WebSocket session is created, which matches decision D-02 and the success criterion #1.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-websocket | 3.4.1 (managed) | STOMP over WebSocket, simple broker, `SimpMessagingTemplate` | Already in build.gradle.kts; Spring's official WebSocket+STOMP stack |
| io.jsonwebtoken:jjwt-api | 0.12.6 | JWT parsing in HandshakeInterceptor | Already in api-gateway; same version; RS256/RSA public key verify |
| io.jsonwebtoken:jjwt-impl | 0.12.6 | jjwt runtime | Runtime dep for above |
| io.jsonwebtoken:jjwt-jackson | 0.12.6 | JSON parsing for JWT claims | Runtime dep for above |
| spring-boot-starter-amqp | 3.4.1 (managed) | RabbitMQ @RabbitListener | Already in build.gradle.kts |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-starter-test | 3.4.1 (managed) | JUnit 5 + AssertJ + Mockito | Unit tests for HandshakeInterceptor and EventConsumer |
| Jackson ObjectMapper | managed via Spring | JSON serialization of WS message envelope | Reuse Spring-managed ObjectMapper (already in RabbitConfig pattern) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Simple in-memory broker | STOMP broker relay to RabbitMQ | Relay adds complexity and a second RabbitMQ connection; unnecessary for single-instance VPS at this scale |
| HandshakeInterceptor | ChannelInterceptor on STOMP CONNECT frame | CONNECT-frame approach authenticates after WebSocket is open (connection established before rejection); HandshakeInterceptor rejects at HTTP level — stricter and matches D-02 |
| Query param `?token=xxx` | SockJS transport header workaround | Browser WebSocket API cannot set custom headers; query param is the only standard option for SockJS/WebSocket |

**Installation — add to `notification-web/build.gradle.kts`:**
```kotlin
// JWT validation (same as api-gateway)
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```
`spring-boot-starter-websocket` is already present — no additional install needed.

---

## Architecture Patterns

### Recommended Package Structure

```
services/notification-web/src/main/java/ru/rutcampustrack/notification/
├── config/
│   ├── RabbitConfig.java          # existing — no changes
│   ├── WebSocketConfig.java       # NEW — @EnableWebSocketMessageBroker, SockJS, HandshakeInterceptor
│   └── JwtHandshakeInterceptor.java  # NEW — JWT validation, session attribute injection
├── event/
│   └── EventConsumer.java         # MODIFY — add SimpMessagingTemplate routing
└── NotificationWebApplication.java  # existing — no changes
```

No `security/` package — this service does NOT use Spring Security. JWT validation is done manually in `JwtHandshakeInterceptor` only.

### Pattern 1: WebSocket Configuration with STOMP + SockJS

**What:** `@EnableWebSocketMessageBroker` configures the in-memory simple broker for `/topic/**` destinations, registers the `/ws` STOMP endpoint with SockJS fallback, and attaches the `JwtHandshakeInterceptor`.

**When to use:** Single-instance deployment, ~500-5000 users, no clustering needed.

```java
// Source: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // No /app prefix needed — this service never receives messages from clients
        config.enableSimpleBroker("/topic");
    }
}
```

### Pattern 2: JWT HandshakeInterceptor

**What:** Intercepts the HTTP Upgrade request, extracts `?token=` query parameter, validates with RSA public key (jjwt 0.12.x), stores claims in session attributes map, returns `false` (HTTP 403) on failure.

**When to use:** Any WebSocket endpoint requiring pre-connection authentication.

```java
// Source: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/socket/server/HandshakeInterceptor.html
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    // publicKey loaded from file (same pattern as JwtService in auth-service)
    // — NOT via HTTP call to Auth Service (D-01)

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        String token = extractTokenFromQuery(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            attributes.put("user_id", Long.parseLong(claims.getSubject()));
            attributes.put("group_id", claims.get("group_id", Integer.class));
            attributes.put("role",     claims.get("role", String.class));
            attributes.put("is_headman", Boolean.TRUE.equals(claims.get("is_headman", Boolean.class)));
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res,
                                WebSocketHandler handler, Exception ex) {}

    private String extractTokenFromQuery(ServerHttpRequest request) {
        String query = request.getURI().getQuery();  // "token=eyJ..."
        if (query == null) return null;
        for (String part : query.split("&")) {
            if (part.startsWith("token=")) return part.substring(6);
        }
        return null;
    }
}
```

**Critical note:** The `group_id` claim in the JWT is stored as a JSON number. jjwt deserializes it as `Integer` (not `Long`) when using `claims.get("group_id", Integer.class)`. Use `Integer` then widen to `Long` if needed for topic path construction. Verify claim type by checking `JwtService.generateAccessToken()` — it sets `claim("group_id", user.getGroupId())` where `getGroupId()` is `Long`. Jackson may deserialize as Integer for small values. Use `Number` cast to be safe: `((Number) claims.get("group_id")).longValue()`.

### Pattern 3: Public Key Loading (file-based, not HTTP)

**What:** Notification-web mounts the Auth Service key file at a known path (Docker volume). Loads once at startup. Uses the same PEM parsing as `JwtService.java`.

**When to use:** Services that need JWT verification but do NOT call Auth Service at runtime.

```java
// Source: services/auth-service/src/main/java/ru/rutcampustrack/auth/service/JwtService.java (adapted)
@PostConstruct
public void init() throws Exception {
    String keyPath = env.getProperty("notification.jwt.public-key-path");
    Path path = Paths.get(keyPath);
    String pem = Files.readString(path);
    String stripped = pem
            .replaceAll("-----BEGIN [A-Z ]+-----", "")
            .replaceAll("-----END [A-Z ]+-----", "")
            .replaceAll("\\s", "");
    byte[] keyBytes = Base64.getDecoder().decode(stripped);
    this.publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(keyBytes));
}
```

Add to `application.yml`:
```yaml
notification:
  jwt:
    public-key-path: ${JWT_PUBLIC_KEY_PATH:/keys/public.key}
```

### Pattern 4: EventConsumer — RabbitMQ to WebSocket Routing

**What:** Enhances the existing `@RabbitListener` to extract `group_id` from event payload, build the topic path, and call `SimpMessagingTemplate.convertAndSend`. For headman-only events, sends only when `is_headman` check passes — but since `SimpMessagingTemplate` broadcasts to all topic subscribers, headman filtering must happen **differently**: either a separate headman topic or the headman subscribes to a different destination. See Pitfall 2 below.

**When to use:** Every RabbitMQ event that must reach WebSocket clients.

```java
// Source: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-send.html
@Component
@Slf4j
public class EventConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    public EventConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(queues = "notification-web.events")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("Event without event_type, ignoring: {}", envelope);
            return;
        }
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        if (payload == null) {
            log.warn("Event without payload, ignoring: {}", envelope);
            return;
        }

        Number groupIdNum = (Number) payload.get("group_id");
        if (groupIdNum == null) {
            log.debug("Event {} has no group_id, skipping WebSocket routing", eventType);
            return;
        }
        long groupId = groupIdNum.longValue();

        // Headman-only events go to /topic/group/{groupId}/headman
        boolean headmanOnly = "excuse.requested".equals(eventType)
                || "late_checkin.requested".equals(eventType);

        String destination = headmanOnly
                ? "/topic/group/" + groupId + "/headman"
                : "/topic/group/" + groupId;

        Map<String, Object> wsMessage = Map.of("type", eventType, "payload", payload);
        messagingTemplate.convertAndSend(destination, wsMessage);
        log.debug("Routed {} to {}", eventType, destination);
    }
}
```

**Important architectural note on headman filtering (D-05):** D-05 states filtering happens server-side and "non-headman clients never see these events." Since `SimpMessagingTemplate.convertAndSend` broadcasts to ALL subscribers of a destination, broadcasting `excuse.requested` to `/topic/group/{groupId}` would expose it to non-headman students. The correct approach is a **separate topic** for headman-only events: `/topic/group/{groupId}/headman`. The headman client subscribes to both `/topic/group/{groupId}` and `/topic/group/{groupId}/headman`. Regular students only subscribe to `/topic/group/{groupId}`. This achieves the D-05 privacy requirement without any per-session session-attributes lookup during publish time (which is not straightforwardly available from `EventConsumer`).

This is a **Claude's Discretion** implementation detail — the decision says "server checks `is_headman` from session attributes" but the mechanism must be a separate topic, since the simple broker fan-out model doesn't support per-subscriber filtering.

### Anti-Patterns to Avoid

- **Spring Security for WebSocket auth:** Do NOT add `spring-security-messaging` or `@EnableWebSocketSecurity`. This service has no Spring Security dependency and doesn't need it — manual `HandshakeInterceptor` JWT check is simpler and complete.
- **ChannelInterceptor for auth:** Using a `ChannelInterceptor` on STOMP CONNECT to validate JWT means the WebSocket connection opens before rejection. Use `HandshakeInterceptor` instead — rejects at HTTP Upgrade.
- **Re-validating JWT on every message:** D-03 explicitly forbids this. Claims are read once at handshake and stored in session attributes.
- **Blocking the RabbitMQ consumer thread:** `SimpMessagingTemplate.convertAndSend` is non-blocking for the in-memory simple broker. No additional async wrapper needed.
- **Setting `setApplicationDestinationPrefixes`:** Not needed — this service never receives messages FROM clients via STOMP SEND frames. The `/app` prefix is only relevant when clients send messages to server-side handlers.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SockJS fallback transports | Custom HTTP long-poll/SSE fallback | `.withSockJS()` in `registerStompEndpoints` | SockJS handles 6+ transport fallbacks with browser detection |
| Topic pub/sub fan-out | Manual session registry iteration | `SimpMessagingTemplate.convertAndSend("/topic/...")` + simple broker | Spring's in-memory broker maintains subscription registry and handles delivery |
| STOMP protocol framing | Raw WebSocket message serialization | `@EnableWebSocketMessageBroker` + `SimpMessagingTemplate` | STOMP framing, heartbeats, ACKs handled by framework |
| RSA JWT parsing | Custom Base64/ASN1 key parsing | jjwt 0.12.x `Jwts.parser().verifyWith(pubKey).build()` | jjwt handles signature verification, expiry, claim extraction |

**Key insight:** The in-memory broker makes this architecture deployable with zero additional infrastructure — no external STOMP broker, no Redis pub/sub, no additional containers. `SimpMessagingTemplate.convertAndSend` is the single integration point between RabbitMQ consumer and WebSocket delivery.

---

## Common Pitfalls

### Pitfall 1: group_id claim type mismatch (Integer vs Long)
**What goes wrong:** `claims.get("group_id", Long.class)` throws `RequiredTypeException` because jjwt deserializes small JSON numbers as `Integer`, not `Long`.
**Why it happens:** JWT payload JSON uses untyped numbers. For values fitting in 32 bits, Jackson assigns `Integer`.
**How to avoid:** Use `((Number) claims.get("group_id")).longValue()` — works for both Integer and Long.
**Warning signs:** `RequiredTypeException: Expected value to be of type: java.lang.Long, but was Integer` in logs.

### Pitfall 2: Headman filtering via convertAndSend to group topic
**What goes wrong:** Publishing `excuse.requested` to `/topic/group/42` delivers it to ALL subscribers including non-headman students — violating WS-05 privacy.
**Why it happens:** Simple broker fan-out sends to all topic subscribers regardless of who they are.
**How to avoid:** Use a separate destination `/topic/group/{groupId}/headman` for headman-only events. Headman client subscribes to both topics. Regular students subscribe only to `/topic/group/{groupId}`.
**Warning signs:** Students receiving `excuse.requested` or `late_checkin.requested` events in the browser console.

### Pitfall 3: SockJS info endpoint interference
**What goes wrong:** SockJS sends an HTTP GET to `/ws/info` before upgrading — this goes through any global filter chain. API Gateway must proxy it correctly.
**Why it happens:** SockJS transport negotiation requires HTTP endpoints at `/ws/info`, `/ws/{server}/{session}/websocket`, etc.
**How to avoid:** Ensure API Gateway routes all `/ws/**` paths to notification-web, not just `/ws`. If notification-web is accessed directly (not via Gateway), no action needed for Phase 21.
**Warning signs:** SockJS client logs "can't connect" or "info endpoint failed."

### Pitfall 4: JWT query parameter in SockJS upgrade URLs
**What goes wrong:** SockJS upgrade URL is `/ws/{server}/{session}/websocket?token=xxx` — the `?token=` appears on different URL paths than plain WebSocket.
**Why it happens:** SockJS generates session-keyed paths. `request.getURI().getQuery()` still returns the token in all cases.
**How to avoid:** Parse query params from `request.getURI().getQuery()` (not from a specific path segment). Test with SockJS transport, not just raw WebSocket.
**Warning signs:** `HandshakeInterceptor` returns 401 for SockJS connections but passes for raw WebSocket.

### Pitfall 5: ObjectMapper bean conflict with RabbitConfig
**What goes wrong:** If `WebSocketConfig` or `JwtHandshakeInterceptor` creates a new `ObjectMapper`, it bypasses the Spring-managed one that has `JavaTimeModule` registered.
**Why it happens:** Same pitfall documented in Phase 20 RabbitConfig comments.
**How to avoid:** Inject `ObjectMapper` as a Spring bean (auto-configured by `JacksonAutoConfiguration`). Do not `new ObjectMapper()`.
**Warning signs:** `com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Java 8 date/time type not supported` when serializing WS message envelope.

### Pitfall 6: No setApplicationDestinationPrefixes leads to message routing failure
**What goes wrong:** If `setApplicationDestinationPrefixes` is accidentally set (e.g., `/app`), messages sent to `/topic/...` by `SimpMessagingTemplate` are intercepted by the application dispatcher instead of routed directly to the broker.
**Why it happens:** Application destination prefix intercepts matching messages before broker delivery.
**How to avoid:** Do NOT call `setApplicationDestinationPrefixes` in `configureMessageBroker`. The server only sends, never receives.
**Warning signs:** Clients subscribed to `/topic/group/42` receive nothing even though `convertAndSend("/topic/group/42", ...)` succeeds without exception.

---

## Code Examples

### Full HandshakeInterceptor with query param extraction

```java
// Source: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/socket/server/HandshakeInterceptor.html
// Combined with jjwt 0.12.x pattern from services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java

@Override
public boolean beforeHandshake(ServerHttpRequest request,
                                ServerHttpResponse response,
                                WebSocketHandler wsHandler,
                                Map<String, Object> attributes) {
    String rawQuery = request.getURI().getRawQuery();  // "token=eyJ..."
    String token = null;
    if (rawQuery != null) {
        for (String param : rawQuery.split("&")) {
            if (param.startsWith("token=")) {
                token = param.substring(6);
                break;
            }
        }
    }
    if (token == null) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
    try {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)          // RSA public key (loaded from file at startup)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        attributes.put("user_id",    Long.parseLong(claims.getSubject()));
        attributes.put("group_id",   ((Number) claims.get("group_id")).longValue());
        attributes.put("role",       claims.get("role", String.class));
        attributes.put("is_headman", Boolean.TRUE.equals(claims.get("is_headman", Boolean.class)));
        return true;
    } catch (JwtException | IllegalArgumentException e) {
        log.debug("WebSocket handshake rejected — invalid JWT: {}", e.getMessage());
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
}
```

### WebSocket message envelope format (D-06, D-07)

```json
{
  "type": "lesson.started",
  "payload": {
    "lesson_id": 101,
    "group_id": 42,
    "subject_id": 7,
    "teacher_id": 3,
    "lesson_number": 2,
    "start_time": "09:45",
    "end_time": "11:20",
    "room": "А-101"
  }
}
```

The `type` field maps to `event_type` from the RabbitMQ envelope. `payload` is the raw `payload` sub-object from the RabbitMQ event — no transformation.

### Topic destinations summary

| Event type | Destination | Who subscribes |
|------------|-------------|----------------|
| `lesson.started` | `/topic/group/{groupId}` | all group members |
| `lesson.cancelled` | `/topic/group/{groupId}` | all group members |
| `homework.published` | `/topic/group/{groupId}` | all group members |
| `excuse.requested` | `/topic/group/{groupId}/headman` | headman only |
| `late_checkin.requested` | `/topic/group/{groupId}/headman` | headman only |

---

## Environment Availability

> Step 2.6: Applies — external dependencies include RabbitMQ and JWT key file.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| RabbitMQ | `@RabbitListener` (EventConsumer) | ✓ | Running in docker-compose | — |
| JWT public key file | `JwtHandshakeInterceptor` | ✓ | `/keys/public.key` via Docker volume (from auth-service container) | — |
| `spring-boot-starter-websocket` | WebSocketConfig | ✓ | 3.4.1 (already in build.gradle.kts) | — |
| jjwt-api 0.12.6 | JwtHandshakeInterceptor | ✗ (not yet in notification-web) | — | Must add to build.gradle.kts |
| jjwt-impl 0.12.6 | JWT runtime | ✗ (not yet in notification-web) | — | Must add to build.gradle.kts |
| jjwt-jackson 0.12.6 | JWT JSON parsing | ✗ (not yet in notification-web) | — | Must add to build.gradle.kts |

**Missing dependencies with no fallback:**
- jjwt-api/impl/jackson — must be added to `notification-web/build.gradle.kts` (same 3-liner as api-gateway)

**Missing dependencies with fallback:**
- None

---

## Validation Architecture

> `workflow.nyquist_validation` is absent in config.json — treating as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito (spring-boot-starter-test, already in build.gradle.kts) |
| Config file | none — Spring Boot auto-detects |
| Quick run command | `./gradlew :services:notification-web:test` |
| Full suite command | `./gradlew :services:notification-web:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| WS-01 | Valid JWT at handshake → session attributes set, returns true | unit | `./gradlew :services:notification-web:test --tests "*.JwtHandshakeInterceptorTest"` | ❌ Wave 0 |
| WS-01 | Missing/invalid JWT at handshake → returns false (HTTP 401) | unit | `./gradlew :services:notification-web:test --tests "*.JwtHandshakeInterceptorTest"` | ❌ Wave 0 |
| WS-02 | `lesson.started` event → convertAndSend to `/topic/group/{groupId}` | unit | `./gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ Wave 0 |
| WS-03 | `lesson.cancelled` event → convertAndSend to `/topic/group/{groupId}` | unit | `./gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ Wave 0 |
| WS-04 | `homework.published` event → convertAndSend to `/topic/group/{groupId}` | unit | `./gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ Wave 0 |
| WS-05 | `excuse.requested` event → convertAndSend to `/topic/group/{groupId}/headman` | unit | `./gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ Wave 0 |
| WS-06 | `late_checkin.requested` event → convertAndSend to `/topic/group/{groupId}/headman` | unit | `./gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ Wave 0 |
| WS-07 | Routing uses group_id from payload, not hardcoded | unit | `./gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ Wave 0 |
| WS-01 | JWT expiry mid-session does NOT terminate delivery | manual | n/a — requires live session; validated by spec (no re-validation after handshake) | manual-only |

### Sampling Rate

- **Per task commit:** `./gradlew :services:notification-web:test`
- **Per wave merge:** `./gradlew :services:notification-web:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `services/notification-web/src/test/java/ru/rutcampustrack/notification/websocket/JwtHandshakeInterceptorTest.java` — covers WS-01
- [ ] `services/notification-web/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java` — covers WS-02 through WS-07

*(Existing `RabbitConfigTest.java` is sufficient for its scope — no changes needed to it.)*

---

## Project Constraints (from CLAUDE.md)

| Directive | Applies to This Phase |
|-----------|----------------------|
| No Lombok in contract modules (`*-api-contract`) | N/A — notification-web has no contract module |
| Lombok allowed in `*-app` | `@Slf4j` Lombok annotation is allowed in EventConsumer (already used) and new classes in notification-web |
| `@ControllerAdvice` for error handling | N/A — no REST controllers in this phase |
| HATEOAS Level 3 / RFC 7807 | N/A — WebSocket messages use a custom envelope format per D-06 |
| Flyway migrations / PostgreSQL rules | N/A — notification-web has no database |
| `UPPER_CASE` Java enums | N/A — no new enums in this phase |
| Soft delete, BIGSERIAL PK | N/A |
| Contract-first: controller implements interface | N/A — no HTTP controllers in this phase |
| Package naming: `ru.rutcampustrack.{service}.{module}` | Use `ru.rutcampustrack.notification.websocket` for WebSocket config/interceptor classes |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Spring Security WebSocket support (`@EnableWebSocketSecurity`) | Manual `HandshakeInterceptor` | Spring Security 6 changed security model | Simpler for services that don't use Spring Security at all |
| `parseClaimsJws()` (jjwt < 0.12) | `parseSignedClaims()` (jjwt 0.12+) | jjwt 0.12.0 (2023) | Old method removed; use `parseSignedClaims().getPayload()` |
| `Jwts.parserBuilder()` (jjwt 0.11.x) | `Jwts.parser()` (jjwt 0.12.x) | jjwt 0.12.0 (2023) | `parserBuilder()` removed; confirmed in existing api-gateway code |

---

## Open Questions

1. **API Gateway routing for `/ws/**`**
   - What we know: API Gateway exists on port 8080. notification-web is on port 9094. Gateway routes are defined elsewhere.
   - What's unclear: Does the API Gateway currently proxy `/ws/**` paths to notification-web? If not, web panel clients cannot reach the WebSocket endpoint through the gateway.
   - Recommendation: Check `api-gateway` route configuration. If missing, add a route for `/ws/**` → `notification-web:9094`. This is a Phase 21 planning concern even if web panel connection is not fully tested this phase.
   - **Research flag from STATE.md**: "Verify JwtAuthenticationFilter handles HTTP GET Upgrade: websocket — injects X-User-Id/X-Group-Id before WebSocket proxy forward." This needs verification — the Gateway's JWT filter runs on the HTTP Upgrade request, but notification-web does its OWN JWT validation in `JwtHandshakeInterceptor`. The Gateway may reject the request before notification-web sees it if the `/ws` path is not in the Gateway's public routes list. Solution: either add `/ws` as a public route on the Gateway (notification-web validates JWT itself), or accept that in Phase 21 the WebSocket endpoint is tested by connecting directly to port 9094 (bypassing gateway).

2. **group_id type safety**
   - What we know: JWT claim `group_id` is set as `Long` in `JwtService.generateAccessToken()`. jjwt may deserialize as `Integer` for small values.
   - What's unclear: Exact deserialization behavior for Long vs Integer in jjwt 0.12.6 depends on Jackson's default number handling.
   - Recommendation: Use `((Number) claims.get("group_id")).longValue()` — safe for both cases. Document this in `JwtHandshakeInterceptor` with a comment.

---

## Sources

### Primary (HIGH confidence)
- Spring Framework official docs — Token Authentication: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html
- Spring Framework official docs — Enable STOMP: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html
- Spring Framework official docs — Sending Messages: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-send.html
- Spring Framework official docs — Interceptors: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/interceptors.html
- Spring Framework Javadoc — HandshakeInterceptor: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/socket/server/HandshakeInterceptor.html
- Project source code — `JwtAuthenticationFilter.java` (jjwt 0.12.x pattern, verified)
- Project source code — `JwtService.java` (file-based key loading pattern, verified)
- Project source code — `EventConsumer.java` (existing placeholder, verified)
- Project source code — `notification-web/build.gradle.kts` (existing deps, verified)
- Project event schemas — `lesson.started.json`, `lesson.cancelled.json`, `homework.published.json`, `excuse.requested.json`, `late_checkin.requested.json` (verified group_id in all payloads)

### Secondary (MEDIUM confidence)
- WebSearch: Spring Boot 3.x STOMP WebSocket HandshakeInterceptor patterns — corroborated by official docs above
- WebSearch: jjwt 0.12 `parseSignedClaims` API — corroborated by existing api-gateway code using same version

### Tertiary (LOW confidence)
- None — all critical claims verified against official sources or project code.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified against existing build.gradle.kts files and official docs
- Architecture: HIGH — HandshakeInterceptor, SimpMessagingTemplate patterns verified against official Spring docs
- Pitfalls: HIGH — group_id type, headman filtering, and SockJS path patterns are verified from code inspection and official docs
- Test strategy: HIGH — unit test approach consistent with Phase 20 pattern (no Spring context, pure unit tests)

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (Spring Boot 3.4 is stable; STOMP APIs are stable)

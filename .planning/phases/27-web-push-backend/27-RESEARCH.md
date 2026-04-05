# Phase 27: Web Push Backend - Research

**Researched:** 2026-04-05
**Domain:** Web Push (VAPID), Spring Boot 3.4, Spring Data MongoDB, module restructure
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** VAPID keys stored as environment variables (VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT) in docker-compose.yml
- **D-02:** Keys pre-generated externally (openssl/script), not auto-generated at startup
- **D-03:** application.yml reads via `${VAPID_PUBLIC_KEY}`, `${VAPID_PRIVATE_KEY}`, `${VAPID_SUBJECT:mailto:noreply@rut.ru}`
- **D-04:** Push subscriptions stored in MongoDB, reusing existing attendance_db (same MongoDB container, same database)
- **D-05:** Collection `push_subscriptions` with fields: userId, endpoint, keys.p256dh, keys.auth, groupId, createdAt
- **D-06:** Spring Data MongoDB dependency added to notification-web (notification-app after restructure)
- **D-07:** EventConsumer calls PushService.sendToGroup() asynchronously (@Async / CompletableFuture) after STOMP routing
- **D-08:** Push errors do not block STOMP delivery — parallel execution
- **D-09:** Only 3 event types trigger Web Push: lesson.started, lesson.cancelled, homework.published (PUSH-04/05/06)
- **D-10:** HTTP 410 Gone from push service triggers automatic subscription deletion from MongoDB (PUSH-07)
- **D-11:** Create notification-api-contract module (java-library) with PushApi interface, request/response DTOs, Swagger annotations
- **D-12:** Restructure: notification-web/ becomes notification-service/notification-app/ + notification-service/notification-api-contract/
- **D-13:** Update settings.gradle.kts, docker-compose.yml build path, Dockerfile path accordingly
- **D-14:** notification-bot/ stays as-is (no changes)
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

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PUSH-01 | notification-web generates VAPID key pair and stores persistently | D-01/D-02/D-03: Keys are env vars (pre-generated). GET /api/ws/push/vapid-public-key returns the configured public key. |
| PUSH-02 | notification-web exposes POST /api/ws/push/subscribe to store PushSubscription | D-05/D-06/D-11: PushSubscriptionDocument in MongoDB push_subscriptions, endpoint as unique key |
| PUSH-03 | notification-web exposes DELETE /api/ws/push/subscribe to unsubscribe | Complement to PUSH-02; delete by userId+endpoint |
| PUSH-04 | notification-web sends Web Push for lesson.started events (async, non-blocking) | D-07/D-09: EventConsumer hook for lesson.started; PushService.sendToGroup async |
| PUSH-05 | notification-web sends Web Push for lesson.cancelled events | Same hook pattern as PUSH-04 |
| PUSH-06 | notification-web sends Web Push for homework.published events | Same hook pattern as PUSH-04 |
| PUSH-07 | notification-web handles expired/invalid subscriptions (HTTP 410 → delete) | D-10: WebPushService catches 410, calls repository.deleteByEndpoint() |
| INFRA-02 | API Gateway route for /api/push/** to notification-web | D-18: Add route in api-gateway application.yml |
</phase_requirements>

---

## Summary

Phase 27 adds Web Push infrastructure to the existing `notification-web` Spring Boot service. The work has three distinct tracks that must be coordinated:

**Track 1 — Module restructure (D-11/D-12/D-13):** The flat `services/notification-web/` module becomes `services/notification-service/notification-app/` + `services/notification-service/notification-api-contract/`, matching the contract-first pattern already used by academic/schedule/attendance services. This is a mechanical rename plus Gradle/Docker wiring, but it must happen first because the API contract shapes everything else.

**Track 2 — Web Push core (PUSH-01..07):** VAPID keys from env vars, MongoDB `push_subscriptions` collection, `PushController` implementing `PushApi`, `PushService` using `nl.martijndwars:web-push:5.1.2`. The critical library risk is BouncyCastle's signed-JAR incompatibility with Spring Boot's nested-JAR loader — the proven workaround is `loaderImplementation = LoaderImplementation.CLASSIC` in the `bootJar` Gradle task.

**Track 3 — Gateway route (INFRA-02):** Add `/api/push/**` → `http://notification-web:9094` in `api-gateway/src/main/resources/application.yml`.

**Primary recommendation:** Use `nl.martijndwars:web-push:5.1.2` with the `LoaderImplementation.CLASSIC` bootJar workaround. Do not attempt to use `bcprov-jdk18on` as a JCE provider in a Spring Boot fat JAR without this fix.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| nl.martijndwars:web-push | 5.1.2 | VAPID signing, RFC 8030 push delivery, AES128GCM encryption | Only maintained Java Web Push library on Maven Central; de-facto standard in the ecosystem |
| org.bouncycastle:bcprov-jdk15on | 1.70 | Transitive dep of web-push; EC crypto provider | Required by web-push:5.1.2 POM; must be explicitly declared to control version |
| spring-boot-starter-data-mongodb | (BOM) | MongoRepository, MongoTemplate, @Document | Already used in attendance-service — same pattern |
| spring-boot-starter-aop | (BOM) | @RequireRole AOP aspect | Already used in attendance-service — copy exact pattern |
| spring-boot-starter-web | (BOM) | Servlet-based REST controllers for push endpoints | Required by contract-first pattern; notification-app already has starter-websocket which pulls this in |

**[VERIFIED: central.sonatype.com]** web-push 5.1.2 published 2025-01-17. Transitive dep is `bcprov-jdk15on:1.70`.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| springdoc-openapi-starter-webmvc-ui | 2.7.0 | Swagger UI for REST endpoints in notification-app | Add to notification-app alongside notification-api-contract |
| jakarta.validation:jakarta.validation-api | 3.1.0 | @NotNull/@NotBlank in contract DTOs | notification-api-contract is a `java-library` — no Spring; matches academic-api-contract |
| spring-hateoas | (BOM) | EntityModel for REST responses | Matches project-wide pattern; `RepresentationModel` in response classes |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| nl.martijndwars:web-push | com.zerodeplibs:zerodep-web-push-java:2.1.2 | zerodep has no BouncyCastle dep (uses JVM built-in EC), eliminating the fat-JAR risk. However it requires manually wiring JWT generation and HTTP client; more code, less proven in Spring contexts. Recommended only if bootJar CLASSIC workaround fails. |
| nl.martijndwars:web-push | Raw BouncyCastle + OkHttp | Full control but 300+ lines of crypto code — hand-rolling AES128GCM envelope encryption is error-prone and not covered by tests |

### Installation

For `notification-app/build.gradle.kts`:

```kotlin
// Web Push delivery
implementation("nl.martijndwars:web-push:5.1.2")
// Explicit BouncyCastle to avoid version mismatch with transitive pull
runtimeOnly("org.bouncycastle:bcprov-jdk15on:1.70")

// MongoDB for push_subscriptions
implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

// AOP for @RequireRole
implementation("org.springframework.boot:spring-boot-starter-aop")

// REST + HATEOAS
implementation("org.springframework.boot:spring-boot-starter-web")
implementation("org.springframework.boot:spring-boot-starter-hateoas")
implementation("org.springframework.boot:spring-boot-starter-validation")

// OpenAPI
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

// CRITICAL: Fix BouncyCastle signed-JAR incompatibility with Spring Boot 3.2+ loader
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    loaderImplementation = org.springframework.boot.loader.tools.LoaderImplementation.CLASSIC
}
```

For `notification-api-contract/build.gradle.kts` (mirrors `academic-api-contract`):

```kotlin
plugins { `java-library` }

dependencies {
    api("jakarta.validation:jakarta.validation-api:3.1.0")
    api("org.springframework:spring-web:6.2.1")
    api("org.springframework.hateoas:spring-hateoas:2.4.1")
    api("io.swagger.core.v3:swagger-annotations-jakarta:2.2.22")
    api("com.fasterxml.jackson.core:jackson-annotations:2.18.2")
}
```

**Version verification:** [VERIFIED: central.sonatype.com] web-push 5.1.2 — latest as of 2025-01-17. bcprov-jdk18on 1.83 is the current jdk18on line but web-push:5.1.2 requires `bcprov-jdk15on:1.70` (same bytecode, different artifact naming convention).

---

## Architecture Patterns

### Recommended Project Structure (after restructure)

```
services/
├── notification-service/          ← NEW parent directory
│   ├── notification-api-contract/ ← NEW java-library (PushApi, DTOs)
│   │   └── src/main/java/ru/rutcampustrack/notification/contract/
│   │       ├── api/PushApi.java
│   │       └── dto/push/
│   │           ├── SubscribeRequest.java  (record)
│   │           └── VapidPublicKeyResponse.java (class extends RepresentationModel)
│   └── notification-app/          ← RENAMED from notification-web/
│       └── src/main/java/ru/rutcampustrack/notification/
│           ├── config/
│           │   ├── RabbitConfig.java        (unchanged)
│           │   ├── WebSocketConfig.java     (unchanged)
│           │   ├── JwtHandshakeInterceptor.java (unchanged)
│           │   ├── AsyncConfig.java         ← NEW @EnableAsync + ThreadPoolTaskExecutor
│           │   ├── MongoConfig.java         ← NEW push_subscriptions indexes
│           │   └── WebPushConfig.java       ← NEW PushService bean with VAPID keys
│           ├── event/
│           │   └── EventConsumer.java       (modified — adds PushService hook)
│           ├── push/                        ← NEW package
│           │   ├── PushController.java
│           │   ├── PushService.java
│           │   └── PushSubscriptionDocument.java
│           └── security/                   ← NEW (copied from attendance-app)
│               ├── RequireRole.java
│               ├── RoleCheckAspect.java
│               ├── UserContextFilter.java
│               └── RequestContext.java
```

### Pattern 1: Contract-First Module (matches project standard)

**What:** `notification-api-contract` is a `java-library` with zero Spring Boot dependencies. It defines the HTTP interface via annotated Java interfaces (`PushApi`), request records (DTOs), and response classes.

**When to use:** Any REST endpoint in notification-app must have its mapping defined in the contract module.

**Example:**

```java
// Source: mirrors services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/GroupApi.java
@Tag(name = "Push", description = "Web Push subscription management")
@RequestMapping("/push")   // Gateway strips /api/ws → /ws, then /push prefix
public interface PushApi {

    @Operation(summary = "Get VAPID public key")
    @GetMapping("/vapid-public-key")
    ResponseEntity<EntityModel<VapidPublicKeyResponse>> getVapidPublicKey();

    @Operation(summary = "Subscribe to push notifications (STUDENT)")
    @PostMapping("/subscribe")
    ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request);

    @Operation(summary = "Unsubscribe from push notifications (STUDENT)")
    @DeleteMapping("/subscribe")
    ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request);
}
```

**Note on Gateway routing:** Current Gateway route `/api/ws/**` strips prefix `/api` → notification-web receives `/ws/**`. Adding a new route `/api/push/**` (D-18) means notification-app receives `/push/**`. PushApi uses `@RequestMapping("/push")` accordingly.

### Pattern 2: @RequireRole AOP (copy from attendance-app)

**What:** Four classes copied verbatim from `attendance-app/src/main/java/ru/rutcampustrack/attendance/security/` with package rename to `ru.rutcampustrack.notification.security`. The only change is the `UserRole` import path — notification-app needs its own `UserRole` enum (or import from notification-api-contract).

**Key implementation detail:**

```java
// Source: attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RequestContext.java
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)  // CRITICAL
public class RequestContext { ... }
```

`ScopedProxyMode.TARGET_CLASS` is mandatory — singleton beans like `RoleCheckAspect` hold a reference to `RequestContext` and need the scoped proxy to get per-request state. Missing this causes stale context bugs.

### Pattern 3: Async Push Delivery

**What:** `EventConsumer.onEvent()` calls `pushService.sendToGroup(groupId, eventType, payload)` after the STOMP `convertAndSend`. The push call is `@Async` so it returns immediately, not blocking RabbitMQ listener thread.

**When to use:** All 3 push-triggering event types.

**Example:**

```java
// AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "pushTaskExecutor")
    public TaskExecutor pushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("push-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}

// PushService.java
@Service
public class PushService {

    @Async("pushTaskExecutor")
    public CompletableFuture<Void> sendToGroup(long groupId, String eventType, Map<String, Object> payload) {
        List<PushSubscriptionDocument> subs = repository.findAllByGroupId(groupId);
        for (PushSubscriptionDocument sub : subs) {
            try {
                sendOne(sub, eventType, payload);
            } catch (HttpResponseException e) {
                if (e.getStatusCode() == 410) {
                    // D-10: Auto-delete expired subscription
                    repository.deleteByEndpoint(sub.getEndpoint());
                }
                // Non-410 errors: log and continue (D-08)
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
```

### Pattern 4: PushSubscriptionDocument (MongoDB)

**What:** Spring Data MongoDB `@Document` for `push_subscriptions` collection. Uses String `@Id` (MongoDB ObjectId as String). Mirrors `AttendanceDocument` pattern.

**Example:**

```java
// Source: mirrors services/attendance-service/attendance-app/...checkin/AttendanceDocument.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "push_subscriptions")
public class PushSubscriptionDocument {

    @Id
    private String id;

    @Field("user_id")
    private Long userId;

    @Field("group_id")
    private Long groupId;

    @Field("endpoint")
    private String endpoint;

    @Field("p256dh")
    private String p256dh;

    @Field("auth")
    private String auth;

    @Field("created_at")
    private Instant createdAt;
}
```

### Pattern 5: Web Push Delivery via webpush-java

**What:** `nl.martijndwars:web-push` sends VAPID-authenticated push via `PushService.send()`. The library handles AES128GCM payload encryption and VAPID JWT signing.

**Example:**

```java
// WebPushConfig.java — construct once at startup
@Configuration
public class WebPushConfig {

    @Bean
    public PushService webPushService(
            @Value("${vapid.public-key}") String publicKeyBase64,
            @Value("${vapid.private-key}") String privateKeyBase64,
            @Value("${vapid.subject:mailto:noreply@rut.ru}") String subject
    ) throws GeneralSecurityException {
        Security.addProvider(new BouncyCastleProvider());
        return new PushService(publicKeyBase64, privateKeyBase64, subject);
    }
}

// PushService — send a single notification
private void sendOne(PushSubscriptionDocument sub, String title, String body) throws Exception {
    Notification notification = new Notification(
        sub.getEndpoint(),
        sub.getP256dh(),
        sub.getAuth(),
        buildPayload(title, body)
    );
    webPushService.send(notification);
}

// Payload format (JSON string → UTF-8 bytes)
private byte[] buildPayload(String title, String body) throws Exception {
    Map<String, Object> payload = Map.of("title", title, "body", body);
    return new ObjectMapper().writeValueAsBytes(payload);
}
```

### Pattern 6: MongoDB Index Initialization (mirrors MongoConfig in attendance-app)

```java
@Configuration
public class PushMongoConfig {

    @Lazy
    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void initIndexes() {
        IndexOperations ops = mongoTemplate.indexOps("push_subscriptions");

        // Unique: one subscription per user+endpoint combination
        ops.ensureIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("endpoint", Sort.Direction.ASC)
                .unique()
                .named("uniq_user_endpoint"));

        // Query: fetch all subscriptions for a group (push delivery path)
        ops.ensureIndex(new Index()
                .on("group_id", Sort.Direction.ASC)
                .named("idx_group_id"));
    }
}
```

### Anti-Patterns to Avoid

- **Auto-wiring RabbitMQ listener thread for push I/O:** Never call `webPushService.send()` synchronously inside `@RabbitListener` — outbound HTTP to FCM/APNS push services can take 100-500ms; this blocks the AMQP consumer thread and starves the queue.
- **Creating a new BouncyCastleProvider per request:** `Security.addProvider(new BouncyCastleProvider())` must be called once at startup (in `@Configuration`). Calling it per request causes `"already registered"` warnings and potential memory leaks.
- **Storing p256dh/auth as raw bytes instead of Base64 strings:** The browser sends these as Base64url strings in the `PushSubscription` JSON. Store them as-is; the web-push library decodes them internally.
- **Missing `loaderImplementation = CLASSIC`:** Without this, `BouncyCastleProvider` registration throws `SecurityException: JCE cannot authenticate the provider BC` when Spring Boot fat JAR uses the 3.2+ nested JAR loader.
- **Using `bcprov-jdk18on` instead of `bcprov-jdk15on`:** The web-push:5.1.2 library imports classes from `bcprov-jdk15on` artifact name. Providing only `jdk18on` can cause `NoClassDefFoundError` because the artifact coordinates differ even though bytecode is compatible. Declare both or just `jdk15on` explicitly.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| VAPID JWT signing | Custom ECDSA JWT over P-256 | nl.martijndwars:web-push PushService | RFC 8291 requires specific JWT claims, expiry, and header; 50+ lines of crypto that web-push handles in one method call |
| AES128GCM content encryption | Custom crypto envelope | nl.martijndwars:web-push Notification | RFC 8291 + draft-ietf-httpbis-encryption-encoding-09; wrong key derivation silently produces invalid ciphertext (browser silently drops it) |
| VAPID key format conversion | Base64url ↔ ECPoint conversion | web-push library internal | P-256 uncompressed point format (0x04 prefix) vs. Base64url; getting this wrong produces invalid subscriptions |
| Subscription endpoint detection | Parse FCM/APNS from endpoint URL | Treat all endpoints uniformly via web-push library | The library handles protocol differences across FCM, Firefox, Safari push services |

**Key insight:** Web Push encryption (RFC 8291) has specific key derivation steps (HKDF, salt, IKM) where a single wrong byte produces a notification that arrives at the push service successfully but is silently discarded by the browser. The library has regression tests against the RFC test vectors. Hand-rolled implementations almost always fail on edge cases.

---

## Common Pitfalls

### Pitfall 1: BouncyCastle SecurityException in Spring Boot 3.2+ Fat JAR

**What goes wrong:** `SecurityException: JCE cannot authenticate the provider BC` at startup, or `IllegalStateException: zip file closed` during push send.

**Why it happens:** Spring Boot 3.2 changed the fat JAR loader to `jar:nested:/...` URL scheme. BouncyCastle is a signed JAR — when nested inside a fat JAR with the new URL scheme, the signature verification fails. This is a known issue tracked in Spring Boot issue #28837 and Spring Boot issue #37586.

**How to avoid:** Add to `notification-app/build.gradle.kts`:

```kotlin
import org.springframework.boot.loader.tools.LoaderImplementation

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    loaderImplementation = LoaderImplementation.CLASSIC
}
```

**Warning signs:** If push send throws `SecurityException` on first call but succeeds after a restart, this is usually the cause.

[CITED: github.com/spring-projects/spring-boot/issues/28837]
[CITED: github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.2-Release-Notes]

### Pitfall 2: Gateway Route Collision — /api/ws vs /api/push

**What goes wrong:** Adding `/api/push/**` route that conflicts with or shadows the existing `/api/ws/**` route.

**Why it happens:** The current Gateway already has `/api/ws/**` → `notification-web:9094` with `StripPrefix=1`. The new route `/api/push/**` → `notification-web:9094` also needs `StripPrefix=1` but must be a separate route entry.

**How to avoid:** Add as a second distinct route `id: notification-push` with predicate `Path=/api/push/**` and filter `StripPrefix=1`. This means notification-app receives `/push/**` — consistent with `@RequestMapping("/push")` on PushApi.

### Pitfall 3: STOMP WebSocket vs REST — Same Port, Different Path, Different Auth

**What goes wrong:** Accidentally applying the JWT token filter (UserContextFilter) to WebSocket upgrade requests, breaking existing STOMP connections.

**Why it happens:** When `spring-boot-starter-web` is added (for REST), Spring Boot activates `DispatcherServlet`. `UserContextFilter` is a servlet filter that runs on all requests. STOMP WebSocket upgrades (`/ws/**`) arrive as HTTP requests first — if UserContextFilter tries to parse `X-User-Id` header (not present on WS upgrades), it may set null context but not fail. However, care is needed in filter exclusion.

**How to avoid:** `UserContextFilter` already handles `null` userIdHeader gracefully (it checks before parsing). No explicit exclusion needed, but test both REST and WS paths after adding the filter.

### Pitfall 4: `ScopedProxyMode.TARGET_CLASS` Missing on RequestContext

**What goes wrong:** `RoleCheckAspect` captures a stale `RequestContext` from the first request; subsequent requests see wrong userId/role.

**Why it happens:** `RoleCheckAspect` is a singleton. If `RequestContext` is also a plain singleton (or request-scoped without proxy), the injected reference is captured at creation time and never refreshed.

**How to avoid:** Declare `RequestContext` exactly as in `attendance-app`:

```java
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext { ... }
```

### Pitfall 5: WebSocket Config Breaks When spring-boot-starter-web Is Added

**What goes wrong:** When `starter-web` is added to notification-app (for REST), Spring Boot may create a `DispatcherServlet` that conflicts with the embedded WebSocket handler configuration.

**Why it happens:** `spring-boot-starter-websocket` already transitively includes `spring-webmvc` but adding `starter-web` explicitly can cause dual servlet registration or path mapping conflicts.

**How to avoid:** Since `starter-websocket` already includes web MVC dependencies, the explicit `starter-web` dependency may not be needed if the existing build already has it transitively. Verify the existing `build.gradle.kts` and only add what is missing. In practice: the existing service has `starter-websocket` which includes all needed web dependencies; add `starter-data-mongodb`, `starter-aop`, `starter-hateoas`, `starter-validation` without an additional `starter-web`.

### Pitfall 6: Subscription Endpoint as Lookup Key

**What goes wrong:** Using `userId` alone as the lookup key for DELETE /subscribe causes multi-device users to lose all subscriptions when unsubscribing from one device.

**Why it happens:** A student may be subscribed from both their phone and laptop. `userId` is not unique per subscription — `endpoint` is the unique identifier of a browser push subscription.

**How to avoid:** DELETE /subscribe body must include the `endpoint` field. Repository method: `deleteByUserIdAndEndpoint(long userId, String endpoint)`. Confirmed by D-05 schema which includes endpoint.

### Pitfall 7: MongoDB Connection String Missing from notification-app

**What goes wrong:** Service starts but MongoDB operations throw `MongoTimeoutException` because `MONGODB_URI` env var is not set in docker-compose.yml.

**Why it happens:** The current `notification-web` service in docker-compose.yml does NOT have `MONGODB_URI` (D-06 confirms this was missing — flagged in STATE.md Research Flags). The MongoDB container is named `mongo-attendance` with no exposed port.

**How to avoid:** Add to docker-compose.yml notification-web service environment:

```yaml
SPRING_DATA_MONGODB_URI: mongodb://mongo-attendance:27017/attendance_db
```

Also add `mongo-attendance` to `depends_on` in the notification-web service block.

---

## Code Examples

Verified patterns from official sources and project codebase:

### VAPID Key Generation Script (openssl)

```bash
# Source: github.com/web-push-libs/webpush-java/wiki/VAPID
# Generate P-256 private key
openssl ecparam -name prime256v1 -genkey -noout -out vapid_private.pem
# Derive public key
openssl ec -in vapid_private.pem -pubout -out vapid_public.pem

# Export as Base64url for env vars (remove PEM headers and newlines)
VAPID_PRIVATE_KEY=$(openssl ec -in vapid_private.pem -outform DER | tail -c +8 | head -c 32 | base64 | tr '+/' '-_' | tr -d '=\n')
VAPID_PUBLIC_KEY=$(openssl ec -in vapid_private.pem -pubout -outform DER | tail -c 65 | base64 | tr '+/' '-_' | tr -d '=\n')
```

Note: The web-push library's `Utils.loadPublicKey()` and `Utils.loadPrivateKey()` accept Base64url-encoded uncompressed P-256 key bytes. [CITED: github.com/web-push-libs/webpush-java README]

### settings.gradle.kts Update

```kotlin
// Replace:
include("services:notification-web")

// With:
include("services:notification-service:notification-api-contract")
include("services:notification-service:notification-app")
```

### docker-compose.yml Update

```yaml
notification-web:    # container name unchanged — Gateway routes by hostname
  build:
    context: ./services/notification-service/notification-app   # updated path
    dockerfile: Dockerfile
  environment:
    # ... existing vars ...
    SPRING_DATA_MONGODB_URI: mongodb://mongo-attendance:27017/attendance_db
    VAPID_PUBLIC_KEY: ${VAPID_PUBLIC_KEY}
    VAPID_PRIVATE_KEY: ${VAPID_PRIVATE_KEY}
    VAPID_SUBJECT: ${VAPID_SUBJECT:-mailto:noreply@rut.ru}
  depends_on:
    mongo-attendance:
      condition: service_healthy
    # ... existing depends_on ...
```

### Push API controller (PushController.java)

```java
// Source: mirrors pattern in attendance-service CheckinController
@RestController
public class PushController implements PushApi {

    private final PushService pushService;
    private final RequestContext requestContext;

    // constructor injection

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<EntityModel<VapidPublicKeyResponse>> getVapidPublicKey() {
        VapidPublicKeyResponse response = pushService.getVapidPublicKey();
        return ResponseEntity.ok(EntityModel.of(response));
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request) {
        pushService.subscribe(requestContext.getUserId(), requestContext.getGroupId(), request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole(UserRole.STUDENT)
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        pushService.unsubscribe(requestContext.getUserId(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
```

### EventConsumer modification

```java
// Modified onEvent() — add after STOMP routing:
private static final Set<String> PUSH_EVENT_TYPES = Set.of(
    "lesson.started", "lesson.cancelled", "homework.published"
);

@RabbitListener(queues = "notification-web.events")
public void onEvent(Map<String, Object> envelope) {
    // ... existing STOMP routing code (unchanged) ...

    // D-07/D-08: Async push delivery — does not block STOMP
    if (PUSH_EVENT_TYPES.contains(eventType)) {
        pushService.sendToGroup(groupId, eventType, payload);  // @Async — returns immediately
    }
}
```

---

## Runtime State Inventory

> This phase is a feature addition (not a rename/refactor), but the module restructure (D-12) renames source paths. No user-visible identifiers change.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no existing push_subscriptions collection | None (new collection) |
| Live service config | notification-web container already running in Docker | Build path change in docker-compose.yml; container must be rebuilt (`docker compose up -d --build notification-web`) |
| OS-registered state | None | None |
| Secrets/env vars | VAPID keys do not yet exist — must be generated and added to docker-compose.yml or .env | Generate + add before first run |
| Build artifacts | `services/notification-web/build/` directory contains old compiled output | After path rename, old build dir is orphaned; `./gradlew clean` clears it |

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| bcprov-jdk15on artifact naming | bcprov-jdk18on (same bytecode, modern artifact) | BouncyCastle 1.71 | web-push:5.1.2 still references jdk15on; use jdk15on to avoid NoClassDefFoundError |
| Spring Boot 3.1 JarLauncher (classic) | Spring Boot 3.2+ nested JAR loader | Spring Boot 3.2 | Breaks signed JARs (BouncyCastle); workaround: `loaderImplementation = CLASSIC` |
| `AsyncResult.forValue()` in @Async methods | `CompletableFuture.completedFuture()` | Spring 6 | AsyncResult deprecated; use CompletableFuture |

**Deprecated/outdated:**
- `AsyncResult`: Replaced by `CompletableFuture` in Spring 6+ / Spring Boot 3+

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `notification-web` Docker container name stays `rct-notification-web` after restructure (module path changes, container name does not) | Architecture | If container name changes, Gateway DNS `http://notification-web:9094` breaks. Planner must verify hostname in docker-compose.yml service key matches Gateway URI. |
| A2 | `starter-websocket` already provides `spring-webmvc` transitively, so `starter-web` is NOT needed in notification-app | Standard Stack | If wrong: two servlet stacks registered, causing startup error. Verify with `./gradlew :services:notification-service:notification-app:dependencies` |
| A3 | `attendance_db` MongoDB database is accessible to notification-app without credentials (no auth configured on mongo-attendance container) | Architecture | Container definition shows no MONGO_INITDB_ROOT_USERNAME — auth is disabled. If a future admin enables auth, MONGODB_URI needs credentials. |

---

## Open Questions

1. **Dockerfile path after restructure**
   - What we know: Current Dockerfile is at `services/notification-web/Dockerfile`; after D-12 it moves to `services/notification-service/notification-app/Dockerfile`
   - What's unclear: Does the project use a shared root Dockerfile or per-module? Currently it's per-module.
   - Recommendation: Move Dockerfile to new path and update docker-compose.yml `context:` and `dockerfile:` accordingly. Content is identical.

2. **UserRole enum in notification-api-contract**
   - What we know: `@RequireRole(UserRole.STUDENT)` in PushApi requires `UserRole` in the contract module. The canonical `UserRole` enum lives in `academic-api-contract`. The attendance-app imports `attendance-api-contract` which declares its own copy.
   - What's unclear: Should `notification-api-contract` declare its own `UserRole` (copy) or depend on `academic-api-contract`?
   - Recommendation: Declare a minimal `UserRole` enum in `notification-api-contract` with only the values used (`STUDENT`, `TEACHER`, `ADMIN`). This avoids cross-service contract dependencies which the project's architecture intentionally prevents.

3. **Retry strategy for transient push failures**
   - What we know: D-10 specifies 410 → delete. D-08 says push errors don't block STOMP.
   - What's unclear: For transient errors (429, 503, network timeout), should there be exponential backoff retry or discard-and-log?
   - Recommendation (Claude's discretion): Discard-and-log for MVP. The next event trigger (e.g., next lesson.started) will retry naturally. Adding retry logic requires a persistent queue and significantly more complexity, which is out of scope for Phase 27.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Container build/run | Yes | 28.5.2 | — |
| Java 21 | Gradle build (JAVA_HOME required) | Configured per CLAUDE.md | ms-21.0.9 | — |
| MongoDB (docker) | push_subscriptions storage | Yes (mongo-attendance container defined in docker-compose.yml) | mongo:7 | — |
| VAPID keys | Push delivery | Not yet generated | — | Must generate before first run (openssl script) |
| mongosh | MongoDB verification | Not on PATH | — | Use `docker exec rct-mongo-attendance mongosh ...` |

**Missing dependencies with no fallback:**
- VAPID keys — must be generated by operator before starting the service. Plan Wave 0 must include key generation instructions.

**Missing dependencies with fallback:**
- mongosh — use `docker exec` to verify MongoDB state during testing.

---

## Validation Architecture

> `workflow.nyquist_validation` not set in config.json → treating as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito (Spring Boot Test) |
| Config file | None — convention-based; tests in `src/test/java/` |
| Quick run command | `./gradlew :services:notification-service:notification-app:test --tests "ru.rutcampustrack.notification.push.*" -x integrationTest` |
| Full suite command | `./gradlew :services:notification-service:notification-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PUSH-01 | GET /api/ws/push/vapid-public-key returns Base64-encoded key | unit | `./gradlew :services:notification-service:notification-app:test --tests "*.PushControllerTest.getVapidPublicKey*"` | ❌ Wave 0 |
| PUSH-02 | POST /push/subscribe stores document in MongoDB | integration (Testcontainers MongoDB) | `./gradlew :services:notification-service:notification-app:test --tests "*.PushServiceTest.subscribe*"` | ❌ Wave 0 |
| PUSH-03 | DELETE /push/subscribe removes document | integration | `./gradlew :services:notification-service:notification-app:test --tests "*.PushServiceTest.unsubscribe*"` | ❌ Wave 0 |
| PUSH-04 | lesson.started triggers sendToGroup async | unit (mock PushService) | `./gradlew :services:notification-service:notification-app:test --tests "*.EventConsumerTest.lessonStarted_triggersPush*"` | ❌ Wave 0 |
| PUSH-05 | lesson.cancelled triggers sendToGroup async | unit | Same as PUSH-04 class | ❌ Wave 0 |
| PUSH-06 | homework.published triggers sendToGroup async | unit | Same as PUSH-04 class | ❌ Wave 0 |
| PUSH-07 | HTTP 410 → subscription deleted | unit (mock webPushService) | `./gradlew :services:notification-service:notification-app:test --tests "*.PushServiceTest.send410*"` | ❌ Wave 0 |
| INFRA-02 | Gateway routes /api/push/** to notification-web | manual smoke | `curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/push/vapid-public-key` | manual-only |

### Sampling Rate
- **Per task commit:** `./gradlew :services:notification-service:notification-app:test -x integrationTest`
- **Per wave merge:** `./gradlew :services:notification-service:notification-app:test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushControllerTest.java` — covers PUSH-01, role enforcement
- [ ] `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushServiceTest.java` — covers PUSH-02, PUSH-03, PUSH-07
- [ ] `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java` — extend existing; add PUSH-04/05/06 push trigger assertions

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT validated by API Gateway; X-User-Id/X-User-Role headers trusted within private_net |
| V3 Session Management | no | Stateless REST endpoints |
| V4 Access Control | yes | @RequireRole(STUDENT) AOP — same pattern as academic/schedule/attendance services |
| V5 Input Validation | yes | @Valid on SubscribeRequest; @NotBlank on endpoint, p256dh, auth fields |
| V6 Cryptography | yes | VAPID signing via webpush-java (P-256 ECDSA); never hand-roll; AES128GCM encryption handled by library |

### Known Threat Patterns for Web Push Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Subscribing on behalf of another user | Elevation of Privilege | Read userId from RequestContext (Gateway-injected header), not from request body |
| Storing raw private VAPID key in MongoDB | Information Disclosure | Keys are env vars only (D-01); never persist private key |
| Endpoint URL as SSRF vector | Tampering | web-push library only POSTs to browser push service endpoints (FCM/Firefox); does not follow redirects arbitrarily |
| Mass subscription of fake endpoints | Denial of Service | Per-user unique index on (userId, endpoint) limits storage abuse; push failures log and discard |
| Unauthenticated subscription | Spoofing | @RequireRole(STUDENT) rejects requests without valid X-User-Role header |

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: central.sonatype.com] `nl.martijndwars:web-push:5.1.2` — latest version, published 2025-01-17, requires `bcprov-jdk15on:1.70`
- [VERIFIED: central.sonatype.com] `org.bouncycastle:bcprov-jdk18on:1.83` — latest jdk18on, published late 2024
- [CITED: github.com/spring-projects/spring-boot/issues/28837] — Spring Boot nested JAR + BouncyCastle SecurityException
- [CITED: github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.2-Release-Notes] — `loaderImplementation = CLASSIC` workaround documented
- Codebase: `services/attendance-service/attendance-app/src/main/java/.../security/` — @RequireRole AOP pattern (read directly)
- Codebase: `services/attendance-service/attendance-app/src/main/java/.../config/MongoConfig.java` — MongoDB index init pattern (read directly)
- Codebase: `services/academic-service/academic-api-contract/build.gradle.kts` — api-contract module structure (read directly)
- Codebase: `services/notification-web/` — all existing source files (read directly)

### Secondary (MEDIUM confidence)
- [CITED: github.com/web-push-libs/webpush-java/wiki/VAPID] — VAPID key generation instructions
- Multiple community sources confirming `bootJar { loaderImplementation = LoaderImplementation.CLASSIC }` as the standard fix for Spring Boot 3.2 + BouncyCastle

### Tertiary (LOW confidence)
- zerodep-web-push-java as fallback alternative — identified via WebSearch, not directly verified for Spring Boot 3.4 compatibility

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified versions on Maven Central, known BouncyCastle issue documented in official Spring Boot release notes
- Architecture: HIGH — all patterns copied from existing project services, verified by direct codebase read
- Pitfalls: HIGH — BouncyCastle/fat-JAR issue is confirmed with official source; other pitfalls from direct code inspection

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (library ecosystem stable; Spring Boot 3.4 is current; BouncyCastle workaround is a known stable fix)

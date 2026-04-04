# Phase 17: Write Path — Geo-Checkin + Manual Marking - Research

**Researched:** 2026-04-04
**Domain:** Spring Boot 3.4 / MongoDB / Redis / RabbitMQ write path — geofence validation, rate limiting, event publishing
**Confidence:** HIGH (all findings verified against existing codebase and established patterns)

## Summary

Phase 17 implements the two primary write paths for the Attendance Service MVP: geo-checkin by students and manual marking by headmen. All architectural decisions are locked in CONTEXT.md with high specificity. The codebase (Phases 15 and 16) provides all infrastructure: MongoDB with unique index, RabbitMQ fanout exchange, gRPC clients for Schedule and Academic services, RequestContext with user metadata, GlobalExceptionHandler with RFC 7807, and the AOP role-enforcement pattern.

The main new infrastructure required is Redis (spring-boot-starter-data-redis + Testcontainers Redis in tests), which is NOT yet present in `attendance-app/build.gradle.kts`. Everything else builds directly on existing patterns. Three new exception types are needed (UnprocessableEntityException for 422, RateLimitException for 429, and a GeofenceBlockedException for 403-geofence) — the GlobalExceptionHandler must be extended to map them. The GeofenceService follows the SemesterCacheService pattern exactly (volatile in-memory cache, @PostConstruct load, TTL-based lazy refresh). GeoUtils is pure math — no external geo library needed for a single Haversine radius check.

The upsert semantics for manual marking (PUT, headman path) must use MongoTemplate for $set + $setOnInsert or a findAndModify/replaceWith pattern to be safe as an idempotent upsert, paralleling the auto-absent bulk logic from Phase 16. The event schema requires `event_type`, `event_id` (UUID), `occurred_at`, and a `payload` object — the AttendanceEventPublisher must construct this envelope precisely matching `event-schemas/attendance.marked.json`.

**Primary recommendation:** Add Redis dependency first, implement GeofenceService + GeoUtils as the standalone utility layer, then wire CheckinService and MarkingService using MongoTemplate for upsert, and extend GlobalExceptionHandler before wiring any controller.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Geo-Checkin API**
- D-01: Endpoint: `POST /api/attendance/checkin` with body `CheckinRequest(double lat, double lng)`. User ID and group ID from `RequestContext` (gateway headers). Service resolves active lesson via `scheduleGrpcClient.getActiveLesson(groupId, now)`.
- D-02: Time window validated server-side from `LessonResponse` start_time/end_time with +/-5 min buffer against server clock. Single source of truth.
- D-03: Response: 201 Created with `EntityModel<CheckinResponse>`. CheckinResponse is compact: `status`, `lessonId`, `timestamp` only. `_links`: self + lesson. Does NOT expose full AttendanceDocument shape.
- D-04: Error responses: RFC 7807 Problem Details via existing `ErrorResponse` record + `GlobalExceptionHandler`. HTTP codes: 422 outside geofence, 404 no active lesson, 403 geo-blocked, 409 duplicate (MongoDB unique index + Redis dedup), 429 rate limit exceeded.
- D-05: 409 for duplicate checkin (not 200 OK). MongoDB unique index `{lesson_id, user_id}` is the authoritative idempotency guard; Redis dedup is a fast-path optimization.
- D-06: `CheckinRequest` record in `attendance-api-contract`. `CheckinResponse` record in `attendance-api-contract`. `CheckinApi` interface in contract with `@PostMapping` and `@Operation` annotations.

**Geofence Architecture**
- D-07: Three-layer design: `AcademicGrpcClient` (transport only) -> `GeofenceService` (orchestration + cache) -> `GeoUtils` (pure math).
- D-08: `GeofenceService` (`attendance/geofence/`) — `boolean isWithinCampus(double lat, double lng)`, `GeofenceResponse getCurrentGeofence()`. In-memory volatile cache with TTL (10-60 min). Same pattern as `SemesterCacheService` for rarely-changing data.
- D-09: `GeoUtils` (`attendance/geofence/`) — package-private static utility. `distanceMeters(lat1, lng1, lat2, lng2)` using Haversine formula, `isWithinRadius(...)`. No Spring, no network. No external geo libraries — pure math sufficient for single radius check.
- D-10: No Haversine logic in `AcademicGrpcClient` or controllers. Clean separation: gRPC client = transport, GeofenceService = business decision, GeoUtils = math.

**Manual Marking Flow**
- D-11: Endpoint: `PUT /api/attendance/lessons/{lessonId}/students/{userId}` with body `{status}`. Autosave per click (MARK-02). Response: 200 OK with `EntityModel<MarkResponse>` (compact: status, lessonId, userId, timestamp + _links).
- D-12: Authorization via `RequestContext.isHeadman()` + `context.groupId` match against lesson's groupId. No extra gRPC call for headman check.
- D-13: Student membership validation via `academicGrpcClient.getGroupMembers(groupId)` — verify target userId exists in headman's group. Prevents marking students outside own group (MARK-01).
- D-14: Allowed statuses: PRESENT, ABSENT, EXCUSED, FREE_ATTENDANCE. CANCELLED is system-only (via lesson.cancelled event). Reject invalid status with 400.
- D-15: Upsert semantics — if no attendance document exists for (lessonId, userId), create one. Headman can mark students before lesson.closed fires. `source=HEADMAN`, `marked_by=headmanUserId`.
- D-16: Headman assistants deferred to v4.1+. Only `is_headman=true` check in this phase.

**Redis Dedup & Rate Limit**
- D-17: Separate Redis keys: dedup = `attendance:dedup:{lessonId}:{userId}` (TTL 5s), rate limit = `attendance:rate:{userId}` (TTL 60s, INCR up to 3).
- D-18: `CheckinRateLimiter` @Service with `acquireDedup(lessonId, userId)` and `checkRateLimit(userId)`. Isolates Redis logic from business code.
- D-19: Rate limit and dedup apply ONLY to geo-checkin (`POST /checkin`), NOT to manual marking.

**Event Publishing**
- D-20: Both checkin and manual mark publish `attendance.marked` event to `rut-uit.events` fanout exchange (INFRA-06). Payload per `event-schemas/attendance.marked.json`: `{lesson_id, user_id, group_id, status, marked_by}`.
- D-21: Synchronous publishing — `RabbitTemplate.convertAndSend()` in the same thread after MongoDB upsert. If Rabbit unavailable, client gets error (fail-loud, not fire-and-forget).
- D-22: `AttendanceEventPublisher` @Service with `publishMarked(AttendanceDocument doc)`. Encapsulates envelope creation and RabbitTemplate. Both CheckinService and MarkingService call it.

### Claude's Discretion

- Exact CheckinService / MarkingService internal structure and method decomposition
- Whether to use MongoTemplate or AttendanceRepository for upsert operations
- Test structure: how many integration tests, which scenarios to prioritize
- Redis configuration bean structure
- Exact TTL value for GeofenceService cache (anywhere in 10-60 min range)

### Deferred Ideas (OUT OF SCOPE)

- **Headman assistants** (JS-HEADMAN-14) — v4.1+, requires headman_assistants table in Academic Service
- **Late checkin flow** (JS-STUDENT-06, JS-HEADMAN-05) — separate phase, needs approval workflow
- **Excuse tickets** (JS-STUDENT-03, JS-STUDENT-04) — v4.1+, own phase
- **GET /api/attendance/checkins/{id}** — full AttendanceResponse with all document fields, deferred to Phase 18 (Read Path)
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CHKN-01 | Student can geo-checkin by sending {lat, lng}, validated against campus geofence (Haversine) | GeofenceService + GeoUtils pattern defined; AcademicGrpcClient.getCampusGeofence() already wired |
| CHKN-02 | Geo-checkin validates active lesson exists for student's group (gRPC to Schedule) | ScheduleGrpcClient.getActiveLesson() already wired; ResourceNotFoundException -> 404 already mapped |
| CHKN-03 | Geo-checkin enforces 5-min time window (lesson start - 5 min to lesson end + 5 min) | LessonResponse.start_time/end_time available from proto; server-side Instant comparison |
| CHKN-04 | Geo-checkin respects is_geo_blocked flag from lesson | LessonResponse.is_geo_blocked field in proto confirmed; new GeofenceBlockedException -> 403 needed |
| CHKN-05 | Geo-checkin is idempotent via MongoDB unique index (duplicate returns 409) | Index already created in MongoConfig (uniq_lesson_user); DuplicateKeyException -> 409 already in GlobalExceptionHandler |
| CHKN-06 | Redis dedup lock prevents double-submit (5-sec TTL per lesson+user) | Redis not yet in build.gradle.kts — must add spring-boot-starter-data-redis; CheckinRateLimiter pattern defined |
| CHKN-07 | Redis rate limiting prevents abuse (3 attempts/minute per user) | Same Redis dependency; INCR + TTL 60s pattern; new RateLimitException -> 429 needed in GlobalExceptionHandler |
| MARK-01 | Headman can manually set attendance status for any student in their group | academicGrpcClient.getGroupMembers() already wired; RequestContext.isHeadman() available |
| MARK-02 | Manual marking works per student (autosave per click, not batch) | PUT endpoint per D-11; upsert via MongoTemplate ($set/$setOnInsert) |
| INFRA-06 | System publishes attendance.marked event after successful checkin/manual mark | RabbitConfig (fanout exchange rut-uit.events) already declared; RabbitTemplate already configured; event schema verified |
</phase_requirements>

---

## Standard Stack

### Core (already present in attendance-app/build.gradle.kts — verified)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-web | 3.4.x (BOM) | REST endpoints | Project standard |
| spring-boot-starter-data-mongodb | 3.4.x (BOM) | AttendanceDocument persistence, MongoTemplate upsert | Project standard for Attendance Service |
| spring-boot-starter-amqp | 3.4.x (BOM) | RabbitTemplate.convertAndSend() for event publishing | RabbitConfig already configured |
| spring-boot-starter-hateoas | 3.4.x (BOM) | EntityModel<T>, WebMvcLinkBuilder | Project HATEOAS Level 3 standard |
| spring-boot-starter-validation | 3.4.x (BOM) | @Valid on request bodies | Project standard |
| springdoc-openapi-starter-webmvc-ui | 2.7.0 | @Operation, @ApiResponse in contract | Project standard |

### New Dependency Required

| Library | Version | Purpose | Why Needed |
|---------|---------|---------|------------|
| spring-boot-starter-data-redis | 3.4.x (BOM) | StringRedisTemplate / RedisTemplate for dedup + rate limit keys | Redis not yet in build.gradle.kts — CHKN-06, CHKN-07 require it |
| testcontainers:redis | 1.20.4 (BOM) | Redis Testcontainer for integration tests | Test isolation for Redis-dependent flows |

**Installation — add to `services/attendance-service/attendance-app/build.gradle.kts`:**
```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-redis")
testImplementation("org.testcontainers:redis")  // requires testcontainers-bom already present
```

**Also add to `application.yml`:**
```yaml
spring:
  data:
    redis:
      host: redis
      port: 6379
```

**Also add to `application-test.yml`** (DynamicPropertySource will override host/port at runtime).

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| StringRedisTemplate (SETNX + EXPIRE) | Redisson RLock | Redisson is heavy; SETNX + EXPIRE is sufficient for 5s dedup TTL |
| MongoTemplate for upsert | AttendanceRepository.save() | save() does insert-or-full-replace, loses $setOnInsert safety; MongoTemplate.upsert() with Update.$set/$setOnInsert is precise — consistent with Phase 16 patterns |
| Haversine pure math | JTS Topology Suite / geo libs | Single-radius check does not justify a geo library; GeoUtils ~20 lines is correct and testable |

---

## Architecture Patterns

### Recommended Package Structure (new code in this phase)

```
attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/
├── api/
│   ├── CheckinApi.java          # @PostMapping("/attendance/checkin"), @Operation
│   └── MarkingApi.java          # @PutMapping("/attendance/lessons/{lessonId}/students/{userId}"), @Operation
└── dto/
    ├── checkin/
    │   ├── CheckinRequest.java  # record(double lat, double lng) + @Valid @NotNull
    │   └── CheckinResponse.java # class extends RepresentationModel (HATEOAS)
    └── marking/
        ├── MarkRequest.java     # record(AttendanceStatus status)
        └── MarkResponse.java    # class extends RepresentationModel (HATEOAS)

attendance-app/src/main/java/ru/rutcampustrack/attendance/
├── geofence/
│   ├── GeofenceService.java     # @Service, volatile cache, isWithinCampus(lat, lng)
│   └── GeoUtils.java           # package-private, static Haversine math
├── checkin/
│   ├── AttendanceDocument.java  # EXISTING — no changes needed
│   ├── AttendanceRepository.java # EXISTING
│   ├── CheckinController.java   # implements CheckinApi, @RequireRole(STUDENT)
│   └── CheckinService.java      # orchestrates: rate limit -> geofence -> lesson -> dedup -> insert -> event
├── marking/
│   ├── MarkingController.java   # implements MarkingApi, @RequireRole(STUDENT) headman check inside
│   └── MarkingService.java      # orchestrates: headman guard -> lesson -> group check -> upsert -> event
├── ratelimit/
│   └── CheckinRateLimiter.java  # @Service, StringRedisTemplate, acquireDedup, checkRateLimit
├── event/
│   ├── AttendanceEventPublisher.java  # NEW @Service, publishMarked(AttendanceDocument)
│   ├── EventConsumer.java       # EXISTING — no changes needed
│   └── LessonEventService.java  # EXISTING — no changes needed
└── exception/
    ├── GeofenceViolationException.java  # NEW -> 422
    ├── GeofenceBlockedException.java    # NEW -> 403 (geo-blocked flag)
    └── RateLimitException.java          # NEW -> 429
```

### Pattern 1: GeofenceService (Volatile In-Memory Cache)

Follows `SemesterCacheService` exactly. The `@PostConstruct` load is wrapped in try/catch to avoid blocking startup if Academic Service is temporarily unavailable. Refresh is triggered lazily on TTL expiry (check `lastRefreshed` Instant against configured TTL before each call).

```java
// Source: services/attendance-service/attendance-app/.../semester/SemesterCacheService.java (existing)
@Service
public class GeofenceService {

    private volatile GeofenceData cachedGeofence;    // null until first load
    private volatile Instant lastRefreshed;

    private static final Duration CACHE_TTL = Duration.ofMinutes(30); // Claude discretion

    @PostConstruct
    public void init() {
        try { refresh(); } catch (Exception e) {
            log.warn("Could not load geofence at startup: {}", e.getMessage());
        }
    }

    public boolean isWithinCampus(double lat, double lng) {
        GeofenceData g = getOrRefresh();
        return GeoUtils.isWithinRadius(lat, lng, g.lat(), g.lng(), g.radiusMeters());
    }

    private GeofenceData getOrRefresh() {
        if (cachedGeofence == null || Duration.between(lastRefreshed, Instant.now()).compareTo(CACHE_TTL) > 0) {
            refresh();
        }
        return cachedGeofence;
    }

    public void refresh() {
        GeofenceResponse r = academicGrpcClient.getCampusGeofence();
        cachedGeofence = new GeofenceData(r.getLat(), r.getLng(), r.getRadiusM());
        lastRefreshed = Instant.now();
    }

    record GeofenceData(double lat, double lng, int radiusMeters) {}
}
```

### Pattern 2: GeoUtils — Haversine Formula

```java
// Package-private — only GeofenceService calls it
class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    static boolean isWithinRadius(double lat, double lng, double centerLat, double centerLng, double radiusMeters) {
        return distanceMeters(lat, lng, centerLat, centerLng) <= radiusMeters;
    }
}
```

### Pattern 3: Redis Dedup and Rate Limit (CheckinRateLimiter)

Use `StringRedisTemplate` (simpler than `RedisTemplate<String, Long>` for string-based SETNX/INCR operations).

```java
@Service
public class CheckinRateLimiter {

    private static final int MAX_CHECKINS_PER_MINUTE = 3;
    private final StringRedisTemplate redis;

    // Dedup: SETNX with 5-second TTL — returns true if lock acquired (first request)
    public boolean acquireDedup(Long lessonId, Long userId) {
        String key = "attendance:dedup:" + lessonId + ":" + userId;
        Boolean set = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(5));
        return Boolean.TRUE.equals(set);
    }

    // Rate limit: INCR with 60-second TTL — returns true if under limit
    public boolean checkRateLimit(Long userId) {
        String key = "attendance:rate:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count == 1L) {
            redis.expire(key, Duration.ofSeconds(60));
        }
        return count <= MAX_CHECKINS_PER_MINUTE;
    }
}
```

**Critical:** `setIfAbsent` returns `null` if the connection is broken — always use `Boolean.TRUE.equals()`, not `== true`.

### Pattern 4: MongoTemplate Upsert for Manual Marking

The manual mark endpoint uses upsert semantics (D-15). For checkin, `AttendanceRepository.save()` is sufficient because the MongoDB unique index will catch duplicates. For manual marking (headman overwriting existing status), MongoTemplate with `$set` on mutable fields and `$setOnInsert` on immutable fields is correct and consistent with Phase 16 patterns:

```java
// MongoTemplate upsert for MarkingService
Query filter = Query.query(
    Criteria.where("lesson_id").is(lessonId).and("user_id").is(userId)
);
Update update = new Update()
    .set("status", status)
    .set("source", AttendanceSource.HEADMAN)
    .set("marked_by", headmanUserId)
    .set("updated_at", Instant.now())
    .setOnInsert("lesson_id", lessonId)
    .setOnInsert("user_id", userId)
    .setOnInsert("group_id", lesson.getGroupId())
    .setOnInsert("subject_id", lesson.getSubjectId())
    .setOnInsert("semester_id", semesterCacheService.getActiveSemesterId())
    .setOnInsert("lesson_number", lesson.getLessonNumber())
    .setOnInsert("lesson_date", LocalDate.parse(lesson.getDate()))
    .setOnInsert("created_at", Instant.now());

mongoTemplate.upsert(filter, update, AttendanceDocument.class);
```

**Note:** For checkin (STUDENT_GEO), use `AttendanceRepository.save(doc)` — cleaner for insert-only path. MongoDB unique index catches duplicates and throws `DuplicateKeyException` -> 409 (already in GlobalExceptionHandler).

### Pattern 5: AttendanceEventPublisher

```java
// Constructs envelope matching event-schemas/attendance.marked.json exactly
@Service
public class AttendanceEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishMarked(AttendanceDocument doc) {
        Map<String, Object> payload = Map.of(
            "lesson_id", doc.getLessonId(),
            "user_id",   doc.getUserId(),
            "group_id",  doc.getGroupId(),
            "status",    doc.getStatus().name().toLowerCase(),
            "marked_by", doc.getSource().name().toLowerCase()
        );
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_type", "attendance.marked");
        envelope.put("event_id",   UUID.randomUUID().toString());
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put("payload",    payload);

        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
    }
}
```

**Important:** Fanout exchange routing key must be `""` (empty string) — fanout ignores routing key but RabbitTemplate requires it. Verified in Phase 16 RabbitConfig: `FanoutExchange("rut-uit.events", true, false)`.

### Pattern 6: Contract API Interface (CheckinApi)

```java
// In attendance-api-contract — no Lombok, no Spring beans
@Tag(name = "Checkin", description = "Геоотметка студентов")
@RequestMapping("/attendance")
public interface CheckinApi {

    @Operation(summary = "Студент отмечается геолокацией")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Отметка принята"),
        @ApiResponse(responseCode = "403", description = "Геоблокировка активна"),
        @ApiResponse(responseCode = "404", description = "Активная пара не найдена"),
        @ApiResponse(responseCode = "409", description = "Уже отмечался"),
        @ApiResponse(responseCode = "422", description = "Вне геофенса"),
        @ApiResponse(responseCode = "429", description = "Превышен лимит запросов")
    })
    @PostMapping("/checkin")
    ResponseEntity<EntityModel<CheckinResponse>> checkin(@Valid @RequestBody CheckinRequest request);
}
```

### Anti-Patterns to Avoid

- **Do not add Lombok to attendance-api-contract:** CLAUDE.md forbids Lombok in `*-api-contract` modules. Use Java records for request DTOs, plain classes for response DTOs (RepresentationModel subclasses).
- **Do not cast Jackson numeric values directly to Long:** EventConsumer already shows the pattern — use `((Number) value).longValue()`. Applies in event payloads if numeric fields are deserialized as Object.
- **Do not use `@Enumerated(EnumType.ORDINAL)`:** CLAUDE.md prohibits it. AttendanceStatus/AttendanceSource already use string converters.
- **Do not skip the Redis null check:** `setIfAbsent` returns null on connection failure, not false. Always `Boolean.TRUE.equals(result)`.
- **Do not use `Boolean.parseBoolean` on X-Is-Headman in new tests:** UserContextFilter already handles the header; tests should set the header directly (see SecuritySmokeTest pattern).
- **Do not declare new `@Bean RabbitTemplate` or `@Bean Jackson2JsonMessageConverter`:** Already declared in RabbitConfig as `attendanceJacksonMessageConverter` and `rabbitTemplate`. Adding a second will cause ambiguous bean errors.
- **Do not use `channelTransacted=true` on RabbitTemplate:** Per RabbitConfig comment — causes message loss.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Redis key TTL management | Custom TTL scheduler | `StringRedisTemplate.setIfAbsent(key, val, duration)` | Atomic SETNX+EXPIRE in one call; built into Spring Data Redis |
| RabbitMQ message serialization | Manual JSON string building | `Jackson2JsonMessageConverter` already in RabbitConfig | Consistent with all other event messages in the project |
| MongoDB upsert with partial update | Fetch + modify + save | `MongoTemplate.upsert(query, update, class)` | Atomic operation; prevents race conditions with auto-absent |
| Time window validation | External time service | Server `Instant.now()` vs `LessonResponse.start_time/end_time` | LessonResponse is already the single source of truth (D-02) |
| Group membership check | Custom query to Academic DB | `academicGrpcClient.getGroupMembers(groupId)` already wired | Cross-service — cannot query Academic DB directly |

**Key insight:** The infrastructure layer (Phase 15) deliberately pre-built all the plumbing. Phase 17 is primarily business logic wiring, not new infrastructure setup.

---

## Common Pitfalls

### Pitfall 1: Redis INCR + EXPIRE Race Condition
**What goes wrong:** If two threads simultaneously call INCR for the same rate limit key when count transitions from 0 to 1, both set EXPIRE and the TTL may be reset on the second call, extending the window unintentionally.
**Why it happens:** INCR and EXPIRE are two separate Redis commands.
**How to avoid:** Only call `expire()` when the returned count equals exactly 1L (the first increment). This is the pattern used in CheckinRateLimiter above — `if (count == 1L)`.
**Warning signs:** Rate limit window duration is inconsistent in tests.

### Pitfall 2: AbstractAttendanceIntegrationTest Needs Redis Testcontainer
**What goes wrong:** Integration tests fail with `Connection refused` to Redis at test startup because `AbstractAttendanceIntegrationTest` only starts MongoDB and RabbitMQ containers.
**Why it happens:** Redis is new in this phase; the base class was built without it.
**How to avoid:** Add a static `RedisContainer` to `AbstractAttendanceIntegrationTest` alongside the existing MongoDB and RabbitMQ containers. Add `@DynamicPropertySource` override for `spring.data.redis.host` and `spring.data.redis.port`. Use `"redis:7.2"` image (consistent with docker-compose.yml if Redis is listed there).
**Warning signs:** Test fails on bean initialization for `StringRedisTemplate` or `RedisConnectionFactory`.

### Pitfall 3: GeofenceService @PostConstruct Blocked in Tests
**What goes wrong:** `GeofenceService` calls `academicGrpcClient.getCampusGeofence()` in `@PostConstruct`. In integration tests, the gRPC clients are `@MockitoBean` — but if `GeofenceService` is NOT mocked, its `@PostConstruct` will call the mock and return null, causing a NullPointerException on `GeofenceResponse.getLat()`.
**Why it happens:** Unlike `SemesterCacheService` (which wraps its `@PostConstruct` in try/catch), `GeofenceService` must do the same — or the mock must return a valid `GeofenceResponse`.
**How to avoid:** Wrap `@PostConstruct init()` body in try/catch identical to `SemesterCacheService`. Tests can either mock `GeofenceService` as a `@MockitoBean` or configure the `AcademicGrpcClient` mock to return a valid `GeofenceResponse` in `AbstractAttendanceIntegrationTest`.

### Pitfall 4: Manual Mark Checks Wrong Group ID
**What goes wrong:** Headman from group 10 calls `PUT /lessons/42/students/99`. If the headman's `RequestContext.getGroupId()` is compared against the wrong group (e.g., the target student's group instead of the lesson's group), authorization fails incorrectly or passes incorrectly.
**Why it happens:** The lesson has a `group_id`; the headman has a `group_id`; the target student also belongs to a group. The correct check is `requestContext.getGroupId().equals(lesson.getGroupId())`.
**How to avoid:** D-12 specifies `context.groupId` match against lesson's groupId. After that check, D-13 verifies the target userId is a member of the headman's group (i.e., `getGroupMembers(headman.groupId)` contains `targetUserId`).
**Warning signs:** 403 when headman correctly marks own group, or no 403 when headman attempts to mark another group's student.

### Pitfall 5: CheckinResponse and MarkResponse Must Extend RepresentationModel
**What goes wrong:** `EntityModel<CheckinResponse>` requires `CheckinResponse` to be a POJO (not a record) if HATEOAS link enrichment is needed. Records can work if only wrapping, but CLAUDE.md says Response DTO = class for HATEOAS.
**Why it happens:** `RepresentationModel<T>` requires extension for HATEOAS self-link auto-population in assemblers.
**How to avoid:** Declare `CheckinResponse extends RepresentationModel<CheckinResponse>` and `MarkResponse extends RepresentationModel<MarkResponse>`. No Lombok in the contract module — use constructor + getters manually.

### Pitfall 6: 422 and 429 Not Handled in GlobalExceptionHandler
**What goes wrong:** New exceptions (`GeofenceViolationException`, `RateLimitException`) thrown in services will fall through to the generic `Exception` handler and return 500 instead of 422/429.
**Why it happens:** GlobalExceptionHandler has handlers for 404, 403, 400, 409, 503 — but no 422 or 429 handlers.
**How to avoid:** Add `@ExceptionHandler(GeofenceViolationException.class)` -> `HttpStatus.UNPROCESSABLE_ENTITY` (422) and `@ExceptionHandler(RateLimitException.class)` -> `HttpStatus.TOO_MANY_REQUESTS` (429) to GlobalExceptionHandler before wiring any controllers.

### Pitfall 7: LessonResponse start_time/end_time Are Strings
**What goes wrong:** Time window validation compares server `Instant.now()` against lesson start/end times. `LessonResponse.start_time` and `end_time` are proto `string` fields (e.g., "09:00"). They are NOT ISO-8601 timestamps — they are time-of-day strings that must be combined with the lesson's `date` field.
**Why it happens:** Proto schedule.proto defines `start_time` and `end_time` as plain strings (likely "HH:mm" format).
**How to avoid:** Combine `lesson.getDate()` (ISO date, e.g., "2026-04-04") with `lesson.getStartTime()` (e.g., "09:00") to build a `LocalDateTime`, then convert to `Instant` using server timezone. Apply the ±5 min buffer to that Instant. Use `ZoneId.of("Europe/Moscow")` or UTC — pick one and be consistent.

---

## Code Examples

### Time Window Validation

```java
// Source: schedule.proto — LessonResponse.date (ISO date), start_time ("HH:mm"), end_time ("HH:mm")
// Assumes Moscow timezone; adjust if UTC stored in Schedule Service
private static final ZoneId SERVER_ZONE = ZoneId.of("Europe/Moscow");
private static final Duration CHECKIN_BUFFER = Duration.ofMinutes(5);

boolean isWithinCheckinWindow(LessonResponse lesson, Instant now) {
    LocalDate date = LocalDate.parse(lesson.getDate());
    LocalTime start = LocalTime.parse(lesson.getStartTime());
    LocalTime end   = LocalTime.parse(lesson.getEndTime());

    Instant windowOpen  = date.atTime(start).atZone(SERVER_ZONE).toInstant().minus(CHECKIN_BUFFER);
    Instant windowClose = date.atTime(end).atZone(SERVER_ZONE).toInstant().plus(CHECKIN_BUFFER);

    return now.isAfter(windowOpen) && now.isBefore(windowClose);
}
```

**Important:** Verify Schedule Service timezone before finalizing. If Schedule stores in UTC, use `ZoneOffset.UTC`. The proto field format for `start_time` needs to be confirmed from Schedule Service implementation (expected "HH:mm" based on how Schedule phase was built).

### Creating AttendanceDocument for Checkin

```java
// Source: AttendanceDocument.java (Phase 15 output) — @Builder available
AttendanceDocument doc = AttendanceDocument.builder()
    .lessonId(lesson.getId())
    .userId(requestContext.getUserId())
    .groupId(requestContext.getGroupId())
    .subjectId(lesson.getSubjectId())
    .semesterId(semesterCacheService.getActiveSemesterId())
    .lessonNumber(lesson.getLessonNumber())
    .lessonDate(LocalDate.parse(lesson.getDate()))
    .status(AttendanceStatus.PRESENT)
    .source(AttendanceSource.STUDENT_GEO)
    .markedBy(null)           // student marks themselves
    .createdAt(Instant.now())
    .updatedAt(Instant.now())
    .build();
attendanceRepository.save(doc);  // DuplicateKeyException -> 409 via GlobalExceptionHandler
```

### AbstractAttendanceIntegrationTest Redis Extension

```java
// Extend existing abstract base class for Redis-aware tests
// Add to AbstractAttendanceIntegrationTest static block:
static final GenericContainer<?> REDIS;

static {
    MONGODB  = new MongoDBContainer("mongo:7.0");
    RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management");
    REDIS    = new GenericContainer<>("redis:7.2").withExposedPorts(6379);
    MONGODB.start();
    RABBITMQ.start();
    REDIS.start();
}

@DynamicPropertySource
static void overrideProperties(DynamicPropertyRegistry registry) {
    // ...existing overrides...
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@MockitoBean` annotation | Available as `@MockitoBean` in Spring 6.2 (replaces `@MockBean`) | Spring Boot 3.4 | AbstractAttendanceIntegrationTest already uses correct API |
| RedisTemplate + manual serializer | StringRedisTemplate for string keys | Spring Data Redis 3.x | StringRedisTemplate is simpler for string key/value operations (dedup, rate limit) |
| `.setIfAbsent(key, val)` returns null | `.setIfAbsent(key, val, duration)` atomic | Spring Data Redis 2.1+ | Always prefer 3-arg form to avoid separate EXPIRE call race |

**Deprecated/outdated:**
- `@MockBean` (Spring Boot 2.x): Replaced by `@MockitoBean` in Spring Boot 3.4+. Code already uses `@MockitoBean` correctly.
- `MongoRepository.save()` for upsert with partial field preservation: Replaced by `MongoTemplate.upsert()` with `Update.$set/$setOnInsert` for safety.

---

## Open Questions

1. **Schedule Service timezone for start_time/end_time**
   - What we know: proto defines `start_time` and `end_time` as string fields; `date` as ISO date string
   - What's unclear: Whether "09:00" is stored as Moscow local time or UTC in Schedule Service
   - Recommendation: Check `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java` (or equivalent) to confirm timezone used when writing `start_time`. Default to Moscow (`ZoneId.of("Europe/Moscow")`) if the Schedule phase used that; use UTC if ISO-8601 timestamps.

2. **GeofenceService test strategy**
   - What we know: `AcademicGrpcClient` is `@MockitoBean` in integration tests; `SemesterCacheService` is also mocked
   - What's unclear: Whether `GeofenceService` should be mocked at the service level (simpler) or tested with a real mock response from `AcademicGrpcClient`
   - Recommendation: Mock `GeofenceService` as `@MockitoBean` in `AbstractAttendanceIntegrationTest` (same as `SemesterCacheService`). Add a dedicated `GeofenceMathTest` unit test for `GeoUtils` (pure math, zero dependencies).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| MongoDB 7.0 | AttendanceDocument persistence | ✓ (Testcontainer) | 7.0 | — |
| RabbitMQ 3.13 | Event publishing (INFRA-06) | ✓ (Testcontainer) | 3.13-management | — |
| Redis 7.2 | CHKN-06 dedup, CHKN-07 rate limit | ✗ (not yet in test stack) | — | Must add Testcontainer |
| spring-boot-starter-data-redis | Redis ops in CheckinRateLimiter | ✗ (not in build.gradle.kts) | — | Must add dependency |
| Haversine math | CHKN-01 geofence check | ✓ (pure Java, no lib needed) | N/A | — |

**Missing dependencies with no fallback:**
- `spring-boot-starter-data-redis` + `testcontainers:redis` — required by CHKN-06 and CHKN-07 with no viable substitute

---

## Validation Architecture

> `workflow.nyquist_validation` is absent from `.planning/config.json` — treating as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot Test + Testcontainers |
| Config file | `services/attendance-service/attendance-app/build.gradle.kts` (test dependencies) |
| Quick run command | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*CheckinServiceTest" --tests "*MarkingServiceTest" --tests "*GeoUtilsTest"` |
| Full suite command | `./gradlew.bat :services:attendance-service:attendance-app:test` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CHKN-01 | Student inside geofence -> status=present in MongoDB | Integration | Full suite | ❌ Wave 0 |
| CHKN-01 | Student outside geofence -> 422, no document written | Integration | Full suite | ❌ Wave 0 |
| CHKN-01 | GeoUtils.distanceMeters math correctness | Unit | Quick run `*GeoUtilsTest` | ❌ Wave 0 |
| CHKN-02 | No active lesson for group -> 404 | Integration | Full suite | ❌ Wave 0 |
| CHKN-03 | Time outside window -> 422 or 404 | Integration | Full suite | ❌ Wave 0 |
| CHKN-04 | is_geo_blocked=true -> 403 | Integration | Full suite | ❌ Wave 0 |
| CHKN-05 | Second identical checkin -> 409 (MongoDB unique index) | Integration | Full suite | ❌ Wave 0 |
| CHKN-06 | Second checkin within 5s -> 409 (Redis dedup) | Integration | Full suite | ❌ Wave 0 |
| CHKN-07 | 4th checkin attempt in 60s -> 429 | Integration | Full suite | ❌ Wave 0 |
| MARK-01 | Headman marks student in own group -> 200 + document upserted | Integration | Full suite | ❌ Wave 0 |
| MARK-01 | Headman marks student outside own group -> 403 | Integration | Full suite | ❌ Wave 0 |
| MARK-02 | Second mark on same student updates status (upsert, not insert) | Integration | Full suite | ❌ Wave 0 |
| INFRA-06 | After checkin, attendance.marked event published to fanout exchange | Integration | Full suite | ❌ Wave 0 |
| INFRA-06 | After manual mark, attendance.marked event published | Integration | Full suite | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*GeoUtilsTest" --tests "*CheckinRateLimiterTest"`
- **Per wave merge:** `./gradlew.bat :services:attendance-service:attendance-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `tests/checkin/CheckinIntegrationTest.java` — covers CHKN-01 through CHKN-07
- [ ] `tests/marking/MarkingIntegrationTest.java` — covers MARK-01, MARK-02, INFRA-06 (marking path)
- [ ] `tests/geofence/GeoUtilsTest.java` — unit test for Haversine math (no Spring context)
- [ ] `tests/ratelimit/CheckinRateLimiterTest.java` — unit test with embedded Redis or mocked StringRedisTemplate
- [ ] `AbstractAttendanceIntegrationTest.java` — must be extended with Redis Testcontainer
- [ ] `application-test.yml` — must add `spring.data.redis.*` placeholder (overridden by DynamicPropertySource)
- [ ] Framework install: `implementation("org.springframework.boot:spring-boot-starter-data-redis")` + `testImplementation("org.testcontainers:redis")` in `build.gradle.kts`

---

## Sources

### Primary (HIGH confidence)

- Existing codebase (verified by direct Read): `attendance-app/build.gradle.kts`, `RabbitConfig.java`, `GlobalExceptionHandler.java`, `AttendanceDocument.java`, `SemesterCacheService.java`, `AbstractAttendanceIntegrationTest.java`, `RequestContext.java`, `RoleCheckAspect.java`, `LessonEventService.java`, `EventConsumer.java`, `MongoConfig.java`
- Proto contracts (verified): `proto/schedule.proto` (LessonResponse fields), `proto/academic.proto` (GeofenceResponse fields)
- Event schema (verified): `event-schemas/attendance.marked.json` (envelope structure + payload fields)
- CONTEXT.md (all 22 decisions, canonical refs, code context)

### Secondary (MEDIUM confidence)

- Spring Data Redis `setIfAbsent` atomic TTL — standard Spring Data Redis API, consistent with Spring Boot 3.4 BOM
- Testcontainers Redis `GenericContainer<>("redis:7.2")` — standard Testcontainers pattern; redis module may require `testcontainers:redis` or `GenericContainer` depending on TC version

### Tertiary (LOW confidence)

- Schedule Service timezone assumption (Moscow) — inferred from RUT MIИТ location; not verified by reading Schedule Service source code

---

## Project Constraints (from CLAUDE.md)

These directives are mandatory and override any research recommendations:

| Constraint | Impact on Phase 17 |
|------------|-------------------|
| NO Lombok in `*-api-contract` modules | `CheckinRequest`, `CheckinResponse`, `MarkRequest`, `MarkResponse`, `CheckinApi`, `MarkingApi` — no @Data, @Builder, @Value |
| Request DTO = Java record | `CheckinRequest`, `MarkRequest` must be records |
| Response DTO = class (for HATEOAS RepresentationModel) | `CheckinResponse`, `MarkResponse` must extend `RepresentationModel<T>` |
| HTTP mappings ONLY in contract interface | `@PostMapping`, `@PutMapping` go in `CheckinApi`/`MarkingApi`, NOT in controllers |
| `@ControllerAdvice` centralizes error handling | New exceptions (422, 429) must be added to existing `GlobalExceptionHandler`, not caught in services |
| Enum UPPER_CASE in Java, lowercase in MongoDB | `AttendanceStatus.PRESENT` stored as "present" — existing `MongoConvertersConfig` already handles this |
| NEVER `@Enumerated(EnumType.ORDINAL)` | N/A for MongoDB but no JPA entities in this phase |
| HATEOAS Level 3 | `EntityModel<CheckinResponse>` with `_links`: self + lesson-checkin |
| Errors: RFC 7807 Problem Details | All new exceptions must use existing `ErrorResponse` record |
| `@ControllerAdvice` only throws, no response construction in controllers | Controllers call services, services throw domain exceptions, handler maps to HTTP |
| Package: `ru.rutcampustrack.attendance.{module}` | `checkin/`, `marking/`, `geofence/`, `ratelimit/` packages |
| Attendance Service checkin/ and report/ domains isolated via `AttendanceReadPort` | Phase 17 is write-only — no report domain involvement; `AttendanceReadPort` is Phase 18 concern |
| Soft delete (status = 'archived') | N/A — no user deletion in this phase |
| PostgreSQL conventions | N/A — Attendance Service uses MongoDB |

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified directly from build.gradle.kts; Redis absence confirmed by grep
- Architecture patterns: HIGH — all patterns derived from existing code (SemesterCacheService, LessonEventService, GlobalExceptionHandler, RoleCheckAspect)
- Pitfalls: HIGH — derived from existing code decisions (STATE.md notes, Phase 16 patterns), proto field types, and established Spring Data Redis behavior
- Time window pitfall: MEDIUM — timezone assumption not verified against Schedule Service source

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable stack; only Schedule Service timezone assumption needs verification before implementation)

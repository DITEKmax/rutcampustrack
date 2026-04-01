# Phase 11: REST API + gRPC Client - Research

**Researched:** 2026-04-01
**Domain:** Spring Boot REST + HATEOAS + gRPC client (contract-first microservice)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Use `net.devh:grpc-client-spring-boot-starter` for the Academic Service gRPC client. Add `com.google.protobuf` Gradle plugin to schedule-app for compiling `academic.proto` client stubs. Phase 14 will add `schedule.proto` server stubs.
- **D-02:** Create an `AcademicGrpcClient` wrapper class that encapsulates all gRPC calls. Always use `.withDeadlineAfter(3, SECONDS)` on every call (pre-decided in STATE.md).
- **D-03:** When Academic Service is unavailable (gRPC failure), reject the operation with HTTP 503 Service Unavailable. FK integrity is non-negotiable — no orphan templates.
- **D-04:** In integration tests, `@MockitoBean` the `AcademicGrpcClient` wrapper. No embedded gRPC server needed. Matches the project pattern of mocking RabbitTemplate.
- **D-05:** RPCs needed as client: `GetGroup` (validate group_id), `GetActiveSemester` (validate semester_id), `IsHeadman` (authorize headman for specific group). `GetUserById` NOT needed.
- **D-06:** FK validation on create: validate `group_id` (GetGroup — exists and active) and `semester_id` (GetActiveSemester). `teacher_id` and `subject_id` are trusted.
- **D-07:** Template deletion is soft delete: set `is_active = false`. Existing generated lessons remain.
- **D-08:** Headman authorization via `IsHeadman` gRPC call to verify headman belongs to the specific group. ADMIN bypasses this check.
- **D-09:** Template update (PUT) allows changing: teacher_id, subject_id, room, day_of_week, lesson_number, start_time, end_time, week_type. Does NOT allow changing group_id or semester_id.
- **D-10:** Who can CRUD templates: HEADMAN (for their own group) + ADMIN (for any group).
- **D-11:** Cancel/restore permissions: HEADMAN (own group) + ADMIN (any group).
- **D-12:** Mass-cancel: `POST /schedule/lessons/mass-cancel` with `{group_id, date_from, date_to, reason}`. Cancels all lessons with status='planned' in the date range for the group.
- **D-13:** `cancel_reason` is REQUIRED on all cancel operations (single and mass).
- **D-14:** Restore: only `cancelled -> planned`. Clears `cancel_reason`. Active/closed lessons cannot be restored.
- **D-15:** Geo-block toggle: HEADMAN/ADMIN can set `is_geo_blocked = true/false` on a specific lesson.
- **D-16:** Response contains IDs only (subject_id, teacher_id, group_id) — no name enrichment. Frontend resolves names separately.
- **D-17:** Flat lesson list with HATEOAS: `GET /schedule/groups/{groupId}/lessons?dateFrom=...&dateTo=...` returns `PagedModel<EntityModel<LessonResponse>>`.
- **D-18:** Optional status filter: `?status=planned,active,closed` to exclude cancelled by default.
- **D-19:** Any authenticated user can view schedule (VIEW-01). STUDENT, TEACHER, HEADMAN, ADMIN all have read access.

### Claude's Discretion

- DTO structure for CreateScheduleItemRequest, UpdateScheduleItemRequest, LessonResponse, ScheduleItemResponse
- Service layer organization (ScheduleItemService, LessonService — or combined)
- HATEOAS link structure (self, collection, cancel, restore actions)
- Error response specifics within RFC 7807 framework
- Exact endpoint paths under `/schedule/` prefix
- Whether PATCH or PUT for template updates (CLAUDE.md says PUT = full update, PATCH = partial)
- Pagination defaults and max page size for schedule view

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TMPL-01 | Headman can create a schedule template (subject, teacher, room, day, time, week parity) | ScheduleItem entity ready; create API + service; gRPC validation of group_id and semester_id via AcademicGrpcClient |
| TMPL-02 | Headman can update an existing schedule template | PUT endpoint; group_id/semester_id immutable; re-validate group ownership |
| TMPL-03 | Headman can delete (deactivate) a schedule template | Soft delete: is_active=false; existing lessons unaffected |
| TMPL-04 | Headman can view all schedule templates for their group | GET with groupId + semesterId filter; ScheduleItemRepository.findByGroupIdAndSemesterIdAndIsActiveTrue already exists |
| TMPL-05 | System validates subject/teacher via gRPC to Academic Service before creating template | AcademicGrpcClient.validateGroup + validateSemester; 503 on gRPC failure |
| LSSN-04 | Headman can cancel a specific lesson with reason | PATCH /schedule/lessons/{id}/cancel; status planned->cancelled; cancel_reason required |
| LSSN-05 | Headman can restore a cancelled lesson | PATCH /schedule/lessons/{id}/restore; cancelled->planned only; clears cancel_reason |
| LSSN-06 | Headman can mass-cancel lessons for a date range | POST /schedule/lessons/mass-cancel; query by group+dateRange; update all planned lessons |
| LSSN-07 | Headman can toggle geo-checkin blocking on a specific lesson | PATCH /schedule/lessons/{id}/geo-block; toggle is_geo_blocked boolean |
| VIEW-01 | Any authenticated user can view group schedule for a date range | GET /schedule/groups/{groupId}/lessons?dateFrom&dateTo; role=ANY_AUTHENTICATED |
| VIEW-02 | Schedule response includes lesson status, room, teacher, subject info | LessonResponse DTO with all fields from joined Lesson+ScheduleItem; IDs only, no enrichment |

</phase_requirements>

---

## Summary

Phase 11 builds the primary REST API surface for Schedule Service on top of the Phase 10 foundation. The work is divided into three natural domains: (1) schedule template CRUD, (2) lesson state operations, and (3) schedule viewing. All three depend on the gRPC client to Academic Service that must be wired first.

The codebase already provides all the infrastructure needed: entities (`ScheduleItem`, `Lesson`), repositories with necessary finder methods, the security layer (`UserContextFilter`, `@RequireRole`, `RoleCheckAspect`, `RequestContext`), RFC 7807 error handling (`GlobalExceptionHandler`), and the integration test base (`AbstractScheduleIntegrationTest`). Phase 11 adds the contract interfaces, DTOs, service logic, and controllers on top of this foundation without needing new infrastructure.

The key architectural challenge is the `HEADMAN` authorization model. The `UserRole` enum in `schedule-api-contract` has only `ADMIN`, `TEACHER`, `STUDENT` — headman is `STUDENT + is_headman=true` expressed via `RequestContext.isHeadman()`. Every write endpoint for templates and lessons must combine `@RequireRole({UserRole.ADMIN, UserRole.STUDENT})` with a service-layer headman check, then further validate group ownership via `AcademicGrpcClient.isHeadman(userId, groupId)` for HEADMAN callers.

**Primary recommendation:** Wire the `AcademicGrpcClient` first (build.gradle.kts + application.yml), then implement ScheduleItem API, then Lesson API, then the schedule view endpoint. Each wave can be independently tested via MockMvc + `@MockitoBean AcademicGrpcClient`.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `net.devh:grpc-client-spring-boot-starter` | 3.1.0.RELEASE | gRPC channel management + Spring integration | Already used in academic-app tests; D-01 locked |
| `com.google.protobuf` Gradle plugin | 0.9.4 | Compiles `.proto` files to Java stubs | Already used in academic-app build.gradle.kts |
| `com.google.protobuf:protoc` | 3.25.3 | Protobuf compiler | Matches academic-app |
| `io.grpc:protoc-gen-grpc-java` | 1.63.0 | gRPC Java code generator | Matches academic-app |
| `javax.annotation:javax.annotation-api` | 1.3.2 | `@Generated` for protobuf stubs | Required — Java 9+ removed from JDK |
| `org.springframework.boot:spring-boot-starter-hateoas` | (BOM managed) | `EntityModel`, `PagedModel`, assemblers | Already in schedule-app dependencies |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.7.0 | Swagger UI + OpenAPI spec | Already in schedule-app |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.springframework.boot:spring-boot-starter-validation` | (BOM managed) | `@Valid`, `@NotNull`, `@Size` on request DTOs | All create/update request records |
| Lombok `@Builder` / `@Setter` | (BOM managed) | Entity mutability | Already in schedule-app; Lesson and ScheduleItem already use it |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `grpc-client-spring-boot-starter` | Manual `ManagedChannel` | Starter handles lifecycle, config, metrics — never hand-roll |
| Separate assembler class per entity | Inline `EntityModel.of()` | Assembler gives reusable HATEOAS links; academic-service uses explicit assembler class |

**Installation (additions to schedule-app/build.gradle.kts):**
```kotlin
// Add Protobuf plugin at top of plugins block:
id("com.google.protobuf") version "0.9.4"

// Add dependencies:
implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
compileOnly("javax.annotation:javax.annotation-api:1.3.2")

// Add sourceSets and protobuf block (copy from academic-app/build.gradle.kts verbatim)
```

---

## Architecture Patterns

### Recommended Project Structure

New source tree additions in `schedule-app`:

```
src/main/java/ru/rutcampustrack/schedule/
├── grpc/
│   └── AcademicGrpcClient.java       # wrapper (D-02) — all 3 RPC calls
├── item/
│   ├── entity/ScheduleItem.java      # Phase 10 — exists
│   ├── repository/ScheduleItemRepository.java  # Phase 10 — exists
│   ├── ScheduleItemService.java      # NEW
│   ├── ScheduleItemController.java   # NEW implements ScheduleItemApi
│   └── ScheduleItemAssembler.java    # NEW — HATEOAS assembler
└── lesson/
    ├── entity/Lesson.java            # Phase 10 — exists
    ├── repository/LessonRepository.java  # Phase 10 — partial — needs new finders
    ├── LessonService.java            # NEW
    ├── LessonController.java         # NEW implements LessonApi
    └── LessonAssembler.java          # NEW — HATEOAS assembler

src/main/java/ru/rutcampustrack/schedule/contract/  (schedule-api-contract)
├── api/
│   ├── ScheduleItemApi.java          # NEW — template CRUD contract
│   └── LessonApi.java                # NEW — lesson ops + view contract
└── dto/
    ├── item/
    │   ├── CreateScheduleItemRequest.java   # record
    │   ├── UpdateScheduleItemRequest.java   # record
    │   └── ScheduleItemResponse.java        # class extends RepresentationModel
    └── lesson/
        ├── CancelLessonRequest.java         # record {reason}
        ├── MassCancelRequest.java           # record {groupId, dateFrom, dateTo, reason}
        ├── GeoBlockRequest.java             # record {blocked}
        └── LessonResponse.java              # class extends RepresentationModel
```

### Pattern 1: Contract-First API Interface

All HTTP mappings live in the contract interface. Controller only implements the interface. This is the established pattern from `SubjectApi` / `SubjectController`.

```java
// In schedule-api-contract — ScheduleItemApi.java
@Tag(name = "Schedule Items", description = "Управление шаблонами расписания")
@RequestMapping("/schedule/items")
public interface ScheduleItemApi {

    @Operation(summary = "Создать шаблон расписания (HEADMAN/ADMIN)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Шаблон создан"),
        @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
        @ApiResponse(responseCode = "403", description = "Нет прав"),
        @ApiResponse(responseCode = "503", description = "Academic Service недоступен")
    })
    @PostMapping
    ResponseEntity<EntityModel<ScheduleItemResponse>> createScheduleItem(
            @Valid @RequestBody CreateScheduleItemRequest request);

    // ... other methods
}
```

```java
// In schedule-app — ScheduleItemController.java
@RestController
public class ScheduleItemController implements ScheduleItemApi {

    @Override
    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})
    public ResponseEntity<EntityModel<ScheduleItemResponse>> createScheduleItem(
            CreateScheduleItemRequest request) {
        ScheduleItem item = scheduleItemService.createScheduleItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toModel(item));
    }
}
```

### Pattern 2: AcademicGrpcClient Wrapper

All gRPC calls isolated in one class. Every call uses `.withDeadlineAfter(3, SECONDS)`. Wraps gRPC `StatusRuntimeException` into a domain-level `AcademicServiceUnavailableException` (mapped to HTTP 503 by `GlobalExceptionHandler`).

```java
// Source: academic-app AcademicGrpcServiceImpl + D-02 decision
@Component
public class AcademicGrpcClient {

    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    public GroupResponse validateGroup(Long groupId) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getGroup(GroupRequest.newBuilder().setGroupId(groupId).build());
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    public SemesterResponse getActiveSemester() {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getActiveSemester(Empty.getDefaultInstance());
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }

    public boolean isHeadman(Long userId, Long groupId) {
        try {
            HeadmanCheckResponse response = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .isHeadman(HeadmanCheckRequest.newBuilder()
                            .setUserId(userId).setGroupId(groupId).build());
            return response.getIsHeadman();
        } catch (StatusRuntimeException e) {
            throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
        }
    }
}
```

**application.yml addition:**
```yaml
grpc:
  server:
    port: 19092
  client:
    academic-service:
      address: static://academic-service:19091
      negotiation-type: plaintext
```

**application-test.yml addition** (prevents gRPC client from trying to connect during tests):
```yaml
grpc:
  server:
    port: -1
  client:
    academic-service:
      address: static://localhost:19091
      negotiation-type: plaintext
```
The `@MockitoBean AcademicGrpcClient` in tests ensures the real stub is never invoked; the address config is still required by the autoconfiguration but the bean is replaced before any call is made.

### Pattern 3: Headman Authorization — Two-Layer Check

The `UserRole` enum in `schedule-contract` does NOT have a `HEADMAN` value — headman is `STUDENT + is_headman=true` from headers. Authorization logic:

```java
// In service layer
private void requireHeadmanForGroup(Long targetGroupId) {
    UserRole role = requestContext.getRole();
    if (role == UserRole.ADMIN) return; // ADMIN bypasses

    if (!requestContext.isHeadman()) {
        throw new AccessDeniedException("Only headman or admin can perform this action");
    }
    // Verify headman actually belongs to this group via gRPC (D-08)
    boolean confirmed = academicGrpcClient.isHeadman(requestContext.getUserId(), targetGroupId);
    if (!confirmed) {
        throw new AccessDeniedException("You are not headman of group " + targetGroupId);
    }
}
```

This pattern must be applied consistently to: createScheduleItem, updateScheduleItem, deleteScheduleItem, cancelLesson, restoreLesson, massCancelLessons, toggleGeoBlock.

### Pattern 4: Lesson State Machine Guard

Cancel/restore must validate status transitions before persisting:

```java
// In LessonService
public Lesson cancelLesson(Long lessonId, CancelLessonRequest request) {
    Lesson lesson = lessonRepository.findById(lessonId)
            .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));

    // Validate group ownership via ScheduleItem
    ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
            .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", lesson.getScheduleItemId()));

    requireHeadmanForGroup(item.getGroupId());

    if (lesson.getStatus() != LessonStatus.PLANNED) {
        throw new InvalidLessonStateException(
                "Only planned lessons can be cancelled, current status: " + lesson.getStatus());
    }
    lesson.setStatus(LessonStatus.CANCELLED);
    lesson.setCancelReason(request.reason());
    return lessonRepository.save(lesson);
}

public Lesson restoreLesson(Long lessonId) {
    // ... similar; only CANCELLED -> PLANNED allowed (D-14)
}
```

### Pattern 5: Schedule View with Multi-Table Query

`LessonResponse` must join Lesson with ScheduleItem data. The view endpoint returns lesson instances enriched with their parent template's static fields (group_id, subject_id, teacher_id, room, lesson_number, start_time, end_time).

Two approaches:
- **A (simpler):** Fetch lessons by group+date, then batch-fetch schedule items by IDs. Two queries, assembled in service layer.
- **B (single query):** JPQL JOIN FETCH or `@Query` with JOIN.

Use **approach A** — simpler, no JPQL complexity, fits the project pattern where repositories use derived query methods.

The `LessonRepository` needs a new finder:
```java
// New method needed in LessonRepository
List<Lesson> findByScheduleItemIdInAndDateBetweenAndStatusIn(
        List<Long> scheduleItemIds, LocalDate from, LocalDate to, List<LessonStatus> statuses);
```

And `ScheduleItemRepository` needs a group-based finder:
```java
// New method needed in ScheduleItemRepository
List<ScheduleItem> findByGroupId(Long groupId);
```

Alternatively, use the existing `findByGroupIdAndSemesterIdAndIsActiveTrue` (returns only active templates). For VIEW, we need all schedule items that have lessons in the range — including deactivated ones (their lessons still exist). So the finder should be `findByGroupId(Long groupId)` without `isActive` filter.

### Pattern 6: HATEOAS Assembler

Follow the academic-service `SubjectAssembler` pattern. Each response class extends `RepresentationModel<T>`. Assembler adds self link and action links:

```java
// ScheduleItemAssembler
@Component
public class ScheduleItemAssembler {
    public EntityModel<ScheduleItemResponse> toModel(ScheduleItem item) {
        ScheduleItemResponse response = toResponse(item);
        return EntityModel.of(response,
                linkTo(methodOn(ScheduleItemController.class).getScheduleItem(item.getId())).withSelfRel(),
                linkTo(methodOn(ScheduleItemController.class).listScheduleItems(item.getGroupId(), item.getSemesterId(), null, null)).withRel("collection"));
    }
}
```

### Pattern 7: Mass-Cancel Implementation

Mass-cancel operates on a group's lesson set within a date range. Needs cross-table lookup: schedule_items for group -> lesson IDs -> filter planned -> update to cancelled.

```java
// In LessonService.massCancelLessons
public int massCancelLessons(MassCancelRequest request) {
    requireHeadmanForGroup(request.groupId());
    List<ScheduleItem> items = scheduleItemRepository.findByGroupId(request.groupId());
    List<Long> itemIds = items.stream().map(ScheduleItem::getId).toList();
    if (itemIds.isEmpty()) return 0;

    List<Lesson> toCancel = lessonRepository
            .findByScheduleItemIdInAndDateBetweenAndStatusIn(
                    itemIds, request.dateFrom(), request.dateTo(),
                    List.of(LessonStatus.PLANNED));
    toCancel.forEach(l -> {
        l.setStatus(LessonStatus.CANCELLED);
        l.setCancelReason(request.reason());
    });
    lessonRepository.saveAll(toCancel);
    return toCancel.size();
}
```

### Anti-Patterns to Avoid

- **Calling `isHeadman` gRPC on every request for ADMIN users:** Check `role == ADMIN` first; skip the gRPC call entirely for admins (D-08).
- **Making REST controller logic decide lesson state transitions:** All state-machine logic stays in the service layer. Controller just calls service and returns response.
- **Enriching LessonResponse with names from Academic Service:** D-16 explicitly forbids this. Only IDs in response.
- **Using `@Enumerated(EnumType.ORDINAL)`:** CLAUDE.md forbids. All enums stored as lowercase strings via `LowercaseEnumConverter` / `autoApply=true` converters.
- **Adding FK constraints in SQL for cross-db IDs:** `group_id`, `subject_id`, `teacher_id`, `semester_id` in `schedule_items` are logical FKs — no SQL `REFERENCES` clause. Integrity is enforced at application level via gRPC.
- **Lombok in contract modules:** CLAUDE.md forbids Lombok in `*-api-contract`. Request DTOs are Java `record`, response DTOs are plain classes with manual constructors/getters.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| gRPC channel lifecycle | Manual `ManagedChannel` | `grpc-client-spring-boot-starter` @GrpcClient | Handles shutdown, reconnect, config |
| Pagination response | Manual page wrapping | `PagedResourcesAssembler` + `PagedModel` | Already in Spring HATEOAS; matches academic-service |
| Request validation | Manual null checks | `@Valid` + `@NotNull` on request records | Spring Validation + GlobalExceptionHandler already handles 400 |
| Error mapping | try/catch in controllers | `GlobalExceptionHandler` `@ExceptionHandler` | Already wired in Phase 10; add new exception types only |
| gRPC deadline enforcement | Per-call timeout logic | `.withDeadlineAfter(3, SECONDS)` on blocking stub | One-liner; stub method is immutable/thread-safe |

**Key insight:** The Phase 10 foundation eliminated all infrastructure boilerplate. Phase 11 is purely domain logic on top of that foundation.

---

## Common Pitfalls

### Pitfall 1: HEADMAN Role Not in UserRole Enum

**What goes wrong:** Developer tries to add `HEADMAN` to the `UserRole` enum or use `@RequireRole({UserRole.HEADMAN})`. This fails because headman is a flag on `STUDENT`, not a distinct role — and adding it breaks the auth-service contract.

**Why it happens:** The authorization model is two-dimensional: role + is_headman flag.

**How to avoid:** Always use `@RequireRole({UserRole.ADMIN, UserRole.STUDENT})` for headman-accessible endpoints. Inside the service, check `requestContext.isHeadman()` for the headman-specific authorization path. The gRPC `isHeadman` call is for group ownership verification, not basic authorization.

**Warning signs:** Any test that tries to set `X-User-Role: HEADMAN` header will get a 403 or an enum parse error.

### Pitfall 2: gRPC Client Config Missing in application-test.yml Causes Context Load Failure

**What goes wrong:** Adding `grpc-client-spring-boot-starter` to `build.gradle.kts` causes Spring context to fail loading in tests because the autoconfiguration tries to resolve the `academic-service` channel address.

**Why it happens:** `grpc-client-spring-boot-starter` autoconfigures on startup. The channel address must be defined even if `@MockitoBean AcademicGrpcClient` replaces the bean — because the stub bean is created before `@MockitoBean` replacement in some configurations.

**How to avoid:** Add `grpc.client.academic-service.address` and `grpc.client.academic-service.negotiation-type` to `src/test/resources/application-test.yml`. The `@MockitoBean AcademicGrpcClient` then ensures the real stub is never called.

**Warning signs:** `IllegalStateException: No name resolvers found for 'static'` or `BeanCreationException` for `AcademicGrpcServiceBlockingStub` in test startup logs.

### Pitfall 3: Schedule View Query Returns Wrong Scope

**What goes wrong:** `GET /schedule/groups/{groupId}/lessons` filters ScheduleItems by `isActive=true`, which excludes deactivated templates. But the lessons generated from now-deactivated templates still exist and should appear in the view (the headman may want to see cancelled lessons from a deactivated template).

**Why it happens:** `ScheduleItemRepository.findByGroupIdAndSemesterIdAndIsActiveTrue` is convenient but over-filters.

**How to avoid:** The view endpoint must query ScheduleItems by `groupId` only (no `isActive` filter), then query Lessons by those itemIds + date range + optional status filter. The template's `isActive` flag controls whether new generation happens (Phase 12), not whether historical lessons are visible.

**Warning signs:** Lessons from soft-deleted templates disappear from the schedule view after D-07 is applied.

### Pitfall 4: Mass-Cancel Silently Operates on Wrong Group

**What goes wrong:** `MassCancelRequest` contains `group_id`, but the service fetches ScheduleItems by that `group_id` without first verifying the caller is headman OF THAT GROUP. A headman of Group A could cancel Group B's lessons.

**Why it happens:** `requireHeadmanForGroup(request.groupId())` must be called before fetching items, not after.

**Why it happens:** The gRPC `IsHeadman` check is easy to forget in a batch operation.

**How to avoid:** `requireHeadmanForGroup(request.groupId())` must be the FIRST line in `massCancelLessons` service method, before any repository access.

### Pitfall 5: LessonResponse Missing ScheduleItem Fields

**What goes wrong:** `LessonResponse` only maps Lesson entity fields (id, date, status, isGeoBlocked, cancelReason). But VIEW-02 requires room, teacher_id, subject_id, lesson_number, start_time, end_time — which are all on `ScheduleItem`, not `Lesson`.

**Why it happens:** `Lesson.scheduleItemId` is a Long FK, not a `@ManyToOne`. The assembler must explicitly fetch the parent ScheduleItem to build a complete response.

**How to avoid:** Design `LessonResponse` to include all fields from both tables. In `LessonAssembler.toModel(Lesson lesson, ScheduleItem item)` — pass both objects to the assembler method. The service is responsible for pairing them.

### Pitfall 6: Unique Constraint Violation on Template Update

**What goes wrong:** `PUT /schedule/items/{id}` allows changing `day_of_week`, `lesson_number`, `week_type`. If the new slot is already occupied by another template for the same group/semester, the update violates the UNIQUE constraint `(group_id, day_of_week, lesson_number, week_type, semester_id)`.

**Why it happens:** JPA `save()` throws `DataIntegrityViolationException` on constraint violation, which the current `GlobalExceptionHandler` maps to HTTP 500.

**How to avoid:** Add a `@ExceptionHandler(DataIntegrityViolationException.class)` to `GlobalExceptionHandler` that returns HTTP 409 Conflict with a meaningful message. Alternatively, check for existing template in the slot before updating and throw a domain exception.

### Pitfall 7: Restoring a Non-Cancelled Lesson Returns Wrong Error Code

**What goes wrong:** `PATCH /schedule/lessons/{id}/restore` called on a `planned` or `active` lesson should return 422 (Unprocessable Entity) or 409 (Conflict), not 500.

**Why it happens:** `InvalidLessonStateException` must be mapped in `GlobalExceptionHandler`.

**How to avoid:** Create `InvalidLessonStateException` extending `RuntimeException`, add `@ExceptionHandler` mapping it to HTTP 422.

---

## Code Examples

### gRPC Client Stub Injection (verified from academic-app build.gradle.kts + grpc-spring-boot-starter docs)

```java
// Source: net.devh grpc-client-spring-boot-starter — @GrpcClient annotation
@Component
public class AcademicGrpcClient {

    @GrpcClient("academic-service")  // matches grpc.client.academic-service in yml
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;
    // stub is injected by the starter via AOP field injection
}
```

### View Endpoint Query Strategy (Two-Query Pattern)

```java
// LessonService.getLessonsForGroup
@Transactional(readOnly = true)
public Page<LessonWithItem> getLessonsForGroup(Long groupId, LocalDate from, LocalDate to,
                                                List<LessonStatus> statuses, Pageable pageable) {
    // Step 1: all schedule items for group (no isActive filter — historical lessons visible)
    List<ScheduleItem> items = scheduleItemRepository.findByGroupId(groupId);
    List<Long> itemIds = items.stream().map(ScheduleItem::getId).toList();
    if (itemIds.isEmpty()) return Page.empty(pageable);

    // Step 2: lessons in date range with status filter
    List<Lesson> lessons = lessonRepository
            .findByScheduleItemIdInAndDateBetweenAndStatusIn(itemIds, from, to, statuses);

    // Step 3: build lookup map for assembler
    Map<Long, ScheduleItem> itemMap = items.stream()
            .collect(Collectors.toMap(ScheduleItem::getId, si -> si));

    // Return paired — assembler uses both objects to build LessonResponse
}
```

### Status Filter Default (no status param = exclude cancelled)

```java
// In LessonController / LessonApi
@GetMapping("/groups/{groupId}/lessons")
ResponseEntity<PagedModel<EntityModel<LessonResponse>>> getLessons(
        @PathVariable Long groupId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) List<LessonStatus> status,  // null = default
        Pageable pageable,
        PagedResourcesAssembler<LessonWithItem> assembler);

// In service: if status == null, default to List.of(PLANNED, ACTIVE, CLOSED)
List<LessonStatus> effectiveStatuses = (status == null || status.isEmpty())
        ? List.of(LessonStatus.PLANNED, LessonStatus.ACTIVE, LessonStatus.CLOSED)
        : status;
```

### Mass-Cancel Response

```java
// MassCancelResponse DTO (in contract)
public record MassCancelResponse(int cancelledCount) {}

// Endpoint returns 200 OK with count
ResponseEntity<MassCancelResponse> massCancelLessons(@Valid @RequestBody MassCancelRequest request);
```

### New Exception Types Needed

```java
// schedule-app exceptions (not in contract):
public class InvalidLessonStateException extends RuntimeException { ... }     // -> 422
public class AcademicServiceUnavailableException extends RuntimeException { ... } // -> 503

// In GlobalExceptionHandler — add:
@ExceptionHandler(InvalidLessonStateException.class)
// -> HTTP 422 with "https://api.rutcampustrack.ru/problems/invalid-lesson-state"

@ExceptionHandler(AcademicServiceUnavailableException.class)
// -> HTTP 503 with "https://api.rutcampustrack.ru/problems/service-unavailable"

@ExceptionHandler(DataIntegrityViolationException.class)
// -> HTTP 409 with "https://api.rutcampustrack.ru/problems/conflict"

@ExceptionHandler(MethodArgumentNotValidException.class)
// -> HTTP 400 with fieldErrors populated from BindingResult
```

Note: `ResourceNotFoundException` is already in `schedule.contract.exception` package — verify its exact location before creating a duplicate.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@MockBean` (deprecated) | `@MockitoBean` | Spring Boot 3.4 | Use `@MockitoBean` — already in AbstractScheduleIntegrationTest |
| `PagedResourcesAssembler<T>` as controller param | Same — still standard | — | Inject via `@Autowired` in controller field or via method param |
| gRPC `ManagedChannelBuilder` manual | `@GrpcClient` annotation | grpc-spring-boot-starter | No manual channel setup needed |

**Important for test assertions:** `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`) is the Spring Boot 3.4+ replacement for deprecated `@MockBean`.

---

## Open Questions

1. **`ResourceNotFoundException` location**
   - What we know: `SubjectService` in academic-app imports `ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException`. The schedule-contract does not currently contain this class.
   - What's unclear: Does `schedule-api-contract` already have `ResourceNotFoundException`, or does it need to be added alongside `ErrorResponse`?
   - Recommendation: Check `schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/exception/` — if absent, create it there (parallel to `ErrorResponse`).

2. **ScheduleItem Assembler — method signature with joined data**
   - What we know: `LessonResponse` needs data from both `Lesson` (status, date, isGeoBlocked, cancelReason) and `ScheduleItem` (groupId, subjectId, teacherId, room, lessonNumber, startTime, endTime, weekType).
   - What's unclear: Whether to create a `LessonWithItem` intermediate record in the app layer or pass both objects to the assembler as separate params.
   - Recommendation: Create a `LessonWithItem` record in the `lesson/` package (app-internal, not in contract) pairing `Lesson` + `ScheduleItem`. Pass it to `LessonAssembler.toModel(LessonWithItem)`.

3. **Pagination for VIEW endpoint — max page size**
   - What we know: D-17 mandates `PagedModel`, D-16 says IDs only.
   - What's unclear: Default page size and max page size to configure.
   - Recommendation: Default `size=50`, max `size=200` (one full semester is ~16 weeks × ~5 days × ~8 lessons = ~640 lessons; 200 covers most week-range queries). Configure via Spring Data's `spring.data.web.pageable.max-page-size=200`.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|---------|
| PostgreSQL (Testcontainers) | Integration tests | ✓ | 16 (container) | — |
| Java 21 | Build | ✓ | ms-21.0.9 (from CLAUDE.md) | — |
| Academic Service (gRPC port 19091) | AcademicGrpcClient runtime | Runtime only | — | `@MockitoBean` in tests |
| `net.devh:grpc-client-spring-boot-starter` | AcademicGrpcClient | Not yet in build.gradle.kts | 3.1.0.RELEASE | Add to dependencies |
| Protobuf Gradle plugin `0.9.4` | Proto compilation | Not yet in schedule-app | 0.9.4 | Add to plugins |

**Missing dependencies with no fallback:**
- `grpc-client-spring-boot-starter` + protobuf plugin — must be added to `schedule-app/build.gradle.kts` in Wave 0.

**Missing dependencies with fallback:**
- Academic Service gRPC at runtime — `@MockitoBean AcademicGrpcClient` in all integration tests (D-04).

---

## Validation Architecture

`workflow.nyquist_validation` is absent from `.planning/config.json` — treated as enabled.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers + MockMvc |
| Config file | `src/test/resources/application-test.yml` (exists) |
| Quick run command | `./gradlew :services:schedule-service:schedule-app:test --tests "*.integration.*"` |
| Full suite command | `./gradlew :services:schedule-service:schedule-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TMPL-01 | POST /schedule/items creates template with gRPC validation | integration | `--tests "*.ScheduleItemApiTest.createTemplate*"` | ❌ Wave 0 |
| TMPL-02 | PUT /schedule/items/{id} updates immutable fields rejected | integration | `--tests "*.ScheduleItemApiTest.updateTemplate*"` | ❌ Wave 0 |
| TMPL-03 | DELETE /schedule/items/{id} sets is_active=false | integration | `--tests "*.ScheduleItemApiTest.deleteTemplate*"` | ❌ Wave 0 |
| TMPL-04 | GET /schedule/items?groupId&semesterId returns active templates | integration | `--tests "*.ScheduleItemApiTest.listTemplates*"` | ❌ Wave 0 |
| TMPL-05 | gRPC failure -> 503; inactive group -> 400 | integration | `--tests "*.ScheduleItemApiTest.grpcValidation*"` | ❌ Wave 0 |
| LSSN-04 | PATCH /schedule/lessons/{id}/cancel cancels planned lesson | integration | `--tests "*.LessonApiTest.cancelLesson*"` | ❌ Wave 0 |
| LSSN-05 | PATCH /schedule/lessons/{id}/restore only from cancelled | integration | `--tests "*.LessonApiTest.restoreLesson*"` | ❌ Wave 0 |
| LSSN-06 | POST /schedule/lessons/mass-cancel cancels range | integration | `--tests "*.LessonApiTest.massCancelLessons*"` | ❌ Wave 0 |
| LSSN-07 | PATCH /schedule/lessons/{id}/geo-block toggles flag | integration | `--tests "*.LessonApiTest.toggleGeoBlock*"` | ❌ Wave 0 |
| VIEW-01 | GET /schedule/groups/{id}/lessons accessible to STUDENT | integration | `--tests "*.ScheduleViewTest.anyRoleCanView*"` | ❌ Wave 0 |
| VIEW-02 | LessonResponse includes all required fields | integration | `--tests "*.ScheduleViewTest.responseContainsAllFields*"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :services:schedule-service:schedule-app:test --tests "*.integration.*" -x javadoc`
- **Per wave merge:** `./gradlew :services:schedule-service:schedule-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiTest.java` — covers TMPL-01..05
- [ ] `src/test/java/ru/rutcampustrack/schedule/integration/LessonApiTest.java` — covers LSSN-04..07
- [ ] `src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewTest.java` — covers VIEW-01..02
- [ ] `build.gradle.kts` — add `grpc-client-spring-boot-starter` + protobuf plugin

---

## Project Constraints (from CLAUDE.md)

The following are mandatory directives that constrain all implementation choices:

- **Contract-first:** API interface in `schedule-api-contract`; controller `implements` interface; HTTP mappings ONLY in interface
- **No Lombok in contract modules:** `schedule-api-contract` must use plain Java. Request DTOs = `record`. Response DTOs = class with manual constructors.
- **Enum handling:** Java `UPPER_CASE`; PostgreSQL `lowercase`; converter via `LowercaseEnumConverter` with `autoApply=true`. NEVER `@Enumerated(EnumType.ORDINAL)`.
- **HATEOAS Level 3:** `EntityModel<T>`, `PagedModel<EntityModel<T>>`, `_links` in all responses.
- **RFC 7807 Errors:** `ErrorResponse` record format. Controller throws, `GlobalExceptionHandler` maps.
- **`ddl-auto: validate`:** Hibernate validates only. New columns require Flyway migration first.
- **Soft delete:** Templates use `is_active = false`. No physical DELETE for templates.
- **BIGSERIAL PKs:** All IDs are `Long`.
- **TIMESTAMPTZ:** All timestamps use `OffsetDateTime` in Java.
- **PUT = full update, PATCH = partial:** Template update → PUT with `UpdateScheduleItemRequest`. Lesson state operations (cancel, restore, geo-block) → PATCH.
- **Package naming:** `ru.rutcampustrack.schedule.{module}`
- **REST paths:** `/schedule/...` prefix (Gateway strips `/api/` prefix per application.yml StripPrefix=1 filter)
- **No @ManyToOne associations:** Cross-db FKs stored as Long IDs only.

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection: `services/academic-service/academic-app/build.gradle.kts` — confirmed protobuf plugin `0.9.4`, grpc-java `1.63.0`, `grpc-server-spring-boot-starter:3.1.0.RELEASE`
- Direct code inspection: `proto/academic.proto` — confirmed RPC signatures for `GetGroup`, `GetActiveSemester`, `IsHeadman`
- Direct code inspection: `services/schedule-service/schedule-app/src/main/java/**` — full inventory of Phase 10 artifacts
- Direct code inspection: `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectController.java` — contract-first pattern
- Direct code inspection: `services/api-gateway/src/main/resources/application.yml` — confirmed StripPrefix=1 and `schedule-service:9092` routing
- Direct code inspection: `11-CONTEXT.md` — all locked decisions D-01 through D-19

### Secondary (MEDIUM confidence)
- `net.devh:grpc-client-spring-boot-starter` `@GrpcClient` injection pattern — verified from `academic-app` using `grpc-client-spring-boot-starter:3.1.0.RELEASE` in testImplementation scope; the same artifact is used as `implementation` in production code in schedule-app

### Tertiary (LOW confidence)
- None — all critical findings verified from project source code directly

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — exact versions read from existing build.gradle.kts in academic-app
- Architecture: HIGH — all patterns read from existing schedule-service and academic-service source code
- Pitfalls: HIGH — derived from direct inspection of existing code structure and CLAUDE.md constraints

**Research date:** 2026-04-01
**Valid until:** 2026-05-01 (stable stack, 30-day window)

# Architecture Patterns: Academic Service Integration

**Domain:** Academic Service — university structure CRUD, gRPC server, Redis cache, RabbitMQ publisher
**Researched:** 2026-03-30
**Confidence:** HIGH — based on existing codebase, verified proto contracts, migration files, and build configs

---

## Recommended Architecture

Academic Service follows the established contract-first pattern already proven in Auth Service (Phase 1). The service has two modules:

- `academic-api-contract` — pure `java-library`, no Spring Boot, no Lombok. Contains REST interfaces, DTOs, enums.
- `academic-app` — Spring Boot application. Contains JPA entities, services, gRPC implementation, cache config, event publishers.

```
Client → API Gateway (JWT validated, headers injected)
             ↓ HTTP REST
    Academic Service :9091
    ├── REST layer (controller implements contract interface)
    ├── Service layer (business logic + authorization by header)
    ├── JPA layer (PostgreSQL academic_db)
    ├── Redis cache layer (@Cacheable + manual eviction)
    ├── gRPC server (implements AcademicGrpcService from proto)
    └── RabbitMQ publisher (fanout exchange rut-uit.events)

Schedule Service, Attendance Service, Notification Bot
    → gRPC client → Academic Service :9091 (internal network)
```

---

## Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `academic-api-contract` | REST interface definitions, request/response DTOs, enums | Nothing (pure library) |
| REST controllers | Route HTTP requests, read headers, delegate to service | Service layer |
| Service layer | Business logic, authorization checks, cache coordination | JPA repositories, Redis, RabbitMQ publisher |
| JPA repositories | PostgreSQL CRUD, custom queries | PostgreSQL academic_db |
| gRPC server impl | Implement AcademicGrpcService proto, serve internal callers | Service layer (reuses same services) |
| Redis cache config | `@EnableCaching`, key strategy, TTL setup | Spring Cache abstraction |
| RabbitMQ publisher | Build and publish domain events | RabbitMQ exchange `rut-uit.events` |

---

## Integration Points: New vs Existing

### Existing (no changes needed)

| Component | Status | Notes |
|-----------|--------|-------|
| API Gateway routing `/api/academic/**` → `academic-service:9091` | Already configured in `application.yml` | No changes |
| Gateway header injection `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` | Already implemented in Phase 1 | Read-only from Academic's perspective |
| `academic_db` schema (V1__baseline.sql) | Already exists, all 12 tables created | Academic Service validates via `ddl-auto: validate` |
| V2__seed_test_data.sql | Already present with admin, teacher, student | Auth Service reads these users |
| `LowercaseEnumConverter`, `EnumConverters` | Already in `academic-app/config/` | Add `AssistantPermission` converter |
| `academic-api-contract` enums | `UserRole`, `AccountStatus`, `SubjectType`, `AssistantPermission` — all present | No new enums needed |
| Docker Compose `academic-service` service | Defined, depends on `postgres-academic` and `redis` | Add RabbitMQ dependency |
| `application.yml` datasource, redis, rabbitmq | All configured | Uncomment/add gRPC port config |

### New (to be added in Phase 2)

| Component | What | Where |
|-----------|------|-------|
| gRPC server | `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE` — commented out in `build.gradle.kts` | Uncomment + add gRPC port (default 9111) |
| gRPC service impl | `AcademicGrpcServiceImpl implements AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase` | `academic-app/grpc/` package |
| Redis `@EnableCaching` | Spring Cache abstraction with TTL config per key | `academic-app/config/CacheConfig.java` |
| RabbitMQ config | Declare exchange `rut-uit.events` (fanout), no queue needed for publisher | `academic-app/config/RabbitMqConfig.java` |
| REST API interfaces | `UserApi`, `GroupApi`, `SemesterApi`, `SubjectApi`, `AssignmentApi`, `HomeworkApi`, `ThresholdApi` | `academic-api-contract` |
| DTOs (request records + response classes) | One record + one HATEOAS class per entity | `academic-api-contract` |
| Assemblers | `UserModelAssembler`, etc. — convert entity to EntityModel | `academic-app/*/assembler/` |
| JPA entities | `User`, `Group`, `Semester`, `Subject`, `TeacherSubjectGroup`, `HeadmanAssistant`, `Homework`, `HomeworkCompletion`, `CampusSetting`, `AttendanceThreshold`, `StudentGroupHistory` | `academic-app/*/entity/` |
| Login sequence generator | DB sequence or `MAX(id)+1` padded to 5 digits | `academic-app/user/service/LoginGenerator.java` |
| `AssistantPermissionConverter` | JPA converter for `VARCHAR(64)[]` — uses `String[]` or `List<String>` | `academic-app/config/EnumConverters.java` |

---

## Data Flow

### REST Request (Header-based Authorization)

```
1. Request arrives: GET /api/academic/groups/{id}/members
   Headers: X-User-Role: TEACHER, X-User-Id: 89
2. Controller reads headers via @RequestHeader
3. Controller passes userId + role to service
4. Service checks: TEACHER may call GetGroupMembers for their own subjects only
   → service queries teacher_subject_groups to verify teacher teaches this group
5. Service calls repository → PostgreSQL → may hit @Cacheable
6. Cache hit: return from Redis (group:{id}:members)
7. Cache miss: query PostgreSQL, store in Redis, return
8. Assembler builds EntityModel<GroupMemberResponse> with _links
9. Controller returns ResponseEntity<EntityModel<...>>
```

### gRPC Request (internal services)

```
1. Schedule Service calls AcademicGrpcService.GetTeacherSubjects(teacher_id, semester_id)
2. AcademicGrpcServiceImpl receives proto message
3. Delegates to same TeacherSubjectService used by REST layer
4. Service checks Redis cache: teacher:{teacher_id}:subjects → hit → return proto response
5. Cache miss → PostgreSQL query → populate cache → return
```

### RabbitMQ Event Publication

```
1. GroupService.transferStudent(...) completes successfully
2. Service calls EventPublisher.publishGroupUpdated(groupId, affectedStudentId)
3. Publisher builds EventEnvelope { event_type, event_id (UUID), occurred_at, payload }
4. RabbitTemplate.convertAndSend("rut-uit.events", "", envelope)
   (fanout exchange ignores routing key — "" is conventional)
5. Both notification-web.events and notification-bot.events queues receive copy
```

---

## Method-Level Authorization Pattern

Gateway injects headers; Academic Service enforces role-based access using a consistent pattern.

### Header Extraction

Controllers extract identity from headers, not from Spring Security context (no JWT here):

```java
// In controller method signature:
@GetMapping("/groups/{id}/members")
public ResponseEntity<PagedModel<EntityModel<GroupMemberResponse>>> getMembers(
    @PathVariable Long id,
    @RequestHeader("X-User-Id") Long userId,
    @RequestHeader("X-User-Role") String role,
    @RequestHeader(value = "X-Group-Id", required = false) Long groupId,
    @RequestHeader(value = "X-Is-Headman", required = false, defaultValue = "false") boolean isHeadman,
    Pageable pageable
) { ... }
```

### Authorization Rules by Endpoint Category

| Category | Who Can Call | Authorization Check |
|----------|-------------|-------------------|
| `POST /academic/users` | ADMIN only | `if (!role.equals("ADMIN")) throw ForbiddenException` |
| `GET /academic/groups/{id}/members` | ADMIN, TEACHER, HEADMAN | TEACHER: verify `teacher_subject_groups` contains (teacher_id, group_id). HEADMAN: verify `X-Group-Id == id` |
| `POST /academic/subjects` | HEADMAN only | Verify `X-Is-Headman == true`, then verify group ownership |
| `GET /academic/users/me` | STUDENT (own profile) | `userId` from header, no role check needed |
| `POST /academic/homeworks` | HEADMAN or ASSISTANT with `MANAGE_HOMEWORK` | Check `headman_assistants.permissions` contains `manage_homework` |
| gRPC endpoints | Internal services only (no Gateway) | No auth — gRPC runs on internal Docker network, port not exposed |

### Authorization Strategy: Service Layer, Not Filter

Do NOT implement a global security filter for role checks. Each service method enforces its own rules. This keeps authorization logic close to the business logic and testable independently.

```java
// In GroupService:
public Page<GroupMemberResponse> getMembers(Long groupId, Long requesterId, String role) {
    if ("TEACHER".equals(role)) {
        boolean teaches = teacherSubjectGroupRepo
            .existsByTeacherIdAndGroupId(requesterId, groupId);
        if (!teaches) throw new ForbiddenException("Teacher does not teach this group");
    }
    // ADMIN and HEADMAN pass through
    return groupMemberRepo.findByGroupId(groupId, pageable);
}
```

### HEADMAN + ASSISTANT Permission Check

Assistants are students in `headman_assistants` with a `permissions VARCHAR(64)[]` column. Check pattern:

```java
// In AssistantPermissionChecker (shared service component):
public boolean hasPermission(Long studentId, Long groupId, AssistantPermission required) {
    return headmanAssistantRepo
        .findActiveByStudentIdAndGroupId(studentId, groupId)
        .map(a -> Arrays.asList(a.getPermissions()).contains(required.name().toLowerCase()))
        .orElse(false);
}
```

---

## Package Structure

```
services/academic-service/
├── academic-api-contract/
│   └── src/main/java/ru/rutcampustrack/academic/contract/
│       ├── enums/
│       │   ├── UserRole.java            ← already exists
│       │   ├── AccountStatus.java       ← already exists
│       │   ├── SubjectType.java         ← already exists
│       │   └── AssistantPermission.java ← already exists
│       ├── exception/
│       │   ├── ErrorResponse.java       ← already exists
│       │   └── ResourceNotFoundException.java ← already exists
│       ├── api/
│       │   ├── UserApi.java             ← NEW (interface with @RequestMapping)
│       │   ├── GroupApi.java            ← NEW
│       │   ├── SemesterApi.java         ← NEW
│       │   ├── SubjectApi.java          ← NEW
│       │   ├── AssignmentApi.java       ← NEW (teacher-subject-group)
│       │   ├── HomeworkApi.java         ← NEW
│       │   └── ThresholdApi.java        ← NEW
│       └── dto/
│           ├── user/
│           │   ├── CreateUserRequest.java     ← record
│           │   ├── UpdateUserRequest.java     ← record (PUT, full)
│           │   ├── PatchUserRequest.java      ← record (PATCH, partial)
│           │   └── UserResponse.java          ← class extends RepresentationModel
│           ├── group/
│           │   ├── CreateGroupRequest.java
│           │   ├── UpdateGroupRequest.java
│           │   └── GroupResponse.java
│           ├── semester/
│           │   ├── CreateSemesterRequest.java
│           │   ├── DeleteSemesterRequest.java ← record with confirmationPhrase
│           │   └── SemesterResponse.java
│           ├── subject/
│           │   ├── CreateSubjectRequest.java
│           │   ├── UpdateSubjectRequest.java
│           │   └── SubjectResponse.java
│           ├── assignment/
│           │   ├── CreateAssignmentRequest.java
│           │   └── AssignmentResponse.java
│           ├── homework/
│           │   ├── CreateHomeworkRequest.java
│           │   ├── UpdateHomeworkRequest.java
│           │   └── HomeworkResponse.java
│           └── threshold/
│               ├── SetThresholdRequest.java
│               └── ThresholdResponse.java

├── academic-app/
│   └── src/main/java/ru/rutcampustrack/academic/
│       ├── AcademicApplication.java
│       ├── config/
│       │   ├── LowercaseEnumConverter.java    ← already exists
│       │   ├── EnumConverters.java            ← exists, ADD AssistantPermissionConverter
│       │   ├── CacheConfig.java               ← NEW (@EnableCaching, TTL per cache)
│       │   └── RabbitMqConfig.java            ← NEW (declare exchange)
│       ├── user/
│       │   ├── entity/User.java               ← NEW (@Entity, @Table("users"))
│       │   ├── entity/StudentGroupHistory.java
│       │   ├── repository/UserRepository.java
│       │   ├── service/UserService.java
│       │   ├── service/LoginGenerator.java    ← NEW (generates student00001, teacher00001)
│       │   ├── assembler/UserModelAssembler.java
│       │   └── controller/UserController.java ← implements UserApi
│       ├── group/
│       │   ├── entity/Group.java
│       │   ├── repository/GroupRepository.java
│       │   ├── service/GroupService.java
│       │   ├── assembler/GroupModelAssembler.java
│       │   └── controller/GroupController.java
│       ├── semester/
│       │   ├── entity/Semester.java
│       │   ├── repository/SemesterRepository.java
│       │   ├── service/SemesterService.java
│       │   ├── assembler/SemesterModelAssembler.java
│       │   └── controller/SemesterController.java
│       ├── subject/
│       │   ├── entity/Subject.java
│       │   ├── repository/SubjectRepository.java
│       │   ├── service/SubjectService.java
│       │   ├── assembler/SubjectModelAssembler.java
│       │   └── controller/SubjectController.java
│       ├── assignment/
│       │   ├── entity/TeacherSubjectGroup.java
│       │   ├── repository/TeacherSubjectGroupRepository.java
│       │   ├── service/AssignmentService.java
│       │   ├── assembler/AssignmentModelAssembler.java
│       │   └── controller/AssignmentController.java
│       ├── assistant/
│       │   ├── entity/HeadmanAssistant.java
│       │   ├── repository/HeadmanAssistantRepository.java
│       │   ├── service/AssistantService.java
│       │   ├── service/AssistantPermissionChecker.java ← shared auth component
│       │   ├── assembler/AssistantModelAssembler.java
│       │   └── controller/AssistantController.java
│       ├── homework/
│       │   ├── entity/Homework.java
│       │   ├── entity/HomeworkCompletion.java
│       │   ├── repository/HomeworkRepository.java
│       │   ├── repository/HomeworkCompletionRepository.java
│       │   ├── service/HomeworkService.java
│       │   ├── assembler/HomeworkModelAssembler.java
│       │   └── controller/HomeworkController.java
│       ├── threshold/
│       │   ├── entity/AttendanceThreshold.java
│       │   ├── repository/AttendanceThresholdRepository.java
│       │   ├── service/ThresholdService.java
│       │   ├── assembler/ThresholdModelAssembler.java
│       │   └── controller/ThresholdController.java
│       ├── campus/
│       │   ├── entity/CampusSetting.java
│       │   └── repository/CampusSettingRepository.java
│       ├── grpc/
│       │   └── AcademicGrpcServiceImpl.java   ← NEW (implements generated ImplBase)
│       └── event/
│           └── AcademicEventPublisher.java    ← NEW (RabbitTemplate wrapper)
```

---

## Entity Build Order

Dependencies flow upward: each entity must be created after all entities it references.

```
Step 1: groups
        — No FK dependencies within academic_db
        — Required by: users, student_group_history, teacher_subject_groups,
          headman_assistants, attendance_thresholds, homeworks

Step 2: users
        — FK: group_id → groups(id)
        — Required by: student_group_history, password_reset_tokens,
          teacher_subject_groups, headman_assistants, attendance_thresholds,
          homeworks (published_by), homework_completions

Step 3: semesters
        — No FK dependencies
        — Required by: teacher_subject_groups, homeworks

Step 4: subjects
        — No FK dependencies
        — Required by: teacher_subject_groups, attendance_thresholds, homeworks

Step 5: student_group_history
        — FK: user_id → users(id), group_id → groups(id)
        — No dependents (historical log only)

Step 6: password_reset_tokens
        — FK: user_id → users(id)
        — No dependents

Step 7: teacher_subject_groups
        — FK: teacher_id → users(id), subject_id → subjects(id),
               group_id → groups(id), semester_id → semesters(id)
        — Required by: authorization checks in service layer

Step 8: headman_assistants
        — FK: group_id → groups(id), student_id → users(id), assigned_by → users(id)
        — No dependents

Step 9: attendance_thresholds
        — FK: group_id → groups(id), subject_id → subjects(id), set_by → users(id)
        — No dependents

Step 10: homeworks
         — FK: group_id, subject_id, semester_id, published_by → users(id)
         — Required by: homework_completions

Step 11: homework_completions
         — FK: homework_id → homeworks(id), student_id → users(id)
         — No dependents
```

### Practical Entity Implementation Order (for phases within milestone)

Build entities in this order to allow compile-time verification and incremental testing:

```
Phase 2.1: Group entity + GroupRepository + GroupService (no deps, enables all later tests)
Phase 2.2: User entity + UserRepository + UserService + LoginGenerator
Phase 2.3: Semester entity + SemesterService (independent, but needed for assignments)
Phase 2.4: Subject entity + SubjectService (independent)
Phase 2.5: TeacherSubjectGroup entity + AssignmentService (needs users + subjects + groups + semesters)
Phase 2.6: HeadmanAssistant entity + AssistantService + AssistantPermissionChecker
Phase 2.7: AttendanceThreshold entity + ThresholdService
Phase 2.8: Homework + HomeworkCompletion entities + HomeworkService
Phase 2.9: gRPC server (AcademicGrpcServiceImpl) — reuses services from 2.1-2.4
Phase 2.10: Redis caching — add @Cacheable/@CacheEvict after services are stable
Phase 2.11: RabbitMQ publishers — add after group/semester/homework services are stable
```

---

## Redis Cache Architecture

### Cache Configuration

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        Map<String, RedisCacheConfiguration> configs = Map.of(
            "groupInfo",        ttl(Duration.ofMinutes(10)),
            "groupMembers",     ttl(Duration.ofMinutes(5)),
            "teacherSubjects",  ttl(Duration.ofMinutes(10)),
            "activeSemester",   ttl(Duration.ofMinutes(30)),
            "campusGeofence",   ttl(Duration.ofHours(1))
        );
        return RedisCacheManager.builder(connectionFactory)
            .withInitialCacheConfigurations(configs)
            .build();
    }

    private RedisCacheConfiguration ttl(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl)
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            );
    }
}
```

### Cache Key Strategy

| Cache Name | Key Pattern | Evict When |
|------------|-------------|-----------|
| `groupInfo` | `group:{id}:info` | Group updated |
| `groupMembers` | `group:{id}:members` | Student transferred, headman changed |
| `teacherSubjects` | `teacher:{id}:subjects` | Assignment created/deleted |
| `activeSemester` | `semester:active` | Semester activated/archived |
| `campusGeofence` | `campus:geofence` | CampusSetting updated |

### gRPC Cache Reuse

gRPC server implementation MUST reuse the same service methods used by REST — it does NOT bypass the cache:

```java
@GrpcService
public class AcademicGrpcServiceImpl extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {

    private final GroupService groupService;
    private final UserService userService;
    // ... inject same services used by REST controllers

    @Override
    public void getGroup(GroupRequest request, StreamObserver<GroupResponse> responseObserver) {
        // groupService.getGroupById() has @Cacheable("groupInfo")
        // so this gRPC call benefits from the same cache
        GroupResponse response = groupService.getGroupForGrpc(request.getGroupId());
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

---

## RabbitMQ Integration Pattern

### Exchange Declaration

```java
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "rut-uit.events";

    @Bean
    public FanoutExchange academicEventsExchange() {
        // durable=true, autoDelete=false
        // Academic Service only publishes — queues are declared by consumers
        return new FanoutExchange(EXCHANGE, true, false);
    }
}
```

### Event Publisher

```java
@Component
public class AcademicEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publishGroupUpdated(Long groupId, Long affectedUserId) {
        publish("group.updated", Map.of(
            "group_id", groupId,
            "affected_user_id", affectedUserId
        ));
    }

    public void publishSemesterArchived(Long semesterId) {
        publish("semester.archived", Map.of("semester_id", semesterId));
    }

    public void publishHomeworkPublished(Long homeworkId, Long groupId, Long subjectId, String title) {
        publish("homework.published", Map.of(
            "homework_id", homeworkId,
            "group_id", groupId,
            "subject_id", subjectId,
            "title", title
        ));
    }

    private void publish(String eventType, Map<String, Object> payload) {
        // Envelope matches event-schemas/*.json structure
        Map<String, Object> envelope = Map.of(
            "event_type", eventType,
            "event_id", UUID.randomUUID().toString(),
            "occurred_at", Instant.now().toString(),
            "payload", payload
        );
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, "", envelope);
    }
}
```

---

## gRPC Server Integration

### Build Configuration Change

In `academic-app/build.gradle.kts`, uncomment the gRPC dependency (already stubbed):

```kotlin
// Uncomment this:
implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")
```

Add protobuf generation to the build (requires root-level proto plugin setup). The proto is at `proto/academic.proto` with `java_package = "ru.rutcampustrack.academic.grpc"`.

### gRPC Port Configuration

Add to `application.yml`:

```yaml
grpc:
  server:
    port: 9111   # default for grpc-spring-boot-starter
```

The gRPC port is NOT exposed through the API Gateway and NOT mapped in `docker-compose.yml` — it is only accessible within the Docker private network. Schedule Service, Attendance Service, and Notification Bot reach it as `academic-service:9111`.

### Proto Code Generation

The `academic.proto` already defines the service. The generated class `AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase` is the base class to extend. All 7 RPCs must be implemented:

| RPC | Backing Service | Cache |
|-----|----------------|-------|
| `GetGroup` | `GroupService.getGroupById` | `@Cacheable("groupInfo")` |
| `GetGroupMembers` | `UserService.getMembersByGroupId` | `@Cacheable("groupMembers")` |
| `GetTeacherSubjects` | `AssignmentService.getTeacherSubjects` | `@Cacheable("teacherSubjects")` |
| `IsHeadman` | `UserService.isHeadman` | No cache (simple flag read) |
| `GetActiveSemester` | `SemesterService.getActiveSemester` | `@Cacheable("activeSemester")` |
| `GetCampusGeofence` | `CampusSettingService.getGeofence` | `@Cacheable("campusGeofence")` |
| `GetUserById` | `UserService.getUserById` | No cache (mutates frequently) |

---

## Patterns to Follow

### Pattern 1: Contract Interface Implementation

Every controller implements its api-contract interface. Mappings are ONLY in the interface, not in the controller class.

```java
// In academic-api-contract:
@RequestMapping("/academic/groups")
public interface GroupApi {
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<GroupResponse>> getById(@PathVariable Long id, ...);
}

// In academic-app:
@RestController
public class GroupController implements GroupApi {
    // No @RequestMapping here — inherited from interface
    @Override
    public ResponseEntity<EntityModel<GroupResponse>> getById(Long id, ...) { ... }
}
```

### Pattern 2: Request as record, Response as RepresentationModel subclass

```java
// api-contract — request record (no Lombok):
public record CreateGroupRequest(
    @NotBlank String name,
    @Size(max = 32) String code
) {}

// api-contract — response class (HATEOAS):
public class GroupResponse extends RepresentationModel<GroupResponse> {
    private Long id;
    private String name;
    private String code;
    private boolean active;
    // getters/setters — no Lombok in contract module
}
```

### Pattern 3: Soft Delete for Users

Users are never deleted. Status transitions:
```
active → expelled    (ADMIN action: student expelled)
active → suspended   (ADMIN action: temporary suspension)
active → archived    (ADMIN action: staff archived)
expelled → active    (ADMIN action: re-enrollment)
```

`DELETE /academic/users/{id}` should not exist. Use `PUT /academic/users/{id}/archive` or `PUT /academic/users/{id}/expel`.

### Pattern 4: Single Active Semester Constraint

The PostgreSQL EXCLUDE constraint already enforces one active semester. The service layer should:
1. Set new semester `is_active=true` → DB constraint fires, rejects if another is active
2. For activation: first deactivate current → then activate new (in one transaction)
3. On `semester.archived` event: evict `activeSemester` cache

### Pattern 5: Login Auto-Generation

Use a dedicated generator that queries the last sequence number:

```java
@Service
public class LoginGenerator {
    private final UserRepository userRepo;

    public String generateLogin(UserRole role) {
        String prefix = switch (role) {
            case STUDENT -> "student";
            case TEACHER -> "teacher";
            case ADMIN -> "admin";
        };
        // Find max numeric suffix for this prefix
        Optional<Integer> maxSeq = userRepo.findMaxLoginSequenceForPrefix(prefix);
        int next = maxSeq.map(n -> n + 1).orElse(1);
        return String.format("%s%05d", prefix, next);
    }
}
```

Reserved logins `student`, `teacher`, `admin` (seeded in V2) must not be overwritten by auto-generation.

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Bypassing Service Layer in gRPC Impl

**What:** Writing duplicate query logic in `AcademicGrpcServiceImpl` instead of reusing existing service methods.

**Why bad:** Cache invalidation applies only to the service layer. gRPC impl bypassing service = stale reads from PostgreSQL while cache is populated, and cache eviction in service does not affect gRPC responses.

**Instead:** Always delegate to the same service methods used by REST controllers.

### Anti-Pattern 2: Fetching authorization context from DB per request

**What:** Calling `teacherSubjectGroupRepo.findByTeacherId()` on every TEACHER request to validate access, without caching.

**Why bad:** teacher_subject_groups is a read-heavy join table. Every API call by a teacher doubles the DB load.

**Instead:** The `@Cacheable("teacherSubjects")` on `AssignmentService.getTeacherSubjects()` covers this. Authorization checks call the cached method first.

### Anti-Pattern 3: Lombok in api-contract module

**What:** Adding `@Data`, `@Builder` to DTOs in `academic-api-contract`.

**Why bad:** Contract modules are pure `java-library`. Adding Lombok requires annotation processing setup that conflicts with the clean library intent and bloats the dependency surface consumed by other modules.

**Instead:** Manual getters/setters for Response classes. Request records are already concise without Lombok.

### Anti-Pattern 4: Publishing events inside @Transactional before commit

**What:** Calling `eventPublisher.publishGroupUpdated(...)` inside a `@Transactional` method before the transaction commits.

**Why bad:** If the transaction rolls back after the event is published, consumers (notification services) act on data that never persisted.

**Instead:** Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` or publish after the transactional method returns successfully.

### Anti-Pattern 5: Duplicate enum converter for AssistantPermission

**What:** The `permissions` column is `VARCHAR(64)[]` (PostgreSQL array). Creating a JPA `@Converter` with `autoApply=true` for `AssistantPermission[]` causes conflicts.

**Why bad:** JPA converters with autoApply on arrays interact badly with Hibernate's type system.

**Instead:** Map `permissions` as `String[]` in the entity. Convert to/from `AssistantPermission` in the service layer manually. Do NOT use `@Enumerated` or a custom converter on the array column.

---

## Scalability Considerations

| Concern | At current scale (dev) | At 1K students | At 10K students |
|---------|----------------------|----------------|-----------------|
| Read throughput | Direct DB queries fine | Redis cache sufficient (5-60 min TTL) | Increase Redis TTL, consider read replicas |
| gRPC calls from other services | Negligible | ~100 concurrent gRPC calls at class start/end | Connection pooling via grpc-spring-boot-starter default pool |
| RabbitMQ event volume | Rare events (admin actions) | Group updates < 100/day | No concern — Academic publishes low-frequency events |
| Semester active constraint | Single-row EXCLUDE — always fast | No change needed | No change needed |

---

## Sources

- Existing codebase: `services/academic-service/academic-app/build.gradle.kts` (gRPC dependency commented out — HIGH confidence)
- Existing codebase: `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql` (12 tables, all FK relationships — HIGH confidence)
- Existing codebase: `proto/academic.proto` (7 RPCs, message definitions — HIGH confidence)
- Existing codebase: `event-schemas/homework.published.json` (envelope structure — HIGH confidence)
- Existing codebase: `docs/architecture.md` (Redis key patterns, service topology — HIGH confidence)
- Existing codebase: `services/academic-service/academic-app/src/main/resources/application.yml` (RabbitMQ config already present — HIGH confidence)
- Existing codebase: `services/auth-service` (header-based authorization pattern — HIGH confidence, validated in Phase 1)

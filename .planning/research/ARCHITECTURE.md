# Architecture Research

**Domain:** Attendance Service MVP — geo-checkin, manual marking, auto-absent, basic reports
**Researched:** 2026-04-04
**Confidence:** HIGH (based on full source inspection of existing codebase)

---

## System Overview

Attendance Service (v4.0) is the first service that consumes from the shared RabbitMQ fanout exchange. It calls both Schedule Service and Academic Service via gRPC. It owns MongoDB `attendance_db`.

```
┌──────────────────────────────────────────────────────────────────────┐
│                     ATTENDANCE SERVICE (port 9093)                    │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  checkin/ domain                                                 │  │
│  │  ┌──────────────────┐   ┌──────────────────────────────────┐   │  │
│  │  │ CheckInController│   │ ManualMarkController              │   │  │
│  │  │ POST /check-in   │   │ POST /manual                     │   │  │
│  │  └────────┬─────────┘   └───────────────┬──────────────────┘   │  │
│  │           │                             │                       │  │
│  │  ┌────────▼─────────────────────────────▼──────────────────┐   │  │
│  │  │ CheckInService                                           │   │  │
│  │  │  - geo validation (Haversine)                           │   │  │
│  │  │  - time window + geo-block check                        │   │  │
│  │  │  - MongoDB upsert (idempotent on lesson_id+user_id)     │   │  │
│  │  │  - publish attendance.marked event                      │   │  │
│  │  └────────┬──────────────────────────────────────────────  ┘   │  │
│  │           │                                                     │  │
│  │  ┌────────▼─────────────────────────────────────────────────┐  │  │
│  │  │ AttendanceRepository (Spring Data MongoDB)               │  │  │
│  │  │ Collection: attendances                                  │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  shared/port/                                                    │  │
│  │  AttendanceReadPort  ◄──── report/ reads ONLY through this      │  │
│  │  (interface)                                                     │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  report/ domain (ISOLATED — no import from checkin.*)           │  │
│  │  ┌──────────────────┐  ┌───────────────────────────────────┐   │  │
│  │  │ JournalController│  │ StudentStatsController            │   │  │
│  │  │ GET /reports/    │  │ GET /reports/student/{id}         │   │  │
│  │  │  group/...       │  │                                   │   │  │
│  │  └────────┬─────────┘  └────────────┬──────────────────── ┘   │  │
│  │           │                         │                          │  │
│  │  ┌────────▼─────────────────────────▼──────────────────────┐  │  │
│  │  │ ReportService                                            │  │  │
│  │  │  - calls AttendanceReadPort                             │  │  │
│  │  │  - calls AcademicGrpcClient (GetGroupMembers)           │  │  │
│  │  │  - calls AcademicGrpcClient (GetTeacherSubjects)        │  │  │
│  │  └──────────────────────────────────────────────────────── ┘  │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  event/ (consumer + publisher)                                  │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │ LessonEventConsumer  @RabbitListener                    │  │  │
│  │  │  - onLessonClosed → trigger auto-absent for lesson      │  │  │
│  │  │  - onLessonCancelled → mark all attendances cancelled   │  │  │
│  │  └──────────────────────────────────────────────────────── ┘  │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │ DomainEventListener  @EventListener                      │  │  │
│  │  │  - publishes attendance.marked to rut-uit.events         │  │  │
│  │  └──────────────────────────────────────────────────────── ┘  │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  grpc/ (client only — no gRPC server in v4.0)                   │  │
│  │  ScheduleGrpcClient  (GetActiveLesson, GetLessonById)           │  │
│  │  AcademicGrpcClient  (GetGroupMembers, GetCampusGeofence,       │  │
│  │                       GetTeacherSubjects)                       │  │
│  └─────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
         │                          │                 ▲
  gRPC (sync)                 MongoDB write       RabbitMQ
         │                     attendance_db       consume
    ┌────┴──────┐         ┌──────────────────┐  lesson.closed
    │ Schedule  │         │ MongoDB           │  lesson.cancelled
    │ :19092    │         │ attendance_db     │
    │           │         └──────────────────┘
    │ Academic  │
    │ :19091    │
    └───────────┘
```

---

## Component Responsibilities

| Component | Responsibility | Implementation |
|-----------|----------------|----------------|
| `checkin/CheckInController` | REST endpoint for student geo-checkin | `implements CheckInApi` from contract |
| `checkin/ManualMarkController` | REST endpoint for headman manual marking | `implements ManualMarkApi` from contract |
| `checkin/CheckInService` | Geo validation, time window, upsert + event publish | `@Service`, uses `ApplicationEventPublisher` |
| `checkin/AutoAbsentService` | Bulk-marks unmarked students absent when lesson closes | `@Service`, called by event consumer |
| `checkin/AttendanceRepository` | MongoDB CRUD on `attendances` collection | `MongoRepository<AttendanceRecord, String>` |
| `report/JournalController` | Group journal by subject | `implements JournalApi` from contract |
| `report/StudentStatsController` | Student attendance stats | `implements StudentStatsApi` from contract |
| `report/ReportService` | Aggregates attendance + enriches with member names | reads via `AttendanceReadPort` |
| `shared/port/AttendanceReadPort` | Interface bridging checkin and report domains | Implemented by `AttendanceReadPortImpl` in `checkin/` |
| `event/LessonEventConsumer` | RabbitMQ consumer for lesson lifecycle events | `@RabbitListener` — first consumer in the system |
| `event/DomainEventListener` | Forwards Spring events to RabbitMQ | `@EventListener` (plain, not transactional — see Anti-Pattern 2) |
| `event/RabbitConfig` | Queue + binding declarations, Jackson converter, RabbitTemplate | `@Configuration` |
| `grpc/ScheduleGrpcClient` | gRPC client to Schedule Service | `@GrpcClient("schedule-service")` |
| `grpc/AcademicGrpcClient` | gRPC client to Academic Service | `@GrpcClient("academic-service")` |
| `config/MongoIndexConfig` | Programmatic index creation on startup | `@EventListener(ApplicationReadyEvent)` |
| `security/` | UserContextFilter, RequestContext, RequireRole, RoleCheckAspect | Copy pattern from schedule-service exactly |

---

## Recommended Project Structure

```
attendance-service/
├── attendance-api-contract/       # java-library — NO Lombok
│   └── src/main/java/ru/rutcampustrack/attendance/contract/
│       ├── api/
│       │   ├── CheckInApi.java           # POST /attendance/check-in
│       │   ├── ManualMarkApi.java        # POST /attendance/manual
│       │   ├── JournalApi.java           # GET /reports/group/...
│       │   └── StudentStatsApi.java      # GET /reports/student/...
│       ├── dto/
│       │   ├── checkin/
│       │   │   ├── GeoCheckinRequest.java    # record {lat, lng}
│       │   │   ├── ManualMarkRequest.java    # record {userId, lessonId, status}
│       │   │   └── AttendanceResponse.java   # class extends RepresentationModel
│       │   └── report/
│       │       ├── JournalResponse.java      # class extends RepresentationModel
│       │       └── StudentStatsResponse.java # class extends RepresentationModel
│       └── enums/
│           ├── AttendanceStatus.java    # already exists
│           ├── AttendanceSource.java    # already exists
│           ├── ExcuseType.java          # already exists
│           └── ExcuseTicketStatus.java  # already exists
│
└── attendance-app/                # Spring Boot app — Lombok allowed
    └── src/main/java/ru/rutcampustrack/attendance/
        ├── AttendanceApplication.java
        ├── checkin/
        │   ├── CheckInController.java
        │   ├── ManualMarkController.java
        │   ├── CheckInService.java
        │   ├── AutoAbsentService.java
        │   ├── AttendanceAssembler.java
        │   ├── model/
        │   │   └── AttendanceRecord.java     # @Document("attendances")
        │   ├── repository/
        │   │   └── AttendanceRepository.java
        │   └── port/
        │       └── AttendanceReadPortImpl.java  # implements shared/port/AttendanceReadPort
        ├── report/
        │   ├── JournalController.java
        │   ├── StudentStatsController.java
        │   └── ReportService.java
        ├── shared/
        │   └── port/
        │       └── AttendanceReadPort.java    # interface only
        ├── event/
        │   ├── RabbitConfig.java             # Queue + binding + converter + template
        │   ├── DomainEvent.java              # abstract, same as schedule-service
        │   ├── AttendanceMarkedEvent.java    # extends DomainEvent
        │   ├── DomainEventListener.java      # @EventListener (plain, not transactional)
        │   ├── LessonEventConsumer.java      # @RabbitListener — NEW, first consumer
        │   └── dto/
        │       ├── LessonClosedPayload.java   # record — deserialize lesson.closed
        │       └── LessonCancelledPayload.java
        ├── grpc/
        │   ├── ScheduleGrpcClient.java
        │   ├── AcademicGrpcClient.java
        │   └── GrpcExceptionAdvice.java      # copy from schedule-service
        ├── config/
        │   └── MongoIndexConfig.java         # ensure indexes exist on startup
        ├── exception/
        │   ├── GlobalExceptionHandler.java
        │   ├── ResourceNotFoundException.java
        │   ├── CheckInNotAllowedException.java
        │   ├── GeofenceViolationException.java
        │   └── ScheduleServiceUnavailableException.java
        └── security/
            ├── UserContextFilter.java
            ├── RequestContext.java
            ├── RequireRole.java
            └── RoleCheckAspect.java
```

### Structure Rationale

- **checkin/ vs report/**: Domain isolation enforced by ArchUnit rules. `report/` queries data only through `shared/port/AttendanceReadPort`. This mirrors the design in `docs/architecture.md` and enables future extraction of Report as its own service by swapping the port implementation.
- **event/dto/**: Payload records for incoming RabbitMQ events live in `event/dto/` not in `checkin/` because the consumer is infrastructure, not domain logic.
- **grpc/**: Both clients in a flat `grpc/` package — same as schedule-service's `grpc/AcademicGrpcClient`.
- **security/**: Verbatim copy of schedule-service security package. Same gateway headers, same AOP pattern.

---

## Data Flow

### Flow 1: Geo-Checkin (Student)

```
Student → POST /attendance/check-in + {lat, lng}
    → API Gateway: JWT → X-User-Id, X-Group-Id, X-User-Role=STUDENT
    → UserContextFilter populates RequestContext
    → @RequireRole(STUDENT) check passes
    → CheckInService:
        1. gRPC → ScheduleGrpcClient.getActiveLesson(groupId, now)
           ← LessonResponse {id, isGeoBlocked, startTime, endTime}
        2. REJECT if isGeoBlocked=true → 409 CheckInNotAllowedException
        3. gRPC → AcademicGrpcClient.getCampusGeofence()
           ← GeofenceResponse {lat, lng, radius_m}
        4. Haversine distance check: student coords vs campus center
           ← REJECT if distance > radius_m → 422 GeofenceViolationException
        5. MongoDB upsert: attendances {lesson_id, user_id, status=present, marked_by=student_geo}
        6. ApplicationEventPublisher.publishEvent(AttendanceMarkedEvent)
           → @EventListener in DomainEventListener
           → RabbitTemplate → rut-uit.events fanout → all bound queues
    ← 200 AttendanceResponse {status=present, distanceM=45}
```

### Flow 2: Manual Mark (Headman)

```
Headman → POST /attendance/manual + {userId, lessonId, status}
    → UserContextFilter: X-User-Role=STUDENT, X-Is-Headman=true
    → @RequireRole(STUDENT) passes, service checks requestContext.isHeadman()
    → CheckInService:
        1. gRPC → ScheduleGrpcClient.getLessonById(lessonId)
           ← Verify lesson.groupId == headman.groupId (ownership)
        2. MongoDB upsert: {lesson_id, userId, status, marked_by=headman}
        3. Publish AttendanceMarkedEvent
    ← 200 AttendanceResponse
```

Note on HEADMAN role: `X-User-Role=STUDENT` always. Headman status is in `X-Is-Headman=true` header, same as schedule-service. `@RequireRole` checks STUDENT; service layer checks `requestContext.isHeadman()`.

### Flow 3: Auto-Absent (lesson.closed event)

```
[Schedule Service cron] → LessonStatusTransitionJob
    → lesson ACTIVE → CLOSED
    → @TransactionalEventListener(AFTER_COMMIT) → RabbitMQ: lesson.closed
        → rut-uit.events fanout → attendance-service.events queue
            → LessonEventConsumer.onEvent():
                event_type = "lesson.closed"
                → AutoAbsentService.markAbsentForLesson(lessonId, groupId)
                    1. gRPC → AcademicGrpcClient.getGroupMembers(groupId)
                       ← List<StudentInfo> with all active students
                    2. MongoDB findAll where lesson_id=lessonId
                       ← List of already-marked user_ids
                    3. Compute: not-marked = all members - already marked
                    4. MongoDB bulk insert absent records (ordered=false)
                    5. No event published for auto-absent
```

Auto-absent does NOT use a Spring transaction because MongoDB in this service has no `MongoTransactionManager` configured. The bulk insert is fire-and-forget with duplicate-key tolerance.

### Flow 4: Report — Group Journal

```
Teacher → GET /reports/group/{groupId}/subject/{subjectId}?semesterId=X
    → API Gateway → X-User-Role=TEACHER
    → ReportService:
        1. gRPC → AcademicGrpcClient.getTeacherSubjects(teacherId, semesterId)
           ← Verify subjectId is in teacher's subjects → 403 if not
        2. gRPC → AcademicGrpcClient.getGroupMembers(groupId)
           ← [{userId, displayName}]
        3. AttendanceReadPort.findByGroupSubjectSemester(groupId, subjectId, semesterId)
           ← List<AttendanceRecord>
        4. In-memory join: userId → displayName + attendance rows
    ← 200 JournalResponse (HATEOAS EntityModel)
```

---

## MongoDB Document Schema

### Collection: `attendances`

```javascript
{
  // MongoDB generated
  _id: ObjectId,

  // Composite unique key — enforced by unique index
  lesson_id:    NumberLong,   // lessons.id from schedule_db
  user_id:      NumberLong,   // users.id from academic_db

  // Denormalized for query performance — populated at write time
  semester_id:  NumberLong,
  group_id:     NumberLong,
  subject_id:   NumberLong,
  teacher_id:   NumberLong,
  lesson_date:  ISODate,      // date of the lesson (for sorting)
  lesson_number: NumberInt,   // 1-8 (for ordering within day)

  // Core attendance data (stored as lowercase strings per project convention)
  status:       String,       // "present" | "absent" | "excused" | "free_attendance"
  marked_by:    String,       // "student_geo" | "headman" | "auto_scheduler"

  // Geo data — populated only when marked_by = "student_geo"
  checkin_location: {
    lat:                    Double,
    lng:                    Double,
    accuracy_m:             Double,
    distance_from_campus_m: Double
  } | null,

  // Excuse — scaffold as null in v4.0, populated in v4.1+
  excuse: null,

  created_at:  ISODate,
  updated_at:  ISODate
}
```

### Java Model

```java
// checkin/model/AttendanceRecord.java
@Document(collection = "attendances")
@Data
public class AttendanceRecord {

    @Id
    private String id;

    @Field("lesson_id")
    private Long lessonId;

    @Field("user_id")
    private Long userId;

    @Field("semester_id")
    private Long semesterId;

    @Field("group_id")
    private Long groupId;

    @Field("subject_id")
    private Long subjectId;

    @Field("teacher_id")
    private Long teacherId;

    @Field("lesson_date")
    private LocalDate lessonDate;

    @Field("lesson_number")
    private Integer lessonNumber;

    // Stored as lowercase string — NOT @Enumerated (JPA only)
    // Set via: record.setStatus(AttendanceStatus.PRESENT.name().toLowerCase())
    private String status;

    @Field("marked_by")
    private String markedBy;

    @Field("checkin_location")
    private CheckinLocation checkinLocation;  // null if not geo

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Data
    public static class CheckinLocation {
        private Double lat;
        private Double lng;
        @Field("accuracy_m")
        private Double accuracyM;
        @Field("distance_from_campus_m")
        private Double distanceFromCampusM;
    }
}
```

### Index Configuration (MongoIndexConfig.java)

```java
@EventListener(ApplicationReadyEvent.class)
public void ensureIndexes() {
    mongoTemplate.indexOps("attendances").ensureIndex(
        new Index().on("lesson_id", Sort.Direction.ASC)
                   .on("user_id", Sort.Direction.ASC)
                   .unique());

    mongoTemplate.indexOps("attendances").ensureIndex(
        new Index().on("user_id", Sort.Direction.ASC)
                   .on("semester_id", Sort.Direction.ASC)
                   .on("lesson_date", Sort.Direction.DESC));

    mongoTemplate.indexOps("attendances").ensureIndex(
        new Index().on("group_id", Sort.Direction.ASC)
                   .on("semester_id", Sort.Direction.ASC)
                   .on("subject_id", Sort.Direction.ASC));

    mongoTemplate.indexOps("attendances").ensureIndex(
        new Index().on("lesson_id", Sort.Direction.ASC));
}
```

---

## Architectural Patterns

### Pattern 1: RabbitMQ Consumer Queue Binding (NEW — first consumer in system)

**What:** Attendance Service is the first service that consumes from `rut-uit.events`. Each consumer needs its own durable queue bound to the exchange. Existing publishers only declare the exchange.

**Critical:** Spring AMQP exchange declaration is idempotent — declaring the same `FanoutExchange("rut-uit.events", true, false)` in attendance-service is safe even though schedule-service already declared it.

```java
// event/RabbitConfig.java
@Configuration
public class RabbitConfig {

    @Bean
    public FanoutExchange attendanceEventsExchange() {
        // Same exchange as schedule-service and academic-service — idempotent declare
        return new FanoutExchange("rut-uit.events", true, false);
    }

    @Bean
    public Queue attendanceServiceQueue() {
        // durable=true, exclusive=false, autoDelete=false
        return new Queue("attendance-service.events", true, false, false);
    }

    @Bean
    public Binding attendanceQueueBinding(Queue attendanceServiceQueue,
                                          FanoutExchange attendanceEventsExchange) {
        return BindingBuilder.bind(attendanceServiceQueue).to(attendanceEventsExchange);
    }

    // Inject shared Spring Boot ObjectMapper — NOT a custom one (same pitfall as schedule-service)
    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // Do NOT set channelTransacted=true — same rule as existing services
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        return factory;
    }
}
```

### Pattern 2: Event Consumer with event_type Routing

**What:** All events arrive on one queue as JSON with `event_type` discriminator. The consumer deserializes to `Map<String, Object>` and switches on type.

**Why Map not typed class:** `DomainEvent` in existing services uses `@JsonTypeInfo(use = Id.NONE)` — no `@class` field in the JSON. Deserializing to a polymorphic hierarchy would require knowing all subtypes. `Map` is safe and forward-compatible.

```java
// event/LessonEventConsumer.java
@Component
@Slf4j
public class LessonEventConsumer {

    private final AutoAbsentService autoAbsentService;
    private final CheckInService checkInService;

    @RabbitListener(queues = "attendance-service.events")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("Received event without event_type, ignoring");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");

        switch (eventType) {
            case "lesson.closed" -> {
                Long lessonId = ((Number) payload.get("lesson_id")).longValue();
                Long groupId = ((Number) payload.get("group_id")).longValue();
                autoAbsentService.markAbsentForLesson(lessonId, groupId);
            }
            case "lesson.cancelled" -> {
                Long lessonId = ((Number) payload.get("lesson_id")).longValue();
                checkInService.cancelLessonAttendances(lessonId);
            }
            // lesson.started, group.updated, homework.*, semester.archived — ignore
            default -> log.debug("Ignoring event type: {}", eventType);
        }
    }
}
```

### Pattern 3: gRPC Client Configuration

Follows schedule-service's `AcademicGrpcClient` pattern exactly. Add to `application.yml`:

```yaml
grpc:
  client:
    schedule-service:
      address: static://schedule-service:19092
      negotiation-type: plaintext
    academic-service:
      address: static://academic-service:19091
      negotiation-type: plaintext
```

Client implementation:

```java
@Component
public class ScheduleGrpcClient {

    @GrpcClient("schedule-service")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;

    public LessonResponse getActiveLesson(Long groupId, LocalDateTime now) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .getActiveLesson(ActiveLessonRequest.newBuilder()
                            .setGroupId(groupId)
                            .setTimestamp(now.toString())
                            .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("ActiveLesson", "group_id", groupId);
            }
            throw new ScheduleServiceUnavailableException(
                "Schedule Service unavailable: " + e.getStatus());
        }
    }
}
```

Attendance Service is gRPC client-only in v4.0. Add `grpc-client-spring-boot-starter` but NOT `grpc-server-spring-boot-starter`. No `grpc.server.port` config needed.

### Pattern 4: MongoDB Upsert for Idempotency

Geo-checkin hitting the endpoint twice must not create a duplicate. Use `MongoTemplate.upsert()` on the `{lesson_id, user_id}` unique index:

```java
public void saveAttendance(AttendanceRecord record) {
    Query query = Query.query(
        Criteria.where("lesson_id").is(record.getLessonId())
                .and("user_id").is(record.getUserId()));
    Update update = new Update()
        .set("status", record.getStatus())
        .set("marked_by", record.getMarkedBy())
        .set("checkin_location", record.getCheckinLocation())
        .set("updated_at", Instant.now())
        .setOnInsert("created_at", Instant.now())
        .setOnInsert("semester_id", record.getSemesterId())
        .setOnInsert("group_id", record.getGroupId())
        .setOnInsert("subject_id", record.getSubjectId())
        .setOnInsert("teacher_id", record.getTeacherId())
        .setOnInsert("lesson_date", record.getLessonDate())
        .setOnInsert("lesson_number", record.getLessonNumber());
    mongoTemplate.upsert(query, update, AttendanceRecord.class);
}
```

For auto-absent bulk insert — skip existing records silently:

```java
// AutoAbsentService
public void markAbsentForLesson(Long lessonId, Long groupId) {
    List<Long> allMemberIds = academicGrpcClient.getGroupMemberIds(groupId);
    Set<Long> alreadyMarked = attendanceRepository
        .findByLessonId(lessonId)
        .stream().map(AttendanceRecord::getUserId)
        .collect(Collectors.toSet());

    List<AttendanceRecord> toInsert = allMemberIds.stream()
        .filter(uid -> !alreadyMarked.contains(uid))
        .map(uid -> buildAbsentRecord(lessonId, groupId, uid))
        .toList();

    if (!toInsert.isEmpty()) {
        // ordered=false continues inserting even if some duplicates slip through
        mongoTemplate.insertAll(toInsert);
    }
}
```

### Pattern 5: Domain Isolation via Port Interface

`report/ReportService` injects `AttendanceReadPort` interface (from `shared/port/`) never `AttendanceRepository` (from `checkin/repository/`). The implementation `AttendanceReadPortImpl` lives in `checkin/port/`.

```java
// shared/port/AttendanceReadPort.java
public interface AttendanceReadPort {
    List<AttendanceRecord> findByLessonId(Long lessonId);
    List<AttendanceRecord> findByGroupSubjectSemester(
        Long groupId, Long subjectId, Long semesterId);
    List<AttendanceRecord> findByUserIdAndSemester(Long userId, Long semesterId);
}

// checkin/port/AttendanceReadPortImpl.java
@Component
public class AttendanceReadPortImpl implements AttendanceReadPort {
    private final AttendanceRepository repo;
    // delegates to repo
}

// report/ReportService.java — only sees the interface
@Service
public class ReportService {
    private final AttendanceReadPort attendanceReadPort;  // NOT AttendanceRepository
    private final AcademicGrpcClient academicGrpcClient;
}
```

Enforced by ArchUnit test in `config/ArchUnitRules.java`:

```java
@ArchTest
static final ArchRule reportDoesNotAccessCheckinInternals =
    noClasses().that().resideInAPackage("..report..")
        .should().accessClassesThat()
        .resideInAnyPackage(
            "..checkin.repository..",
            "..checkin.service..",
            "..checkin.model..");
```

---

## Integration Points

### New Components in v4.0

| Component | Type | Notes |
|-----------|------|-------|
| `event/LessonEventConsumer` | NEW | First RabbitMQ consumer in the system |
| `event/RabbitConfig` (attendance) | NEW | Adds Queue + Binding on top of existing FanoutExchange pattern |
| `grpc/ScheduleGrpcClient` | NEW | gRPC client to Schedule Service |
| `grpc/AcademicGrpcClient` | NEW | Same pattern as schedule-service's `AcademicGrpcClient` |
| `checkin/` domain (all classes) | NEW | |
| `report/` domain (all classes) | NEW | |
| `shared/port/AttendanceReadPort` | NEW | Isolation boundary |
| MongoDB indexes | NEW | Created programmatically at startup |

### Modified: attendance-app/build.gradle.kts

Current scaffold has: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-validation`, `spring-boot-starter-hateoas`, `spring-boot-starter-amqp`, `springdoc-openapi`.

Add:

```kotlin
plugins {
    // Add to existing plugins block:
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    // gRPC client only (NOT server — v4.0 has no gRPC server)
    implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    // AOP for @RequireRole
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Testcontainers for MongoDB
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
}

sourceSets {
    main {
        proto {
            srcDir(rootProject.file("proto"))
        }
    }
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.3" }
    plugins { create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.63.0" } }
    generateProtoTasks { ofSourceSet("main").forEach { it.plugins { create("grpc") {} } } }
}
```

Attendance uses `academic.proto` + `schedule.proto` — no new `.proto` file needed in v4.0.

### Modified: application.yml

Add to existing scaffold:

```yaml
grpc:
  client:
    schedule-service:
      address: static://schedule-service:19092
      negotiation-type: plaintext
    academic-service:
      address: static://academic-service:19091
      negotiation-type: plaintext
```

No `grpc.server.port` — Attendance Service has no gRPC server in v4.0.

### Unchanged: Existing Services

| Service | Impact |
|---------|--------|
| Schedule Service | Zero — publishes `lesson.closed` / `lesson.cancelled` already (v3.0) |
| Academic Service | Zero — all gRPC endpoints used (`GetGroupMembers`, `GetCampusGeofence`, `GetTeacherSubjects`) already exist (v2.0) |
| API Gateway | Add routes: `/attendance/**` and `/reports/**` → `attendance-service:9093` |
| `proto/attendance.proto` | Not needed in v4.0 (no gRPC server). Create when Attendance adds gRPC server |

---

## Anti-Patterns

### Anti-Pattern 1: Adding a Consumer Without a Dedicated Queue

**What people do:** Assume the fanout exchange delivers to all connected services without queue binding.

**Why it's wrong:** Fanout delivers one copy per bound queue. No queue = messages dropped. Sharing a queue with another service = only one service receives each message.

**Do this instead:** Declare a unique `Queue("attendance-service.events", true, false, false)` and bind it with `BindingBuilder.bind(queue).to(exchange)` in `RabbitConfig`.

### Anti-Pattern 2: Using @TransactionalEventListener(AFTER_COMMIT) Without a Transaction Manager

**What people do:** Copy `DomainEventListener` from schedule-service (which uses JPA + `@TransactionalEventListener(AFTER_COMMIT)`) into the attendance-service without configuring `MongoTransactionManager`.

**Why it's wrong:** `@TransactionalEventListener(AFTER_COMMIT)` only fires if the current thread is inside an active Spring transaction. MongoDB does not participate in Spring transactions unless `MongoTransactionManager` is configured and `@Transactional` is applied. Without this, the event is published in `BEFORE_COMMIT` phase by default — or never, if no transaction context exists. The result: silent event loss.

**Do this instead:** Use plain `@EventListener` for MongoDB-backed event publishing in v4.0. This fires synchronously on `publishEvent()` call, after the `mongoTemplate.upsert()` returns. The risk (event fires before mongo write actually persists) is negligible given MongoDB's default `w:1` write concern. Document this decision. If strict ordering is required in future, add `MongoTransactionManager` + `@Transactional` on service methods.

### Anti-Pattern 3: Calling GetGroupMembers gRPC on Every Geo-Checkin

**What people do:** Call `GetGroupMembers` on every checkin to verify student membership.

**Why it's wrong:** Unnecessary gRPC on the hot checkin path. The JWT already proves identity; `group_id` is in `X-Group-Id` header; the lesson's `group_id` comes from `GetActiveLesson`.

**Do this instead:** Verify `requestContext.getGroupId().equals(lesson.getGroupId())`. No membership gRPC needed. Reserve `GetGroupMembers` for auto-absent and report generation only.

### Anti-Pattern 4: Importing checkin.repository in report/

**What people do:** Inject `AttendanceRepository` directly in `ReportService` for convenience.

**Why it's wrong:** Violates domain isolation. Breaks the future extraction path. Caught by `ArchUnitRules` test at compile time.

**Do this instead:** Use `AttendanceReadPort` in `report/` exclusively.

### Anti-Pattern 5: Storing Enum Values as Uppercase in MongoDB

**What people do:** Let Spring Data MongoDB's default `@Field` behavior serialize `AttendanceStatus.PRESENT` as `"PRESENT"`.

**Why it's wrong:** Project convention: all enum values stored as lowercase strings. Inconsistency with existing services, harder to query.

**Do this instead:** Store manually: `record.setStatus(status.name().toLowerCase())`. Read back: `AttendanceStatus.valueOf(record.getStatus().toUpperCase())`. Do NOT use `@Enumerated` — that is a JPA annotation, it has no effect with Spring Data MongoDB.

---

## Build Order

Build phases in dependency order:

**Phase 1 — Infrastructure wiring** (no business logic)
- `build.gradle.kts`: add grpc-client, protobuf plugin, AOP, testcontainers-mongodb
- `application.yml`: add `grpc.client` config for schedule-service and academic-service
- `security/` package: copy from schedule-service, update package to `ru.rutcampustrack.attendance.security`
- `exception/` package: GlobalExceptionHandler, base exceptions
- `grpc/ScheduleGrpcClient` + `grpc/AcademicGrpcClient`: stubs with 3-second deadline
- Verify: project compiles, proto stubs generated

**Phase 2 — RabbitMQ consumer**
- `event/RabbitConfig`: FanoutExchange + Queue + Binding + Jackson converter + RabbitTemplate + listener container factory
- `event/DomainEvent` + `event/AttendanceMarkedEvent`: same envelope structure as schedule-service
- `event/DomainEventListener`: plain `@EventListener` (not transactional)
- `event/LessonEventConsumer`: `@RabbitListener("attendance-service.events")` with empty handlers
- Verify: service starts, connects to RabbitMQ, queue is declared and bound, events received without crash

**Phase 3 — MongoDB document + repository**
- `checkin/model/AttendanceRecord`: `@Document("attendances")`, all fields, nested `CheckinLocation`
- `checkin/repository/AttendanceRepository`: `findByLessonId`, `findByLessonIdAndUserIdIn`, etc.
- `config/MongoIndexConfig`: all 4 indexes via `IndexOperations.ensureIndex`
- `shared/port/AttendanceReadPort`: interface only
- `checkin/port/AttendanceReadPortImpl`: implements port with repo delegates
- Verify: Testcontainer MongoDB, index creation on startup, basic CRUD

**Phase 4 — Geo-checkin + manual mark**
- `attendance-api-contract`: `GeoCheckinRequest`, `ManualMarkRequest`, `AttendanceResponse`, `CheckInApi`, `ManualMarkApi`
- `checkin/CheckInService`: Haversine geo validation, time window check, upsert, event publish
- `checkin/CheckInController` + `checkin/ManualMarkController` + `checkin/AttendanceAssembler`
- Integration tests: Testcontainers MongoDB + `@MockitoBean` for gRPC clients

**Phase 5 — Auto-absent handler**
- `checkin/AutoAbsentService`: bulk absent on lesson.closed
- Wire into `LessonEventConsumer`
- `checkInService.cancelLessonAttendances()` for lesson.cancelled
- Integration tests: publish lesson.closed event via RabbitMQ, verify absent records in MongoDB

**Phase 6 — Reports**
- `attendance-api-contract`: `JournalResponse`, `StudentStatsResponse`, `JournalApi`, `StudentStatsApi`
- `report/ReportService`: MongoDB queries via `AttendanceReadPort` + gRPC enrichment
- `report/JournalController` + `report/StudentStatsController`
- `config/ArchUnitRules`: domain isolation test
- Integration tests: full journal and stats queries

---

## Scaling Considerations

| Scale | Architecture Notes |
|-------|--------------------|
| < 1k students | Single instance, current design is sufficient |
| 1k-5k students | Auto-absent bulk-marks on lesson.closed are bounded by group size (30-40), not total students. MongoDB write throughput is fine. |
| 5k+ students | Consider caching `GetGroupMembers` results locally (Redis) to avoid gRPC on every auto-absent burst. Reports may need aggregation pipelines instead of in-memory joins. |

First bottleneck on hot path: geo-checkin makes two gRPC calls synchronously. `GetCampusGeofence` is cached in Academic's Redis (60 min TTL); `GetActiveLesson` is real-time. If Schedule Service is unavailable, checkins fail. Mitigation: 3-second gRPC deadline is already in place; circuit breaker is a future concern.

---

## Sources

All findings are HIGH confidence — verified directly from source code.

- `services/schedule-service/schedule-app/src/` — gRPC client pattern (`AcademicGrpcClient`), DomainEvent base class, DomainEventListener, RabbitConfig, security package (full source read)
- `services/academic-service/academic-app/src/` — RabbitConfig pattern, event publishing pattern
- `services/attendance-service/` — existing scaffold (build.gradle.kts, application.yml, contract enums)
- `proto/academic.proto`, `proto/schedule.proto` — gRPC contracts: all RPCs and message types
- `event-schemas/lesson.started.json`, `lesson.closed.json`, `lesson.cancelled.json`, `attendance.marked.json` — event envelope structure
- `docs/architecture.md` — service topology, MongoDB collection design, package structure mandate
- `docs/database-schema.md` — MongoDB document schema, index strategy, enum conventions

---

*Architecture research for: Attendance Service MVP (v4.0)*
*Researched: 2026-04-04*

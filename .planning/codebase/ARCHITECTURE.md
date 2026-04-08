# Architecture

**Analysis Date:** 2026-04-08

## Pattern Overview

**Overall:** Event-driven microservices with contract-first APIs, synchronous gRPC inter-service communication, and asynchronous event distribution via RabbitMQ fanout.

**Key Characteristics:**
- Each backend service is split into `{name}-api-contract` (contract library) and `{name}-app` (Spring Boot implementation)
- Controllers implement contract interfaces; REST mappings live only in contract layer
- Internal service-to-service calls use gRPC stubs (point-to-point, request/response)
- Domain events publish to RabbitMQ fanout exchange, broadcast to all subscribers
- JWT tokens validated at API Gateway before routing to microservices; gateway injects headers
- Role-based authorization via AOP (`@RequireRole` annotation) on controller methods
- STOMP WebSocket (SockJS fallback) for real-time notifications to connected clients
- Web Push via VAPID keys for offline notifications
- Databases per service: PostgreSQL (academic_db, schedule_db), MongoDB (attendance_db), Redis (caching + auth tokens)

## Layers

**API Contract Layer (Gateway):**
- Purpose: Entry point for all frontend traffic; JWT validation; request routing to microservices
- Location: `services/api-gateway/`
- Contains: Spring Cloud Gateway filters, JWT validation, public key caching
- Depends on: JWT library (JJWT), public key endpoint from Auth Service
- Used by: All frontends and gRPC clients

**Authentication Service:**
- Purpose: User login, OTP generation, JWT token issuance, public key distribution
- Location: `services/auth-service/`
- Contains: User credentials, session/refresh token management, OTP logic
- Depends on: PostgreSQL (user table), Redis (token blacklist)
- Used by: API Gateway (public key), other services (token validation via public key)

**Academic Service (Contract + App):**
- Purpose: Groups, semesters, subjects, users, thresholds, headman management
- Location: `services/academic-service/academic-api-contract/` + `services/academic-service/academic-app/`
- Contains: Entities (User, Group, Subject, Semester, etc.), REST controllers, services, repositories
- Depends on: PostgreSQL (academic_db), Redis (caching: groups, members, semesters, geofence), RabbitMQ (publishes lesson.started, group.updated, semester.archived)
- Used by: Attendance Service (gRPC calls for group info), Schedule Service (gRPC for subjects)
- DB: PostgreSQL `academic_db`

**Schedule Service (Contract + App):**
- Purpose: Lessons, timetables, class schedules
- Location: `services/schedule-service/schedule-api-contract/` + `services/schedule-service/schedule-app/`
- Contains: Lesson entities, REST controllers, schedule logic
- Depends on: PostgreSQL (schedule_db), RabbitMQ (publishes lesson.started, lesson.closed, lesson.cancelled)
- Used by: Attendance Service (gRPC for lesson details)
- DB: PostgreSQL `schedule_db`

**Attendance Service (Contract + App):**
- Purpose: Mark attendance, geo-checkin validation, attendance statistics, excuse tickets, automated marking
- Location: `services/attendance-service/attendance-api-contract/` + `services/attendance-service/attendance-app/`
- Contains: Two isolated domains (checkin, report), shared port pattern for cross-domain access
- Depends on: MongoDB (attendance_db), Academic Service gRPC (group info, geofence), Schedule Service gRPC (lesson info), RabbitMQ (subscribes to lesson.started/closed, publishes attendance.marked)
- Used by: Web frontends (checkin, stats), Notification Web (via events)
- DB: MongoDB `attendance_db`

**Notification Web Service (Contract + App):**
- Purpose: STOMP WebSocket server for real-time notifications, Web Push subscription management, offline notification delivery
- Location: `services/notification-service/notification-api-contract/` + `services/notification-service/notification-app/`
- Contains: WebSocket/STOMP config, Web Push (VAPID) config, Push subscription storage (MongoDB), event consumers from RabbitMQ
- Depends on: MongoDB (subscription_db), RabbitMQ (subscribes to all events: attendance.marked, excuse.requested, etc.), VAPID keys (env vars)
- Used by: Connected browser clients (WebSocket), offline clients (Web Push)
- DB: MongoDB `subscription_db`

## Data Flow

**User Login Flow:**
1. Frontend POSTs credentials to `/api/auth/login` (public endpoint)
2. API Gateway passes to Auth Service (port 9090)
3. Auth Service validates, returns JWT + refresh token
4. Frontend stores refresh token in httpOnly cookie, access token in memory
5. Subsequent requests include `Authorization: Bearer <access_token>` header

**Request Authorization Flow:**
1. Frontend sends request with JWT in `Authorization` header
2. API Gateway's `JwtAuthenticationFilter` (Spring Cloud Gateway GlobalFilter) validates JWT signature using cached public key from Auth Service
3. On valid token, gateway extracts claims and injects headers: `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman`
4. Gateway forwards request to target service with injected headers
5. Service method decorated with `@RequireRole({ADMIN, STUDENT})` is intercepted by AOP aspect
6. Aspect reads `X-User-Role` header, checks against required roles; if denied, returns 403
7. RequestContext bean reads headers for business logic use

**Synchronous Service Call (gRPC):**
1. Service A needs data from Service B (e.g., Attendance Service needs group info from Academic Service)
2. Service A has gRPC stub client configured in Spring context
3. Calls stub method: `academicGrpcService.getGroup(groupRequest)` (port 19091 for Academic Service)
4. Service B's gRPC server handles request, returns protobuf message
5. Service A deserializes and uses data

**Asynchronous Event Publication (RabbitMQ):**
1. Business logic in Service A publishes domain event (e.g., `AttendanceEventPublisher.publishMarked()`)
2. Event is serialized to JSON envelope matching `event-schemas/*.json` schema
3. Message published to fanout exchange `rut-uit.events` with empty routing key
4. All bound queues (e.g., notification-web's queue) receive the message
5. Subscriber deserializes and processes (e.g., sends STOMP notification)

**WebSocket/STOMP Notification Flow:**
1. Frontend establishes WebSocket connection to `/api/ws` (via gateway → notification-web)
2. `JwtHandshakeInterceptor` reads `?token=` query param, validates JWT, stores user context
3. Frontend subscribes to `/topic/group/{groupId}` via STOMP SUBSCRIBE frame
4. `SubscriptionAuthInterceptor` validates subscription (user must be in that group)
5. When event arrives (e.g., lesson.started), notification service publishes to `/topic/group/123`
6. All subscribed clients in that topic receive the message (SimpMessagingTemplate.convertAndSend)

**Web Push Delivery:**
1. PWA frontend calls `POST /api/notification/push/subscribe` with PushSubscription (endpoint URL, keys)
2. Notification Web stores subscription in MongoDB with user ID
3. When offline client should be notified, service queries MongoDB for subscriptions
4. Uses `nl.martijndwars.webpush.PushService` with VAPID private key to send HTTP POST to push service endpoint
5. Browser's service worker receives push event and displays notification

**State Management:**
- Transient state (user tokens, session): Redis + httpOnly cookies
- User authentication state: Auth Service database (PostgreSQL)
- Academic data: Academic Service PostgreSQL + Redis cache (TTL 5-10 min)
- Attendance records: MongoDB (immutable, append-only by design)
- Notifications in-flight: RabbitMQ queues (auto-ack on delivery to client)
- Push subscriptions: MongoDB in notification-web

## Key Abstractions

**API Contract Interface:**
- Purpose: Define REST endpoint contract, decoupled from implementation
- Examples: `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java`, `UserApi.java`, `GroupApi.java`
- Pattern: Interface with `@RequestMapping`, method annotations (`@GetMapping`, `@PostMapping`), Swagger annotations. Concrete controller implements interface.

**DTO (Data Transfer Object):**
- Purpose: Represent request/response payloads
- Examples: `CreateHomeworkRequest` (record), `HomeworkResponse` (class with HATEOAS RepresentationModel)
- Pattern: Request = Java record (immutable, compact), Response = class (for HATEOAS links)

**Entity:**
- Purpose: ORM-mapped database row
- Examples: `Homework.java`, `User.java`, `Group.java`
- Pattern: `@Entity`, JPA annotations, Lombok getters/setters. Entities live only in `*-app` module.

**Service Layer:**
- Purpose: Business logic, validation, orchestration between repositories and controllers
- Examples: `HomeworkService.java`, `AssignmentService.java`, `DashboardService.java`
- Pattern: `@Service`, `@Transactional`, constructor-injected dependencies, throws domain exceptions

**Repository:**
- Purpose: Data access abstraction
- Examples: `HomeworkRepository.java` (extends Spring Data JPA), `TeacherSubjectGroupRepository.java`
- Pattern: Interface extends `JpaRepository<T, ID>`, custom query methods with `@Query` annotations

**Assembler (HATEOAS):**
- Purpose: Convert Entity → EntityModel<DTO> with HAL links
- Examples: `HomeworkAssembler.java`, `GroupAssembler.java`
- Pattern: Implements `RepresentationModelAssembler<E, EntityModel<R>>`, method returns `EntityModel.of(response, linkTo(...).withSelfRel())`

**Event Publisher:**
- Purpose: Serialize domain event and publish to RabbitMQ
- Examples: `AttendanceEventPublisher.java`
- Pattern: Constructs JSON envelope, calls `rabbitTemplate.convertAndSend(exchange, "", payload)`

**Port Interface (Domain Isolation):**
- Purpose: Allow cross-domain access without direct imports
- Examples: `AttendanceReadPort.java` in `attendance/shared/port/`
- Pattern: Interface in shared package with zero domain-specific imports; implementation in one domain

**gRPC Service:**
- Purpose: Define RPC contract for inter-service communication
- Examples: `AcademicGrpcService` (proto definition in `proto/academic.proto`), implementation in `academic-app/src/main/java/.../grpc/AcademicReadService.java`
- Pattern: Protobuf message definitions, Spring `@GrpcService` annotation on impl, grpc server port in config

## Entry Points

**API Gateway:**
- Location: `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/GatewayApplication.java`
- Triggers: Spring Boot startup
- Responsibilities: Listen on port 8080, route all `/api/*` to microservices, validate JWT, inject user headers, handle CORS

**Microservice Boot Class:**
- Location: `services/{name}/{name}-app/src/main/java/ru/rutcampustrack/{name}/{Name}Application.java` (e.g., `AcademicApplication.java`)
- Triggers: `java -jar academic-app-0.1.0.jar`
- Responsibilities: Initialize Spring Boot app, load beans, connect to DB/RabbitMQ/gRPC, start server on service port

**gRPC Server:**
- Location: Each microservice has gRPC server config (e.g., `services/academic-service/academic-app/src/main/resources/application.yml` with `grpc.server.port: 19091`)
- Triggers: Spring Boot startup
- Responsibilities: Listen for gRPC requests on alternate port, route to `@GrpcService` implementations

**WebSocket/STOMP Endpoint:**
- Location: `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java`
- Triggers: HTTP Upgrade request to `/api/ws`
- Responsibilities: Validate JWT at handshake, accept WebSocket connection, route STOMP frames

**Scheduled Tasks:**
- Location: Various services may use `@Scheduled` on task methods
- Triggers: Spring Scheduler (cron expressions in `application.yml` or code)
- Responsibilities: Periodic jobs (e.g., close lessons, send reminders, auto-mark absent)

## Error Handling

**Strategy:** Centralized error handling via `@ControllerAdvice` beans. Controllers throw domain exceptions; advice catches and transforms to RFC 7807 Problem Details.

**Patterns:**

1. **Domain Exception (thrown by service):**
```java
throw new ResourceNotFoundException("Homework", "id", homeworkId);
throw new AccessDeniedException("Только админ может...");
throw new BadRequestException("Invalid semester");
```

2. **Exception Handler (catches and converts):**
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(404, ex.getMessage(), ...));
    }
}
```

3. **Response Format (RFC 7807 Problem Details):**
```json
{
  "status": 404,
  "message": "Resource not found: Homework with id=999",
  "path": "/api/academic/homeworks/999",
  "timestamp": "2026-04-08T12:34:56Z"
}
```

4. **Validation Errors:**
- `@Valid` on request DTO triggers JSR-303 validation
- `MethodArgumentNotValidException` caught by advice, returns 400 with field-level errors

5. **Authorization Errors:**
- `@RequireRole` AOP aspect throws custom exception if role check fails
- Advice catches, returns 403 Forbidden

## Cross-Cutting Concerns

**Logging:**
- SLF4J via Logback (Spring Boot default)
- Packages `ru.rutcampustrack.*` at DEBUG level
- Config: `application.yml` logging section
- Pattern: `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`
- Key events: request start/end, authentication, DB queries, event publish/consume

**Validation:**
- Request DTOs use JSR-303 annotations: `@NotNull`, `@Size`, `@Min`, `@Email`, etc.
- Triggered by `@Valid` on controller method parameters
- Errors collected and returned as 400 with details

**Authentication:**
- JWT: decoded at gateway, claims validated using public key from Auth Service
- Public key cached in Redis with TTL (per `cache.ttl.active-semester` config pattern)
- Fallback: re-fetch from Auth Service if cache miss
- Header injection: `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman`

**Authorization:**
- `@RequireRole({ADMIN, TEACHER})` AOP aspect on controller methods
- Reads `X-User-Role` header injected by gateway
- Throws `AccessDeniedException` if role not in allowed set
- Location: `services/{name}/{name}-app/src/main/java/ru/rutcampustrack/{name}/security/RequireRole.java` (aspect)

**Caching (Redis):**
- Cache groups, group members, users, active semester, campus geofence
- TTL per item type: 5 min (groups/members/users), 10 min (semester), 1 hour (geofence)
- Config: `cache.ttl.*` in `application.yml`
- Invalidation: RabbitMQ event listener clears cache on `group.updated`, `semester.archived`

**Database Transactions:**
- `@Transactional` on service methods that modify state
- Default isolation level: `READ_COMMITTED`
- Rollback on unchecked exceptions only (custom domain exceptions extend `RuntimeException`)

---

*Architecture analysis: 2026-04-08*

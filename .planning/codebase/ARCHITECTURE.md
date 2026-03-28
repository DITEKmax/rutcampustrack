# Architecture

**Analysis Date:** 2026-03-28

## Pattern Overview

**Overall:** Microservices with API Gateway (contract-first design)

**Key Characteristics:**
- Monorepo with Gradle multi-module build
- Contract-first: each domain service has a `*-api-contract` (pure `java-library`) and a `*-app` (Spring Boot application)
- Database-per-service: each service owns its own database, no shared schemas
- Synchronous inter-service calls via gRPC; asynchronous events via RabbitMQ (fanout exchange)
- All external traffic routes through a single API Gateway on port 8080
- Phase 0 is complete (scaffold, contracts, infrastructure). No business logic implemented yet -- only enums, exceptions, converters, and database migrations exist.

## Service Map

**API Gateway (port 8080):**
- Responsibility: Route external HTTP requests to internal services, JWT validation
- Tech: Spring Cloud Gateway 2024.0.0, JJWT 0.12.6
- Database: None
- Entry point: `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/GatewayApplication.java`
- Config: `services/api-gateway/src/main/resources/application.yml`
- Build: `services/api-gateway/build.gradle.kts`

**Auth Service (port 9090):**
- Responsibility: JWT authentication, OTP, password management
- Tech: Spring Boot 3.4.1, Spring Security, Spring Data Redis, JJWT 0.12.6
- Database: Redis (session/token store)
- Entry point: `services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java`
- Config: `services/auth-service/src/main/resources/application.yml`
- Build: `services/auth-service/build.gradle.kts`

**Academic Service (port 9091):**
- Responsibility: Users, groups, semesters, subjects, teacher assignments, homework, campus settings, attendance thresholds
- Tech: Spring Boot 3.4.1, Spring Data JPA, Spring Data Redis (cache), Spring AMQP, Flyway, HATEOAS
- Database: PostgreSQL 16 (`academic_db`) + Redis cache
- Contract module: `services/academic-service/academic-api-contract/`
- App module: `services/academic-service/academic-app/`
- Entry point: `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/AcademicApplication.java`
- Config: `services/academic-service/academic-app/src/main/resources/application.yml`
- Migration: `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql`

**Schedule Service (port 9092):**
- Responsibility: Weekly schedule templates (`schedule_items`), concrete lesson instances (`lessons`), lesson lifecycle (planned -> active -> closed/cancelled)
- Tech: Spring Boot 3.4.1, Spring Data JPA, Spring AMQP, Flyway, HATEOAS
- Database: PostgreSQL 16 (`schedule_db`)
- Contract module: `services/schedule-service/schedule-api-contract/`
- App module: `services/schedule-service/schedule-app/`
- Entry point: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/ScheduleApplication.java`
- Config: `services/schedule-service/schedule-app/src/main/resources/application.yml`
- Migration: `services/schedule-service/schedule-app/src/main/resources/db/migration/V1__baseline.sql`

**Attendance Service (port 9093):**
- Responsibility: Check-in records, attendance reports, excuse tickets
- Tech: Spring Boot 3.4.1, Spring Data MongoDB, Spring AMQP, HATEOAS
- Database: MongoDB 7 (`attendance_db`)
- Contract module: `services/attendance-service/attendance-api-contract/`
- App module: `services/attendance-service/attendance-app/`
- Entry point: `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/AttendanceApplication.java`
- Config: `services/attendance-service/attendance-app/src/main/resources/application.yml`
- Note: Internal domain isolation -- `checkin/` and `report/` subdomains communicate only through `shared/port/AttendanceReadPort` interface

**Notification Web (port 9094):**
- Responsibility: WebSocket push notifications to browser clients
- Tech: Spring Boot 3.4.1, Spring WebSocket, Spring AMQP
- Database: None
- Entry point: `services/notification-web/src/main/java/ru/rutcampustrack/notification/NotificationWebApplication.java`
- Config: `services/notification-web/src/main/resources/application.yml`

**Notification Bot (no port -- standalone process):**
- Responsibility: Telegram bot for student notifications and headman workflows
- Tech: Python, Aiogram 3.15.0, aio-pika 9.5.3 (RabbitMQ), gRPC (grpcio 1.69.0)
- Database: None (consumes events from RabbitMQ, calls gRPC for data)
- Location: `services/notification-bot/`
- Dependencies: `services/notification-bot/requirements.txt`
- Environment config: `services/notification-bot/.env.example` (existence noted only)

## Communication Patterns

**Synchronous -- REST (external):**
- All client traffic enters through API Gateway on port 8080
- Gateway routes by path prefix:
  - `/api/auth/**` -> Auth Service (9090)
  - `/api/academic/**` -> Academic Service (9091)
  - `/api/schedule/**` -> Schedule Service (9092)
  - `/api/attendance/**`, `/api/reports/**` -> Attendance Service (9093)
  - `/api/ws/**` -> Notification Web (9094)
- Gateway applies `StripPrefix=1` filter to remove `/api` prefix before forwarding
- REST API follows HATEOAS Level 3 with `EntityModel<T>` and `PagedModel<EntityModel<T>>`
- Errors follow RFC 7807 Problem Details via `ErrorResponse` record at `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ErrorResponse.java`

**Synchronous -- gRPC (inter-service):**
- Proto definitions in `proto/` directory
- `proto/academic.proto`: `AcademicGrpcService` -- 7 RPCs for group info, members, teacher subjects, headman check, active semester, campus geofence, user lookup
- `proto/schedule.proto`: `ScheduleGrpcService` -- 3 RPCs for active lesson, lesson by ID, lessons by group
- Java package: `ru.rutcampustrack.{service}.grpc`
- Notification Bot uses gRPC to call Academic and Schedule services (via grpcio Python client)
- gRPC not yet wired in Spring Boot services (commented out in `build.gradle.kts`: `// implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")`)

**Asynchronous -- RabbitMQ Events:**
- Broker: RabbitMQ 3.13 with management UI on port 15672 (dev only)
- Exchange type: fanout
- Event envelope: `{ event_type, event_id (UUID), occurred_at (ISO-8601), payload }`
- Event type naming: `{domain}.{action}`
- JSON Schema contracts in `event-schemas/`:

| Event | Producer | Schema File |
|-------|----------|-------------|
| `lesson.started` | Schedule Service | `event-schemas/lesson.started.json` |
| `lesson.closed` | Schedule Service | `event-schemas/lesson.closed.json` |
| `lesson.cancelled` | Schedule Service | `event-schemas/lesson.cancelled.json` |
| `attendance.marked` | Attendance Service | `event-schemas/attendance.marked.json` |
| `excuse.requested` | Attendance Service | `event-schemas/excuse.requested.json` |
| `late_checkin.requested` | Attendance Service | `event-schemas/late_checkin.requested.json` |
| `homework.published` | Academic Service | `event-schemas/homework.published.json` |

- Consumers: Notification Web (WebSocket push), Notification Bot (Telegram messages), Attendance Service (auto-absent on `lesson.closed`)

## Data Architecture

**Database-per-Service:**

| Service | Engine | Database | Connection |
|---------|--------|----------|------------|
| Academic | PostgreSQL 16 | `academic_db` | `postgres-academic:5432` |
| Schedule | PostgreSQL 16 | `schedule_db` | `postgres-schedule:5432` |
| Attendance | MongoDB 7 | `attendance_db` | `mongo-attendance:27017` |
| Auth | Redis 7 | N/A (key-value) | `redis:6379` |
| Academic (cache) | Redis 7 | N/A (shared Redis) | `redis:6379` |

**Schema Ownership:**
- Academic Service owns: `users`, `groups`, `semesters`, `subjects`, `teacher_subject_groups`, `headman_assistants`, `campus_settings`, `attendance_thresholds`, `homeworks`, `homework_completions`, `student_group_history`, `password_reset_tokens`
- Schedule Service owns: `schedule_items`, `lessons`
- Schedule Service references Academic IDs (`group_id`, `subject_id`, `teacher_id`, `semester_id`) by value only -- no foreign keys across databases
- Attendance Service uses MongoDB documents (schema not yet defined in migrations)

**Migration Approach:**
- Flyway for PostgreSQL services
- Migration files: `src/main/resources/db/migration/V{N}__description.sql`
- Hibernate `ddl-auto: validate` -- Hibernate validates against schema, never creates/modifies it
- PostgreSQL enums stored as lowercase strings; custom enum types created in SQL (`CREATE TYPE user_role AS ENUM (...)`)

**Primary Keys:**
- PostgreSQL: `BIGSERIAL` (maps to `Long` in Java)
- Timestamps: `TIMESTAMPTZ` (stored in UTC)

**Enum Conversion:**
- Java enums use `UPPER_CASE` (e.g., `UserRole.ADMIN`)
- PostgreSQL stores lowercase strings (e.g., `'admin'`)
- Conversion via `LowercaseEnumConverter<E>` abstract class at `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/LowercaseEnumConverter.java`
- Concrete converters with `@Converter(autoApply = true)` in `EnumConverters.java` per service

**Soft Delete:**
- Users use `status = 'archived'` instead of physical DELETE
- `AccountStatus` enum: `ACTIVE`, `EXPELLED`, `SUSPENDED`, `ARCHIVED`

## Entry Points

**Application Main Classes:**
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/GatewayApplication.java`
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/AcademicApplication.java`
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/ScheduleApplication.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/AttendanceApplication.java`
- `services/notification-web/src/main/java/ru/rutcampustrack/notification/NotificationWebApplication.java`

**Gateway Routes (external entry):**
- All routes defined in `services/api-gateway/src/main/resources/application.yml`
- Pattern: `/api/{service-prefix}/**` stripped to `/{service-prefix}/**` before forwarding

## Cross-Cutting Concerns

**Error Handling:**
- RFC 7807 Problem Details format
- `ErrorResponse` record defined in academic-api-contract (shared)
- `ResourceNotFoundException` in academic-api-contract as base exception
- Each service uses `@ControllerAdvice` with `GlobalExceptionHandler` (to be implemented)
- Controllers only throw exceptions, never construct error responses directly

**Authentication/Authorization:**
- JWT-based authentication via Auth Service
- API Gateway validates JWT and forwards user context headers
- JJWT 0.12.6 library for JWT parsing/validation
- Roles: `ADMIN`, `TEACHER`, `STUDENT`
- Headman is a `STUDENT` with `is_headman=true` flag, not a separate role

**API Documentation:**
- SpringDoc OpenAPI (springdoc-openapi-starter-webmvc-ui 2.7.0)
- Swagger UI available per service at `/swagger-ui.html`
- API docs at `/api-docs`
- OpenAPI annotations (`@Operation`, `@ApiResponse`) placed in contract interfaces

**Logging:**
- Standard Spring Boot logging
- Debug level for `ru.rutcampustrack` package in all services
- Gateway: debug level for `org.springframework.cloud.gateway`

**Configuration:**
- Environment variables with defaults for dev (e.g., `${POSTGRES_ACADEMIC_PASSWORD:rct_dev_pass}`)
- Docker Compose provides infrastructure services on `private_net` bridge network
- Services communicate via Docker service names (e.g., `postgres-academic`, `redis`, `rabbitmq`)

**Monitoring:**
- Spring Boot Actuator included in API Gateway (`spring-boot-starter-actuator`)
- Not yet added to other services

**Networking:**
- All infrastructure containers on `private_net` Docker bridge network
- Infrastructure ports exposed only within Docker network (no host port mapping except RabbitMQ management UI 15672)

---

*Architecture analysis: 2026-03-28*

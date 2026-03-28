# Codebase Structure

**Analysis Date:** 2026-03-28

## Directory Layout

```
rutcampustrack/
├── build.gradle.kts            # Root Gradle build (plugins, allprojects, subprojects config)
├── settings.gradle.kts         # Defines all Gradle subprojects (9 modules)
├── gradle/wrapper/             # Gradle wrapper (8.x)
├── docker-compose.yml          # Infrastructure: 2x PostgreSQL, MongoDB, Redis, RabbitMQ
├── .gitignore                  # Ignores build/, .gradle/, .idea/, .env files
├── CLAUDE.md                   # Project instructions and coding rules
├── docs/                       # Project documentation (architecture, schemas, phases)
├── proto/                      # gRPC .proto contract definitions
├── event-schemas/              # JSON Schema for RabbitMQ event messages
├── services/                   # All backend services (Java + Python)
│   ├── api-gateway/            # Spring Cloud Gateway (single module)
│   ├── auth-service/           # Auth JWT/OTP service (single module)
│   ├── academic-service/       # Academic domain (contract + app)
│   │   ├── academic-api-contract/  # Pure java-library: DTOs, enums, interfaces
│   │   └── academic-app/           # Spring Boot application
│   ├── schedule-service/       # Schedule domain (contract + app)
│   │   ├── schedule-api-contract/
│   │   └── schedule-app/
│   ├── attendance-service/     # Attendance domain (contract + app)
│   │   ├── attendance-api-contract/
│   │   └── attendance-app/
│   ├── notification-web/       # Java WebSocket push service (single module)
│   └── notification-bot/       # Python Telegram bot (NOT a Gradle module)
└── frontends/                  # Placeholder directory (no code yet)
```

## Directory Purposes

**`docs/`:**
- Purpose: Project-level documentation
- Contains: Architecture decisions, database schema, job stories, phase reports, phase plans
- Key files:
  - `docs/architecture.md` -- detailed system architecture
  - `docs/database-schema.md` -- full database schema documentation
  - `docs/job-stories.md` -- all user/job stories (business requirements)
  - `docs/phases-plan.md` -- detailed plan for all implementation phases
  - `docs/phase-0-report.md` -- Phase 0 completion report
  - `docs/claude-code-guide.md` -- Claude Code usage guide

**`proto/`:**
- Purpose: gRPC contract definitions shared across services
- Contains: `.proto` files
- Key files:
  - `proto/academic.proto` -- AcademicGrpcService (7 RPCs)
  - `proto/schedule.proto` -- ScheduleGrpcService (3 RPCs)

**`event-schemas/`:**
- Purpose: JSON Schema contracts for RabbitMQ events
- Contains: One `.json` file per event type
- Key files:
  - `event-schemas/lesson.started.json`
  - `event-schemas/lesson.closed.json`
  - `event-schemas/lesson.cancelled.json`
  - `event-schemas/attendance.marked.json`
  - `event-schemas/excuse.requested.json`
  - `event-schemas/late_checkin.requested.json`
  - `event-schemas/homework.published.json`

**`services/api-gateway/`:**
- Purpose: HTTP entry point, routes requests to backend services
- Key files:
  - `services/api-gateway/build.gradle.kts`
  - `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/GatewayApplication.java`
  - `services/api-gateway/src/main/resources/application.yml` -- route definitions

**`services/auth-service/`:**
- Purpose: JWT authentication, OTP, password management
- Key files:
  - `services/auth-service/build.gradle.kts`
  - `services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java`
  - `services/auth-service/src/main/resources/application.yml`

**`services/academic-service/academic-api-contract/`:**
- Purpose: Pure Java library containing the Academic Service API contract
- Contains: Enums, exception classes (no Spring Boot, no Lombok)
- Key files:
  - `services/academic-service/academic-api-contract/build.gradle.kts` -- `java-library` plugin
  - `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/UserRole.java`
  - `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/AccountStatus.java`
  - `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/SubjectType.java`
  - `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/AssistantPermission.java`
  - `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ErrorResponse.java`
  - `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ResourceNotFoundException.java`

**`services/academic-service/academic-app/`:**
- Purpose: Spring Boot application implementing the Academic Service
- Key files:
  - `services/academic-service/academic-app/build.gradle.kts`
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/AcademicApplication.java`
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/LowercaseEnumConverter.java`
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/EnumConverters.java`
  - `services/academic-service/academic-app/src/main/resources/application.yml`
  - `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql`

**`services/schedule-service/schedule-api-contract/`:**
- Purpose: Pure Java library for Schedule Service API contract
- Key files:
  - `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/enums/WeekType.java`
  - `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/enums/LessonStatus.java`

**`services/schedule-service/schedule-app/`:**
- Purpose: Spring Boot application implementing the Schedule Service
- Key files:
  - `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/ScheduleApplication.java`
  - `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/EnumConverters.java`
  - `services/schedule-service/schedule-app/src/main/resources/application.yml`
  - `services/schedule-service/schedule-app/src/main/resources/db/migration/V1__baseline.sql`

**`services/attendance-service/attendance-api-contract/`:**
- Purpose: Pure Java library for Attendance Service API contract
- Key files:
  - `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/AttendanceStatus.java`
  - `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/AttendanceSource.java`
  - `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/ExcuseType.java`
  - `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/ExcuseTicketStatus.java`

**`services/attendance-service/attendance-app/`:**
- Purpose: Spring Boot application implementing the Attendance Service (MongoDB)
- Key files:
  - `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/AttendanceApplication.java`
  - `services/attendance-service/attendance-app/src/main/resources/application.yml`

**`services/notification-web/`:**
- Purpose: WebSocket push notification service
- Key files:
  - `services/notification-web/src/main/java/ru/rutcampustrack/notification/NotificationWebApplication.java`
  - `services/notification-web/src/main/resources/application.yml`

**`services/notification-bot/`:**
- Purpose: Python Telegram bot (not a Gradle module)
- Key files:
  - `services/notification-bot/requirements.txt` -- Aiogram 3.15, aio-pika, gRPC
  - `services/notification-bot/.env.example` -- environment config template (existence noted only)

**`frontends/`:**
- Purpose: Placeholder for frontend applications (React mini-app, Angular web-panel, landing page)
- Status: Empty -- no code yet

## Module Dependency Graph

**Gradle Modules (from `settings.gradle.kts`):**

```
Root (rutcampustrack)
├── services:api-gateway                          [Spring Boot]
├── services:auth-service                         [Spring Boot]
├── services:academic-service:academic-api-contract   [java-library]
├── services:academic-service:academic-app            [Spring Boot] -> depends on academic-api-contract
├── services:schedule-service:schedule-api-contract   [java-library]
├── services:schedule-service:schedule-app            [Spring Boot] -> depends on schedule-api-contract
├── services:attendance-service:attendance-api-contract [java-library]
├── services:attendance-service:attendance-app         [Spring Boot] -> depends on attendance-api-contract
└── services:notification-web                         [Spring Boot]
```

**Contract vs Implementation:**
- `*-api-contract` modules use the `java-library` Gradle plugin -- they produce a JAR with DTOs, enums, endpoint interfaces, and exception classes. NO Spring Boot, NO Lombok.
- `*-app` modules use the `org.springframework.boot` plugin -- they depend on their contract module via `implementation(project(":services:{service}:{service}-api-contract"))`.
- Contract modules expose dependencies via `api(...)` scope so consumers get transitive access to validation API, Spring Web annotations, HATEOAS, Swagger annotations, and Jackson annotations.

**Inter-Service Dependencies (runtime only, not compile-time):**
- API Gateway -> all services (HTTP routing)
- Schedule Service -> Academic Service (gRPC: group info, semester, subjects)
- Attendance Service -> Academic Service (gRPC: group members, headman check)
- Attendance Service -> Schedule Service (gRPC: lesson info)
- Notification Web -> all services (RabbitMQ consumer)
- Notification Bot -> Academic Service (gRPC), Schedule Service (gRPC), RabbitMQ (consumer)

## Configuration Files

**Root-Level Build:**
- `build.gradle.kts` -- plugins (Spring Boot 3.4.1, Spring dependency management 1.1.7), Java 21, UTF-8, JUnit Platform
- `settings.gradle.kts` -- all 9 Gradle subproject includes

**Per-Service Build:**
- Each `build.gradle.kts` in `services/*/` or `services/*/*/`

**Application Config:**
- Each service has `src/main/resources/application.yml`
- No profile-specific configs detected (no `application-dev.yml`, `application-prod.yml`)
- Environment variables with defaults used for secrets: `${VARIABLE_NAME:default_value}`

**Infrastructure:**
- `docker-compose.yml` -- all infrastructure containers (PostgreSQL x2, MongoDB, Redis, RabbitMQ)
- Docker network: `private_net` (bridge)
- Named volumes for data persistence

**Key Environment Variables:**
- `POSTGRES_ACADEMIC_PASSWORD` (default: `rct_dev_pass`)
- `POSTGRES_SCHEDULE_PASSWORD` (default: `rct_dev_pass`)
- `RABBITMQ_USER` (default: `rct_user`)
- `RABBITMQ_PASSWORD` (default: `rct_dev_pass`)

## Key Entry Points

**Main Application Classes:**

| Service | Class | Package |
|---------|-------|---------|
| API Gateway | `GatewayApplication` | `ru.rutcampustrack.gateway` |
| Auth Service | `AuthApplication` | `ru.rutcampustrack.auth` |
| Academic Service | `AcademicApplication` | `ru.rutcampustrack.academic` |
| Schedule Service | `ScheduleApplication` | `ru.rutcampustrack.schedule` |
| Attendance Service | `AttendanceApplication` | `ru.rutcampustrack.attendance` |
| Notification Web | `NotificationWebApplication` | `ru.rutcampustrack.notification` |

**Important Configuration Classes:**
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/LowercaseEnumConverter.java` -- abstract base for all enum converters
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/EnumConverters.java` -- concrete converters for UserRole, AccountStatus, SubjectType
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/EnumConverters.java` -- concrete converters for WeekType, LessonStatus

## Naming Conventions

**Files:**
- Java classes: PascalCase (e.g., `LowercaseEnumConverter.java`, `ErrorResponse.java`)
- SQL migrations: `V{N}__description.sql` (Flyway convention)
- Proto files: `{service}.proto` (e.g., `academic.proto`)
- Event schemas: `{domain}.{action}.json` (e.g., `lesson.started.json`)

**Packages:**
- Contract modules: `ru.rutcampustrack.{service}.contract.{subpackage}`
- App modules: `ru.rutcampustrack.{service}.{subpackage}`
- gRPC generated: `ru.rutcampustrack.{service}.grpc`

**REST Paths:**
- External (via Gateway): `/api/{service}/{resource}`
- Internal (within service): `/{resource}`

## Where to Add New Code

**New Feature in an Existing Service (e.g., Academic):**
- Contract DTOs/interfaces: `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/`
  - Request DTOs (records): `contract/dto/` (create if not exists)
  - Response DTOs (classes extending RepresentationModel): `contract/dto/`
  - Endpoint interfaces: `contract/endpoints/` (create if not exists)
  - Enums: `contract/enums/`
  - Exceptions: `contract/exception/`
  - Validation: `contract/validation/` (create if not exists)
- Service implementation: `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/`
  - Controllers (implement contract interfaces): create per-resource package
  - Services: create per-resource package
  - Repositories (JPA): create per-resource package
  - Entities: create per-resource package
  - Config: `config/`
- Database migrations: `services/academic-service/academic-app/src/main/resources/db/migration/V{N}__description.sql`

**New gRPC Contract:**
- Proto definition: `proto/{service}.proto`
- Generated code: configure in service's `build.gradle.kts`

**New RabbitMQ Event:**
- JSON Schema: `event-schemas/{domain}.{action}.json`
- Follow existing envelope structure: `event_type`, `event_id`, `occurred_at`, `payload`

**New Enum Converter (PostgreSQL service):**
1. Define enum in `*-api-contract` module under `contract/enums/`
2. Create concrete converter in `*-app` module under `config/EnumConverters.java` extending `LowercaseEnumConverter<E>`
3. Use `@Converter(autoApply = true)`

**Attendance Service Internal Domains:**
- Check-in domain: `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/`
- Report domain: `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/`
- Shared port: `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/`
- Rule: `report/` NEVER imports from `checkin/` directly; use `AttendanceReadPort` interface

**Tests:**
- Place alongside source in `src/test/java/` mirroring the main package structure
- Framework: JUnit 5 (JUnit Platform configured in root `build.gradle.kts`)

## Special Directories

**`build/`:**
- Purpose: Gradle build output
- Generated: Yes
- Committed: No (in `.gitignore`)

**`.gradle/`:**
- Purpose: Gradle cache
- Generated: Yes
- Committed: No (in `.gitignore`)

**`.planning/`:**
- Purpose: GSD planning documents
- Generated: By tooling
- Committed: Check project convention

**`.claude/`:**
- Purpose: Claude Code project config
- Generated: By Claude Code
- Committed: Check project convention

---

*Structure analysis: 2026-03-28*

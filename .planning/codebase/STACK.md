# Technology Stack

**Analysis Date:** 2026-03-28

## Languages

**Primary:**
- Java 21 - All backend services (gateway, auth, academic, schedule, attendance, notification-web)
  - Compiler flag `-parameters` enabled for reflection-based frameworks
  - Source/target compatibility: Java 21

**Secondary:**
- Python (version unspecified) - Notification Bot (`services/notification-bot/`)
  - Async stack: aiogram 3.15.0, aio-pika 9.5.3, aiohttp 3.11.11

## Runtime

**Environment:**
- JVM 21 (Microsoft JDK `ms-21.0.9` used in development)
- Python async runtime for notification-bot

**Package Manager:**
- Gradle 8.12 (wrapper: `gradle/wrapper/gradle-wrapper.properties`)
- Lockfile: Not present (no `gradle.lockfile`)
- pip/requirements.txt for Python bot (`services/notification-bot/requirements.txt`)

## Frameworks

**Core:**
- Spring Boot 3.4.1 - All Java services (`build.gradle.kts` root plugin)
- Spring Cloud Gateway (Spring Cloud 2024.0.0) - API Gateway (`services/api-gateway/build.gradle.kts`)
- Spring Data JPA - Academic Service, Schedule Service
- Spring Data MongoDB - Attendance Service
- Spring Data Redis - Auth Service, Academic Service
- Spring HATEOAS - Academic, Schedule, Attendance services
- Spring AMQP - Academic, Schedule, Attendance, Notification-Web services
- Spring WebSocket - Notification-Web (`services/notification-web/build.gradle.kts`)
- Spring Security - Auth Service (`services/auth-service/build.gradle.kts`)
- Spring Boot Actuator - API Gateway

**Python Bot:**
- Aiogram 3.15.0 - Telegram bot framework (`services/notification-bot/requirements.txt`)
- aio-pika 9.5.3 - RabbitMQ async client
- Pydantic 2.10.4 + pydantic-settings 2.7.1 - Data validation and configuration
- gRPCio 1.69.0 + grpcio-tools 1.69.0 - Inter-service communication

**Testing:**
- JUnit 5 (JUnit Platform) - All Java services via `spring-boot-starter-test`
- Test runner configured in root `build.gradle.kts`: `useJUnitPlatform()`

**Build/Dev:**
- Gradle 8.12 with Kotlin DSL (`build.gradle.kts`)
- Spring Dependency Management Plugin 1.1.7
- Docker Compose 3.9 for infrastructure (`docker-compose.yml`)

## Key Dependencies

**Critical:**
- `io.jsonwebtoken:jjwt-api:0.12.6` - JWT creation and validation (Auth Service + Gateway)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql` - Database migrations (Academic, Schedule)
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0` - API documentation (all business services)
- `org.projectlombok:lombok` - Code generation in `*-app` modules only (NOT in `*-api-contract`)

**API Contract Dependencies (pure Java, no Spring Boot):**
- `jakarta.validation:jakarta.validation-api:3.1.0` - Bean validation annotations
- `org.springframework:spring-web:6.2.1` - Web annotations (`@RequestMapping`, etc.)
- `org.springframework.hateoas:spring-hateoas:2.4.1` - `RepresentationModel`, `EntityModel`
- `io.swagger.core.v3:swagger-annotations-jakarta:2.2.22` - OpenAPI annotations
- `com.fasterxml.jackson.core:jackson-annotations:2.18.2` - JSON serialization annotations

**Infrastructure:**
- `org.postgresql:postgresql` - JDBC driver (runtime dependency)
- `org.springframework.boot:spring-boot-starter-data-mongodb` - MongoDB driver
- `org.springframework.boot:spring-boot-starter-data-redis` - Redis client (Lettuce)
- `org.springframework.boot:spring-boot-starter-amqp` - RabbitMQ client

**Proto/gRPC (Python bot + planned Java):**
- `protobuf:5.29.3` - Protocol Buffers (Python)
- gRPC Java starter commented out in `services/academic-service/academic-app/build.gradle.kts`: `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE` (planned for Phase 2)

## Configuration

**Environment:**
- Spring profiles via `application.yml` per service
- Environment variable overrides with defaults: `${VAR_NAME:default_value}`
- `.env.example` present for notification-bot (`services/notification-bot/.env.example`)
- `.env` files exist but are NOT committed (gitignored)

**Key Configuration Files:**
- `services/api-gateway/src/main/resources/application.yml` - Gateway routes
- `services/auth-service/src/main/resources/application.yml` - JWT expiration settings
- `services/academic-service/academic-app/src/main/resources/application.yml` - DB, Redis, RabbitMQ, Flyway
- `services/schedule-service/schedule-app/src/main/resources/application.yml` - DB, RabbitMQ, Flyway
- `services/attendance-service/attendance-app/src/main/resources/application.yml` - MongoDB, RabbitMQ
- `services/notification-web/src/main/resources/application.yml` - RabbitMQ

**Build:**
- Root `build.gradle.kts` - Shared Java config, plugin versions
- `settings.gradle.kts` - Multi-module project definition (9 subprojects)
- Each service has its own `build.gradle.kts` with specific dependencies

## Data Stores

**PostgreSQL 16:**
- `academic_db` - Academic Service (users, groups, subjects, semesters)
- `schedule_db` - Schedule Service (schedule items, lessons)
- Migrations: Flyway (`src/main/resources/db/migration/`)
- Hibernate: `ddl-auto: validate` (schema managed by Flyway, not Hibernate)
- `open-in-view: false` (no lazy loading in views)

**MongoDB 7:**
- `attendance_db` - Attendance Service (check-ins, records, excuse tickets)
- Connection: direct URI without auth in dev

**Redis 7 (Alpine):**
- Auth Service: Session/token storage
- Academic Service: Caching layer

**RabbitMQ 3.13 (Management Alpine):**
- Async event bus between all services
- Management UI exposed on port 15672 (dev only)
- Consumers: Academic, Schedule, Attendance, Notification-Web, Notification-Bot

## Platform Requirements

**Development:**
- JDK 21 (Microsoft build confirmed)
- Docker + Docker Compose for infrastructure
- Gradle 8.12 (wrapper included)
- Python 3.x for notification-bot

**Production:**
- 5 Java services + 1 Python service + 5 infrastructure containers
- Minimum: PostgreSQL 16, MongoDB 7, Redis 7, RabbitMQ 3.13
- All services communicate over Docker `private_net` bridge network

## Port Assignments

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Auth Service | 9090 |
| Academic Service | 9091 |
| Schedule Service | 9092 |
| Attendance Service | 9093 |
| Notification-Web | 9094 |
| RabbitMQ Management | 15672 |

---

*Stack analysis: 2026-03-28*

# Codebase Structure

**Analysis Date:** 2026-04-08

## Directory Layout

```
rutcampustrack/
├── .planning/
│   ├── codebase/                    # GSD codebase analysis docs (this directory)
│   │   └── *.md
│   ├── ROADMAP.md                   # v1-v9 releases, phase breakdown
│   ├── PROJECT.md                   # detailed project spec
│   └── v9.0-BRIEF.md               # current phase status
├── .github/
│   └── workflows/                   # CI/CD pipelines
├── build.gradle.kts                 # Root Gradle build config
├── settings.gradle.kts              # Gradle subproject definitions (see below)
├── docker-compose.yml               # Local dev infra: PostgreSQL×2, MongoDB, Redis, RabbitMQ
├── gradle.properties                # Gradle version / Java version
├── gradlew & gradlew.bat            # Gradle wrapper
├── proto/
│   ├── academic.proto               # Academic Service gRPC contract (GetGroup, GetUserById, etc.)
│   └── schedule.proto               # Schedule Service gRPC contract
├── event-schemas/
│   ├── attendance.marked.json        # Schema for attendance.marked event
│   ├── lesson.started.json          # Schema for lesson.started event
│   ├── lesson.closed.json           # Schema for lesson.closed event
│   ├── lesson.cancelled.json        # Schema for lesson.cancelled event
│   ├── excuse.requested.json        # Schema for excuse.requested event
│   ├── homework.published.json      # Schema for homework.published event
│   ├── homework.updated.json        # Schema for homework.updated event
│   ├── late_checkin.requested.json  # Schema for late_checkin.requested event
│   ├── group.updated.json           # Schema for group.updated event
│   └── semester.archived.json       # Schema for semester.archived event
├── docs/
│   ├── phase-0-report.md            # Phase 0: Context & requirements
│   ├── phase-1-report.md            # Phase reports (one per completed phase)
│   ├── architecture.md              # Detailed system architecture
│   ├── job-stories.md               # All user stories & job stories
│   ├── database-schema.md           # PostgreSQL/MongoDB schemas
│   ├── design-decisions.md          # UI/UX guidelines, branding
│   └── skills-inventory.md          # Claude Code skills installed
├── nginx/
│   ├── conf.d/
│   │   └── default.conf             # Production HTTPS config, SSL termination, reverse proxy routing
│   ├── nginx.conf                   # Main nginx config (includes conf.d/)
│   └── scripts/
│       └── entrypoint.sh            # Auto-generate dhparam.pem, run certbot on first deploy
├── services/
│   ├── api-gateway/                 # Spring Cloud Gateway
│   │   ├── build.gradle.kts
│   │   ├── src/main/java/ru/rutcampustrack/gateway/
│   │   │   ├── GatewayApplication.java
│   │   │   ├── config/
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   └── PublicKeyConfig.java         # Cache public key from Auth Service
│   │   │   └── filter/
│   │   │       └── JwtAuthenticationFilter.java # Global filter: validate JWT, inject headers
│   │   └── src/test/java/...
│   ├── auth-service/
│   │   ├── build.gradle.kts
│   │   └── src/main/java/ru/rutcampustrack/auth/
│   │       ├── AuthApplication.java
│   │       ├── user/
│   │       │   ├── UserController.java           # REST: /api/auth/login, /refresh, /public-key
│   │       │   ├── UserService.java
│   │       │   └── UserRepository.java
│   │       ├── otp/
│   │       │   ├── OtpService.java               # One-time password generation
│   │       │   └── OtpRepository.java
│   │       ├── jwt/
│   │       │   ├── JwtProvider.java              # Sign tokens with private key
│   │       │   └── JwtConfig.java
│   │       ├── config/
│   │       │   ├── RabbitConfig.java
│   │       │   ├── RedisConfig.java
│   │       │   └── SecurityConfig.java
│   │       └── entity/
│   │           └── User.java                     # @Entity for auth DB
│   ├── academic-service/
│   │   ├── academic-api-contract/               # Pure library module (no Spring Boot)
│   │   │   ├── build.gradle.kts                 # java-library plugin, NO spring-boot plugin
│   │   │   └── src/main/java/ru/rutcampustrack/academic/contract/
│   │   │       ├── api/
│   │   │       │   ├── HomeworkApi.java         # REST contract interface
│   │   │       │   ├── UserApi.java
│   │   │       │   ├── GroupApi.java
│   │   │       │   ├── SemesterApi.java
│   │   │       │   ├── SubjectApi.java
│   │   │       │   ├── AssignmentApi.java
│   │   │       │   ├── AssistantApi.java
│   │   │       │   ├── DashboardApi.java
│   │   │       │   └── ThresholdApi.java
│   │   │       ├── dto/
│   │   │       │   ├── homework/
│   │   │       │   │   ├── CreateHomeworkRequest.java  # record
│   │   │       │   │   ├── UpdateHomeworkRequest.java
│   │   │       │   │   └── HomeworkResponse.java       # class with HATEOAS
│   │   │       │   ├── assignment/
│   │   │       │   ├── user/
│   │   │       │   └── ... (other domains)
│   │   │       ├── enums/
│   │   │       │   ├── UserRole.java            # ADMIN, TEACHER, STUDENT (UPPER_CASE)
│   │   │       │   ├── UserStatus.java
│   │   │       │   └── SubjectType.java
│   │   │       └── exception/
│   │   │           └── ResourceNotFoundException.java
│   │   └── academic-app/                        # Spring Boot app module
│   │       ├── build.gradle.kts                 # org.springframework.boot plugin
│   │       ├── src/main/java/ru/rutcampustrack/academic/
│   │       │   ├── AcademicApplication.java
│   │       │   ├── assignment/
│   │       │   │   ├── AssignmentController.java        # implements AssignmentApi
│   │       │   │   ├── AssignmentService.java
│   │       │   │   ├── AssignmentAssembler.java         # Entity → EntityModel<DTO>
│   │       │   │   └── ... (repositories, etc.)
│   │       │   ├── homework/
│   │       │   │   ├── HomeworkController.java
│   │       │   │   ├── HomeworkService.java
│   │       │   │   ├── HomeworkAssembler.java
│   │       │   │   └── ...
│   │       │   ├── group/
│   │       │   ├── user/
│   │       │   ├── semester/
│   │       │   ├── subject/
│   │       │   ├── threshold/
│   │       │   ├── assistant/
│   │       │   ├── dashboard/
│   │       │   ├── entity/
│   │       │   │   ├── User.java                 # @Entity for academic_db
│   │       │   │   ├── Group.java
│   │       │   │   ├── Homework.java
│   │       │   │   ├── Semester.java
│   │       │   │   ├── Subject.java
│   │       │   │   ├── TeacherSubjectGroup.java
│   │       │   │   ├── AttendanceThreshold.java
│   │       │   │   ├── HeadmanAssistant.java
│   │       │   │   ├── StudentGroupHistory.java
│   │       │   │   └── CampusSetting.java
│   │       │   ├── repository/
│   │       │   │   ├── HomeworkRepository.java   # Spring Data JPA
│   │       │   │   ├── UserRepository.java
│   │       │   │   ├── GroupRepository.java
│   │       │   │   └── ...
│   │       │   ├── grpc/
│   │       │   │   └── AcademicReadService.java  # @GrpcService implementation
│   │       │   ├── event/
│   │       │   │   └── AcademicEventListener.java # Consumes RabbitMQ events
│   │       │   ├── config/
│   │       │   │   ├── RabbitConfig.java          # RabbitMQ setup, fanout exchange
│   │       │   │   ├── RedisConfig.java           # Redis cache setup
│   │       │   │   ├── SecurityConfig.java        # Spring Security (OAuth2 resource server)
│   │       │   │   ├── GrpcConfig.java            # gRPC server config
│   │       │   │   └── AsyncConfig.java           # Thread pools for async tasks
│   │       │   ├── security/
│   │       │   │   ├── RequireRole.java           # @RequireRole annotation
│   │       │   │   ├── RequireRoleAspect.java     # AOP aspect for role checks
│   │       │   │   ├── RequestContext.java        # Holds current user info (thread-local)
│   │       │   │   └── GlobalExceptionHandler.java # @ControllerAdvice
│   │       │   ├── exception/
│   │       │   │   ├── AccessDeniedException.java
│   │       │   │   ├── BadRequestException.java
│   │       │   │   ├── ConflictException.java
│   │       │   │   └── ... (other domain exceptions)
│   │       │   └── converter/
│   │       │       └── LowercaseEnumConverter.java # Attribute converter for enum ↔ DB string
│   │       ├── src/main/resources/
│   │       │   ├── application.yml                 # Spring config: port 9091, DB, Redis, RabbitMQ
│   │       │   ├── application-prod.yml            # Production overrides
│   │       │   └── db/migration/
│   │       │       ├── V1__baseline.sql            # Initial schema
│   │       │       ├── V2__seed_test_data.sql      # Test data (students, groups, etc.)
│   │       │       ├── V3__login_sequences.sql     # Sequences for login generation
│   │       │       ├── V4__campus_settings_bigserial.sql
│   │       │       └── ... (other Flyway migrations)
│   │       └── src/test/java/ru/rutcampustrack/academic/
│   │           ├── integration/
│   │           │   ├── AbstractAcademicIntegrationTest.java  # Base class: Testcontainers PostgreSQL
│   │           │   ├── RestApiIntegrationTest.java           # Test REST endpoints
│   │           │   ├── EventIntegrationTest.java             # Test RabbitMQ event listeners
│   │           │   ├── CacheIntegrationTest.java             # Test Redis caching
│   │           │   ├── AcademicGrpcIntegrationTest.java      # Test gRPC endpoints
│   │           │   └── ... (other integration tests)
│   │           └── unit/
│   │               ├── HomeworkServiceTest.java
│   │               ├── ... (unit tests for services)
│   ├── schedule-service/
│   │   ├── schedule-api-contract/
│   │   │   └── src/main/java/ru/rutcampustrack/schedule/contract/
│   │   │       ├── api/
│   │   │       │   └── LessonApi.java             # REST contract
│   │   │       ├── dto/
│   │   │       ├── enums/
│   │   │       └── exception/
│   │   └── schedule-app/
│   │       ├── src/main/java/ru/rutcampustrack/schedule/
│   │       │   ├── ScheduleApplication.java
│   │       │   ├── lesson/
│   │       │   │   ├── LessonController.java      # implements LessonApi
│   │       │   │   ├── LessonService.java
│   │       │   │   ├── LessonAssembler.java
│   │       │   │   └── ...
│   │       │   ├── grpc/
│   │       │   ├── event/
│   │       │   ├── config/
│   │       │   ├── entity/
│   │       │   └── repository/
│   │       └── src/main/resources/
│   │           ├── application.yml                # Port 9092
│   │           └── db/migration/
│   │               └── V*.sql
│   ├── attendance-service/
│   │   ├── attendance-api-contract/
│   │   │   └── src/main/java/ru/rutcampustrack/attendance/contract/
│   │   │       ├── api/
│   │   │       │   ├── CheckinApi.java
│   │   │       │   └── ReportApi.java
│   │   │       ├── dto/
│   │   │       └── enums/
│   │   └── attendance-app/
│   │       ├── src/main/java/ru/rutcampustrack/attendance/
│   │       │   ├── AttendanceApplication.java
│   │       │   ├── checkin/                       # Domain: mark attendance, geo-validation
│   │       │   │   ├── CheckinController.java     # implements CheckinApi
│   │       │   │   ├── CheckinService.java
│   │       │   │   ├── AttendanceDocument.java    # MongoDB document (not JPA @Entity)
│   │       │   │   ├── CheckinRepository.java     # MongoRepository
│   │       │   │   └── AttendanceReadPortImpl.java # Implements shared port
│   │       │   ├── report/                        # Domain: statistics, queries (ISOLATED)
│   │       │   │   ├── ReportController.java      # implements ReportApi
│   │       │   │   ├── ReportService.java         # Uses AttendanceReadPort (NOT direct imports from checkin/)
│   │       │   │   └── ... (repositories for report queries)
│   │       │   ├── shared/
│   │       │   │   ├── port/
│   │       │   │   │   ├── AttendanceReadPort.java     # Interface (zero domain imports)
│   │       │   │   │   └── AttendanceRecord.java       # DTO for port
│   │       │   │   └── ... (shared utilities)
│   │       │   ├── marking/                       # Business logic: auto-mark absent, apply excuses
│   │       │   ├── ratelimit/                     # Prevent spam checkins
│   │       │   ├── geofence/                      # Geo validation logic
│   │       │   ├── semester/                      # Semester lifecycle listener
│   │       │   ├── event/
│   │       │   │   └── AttendanceEventPublisher.java # Publishes attendance.marked
│   │       │   ├── grpc/
│   │       │   ├── config/
│   │       │   ├── exception/
│   │       │   └── security/
│   │       └── src/main/resources/
│   │           ├── application.yml                # Port 9093, MongoDB connection
│   │           └── db/migration/                  # Flyway not used (MongoDB schema-less)
│   └── notification-service/
│       ├── notification-api-contract/
│       │   └── src/main/java/ru/rutcampustrack/notification/contract/
│       │       ├── api/
│       │       │   └── PushApi.java               # REST contract for Web Push
│       │       ├── dto/
│       │       │   └── push/
│       │       │       ├── SubscribeRequest.java
│       │       │       ├── UnsubscribeRequest.java
│       │       │       └── VapidPublicKeyResponse.java
│       │       └── enums/
│       └── notification-app/
│           ├── src/main/java/ru/rutcampustrack/notification/
│           │   ├── NotificationWebApplication.java
│           │   ├── config/
│           │   │   ├── WebSocketConfig.java       # STOMP/SockJS endpoint config, routing, interceptors
│           │   │   ├── JwtHandshakeInterceptor.java # Validate JWT at WebSocket upgrade
│           │   │   ├── SubscriptionAuthInterceptor.java # Validate STOMP subscription destinations
│           │   │   ├── WebPushConfig.java         # VAPID key setup, PushService bean
│           │   │   ├── RabbitConfig.java          # Event listeners from fanout exchange
│           │   │   ├── AsyncConfig.java           # Thread pools for async push delivery
│           │   │   └── PushMongoConfig.java       # MongoDB connection for subscriptions
│           │   ├── push/
│           │   │   ├── PushController.java        # Implements PushApi: subscribe, unsubscribe, get public key
│           │   │   ├── PushService.java           # Business logic for subscriptions
│           │   │   ├── PushDocument.java          # MongoDB document: push subscription
│           │   │   ├── PushRepository.java        # MongoRepository
│           │   │   ├── PushDeliveryService.java   # Sends actual Web Push via VAPID
│           │   │   └── ... (related services)
│           │   ├── event/
│           │   │   └── EventListener.java         # @RabbitListener for all events, publishes to WebSocket topics
│           │   ├── security/
│           │   │   ├── JwtTokenProvider.java
│           │   │   └── RequestContext.java
│           │   ├── exception/
│           │   └── HealthCheckController.java     # / endpoint for liveness probe
│           └── src/main/resources/
│               ├── application.yml                # Port 9094, MongoDB, RabbitMQ, VAPID keys
│               └── no db/migration (MongoDB)
├── frontends/
│   ├── landing/                                   # Static HTML + CSS landing page
│   │   ├── package.json
│   │   ├── src/
│   │   │   ├── index.html
│   │   │   ├── styles.css
│   │   │   └── ... (static assets)
│   │   └── docker/
│   │       └── Dockerfile                         # nginx container for serving static files
│   ├── web-panel/                                 # Angular admin panel
│   │   ├── package.json
│   │   ├── angular.json
│   │   ├── src/
│   │   │   ├── main.ts
│   │   │   ├── app/
│   │   │   │   ├── app.component.ts
│   │   │   │   ├── modules/
│   │   │   │   │   ├── auth/
│   │   │   │   │   ├── dashboard/
│   │   │   │   │   ├── users/
│   │   │   │   │   ├── groups/
│   │   │   │   │   └── ... (feature modules)
│   │   │   │   ├── services/
│   │   │   │   ├── shared/
│   │   │   │   └── guards/
│   │   │   ├── styles.scss
│   │   │   └── index.html
│   │   ├── docker/
│   │   │   └── Dockerfile                         # Build: ng build --configuration production --base-href /admin/
│   │   └── .angular.json
│   ├── mini-app/                                  # React Telegram Mini App
│   │   ├── package.json
│   │   ├── vite.config.ts                         # Vite build config
│   │   ├── src/
│   │   │   ├── main.tsx
│   │   │   ├── App.tsx                            # Root component
│   │   │   ├── features/
│   │   │   │   ├── auth/                          # Login feature (Telegram-specific)
│   │   │   │   ├── checkin/                       # Geo-checkin feature
│   │   │   │   ├── schedule/                      # View schedule
│   │   │   │   ├── stats/                         # Attendance stats
│   │   │   │   ├── homework/                      # View & submit homework
│   │   │   │   └── __tests__/                     # Feature tests
│   │   │   ├── components/
│   │   │   │   ├── ui/                            # Reusable UI components
│   │   │   │   └── __tests__/
│   │   │   ├── shared/
│   │   │   │   ├── hooks/                         # Custom React hooks (useAuth, useAttendance)
│   │   │   │   ├── lib/                           # Utilities (apiClient, formatters)
│   │   │   │   ├── providers/                     # Context providers (AuthProvider, ThemeProvider)
│   │   │   │   ├── components/                    # Shared components
│   │   │   │   └── __tests__/
│   │   │   ├── lib/
│   │   │   │   ├── apiClient.ts                   # Axios instance for /api/* calls via gateway
│   │   │   │   ├── formatters.ts
│   │   │   │   └── validators.ts
│   │   │   ├── styles/
│   │   │   │   ├── index.css
│   │   │   │   └── theme.css
│   │   │   ├── test/
│   │   │   │   └── setup.ts
│   │   │   └── vite-env.d.ts
│   │   └── docker/
│   │       └── Dockerfile                         # Build: npm run build; serve with nginx
│   └── pwa/                                       # React PWA (RutTrack mobile app)
│       ├── package.json
│       ├── vite.config.ts                         # Vite config
│       ├── src/
│       │   ├── main.tsx
│       │   ├── sw.ts                              # Service Worker (Web Push, offline cache)
│       │   ├── App.tsx                            # Root component
│       │   ├── features/
│       │   │   ├── auth/                          # Login, refresh token, logout
│       │   │   ├── checkin/                       # Geo-checkin with live map, real-time status
│       │   │   ├── home/                          # Dashboard: schedule, stats, quick access
│       │   │   ├── schedule/                      # Week/month schedule view
│       │   │   ├── profile/                       # User profile, settings
│       │   │   ├── push/                          # Web Push subscription management
│       │   │   ├── notification/                  # Real-time notifications via STOMP
│       │   │   └── __tests__/
│       │   ├── components/
│       │   │   ├── ui/                            # Material-UI based (button, card, modal, etc.)
│       │   │   └── __tests__/
│       │   ├── shared/
│       │   │   ├── hooks/
│       │   │   │   ├── useAuth.ts                 # Auth state (JWT handling)
│       │   │   │   ├── useWebSocket.ts            # STOMP subscription wrapper
│       │   │   │   ├── usePushNotifications.ts    # Web Push subscription
│       │   │   │   └── useGeolocation.ts          # Browser geolocation
│       │   │   ├── lib/
│       │   │   │   ├── apiClient.ts               # Axios + auto-refresh logic
│       │   │   │   ├── stompClient.ts             # SockJS + STOMP wrapper
│       │   │   │   ├── formatters.ts
│       │   │   │   └── validators.ts
│       │   │   ├── providers/
│       │   │   │   ├── AuthProvider.tsx           # JWT + refresh token context
│       │   │   │   ├── ThemeProvider.tsx          # Dark/light mode
│       │   │   │   ├── NotificationProvider.tsx   # STOMP connection state
│       │   │   │   └── PushProvider.tsx           # Web Push state
│       │   │   └── components/
│       │   ├── lib/
│       │   ├── styles/
│       │   │   └── index.css
│       │   ├── test/
│       │   └── vite-env.d.ts
│       ├── public/
│       │   ├── manifest.json                      # PWA manifest (name, icons, start_url, display)
│       │   └── icons/                             # Icon assets (192x192, 512x512)
│       └── docker/
│           └── Dockerfile
└── README.md                                       # Project overview (or see CLAUDE.md)
```

## Directory Purposes

**`.planning/codebase/`**
- Purpose: Analysis documents auto-generated by GSD mapper
- Contains: STACK.md, INTEGRATIONS.md, ARCHITECTURE.md, STRUCTURE.md, CONVENTIONS.md, TESTING.md, CONCERNS.md
- Key files: ARCHITECTURE.md (this file's sibling — cross-referenced by phase planners/executors)

**`services/{name}/{name}-api-contract/`**
- Purpose: Pure contract library (no Spring Boot runtime)
- Contains: REST API interfaces, DTOs, enums, exceptions
- Key files: `contract/api/*.java` (interface definitions), `contract/dto/` (records + classes), `contract/enums/` (UserRole, etc.)
- Build: `java-library` plugin (produces JAR with no Spring Framework)
- Dependency: `{name}-app` depends on this; other services may also depend (e.g., notification-api-contract used by notification-app)

**`services/{name}/{name}-app/`**
- Purpose: Spring Boot application (controllers, services, repositories)
- Contains: Controllers, services, repositories, entities, configurations, Flyway migrations
- Key files: `{Name}Application.java` (entry point), `config/*.java` (Spring beans), `entity/` (JPA entities, Flyway triggers)
- Build: `org.springframework.boot` plugin

**`services/{name}/{name}-app/src/main/resources/db/migration/`**
- Purpose: Flyway SQL migrations (version control for schema)
- Naming: `V{N}__{description}.sql` (e.g., `V1__baseline.sql`)
- Execution: Automatic on app startup (Flyway bean in Spring Boot)
- Key files: `V1__baseline.sql` (create schema), `V2__seed_test_data.sql` (test data)
- Not used: MongoDB services (schema-less)

**`proto/`**
- Purpose: Protocol Buffer definitions for gRPC
- Contains: `.proto` files defining services and messages
- Key files: `academic.proto` (GetGroup, GetGroupMembers, GetUserById, etc.), `schedule.proto`
- Usage: Compiled to Java stubs, imported by microservices as gRPC clients/servers

**`event-schemas/`**
- Purpose: JSON Schema definitions for domain events
- Contains: Event envelope schemas (event_type, event_id, occurred_at, payload)
- Key files: `attendance.marked.json`, `lesson.started.json`, `lesson.closed.json`, `homework.published.json`, etc.
- Usage: Documentation + validation for RabbitMQ messages

**`nginx/`**
- Purpose: Production reverse proxy, HTTPS termination, static file serving
- Key files: `conf.d/default.conf` (routing rules, SSL certs, security headers), `scripts/entrypoint.sh` (dhparam generation, certbot setup)
- Locations: `/api/*` → API Gateway (8080), `/admin/*` → Web Panel, `/mini-app/*` → Mini App, `/landing/*` → Landing, `/` → PWA

**`docs/`**
- Purpose: Project documentation (not auto-generated)
- Key files:
  - `architecture.md` — detailed system design
  - `job-stories.md` — all user/job stories
  - `database-schema.md` — schema diagrams, relationships
  - `design-decisions.md` — UI/UX guidelines, branding, design rationale
  - `phase-{N}-report.md` — completion report for phase N

**`frontends/{name}/src/`**
- Purpose: React (mini-app, pwa) or Angular (web-panel) source code
- Structure (React):
  - `features/` — Feature modules (auth, checkin, schedule, etc.)
  - `shared/` — Reusable hooks, providers, components, utilities
  - `components/` — Generic UI components (separate from feature-specific ones)
  - `lib/` — Client library setup (apiClient, stompClient)
  - `test/` — Test setup, mocks, helpers
- Testing: `src/**/__tests__/*.test.ts` (co-located with feature/component)

## Key File Locations

**Entry Points:**

- **API Gateway:** `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/GatewayApplication.java`
- **Auth Service:** `services/auth-service/src/main/java/ru/rutcampustrack/auth/AuthApplication.java` (port 9090)
- **Academic Service:** `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/AcademicApplication.java` (port 9091)
- **Schedule Service:** `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/ScheduleApplication.java` (port 9092)
- **Attendance Service:** `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/AttendanceApplication.java` (port 9093)
- **Notification Web:** `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/NotificationWebApplication.java` (port 9094)

**Configuration:**

- **API Gateway routing:** `services/api-gateway/src/main/resources/application.yml` (routes to microservices)
- **Auth Service JWT:** `services/auth-service/src/main/resources/application.yml` (JWT issuer config)
- **Microservice database:** `services/{name}/{name}-app/src/main/resources/application.yml` (DB connection, RabbitMQ, gRPC port)
- **Nginx production routes:** `nginx/conf.d/default.conf` (SSL, reverse proxy, security headers)

**Core Logic:**

- **JWT validation (Gateway):** `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java`
- **Role-based access control (AOP):** `services/{name}/{name}-app/src/main/java/ru/rutcampustrack/{name}/security/RequireRoleAspect.java` + `@RequireRole` annotation
- **Request context (user info):** `services/{name}/{name}-app/src/main/java/ru/rutcampustrack/{name}/security/RequestContext.java`
- **gRPC service implementations:** `services/{name}/{name}-app/src/main/java/ru/rutcampustrack/{name}/grpc/...` (marked with `@GrpcService`)
- **Event publishing (RabbitMQ):** `services/{name}/{name}-app/src/main/java/ru/rutcampustrack/{name}/event/{Domain}EventPublisher.java`
- **Event consuming (RabbitMQ):** `services/{name}/{name}-app/src/main/java/ru/rutcampustrack/{name}/event/EventListener.java` or `*EventConsumer.java` (marked with `@RabbitListener`)

**Testing:**

- **Integration test base:** `services/{name}/{name}-app/src/test/java/ru/rutcampustrack/{name}/integration/Abstract{Name}IntegrationTest.java` (Testcontainers setup)
- **REST API tests:** `services/{name}/{name}-app/src/test/java/ru/rutcampustrack/{name}/integration/RestApiIntegrationTest.java`
- **gRPC tests:** `services/{name}/{name}-app/src/test/java/ru/rutcampustrack/{name}/integration/{Name}GrpcIntegrationTest.java`
- **Event tests:** `services/{name}/{name}-app/src/test/java/ru/rutcampustrack/{name}/integration/EventIntegrationTest.java`

**Flyway Migrations:**

- **Location:** `services/{name}/{name}-app/src/main/resources/db/migration/`
- **Naming:** `V{N}__{description}.sql`
- **Examples:**
  - `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql` — Create tables
  - `services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql` — Insert test data
  - `services/schedule-service/schedule-app/src/main/resources/db/migration/` — Schedule schema

## Naming Conventions

**Files:**

| Pattern | Example | Location |
|---------|---------|----------|
| `{Name}Application.java` | `AcademicApplication.java` | `services/{name}/{name}-app/src/main/java/...` |
| `{Name}Api.java` | `HomeworkApi.java` | `services/{name}/{name}-api-contract/src/main/java/.../contract/api/` |
| `{Name}Controller.java` | `HomeworkController.java` | `services/{name}/{name}-app/src/main/java/.../{module}/` |
| `{Name}Service.java` | `HomeworkService.java` | `services/{name}/{name}-app/src/main/java/.../{module}/` |
| `{Name}Repository.java` | `HomeworkRepository.java` | `services/{name}/{name}-app/src/main/java/.../repository/` |
| `{Name}Entity.java` or just `{Name}.java` | `Homework.java` | `services/{name}/{name}-app/src/main/java/.../entity/` |
| `{Name}Assembler.java` | `HomeworkAssembler.java` | `services/{name}/{name}-app/src/main/java/.../{module}/` |
| `Create{Name}Request.java` | `CreateHomeworkRequest.java` | `services/{name}/{name}-api-contract/src/main/java/.../contract/dto/{module}/` |
| `{Name}Response.java` | `HomeworkResponse.java` | `services/{name}/{name}-api-contract/src/main/java/.../contract/dto/{module}/` |
| `V{N}__{description}.sql` | `V1__baseline.sql`, `V2__seed_test_data.sql` | `services/{name}/{name}-app/src/main/resources/db/migration/` |

**Directories:**

| Pattern | Example | Purpose |
|---------|---------|---------|
| `services/{name}/{name}-api-contract/` | `academic-api-contract/` | Contract library |
| `services/{name}/{name}-app/` | `academic-app/` | Spring Boot app |
| `src/main/java/ru/rutcampustrack/{service}/{module}/` | `ru/rutcampustrack/academic/homework/` | Feature module |
| `src/main/java/ru/rutcampustrack/{service}/config/` | `ru/rutcampustrack/academic/config/` | Spring configuration |
| `src/main/java/ru/rutcampustrack/{service}/entity/` | `ru/rutcampustrack/academic/entity/` | JPA entities |
| `src/main/java/ru/rutcampustrack/{service}/repository/` | `ru/rutcampustrack/academic/repository/` | Data access layer |
| `src/main/java/ru/rutcampustrack/{service}/security/` | `ru/rutcampustrack/academic/security/` | Auth/authz components |
| `src/main/java/ru/rutcampustrack/{service}/exception/` | `ru/rutcampustrack/academic/exception/` | Domain exceptions |
| `src/main/java/ru/rutcampustrack/{service}/event/` | `ru/rutcampustrack/academic/event/` | Event pub/sub |
| `src/main/java/ru/rutcampustrack/{service}/grpc/` | `ru/rutcampustrack/academic/grpc/` | gRPC services |
| `src/test/java/ru/rutcampustrack/{service}/integration/` | `ru/rutcampustrack/academic/integration/` | Integration tests |

**Package Names:**

- Java: `ru.rutcampustrack.{service}.{module}` (e.g., `ru.rutcampustrack.academic.homework`)
- gRPC: `ru.rutcampustrack.{service}.grpc` (proto package)
- REST paths: `/api/{service}/...` (e.g., `/api/academic/homeworks`, `/api/attendance/checkin`)
- Event types: `{domain}.{action}` (e.g., `attendance.marked`, `lesson.started`, `homework.published`)
- gRPC service names: `{Name}GrpcService` (e.g., `AcademicGrpcService`)

## Where to Add New Code

**New REST Endpoint:**
1. Define interface in `services/{name}/{name}-api-contract/src/main/java/.../contract/api/{Name}Api.java`
   - Add method with `@GetMapping`, `@PostMapping`, etc.
   - Add Swagger annotations: `@Operation`, `@ApiResponse`
2. Implement in `services/{name}/{name}-app/src/main/java/.../{module}/{Name}Controller.java`
   - Class implements `{Name}Api`
   - Inject service, assembler
3. Add corresponding DTO classes in `services/{name}/{name}-api-contract/src/main/java/.../contract/dto/{module}/`
   - Request = record, Response = class with HATEOAS
4. Write integration test in `services/{name}/{name}-app/src/test/java/.../integration/RestApiIntegrationTest.java`

**New Domain Entity:**
1. Create entity class in `services/{name}/{name}-app/src/main/java/.../entity/{Name}.java`
   - `@Entity`, `@Table`, JPA annotations
   - Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`
2. Create repository interface in `services/{name}/{name}-app/src/main/java/.../repository/{Name}Repository.java`
   - Extends `JpaRepository<{Name}, Long>`
   - Add custom query methods if needed
3. Create Flyway migration in `services/{name}/{name}-app/src/main/resources/db/migration/V{N}__{description}.sql`
   - Define table, columns, constraints
4. Update contract DTOs if this entity needs REST exposure

**New Microservice Module (e.g., new feature domain):**
1. Create directory: `services/{name}/{name}-app/src/main/java/ru/rutcampustrack/{name}/{module}/`
2. Add controller: `{Name}Controller.java` (implements contract interface)
3. Add service: `{Name}Service.java` (business logic, validates, orchestrates)
4. Add assembler: `{Name}Assembler.java` (Entity → HATEOAS response)
5. Add entity: `src/main/java/.../entity/{Name}.java`
6. Add repository: `src/main/java/.../repository/{Name}Repository.java`
7. Add contract interface + DTOs to `{name}-api-contract`

**New Shared Utility/Helper:**
- Location: `services/{name}/{name}-app/src/main/java/.../util/` or `.../shared/`
- Naming: `{Name}Utility.java`, `{Name}Helper.java`
- Access: Import by package, NOT by copy-pasting

**New RabbitMQ Event Listener:**
1. Create listener class in `services/{name}/{name}-app/src/main/java/.../event/...Listener.java`
   - Method decorated with `@RabbitListener(queues = "queue-name")`
2. Config: Ensure exchange/queue binding in `services/{name}/{name}-app/src/main/java/.../config/RabbitConfig.java`
3. Test: Add test in `services/{name}/{name}-app/src/test/java/.../integration/EventIntegrationTest.java`

**New Frontend Component (React):**
1. For feature-specific: `frontends/{app}/src/features/{feature}/components/{Name}.tsx`
2. For shared UI: `frontends/{app}/src/shared/components/{Name}.tsx`
3. Add test co-located: `frontends/{app}/src/{location}/__tests__/{Name}.test.ts`
4. Export from `index.ts` barrel file in that directory

**New Frontend Hook (React):**
1. Location: `frontends/{app}/src/shared/hooks/use{Name}.ts`
2. Pattern: `export function use{Name}() { ... }` (no default export)
3. Test: `frontends/{app}/src/shared/hooks/__tests__/use{Name}.test.ts`

## Special Directories

**`services/{name}/{name}-app/target/` or `build/`**
- Purpose: Build artifacts (JAR, compiled classes)
- Generated: Yes (by Gradle)
- Committed: No (in `.gitignore`)
- Action: Ignore, never commit

**`frontends/{name}/node_modules/`**
- Purpose: npm dependencies
- Generated: Yes (by `npm install`)
- Committed: No (in `.gitignore`)
- Action: Ignore, never commit

**`frontends/{name}/dist/`**
- Purpose: Built frontend artifacts (bundled JS, HTML, CSS)
- Generated: Yes (by build command: `npm run build`)
- Committed: No (in `.gitignore`)
- Action: Ignore; consumed by Docker image at deploy time

**`.gradle/`**
- Purpose: Gradle cache
- Generated: Yes
- Committed: No
- Action: Ignore

**`.planning/codebase/`**
- Purpose: Analysis documents for GSD orchestrator
- Generated: Yes (by GSD mapper commands)
- Committed: Yes (includes in version control for executor reference)
- Action: Keep, do not modify manually (managed by GSD tools)

---

*Structure analysis: 2026-04-08*

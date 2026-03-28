# External Integrations

**Analysis Date:** 2026-03-28

## APIs & External Services

**Telegram Bot API:**
- Service: Notification Bot (`services/notification-bot/`)
- SDK: Aiogram 3.15.0 (`services/notification-bot/requirements.txt`)
- Auth: Bot token via environment variable (see `services/notification-bot/.env.example`)
- Purpose: Push notifications to students via Telegram (attendance reminders, excuse confirmations, homework alerts)

**Springdoc OpenAPI / Swagger UI:**
- Services: Academic, Schedule, Attendance, Auth
- Library: `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0`
- Endpoints per service:
  - API docs: `/api-docs`
  - Swagger UI: `/swagger-ui.html`
- Annotations defined in `*-api-contract` modules using `io.swagger.core.v3:swagger-annotations-jakarta`

## Message Queues & Events

**RabbitMQ 3.13:**
- Connection: `rabbitmq:5672` (all services)
- Auth: `${RABBITMQ_USER:rct_user}` / `${RABBITMQ_PASSWORD:rct_dev_pass}`
- Pattern: Fanout exchange for async domain events
- Client libraries:
  - Java: `spring-boot-starter-amqp` (Spring AMQP / RabbitTemplate)
  - Python: `aio-pika:9.5.3` (async)

**Event Schemas (JSON Schema 2020-12):**
All schemas in `event-schemas/` directory. Standard envelope:
```json
{
  "event_type": "domain.action",
  "event_id": "uuid",
  "occurred_at": "ISO-8601 datetime",
  "payload": { ... }
}
```

**Events produced by Schedule Service:**
- `lesson.started` (`event-schemas/lesson.started.json`)
  - Trigger: Lesson transitions to ACTIVE status
  - Payload: `lesson_id`, `group_id`, `subject_id`, `teacher_id`, `lesson_number`, `start_time`, `end_time`, `room`
  - Consumers: Attendance Service (opens check-in window), Notification services (send reminders)

- `lesson.closed` (`event-schemas/lesson.closed.json`)
  - Trigger: Lesson transitions to CLOSED status
  - Payload: `lesson_id`, `group_id`, `subject_id`
  - Consumers: Attendance Service (auto-mark absent for unmarked students)

- `lesson.cancelled` (`event-schemas/lesson.cancelled.json`)
  - Trigger: Headman cancels a lesson
  - Payload: `lesson_id`, `group_id`, `subject_id`, `date`, `cancel_reason`
  - Consumers: Notification services (inform students)

**Events produced by Attendance Service:**
- `attendance.marked` (`event-schemas/attendance.marked.json`)
  - Trigger: Student check-in recorded
  - Payload: `lesson_id`, `user_id`, `group_id`, `status` (present/absent/excused/free_attendance), `marked_by` (student_geo/headman/auto_scheduler/late_checkin)
  - Consumers: Notification services (confirm to student)

- `excuse.requested` (`event-schemas/excuse.requested.json`)
  - Trigger: Student submits excuse ticket
  - Payload: `user_id`, `group_id`, `excuse_type`, `ticket_id`, `lesson_ids`, `has_attachments`
  - Consumers: Notification-Bot (forward to headman via Telegram)

- `late_checkin.requested` (`event-schemas/late_checkin.requested.json`)
  - Trigger: Student requests retroactive check-in
  - Payload: `user_id`, `group_id`, `lesson_id`, `student_name`, `lesson_date`
  - Consumers: Notification-Bot (send approval request to headman)

**Events produced by Academic Service:**
- `homework.published` (`event-schemas/homework.published.json`)
  - Trigger: Headman publishes homework
  - Payload: `homework_id`, `group_id`, `subject_id`, `lesson_id`, `title`, `has_link`
  - Consumers: Notification services (notify group students)

## Inter-Service Communication

**REST via API Gateway:**
- Gateway: `services/api-gateway/` on port 8080
- Pattern: Path-based routing with `StripPrefix=1`
- Routes defined in `services/api-gateway/src/main/resources/application.yml`:

| Route Pattern | Target Service | Target Port |
|---------------|---------------|-------------|
| `/api/auth/**` | auth-service | 9090 |
| `/api/academic/**` | academic-service | 9091 |
| `/api/schedule/**` | schedule-service | 9092 |
| `/api/attendance/**`, `/api/reports/**` | attendance-service | 9093 |
| `/api/ws/**` | notification-web | 9094 |

**gRPC (proto contracts defined, implementation planned for Phase 2):**
- Proto definitions: `proto/` directory
- Java gRPC starter (`net.devh:grpc-spring-boot-starter:3.1.0.RELEASE`) commented out in build files
- Python gRPC client active in notification-bot: `grpcio:1.69.0`

**Academic gRPC Service** (`proto/academic.proto`):
- Package: `rutcampustrack.academic` / Java: `ru.rutcampustrack.academic.grpc`
- RPCs:
  - `GetGroup(GroupRequest) -> GroupResponse`
  - `GetGroupMembers(GroupMembersRequest) -> GroupMembersResponse`
  - `GetTeacherSubjects(TeacherSubjectsRequest) -> TeacherSubjectsResponse`
  - `IsHeadman(HeadmanCheckRequest) -> HeadmanCheckResponse`
  - `GetActiveSemester(Empty) -> SemesterResponse`
  - `GetCampusGeofence(Empty) -> GeofenceResponse`
  - `GetUserById(UserRequest) -> UserResponse`

**Schedule gRPC Service** (`proto/schedule.proto`):
- Package: `rutcampustrack.schedule` / Java: `ru.rutcampustrack.schedule.grpc`
- RPCs:
  - `GetActiveLesson(ActiveLessonRequest) -> LessonResponse`
  - `GetLessonById(LessonByIdRequest) -> LessonResponse`
  - `GetLessonsByGroup(LessonsByGroupRequest) -> LessonsResponse`

**WebSocket (Notification-Web):**
- Service: `services/notification-web/` on port 9094
- Framework: `spring-boot-starter-websocket`
- Purpose: Real-time push notifications to frontend clients
- Receives events from RabbitMQ, pushes to connected WebSocket clients

## Authentication & Security

**JWT-based Authentication:**
- Library: JJWT 0.12.6 (`io.jsonwebtoken:jjwt-api`)
- Token types:
  - Access token: 900 seconds (15 minutes)
  - Refresh token: 604,800 seconds (7 days)
- Configuration: `services/auth-service/src/main/resources/application.yml`

**Auth Service** (`services/auth-service/`):
- Spring Security (`spring-boot-starter-security`)
- JWT creation and validation
- OTP support (planned)
- Token storage: Redis (`spring-boot-starter-data-redis`)
- BCrypt password hashing (via Spring Security)

**API Gateway JWT Validation:**
- Gateway validates JWT using public key (`services/api-gateway/build.gradle.kts` includes JJWT)
- Routes protected at gateway level before forwarding to services

**Service-to-Service Auth:**
- Currently internal network only (Docker `private_net`)
- No service-to-service auth tokens defined yet
- gRPC calls (when implemented) will use internal network trust

## Data Storage Connections

**PostgreSQL 16 (two isolated instances):**
- Academic DB:
  - Host: `postgres-academic:5432`
  - Database: `academic_db`
  - User: `rct_user`
  - Password: `${POSTGRES_ACADEMIC_PASSWORD:rct_dev_pass}`
  - Config: `services/academic-service/academic-app/src/main/resources/application.yml`
- Schedule DB:
  - Host: `postgres-schedule:5432`
  - Database: `schedule_db`
  - User: `rct_user`
  - Password: `${POSTGRES_SCHEDULE_PASSWORD:rct_dev_pass}`
  - Config: `services/schedule-service/schedule-app/src/main/resources/application.yml`

**MongoDB 7:**
- Host: `mongo-attendance:27017`
- Database: `attendance_db`
- No authentication in dev mode
- Config: `services/attendance-service/attendance-app/src/main/resources/application.yml`

**Redis 7:**
- Host: `redis:6379`
- No authentication in dev mode
- Used by: Auth Service (token store), Academic Service (cache)

## Monitoring & Observability

**Spring Boot Actuator:**
- Enabled on API Gateway (`services/api-gateway/build.gradle.kts`)
- Not explicitly added to other services

**Logging:**
- Standard Spring Boot logging
- Debug level for `ru.rutcampustrack` package in all services
- Gateway: debug level for `org.springframework.cloud.gateway`

**Error Tracking:** Not configured

**Metrics:** Not configured beyond Actuator defaults

## CI/CD & Deployment

**CI Pipeline:** Not detected (no `.github/workflows/`, `Jenkinsfile`, or `.gitlab-ci.yml`)

**Containerization:**
- `docker-compose.yml` for infrastructure only (databases, Redis, RabbitMQ)
- No Dockerfiles detected for application services
- Services run locally during development, infrastructure in Docker

## Environment Configuration

**Required environment variables (with dev defaults):**
- `POSTGRES_ACADEMIC_PASSWORD` (default: `rct_dev_pass`)
- `POSTGRES_SCHEDULE_PASSWORD` (default: `rct_dev_pass`)
- `RABBITMQ_USER` (default: `rct_user`)
- `RABBITMQ_PASSWORD` (default: `rct_dev_pass`)
- JWT secret/key (configured in auth-service, specific var not in application.yml)
- Telegram bot token (notification-bot, see `.env.example`)

**Secrets location:**
- Environment variables with defaults for development
- `.env.example` in `services/notification-bot/` documents required Python bot config
- No centralized secret management (Vault, AWS Secrets Manager, etc.)

## Webhooks & Callbacks

**Incoming:**
- None detected (Telegram bot uses long polling via Aiogram, not webhooks)

**Outgoing:**
- None detected

---

*Integration audit: 2026-03-28*

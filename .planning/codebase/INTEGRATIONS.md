# External Integrations

**Analysis Date:** 2026-04-08

## APIs & External Services

**Telegram Bot API:**
- Service: Telegram Bot API (for notification-bot)
- What it's used for: Sending attendance reminders, lesson notifications, excuse ticket updates to users via Telegram
- SDK/Client: Aiogram 3.15.0 (Python async framework)
- Auth: Environment variable `BOT_TOKEN` (Telegram bot token)
- Location: `services/notification-bot/` at `/services/notification-bot/`

**Telegram Mini App:**
- Service: Telegram Mini App platform (embedded app in Telegram)
- What it's used for: Mobile check-in interface for students (geolocation-based attendance marking)
- SDK/Client: @telegram-apps/sdk 3.11.8 (TypeScript SDK for Mini App)
- React Bindings: @telegram-apps/sdk-react 3.3.9
- Auth: HMAC signature verification of Telegram Mini App init data (`tma.initData`, `tma.initDataUnsafe`)
- Environment: `TMA_BOT_TOKEN` (bot token for HMAC verification)
- Location: `frontends/mini-app/` at `/frontends/mini-app/`
- Manifest Integration: Telegram Mini App manifest configured in Mini App frontend

**Telegram API Gateway:**
- Service: Telegram API (for auth verification via Bot API `getMe`)
- Used by: Auth Service for validating Mini App init data
- Implementation: Direct HTTP calls to https://api.telegram.org/bot{BOT_TOKEN}/ endpoints

## Data Storage

**Databases:**

**PostgreSQL (Two instances):**
- Academic Database: `academic_db`
  - Host: `postgres-academic` (dev), internal network (prod)
  - Port: 5432
  - Auth: User `rct_user`, password from `POSTGRES_ACADEMIC_PASSWORD` env var
  - Client: Spring Data JPA + Hibernate ORM
  - Location: `services/academic-service/academic-app/` at `/services/academic-service/academic-app/`
  - Migrations: Flyway at `services/academic-service/academic-app/src/main/resources/db/migration/`
  - Schema: Departments, Programs, Courses, Teachers, Groups, Semesters, Academic calendar

- Schedule Database: `schedule_db`
  - Host: `postgres-schedule` (dev), internal network (prod)
  - Port: 5432
  - Auth: User `rct_user`, password from `POSTGRES_SCHEDULE_PASSWORD` env var
  - Client: Spring Data JPA + Hibernate ORM
  - Location: `services/schedule-service/schedule-app/` at `/services/schedule-service/schedule-app/`
  - Migrations: Flyway at `services/schedule-service/schedule-app/src/main/resources/db/migration/`
  - Schema: Lessons, lesson schedules, classroom assignments, timeslots, lesson states (planned/in-progress/completed/cancelled)

**MongoDB:**
- Attendance Database: `attendance_db`
  - Host: `mongo-attendance` (dev), internal network (prod)
  - Port: 27017
  - Auth: Root user (from env vars), application user `${MONGO_USER}:${MONGO_PASSWORD}`
  - Connection String: `mongodb://${MONGO_USER}:${MONGO_PASSWORD}@mongo-attendance:27017/attendance_db?authSource=admin`
  - Client: Spring Data MongoDB (embedded driver)
  - Locations:
    - `services/attendance-service/attendance-app/` at `/services/attendance-service/attendance-app/`
    - `services/notification-service/notification-app/` at `/services/notification-service/notification-app/` (for push subscriptions)
  - Collections: Attendance records, excuse tickets, push subscriptions

**File Storage:**
- None persistent - excuse ticket files are NOT stored in the system
- Implementation: Files uploaded by students are transmitted directly to Telegram headman private chats

## Caching & Session Store

**Redis:**
- Service: Redis 7-alpine
- Host: `redis` (dev), internal network (prod)
- Port: 6379
- Auth: Password from `REDIS_PASSWORD` env var (requirepass setting)
- Client: Spring Data Redis (Lettuce driver)
- Uses:
  - Session storage for JWT refresh tokens (auth-service)
  - Entity caching (academic-service)
  - Reminder message storage (notification-bot)
  - Distributed cache for frequently accessed data
- Location: Used by `services/auth-service/`, `services/academic-service/`, `services/notification-service/notification-app/`, `services/notification-bot/`
- Connection: Environment variables `REDIS_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`

## Message Broker & Event Streaming

**RabbitMQ:**
- Service: RabbitMQ 3.13 (management-alpine for dev, alpine for prod)
- Host: `rabbitmq` (dev), internal network (prod)
- Port: 5672 (AMQP), 15672 (Management UI - dev only)
- Auth: User `${RABBITMQ_USER}` (rct_user), password `${RABBITMQ_PASSWORD}`
- Client: Spring AMQP (RabbitTemplate + message listeners)
- Exchange Type: Fanout exchange (broadcast events to all subscribers)
- Event Flow:
  - Academic Service publishes: `lesson.started`, `lesson.ended`, `lesson.cancelled`
  - Schedule Service publishes: `schedule.updated`
  - Attendance Service publishes: `attendance.marked`, `attendance.auto_marked`, `excuse_ticket.created`, `excuse_ticket.resolved`
  - Notification Web and Bot subscribe to all events
- Event Schema: JSON Schema definitions at `event-schemas/` at `/event-schemas/`
- Locations:
  - `services/academic-service/academic-app/` - Publisher
  - `services/schedule-service/schedule-app/` - Publisher
  - `services/attendance-service/attendance-app/` - Publisher
  - `services/notification-service/notification-app/` - Consumer
  - `services/notification-bot/` - Consumer
- Connection: Environment variables `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`

## Authentication & Identity

**Auth Provider:**
- Custom JWT-based authentication
- Implementation: Auth Service at `services/auth-service/` at `/services/auth-service/`
- Mechanism:
  - JWT signed with RS256 (RSA private key) by auth-service
  - Verified with public key by api-gateway and other services
  - Keys: RSA 3072-bit keypair stored in Docker volume `jwt-keys`
  - Private key: `/keys/private.key` (auth-service only)
  - Public key: `/keys/public.key` (shared via volume, accessible to gateway and notification-web)
- Key Generation (deployment):
  - Script in `.github/workflows/deploy.yml` generates keys on first deployment using OpenSSL
  - Keys persisted in named volume `rutcampustrack_jwt-keys`
  - Permissions: private.key (640), public.key (644)
- Token Structure: Standard JWT with roles, user ID, tenant context
- Refresh Tokens: Stored in Redis with expiration
- Password Hashing: BCrypt via Spring Security (academic-service)

**Telegram Mini App Auth:**
- OAuth-like flow via Telegram Mini App
- Init data HMAC signature verification (bot token as secret)
- User identity: Telegram user ID embedded in init data
- Implementation: auth-service validates Mini App init data payload

## Monitoring & Observability

**Health Checks:**
- Spring Boot Actuator `/actuator/health` endpoints on each service
- Health check types:
  - Liveness: `/actuator/health/liveness` (notification-web)
  - Readiness: `/actuator/health` (api-gateway)
  - Custom: Docker HEALTHCHECK directives
- Used by: docker-compose health checks, Kubernetes (if deployed)

**Metrics:**
- Spring Boot Actuator `/actuator/metrics` (enabled on all services)
- Metrics collected: request counts, response times, JVM memory, database connection pools
- Exposed via: Actuator endpoints (JMX not exposed in containers)

**Error Tracking:**
- None detected - errors logged to stdout via Logback

**Logs:**
- Approach: Structured logging via SLF4J + Logback
- Output: STDOUT (Docker captures to container logs)
- Access: `docker logs <container-name>`
- Log level: Configured via Spring profile `logging.level.*` properties

## CI/CD & Deployment

**Hosting:**
- Platform: Docker containers on Linux VPS
- Container Registry: GitHub Container Registry (GHCR) at `ghcr.io/ditekmax/rutcampustrack/*`
- Deployment method: SSH script execution (`.github/workflows/deploy.yml`)

**CI Pipeline:**
- Platform: GitHub Actions (`.github/workflows/ci.yml`)
- Triggers: Push to any branch, Pull Requests
- Jobs:
  1. **Java Build & Test**: Gradle build/test for each service (matrix strategy)
     - Services tested: api-gateway, auth-service, academic-service, schedule-service, attendance-service, notification-service
     - Java version: 21 (Temurin)
     - Cache: Gradle dependency cache
  2. **Python Lint & Test**: Python notification-bot
     - Python version: 3.12
     - Tools: ruff (linter + formatter), pytest
     - Cache: pip dependencies cache
  3. **Frontend Build & Test**: React and Angular apps
     - Apps tested: pwa, mini-app, web-panel
     - Node version: 22
     - Commands: npm ci, npm test, npm run build
- Artifacts: Test reports uploaded to GitHub Actions (retention 7 days on failure)

**CD Pipeline:**
- Trigger: Push to `main` branch (triggers after CI passes)
- Docker Build & Push:
  - Tool: docker/build-push-action@v7
  - Registry: ghcr.io (requires GHCR_TOKEN secret)
  - Images built:
    - `ghcr.io/ditekmax/rutcampustrack/api-gateway:latest`
    - `ghcr.io/ditekmax/rutcampustrack/auth-service:latest`
    - `ghcr.io/ditekmax/rutcampustrack/academic-service:latest`
    - `ghcr.io/ditekmax/rutcampustrack/schedule-service:latest`
    - `ghcr.io/ditekmax/rutcampustrack/attendance-service:latest`
    - `ghcr.io/ditekmax/rutcampustrack/notification-web:latest`
    - `ghcr.io/ditekmax/rutcampustrack/notification-bot:latest`
    - `ghcr.io/ditekmax/rutcampustrack/pwa-nginx:latest`
    - `ghcr.io/ditekmax/rutcampustrack/mini-app-nginx:latest`
    - `ghcr.io/ditekmax/rutcampustrack/web-panel-nginx:latest`
    - `ghcr.io/ditekmax/rutcampustrack/landing-nginx:latest`
  - Tags: `latest` and git commit SHA
  - Caching: GHA cache per service (mode=max for better reuse)
- Deployment:
  - Tool: appleboy/ssh-action@v1
  - Target: VPS host (from secrets `VPS_HOST`, `VPS_USER`)
  - Auth: SSH private key (from secrets `SSH_PRIVATE_KEY`)
  - Deployment Script:
    1. Docker login to GHCR using `GHCR_TOKEN`
    2. Git pull --ff-only (fast-forward only)
    3. Generate nginx dhparam.pem (2048-bit) if missing
    4. Create Docker volume `rutcampustrack_jwt-keys` if not exists
    5. Generate RSA keypair (3072-bit) for JWT signing if missing
    6. Docker pull latest images
    7. Docker Compose up with `.env.prod` and `docker-compose.prod.yml`
    8. Sleep 30s, run docker compose up again (for health check settling)

## Environment Configuration

**Required Environment Variables (Dev):**
- `POSTGRES_ACADEMIC_PASSWORD` - Academic DB password
- `POSTGRES_SCHEDULE_PASSWORD` - Schedule DB password
- `MONGO_USER` - MongoDB application user
- `MONGO_PASSWORD` - MongoDB application password
- `REDIS_PASSWORD` - Redis password
- `RABBITMQ_USER` - RabbitMQ user
- `RABBITMQ_PASSWORD` - RabbitMQ password
- `BOT_TOKEN` - Telegram Bot API token
- `JWT_KEY_DIR` - Path to JWT keypair (default: `/keys` in containers)
- `TMA_BOT_TOKEN` - Telegram Mini App bot token (for HMAC verification)

**Required Environment Variables (Prod):**
Same as dev, plus:
- `VAPID_PUBLIC_KEY` - Web Push VAPID public key
- `VAPID_PRIVATE_KEY` - Web Push VAPID private key
- `VAPID_SUBJECT` - Web Push VAPID subject (mailto:email)
- `CORS_ALLOWED_ORIGIN` - CORS allowed origin for API Gateway
- `MINI_APP_URL` - Telegram Mini App URL (https://t.me/BotUsername/appname)
- `GHCR_TOKEN` - GitHub Container Registry token (for docker login)

**Configuration File Locations:**
- `.env` - Development environment variables (git-ignored, contains secrets)
- `.env.prod` - Production environment variables (git-ignored, contains secrets)
- `.github/workflows/deploy.yml` - CI/CD deployment script references secrets

**Secrets Management:**
- Secrets location: GitHub Actions Secrets
  - `GHCR_TOKEN` - Push images to GHCR
  - `VPS_HOST` - Deployment target IP/hostname
  - `VPS_USER` - SSH user on VPS
  - `SSH_PRIVATE_KEY` - SSH private key for VPS access
- Deployment: SSH script exports `GHCR_TOKEN` as environment variable during execution
- Sensitive files in `.env` and `.env.prod` are git-ignored

## SSL/TLS & HTTPS

**Certificate Provider:**
- Let's Encrypt (free, automated)
- Implementation: certbot Docker container (certbot/certbot image)
- Location: `docker-compose.prod.yml` service `certbot`

**Certificate Management:**
- Renewal: Automated renewal in container (12-hour interval check)
- Storage: Docker volume `certbot-conf:/etc/letsencrypt`
- Certificate files: `/etc/letsencrypt/live/ruttrack.site/` (on VPS)
- Renewal script: Certbot renewal runs on 12-hour loop with `--quiet` flag
- Reverse proxy: nginx at `nginx/` at `/nginx/` handles HTTPS termination

**Nginx Configuration:**
- Main config: `nginx/nginx.conf` at `/nginx/nginx.conf`
- Site configs: `nginx/conf.d/` at `/nginx/conf.d/` (included from main)
- Enabled: HTTP/2, TLS 1.2/1.3, cipher suites
- Features:
  - Client max body size: 10MB
  - Server tokens hidden (security, NET-01)
  - SSL cert reload: Every 6 hours (from deploy.yml command)
  - Certificate paths: `/etc/letsencrypt/live/` (mounted from certbot volume)

## Webhooks & Callbacks

**Incoming Webhooks:**
- Telegram Bot API webhook - Not applicable (polling-based via aiogram)
- RabbitMQ event listeners - Event-driven architecture (no HTTP webhooks)

**Outgoing Webhooks:**
- None detected - System publishes RabbitMQ events internally

## Web Push (Notifications)

**Web Push Service:**
- Protocol: Web Push API (RFC 8030)
- SDK: nl.martijndwars/web-push 5.1.2 (Java)
- Implementation: Notification Web Service at `services/notification-service/notification-app/`

**VAPID Configuration:**
- VAPID Key Pair: Application-generated RSA keypair
- Public Key: Sent to browsers, embedded in Web App Manifest
- Private Key: Used by server to sign push messages
- Subject: mailto: email for push service provider contact
- Generation: Manual (not auto-generated like JWT keys)
- Storage: Environment variables `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`

**Push Subscription Storage:**
- Database: MongoDB in `attendance_db`
- Collection: `push_subscriptions` (implied)
- Data: Endpoint URL, P256DH key, auth token (from browser Service Worker)
- Delivery Service: Notification Web Service at port 9094

**Service Worker Integration:**
- Location: PWA frontend at `frontends/pwa/` at `/frontends/pwa/`
- Plugin: vite-plugin-pwa 1.2.0 auto-generates Web App Manifest
- Manifest includes: VAPID public key
- Browser support: Subscribe via ServiceWorkerContainer.pushManager

## gRPC Inter-Service Communication

**gRPC Services:**
All services using gRPC are defined in `proto/` directory at `/proto/`

**Services Exposing gRPC:**
- Academic Service: Port 19091 (internal, intra-container)
  - Location: `services/academic-service/academic-app/`
  - Endpoints: Querying academic data
  - Clients: Notification Bot, Schedule Service (client calls)
  - Security: GRPC_SECRET env var for authentication

- Schedule Service: Port 19092 (internal, intra-container)
  - Location: `services/schedule-service/schedule-app/`
  - Endpoints: Querying lesson schedules, states
  - Clients: Notification Bot, Attendance Service (client calls)
  - Security: GRPC_SECRET env var for authentication

- Attendance Service: gRPC server
  - Location: `services/attendance-service/attendance-app/`
  - Clients: Schedule Service, other services
  - Security: GRPC_SECRET env var for authentication

**gRPC Client Usage:**
- Notification Bot: gRPC clients for academic-service (19091) and schedule-service (19092)
  - Language: Python gRPC (grpcio 1.73.0, grpcio-tools 1.73.0)
  - Protobuf: protobuf 6.31.0
  - Implementation: `services/notification-bot/` at `/services/notification-bot/`

**gRPC Security:**
- Authentication: GRPC_SECRET shared env var (interceptor-based auth)
- Transport: Plain gRPC over HTTP/2 (internal network only, no TLS in dev)
- Metadata: Custom headers for auth token/secret

## STOMP WebSocket Protocol

**WebSocket Service:**
- Service: Notification Web Service at `services/notification-service/notification-app/`
- Protocol: STOMP (Streaming Text Oriented Messaging Protocol)
- Port: 9094 (exposed to clients through API Gateway at /api/notification/ws)
- Location: `services/notification-service/notification-app/`

**STOMP Configuration:**
- Framework: Spring WebSocket + STOMP broker
- Broker: In-memory message broker (no external broker)
- Endpoints: `/api/notification/ws` (configured in app)
- Authentication: JWT token validation at WebSocket handshake

**Client Integration:**
- SDK: @stomp/stompjs 7.3.0 (TypeScript/JavaScript)
- Fallback: sockjs-client 1.6.1 (for browsers without WebSocket support)
- Location: `frontends/pwa/` at `/frontends/pwa/` uses STOMP for real-time notifications

**Message Flow:**
1. Browser connects to `/api/notification/ws` with JWT token
2. STOMP subscribe to user-specific topics (e.g., `/user/{userId}/queue/attendance-updates`)
3. Server publishes messages from RabbitMQ events to WebSocket subscribers
4. Notification Web Service bridges RabbitMQ events → STOMP topics

---

*Integration audit: 2026-04-08*

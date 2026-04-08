# Technology Stack

**Analysis Date:** 2026-04-08

## Languages

**Primary:**
- Java 21 - Backend services (api-gateway, auth-service, academic-service, schedule-service, attendance-service, notification-web)
- TypeScript ~5.8.3 - Frontend development (pwa, mini-app, web-panel)
- Python 3.12 - Notification bot (aiogram async framework)

**Secondary:**
- JavaScript (Node.js 22) - Frontend tooling and dependencies
- SQL - PostgreSQL and database migrations via Flyway
- JavaScript/Protobuf - gRPC service definitions

## Runtime

**JVM Environment:**
- Java 21 (Temurin distribution)
- Gradle 8.x (wrapper: `gradlew.bat`, `gradlew`)
- Spring Boot 3.4.1

**Node.js:**
- Version 22 (for frontend development)
- npm for package management

**Python:**
- Version 3.12-slim (Docker image for notification-bot)
- pip for Python dependencies

**Package Manager:**
- Gradle - Java build system with Kotlin DSL (`build.gradle.kts`)
- npm - JavaScript package management
- pip - Python package management
- Lockfiles: `package-lock.json` (npm), `gradle.lockfile` (implied)

## Frameworks

**Backend Core:**
- Spring Boot 3.4.1 - Base framework for all Java services
- Spring Cloud Gateway 2024.0.0 - API Gateway service at `services/api-gateway/`
- Spring Cloud 2024.0.0 - Dependency management for services

**Spring Boot Starters (across services):**
- `spring-boot-starter-web` - REST endpoints
- `spring-boot-starter-data-jpa` - ORM (academic-service, schedule-service)
- `spring-boot-starter-data-mongodb` - MongoDB driver (attendance-service, notification-web)
- `spring-boot-starter-data-redis` - Redis caching (auth-service, academic-service, notification-web)
- `spring-boot-starter-amqp` - RabbitMQ integration (academic-service, schedule-service, attendance-service, notification-web)
- `spring-boot-starter-websocket` - WebSocket support (notification-web for STOMP)
- `spring-boot-starter-security` - Security framework (auth-service)
- `spring-boot-starter-actuator` - Health checks and monitoring (all services)
- `spring-boot-starter-validation` - Jakarta validation annotations
- `spring-boot-starter-hateoas` - HATEOAS REST response building
- `spring-boot-starter-aop` - Aspect-oriented programming (academic-service, attendance-service, notification-web)

**gRPC & RPC:**
- Protocol Buffers 3.25.3 - Service contracts at `proto/`
- gRPC Java 1.63.0 - Inter-service communication
- grpc-server-spring-boot-starter 3.1.0 - Server support (academic-service, schedule-service, attendance-service)
- grpc-client-spring-boot-starter 3.1.0 - Client support (schedule-service, notification-bot)

**Frontend Frameworks:**
- React 19.1.0 - UI library (pwa, mini-app)
- React Router 7.14.0 - Client-side routing (pwa, mini-app)
- Angular 19.2.20 - Admin SPA (web-panel at `frontends/web-panel/`)
- Angular CDK 19.2.19 - Component toolkit
- Angular Material 19.2.19 - Material Design components

**Frontend State & Query:**
- @tanstack/react-query 5.96.2 - Data fetching and caching (React apps)
- axios 1.14.0 - HTTP client (pwa, mini-app)

**Frontend Styling & UI:**
- Tailwind CSS 4.1.4 (pwa, web-panel) / 4.2.2 (web-panel) - Utility-first CSS
- Tailwind CVA (class-variance-authority) 0.7.1 - Component variant system
- tailwind-merge 3.5.0 - Utility conflict resolution
- tw-animate-css 1.4.0 - Animation utilities
- Lucide React 1.7.0 - Icon library (pwa, mini-app)
- @phosphor-icons/react 2.1.10 - Alternative icon set (pwa, mini-app)
- @phosphor-icons/web 2.1.2 - Web icons (web-panel)
- @base-ui/react 1.3.0 - Unstyled component primitives (pwa, mini-app)

**Frontend Fonts & Theming:**
- @fontsource-variable/geist 5.2.8 - Variable font (pwa, mini-app, web-panel)

**WebSocket & Real-time:**
- @stomp/stompjs 7.3.0 - STOMP protocol client (pwa)
- sockjs-client 1.6.1 - WebSocket fallback (pwa)

**Telegram Integration:**
- @telegram-apps/sdk 3.11.8 - Telegram Mini App SDK (mini-app)
- @telegram-apps/sdk-react 3.3.9 - React bindings for Telegram SDK

**PWA & Service Workers:**
- vite-plugin-pwa 1.2.0 - PWA support and Web Push manifest
- workbox-precaching 7.4.0 - Asset precaching for offline support

**Analytics & Charts (Admin Panel):**
- chart.js 4.5.1 - Chart library (web-panel)
- ng2-charts 6.0.1 - Angular wrapper for Chart.js

**Build & Dev Tools:**
- Vite 7.0.0 - Build tool and dev server (pwa, mini-app)
- @vitejs/plugin-react 4.5.2 - React plugin for Vite
- @tailwindcss/vite 4.1.4 - Tailwind CSS plugin for Vite
- Vite PWA Plugin 1.2.0 - Web Push and manifest generation

**Testing:**
- Vitest 3.1.3 (pwa, mini-app) / 3.2.4 (web-panel) - Unit test runner (React/Angular)
- @testing-library/react 16.3.0 - React component testing utilities
- @testing-library/angular 17.4.0 - Angular component testing
- @testing-library/jest-dom 6.6.3 (pwa, mini-app) / 6.9.1 (web-panel) - DOM matchers
- @testing-library/user-event 14.6.1 - User interaction simulation
- @analogjs/vitest-angular 2.4.0 - Angular-Vitest integration
- jsdom 26.1.0 (pwa, mini-app) / 29.0.2 (web-panel) - DOM environment
- Jest (implied via @testing-library but using Vitest)

**Java Testing & Quality:**
- JUnit 5 - Test framework (implied via Spring Boot Starter Test)
- TestContainers 1.20.4 - Container-based integration testing
  - testcontainers-postgresql - PostgreSQL containers for DB tests
  - testcontainers-mongodb - MongoDB containers
  - testcontainers-rabbitmq - RabbitMQ containers
- Awaitility 4.2.2 - Async assertion library (attendance-service)
- ArchUnit 1.3.0 - Architecture testing (attendance-service)

**Java Cryptography & JWT:**
- JJWT (JSON Web Token) 0.12.6 - JWT creation and validation (auth-service, api-gateway, notification-web)
  - jjwt-api
  - jjwt-impl
  - jjwt-jackson
- BouncyCastle (bcprov-jdk15on) 1.70 - Cryptographic provider (notification-web for Web Push)

**Web Push:**
- nl.martijndwars:web-push 5.1.2 - Web Push API library (notification-web)
- jose4j 0.7.9 - JWE/JWS implementation (notification-web, transitive dependency)
- Apache HttpClient 4.5.13 - HTTP client (notification-web for Push Service)

**Python Dependencies (notification-bot):**
- Aiogram 3.15.0 - Async Telegram Bot framework
- aio-pika 9.5.3 - RabbitMQ async client
- aiohttp 3.10.11 - Async HTTP client
- pydantic 2.9.2 - Data validation
- pydantic-settings 2.6.1 - Settings management
- gRPC Python 1.73.0 - gRPC client for academic/schedule service calls
- redis[hiredis] 5.2.1 - Redis client with Hiredis accelerator
- protobuf 6.31.0 - Protocol Buffer runtime

**ORM & Database:**
- Hibernate 6.x (via Spring Data JPA) - ORM for PostgreSQL
- MongoDB Java Driver 4.x (transitive via Spring Data MongoDB)
- PostgreSQL JDBC Driver (org.postgresql) - Database driver
- Flyway 10.x - Database migration tool (academic-service, schedule-service at `src/main/resources/db/migration/`)
  - flyway-core
  - flyway-database-postgresql

**JSON Serialization:**
- Jackson 2.18.2 - JSON/XML serialization
  - jackson-annotations
  - jackson-datatype-hibernate6 - Hibernate proxy handling for Redis serialization
  - jackson-databind (transitive)

**Logging & Monitoring:**
- SLF4J + Logback (transitive via Spring Boot) - Logging framework
- Spring Boot Actuator - Health, metrics, OpenAPI endpoints

**API Documentation:**
- SpringDoc OpenAPI 2.8.6 - Swagger/OpenAPI 3.0 integration
  - springdoc-openapi-starter-webmvc-ui (rest-based services)
  - springdoc-openapi-starter-webflux-ui (api-gateway)
- Swagger Annotations 2.2.22 - OpenAPI annotations

**Security Utilities:**
- Spring Security Crypto - BCrypt password encoding (academic-service)

**Utilities:**
- Lombok - Boilerplate reduction (only in `*-app` modules, NOT in contracts per `CLAUDE.md`)
- Apache Commons - Utility classes (transitive dependencies)

## Configuration

**Environment:**
Files using environment variables:
- `docker-compose.yml` - Development containers
- `docker-compose.prod.yml` - Production deployment
- `.env` file (not read for secrets) - Development environment variables
- `.env.prod` file (not read for secrets) - Production environment variables
- `.github/workflows/ci.yml` - GitHub Actions CI pipeline at `/.github/workflows/ci.yml`
- `.github/workflows/deploy.yml` - GitHub Actions deployment at `/.github/workflows/deploy.yml`

**Key Configuration Files:**
- `build.gradle.kts` - Root Gradle build configuration at `/build.gradle.kts`
- `settings.gradle.kts` - Gradle project structure at `/settings.gradle.kts`
- `gradle/` directory - Gradle wrapper runtime
- `.gitignore` - Excluded files pattern

**Build:**
- Gradle Kotlin DSL (`*.gradle.kts` files)
- Java encoding: UTF-8
- Java compilation with `-parameters` flag for parameter reflection
- Protobuf compilation with protoc 3.25.3

## Frontend Build Configuration

**Vite Config (pwa, mini-app):**
- React plugin: @vitejs/plugin-react
- TypeScript: ~5.8.3
- Build target: ES modules with Vite optimizations
- Output to `dist/` directory

**Angular Config (web-panel):**
- Angular CLI 19.2.23
- Angular DevKit 19.2.23
- TypeScript: ~5.7.2
- Build output to `dist/` for nginx serving
- Routing baseHref: `/admin/` (per docker-compose context)

**Vite PWA Configuration (pwa):**
- vite-plugin-pwa 1.2.0
- Web App Manifest generation
- Workbox precaching for offline support
- Service Worker generation

## Platform Requirements

**Development:**
- Java 21 (JDK Temurin or compatible)
- Node.js 22 + npm
- Python 3.12 (for notification-bot local development)
- Docker + Docker Compose (for infrastructure)
- Gradle wrapper (bundled in repo)
- Optional: IDE with Spring Boot and TypeScript support

**Production:**
- Docker 20+ with Docker Compose 2+
- Deployment target: Docker containers on Linux VPS
- Container images published to GitHub Container Registry (GHCR): `ghcr.io/ditekmax/rutcampustrack/*`
- Environment-specific configuration via `.env.prod`

**Containerization:**
- Base images:
  - `openjdk:21-slim` or `eclipse-temurin:21-jre` (Java services)
  - `python:3.12-slim` (notification-bot)
  - `nginx:1.27-alpine` (frontend reverse proxies)
  - `postgres:16` (PostgreSQL databases)
  - `mongo:7` (MongoDB)
  - `redis:7-alpine` (Redis cache/session store)
  - `rabbitmq:3.13-management-alpine` (message broker, dev) or `rabbitmq:3.13-alpine` (prod)
  - `certbot/certbot` (Let's Encrypt SSL certificate renewal)

**Network:**
- Docker bridge network: `private_net`
- Ports exposed internally (container network):
  - 9090 - auth-service
  - 9091 - academic-service
  - 9092 - schedule-service
  - 9093 - attendance-service
  - 9094 - notification-web
  - 8080 - api-gateway
  - 8081 - landing (dev only)
  - 5672 - RabbitMQ AMQP
  - 5432 - PostgreSQL
  - 27017 - MongoDB
  - 6379 - Redis
- Public ports (host bindings prod):
  - 80/443 - nginx reverse proxy
  - Internal services on 8080 (gateway)

---

*Stack analysis: 2026-04-08*

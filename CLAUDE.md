# CLAUDE.md — RutCampusTrack

## Что это за проект

RutCampusTrack — микросервисная система учёта посещаемости для вуза РУТ МИИТ. Monorepo на Java 21 + Spring Boot 3.4 + Gradle.

## Текущий статус

- **v1.0**: ЗАВЕРШЕНА (Auth Service + API Gateway) — фазы 1-4, 26 тестов
- **v2.0**: ЗАВЕРШЕНА (Academic Service) — фазы 5-9, 50 тестов
- **v3.0**: ЗАВЕРШЕНА (Schedule Service) — фазы 10-14, 55 тестов
- **v4.0**: ЗАВЕРШЕНА (Attendance Service) — фазы 15-19, ~95 тестов
- **v5.0**: ЗАВЕРШЕНА (Notification Service — Web + Bot) — фазы 20-26, ~128 тестов (20 Java + 108 Python)
- **v6.0**: ЗАВЕРШЕНА (PWA + Web Push) — фазы 27-32
- **v7.0**: ЗАВЕРШЕНА (Frontends — Mini App, Web Panel, Landing) — фазы 33-40
- **v8.0**: ЗАВЕРШЕНА (CI/CD, Deployment & Documentation) — фазы 41-48
- **v9.0**: ЗАВЕРШЕНА (Frontend Unification — Single Login & Role-Based Web Clients) — фазы 49-61 (включая 58 BUG-006 Admin Fixes, 59 Excuse Tickets Backend, 60 Headman Schedule Management, 61 Headman Homework Management UI)
- **v0.0.0**: В ПРОДЕ (Pre-release hardening по аудиту) — 14 milestones M01-M14 в `docs/milestones/`, deployed на VPS 2026-04-27 (M15)
- **M15**: ВЫПОЛНЕН (First VPS Deploy + 4 hotfix-коммита) — без отдельной milestone-папки, только git history (`b8cf106`, `c7b2b93`, `c3ff148`, `8d7c168`)
- **M16**: BACKLOG (Cleanup после первого деплоя) — пока только в `docs/archive/future-ideas.md` § «M16 Cleanup Backlog», milestone-папки нет
- Полный план v0.0.0: `docs/archive/report-before-v0.0.0/99-executive-summary.md`
- Исходный аудит: `docs/archive/report-before-v0.0.0/` (16 отчётов, OWNER-ANSWERS.md, COVERAGE-AUDIT.md)
- Полный план v1.0-v9.0: `.planning/ROADMAP.md`, отчёты: `docs/phase-reports/phase-{N}-report.md`

### v0.0.0 Milestones

| # | Milestone | Содержание |
|---|-----------|------------|
| M01 | Shared Foundations | ✅ 4 shared-модуля (web/events/logback/test-containers) — завершён 2026-04-19 |
| M02 | Reliable Eventing | ✅ ShedLock + shared-outbox + contract-тесты + ArchUnit — завершён 2026-04-19 |
| M03a | Internal JWT + Rate-limit | ✅ shared-security + token-exchange + Gateway issuer + downstream dual-mode + RL 6 роутов + composite login key — завершён 2026-04-20 |
| M03b | Secure Boundaries Part B | ✅ HttpOnly cookie + WS-ticket + logout lifecycle + KI-3/6/7/8 hot-patches — завершён 2026-04-20 |
| M04 | Observability | ✅ OTel+Tempo tracing + Alertmanager → bot → Telegram + JSON-логи + 8 counter'ов + 3 gauge'а + retention 14d + Grafana business-kpis dashboard — завершён 2026-04-20 |
| M05 | Performance | ✅ Composite indexes + Redis rbac/subject cache + HikariCP tuning + batch endpoints + gRPC fan-out + push retention 90d — завершён 2026-04-21 |
| M06 | Ops & Supply Chain | ✅ SHA tagging + digest cadvisor/promtail + semver-pin observability + HEALTHCHECK × 7 + Renovate/Dependabot + Trivy/Gitleaks + CI/deploy gate + M05 security defer'ы — завершён 2026-04-21 |
| M07 | Frontend Hardening | ✅ CSP self-host + openapi-typescript + RFC 7807 + unified STOMP + UX P2-7A (PullToRefresh/swipe/dateNav/bounds/scroll/BottomSheet/geolocation) + ConfirmWithReason + lazy-per-role + sparklines placeholder + a11y baseline — завершён 2026-04-22 |
| M08 | Test Infrastructure | ✅ 31 `*Test→*IT` rename + Testcontainers reuse + Flyway MigrationIT + Clock-injection + Playwright e2e (8 specs + axe) + frontend unit regression guards + k6 load scaffold + security contracts (GrpcSecretFailFast+TmaIT+SameSiteCookie) + event schema coverage (40 тестов) + STOMP lifecycle IT + PWA reconnect guards + JaCoCo per-module ratchet (+ M09 latecheckin 70% pilot) + Vitest 50%/PWA 38% baseline + pytest-cov 50%/70% baseline + diff-cover 80% + SBOM+cosign keyless + digest-pin 13 images + Trivy SHA-pin + Renovate monthly digest-bump — завершён 2026-04-23 |
| M09 | Prod Release Blockers (Фаза 3 + event unification) | ✅ OTP через RabbitMQ event (08 P0-2) + MessageDigest.isEqual (01 P0-5) + cleanupOrphans delete (04 P0-6) + landing deep-link (12 P0-2) + latecheckin unit/IT/contract + 70% jacoco gate (14 P0-1) + bot callback unit + 70% handlers coverage (14 P0-2) + lesson.cancelled full snapshot (P2-11/5, V13 миграция cancelled_by/at) + excuse/late_checkin headman role check (06 P1-1) + prod-deploy-checklist + secret-rotation runbook + bot-webhook-migration + resource-limits (NEW-154/155/157) + docker-compose mem_limits + JVM opts + Prometheus ContainerMemoryHigh alert — завершён 2026-04-24 |
| M10 | Notification History | ✅ Stateful notification-web + MongoDB notification_db (PoLP user) + notification_history TTL 30d + Caffeine unread-count 30s + NotificationApi 4 endpoints + PWA/web-panel hybrid integration — завершён 2026-04-24 |
| M11 | OpenAPI Polish | ✅ SharedOpenApiCustomizer наполнение + @Schema на DTO (100% coverage) + nginx basic-auth на prod /swagger-ui + OpenAPI↔runtime conformance IT (academic/schedule/attendance/notification) — завершён 2026-04-24 |
| M12 | Auth Contract-first Refactor | ✅ auth-api-contract + auth-app Gradle split + 12 DTO migration + 4 interfaces (AuthApi/WsTicketApi/InternalIssuerApi/InternalWsTicketApi) + controllers implement + ArchUnit contract test + OpenApiSnapshotIT + @Hidden на internal endpoints — завершён 2026-04-24 |
| M13 | Pre-Deploy Hardening | ✅ 24 групп — flaky tests + rate-limit semantics + pageable cap + /auth/refresh-body removal + InvalidParam migration + Mongo TTL/RS/@Transactional + consumer dedup (PG+Mongo+bot Redis) + 12 IDOR fixes + Actuator tracing exclude + mem_limit 26 containers + healthcheck IT + .env.prod validator + Prometheus/Alertmanager basic-auth + backup/restore GPG + CSP report endpoint + Grafana dashboards + STOMP heartbeat 10s/10s + 18 alerts catalog + blackbox-exporter SSL alerts + Flyway CONCURRENTLY guard + Playwright auth lifecycle E2E + preflight/verify-deploy scripts + B1-B5 + H2-H5 fixes — ядро завершено 2026-04-25 (`v0.0.0-alpha.14`); G25 CI hot-fixes закрыты 2026-04-26 (`v0.0.0-alpha.15`) |
| M14 | Post-Audit Fixes | ✅ 9 групп — закрытие блокеров first VPS deploy из 4 аудитов (CSO + G26 test + G26 code-review + tech-debt). G1: legacy headers strict default + G2: SHA-pin appleboy/ssh-action + G3: PKCS#8 idempotent JWT keygen + G4 v2: RequiredSecretsValidator (EnvironmentPostProcessor + JUnit-classpath skip) + G5: aiohttp+aiogram bump + G6: SHA-pin 16 actions × 3 workflows + per-job least-privilege permissions + G7: 7 false-pass spec fixes + headman bulk-mark by-design out-of-scope skip (PWA owns flow) + 3 testid additions + G8: burstCapacity prod default 60 (e2e env override 600) + diagnostic test removal + DRY users + G9: notification-web RABBITMQ_PASSWORD/INTERNAL_ISSUER_SECRET + G1 NotificationErrorHandlingIT legacy override — завершён 2026-04-26 (`v0.0.0-alpha.16`) |
| M15 | First VPS Deploy | ✅ Деплой на VPS 2026-04-27. 4 hotfix-коммита: `b8cf106` suppress 3 transitive CVEs + 4 frontend Dockerfile DS-0002 + `c7b2b93` cosign verify case-insensitive + `c3ff148` env regression (INTERNAL_ISSUER_SECRET) + OOM (notification-web mem_limit) + alertmanager basic-auth + `8d7c168` nginx CSP allow Angular inline onload (inlineCritical CSS lazy-load). **Без отдельной milestone-папки** — milestone полностью в git history, lessons learned в M16 backlog |
| M16 | Cleanup Backlog | 📋 BACKLOG — план в `docs/archive/future-ideas.md` § «M16 Cleanup Backlog». Untrack `.claude/`/`.agents/` (~295 случайно committed файлов), phantom submodule fix, актуализация transitive CVE bumps, Node 20→24 upgrade, расширение `.gitignore`. **Milestone-папки `docs/milestones/M16-*/` не существует** — задачи pre-v0.1, не блокируют ничего |

Начало и порядок выполнения: `docs/milestones/README.md`. Workflow
описан там же — per milestone ведётся PLAN + CHECKLIST + NOTES + DECISIONS.
M15-M16 не имеют этой структуры (M15 — целиком в git history,
M16 — pre-v0.1 backlog в `future-ideas.md`).

### URL Layout (v9.0)

Production reverse-proxy nginx на `https://ruttrack.site`:

| Путь | Обслуживает | Назначение |
|------|-------------|------------|
| `/` | 301 → `/login` | INFRA-v9-01 — единая точка входа |
| `/login` | web-panel (Angular SPA, baseHref `/`) | AUTH-v9-01 |
| `/admin/*` | web-panel lazy feature | роль ADMIN |
| `/teacher/*` | web-panel lazy feature | роль TEACHER |
| `/student/*` | web-panel lazy feature | роль STUDENT (включая headman) |
| `/headman/*` | web-panel lazy feature | STUDENT + `is_headman=true` |
| `/app/` | PWA (React + Vite) | INFRA-v9-03 — мобильный клиент |
| `/presentation/` | Landing (static HTML) | INFRA-v9-02 — описание проекта |
| `/api/*` | API Gateway (proxy pass) | backend REST + STOMP WebSocket |

Полная таблица: `docs/product/url-layout.md`.

## Архитектура (кратко)

5 сервисов + Gateway + 2 контейнера Notification:

| Сервис | Порт | Стек | БД |
|--------|------|------|----|
| API Gateway | 8080 | Spring Cloud Gateway | — |
| Auth Service | 9090 | Spring Boot | Redis |
| Academic Service | 9091 | Spring Boot | PostgreSQL (academic_db) + Redis cache |
| Schedule Service | 9092 | Spring Boot | PostgreSQL (schedule_db) |
| Attendance Service | 9093 | Spring Boot | MongoDB (attendance_db) |
| Notification Web | 9094 | Spring Boot WebSocket (STOMP) + Caffeine | MongoDB (notification_db) — stateful history store (M10, NEW-166/167/168) |
| Notification Bot | — | Python Aiogram 3 | Redis (reminder msgs) |

Между сервисами: gRPC. Асинхронные события: RabbitMQ (fanout exchange).

## Правила кодирования (ОБЯЗАТЕЛЬНО)

### Contract-first

- Каждый сервис, публикующий собственный REST API, имеет `*-api-contract` (чистый `java-library`) и `*-app` (Spring Boot)
- Контроллер `implements` интерфейс из контракта. Маппинги ТОЛЬКО в интерфейсе
- Request DTO = Java `record`. Response DTO = класс (для HATEOAS `RepresentationModel`)
- **БЕЗ Lombok в контрактных модулях** (`*-api-contract`). Lombok допустим только в `*-app` (entity, внутренние классы)
- **Исключение:** `api-gateway` — прокси, собственного REST API не публикует, `*-api-contract` не нужен (зафиксировано M09 D2). **Единственное исключение правила.**

### Enum-ы

- В Java: `UPPER_CASE` (например `UserRole.ADMIN`)
- В PostgreSQL: `lowercase` строки (например `'admin'`)
- Конвертация через `LowercaseEnumConverter` с `autoApply=true`
- **НИКОГДА** не используй `@Enumerated(EnumType.ORDINAL)` — только строки

### База данных

- Все значения в PostgreSQL хранятся в **нижнем регистре**
- Миграции через Flyway (`src/main/resources/db/migration/V{N}__description.sql`)
- **НИКОГДА** не редактируй applied миграции (checksum mismatch ломает prod boot). Создавай V{N+1}__patch.sql
- `ddl-auto: validate` — Hibernate только проверяет, НЕ создаёт схему
- Soft delete для пользователей (status = 'archived'), никогда DELETE
- PK: `BIGSERIAL` (Long в Java)
- Временные метки: `TIMESTAMPTZ` (UTC)
- **CREATE INDEX на prod-таблицах** (users / groups / lessons / homeworks / schedule_items / one_off_lessons и аналогичные hot-tables) — обязательно `CREATE INDEX CONCURRENTLY ... IF NOT EXISTS`. Plain `CREATE INDEX` блокирует таблицу на время build'а, что = downtime в prod. CONCURRENTLY требует **single-statement миграцию** (не работает в transaction): добавь `-- ##` в начало файла либо вынеси index в отдельную миграцию. Backstop через `MigrationConcurrentlyTest` в каждом Postgres-сервисе (M13 G21) — fail при baseline cutoff exceeded

### REST API

- HATEOAS Level 3: `EntityModel<T>`, `PagedModel<EntityModel<T>>`, `_links`
- Ошибки: **RFC 9457** Problem Details (единый `ErrorResponse` в
  `shared-web-api`, 10 полей — M11 G0 устранил 5 дублей). Content-Type
  `application/problem+json`. Поля-расширения: `traceId` (MDC),
  `fieldErrors[]` (body validation), `field` + `extras` (BUG-006-2)
- Swagger/OpenAPI: аннотации `@Operation`, `@ApiResponse` в контрактных интерфейсах
- `@ControllerAdvice` архитектура:
  - shared `GlobalExceptionHandler` с `@Order(LOWEST_PRECEDENCE)` —
    catch-all Spring MVC (validation/noHandler/accessDenied/generic) —
    приходит транзитивно через `shared-web` `@AutoConfiguration`
  - per-service `GlobalExceptionHandler` с `@Order(HIGHEST_PRECEDENCE)`
    — только **domain exceptions**. НЕ дублировать Spring MVC handler'ы.
  - Контроллер только бросает исключения, handler маппит в HTTP status.
- PUT = полное обновление, PATCH = частичное (отдельные DTO)

### Пакетная структура (Attendance Service)

- `checkin/` — домен отметок
- `report/` — домен отчётов (ИЗОЛИРОВАН)
- `shared/port/` — интерфейс `AttendanceReadPort` для связи между доменами
- `report/` НИКОГДА не импортирует из `checkin/` напрямую

### Именование

- Пакеты: `ru.rutcampustrack.{service}.{module}`
- REST пути: `/api/{service}/...` (через Gateway)
- gRPC: `ru.rutcampustrack.{service}.grpc`
- Event types: `{domain}.{action}` (например `lesson.started`, `attendance.marked`)

## Структура репозитория

```
rutcampustrack/
├── CLAUDE.md                           ← ЭТОТ ФАЙЛ
├── README.md                           ← entry point репо
├── CHANGELOG.md                        ← Keep a Changelog + SemVer
├── SECURITY.md                         ← security policy (нужен в корне для GitHub)
├── build.gradle.kts                    ← корневой Gradle
├── settings.gradle.kts                 ← все подпроекты
├── docker-compose.yml                  ← PostgreSQL×2, MongoDB, Redis, RabbitMQ (dev)
├── docker-compose.prod.yml             ← production stack (17 контейнеров, M13)
├── docker-compose.e2e.yml              ← e2e CI stack (M13 G25)
├── proto/                              ← gRPC контракты (.proto)
├── event-schemas/                      ← JSON Schema для событий RabbitMQ
├── docs/                               ← вся документация по доменам (после реорганизации 2026-04-27)
│   ├── INDEX.md                        ← оглавление + точка входа для нового разработчика
│   ├── architecture/                   ← architecture, database-schema, event-schemas, websocket-flow, shared-modules-usage
│   ├── api/                            ← api-error-conventions, api-pagination, api-rate-limits, internal-jwt-spec, openapi-conformance
│   ├── auth/                           ← auth-flow
│   ├── operations/
│   │   ├── runbooks/                   ← 10 runbooks: backup-restore, cert-renewal, secret-rotation, и т.д.
│   │   ├── deploy/                     ← prod-deploy-checklist, ci-cd, dockerfile-conventions, nginx-config, resource-limits, container-trust
│   │   └── monitoring/                 ← observability, alerts, logging-conventions
│   ├── security/                       ← SECURITY-AUDIT, security-headers, data-retention-policy
│   ├── testing/                        ← testing, e2e-testing, load-testing, golden-tests
│   ├── performance/                    ← performance-baseline, performance-indexes, caching-strategy, connection-pool-tuning
│   ├── product/                        ← brandbook, design-decisions, job-stories, url-layout, a11y-checklist
│   ├── meta/                           ← contributing, claude-code-guide, info-for-gsd, skills-inventory, phases-plan
│   ├── modules/                        ← (placeholder для shared-модуль README'ов, пока пуст)
│   ├── phase-reports/                  ← phase-{0..7,57..60}-report.md (11 отчётов фаз)
│   ├── archive/                        ← report-before-v0.0.0/ + future-ideas + deferred (старая дока)
│   ├── milestones/                     ← live workspace M01-M14 (PLAN/CHECKLIST/NOTES/DECISIONS per milestone) + _TEMPLATE
│   ├── bug-tracker/                    ← versioned QA bug pool (v1-post-v9.0/ — 9 fixed bugs)
│   └── openapi/                        ← OpenAPI spec snapshots (5 services) — committed для drift-guard
├── gradle/
│   └── libs.versions.toml              ← Version Catalog (shared-модули, M01)
├── services/
│   ├── shared/                         ← shared foundations (M01+M02+M11)
│   │   ├── shared-web-api/                ← M11 G0: чистые DTO (ErrorResponse, FieldError, InvalidParam), java-library без Spring
│   │   ├── shared-web/                    ← M11 G0: Spring Boot starter (handler, JacksonConfig, Customizer, AdminActionAspect) + @AutoConfiguration
│   │   ├── shared-events/                 ← DomainEvent base, publisher/consumer MDC helpers
│   │   ├── shared-logback/                ← JSON appender + masking (Bearer/telegram_id/FCM)
│   │   ├── shared-observability/          ← M04: MdcKeys, MetricNames, BusinessMetrics, Health-индикаторы
│   │   ├── shared-test-containers/        ← java-test-fixtures модуль: ContainerTestBase + gRPC + WireMock
│   │   ├── shared-outbox/                 ← M02: OutboxStorage (Jpa/Mongo) + PublisherJob + CleanupJob + Metrics
│   │   └── shared-security/               ← M03a: Internal JWT validator (PublicKeyProvider + DualModeUserContextFilter + testFixtures InternalJwtTestFactory)
│   ├── api-gateway/                    ← Spring Cloud Gateway
│   ├── auth-service/                   ← JWT, OTP
│   │   ├── auth-api-contract/         ← DTO, AuthApi/WsTicketApi/InternalIssuerApi/InternalWsTicketApi
│   │   └── auth-app/                  ← Spring Boot app
│   ├── academic-service/
│   │   ├── academic-api-contract/     ← DTO, интерфейсы, enum-ы
│   │   └── academic-app/             ← Spring Boot app
│   ├── schedule-service/
│   │   ├── schedule-api-contract/
│   │   └── schedule-app/
│   ├── attendance-service/
│   │   ├── attendance-api-contract/
│   │   └── attendance-app/
│   ├── notification-service/          ← Java WebSocket + Web Push (notification-web)
│   │   ├── notification-api-contract/ ← M10: NotificationApi
│   │   └── notification-app/          ← stateful history store + STOMP
│   └── notification-bot/              ← Python Aiogram 3 (Telegram bot)
├── frontends/
│   ├── mini-app/                       ← React (Telegram Mini App)
│   ├── pwa/                            ← React PWA «RutTrack» (мобильный клиент)
│   ├── web-panel/                      ← Angular 19 (admin/teacher/student/headman, single SPA после v9.0)
│   └── landing/                        ← Static HTML/CSS/JS (no bundler, dist/ = canonical source)
├── tests/
│   ├── e2e/                            ← Playwright (8 specs + axe a11y)
│   └── load/                           ← k6 (bulk-mark.js, geolocation-flood.js)
└── .planning/                          ← GSD-инструмент (НЕ docs!) — STATE/ROADMAP/PROJECT/REQUIREMENTS/MILESTONES + milestones/v{1-9}.0-phases/ архив + research/ + codebase/
```

## Роли в системе

- `ADMIN` — управление пользователями, группами, семестрами
- `TEACHER` — read-only журнал, статистика. БЕЗ Telegram
- `STUDENT` — геоотметка, excuse-тикеты, ДЗ трекер
- `STUDENT + is_headman=true` — расширенные права старосты
- Помощник старосты — студент с делегированными правами (`headman_assistants`)

## Статусы посещаемости

| Статус | В журнале | В статистике |
|--------|-----------|-------------|
| `present` | б | да |
| `absent` | н | да |
| `excused` | у | да (уважит.) |
| `free_attendance` | сп | да (уважит.) |
| `cancelled` | отменена | нет |

## Ключевые бизнес-правила

- Окно геоотметки: 5 мин до пары → вся пара → 5 мин после
- 3 напоминания об отметке: начало, середина, конец пары. После пары — удалить сообщения
- Автоматический `absent` при закрытии пары для неотметившихся
- Будущие пары (planned) и отменённые (cancelled) НЕ влияют на статистику
- Excuse-тикет: студент создаёт → выбирает пары → прикрепляет файлы → файлы пересылаются старосте через Telegram (не хранятся в системе)
- Логин: `student00001`, `teacher00001`. Тестовые: `student`, `teacher`, `admin`
- Порог красной зоны: глобальный (admin) → группа (headman) → предмет (headman)

## Запуск для разработки

```bash
# Инфраструктура
docker compose up -d

# Сборка
$env:JAVA_HOME = "C:\Users\maksd\.jdks\ms-21.0.9"
.\gradlew.bat build

# Проверка
docker compose ps
```

## Детальная документация

- **Точка входа**: `docs/INDEX.md` ← оглавление всей документации, читать первым при незнакомом контексте
- **План всех фаз**: `docs/meta/phases-plan.md` ← ЧИТАТЬ ПЕРЕД НАЧАЛОМ КАЖДОЙ ФАЗЫ
- Архитектура: `docs/architecture/architecture.md`
- Job Stories: `docs/product/job-stories.md`
- Схема БД: `docs/architecture/database-schema.md`
- Дизайн-решения: `docs/product/design-decisions.md` — иконки, анимации, PWA, брендинг
- Реестр skills: `docs/meta/skills-inventory.md` — все установленные Claude Code skills
- Отчёты фаз: `docs/phase-reports/phase-{N}-report.md` (фазы 0..7, 57..60)
- **Bug tracker**: `docs/bug-tracker/` — versioned QA bug pools (v1-post-v9.0/ — 9 fixed)
- **Future ideas / backlog**: `docs/archive/future-ideas.md` — единый source of truth для отложенных идей (M16, pre-v0.1 sweep, G27 tech-debt)
- **Pre-release аудит**: `docs/archive/report-before-v0.0.0/` — 16 отчётов до v0.0.0
- **Milestone workspaces** (live): `docs/milestones/` — папки M01-shared-foundations/ … M14-post-audit-fixes/, каждая с PLAN/CHECKLIST/NOTES/DECISIONS

## Инструкция для Claude Code

При начале работы над новой фазой:
1. Прочитай `docs/meta/phases-plan.md` — там детальное описание что реализовать
2. Прочитай `docs/product/job-stories.md` — бизнес-требования
3. Прочитай `docs/architecture/database-schema.md` — структура БД
4. При работе с фронтендом — прочитай `docs/product/design-decisions.md` для соблюдения единого стиля
5. Создавай код в соответствии с правилами из раздела "Правила кодирования" выше
6. После завершения фазы — обнови `docs/phase-reports/phase-{N}-report.md` и статус в этом файле
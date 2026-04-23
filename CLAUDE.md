# CLAUDE.md — RutCampusTrack

## Что это за проект

RutCampusTrack — микросервисная система учёта посещаемости для вуза РУТ МИИТ. Monorepo на Java 21 + Spring Boot 3.4 + Gradle.

## Текущий статус

- **v1.0**: ЗАВЕРШЕНА (Auth Service + API Gateway) — фазы 1.1-1.4, 26 тестов
- **v2.0**: ЗАВЕРШЕНА (Academic Service) — фазы 5-9, 50 тестов
- **v3.0**: ЗАВЕРШЕНА (Schedule Service) — фазы 10-14, 55 тестов
- **v4.0**: ЗАВЕРШЕНА (Attendance Service) — фазы 15-19, ~95 тестов
- **v5.0**: ЗАВЕРШЕНА (Notification Service — Web + Bot) — фазы 20-26, ~128 тестов (20 Java + 108 Python)
- **v6.0**: ЗАВЕРШЕНА (PWA + Web Push) — фазы 27-32
- **v7.0**: ЗАВЕРШЕНА (Frontends — Mini App, Web Panel, Landing) — фазы 33-40
- **v8.0**: ЗАВЕРШЕНА (CI/CD, Deployment & Documentation) — фазы 41-48
- **v9.0**: ЗАВЕРШЕНА (Frontend Unification — Single Login & Role-Based Web Clients) — фазы 49-57 + Phase 58 (BUG-006 Admin Fixes) + Phase 59 (Excuse Tickets Backend) + Phase 60 (Headman Schedule Management)
- **v0.0.0**: В РАБОТЕ (Pre-release hardening по аудиту) — 12 milestones в `docs/milestones/`, активный — см. `docs/milestones/README.md`
- Полный план v0.0.0: `docs/report-before-v0.0.0/99-executive-summary.md`
- Исходный аудит: `docs/report-before-v0.0.0/` (16 отчётов, OWNER-ANSWERS.md, COVERAGE-AUDIT.md)
- Полный план v1.0-v9.0: `.planning/ROADMAP.md`, отчёты: `docs/phase-{N}-report.md`

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
| M10 | Notification History | Stateful notification-web + MongoDB notification_db + notification_history TTL 30d + Caffeine unread-count + NotificationCenter backend pagination |
| M11 | OpenAPI Polish | SharedOpenApiCustomizer наполнение + @Schema на DTO + nginx basic-auth на prod /swagger-ui + OpenAPI↔runtime conformance CI |
| M12 | Auth Contract-first Refactor | `auth-api-contract` модуль + AuthApi/WsTicketApi/InternalAuthApi interfaces + DTO migration + controller implements + убирает последнее исключение Contract-first правила |

Начало и порядок выполнения: `docs/milestones/README.md`. Workflow
описан там же — per milestone ведётся PLAN + CHECKLIST + NOTES + DECISIONS.

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

Полная таблица: `docs/url-layout.md`.

## Архитектура (кратко)

5 сервисов + Gateway + 2 контейнера Notification:

| Сервис | Порт | Стек | БД |
|--------|------|------|----|
| API Gateway | 8080 | Spring Cloud Gateway | — |
| Auth Service | 9090 | Spring Boot | Redis |
| Academic Service | 9091 | Spring Boot | PostgreSQL (academic_db) + Redis cache |
| Schedule Service | 9092 | Spring Boot | PostgreSQL (schedule_db) |
| Attendance Service | 9093 | Spring Boot | MongoDB (attendance_db) |
| Notification Web | 9094 | Spring Boot WebSocket (STOMP) + Caffeine | MongoDB (notification_db) — stateful history store в M10 (NEW-166/167/168); до M10 stateless event forwarder |
| Notification Bot | — | Python Aiogram 3 | Redis (reminder msgs) |

Между сервисами: gRPC. Асинхронные события: RabbitMQ (fanout exchange).

## Правила кодирования (ОБЯЗАТЕЛЬНО)

### Contract-first

- Каждый сервис, публикующий собственный REST API, имеет `*-api-contract` (чистый `java-library`) и `*-app` (Spring Boot)
- Контроллер `implements` интерфейс из контракта. Маппинги ТОЛЬКО в интерфейсе
- Request DTO = Java `record`. Response DTO = класс (для HATEOAS `RepresentationModel`)
- **БЕЗ Lombok в контрактных модулях** (`*-api-contract`). Lombok допустим только в `*-app` (entity, внутренние классы)
- **Исключения:**
  - `api-gateway` — прокси, собственного REST API не публикует, `*-api-contract` не нужен (зафиксировано M09 D2). **Единственное постоянное исключение.**
  - `auth-service` — **временный нарушитель** (01 P0-1, owner-решение 01-Q1).
    Планирование refactor'а — M12 (в v0.0.0 scope'е); фактическая реализация
    отложена в v0.1 (см. `docs/future-ideas.md` → «Auth API contract-first
    refactor»). До завершения рефакторинга нарушение acceptable —
    контракт зафиксирован в `docs/openapi/auth.json` (ручная конкатенация).

### Enum-ы

- В Java: `UPPER_CASE` (например `UserRole.ADMIN`)
- В PostgreSQL: `lowercase` строки (например `'admin'`)
- Конвертация через `LowercaseEnumConverter` с `autoApply=true`
- **НИКОГДА** не используй `@Enumerated(EnumType.ORDINAL)` — только строки

### База данных

- Все значения в PostgreSQL хранятся в **нижнем регистре**
- Миграции через Flyway (`src/main/resources/db/migration/V{N}__description.sql`)
- `ddl-auto: validate` — Hibernate только проверяет, НЕ создаёт схему
- Soft delete для пользователей (status = 'archived'), никогда DELETE
- PK: `BIGSERIAL` (Long в Java)
- Временные метки: `TIMESTAMPTZ` (UTC)

### REST API

- HATEOAS Level 3: `EntityModel<T>`, `PagedModel<EntityModel<T>>`, `_links`
- Ошибки: RFC 7807 Problem Details (`ErrorResponse` record)
- Swagger/OpenAPI: аннотации `@Operation`, `@ApiResponse` в контрактных интерфейсах
- `@ControllerAdvice` — централизованная обработка ошибок, контроллер только бросает исключения
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
├── build.gradle.kts                    ← корневой Gradle
├── settings.gradle.kts                 ← все подпроекты
├── docker-compose.yml                  ← PostgreSQL×2, MongoDB, Redis, RabbitMQ
├── proto/                              ← gRPC контракты (.proto)
├── event-schemas/                      ← JSON Schema для событий RabbitMQ
├── docs/
│   ├── phase-0-report.md              ← отчёт фазы 0 (контекст проекта)
│   ├── architecture.md                ← детальная архитектура
│   ├── job-stories.md                 ← все user/job stories
│   └── database-schema.md            ← схема БД
├── gradle/
│   └── libs.versions.toml              ← Version Catalog (shared-модули, M01)
├── services/
│   ├── shared/                         ← shared foundations (M01+M02)
│   │   ├── shared-web/                    ← RFC 9457 ErrorResponse, handlers, validation, JacksonConfig
│   │   ├── shared-events/                 ← DomainEvent base, publisher/consumer MDC helpers
│   │   ├── shared-logback/                ← JSON appender + masking (Bearer/telegram_id/FCM)
│   │   ├── shared-test-containers/        ← java-test-fixtures модуль: ContainerTestBase + gRPC + WireMock
│   │   ├── shared-outbox/                 ← M02: OutboxStorage (Jpa/Mongo) + PublisherJob + CleanupJob + Metrics
│   │   └── shared-security/               ← M03a: Internal JWT validator (PublicKeyProvider + DualModeUserContextFilter + testFixtures InternalJwtTestFactory)
│   ├── api-gateway/                    ← Spring Cloud Gateway
│   ├── auth-service/                   ← JWT, OTP
│   ├── academic-service/
│   │   ├── academic-api-contract/     ← DTO, интерфейсы, enum-ы
│   │   └── academic-app/             ← Spring Boot app
│   ├── schedule-service/
│   │   ├── schedule-api-contract/
│   │   └── schedule-app/
│   ├── attendance-service/
│   │   ├── attendance-api-contract/
│   │   └── attendance-app/
│   ├── notification-web/              ← Java WebSocket + Web Push
│   └── notification-bot/              ← Python Aiogram
└── frontends/
    ├── mini-app/                       ← React (Telegram Mini App)
    ├── pwa/                            ← React PWA «RutTrack» (мобильный клиент)
    ├── web-panel/                      ← Angular (админка)
    └── landing/                        ← HTML + CSS
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

- **План всех фаз**: `docs/phases-plan.md` ← ЧИТАТЬ ПЕРЕД НАЧАЛОМ КАЖДОЙ ФАЗЫ
- Архитектура: `docs/architecture.md`
- Job Stories: `docs/job-stories.md`
- Схема БД: `docs/database-schema.md`
- Дизайн-решения: `docs/design-decisions.md` — иконки, анимации, PWA, брендинг
- Реестр skills: `docs/skills-inventory.md` — все установленные Claude Code skills
- Отчёт Фазы 0: `docs/phase-0-report.md`

## Инструкция для Claude Code

При начале работы над новой фазой:
1. Прочитай `docs/phases-plan.md` — там детальное описание что реализовать
2. Прочитай `docs/job-stories.md` — бизнес-требования
3. Прочитай `docs/database-schema.md` — структура БД
4. При работе с фронтендом — прочитай `docs/design-decisions.md` для соблюдения единого стиля
5. Создавай код в соответствии с правилами из раздела "Правила кодирования" выше
6. После завершения фазы — обнови `docs/phase-{N}-report.md` и статус в этом файле
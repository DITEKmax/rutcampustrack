# RutCampusTrack

Микросервисная система учёта посещаемости для РУТ (МИИТ) — геоотметка на парах, журнал, excuse-тикеты, расписание, уведомления через Telegram и Web Push.

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4-green)
![Angular 19](https://img.shields.io/badge/Angular-19-DD0031)
![React 18](https://img.shields.io/badge/React-18-61DAFB)
![Python 3.12](https://img.shields.io/badge/Python-3.12-blue)
![Docker Compose](https://img.shields.io/badge/Docker-Compose-2496ED)
![Status](https://img.shields.io/badge/status-v0.0.0--alpha.16%20%E2%80%94%20deployed-success)
![CI](https://github.com/maksd/rutcampustrack/actions/workflows/ci.yml/badge.svg)

---

## Содержание

1. [Что это](#что-это)
2. [Текущий статус](#текущий-статус)
3. [Стек](#стек)
4. [Архитектура](#архитектура)
5. [Структура репозитория](#структура-репозитория)
6. [Быстрый старт (dev)](#быстрый-старт-dev)
7. [URL layout (prod)](#url-layout-prod)
8. [Тестирование](#тестирование)
9. [CI / CD](#ci--cd)
10. [Развёртывание (production)](#развёртывание-production)
11. [Безопасность](#безопасность)
12. [Документация](#документация)
13. [Вклад в проект](#вклад-в-проект)
14. [Лицензия](#лицензия)

> Если ты впервые в репо и хочешь сразу понять *что лежит и где* — открой
> [`docs/INDEX.md`](docs/INDEX.md). Это оглавление всей документации
> (55 файлов в 14 категориях).

---

## Что это

**RutCampusTrack** — учётная система посещаемости вуза. Студент отмечается на паре геолокацией с телефона (PWA или Telegram Mini App), преподаватель видит журнал в браузере, староста управляет excuse-тикетами и порогами «красной зоны», админ ведёт пользователей и расписание. Уведомления приходят в Telegram-бот и в PWA через Web Push.

Проект построен как **monorepo** с 5 Java-микросервисами + API Gateway + 2 контейнерами Notification (Java WebSocket + Python Telegram bot) + 4 фронтенда (Angular SPA, React PWA, React Mini App, статичный лендинг). Один внешний порт — nginx `:443` в проде, `:8080` в dev.

Бизнес-правила и пользовательские сценарии: [`docs/product/job-stories.md`](docs/product/job-stories.md). Подробная архитектура: [`docs/architecture/architecture.md`](docs/architecture/architecture.md).

---

## Текущий статус

**В проде:** `v0.0.0-alpha.16` (deployed на VPS `2026-04-27`, M15).

| Версия | Содержание | Статус |
|--------|------------|--------|
| **v1.0** | Auth Service + API Gateway (фазы 1–4) | ✅ |
| **v2.0** | Academic Service (фазы 5–9) | ✅ |
| **v3.0** | Schedule Service (фазы 10–14) | ✅ |
| **v4.0** | Attendance Service (фазы 15–19) | ✅ |
| **v5.0** | Notification Web + Telegram Bot (фазы 20–26) | ✅ |
| **v6.0** | PWA + Web Push (фазы 27–32) | ✅ |
| **v7.0** | Frontends — Mini App, Web Panel, Landing (фазы 33–40) | ✅ |
| **v8.0** | CI/CD, Deployment, Documentation (фазы 41–48) | ✅ |
| **v9.0** | Frontend Unification — Single Login + role-based SPA (фазы 49–61) | ✅ |
| **v0.0.0** | Pre-release hardening (M01–M14) | ✅ shipped |
| **M15** | First VPS Deploy + 4 hotfix-коммита | ✅ live |
| **M16** | Cleanup backlog (untrack `.claude/`, CVE bumps, Node 20→24, …) | 📋 backlog |

Детальная карта milestone'ов M01–M16: [`CLAUDE.md` § «v0.0.0 Milestones»](CLAUDE.md). Live workspace milestone'ов: [`docs/milestones/`](docs/milestones/) (PLAN/CHECKLIST/NOTES/DECISIONS на каждый). Journey каждой фазы v1.0–v9.0: [`docs/phase-reports/`](docs/phase-reports/).

---

## Стек

| Слой | Технология |
|------|-----------|
| Backend (бизнес-сервисы) | Java 21 + Spring Boot 3.4 |
| API Gateway | Spring Cloud Gateway 4.x |
| Межсервисный sync | gRPC (`grpc-spring-boot-starter`) |
| Межсервисный async | RabbitMQ 3.13 (fanout exchange + outbox pattern) |
| Telegram Bot | Python 3.12 + Aiogram 3.x |
| Сборка backend | Gradle 8.12 (Kotlin DSL) + Version Catalog |
| Postgres | PostgreSQL 16 (Flyway migrations) |
| Mongo | MongoDB 7 (replica-set, TTL indexes) |
| Кэш / OTP | Redis 7 |
| Контейнеризация | Docker + Docker Compose v2 |
| CI/CD | GitHub Actions + GHCR + cosign keyless |
| Web Panel | Angular 19 (single SPA, lazy-loaded role features) |
| PWA «RutTrack» | React + Vite + TypeScript + Workbox (service worker) |
| Mini App (Telegram) | React + Vite + TypeScript |
| Landing | Static HTML/CSS (no bundler) |
| Reverse Proxy (prod) | Nginx + Let's Encrypt (certbot auto-renew) |
| Observability | OpenTelemetry → Tempo + Loki + Prometheus + Grafana + Alertmanager |

---

## Архитектура

Принципы: **database-per-service**, **contract-first** (gRPC `.proto` + OpenAPI), **single external port**, **internal JWT** между сервисами (issued by Gateway, verified locally через public-key — see M03a).

```
+----------------------------------------------------------------------+
|                        DOCKER PRIVATE NETWORK                        |
|                                                                      |
|  Клиенты --> [API Gateway :8080]  Spring Cloud Gateway               |
|  (Web Panel, PWA, Mini App)                                          |
|               |  JWT-валидация (публичный ключ, локально)            |
|               |  Internal-JWT issue (token-exchange)                 |
|               |  Маршрутизация по пути + Rate Limiting              |
|               |  HttpOnly cookie + WS-ticket flow (M03b)             |
|               |                                                      |
|               +---> [Auth Service :9090]          -> Redis (OTP, JWT)|
|               +---> [Academic Service :9091]      -> PostgreSQL      |
|               |                                      + Redis (cache) |
|               +---> [Schedule Service :9092]      -> PostgreSQL      |
|               +---> [Attendance Service :9093]    -> MongoDB         |
|                      +-- checkin/  (домен отметок)                   |
|                      +-- report/   (домен отчётов, изолирован)       |
|                                                                      |
|  [Notification Web :9094]  Java                                      |
|     +-- WebSocket (STOMP) -> Web Panel, PWA (real-time)              |
|     +-- Web Push          -> Service Worker -> PWA (background)      |
|     +-- REST: /api/notifications/** (history, unread, subscribe)     |
|     +-- Caffeine cache (unread-count 30s)                            |
|  [Notification Bot]        Python -- Telegram уведомления            |
|                                                                      |
|  [RabbitMQ :5672]  <-- события от Schedule, Attendance, Auth         |
|  [Redis :6379]     <-- OTP, кэш Academic, reminder msgs, VAPID, RBAC |
+----------------------------------------------------------------------+
```

### Матрица: сервис → хранилище

| Сервис | PostgreSQL | MongoDB | Redis | RabbitMQ |
|--------|-----------|---------|-------|----------|
| Auth Service | — | — | OTP, JWT keys | Publish: `otp.requested` |
| Academic Service | `academic_db` | — | RBAC + subject cache | Publish: `group.updated`, `semester.archived` |
| Schedule Service | `schedule_db` | — | — | Publish: `lesson.started`, `lesson.closed`, `lesson.cancelled` |
| Attendance Service | — | `attendance_db` | — | Publish: `attendance.marked`, `attendance.session.closed`, `late.checkin` |
| Notification Web | — | `notification_db` (history TTL 30d, push_subscriptions) | VAPID keys, unread-count cache | Consume: все события |
| Notification Bot | — | — | reminder msgs (3 напоминания на пару) | Consume: все события |

> Полные event-схемы: [`event-schemas/`](event-schemas/) + [`docs/architecture/event-schemas.md`](docs/architecture/event-schemas.md). gRPC контракты: [`proto/`](proto/). STOMP WebSocket flow: [`docs/architecture/websocket-flow.md`](docs/architecture/websocket-flow.md).

### Contract-first layout

Каждый сервис, публикующий собственный REST API, разделён на два модуля:

- **`*-api-contract`** — чистый `java-library`: DTO (Java records для request, классы для response), интерфейсы контроллеров с `@Operation`/`@ApiResponse`/`@RequestMapping`, enum-ы. **Без Lombok, без Spring Boot.**
- **`*-app`** — Spring Boot приложение: контроллеры `implements` интерфейс из контракта, маппинги ТОЛЬКО в интерфейсе, доменная логика, Hibernate entity, Lombok допустим.

Исключение: `api-gateway` — прокси, собственного REST API не публикует, контрактного модуля нет.

---

## Структура репозитория

```
rutcampustrack/
├── README.md                         ← этот файл
├── CLAUDE.md                         ← инструкции для AI-агентов + статус milestone'ов
├── CHANGELOG.md                      ← Keep a Changelog + SemVer
├── SECURITY.md                       ← security policy (vulnerability disclosure)
├── settings.gradle.kts               ← все backend-подпроекты
├── build.gradle.kts                  ← корневой Gradle
├── docker-compose.yml                ← dev infrastructure (Postgres×2, Mongo, Redis, RabbitMQ)
├── docker-compose.override.yml       ← dev overrides (auto-loaded)
├── docker-compose.prod.yml           ← production stack (17 контейнеров)
├── docker-compose.e2e.yml            ← e2e CI stack (M13 G25)
├── docker-compose.test-restore.yml   ← backup/restore drill
├── renovate.json                     ← Renovate config (monthly digest-bump)
├── skills-lock.json                  ← Claude Code skills lockfile
│
├── proto/                            ← gRPC контракты (.proto)
├── event-schemas/                    ← JSON Schema для событий RabbitMQ
│
├── services/
│   ├── shared/                       ← shared foundations (M01+M02+M11)
│   │   ├── shared-web-api/           ← чистые DTO (ErrorResponse, FieldError, InvalidParam)
│   │   ├── shared-web/               ← Spring Boot starter (handler, JacksonConfig, AdminActionAspect)
│   │   ├── shared-events/            ← DomainEvent base, publisher/consumer MDC helpers
│   │   ├── shared-logback/           ← JSON appender + masking (Bearer/telegram_id/FCM)
│   │   ├── shared-observability/     ← MdcKeys, MetricNames, BusinessMetrics, health
│   │   ├── shared-test-containers/   ← ContainerTestBase + gRPC + WireMock (test-fixtures)
│   │   ├── shared-outbox/            ← OutboxStorage (Jpa/Mongo) + PublisherJob + CleanupJob
│   │   └── shared-security/          ← Internal JWT validator + DualModeUserContextFilter
│   ├── api-gateway/                  ← Spring Cloud Gateway (:8080)
│   ├── auth-service/                 ← JWT, OTP, Redis (:9090)
│   │   ├── auth-api-contract/        ← AuthApi/WsTicketApi/InternalIssuerApi/InternalWsTicketApi
│   │   └── auth-app/
│   ├── academic-service/             ← (:9091) PostgreSQL + Redis cache
│   │   ├── academic-api-contract/
│   │   └── academic-app/
│   ├── schedule-service/             ← (:9092) PostgreSQL
│   │   ├── schedule-api-contract/
│   │   └── schedule-app/
│   ├── attendance-service/           ← (:9093) MongoDB, checkin/ + report/ домены
│   │   ├── attendance-api-contract/
│   │   └── attendance-app/
│   ├── notification-service/         ← (:9094) WebSocket + Web Push + history (M10)
│   │   ├── notification-api-contract/
│   │   └── notification-app/
│   └── notification-bot/             ← Python Aiogram 3 (Telegram)
│
├── frontends/
│   ├── web-panel/                    ← Angular 19 (admin/teacher/student/headman, single SPA, v9.0)
│   ├── pwa/                          ← React PWA «RutTrack» (мобильный клиент, /app/)
│   ├── mini-app/                     ← React Telegram Mini App
│   └── landing/                      ← Static HTML/CSS/JS (/presentation/)
│
├── nginx/                            ← reverse-proxy + Let's Encrypt config + init scripts
├── infra/                            ← Prometheus/Loki/Grafana/Tempo/Alertmanager configs
├── scripts/                          ← preflight-deploy, verify-deploy, backup, restore, smoke
├── tests/
│   ├── e2e/                          ← Playwright (8 specs + axe a11y)
│   └── load/                         ← k6 (bulk-mark.js, geolocation-flood.js)
│
├── gradle/
│   └── libs.versions.toml            ← Version Catalog (shared-модули pin)
├── .github/
│   ├── workflows/                    ← ci.yml, deploy.yml, security.yml, openapi-drift.yml, coverage.yml
│   └── pull_request_template.md
│
├── docs/                             ← вся продуктовая документация (см. docs/INDEX.md)
└── .planning/                        ← GSD-планировщик (НЕ редактировать вручную)
```

Полная карта `docs/` по категориям: [`docs/INDEX.md`](docs/INDEX.md).

---

## Быстрый старт (dev)

### Требования

- **Java 21** (Microsoft OpenJDK или Temurin). На Windows экспортируй `JAVA_HOME` перед `gradlew`.
- **Docker** + Docker Compose v2.
- **Node.js 20+** (для фронтендов).
- **Python 3.12+** (только если запускаешь `notification-bot` локально — иначе только в Docker).

### Поднять

```bash
# 1. Клонировать
git clone https://github.com/maksd/rutcampustrack.git
cd rutcampustrack

# 2. Поднять инфраструктуру (PostgreSQL × 2, MongoDB, Redis, RabbitMQ)
docker compose up -d
docker compose ps              # все должны быть healthy

# 3. Собрать все Java-сервисы
./gradlew build                # Linux/macOS
.\gradlew.bat build            # Windows

# 4. Запустить отдельный сервис
./gradlew :services:auth-service:auth-app:bootRun

# 5. Запустить фронтенд (PWA)
cd frontends/pwa
npm install
npm run dev                    # http://localhost:5173

# 6. Запустить web-panel
cd frontends/web-panel
npm install
npm start                      # http://localhost:4200
```

Подробный dev-setup: [`docs/operations/runbooks/dev-setup.md`](docs/operations/runbooks/dev-setup.md).

### Порты (dev)

| Сервис | Порт | Доступ |
|--------|------|--------|
| API Gateway | 8080 | **единственный** внешний для backend |
| Auth Service | 9090 | внутренний |
| Academic Service | 9091 | внутренний |
| Schedule Service | 9092 | внутренний |
| Attendance Service | 9093 | внутренний |
| Notification Web | 9094 | внутренний (STOMP через Gateway) |
| Web Panel | 4200 | dev (Angular CLI) |
| PWA | 5173 | dev (Vite) |
| Mini App | 5174 | dev (Vite) |
| RabbitMQ Management | 15672 | dev only |

### Тестовые учётные записи

`student`, `teacher`, `admin` — пароли в seed-данных Auth Service. Прод-логины формата `student00001`, `teacher00001`.

---

## URL layout (prod)

Production reverse-proxy nginx на `https://ruttrack.site`:

| Путь | Обслуживает | Назначение |
|------|-------------|------------|
| `/` | 301 → `/login` | единая точка входа (INFRA-v9-01) |
| `/login` | web-panel (Angular SPA, `baseHref=/`) | AUTH-v9-01 |
| `/admin/*` | web-panel lazy feature | роль `ADMIN` |
| `/teacher/*` | web-panel lazy feature | роль `TEACHER` |
| `/student/*` | web-panel lazy feature | роль `STUDENT` (вкл. headman) |
| `/headman/*` | web-panel lazy feature | `STUDENT` + `is_headman=true` |
| `/app/` | PWA (React + Vite) | мобильный клиент |
| `/presentation/` | Landing (static) | описание проекта |
| `/api/*` | API Gateway (proxy_pass) | backend REST + STOMP WebSocket |

Полная таблица + CORS origins + container names: [`docs/product/url-layout.md`](docs/product/url-layout.md).

---

## Тестирование

```bash
# Java backend (unit + integration через Testcontainers)
./gradlew test                                          # все сервисы
./gradlew :services:academic-service:academic-app:test  # отдельный
./gradlew jacocoTestReport                              # coverage report (per-module ratchet)

# Python notification-bot (pytest + pytest-cov)
cd services/notification-bot
pip install -r requirements-test.txt
python -m pytest tests/ -v --cov

# Frontend
cd frontends/pwa && npm test         # Vitest (50% baseline)
cd frontends/web-panel && npm test   # Karma/Jasmine

# E2E (Playwright + axe a11y)
cd tests/e2e
npm install
npx playwright test                  # 8 specs

# Load (k6)
cd tests/load
k6 run bulk-mark.js
```

**Test footprint:** ~350+ Java tests, ~108 Python, Vitest 50%/PWA 38% baseline, pytest-cov 50%/handlers 70%, JaCoCo per-module ratchet (M08).

Конвенции тестирования: [`docs/testing/testing.md`](docs/testing/testing.md). E2E детали: [`docs/testing/e2e-testing.md`](docs/testing/e2e-testing.md). Load profile: [`docs/testing/load-testing.md`](docs/testing/load-testing.md). Golden contract tests: [`docs/testing/golden-tests.md`](docs/testing/golden-tests.md).

---

## CI / CD

5 workflow в `.github/workflows/`:

| Workflow | Триггер | Что делает |
|----------|---------|------------|
| **`ci.yml`** | push, PR | Java build+test, Python lint+test, frontend build+test (3 параллельных job) |
| **`coverage.yml`** | push, PR | JaCoCo + diff-cover 80% gate (M08) |
| **`security.yml`** | push, PR, weekly | Trivy (SHA-pin), Gitleaks, OSV scanner, SBOM |
| **`openapi-drift.yml`** | PR | OpenAPI spec snapshot conformance (M11) |
| **`deploy.yml`** | push в `main` | Build 11 Docker-образов → cosign keyless sign → push в GHCR → SSH в VPS → `docker compose pull && up -d` |

GHCR images:

```
ghcr.io/maksd/rutcampustrack/{api-gateway,auth-service,academic-service,
                              schedule-service,attendance-service,
                              notification-web,notification-bot,
                              pwa-nginx,mini-app-nginx,
                              web-panel-nginx,landing-nginx}:latest
```

Все 11 образов **digest-pin** + **cosign verified** на pull (M06 + M13 G25).
Renovate / Dependabot обеспечивают monthly digest-bump.

Подробности: [`docs/operations/deploy/ci-cd.md`](docs/operations/deploy/ci-cd.md). Container trust (cosign keyless verify): [`docs/operations/deploy/container-trust.md`](docs/operations/deploy/container-trust.md). Image signing: [`docs/operations/runbooks/image-signing-verification.md`](docs/operations/runbooks/image-signing-verification.md).

---

## Развёртывание (production)

> **Pre-deploy checklist** обязательно: [`docs/operations/deploy/prod-deploy-checklist.md`](docs/operations/deploy/prod-deploy-checklist.md).

### Требования к серверу

- Ubuntu 22.04+ (или другой Linux с systemd)
- Docker + Docker Compose v2
- **4 GB RAM** (memory budget расписан в [`docs/operations/deploy/resource-limits.md`](docs/operations/deploy/resource-limits.md))
- Порты `80` и `443` открыты в firewall
- Домен с DNS A-записью на IP сервера

### GitHub Secrets для `deploy.yml`

| Secret | Описание |
|--------|----------|
| `VPS_HOST` | IP / hostname VPS |
| `VPS_USER` | SSH user (например `deploy`) |
| `SSH_PRIVATE_KEY` | приватный SSH-ключ |

### `.env.prod` на VPS

В `/opt/rutcampustrack/.env.prod` (никогда не коммитить — в `.gitignore`):

```bash
# --- Postgres ---
POSTGRES_ACADEMIC_PASSWORD=...
POSTGRES_SCHEDULE_PASSWORD=...

# --- RabbitMQ ---
RABBITMQ_USER=rct_user
RABBITMQ_PASSWORD=...

# --- Internal JWT (M03a) ---
INTERNAL_ISSUER_SECRET=...

# --- Telegram ---
BOT_TOKEN=...
TMA_BOT_TOKEN=...
MINI_APP_URL=https://t.me/YourBot/your_mini_app
MINI_APP_WEB_URL=https://ruttrack.site/mini-app/

# --- SSL / nginx ---
DOMAIN=ruttrack.site
CERTBOT_EMAIL=ops@example.com

# --- Web Push (VAPID) ---
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...
VAPID_SUBJECT=mailto:noreply@ruttrack.site

# --- Observability basic-auth (Prometheus/Grafana/Alertmanager/Swagger) ---
OBS_BASIC_AUTH_USER=...
OBS_BASIC_AUTH_PASSWORD_HASH=...
```

Полный список + валидация: `scripts/validate-env-prod.sh` (M13). Schema-валидация запускается автоматически на boot Spring через `RequiredSecretsValidator` (M14 G4).

### Первое развёртывание

```bash
# на VPS
ssh deploy@your-server-ip
git clone https://github.com/maksd/rutcampustrack.git /opt/rutcampustrack
cd /opt/rutcampustrack

nano .env.prod                                    # см. шаблон выше
./scripts/validate-env-prod.sh                    # проверить .env.prod (M13 G24)

# Авторизоваться в GHCR (требуется PAT с read:packages)
echo $GITHUB_TOKEN | docker login ghcr.io -u maksd --password-stdin

# Verify cosign signatures (M06)
docker compose -f docker-compose.prod.yml config --images | xargs -n1 \
    cosign verify --certificate-identity-regexp 'github.com/maksd/rutcampustrack' \
                  --certificate-oidc-issuer https://token.actions.githubusercontent.com

# Первичный SSL-сертификат
chmod +x nginx/scripts/init-letsencrypt.sh
./nginx/scripts/init-letsencrypt.sh

# Преflight + запуск
./scripts/preflight-deploy.sh                     # health-проверки до старта
docker compose -f docker-compose.prod.yml up -d
./scripts/verify-deploy.sh                        # post-deploy smoke
```

Запасные / связанные runbook'и:

- **Backup + restore** (с GPG): [`docs/operations/runbooks/backup-restore.md`](docs/operations/runbooks/backup-restore.md) + `scripts/backup.sh`, `scripts/restore.sh`, `scripts/test-restore.sh`.
- **Cert renewal** (Let's Encrypt + первый деплой SSL): [`docs/operations/runbooks/cert-renewal.md`](docs/operations/runbooks/cert-renewal.md).
- **Secret rotation** (quarterly): [`docs/operations/runbooks/secret-rotation.md`](docs/operations/runbooks/secret-rotation.md).
- **Bot polling → webhook**: [`docs/operations/runbooks/bot-webhook-migration.md`](docs/operations/runbooks/bot-webhook-migration.md).
- **Mongo TTL/индексы**: [`docs/operations/runbooks/mongo-indexes-verify.md`](docs/operations/runbooks/mongo-indexes-verify.md).
- **Flyway dry-run**: [`docs/operations/runbooks/migration-testing.md`](docs/operations/runbooks/migration-testing.md).
- **Loki major upgrade**: [`docs/operations/runbooks/loki-major-upgrade.md`](docs/operations/runbooks/loki-major-upgrade.md).
- **Swagger в проде**: [`docs/operations/runbooks/swagger-prod-access.md`](docs/operations/runbooks/swagger-prod-access.md).

### Обновление

Push в `main` → workflow `deploy.yml` сам собирает 11 образов, подписывает (cosign), пушит в GHCR, делает `pull && up -d --remove-orphans` через SSH. Ручное обновление не требуется.

### Observability (prod)

OpenTelemetry → Tempo (traces, retention 14d), Loki (logs), Prometheus (metrics) → Grafana (`business-kpis` dashboard) + Alertmanager → Telegram bot (18 alert'ов, каталог в [`docs/operations/monitoring/alerts.md`](docs/operations/monitoring/alerts.md)). Logging-конвенции (JSON + masking Bearer/telegram_id/FCM): [`docs/operations/monitoring/logging-conventions.md`](docs/operations/monitoring/logging-conventions.md). Полная инструкция: [`docs/operations/monitoring/observability.md`](docs/operations/monitoring/observability.md).

---

## Безопасность

- **Vulnerability disclosure** — [`SECURITY.md`](SECURITY.md). Не открывайте публичные issue для security findings.
- **Auth flow** (login / OTP / refresh / logout / TMA / cookie / WS-ticket): [`docs/auth/auth-flow.md`](docs/auth/auth-flow.md).
- **Internal JWT spec** (M03a token-exchange): [`docs/api/internal-jwt-spec.md`](docs/api/internal-jwt-spec.md).
- **Rate limits** (per route): [`docs/api/api-rate-limits.md`](docs/api/api-rate-limits.md).
- **CSP / HSTS / security headers**: [`docs/security/security-headers.md`](docs/security/security-headers.md).
- **Pre-release security audit** (16 отчётов): [`docs/archive/report-before-v0.0.0/`](docs/archive/report-before-v0.0.0/) + сводный [`docs/security/SECURITY-AUDIT.md`](docs/security/SECURITY-AUDIT.md).
- **Data retention policy** (TTL, GDPR-style): [`docs/security/data-retention-policy.md`](docs/security/data-retention-policy.md).

Supply-chain: Trivy (SHA-pin), Gitleaks, OSV scanner, SBOM + cosign keyless signing. Renovate monthly digest-bump для всех 11 образов.

---

## Документация

**Точка входа:** [`docs/INDEX.md`](docs/INDEX.md) — оглавление 55 файлов в 14 категориях.

| Категория | Что внутри |
|-----------|------------|
| [`docs/architecture/`](docs/architecture/) | architecture, database-schema, event-schemas, websocket-flow, shared-modules-usage |
| [`docs/api/`](docs/api/) | error conventions (RFC 9457), pagination, rate-limits, internal-jwt-spec, openapi-conformance |
| [`docs/auth/`](docs/auth/) | auth-flow (login/OTP/refresh/logout/TMA/cookie/WS-ticket) |
| [`docs/operations/runbooks/`](docs/operations/runbooks/) | 10 runbook'ов: backup-restore, cert-renewal, secret-rotation, bot-webhook-migration, … |
| [`docs/operations/deploy/`](docs/operations/deploy/) | prod-deploy-checklist, ci-cd, dockerfile-conventions, nginx-config, resource-limits, container-trust, admin-scripts |
| [`docs/operations/monitoring/`](docs/operations/monitoring/) | observability, alerts (18), logging-conventions |
| [`docs/security/`](docs/security/) | SECURITY-AUDIT, security-headers, data-retention-policy |
| [`docs/testing/`](docs/testing/) | testing, e2e-testing (Playwright + axe), load-testing (k6), golden-tests |
| [`docs/performance/`](docs/performance/) | baseline (k6), composite indexes, caching-strategy, connection-pool-tuning |
| [`docs/product/`](docs/product/) | brandbook, design-decisions, job-stories, url-layout, a11y-checklist |
| [`docs/meta/`](docs/meta/) | contributing, claude-code-guide, info-for-gsd, skills-inventory, phases-plan |
| [`docs/phase-reports/`](docs/phase-reports/) | отчёты фаз (0..7, 57..60) |
| [`docs/milestones/`](docs/milestones/) | live workspace M01–M14 (PLAN/CHECKLIST/NOTES/DECISIONS) + `_TEMPLATE` |
| [`docs/bug-tracker/`](docs/bug-tracker/) | versioned QA bug pools (`v1-post-v9.0/` — 9 fixed) |
| [`docs/archive/`](docs/archive/) | report-before-v0.0.0/ (16 docs), future-ideas, deferred |

Прочее в корне:

- [`CLAUDE.md`](CLAUDE.md) — инструкции для AI-агентов + статус всех milestone'ов M01–M16, правила кодирования (contract-first, enum-ы, Flyway, REST, ControllerAdvice).
- [`CHANGELOG.md`](CHANGELOG.md) — Keep a Changelog + SemVer.
- [`.github/pull_request_template.md`](.github/pull_request_template.md) — PR template.

---

## Вклад в проект

Гайд: [`docs/meta/contributing.md`](docs/meta/contributing.md) — branching, Conventional Commits, PR-labels, review-routing, Flyway правила.

Коротко:

- `main` — prod, `dev` — active development. Feature branches: `feat/<topic>`, `fix/<topic>`.
- **Conventional Commits** обязательно: `<type>(<scope>): <subject>`.
- **Никогда** не редактируй applied Flyway-миграции. Patch через `V{N+1}__fix_*.sql`.
- PR labels: `landing-review`, `docs-review`, `security`, `breaking`, `migration`, `dependency`.

AI-агенты (Claude Code) — см. [`CLAUDE.md`](CLAUDE.md) и [`docs/meta/claude-code-guide.md`](docs/meta/claude-code-guide.md). GSD-планировщик: [`docs/meta/info-for-gsd.md`](docs/meta/info-for-gsd.md).

---

## Лицензия

Файл лицензии пока не добавлен. Планируется MIT (после `v0.1`).

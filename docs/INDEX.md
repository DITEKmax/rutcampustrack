# Documentation Index

Навигация по всей проектной документации. Этот файл — точка входа для нового контрибьютора и для Claude/AI-агентов, которым нужно сориентироваться.

> **Status (2026-04-27):** миграция выполнена. Все 55 файлов перенесены в подкатегории, ссылки обновлены через 7 атомарных коммитов (architecture → api → auth → operations → security/testing/performance → product/meta → phase-reports/archive). История git сохранена через `git mv`.

---

## Корневые файлы (остаются в корне репо)

- `README.md` — entry point репозитория, локальная разработка, ссылки.
- `CLAUDE.md` — инструкции для Claude Code: правила кодирования, статус milestone'ов.
- `CHANGELOG.md` — Keep a Changelog + SemVer.
- `SECURITY.md` — security policy, как репортить уязвимости (нужен в корне для GitHub Security tab).
- `.github/pull_request_template.md` — PR template (нужен в `.github/` для GitHub).

---

## docs/ — раскладка по доменам

### architecture/ — архитектура системы
- `architecture.md` — общий обзор (6 services + Gateway + 2 notification containers)
- `database-schema.md` — схема БД (PostgreSQL × 2, MongoDB)
- `event-schemas.md` — RabbitMQ JSON Schema контракты
- `websocket-flow.md` — STOMP WebSocket flows
- `shared-modules-usage.md` — как использовать shared-модули (M01)

### api/ — публичные REST/RPC API
- `api-error-conventions.md` — RFC 9457 Problem Details
- `api-pagination.md` — пагинация HATEOAS
- `api-rate-limits.md` — лимиты per-route
- `headman-weekly-report.md` — API скачивания недельного отчёта старосты в DOCX/PDF/PNG
- `internal-jwt-spec.md` — internal-JWT contract (M03a)
- `openapi-conformance.md` — OpenAPI ↔ runtime conformance tests
- `golden-tests.md` — contract tests

### auth/ — аутентификация и авторизация
- `auth-flow.md` — login/refresh/logout/OTP/TMA/cookie flows
- `security-headers.md` — CSP, HSTS, etc.

### operations/ — эксплуатация
#### operations/runbooks/ — оперативные процедуры
- `backup-restore.md` — backup + GPG restore
- `bot-webhook-migration.md` — Telegram bot polling → webhook
- `cert-renewal.md` — Let's Encrypt + certbot
- `dev-setup.md` — локальная разработка
- `image-signing-verification.md` — cosign keyless
- `loki-major-upgrade.md` — Loki upgrade
- `migration-testing.md` — Flyway + Testcontainers
- `mongo-indexes-verify.md` — Mongo TTL/индексы
- `secret-rotation.md` — секрет-ротация runbook
- `swagger-prod-access.md` — basic-auth на prod /swagger-ui

#### operations/deploy/ — развёртывание
- `prod-deploy-checklist.md` — pre-deploy чеклист
- `ci-cd.md` — GitHub Actions
- `dockerfile-conventions.md` — multi-stage Dockerfiles
- `nginx-config.md` — nginx reverse-proxy
- `resource-limits.md` — mem_limit конвенция

#### operations/monitoring/ — наблюдаемость
- `observability.md` — OTel + Tempo + Grafana
- `alerts.md` — Alertmanager → bot → Telegram
- `logging-conventions.md` — JSON logs + masking

### security/ — безопасность
- `SECURITY-AUDIT.md` — audit отчёты
- (другие security-related .md, кроме корневого `SECURITY.md`)

### testing/ — тестирование
- `testing.md` — общие конвенции
- `e2e-testing.md` — Playwright E2E
- `load-testing.md` — k6 нагрузка
- `golden-tests.md` (если решим перенести из api/)

### performance/ — производительность
- `performance-baseline.md` — k6 baseline numbers
- `performance-indexes.md` — composite indexes
- `caching-strategy.md` — Redis caching
- `connection-pool-tuning.md` — HikariCP tuning

### product/ — продукт и UX
- `Rutcampustrack brandbook.md` — брандбук
- `design-decisions.md` — UI/брендинг
- `job-stories.md` — пользовательские сценарии
- `url-layout.md` — URL routing (v9.0)
- `a11y-checklist.md` — accessibility

### meta/ — мета-документация
- `contributing.md` — гайд контрибьютора
- `claude-code-guide.md` — как Claude Code работает с этим репо
- `info-for-gsd.md` — info для GSD-планировщика
- `skills-inventory.md` — список skills

### modules/ — README shared-модулей
> Эти README сейчас лежат рядом с кодом в `services/shared/<module>/README.md`. После миграции здесь будет копия (или каноническая версия), а в коде останется тонкий stub со ссылкой сюда.

- `shared-logback.md`
- `shared-observability.md`
- `shared-test-containers.md`
- `tests-e2e.md` (из `tests/e2e/README.md`)
- `tests-load.md` (из `tests/load/README.md`)

### phase-reports/ — отчёты по фазам v1.0-v9.0
- `phase-0-report.md` — initial context report
- `phase-1-report.md` … `phase-7-report.md`
- `phase-57-report.md`, `phase-58-report.md`, `phase-59-report.md`, `phase-60-report.md`

### archive/ — устаревшая документация
- `report-before-v0.0.0/` — pre-release audit (16 docs)
- (старые design docs, deferred-ideas, future-ideas, etc.)

### milestones/ — pre-release hardening v0.0.0
> Существует как есть. Это live workspace M01-M16, не переноситься.

- `README.md` — что и в каком порядке
- `_TEMPLATE/` — шаблон milestone директории
- `M01-shared-foundations/` … `M14-post-audit-fixes/` (PLAN/CHECKLIST/NOTES/DECISIONS)
- `NEXT-SESSION.md` — закладка для следующей сессии

---

## .planning/ — рабочая память GSD

Не часть продуктовой документации — это инструментарий планировщика. **Не редактировать вручную** кроме как в режиме «навести порядок» (как сделано 2026-04-27).

- `STATE.md`, `ROADMAP.md`, `PROJECT.md`, `REQUIREMENTS.md`, `MILESTONES.md`, `RETROSPECTIVE.md`
- `phases/` — активная фаза (если есть)
- `milestones/` — архивы по версиям
- `bug-reports/` — ручной QA-каталог (BUG-001..009)
- `codebase/`, `research/` — служебные снимки

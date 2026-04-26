# 99. Executive Summary — roadmap v0.0.0

**Дата:** 2026-04-19 · **Итог фазы аудита pre-v0.0.0** · **Статус:** финал.

---

## TL;DR

Аудит **14 отчётов** (01-10, 12-15 + 16 P3-backlog, отчёт 11 пропущен) покрыл
**354 проблемы** (53 P0 · 136 P1 · 165 P2) + 110 P3-nit'ов. После опроса
владельца (201 consolidated вопрос, 178 NEW-задач) сформирован план
v0.0.0 объёмом **~60-80 человеко-дней** сверх уже готового v9.0.

**Meta-решения:**
- **M1** — проект вне юрисдикции РФ, 152-ФЗ не применяется. Plaintext
  `initial_password` и GPS-поля переведены в «accepted tradeoff».
- **M2** — весь P2 (165 пунктов) включён в scope v0.0.0, не отложен
  в v0.1 backlog. Причина — чистота кода и корректность с первого
  релиза.

**Coverage:** 100% — 354 пункта имеют явный ответ (см. `COVERAGE-AUDIT.md`).
Неразобранных gaps нет.

---

## Roadmap v0.0.0 — по порядку выполнения

### Фаза 0 — Инфра-подготовка (~2-3 дня)

Выполняется первой, разблокирует всё остальное.

| # | Задача | Estimate | Замечание |
|---|--------|----------|-----------|
| C0-9 | `.env.prod.example` + документация секретов | 0.5д | Без ротации (owner) |
| C0-10 | Let's Encrypt cert-name fix + force-renewal | 0.5д | Plan maintenance window |
| C0-8 | GitHub branch protection + `workflow_run` в deploy.yml | 0.5д | Required status checks |
| QD4 | Digest-пин для cadvisor/promtail; Renovate для остального | 0.5д | Supply-chain guard |
| QD5 | Trivy + Gitleaks + Dependabot + SECURITY.md | 1д | Security baseline |

### Фаза 1 — Shared-модули (критичный parallel track, ~4-5 дней)

Создание фундамента, на который опираются все последующие фиксы.

| Модуль | Источник | Estimate | Закрывает |
|--------|----------|----------|-----------|
| `shared-web` | Q16a + P2-3/1..3/3 + P2-2/1 + P2-4/1 + P2-4/6 | 2д | 05 P0-2, C1-11, RFC 7807 унификация |
| `shared-events` | NEW-60 + P2-1/5 | 1д | DomainEvent base с event_version/trace_id/occurred_at |
| `shared-logback` | NEW-68 + P2-6/1 | 1д | JSON + MaskingConverter + unified labels |
| `shared-test-containers` | NEW-158 | 1д | Testcontainers fixtures (Postgres/Mongo/Redis/Rabbit) |

### Фаза 2 — P0-кластеры (~25-35 дней)

Строгий порядок из 15-cross-cutting (C0-2 распущен через M1):

```
C0-9 → C0-10 → C0-8 ────────┐
                            ↓
                C1-7 ShedLock ← prerequisite для publisher-job
                            ↓
                   C0-3 outbox ← prerequisite для C0-1
                            ↓
                   C0-1 Internal JWT ← prerequisite для всех backend-фиксов
                            ↓
C0-4 rate-limit ──┬── C0-5 logout lifecycle ──┐
                  │                            │
                  └── C0-6 CSP self-host      │
                                              ↓
                                         C0-7 JWT cookie + ws-ticket
```

| Кластер | Estimate | Что закрывает | Приоритет |
|---------|----------|---------------|-----------|
| C1-7 ShedLock | 1-2д | 03 P0-4 + C0-3 prerequisite | блокер |
| C0-3 outbox | 5-7д | 02 P0-6, 03 P0-2, 04 P0-5 — 3 P0 | блокер |
| C0-1 Internal JWT | 3-5д | 5 P0 (02/03/04/05/07) + IDOR-защита | блокер |
| C0-4 rate-limit (Gateway+Redis) | 2-3д | 01 P0-6, 07 P1-2, /otp/* guards | блокер |
| C0-5 logout lifecycle | 2д | 09 P0-4/5, 10 P0-4 (3 P0) | блокер |
| C0-6 CSP self-host лендинга | 1-2д | 12 P0-1, 13 P0-4 | блокер |
| C0-7 JWT HttpOnly cookie + ws-ticket | 8-12д | 09 P0-1/2, 10 P0-1/2 (4 P0) + CSRF | **самый дорогой** |

**C0-2 DISSOLVED** через M1 (plaintext `initial_password` accepted).

### Фаза 3 — Точечные P0 (~3-5 дней)

Остаточные P0, не попавшие в кластеры:

- 01 P0-1 auth-api-contract (1д) + P2-2/2 (OpenAPI в interface)
- 01 P0-4 OTP через RabbitMQ event (1-2д) + 08 P0-2 схема otp.requested (2-3ч)
- 01 P0-5 MessageDigest.isEqual (~5 мин)
- 04 P0-6 удалить cleanupOrphans (1ч)
- 10 P0-3 CSP nginx web-panel (40 мин) + NEW-55 PWA vhost
- 12 P0-2 Telegram deep-link (30 мин)
- 14 P0-1 latecheckin тесты (1.5д) + 14 P0-2 callback_query pytest (1.5д)

### Фаза 4 — P1-пачки (~10-14 дней)

5 пачек, параллелизуемы после Фазы 2:

| Пачка | Темы | Estimate |
|-------|------|----------|
| P1-A observability | QA1..7 — INFO, OTel+Tempo, trace_id в events, @Counted+Alertmanager, retention 14д, health, JSON-логи | 4-5д |
| P1-B data integrity | QB1..7 — soft-delete, audit через Loki, expand/contract migrations, telegram_id verification, uniqueness | 2-3д |
| P1-C frontend reuse | QC1..7 — unified NotificationCenter, openapi-typescript, RFC 7807 interceptor, ConfirmWithReasonDialog, lazy per-role, aggregate stats, real sparklines | 3-4д |
| P1-D CI/CD | QD1..7 — SHA+semver, coverage-gate 60/50/50 + diff 80%, contract-тесты 14+ events, digest/Renovate, Trivy+Gitleaks, CHANGELOG+git-теги | 2-3д |
| P1-E remaining infra | QE1..5 — PR-template ревизия лендинга, ArchUnit ShedLock, prefers-reduced-motion, og/twitter/canonical, JWT audit в Gateway | 1-2д |

### Фаза 5 — P2-группы (~25-30 дней)

12 групп, ~165 пунктов → 79 consolidated вопросов. Порядок из P2-preamble:

| Группа | Estimate | Ключевое |
|--------|----------|----------|
| P2-11 Event schemas | 1-1.5д | _common.json $defs, lesson.cancelled snapshot, excuse.decision |
| P2-2 OpenAPI customizer | 1-1.5д | shared-web customizer RFC 7807, Swagger basic-auth |
| P2-3 Error handling edges | 1-1.5д | 9 handler'ов, invalid-params[], Python transient/permanent |
| P2-4 Validation | 2.5д | cross-field, format-patterns, max-page-size, @ValidFile |
| P2-10 Performance | 5-6д | composite indexes, Caffeine cache, @EntityGraph, batch endpoints, SQL-aggregate |
| P2-9 Docker/compose | 4-5д | **Alertmanager контейнер (новый)**, HEALTHCHECK, resource limits |
| P2-8 Test gaps | 10-12д | Testcontainers refactor, Playwright e2e, golden tests, SecurityContractsIT, k6 load |
| P2-6 Логи-нюансы | 5-6д | **notification-web stateful + MongoDB notification_db**, masking, nginx JSON |
| P2-7A Frontend UX | 2.5-3д | pull-to-refresh, useSwipeHandler, useDateNavigation, geolocation high-accuracy |
| P2-7B Frontend a11y | 3-4д | semantic HTML audit, axe-core в Playwright, SMIL→CSS keyframes |
| P2-1 Contract quality | 1.5д | @JsonProperty("isHeadman"), explicit Redis serializers, enum рефактор, Lombok conventions |
| P2-5 Cleanup | 2.5д | 204 для void, expiresInSeconds, admin /campus-settings, gateway config audit |
| P2-12 Misc | 1.5д | ArchUnit правила +3, @CreationTimestamp, JWT key periodic publish, reminders v0.0.0 accept |

### Фаза 6 — P3-уборка (~3-4 дня)

Отдельным PR. 110 пунктов по 16 темам (A-P) из `16-nit-backlog.md`.
Делать между C0-8 и C0-7 (середина кластерной работы) или после всех
кластеров — не блокер.

---

## Суммарный estimate

| Фаза | Дни |
|------|-----|
| Фаза 0 — Инфра-подготовка | 2-3 |
| Фаза 1 — Shared-модули | 4-5 |
| Фаза 2 — P0-кластеры | 25-35 |
| Фаза 3 — Точечные P0 | 3-5 |
| Фаза 4 — P1-пачки | 10-14 |
| Фаза 5 — P2-группы | 25-30 |
| Фаза 6 — P3-уборка | 3-4 |
| **Итого v0.0.0** | **72-96 человеко-дней** |

Для одного разработчика полный рабочий день = **3-4 месяца** непрерывной
работы. Параллелизация ограничена зависимостями (Фаза 2 — последовательная
цепочка).

---

## Ключевые архитектурные изменения

### Новые shared-модули (Gradle)

1. **`shared-web`** — GlobalExceptionHandler + ErrorResponse record + OpenApiCustomizer + custom validation annotations (`@StartBeforeEnd`, `@DateRangeValid`, `@ValidFile`) + Jackson config с `READ_UNKNOWN_ENUM_VALUES_AS_NULL` + `@AdminAction` aspect. Подключается к 5 backend-сервисам.
2. **`shared-events`** — `DomainEvent` base record с полями `event_version`/`trace_id`/`occurred_at`/`source`. Публишер и консюмер auto-заполняют из MDC.
3. **`shared-test-containers`** — Testcontainers fixtures (Postgres/Mongo/Redis/Rabbit + gRPC in-process helper + WireMock + MigrationTestUtils).
4. **`shared-logback`** — JSON encoder + MaskingConverter (regex для Bearer/telegram_id/FCM endpoint) + unified labels `{ts, level, msg, service, trace_id, user_id}`.

### Новые infra-контейнеры (docker-compose.prod.yml)

1. **`alertmanager: prom/alertmanager:v0.27.0`** — unified router для Prometheus + Loki alerts. Receiver = `notification-bot /internal/alert` webhook. Grouping/silencing/inhibition + `mute_time_intervals` для «тихого часа». Меняет payload-schema QA4+NEW-62.
2. **`grafana/tempo:2.3`** — distributed tracing (QA2) поверх OTel → Micrometer Tracing. Retention 14д (QA5).

### Архитектурные изменения сервисов

- **`notification-web`** перестаёт быть «stateless event forwarder» (P2-6/4). Получает own **`notification_db` Mongo** с коллекцией `notification_history` (TTL 30д), read/unread tracking, pagination REST API. Separate `NotificationHistoryConsumer` decoupled от delivery. Frontend мигрирует с sessionStorage на backend pagination + Caffeine unread-count. **CLAUDE.md update (NEW-168).**
- **`auth-service`** → `academic_db` shared (accepted) + добавляется periodic publish JWT public key через `@Scheduled + ShedLock` (P2-12/5).
- **`academic/schedule/attendance`** получают `{service}_outbox` таблицу + `OutboxPublisherJob` с ShedLock (C0-3).
- **Gateway** переходит на Spring Cloud Gateway redis-rate-limiter (C0-4) + Internal JWT issuer (C0-1) + WebFlux RFC 7807 utility (P2-3/6).
- **Frontend**: breaking change без двойных endpoint'ов — `/auth/refresh` меняет поведение (body → HttpOnly cookie), `localStorage['rct.auth.v1']` удаляется, `clearAllClientState()` в logout, ws-ticket для WebSocket.

### Новые ArchUnit-правила (~6 штук)

1. `@Scheduled` → `@SchedulerLock` или `@SingleInstanceOnly` (QE2).
2. Integration-тесты с `@SpringBootTest`/`@DataJpaTest` → `*IT` naming (P2-8/1).
3. Repository collection → `Pageable` / `@EntityGraph` / projection (P2-10/2).
4. `@Data` banned на `@Entity` / `@Document` (P2-1/8).
5. `boolean is*` getter в response DTO → `@JsonProperty("isX")` (P2-1/1).
6. Admin-mutating service methods → `@RequireRole(ADMIN)` (P2-12/2).
7. `createdAt`/`updatedAt` поля в entity → `@CreationTimestamp`/`@UpdateTimestamp` (P2-12/3).

### Новые CI gates

- **Coverage-gate**: JaCoCo 60% / Vitest 50% / pytest-cov 50% + **diff-coverage ≥ 80%** для changed lines.
- **Contract-тесты**: schema validation publisher + consumer для всех 14+ RabbitMQ events (QD3).
- **OpenAPI runtime conformance**: swagger-request-validator проверяет spec↔runtime (NEW-123).
- **Empty-catch lint**: Checkstyle `EmptyCatchBlock` + ruff/flake8-bugbear (NEW-137).
- **A11y**: ESLint jsx-a11y + @angular-eslint/template-accessibility + stylelint prefers-reduced-motion + axe-core в Playwright + vitest-axe.
- **OpenAPI schema drift-guard** (NEW-84): generated TS code in-sync.
- **Supply-chain**: Trivy + Gitleaks + Dependabot + Renovate auto-merge patch.

### Новые документы (~30 штук)

**Архитектура / security:** `architecture.md` (shared-DB tradeoffs, event philosophy, lesson lifecycle, notification history), `security-model.md` (trust-модель docker-сети, logging hygiene, shared credentials tradeoffs), `internal-jwt-spec.md`, `redis-keyspace.md`, `api-error-conventions.md`, `format-patterns.md`.

**Frontend:** `frontend-architecture.md` (NotificationCenter, error handling, form validation), `frontend-navigation.md`, `a11y-checklist.md`, `geofencing.md`, `websocket-protocol.md`.

**Infra / ops:** `dockerfile-conventions.md`, `nginx-config.md`, `gateway-config.md`, `admin-access.md` (htpasswd), `resource-limits.md` (VPS 4GB budget), `connection-pool-tuning.md`, `performance-indexes.md`, `caching-strategy.md`, `data-retention-policy.md`.

**Observability:** `observability.md` (Tempo dashboards, audit через Loki, retention), `alerts.md` (AM routing), `logging-conventions.md` (whitelist fields, masking).

**Testing / CI:** `testing.md`, `golden-tests.md`, `e2e-testing.md`, `load-testing.md`, `performance-baseline.md`, `migration-testing.md`, `ci-cd.md`.

**Runbooks:** `release-v0.0.0-runbook.md`, `rollback.md`, `flyway-expand-contract.md`, `flyway-migration-move.md`, `rabbit-dlq-recovery.md`, `loki-major-upgrade.md`, `secret-rotation.md`, `admin-scripts.md`.

**Meta:** `contributing.md` (PR-template), `event-schemas.md` (versioning policy), `notification-template-catalog.md`, `java-conventions.md`, `container-trust.md`, `future-ideas.md` (magic-link, admin PWA, auth-owned schema, temporal lesson history, full load-suite).

---

## Что в scope v0.0.0

- Все **P0** (53 → 30 через кластеры + 6 точечных групп, 1 DISSOLVED через M1).
- Все **P1** (136 → покрыты 5 пачками).
- Все **P2** (165 → M2 обязывает включить, 12 групп × ~79 consolidated вопросов).
- Все **P3** (110 → одной пачкой через `16-nit-backlog.md`).

---

## Что ACCEPTED / DISSOLVED

### ACCEPTED (by owner, M1 + M2)

- **Plaintext `initial_password`** цепочка (БД + REST + gRPC + Telegram + admin таблица): 01 P0-2, 02 P0-1, 06 P0-3, 08 P0-1, 10 P2-13, 10-Q7.
- **Shared-DB** auth ↔ academic_db, notification ↔ attendance_db: 01 P0-3, 05 P0-3.
- **Single-admin invariant** (race в activateSemester): 02 P0-4.
- **`insecure_channel` gRPC** в docker-сети: 06 P0-1.
- **Координаты геоотметки** НЕ хранятся: 04 P0-2 (doc-fix).
- **История показа initial_password** в боте отсутствует: 06-Q2.
- **changePassword без MFA** + отсутствие password recovery flow: 01 P1-7, P1-8.
- **`/admin/users` показывает initialPassword**: 10 P2-13.
- **Reminders in-memory в Python bot** (rehydration на startup): 05 P1-1 + 06 P1-2.
- **Loki retention 14д**: QA5 + 13 P2-9.
- **Shared POSTGRES_ACADEMIC_PASSWORD** (rotation quarterly): 13 P2-12.
- **Dev .env одинаковые пароли** (Testcontainers для RBAC): 13 P2-6.
- **Mini-app тесты** (not ready, accept через P2-8/6).
- **Landing zero тестов** (визуальный review через PR, P2-8/5).
- **Ротация `.env.prod`** не делается (.env.prod.example создаётся как шаблон): C0-9.
- **CSRF для Telegram callback_query** (06 P2-9 — accept через 06 P1-1 role check).

### DISSOLVED

- **C0-2 (initial_password кластер)** — распущен через 01-Q1 + M1.
- **12 P1-4 SRI на CDN** — не нужен (self-host C0-6).
- **12 P2-2 preconnect к fonts.googleapis.com** — убран с self-host.

### REJECTED

- **13 P1-3 rate-limit в nginx** — выбран Spring Cloud Gateway + Redis (C0-4).

---

## 178 NEW-задач — индекс по категориям

| Диапазон | Темы |
|----------|------|
| NEW-1..50 | Архитектура tradeoffs, internal JWT spec, dual-mode deploy, shared-outbox, rate-limit keyspace, Grafana/alerts/retention, pre-deploy QA, migration runbooks, CSRF, auth tests, legacy cleanup (refresh-body), licenses, CSP, bot runbook |
| NEW-51..100 | future-ideas (magic-link, PWA admin/teacher, JSON-LD), redis-keyspace, event versioning retrofit, CSP report-uri, tracing+Tempo, shared-events, Alertmanager webhook, retention trigger, rollback, GHCR retention |
| NEW-101..130 | Container trust, SECURITY.md + disclosure, semantic-release v0.1+, contributing.md, ArchUnit framework, stylelint a11y (v0.0.0 после P2-7B), JSON-LD, Loki alerts, proto optional audit, current_semester_id, display_name_short формат |
| NEW-131..150 | OpenAPI customizer docs, CI conformance, @Schema lint, admin-access htpasswd, AsyncAPI + STOMP payloads, architecture event docs, api-error-conventions, websocket-protocol, Python back-off/DLQ recovery, empty-catch CI lint, shared-web/validation, format-patterns, data-retention-policy, dockerfile-conventions |
| NEW-151..178 | Loki major-upgrade, nginx-config, alerts.md расширение, bot webhook schema migration, secret-rotation, testing.md, resource-limits, shared-test-containers, migration-testing, golden-tests, e2e-testing, critical frontend units, load-testing + baseline, SecurityContractsIT, logging-conventions, notification_history schema, notification OpenAPI, CLAUDE.md update, Promtail pipeline, useSwipeHandler, useDateNavigation, frontend-navigation, geofencing, a11y-checklist, axe-core setup, java-conventions, gateway-config, notification-template-catalog |

Полные описания — в `OWNER-ANSWERS.md`.

---

## Связанные документы

- **`COVERAGE-AUDIT.md`** — сверка 354 пунктов против OWNER-ANSWERS.md (100% coverage).
- **`OWNER-ANSWERS.md`** — все ответы владельца, meta-решения, NEW-задачи, audit trail.
- **`PROGRESS.md`** — handoff-сводка по всем 8 сессиям аудита.
- **`00-PLAN.md`** — оригинальный план аудита.
- **`15-cross-cutting-issues.md`** — dependency graph между кластерами, порядок исполнения.
- **`16-nit-backlog.md`** — консолидированный срез всех 110 P3 по 16 темам.
- **`01-14` + `16`** — детальные отчёты по каждому сервису/фронту/инфре/тестам.
- **`docs/archive/future-ideas.md`** — отложенные идеи для v0.1+.

---

## Итог

v0.0.0 — **осознанный архитектурный релиз**, а не «закрытие багов». Scope
M2 (весь P2 в сфере v0.0.0) означает, что после этих ~75-95 человеко-дней
работы репозиторий выйдет на уровень, после которого **будущий аудит
не откроет тех же проблем повторно** (ArchUnit + CI gates + унифицированные
shared-модули закрывают классы проблем, не отдельные случаи).

Следующая сессия — **финальный коммит** всех артефактов аудита одним PR.

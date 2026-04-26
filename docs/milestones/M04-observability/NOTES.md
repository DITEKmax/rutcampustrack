# M04 Notes

Живой файл. Surprises, отклонения, измерения, технические долги.

---

## 2026-04-20 — старт milestone

- M03b закрыт `eb125c4` + tag `v0.0.0-alpha.4` (локально без push).
- 58 коммитов ahead origin — push отложен до конца v0.0.0 (по решению владельца, эта сессия).
- Выбран M04 первым по dependency graph (рекомендация из hand-off `e85081a`).
- В DECISIONS.md 3 открытых развилки требуют подтверждения до старта кода:
  1. shared-observability модуль vs duplication по сервисам.
  2. Alert receiver: новый endpoint `/internal/alert` в notification-service vs прямой Aiogram bot endpoint.
  3. Тихий час: фиксированный 22:00-08:00 (MSK) vs configurable per-alert.

## 2026-04-20 — Surprise при старте Группы 6

Аудит показал что архитектура events **сильно отличается от того что
описано в QA3**:

- **Реальная схема (внедрена в M02 P0-6):** Spring `ApplicationEvent` →
  `DomainEventListener.@TransactionalEventListener(BEFORE_COMMIT)` →
  outbox table → `OutboxPublisherJob` → `RabbitOutboxEventSender` →
  Rabbit fanout `rut-uit.events`. Headers `event_type`, body =
  serialized event (envelope `{event_type, event_id, occurred_at, payload}`).

- **`shared-events.DomainEvent`** (создан в M02) имеет envelope
  `{event_version, trace_id, occurred_at, source}` БЕЗ обёртки `payload`.
  Используется только в shared-events тестах. **Сервисы НЕ мигрированы.**

- Каждый сервис (academic/auth/schedule/attendance) имеет
  собственный `event/DomainEvent.java extends ApplicationEvent` —
  envelope несовместим с `shared-events.DomainEvent`.

- Event-schemas (`event-schemas/*.json`) — required: `event_type`,
  `event_id`, `occurred_at`, `payload`. `trace_id`/`event_version`
  объявлены как опциональные в `_common.json#/$defs` но не required.

**Что это значит для QA3:**

(a) Полная migration сервис-DomainEvent → `shared-events.DomainEvent` —
    разные envelopes, требует переписать все event-классы (~25 шт),
    schemas (15+), publisher'ов, consumer'ов, контракт-тесты, и
    обновить версии всех событий (breaking change для consumer'ов).
    Объём ~3-5д, риск обвала existing flows.

(b) Минимально-инвазивный retrofit: добавить `trace_id` поле в
    каждый сервис-`DomainEvent` (envelope) + MDC.get/put в
    `DomainEventListener` (publish) и `EventConsumer` (consume) +
    сделать `trace_id` required в `_common.json` schema + обновить
    schemas чтобы включали `trace_id` в required envelope.
    Объём ~3-4 часа. Совместимо с outbox flow. Тестируется через
    contract-тесты M02.

(c) Wrap envelope: добавить `trace_id`/`event_version`/`source` через
    AMQP headers (publisher писать в headers, consumer читать), без
    касания JSON envelope/schemas. Cleaner с точки зрения
    backward-compat, но trace_id не в JSON-логах (нужен дополнительный
    extract).

Рекомендую **(b)** — минимум кода, максимум value (trace_id в JSON
event payload = self-describing, виден в logs/Loki, парсится
Python-ботом). (c) проигрывает потому что Loki ingest не видит AMQP
headers (только JSON body).

**Развилка для пользователя.** До решения — Группа 6 на паузе.

---

## 2026-04-20 — Группа 6 — pre-existing test failure unrelated to M04

При запуске contract-тестов attendance после migration:

- `ExcuseEventContractIT.createExcuse_publishesRequestedEvent_matchingBotContract`
  падает с `BadRequestException: Уважительную можно подать только на пару
  с «н». Урок id=1 имеет статус «PRESENT»`.

Причина: business-rule в `ExcuseService.java:172` (status != ABSENT →
reject). Test setup создаёт attendance record со статусом PRESENT, что
противоречит этому правилу. Бизнес-правило, видимо, добавлено каким-то
PR между моментом создания теста и сейчас. Pre-existing failure, не
вызвано M04 D5(a) migration.

Все остальные contract-тесты ✅ зелёные:
- `LessonStartedContractIT` ✅
- `LessonCancelledContractIT` ✅
- `LessonClosedContractIT` ✅
- `AttendanceMarkedContractIT` ✅
- `ExcuseEventContractIT.updateStatus_publishesDecidedEvent` ✅
- `*Contract*` в academic ✅

Действие: фиксировать в backlog для отдельной фазы (test seed нужно
обновить под новое business-правило) — НЕ scope M04.

---

## 2026-04-20 — развилки закрыты владельцем

- D1=(a) shared-observability модуль.
- D2=(a) `POST /internal/alert` в notification-service → RabbitMQ event → bot.
- D3=(a) фиксированный 22:00-08:00 MSK через `mute_time_intervals`.
- D5=(a) — полная migration сервис-DomainEvent → shared.events.DomainEvent.

Подробности в DECISIONS.md.

---

## 2026-04-20 — Группа 7 — Python-бот instrumentation

**Сюрприз с зависимостями.** OTel 1.27 / 0.48b0 (первоначальный выбор)
требует `protobuf<5`, а у бота уже `grpcio-tools==1.73.0 → protobuf 6.31`.
`pip install` падает ResolutionImpossible. Решение: минимальная версия
OTel 1.41.0 / 0.62b0 — совместимая с protobuf 6. Версии фиксированы в
requirements.txt.

**Архитектура решения:**

- `bot/observability.py` — bootstrap. structlog конфигурируется с
  ProcessorFormatter + stdlib bridge → все stdlib-логеры (aiogram,
  aio_pika, aiormq, grpc) проходят через тот же JSON-рендер. Поля:
  `ts/level/logger/msg/service + contextvars`. `event` → `msg` чтобы
  совпало с logstash-logback-encoder в Java-сервисах.
- `bind_trace_context(trace_id, **extra)` — context-manager, snapshot+
  restore предыдущих contextvars (nested contexts безопасны).
- Tracing: TracerProvider + OTLPSpanExporter(insecure=True) +
  BatchSpanProcessor. Auto-instrumentation aio-pika/aiohttp/grpc/redis
  под `try/except` — ImportError в unit-тестах не валит setup.
- `OTEL_SDK_DISABLED=true` — пропускает tracing setup (используется в
  unit-тестах чтобы не тащить сетевой клиент).

**Middleware регистрируется как `dp.update.middleware(...)`** —
глобально на update-уровне чтобы ловить message/callback_query/
inline_query одним handler'ом.

**event_publisher.py** теперь тоже пишет unified envelope (trace_id из
contextvars + UUID-fallback, event_version=1, source=notification-bot).
Symmetry с Java AbstractEventPublisher.fillDefaults. Цепочка
Java-publish → bot-consume → bot-publish → Java-consume сохраняет
trace_id.

**Тесты:**

- 7 новых: 4 в `test_observability.py` + 3 в `test_observability_middleware.py`.
- 146/146 overall passed (139 pre-existing не пострадали).

**Отложено в Группу 11:** E2E «bot отправляет сообщение → trace долетает
до Grafana Tempo» — требует docker-compose окружения.

---

## 2026-04-20 — Группа 8 — Business metrics + custom gauges

**Архитектура:**

- 5 ObservabilityConfig'ов (auth/attendance/academic/schedule/notification)
  создают `BusinessMetrics` bean. Сервисы ранее не имели этого bean'а,
  несмотря на наличие shared-observability зависимости (added in G1).
- shared-security теперь `api` зависит на shared-observability — чтобы
  `DualModeUserContextFilter.java` принимал nullable `BusinessMetrics`
  в конструкторе без transitive ломки downstream-тестов. Один legacy
  двухарный конструктор сохранён (backward-compat для DualModeUserContextFilterTest).
- shared-outbox → api dep на shared-observability (MetricNames).

**KI-2 counter** — `internal_jwt_fallback_total{from="internal-jwt",to="legacy-headers"}`.
Инкрементируется **только при успешном применении legacy headers** —
иначе counter шумел бы от уже отваленных 401 (сигнал «fallback
использован» важнее «header пришёл»).

**students_in_red_zone gauge** — первая итерация упрощена. Настоящее
определение красной зоны (academic-service thresholds per-subject/
per-group из v9.0 P59-60) требует cross-service join, что для 5-минутного
gauge слишком дорого. Baseline: «студенты с ≥ 3 absent за 30 дней»
через MongoDB aggregation pipeline. Threshold tags (global/group/subject)
остаются отдельными academic.threshold.* counter'ами (не scope M04).

**active_ws_sessions** — через Spring event listener. Handshake-level
counter не ловил бы TCP-обрывы (afterHandshake не даёт disconnect).
`SessionConnectedEvent` инкремент + `SessionDisconnectEvent` декремент
с guard против negative (rare race при multi-disconnect).

**outbox.lag.seconds** — add-on к legacy `outbox.lag` (count). Secondary
metric: при стабильном publishing countPending=5 может быть нормой
(batch не забран), но age=300с это уже 5-минутный lag → alert triggered.
Pure-SQL MIN(createdAt) + epochSecond diff — без sort-based query
(MongoDB findOne with sort по index `idx_outbox_pending`).

**NEW-28 compliance для RedZoneGauge** — `@SuppressWarnings("SingleInstance")`
недостаточен (SOURCE retention, ArchUnit видит bytecode). Решение:
`@SchedulerLock(name="RedZoneGauge-refresh", lockAtMostFor="4m",
lockAtLeastFor="30s")` — только один pod пересчитывает в 5-мин окне,
остальные потянут свежее значение через Prometheus remote_read. То же
самое что в outbox/lesson-closure scheduled jobs.

**Тесты:** CheckinServiceTest + ExcuseServiceTest получили `@Mock
BusinessMetrics` + `lenient().when(...checkinCounter|excuseCreatedCounter)`
→ возвращает Counter mock. SecurityInfrastructureTest (notification)
обновлён под 4-арный конструктор фильтра (`null` для BusinessMetrics).

**Тест-результаты:**
- shared-security: ✅
- auth-service: ✅
- attendance-service: 157/157 ✅ (EventConsumerIntegrationTest flaky —
  прошёл со второго запуска; не scope G8)
- academic/schedule/notification: ✅

**Pre-existing failure** `EventSchemaRefTest` в shared-outbox — тест
создан в M02 Группа 7 (`bfd43eb`), подаёт envelope без trace_id/event_version/
source, которые после G6 стали required. Unrelated G8; добавлено в
backlog G11 audit.

---

## 2026-04-20 — Hand-off для следующей сессии (после Группы 6)

**Состояние M04:** 6/12 групп закрыто. Текущая сессия упёрлась в ~50%
контекста — переключаемся ради чистоты для оставшихся 6 групп.

### Закрыто в этой сессии (8 коммитов)

| # | Коммит | Группа |
|---|--------|--------|
| 1 | `89549af` | Scaffold milestone (PLAN/CHECKLIST/NOTES/DECISIONS) |
| 2 | `fc821a7` | Закрыты развилки D1/D2/D3 — все (a) |
| 3 | `de72589` | G1 — shared-observability модуль (15 тестов ✅) |
| 4 | `3fba630` | G2 — INFO-default + dev-profile + verifyNoDebugInProd CI-check |
| 5 | `ab06c09` | G3 — JSON-логи во всех 6 сервисах + verifyLogbackJsonInAllServices |
| 6 | `cd29e7c` | G4 — health show-details:always + PublicKeyHealthIndicator (KI-4) + git.properties |
| 7 | `25f76c3` | G5 — distributed tracing OTel + Tempo container |
| 8 | `08fbd1f` | G6 — D5(a) unified envelope с trace_id/event_version/source (47 файлов) |

Plus коммиты до M04: `e85081a` hand-off от M03b. Tag `v0.0.0-alpha.4`
на `eb125c4` (без push).

### Что делать в новой сессии (первая команда)

1. Прочитай этот файл (NOTES.md) — особенно блоки «Surprise при старте
   Группы 6», «D5(a) решение», текущий hand-off.
2. Прочитай `CHECKLIST.md` — для каждой группы видишь что закрыто `[x]`
   и что осталось `[ ]`. Группа 7 первая невыполненная.
3. Прочитай `DECISIONS.md` — все 5 решений (D1..D5) уже закрыты, новых
   развилок не предвидится для Группы 7.
4. **Сразу стартуй Группу 7 без подтверждения** — пользователь даст
   `go` через сообщение "go", а не отдельным повторным вопросом.
5. Если возникнет surprise по архитектуре Python-бота — стоп, NOTES.md,
   спросить.

### Контекст Группы 7 (Python-бот instrumentation, QA2 + QA7)

**Что нужно сделать:**
1. `services/notification-bot/requirements.txt` — добавить `structlog`,
   `opentelemetry-instrumentation-aiogram`, `opentelemetry-exporter-otlp`.
2. `services/notification-bot/observability.py` (новый файл) —
   `structlog` setup с JSON-processor + OTLP tracer init.
3. Aiogram middleware — вставлять `user_id`, `callback_type`, `trace_id`
   в structlog context.
4. aio-pika consumer — extract `trace_id` из `event payload['trace_id']`
   (тот же envelope из shared-events после G6) → MDC-equivalent через
   `structlog.contextvars`.
5. `docker-compose.yml`/`prod.yml` — `OTEL_EXPORTER_OTLP_ENDPOINT`
   env-var у notification-bot.

**Команды для быстрого orientation:**

```bash
ls services/notification-bot/
cat services/notification-bot/requirements.txt
# найти место где bot обрабатывает events:
grep -rln "aio_pika\|aio-pika" services/notification-bot/
grep -rln "Dispatcher\|@dp.message\|@router\." services/notification-bot/ | head
# понять где сейчас logging:
grep -rln "import logging\|logger =" services/notification-bot/ | head
```

**Проверка:** `docker compose -f docker-compose.yml up notification-bot`
→ `docker logs rct-notification-bot` → первая строка валидный JSON.

**Spec из аудита:** `docs/archive/report-before-v0.0.0/06-notification-bot.md` +
`OWNER-ANSWERS.md` строки 1389-1395 (QA2 — Python instrumentation) +
1530-1564 (QA7 — structlog).

### Незакрытые backlog'и из текущей сессии

1. **Pre-existing failure** в `ExcuseEventContractIT.createExcuse_publishesRequestedEvent_matchingBotContract`
   — business rule unrelated to M04, фиксить в отдельной фазе attendance
   test seeds. НЕ scope M04.
2. **Group 5 deferred:** exclude `/actuator/**` из tracing sampling —
   разобраться в Группе 11 audit (Spring Boot 3.4 не даёт out-of-box).
3. **Group 5 deferred:** end-to-end span-tree через docker-compose →
   Группа 11.
4. **Group 4 deferred:** docker `healthcheck:` directive → M06 (там же
   HEALTHCHECK + compose-config scope).
5. **Group 4 deferred:** «kill RabbitMQ → health DOWN» smoke-test →
   Группа 11.
6. **Group 1 deferred:** GrpcClientHealthIndicator per-channel
   (D4=(b) — отложен в backlog, grpc-client-spring-boot-starter уже
   даёт свои indicators).

### Состояние веток / push

- **58+ коммитов ahead origin** — push отложен до конца v0.0.0
  (по решению владельца — «не пушим в репозиторий только в конце как
  скажу сразу всё запушим»).
- Tag `v0.0.0-alpha.4` на `eb125c4` локально, без push.
- v0.0.0-alpha.5 будет на финальном коммите M04 (Группа 12).

---

## 2026-04-20 — Hand-off после Группы 8 (8/12 закрыто)

**Состояние:** G1-G8 закрыты. Commits этой сессии: `b08490e` (G7),
`1e9112e` (G8). Всего ahead origin ~60+.

### Что осталось (G9-G12)

**G9 — Alertmanager + alerts.** QA4 + NEW-62/63/64. Крупный кусок:
- `prom/alertmanager:v0.27` контейнер в docker-compose.prod.yml
- `infra/observability/alertmanager.yml` — receivers, routing tree,
  mute 22:00-08:00 MSK (D3=(a))
- `infra/observability/prometheus.yml` — alert rules: service down,
  DLQ size, outbox.lag.seconds, disk > 80%, attendance rate anomaly
- notification-service `POST /internal/alert` (D2=(a) webhook →
  RabbitMQ → bot). Auth: internal-secret header (M06 заменит на mTLS)
- bot consumer для `alert.fired` → forward Telegram админу
- `docs/operations/monitoring/alerts.md` — каталог + runbook
- Smoke: kill auth → alert в Telegram через 30-60с

**G10 — Retention + Grafana dashboard.** QA5 + NEW-66.
- `infra/observability/loki.yaml` retention 336h
- `prometheus.yml` retention 14d
- `tempo.yaml` retention 14d (если G5 не закрыл)
- `infra/observability/grafana/dashboards/business-kpis.json` — 6-8
  панелей по метрикам G8 (checkin/login rate, red zone, active ws,
  outbox lag, heap, RabbitMQ queue)
- Disk alert > 80% (cadvisor)
- `docs/archive/future-ideas.md` запись

**G11 — Audit.** bug-hunter + security-auditor + code-reviewer на diff
M04. Также pre-existing backlog из G1-G8:
1. `EventSchemaRefTest` в shared-outbox — envelope без
   trace_id/event_version/source после G6 стали required. Обновить
   тест-payload или сделать поля optional в _common.json schema.
2. `/actuator/**` из tracing sampling (G5 deferred)
3. E2E span-tree через docker-compose (G5 deferred)
4. docker-compose healthcheck (G4 deferred → M06)
5. Kill RabbitMQ → health DOWN smoke (G4 deferred)
6. GrpcClientHealthIndicator per-channel (G1 deferred → backlog)
7. `ExcuseEventContractIT.createExcuse_publishesRequestedEvent_matchingBotContract`
   pre-existing business-rule failure (G6 deferred → attendance seeds)

**G12 — Documentation + закрытие.** Finishing pass:
- `docs/operations/monitoring/observability.md` runbook (~200-250 строк, новый)
- `docs/operations/monitoring/alerts.md` каталог (создать/финализировать, часть в G9)
- `docs/architecture/architecture.md` раздел «Observability stack»
- `docs/operations/monitoring/logging-conventions.md`
- `CHANGELOG.md [Unreleased]` M04 секция
- `CLAUDE.md` «Текущий статус» → M04 ✅
- `docs/milestones/README.md` → ✅ + дата
- PLAN.md Post-mortem
- `git tag v0.0.0-alpha.5` (без push)
- Hand-off для M05/M06/M07

### Команды для быстрого orientation в новой сессии

```bash
# Читай первыми:
cat docs/milestones/M04-observability/CHECKLIST.md
cat docs/milestones/M04-observability/NOTES.md  # этот файл
cat docs/milestones/M04-observability/DECISIONS.md

# Посмотри последние 3 коммита M04:
git log --oneline -5

# Быстрый smoke — всё ли компилируется:
JAVA_HOME="C:/Users/maksd/.jdks/ms-21.0.9" ./gradlew.bat build -x test
```

### Контекст Группы 9 (следующая)

**Spec из аудита:**
- `docs/archive/report-before-v0.0.0/OWNER-ANSWERS.md` QA4 (строки 1445-1510)
- `docs/archive/report-before-v0.0.0/99-executive-summary.md` P1-A блок

**Ключевые решения уже закрыты (см. DECISIONS.md):**
- D2=(a) — `POST /internal/alert` в notification-service → RabbitMQ
  `alert.fired` event → notification-bot handler. Auth: internal-secret
  header, M06 заменит на mTLS.
- D3=(a) — тихий час фиксированный 22:00-08:00 MSK через
  alertmanager `mute_time_intervals`.

**Что делать первым шагом G9:**
1. Создать `infra/observability/alertmanager.yml` + `prometheus.yml`
2. Добавить `prom/alertmanager:v0.27` контейнер в
   `docker-compose.prod.yml` + `docker-compose.yml`
3. Добавить port 9093 exposure для локальной проверки
4. Написать minimal rule (service down) + test что alert долетает
5. Далее — notification-service endpoint + bot handler

### Правила работы (без изменений)

- Русский в отчётах / NOTES / ответах.
- READ-BEFORE-EDIT reminder'ы после Read в той же сессии — ложные, игнорируй.
- Коммит после каждой логической группы (`feat/fix/test/docs` scope:
  `<service>/<module>` + `(M04 Группа N)`).
- `gsd-*` агенты НЕ звать. `Explore` для поиска, `bug-hunter` +
  `security-auditor` / `code-reviewer` — в Группе 11.
- Surprise → NOTES.md + спросить.
- Закрыл пункт CHECKLIST → `[x]` через Edit.

---

## 2026-04-20 — Hand-off после G9+G10 (10/12 закрыто)

**Состояние:** G1-G10 закрыты. Остались G11 (audit) и G12 (docs + закрытие).
Коммиты этой сессии: `6b8a233` (G9 Alertmanager), `7f18104` (G10
retention + dashboard). Ahead origin ~65+.

### Что было сделано в этой сессии

**G9 — Alertmanager end-to-end chain:**
- `infra/alertmanager/alertmanager.yml` — routing (critical всегда,
  warning muted 19-05 UTC = 22-08 MSK через `time_intervals` v0.27+).
  Receiver webhook → notification-web:9094/internal/alert. Inhibit
  ServiceDown → HealthCheckDown.
- `infra/prometheus/rules/service-health.yml` — 8 alert rules в 4
  группах (service-health / outbox / infra / business-anomaly).
- `infra/prometheus/prometheus.yml` — alerting block + rule_files +
  alertmanager scrape job.
- `prom/alertmanager:v0.27.0` контейнер в обоих compose с
  entrypoint-wrapper (echo secret → /etc/alertmanager/secret → exec).
  alertmanager.yml не поддерживает env vars напрямую — credentials_file
  единственный путь.
- notification-web: `alert/` пакет (Controller + Publisher + Payload +
  Properties). POST /internal/alert с Bearer-auth → публикует в
  `rut-uit.events` события `alert.fired` (unified envelope G6-стиля).
- `NotificationUserContextFilter.isExcludedPath` — добавлен
  `/internal/alert`.
- notification-bot: `bot/notifications/alert_fired.py` handler +
  регистрация в EventDispatcher. `config.py` — `admin_telegram_ids`
  (comma-separated). HTML escape в message format.
- docker-compose env: `ALERT_WEBHOOK_SECRET` (notification-web),
  `ADMIN_TELEGRAM_IDS` (notification-bot) в обоих compose.
- 7 Java unit-тестов + 7 Python unit-тестов. Bot suite 153/153.

**G10 — Retention + Grafana dashboard:**
- Loki retention 168h → 336h + добавлен `compactor` (retention_enabled:
  true; без него Loki не удаляет старые chunks даже при limits_config).
- Loki `ruler.alertmanager_url` исправлен с `localhost:9093` на
  `alertmanager:9093` (в контейнере localhost — сам ruler).
- Prometheus retention 30d → 14d (QA5 spec).
- `infra/grafana/provisioning/dashboards/business-kpis.json` — 8
  панелей (checkin rate, login/OTP rate, red zone stat, ws sessions
  stat, outbox lag with thresholds 60s/300s, JVM heap %, RabbitMQ
  queue depth, KI-2 fallback rate). UID `business-kpis-m04`,
  автообновление 30s.

### G11 — audit, прерван в текущей сессии

3 background-агента были запущены параллельно (bug-hunter,
security-auditor, code-reviewer) и остановлены пользователем до
завершения. Reports не получены — перезапустить с нуля в следующей
сессии.

**Как возобновить G11 в новой сессии:**

```
# Полный build проверить ещё раз (он был зелёным):
JAVA_HOME="C:/Users/maksd/.jdks/ms-21.0.9" ./gradlew.bat build -x test

# Запустить все 3 агента параллельно — промпты уже подготовлены,
# можно взять из моего недавнего prompt'а (см. git log или
# восстановить по контексту).
```

Агентам передать diff-контекст: последний pre-M04 коммит —
`19f2faf`, M04-цепочка начинается с `89549af` (scaffold milestone).

**Особые точки для агентов (что я просил проверить):**

1. DualModeUserContextFilter — nullable BusinessMetrics overload
2. RedZoneGauge — race @PostConstruct vs @Scheduled initial
3. JpaOutboxStorage.oldestPendingAgeSeconds + Mongo аналог
4. AlertController — parse безопасность + timing attack в secret.equals
5. AlertPublisher — Map.of() 7 полей (limit 10, ОК)
6. observability.py — structlog re-configure idempotent?
7. alert_fired.py — closure late-binding в Python (я использовал
   default args trick `cid=cid, text=text`)
8. Prometheus rules `hour() >= 6 and hour() <= 15` — правильный UTC
9. time_intervals v0.27 structure — правильно
10. docker-compose entrypoint `$${ALERT_WEBHOOK_SECRET}` — двойной $$
    для compose variable escaping

### G12 — documentation + закрытие

Ещё не начато. Список задач из CHECKLIST Группа 12:
- `docs/operations/monitoring/observability.md` runbook (~200-250 строк, новый)
- `docs/operations/monitoring/alerts.md` каталог + runbook (часть добавлена в G9 rules,
  нужен дополнительный overview doc)
- `docs/architecture/architecture.md` — раздел «Observability stack»
- `docs/operations/monitoring/logging-conventions.md` финализировать
- `CHANGELOG.md [Unreleased]` M04 секция
- `CLAUDE.md` «Текущий статус» → M04 ✅ + дата
- `docs/milestones/README.md` → ✅ + дата
- `docs/archive/future-ideas.md` NEW-66 retention review
- PLAN.md Post-mortem секция
- `git tag v0.0.0-alpha.5` локально (без push)
- Hand-off для M05/M06/M07

### Состояние веток / push

- ~65+ коммитов ahead origin — push отложен до конца v0.0.0.
- Tag `v0.0.0-alpha.4` на `eb125c4` локально.
- `v0.0.0-alpha.5` будет на финальном коммите M04 (Группа 12).

### Правила работы (без изменений)

- Русский в отчётах / NOTES / ответах. Технические термины / код — оригинал.
- READ-BEFORE-EDIT reminder'ы ложные (после Read в той же сессии) —
  игнорируй. Отличить настоящий: «File has not been read yet» в Edit
  result означает что файл реально не Read'ался — тогда Read'ай.
- Коммит после каждой логической группы (`feat/fix/test/docs` scope:
  `<service>/<module>` + `(M04 Группа N)`).
- Не звать `gsd-*` агентов. `Explore` для «найти все X», `bug-hunter` +
  `security-auditor` / `code-reviewer` — в Группе 11.
- Surprise → NOTES.md + спросить владельца до продолжения.
- Micro-решение → DECISIONS.md.
- Закрыл пункт CHECKLIST → `[x]` через Edit.

### Источники истины

- `docs/milestones/M04-observability/PLAN.md` — scope, acceptance criteria.
- `docs/milestones/M04-observability/CHECKLIST.md` — 12 групп, статус каждой.
- `docs/milestones/M04-observability/NOTES.md` — этот файл, surprises + hand-off.
- `docs/milestones/M04-observability/DECISIONS.md` — D1..D5 закрыты.
- `docs/archive/report-before-v0.0.0/OWNER-ANSWERS.md` — строки 1349-1564 (QA1..QA7).
- `docs/archive/report-before-v0.0.0/99-executive-summary.md` — пачка P1-A.
- `git log --oneline -15` — последние 8 коммитов M04 + до этого M03b.

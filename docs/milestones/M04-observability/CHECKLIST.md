# M04 Checklist

Атомарные задачи в порядке выполнения. Группа = логический коммит.

## Группа 1 — shared-observability модуль (фундамент) ✅

- [x] Создать Gradle-модуль `services/shared/shared-observability/` (`java-library` + `java-test-fixtures`).
- [x] Добавить зависимости: `micrometer-core` (api), actuator/grpc-api (compileOnly). OTLP exporter / tracing-bridge остаются в `*-app` модулях (тяжёлые, не нужны в каждом тесте).
- [x] `MdcKeys.java` — константы `TRACE_ID`, `USER_ID`, `EVENT_TYPE`, `INTERNAL_JWT_FALLBACK` (KI-2).
- [x] `MetricNames.java` — единые имена метрик (auth.login, attendance.checkin, otp.request/verify, internal_jwt.fallback, gauges).
- [x] `BusinessMetrics.java` — fluent-helper `loginCounter(role)`, `checkinCounter(status)`, `internalJwtFallbackCounter(from,to)` и т.д.
- [x] `GrpcClientHealthIndicator.java` — через `ManagedChannel.getState()` (READY→UP, IDLE/CONNECTING→UNKNOWN, TRANSIENT_FAILURE/SHUTDOWN→DOWN).
- [x] `PublicKeyHealthIndicator.java` — принимает `BooleanSupplier` (без зависимости от shared-security, сервис связывает через лямбду).
- [x] testFixtures: `MetricsTestSupport` — `assertCounter(registry, name, tagKey, tagValue, expectedCount)` через AssertJ.
- [x] Тесты: 15 шт всё зелёное (6 BusinessMetrics + 4 PublicKey + 5 Grpc через Mockito).
- [x] Подключить модуль во все 6 backend-сервисов (api-gateway, auth, academic, schedule, attendance, notification).

## Группа 2 — INFO-default + dev-profile (QA1 + NEW-57) ✅

- [x] Audit всех 6 `application.yml` — у всех был `ru.rutcampustrack: DEBUG`. Заменено на INFO.
- [x] api-gateway также понизил `org.springframework.cloud.gateway: DEBUG → INFO`.
- [x] api-gateway/notification: дублирующее `ru.rutcampustrack: INFO` в `application-prod.yml` удалено (default уже INFO). У api-gateway оставлен prod-override `org.springframework.cloud.gateway: WARN`.
- [x] Создано 6 новых `application-dev.yml` с DEBUG для `ru.rutcampustrack` + `management.tracing.sampling.probability: 1.0` (для Группы 5).
- [x] CI-check `verifyNoDebugInProd` Gradle task в root `build.gradle.kts` — паттерн `ru\.rutcampustrack[^:]*:\s*DEBUG`, привязан к `check`. Игнорирует `application-dev.yml` и комментарии (отрезает `#`).
- [x] Sanity-test: подменил INFO→DEBUG → BUILD FAILED с правильным сообщением. Восстановил → BUILD SUCCESSFUL.
- [x] Документация секции «Logging defaults» — отложено в Группу 12 (общий `docs/observability.md` runbook).

## Группа 3 — JSON-логи во всех сервисах (QA7 + NEW-68) ✅

- [x] Audit: только notification-service подключал `shared/logback-base.xml`. Остальные 5 — нет.
- [x] Создан `logback-spring.xml` в auth, api-gateway, academic, schedule, attendance — `<include resource="shared/logback-base.xml"/>` + `SERVICE_NAME` per service.
- [x] Добавлена зависимость `implementation(project(":services:shared:shared-logback"))` в build.gradle.kts тех же 5 сервисов (тащит logstash-logback-encoder транзитивно).
- [x] CI-check `verifyLogbackJsonInAllServices` Gradle task — проверяет наличие `logback-spring.xml` + include для всех 6 ожидаемых путей. Привязан к `check`. Sanity-test: rename файла → BUILD FAILED, восстановление → BUILD SUCCESSFUL.
- [x] Smoke verified: notification-app + auth-service test logs выдают корректный JSON с полями `ts/v/level/logger/thread/msg/service`. Pipeline работает end-to-end.
- [x] `docs/logging-conventions.md` — отложено в Группу 12 (общая документация runbook'ом).

## Группа 4 — Health endpoints + custom indicators (QA6 + NEW-67 + KI-4) ✅

- [x] `show-details: always` + `probes.enabled: true` во всех 6 `application.yml` (api-gateway уже имел always — добавлен probes).
- [x] `application-prod.yml` × 6 — удалён override `show-details: never` (default уже always).
- [x] `db`/`rabbit`/`redis`/`mongo` indicators подключаются автоматически Spring Boot когда соответствующие starters на classpath. `show-details: always` сделает их видимыми.
- [x] D4 (DECISIONS): `GrpcClientHealthIndicator` per-channel — отложен в backlog. `grpc-client-spring-boot-starter` уже даёт свои indicators. shared-observability помощник остаётся опциональным.
- [x] `PublicKeyHealthIndicator` подключён в api-gateway через `ObservabilityConfig` bean `publicKey`. Добавлен `PublicKeyConfig.isReady()` non-throwing probe (KI-4).
- [x] `info.git.enabled: true` + `info.build.enabled: true` во всех 6 `application.yml` (NEW-67).
- [x] Root Gradle task `generateGitProperties` — генерирует `git.properties` (commit.id, branch, time) в каждом `*-app/src/main/resources/`. Привязан к `processResources` через `afterEvaluate`. `.gitignore` дополнен — артефакт не коммитится.
- [x] Smoke verified: ActuatorIT в auth-service зелёный, gateway PublicKeyConfigTest зелёный.
- [ ] `docker-compose.prod.yml` healthcheck — отложено в M06 (Ops & Supply Chain), там же HEALTHCHECK directives и compose-config.
- [ ] Тест «kill RabbitMQ → health DOWN» — отложено в Группу 11 audit (требует docker-compose окружения).

## Группа 5 — Distributed tracing (QA2 + NEW-58/59) ✅

- [x] 6 × `application.yml`: `management.tracing.sampling.probability: 1.0` + `management.otlp.tracing.endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://tempo:4317}`.
- [x] 6 × `build.gradle.kts`: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` (Spring Boot BOM версии).
- [x] `infra/tempo/tempo.yml` — retention 336h (14d), local storage backend, OTLP gRPC :4317 + HTTP :4318 receivers.
- [x] `infra/grafana/provisioning/datasources/tempo.yml` — Grafana datasource + traces↔logs (Loki) + traces↔metrics (Prometheus) cross-linking + serviceMap + nodeGraph.
- [x] `docker-compose.prod.yml`: контейнер `grafana/tempo:2.3.1` (ungit'd digest pin отложен в M06 NEW-16) + tempo-data volume + `OTEL_EXPORTER_OTLP_ENDPOINT` env во всех 6 app-сервисах + `grafana depends_on tempo`.
- [x] `docker-compose.yml` (dev): tempo контейнер + порт 4317 на хост (для локальной разработки) + OTLP env в notification-web.
- [ ] Health-check requests исключить из sampling — отложено: Spring Boot 3.4 не имеет out-of-box `exclude-path-patterns`. Альтернативные подходы (`tracing.enabled: false` для actuator, custom `Sampler`) — реализую в Группе 11 audit, если spam в Tempo действительно появится.
- [x] Smoke: ActuatorIT в auth-service с включённым tracing autoconfig — Spring контекст поднимается, OTel autoconfig работает (соединение с tempo:4317 не нужно для unit-теста).
- [ ] End-to-end span-tree в Grafana Tempo — отложено в Группу 11 (требует docker-compose окружение).

## Группа 6 — RabbitMQ event tracing (QA3 + NEW-60/61) ✅

D5(a) — полная migration на shared-events.DomainEvent (см. DECISIONS).

- [x] `shared-events.DomainEvent` расширен envelope-полями `event_type`/`event_id`/`event_version`/`trace_id`/`occurred_at`/`source`/`payload`. Extends Spring `ApplicationEvent` (один источник правды для всех сервисов).
- [x] `AbstractEventPublisher.fillDefaults()` — fill из MDC (`MDC.get(MDC_TRACE_ID)`) с UUID-fallback когда MDC пуст (scheduled jobs / unit tests без HTTP context).
- [x] `AbstractEventConsumer.withTraceContext(Map<String,Object> envelope, Runnable)` — extract trace_id → MDC.put перед handler'ом, restore после (даже при exception).
- [x] 19 × `event-schemas/*.json` — добавлено `event_version`/`trace_id`/`source` в `required` envelope + properties с `$ref` на `_common.json#/$defs`. Bulk-update через Node script.
- [x] Service-DomainEvent × 3 (academic/auth/schedule) → extends `shared.events.DomainEvent` (старая сигнатура конструктора `(source, eventType, payload)` сохранена для backward-compat подклассов).
- [x] DomainEventListener × 3 → extends `AbstractEventPublisher` + вызывает `fillDefaults(event)` перед `objectMapper.writeValueAsString`.
- [x] EventConsumer × 3 (schedule/attendance/notification) → extends `AbstractEventConsumer` + wraps switch routing в `withTraceContext(envelope, () -> ...)`.
- [x] Attendance — 3 publisher'а (AttendanceEventPublisher/ExcuseEventPublisher/LateCheckinEventPublisher) рефакторены через общий `EventEnvelope.build()` helper (Map-based path с MDC + UUID-fallback).
- [x] shared-events build.gradle.kts: добавлен `compileOnly("org.springframework:spring-context")` (DomainEvent extends ApplicationEvent).
- [x] 4 × build.gradle.kts (academic/schedule/attendance/auth): добавлено `implementation(project(":services:shared:shared-events"))`.
- [x] Contract-тесты M02 валидируют новый envelope: schedule (3) ✅, academic (1) ✅, attendance.marked ✅. Один pre-existing failure в excuse — unrelated business-rule (см. NOTES).

## Группа 7 — Python-бот instrumentation (QA2 + QA7 для бота) ✅

- [x] `requirements.txt` — добавлено: `structlog==24.4.0`, `opentelemetry-{api,sdk,exporter-otlp-proto-grpc}==1.41.0`, `opentelemetry-instrumentation-{aio-pika,aiohttp-client,grpc,redis}==0.62b0`. OTel 1.41+ — грамотный выбор: pre-1.41 требует protobuf<5, а grpcio-tools 1.73 (уже в проекте) тянет protobuf 6.31.
- [x] `bot/observability.py` — `setup_observability()` (structlog JSON через ProcessorFormatter + stdlib bridge, OTLP tracer init через TracerProvider + BatchSpanProcessor + auto-instrumentation aio-pika/aiohttp/grpc/redis, `OTEL_SDK_DISABLED=true` skip для unit-тестов). Public API: `bind_trace_context()` context-manager и `get_logger()`. 4 unit-теста ✅.
- [x] `bot/middlewares/observability_middleware.py` — `ObservabilityMiddleware` глобальный на `dp.update` с user_id/callback_type/callback_data[:60]/trace_id=uuid4. 3 unit-теста ✅.
- [x] aio-pika consumer (`event_consumer.py`) — `bind_trace_context(body["trace_id"], event_type, event_id)` оборачивает dispatch. Java publisher уже заполняет `trace_id` (G6 fillDefaults).
- [x] `event_publisher.py` — bot→RabbitMQ публикация теперь пишет unified envelope (`trace_id` из structlog contextvars + UUID-fallback, `event_version=1`, `source=notification-bot`). Symmetry с Java AbstractEventPublisher.
- [x] `__main__.py` — `setup_observability()` вместо `logging.basicConfig` + регистрация middleware.
- [x] `docker-compose.yml` + `docker-compose.prod.yml` — `OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4317` env у notification-bot.
- [x] Smoke: 146/146 bot unit-тестов зелёные (7 новых + 139 pre-existing unaffected).
- [ ] E2E «bot отправляет сообщение → trace в Tempo» — отложено в Группу 11 (требует docker-compose окружения).

## Группа 8 — Business metrics + custom gauges (QA4 + KI-2 + logout) ✅

- [x] BusinessMetrics bean → ObservabilityConfig в auth/attendance/academic/schedule/notification.
- [x] `AuthService.login` → `auth.login{role}` (role downcased).
- [x] `AuthService.logout` → `auth.logout{cause=user|invalid_token}` (замещает deferred из M03b).
- [x] `OtpService.requestOtp` → `otp.request{channel=telegram}`.
- [x] `OtpService.verifyOtp` + `verifyOtpByCode` → `otp.verify{outcome=success|expired|mismatch|annulled}`.
- [x] `CheckinService.checkin` → `attendance.checkin{status=present}`.
- [x] `ExcuseService.createExcuse` + `createExcuseWithFile` → `excuse.created{kind=illness|...}`.
- [x] `LateCheckinService.createRequest` → `late_checkin.created`.
- [x] KI-2 counter `internal_jwt_fallback_total{from,to}` в `DualModeUserContextFilter` (nullable BusinessMetrics в конструкторе — backward-compat tests проходят). shared-security → api dep на shared-observability. 4 downstream фильтра (academic/schedule/attendance/notification) передают BusinessMetrics через super().
- [x] Gauge `attendance.students_in_red_zone` — `RedZoneGauge` с @Scheduled (5 мин) + @SchedulerLock (NEW-28 compliance) + @PostConstruct initial refresh. AtomicLong storage, Micrometer lazy-read. Упрощённое определение: студенты с ≥ 3 `absent` статусами за 30 дней через MongoDB `$group`-aggregation.
- [x] Gauge `notification.active_ws_sessions` — `WsSessionGauge` через `@EventListener(SessionConnectedEvent/SessionDisconnectEvent)`. AtomicLong, guard против negative.
- [x] Gauge `outbox.lag.seconds` — `OutboxStorage.oldestPendingAgeSeconds()` + реализации в JpaOutboxStorage и MongoOutboxStorage. shared-outbox → api dep на shared-observability. OutboxMetrics регистрирует оба gauge (`outbox.lag` legacy + `outbox.lag.seconds`).
- [x] Smoke: CheckinServiceTest + ExcuseServiceTest — Mockito stubs на BusinessMetrics. auth-service (все тесты), attendance-service (157/157), academic/schedule/notification — зелёные.

## Группа 9 — Alertmanager + alerts (QA4 + NEW-62/63/64) ✅

- [x] `docker-compose.prod.yml` + `docker-compose.yml` — контейнер `prom/alertmanager:v0.27.0` с entrypoint-wrapper'ом (записывает ALERT_WEBHOOK_SECRET в /etc/alertmanager/secret, потом exec alertmanager). Volume alertmanager-data.
- [x] `infra/alertmanager/alertmanager.yml` — routing tree (critical → всегда; warning → mute 22:00-08:00 MSK через `time_intervals` v0.27+). Receiver webhook → notification-web:9094/internal/alert. Inhibit rule: ServiceDown подавляет HealthCheckDown.
- [x] `infra/prometheus/prometheus.yml` — alerting block с alertmanager:9093 + rule_files /etc/prometheus/rules/*.yml. Alertmanager scrape job добавлен.
- [x] `infra/prometheus/rules/service-health.yml` — 4 группы alerts: **service-health** (ServiceDown critical, HealthCheckDown warning), **outbox-eventing** (OutboxLagHigh, DLQBacklog), **infra** (DiskUsageHigh, JvmHeapPressure), **business-anomaly** (CheckinRateZero в рабочие часы, InternalJwtFallbackUnexpected).
- [x] `notification-service` — `AlertController` POST `/internal/alert` с Bearer-auth (shared secret из application.yml → env ALERT_WEBHOOK_SECRET). Парсит Alertmanager v4 webhook (list alerts), мапит в `AlertPayload`, публикует каждый через `AlertPublisher` в fanout `rut-uit.events` как `alert.fired` событие с unified envelope (trace_id, event_version=1, source=notification-web).
- [x] `NotificationUserContextFilter.isExcludedPath` — `/internal/alert` bypass'ит identity-фильтр (auth через Bearer, не Internal JWT).
- [x] `notification-bot` — `alert_fired.py` handler: парсит admin IDs из env ADMIN_TELEGRAM_IDS (comma-separated), форматирует сообщение с HTML escape + severity emoji (🔴/🟡/🔵) + status emoji (🔔/✅), отправляет каждому админу через send_queue (rate-limit safe). Зарегистрирован в EventDispatcher registry.
- [x] docker-compose env: ALERT_WEBHOOK_SECRET в notification-web (prod + dev), ADMIN_TELEGRAM_IDS в notification-bot (оба).
- [x] Smoke: `AlertControllerTest` 7 тестов (happy path, auth variants, malformed body) ✅. `test_alert_fired.py` 7 тестов (format/parse/handler) ✅. Полный bot suite 153/153.
- [ ] `docs/alerts.md` — каталог alerts + runbook → Группа 12 (общая documentation pass).
- [ ] E2E: kill auth → alert в Telegram — Группа 11 audit (требует docker-compose).

## Группа 10 — Retention + Grafana dashboard (QA5 + NEW-66) ✅

- [x] `infra/loki/loki.yml` — `retention_period: 336h` (было 168h). Добавлен `compactor` block (`retention_enabled: true`) — без него Loki не удаляет старые chunks. `ruler.alertmanager_url` исправлен на `alertmanager:9093`.
- [x] `docker-compose.prod.yml` prometheus — `--storage.tsdb.retention.time=14d` (было 30d, QA5 spec).
- [x] Tempo retention 14d уже настроен в G5 (336h в `infra/tempo/tempo.yml`).
- [x] `infra/grafana/provisioning/dashboards/business-kpis.json` — 8 панелей: checkin rate by status, login/OTP rate by role/outcome, students_in_red_zone stat, active_ws_sessions stat, outbox_lag_seconds by job, JVM heap %, RabbitMQ queue depth, KI-2 fallback rate. UID `business-kpis-m04`, автообновление 30s, окно now-6h. JSON валиден.
- [x] DiskUsageHigh alert > 80% уже в G9 rules (`service-health.yml` infra group).
- [ ] `docs/future-ideas.md` NEW-66 → Группа 12 docs pass.

## Группа 11 — Audit (bug-hunter + security-auditor + code-reviewer) ✅

- [x] Финальный `./gradlew build -x test` — зелёное.
- [x] `bug-hunter` — 0 BLOCKER, 5 HIGH, 5 MEDIUM, 5 LOW. Ключевые: H1 (RedZoneGauge self-invocation — SchedulerLock не работал), H3 (AlertController unchecked cast), H5 (CheckinRateZero нужен `absent()` branch).
- [x] `security-auditor` — 0 CRITICAL/HIGH, 2 MEDIUM (timezone в alertmanager — на деле ОК, агент ошибся; PII masking в Python structlog), 5 LOW.
- [x] `code-reviewer` — 46/51 quality score. SHOULD FIX: AlertPublisher должен extend AbstractEventPublisher (отложено, пометка в NOTES); AlertController DTO вместо Map (отложено в M05/M06 refactor). Noteworthy positives: JavaDoc quality, fail-safe defaults, tests.
- [x] Hot-patches применены:
    - **H1:** убран `@PostConstruct init()` в `RedZoneGauge` — использует `initialDelayString=10s` чтобы Spring proxy поднял AOP, и `@SchedulerLock` работал правильно.
    - **H3:** `AlertController.toPayload` — новый `coerceStringMap()` безопасно конвертирует labels/annotations с любыми value types в Map<String,String>. +1 unit-тест.
    - **H5:** `CheckinRateZero` prometheus rule — добавлен `or absent(attendance_checkin_total)` branch.
    - **M-sec-2:** `_mask_pii` processor в structlog маскирует `user_id`/`telegram_id`/`from_user_id`/`chat_id` → `"***<last-3-digits>"` (не plaintext). 1 тест обновлён, middleware contextvars не затронуты.
    - **M4:** `alert_fired._format_message` truncate description до 3500 chars (Telegram 4096 limit). +1 unit-тест.
    - **LOW timing attack:** `AlertController.isAuthorized` → `MessageDigest.isEqual` (constant-time).
- [x] Отложено (пометки в NOTES.md для M05/M06): SHOULD #1 AlertPublisher extends AbstractEventPublisher, SHOULD #2 DTO-based webhook parsing, timezone подтверждение (на деле правильный).
- [x] Тесты после патчей: 154/154 bot, attendance CheckinService/ExcuseService/ArchUnit ✅, notification-web alert test ✅.

## Группа 12 — Documentation + закрытие milestone ✅

- [x] `docs/observability.md` runbook (~210 строк) — стек, связь signals через trace_id, типичные PromQL/LogQL/Tempo запросы, troubleshooting, deferred items.
- [x] `docs/alerts.md` каталог — 8 алертов с runbook-действиями, silencing, quiet hours.
- [x] `docs/architecture.md` — добавлен раздел «Observability stack (M04)» в секцию «Протоколы и коммуникации».
- [x] `docs/logging-conventions.md` — новый (~160 строк): уровни, JSON формат Java/Python, PII masking, trace_id correlation chain.
- [x] `CHANGELOG.md [Unreleased]` — большая секция M04 (tag `v0.0.0-alpha.5`).
- [x] `CLAUDE.md` — M04 статус ✅ + дата 2026-04-20.
- [x] `docs/milestones/README.md` — M04 ✅ + дата.
- [x] PLAN.md Post-mortem — surprises, decisions, deferred items, artefacts.
- [x] Hand-off для M05/M06/M07 уже в NOTES.md (секция «Hand-off после G9+G10»).
- [ ] `git tag v0.0.0-alpha.5` — создан после финального коммита G12 (commit hash будет ниже).

---

_Если задача превращается в 6+ часов — разрежь._

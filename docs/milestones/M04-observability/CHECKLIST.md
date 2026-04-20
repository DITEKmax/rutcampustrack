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

## Группа 3 — JSON-логи во всех сервисах (QA7 + NEW-68)

- [ ] Audit: какие сервисы уже подключают `shared/logback-base.xml` (notification-service точно). Найти missing.
- [ ] Создать/обновить `logback-spring.xml` в auth, api-gateway, academic, schedule, attendance — `<include resource="shared/logback-base.xml"/>` + `<property name="SERVICE_NAME" value="..."/>`.
- [ ] Verify dependency `net.logstash.logback:logstash-logback-encoder` доступен в каждом сервисе через `shared-logback`.
- [ ] Smoke-test: `./gradlew :services:auth-service:bootRun` → первая строка лога валидный JSON через `jq .`.
- [ ] Документировать в `docs/logging-conventions.md` (whitelist MDC, masking, format).

## Группа 4 — Health endpoints + custom indicators (QA6 + NEW-67 + KI-4)

- [ ] `management.endpoint.health.show-details: always` + `probes.enabled: true` во всех 6 `application.yml`.
- [ ] Включить явно в каждом сервисе indicators: `db`, `rabbit`, `redis`, `mongo` (где применимо).
- [ ] `GrpcClientHealthIndicator` подключить в каждом сервисе с gRPC-клиентом (auth↔academic, schedule↔academic, attendance↔academic, notification↔attendance — точный список из gRPC-спека).
- [ ] `PublicKeyHealthIndicator` подключить в api-gateway (KI-4 readiness gate).
- [ ] `management.endpoint.info.enabled: true` + `management.info.git.enabled: true` + `management.info.build.enabled: true` (NEW-67).
- [ ] Plugin `org.springframework.boot.experimental.thin-launcher` или Gradle `git.properties` task — генерация `git.properties` в каждом сервисе.
- [ ] `docker-compose.prod.yml`: `healthcheck:` для каждого сервиса через `curl /actuator/health | grep UP`. `restart: unless-stopped`.
- [ ] Тест: убить RabbitMQ контейнер → health = DOWN с `components.rabbit.status = DOWN`.

## Группа 5 — Distributed tracing (QA2 + NEW-58/59)

- [ ] Конфиг во всех 6 сервисах: `management.tracing.sampling.probability: 1.0`, `management.otlp.tracing.endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://tempo:4317}`.
- [ ] `docker-compose.prod.yml` + `docker-compose.yml`: контейнер `grafana/tempo:2.3@sha256:...` + persistent volume + `tempo.yaml` config.
- [ ] `infra/observability/tempo.yaml` — retention 336h.
- [ ] Grafana datasource provisioning для Tempo.
- [ ] `OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4317` в env во всех Spring-сервисах в compose.
- [ ] Health-check requests исключить из sampling (`management.tracing.sampling.exclude-path-patterns: /actuator/**`).
- [ ] Smoke: запрос через gateway → trace в Grafana Tempo с full span-tree.

## Группа 6 — RabbitMQ event tracing (QA3 + NEW-60/61)

- [ ] `services/shared/shared-events/AbstractEventEnvelope` — record с `traceId`, `occurredAt`, `eventVersion` (required).
- [ ] `AbstractEventPublisher` — fill из MDC (`MDC.get(MdcKeys.TRACE_ID)`).
- [ ] `AbstractEventConsumer` — extract trace_id → MDC.put перед handler'ом.
- [ ] `event-schemas/*.json` — добавить required `trace_id`, `occurred_at`, `event_version` во все 14+ schemas. Контракт-тесты M02 поймают.
- [ ] Сделать так, чтобы все существующие event records наследовали `AbstractEventEnvelope`.
- [ ] Тест: publish → consumer видит trace_id в MDC → log line содержит тот же trace_id.

## Группа 7 — Python-бот instrumentation (QA2 + QA7 для бота)

- [ ] `services/notification-bot/requirements.txt` — `structlog`, `opentelemetry-instrumentation-aiogram`, `opentelemetry-exporter-otlp`.
- [ ] `services/notification-bot/observability.py` — структурный logger + OTLP setup.
- [ ] Aiogram middleware: вставить `user_id`, `callback_type`, `trace_id` в context.
- [ ] aio-pika: extract trace_id из event payload → context.
- [ ] Smoke: bot отправляет сообщение → trace в Tempo, JSON log в Loki содержит trace_id.

## Группа 8 — Business metrics + custom gauges (QA4 + KI-2 + logout)

- [ ] `@Counted`/`@Timed` на: `AuthService.login`, `LogoutService.logout` (метрика `user.logged-out` deferred из M03b), `OtpService.request/verify`, `AttendanceService.checkIn`, `ExcuseService.create`, `LateCheckinService.create`.
- [ ] Counter `internal_jwt_fallback_total{from,to}` в `DualModeUserContextFilter` (KI-2).
- [ ] Gauge `students_in_red_zone` — scheduled job каждые 5 мин в attendance-service.
- [ ] Gauge `active_ws_sessions` — из notification-service handshake interceptor.
- [ ] Gauge `outbox_lag_seconds` — max(now - created_at) where sent_at IS NULL — добавить в `shared-outbox/Metrics.java`.
- [ ] Тест: mock checkin → `attendance_checkin_total{status="present"}` инкрементирован.

## Группа 9 — Alertmanager + alerts (QA4 + NEW-62/63/64)

- [ ] `docker-compose.prod.yml`: контейнер `prom/alertmanager:v0.27@sha256:...`.
- [ ] `infra/observability/alertmanager.yml` — receivers (webhook), routing tree, `mute_time_intervals` тихий час 22:00-08:00.
- [ ] `infra/observability/prometheus.yml` — alert rules: service down, DLQ size, outbox lag, disk > 80%, attendance rate anomaly.
- [ ] `notification-service`: новый endpoint `POST /internal/alert` (Alertmanager webhook contract). Auth: internal-secret header (M06 заменит на mTLS).
- [ ] Forward логика: сериализовать алерт → Telegram админу через notification-bot RabbitMQ event.
- [ ] `docs/alerts.md` — каталог alerts + runbook.
- [ ] Smoke: kill auth-service → alert долетает в Telegram через 30-60с (depending on Prometheus scrape).

## Группа 10 — Retention + Grafana dashboard (QA5 + NEW-66)

- [ ] `infra/observability/loki.yaml` — `limits_config.retention_period: 336h`.
- [ ] `infra/observability/prometheus.yml` — `--storage.tsdb.retention.time=14d`.
- [ ] `infra/observability/tempo.yaml` — retention 14d (если не сделано в Группе 5).
- [ ] `infra/observability/grafana/dashboards/business-kpis.json` — 6-8 панелей: checkin rate, login rate, OTP success rate, students_in_red_zone, active_ws_sessions, outbox lag, JVM heap, RabbitMQ queue depth.
- [ ] Disk-usage alert > 80% (через cadvisor exporter, должен быть в инфре).
- [ ] `docs/future-ideas.md` — NEW-66 запись «retention review trigger».

## Группа 11 — Audit (bug-hunter + security-auditor + code-reviewer)

- [ ] Финальный `./gradlew build` — всё зелёное.
- [ ] `bug-hunter` агент на diff M04 — root cause review.
- [ ] `security-auditor` на новые endpoint'ы (`/internal/alert`, `/actuator/health` exposure).
- [ ] `code-reviewer` на `shared-observability` модуль + изменения в шести сервисах.
- [ ] Hot-patches из audit'а → отдельный коммит.

## Группа 12 — Documentation + закрытие milestone

- [ ] `docs/observability.md` runbook (новый, ~200-250 строк).
- [ ] `docs/alerts.md` каталог алертов (новый).
- [ ] `docs/architecture.md` — раздел «Observability stack».
- [ ] `docs/logging-conventions.md` — финализировать.
- [ ] `CHANGELOG.md [Unreleased]` — M04 секция Added/Changed/Fixed/Documentation.
- [ ] `CLAUDE.md` — обновить «Текущий статус» → M04 ✅.
- [ ] `docs/milestones/README.md` — статус M04 → ✅ + дата.
- [ ] PLAN.md → Post-mortem секция.
- [ ] `git tag v0.0.0-alpha.5` (без push).
- [ ] Hand-off для следующей сессии в NOTES.md (выбор M05/M06/M07).

---

_Если задача превращается в 6+ часов — разрежь._

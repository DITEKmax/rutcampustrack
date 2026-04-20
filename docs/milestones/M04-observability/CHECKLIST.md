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

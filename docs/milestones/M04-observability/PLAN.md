# M04 — Observability (Tracing + JSON-логи + Metrics + Alertmanager + Health)

**Статус:** ⏳ в работе
**Старт / финиш:** 2026-04-20 / —
**Estimate:** ~5-7 человеко-дней (P1-A пачка по аудиту = 4-5д + интеграция с
M03a/M03b долгами + Python instrumentation)

---

## Scope

Закрывает пачку P1-A из `99-executive-summary.md` и долги M03a/M03b
(событие `user.logged-out`, KI-2 silent fallback metric, KI-4 PublicKeyProvider
readiness).

- **QA1** — `logging.level.ru.rutcampustrack: INFO` дефолт во всех 5 backend-сервисах + Gateway. `application-dev.yml` с DEBUG. CI-check (NEW-57) против регрессии.
- **QA2** — Distributed tracing: `micrometer-tracing-bridge-otel` + OTLP exporter → `grafana/tempo:2.x` (sampling 1.0 для v0.0.0). Python-бот: `opentelemetry-instrumentation-aiogram`.
- **QA3** — `trace_id`, `occurred_at`, `event_version` в каждом RabbitMQ-event. Publisher из MDC, consumer в MDC. Связано с NEW-47/60/61.
- **QA4** — `@Counted` / `@Timed` на бизнес-методах (checkin, otp.request/verify, login, excuse, late-checkin). Custom gauges (`students_in_red_zone`, `active_ws_sessions`, `outbox_lag`). Grafana dashboard «Business KPIs». Alert-rules → Alertmanager → notification-bot `/internal/alert` webhook → Telegram.
- **QA5** — Retention 14д для Loki (`336h`), Prometheus (`14d`), Tempo (`14d`). Disk-usage алерт > 80%.
- **QA6** — `management.endpoint.health.show-details: always`, `probes.enabled: true`, custom `GrpcClientHealthIndicator` + явные `db`/`rabbit`/`redis`/`mongo` indicators. Docker `healthcheck` через `/actuator/health`. `/actuator/info` с git-sha (NEW-67).
- **QA7** — JSON-логи везде: shared-logback (M01) уже базис, нужно подключить во всех 6 сервисах (auth, api-gateway, academic, schedule, attendance, notification). Python-бот: `structlog` + JSON processor + Aiogram middleware (`user_id`/`callback_type`/`trace_id`).

**Долги M03a/M03b закрытые здесь:**
- Метрика `event user.logged-out` — добавить counter в auth `LogoutService`.
- KI-2 (dual-mode silent fallback) — counter `internal_jwt_fallback_total{from,to}` + alert.
- KI-4 (PublicKeyProvider readiness) — custom `PublicKeyHealthIndicator` сигналит DOWN если ключ не подгружен.

## Модули / изменения

### Новые

- `services/shared/shared-observability/` — Gradle-модуль (`java-library` + Spring Boot starter-стиль): константы MDC-keys, `OtelAutoConfig` (общая настройка sampling/exporter), `BusinessMetrics` helper-bean, `GrpcClientHealthIndicator`. Подключается во все 6 Spring-сервисов через `implementation(libs.shared.observability)`.
- `gradle/libs.versions.toml` — версии: `micrometer-tracing` (4.x с Spring Boot 3.4), `opentelemetry-bom`, `logstash-logback-encoder` (уже есть в shared-logback — verify).
- `docker-compose.prod.yml` + `docker-compose.yml` — контейнеры:
  - `grafana/tempo:2.3@sha256:...` (digest-pin, NEW-16/QA2)
  - `prom/alertmanager:v0.27@sha256:...` (NEW-65/QA4)
  - port 4317 (OTLP gRPC) во внутренней сети.
- `infra/observability/`:
  - `tempo.yaml` — config + retention 336h.
  - `loki.yaml` — patch existing `limits_config.retention_period: 336h`.
  - `prometheus.yml` — patch `--storage.tsdb.retention.time=14d` + alert rules.
  - `alertmanager.yml` — routing, receivers (`webhook → notification-bot`), `mute_time_intervals` тихий час 22:00-08:00.
  - `grafana/dashboards/business-kpis.json` — 6-8 панелей.
  - `grafana/datasources/tempo.yaml` — datasource provisioning.
- `services/notification-service/notification-app/` — новый endpoint `POST /internal/alert` (Alertmanager webhook → forward Telegram админу). Защищён internal-secret header или mTLS (refer M06).
- `services/notification-bot/` (Python) — `structlog` + `opentelemetry-instrumentation-aiogram` + `opentelemetry-exporter-otlp` + Aiogram middleware для MDC-эквивалента.

### Меняем

- 6 × `application.yml` — `logging.level.ru.rutcampustrack: INFO`, `management.endpoint.health.show-details: always`, `management.tracing.sampling.probability: 1.0`, OTLP endpoint env-var.
- 6 × создаём `application-dev.yml` (если нет) — DEBUG + tracing sampling 1.0 + Tempo `localhost:4317`.
- 6 × `application-prod.yml` — убираем дублирующее `logging.level.root: INFO` (уже default).
- 6 × `build.gradle.kts` — `implementation(libs.shared.observability)`.
- `services/shared/shared-events/` — `AbstractEventEnvelope` record с обязательными `traceId`/`occurredAt`/`eventVersion` (NEW-60). `AbstractEventPublisher` заполняет из MDC.
- `event-schemas/*.json` — добавить required `trace_id`, `occurred_at`, `event_version` во все 14+ событий (контракт-тесты в M02 ловят отсутствующие).
- `services/auth-service/.../LogoutService.java` — counter `auth_logout_total{cause}`.
- `services/api-gateway/.../DualModeUserContextFilter` — counter `internal_jwt_fallback_total{from,to}`.
- `services/api-gateway/.../PublicKeyProvider.java` — readiness gate + `PublicKeyHealthIndicator`.
- `nginx/nginx.conf` (P2-6/5) — JSON access-log format (если ещё не сделан). Optional: defer в M07 если выходит за scope.

### Документы

- `docs/observability.md` — runbook (новый): tracing flow, dashboards, retention, audit через Loki, типичные LogQL/TraceQL запросы.
- `docs/alerts.md` — список алертов + порогов + runbook на каждое срабатывание.
- `docs/architecture.md` — раздел «Observability stack» (схема: app → OTLP → Tempo / Prometheus / Loki → Grafana → Alertmanager → bot).
- `docs/logging-conventions.md` — whitelist полей MDC, политика masking (уже есть baseline в shared-logback).
- `CHANGELOG.md [Unreleased]` — секция M04.

## Acceptance criteria

- [ ] `curl http://localhost:9090/actuator/health | jq .status` = `UP` для всех 6 сервисов; при падении gRPC dependency status = `DOWN`.
- [ ] `curl http://localhost:9090/actuator/health | jq .components.publicKey` присутствует и `UP` для api-gateway (KI-4).
- [ ] Trace_id из gateway-запроса виден в Grafana Tempo span-tree через все 2-3 сервиса + RabbitMQ + Python-бот (end-to-end).
- [ ] JSON-формат логов одинаков во всех 6 сервисах + Python-боте (поля: `ts`, `level`, `service`, `traceId`, `userId`, `msg`). Проверка: `docker logs rct-auth | head -1 | jq .`.
- [ ] `grep -r "logging.level.ru.rutcampustrack: DEBUG" services/*/src/main/resources/` пусто (только `*-dev.yml` может содержать).
- [ ] CI-check (NEW-57) грепом валит build при DEBUG в `application.yml`/`application-prod.yml`.
- [ ] Метрики видны в Prometheus: `attendance_checkin_total`, `auth_login_total`, `auth_logout_total`, `otp_request_total`, `internal_jwt_fallback_total`, `students_in_red_zone`, `active_ws_sessions`, `outbox_lag_seconds`.
- [ ] Тестовый alert (`up{job="auth-service"} == 0` сэмплированный вручную через kill контейнера) долетает до Telegram-админа через `notification-bot /internal/alert`.
- [ ] Loki / Prometheus / Tempo retention = 14д (smoke: проверить что данные старше 14д не возвращаются, либо config-grep).
- [ ] Все 14+ событий RabbitMQ имеют `trace_id`, `occurred_at`, `event_version` (контракт-тесты M02 проходят).
- [ ] `GET /actuator/info` возвращает git-sha + build-version во всех 6 сервисах (NEW-67).

## Dependencies

- **Блокирует:** M07 (Frontend Hardening — UX-фиксы лучше после видимых метрик), M08 (Test Infrastructure — golden-tests смогут проверять trace propagation).
- **Блокируется:** ничего (M01 ✅, M02 ✅).
- **Parallel safe:** M05 (Performance — независимо), M06 (Ops — частично пересекается через docker-compose.prod.yml — нужно координировать в одной сессии или после).

## Artifacts

- `services/shared/shared-observability/` — новый shared-модуль.
- `infra/observability/` — Tempo/Loki/Prometheus/Alertmanager/Grafana configs.
- `docs/observability.md` — runbook (новый, ~250 строк).
- `docs/alerts.md` — alert catalog (новый).
- `docs/architecture.md` — раздел «Observability stack».
- `CHANGELOG.md [Unreleased]` — M04 секция.
- Контейнеры: `tempo`, `alertmanager` (новые), Loki/Prom/Grafana — config-update.

---

## Post-mortem

_Заполняется при закрытии milestone'а._

# Observability Runbook

Документ описывает observability-стек RutCampusTrack после M04. Рассчитан
на on-call и разработчиков которые диагностируют прод-инциденты.

## Стек (конец M04)

| Слой | Компонент | Retention | Доступ |
|------|-----------|-----------|--------|
| Метрики | Prometheus | 14d | `private_net`, scrape `/actuator/prometheus` |
| Логи | Loki + Promtail | 14d (336h) | `private_net` |
| Трейсы | Tempo (OTLP gRPC :4317) | 14d | `private_net` |
| Алерты | Alertmanager | — | webhook → `notification-web:9094/internal/alert` |
| UI | Grafana | — | доступ через reverse proxy |
| Бизнес-метрики | Micrometer (BusinessMetrics helper) | — | 6 backend-сервисов + Python-бот |

## Как данные связаны

```
Java-сервис → Micrometer → /actuator/prometheus ─┐
                                                 ├→ Prometheus (scrape 15s) ─┐
Node-exporter / cAdvisor ────────────────────────┘                           │
                                                                             ├→ Alertmanager (rules fire) ─→ notification-web /internal/alert
                                                                             │                                       │
Java-сервис → OTel SDK (1.0 sampler) ──→ Tempo (OTLP 4317)                   │                                       ↓
Python-бот → OTLP exporter ───────────→                                      │                                   RabbitMQ alert.fired
                                                                             │                                       ↓
Java-сервис → JSON logs (logstash-logback-encoder) ─┐                        │                                   notification-bot
                                                    ├→ Promtail → Loki ──────┘                                       ↓
Python-бот → structlog JSON ────────────────────────┘                                                            Telegram admin(s)
```

Все три signal типа связываются через `trace_id` (UUID v4):

- **Jakarta** — Micrometer Tracing bridges OTel: `traceId` пишется в
  MDC, `logback-base.xml` включает `%X{traceId}` в JSON, Tempo получает
  spans с тем же ID.
- **Python-бот** — `bot/observability.py` биндит `trace_id` в structlog
  contextvars через `bind_trace_context()`. Middleware генерирует UUID
  per-update, aio-pika consumer извлекает его из envelope события.
- **События RabbitMQ** — unified envelope (shared-events.DomainEvent)
  несёт `trace_id` от publisher'а → consumer. `AbstractEventConsumer.
  withTraceContext(envelope, runnable)` восстанавливает MDC перед
  handler'ом (Java-сторона), bot делает аналогичное через
  `bind_trace_context()`.

## Быстрые ссылки в Grafana

- **Business KPIs & Health** (`business-kpis-m04`) — 8 панелей: checkin
  rate, login/OTP rate, red zone студентов, active WS, outbox lag, JVM
  heap, RabbitMQ queues, KI-2 fallback rate.
- **Spring Boot APM** — стандартный Boot dashboard (pre-M04).
- **Logs Overview** — Loki search интерфейс.

## Типичные запросы

### Prometheus (PromQL)

```promql
# Rate гео-отметок по статусам за последний час
sum by (status) (rate(attendance_checkin_total[5m]))

# Outbox lag по сервисам — выявить публишер не успевает
max by (job) (outbox_lag_seconds)

# KI-2 fallback — любой ненулевой rate = регрессия M03a
sum by (job) (rate(internal_jwt_fallback_total[5m]))

# Heap pressure — сервис идёт к OOM
max by (application) (jvm_memory_used_bytes{area="heap"})
  / max by (application) (jvm_memory_max_bytes{area="heap"})
```

### Loki (LogQL)

```logql
# Все ошибки в auth-service за 15 минут
{service="auth-service"} |= "ERROR" | json | line_format "{{.msg}}"

# Конкретный trace_id — полная цепочка через все сервисы
{service=~".+"} | json | trace_id="<uuid>"

# Rate ERROR лог-записей по сервисам — быстрый health-check
sum by (service) (rate({service=~".+"} |= "ERROR" [5m]))
```

### Tempo

В Grafana: Explore → Tempo → Search → filter по `service.name` и
`duration > 500ms`. Клик на span → переход в Loki с pre-filled
`trace_id`.

## Troubleshooting

### Сервис DOWN в Grafana

1. `docker compose ps` на VPS — контейнер запущен?
2. `docker logs rct-<service> --tail 100` — stack trace старта?
3. `/actuator/health` через API Gateway (если UP):
    ```bash
    curl -s http://api-gateway:8080/api/<service>/actuator/health | jq
    ```
   Поле `components.<db|rabbit|redis|mongo>.status` скажет какая
   зависимость положила сервис.
4. Смотри соответствующий alert в Alertmanager: была фоновая deps
   деградация до crash?

### Outbox lag растёт

1. Grafana → outbox.lag.seconds per job → найти отстающий сервис.
2. Проверить RabbitMQ health (`rct-rabbitmq` up?) — publisher job
   зависит от него.
3. Логи сервиса: `{service="<svc>"} |= "OutboxPublisherJob"`. Ищи
   `markFailed` с last_error.
4. RabbitMQ management UI (в dev экспонирован `:15672`): queue depth,
   DLQ size. Consumers online?

### Alert не долетает в Telegram

1. Проверь что alert действительно fire'ит в Alertmanager:
    ```bash
    docker exec rct-alertmanager amtool alert query
    ```
2. notification-web логи:
    ```
    docker logs rct-notification-web | grep /internal/alert
    ```
   Должно быть `INFO Alert webhook processed: N alerts published`.
   Если 401 — проверь `ALERT_WEBHOOK_SECRET` совпадает в compose и
   alertmanager.yml.
3. notification-bot логи:
    ```
    docker logs rct-notification-bot | grep alert.fired
    ```
   Нет `alert.fired`? Событие не долетело в RabbitMQ (проверь
   fanout exchange subscription).
4. `ADMIN_TELEGRAM_IDS` env переменная задана (не "0")?
5. Admin заблокировал бота в Telegram → `send_queue` упадёт,
   проверить `send_queue._total_failed` через debug лог.

### Тихий час работает не так как ожидал

Alertmanager `time_intervals` использует **UTC**, не MSK. Текущая
конфигурация `start_time: '19:00' end_time: '05:00'` = 22:00-08:00 MSK
(MSK = UTC+3). Только `warning` муте, `critical` всегда fire'ит.

## Логирование

### Java-сервисы

- Default level: `INFO` (верифицировано CI-check `verifyNoDebugInProd`).
- `application-dev.yml` — `ru.rutcampustrack: DEBUG` и `sampling.
  probability: 1.0` для локальной разработки.
- JSON schema: `{ts, v, level, logger, thread, msg, service, traceId?,
  spanId?, <MDC>...}`. `shared-logback/logback-base.xml` маскирует
  `Bearer`, `telegram_id=<digits>`, `fcm_token` через regex.
- Ошибки пишутся в поле `stack` (logstash-logback-encoder).

### Python-бот

- `structlog` конфигурируется в `bot/observability.py:setup_observability()`.
- Processors: merge_contextvars → add_log_level → add_logger_name →
  TimeStamper(iso,utc) → add_service_name → `_mask_pii` (маскирует
  `user_id/telegram_id/from_user_id/chat_id`) → rename `event`→`msg` →
  JSONRenderer.
- stdlib logging (aiogram, aio_pika, grpcio) проходят через тот же
  ProcessorFormatter — единый JSON формат.

### Секреты, которые НЕ должны попадать в логи

- Telegram bot token (`BOT_TOKEN`)
- OTP коды (`OtpService` не логирует код)
- FCM tokens (shared-logback masking)
- JWT body (shared-logback маскирует Bearer)
- User Telegram IDs (Python `_mask_pii` оставляет только `***<last3>`)

При появлении нового secret — добавить паттерн в `logback-base.xml` и
в `_mask_pii._SENSITIVE_KEYS`.

## M04 deferred items → followups

| Item | Куда |
|------|------|
| `/actuator/**` исключить из tracing sampling | M04 G11 backlog или M05 |
| docker-compose healthcheck directives | M06 Ops & Supply Chain |
| `AlertPublisher extends AbstractEventPublisher` | M05 (code-reviewer SHOULD #1) |
| Typed DTO для Alertmanager webhook | M06 (code-reviewer SHOULD #2) |
| mTLS вместо Bearer secret для `/internal/alert` | M06 |
| Per-subject/per-group thresholds в RedZoneGauge | Future (cross-service join) |

## Ссылки

- Исходные требования: `docs/report-before-v0.0.0/OWNER-ANSWERS.md`
  строки 1349-1564 (QA1..QA7).
- M04 план: `docs/milestones/M04-observability/PLAN.md`.
- Alert каталог: `docs/alerts.md`.
- Архитектура: `docs/architecture.md` раздел Observability stack.

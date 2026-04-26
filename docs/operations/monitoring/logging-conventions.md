# Logging Conventions

Применимо ко всем 6 Java-сервисам + Python-боту. Нарушения ловятся
Gradle-task'ом `verifyNoDebugInProd` + `verifyLogbackJsonInAllServices`
в `check` phase.

## Уровни

| Level | Когда использовать | Prod | Dev |
|-------|-------------------|------|-----|
| ERROR | Unhandled exception, data corruption, security violation | ✅ | ✅ |
| WARN | Retry exhausted, fallback сработал, deprecated path, config mismatch | ✅ | ✅ |
| INFO | Бизнес-событие (login, checkin published), startup/shutdown, scheduled tick | ✅ | ✅ |
| DEBUG | Детали обработки, branch'и, внутренние value'ы | ❌ | ✅ |
| TRACE | Реже чем DEBUG, только временно | ❌ | ❌ |

**Правило для prod:** `ru.rutcampustrack*` **всегда** `INFO`. CI-check
`verifyNoDebugInProd` в root `build.gradle.kts` проверяет regex
`ru\.rutcampustrack[^:]*:\s*DEBUG` в `application.yml` / `application-prod.yml`
(исключения: `application-dev.yml`). Если пропустишь — build fail.

**Исключения:** `org.springframework.cloud.gateway: WARN` в
api-gateway prod (гасим spam).

## Формат (JSON)

### Java (logstash-logback-encoder)

`shared-logback/logback-base.xml` задаёт единый формат:

```json
{
  "ts": "2026-04-20T14:15:30.123+03:00",
  "v": "1",
  "level": "INFO",
  "logger": "ru.rutcampustrack.auth.service.AuthService",
  "thread": "http-nio-9090-exec-3",
  "msg": "User logged in",
  "service": "auth-service",
  "traceId": "<hex>",
  "spanId": "<hex>",
  "userId": 42,
  "role": "student"
}
```

Все MDC ключи (`traceId`, `spanId`, custom context) автоматически
попадают в payload.

**Маскирование:** regex-маска через `PatternLayout`-extension в
`shared-logback`. Маскируется:

- `Bearer <jwt>` → `Bearer ***`
- `telegram_id=<digits>` → `telegram_id=***`
- `fcm_token=<value>` → `fcm_token=***`

При добавлении нового секрета — добавить паттерн в `logback-base.xml` +
regression-тест.

### Python-бот (structlog)

`bot/observability.py:setup_observability()`:

```
merge_contextvars → add_log_level → add_logger_name
  → TimeStamper(iso, utc, key="ts") → _add_service_name
  → _mask_pii → _rename_event_key("event"→"msg") → JSONRenderer
```

PII-masking в `_mask_pii` processor: `user_id`, `telegram_id`,
`from_user_id`, `chat_id` → `"***<last-3-digits>"` (сохраняет
отладочную ценность «тот же юзер или другой»).

stdlib logging (aiogram, aio_pika, grpcio) проходит через тот же
`ProcessorFormatter` — единый JSON для всех источников.

## trace_id correlation

**Java → Python через RabbitMQ:**

1. Java publisher: `AbstractEventPublisher.fillDefaults()` берёт
   `trace_id` из MDC (Micrometer Tracing bridge пишет `traceId` при
   HTTP-request entry) или генерирует UUID.
2. Envelope: `{"event_type", "event_id", "event_version", "trace_id",
   "source", "occurred_at", "payload"}`.
3. Python consumer: `bot/consumers/event_consumer.py` вытаскивает
   `trace_id` и биндит через `bind_trace_context(body["trace_id"], ...)`.
4. Все handler'ы бота получают contextvars автоматически.

**Python → Java:**

1. Bot `event_publisher.publish()` читает `trace_id` из structlog
   contextvars (или UUID fallback).
2. Java consumer: `AbstractEventConsumer.withTraceContext(envelope,
   runnable)` восстанавливает MDC.

**aiogram update → Python publish:**

Middleware `ObservabilityMiddleware` генерирует `trace_id=uuid4().hex`
per-update, биндит в structlog contextvars. EventPublisher дальше
видит его через `structlog.contextvars.get_contextvars()`.

## Что логировать

### Должно

- Логин/logout (без пароля/code/token)
- Создание/изменение бизнес-сущности (ticket, excuse, late-checkin)
- Scheduled tick с результатом (сколько записей обработано)
- Сценарии ошибок с контекстом (какой userId, какой lessonId)

### Не должно

- Telegram bot token (`BOT_TOKEN`)
- OTP коды
- Полный JWT (только jti если нужен)
- FCM tokens
- Password hashes
- Полные query-параметры URL (могут содержать token в edge cases)

## Запрос через Loki

```logql
# Все ошибки сервиса за 15 минут
{service="auth-service", level="ERROR"}

# Конкретный trace через все сервисы
{service=~".+"} | json | trace_id="<uuid>"

# User actions
{service="attendance-service"} | json | userId="42"
```

См. также `docs/operations/monitoring/observability.md` раздел «Типичные запросы».

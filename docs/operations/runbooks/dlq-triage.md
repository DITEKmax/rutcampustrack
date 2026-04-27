# DLQ triage runbook

Когда messages начинают копиться в `*.dlq` очередях — handler в одном
из consumer'ов кидает exception. Этот runbook описывает как найти
причину и расчистить DLQ.

## Когда срабатывает

Алёрт **`DLQBacklog`** в Prometheus
(`infra/prometheus/rules/rabbitmq.yml`):

```
rabbitmq_queue_messages{queue=~".*\\.dlq"} > 10  for 5m
severity: warning
```

Алёрт прилетает в Telegram через alertmanager → notification-web →
notification-bot → admin chat.

## Очереди под наблюдением

| Очередь | Consumer | Source events |
|---------|----------|---------------|
| `notification-bot.events.dlq` | notification-bot | fanout `rut-uit.events` (lesson.*, homework.*, excuse.*, otp.*, alert.fired, и т.д.) |
| `notification-web.history.dlq` | notification-web | events для history-store (M10) |
| Другие `*.dlq` | per-service | См. soustreten consumer |

## Retention DLQ

- **`notification-bot.events.dlq`** — TTL 7d, max-length 10000, drop-head
  (M16 G2 — `services/notification-bot/bot/consumers/event_consumer.py`).
- **`notification-web.history.dlq`** — без retention args (отложено в
  `future-ideas.md` § N6, ждёт PR).

После 7d (для bot DLQ) message исчезает автоматически. Если не успели
провести triage — событие потеряно навсегда. Поэтому **алёрт надо
обрабатывать в день срабатывания**.

## Шаги triage

### 1. Подтвердить что DLQ растёт

```bash
ssh deploy@<VPS>
docker exec rct-rabbitmq rabbitmqctl list_queues name messages --no-table-headers \
  | grep '\.dlq'
```

Если `notification-bot.events.dlq` имеет messages > 0 — есть что
разбирать.

### 2. Найти причину в логах

DLQ-сообщения попадают туда из-за handler exception. Найди их в Loki:

```
{service="rct-notification-bot"} |= "Handler failed" or |= "Traceback"
```

Или (если exception проходит до consumer'а):

```
{service="rct-notification-bot"} |~ "(?i)error|traceback"
| json | line_format "{{.event_type}} {{.msg}}"
```

Время — последние 30 минут до алёрта.

Типичные причины:
- **gRPC timeout** к academic/schedule — proverь `RabbitMQConnectionLost`
  и health endpoint'ы.
- **KeyError / payload schema mismatch** — Java publisher изменил
  структуру event'а, bot handler не обновили.
- **Bot-API limit** (Telegram 429 / 400) — handler пытается отправить
  message в archived chat / blocked user.
- **Redis недоступен** — `BotIdempotencyGuard.try_claim` fail-closed
  бросает RuntimeError → DLQ.

### 3. Прочитать сами DLQ-сообщения

```bash
docker exec rct-rabbitmq rabbitmqadmin --username=$RABBITMQ_USER \
  --password=$RABBITMQ_PASSWORD \
  get queue=notification-bot.events.dlq count=5 ackmode=ack_requeue_true
```

`ack_requeue_true` — подсмотрит content **без удаления** (peek).
В payload видно `event_type`, `event_id`, `payload` — полная
информация о неудавшемся событии.

### 4. Зафиксировать root cause + сделать fix

После выявления причины:

- **Bug в handler'е** → PR с fix'ом + regression test. После деплоя —
  step 5 (replay).
- **Bug в publisher'е (Java side)** → PR в соответствующем сервисе.
  Затем step 5.
- **Transient failure** (Redis blip, gRPC timeout) → root cause
  пройдёт сам, можно сразу step 5 (replay).

### 5. Replay сообщений

Когда fix задеплоен — переложить DLQ messages обратно в основную
очередь:

```bash
docker exec rct-rabbitmq rabbitmqadmin --username=$RABBITMQ_USER \
  --password=$RABBITMQ_PASSWORD \
  publish exchange=rut-uit.events routing_key="" \
  payload="$(rabbitmqadmin get queue=notification-bot.events.dlq count=1 \
            ackmode=ack_requeue_false | jq -r '.[0].payload')"
```

(`ack_requeue_false` — теперь действительно удаляем из DLQ.)

Idempotency-guard (M13 G8 + M16 G2) гарантирует что **если событие
уже было обработано до handler-bug'а** — replay не приведёт к дублю.

Для bulk-replay (>10 messages):

```bash
# TODO: shovel-plugin script — ждёт реальной нужды
# Пока вручную через цикл rabbitmqadmin
```

### 6. Очистить алёрт

После replay'я и убеждения что DLQ снова empty (или близко к 0):

```bash
docker exec rct-rabbitmq rabbitmqctl list_queues name messages --no-table-headers \
  | grep '\.dlq'
# должны быть нули
```

`DLQBacklog` алёрт автоматически resolve'нется через `for: 5m` после
очистки.

### 7. Записать инцидент

Если был не trivial — добавь запись в `docs/milestones/M??/NOTES.md`
текущего активного milestone:

- Дата + duration
- Root cause
- Сколько сообщений потерялось (если что-то истекло до 7d retention)
- Что изменили чтобы не повторилось

## Когда **не** делать replay

- **Plain validation failures** (e.g. JSON parse error — see
  `event_consumer.py:73-81`) — message всё равно невалиден, replay не
  поможет. Просто purge:
  ```bash
  docker exec rct-rabbitmq rabbitmqctl purge_queue notification-bot.events.dlq
  ```
- **Старая событийная схема** — если bot обновили на v2 schema, а в
  DLQ лежат v1 messages, они никогда не пройдут. Purge или manual
  re-derive.

## Связанные документы

- `docs/architecture/event-schemas.md` — схемы событий (для понимания
  payload в DLQ).
- `docs/architecture/architecture.md` § «Eventing» — кто publish'ит,
  кто consume'ит.
- `docs/operations/monitoring/alerts.md` — все Prometheus alerts.
- `services/notification-bot/bot/services/idempotency_guard.py` —
  guarantees при replay'е.

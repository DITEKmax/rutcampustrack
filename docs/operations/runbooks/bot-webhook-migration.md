# Alertmanager → Bot Webhook Migration Runbook (NEW-154)

> Reference для текущего flow `Prometheus → Alertmanager →
> notification-web `/internal/alert` → RabbitMQ `alert.fired` → bot →
> Telegram` + как менять payload schema или endpoint без downtime.

## Current flow

```
┌──────────────┐        ┌───────────────┐
│  Prometheus  │─ eval ▶│ Alert rules   │
└──────────────┘        │ (yml files)   │
                        └───────┬───────┘
                                │ firing/resolved
                                ▼
                        ┌───────────────┐
                        │ Alertmanager  │
                        └───────┬───────┘
                                │ POST /internal/alert
                                │ Authorization: Bearer $ALERT_WEBHOOK_SECRET
                                ▼
                        ┌─────────────────────┐
                        │ notification-web    │
                        │ AlertController     │
                        │ - validate secret   │
                        │ - map to payload    │
                        │ - publish rabbit    │
                        └──────────┬──────────┘
                                   │ alert.fired event
                                   ▼
                        ┌─────────────────────┐
                        │ RabbitMQ fanout     │
                        │ rut-uit.events      │
                        └──────────┬──────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │ notification-bot    │
                        │ handle_alert_fired  │
                        │ - parse admin list  │
                        │ - format HTML       │
                        │ - send to Telegram  │
                        └─────────────────────┘
```

## Alertmanager payload v4 contract

Alertmanager POST'ит JSON со следующей shape'ой (docs: 
<https://prometheus.io/docs/alerting/latest/configuration/#webhook_config>):

```json
{
  "version": "4",
  "groupKey": "...",
  "status": "firing|resolved",
  "receiver": "bot-webhook",
  "groupLabels": { "alertname": "...", "severity": "..." },
  "commonLabels": { ... },
  "commonAnnotations": { ... },
  "externalURL": "http://alertmanager:9093",
  "alerts": [
    {
      "status": "firing|resolved",
      "labels": {
        "alertname": "ServiceDown",
        "severity": "critical",
        "instance": "auth-service:9090",
        "job": "auth-service"
      },
      "annotations": {
        "summary": "Сервис auth-service недоступен",
        "description": "..."
      },
      "startsAt": "2026-01-01T10:00:00Z",
      "endsAt": "0001-01-01T00:00:00Z",
      "generatorURL": "http://prometheus:9090/..."
    }
  ]
}
```

Наш `AlertController` читает `status`, `alerts[].labels.alertname`,
`alerts[].labels.severity`, `alerts[].annotations.summary`,
`alerts[].annotations.description`, `alerts[].status`. Всё остальное
игнорируется. Добавление новых полей в alertmanager не ломает endpoint —
Jackson'у параметры unknown-fields пропускаются (`FAIL_ON_UNKNOWN=false`).

## Изменение payload schema (без downtime)

### Сценарий A: alertmanager upgrade, новая v5 schema

Alertmanager 0.28+ может ввести breaking `version: "5"`.

**Steps:**
1. Развернуть в **новом endpoint'е** (`/internal/alert/v5`) — оставив
   старый `/internal/alert` работать с v4.
2. `application.yml` для notification-web: 2 controller'а, 2 routes,
   общий publisher в Rabbit (`alert.fired` — единое событие).
3. Смена alertmanager URL в `infra/alertmanager/alertmanager.yml`:
   `url: http://notification-web:9094/internal/alert/v5`.
4. Restart alertmanager: `docker compose up -d --no-deps alertmanager`.
5. Мониторить 24ч — никаких событий в `alert.fired`? откатить через
   `url` → v4.
6. Через 7 дней без инцидентов — удалить старый `/internal/alert`
   handler в следующем релизе.

### Сценарий B: изменение `alert.fired` event schema

1. Новый `event_type: alert.fired.v2`. Bot consumer добавляет handler
   в `event_dispatcher.py` ПАРАЛЛЕЛЬНО со старым `alert.fired`.
2. notification-web публикует обе версии (dual-write) — ту же инфо в
   `alert.fired` и `alert.fired.v2`. Это лишний трафик, но safe.
3. После deploy bot с новым handler — проверить, что
   `alert.fired.v2` доставляется (мониторить `outbox_published_total{
   event_type=alert.fired.v2}`).
4. Убрать legacy `alert.fired` publish в notification-web следующим
   релизом.

## Изменение endpoint'а (/internal/alert → другой)

1. Новый endpoint (`/api/alert/v1`, например) + Bearer auth.
2. Добавить route в alertmanager `receivers[]` + переключить `route:`.
3. Оставить старый `/internal/alert` работать 30 дней (для rollback).
4. Remove старый handler в следующем релизе.

## Rollback plan

### Alertmanager не шлёт в notification-web
- Проверить `docker logs rct-alertmanager --tail 100` — есть ли ошибки
  POST.
- Проверить `docker logs rct-notification-web --tail 100 | grep
  "/internal/alert"` — приходит ли Bearer Auth.
- Временный fix: откатить `infra/alertmanager/alertmanager.yml` на
  предыдущий коммит + `docker compose up -d --no-deps alertmanager`.

### notification-web → bot не доставляет
- Проверить `rabbitmqctl list_queues` — есть ли backlog на
  `notification-bot.events`.
- Проверить `docker logs rct-notification-bot --tail 100 | grep
  alert.fired` — consumer получает ли events.
- `ALERT_WEBHOOK_SECRET` != `BOT_ALERT_TOKEN` — две разных переменных,
  secret между Alertmanager↔notification-web, token между alert
  handler↔Telegram admin lookup.

### Admin Telegram-ID не получает
- `docker exec rct-notification-bot env | grep ADMIN_TELEGRAM_IDS` —
  comma-separated, правильный формат.
- `docker logs rct-notification-bot | grep "_parse_admin_ids"` — какой
  список парсится.

## Testing locally

`services/notification-service/notification-app/src/test/.../AlertControllerTest.java`
+ `services/notification-bot/tests/test_alert_fired.py` покрывают оба
конца. Intent: добавить integration test через docker compose local →
отложено в M09 Группе 9 audit (если перед v1.0 ещё останется scope).

## Связанные секреты

- `ALERT_WEBHOOK_SECRET` — Bearer между Alertmanager и notification-web
  `/internal/alert`.
- `BOT_ALERT_TOKEN` — не путать: это Telegram bot token для rct-bot,
  используется в format-helper'е; ротируется по
  [secret-rotation.md](secret-rotation.md).
- `ADMIN_TELEGRAM_IDS` (в `.env.prod`, не секрет но sensitive) —
  comma-separated IDs админов, получающих alerts.

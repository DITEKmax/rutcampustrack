# Alerts Catalog

Все правила в `infra/prometheus/rules/service-health.yml`. Routing —
`infra/alertmanager/alertmanager.yml`. Доставка — через
`notification-web:9094/internal/alert` → RabbitMQ `alert.fired` →
notification-bot → Telegram админы (`ADMIN_TELEGRAM_IDS`).

**Severity levels:**

- **critical** — fire всегда (включая 22:00-08:00 MSK quiet hours).
  Ожидание: разбудить on-call. Примеры: сервис упал полностью.
- **warning** — muted в тихий час (warning, накопившиеся за ночь,
  придут в 08:00 MSK одной пачкой). Примеры: высокий heap, медленный
  publisher.

## Группа `service-health`

### ServiceDown (critical)

**Triggers:** `up == 0` в течение `1m`.

**Что значит:** Prometheus 4+ раз подряд не scrape'ил `/actuator/prometheus`
на target'е. Сервис либо crashed, либо network изоляция.

**Runbook:**
1. `docker compose ps` — контейнер down? restart loop?
2. `docker logs rct-<service> --tail 200` — stack trace старта?
3. Проверь зависимости (DB, Redis, RabbitMQ): `up{job=~"postgres|redis|.."}`.
4. Если scheduled restart (deploy) — silence alert на `30m`.

**Inhibited by:** ничего (это самый низкий уровень).

**Inhibits:** `HealthCheckDown` для того же target.

### HealthCheckDown (warning)

**Triggers:** `health_status{status!="UP"} == 1` в течение `2m`.

**Что значит:** `/actuator/health` вернул `DOWN`/`OUT_OF_SERVICE`.
Сервис жив, но зависимость (DB/Rabbit/Redis/Mongo) деградирует.

**Runbook:**
1. `curl -s http://<host>:<port>/actuator/health | jq` — какая deps
   упала?
2. Проверь соответствующий контейнер (Postgres/Mongo/Redis/Rabbit).
3. Логи сервиса — `{service="<svc>"} |= "connection refused"`?

## Группа `outbox-eventing`

### OutboxLagHigh (warning)

**Triggers:** `outbox_lag_seconds > 300` в течение `2m`.

**Что значит:** Старейшая pending-запись в outbox висит > 5 мин.
Publisher job не справляется или RabbitMQ недоступен.

**Runbook:**
1. RabbitMQ health: `docker logs rct-rabbitmq`, management UI
   (`localhost:15672` в dev).
2. Сервис-источник логи: `{service="<svc>"} |= "OutboxPublisher"`.
3. `outbox.published.total` vs `outbox.failed.total` — rate failed?
4. Если много failed — DLQ или retry loop. Проверь причину в
   `last_error` (`outbox` table / collection).

### DLQBacklog (warning)

**Triggers:** `rabbitmq_queue_messages{queue=~".*\\.dlq"} > 10` в
течение `5m`.

**Что значит:** Consumer систематически падает на message'ах, они
улетают в dead-letter queue.

**Runbook:**
1. Выбери consumer из `queue` label (`notification-web.events.dlq`
   → notification-web; `notification-bot.events.dlq` → bot).
2. Логи consumer'а — ищи exception'ы.
3. Если payload invalid (schema breaking) — исправь publisher + purge
   DLQ вручную (не retry автоматически, можно зациклить).

## Группа `infra`

### DiskUsageHigh (warning)

**Triggers:** `container_fs_usage_bytes / container_fs_limit_bytes > 0.80`
в течение `10m`.

**Что значит:** Контейнер использует > 80% своего filesystem'а.

**Runbook:**
1. `docker system df` — какой volume жирный? (обычно logs или
   Postgres data).
2. Mongo `db.stats()` / PG `pg_total_relation_size()` — найти большие
   таблицы/коллекции.
3. Старые backup'ы / docker images: `docker image prune`.

### JvmHeapPressure (warning)

**Triggers:** `jvm_memory_used_bytes{area="heap"} /
jvm_memory_max_bytes{area="heap"} > 0.90` в течение `5m`.

**Что значит:** JVM heap > 90% использован. GC не успевает или
утечка.

**Runbook:**
1. Grafana → Spring Boot APM → JVM memory панели. Растёт linearly =
   утечка, пила = GC-pattern.
2. Снять heap dump:
    ```bash
    docker exec rct-<service> jmap -dump:live,format=b,file=/tmp/heap.hprof <pid>
    ```
3. Analyze в Eclipse MAT / VisualVM.
4. Быстрый workaround: рестарт. Долгосрочно — найти leak.

## Группа `business-anomaly`

### CheckinRateZero (warning)

**Triggers:** В рабочие часы (09:00-18:00 MSK, пн-пт):
```
(rate(attendance_checkin_total[10m]) == 0)
  or absent(attendance_checkin_total)
```
в течение `10m`.

**Что значит:** Студенты не отмечаются совсем. Возможные причины:
Gateway роутинг сломан, attendance-service упал, геоотметка
повсеместно отвергается (campus boundary baг?).

**Runbook:**
1. attendance-service UP? `up{job="attendance-service"}`.
2. `Grafana → CheckinRateZero панель → разбивка по статусам` — может,
   все `absent` (никого нет в кампусе)? Это норма в каникулы — silence.
3. api-gateway лог: `{service="api-gateway"} |~ "attendance"`. 500-ки?

### InternalJwtFallbackUnexpected (warning)

**Triggers:** `rate(internal_jwt_fallback_total[5m]) > 0.1` в течение
`5m`.

**Что значит:** KI-2 silent fallback — какой-то service-to-service
вызов идёт через legacy `X-User-*` headers вместо Internal JWT.
Регрессия M03a rollout.

**Runbook:**
1. Label `job` + `from`/`to` покажет downstream service где fallback
   сработал.
2. Grep в upstream (каллер) — где Gateway/API-client не шлёт
   `X-Internal-Token`.
3. M03a spec: `docs/internal-jwt-spec.md`. Проверить что issuer
   подписывает все запросы.

## Silencing alerts

На VPS:
```bash
docker exec rct-alertmanager amtool silence add \
  alertname="ServiceDown" \
  job="auth-service" \
  --duration=30m \
  --comment="Scheduled restart for migration"
```

Список активных silences:
```bash
docker exec rct-alertmanager amtool silence query
```

## Quiet hours (22:00-08:00 MSK)

Определены в `alertmanager.yml` `time_intervals.quiet-hours-msk`
(`19:00-05:00 UTC`). Только `warning` severity уходит в mute; `critical`
всегда fire'ит.

Изменить — отредактировать `alertmanager.yml` + передеплой
Alertmanager контейнера.

## Изменение каталога

1. Добавь правило в `infra/prometheus/rules/service-health.yml`.
2. Добавь раздел в этот файл (severity, trigger, runbook).
3. Обнови Grafana dashboard если нужна визуализация.
4. `docker compose restart prometheus alertmanager`.
5. Верифицировать `amtool alert query` после fire.

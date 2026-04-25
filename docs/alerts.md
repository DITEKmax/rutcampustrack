# Alerts catalog — RutCampusTrack

Каталог всех Prometheus alert'ов. Cross-ref `infra/prometheus/rules/*.yml`.
Alertmanager routing описан в `infra/alertmanager/alertmanager.yml`,
flow до Telegram админа — `notification-web /internal/alert` →
RabbitMQ `alert.fired` → notification-bot consumer → admin chat
(`ADMIN_TELEGRAM_IDS`).

## Severity и routing

| Severity | Routing | Тихий час 22:00–08:00 MSK |
|----------|---------|----------------------------|
| `critical` | webhook немедленно | будит ночью (mute не применяется) |
| `warning` | webhook | mute'ится, накопившиеся придут утром |

`group_wait: 30s`, `group_interval: 5m`, `repeat_interval: 3h`.
Inhibit-rule: `ServiceDown` подавляет `HealthCheckDown` для того же
target (один сигнал достаточен).

## Каталог (18 alert'ов)

### 1. ServiceDown
**Файл:** `infra/prometheus/rules/service-health.yml` · severity=critical · for=1m

#### Symptom
В Telegram: `[CRITICAL] ServiceDown: <job>`. Prometheus не может scrape
target ≥ 1 минуту.

#### Meaning
Сервис либо упал (process exit), либо сеть/Docker сломаны. `up == 0`
включает все scrape jobs: backend services, alertmanager,
node-exporter, cadvisor, rabbitmq.

#### Runbook
1. `docker compose ps` — статус контейнера. Если `Restarting (XX)` —
   crashloop, см. `docker logs rct-<job> --tail 100`.
2. Если контейнер up но scrape fail — проверь network alias / port:
   `docker exec rct-prometheus wget -qO- http://<job>:<port>/actuator/prometheus`.
3. Если intentional restart (deploy/migration) — silence в Alertmanager
   на 15 мин: `docker exec rct-alertmanager amtool silence add alertname=ServiceDown job=<job> --duration=15m`.

**Inhibits:** HealthCheckDown для того же target.

---

### 2. HealthCheckDown
**Файл:** `service-health.yml` · severity=warning · for=2m

#### Symptom
`<instance> health=DOWN`. Spring Boot Actuator health endpoint вернул
не-UP (DOWN либо OUT_OF_SERVICE).

#### Meaning
Сервис up (process жив), но dependency сломана: RabbitMQ unreachable,
Postgres connection pool exhausted, Mongo replica set не сформирован,
Redis за timeout.

#### Runbook
1. `curl http://<host>:<port>/actuator/health` — ищи `components.<name>.status: DOWN`.
2. По имени dependency: проверь её через `docker compose ps` + healthcheck.
3. Если RabbitMQ DOWN — также сработает `RabbitMQConnectionLost`.
4. Если несколько сервисов одновременно — общий dependency upstream.

---

### 3. OutboxLagHigh
**Файл:** `service-health.yml` · severity=warning · for=2m

#### Symptom
`Outbox lag <duration> в <job>`. Старейшее pending-событие в
`event_outbox` table висит > 5 мин.

#### Meaning
Publisher job не справляется или RabbitMQ недоступен. Метрика
`outbox_lag_seconds` экспортируется per-event-type через
`OutboxMetrics` (M02). Растущий lag → backpressure на event'ах.

#### Runbook
1. Проверь RabbitMQ: `docker exec rct-rabbitmq rabbitmqctl status`.
2. Logs publisher'а: `{job=~"rct-.*"} |= "outbox" |= "publish"` в Loki.
3. Если Rabbit OK — publisher job отказался либо `application-prod.yml`
   `outbox.publisher.enabled=false`. Перезапусти сервис.
4. Если lag > 30 мин — manual trigger через `POST /actuator/scheduledtasks` (admin-only).

---

### 4. DLQBacklog
**Файл:** `infra/prometheus/rules/rabbitmq.yml` · severity=warning · for=5m

#### Symptom
`DLQ <queue> > 10 сообщений`.

#### Meaning
Consumer постоянно падает на сообщениях, они улетают в DLQ. M13 G19:
до этого alert был silent dangling (rabbitmq_prometheus plugin не был
включён в `rabbitmq:3.13-alpine` image).

#### Runbook
1. Имя consumer'а — из `<queue>`: `notification-web.events.dlq` →
   notification-web. `attendance.events.dlq` → attendance.
2. Logs consumer'а: `{job=<consumer>} |= "DLQ" or |= "exception"`.
3. Inspect message: `docker exec rct-rabbitmq rabbitmqctl get_messages <queue> 1`.
4. Fix root cause → republish из DLQ если payload валидный (manual,
   через RabbitMQ management UI `/api/queues/<vhost>/<dlq>/get`).

---

### 5. RabbitMQQueueBacklog
**Файл:** `rabbitmq.yml` · severity=warning · for=5m

#### Symptom
`Очередь <queue> > 1000 pending`. Non-DLQ очереди.

#### Meaning
Consumer не справляется. Slow processing, deadlock в DB, нехватка
worker thread'ов, или просто burst events overflow.

#### Runbook
1. `docker exec rct-rabbitmq rabbitmqctl list_queues name messages messages_ready consumers`.
2. Если `consumers = 0` — consumer service вообще не подключён,
   также увидишь `RabbitMQConnectionLost`.
3. Если `consumers > 0` но messages растут — смотри latency consumer'а
   (`http_server_requests_seconds` в HighRequestLatency).
4. Если транзитный peak — wait + observe; если sustainable load — bump
   `concurrency` consumer (`spring.rabbitmq.listener.simple.concurrency`).

---

### 6. RabbitMQConnectionLost
**Файл:** `rabbitmq.yml` · severity=critical · for=1m

#### Symptom
`RabbitMQ без активных подключений`.

#### Meaning
`rabbitmq_connections == 0`. Catastrophic — все consumer'ы отвалились
или Rabbit перезапустился без graceful reconnect клиентов. Все
async-events копятся, outbox lag растёт.

#### Runbook
1. `docker compose ps rabbitmq` — up?
2. `docker logs rct-rabbitmq --tail 50` — ошибки startup / network.
3. Restart consumer-сервисов чтобы триггернуть reconnect:
   `docker compose restart academic-service schedule-service attendance-service notification-web notification-bot`.
4. Проверь `RABBITMQ_USER` / `RABBITMQ_PASSWORD` в `.env.prod` —
   credentials change?

---

### 7. ContainerMemoryHigh
**Файл:** `infra/prometheus/rules/resource-limits.yml` · severity=warning · for=5m

#### Symptom
`Контейнер <name> > 90% memory`.

#### Meaning
Контейнер использует > 90% своего mem_limit ≥ 5 мин. Утечка, cache
overgrowth или недооценённый budget. Все 26 контейнеров имеют
`mem_limit` после M11 G11 (6.1GB total на 8GB VPS).

#### Runbook
1. `docker stats --no-stream rct-<name>` — точное использование.
2. Heap dump для Java сервисов:
   `docker exec <name> jcmd 1 GC.heap_dump /tmp/heap.hprof`.
3. Если transient (request burst) — wait. Sustainable — bump
   `mem_limit` в `docker-compose.prod.yml` + update `docs/resource-limits.md`.
4. См. также `JvmHeapPressure` для Java-specific heap saturation.

---

### 8. ContainerWithoutMemoryLimit
**Файл:** `resource-limits.yml` · severity=warning · for=10m

#### Symptom
`Контейнер <name> без mem_limit`.

#### Meaning
Контейнер запущен без `mem_limit`. Защита от division-by-zero в
`ContainerMemoryHigh` + сигнал что новый сервис добавили без budget.
В норме fire'ить не должен (после M11 G11 все 26 имеют limit).

#### Runbook
1. Найди сервис в `docker-compose.prod.yml` — должен быть `mem_limit:`.
2. Если новый — добавь limit + update `docs/resource-limits.md` table.

---

### 9. DiskUsageHigh
**Файл:** `service-health.yml` · severity=warning · for=10m

#### Symptom
`Disk <device> > 80%`.

#### Meaning
cAdvisor видит > 80% использования filesystem на VPS. На 120GB VPS
80% = 96GB.

#### Runbook
1. SSH на VPS: `df -h`, `du -sh /var/lib/docker/volumes/* | sort -h | tail`.
2. Очисти старые backups: `find /opt/backups -mtime +14 -delete`
   (retention 7d уже автомат — M13 G15).
3. Docker prune: `docker system prune --volumes -f` (ОСТОРОЖНО — удаляет
   unused volumes; сначала `docker volume ls`).
4. Logs rotation: проверь `/etc/docker/daemon.json` `log-opts.max-size`.

---

### 10. JvmHeapPressure
**Файл:** `service-health.yml` · severity=warning · for=5m

#### Symptom
`JVM heap > 90% в <application>`.

#### Meaning
Java сервис использует > 90% configured `-Xmx` heap. Возможна утечка
либо memory-heavy запрос (отчёты с большими date range).

#### Runbook
1. Grafana → Spring Boot APM → JVM memory панели. Растёт linearly =
   утечка, пила = GC-pattern.
2. `docker exec rct-<app> jcmd 1 GC.heap_info` — current usage.
3. Heap dump: `jcmd 1 GC.heap_dump /tmp/heap.hprof` + analyse через
   Eclipse MAT.
4. Sustainable — bump `JAVA_OPTS=-Xmx<N>m` (M09 G7 NEW-154).

---

### 11. HikariPoolExhaustion
**Файл:** `service-health.yml` · severity=warning · for=5m

#### Symptom
`HikariCP pool > 80% в <application>`.

#### Meaning
> 80% pool занято ≥ 5 мин. Concurrent queries растут быстрее чем pool
spec'нут. Рискует connection-timeout. Hot-spot connections ≠ closed
(leak — `leak-detection-threshold=60s` логирует).

#### Runbook
1. Logs: `{application=<app>} |= "HikariPool" |= "leak"` в Loki.
2. Slow queries: `pg_stat_statements` ORDER BY total_exec_time DESC
   (через `psql -h <vps>`).
3. Если конкретная query — оптимизируй (composite index из M05) либо
   bump `spring.datasource.hikari.maximum-pool-size`.
4. Также проверь `HighRequestLatency` для коррелирующего endpoint'а.

---

### 12. HighErrorRate
**Файл:** `service-health.yml` · severity=warning · for=5m

#### Symptom
`HTTP 5xx rate > 1 req/s в <job>`.

#### Meaning
Сервис возвращает > 1 запрос в секунду 5xx за 5 мин. Регрессия после
deploy, dependency отвалилась, panic loop. Метрика
`http_server_requests_seconds_count` экспортируется Spring Boot
Actuator из коробки.

#### Runbook
1. Tempo trace search по `http.status_code=500` за последние 5 мин →
   stack trace ошибки.
2. Logs: `{job=<job>}, level=ERROR` в Loki.
3. Если correlate с deploy time — rollback (`docker compose pull` +
   prev tag).
4. Если correlate с upstream issue (`HealthCheckDown`) — root cause
   там.

---

### 13. HighRequestLatency
**Файл:** `service-health.yml` · severity=warning · for=10m

#### Symptom
`p95 latency > 2s в <job>`.

#### Meaning
95-percentile latency request'а > 2 секунд за 10 мин. Slow path:
N+1 queries, missing index, cache miss storm, downstream slow.

#### Runbook
1. Tempo: trace search top-by-duration → найди slow span.
2. Если DB span — `pg_stat_statements`, добавь index, M05 patterns.
3. Если RestTemplate / WebClient к downstream — другой service slow,
   проверь `HighRequestLatency{job=<downstream>}` параллельно.
4. Если cache-related — Redis miss rate в Grafana
   (`redis_keyspace_hits / redis_keyspace_total`).

---

### 14. CheckinRateZero
**Файл:** `service-health.yml` · severity=warning · for=10m

#### Symptom
`Нет гео-отметок 10 минут подряд в рабочее время` (UTC 06–15, Mon–Fri).

#### Meaning
`rate(attendance_checkin_total) == 0` либо counter совсем отсутствует
(M09 G11 H5 fix добавил `absent()` branch — без этого fresh restart
+ 0 отметок не fire'ил alert). В рабочее время аномалия: gateway не
пускает, клиенты offline массово, либо геоотметка повсеместно отваливается.

#### Runbook
1. Smoke endpoint: `curl https://ruttrack.site/api/health` → должен
   вернуть 200 без auth.
2. Manual check: открыть PWA как студент → попробовать отметиться.
3. Logs gateway: `{job="api-gateway"} |= "/api/attendance"` за 10 мин.
4. Если intentional pause (учебный праздник) — silence на день:
   `amtool silence add alertname=CheckinRateZero --duration=24h --comment="Каникулы"`.

---

### 15. InternalJwtFallbackUnexpected
**Файл:** `service-health.yml` · severity=warning · for=5m

#### Symptom
`KI-2 fallback rate=<value>/s в <job>`.

#### Meaning
Service-to-service вызов не несёт Internal JWT (M03a). В норме = 0.
Rate > 0.1/s = регрессия (новый endpoint не получил token, либо
clock skew между сервисами).

#### Runbook
1. Counter `internal_jwt_fallback_total` имеет labels `from`/`to` —
   identify пара сервисов.
2. Logs от service: `{job=<from>} |= "internal_jwt_fallback"` →
   stack trace вызова.
3. Чаще всего fix — добавить `@Bean InternalJwtIssuer` injection в
   новый client / restTemplate (M03a pattern). См. `shared-security`.

---

### 16. SslCertExpiresSoon
**Файл:** `infra/prometheus/rules/ssl-expiry.yml` · severity=warning · for=10m

#### Symptom
`TLS-cert <instance> истекает через <N> дней`. Threshold: < 30 дней.

#### Meaning
Метрика `probe_ssl_earliest_cert_expiry` от blackbox-exporter probing'а
`https://ruttrack.site` показывает что cert истекает менее чем через
30 дней. Let's Encrypt **должен** auto-renew (certbot loop каждые 12h
+ nginx auto-reload каждые 5 мин), но что-то сломалось.

#### Runbook
1. `docker exec rct-certbot certbot certificates` — какие certs
   зарегистрированы и когда истекают.
2. Если no certs / fail — manual renew:
   `docker exec rct-certbot certbot renew --force-renewal`.
3. Logs certbot: `docker logs rct-certbot --tail 100 | grep -E "renew|error"`.
4. Если ACME challenge fail — проверь nginx `/.well-known/acme-challenge/`
   доступен на :80 (HTTP), firewall не блочит.
5. См. `docs/runbooks/cert-renewal.md` для full troubleshooting.

---

### 17. SslCertExpiresUrgently
**Файл:** `ssl-expiry.yml` · severity=critical · for=5m

#### Symptom
`URGENT: cert <instance> истекает через <N> дней`. Threshold: < 7 дней.

#### Meaning
Auto-renew не сработал за **23+ дня** (от 30d threshold выше до 7d).
Будит ночью — service degradation imminent.

#### Runbook
1. Manual force-renewal **сейчас**:
   `docker exec rct-certbot certbot renew --force-renewal`.
2. Если Let's Encrypt rate-limit (5/week per domain): подождать сброса
   или DNS-01 challenge через alternative provider.
3. Если ACME HTTP-01 challenge fail: `curl http://ruttrack.site/.well-known/acme-challenge/test`
   должен вернуть 404 (не 502/connection refused).
4. Backup plan: temporary self-signed cert чтобы не потерять HTTPS
   полностью, потом разобрать root cause.

---

### 18. SslProbeFailed
**Файл:** `ssl-expiry.yml` · severity=critical · for=10m

#### Symptom
`Blackbox probe <instance> fail'ит`.

#### Meaning
`probe_success == 0` ≥ 10 мин. Не удаётся завершить TLS handshake к
HTTPS endpoint'у. Cert уже expired, revoked, либо HTTPS endpoint down.

#### Runbook
1. Если параллельно `ServiceDown{job="nginx"}` или `ServiceDown` для
   backend — root cause там.
2. `openssl s_client -connect ruttrack.site:443 -servername ruttrack.site`
   — посмотри cert details, errors.
3. Проверь DNS: `nslookup ruttrack.site` — A-record указывает на VPS?
4. Если cert expired — см. `SslCertExpiresUrgently` runbook.

---

## Cross-ref — файлы и labels

| Alert | Rule file | Source metric | Severity |
|-------|-----------|---------------|----------|
| ServiceDown | service-health.yml | up | critical |
| HealthCheckDown | service-health.yml | health_status | warning |
| OutboxLagHigh | service-health.yml | outbox_lag_seconds | warning |
| DLQBacklog | rabbitmq.yml | rabbitmq_queue_messages | warning |
| RabbitMQQueueBacklog | rabbitmq.yml | rabbitmq_queue_messages | warning |
| RabbitMQConnectionLost | rabbitmq.yml | rabbitmq_connections | critical |
| ContainerMemoryHigh | resource-limits.yml | container_memory_usage_bytes | warning |
| ContainerWithoutMemoryLimit | resource-limits.yml | container_spec_memory_limit_bytes | warning |
| DiskUsageHigh | service-health.yml | container_fs_usage_bytes | warning |
| JvmHeapPressure | service-health.yml | jvm_memory_used_bytes | warning |
| HikariPoolExhaustion | service-health.yml | hikaricp_connections_active | warning |
| HighErrorRate | service-health.yml | http_server_requests_seconds_count | warning |
| HighRequestLatency | service-health.yml | http_server_requests_seconds_bucket | warning |
| CheckinRateZero | service-health.yml | attendance_checkin_total | warning |
| InternalJwtFallbackUnexpected | service-health.yml | internal_jwt_fallback_total | warning |
| SslCertExpiresSoon | ssl-expiry.yml | probe_ssl_earliest_cert_expiry | warning |
| SslCertExpiresUrgently | ssl-expiry.yml | probe_ssl_earliest_cert_expiry | critical |
| SslProbeFailed | ssl-expiry.yml | probe_success | critical |

## E2E test (alertmanager → Telegram)

**Coverage без manual smoke** (M13 G19, owner-policy «ничего руками»):

| Этап | Test |
|------|------|
| Prometheus eval rules | `promtool check rules infra/prometheus/rules/*.yml` (G23 dry-run) |
| Alertmanager → webhook | `AlertControllerTest` (8 cases, notification-web) |
| Webhook auth (Bearer) | `AlertControllerTest.missingAuthorization_returns401_andDoesNotPublish` |
| Webhook → RabbitMQ publish | `AlertControllerTest.happyPath_publishesEachAlertAndReturns200` |
| RabbitMQ → bot dispatcher | `test_event_dispatcher.py::test_alert_fired_routing` |
| Bot → Telegram format | `test_alert_fired.py::test_format_message_critical` |
| Admin filter | `test_alert_fired.py::test_parse_admin_ids_mixed` |

**Live smoke** (`docker stop rct-auth-service` → ждать Telegram alert)
— deferred в G23 VPS dry-run. На VPS owner один batch'ом проверит
все alerts вместе с другими runbook'ами.

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

UI Alertmanager доступен через `https://ruttrack.site/alertmanager/`
(basic-auth, M13 G14).

## Quiet hours (22:00-08:00 MSK)

Определены в `alertmanager.yml` `time_intervals.quiet-hours-msk`
(`19:00-05:00 UTC`). Только `warning` severity уходит в mute; `critical`
всегда fire'ит. Изменить — отредактировать `alertmanager.yml` +
`docker compose restart alertmanager`.

## Изменение каталога

1. Добавь правило в `infra/prometheus/rules/<group>.yml`.
2. Validate: `promtool check rules infra/prometheus/rules/*.yml`.
3. Добавь раздел в этот файл (Symptom / Meaning / Runbook).
4. Обнови cross-ref таблицу + History changes.
5. Grafana dashboard если нужна визуализация.
6. `docker compose restart prometheus alertmanager` на prod.

## История изменений

- **M04 G9** (2026-04-20): создана базовая infra (Alertmanager → webhook
  → bot → Telegram), 9 первых alerts.
- **M09 G7** (2026-04-24): + ContainerMemoryHigh, ContainerWithoutMemoryLimit
  (NEW-157).
- **M09 G11** (2026-04-24): CheckinRateZero — `absent()` branch fix.
- **M13 G19** (2026-04-25):
  - Switched rabbitmq image на `:3.13-management-alpine` (digest pin) —
    включает rabbitmq_prometheus plugin.
  - Scrape job `rabbitmq:15692/metrics` в prometheus.yml.
  - Перенесён `DLQBacklog` в `rabbitmq.yml` (был silent dangling).
  - Добавлены `RabbitMQQueueBacklog`, `RabbitMQConnectionLost`,
    `HighErrorRate`, `HighRequestLatency`. Итого 15 alerts (AC-13 «15+»).
  - Документ полностью переработан под Symptom/Meaning/Runbook standard.
- **M13 G20** (2026-04-25): blackbox-exporter + 3 SSL alerts:
  `SslCertExpiresSoon` (30d warning), `SslCertExpiresUrgently` (7d
  critical), `SslProbeFailed` (probe_success == 0 critical). Итого 18.

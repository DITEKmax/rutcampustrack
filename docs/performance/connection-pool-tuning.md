# Connection Pool Tuning

**Версия:** v0.0.0 (M05 Группа 6, NEW-147)
**Последнее обновление:** 2026-04-20

Документ фиксирует значения HikariCP connection pool, формулу
подбора, триггеры пересмотра и мониторинг.

---

## Текущие значения (v0.0.0)

| Сервис | maximum-pool-size | minimum-idle | Обоснование |
|--------|-------------------|--------------|-------------|
| academic-service | **20** | 5 | Write-heavy (headman actions, admin CRUD) + read-traffic (каждый запрос делает RBAC-lookup даже с Redis-кешем на промахе). |
| schedule-service | **20** | 5 | Heavy reads (week-journal через gRPC-клиент + REST). Scheduled jobs (CRON-01/02 lesson activation/closure). |
| auth-service | **10** | 3 | Read-only: login lookup по логину при /auth/login. Нет long transactions. Pool меньше — экономия PG connections. |
| attendance-service | — | — | MongoDB only, datasource не используется. |
| notification-service | — | — | Нет БД (stateless WebSocket forwarder). |

Все сервисы используют единые timeout'ы:

```yaml
connection-timeout: 5000          # 5 сек — fail-fast если pool exhausted
idle-timeout: 600000              # 10 мин — idle connection release
max-lifetime: 1800000             # 30 мин — PG автоматически закрывает stale
leak-detection-threshold: 60000   # 1 мин — detects long-running tx (log warn)
```

## Формула подбора

**Отправная формула** (HikariCP wiki / Oracle):

```
connections = ((core_count × 2) + effective_spindle_count)
```

Где:
- `core_count` — CPU cores на БД-сервере.
- `effective_spindle_count` — 1 для SSD, 0 если dataset в RAM, >1 для RAID HDD.

**Для проекта v0.0.0** (single-VPS Docker deploy):
- PG-контейнер: 4 vCPU, 1 SSD → `4 × 2 + 1 = 9` «магическое» число.
- Маркер: maximum-pool-size=20 даёт запас на burst (headman bulk-mark 30 студентов, concurrent admin CRUD).
- В prod-окружении с scale-out (2+ инстанса per service) общее число connections **удваивается** → PG `max_connections` должен быть ≥ 2 × 20 × N_services.

**Почему не формула 9 напрямую:** HikariCP рекомендует избегать agressive под-pool'ов. 20 даёт buffer на:
- concurrent batch-operations (M05 G4 bulk-mark);
- scheduled jobs конкурирующие с REST traffic (lesson activation CRON);
- hikaricp_connections_pending spike при short burst.

## Когда пересматривать

### Триггеры увеличения pool-size

1. **Alert `HikariPoolExhaustion` firing в Prometheus** — pool > 80%
   больше 5 минут. См. `infra/prometheus/rules/service-health.yml`.
2. **`hikaricp_connections_pending > 0`** стабильно (> 1 req/s) в
   Grafana `business-kpis` dashboard. Значит worker threads ждут
   connection.
3. **Concurrent traffic > 30 req/s** на сервис (business grows).
4. **Scale-out** — при деплое 2+ инстансов одного сервиса через
   compose scale / K8s replicas.

### Триггеры уменьшения pool-size

1. **PG `max_connections` предел** (default 100) не вмещает
   суммарный pool всех сервисов. Расчёт: `N_instances × pool_size`
   per service ≤ `0.7 × max_connections` (запас на admin, pg_stat).
2. **`hikaricp_connections_idle` стабильно ≈ max_pool** — overprovisioned.

### Read-replica рассмотрение

- Появление отдельного read-replica для `academic_db` → разделение
  pool на read/write:
  ```yaml
  spring.datasource.primary.hikari.maximum-pool-size: 12   # writes + strong reads
  spring.datasource.replica.hikari.maximum-pool-size: 20   # read-traffic
  ```
- Отдельный ADR при внедрении. V0.0.0 — нет read-replica.

## Monitoring

### Grafana panels (business-kpis dashboard, M04)

- `hikaricp_connections_active` — текущее количество занятых соединений.
- `hikaricp_connections_idle` — idle в pool.
- `hikaricp_connections_pending` — worker threads ждут connection
  (если > 0 — **уже проблема**).
- `hikaricp_connections_acquire_seconds` — p50/p95/p99 время получения
  connection. Норма < 1ms, плохо > 100ms.

### Alerts (service-health.yml)

- **`HikariPoolExhaustion`** — pool utilization > 80% больше 5 мин.
  Severity warning. Routed в Telegram через Alertmanager (M04).

## Known quirks

### leak-detection-threshold vs long batch jobs

`leak-detection-threshold=60000` (1 минута) — если транзакция держит
connection > 60s, HikariCP пишет **stack trace в WARN**. Это не
ошибка, а сигнал о возможном leak.

Legitimate long-running jobs (например, `M02 outbox PublisherJob`
в schedule-service при backlog 1000+ events) могут триггерить
warn. Это **не** меняет значение threshold глобально — если
конкретная job обязана держать connection долго, она должна:

- Явно использовать `@Scheduled` + `@Transactional(timeout=N)` с
  осознанно большим N;
- Либо разбить batch на chunks с отдельными транзакциями.

### HikariCP bootstrap timing

При старте сервиса Hikari сначала инициализирует `minimum-idle`
connections (lazy warm-up). Если БД медленно отвечает при старте
(например, Flyway миграция) — первые HTTP-запросы могут упираться
в `connection-timeout`. Смена `initialization-fail-timeout` в
`yml` не нужна (default работает), но **liveness probe** Spring
Boot autoconfigures ждёт полный bootstrap.

## Smoke-тест (manual)

```bash
# 30 concurrent HTTP requests в academic-service (после `docker compose up`):
for i in {1..30}; do
  curl -s -o /dev/null -w "%{http_code} %{time_total}\n" \
    -H "Authorization: Bearer $JWT" \
    http://localhost:8080/api/academic/groups/1 &
done; wait

# Ожидается:
# - все 200, time_total < 500ms
# - в логах нет "pool exhausted" / "connection is not available"
# - hikaricp_connections_pending_gauge spike'нул < pool_size, но не завис > 1s
```

## References

- `services/academic-service/academic-app/src/main/resources/application.yml` — academic Hikari config
- `services/schedule-service/schedule-app/src/main/resources/application.yml` — schedule
- `services/auth-service/src/main/resources/application.yml` — auth (smaller pool)
- `infra/prometheus/rules/service-health.yml` — `HikariPoolExhaustion` alert
- `docs/milestones/M05-performance/DECISIONS.md` — M05 group 6 decisions
- HikariCP official sizing guide: https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing

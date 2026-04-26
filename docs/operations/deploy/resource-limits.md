# Resource Limits (NEW-157, M09 G7)

> Memory budget для VPS 4GB + документация per-service лимитов.
> Цель: избежать OOMKilled при пиковых нагрузках (сбор отчётов,
> массовое начало пары) и не дать утечке в одном контейнере убить
> остальные.

## VPS environment

- **Total RAM:** 4GB.
- **Host OS overhead:** ~400MB (kernel, systemd, sshd, logrotate).
- **Docker daemon + networking:** ~200MB.
- **Доступно для контейнеров:** ~3.4GB. Ставим бюджет **3.2GB**
  (200MB headroom для spikes).

## Budget table (per-service)

### Business services (Java Spring Boot)

| Service | `mem_limit` | `mem_reservation` | JVM RAM% | Обоснование |
|---------|-------------|-------------------|----------|-------------|
| `auth-service` | 256M | 192M | `-XX:MaxRAMPercentage=75.0` | Stateless, 1 SELECT/login. Hikari 10 connections, Redis cache только OTP. |
| `academic-service` | 512M | 384M | 75.0 | Самый горячий gRPC-сервер + Caffeine cache (subjects/groups/RBAC). Batch endpoints потребляют больше. |
| `schedule-service` | 512M | 384M | 75.0 | LessonGeneration job держит `ScheduleItem`-кэш в памяти на период генерации (~пиковые 300MB при semester import). |
| `attendance-service` | 512M | 384M | 75.0 | MongoDB driver + report aggregations (Mongo aggregation pipelines). |
| `notification-web` | 256M | 192M | 75.0 | WebSocket STOMP + Web Push; event consumer. Без БД кэша. |
| `api-gateway` | 256M | 192M | 75.0 | Spring Cloud Gateway reactive + Caffeine token exchange cache. |
| **Subtotal Java** | **2304M** | **1728M** | | |

### Bot (Python)

| Service | `mem_limit` | `mem_reservation` | Обоснование |
|---------|-------------|-------------------|-------------|
| `notification-bot` | 256M | 128M | Aiogram async, без heavy deps. Реальный RSS в prod ~80-120MB. |
| **Subtotal Python** | **256M** | **128M** | |

### Infra (stateful + stateless)

| Service | `mem_limit` | `mem_reservation` | Обоснование |
|---------|-------------|-------------------|-------------|
| `postgres-academic` | 192M | 128M | Small DB (≤10k users + small ref tables). `shared_buffers=48M`, `effective_cache_size=144M`. |
| `postgres-schedule` | 256M | 192M | Большие таблицы (lessons up to 100k + schedule_items). `shared_buffers=64M`. |
| `mongo-attendance` | 384M | 256M | WiredTiger cache 192M + connections + index pages. Attendance самая hot БД (insertpath из PWA). |
| `redis` | 128M | 64M | OTP + RBAC cache (<10k keys, avg 100B). `maxmemory 96M`, `maxmemory-policy allkeys-lru`. |
| `rabbitmq` | 256M | 192M | Queue backlogs + 7-day retention; watermark `0.6`. |
| **Subtotal DB/MQ** | **1216M** | **832M** | |

### Observability stack

| Service | `mem_limit` | `mem_reservation` | Обоснование |
|---------|-------------|-------------------|-------------|
| `prometheus` | 192M | 128M | 14d retention, scrape interval 15s. |
| `grafana` | 96M | 64M | Read-only dashboards, no alerting logic. |
| `loki` | 192M | 128M | 14d log retention (~500MB/day). |
| `promtail` | 64M | 32M | Tail-only, без buffer'а. |
| `tempo` | 128M | 96M | Sampling 1.0 (пока traffic мал). |
| `alertmanager` | 64M | 32M | |
| `cadvisor` | 96M | 64M | Metric exporter, per-container. |
| `node-exporter` | 32M | 16M | Minimal host metrics. |
| **Subtotal obs** | **864M** | **560M** | |

### Nginx / frontends

| Service | `mem_limit` | `mem_reservation` | Обоснование |
|---------|-------------|-------------------|-------------|
| `nginx` (reverse-proxy) | 64M | 32M | 4 vhosts routing. |
| `pwa-nginx` + `mini-app-nginx` + `web-panel-nginx` + `landing-nginx` | 32M × 4 = 128M | 16M × 4 = 64M | Static-only. |
| `certbot` | 32M | 16M | Run раз в сутки, idle остальное время. |
| **Subtotal nginx** | **224M** | **112M** | |

### Summary

- **Total `mem_limit`:** 2304 + 256 + 1216 + 864 + 224 = **4864M**
- **Total `mem_reservation`:** 1728 + 128 + 832 + 560 + 112 = **3360M**

**Overcommit:** limits > RAM. Это OK т.к. normal-case RSS ниже лимитов,
пики разных сервисов статистически не совпадают. `mem_reservation`
(гарантированное) = 3360M — под физический budget 3.4GB.

Если OOM всё-таки случится — контейнер с превышением лимита будет убит
первым (не вся система). Alert ниже срабатывает до этого (>90% usage).

## JVM opts

Single-source-of-truth в `docker-compose.prod.yml` через env var
`JAVA_TOOL_OPTIONS`:

```yaml
environment:
  JAVA_TOOL_OPTIONS: >
    -XX:MaxRAMPercentage=75.0
    -XX:InitialRAMPercentage=50.0
    -XX:+UseG1GC
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/tmp
    -Djava.security.egd=file:/dev/./urandom
```

- `MaxRAMPercentage=75.0`: heap = 0.75 × mem_limit. Для
  `mem_limit=512M` → heap=384M, остальное — Metaspace, code cache,
  stacks (~128M).
- `InitialRAMPercentage=50.0`: сразу аллоцируем 50% чтобы G1GC не
  крутил expansion cycles на первые запросы.
- `UseG1GC`: default в JDK 21, пишем явно для читаемости.
- `HeapDumpOnOutOfMemoryError`: если всё-таки OOM — дамп в `/tmp` для
  post-mortem (volume сохраняется между restart'ами если tmpfs=/tmp
  не монтируется; иначе нужен `/var/lib/dumps` volume).

## Prometheus alert rule

Добавлено в `infra/prometheus/rules/resource-limits.yml`:

```yaml
groups:
  - name: resource-limits
    rules:
      - alert: ContainerMemoryHigh
        expr: |
          (container_memory_usage_bytes{name=~"rct-.*"}
          / container_spec_memory_limit_bytes{name=~"rct-.*"}) > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Контейнер {{ $labels.name }} > 90% memory"
          description: "{{ $labels.name }} использует более 90% mem_limit уже 5 минут. Возможна утечка или недооценённый budget."
```

Cadvisor exportит `container_memory_usage_bytes` + `container_spec_memory_limit_bytes`. 
`for: 5m` фильтрует GC-пики (G1GC может на секунды подскочить к лимиту).

## Validation после deploy

1. `docker stats --no-stream | grep rct-` — `MEM %` < 70% для каждого
   контейнера в idle.
2. `docker compose ps` — все `healthy`, нет `OOMKilled` restart'ов.
   `docker inspect rct-<name> | grep OOMKilled` должен быть `false`.
3. Prometheus query `max(container_memory_usage_bytes{name=~"rct-.*"}
   / container_spec_memory_limit_bytes{name=~"rct-.*"})` — ниже 0.8 в
   нормальном режиме.
4. Для Java-сервисов: Grafana dashboard `JVM` — `Heap Used` не
   упирается в `Heap Max` (GC handles).

## Если alert сработал

1. Найти какой контейнер: `ContainerMemoryHigh{name="rct-X"}`.
2. Если Java:
   - `docker exec rct-X jcmd 1 GC.heap_info` — Eden/Old/Metaspace
     usage.
   - Если Old вырос + не снижается → утечка. `jcmd 1 VM.native_memory
     summary` (если включён NMT).
   - Temporary: `docker compose restart rct-X` → ждём повторного
     alert (обычно утечка даёт alert снова за 2-6 часов).
3. Если Mongo/Redis/Postgres:
   - Лимит мал? Проверить prod traffic vs baseline.
   - Увеличить `mem_limit` + соответствующий internal buffer
     (`shared_buffers`, `maxmemory`, `wiredTigerCacheSizeGB`) в
     следующем релизе. Обновить таблицу выше.

## Связанные доки

- [prod-deploy-checklist.md](prod-deploy-checklist.md) — step 2.2
  healthcheck после применения лимитов.
- [observability.md](observability.md) — как устроены метрики
  cadvisor/prometheus.

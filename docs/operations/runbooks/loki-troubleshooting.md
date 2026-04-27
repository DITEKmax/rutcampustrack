# Loki troubleshooting runbook

Известные ошибки в логах Loki, как их диагностировать и когда эскалировать.

## Тип 1 — `pusher failed: InstancesCount <= 0`

**Симптом** (Loki container logs):
```
caller=rate_limited_logger.go:27 msg="pusher failed to consume trace data"
err="DoBatch: InstancesCount <= 0"
```

**Что значит:** distributor (в monolithic mode он же ingester) пытается
batch-push в ring, но видит **0 active instances**. Корневая причина —
**startup race**: gRPC listener ingester'а ещё не поднялся, ring видит
self-instance, но connect к `127.0.0.1:9096` fails → instance помечается
unhealthy → batch отвергается.

**Когда срабатывает:** в первые ~15 секунд после `docker compose
restart loki` или cold deploy. После того как Loki ACTIVE и promtail
respect'ит `/ready` — push'и идут нормально.

**Текущий smell:** 1-2 ошибки/час → нормально (rolling deploy,
restart).

**Когда эскалировать:**
- > 10 ошибок/час непрерывно → ingester не достигает ACTIVE state.
  Проверить: `docker exec rct-loki wget -qO- http://localhost:3100/ready`
  → должно быть `ready` после 15с с момента старта.
- > 100 ошибок/час → ring не работает совсем, логи **теряются**.

### Диагностика

```bash
# Частота ошибок за 24h
docker logs rct-loki --since 24h 2>&1 | grep -c "InstancesCount"

# В каком time-window они кучкуются (startup vs steady-state)
docker logs rct-loki --since 24h --timestamps 2>&1 \
  | grep "InstancesCount" | head -20

# Если кучкуются вокруг restart timestamps — startup race (известно, ok).
# Если разбросаны равномерно — что-то более серьёзное (gRPC issue?).
```

### Mitigation в M16 G4

В `infra/loki/loki.yml`:
```yaml
ingester:
  lifecycler:
    min_ready_duration: 15s
```

В `docker-compose.prod.yml`:
```yaml
loki:
  healthcheck:
    test: ["CMD-SHELL", "wget -qO- http://localhost:3100/ready || exit 1"]
promtail:
  depends_on:
    loki:
      condition: service_healthy
```

Promtail теперь стартует только после того как Loki `/ready` вернул
200 — `InstancesCount` должны почти исчезнуть.

### Если не помогло

1. Проверь что Loki **действительно single-instance** (мы не запустили
   случайно второй pod через scale).
2. Проверь что `kvstore.store: inmemory` — для multi-instance нужен
   `consul`/`memberlist`, и тогда race обостряется.
3. Если grpc_listen_port 9096 в config — проверь что не conflict'ит
   с другим listener'ом (`netstat -tlnp | grep 9096` внутри контейнера).
4. Возможно gRPC keepalive timeout — добавить
   `grpc_server_keepalive_time: 30s`.

---

## Тип 2 — `org_id=fake closing iterator: context canceled`

**Симптом:**
```
caller=errors.go:26 message="closing iterator" error="context canceled"
```
Иногда с `org_id=fake` в labels.

**Что значит:** **не ошибка**. Это нормальное поведение когда:
1. Grafana dashboard открылся → отправил query → пользователь закрыл
   tab / refresh'нул → context canceled на server-side.
2. Multi-tenancy не настроен (`auth_enabled: false`), Loki использует
   default tenant `fake`. Это **by design** для single-org setup.

**Когда эскалировать:** если параллельно с этим Grafana показывает
"failed to load logs" — возможно query timeout слишком короткий.

### Диагностика

```bash
# Частота context cancel'ов
docker logs rct-loki --since 24h 2>&1 | grep -c "context canceled"

# Если > 1000/час — Grafana dashboard'ы переполнены query'ями (refresh
# 5s + 10 widgets = 12000/час нормально). Если > 10000 — auto-refresh
# слишком частый, понизить в Grafana.
```

### Mitigation

Не нужен. Это log noise, не indicator проблемы.

---

## Тип 3 — `entry too far behind`

**Симптом:**
```
err="entry too far behind, oldest acceptable timestamp is X, but got Y"
```

**Что значит:** клиент (promtail) пытается push log с timestamp'ом
старше `reject_old_samples_max_age` (default 168h = 7d).

**Когда срабатывает:** при первом старте promtail после downtime > 7d
— positions file имеет старые offsets, promtail tail'ит логи
которым уже 7+ дней. Loki их отказывается принять.

### Mitigation

```bash
# На VPS — резетнуть positions promtail'а
docker exec rct-promtail rm /tmp/positions.yaml
docker restart rct-promtail
```

Это **дропнет** старые логи (которым 7+ дней) — это OK, ретеншн всё
равно 14d.

---

## Тип 4 — `out of memory` / OOM exit 137

**Симптом:** Loki контейнер неожиданно рестартует, `docker inspect
rct-loki | jq '.[0].State.OOMKilled'` → `true`.

**Когда:** burst log-pushy (massive deploy event), `mem_limit: 192m`
не хватает.

### Mitigation

1. Краткосрочно: bump `mem_limit` до 256m.
2. Долгосрочно: настроить `query.max_concurrent` пониже + `query.max-bytes-read` cap.

---

## Связанные документы

- `docs/operations/monitoring/observability.md` — общая Loki/Tempo/Grafana
  архитектура.
- `docs/operations/runbooks/loki-major-upgrade.md` — как делать major version
  bump (3.x → 4.x).
- `infra/loki/loki.yml` — текущая конфигурация.
- `infra/promtail/promtail.yml` — promtail scrape config.

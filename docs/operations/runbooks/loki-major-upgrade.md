# Runbook: Loki major upgrade

Процедура для **major**-версии Loki (2.x → 3.x, 3.x → 4.x). Minor/patch
bump — автоматически через Renovate (QD6).

Введён в M06 Группа 4 (NEW-151).

## Когда применяется

- Renovate создал PR с `grafana/loki: 3.x → 4.x` (или выше).
- `schema_config.schemas` в `infra/loki/loki.yml` не сконфигурирован
  для новой major-версии.
- Loki CHANGELOG упоминает breaking changes (storage, schema, API).

**НЕ применяется** для:
- patch-bump (`3.2.1 → 3.2.2`) — Renovate auto-merge.
- minor-bump (`3.2.x → 3.3.x`) — Renovate создаёт PR, ручной merge
  без этого runbook'а (backward-compatible schema).

## Prerequisites

- Access к VPS (`/opt/rutcampustrack`).
- Maintenance window ~30-60 минут (Loki ingester недоступен при
  schema-migration).
- Backup `/var/lib/docker/volumes/rutcampustrack_loki-data/_data/`.

## Шаги

### 1. Прочитать CHANGELOG (5 мин)

- https://github.com/grafana/loki/releases
- Фокус: `Breaking changes`, `Storage format`, `Configuration changes`.
- Если новая major требует schema `tsdb_shipper_2` или `v14` — это
  expand-contract migration (см. ниже).

### 2. Backup (5 мин)

На VPS:

```bash
cd /opt/rutcampustrack
docker compose -f docker-compose.prod.yml stop loki
docker run --rm \
  -v rutcampustrack_loki-data:/loki \
  -v $(pwd)/backups:/backup \
  alpine tar czf /backup/loki-data-$(date +%Y%m%d-%H%M%S).tar.gz /loki
ls -lh backups/ | tail -3
```

Backup хранится минимум 7 дней.

### 3. Schema expand (opt-in, 10 мин)

Если CHANGELOG требует **new schema version** — добавить **новую
запись** в `infra/loki/loki.yml` с `from:` = today (UTC), БЕЗ удаления
старой. Loki читает старые chunks по старой schema, пишет новые по
новой.

```yaml
schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h
    # M06 LOKI-UPGRADE: новая schema с today (expand-phase).
    - from: 2026-05-01
      store: tsdb
      object_store: filesystem
      schema: v14        # ← из CHANGELOG новой major
      index:
        prefix: index_
        period: 24h
```

Коммит: `chore(loki): schema expand v13 → v14 (manual upgrade runbook)`.
Merge → `deploy.yml` apply без рестарта Loki (config reload).

### 4. Deploy + verify (5 мин)

```bash
cd /opt/rutcampustrack
git pull --ff-only
docker compose -f docker-compose.prod.yml pull loki
docker compose -f docker-compose.prod.yml up -d loki
docker compose -f docker-compose.prod.yml logs loki --tail=100
```

Ожидаемые логи:
- `starting querier` / `starting ingester` / `starting distributor`.
- БЕЗ `schema migration failed` / `unknown schema`.

Verify через Grafana Explore:
- Запрос `{container="rct-auth-service"} |= "" | limit 10` → возвращает
  recent logs.
- Query за вчерашний день (`now-24h`) → возвращает old chunks (читается
  по старой schema).

### 5. Rollback если failure

```bash
docker compose -f docker-compose.prod.yml stop loki
docker tag grafana/loki:<old-version> grafana/loki:current
# Или: git revert <loki upgrade commit> && git push
docker run --rm \
  -v rutcampustrack_loki-data:/loki \
  -v $(pwd)/backups:/backup \
  alpine tar xzf /backup/loki-data-<timestamp>.tar.gz -C /
docker compose -f docker-compose.prod.yml up -d loki
```

### 6. Contract-phase (opt-in, ~30 дней)

Старая schema (`v13`) остаётся активной для запросов за период
`< new schema from`. Через 14 дней (retention period, OWNER-ANSWERS
P2-9/4) все chunks по старой schema expire'аются.

**Не удалять** старую schema entry до того как:
- Loki metrics `loki_chunks_stored_total{schema="v13"}` == 0.
- Retention period × 2 прошёл.

Когда old schema пустая → PR `chore(loki): contract v13 schema (all
chunks expired)`. Это даёт dead code cleanup, не функциональное
изменение.

## Troubleshooting

### «schema migration failed»

Старая schema entry недостаточна для старых chunks. Проверить `from:`
— должен быть раньше самого старого chunk'а.

### Grafana Explore возвращает пустые результаты

- Проверить `Promtail → Loki connection`: `docker compose logs promtail`.
- `loki_ingester_chunks_stored_total` растёт — ingest работает.
- Возможная причина: Grafana datasource cache. Restart `grafana` container.

### OOM Loki при upgrade

Major upgrade может потребовать re-index. Увеличить `deploy.resources.
limits.memory` на время миграции, вернуть после.

## История upgrades

| Дата | От | До | Schema | Заметки |
|------|-----|-----|--------|---------|
| 2026-04-21 | — | 3.2.1 | v13 | M06 initial pin (не major) |

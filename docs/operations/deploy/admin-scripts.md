# Admin Scripts (NEW-33, M09 G8)

> Шаблоны разовых административных задач, которые НЕ входят в штатный
> flow приложения. Запускать через `docker exec` на prod-VPS под root,
> с обязательным backup'ом до запуска (см. section 1.5 prod-deploy-checklist).

**Принципы:**
- Все скрипты — **идемпотентные** и **dry-run-friendly** (сначала
  `LOG`-only run, убедились в ожидаемом scope → apply).
- Пишем в `/tmp/admin-*.log` на VPS с timestamp.
- Каждый запуск фиксируется в `/opt/rutcampustrack/admin-log.txt`:
  строка `<YYYY-MM-DD HH:MM> <operator> <script> <scope>`.

---

## 1. Cleanup orphan attendance docs (mongosh)

**Контекст:** если `lesson.deleted` cascade потерян (Rabbit outage
в момент publish), attendance docs остаются без parent Lesson. Можно
увидеть через report API (дубли по lesson_id).

**Dry-run (list only):**

```bash
docker exec -i rct-mongo-attendance mongosh \
  -u "$MONGO_USER" -p "$MONGO_PASSWORD" --authenticationDatabase admin \
  attendance_db <<'EOF'
// Собираем все lesson_id из attendance_records; gRPC-call из mongosh невозможен,
// поэтому прогоняем через PG dump: полный список existing lesson_id.
// Pre-requisites: /tmp/lesson_ids.txt с существующими lesson_id по одному в строке.
const fs = require('fs');
const existingIds = new Set(
  fs.readFileSync('/tmp/lesson_ids.txt', 'utf8')
    .split('\n').filter(Boolean).map(Number)
);

const orphans = [];
db.attendance_records.find({}, {lesson_id: 1}).forEach(doc => {
  if (!existingIds.has(doc.lesson_id)) orphans.push(doc._id);
});

print(`Found ${orphans.length} orphan docs`);
print(JSON.stringify(orphans.slice(0, 20)));  // первые 20 для review
EOF
```

**Apply (после review):**

```bash
docker exec -i rct-mongo-attendance mongosh \
  -u "$MONGO_USER" -p "$MONGO_PASSWORD" --authenticationDatabase admin \
  attendance_db <<'EOF'
const fs = require('fs');
const existingIds = new Set(
  fs.readFileSync('/tmp/lesson_ids.txt', 'utf8')
    .split('\n').filter(Boolean).map(Number)
);
const result = db.attendance_records.deleteMany({
  lesson_id: {$nin: Array.from(existingIds)}
});
print(`Deleted ${result.deletedCount} orphan docs`);
EOF
```

**Prep lesson_ids.txt:**

```bash
docker exec rct-postgres-schedule psql -U rct_user -d schedule_db \
  -t -A -c "SELECT id FROM lessons;" > /tmp/lesson_ids.txt
```

**Validation:** after cleanup — `docker exec rct-mongo-attendance
mongosh ... --eval "db.attendance_records.countDocuments()"` не
должен упасть за счёт active student attendance'а.

**История как это всплывало:** M09 G1 (04 P0-6) — удалили startup
`cleanupOrphans` из `AttendanceIndexInitializer`. Если в будущем
dual-write outage случится, используем этот скрипт вручную.

---

## 2. Backfill cancelled_by / cancelled_at на legacy lessons

**Контекст:** V13 миграция добавила поля как nullable, legacy cancelled
строки не знают кто и когда отменил. Если нужно аналитика — backfill
из event-schemas outbox за последние N дней (где сохранены cancelled_by).

**Каталог source данных:**
- `schedule_outbox` Postgres — события типа `lesson.cancelled` до 7 дней
  (retention).
- Loki — production logs (retention 14d).

**Dry-run — mapping lesson_id → (cancelled_by, cancelled_at):**

```bash
docker exec rct-postgres-schedule psql -U rct_user -d schedule_db \
  -c "SELECT payload::jsonb->'payload'->>'lesson_id' as lesson_id,
             payload::jsonb->'payload'->>'cancelled_by' as by,
             payload::jsonb->'payload'->>'cancelled_at' as at,
             created_at
      FROM schedule_outbox
      WHERE event_type = 'lesson.cancelled'
        AND status = 'sent'
        AND created_at > now() - interval '7 days'
      ORDER BY created_at DESC
      LIMIT 20;"
```

**Apply:** INSERT через tmp table + UPDATE. Делать только когда legacy
set небольшой (<1000) — большой объём → отдельная миграция Flyway.

```bash
docker exec -i rct-postgres-schedule psql -U rct_user -d schedule_db <<'EOF'
BEGIN;
WITH evt AS (
  SELECT (payload::jsonb->'payload'->>'lesson_id')::bigint AS lesson_id,
         (payload::jsonb->'payload'->>'cancelled_by')::bigint AS by,
         (payload::jsonb->'payload'->>'cancelled_at')::timestamptz AS at
  FROM schedule_outbox
  WHERE event_type = 'lesson.cancelled'
    AND status = 'sent'
    AND created_at > now() - interval '30 days'
)
UPDATE lessons l
SET cancelled_by = evt.by,
    cancelled_at = evt.at
FROM evt
WHERE l.id = evt.lesson_id
  AND l.cancelled_by IS NULL
  AND l.status = 'cancelled';
-- Проверить rows affected, если нормально — COMMIT; иначе ROLLBACK
COMMIT;
EOF
```

---

## 3. Purge stuck outbox rows

**Контекст:** если publisher-тик упал (SchedulerLock expired без
advance pointer), PENDING-rows могут висеть часами. `outbox.lag`
gauge растёт. Транспортные сбои RabbitMQ не переводят rows в `failed`;
они остаются `pending` и ретраятся следующим tick'ом.

**Dry-run:**

```bash
for svc in academic schedule; do
  echo "=== $svc ==="
  docker exec rct-postgres-${svc} psql -U rct_user -d ${svc}_db \
    -c "SELECT id, event_type, created_at, status, retry_count
        FROM ${svc}_outbox
        WHERE status = 'pending'
          AND created_at < now() - interval '1 hour'
        ORDER BY created_at
        LIMIT 20;"
done
```

**Apply (legacy failed rows только после ручной проверки):**

```bash
docker exec -i rct-postgres-schedule psql -U rct_user -d schedule_db <<'EOF'
UPDATE schedule_outbox
SET retry_count = 0, last_error = NULL, status = 'pending'
WHERE status = 'failed'
  AND created_at > now() - interval '1 day';
EOF
# Restart publisher — он подхватит pending rows следующим tick'ом:
docker compose restart schedule-service
```

**Validation:** Grafana dashboard → `outbox_lag_seconds` возвращается
к 0-30s baseline в течение 5 минут.

---

## 4. Invalidate all refresh tokens (force re-login)

**Контекст:** при подозрении на компрометацию JWT signing key, или
после ротации JWT keys — выкинуть всех пользователей из сессий.

```bash
docker exec rct-redis redis-cli -a "$REDIS_PASSWORD" --scan --pattern 'refresh:*' | \
  xargs -r docker exec -i rct-redis redis-cli -a "$REDIS_PASSWORD" DEL
```

Access tokens останутся валидными до TTL (15 мин) — это acceptable;
если compromise критичный, дополнительно bump `jwt_key_id` в auth-service
config.

**Validation:** `redis-cli -a $REDIS_PASSWORD KEYS 'refresh:*'` → empty.

---

## 5. Recompute attendance stats cache (Redis)

**Контекст:** academic/attendance держат Caffeine-cache + Redis-cache
RBAC. Если видим stale данные (юзер был перемещён, но ещё видит old
group) — можно force-flush.

```bash
# Academic Redis-cache RBAC
docker exec rct-redis redis-cli -a "$REDIS_PASSWORD" --scan --pattern 'rbac:*' | \
  xargs -r docker exec -i rct-redis redis-cli -a "$REDIS_PASSWORD" DEL

# Academic Caffeine-cache через actuator
docker exec rct-api-gateway wget -qO- \
  --header "X-Internal-Token: $(get_internal_token)" \
  http://academic-service:9091/actuator/caches \
  | jq -r '.cacheManagers.caffeineCacheManager.caches | keys[]' \
  | while read cache; do
      docker exec rct-academic-service wget -qO- --method=DELETE \
        http://localhost:9091/actuator/caches/$cache
    done
```

**Validation:** staros видит корректную group membership в PWA после
next refresh (30с cache).

---

## 6. Rotate JWT signing key (emergency)

**Контекст:** compromise private key. Процедура **ДОРОГАЯ** — все
активные сессии (access + refresh) инвалидируются, пользователи должны
перелогиниться.

См. полную процедуру в `docs/operations/runbooks/secret-rotation.md` раздел
«INTERNAL_ISSUER_SECRET» как аналог. Для JWT signing key:

1. Остановить auth-service: `docker compose stop auth-service`.
2. Удалить существующий `jwt-keys` volume:
   `docker volume rm rutcampustrack_jwt-keys`.
3. Старт заново — `auth-service` сгенерирует свежую пару RSA при первом
   запуске: `docker compose up -d auth-service`.
4. Дождаться healthy → `docker logs rct-auth-service --tail 50 | grep
   "public.key generated"`.
5. Рестартовать всё остальное (они perlisten'ят `/auth/public-key` и
   переподтянут public half):
   `docker compose restart api-gateway notification-web attendance-service
   academic-service schedule-service`.
6. Session invalidation — см. script 4 выше (удаление refresh-токенов).

---

## Log template

`/opt/rutcampustrack/admin-log.txt`:

```
2026-04-24 14:30 maksd script-1-orphan-cleanup 47-docs-dropped
2026-04-24 16:45 maksd script-3-stuck-outbox schedule:12-rows-reset
```

Хранится локально (не в VCS) — аудиторам передаётся в incident-reports
по запросу.

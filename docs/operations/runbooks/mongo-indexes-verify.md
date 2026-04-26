# Mongo Indexes Verify Runbook

**Когда использовать:** после deploy notification-web, миграции Mongo,
или при подозрении на деградацию `/api/notifications` (list, unread-count).

**Цель:** убедиться что `notification_db.notification_history` имеет
все 3 кастомных индекса + TTL 30 дней (M10 G3 + M13 G6 audit).

## Автоматическая startup-проверка (M13 G6)

С M13 `NotificationHistoryMongoConfig.verifyIndexes()` в
`@EventListener(ApplicationReadyEvent)` бросает `IllegalStateException`
и контейнер падает если:

- любой из 3 индексов `idx_user_sent_desc`, `idx_user_read`,
  `ttl_sent_at` не создан;
- `ttl_sent_at.expireAfterSeconds` не совпадает с
  `notification.history.ttl-days * 86400` (по-умолчанию 30 дней =
  `2_592_000`).

Логи startup:

```
notification_history indexes ensured: idx_user_sent_desc, idx_user_read, ttl_sent_at (TTL 30 days)
notification_history indexes verified: [_id_, idx_user_sent_desc, idx_user_read, ttl_sent_at] (TTL 2592000s)
```

Если видишь `ERROR ... notification_history missing indexes: [...]` —
контейнер уже упал, см. раздел «Ручная проверка» ниже + «Fix».

## Ручная проверка на VPS

На VPS (`/opt/rutcampustrack`) exec'нуться в Mongo:

```bash
docker exec -it rct-mongo-notification mongosh \
    -u "$MONGO_NOTIFICATION_USER" \
    -p "$MONGO_NOTIFICATION_PASSWORD" \
    --authenticationDatabase notification_db \
    notification_db
```

Внутри shell:

```javascript
// Ожидание: 4 индекса total (_id_ + 3 custom).
db.notification_history.getIndexes()

// Ожидание: expireAfterSeconds = 2592000 (30 дней).
db.notification_history.getIndexes()
  .filter(i => i.name === 'ttl_sent_at')[0]
  .expireAfterSeconds
```

**Пример valid output:**

```json
[
  { "v" : 2, "key" : { "_id" : 1 }, "name" : "_id_" },
  { "v" : 2, "key" : { "user_id" : 1, "sent_at" : -1 }, "name" : "idx_user_sent_desc" },
  { "v" : 2, "key" : { "user_id" : 1, "read_at" : 1 }, "name" : "idx_user_read" },
  { "v" : 2, "key" : { "sent_at" : 1 }, "name" : "ttl_sent_at", "expireAfterSeconds" : 2592000 }
]
```

## Fix — индексы отсутствуют

1. **Через рестарт:** `docker compose restart notification-web`.
   `ApplicationReadyEvent` handler пересоздаст через `ensureIndex()`.
2. **Вручную** (если рестарт не помог — например, старое приложение
   всё ещё подключено, держит namespace без индексов):
   ```javascript
   db.notification_history.createIndex(
     { user_id: 1, sent_at: -1 },
     { name: 'idx_user_sent_desc' }
   );
   db.notification_history.createIndex(
     { user_id: 1, read_at: 1 },
     { name: 'idx_user_read' }
   );
   db.notification_history.createIndex(
     { sent_at: 1 },
     { name: 'ttl_sent_at', expireAfterSeconds: 2592000 }
   );
   ```
3. **Проверить повторно:** `db.notification_history.getIndexes()` показывает
   4 элемента.

## Fix — TTL expireAfterSeconds не совпадает

**Причина:** `notification.history.ttl-days` был изменён, но индекс
уже создан с прежним TTL. MongoDB не позволяет `ensureIndex` изменить
существующий индекс — надо или drop+recreate, или `collMod`.

**Safer вариант — `collMod`** (нет downtime, нет потери данных):

```javascript
db.runCommand({
  collMod: 'notification_history',
  index: {
    name: 'ttl_sent_at',
    expireAfterSeconds: 2592000   // подставить новый TTL в секундах
  }
});
```

**Если `collMod` не доступен** (старая версия Mongo < 5.0):

```javascript
db.notification_history.dropIndex('ttl_sent_at');
db.notification_history.createIndex(
  { sent_at: 1 },
  { name: 'ttl_sent_at', expireAfterSeconds: 2592000 }
);
```

После — `docker compose restart notification-web` чтобы startup
verification снова зелёный.

## Fix — коллекция отсутствует

Если `show collections` не показывает `notification_history`:

1. Проверить что notification-web startup log содержит
   `notification_history collection created` (M10 G9 hot-patch pattern).
2. Если нет — есть проблема с connect'ом Mongo → auth (wrong user /
   password / authSource). См. `docs/operations/runbooks/dev-setup.md` раздел
   Mongo.

## Performance check (опционально)

Убедиться что индексы **используются** reader-queries:

```javascript
// Должен быть IXSCAN на idx_user_sent_desc, не COLLSCAN.
db.notification_history.find({ user_id: 1 })
  .sort({ sent_at: -1 })
  .limit(20)
  .explain('executionStats').queryPlanner.winningPlan

// Должен быть IXSCAN на idx_user_read.
db.notification_history.countDocuments({ user_id: 1, read_at: null })
```

Если `COLLSCAN` — значит index не используется. См. выше «Fix —
индексы отсутствуют».

## Связанные документы

- `services/notification-service/notification-app/src/main/java/.../NotificationHistoryMongoConfig.java` — implementation
- `services/notification-service/notification-app/src/test/java/.../NotificationMongoIndexesIT.java` — IT
- `docs/milestones/M10-notification-history/NOTES.md` раздел «S4» — root cause M10 hot-patch
- `docs/milestones/M13-pre-deploy-hardening/NOTES.md` раздел «Группа 6» — fail-fast rationale

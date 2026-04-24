# M10 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу.

---

## Вопросы к owner'у до старта

1. **Module naming:** ~~текущее имя — `notification-web` (single-module)~~
   **РАЗРЕШЕНО (D1, 2026-04-24)** — модуль
   `services/notification-service/notification-api-contract/` уже существует
   (с `PushApi` из v6.0). Структура — вариант (b) по факту. M10 добавляет
   классы в существующий модуль.

2. **NotificationType enum scope:** покрываем ли все 14+ event types
   одним enum'ом? Или разделяем user-facing vs system?
   **Default:** все user-facing (без system events типа `user.logged-out`
   — он не показывается в UI).

3. **History queue name:** `notification.history` или более явно
   `notification.history.persist`?
   **Default:** короткое `notification.history`.

4. **Caffeine vs Redis для unread-count:** OWNER-ANSWERS P2-6/4 явно
   говорит «Caffeine», P2-10/3 тоже. Notification-web — single-instance,
   так что Caffeine OK. Но если будем масштабировать — потребуется
   переход на Redis. Оставить комментарий «single-instance assumption»
   в `CaffeineConfig.java`.

5. **TTL 30d — глобально или configurable?** OWNER-ANSWERS говорит
   30d. Делаем константу или env var `NOTIFICATION_HISTORY_TTL_DAYS=30`?
   **Default:** env var с default 30 — для future tuning.

## Фактические surprises (обнаружены при старте G1)

### S1 — Module уже в варианте (b)
Разрешено в D1. См. выше.

### S2 — Mongo user уже имеет readWrite на notification_db
`infra/mongo/init-mongo.js` создаёт единый `MONGO_USER` с правами
`readWrite + dbAdmin` на `notification_db` И `attendance_db`. M05 якобы
«зарезервировал» отдельного `notification_user`, но по факту в коде
его нет — есть общий user.

Варианты:
- **(a) Отдельный `notification_user`** с правами только на `notification_db`.
  Plus: PoLP / изоляция blast-radius если один credential leak'нет.
  Minus: +2 env vars (`MONGO_NOTIFICATION_USER/PASSWORD`), breaking для
  существующих local dev `.env` файлов; два credentials для одной Mongo
  инстансы — overhead без реального benefit (single-tenant VPS).
- **(b) Keep shared `MONGO_USER`** — уже readWrite на notification_db.
  Plus: zero-churn, ноль breaking. Minus: credential одного сервиса
  даёт доступ к DB другого (notification-web → attendance_db и наоборот).

**Контекст:** PLAN.md:51 утверждает «Mongo user `notification_user`
создан в M05 как reserved, активируется здесь». В M05 нет такого кода.
**Default (предлагаю):** вариант (a) — выполняем PLAN как написано,
PoLP выигрывает. Env vars `MONGO_NOTIFICATION_USER/PASSWORD` с default
на `rct_notification_user`/`rct_dev_pass` для local dev, rotation в
.env.prod уже требуется M09 runbook'ом `docs/runbooks/secret-rotation.md`.

### S3 — notification-web.SPRING_DATA_MONGODB_URI указывает на attendance_db
`docker-compose.yml:161`:
```
SPRING_DATA_MONGODB_URI: mongodb://${MONGO_USER}:${MONGO_PASSWORD}@mongo-attendance:27017/attendance_db?authSource=admin
```
notification-web сейчас не пишет в Mongo (stateless forwarder), поэтому
URI просто legacy-placeholder. M10 переключает на `notification_db`:
- Name hostname `mongo-attendance` remains (контейнер физически тот же,
  хранит и attendance_db и notification_db как разные DB в одном Mongo —
  matches P2-9/6 «разные DB, один instance»).
- Authsource остаётся `admin` (root user там).
- DB в URI: `notification_db`.
- В compose `docker-compose.prod.yml` — то же изменение.

## Ожидаемые surprises

- **Fanout exchange уже имеет delivery bindings** — добавление
  history queue не должно ломать существующие consumers. Проверить
  через `rabbitmqctl list_bindings`.
- **Mongo TTL monitor cycle = 60s по default** — документы старше
  `expireAfterSeconds` удаляются не мгновенно, а при следующем
  TTL check. Это нормально для retention policy, но может сюрпризнуть
  в integration test (нужен sleep или `db.adminCommand({setParameter:1,
  ttlMonitorSleepSecs:1})` в test setup).
- **Denormalize payload — consistency risk.** `notification_history`
  хранит snapshot события на момент persist. Если исходный lesson/user
  потом обновится/удалится — history покажет старые данные. Это
  **желаемое поведение** (история = snapshot). Тест на immutability.
- **PWA TanStack Query v5 `useInfiniteQuery` API breaking vs v4**:
  проверить версию в `package.json`. Если v4 — migrations к v5 отложить.
- **Angular Signal + Observable interop** — если web-panel использует
  старый BehaviorSubject pattern в NotificationCenter, миграция на
  Signal потребует rxjs → toSignal conversion.

## Связь с другими milestones

### С M07 Frontend Hardening
- **M07 делает thin-client unified NotificationCenter** (QC1 из 3 STOMP
  клиентов в web-panel → 1 shared service).
- **M10 подменяет data source** — backend authoritative pagination
  вместо sessionStorage.
- **Конфликт merge:** если M10 merges раньше M07 — NotificationCenter
  будет 3 клиента со stateful backend; если M07 раньше — unified
  thin-client с sessionStorage, ждёт M10.
- **Рекомендация:** M07 → M10 (M07 делает unified компонент, M10
  меняет internals).

### С M08 Test Infrastructure
- **Testcontainers pattern** для NotificationHistoryConsumerIT —
  использовать `shared-test-containers` из M01; iteration 2 reuse
  сделает M08.
- **Frontend unit-тест** `NotificationCenter.test.tsx` — в M08 Группа
  6. M10 делает smoke, M08 — полное покрытие hooks.
- **Contract-тест на новые events** — M08 Группа 9 покрывает через
  параметризованный `EventContractIT`, читающий `event-schemas/*.json`.

### С M11 OpenAPI Polish
- 4 новых endpoint'а в `NotificationApi.java` должны иметь
  `@Operation`/`@ApiResponse`/`@Schema` с первого коммита.
- `@Schema(description, example)` для `NotificationHistoryDto` полей —
  сразу (не откладывать в M11).

## Deferred в v0.1

- **Full-text search** по notification history — не в scope v0.0.0.
- **Group by type filter** в UI — nice-to-have, v0.1.
- **Email digest** (weekly summary) — v0.1.
- **Retention configurable per user** — v0.1 (admin может включить
  «keep all»).

## Baseline metrics (после деплоя)

- Mongo `notification_history` size growth rate: [TBD per week]
- Caffeine cache hit rate на unread-count: [TBD] (target ≥80%)
- REST p95 latency `GET /api/notifications`: [TBD] (target <100ms для
  page size=20)
- Unread-count p95: [TBD] (target <20ms с Caffeine hit)

---

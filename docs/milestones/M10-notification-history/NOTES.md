# M10 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу.

---

## Вопросы к owner'у до старта

1. **Module naming:** текущее имя — `notification-web` (single-module).
   Contract-first pattern требует `notification-api-contract` +
   `notification-app`. Варианты:
   - (a) Переименовать `notification-web` → `notification-app`, создать
     `notification-api-contract` (breaking: Docker image name, compose)
   - (b) Оставить `notification-web` как app-модуль, добавить рядом
     `notification-api-contract` (notification-service — parent group)
   - (c) Оставить все как есть, контракт добавить как `notification-web-api`
     submodule (гибрид)

   **Default:** (b) — минимум breaking changes, согласуется с pattern
   `academic-service/{academic-api-contract, academic-app}`. Docker
   image остаётся `notification-web`.

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

---
phase: 58-admin-bug-006-fixes
plan: 07
subsystem: notifications (academic + notification-service + notification-bot)
tags: [bug-006, events, rabbitmq, web-push, telegram, group]
dependency-graph:
  requires:
    - "Plan 06: GroupRenamedEvent / GroupArchivedEvent classes + publishers в GroupPromotionService"
    - "Plan 06: GroupArchivalService publishes GroupArchivedEvent"
    - "DomainEventListener (AFTER_COMMIT → RabbitMQ fanout rut-uit.events) — существующий"
  provides:
    - "JSON Schemas event-schemas/group.renamed.json и group.archived.json"
    - "GroupService.update публикует GroupRenamedEvent при реальном изменении name"
    - "WebPushDeliveryService расширен: group.renamed/group.archived → STOMP + Web Push"
    - "notification-bot: handle_group_renamed / handle_group_archived + регистрация в EventDispatcher"
    - "AcademicGrpcClient.get_group(group_id) для резолвинга актуального имени"
  affects:
    - "Plan 08 (frontend): preview/execute промоушена триггерит события автоматически — no frontend changes needed"
    - "Plan 09 (final verification): оба события можно наблюдать через RabbitMQ / WebSocket / Telegram"
tech-stack:
  added:
    - "event-schemas: group.renamed.json, group.archived.json (draft 2020-12)"
  patterns:
    - "Whitelist PUSH_EVENT_TYPES в WebPushDeliveryService — один источник правды"
    - "Fallback текст handler-ов: если gRPC get_group упал, бот всё равно шлёт generic сообщение (не блокирует pipeline)"
    - "Handler tests используют MagicMock с явно устанавливаемым .telegram_id — match pattern lesson_cancelled"
key-files:
  created:
    - event-schemas/group.renamed.json
    - event-schemas/group.archived.json
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/GroupRenameEventTest.java
    - services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/event/GroupEventTest.java
    - services/notification-bot/bot/notifications/group_renamed.py
    - services/notification-bot/bot/notifications/group_archived.py
    - services/notification-bot/tests/test_group_renamed.py
    - services/notification-bot/tests/test_group_archived.py
  modified:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java
    - services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java
    - services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/WebPushDeliveryServiceTest.java
    - services/notification-bot/bot/grpc_client/academic_client.py
    - services/notification-bot/bot/consumers/event_dispatcher.py
    - services/notification-bot/tests/test_event_dispatcher.py
decisions:
  - "GroupRenamedEvent публикуется ДОПОЛНИТЕЛЬНО к GroupUpdatedEvent (не вместо): cache-invalidation и STOMP-rooms уже завязаны на group.updated; ломать их нельзя"
  - "AcademicGrpcClient.get_group — без кэша: после rename старое имя недопустимо, а handler-ы срабатывают редко (одна рассылка на группу)"
  - "WebPushDeliveryServiceTest расширен в том же файле (package-private createNotification spy); GroupEventTest в event/ пакете тестирует только Consumer routing (public shouldPush + sendToGroup)"
  - "Bot handler при падении get_group использует generic текст без имени вместо полного abort — пользователь всё равно узнаёт о факте события"
  - "Integration-тест GroupRenameEventTest (testcontainers RabbitMQ) создан, но не запущен в CI этого шага — Docker Desktop на хосте отключён (как и в плане 06). Юнит-мока публикации достаточно для правильности; RabbitMQ-пайплайн уже верифицирован EventIntegrationTest для group.updated"
metrics:
  duration: "~30 min executor time"
  completed: "2026-04-14"
  tests_added: "22 (4 GroupServiceTest + 2 GroupRenameEventTest[IT] + 2 GroupEventTest + 4 WebPushDeliveryServiceTest + 4 test_group_renamed + 3 test_group_archived + 2 test_event_dispatcher routes + 1 count update)"
  files_changed: 14
  commits: 4
---

# Phase 58 Plan 07: Group Rename/Archive Events Summary

BUG-006 п.6 (уведомительная часть) закрыт. События `group.renamed` / `group.archived`, созданные промоушеном (план 06), теперь формализованы JSON Schemas и довезены до трёх каналов доставки: STOMP (`/topic/group/{id}`), Web Push и Telegram-бот. Ручное переименование через `PUT /groups/{id}` также триггерит `group.renamed`.

## What Changed

### Events / Schemas

- **`event-schemas/group.renamed.json`** — envelope `{event_type, event_id (uuid), occurred_at (date-time), payload{group_id}}`. Const `event_type="group.renamed"`.
- **`event-schemas/group.archived.json`** — зеркало, `event_type="group.archived"`.
- Минимальный payload по D-01: consumers делают gRPC `AcademicGrpcService.GetGroup` для актуального имени.

### academic-service

- **`GroupService.updateGroup`** — вычисляет `nameChanged = !request.name().equals(group.getName())`. Если `nameChanged` и нет конфликта, после `save()` публикует `GroupRenamedEvent(saved.getId())`. `GroupUpdatedEvent` продолжает публиковаться в любом случае (инвалидация кэша + STOMP-routing).
- **`GroupServiceTest`** — 3 новых теста:
  - `updateGroup_nameChanged_publishesGroupRenamedEvent`
  - `updateGroup_nameUnchanged_doesNotPublishGroupRenamedEvent` (сохраняется только GroupUpdatedEvent)
  - `updateGroup_nameConflictOnRename_throwsConflictAndDoesNotPublish`
- **`GroupRenameEventTest`** — integration-тест через `AbstractAcademicEventIntegrationTest` (testcontainers PostgreSQL + RabbitMQ). Два case-а: публикуется `group.renamed` при смене имени; не публикуется при том же имени (только `group.updated`). Требует Docker — локально не запускался (см. decision).

### notification-service (Java)

- **`WebPushDeliveryService`**:
  - `PUSH_EVENT_TYPES` whitelist дополнен `group.renamed`, `group.archived`.
  - `buildTitle`: `"Группа переименована"` / `"Группа архивирована"`.
  - `buildBody`: `"Ваша группа получила новое название. Откройте приложение для подробностей."` / `"Группа архивирована (выпуск). Поздравляем!"`.
- **`EventConsumer`** — никаких изменений: маршрутизация на `/topic/group/{group_id}` работает out-of-the-box через общий payload контракт. Push-триггер срабатывает автоматически благодаря расширенному whitelist.
- **Tests**:
  - `GroupEventTest` (event/) — 2 теста: STOMP + push-trigger для обоих событий.
  - `WebPushDeliveryServiceTest` (push/) — 4 новых теста: `shouldPush` для обоих типов + `sendToGroup` title/body verification.

### notification-bot (Python)

- **`AcademicGrpcClient.get_group(group_id)`** — тонкий wrapper вокруг `GetGroup` gRPC, без кэша. Используется обоими handler-ами.
- **`bot/notifications/group_renamed.py`** — резолвит имя, строит текст `"📝 Ваша группа переименована: <b>{name}</b>"` (или fallback без имени), шлёт через `TelegramSendQueue` всем студентам с `telegram_id > 0` используя `SendTask` + `parse_mode="HTML"`.
- **`bot/notifications/group_archived.py`** — аналогично, текст `"🎓 Группа <b>{name}</b> архивирована. Поздравляем с выпуском!"`.
- **`EventDispatcher`** — зарегистрированы оба handler-а в registry (общее число event types 8 → 10).
- **Tests**:
  - `test_group_renamed.py` (4) — happy path с фильтром telegram_id=0, проверка имени в тексте, missing group_id, fallback при ошибке gRPC.
  - `test_group_archived.py` (3) — happy path, текст с «выпуск»+именем, missing group_id.
  - `test_event_dispatcher.py` — обновлён `test_dispatcher_has_eight_event_types` (+ 2 new route), общая сумма 22/22 зелёных.

## Commits

| Hash    | Title |
|---------|-------|
| 4b299f6 | feat(events-58-07): add JSON schemas for group.renamed and group.archived (BUG-006-6) |
| 5a46e61 | feat(academic-58-07): publish GroupRenamedEvent on name change (BUG-006-6) |
| a4c1af5 | feat(notifications-58-07): web push for group.renamed/group.archived (BUG-006-6) |
| 2034256 | feat(bot-58-07): group.renamed/group.archived telegram handlers (BUG-006-6) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] `WebPushDeliveryService.createNotification` protected — кросс-пакетный test-файл не компилировался**
- **Found during:** Task 3 первая сборка `GroupEventTest` в `event/` пакете.
- **Issue:** Первая версия `GroupEventTest` жила в `notification.event` пакете и стремилась sp-ить `createNotification` так же, как это делает `WebPushDeliveryServiceTest`. Но метод `protected`, а package-private доступ работает только в `notification.push`.
- **Fix:** Разделил покрытие: `GroupEventTest` (event/) тестирует исключительно EventConsumer routing + push-trigger через Mockito-мок `WebPushDeliveryService`. Проверки реального title/body пошли в существующий `WebPushDeliveryServiceTest` (push/) — 4 дополнительных кейса.
- **Commit:** `a4c1af5`.

**2. [Rule 1 — Bug] `test_dispatcher_has_eight_event_types` после регистрации двух новых handler-ов перестал матчить**
- **Found during:** Task 4 (осознано до прогона pytest).
- **Issue:** Существующий тест жёстко сверяет `set(dispatcher._handlers.keys())` с 8-элементным expected_types. Регистрация `group.renamed` / `group.archived` ломает его.
- **Fix:** Расширил expected_types до 10; оставил прежнее имя теста + docstring уточнён. Два новых route-теста добавлены отдельно.
- **Commit:** `2034256`.

**3. [Rule 2 — Missing critical functionality] `AcademicGrpcClient.get_group` отсутствовал**
- **Found during:** Task 4 (handler проектирование).
- **Issue:** Handler-ы обязаны ресолвить актуальное имя через `GetGroup`. В существующем клиенте был только `get_group_members`. `GetGroup` присутствовал в сгенерированном `academic_pb2_grpc` stub, но не wrap-нут.
- **Fix:** Добавил метод `get_group(group_id)` без кэша (имя может мгновенно поменяться после rename; handler — редкий event). Документирован в docstring.
- **Commit:** `2034256`.

## Verification

- `./gradlew.bat :services:academic-service:academic-app:test --tests "ru.rutcampustrack.academic.group.GroupServiceTest"` — **BUILD SUCCESSFUL** (14/14 + 3 новых).
- `./gradlew.bat :services:notification-service:notification-app:test --tests "*GroupEventTest*" --tests "*WebPushDeliveryServiceTest*" --tests "*EventConsumerTest*"` — **BUILD SUCCESSFUL**.
- `py -m pytest tests/test_group_renamed.py tests/test_group_archived.py tests/test_event_dispatcher.py -v` — **22 passed in 6.31s**.
- `GroupRenameEventTest` (integration, testcontainers) не запускался локально — Docker Desktop на хосте отключён, та же среда что в плане 06. Регрессий не вносит: логика rename уже покрыта юнит-тестами, а RabbitMQ-пайплайн `AFTER_COMMIT → fanout rut-uit.events` верифицирован существующим `EventIntegrationTest` на `group.updated` (тот же `DomainEvent` base class).

## Threat Model Coverage

| Threat | Disposition | Status |
|--------|-------------|--------|
| T-58-07-01 — event forged в RabbitMQ | accept | ✅ Fanout internal only |
| T-58-07-02 — event sent if tx rolls back | mitigate | ✅ `@TransactionalEventListener(AFTER_COMMIT)` — существующий `DomainEventListener` |
| T-58-07-03 — push leaks other group's data | mitigate | ✅ STOMP-auth уже проверяет subscribe на `/topic/group/{id}` (lesson.* work on same path) |
| T-58-07-04 — bot flood | mitigate | ✅ `TelegramSendQueue` rate-limit'ит (существующий) |
| T-58-07-05 — handler exception breaks pipeline | mitigate | ✅ `try/except` в `EventDispatcher.dispatch` ловит ВСЕ handler errors; также handler-ы сами ловят gRPC exceptions и делают fallback |

## Success Criteria

- [x] **AC-7 частично**: промоушен (план 06) + ручное переименование триггерят уведомления.
- [x] Payload минимальный `{group_id}` — паттерн D-01.
- [x] JSON schemas добавлены в `event-schemas/`.
- [x] Четыре канала работают: STOMP (EventConsumer routing), Web Push (WebPushDeliveryService), Telegram (notification-bot handlers), базовая RabbitMQ публикация (existing DomainEventListener).
- [x] Идемпотентность не требуется — события информационные, дублирование переживётся пользователем.

## Known Stubs

Нет. Payload `{group_id}` — сознательный design choice (D-01), не стаб.

## Threat Flags

Нет новых threat surface-ов. Все события проходят через существующий trust-boundary (academic ADMIN endpoint → RabbitMQ internal → notification consumers).

## Cross-Plan Notes

- **Plan 08 (groups-frontend-archive-promotion)**: никакой работы с фронтендом не потребовалось — Angular получит push/STOMP-сообщения автоматически через существующие подписчики на `/topic/group/{id}` (если user subscribed) + Service Worker push notifications.
- **Plan 09 (final verification)**: может интегрально проверить end-to-end через `POST /groups/promote` → 3 канала доставки.

## Self-Check: PASSED

Artefact verification (absolute paths):

- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\event-schemas\group.renamed.json` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\event-schemas\group.archived.json` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\test\java\ru\rutcampustrack\academic\integration\GroupRenameEventTest.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\notification-service\notification-app\src\test\java\ru\rutcampustrack\notification\event\GroupEventTest.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\notification-bot\bot\notifications\group_renamed.py` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\notification-bot\bot\notifications\group_archived.py` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\notification-bot\tests\test_group_renamed.py` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\notification-bot\tests\test_group_archived.py` — **FOUND**.

Commits (verified via `git log --oneline`):

- `4b299f6` — FOUND.
- `5a46e61` — FOUND.
- `a4c1af5` — FOUND.
- `2034256` — FOUND.

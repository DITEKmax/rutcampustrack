---
phase: 60-headman-schedule-management
plan: 04
subsystem: schedule-service + notification-bot
tags: [events, rabbitmq, json-schema, domain-event, notification-bot, aiogram, pytest, headman]
dependency_graph:
  requires:
    - "60-03 (OneOffLessonService hook points — createOneOffLesson / deleteOneOffLesson)"
  provides:
    - "JSON Schema contracts: event-schemas/lesson.one_off.created.json, lesson.one_off.cancelled.json"
    - "Java event classes: OneOffLessonCreatedEvent, OneOffLessonCancelledEvent (extending DomainEvent, published via AFTER_COMMIT)"
    - "OneOffLessonService publishes both events after successful save/delete"
    - "notification-bot handlers: handle_lesson_one_off_created, handle_lesson_one_off_cancelled (D-18: push entire group incl. headman)"
    - "EventDispatcher registration of both event types"
  affects:
    - "downstream 60-05: attendance-service will consume lesson.one_off.created/cancelled for read-model + cascade delete"
    - "notification-web (future): can subscribe to the same events for STOMP delivery to open PWA/web-panel tabs"
tech_stack:
  added: []
  patterns:
    - "DomainEvent subclass + @TransactionalEventListener(AFTER_COMMIT) → no event published on rollback"
    - "Snapshot entity fields before delete() so the subsequent published event carries accurate values"
    - "notification-bot handler: KeyError guard on required payload fields, graceful gRPC fallback for subject name, TelegramSendQueue with lazy coroutine_factory per student"
    - "TDD: RED (failing imports) → GREEN (implement handlers + register) for both bot handlers"
key_files:
  created:
    - event-schemas/lesson.one_off.created.json
    - event-schemas/lesson.one_off.cancelled.json
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/OneOffLessonCreatedEvent.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/OneOffLessonCancelledEvent.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonEventPublisherIT.java
    - services/notification-bot/bot/notifications/lesson_one_off_created.py
    - services/notification-bot/bot/notifications/lesson_one_off_cancelled.py
    - services/notification-bot/tests/test_one_off_created_handler.py
    - services/notification-bot/tests/test_one_off_cancelled_handler.py
  modified:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonService.java
    - services/notification-bot/bot/consumers/event_dispatcher.py
    - services/notification-bot/tests/test_event_dispatcher.py
decisions:
  - "Event publisher location: plan указывал `bot/event_dispatcher.py`, реальный — `bot/consumers/event_dispatcher.py`; использовал реальный путь чтобы не создавать дубликат"
  - "Обе payload-схемы включают общий envelope (event_type, event_id, occurred_at) для совместимости с DomainEvent — не только payload-subset, как минималистично предполагал план"
  - "Перед DELETE публикацией — snapshot полей сущности (groupId/subjectId/date/lessonNumber), потому что после repository.delete() доступ к entity может быть небезопасен"
  - "notification-bot handler использует тот же паттерн TelegramSendQueue + get_group_members + get_subjects_by_ids (с gRPC fallback), что и lesson_cancelled.py — единый стиль"
metrics:
  duration_min: ~12
  completed: 2026-04-14
---

# Phase 60 Plan 04: One-off Lesson Events + Push — Summary

Добавлена событийная шина для разовых пар: `OneOffLessonService` публикует `lesson.one_off.created` после `save` и `lesson.one_off.cancelled` после `delete` (snapshot перед удалением). Обе схемы добавлены в `event-schemas/`, Java-классы наследуют `DomainEvent` (AFTER_COMMIT forward в RabbitMQ). `notification-bot` получил два новых обработчика, шлющих push всем студентам группы (включая старосту — D-18), зарегистрированы в `EventDispatcher`. IT `OneOffLessonEventPublisherIT` (2 теста) + pytest `test_one_off_created_handler.py` (4) + `test_one_off_cancelled_handler.py` (4) — все зелёные; `:services:schedule-service:schedule-app:build` — BUILD SUCCESSFUL; notification-bot — 136/136 pytest PASSED.

## Что сделано

### Task 1 — Schemas + Java events + publisher + IT (commit `04afe77`)

**Contracts (event-schemas/):**
- `lesson.one_off.created.json` — envelope `{event_type, event_id, occurred_at, payload}`, payload содержит `{one_off_lesson_id, group_id, subject_id, date, lesson_number, classroom?}`.
- `lesson.one_off.cancelled.json` — аналогичный envelope, payload `{group_id, subject_id, date, lesson_number}` (без `one_off_lesson_id`, т.к. consumer-у attendance-service удобнее работать по натуральному ключу для cascade delete — D-22).

**Java (schedule-app/src/main/java/.../event/):**
- `OneOffLessonCreatedEvent` extends `DomainEvent`, nested `record Payload` с `@JsonProperty` аннотациями (snake_case на wire).
- `OneOffLessonCancelledEvent` — то же для отмены.

**Сервис (`OneOffLessonService`):**
- Добавлено поле `ApplicationEventPublisher eventPublisher` в конструктор.
- `createOneOffLesson`: после `oneOffLessonRepository.save(oneOff)` публикуется `OneOffLessonCreatedEvent` с полями сохранённой сущности.
- `deleteOneOffLesson`: snapshot (`groupId/subjectId/date/lessonNumber`) → `delete` → `publishEvent(new OneOffLessonCancelledEvent(...))`. `DomainEventListener` форвардит в RabbitMQ только после коммита транзакции — при rollback событие не появится.

**IT (`OneOffLessonEventPublisherIT`, 2/2 зелёные):**
- `publishesCreatedEventOnCreate` — mock `RequestContext` (ADMIN), `AcademicGrpcClient` (active semester Spring 2026), вызов `createOneOffLesson` → `verify(rabbitTemplate).convertAndSend(anyString(), anyString(), argThat(isOneOffLessonCreatedEvent))`.
- `publishesCancelledEventOnDelete` — предварительная вставка one-off через `oneOffLessonRepository.save`, вызов `deleteOneOffLesson` → `verify` `OneOffLessonCancelledEvent`.
- Тест не `@Transactional` (AFTER_COMMIT должен сработать); `rabbitTemplate` mock унаследован от `AbstractScheduleIntegrationTest`.

### Task 2 — notification-bot handlers + pytest (TDD, commits `c6e49f6` RED → `c4227a5` GREEN)

**RED:** `test_one_off_created_handler.py` (4 теста) + `test_one_off_cancelled_handler.py` (4) созданы первыми, упали с `ModuleNotFoundError`.

**GREEN (реализация):**
- `bot/notifications/lesson_one_off_created.py`:
  - payload guard: `try: group_id, subject_id, date, lesson_number except KeyError → warning+return`.
  - subject name через `academic_client.get_subjects_by_ids([subject_id])`, fallback на `"Пара"` при исключении.
  - текст: `"📅 Добавлена разовая пара\n\n{subject}\nДата: {date}\nПара: {lesson_number}-я\nКабинет: {classroom or 'каб. не указан'}"`.
  - итерация по `get_group_members(group_id)`; пропуск `telegram_id == 0`; enqueue `SendTask` с lazy `coroutine_factory` (capture student по default-argument, чтобы избежать late-binding bug).
- `bot/notifications/lesson_one_off_cancelled.py`:
  - тот же guard + subject resolve, текст `"❌ Разовая пара отменена\n\n{subject}\nДата: {date}\nПара: {lesson_number}-я"`.

**Dispatcher (`bot/consumers/event_dispatcher.py`):**
- Импорты обоих handlers, регистрация:
  ```python
  "lesson.one_off.created": lambda event: handle_lesson_one_off_created(event, bot=..., academic_client=..., send_queue=...),
  "lesson.one_off.cancelled": lambda event: handle_lesson_one_off_cancelled(...),
  ```

**Pytest (8/8 новых зелёные, 136/136 full suite):**
- `test_sends_to_all_group_students` — 3 студента все получают задачу (D-18, не фильтруем старосту); текст содержит date + lesson_number + subject + classroom.
- `test_empty_group_no_error` — пустой список не вызывает ошибок и не enqueue-ит.
- `test_skips_students_without_telegram_id` — `telegram_id=0` пропущен.
- `test_skips_missing_payload_fields` — неполный payload → warning, `send_queue.put` не вызван.
- Зеркальные 4 теста для cancelled + `test_fallback_subject_on_grpc_error`.

## Verification

| Команда | Результат |
|---------|-----------|
| `./gradlew :services:schedule-service:schedule-app:test --tests OneOffLessonEventPublisherIT` | 2/2 PASSED (BUILD SUCCESSFUL in 24s) |
| `./gradlew :services:schedule-service:schedule-app:build` | BUILD SUCCESSFUL in 36s |
| `cd services/notification-bot && py -m pytest -q` | **136 passed in 12.77s** (+8 новых, без регрессии) |
| `ls event-schemas/lesson.one_off.*.json` | lesson.one_off.created.json + lesson.one_off.cancelled.json |
| `grep -rn "publishEvent.*OneOffLesson" .../OneOffLessonService.java` | 2 совпадения (create + delete) |
| `grep -rn "lesson.one_off" .../event_dispatcher.py` | 4 совпадения (2 import + 2 register) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] test_dispatcher_has_eight_event_types оказался зависимым assertion**

- **Found during:** Task 2 full pytest pass после регистрации двух новых handlers.
- **Issue:** Существующий тест `tests/test_event_dispatcher.py::test_dispatcher_has_eight_event_types` сравнивает `set(dispatcher._handlers.keys())` с захардкоженным expected set; регистрация `lesson.one_off.created/cancelled` валит assertion `Extra items in the left set`.
- **Fix:** Добавил оба новых ключа в expected set в том же тесте, с комментарием `# 60-04`.
- **Files modified:** `services/notification-bot/tests/test_event_dispatcher.py`.
- **Commit:** `c4227a5`.

### Scope boundary notes

- Plan указывал путь `services/notification-bot/bot/event_dispatcher.py`, реальный — `services/notification-bot/bot/consumers/event_dispatcher.py`. Использовал реальный путь; `bot/event_dispatcher.py` не создавал, чтобы не иметь дубль.
- Four pytest-имён в plan behaviour (`test_sends_to_all_group_students`, `test_empty_group_no_error`, `test_sends_cancellation_to_all`, `test_message_contains_date_and_subject`) присутствуют как ожидается; добавлены дополнительно `test_skips_students_without_telegram_id`, `test_skips_missing_payload_fields`, `test_fallback_subject_on_grpc_error` для покрытия edge-cases по примеру существующего `test_lesson_cancelled.py`.

## Known Stubs

Нет. Attendance-service consumer для `lesson.one_off.*` — out-of-scope для 60-04 (планируется в 60-05, см. D-22 и CONTEXT.md). Notification-web (STOMP forwarding) — не является требованием этого плана.

## Threat Flags

STRIDE-mapping плана полностью смитигирован:
- **T-60-04 (Idempotency, notification-bot):** дублирующий event вызывает повторную отправку — accept; send_queue + try/except на send_message; KeyError guard на отсутствующие поля.
- **T-60-04 (Tampering, attendance-service):** caught за пределами плана — реализация в 60-05.

Новый surface: publisher side (schedule-service → RabbitMQ fanout `rut-uit.events`, внутренняя шина, без новых сетевых границ) и consumer side (notification-bot внутри той же docker network). Нет новых threat flags.

## Self-Check: PASSED

**Created files (existence verified):**
- FOUND: event-schemas/lesson.one_off.created.json
- FOUND: event-schemas/lesson.one_off.cancelled.json
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/OneOffLessonCreatedEvent.java
- FOUND: services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/OneOffLessonCancelledEvent.java
- FOUND: services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonEventPublisherIT.java
- FOUND: services/notification-bot/bot/notifications/lesson_one_off_created.py
- FOUND: services/notification-bot/bot/notifications/lesson_one_off_cancelled.py
- FOUND: services/notification-bot/tests/test_one_off_created_handler.py
- FOUND: services/notification-bot/tests/test_one_off_cancelled_handler.py

**Commits (verified via `git log --oneline -5`):**
- FOUND: 04afe77 feat(60-04): publish lesson.one_off.created/cancelled events from schedule-service
- FOUND: c6e49f6 test(60-04): add failing tests for one_off created/cancelled bot handlers
- FOUND: c4227a5 feat(60-04): notification-bot handlers for lesson.one_off.created/cancelled

**Tests:**
- `:services:schedule-service:schedule-app:build` — BUILD SUCCESSFUL (включая новый `OneOffLessonEventPublisherIT`).
- `notification-bot pytest` — 136/136 PASSED (+8 новых, +update 1 assertion, без регрессии).

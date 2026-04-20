# M04 Notes

Живой файл. Surprises, отклонения, измерения, технические долги.

---

## 2026-04-20 — старт milestone

- M03b закрыт `eb125c4` + tag `v0.0.0-alpha.4` (локально без push).
- 58 коммитов ahead origin — push отложен до конца v0.0.0 (по решению владельца, эта сессия).
- Выбран M04 первым по dependency graph (рекомендация из hand-off `e85081a`).
- В DECISIONS.md 3 открытых развилки требуют подтверждения до старта кода:
  1. shared-observability модуль vs duplication по сервисам.
  2. Alert receiver: новый endpoint `/internal/alert` в notification-service vs прямой Aiogram bot endpoint.
  3. Тихий час: фиксированный 22:00-08:00 (MSK) vs configurable per-alert.

## 2026-04-20 — Surprise при старте Группы 6

Аудит показал что архитектура events **сильно отличается от того что
описано в QA3**:

- **Реальная схема (внедрена в M02 P0-6):** Spring `ApplicationEvent` →
  `DomainEventListener.@TransactionalEventListener(BEFORE_COMMIT)` →
  outbox table → `OutboxPublisherJob` → `RabbitOutboxEventSender` →
  Rabbit fanout `rut-uit.events`. Headers `event_type`, body =
  serialized event (envelope `{event_type, event_id, occurred_at, payload}`).

- **`shared-events.DomainEvent`** (создан в M02) имеет envelope
  `{event_version, trace_id, occurred_at, source}` БЕЗ обёртки `payload`.
  Используется только в shared-events тестах. **Сервисы НЕ мигрированы.**

- Каждый сервис (academic/auth/schedule/attendance) имеет
  собственный `event/DomainEvent.java extends ApplicationEvent` —
  envelope несовместим с `shared-events.DomainEvent`.

- Event-schemas (`event-schemas/*.json`) — required: `event_type`,
  `event_id`, `occurred_at`, `payload`. `trace_id`/`event_version`
  объявлены как опциональные в `_common.json#/$defs` но не required.

**Что это значит для QA3:**

(a) Полная migration сервис-DomainEvent → `shared-events.DomainEvent` —
    разные envelopes, требует переписать все event-классы (~25 шт),
    schemas (15+), publisher'ов, consumer'ов, контракт-тесты, и
    обновить версии всех событий (breaking change для consumer'ов).
    Объём ~3-5д, риск обвала existing flows.

(b) Минимально-инвазивный retrofit: добавить `trace_id` поле в
    каждый сервис-`DomainEvent` (envelope) + MDC.get/put в
    `DomainEventListener` (publish) и `EventConsumer` (consume) +
    сделать `trace_id` required в `_common.json` schema + обновить
    schemas чтобы включали `trace_id` в required envelope.
    Объём ~3-4 часа. Совместимо с outbox flow. Тестируется через
    contract-тесты M02.

(c) Wrap envelope: добавить `trace_id`/`event_version`/`source` через
    AMQP headers (publisher писать в headers, consumer читать), без
    касания JSON envelope/schemas. Cleaner с точки зрения
    backward-compat, но trace_id не в JSON-логах (нужен дополнительный
    extract).

Рекомендую **(b)** — минимум кода, максимум value (trace_id в JSON
event payload = self-describing, виден в logs/Loki, парсится
Python-ботом). (c) проигрывает потому что Loki ingest не видит AMQP
headers (только JSON body).

**Развилка для пользователя.** До решения — Группа 6 на паузе.

---

## 2026-04-20 — Группа 6 — pre-existing test failure unrelated to M04

При запуске contract-тестов attendance после migration:

- `ExcuseEventContractIT.createExcuse_publishesRequestedEvent_matchingBotContract`
  падает с `BadRequestException: Уважительную можно подать только на пару
  с «н». Урок id=1 имеет статус «PRESENT»`.

Причина: business-rule в `ExcuseService.java:172` (status != ABSENT →
reject). Test setup создаёт attendance record со статусом PRESENT, что
противоречит этому правилу. Бизнес-правило, видимо, добавлено каким-то
PR между моментом создания теста и сейчас. Pre-existing failure, не
вызвано M04 D5(a) migration.

Все остальные contract-тесты ✅ зелёные:
- `LessonStartedContractIT` ✅
- `LessonCancelledContractIT` ✅
- `LessonClosedContractIT` ✅
- `AttendanceMarkedContractIT` ✅
- `ExcuseEventContractIT.updateStatus_publishesDecidedEvent` ✅
- `*Contract*` в academic ✅

Действие: фиксировать в backlog для отдельной фазы (test seed нужно
обновить под новое business-правило) — НЕ scope M04.

---

## 2026-04-20 — развилки закрыты владельцем

- D1=(a) shared-observability модуль.
- D2=(a) `POST /internal/alert` в notification-service → RabbitMQ event → bot.
- D3=(a) фиксированный 22:00-08:00 MSK через `mute_time_intervals`.

Подробности в DECISIONS.md. Стартую Группу 1.

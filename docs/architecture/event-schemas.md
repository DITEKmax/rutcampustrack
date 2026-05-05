# Event Schemas

JSON Schema для событий RabbitMQ. Все схемы живут в `event-schemas/*.json`,
используют draft 2020-12. Contract-тесты в каждом backend-сервисе валидируют
реальный payload (из outbox) против schema.

## Структура

### `_common.json` — shared `$defs`

Единое место для envelope-полей и типов, используемых несколькими событиями.
Остальные схемы ссылаются через `{"$ref": "_common.json#/$defs/<name>"}`.

| `$def` | Тип | Описание |
|--------|-----|----------|
| `eventId` | string, format: uuid | `envelope.event_id` — идентификатор сообщения |
| `occurredAt` | string, format: date-time | `envelope.occurred_at` — время в UTC (ISO-8601) |
| `traceId` | string, minLength 1 | Correlation ID из MDC (M04 observability) |
| `eventVersion` | integer, min 1 | Версия schema (текущее — 1) |
| `lessonNumber` | integer, 1..8 | Номер пары в дне (унифицированный diapason) |

### Envelope

Все события имеют одинаковую структуру:

```json
{
  "event_type": "<const>",
  "event_id":   "<uuid>",
  "occurred_at": "<ISO-8601>",
  "payload":    { ... }
}
```

`event_type` — `const` с именем события (`lesson.started`, `attendance.marked`
и т.д.). `event_id` и `occurred_at` переходят в схемах через `$ref`.

## Текущие события (v1)

### Schedule Service

| Event | Schema | Триггер |
|-------|--------|---------|
| `lesson.started` | `lesson.started.json` | Пара перешла в ACTIVE (cron) |
| `lesson.reminder` | `lesson.reminder.json` | Idempotent broadcast в фазах `phase=midpoint` (середина active-пары) и `phase=near_end` (за ~5 минут до конца). Schedule помечает отправку в `lessons`, Notification Web/Bot фильтруют server-side по пользовательским настройкам и состоянию отметки; PWA дополнительно скрывает foreground-уведомление после локальной отметки. |
| `lesson.closed` | `lesson.closed.json` | Пара перешла в CLOSED (end_time + 5min) |
| `lesson.cancelled` | `lesson.cancelled.json` | Староста/admin отменил пару |
| `lesson.deleted` | `lesson.deleted.json` | Admin удалил пару из расписания |
| `lesson.one_off.created` | `lesson.one_off.created.json` | Создана разовая пара |
| `lesson.one_off.cancelled` | `lesson.one_off.cancelled.json` | Разовая пара отменена |

### Academic Service

| Event | Schema | Триггер |
|-------|--------|---------|
| `group.updated` | `group.updated.json` | Any PUT /groups/{id} |
| `group.renamed` | `group.renamed.json` | Rename детектируется сервисом |
| `group.archived` | `group.archived.json` | Group → archived |
| `semester.archived` | `semester.archived.json` | Активный семестр архивирован |
| `homework.published` | `homework.published.json` | Староста создал ДЗ |
| `homework.updated` | `homework.updated.json` | Update ДЗ |

### Attendance Service

| Event | Schema | Триггер |
|-------|--------|---------|
| `attendance.marked` | `attendance.marked.json` | Студент отметился (geo/headman/auto). Payload расширен `lesson_number`, `lesson_date`, `subject_id`, `subject_name` (NOTIF unification): bot/PWA/web показывают студенту «Староста проставил статус …» при `marked_by="headman"`. |
| `excuse.requested` | `excuse.requested.json` | Студент создал excuse-тикет |
| `excuse.decided` | `excuse.decided.json` | Староста одобрил/отклонил тикет |
| `late_checkin.requested` | `late_checkin.requested.json` | Запрос позднего checkin'а |
| `late_checkin.decided` | `late_checkin.decided.json` | Решение по позднему checkin'у |
| `late_checkin.decision` | `late_checkin.decision.json` | Legacy-вариант (bot consumer) |

### Auth Service

| Event | Schema | Триггер |
|-------|--------|---------|
| `otp.verified` | `otp.verified.json` | OTP-код подтверждён |

## Versioning policy

**Текущее поколение — v1.** Все события сейчас без explicit `event_version`
в envelope — подразумевается 1 (через `shared-events` `AbstractEventPublisher`
`EventVersion.resolveEventVersion()` — default 1).

### Типы изменений

**Non-breaking (minor, same version):**
- Добавление нового **optional** поля в payload.
- Добавление нового события (новый файл schema).
- Добавление новой `const` в existing enum (осторожно — consumer должен
  игнорировать неизвестные значения).

**Breaking (major, требует bump):**
- Удаление / переименование поля.
- Изменение типа поля.
- Добавление нового **required** поля.
- Сужение enum (удаление значения).
- Изменение семантики поля.

### Bump процедура

1. Текущая schema остаётся как есть — consumer'ы продолжают получать v1.
2. Новая schema: `event-schemas/<event>.v2.json` (или добавить
   `"event_version": { "const": 2 }` в существующую + создать v1-snapshot).
3. Publisher пишет новую v2 с `event_version=2`.
4. Consumer'ы детектируют версию через `event_version` и обрабатывают раздельно.
5. После миграции всех consumer'ов — старая schema удаляется.

Живущий пример будет в M05/M07 (когда появится необходимость). Пока —
все события v1.

## $ref и tooling

### Валидация в тестах

Контракт-тесты в `*-app/src/test/java/.../events/*ContractIT.java`
используют `networknt/json-schema-validator` 1.5.4:

```java
JsonSchema schema = JsonSchemaFactory
        .getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(schemaFileUri);
Set<ValidationMessage> errors = schema.validate(payloadNode);
```

`$ref: "_common.json#/$defs/..."` резолвится автоматически — networknt
ищет sibling-файл относительно baseURI загруженной схемы.

### Валидация вне тестов (contract review)

```bash
# Установка networknt CLI — опционально, когда нужно вручную проверить payload
# Локально можно использовать ajv-cli или https://www.jsonschemavalidator.net/

ajv validate \
  -s event-schemas/lesson.started.json \
  -r event-schemas/_common.json \
  -d payload.json
```

## Добавление нового события

1. Создать `event-schemas/<event>.json` со структурой envelope + payload.
2. Для общих полей использовать `$ref` на `_common.json#/$defs/...`.
3. Добавить запись в эту таблицу.
4. Publisher'ить из соответствующего сервиса через outbox (не напрямую в Rabbit!).
5. Contract-тест: `*ContractIT` в сервисе, который валидирует реальный payload
   (см. `schedule.events.LessonStartedContractIT` как reference).
6. Если консьюмер — в другом сервисе, добавить запись event-schemas/ в
   `architecture.md` → таблица коммуникаций.

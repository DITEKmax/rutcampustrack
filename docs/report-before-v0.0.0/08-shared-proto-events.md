# 08. Shared Contracts — proto/ + event-schemas/ — отчёт аудита

## Сводка

Общие контракты RutCampusTrack делятся на два канала: **gRPC** для синхронных запросов между сервисами (`proto/*.proto`) и **RabbitMQ events** для асинхронного fanout (`event-schemas/*.json`, fanout exchange `rut-uit.events`). Этот аудит проверяет **качество и согласованность сами контрактов**, не исходников, которые их используют.

Состояние:
- **`proto/`** — 2 файла: `academic.proto` и `schedule.proto`. `attendance-service` не имеет сервер-контракта (только клиент). `auth-service` — не использует gRPC вовсе. `notification-service` — тоже не сервер gRPC.
- **`event-schemas/`** — **19 JSON Schema** (draft 2020-12) файлов. Покрыты события: attendance, excuse (request/decided), late_checkin (requested/decision/decided), lesson (started/closed/cancelled/deleted/one_off.created/one_off.cancelled), homework (published/updated), group (archived/renamed/updated), semester.archived, otp.verified.

Главные претензии:
1. **Утечка пароля через gRPC-контракт**: `UserByTelegramIdResponse.initial_password` (proto/academic.proto:155) — пароль в открытом виде в proto-контракте. Это системный артефакт, связывающий P0-1 из academic-service и P0-3 из notification-bot. Исправление требует синхронных изменений: удалить поле из .proto, пересобрать stubs, адаптировать bot.
2. **Нет единого стиля дат/времени**: поля времени в .proto — `string` (ISO, без TZ), в event-schemas — то `format: "date-time"`, то `format: "time"`, то просто `string`. Никакой договорённости про timezone. Часть событий (lesson.started) шлёт `start_time` как строка без даты — при обработке требуется дополнительный запрос (зависимость от schedule для получения date).
3. **Enum-значения передаются как string** и повторяются между proto/json-schema/Java. Отсутствует единый источник правды. Например, `status = 10 // planned, active, closed, cancelled` в .proto — это комментарий, а не enum. Legal `new_status` можно внести и компилятор не поймает.
4. **Отсутствие `additionalProperties: false` во всех JSON-схемах** — валидатор пропускает любые лишние поля. Значит нет гарантии, что producer и consumer согласованы по версии. Изменение схемы не отловится автоматическими проверками.
5. **Дублированные концепты**: `late_checkin.decision` (бот→attendance, нажатие кнопки) и `late_checkin.decided` (attendance→все, итог) — OK. Но `excuse.decided` существует без параллельного `excuse.decision` — значит бот публикует решение по excuse как-то иначе (возможно прямым REST в attendance). Согласованности нет.
6. **`lesson_number: 1..8` — hardcode** — в 6 JSON-схемах и в .proto. Если учреждение меняет количество пар — придётся править все schemas.
7. **Нет схемы для `otp.requested`** — но она нужна! В отчёте по 01-auth (P0-4) сказано, что OTP-код нужно ДОСТАВЛЯТЬ через RabbitMQ event `otp.requested` (который сейчас не существует), а не в теле HTTP-ответа. Это отсутствие контракта — часть проблемы.

**Счётчики:** **P0 = 2**, **P1 = 7**, **P2 = 8**, **P3 = 5**.

## Структура

```
rutcampustrack/
├── proto/
│   ├── academic.proto                         ← 9 RPC методов, 15 message
│   └── schedule.proto                         ← 6 RPC методов, 10 message
└── event-schemas/
    ├── attendance.marked.json
    ├── excuse.decided.json
    ├── excuse.requested.json
    ├── group.archived.json
    ├── group.renamed.json
    ├── group.updated.json
    ├── homework.published.json
    ├── homework.updated.json
    ├── late_checkin.decided.json
    ├── late_checkin.decision.json
    ├── late_checkin.requested.json
    ├── lesson.cancelled.json
    ├── lesson.closed.json
    ├── lesson.deleted.json
    ├── lesson.one_off.cancelled.json
    ├── lesson.one_off.created.json
    ├── lesson.started.json
    ├── otp.verified.json
    └── semester.archived.json
```

Расхождения / замечания со структурой:
- Нет `attendance.proto`, `auth.proto`, `notification.proto` — `attendance-service` не serve'ит gRPC (только клиент), остальное — тоже REST-only.
- Нет события `otp.requested` — см. 01-auth P0-4.
- Нет события `lesson.reminder` (три напоминания) — см. 05-notification-service P1-1.
- Нет события `user.created`, `user.updated`, `user.archived` — но у академ есть gRPC для get'ов. Для аудита действий пользователей событий нет.

---

## Критичные проблемы (P0)

### P0-1: ✅ ACCEPTED — `UserByTelegramIdResponse.initial_password` в .proto — канал для утечки паролей
**Статус:** by design (см. `OWNER-ANSWERS.md` 08-Q1 + 01-Q1 + Meta M1, 2026-04-18). Поле остаётся в контракте, нужно для notification-bot. Альтернативный канал (setup_token) не вводится. Ниже — оригинальное описание.

- **Где:** `proto/academic.proto:155` — `string initial_password = 10;`.
- **Что:** gRPC RPC `GetUserByTelegramId` (вызов notification-bot'ом при `/start`) возвращает поле `initial_password`. Это связующий артефакт: пока поле в контракте — его тянут stubs'ы и Java-реализация (`AcademicGrpcServiceImpl`) обязана его заполнять (или возвращать пустой string, что нигде не гарантируется в текущем коде).
- **Риск:** при utleчке gRPC-трафика — пароль в проде попадает в логи, метрики (даже на уровне stub). Фиксация P0-1 academic-service требует удаления этого поля.
- **Как чинить:**
  1. Удалить поле `initial_password = 10;` из `UserByTelegramIdResponse`. Добавить `reserved 10; reserved "initial_password";`.
  2. Обновить `AcademicGrpcServiceImpl.java` — не заполнять поле (уже помечено к удалению в 02-academic).
  3. Заменить механизм: при создании пользователя academic возвращает **одноразовый setup-токен** через свой REST (admin-flow), а бот при `/start` отправляет ссылку с токеном. Настоящий пароль никуда не уходит.
- **Зависимости:** 01-auth P0-2 (initial_password в БД), 02-academic P0-1 (возврат в REST), 06-notification-bot P0-3 (plain в Telegram).

### P0-2: Отсутствует событие `otp.requested` (и схема для него)
- **Где:** `event-schemas/` — нет файла `otp.requested.json`. Есть только `otp.verified.json` (auth→bot «удали сообщения»).
- **Что:** в текущей архитектуре auth-service возвращает OTP-код в теле HTTP-ответа `POST /auth/otp/request` (см. 01-auth P0-4). После фикса P0-4 нужно передавать код через RabbitMQ на notification-bot → в Telegram. Это требует контракт-схемы `otp.requested { telegram_id, code, ttl_seconds }`.
- **Риск:** без схемы любая реализация будет ad-hoc и несогласованной. Bot будет ждать одну форму, auth будет слать другую.
- **Как чинить:** создать `event-schemas/otp.requested.json`:
  ```json
  {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "otp.requested",
    "description": "Auth Service публикует после генерации OTP. Consumer: notification-bot — доставляет код в Telegram.",
    "type": "object",
    "required": ["event_type", "event_id", "occurred_at", "payload"],
    "additionalProperties": false,
    "properties": {
      "event_type": { "const": "otp.requested" },
      "event_id": { "type": "string", "format": "uuid" },
      "occurred_at": { "type": "string", "format": "date-time" },
      "payload": {
        "type": "object",
        "additionalProperties": false,
        "required": ["telegram_id", "code", "ttl_seconds"],
        "properties": {
          "telegram_id": { "type": "integer" },
          "code": { "type": "string", "pattern": "^\\d{6}$" },
          "ttl_seconds": { "type": "integer", "minimum": 60, "maximum": 600 }
        }
      }
    }
  }
  ```
- **Зависимости:** 01-auth (publish), 06-notification-bot (consume), RabbitMQ binding.

---

## Серьёзные проблемы (P1)

### P1-1: Время/даты передаются как строки без TZ (и без единого формата)
- **Где:**
  - `proto/schedule.proto`: `ActiveLessonRequest.timestamp` (ISO-8601), `date`, `date_from`, `start_time`, `end_time`, `starts_at = date + T + start_time` — всё `string`.
  - `proto/academic.proto`: `SemesterResponse.date_from`, `date_to` — `string`.
  - `event-schemas/*.json`: иногда `format: "date-time"`, иногда `format: "date"`, иногда `format: "time"`, без указания TZ.
- **Что:**
  1. Для Java — используется `OffsetDateTime` / `LocalDate` — десериализация строки может идти по-разному в разных сервисах.
  2. Для Python (notification-bot) — `datetime.fromisoformat()` без TZ даёт naive datetime, что в 06-notification-bot уже привело к P1-3 (сдвиг 3 часа при отсутствии `TZ=Europe/Moscow`).
  3. proto3 имеет `google.protobuf.Timestamp` — этой типа нет.
- **Как чинить:**
  - `.proto`: заменить `string` на `google.protobuf.Timestamp` для всех временных точек (activeAt, starts_at, occurred_at).
  - В event-schemas — требовать `format: "date-time"` (ISO-8601 c offset): `"2026-04-17T14:30:00+03:00"`. Строгий pattern: `^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?([+-]\\d{2}:\\d{2}|Z)$`.
  - Документировать в `docs/architecture.md`: «все временные метки — UTC ISO-8601 с offset, DB — TIMESTAMPTZ».
- **Зависимости:** все сервисы (гoogle.protobuf.Timestamp требует регенерации stub'ов + миграций парсинга).

### P1-2: Все JSON-схемы без `additionalProperties: false`
- **Где:** все 19 файлов в `event-schemas/`.
- **Что:** JSON Schema по умолчанию допускает extra properties. Producer может добавить любое поле, и consumer (валидатор схемы) пропустит. Это хорошо для forward-compatibility (новые поля добавляются безопасно), но плохо для раннего обнаружения typos и schema drift.
- **Риск:**
  - Rename'ы не ловятся в тестах (поле просто не попадёт в объект на той стороне, и не будет ошибки).
  - Опечатки при переписывании события (`user_id` → `userId`) не отлавливаются.
- **Как чинить:** везде в `event-schemas/*.json` добавить `"additionalProperties": false` на уровне корня и внутри `payload`. Если нужна forward-compat — оставить на корне `additionalProperties: true`, а на payload — `false`. Добавить в CI тест: для каждого opublication в каждом сервисе генерируется фиктивный event и валидируется по схеме.
- **Зависимости:** все consumer'ы событий — проверить, что они шлют только объявленные поля.

### P1-3: Enum-значения хардкодятся как string в .proto — нет enforcement типа
- **Где:**
  - `proto/schedule.proto:69` — `string status = 10;  // planned, active, closed, cancelled`
  - `proto/schedule.proto` — `TeacherSubjectInfo.subject_type` — `string`
  - `proto/academic.proto:102` — `SemesterResponse.first_week_type` — `string` (должен быть ODD/EVEN или 1/2 — неясно из контракта)
  - `proto/academic.proto:4,5,119,120` — `role`, `status` — `string`
- **Что:** protobuf3 имеет enum-тип. Если кто-то на клиенте напишет `"active"` (нижний регистр — принято в БД) vs `"ACTIVE"` (Java-сторона) — всё зависит от сервисной реализации. Это backward compat, но hidden fragile.
- **Риск:** новое значение добавляется → старый клиент не знает про него → unpredictable поведение (NPE в Java при `LessonStatus.valueOf("new_status")`).
- **Как чинить:** перевести все enum-поля на `enum` в proto3:
  ```proto
  enum LessonStatus {
    LESSON_STATUS_UNSPECIFIED = 0;
    LESSON_STATUS_PLANNED = 1;
    LESSON_STATUS_ACTIVE = 2;
    LESSON_STATUS_CLOSED = 3;
    LESSON_STATUS_CANCELLED = 4;
  }
  message LessonResponse { ... LessonStatus status = 10; ... }
  ```
  — proto3 enum поддерживает unknown values как `UNRECOGNIZED` в Java stub, forward-compat.
- **Зависимости:** все сервисы, которые читают .proto. Миграция одноразовая.

### P1-4: `first_week_type` — string в контракте и неочевидная семантика
- **Где:** `proto/academic.proto:102` — `string first_week_type = 5;` в `SemesterResponse`.
- **Что:** из памяти пользователя (project memory): «ISO-чёт→1-я→WeekType.ODD; ISO-нечёт→2-я→WeekType.EVEN; UI показывает только «1»/«2»». В .proto — просто string. Значит по сети может прийти «ODD», «EVEN», «1», «2», «одна», «две» — кто знает. Semantic drift неизбежен.
- **Как чинить:** enum:
  ```proto
  enum WeekType {
    WEEK_TYPE_UNSPECIFIED = 0;
    WEEK_TYPE_ODD = 1;   // 1-я (нечётная ISO)
    WEEK_TYPE_EVEN = 2;  // 2-я (чётная ISO)
  }
  message SemesterResponse { ... WeekType first_week_type = 5; ... }
  ```
- **Зависимости:** schedule-service, pwa, web-panel — везде где читается (см. 03-schedule-service P0-5 про дрейф алгоритма).

### P1-5: Нет контракта версионирования
- **Где:** нигде в `proto/` и `event-schemas/` нет механизма `schema_version` или `event_version`.
- **Что:** при breaking change невозможно консьюмеру понять, совместим ли он с producer'ом. Producer может измениться — consumer молча сломается.
- **Как чинить:**
  - В событиях: добавить `"schema_version": { "const": 1 }` как required поле. Консьюмер сверяет номер.
  - В .proto: `package rutcampustrack.schedule.v1;` — version в namespace + использовать `reserved` при удалении полей.
- **Зависимости:** все publishers/consumers.

### P1-6: `excuse.requested` содержит массив `lessons` с опциональными полями для «обогащения», но без гарантии consistency
- **Где:** `event-schemas/excuse.requested.json:20-33`.
- **Что:** поля `lesson_number`, `date`, `subject_id`, `subject_name` могут быть `null`, «если не удалось резолвить через schedule/academic». Это значит producer пытается сделать cross-service call во время публикации события, и если он неудачен — пишет null. Тогда consumer (бот) получает незавершённый объект, должен самостоятельно fetch'ить.
- **Риск:**
  - Два источника истины — событие и синхронный fetch.
  - Бот должен уметь обрабатывать оба случая (null / non-null).
  - Дополнительная нагрузка на academic/schedule от каждого publish.
- **Как чинить:** (а) или гарантировать, что `lessons` всегда содержит все поля (блокирующий enrichment на стороне attendance с retries/таймаутом), (б) или не включать enrichment в событие — consumer всегда fetch'ит по `lesson_ids`. Текущий «шит ту» — худший вариант.
- **Зависимости:** attendance-service, notification-bot, notification-service.

### P1-7: Event `otp.verified` — не указано, где защищён канал от injection
- **Где:** `event-schemas/otp.verified.json`.
- **Что:** событие содержит `telegram_id`. Это достаточно уязвимый data point — если атакующий внедрит event с чужим `telegram_id` в exchange, бот начнёт удалять чужие сообщения. В схеме нет `user_id` для cross-reference, нет signature.
- **Как чинить:** RabbitMQ exchange закрыт (только publisher из rct-network), но добавить HMAC-подпись событий как best practice. Либо: в consumer (bot) делать double-check (gRPC AcademicGrpcService.GetUserByTelegramId перед применением).
- **Зависимости:** bot consumer.

---

## Средние (P2)

### P2-1: proto3 — нет `optional` у полей; `int64 group_id = 6` — 0 это значение или «отсутствует»?
- **Где:** `academic.proto:121, 151` (UserResponse.group_id, UserByTelegramIdResponse.group_id).
- **Что:** в proto3 по умолчанию primitive-поля не могут быть «не установлены». Для teacher/admin у которых нет group_id — в Java-stub придёт `0`. Но нулевой ID может стать валидным id в далёком будущем. Правильно — использовать `optional int64` (proto3 optional) или сначала `google.protobuf.Int64Value`.
- **Как чинить:** `optional int64 group_id = 6;`. Соответственно, в Java-коде проверка `hasGroupId()` вместо `getGroupId() != 0`.

### P2-2: `GroupResponse` не содержит `semester_id`
- **Где:** `academic.proto:47-54`.
- **Что:** группа как единица не привязана к семестру в контракте, но бизнес-логика может требовать (например, рассчёт расписания). Проверить, нужен ли.

### P2-3: `StudentInfo.display_name` содержит полное ФИО — privacy concern
- **Где:** `academic.proto:64-69`.
- **Что:** display_name собирается конкатенацией `last_name + first_name + middle_name`. Отчество — иногда чувствительно. Если это отдаёт в Telegram-сообщения — видно всем. В контракте нет возможности запросить краткое имя.
- **Как чинить:** разбить на отдельные поля `last_name`, `first_name`, `middle_name_opt` + короткое `display_name` = `"Иванов И.И."`. Consumer сам решает, как отображать.

### P2-4: `HeadmanCheckRequest` принимает `(user_id, group_id)` — user_id уже известен, group_id избыточен?
- **Где:** `academic.proto:88-91`.
- **Что:** староста определяется одним user_id (одна группа = один староста в DAO). Зачем group_id? Либо это защита от кросс-групповой уязвимости, либо артефакт. Неочевидно.
- **Как чинить:** (а) комментарий в .proto объясняющий `group_id` (защита от cross-group), либо (б) упростить контракт.

### P2-5: `lesson.deleted` — есть схема, но `lesson_id` только уникальный идентификатор
- **Где:** `event-schemas/lesson.deleted.json` (предполагаю содержимое по названию — не прочёл).
- **Что:** при удалении пары consumer (attendance, notification) должны знать `group_id`, `subject_id`, `date`, чтобы корректно отреагировать. Если в schema только `lesson_id` — потребуется дополнительный запрос к schedule, но schedule уже удалил lesson → 404.
- **Как чинить:** в `lesson.deleted` включать полный snapshot удалённого lesson'а.

### P2-6: `lesson.closed` — мало данных
- **Где:** `event-schemas/lesson.closed.json`.
- **Что:** обязательные только `lesson_id`, `group_id`; опционально `subject_id`. Consumer attendance должен знать list'ы студентов группы, которые не отметились, чтобы выставить им ABSENT — делает это через gRPC `AcademicGrpcService.GetGroupMembers`. Это нормально, но ведёт к дополнительному вызову. Альтернативно — включить snapshot `group_members` в событие (тяжелее, но без round-trip'а).

### P2-7: `lesson_number: 1..8` жёстко зашито в 6 схемах
- **Где:** `event-schemas/lesson.started.json:18`, `lesson.one_off.created.json:19`, и т.д.
- **Что:** `"maximum": 8` — если РУТ МИИТ расширит до 10 пар, потребуется правка всех схем и валидаторов. Даже если сейчас 8 — это бизнес-решение, а не technical invariant.
- **Как чинить:** убрать `maximum` или вынести в константу-конфиг.

### P2-8: Контракт `excuse.decision` отсутствует, хотя в 06-notification-bot упомянут
- **Где:** `event-schemas/excuse.decision.json` отсутствует. Только `excuse.decided` (attendance→все, итог).
- **Что:** когда староста нажимает «одобрить» в Telegram, бот должен опубликовать решение — attendance его consumes. Либо бот делает это через прямой REST на attendance (минуя RabbitMQ), либо bus-based decision handling — асимметрично c late_checkin.
- **Как чинить:** если стараются через REST — задокументировать. Если через event — добавить `excuse.decision.json`.

---

## Мелкие и nit (P3)

### P3-1: `semester.archived` — только `semester_id`, но семестр — важная сущность
- Описание минимального payload ОК, но даты семестра и имя полезны для consumer'ов (чтобы показать «Архивирован семестр Весна 2026»).

### P3-2: `group.renamed` — проверить наличие `old_name` + `new_name`
- Не прочитал файл, но логически — оба нужны. Минимальный payload `{group_id}` заставит consumer делать gRPC на academic, а academic скажет новое имя (старого уже нет).

### P3-3: Нет схемы для `threshold.changed`
- Если порог посещаемости меняется (global/group/subject) — это влияет на расчёт «красной зоны». Но события нет.

### P3-4: Комментарии в `.proto` смешивают русский и английский
- `schedule.proto:10-11`: «Получить активную пару для группы в данный момент». `academic.proto:2`: «gRPC контракт» — русский. Но рядом `reserved 3; reserved "code"` с комментарием «BUG-006-5 / план 58-04» — cross-reference на внутренние документы.
- Nit: перевести всё в один язык (русский логичнее для этого проекта).

### P3-5: Нет `.proto` для common types (Timestamp, Email, Money)
- Прямо сейчас это не нужно, но если система расширится — common.proto с типажами common'ами сократит дублирование.

---

## Мёртвый код

- Нет gRPC-сервера attendance (`attendance.proto` отсутствует) — ОК, attendance exposed через REST. Но если в будущем кто-то захочет вызывать attendance из бота напрямую — нет контракта.

---

## Костыли и TODO/FIXME

- `academic.proto:50-52` — `reserved 3; reserved "code";` с комментарием про `BUG-006-5 / план 58-04` — правильно использован reserved, но ссылка на внутренний план не будет понятна новому разработчику.
- `academic.proto:124` — `Preset avatar id (e.g. "avatar_03"); empty string == not chosen, render initials. BUG-004.` — тот же паттерн.
- `schedule.proto:61-64` — `reserved 5; reserved "teacher_id";` с комментарием `D-16` — ок.
- `schedule.proto:22-25` — комментарий `Phase 61 D-04` — ссылки на phase-артефакты.

Все `reserved` использованы корректно — хорошее соблюдение proto best practice.

---

## Тесты

В отчёте «08» нет тестов как таковых — .proto компилируется в рамках каждого сервиса, события валидируются в тестах соответствующих сервисов. Но:

### Что не покрыто глобально
- **CI-шаг, валидирующий все event-schemas**: не обнаружен (см. 13-infra-docker-ci.md). Если кто-то коммитит сломанный JSON — заметят только в PR-ревью.
- **Тест round-trip**: publisher создаёт событие → валидация по схеме → consumer парсит → сверяется. Ни в одном сервисе этого нет.
- **Тест `reserved` полей**: что при попытке записать «старое» значение — в `.proto` stub выдаёт ошибку.

### Рекомендации
- Ввести `tools/validate-schemas.py` в корень репо и шаг в CI: для каждого `.json` в `event-schemas/` — валидация как JSON Schema против самой себя (meta-schema).
- Ввести `buf lint` (https://buf.build) для `.proto` — отлов опечаток, breaking changes, несогласованной `option`.
- В каждом сервисе — тест, что все publish'ы валидируются по соответствующей схеме.

---

## Соответствие CLAUDE.md

| Правило | Статус | Комментарий |
|---------|:------:|-------------|
| gRPC контракты в `proto/` | ✅ | 2 файла на корне |
| gRPC namespace `ru.rutcampustrack.{service}.grpc` | ✅ | `option java_package = "ru.rutcampustrack.academic.grpc";` + schedule |
| Event schemas в `event-schemas/` | ✅ | 19 схем |
| Event types `{domain}.{action}` | ✅ | `lesson.started`, `attendance.marked`, `excuse.requested`, и т.д. |
| `proto3` | ✅ | `syntax = "proto3";` |
| Использование `optional` для nullable | ❌ | Нигде не используется (см. P2-1) |
| `google.protobuf.Timestamp` для времени | ❌ | Всё строки (см. P1-1) |
| JSON Schema draft | ✅ | draft/2020-12 везде |
| `additionalProperties: false` | ❌ | Нигде не установлено (см. P1-2) |
| enum типизация (не string) | ❌ | Всё string (см. P1-3) |
| Версионирование | ❌ | Нет механизма (см. P1-5) |
| `reserved` при breaking change | ✅ | `GroupResponse.field 3`, `LessonResponse.field 5` |

---

## Зависимости между проблемами

- **P0-1 (initial_password в gRPC)** зависит от и блокирует: 01-auth P0-2, 02-academic P0-1, 06-notification-bot P0-3. Решается одним крестом.
- **P0-2 (нет `otp.requested` схемы)** блокирует: 01-auth P0-4 (нужен канал для кода).
- **P1-1 (string вместо Timestamp)** — большой рефакторинг. Можно отложить на v0.1.0, но документировать как долг.
- **P1-3 (string вместо enum)** — связан с P0-5 из 03-schedule (дрейф week parity) и P1-4 (`first_week_type` неопределён). Единое решение.
- **P1-2 (additionalProperties)** — быстрый fix в 19 файлах + CI-шаг.

---

## Вопросы к владельцу проекта

1. ✅ **`initial_password` в gRPC-контракте**: когда удалим (как часть фикса P0-2 academic)? Нужен ли коммуникационный канал альтернативный — `setup_token`?
   → **ACCEPTED BY OWNER (2026-04-18)**: поле остаётся, альтернативный канал не нужен. См. `OWNER-ANSWERS.md` 08-Q1.
2. **Тайм-зоны**: все даты/время в UTC или в `Europe/Moscow`? Предлагаю явно зафиксировать в docs/architecture.md и адаптировать все сервисы.
3. **proto3 Timestamp vs string**: готовы ли мигрировать? Это breaking change для stub'ов.
4. **Enum vs string в .proto**: та же миграция.
5. **`additionalProperties: false`**: планировалось ли? Если отклоняется — обоснование (forward-compat?) — нужно явно.
6. **Версионирование**: какой предпочтительный механизм — `schema_version` field или namespace `v1/v2/v3`?
7. **`excuse.decision`**: где сейчас публикуется решение старосты по excuse? Предлагаю ввести в `event-schemas/` симметрично `late_checkin`.
8. **`otp.requested`**: согласованы ли поля `{ telegram_id, code, ttl_seconds }`? Нужны ли дополнительные (например `delivery_channel: telegram`)?
9. **Валидация схем в CI**: готовы ли принять `tools/validate-schemas.py`?
10. **`lesson_number: 1..8`**: бизнес-константа (не изменится?) или это текущее значение?

---

_Конец отчёта 08-shared-proto-events.md_

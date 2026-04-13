# Phase 59: Excuse Tickets Backend — Context

**Gathered:** 2026-04-14 (по результатам разведки в рамках сессии багфиксов)
**Status:** Ready for planning
**Source bug-report:** `.planning/bug-reports/BUG-008-student+pwa/report.md`
**Source spec:** `.planning/bug-reports/BUG-008-student+pwa/excuses-backend-spec.md` (актуальные правила автора отражены ниже)

<domain>
## Phase Boundary

Реализуем backend для тикетов о пропуске занятий — функционал, существование которого предусмотрено в нескольких местах системы, но сама реализация отсутствует.

**Что уже есть** (см. RESEARCH.md):
- `ExcuseType` enum: ILLNESS, SUMMONS, UNIVERSITY_ORDER, EXEMPTION, FREE_ATTENDANCE, OTHER.
- `ExcuseTicketStatus` enum (содержимое сверить).
- `notification-bot` слушает `excuse.requested` event из RabbitMQ и шлёт алерт старосте.
- `frontend/web-panel/.../student/excuses/` — UI создания тикета и список тикетов (но `submitExcuse`/`getExcuseTickets` помечены DEFERRED).

**Что добавляем в этой фазе:**
- Полный CRUD для excuse-тикетов в `attendance-service`.
- Event publisher → `excuse.requested` (бот уже слушает) и `excuse.decided`.
- Frontend: подключить уже существующие компоненты к рабочему API, добавить выбор причины.
- Headman UI для одобрения/отклонения тикетов.
- Каскад на `attendance` записи при одобрении (тикет одобрен → статус урока меняется на excused/free_attendance).

**Out of scope:**
- Файлы (вложения) — отдельный flow через Telegram, реализуется ПОЗЖЕ. В этой фазе тикет создаётся без файлов, только текст + причина.
- Late check-in (запросы поздней отметки) — отдельный аналогичный flow, не в этой фазе.
</domain>

<decisions>
## Implementation Decisions (зафиксированы пользователем)

### Основные сущности

- **D-01:** `ExcuseTicket` — MongoDB document в `attendance_db` (attendance-service), коллекция `excuse_tickets`. Без миграций (Mongo авто-создаёт).
- **D-02:** Поля документа:
  ```
  id              : ObjectId
  studentId       : Long  (FK users.id)
  groupId         : Long  (FK groups.id; снапшот на момент создания)
  studentName     : String  (снапшот ФИО студента)
  lessonIds       : List<Long>  (FK schedule lessons)
  excuseType      : ExcuseType  (lowercase в Mongo)
  comment         : String?  (≤ 1000 симв.)
  status          : ExcuseTicketStatus  (default: pending)
  decisionBy      : Long?  (староста, принявший решение)
  decisionComment : String?
  decisionAt      : Instant?
  createdAt       : Instant
  updatedAt       : Instant
  ```
- **D-03:** Файлы (вложения) — НЕ в этой фазе. Поле для них не добавляем сейчас, чтобы не было соблазна частичной реализации. Будущее место — отдельное поле `attachments: List<TelegramFile>` или отдельная коллекция.

### REST контракт (через API Gateway → /api/attendance/excuses)

- **D-04:** `POST /excuses` — STUDENT создаёт свой тикет.
- **D-05:** `GET /excuses/me` — STUDENT смотрит свои тикеты, сортировка по createdAt desc, постранично.
- **D-06:** `GET /excuses/group/{groupId}` — STUDENT+headman смотрит тикеты группы. Только если `is_headman=true` И `groupId == own.groupId`. Иначе 403.
- **D-07:** `PATCH /excuses/{id}/status` — STUDENT+headman одобряет/отклоняет. Запрос: `{ status: APPROVED|REJECTED, decisionComment?: string }`. Только для тикетов своей группы. Дополнительно: запрет одобрять/отклонять собственный тикет (нельзя быть и автором, и судьёй).
- **D-08:** `GET /excuses/{id}` — детали; STUDENT — только свой, headman — только своей группы.
- **D-09:** Все эндпоинты — `@RequireRole({STUDENT})` (потому что headman — это `STUDENT + is_headman`). Дополнительные проверки в сервисе.

### Валидация и бизнес-правила (учтены правки автора в excuses-backend-spec.md)

- **D-10:** STUDENT может создать тикет за **прошедшие, текущие или будущие** уроки своей группы (lessonIds должны принадлежать `user.groupId`). Это позволяет студенту заранее предупредить о пропуске (например, командировка).
- **D-11:** Один lessonId может фигурировать **только в одном активном тикете** (`pending` или `approved`) данного студента. `rejected` не блокирует новый.
- **D-12:** **Староста не подаёт тикет — он сам заполняет свои пропуски** (через обычный `markAttendance` flow). На уровне сервиса — если `request.userId == headman` и `is_headman=true` → backend возвращает 409 с подсказкой «Староста проставляет себе пропуски через журнал».
- **D-13:** Староста не может одобрять/отклонять **собственный** тикет (даже если по D-12 он бы как-то его создал — защита по логике). 409 с понятным сообщением.
- **D-14:** Сторонний просмотр (другие старосты, другие группы, не свой тикет) — 403.
- **D-15:** comment ≤ 1000 символов; lessonIds ≥ 1; excuseType обязателен.

### Каскад на attendance при одобрении

- **D-16:** При одобрении тикета (status: pending → approved):
  1. Внутренний flow: для каждого lessonId создать/обновить запись `AttendanceRecord` (студент=studentId, lessonId) со статусом
     - `EXCUSED` если `excuseType ∈ {ILLNESS, SUMMONS, UNIVERSITY_ORDER, EXEMPTION, OTHER}`
     - `FREE_ATTENDANCE` если `excuseType = FREE_ATTENDANCE`.
  2. Если запись уже есть — обновить статус.
  3. Транзакционно с обновлением тикета.
- **D-17:** При отклонении тикета (status: pending → rejected) — никаких изменений в attendance.
- **D-18:** Повторное одобрение/отклонение уже разрешённого тикета — 409 «Решение уже принято».

### Event contract

- **D-19:** При успешном создании — публикация в RabbitMQ:
  ```json
  {
    "type": "excuse.requested",
    "ticketId": "<mongo id>",
    "studentId": <long>,
    "studentName": "<lastName firstName>",
    "groupId": <long>,
    "lessonIds": [<long>, ...],
    "excuseType": "illness|summons|...",
    "comment": "<string|null>",
    "createdAt": "<ISO-8601>"
  }
  ```
  **Контракт-тест**: добавить тест что JSON-схема совпадает с тем, что ожидает `notification-bot/.../headman_alerts.py:53`.
- **D-20:** При смене статуса — `excuse.decided`:
  ```json
  {
    "type": "excuse.decided",
    "ticketId": "...",
    "studentId": <long>,
    "decisionBy": <long>,
    "status": "approved|rejected",
    "decisionComment": "...",
    "decidedAt": "..."
  }
  ```
  Бот должен уведомить студента — отдельный handler в `notification-bot` (добавить в этой фазе).

### Frontend интеграция

- **D-21:** `frontends/web-panel/.../student/excuses/excuse-form-dialog.component.ts` — добавить dropdown «Причина пропуска» (ExcuseType с русскими подписями: «Болезнь», «Повестка», «Приказ университета», «Освобождение», «Свободное посещение», «Другое»). Связать с `submitExcuse` (убрать DEFERRED).
- **D-22:** `frontends/web-panel/.../student/excuses/` — список тикетов — обновить чтобы дёргал `GET /excuses/me`.
- **D-23:** `frontends/web-panel/.../headman/excuses/` — компоненты-заглушки уже есть (Фаза 55). Заменить graceful-degradation на реальные вызовы:
  - `GET /excuses/group/{groupId}`,
  - `PATCH /excuses/{id}/status`.
- **D-24:** UI на странице headman: список pending тикетов сверху, approved/rejected ниже (с фильтром по статусу). Действия: «Одобрить» / «Отклонить» с обязательным комментарием для отклонения.

### gRPC и schedule-service

- **D-25:** Валидация lessonIds — backend проверяет что все принадлежат `user.groupId` через gRPC к schedule-service. Если метода нет — добавить `LessonsByIdsRequest/Response` в `proto/schedule.proto` (вернёт массив `{lessonId, groupId, subjectId}`).
- **D-26:** При создании тикета сохраняем `studentName` снапшотом (через gRPC `academic.GetUserById`). Не делаем поиск по имени в Mongo при выдаче — берём из снапшота.

### Notification bot

- **D-27:** Бот уже слушает `excuse.requested` (`headman_alerts.py:53`). Сверить контракт после реализации D-19.
- **D-28:** Добавить consumer для `excuse.decided` — уведомляет студента об одобрении/отклонении (русский текст с причиной из decisionComment).
- **D-29:** Тесты бота на оба event-handler.

### Claude's Discretion

- Точный алгоритм проверки overlap lessonId × активные тикеты (Mongo агрегация vs python-level loop).
- Конкретный текст русских ошибок 4xx/5xx.
- Структура pageable для `GET /excuses/me` (Spring Pageable стандартный).
- Использовать ли Spring Data Mongo Specifications или ручные queries.
- Дизайн UI headman: таблица vs карточки.

### Deferred Ideas (OUT OF SCOPE)

- **Файлы (вложения)** — через Telegram, отдельной фазой.
- **Late check-in tickets** — структурно похоже, отдельной фазой.
- **Excuse cancellation by student** (студент отзывает свой pending тикет) — добавить тикет в backlog.
- **Email-нотификация** студенту/старосте — отдельно, если потребуется.
- **Bulk-approve** для старосты — если будет много тикетов.

</decisions>

## Risk Register

| Риск | Вероятность | Митигация |
|---|---|---|
| Контракт `excuse.requested` не совпадает с тем, что ждёт бот → бот молча игнорирует | Высокая | Контракт-тест (D-19): JSON-схема + интеграционный тест с реальной RabbitMQ или mock |
| Каскад на attendance ломает идемпотентность (одобрили дважды → 2 записи) | Средняя | Транзакция + проверка существующей записи + D-18 (запрет повторного решения) |
| schedule-service не имеет gRPC `LessonsByIds` → нужно расширять proto | Высокая | D-25 — план добавить метод в этой же фазе |
| MongoDB collection auto-create не настроен в integration-тестах | Средняя | Использовать Testcontainers Mongo (уже есть в attendance-service) |
| Frontend пытается дёрнуть API до выкатки backend | Низкая | Явная проверка через feature-flag или просто выкатить backend первым |
| RabbitMQ event lost при сбое → бот не узнает | Низкая | RabbitMQ persistent + acknowledge; если бот не отвечает — message в DLX (если настроено) |

## Connections to other phases

- **Зависит от**: Фаза B багфиксов (нет — независимо).
- **Конфликтует с**: ничем.
- **Может выполняться параллельно с Фазой 58** (admin bug-006).
- **GSD-state**: рекомендуется `/gsd-discuss-phase --power` для генерации вопросов про каскад на attendance и retry-стратегию для RabbitMQ.

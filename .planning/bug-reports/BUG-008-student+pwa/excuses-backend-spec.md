# Excuses Backend — спецификация для GSD-фазы

**Контекст:** BUG-008 (тикет о пропуске у студента). При разведке выяснилось,
что **backend для тикетов о пропуске не реализован**, хотя на стороне
frontend (`web-panel/student/excuses`) и notification-bot частично
существует ожидание этого функционала. Эта спецификация — задел для
отдельной GSD-фазы.

Этот документ создан в рамках Фазы C сессии багфиксов 2026-04-13/14 и
**не сопровождается изменениями в коде**.

## Что уже есть

| Артефакт | Путь | Состояние |
|---|---|---|
| Enum причины пропуска | `attendance-api-contract/.../enums/ExcuseType.java` | ✅ полностью: `ILLNESS, SUMMONS, UNIVERSITY_ORDER, EXEMPTION, FREE_ATTENDANCE, OTHER` |
| Enum статуса тикета | `attendance-api-contract/.../enums/ExcuseTicketStatus.java` | ✅ существует (читать содержимое) |
| Frontend форма | `web-panel/.../student/excuses/excuse-form-dialog.component.ts` | ⚠ есть, но без поля «причина», `submitExcuse` дёргает несуществующий REST |
| Frontend сервис | `web-panel/.../student/shared/student-api.service.ts` (`submitExcuse`, `getExcuseTickets`) | ⚠ методы есть, помечены DEFERRED — реальный 404 от backend |
| Bot consumer | `notification-bot/bot/consumers/event_dispatcher.py` | ✅ слушает `excuse.requested` |
| Bot handler | `notification-bot/bot/notifications/headman_alerts.py` | ✅ шлёт алерт старосте при `excuse.requested` |
| Тесты бота | `notification-bot/tests/test_headman_alerts.py` | ✅ покрывают алерты |

## Что отсутствует

В `services/attendance-service/`:
- Entity `ExcuseTicket` (MongoDB document)
- Repository `ExcuseTicketRepository`
- DTO: `CreateExcuseRequest`, `ExcuseTicketResponse`, `UpdateExcuseStatusRequest`
- API контракт `ExcuseApi` (`*-api-contract`)
- Service `ExcuseService`
- Controller `ExcuseController`
- Event publisher: публикация `excuse.requested` в RabbitMQ при создании тикета

## Целевой API

Все пути проходят через API Gateway (`/api/attendance/excuses`).

| Метод | Путь | Роль | Назначение |
|---|---|---|---|
| `POST` | `/excuses` | STUDENT | Создать тикет (свой) |
| `GET` | `/excuses/me` | STUDENT | Свои тикеты, постранично, фильтр по статусу |
| `GET` | `/excuses/group/{groupId}` | STUDENT+headman | Тикеты своей группы (только если `is_headman=true` И `groupId == own`) |
| `PATCH` | `/excuses/{id}/status` | STUDENT+headman | Одобрить/отклонить (`status` + опциональный `decisionComment`) |
| `GET` | `/excuses/{id}` | STUDENT/HEADMAN | Получить тикет; STUDENT — только свой, headman — только своей группы |

## Модель данных

`ExcuseTicket` (MongoDB collection `excuse_tickets`):

```
id              : String (ObjectId)
studentId       : Long  (FK users.id, не enforced на уровне Mongo)
groupId         : Long  (FK groups.id; снапшот на момент создания)
lessonIds       : List<Long>  (FK schedule lessons; обычно 1+ из одной группы)
excuseType      : ExcuseType  (enum, lowercase в БД)
comment         : String?  (свободный текст до 1000 симв.)
status          : ExcuseTicketStatus  (enum)
decisionBy      : Long?  (FK users.id — староста, который принял решение)
decisionComment : String?  (комментарий старосты)
decisionAt      : OffsetDateTime?
createdAt       : OffsetDateTime
updatedAt       : OffsetDateTime
```

**Файлы (вложения)** в первой версии **не реализуем**.
По CLAUDE.md «файлы пересылаются старосте через Telegram (не хранятся в системе)» — это отдельный flow через `notification-bot` с использованием Telegram file_id. Запланировать как отдельную подзадачу после первой версии.

## Event contract

При успешном создании тикета backend публикует в RabbitMQ:

```json
{
  "type": "excuse.requested",
  "ticketId": "<mongo id>",
  "studentId": <long>,
  "studentName": "<lastName firstName>",
  "groupId": <long>,
  "lessonIds": [<long>, ...],
  "excuseType": "illness|summons|university_order|exemption|free_attendance|other",
  "comment": "<string|null>",
  "createdAt": "<ISO-8601>"
}
```

Бот уже умеет это обрабатывать (`headman_alerts.py:53`). Нужно сверить
имена полей с тем, что бот ожидает (в спецификации фазы — отдельный
шаг «контракт-тест»).

При смене статуса тикета — `excuse.decided` (по аналогии). Имя события
согласовать при планировании.

## Бизнес-правила

1. STUDENT может создать тикет **только за прошедшие текущие или покрыть будущие** уроки своей группы (lessonIds должны принадлежать `user.groupId`).
2. Один lessonId может фигурировать **только в одном активном тикете** студента (защита от дубликатов; `pending`/`approved` блокируют новый, `rejected` — нет).
3. Староста не подаёт тикет он может сам заполнить свои пропуски.
4. После одобрения тикета — соответствующие записи в `attendance` сервисе должны быть переведены в `excused` или `free_attendance` (по типу). Это нужно сделать через **gRPC-вызов** или **событие** в attendance из самого attendance-сервиса (внутренний flow). Уточнить при планировании.
5. Сторонний просмотр (другие старосты, другие группы) — **запрещён** (HTTP 403).

## Зависимости и риски

- **MongoDB**: коллекция авто-создаётся, миграции не нужны.
- **gRPC**: вызывает `academic.GetUserById` для получения `displayName` (есть, см. `proto/academic.proto`). Не вызываем — сохраняем `studentName` снапшотом.
- **Schedule service**: чтобы валидировать lessonIds — нужен gRPC-метод вроде `LessonInfoBatch`. Если нет, придётся либо добавлять, либо доверять frontend и валидировать минимально (тип лекции/групповая принадлежность).
- **Cascade на attendance статус** — самый риск. Если в первой версии не делать, тикет одобрённый не повлияет на журнал — сделать TODO с явным баннером в UI старосте «не забудьте проставить уважительную в журнале вручную».

## Acceptance criteria (для GSD-планирования)

- [ ] STUDENT в web-panel создаёт тикет с указанием причины (dropdown ExcuseType) и опциональным комментарием → статус `pending`, событие в RabbitMQ.
- [ ] Староста видит тикет в `/headman/excuses` (отдельная страница, **уже не реализована** — частично затронуть в этой же фазе).
- [ ] Староста принимает или отклоняет тикет с комментарием → статус меняется, событие `excuse.decided` (название уточнить).
- [ ] Студент видит итоговый статус и комментарий старосты.
- [ ] Бот старосты получает алерт `excuse.requested` (уже работает) — добавить тест, что данные совпадают.
- [ ] Запреты: STUDENT не видит чужие тикеты, headman не видит чужую группу.
- [ ] Покрытие тестами: контракт API, бизнес-правила (5 пунктов выше), security (роли).

## Связанные баг-репорты

- `BUG-008` (родитель) — UI-проблемы у студента и старосты, поле «причина пропуска».
- В рамках Фазы C сделано: PWA-фикс, code-review student-роутов, добавление поля `excuseType` в форму (frontend будет ждать backend этой фазы).

## Что делать дальше

В отдельной сессии:
```
/gsd-discuss-phase  # выяснить open-вопросы, особенно cascade на attendance
/gsd-plan-phase     # детальный PLAN.md
/gsd-execute-phase  # реализация по плану
```

Эта спецификация — стартовая точка для discuss-phase.

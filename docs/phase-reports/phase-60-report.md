# Phase 60 Report: Headman Schedule Management

**Завершена:** 2026-04-14
**Milestone:** v9.0 Frontend Unification — Single Login & Role-Based Web Clients
**Requirements закрыты:** AC-01..AC-08, AC-10, AC-11 полностью; AC-09 отложен на 60-09 (read-path merge)
**Контекст фазы:** `.planning/phases/60-headman-schedule-management/60-CONTEXT.md` (D-00..D-23)

## Цель

Объединены две тесно связанные задачи, всплывшие при разборе багов старосты:

1. **#4-Full — Починка модели «предмет группы + его преподаватели»**: `CreateSubjectRequest` игнорировал `teacherId`, `Subject.type` не задавался из UI, отсутствовала привязка subject→group, `TeacherSubjectGroup` не создавалась.
2. **#5 — UI управления расписанием для старосты**: полностью отсутствовал, включая единоразовые правки (отмена конкретной пары + вставка разовой пары на конкретную дату).

Обе задачи опирались на одну модель (`Subject → TeacherSubjectGroup → ScheduleItem`), поэтому реализованы в единой фазе из 8 подпланов (4 backend волны + 2 frontend + финальная верификация).

## Реализовано (AC coverage)

| AC | Описание | Покрытие | Статус |
|----|----------|----------|--------|
| AC-01 | Староста создаёт предмет с type + списком преподавателей; атомарная запись subjects + N teacher_subject_groups | `SubjectServiceIT.createSubject_withTwoTeachers_atomicInsert` + `subject-dialog.component.spec.ts` | ✅ |
| AC-02 | `subjects.group_id NOT NULL` миграция (V12); cross-group операции заблокированы | `SubjectSchemaIT` (3 теста) + `SubjectServiceIT.listSubjects_filteredByGroup` | ✅ |
| AC-03 | Endpoints `POST/DELETE /subjects/{id}/teachers/{teacherId}` для inline-управления | `SubjectServiceIT.addTeacher_and_removeTeacher` (201→409→204) + API service spec | ✅ |
| AC-04 | `ScheduleItem.teacherId` удалён end-to-end (entity, DTO, event, gRPC proto `reserved 5`) | `ScheduleItemEntityTest` (reflection), `ScheduleItemSecurityTest` (4 кейса RBAC) | ✅ |
| AC-05 | Таблица `schedule_one_off_lessons` (Flyway V4) с UNIQUE(group_id, date, lesson_number) и semester_id NOT NULL | `OneOffLessonSchemaIT` (3 теста) | ✅ |
| AC-06 | REST CRUD `/api/schedule/one-off-lessons` (HEADMAN) с D-09 template conflict, D-22 past-date delete, D-23 semester auto-resolve | `OneOffLessonControllerIT` (6 тестов) | ✅ |
| AC-07 | События `lesson.one_off.created/cancelled` (JSON Schema + DomainEvent + AFTER_COMMIT) | `OneOffLessonEventPublisherIT` (2 теста) | ✅ |
| AC-08 | Cascade delete attendance-docs по `lesson.one_off.cancelled` с идемпотентностью | `OneOffLessonCancelledConsumerIT` (3 теста: happy / idempotent / isolation) | ✅ |
| AC-09 | Read-path merge one-off + template lessons в attendance-service | **Отложен на 60-09** — требует нового proto-метода `getOneOffLessonsOnDate` в schedule-service | ⏳ Deferred |
| AC-10 | notification-bot handlers шлют push всем студентам группы (D-18, включая старосту) | `test_one_off_created_handler.py` + `test_one_off_cancelled_handler.py` (8 тестов) | ✅ |
| AC-11 | Angular UI: `/headman/subjects` multi-select преподавателей + `/headman/schedule` матрица 5×8 с диалогами | `subject-dialog.component.spec.ts` (8) + `headman-schedule.component.spec.ts` (6) + 2 dialog specs (10) | ✅ |

## Результаты по планам

### Plan 60-01: Subject Model Fix — group_id + N teachers atomicity (Wave 1)

- **Flyway V12** (`subjects_group_id.sql`): транзакционный DELETE тестовых данных + `ADD COLUMN group_id BIGINT NOT NULL` + FK `fk_subjects_group` + `idx_subjects_group`.
- `Subject` entity получил поле `groupId`; `SubjectRepository` — методы `findByGroupId`, `existsByIdAndGroupId`.
- `CreateSubjectRequest` теперь содержит `@NotNull List<Long> teacherIds`; `SubjectResponse` — `groupId + teacherIds`.
- `SubjectApi` расширен endpoint'ами `POST/DELETE /subjects/{id}/teachers/{teacherId}` (201/204/403/404/409).
- `SubjectService.createSubject` `@Transactional`: lookup активного семестра → save Subject с `groupId = requestContext.getGroupId()` → `tsgRepository.saveAll(...)` для N назначений. При любом сбое — rollback.
- `SubjectService.listSubjects`: ADMIN — все, HEADMAN — только `findByGroupId(self.groupId)`.
- Тесты: `SubjectSchemaIT` (3) + `SubjectServiceIT` (4, включая `rollbackOnTeacherSaveFail` с FK violation). Полный `:academic-app:test` — 165/165 зелёные.

### Plan 60-02: Remove ScheduleItem.teacherId (D-16) (Wave 1)

- **Flyway V3** (`drop_teacher_id.sql`): `ALTER TABLE schedule_items DROP COLUMN IF EXISTS teacher_id`.
- `ScheduleItem` entity, `CreateScheduleItemRequest`, `UpdateScheduleItemRequest`, `ScheduleItemResponse`, `LessonResponse`, `LessonStartedEvent.Payload`, `event-schemas/lesson.started.json` — поле удалено end-to-end.
- `proto/schedule.proto LessonResponse`: `int64 teacher_id = 5;` → `reserved 5; reserved "teacher_id";` (wire-forward compat).
- Каскадная чистка: `ScheduleGrpcServiceImpl`, `LessonAssembler`, `LessonStatusTransitionJob`, 8 тестовых файлов.
- `ScheduleItemEntityTest` (reflection — поле отсутствует) + `ScheduleItemSecurityTest` (4 кейса: headman правильной группы / чужой группы → 403 / не-headman → 403 / ADMIN bypass).
- Downstream check: `:attendance-app:compileJava + compileTestJava` — BUILD SUCCESSFUL без изменений (teacher-journal JOIN путь не читает `teacher_id` из lessons).

### Plan 60-03: schedule_one_off_lessons model + HEADMAN CRUD (Wave 2)

- **Flyway V4** (`one_off_lessons.sql`): `CREATE TABLE schedule_one_off_lessons` с `id, group_id, subject_id, semester_id NOT NULL, date, lesson_number (CHECK 1..8), classroom, created_by, created_at`, `UNIQUE(group_id, date, lesson_number)`, `idx_one_off_group_date`. Без `teacher_id` (D-04/D-16).
- `OneOffLesson` entity + `OneOffLessonRepository` (derived queries `findByGroupIdAndDateBetween`, `existsByGroupIdAndDateAndLessonNumber`).
- `ScheduleItemRepository.existsActiveTemplateSlot` — **native SQL** с `CAST(:weekType AS week_type)` (PG custom enum требует явного каста; см. key decision V5 convention).
- Contract (без Lombok): `CreateOneOffLessonRequest` record, `OneOffLessonResponse` класс с HATEOAS, `OneOffLessonApi` interface.
- `OneOffLessonService` `@Transactional`: двухфазный RBAC (`requestContext.isHeadman()` + `academicGrpcClient.isHeadman(userId, groupId)`), `getActiveSemester` с проверкой `date ∈ [date_from..date_to]` (иначе 409), `computeWeekTypeForDate` (идентичен `LessonGenerationService`), conflict check по active template slot.
- `ConflictException` → 409 через `GlobalExceptionHandler`.
- Tests: `OneOffLessonSchemaIT` (3) + `OneOffLessonControllerIT` (6 кейсов: valid / template conflict / duplicate / past-date-delete / foreign-group 403 / list-by-group).

### Plan 60-04: One-off Lesson Events + Push (Wave 3)

- JSON Schemas: `event-schemas/lesson.one_off.created.json` + `lesson.one_off.cancelled.json` с envelope `{event_type, event_id, occurred_at, payload}`.
- Java events: `OneOffLessonCreatedEvent` + `OneOffLessonCancelledEvent` extends `DomainEvent` (AFTER_COMMIT → RabbitMQ fanout).
- `OneOffLessonService` публикует: при create — после save; при delete — snapshot полей → delete → publish (не триггерится на rollback).
- notification-bot handlers (`lesson_one_off_created.py`, `lesson_one_off_cancelled.py`): payload guard, `academic_client.get_subjects_by_ids` с fallback на `"Пара"`, `TelegramSendQueue` с lazy `coroutine_factory`, отправка всем студентам группы (D-18 — староста включён).
- `EventDispatcher` регистрация обоих типов событий.
- Tests: `OneOffLessonEventPublisherIT` (2) + pytest `test_one_off_created_handler.py` (4) + `test_one_off_cancelled_handler.py` (4). Notification-bot — 136/136.

### Plan 60-05: Attendance Service — One-off Lesson Cascade Delete (Wave 3)

- `EventConsumer.handleOneOffLessonCancelled`: extract `{group_id, date, lesson_number}` → делегирует `LessonEventService`.
- `LessonEventService.processOneOffLessonCancelled(groupId, date, lessonNumber)`: `mongoTemplate.remove(Query.query(Criteria.where("group_id").is(...).and("lesson_date").is(...).and("lesson_number").is(...)), AttendanceDocument.class)`. Идемпотентность по natural key — повторная доставка → `deletedCount=0`, без exception.
- `OneOffLessonCancelledConsumerIT` (3 кейса: happy / idempotent / isolation — другая группа/дата/слот не затронуты).
- `LessonGenerationMergeTest` — placeholder-тест, пинящий текущий shape `LessonResponse` от `getLessonById`, с Javadoc, указывающим на AC-09 follow-up в 60-09.

### Plan 60-06: Headman Subjects Dialog — Multi-select Teachers + Type Field (Wave 4)

- `subject-dialog.component.ts`: FormGroup с `name` (required, maxLength 120), `type` (required, LECTURE/PRACTICE/LAB), `teacherIds: number[]` (nonNullable, default `[]`).
- `<mat-select multiple>` для teacherIds с сортировкой по lastName (ru-collator); `<mat-select>` для type.
- Edit mode предзаполняет текущие значения; submit body: `{name, type, teacherIds}`.
- `HeadmanApiService.createSubject/updateSubject` — новая сигнатура; `addTeacherToSubject/removeTeacherFromSubject` для inline-CRUD.
- Tests: `subject-dialog.component.spec.ts` (8) + `headman-api.service.spec.ts` (13, +4). Полный vitest — 369/369 (было 365, +4 новых).

### Plan 60-07: Headman Schedule Page — Matrix + Dialogs (Wave 4)

- `HeadmanScheduleComponent` (`/headman/schedule`, headmanGuard, lazy-load): матрица 5 дней × 8 слотов через CSS grid. `groupId` — из JWT claim через `AuthService.currentUser()`. Активный семестр через `listSemesters()` + client-side filter.
- Empty cell → диалог create-mode с pre-filled `dayOfWeek/lessonNumber`; filled cell → edit-mode со всеми полями. Toolbar: кнопка «Добавить разовую пару». Бейджи WeekType (ALL/ODD/EVEN). Без drag-and-drop (D-14).
- `ScheduleSlotDialogComponent`: subjectId (required) + room (64) + weekType. Препод не выбирается (D-16). Error mapping 403/409.
- `OneOffDialogComponent`: date (MatDatepicker, любая дата — D-08), lessonNumber (1..8), subjectId из каталога группы (D-03), classroom. ISO-formatting даты. 409 → «Слот занят. Сначала отмените шаблонную пару...».
- `HeadmanApiService` расширен 9 новыми методами: `listSemesters`, `getGroupScheduleItems`, `createScheduleItem`, `updateScheduleItem`, `deleteScheduleItem`, `createOneOffLesson`, `deleteOneOffLesson`, `getOneOffLessons`, `cancelLesson`.
- Sidebar: пункт «Расписание» (ph-calendar-blank).
- Tests: +25 (3 component spec + 2 dialog spec + 9 api methods). Полный vitest — 394/394 (было 369).

### Plan 60-08: Final Verification + Report (Wave 5, эта фаза)

- Полный `./gradlew.bat build` — BUILD SUCCESSFUL за 1m 7s (все сервисы: auth/academic/schedule/attendance/notification/gateway).
- Phase 60 focused test suites: academic `subject.*`, schedule `oneoff.*`, attendance `OneOffLessonCancelledConsumerIT` — все зелёные.
- `:notification-bot pytest` — **136 passed**.
- `:web-panel vitest` — **394 passed** (53 файла).
- Grep `teacherId|teacher_id` в `schedule-service/schedule-app/src/main/java/` — только D-16 комментарии, production-поля отсутствуют.

## Изменения по сервисам

### academic-service
- **Migration:** V12 (`subjects_group_id.sql`) — `group_id NOT NULL` + FK.
- **Entity:** `Subject.groupId` (immutable after creation).
- **Contract:** `CreateSubjectRequest.teacherIds`, `SubjectResponse.{groupId, teacherIds}`, `SubjectApi` +2 endpoint.
- **Service:** atomic create (subject + N TSG), group-filtered list, teacher CRUD.
- **Tests:** +7 новых (3 schema + 4 service), total 165/165.

### schedule-service
- **Migrations:** V3 (DROP teacher_id), V4 (schedule_one_off_lessons).
- **Entity:** `ScheduleItem` — teacherId removed; `OneOffLesson` — new.
- **Contract:** все schedule/lesson DTO без teacherId; новый `OneOffLessonApi` с 3 endpoint'ами.
- **Service:** `OneOffLessonService` с RBAC/conflict check/semester resolve; `ScheduleItemService.requireHeadmanForGroup` с unit-coverage.
- **Events:** `OneOffLessonCreatedEvent/CancelledEvent` (AFTER_COMMIT).
- **gRPC:** `proto/schedule.proto LessonResponse.teacher_id` → `reserved 5`.
- **Tests:** +15 (2 entity/security + 3 schema + 6 controller + 2 event IT + 2 obsolete updates).

### attendance-service
- **EventConsumer:** case `lesson.one_off.cancelled`.
- **Service:** `LessonEventService.processOneOffLessonCancelled` — MongoDB natural-key cascade delete, idempotent.
- **Tests:** +4 (3 consumer IT + 1 placeholder). No regressions.

### notification-bot
- **Handlers:** `lesson_one_off_created.py`, `lesson_one_off_cancelled.py`.
- **Dispatcher:** регистрация двух новых event types.
- **Tests:** +8 (4+4), total 136/136.

### web-panel (Angular)
- **HeadmanApiService:** +11 методов (subject teachers CRUD + schedule items + one-off + lesson cancel + semesters).
- **Components:**
  - `subject-dialog` — multi-select teachers + type field.
  - `headman-schedule` — матрица 5×8 (standalone, OnPush + signals).
  - `schedule-slot-dialog`, `one-off-dialog` — standalone dialog components.
- **Routing:** `/headman/schedule` с headmanGuard + lazy-load.
- **Sidebar:** пункт «Расписание».
- **Tests:** +29 (8 subject-dialog + 6 schedule + 5+5 dialogs + 9 api service). Total 394/394.

## Новые Flyway миграции

| Service | Migration | Описание |
|---------|-----------|----------|
| academic | `V12__subjects_group_id.sql` | DELETE test data + `ADD COLUMN group_id BIGINT NOT NULL` + FK `fk_subjects_group` + `idx_subjects_group` |
| schedule | `V3__drop_teacher_id.sql` | `ALTER TABLE schedule_items DROP COLUMN IF EXISTS teacher_id` (idempotent) |
| schedule | `V4__one_off_lessons.sql` | `CREATE TABLE schedule_one_off_lessons` + UNIQUE(group_id, date, lesson_number) + `idx_one_off_group_date` |

## Новые API endpoints

| Метод | Путь | Роль | Комментарий |
|-------|------|------|-------------|
| POST | `/api/academic/subjects/{id}/teachers/{teacherId}` | HEADMAN/ADMIN | 201/409 |
| DELETE | `/api/academic/subjects/{id}/teachers/{teacherId}` | HEADMAN/ADMIN | 204/404 |
| POST | `/api/schedule/one-off-lessons` | HEADMAN/ADMIN | 201/400/403/409/503 |
| GET | `/api/schedule/one-off-lessons?groupId=&dateFrom=&dateTo=` | любая аутентиф. | 200 |
| DELETE | `/api/schedule/one-off-lessons/{id}` | HEADMAN/ADMIN | 204/403/404, любая дата (D-22) |

Также уточнены/обновлены существующие: `POST/PUT /api/academic/subjects` (принимают `type + teacherIds[]`), `POST/PUT/DELETE /api/schedule/items` (без `teacherId`).

## Новые gRPC (нет в этой фазе)

Новый proto-метод `GetOneOffLessonsOnDate` (требуется для AC-09) **отложен на 60-09**, чтобы не выходить за scope attendance-service в плане 60-05.

## События RabbitMQ

Новые событийные типы (fanout `rut-uit.events`):
- `lesson.one_off.created` — публикует `OneOffLessonService.createOneOffLesson` после коммита. Consumers: notification-bot (push всем студентам группы).
- `lesson.one_off.cancelled` — публикует `OneOffLessonService.deleteOneOffLesson` после коммита. Consumers: notification-bot (push) + attendance-service (cascade delete attendance docs).

Payload schemas: `event-schemas/lesson.one_off.created.json`, `event-schemas/lesson.one_off.cancelled.json`.

## Тесты

| Компонент | Новых | Итого | Команда |
|---|---|---|---|
| academic-service | +7 | 165 | `:academic-app:test` |
| schedule-service | +15 | ~90 | `:schedule-app:test` |
| attendance-service | +4 | ~100 | `:attendance-app:test` |
| notification-bot pytest | +8 | 136 | `cd services/notification-bot && pytest -q` |
| web-panel vitest | +29 | 394 | `cd frontends/web-panel && npx vitest run` |

### Verification commands

| Команда | Результат |
|---|---|
| `./gradlew.bat build -x javadoc` | **BUILD SUCCESSFUL** in 1m 7s (75 tasks: 18 executed, 57 up-to-date) |
| `./gradlew :academic-app:test --tests "...subject.*"` | PASSED |
| `./gradlew :schedule-app:test --tests "...oneoff.*"` | PASSED |
| `./gradlew :attendance-app:test --tests "*OneOffLessonCancelledConsumerIT*"` | PASSED |
| `cd services/notification-bot && py -m pytest -q` | **136 passed in 13.52s** |
| `cd frontends/web-panel && npx vitest run` | **394 passed** (53 файла) in 22.55s |
| `grep -rn "setTeacherId" services/schedule-service/schedule-app/src/main/java/` | 0 matches |

## Ключевые архитектурные решения

- **Subject.groupId immutable after creation (D-02):** `updateSubject` rejects cross-group tampering. Group scoping enforcement через `RequestContext.getGroupId()`.
- **ScheduleItem.teacherId removed end-to-end (D-16):** proto field `reserved 5` — wire-forward compat защищает старых клиентов. Preodavatel видит журнал через JOIN `ScheduleItem × TeacherSubjectGroup WHERE TSG.teacher_id = :me`.
- **`existsActiveTemplateSlot`: native SQL с `CAST(:weekType AS week_type)`:** JPA varchar несовместим с PostgreSQL custom enum — задокументировано как V5 convention в проекте.
- **One-off lesson semester_id резолвится по активному семестру:** дата вне `[date_from..date_to]` → 409 ConflictException (MVP D-23, gRPC `getSemesterByDate` отложен).
- **One-off lesson events published via DomainEvent + AFTER_COMMIT:** rollback не триггерит publish. Snapshot полей перед `delete()` для cancelled event.
- **Notification-bot push entire group (D-18, headman included):** единый канал рассылки, без фильтра старосты.
- **Attendance-service cascade delete — natural key `(group_id, lesson_date, lesson_number)`:** idempotency через MongoDB `remove` → `deletedCount=0` без exception.
- **Angular: `groupId` из `AuthService.currentUser().groupId` (JWT claim):** пользователь не может передать чужой groupId; backend дополнительно валидирует через `requireHeadmanForGroup`.
- **Standalone Angular components + `inject()` DI + OnPush + signals:** единый паттерн Phase 60 frontends.

## Known limitations

### AC-09 — Read-path merge one-off + template lessons (deferred to 60-09)

**Что не сделано:** Attendance-service `ScheduleGrpcClient.getLessonsByGroup / getLessonById / getActiveLesson` читают только `ScheduleItem + Lesson` на стороне schedule-service — они НЕ включают `schedule_one_off_lessons`. Как следствие, разовая пара не отражается в gRPC-ответах attendance-service-у до момента материализации в `Lesson` таблицу.

**Почему отложено:** Требует нового proto-метода в `schedule.proto` (например `GetOneOffLessonsOnDate(groupId, date)` или расширение существующего `GetLessonsByGroup` с merge на серверной стороне). Это изменение контракта между сервисами, выходящее за scope плана 60-05 (attendance-service only).

**Митигирующий write-path (работает end-to-end):**
- Create one-off → `lesson.one_off.created` → push студентам (AC-10 ✅).
- Delete one-off → `lesson.one_off.cancelled` → notify + cascade delete attendance docs (AC-08 ✅).
- Closing of one-off lesson (trigger auto-absent): для полной работы на чтение требует материализации one-off в `Lesson` таблицу через LessonGenerationService — часть 60-09 scope.

**Deferred к 60-09.** Задача зафиксирована в:
- `.planning/phases/60-headman-schedule-management/60-05-SUMMARY.md` (секция Known Limitations).
- `LessonGenerationMergeTest.java` placeholder + Javadoc pin.
- Этот отчёт.

### Manual UI UAT pending

Автоматические тесты (vitest + pytest + IT) покрывают структуру, API payload, error handling, роутинг, sidebar. Визуальная проверка (Material Design-соответствие, matrix rendering, snackBar messages, клик-flow `/headman/schedule` и `/headman/subjects`) требует запущенного stack'а и должна быть пройдена перед релизом v9.0. Перенесено в отдельный UAT-чеклист на finalization.

## Файлы артефактов

- Plans: `.planning/phases/60-headman-schedule-management/60-{01..08}-PLAN.md`
- Summaries: `.planning/phases/60-headman-schedule-management/60-{01..08}-SUMMARY.md`
- Context: `.planning/phases/60-headman-schedule-management/60-CONTEXT.md` (D-00..D-23)
- Research: `.planning/phases/60-headman-schedule-management/60-RESEARCH.md`
- Validation: `.planning/phases/60-headman-schedule-management/60-VALIDATION.md`
- Migrations: `services/academic-service/academic-app/src/main/resources/db/migration/V12__subjects_group_id.sql`, `services/schedule-service/schedule-app/src/main/resources/db/migration/V3__drop_teacher_id.sql`, `V4__one_off_lessons.sql`
- Event schemas: `event-schemas/lesson.one_off.created.json`, `lesson.one_off.cancelled.json`
- Proto changes: `proto/schedule.proto` (`LessonResponse.reserved 5`)

## Next steps

1. **UAT для AC-11** — прогнать на deployed stack: логин staросты → `/headman/subjects` create subject with multi-teachers → `/headman/schedule` матрица → click на ячейку (edit), на пустую (create), на «Добавить разовую пару» → проверить push в Telegram.
2. **Plan 60-09 (follow-up)** — AC-09 read-path merge: новый proto-method `GetOneOffLessonsOnDate` в schedule-service + обновление `LessonGenerationService` на материализацию one-off в `Lesson` таблицу + расширение `ScheduleGrpcClient` в attendance-service.
3. **Phase 61 / future:** drag-and-drop на `/headman/schedule` (D-14 out-of-scope), unified «Перенести» операция (D-04 out-of-scope), права `schedule_manage` для помощника старосты (D-12 out-of-scope).
4. **Housekeeping:** консолидация `subject-dialog` UI паттерна в shared components (если повторится в teacher-панели).

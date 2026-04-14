---
phase: 60-headman-schedule-management
plan: 07
subsystem: web-panel
tags: [frontend, angular, material, headman, schedule, matrix, dialogs, standalone]
dependency_graph:
  requires:
    - "60-03 (schedule_one_off_lessons REST endpoints)"
    - "60-06 (headman-api.service patterns)"
  provides:
    - "/headman/schedule route (lazy-loaded, headmanGuard)"
    - "HeadmanScheduleComponent — матрица 5×8 дни×слоты"
    - "ScheduleSlotDialogComponent — редактирование шаблонного слота (subject + room + weekType, D-13/D-16)"
    - "OneOffDialogComponent — создание разовой пары (date + lessonNumber + subject + classroom, D-05/D-08/D-09)"
    - "HeadmanApiService методы: getGroupScheduleItems / createScheduleItem / updateScheduleItem / deleteScheduleItem / createOneOffLesson / deleteOneOffLesson / getOneOffLessons / cancelLesson / listSemesters"
    - "Sidebar-пункт «Расписание» в разделе Старостат"
  affects: []
tech_stack:
  added: []
  patterns:
    - "standalone Angular component + inject() DI"
    - "ChangeDetectionStrategy.OnPush + signal() state"
    - "MatDialog → ref.afterClosed().subscribe(reload)"
    - "computed grid layout (CSS grid 60px + repeat(5, 1fr))"
    - "JWT-claim derived groupId через AuthService.currentUser() (T-60-01)"
requirements: [AC-11]
key_files:
  created:
    - frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.ts
    - frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.spec.ts
    - frontends/web-panel/src/app/features/headman/schedule/schedule-slot-dialog.component.ts
    - frontends/web-panel/src/app/features/headman/schedule/schedule-slot-dialog.component.spec.ts
    - frontends/web-panel/src/app/features/headman/schedule/one-off-dialog.component.ts
    - frontends/web-panel/src/app/features/headman/schedule/one-off-dialog.component.spec.ts
  modified:
    - frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts
    - frontends/web-panel/src/app/features/headman/shared/headman-api.service.spec.ts
    - frontends/web-panel/src/app/app.routes.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
decisions:
  - "ScheduleItemApi endpoint — реальный путь /api/schedule/items (не /schedule-items как в плане); Rule 1 — deviation от текста плана к фактическому контракту"
  - "ScheduleSlotDialog не импортирует MatDialogModule (только открывает dialogs, не содержит директив) — это позволяет TestBed override MatDialog provider без конфликта с модульным провайдером"
  - "defaultSlotTimes прибит в dialog: standard 1.5h сетка 09:00 → 22:30 (сервер валидирует; при редактировании время берётся из item.startTime/endTime)"
  - "listSemesters → filter(s.active===true) в component; gRPC getActiveSemester на фронте отсутствует, поэтому делаем client-side filter"
metrics:
  duration_min: ~20
  completed: 2026-04-14
  tests_total: 394
  tests_added: 25
---

# Phase 60 Plan 07: Headman Schedule Page — Matrix + Dialogs

Реализована Angular-страница `/headman/schedule` для AC-11: матрица дни×слоты с текущим шаблоном активного семестра группы старосты. Три standalone компонента, два диалога, маршрут через headmanGuard, интеграция с уже существующими бэкенд-эндпоинтами (60-02 `/api/schedule/items`, 60-03 `/api/schedule/one-off-lessons`, D-20 `/api/schedule/lessons/{id}/cancel`).

## Что сделано

### Task 1 — HeadmanScheduleComponent + API + роутинг (commit `d02edc0`)

**HeadmanApiService (8 новых методов):**
- `listSemesters()` → `GET /api/academic/semesters?size=200`
- `getGroupScheduleItems(groupId, semesterId)` → `GET /api/schedule/items?groupId=X&semesterId=Y&size=200`
- `createScheduleItem(body)` → `POST /api/schedule/items` (HEADMAN)
- `updateScheduleItem(id, body)` → `PUT /api/schedule/items/{id}` (HEADMAN)
- `deleteScheduleItem(id)` → `DELETE /api/schedule/items/{id}` (HEADMAN)
- `createOneOffLesson(body)` → `POST /api/schedule/one-off-lessons` (HEADMAN, 409 при конфликте)
- `deleteOneOffLesson(id)` → `DELETE /api/schedule/one-off-lessons/{id}` (D-22)
- `getOneOffLessons(groupId, dateFrom, dateTo)` → `GET /api/schedule/one-off-lessons`
- `cancelLesson(lessonId, reason)` → `POST /api/schedule/lessons/{id}/cancel` (D-20, существующий)

**HeadmanScheduleComponent (`/headman/schedule`):**
- Матрица 5 дней (Пн..Пт) × 8 слотов через CSS grid (`grid-template-columns: 60px repeat(5, 1fr)`)
- groupId берётся из `AuthService.currentUser().groupId` (JWT claim — T-60-01)
- Активный семестр через `listSemesters()` + client-side `filter(s.active===true)`
- Пустая ячейка → клик открывает диалог в create-mode с pre-filled `dayOfWeek` + `lessonNumber`
- Занятая ячейка → клик открывает диалог в edit-mode со всеми полями item
- Toolbar: кнопка «Добавить разовую пару» → `OneOffDialogComponent`
- Бейджи WeekType: ALL→«Все», ODD→«Нечёт», EVEN→«Чёт»
- Состояния: `loading()`, `error()`, empty state для «нет активного семестра»
- НЕТ drag-and-drop (D-14)

**Роутинг:**
- `/headman/schedule` добавлен в `app.routes.ts` с `headmanGuard` + lazy-load
- Sidebar: пункт «Расписание» (ph-calendar-blank) между «Предметы» и «Журнал»

**Тесты HeadmanScheduleComponent (6/6 зелёные):**
1. creates without errors (smoke)
2. loadSchedule → getGroupScheduleItems(5,1) → populates matrix, cellAt(0,2) = item
3. click на занятую ячейку → MatDialog open с `mode:'edit'`
4. click на пустую ячейку → MatDialog open с `mode:'create'`, `dayOfWeek`, `lessonNumber`
5. openOneOffDialog → MatDialog open с `groupId:5`
6. empty state когда нет активного семестра

**Тесты HeadmanApiService (+9):** GET/POST/PUT/DELETE всех 8 новых методов + listSemesters.

### Task 2 — ScheduleSlotDialogComponent + OneOffDialogComponent (commit `110b43f`)

**ScheduleSlotDialogComponent:**
- Форма: `subjectId` (required, mat-select из `listSubjects()`), `room` (mat-input, 64 chars), `weekType` (mat-select ALL/ODD/EVEN, required)
- Препод НЕ выбирается (D-16 — TeacherSubjectGroup на уровне Subject)
- Title: «Новый слот» | «Редактировать слот»
- Create-mode: `defaultSlotTimes(lessonNumber)` 09:00..22:30 grid прибит в компоненте
- Edit-mode: время из `item.startTime/endTime` (неизменяемо без смены слота)
- 403 → `apiError: "Недостаточно прав..."`
- 409 → `apiError: "Конфликт..."`
- 201/200 → snackBar + `dialogRef.close(true)`

**OneOffDialogComponent:**
- Форма: `date` (MatDatepicker, любая дата — D-08), `lessonNumber` (mat-select 1..8 — D-05), `subjectId` (mat-select из каталога группы — D-03), `classroom` (необязательно)
- При submit: ISO форматирование даты (YYYY-MM-DD) + `createOneOffLesson({groupId, subjectId, date, lessonNumber, classroom})`
- 409 → `apiError: "Слот занят. Сначала отмените шаблонную пару на эту дату."` (D-09)
- 403 → `apiError: "Недостаточно прав..."`
- 201 → snackBar «Разовая пара добавлена.» + `dialogRef.close(true)`

**Тесты ScheduleSlotDialogComponent (5/5 зелёные):**
1. create mode: форма содержит subjectId/room/weekType + subjects loaded
2. submit (create) → `createScheduleItem` с {groupId, subjectId, semesterId, dayOfWeek, lessonNumber, weekType, room}
3. submit (edit) → `updateScheduleItem(id, {...updated fields})`
4. missing subjectId → form.invalid → API не вызван
5. 403 response → apiError = «Недостаточно прав»

**Тесты OneOffDialogComponent (5/5 зелёные):**
1. creates с date/lessonNumber/subjectId/classroom + lessonNumbers=[1..8]
2. submit → `createOneOffLesson` с ISO-date + dialogRef.close(true)
3. form invalid когда required fields missing → API не вызван
4. 409 → apiError «Слот занят»
5. 403 → apiError «Недостаточно прав»

## Endpoints (покрытые сервисом)

| Метод | Путь | Назначение |
|-------|------|-----------|
| GET   | `/api/schedule/items?groupId=&semesterId=` | Шаблон группы для семестра |
| POST  | `/api/schedule/items` | Создать шаблонный слот (HEADMAN) |
| PUT   | `/api/schedule/items/{id}` | Редактировать шаблонный слот |
| DELETE| `/api/schedule/items/{id}` | Деактивировать шаблонный слот |
| POST  | `/api/schedule/one-off-lessons` | Создать разовую пару (409 при конфликте) |
| DELETE| `/api/schedule/one-off-lessons/{id}` | Удалить разовую пару (D-22) |
| GET   | `/api/schedule/one-off-lessons?groupId=&dateFrom=&dateTo=` | Список разовых пар |
| POST  | `/api/schedule/lessons/{id}/cancel` | Отменить конкретный урок (D-20) |
| GET   | `/api/academic/semesters?size=200` | Активный семестр |

## Verification

| Команда | Результат |
|---------|-----------|
| `npx vitest run src/app/features/headman/schedule` | 16/16 PASSED (3 файла) |
| `npx vitest run` | **394/394 PASSED** (было 369, +25 новых: 16 component + 9 api) |
| `ls frontends/web-panel/src/app/features/headman/schedule/` | 6 файлов (3 ts + 3 spec.ts) |
| `grep "schedule" src/app/app.routes.ts` | `/headman/schedule` → HeadmanScheduleComponent |
| `grep "schedule" sidebar.component.ts` | «Расписание» пункт для isHeadman:true |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Endpoint URL: `/schedule-items` → `/schedule/items`**

- **Found during:** Task 1, чтение `ScheduleItemApi` из `schedule-api-contract`.
- **Issue:** План указывал в коде `getGroupScheduleItems` вызов `/api/schedule/schedule-items`, но реальный контракт имеет `@RequestMapping("/schedule/items")` (см. `ScheduleItemApi.java:31`). Запрос по несуществующему URL → 404 на интеграции.
- **Fix:** Использован реальный путь `/api/schedule/items` во всех методах (get/create/update/delete).
- **Files modified:** `headman-api.service.ts`, spec + тесты.

**2. [Rule 3 — Blocking] MatDialog mock override не работал из-за `MatDialogModule` в imports компонента**

- **Found during:** Task 1, первый прогон `headman-schedule.component.spec.ts`.
- **Issue:** `TestBed.configureTestingModule({providers:[{provide:MatDialog, useValue: mock}]})` не перехватывал `inject(MatDialog)` в компоненте — вызывались методы реального сервиса. Корневая причина: у standalone-компонента в `imports:[MatDialogModule]` модуль объявляет собственные dependencies, из-за которых `inject(MatDialog)` резолвился через модульный injector вместо TestBed root.
- **Fix:** `MatDialogModule` удалён из `imports` компонента (используем только `MatDialog.open()`, никаких template-директив `mat-dialog-*` в этом компоненте — они живут в самих диалогах).
- **Files modified:** `headman-schedule.component.ts` (убран импорт).
- **Impact:** Визуально никакого — диалоги открываются через `dialog.open(Component, {...})`, которое работает без MatDialogModule в родителе. Только тестируемость улучшилась.

## Known Stubs

Нет. Все компоненты полностью функциональны и ходят в реальные backend endpoints.

## Deferred Checkpoint

**checkpoint:human-verify** — ручная UI-верификация отложена на отдельный UAT-проход (см. Phase 60 finalization). Автоматические проверки покрывают:
- Структуру компонентов (смоук + поведение)
- Передачу правильных payload в API (request body, path params)
- Error handling (403/409)
- Роутинг (маршрут `/headman/schedule` зарегистрирован с headmanGuard)
- Sidebar entry (пункт «Расписание» для headman)

Визуальная проверка (Material Design-соответствие, matrix rendering, snackBar messages) требует запущенного stack'а (auth + academic + schedule services) и должна быть пройдена перед релизом v9.0.

## Threat Flags

Нет нового surface. STRIDE-mapping плана смитигирован:
- **T-60-01 (Broken Access Control):** `groupId` берётся из `AuthService.currentUser().groupId` (JWT claim), не из form/URL — пользователь не может передать чужой groupId. Backend дополнительно валидирует через `requireHeadmanForGroup` (см. 60-02/60-03).
- **T-60-03 (Duplicate one-off):** Frontend показывает 409 → «Слот занят. Сначала отмените шаблонную пару...» — дружелюбное сообщение; backend UNIQUE constraint предотвращает дубль даже при race condition.

## Self-Check: PASSED

**Created files (existence verified):**
- FOUND: frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.ts
- FOUND: frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.spec.ts
- FOUND: frontends/web-panel/src/app/features/headman/schedule/schedule-slot-dialog.component.ts
- FOUND: frontends/web-panel/src/app/features/headman/schedule/schedule-slot-dialog.component.spec.ts
- FOUND: frontends/web-panel/src/app/features/headman/schedule/one-off-dialog.component.ts
- FOUND: frontends/web-panel/src/app/features/headman/schedule/one-off-dialog.component.spec.ts

**Commits (verified via `git log --oneline -2`):**
- FOUND: d02edc0 feat(60-07): headman schedule page — matrix + route + api methods
- FOUND: 110b43f feat(60-07): schedule-slot-dialog + one-off-dialog components

**Tests:** `npx vitest run` — 394/394 PASSED (53 test files).

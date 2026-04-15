---
phase: 61-headman-homework-management-ui-homeworkapi-controller-homewo
plan: 05
subsystem: web-panel headman cabinet — homework CRUD UI
tags: [frontend, angular, headman, homework, ui, d-08, d-11, d-12, d-13, d-14]
requires:
  - "Phase 61-01 — Homework entity + DTO (lessonDate, lessonNumber) + HomeworkResponse shape"
  - "Phase 61-03 — Backend D-05/D-06 guards: publishedBy + HEADMAN-only"
provides:
  - "Страница /headman/homework — недельный список пар с ДЗ под каждой парой"
  - "HomeworkInlineFormComponent — create/edit форма (standalone, НЕ MatDialog)"
  - "Shared week-navigator и homework-card компоненты для переиспользования в Phase 61-06 (student UI)"
  - "HeadmanHomeworkApiService — typed list/create/update/delete для /api/academic/homeworks"
  - "Sidebar entry «Домашние задания» (ph-notebook) в секции старостата"
  - "Lazy route /headman/homework с canActivate: [headmanGuard]"
affects:
  - frontends/web-panel/src/app/shared/week-navigator/week-navigator.component.ts
  - frontends/web-panel/src/app/shared/homework-card/homework-card.component.ts
  - frontends/web-panel/src/app/features/headman/homework/headman-homework-api.service.ts
  - frontends/web-panel/src/app/features/headman/homework/headman-homework.component.ts
  - frontends/web-panel/src/app/features/headman/homework/homework-inline-form/homework-inline-form.component.ts
  - frontends/web-panel/src/app/features/headman/homework/headman-homework.component.spec.ts
  - frontends/web-panel/src/app/features/headman/homework/homework-inline-form/homework-inline-form.component.spec.ts
  - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
  - frontends/web-panel/src/app/app.routes.ts
tech-stack:
  added: []
  patterns:
    - "Shared/ folder создан впервые в web-panel — все предыдущие переиспользуемые элементы жили внутри features/"
    - "WeekNavigatorComponent переиспользует week-utils.ts из features/student/schedule (getMonday/addDays/formatWeekRange/isSameWeek) вместо дублирования"
    - "HomeworkCardModel interface сознательно минимален — совместим и с backend HomeworkResponse (headman), и с фронтовым HomeworkItem (student PLAN-06)"
    - "editingState: signal<{lessonKey, homework|null}|null> на уровне родителя — гарантирует что одновременно открыта только одна inline-форма; смена недели или saved/cancelled очищают signal"
    - "forkJoin(lessons, homeworks) + catchError(of([])) — частичный сбой API не блокирует рендер пустого экрана"
    - "Тесты: Vitest + @analogjs/vitest-angular + provideNoopAnimations() — уже стандартный паттерн web-panel (411 существующих spec'ов)"
key-files:
  created:
    - frontends/web-panel/src/app/shared/week-navigator/week-navigator.component.ts
    - frontends/web-panel/src/app/shared/homework-card/homework-card.component.ts
    - frontends/web-panel/src/app/features/headman/homework/headman-homework-api.service.ts
    - frontends/web-panel/src/app/features/headman/homework/headman-homework.component.ts
    - frontends/web-panel/src/app/features/headman/homework/homework-inline-form/homework-inline-form.component.ts
    - frontends/web-panel/src/app/features/headman/homework/headman-homework.component.spec.ts
    - frontends/web-panel/src/app/features/headman/homework/homework-inline-form/homework-inline-form.component.spec.ts
  modified:
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
    - frontends/web-panel/src/app/app.routes.ts
decisions:
  - "Plan ожидал extraction week-navigator из headman-schedule inline-кода, но у headman-schedule week-навигации НЕТ (только fixed matrix + current-week banner). WeekNavigatorComponent создан с нуля, переиспользуя существующие week-utils.ts из features/student/schedule (Phase 51). headman-schedule не модифицирован — reusable extraction отложена как отдельная задача при необходимости."
  - "Plan требовал `ng test --browsers=ChromeHeadless`, но web-panel использует Vitest + jsdom (см. package.json \"test\": \"vitest run\"). Тесты написаны под Vitest; full регрессия 421/421 green."
  - "HomeworkCardModel вынесен в shared/homework-card/homework-card.component.ts как interface — избегаем импорта headman-specific или student-specific типа в shared/ слой. Оба feature-компонента удовлетворяют shape'у структурно."
  - "isLessonCancelled guard скрывает кнопку «+ Добавить задание» на отменённых парах (даже если backend D-04 может их разрешить — UX-подсказка, что на такую пару ДЗ бессмысленно)."
metrics:
  duration: "~45 min"
  completed: "2026-04-15"
  tasks: 2
  commits: 2
---

# Phase 61 Plan 05: Headman Homework Management UI Summary

Реализует D-08 (недельный layout + inline-форма), D-11 (subject автоподстановка из контекста пары), D-12 (sidebar «Домашние задания»), D-13 (структура features/headman/homework + shared/), D-14 (русский UI + routeFade + expand/collapse анимации). Старосте доступна страница `/headman/homework`, где он видит все пары своей группы на неделе (Пн-Сб), под каждой парой — список опубликованных ДЗ с иконками edit/delete для своих записей и inline-кнопкой «+ Добавить задание», раскрывающей неммодальную форму create/edit.

## Что сделано

### Task 1: Shared компоненты + HeadmanHomeworkApiService

- **shared/week-navigator/week-navigator.component.ts** — standalone переиспользуемый компонент навигации ← «Неделя X-Y мес» → + плавающий pill «Сегодня» (когда не на текущей неделе). Переиспользует `week-utils.ts` (getMonday/addDays/formatWeekRange/isSameWeek) из features/student/schedule — не дублирует логику. Emit `weekChanged: { monday, weekOffset }` на init и каждом клике.
- **shared/homework-card/homework-card.component.ts** — feature-agnostic карточка ДЗ с двумя вариантами через @Input флаги: `showEditActions` (headman → иконки ph-pencil / ph-trash) и `showCompleteCheckbox` (student → MatCheckbox). Поддерживает описание, external link с rel="noopener", completed-state (opacity 0.55 + line-through). `HomeworkCardModel` interface — минимальный surface, совместимый с обеими доменными DTO.
- **HeadmanHomeworkApiService** — typed `list/create/update/delete` для `/api/academic/homeworks`. `CreateHomeworkRequest` / `UpdateHomeworkRequest` / `HomeworkResponse` TS-типы совпадают 1:1 с backend-контрактом (Phase 61-01 расширил их на `lessonDate: LocalDate` + `lessonNumber: Integer`).

### Task 2: HeadmanHomeworkComponent + HomeworkInlineFormComponent + sidebar + route + специи

- **HeadmanHomeworkComponent** — standalone, OnPush, signals + computed:
  - `groupId`, `currentUserId` из `AuthService.currentUser()` (JWT claims)
  - `semesterId` резолвится из `HeadmanApiService.listSemesters().find(active)`
  - `monday` обновляется через `(weekChanged)` от `<app-week-navigator>`
  - `reload()` делает `forkJoin(studentApi.getWeekLessons, homeworkApi.list)` с `catchError(of([]))` для graceful degradation
  - `days` — computed массив `DayRow[]`, группирует lessons по dayOfWeek (1..6), сортирует по lessonNumber, только дни с парами (пустые Пн-Сб скрыты)
  - `homeworksFor(lessonKey)` — клиентская фильтрация ДЗ по `lessonDate + lessonNumber` (RESEARCH VERIFIED: не добавляем server-side `?from&to` — N ≤ 100 ДЗ/семестр, перенесено в deferred)
  - `isOwn(hw)` — `hw.publishedBy === currentUserId` → показываем edit/delete иконки (T-61-17 duplicate с backend D-05)
  - `editingState: signal<{lessonKey, homework|null} | null>` — только одна форма открыта; смена недели/save/cancel → `.set(null)`
  - Pre-delete `confirm()` с названием ДЗ. Error state в `error()` signal.
  - Анимация `routeFade` 200ms (стандартный web-panel паттерн).
- **HomeworkInlineFormComponent** — standalone, OnPush:
  - Reactive `FormGroup`: `title` (required, maxLength 255), `description` (maxLength 4000), `link` (maxLength 2048). Errors под полями через `<mat-error>` (стиль D-14 subject-dialog).
  - Readonly context-header сверху: «Предмет: {name} · {dd мес YYYY} · Пара № N» (D-11 автоподстановка из контекста пары).
  - `submit()` ветвится по `isEdit()`: create → POST с полным lesson binding; edit → PUT только {title, description, link} (D-01 lesson binding immutable).
  - Backend errors (RFC 7807 `ErrorResponse.detail`) выводятся в `apiError()` signal с иконкой ph-warning-circle. 403 → специальный текст «Недостаточно прав для этого действия.», 400 → «Проверьте введённые данные.».
  - Анимация `expand` 200ms on enter + 150ms on leave (height 0 ↔ *, opacity 0 ↔ 1) — подкрепляет D-14 требование inline-раскрытия.
- **sidebar.component.ts** — добавлен пункт «Домашние задания» (icon `ph-notebook`, route `/headman/homework`, `roles: ['STUDENT']`, `isHeadman: true`) после «Предметы», до «Расписание».
- **app.routes.ts** — lazy route `/headman/homework` с `canActivate: [headmanGuard]` между `subjects` и `journal` (T-61-17 elevation mitigation: plain STUDENT без is_headman редиректится).
- **Тесты** — 10 vitest спецификаций:
  - `HomeworkInlineFormComponent`: (1) title required → form.invalid; (2) create mode → api.create со всеми полями lesson context; (3) edit mode → api.update только с title/description/link (без lesson binding); (4) api error → apiError signal, saved не emit; (5) cancel → cancelled event.
  - `HeadmanHomeworkComponent`: (1) smoke + ngOnInit подгружает семестр + lessons; (2) onAddHomework → editingState + isEditing true; (3) onSaved → editingState cleared + reload; (4) homeworksFor фильтрует по lessonKey; (5) isOwn true/false.
  - Full web-panel регрессия: **421/421 green** (+10 над baseline 411).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] План требовал extraction week-navigator из inline-кода headman-schedule, но у headman-schedule его нет**
- **Found during:** Task 1, чтение `headman-schedule.component.ts`
- **Issue:** Plan <interfaces> ссылается на «строки 35-110: weekOffset signal, mondayOf(date), isoWeekNumber, DAY_LABELS». Actual: `headman-schedule` показывает fixed 5×8 матрицу текущего семестра без переключения недель (есть только `currentWeekIsOdd()` banner). Plan также сам говорит «Обновить headman-schedule — удалить inline week-navigation utilities» — их там нет.
- **Fix:** `WeekNavigatorComponent` создан с нуля в `shared/`, реюзает `features/student/schedule/week-utils.ts` (getMonday/addDays/formatWeekRange/isSameWeek) — эти утилиты уже есть из Phase 51. `headman-schedule.component.ts` **не трогается** (regression-safe; у него свой UX «матрица семестра», а не недельная навигация).
- **Files:** создан `shared/week-navigator/week-navigator.component.ts`; `headman-schedule.component.ts` не модифицирован.
- **Commit:** 7291a35

**2. [Rule 3 - Blocking] Plan указал `ng test --browsers=ChromeHeadless`, но проект использует Vitest**
- **Found during:** Task 2, запуск тестов
- **Issue:** `frontends/web-panel/package.json`: `"test": "vitest run"`, devDependencies содержит `@analogjs/vitest-angular` + `jsdom` + `vitest` (НЕ Karma/Jasmine/ChromeHeadless). Существующий `headman-schedule.component.spec.ts` импортирует из `vitest`.
- **Fix:** Спеки написаны под Vitest: `import { describe, it, expect, beforeEach, vi } from 'vitest'`, `provideNoopAnimations()` для Material, `TestBed.resetTestingModule()` в `beforeEach`. Запуск через `npx vitest run src/app/features/headman/homework`.
- **Files:** `headman-homework.component.spec.ts`, `homework-inline-form.component.spec.ts`.
- **Commit:** 521282c

**3. [Rule 2 - Coverage] Добавлен 4-й и 5-й тесты в каждый spec сверх плана**
- **Found during:** Task 2
- **Issue:** План требовал 3 теста в каждом spec. Threat model (T-61-15 XSS, T-61-17 Elevation) и `must_haves.truths` упоминают edit/delete видимость только для своих ДЗ, error-handling формы и cancel. Минимум-3 не покрывает.
- **Fix:** HomeworkInlineFormComponent spec → +2 теста (api error path, cancel). HeadmanHomeworkComponent spec → +2 теста (homeworksFor filter, isOwn guard для publishedBy mismatch).
- **Files:** оба `.spec.ts`.
- **Commit:** 521282c

## Verification

- `npx ng build --configuration=development` → exit 0 (warnings в pre-existing student-homework template — out of scope).
- `npx vitest run src/app/features/headman/homework` → **10/10 green** (2 файла).
- `npx vitest run` (full web-panel) → **56 файлов, 421/421 green** (baseline 411, +10 новых).

## Success criteria

- [x] Страница /headman/homework отображает недельный список пар Пн-Сб (HeadmanHomeworkComponent.days computed — группирует по dayOfWeek 1..6, пустые дни скрыты)
- [x] Навигация недель работает (app-week-navigator ← → + pill «Сегодня»)
- [x] Inline-форма раскрывается с анимацией expand/collapse (trigger('expand') в HomeworkInlineFormComponent), сохраняет/отменяет и сворачивает список
- [x] Edit/delete работают только для своих ДЗ (isOwn() guard в template → showEditActions)
- [x] Sidebar содержит пункт «Домашние задания» (ph-notebook, /headman/homework, roles STUDENT + isHeadman:true)
- [x] Route зарегистрирован с canActivate: [headmanGuard] (app.routes.ts между subjects и journal)
- [x] Spec-тесты зелёные (10/10 + full регрессия 421/421)
- [x] D-11 subject/date/lessonNumber readonly из контекста пары (не редактируются в форме)
- [x] НЕ используется MatDialog для формы старосты (D-08)

## Self-Check: PASSED

- FOUND: frontends/web-panel/src/app/shared/week-navigator/week-navigator.component.ts
- FOUND: frontends/web-panel/src/app/shared/homework-card/homework-card.component.ts
- FOUND: frontends/web-panel/src/app/features/headman/homework/headman-homework-api.service.ts
- FOUND: frontends/web-panel/src/app/features/headman/homework/headman-homework.component.ts
- FOUND: frontends/web-panel/src/app/features/headman/homework/homework-inline-form/homework-inline-form.component.ts
- FOUND: frontends/web-panel/src/app/features/headman/homework/headman-homework.component.spec.ts
- FOUND: frontends/web-panel/src/app/features/headman/homework/homework-inline-form/homework-inline-form.component.spec.ts
- MODIFIED: frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts (добавлен пункт «Домашние задания»)
- MODIFIED: frontends/web-panel/src/app/app.routes.ts (добавлен route /headman/homework)
- FOUND commit: 7291a35 (feat(61-05): add shared week-navigator + homework-card + headman homework api)
- FOUND commit: 521282c (feat(61-05): add /headman/homework page with inline CRUD form)

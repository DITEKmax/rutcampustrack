---
phase: 54-headman-web-cabinet-group-management-subjects
verified: 2026-04-09T21:00:00Z
status: human_needed
score: 4/4 roadmap truths supported by code
human_verification:
  - test: "Войти под headman-пользователем, открыть /student/schedule — страница загружается без ошибок 403"
    expected: "Расписание отображается; headman проходит studentGuard (role === 'STUDENT')"
    why_human: "studentGuard проверяет только role === 'STUDENT' — headman имеет эту роль, поэтому логически должен пройти, но фактическое поведение Guards в Angular SPA можно подтвердить только в браузере с реальным JWT"
  - test: "Открыть /headman/dashboard под headman-пользователем — отображаются 4 тайла со статами"
    expected: "Плитки 'Студентов в группе', 'Тикеты о пропуске' (0), 'сегодняшняя пара или Нет пары сегодня', 'Запросы поздней отметки' (0) видны корректно; скелет показывается во время загрузки"
    why_human: "forkJoin использует catchError для отложенных эндпоинтов (/headman/excuses, /headman/late-checkins); нужно убедиться, что 404 деградирует к 0, а не крашит компонент"
  - test: "Открыть /headman/group — назначить нового помощника через диалог с хотя бы одним checkbox"
    expected: "POST /api/academic/assistants возвращает 201; список помощников обновляется; снекбар 'Помощник назначен.' виден"
    why_human: "WPAN-13 backend fix — headman обходит @RequireRole(STUDENT) через headmanBypass, но реальный HTTP-запрос через Gateway с заголовком X-Is-Headman надо проверить end-to-end"
  - test: "В диалоге /headman/subjects нажать 'Добавить предмет' — проверить, что MatSelect 'Преподаватель' заполнен"
    expected: "GET /api/academic/users/teachers возвращает список преподавателей; MatOption-ы видны в выпадающем списке"
    why_human: "Новый эндпоинт из Plan 01 — нужно убедиться, что Angular получает данные и CollectionModel._embedded корректно разворачивается"
---

# Phase 54: Headman Web Cabinet Verification Report

**Phase Goal:** Backend WPAN-13 blocker resolved (headman can call assistant endpoints); headman has working dashboard, group management with assistant CRUD, and subject CRUD with teacher assignments.
**Verified:** 2026-04-09T21:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Roadmap Success Criteria

| #  | Критерий                                                                                                                  | Статус     | Доказательство                                                                                                           |
|----|--------------------------------------------------------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------|
| SC1 | headman проходит `studentGuard` (role === 'STUDENT') и может открыть /student/schedule и /student/checkin               | ? UNCERTAIN | `studentGuard` проверяет `user.role === 'STUDENT'` — headman имеет роль STUDENT, условие выполнено в коде; runtime-поведение требует ручной проверки |
| SC2 | /headman/dashboard показывает счётчик членов, карточку сегодняшней пары и числовые счётчики pending-тикетов             | ✓ VERIFIED  | `HeadmanDashboardComponent` — forkJoin с 4 вызовами, catchError на отложенных эндпоинтах, 4 тайла в шаблоне, скелет, error state |
| SC3 | /headman/group — список студентов; создание помощника + удаление без 403 (WPAN-13 resolved)                              | ✓ VERIFIED  | `RoleCheckAspect.headmanBypass` присутствует; `HeadmanGroupComponent` + `AssignAssistantDialogComponent` + `DeleteAssistantDialogComponent` полностью реализованы и подключены |
| SC4 | /headman/subjects — CRUD предметов с назначением преподавателя без ошибок                                                | ✓ VERIFIED  | `HeadmanSubjectsComponent` + `SubjectDialogComponent` (режимы create/edit) + `DeleteSubjectDialogComponent` с обработкой 409; `listTeachers()` подключён в ngOnInit диалога |

**Score:** 4/4 кода подтверждены, 1 SC требует ручной runtime-проверки (SC1 — поведение guard-а)

### Observable Truths (из PLAN must_haves)

#### Plan 01 Truths

| # | Truth | Статус | Доказательство |
|---|-------|--------|----------------|
| 1 | headman (STUDENT + isHeadman=true) не блокируется RoleCheckAspect для @RequireRole({STUDENT}) | ✓ VERIFIED | `RoleCheckAspect.java:31-34` — `headmanBypass = isHeadman() && required.contains(STUDENT)` |
| 2 | Обычный студент (isHeadman=false) отклоняется для @RequireRole({ADMIN}) | ✓ VERIFIED | Тест `plainStudentBlockedForAdminRole` в `RoleCheckAspectTest.java:71-77` |
| 3 | ADMIN и TEACHER не затронуты bypass — их проверки работают | ✓ VERIFIED | Тесты `adminPassesAdminRole`, `teacherPassesTeacherRole` |
| 4 | GET /academic/users/teachers возвращает CollectionModel учителей для роли STUDENT | ✓ VERIFIED | `UserApi.java:120-123`, `UserController.java:103-111` @RequireRole({STUDENT}), `UserService.java:119-121` |
| 5 | RoleCheckAspectTest — 7 JUnit 5 unit-тестов без Spring контекста | ✓ VERIFIED | Файл существует, 7 тестов, @ExtendWith(MockitoExtension.class) |

#### Plan 02 Truths

| # | Truth | Статус | Доказательство |
|---|-------|--------|----------------|
| 1 | Маршруты /headman/group и /headman/subjects существуют с headmanGuard | ✓ VERIFIED | `app.routes.ts:183-201` — оба маршрута с canActivate: [headmanGuard] |
| 2 | Sidebar показывает секцию 'Старостат' только когда isHeadman=true | ✓ VERIFIED | `sidebar.component.ts:209-215` — `filteredHeadmanNavItems` возвращает [] когда !user.isHeadman |
| 3 | HeadmanApiService — все HTTP-методы на верные пути | ✓ VERIFIED | 13 методов в `headman-api.service.ts`, 10 тестов в spec-файле |
| 4 | Headman nav items НЕ видны для обычных студентов | ✓ VERIFIED | `filteredNavItems` фильтрует `!item.isHeadman`; `filteredHeadmanNavItems` возвращает [] когда !user.isHeadman |

#### Plan 03 Truths

| # | Truth | Статус | Доказательство |
|---|-------|--------|----------------|
| 1 | /headman/dashboard — 4-тайловый грид без console errors | ✓ VERIFIED | Шаблон с 4 тайлами, forkJoin, catchError, нет console.log в продакшн-коде |
| 2 | Тайл членов группы показывает totalElements из getGroupMembers | ✓ VERIFIED | `memberCount.set(members?.page?.totalElements ?? 0)` строка 225 |
| 3 | Карточка сегодняшней пары или 'Нет пары сегодня' | ✓ VERIFIED | Шаблон: `@if (todayLesson())` с данными / `@else` с 'Нет пары сегодня' |
| 4 | Excuse и late-checkin тайлы показывают 0 при 404 | ✓ VERIFIED | `catchError(() => of(null))` на обоих вызовах, fallback `?? 0` |
| 5 | Скелет во время загрузки, error state при сбое | ✓ VERIFIED | `@if (loading())` — скелет с 4 картами; `@else if (error())` — .page-error |

#### Plan 04 Truths

| # | Truth | Статус | Доказательство |
|---|-------|--------|----------------|
| 1 | Таблица студентов с именем + логином + чипом роли | ✓ VERIFIED | `headman-group.component.ts:82-106` — mat-table с колонками student/status/action |
| 2 | Секция помощников с чипами прав и кнопкой удаления | ✓ VERIFIED | `headman-group.component.ts:110-137` — @for по assistants(), permission chips, trash button |
| 3 | Диалог 'Назначить помощника' открывается; форма валидирует выбор студента и хотя бы одно право | ✓ VERIFIED | `AssignAssistantDialogComponent` — MatSelect (required) + 4 MatCheckbox + showPermissionError |
| 4 | При успехе диалог закрывается и список помощников обновляется | ✓ VERIFIED | `dialogRef.close(true)` → `ref.afterClosed().subscribe(result => { if (result) loadData() })` |
| 5 | Клик на корзину открывает диалог подтверждения; подтверждение вызывает revokeAssistant | ✓ VERIFIED | `openDeleteDialog()` → `DeleteAssistantDialogComponent` → `headmanApi.revokeAssistant(assistant.id)` |

#### Plan 05 Truths

| # | Truth | Статус | Доказательство |
|---|-------|--------|----------------|
| 1 | Таблица предметов — название и имя преподавателя | ✓ VERIFIED | `headman-subjects.component.ts:91-127` — mat-table с колонками name/teacher/actions |
| 2 | 'Не назначен' курсивом при отсутствии преподавателя | ✓ VERIFIED | `@else { <span class="no-teacher">Не назначен</span> }` + CSS `font-style: italic` |
| 3 | 'Добавить предмет' открывает диалог с валидацией имени | ✓ VERIFIED | `SubjectDialogComponent` — FormControl name с Validators.required + maxLength(120) |
| 4 | Teacher select загружается из GET /teachers; при пустом списке — хинт | ✓ VERIFIED | `ngOnInit()` вызывает `listTeachers()`, хинт при `teachers.length === 0 && !teachersLoading` |
| 5 | Create/edit закрывает диалог и перезагружает список | ✓ VERIFIED | `dialogRef.close(true)` → `loadSubjects()` |
| 6 | Edit открывает диалог с предзаполненными данными | ✓ VERIFIED | `form.patchValue({name: ..., teacherId: ...})` в constructor |
| 7 | Удаление с 409 показывает 'Нельзя удалить предмет с записями посещаемости' | ✓ VERIFIED | `error: (err) => { if (err.status === 409) this.snackBar.open('Нельзя удалить предмет с записями посещаемости.', ...)` |

### Required Artifacts

| Artifact | Ожидается | Статус | Детали |
|----------|-----------|--------|--------|
| `services/academic-service/academic-app/.../security/RoleCheckAspect.java` | headmanBypass | ✓ VERIFIED | `headmanBypass` на строке 31, логика на строках 31-35 |
| `services/academic-service/academic-api-contract/.../api/UserApi.java` | listTeachers() | ✓ VERIFIED | GET /teachers, CollectionModel, строки 120-123 |
| `services/academic-service/academic-app/.../user/UserController.java` | listTeachers() с @RequireRole({STUDENT}) | ✓ VERIFIED | строки 103-111 |
| `services/academic-service/academic-app/.../user/UserService.java` | listTeachers() → findByRole("teacher") | ✓ VERIFIED | строки 119-121 |
| `services/academic-service/academic-app/src/test/.../security/RoleCheckAspectTest.java` | 7 тест-кейсов | ✓ VERIFIED | 7 методов, @ExtendWith(MockitoExtension.class) |
| `frontends/web-panel/src/app/app.routes.ts` | /headman/dashboard, /headman/group, /headman/subjects | ✓ VERIFIED | строки 168-203, все с headmanGuard |
| `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` | isHeadman фильтрация | ✓ VERIFIED | NavItem interface, filteredNavItems, filteredHeadmanNavItems |
| `frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts` | 13 HTTP-методов | ✓ VERIFIED | все методы включая listTeachers() |
| `frontends/web-panel/src/app/features/headman/shared/headman-api.service.spec.ts` | 10 тестов | ✓ VERIFIED | 10 тест-кейсов с HttpTestingController |
| `frontends/web-panel/src/app/features/headman/dashboard/headman-dashboard.component.ts` | forkJoin, 4 тайла | ✓ VERIFIED | 244 строки, forkJoin с catchError |
| `frontends/web-panel/src/app/features/headman/group/headman-group.component.ts` | listAssistants, forkJoin | ✓ VERIFIED | 313 строк, forkJoin, listAssistants |
| `frontends/web-panel/src/app/features/headman/group/assign-assistant-dialog.component.ts` | MatCheckbox | ✓ VERIFIED | MatCheckboxModule, 4 checkbox-а с 44px min-height |
| `frontends/web-panel/src/app/features/headman/group/delete-assistant-dialog.component.ts` | mat-dialog-close паттерн | ✓ VERIFIED | [mat-dialog-close]="true/false" |
| `frontends/web-panel/src/app/features/headman/subjects/headman-subjects.component.ts` | listSubjects | ✓ VERIFIED | 221 строка, listSubjects() в ngOnInit |
| `frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.ts` | listTeachers | ✓ VERIFIED | listTeachers() в ngOnInit, create/edit режимы |
| `frontends/web-panel/src/app/features/headman/subjects/delete-subject-dialog.component.ts` | confirmation dialog | ✓ VERIFIED | 32 строки, [mat-dialog-close] паттерн |

### Key Link Verification

| From | To | Via | Статус | Детали |
|------|----|-----|--------|--------|
| `RoleCheckAspect.checkRole()` | `requestContext.isHeadman()` | headmanBypass boolean | ✓ WIRED | строка 31 RoleCheckAspect.java |
| `UserController.listTeachers()` | `UserService.listTeachers()` | прямой вызов | ✓ WIRED | строка 106 UserController.java |
| `UserService.listTeachers()` | `userRepository.findByRole("teacher", ...)` | JPQL query | ✓ WIRED | строка 120 UserService.java |
| `sidebar filteredNavItems` | `currentUser().isHeadman` | computed signal с !item.isHeadman фильтром | ✓ WIRED | строки 200-205 sidebar.component.ts |
| `filteredHeadmanNavItems` | `user.isHeadman` | computed signal | ✓ WIRED | строки 209-215 sidebar.component.ts |
| `app.routes.ts headman block` | headmanGuard | canActivate на всех 3 маршрутах | ✓ WIRED | строки 171, 184, 192 |
| `HeadmanDashboardComponent.ngOnInit()` | `HeadmanApiService.getGroupMembers()` | forkJoin | ✓ WIRED | строки 218-223 headman-dashboard.component.ts |
| `HeadmanGroupComponent.loadData()` | `HeadmanApiService.listAssistants()` | forkJoin | ✓ WIRED | строки 265-267 headman-group.component.ts |
| `HeadmanGroupComponent.openAssignDialog()` | `AssignAssistantDialogComponent` | MatDialog.open() | ✓ WIRED | строки 281-290 |
| `AssignAssistantDialogComponent.onSubmit()` | `HeadmanApiService.assignAssistant()` | subscribe к POST | ✓ WIRED | строки 155-168 assign-assistant-dialog.component.ts |
| `HeadmanGroupComponent.openDeleteDialog()` | `HeadmanApiService.revokeAssistant()` | afterClosed subscribe | ✓ WIRED | строки 300-307 |
| `SubjectDialogComponent ngOnInit()` | `HeadmanApiService.listTeachers()` | subscribe | ✓ WIRED | строки 133-153 subject-dialog.component.ts |
| `HeadmanSubjectsComponent.openDeleteDialog()` | `HeadmanApiService.deleteSubject()` | afterClosed + 409 check | ✓ WIRED | строки 203-218 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Источник | Реальные данные | Статус |
|----------|---------------|----------|-----------------|--------|
| HeadmanDashboardComponent | memberCount | getGroupMembers() → members.page.totalElements | GET /api/academic/groups/my/members (реальный endpoint, фаза 51) | ✓ FLOWING |
| HeadmanDashboardComponent | pendingExcuses | getPendingExcuses() с catchError → 0 | Деградирует к 0 при 404 (отложен до фазы 55) | ✓ FLOWING (graceful) |
| HeadmanGroupComponent | students | getGroupMembers() → _embedded unwrap | HATEOAS _embedded разворачивается | ✓ FLOWING |
| HeadmanGroupComponent | assistants | listAssistants(groupId) → _embedded unwrap | HATEOAS _embedded разворачивается | ✓ FLOWING |
| HeadmanSubjectsComponent | subjects | listSubjects() → _embedded unwrap | GET /api/academic/subjects (реальный endpoint) | ✓ FLOWING |
| SubjectDialogComponent | teachers | listTeachers() → _embedded unwrap | GET /api/academic/users/teachers (новый endpoint из Plan 01) | ✓ FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — Angular-приложение требует запущенного сервера и браузера для runtime-проверки Guards и HTTP-вызовов. Все важные поведения требуют ручной проверки (см. раздел Human Verification).

### Requirements Coverage

| Requirement | Source Plan | Описание | Статус | Доказательство |
|-------------|-------------|----------|--------|----------------|
| HEAD-WEB-01 | 54-01, 54-02 | headman bypass @RequireRole(STUDENT) + маршруты с headmanGuard | ✓ SATISFIED | RoleCheckAspect.headmanBypass + headmanGuard на всех 3 маршрутах |
| HEAD-WEB-02 | 54-03 | /headman/dashboard с 4-тайловым гридом | ✓ SATISFIED | HeadmanDashboardComponent полностью реализован |
| HEAD-WEB-03 | 54-01, 54-04 | /headman/group — assistant CRUD, WPAN-13 resolved | ✓ SATISFIED | HeadmanGroupComponent + 2 диалога + backend fix |
| HEAD-WEB-04 | 54-01, 54-05 | /headman/subjects — CRUD + teacher select | ✓ SATISFIED | HeadmanSubjectsComponent + SubjectDialogComponent + listTeachers endpoint |

### Anti-Patterns Found

| Файл | Строка | Паттерн | Серьёзность | Влияние |
|------|--------|---------|-------------|---------|
| `headman-group.component.ts` | 68, 76 | `*ngFor` (структурная директива) внутри `@if`/`@else if` блоков Angular 17 control flow | ⚠️ Warning | Смешение старого (NgFor/`*ngFor`) и нового (@for) синтакса в одном компоненте. Компонент импортирует CommonModule (для NgFor), но также использует @if/@else if/@for в других местах. Работает, но является непоследовательным стилем. |
| `headman-subjects.component.ts` | 76, 77 | `*ngFor` (структурная директива) внутри `@if` блока Angular 17 | ⚠️ Warning | Та же проблема — imports содержит NgFor, используется `*ngFor="let i of [1,2,3,4]"` внутри нового control flow |

**Примечание:** Эти паттерны не блокируют функциональность — Angular поддерживает оба подхода. Это warning уровня стиля кода, не блокер.

### Human Verification Required

#### 1. headman проходит studentGuard

**Тест:** Войти как headman-пользователь (STUDENT + isHeadman=true), открыть `/student/schedule`
**Ожидаемо:** Страница загружается; headman не перенаправляется на /login или dashboard
**Почему человек:** studentGuard проверяет `user.role === 'STUDENT'` — headman имеет эту роль в коде. Нужна проверка, что JWT-токен содержит `role: "STUDENT"` для headman и что Angular router корректно активирует guard в runtime.

#### 2. /headman/dashboard — graceful degradation для деградировавших endpoints

**Тест:** Открыть `/headman/dashboard` под headman; убедиться, что тайлы Excuse и Late check-in показывают 0, а не ошибку
**Ожидаемо:** Компонент полностью загружается; тайлы показывают 0; никаких unhandled rejection в консоли
**Почему человек:** `getPendingExcuses` и `getPendingLateCheckins` будут возвращать 404. `catchError(() => of(null))` должен работать, но поведение Angular с неизвестными 404-маршрутами через Gateway может отличаться от ожидаемого.

#### 3. WPAN-13 end-to-end — назначение помощника без 403

**Тест:** Открыть `/headman/group`; нажать "Назначить помощника"; выбрать студента; выбрать хотя бы одно право; нажать "Назначить помощника"
**Ожидаемо:** POST /api/academic/assistants возвращает 201; снекбар "Помощник назначен." виден; список помощников обновляется
**Почему человек:** Критическая проверка WPAN-13. RoleCheckAspect.headmanBypass реализован корректно в коде, но реальный HTTP-запрос должен пройти через Gateway с заголовком `X-Is-Headman: true` из JWT. Нужно подтвердить, что JwtAuthenticationFilter в gateway корректно извлекает is_headman из JWT и проксирует заголовок.

#### 4. Teacher select в диалоге предмета заполняется реальными данными

**Тест:** Открыть `/headman/subjects`; нажать "Добавить предмет"; в диалоге кликнуть на MatSelect "Преподаватель"
**Ожидаемо:** Список преподавателей загружается из GET /api/academic/users/teachers; MatOption-ы с именами преподавателей видны
**Почему человек:** Новый эндпоинт из Plan 01. Нужно убедиться, что CollectionModel._embedded ключ (`userResponseList` или другой) корректно разворачивается паттерном `Object.values(resp?._embedded ?? {})[0]` для реальных данных.

---

## Выводы

Все 12 ключевых артефактов фазы 54 существуют, являются субстантивными (не заглушки) и корректно подключены. Четыре требования (HEAD-WEB-01..04) покрыты кодом. Обнаружены 2 предупреждения стиля (смешение `*ngFor` и `@for`), не блокирующих функциональность.

Единственная причина статуса `human_needed`: два Angular Guard-а и два сетевых вызова требуют runtime-проверки в браузере с работающим бэкендом для подтверждения корректности JWT-проксирования и HATEOAS-десериализации.

---

_Verified: 2026-04-09T21:00:00Z_
_Verifier: Claude (gsd-verifier)_

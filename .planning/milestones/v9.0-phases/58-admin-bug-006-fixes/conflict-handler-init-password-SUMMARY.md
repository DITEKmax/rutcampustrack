---
phase: 58-admin-bug-006-fixes
plan: 02
subsystem: academic-service, web-panel (admin)
tags: [bug-006, 409-conflict, rfc7807, initial-password, ui-ux]
dependency-graph:
  requires: []
  provides:
    - "409 ProblemDetail с property field для login/email/telegramId/employeeNumber/name"
    - "UsersPage колонка «Начальный пароль» с copy-to-clipboard"
    - "UserDialog field-specific сообщения при 409"
  affects:
    - "Будущие плейны группы/семестра (groups_name_key уже замаплен)"
    - "Возможные admin-ui подобные диалоги — паттерн handleSaveError переиспользуем"
tech-stack:
  added: []
  patterns:
    - "Explicit pre-check + DataIntegrityViolation fallback (D-07) для 409"
    - "Record ErrorResponse с опциональным property field (RFC 7807 совместимо)"
    - "Angular computed<string[]> для условных columns в mat-table"
key-files:
  created:
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandlerTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceConflictTest.java
    - frontends/web-panel/src/app/features/admin/users/users-page.component.scss
  modified:
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ErrorResponse.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/ConflictException.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
    - frontends/web-panel/src/app/features/admin/shared/types.ts
    - frontends/web-panel/src/app/features/admin/users/users-page.component.ts
    - frontends/web-panel/src/app/features/admin/users/users-page.component.html
    - frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts
    - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts
    - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html
    - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts
decisions:
  - "ErrorResponse расширён опциональным String field (8-й параметр) + backward-compat 7-arg constructor чтобы не трогать остальные сервисы (auth/attendance/schedule)"
  - "ConflictException получил 3-arg constructor (field, value, message); legacy 1-arg сохранён"
  - "Login в UserDialog не control-а, поэтому для 409 field=login только submitError баннер (login генерирует backend)"
  - "Колонка initialPassword управляется computed displayedColumns — появляется только когда кто-то из текущей страницы ещё не сменил пароль"
metrics:
  duration: "~15 min executor time"
  completed: "2026-04-14"
  tests_added: "8 Java (3 UserServiceConflictTest + 5 GlobalExceptionHandlerTest conflict cases) + 11 vitest (4 users-page + 7 user-dialog)"
---

# Phase 58 Plan 02: Conflict handler + init password Summary

Human-readable 409 conflict messages для ADMIN-а при создании пользователя и видимая колонка начального пароля в админской таблице — два связанных UX-дефекта из BUG-006 объединены в один PR, поскольку оба завязаны на паре `UsersPage`/`UserDialog` + `UserService`.

## What Changed

### Backend (academic-service)

- **ConflictException** — теперь носит `field` и `value` (3-arg constructor). Legacy 1-arg конструктор сохранён для совместимости.
- **GlobalExceptionHandler**:
  - `@ExceptionHandler(ConflictException)` кладёт `ex.getField()` в тело `ErrorResponse`.
  - **Новый** `@ExceptionHandler(DataIntegrityViolationException)` — race-condition backstop (T-58-02-02). Регэкспом `constraint\s+"([^"]+)"` вынимает имя constraint-а из сообщения PG, маппит через константную `Map<String,String>` (`users_login_key → login`, `users_email_key → email`, `users_telegram_id_key → telegramId`, `users_employee_number_key → employeeNumber`, `groups_name_key → name`) и возвращает `409` с `field`. Unknown constraint → `500` + WARN-лог, без утечки raw SQL в ответ (T-58-02-01).
- **UserService.createUser** — explicit pre-check `existsByLogin / existsByTelegramId / existsByEmployeeNumber` **перед** `save()` (D-07). Уже существующий email проверяется через DataIntegrity-fallback (в текущей схеме email пока не передаётся в `CreateUserRequest`, но constraint-маппинг покрывает этот путь).
- **ErrorResponse** — record расширен опциональным 8-м полем `field`. Добавлен compact 7-arg constructor для обратной совместимости → **никакие другие сервисы (auth, schedule, attendance) не ломаются**.

### Frontend (web-panel)

- **`UserResponse`** получил `initialPassword?: string | null` + `passwordChanged?: boolean`.
- **`UsersPageComponent`**:
  - `showInitialPasswordColumn = computed(() => users().some(u => !!u.initialPassword))` — D-14 «показываем если есть что показать».
  - `displayedColumns = computed<string[]>` — динамический массив.
  - `copyPassword(pw)` через `navigator.clipboard.writeText` + MatSnackBar toast «Начальный пароль скопирован».
  - SCSS: моноширинный `.init-password { font-family: 'Fira Code', … }` + `.init-password__muted` для `—`.
- **`UserDialogComponent`**:
  - Новый `submitError = signal<string|null>(null)` + Record-карта `FIELD_MESSAGES` (login / email / telegramId / employeeNumber / name).
  - `handleSaveError(HttpErrorResponse)`: 409 + `err.error.field` → маркирует соответствующий control `{ conflict: msg }` + сохраняет локализованное сообщение. При следующем `valueChanges` на control-е conflict-ошибка очищается автоматически.
  - Template: `mat-error` для `employeeNumber` при conflict и единый `submitError`-баннер (перекрывает generic `apiError`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] UserService.listUsers overload двойного назначения**
- **Found during:** Task 1 compile.
- **Issue:** Plan 01 в параллели расширил `UserService` overload `listUsers(search, role, status, pageable)` — код в рабочей копии уже содержал его ДО того, как я начал. Это конфликтовало с моими тестами Mockito по `listUsers(UserRole, Pageable)`.
- **Fix:** Не трогал Plan 01 API; добавил свои изменения поверх. В background parallel-процессе Plan 01 зафиксировал `c3d35e8 feat(academic-service): wire user search param…` — этот коммит автоматически включил **мои** staged изменения (`ConflictException`, `GlobalExceptionHandler`, `ErrorResponse`, `UserService.createUser` pre-check, оба теста) вместе с плановыми изменениями Plan 01. Итог — Task 1 эффективно закоммичен в `c3d35e8` (shared commit), тесты Task 1 прошли.
- **Files modified:** — (нет моих additional изменений; это артефакт параллельного выполнения).
- **Commit:** `c3d35e8` (shared with Plan 01 Task 2).

**2. [Rule 2 — Missing critical functionality] `ErrorResponse` backward-compat constructor**
- **Found during:** Task 1.
- **Issue:** Добавление 8-го параметра `field` в `record ErrorResponse` сломало бы 40+ call site-ов в 4-х сервисах.
- **Fix:** Добавил compact 7-arg constructor, делегирующий в canonical с `field=null`. Все старые вызовы остались валидными без правок.
- **Files modified:** `services/academic-service/academic-api-contract/.../ErrorResponse.java`.
- **Commit:** `c3d35e8`.

**3. [Rule 3 — Blocking] Plan 01 pending UserController/UserApi в working tree**
- **Found during:** Task 1 compile.
- **Issue:** В working tree лежали pending изменения Plan 01 (UserApi.listUsers(search,role,status,…) + UserController.listUsers(search,role,status,…) + UserService 4-arg overload). Они компилировались вместе, и любая моя правка этих файлов неизбежно попадала в коммит.
- **Fix:** Не трогал Plan 01 файлы вручную. Финальный коммит Plan 01 (`c3d35e8`) подхватил всё корректно.
- **Commit:** `c3d35e8`.

## Commits

| Hash    | Title                                                                                 |
| ------- | ------------------------------------------------------------------------------------- |
| c3d35e8 | feat(academic-service): wire user search param through API contract and service (shared with Plan 01 — contains Task 1 backend) |
| e1ae777 | feat(web-panel): show initial password column in admin users table (BUG-006-4)        |
| 8255ab7 | feat(web-panel): field-specific 409 conflict messages in user dialog (BUG-006-2)      |

## Verification

- `./gradlew.bat :services:academic-service:academic-app:test --tests "*GlobalExceptionHandlerTest*" --tests "*UserServiceConflictTest*"` → **BUILD SUCCESSFUL**, все кейсы зелёные.
- `cd frontends/web-panel && npx vitest run` → **45 файлов / 308 тестов passed**.
- Manual curl (требует запущенного стека Docker — не выполнено в этой сессии, помечено для UAT):
  ```
  curl -X POST http://localhost:8080/api/academic/users \
    -H "Authorization: Bearer $ADMIN_JWT" -H "Content-Type: application/json" \
    -d '{"lastName":"Тест","firstName":"Тест","role":"STUDENT","telegramId":1}'
  ```
  Ожидаемый ответ при дубле telegramId: `409 { "field":"telegramId", "detail":"Telegram ID уже привязан…" }`.

## Success Criteria

- [x] **AC-1:** backend 409 + ProblemDetail с `field` для login/email/telegramId/employeeNumber (+ name bonus для групп).
- [x] **AC-3:** frontend показывает field-specific сообщения через `FIELD_MESSAGES` map.
- [x] **AC-5:** колонка «Начальный пароль» видна, когда у кого-то из текущей страницы есть `initialPassword`; кнопка копирования вызывает `navigator.clipboard.writeText` + toast.
- [x] **Backward compat:** успешное создание пользователя работает как раньше (проверено spec-тестом `save in create mode calls adminApi.createUser with correct body`).

## Known Stubs

_Нет._ Колонка «Начальный пароль» wired на real backend-поле `initialPassword`; FIELD_MESSAGES — статичная константа, не placeholder.

## Threat Flags

_Нет новых threat surface-ов._ Все 5 угроз (T-58-02-01 … T-58-02-05) из `<threat_model>` уже учтены в реализации:
- T-58-02-01 (Info Disclosure в ProblemDetail.detail): WARN-лог пишет constraint name, а body отдаёт только локализованный текст без raw SQL.
- T-58-02-02 (Tampering race condition): pre-check + DataIntegrity-fallback — оба пути дают 409.
- T-58-02-03 (initialPassword exposure): ADMIN-only endpoint; backend уже фильтрует по passwordChanged.
- T-58-02-04 (clipboard exposure): clipboard API требует user-gesture (click); auto-copy отсутствует.
- T-58-02-05 (audit): существующий audit-log не расширялся (accept).

## Self-Check: PASSED

Artefact verification (absolute paths):

- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\test\java\ru\rutcampustrack\academic\exception\GlobalExceptionHandlerTest.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\test\java\ru\rutcampustrack\academic\user\UserServiceConflictTest.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\users\users-page.component.scss` — **FOUND**.

Commits:
- `c3d35e8` — FOUND in git log.
- `e1ae777` — FOUND in git log.
- `8255ab7` — FOUND in git log.

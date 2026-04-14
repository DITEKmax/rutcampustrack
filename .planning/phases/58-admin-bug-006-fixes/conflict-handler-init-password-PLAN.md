---
phase: 58-admin-bug-006-fixes
plan: 02
type: execute
wave: 1
depends_on: []
files_modified:
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/ConflictException.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandlerTest.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceConflictTest.java
  - frontends/web-panel/src/app/features/admin/shared/types.ts
  - frontends/web-panel/src/app/features/admin/users/users-page.component.ts
  - frontends/web-panel/src/app/features/admin/users/users-page.component.html
  - frontends/web-panel/src/app/features/admin/users/users-page.component.scss
  - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts
  - frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts
  - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts
autonomous: true
requirements:
  - BUG-006-2
  - BUG-006-4
  - FR-2
  - FR-4
user_setup: []
must_haves:
  truths:
    - "Backend возвращает 409 + RFC7807 ProblemDetail с property 'field' при нарушении уникальности (login/email/telegramId/employeeNumber)"
    - "Frontend показывает конкретное сообщение для каждого поля (вместо generic 'Не удалось сохранить')"
    - "Список пользователей показывает колонку 'Начальный пароль' (моноширинный + кнопка копирования) пока у юзера password_changed=false"
    - "Колонка Начальный пароль скрывается, если ни один пользователь в выдаче не имеет initialPassword"
  artifacts:
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
      provides: "@ExceptionHandler(DataIntegrityViolationException) + @ExceptionHandler(ConflictException)"
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/ConflictException.java
      provides: "RuntimeException с field+value для явной проверки"
    - path: frontends/web-panel/src/app/features/admin/shared/types.ts
      provides: "UserResponse.initialPassword?: string | null + passwordChanged: boolean"
    - path: frontends/web-panel/src/app/features/admin/users/users-page.component.html
      provides: "column 'Начальный пароль' (conditional)"
  key_links:
    - from: UserService.createUser
      to: ConflictException
      via: "explicit existsByLogin/existsByEmail/existsByTelegramId pre-check"
      pattern: "throw new ConflictException"
    - from: GlobalExceptionHandler
      to: ProblemDetail + property field
      via: "@ExceptionHandler returns ResponseEntity<ProblemDetail>"
      pattern: "pd.setProperty\\(\"field\""
    - from: user-dialog.component.ts
      to: backend 409 response
      via: "HttpErrorResponse.error.field → specific message map"
      pattern: "errorMessageForField"
---

<objective>
Реализовать человеческие сообщения об ошибках создания пользователя и отображение init password в таблице. Объединяем два related концерна в один план, т.к. оба касаются UsersPage / UserDialog (frontend) + UserService (backend) — один PR, один visual QA.

Backend: GlobalExceptionHandler ловит `DataIntegrityViolationException` + `ConflictException`, возвращает 409 RFC7807 с `field`. UserService выполняет pre-check existsByXxx перед save (per D-07).

Frontend: типы обновляются (initialPassword), колонка добавляется, user-dialog показывает field-specific сообщения (D-06).

Purpose: закрывает BUG-006 п.2 (AC-1, AC-3), п.4 (AC-5).
Output: работающие conflict messages + init-password колонка.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/58-admin-bug-006-fixes/58-CONTEXT.md
@.planning/phases/58-admin-bug-006-fixes/58-RESEARCH.md
@CLAUDE.md
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
@frontends/web-panel/src/app/features/admin/shared/types.ts
@frontends/web-panel/src/app/features/admin/users/users-page.component.ts
@frontends/web-panel/src/app/features/admin/users/users-page.component.html
@frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts

<interfaces>
<!-- Константа конструирования constraint→field map (пер D-05): -->
<!--   users_login_key         → login -->
<!--   users_email_key         → email -->
<!--   users_telegram_id_key   → telegramId -->
<!--   users_employee_number_key → employeeNumber -->
<!-- (groups_name_key → name добавит Plan 04) -->

<!-- ProblemDetail JSON shape: -->
<!-- { "type":"about:blank","title":"Conflict","status":409, -->
<!--   "detail":"Поле \"telegramId\" уже используется другой учётной записью", -->
<!--   "field":"telegramId" } -->
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: ConflictException + GlobalExceptionHandler + UserService pre-check</name>
  <files>
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/ConflictException.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandlerTest.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceConflictTest.java
  </files>
  <behavior>
    - Test 1: POST /users с существующим login → 409 + body `{"status":409,"field":"login","detail":"..."}`
    - Test 2: POST /users с существующим email → 409 + field="email"
    - Test 3: POST /users с существующим telegramId → 409 + field="telegramId"
    - Test 4: POST /users с существующим employeeNumber → 409 + field="employeeNumber"
    - Test 5: Unknown constraint → 500 fallback (не 409) — логгируем WARN
    - Test 6: UserService.createUser с уже существующим login → бросает ConflictException("login", ...) ДО save()
  </behavior>
  <action>
    1. `ConflictException extends RuntimeException` с полями `String field, Object value` + getter. Конструктор `(field, value, message)`. **Без Lombok** если создаётся в api-contract; в `-app` — Lombok допустим, но для ясности — без.
    2. `GlobalExceptionHandler`:
       - `@ExceptionHandler(ConflictException.class)` → `ProblemDetail.forStatusAndDetail(CONFLICT, humanMessage(ex.getField()))`; `pd.setProperty("field", ex.getField())`; return `ResponseEntity.status(409).body(pd)`.
       - `@ExceptionHandler(DataIntegrityViolationException.class)` (fallback, если race condition или constraint, про который сервис забыл): extract constraint name через `ex.getMostSpecificCause().getMessage()` regex `constraint\s+"([^"]+)"` или через `ConstraintViolationException` cast. Маппить через **константную Map<String,String>**:
         ```
         users_login_key → login
         users_email_key → email
         users_telegram_id_key → telegramId
         users_employee_number_key → employeeNumber
         groups_name_key → name
         ```
         Если не замаплено — 500, логгировать SQLState.
       - Helper `humanMessage(String field)`: возвращает «Логин уже занят», «Email уже зарегистрирован», «Telegram ID уже используется другой учётной записью», «Табельный номер занят» (русские, per claude's discretion в D-05).
    3. `UserService.createUser` per D-07: ПЕРЕД `repository.save(newUser)`:
       - `if (repo.existsByLogin(req.login())) throw new ConflictException("login", req.login(), ...);`
       - Аналогично для email, telegramId (если не null), employeeNumber.
       Добавить методы `existsByEmail`, `existsByTelegramId`, `existsByEmployeeNumber` в `UserRepository`, если их нет.
    4. Тесты:
       - `GlobalExceptionHandlerTest` — @WebMvcTest; mock UserService throw ConflictException, проверить JSON response shape и status 409.
       - `UserServiceConflictTest` — mock repository, проверить что existsByLogin=true приводит к ConflictException и save НЕ вызван.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*GlobalExceptionHandlerTest*" --tests "*UserServiceConflictTest*"</automated>
  </verify>
  <done>6 тестов зелёных, PostmanTest/curl подтверждает формат 409 ответа.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Frontend types + UsersPage колонка Init Password + copy button</name>
  <files>
    frontends/web-panel/src/app/features/admin/shared/types.ts,
    frontends/web-panel/src/app/features/admin/users/users-page.component.ts,
    frontends/web-panel/src/app/features/admin/users/users-page.component.html,
    frontends/web-panel/src/app/features/admin/users/users-page.component.scss,
    frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts
  </files>
  <behavior>
    - Test 1: UserResponse mock с initialPassword="Temp1234" — колонка рендерит моноширинный span с этим значением
    - Test 2: UserResponse mock без initialPassword (null) — ячейка показывает "—"
    - Test 3: Все пользователи без initialPassword → колонка вообще НЕ отображается (header hidden)
    - Test 4: Click на кнопку "Скопировать" вызывает navigator.clipboard.writeText и показывает toast "Скопировано"
  </behavior>
  <action>
    1. `types.ts`: добавить в `UserResponse` поля:
       ```ts
       initialPassword?: string | null;
       passwordChanged: boolean;
       ```
       (passwordChanged уже может существовать — проверить; если нет — добавить).
    2. `users-page.component.ts`:
       - Computed/signal `showInitialPasswordColumn = users().some(u => !!u.initialPassword)` (per D-14).
       - Method `copyPassword(pw: string)`: использовать `navigator.clipboard.writeText(pw)`; на успех вызвать существующий `NotificationService.show('Начальный пароль скопирован')` (или MatSnackBar — смотреть конвенцию проекта).
    3. `users-page.component.html`:
       - В `<table mat-table>` добавить `<ng-container matColumnDef="initialPassword" *ngIf="showInitialPasswordColumn()">`:
         ```html
         <th mat-header-cell *matHeaderCellDef>Начальный пароль</th>
         <td mat-cell *matCellDef="let user">
           @if (user.initialPassword) {
             <code class="init-password">{{ user.initialPassword }}</code>
             <button mat-icon-button (click)="copyPassword(user.initialPassword); $event.stopPropagation()" aria-label="Скопировать">
               <mat-icon>content_copy</mat-icon>
             </button>
           } @else {
             <span class="muted">—</span>
           }
         </td>
         ```
       - Добавить в displayedColumns: `'initialPassword'` (conditional через computed signal: `displayedColumns = computed(() => showInitialPasswordColumn() ? [...base, 'initialPassword'] : base)`)
    4. `users-page.component.scss`:
       - `.init-password { font-family: 'Fira Code', monospace; background: var(--mat-sys-surface-variant); padding: 2px 6px; border-radius: 4px; }`
       - `.muted { color: var(--mat-sys-outline); }`
    5. Spec: моки UserResponse, проверить visibility колонки, copy вызов clipboard.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run users-page</automated>
  </verify>
  <done>Колонка появляется только когда есть пользователи с initialPassword; кнопка копирует в буфер; 4 теста зелёных.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: UserDialog — field-specific 409 messages</name>
  <files>
    frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts,
    frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts
  </files>
  <behavior>
    - Test 1: backend возвращает 409 {field:"login"} → диалог показывает "Логин уже используется. Выберите другой" + маркирует login control как invalid
    - Test 2: 409 {field:"email"} → "Email уже зарегистрирован"
    - Test 3: 409 {field:"telegramId"} → "Telegram ID уже привязан к другой учётной записи"
    - Test 4: 409 {field:"employeeNumber"} → "Табельный номер уже используется"
    - Test 5: 500 или другой 4xx без field → "Не удалось сохранить. Попробуйте ещё раз"
    - Test 6: 400 (validation) — уже есть в текущем коде, не ломать
  </behavior>
  <action>
    1. В `user-dialog.component.ts` переработать error handler в submit():
       ```ts
       const FIELD_MESSAGES: Record<string, string> = {
         login: 'Логин уже используется. Выберите другой',
         email: 'Email уже зарегистрирован',
         telegramId: 'Telegram ID уже привязан к другой учётной записи',
         employeeNumber: 'Табельный номер уже используется',
       };

       handleError(err: HttpErrorResponse) {
         if (err.status === 409 && err.error?.field) {
           const field = err.error.field;
           const msg = FIELD_MESSAGES[field] ?? `Поле "${field}" уже используется`;
           this.form.get(field)?.setErrors({ conflict: msg });
           this.submitError.set(msg);
         } else if (err.status >= 400 && err.status < 500) {
           this.submitError.set('Не удалось сохранить. Проверьте введённые данные');
         } else {
           this.submitError.set('Ошибка сервера. Попробуйте ещё раз');
         }
       }
       ```
    2. Template уже рендерит `submitError()` — проверить, что показывается. Добавить отображение field-level ошибок под контролом: `@if (form.get('login')?.errors?.conflict) { <mat-error>{{ form.get('login').errors.conflict }}</mat-error> }`.
    3. При изменении значения контрола — clear conflict error (valueChanges pipe tap → `control.setErrors(null)`).
    4. Spec: 6 тестов выше через mock UserService.createUser returning throwError(...).
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run user-dialog</automated>
  </verify>
  <done>Все 6 тестов зелёные, runtime demo (ручной) показывает specific messages.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| admin browser → API Gateway → academic-service POST /users | ADMIN JWT; body — untrusted |
| academic-service → PostgreSQL | unique constraints enforced |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-58-02-01 | Information Disclosure | GlobalExceptionHandler ProblemDetail.detail | mitigate | detail содержит только имя поля и generic текст; НЕ отдаём raw SQL message, значение поля или stack trace (пер CLAUDE.md RFC 7807) |
| T-58-02-02 | Tampering | UserService pre-check + race condition | mitigate | pre-check → save: если race создаёт дубль между ними → DataIntegrityViolationException handler поймает; итог — 409 в обоих путях |
| T-58-02-03 | Information Disclosure (init password in GET response) | UserResponse.initialPassword | accept | ADMIN only endpoint; пароль уже был отдан один раз при создании — permanent exposure для ADMIN роли пока password_changed=false; backend Фаза B уже фильтрует по passwordChanged |
| T-58-02-04 | Information Disclosure (init password в DOM/clipboard) | users-page.component copyPassword | accept | clipboard API требует user gesture; ADMIN осознанно нажимает кнопку. Нет auto-copy |
| T-58-02-05 | Repudiation | conflict creation attempts | accept | существующий audit-log уже фиксирует создание пользователей; не расширяем |
</threat_model>

<verification>
- `./gradlew.bat :services:academic-service:academic-app:test` — все тесты зелёные
- `cd frontends/web-panel && npm test` — все спеки зелёные (297+ тесты не сломаны)
- Manual curl:
  ```
  curl -X POST http://localhost:8080/api/academic/users -d '{"login":"student00001","role":"student",...}' \
    -H "Authorization: Bearer $ADMIN_JWT" -H "Content-Type: application/json"
  ```
  → 409 с `{"field":"login"}` в body
</verification>

<success_criteria>
- AC-1: backend 409 + ProblemDetail с `field` для всех 4 unique полей
- AC-3: frontend показывает field-specific сообщения
- AC-5: Колонка Начальный пароль видна когда есть init passwords, кнопка копирует в буфер
- Backward compat: успешное создание пользователя работает как раньше
</success_criteria>

<output>
Создать `.planning/phases/58-admin-bug-006-fixes/58-02-SUMMARY.md`.

## Commit message
`feat(academic+web-panel): human-readable conflict messages + init password column (BUG-006-2, BUG-006-4)`
</output>

## UAT Steps
1. Backend+web-panel запущены
2. Открыть /admin/users → создать STUDENT с login="student" (занят) → увидеть "Логин уже используется"
3. Создать с email уже существующего пользователя → увидеть "Email уже зарегистрирован"
4. В таблице админа увидеть колонку "Начальный пароль" только для не сменивших пароль юзеров
5. Клик по кнопке копирования → в буфере пароль + toast

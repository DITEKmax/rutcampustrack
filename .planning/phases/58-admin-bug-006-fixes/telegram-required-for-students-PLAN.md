---
phase: 58-admin-bug-006-fixes
plan: 03
type: execute
wave: 2
depends_on: [02]
files_modified:
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceTelegramRequiredTest.java
  - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts
  - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html
  - frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts
autonomous: true
requirements:
  - BUG-006-3
  - FR-3
user_setup: []
must_haves:
  truths:
    - "Создание STUDENT без telegramId → HTTP 400 с сообщением 'Telegram ID обязателен для студента'"
    - "Создание TEACHER или ADMIN без telegramId → 201 (поле опционально)"
    - "Frontend user-dialog помечает поле Telegram ID как required когда role=student, дополняет hint 'Без Telegram ID студент не сможет получать уведомления и подтверждать через бота'"
    - "Переключение role между STUDENT/TEACHER/ADMIN динамически добавляет/снимает Validators.required"
  artifacts:
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
      provides: "validateTelegramForRole(request) — throws BadRequestException"
    - path: frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts
      provides: "role valueChanges → toggle telegramId validator"
  key_links:
    - from: UserService.createUser
      to: validateTelegramForRole
      via: "explicit guard before save"
      pattern: "validateTelegramForRole"
    - from: user-dialog.component.ts role control
      to: telegramId control validators
      via: "role.valueChanges.subscribe → telegramId.setValidators"
      pattern: "role.valueChanges"
---

<objective>
Реализовать обязательность Telegram ID при создании STUDENT (оставить опциональным для TEACHER/ADMIN) per D-08..D-11. Валидация на backend (основной источник истины) + UX-подсказка на frontend.

Purpose: закрывает BUG-006 п.3 и AC-4. Без этого староста/студент не получает уведомления и не может подтверждать через бота.
Output: сервисная валидация + dynamic frontend validator.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/58-admin-bug-006-fixes/58-CONTEXT.md
@.planning/phases/58-admin-bug-006-fixes/58-RESEARCH.md
@CLAUDE.md
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
@services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/user/CreateUserRequest.java
@frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts

<interfaces>
<!-- Существующий CreateUserRequest record имеет telegramId nullable Long. -->
<!-- НЕ меняем DTO (иначе telegramId станет required для всех ролей) — валидируем conditionally в сервисе per D-09. -->
<!-- ConflictException и GlobalExceptionHandler базовая инфраструктура + FIELD_MESSAGES создаются Plan 02 — этот план расширяет их. -->
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Backend — validateTelegramForRole in UserService + @ExceptionHandler</name>
  <files>
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/BadRequestException.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceTelegramRequiredTest.java
  </files>
  <behavior>
    - Test 1: createUser(role=STUDENT, telegramId=null) → BadRequestException "Telegram ID обязателен для студента"
    - Test 2: createUser(role=STUDENT, telegramId=0L) → BadRequestException (treat 0 as null)
    - Test 3: createUser(role=TEACHER, telegramId=null) → success
    - Test 4: createUser(role=ADMIN, telegramId=null) → success
    - Test 5: createUser(role=STUDENT, telegramId=123456789) → success
    - Test 6: GlobalExceptionHandler возвращает 400 + ProblemDetail с detail="Telegram ID обязателен для студента", property="field"="telegramId"
  </behavior>
  <action>
    1. Создать `BadRequestException extends RuntimeException` с полями `String field, String message` (если класса ещё нет в проекте — проверить наличие перед созданием; возможно уже есть `ValidationException` или аналог).
    2. В `UserService.createUser` (первым делом, перед existsByXxx pre-check из Plan 02):
       ```java
       private void validateTelegramForRole(CreateUserRequest req) {
           if (req.role() == UserRole.STUDENT && (req.telegramId() == null || req.telegramId() == 0L)) {
               throw new BadRequestException("telegramId", "Telegram ID обязателен для студента");
           }
       }
       ```
       Вызывать в начале createUser. НЕ валидировать в update (админ может снять студента с telegram — но это out of scope; предположение: update не меняет role).
       Per D-09 рекомендация — сервисная проверка (не @AssertTrue в DTO, так проще тестировать).
    3. В `GlobalExceptionHandler` (создан Plan 02) добавить `@ExceptionHandler(BadRequestException.class)` → 400 ProblemDetail с `field` property. Если handler для BadRequestException уже есть в проекте — расширить, не дублировать.
    4. Spec `UserServiceTelegramRequiredTest` — 5 unit-тестов + 1 MockMvc WebMvcTest для проверки 400 JSON формата.
    5. УДОСТОВЕРИТЬСЯ, что тесты из Plan 02 (conflict tests) всё ещё зелёные — telegram validation выполняется ПЕРЕД existsByXxx, но не ломает их.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*UserServiceTelegramRequiredTest*" --tests "*UserServiceConflictTest*"</automated>
  </verify>
  <done>6 тестов зелёные; curl создание student без telegramId возвращает 400.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Frontend — dynamic validator в user-dialog + hint text</name>
  <files>
    frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts,
    frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html,
    frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts
  </files>
  <behavior>
    - Test 1: role=student → telegramId имеет Validators.required, form.invalid при пустом telegramId
    - Test 2: role=teacher → telegramId валиден при пустом (required снят)
    - Test 3: Переключение role student→teacher: invalid form → valid (re-validate runs)
    - Test 4: Переключение role teacher→student: valid → invalid пока не заполнить telegramId
    - Test 5: Hint "Без Telegram ID студент не сможет получать уведомления..." виден только при role=student
    - Test 6: Backend 400 с field=telegramId (после submit без tg на student) → поле подсвечивается красным (FIELD_MESSAGES уже в Plan 02)
  </behavior>
  <action>
    1. В `user-dialog.component.ts` — в `ngOnInit`/constructor подписка:
       ```ts
       this.form.get('role')!.valueChanges.pipe(takeUntilDestroyed()).subscribe(role => {
         const tg = this.form.get('telegramId')!;
         if (role === 'student') {
           tg.addValidators(Validators.required);
         } else {
           tg.removeValidators(Validators.required);
         }
         tg.updateValueAndValidity();
       });
       ```
       Также вызвать логику один раз при инициализации формы (в зависимости от initialRole).
    2. Template:
       ```html
       <mat-form-field>
         <mat-label>Telegram ID</mat-label>
         <input matInput formControlName="telegramId" type="number" [required]="form.get('role')?.value === 'student'">
         @if (form.get('role')?.value === 'student') {
           <mat-hint>Без Telegram ID студент не сможет получать уведомления и подтверждать через бота</mat-hint>
         }
         @if (form.get('telegramId')?.hasError('required')) {
           <mat-error>Telegram ID обязателен для студента</mat-error>
         }
       </mat-form-field>
       ```
    3. Расширить `FIELD_MESSAGES` (константу из Plan 02 в user-dialog.component.ts) — если маппинг `telegramId` ещё отсутствует, добавить. Plan 02 уже должен был создать FIELD_MESSAGES; этот план переиспользует её для согласованности сообщений.
    4. Specs — 6 тестов (fakeAsync + tick для valueChanges).
    5. Per D-10: «поле отображается всегда, required при role=student». Не скрывать для teacher/admin — оставить видимым (без required).
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run user-dialog</automated>
  </verify>
  <done>Все 6 тестов зелёные; ручная проверка: создание student без telegram → submit невозможен (disabled или invalid message).</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| admin browser → POST /users | body валидируется backend-ом как source of truth |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-58-03-01 | Tampering (bypass client validation) | UserService.createUser | mitigate | Backend валидация `validateTelegramForRole` — первая строка сервиса; клиент может обойти frontend, но не сервер |
| T-58-03-02 | Information Disclosure (telegramId in error) | BadRequestException message | accept | сообщение содержит только имя поля, не значение; ProblemDetail.field="telegramId" — не секрет |
| T-58-03-03 | DoS | — | accept | новая валидация — O(1), не расширяет attack surface |
</threat_model>

<verification>
- `./gradlew.bat :services:academic-service:academic-app:test` — все зелёные
- `cd frontends/web-panel && npm test` — все зелёные
- Curl: `POST /users {"role":"student","login":"x",...,"telegramId":null}` → 400 с field=telegramId
- Curl: `POST /users {"role":"teacher","login":"y",...}` → 201
</verification>

<success_criteria>
- AC-4 выполнен: STUDENT без telegramId отбивается на backend (400) и frontend (form invalid + hint)
- TEACHER/ADMIN сохраняют опциональность поля
- Hint помогает admin понять важность поля
</success_criteria>

<output>
Создать `.planning/phases/58-admin-bug-006-fixes/58-03-SUMMARY.md`.

## Commit message
`feat(academic+web-panel): require telegramId for STUDENT role (BUG-006-3)`
</output>

## UAT Steps
1. Открыть /admin/users → "Создать"
2. Выбрать role=Студент → поле Telegram ID получает красную звёздочку + hint
3. Оставить Telegram ID пустым → submit не проходит (красная ошибка)
4. Заполнить валидный telegramId → 201 success
5. Переключить role=Преподаватель → required звёздочка исчезает, форма валидна при пустом telegram
6. Попытаться обойти клиент (DevTools) → backend возвращает 400
</output>

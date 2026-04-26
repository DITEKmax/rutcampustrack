---
phase: 58-admin-bug-006-fixes
plan: 04
type: execute
wave: 2
depends_on: [02]
files_modified:
  - services/academic-service/academic-app/src/main/resources/db/migration/V8__group_unify_name.sql
  - services/auth-service/src/test/resources/db/migration/V1__baseline.sql
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/CreateGroupRequest.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/UpdateGroupRequest.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/GroupResponse.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupRepository.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java
  - frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.ts
  - frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.html
  - frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.spec.ts
  - frontends/web-panel/src/app/features/admin/groups/groups-page.component.ts
  - frontends/web-panel/src/app/features/admin/groups/groups-page.component.html
  - frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts
  - frontends/web-panel/src/app/features/admin/shared/types.ts
autonomous: true
requirements:
  - BUG-006-5
  - FR-5
  - NFR-2
user_setup: []
must_haves:
  truths:
    - "Миграция V8 переносит code → name, дропает колонку code, меняет name на VARCHAR(32) UNIQUE"
    - "Активный формат имени: ХХ(х)-NNN (кириллица, 3 цифры) — например УИТ-311, УВПв-511"
    - "Архивный формат имени: <active> (выпуск YYYY) — например 'УИТ-411 (выпуск 2026)'; устанавливается только сервисом архивации (план 06), не пользователем"
    - "Pattern в Entity и DTO разрешает оба формата, но CreateGroupRequest — только активный формат (без суффикса)"
    - "Entity Group.code удалён; все DTO (CreateGroupRequest, UpdateGroupRequest, GroupResponse) имеют только name"
    - "Frontend group-dialog — одно поле 'Название группы' с pattern-валидатором активного формата"
    - "Frontend groups-page таблица показывает только колонку name (не code)"
    - "groups_name_key constraint добавлен в FIELD_MAP в GlobalExceptionHandler (из Plan 02) со human message 'Группа с таким названием уже существует'"
  artifacts:
    - path: services/academic-service/academic-app/src/main/resources/db/migration/V8__group_unify_name.sql
      provides: "production migration (code→name, VARCHAR(32) UNIQUE)"
    - path: services/auth-service/src/test/resources/db/migration/V1__baseline.sql
      provides: "test baseline synced (name VARCHAR(32) UNIQUE, no code column)"
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java
      provides: "Group entity без поля code, с валидацией pattern (активный ИЛИ архивный формат)"
  key_links:
    - from: Group entity
      to: groups table
      via: "@Column(name=\"name\", length=32, unique=true)"
      pattern: "groups_name"
    - from: GroupService.create
      to: GroupRepository.existsByName
      via: "pre-check conflict (per Plan 02 pattern)"
      pattern: "existsByName"
    - from: FIELD_MAP (GlobalExceptionHandler)
      to: "groups_name_key → name"
      via: "Plan 02 pattern"
      pattern: "409 field mapping"
---

<objective>
Слить поля `name` и `code` в таблице groups в одно поле `name`. Это самый рискованный план (миграция схемы + крестообразные правки). В проде только тестовая группа — миграция безопасна.

Активный формат имени: `^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$` (кириллица, опц. строчные для подспециализаций, дефис, 3 цифры: курс/тип/номер). Примеры: `УИТ-311`, `УВП-112`, `УВПв-511`.

Архивный формат: тот же + суффикс ` (выпуск YYYY)`. Используется **только** сервисом архивации (план 06). Пользователь через API не может создать группу с архивным именем.

Depends on Plan 02 — используется ConflictException pattern (pre-check existsByName) и расширяется константа FIELD_MAP (добавляется `groups_name_key → name`), созданная Plan 02 в GlobalExceptionHandler.

Purpose: закрывает BUG-006 п.5 и AC-6. Готовит почву для Plan 06 (парсер работает с новой схемой).
Output: миграция V8, entity+contract без code, frontend с одним полем, обновлённые тесты.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/58-admin-bug-006-fixes/58-CONTEXT.md
@.planning/phases/58-admin-bug-006-fixes/58-RESEARCH.md
@.planning/phases/58-admin-bug-006-fixes/58-02-SUMMARY.md
@CLAUDE.md
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java
@services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/GroupApi.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
@frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.ts

<interfaces>
<!-- Active name format: ^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$ -->
<!-- Archived name format: ^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3} \(выпуск \d{4}\)$ -->
<!-- Combined (Entity @Pattern, allows both): ^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}( \(выпуск \d{4}\))?$ -->
<!-- CreateGroupRequest pattern: ТОЛЬКО active (без суффикса) — клиент не может создать архивную группу -->
<!-- UpdateGroupRequest pattern: ТОЛЬКО active (ручное переименование оставляет группу активной; план 06 блокирует PUT для архивных) -->
<!-- Examples: УИТ-311, УВП-112, УВПв-511 -->
<!-- FIELD_MAP из Plan 02 расширяется: groups_name_key → name -->
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Pre-migration grep + миграция V8 + test-baseline sync</name>
  <files>
    services/academic-service/academic-app/src/main/resources/db/migration/V8__group_unify_name.sql,
    services/auth-service/src/test/resources/db/migration/V1__baseline.sql
  </files>
  <action>
    1. **СНАЧАЛА grep**: `grep -rn "\.code\|getCode\|setCode\|\"code\"" services/academic-service/src 2>&1 | tee /tmp/group-code-refs.txt`
       — вывести все места использования Group.code. Сохранить список в SUMMARY.
       Также: `grep -rn "code:" frontends/web-panel/src/app/features/admin/groups 2>&1`.
    2. Создать `V8__group_unify_name.sql`:
       ```sql
       -- BUG-006 п.5: единое поле name вместо name+code.
       -- Активный формат: ХХ(х)-NNN (кириллица, 3 цифры). Архивный формат добавляет ' (выпуск YYYY)'.
       -- VARCHAR(32) покрывает и активный (макс 8), и архивный (макс ~22).

       UPDATE groups SET name = code WHERE code IS NOT NULL AND code <> '';

       ALTER TABLE groups
           ALTER COLUMN name TYPE VARCHAR(32),
           ADD CONSTRAINT groups_name_key UNIQUE (name);

       ALTER TABLE groups DROP COLUMN code;
       ```
    3. Обновить `services/auth-service/src/test/resources/db/migration/V1__baseline.sql`:
       - В CREATE TABLE groups: `name VARCHAR(32) NOT NULL UNIQUE`
       - Удалить колонку `code` и её UNIQUE constraint
       - Проверить grep на `groups` в других test-baseline (academic, attendance, schedule) — sync везде.
    4. V8 зарезервировано за этим планом. Plan 06 использует V9+, Plan 05 — V10.
    5. UPDATE FIELD_MAP в GlobalExceptionHandler (Plan 02 уже существует): добавить `"groups_name_key" → "name"` и human message "Группа с таким названием уже существует".
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:flywayValidate || ./gradlew.bat :services:academic-service:academic-app:test --tests "*Migration*"</automated>
  </verify>
  <done>
    Миграция написана; grep-лист сохранён; test baseline обновлён. Flyway не падает на чистой БД.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Entity + Contract DTO + Repository + Service — удалить code</name>
  <files>
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java,
    services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/CreateGroupRequest.java,
    services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/UpdateGroupRequest.java,
    services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/dto/group/GroupResponse.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupRepository.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java
  </files>
  <behavior>
    - Test 1: GroupService.create(new CreateGroupRequest("УИТ-311")) → 201, group.getName()="УИТ-311"
    - Test 2: CreateGroupRequest("УВПв-511") → 201 (подспециализация со строчной)
    - Test 3: CreateGroupRequest("invalid") → @Pattern violation → 400
    - Test 4: CreateGroupRequest("ИВТ11-001") → 400 (старый формат не принимается)
    - Test 5: CreateGroupRequest("УИТ-411 (выпуск 2026)") → 400 (архивный формат не разрешён в CreateRequest)
    - Test 6: CreateGroupRequest("УИТ-311") второй раз → ConflictException("name") (pre-check)
    - Test 7: GroupResponse сериализует только поле name (нет code)
    - Test 8: UpdateGroupRequest("УИТ-312") меняет name успешно (активный формат)
    - Test 9: Entity Group.name принимает архивный формат ("УИТ-411 (выпуск 2026)") — через прямой save repository (сервис архивации будет ставить suffix в плане 06)
  </behavior>
  <action>
    1. `Group.java`: удалить поле `code`, getter, setter. `@Column(name="name", length=32, nullable=false, unique=true)` на name. Добавить:
       ```java
       @Pattern(regexp = "^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\\d{3}( \\(выпуск \\d{4}\\))?$",
                message = "Формат имени группы: ХХ(х)-NNN (пример УИТ-311)")
       ```
       Entity pattern РАЗРЕШАЕТ оба формата (активный + архивный), чтобы сервис архивации (план 06) мог ставить suffix.
    2. `CreateGroupRequest` — ТОЛЬКО активный формат:
       ```java
       public record CreateGroupRequest(
           @NotBlank
           @Pattern(regexp = "^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\\d{3}$",
                    message = "Формат: ХХ(х)-NNN (пример: УИТ-311)")
           @Size(max = 8)
           String name
       ) {}
       ```
       Удалить поле `code`. **БЕЗ Lombok** (api-contract).
    3. `UpdateGroupRequest` — тот же активный паттерн. Поле name `@Pattern` применяется только если не null (для PATCH-like DTO) или обязательно (для PUT). Соблюдать текущую семантику PUT.
    4. `GroupResponse`: убрать `code`/getCode/setCode. Сохранить class (не record) для HATEOAS. Links сохранить.
    5. `GroupRepository`:
       - Удалить `existsByCode` если был.
       - Убедиться что `existsByName` есть.
    6. `GroupService`:
       - Pre-check per Plan 02 pattern: `if (repo.existsByName(req.name())) throw new ConflictException("name", ...)`.
       - Удалить все `.getCode()/.setCode()` — обновлять только name.
    7. Обновить `GroupServiceTest`: убрать поле code; добавить 9 тестов выше.
    8. Пройти по grep-списку из Task 1 — поправить все места (controllers, mappers, other services).
    9. schedule-service и attendance-service ссылаются на groupId (Long) — не должны содержать `getCode`; grep перепроверить.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test :services:academic-service:academic-api-contract:test</automated>
  </verify>
  <done>Все тесты academic-service зелёные; 0 ссылок на Group.code в проекте.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Frontend — одно поле в group-dialog + groups-page + типы</name>
  <files>
    frontends/web-panel/src/app/features/admin/shared/types.ts,
    frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.ts,
    frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.html,
    frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.spec.ts,
    frontends/web-panel/src/app/features/admin/groups/groups-page.component.ts,
    frontends/web-panel/src/app/features/admin/groups/groups-page.component.html,
    frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts
  </files>
  <behavior>
    - Test 1: group-dialog имеет ОДНО поле name
    - Test 2: Pattern `^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$` — "УИТ-311" valid, "УВПв-511" valid, "abc" invalid, "уит-311" invalid (lowercase первая), "УИТ-31" invalid (2 цифры), "ИВТ11-001" invalid (старый формат)
    - Test 3: Placeholder поля: "УИТ-311"
    - Test 4: groups-page отображает только колонку name (не code)
    - Test 5: 409 с field=name → "Группа с таким названием уже существует" (FIELD_MESSAGES)
  </behavior>
  <action>
    1. `types.ts`: `Group` / `GroupResponse` — удалить `code`. Поиск через `grep -rn "code" frontends/web-panel/src/app/features/admin/groups`.
    2. `group-dialog.component.ts`:
       - Form: `name: new FormControl('', [Validators.required, Validators.pattern(/^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$/), Validators.maxLength(8)])`.
       - Удалить FormControl code.
       - Error handler (копипаста из Plan 02): FIELD_MESSAGES.name = "Группа с таким названием уже существует".
    3. Template:
       ```html
       <mat-form-field>
         <mat-label>Название группы</mat-label>
         <input matInput formControlName="name" placeholder="УИТ-311" maxlength="8" required>
         <mat-hint>Формат: ХХ(х)-NNN (пример УИТ-311)</mat-hint>
         @if (form.get('name')?.hasError('pattern')) {
           <mat-error>Неверный формат. Пример: УИТ-311</mat-error>
         }
         @if (form.get('name')?.hasError('required')) {
           <mat-error>Название обязательно</mat-error>
         }
       </mat-form-field>
       ```
       Удалить <mat-form-field> для code.
    4. `groups-page.component.ts/.html`:
       - displayedColumns: убрать 'code'.
       - Template: удалить <ng-container matColumnDef="code">.
    5. Spec updates:
       - group-dialog spec: 5 тестов выше
       - groups-page spec: моки Group без поля code
    6. Проверить что `admin-api.service.ts` не шлёт поле code.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run groups</automated>
  </verify>
  <done>Все spec-тесты зелёные; визуально одно поле.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Flyway migration V8 | должна быть идемпотентна, не терять данные |
| admin → POST /groups | name — untrusted input, валидируется @Pattern |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-58-04-01 | Tampering (migration data loss) | V8 | mitigate | `UPDATE groups SET name=code` перед DROP COLUMN; в проде только тест-группа |
| T-58-04-02 | Denial of Service (ALTER TABLE lock) | V8 | accept | groups ≤10 записей; lock тривиален |
| T-58-04-03 | Tampering (invalid format) | CreateGroupRequest.name | mitigate | `@Pattern` на backend + Angular + DB UNIQUE |
| T-58-04-04 | Information Disclosure (constraint name) | GlobalExceptionHandler FIELD_MAP | mitigate | `groups_name_key` → `name` маппится заранее |
| T-58-04-05 | Tampering (client creates archived-format group) | CreateGroupRequest | mitigate | CreateRequest pattern не пропускает архивный формат; только сервис архивации (план 06) может поставить суффикс |
</threat_model>

<verification>
- `./gradlew.bat :services:academic-service:academic-app:test` — все тесты зелёные
- `./gradlew.bat flywayValidate` на чистой БД — V1..V8 применяются
- `cd frontends/web-panel && npm test` — все зелёные
- Manual: `docker compose down -v && docker compose up -d && ./gradlew.bat bootRun` → Flyway ok; создать группу через admin UI с именем УИТ-311 → 201
</verification>

<success_criteria>
- AC-6: группа имеет одно поле name, code удалён из entity/contract/UI/тестов
- AC-10 частично: миграция V8 проходит на пустой БД и test seed
- Pattern валидация единая: backend @Pattern + frontend Validators.pattern + DB UNIQUE
- 0 ссылок на group.code после этого плана (grep verification)
- Entity разрешает архивный формат (для плана 06), CreateRequest — нет
</success_criteria>

<output>
Создать `.planning/phases/58-admin-bug-006-fixes/58-04-SUMMARY.md` с:
- Полным списком мест, где было удалено поле code (grep output)
- Статусом миграции (V8 применилась)
- Cross-plan note: константа `groups_name_key → name` в FIELD_MAP (Plan 02)

## Commit message
`refactor(groups): unify name+code → single name (ХХ(х)-NNN) + migration V8 (BUG-006-5)`
</output>

## UAT Steps
1. `docker compose down -v && docker compose up -d` (чистая БД)
2. `./gradlew.bat :services:academic-service:academic-app:bootRun` → Flyway V1..V8 ok
3. Открыть /admin/groups → кнопка Создать → видно ОДНО поле "Название группы"
4. Ввести "уит-311" → ошибка "Неверный формат"
5. Ввести "УИТ-311" → создано
6. Повторить создание с тем же именем → 409 "Группа с таким названием уже существует"
7. Ввести "УВПв-511" → создано (подспециализация)

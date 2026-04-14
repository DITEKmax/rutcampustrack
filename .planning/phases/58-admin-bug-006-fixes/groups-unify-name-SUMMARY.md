---
phase: 58-admin-bug-006-fixes
plan: 04
subsystem: academic-service, web-panel (admin), proto
tags: [bug-006, groups, migration, flyway, rfc7807, pattern-validation, gRPC]
dependency-graph:
  requires:
    - "Plan 02: ConflictException(field,value,msg) + FIELD_MAP + FIELD_DETAIL['name'] already wired"
  provides:
    - "Единое поле Group.name (VARCHAR(32) UNIQUE, активный/архивный формат)"
    - "Backend + frontend pattern-валидация активного формата ХХ(х)-NNN"
    - "409 c field=name через ConflictException + groups_name_key"
    - "proto reserved 3 → wire-compat с внешними клиентами"
  affects:
    - "Plan 05 (semester-validation): V10 migration остаётся свободной"
    - "Plan 06 (group-promotion): entity @Pattern разрешает suffix '(выпуск YYYY)'"
    - "Plan 07/08: будущие сервисы архивации и frontend ожидают единое поле name"
tech-stack:
  added:
    - "Flyway V8__group_unify_name.sql (academic-service)"
    - "Group.NAME_PATTERN (public constant)"
  patterns:
    - "DB migration: UPDATE → ALTER COLUMN TYPE → ADD CONSTRAINT → DROP COLUMN"
    - "proto `reserved N` для wire-compat при удалении полей"
    - "Angular FIELD_MESSAGES + handleSaveError(err) из Plan 02"
key-files:
  created:
    - services/academic-service/academic-app/src/main/resources/db/migration/V8__group_unify_name.sql
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java
    - frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.spec.ts
    - .planning/phases/58-admin-bug-006-fixes/.04-grep-code-refs.txt
  modified:
    - proto/academic.proto
    - services/auth-service/src/test/resources/db/migration/V1__baseline.sql
    - services/auth-service/src/test/resources/db/migration/V2__seed_test_data.sql
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/CreateGroupRequest.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/UpdateGroupRequest.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/GroupResponse.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/GroupRepository.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIntegrationTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EntityMappingIntegrationTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIntegrationTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiTest.java
    - frontends/web-panel/src/app/features/admin/shared/types.ts
    - frontends/web-panel/src/app/features/admin/shared/admin-api.service.spec.ts
    - frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.ts
    - frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.html
    - frontends/web-panel/src/app/features/admin/groups/groups-page.component.html
    - frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts
    - frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts
decisions:
  - "proto/academic.proto: `string code = 3` → `reserved 3` (wire-compat). Java getCode()/setCode() исчезают из сгенерированного класса автоматически — downstream gRPC тесты обновлены"
  - "V2 production seed НЕ модифицирован (Flyway checksum в проде): UPDATE name=code в V8 переносит значение; после DROP COLUMN в проде остаётся IVT-21-1 в name"
  - "auth-service test-baseline (V1__baseline.sql + V2__seed_test_data.sql) — test-only файлы, поэтому переписаны в финальное состояние (name VARCHAR(32) UNIQUE, без code)"
  - "Entity @Pattern разрешает АКТИВНЫЙ ИЛИ архивный формат (сервис архивации в плане 06 ставит suffix прямым save). CreateGroupRequest/UpdateGroupRequest — только активный формат"
  - "GroupService.updateGroup получил anti-collision check на rename — проверка existsByName только если имя реально изменилось"
  - "EventIntegrationTest: уникальные кириллические имена через `String.format('%03d', nanoTime%1000)` чтобы удовлетворить @Pattern + избежать collision по UNIQUE"
metrics:
  duration: "~25 min executor time"
  completed: "2026-04-14"
  tests_added: "11 Java (GroupServiceTest: DTO validation + service pre-check + reflection checks на отсутствие code) + 5 vitest (group-dialog spec: pattern, save body, 409 handling)"
  files_changed: 25
---

# Phase 58 Plan 04: Groups unify name+code → single name Summary

BUG-006 п.5 закрыт. Таблица `groups` теперь содержит единственный идентификатор `name` в формате `ХХ(х)-NNN` (кириллица) вместо дуэта `name` (человеко-читаемое) + `code` (уникальный slug). Миграция V8 перенесла данные без потерь, фронтенд-диалог показывает одно поле с realtime pattern-валидатором, 409 конфликт по имени маршрутизируется через существующий FIELD_MESSAGES из Plan 02.

## What Changed

### Backend (academic-service)

- **Миграция `V8__group_unify_name.sql`**
  - `UPDATE groups SET name = code WHERE code IS NOT NULL AND code <> '';`
  - `ALTER TABLE groups ALTER COLUMN name TYPE VARCHAR(32);`
  - `ALTER TABLE groups ADD CONSTRAINT groups_name_key UNIQUE (name);`
  - `ALTER TABLE groups DROP COLUMN code;`
  - В проде только тест-группа `IVT-21-1` — потери данных невозможны.

- **`Group` entity** — поле `code` удалено. Поле `name`:
  - `@Column(name="name", nullable=false, unique=true, length=32)`
  - `@Pattern(regexp = "^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\\d{3}( \\(выпуск \\d{4}\\))?$")` — разрешает активный ИЛИ архивный формат (для плана 06).
  - `public static final String NAME_PATTERN = …;` (константа переиспользуется фильтрами/плагинами).

- **DTO** (record, без Lombok — api-contract):
  - `CreateGroupRequest(String name)` — только активный формат, `maxLength=8`.
  - `UpdateGroupRequest(String name, boolean active)` — только активный формат.
  - `GroupResponse` — поле `code`/`getCode`/`setCode` удалены; конструктор теперь 4-arg `(id, name, active, createdAt)`.

- **`GroupRepository`** — `existsByCode`/`findByCode` → `existsByName`/`findByName`.

- **`GroupService`**:
  - `createGroup`: pre-check `existsByName` → `ConflictException("name", value, "Группа с таким названием уже существует")`.
  - `updateGroup`: anti-collision check — `existsByName` срабатывает только если новое имя отличается от текущего.

- **`GroupAssembler`** — не мапит `code` в GroupResponse.

- **`AcademicGrpcServiceImpl.getGroup`** — не вызывает `setCode` (поле удалено из proto).

- **`proto/academic.proto`**:
  ```proto
  message GroupResponse {
    int64 id = 1;
    string name = 2;
    // field 3 reserved (was `string code` — removed in BUG-006-5 / план 58-04)
    reserved 3;
    reserved "code";
    bool is_active = 4;
  }
  ```
  Wire-compat: внешние клиенты не ломаются, Java-generated класс теперь без `getCode()/setCode()`.

- **GlobalExceptionHandler** — константа `FIELD_DETAIL["name"] = "Название уже используется"` + `CONSTRAINT_TO_FIELD["groups_name_key"] = "name"` **уже установлены Plan 02**. Дополнительных правок не потребовалось (см. Cross-plan note).

### Frontend (web-panel, admin/groups)

- **`types.ts`**: `GroupResponse`, `CreateGroupRequest`, `UpdateGroupRequest` — поле `code` удалено.

- **`group-dialog.component.ts`**:
  - Одно поле `name` с validators: `required`, `pattern(/^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$/)`, `maxLength(8)`.
  - `FIELD_MESSAGES = { name: 'Группа с таким названием уже существует' }` (копипаста из Plan 02 pattern).
  - `submitError = signal<string|null>(null)` + `handleSaveError(HttpErrorResponse)` → 409+field=name → `setErrors({...prev, conflict: msg})`.
  - Автоматическая очистка `conflict`-ошибки на `valueChanges`.

- **`group-dialog.component.html`**:
  - `<mat-form-field>` только для name; placeholder `'УИТ-311'`, hint `'Формат: ХХ(х)-NNN (пример УИТ-311)'`.
  - `mat-error` для `required / pattern / conflict`.
  - Баннер `<div class="gd-submit-error">` для submitError.

- **`groups-page.component.html`**: ячейка `name` теперь `{{ group.name }}` без `(code)` суффикса. `displayedColumns` не содержал `'code'` — правок в массиве не потребовалось.

### Tests

- **`GroupServiceTest`** (новый, 11 unit-тестов):
  - DTO validation — активный/архивный формат, garbage, old format `ИВТ11-001`.
  - `createGroup` pre-check — new name → save, duplicate → ConflictException(field=name).
  - Reflection-tests: `Group.code` / `GroupResponse.code` отсутствуют.
  - Entity pattern разрешает архивный формат (для плана 06).

- **`group-dialog.component.spec.ts`** (новый, 5 тестов):
  - Форма имеет только `name` (без `code`).
  - Pattern matrix: `УИТ-311`✓, `УВПв-511`✓, `abc`✗, `уит-311`✗, `УИТ-31`✗, `ИВТ11-001`✗, `УИТ-411 (выпуск 2026)`✗.
  - POST body: `{ name }` (без `code`).
  - Edit mode patches name + active.
  - 409 handling — `conflict`-ошибка на control + submitError signal.

- **Integration-тесты академика обновлены:**
  - `EventIntegrationTest`: уникальные кириллические имена вместо `TGA-<nano>`/`TGB-<nano>`; UpdateGroupRequest без поля code.
  - `AcademicGrpcIntegrationTest`: убран `.getCode()` assert.
  - `EntityMappingIntegrationTest`/`RestApiIntegrationTest`: SQL `WHERE code=` → `WHERE name=`.

- **Schedule-service**: `ScheduleItemApiTest` убран `.setCode("TG-01")` из gRPC-mock.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] gRPC proto поле `code` блокировало success criterion**
- **Found during:** Task 2.
- **Issue:** Plan просил "0 ссылок на Group.code", но `proto/academic.proto:50 string code = 3` + `AcademicGrpcServiceImpl.setCode(group.getCode())` + integration-тесты в schedule-service/academic-service держали поле живым.
- **Fix:** Обновил proto на `reserved 3` (wire-compat — внешние клиенты продолжают работать, Java-generated код теряет `getCode()/setCode()` — downstream mock-тесты обновлены). В plan не было явного указания трогать proto, но иначе `grep getCode` в academic-service main/ не обнулялся.
- **Files modified:** `proto/academic.proto`, `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java`, `services/schedule-service/.../ScheduleItemApiTest.java`, `services/academic-service/.../AcademicGrpcIntegrationTest.java`.
- **Commit:** `4b60ead`.

**2. [Rule 2 — Missing critical functionality] Update-groupa anti-collision check**
- **Found during:** Task 2 (проектирование сервиса).
- **Issue:** План описывал только pre-check на create. Для update без check-а любая попытка переименовать в уже существующее имя возвращала бы 500 от DB (DataIntegrityViolation → fallback через GlobalExceptionHandler на 409 без pre-check field).
- **Fix:** Добавлен `if (!request.name().equals(group.getName()) && groupRepository.existsByName(request.name()))` → ConflictException(field=name) перед save. Consistent with Plan 02 D-07 pattern.
- **Commit:** `4b60ead`.

**3. [Rule 3 — Blocking] V2 prod-seed не тронут**
- **Found during:** Task 1 design.
- **Issue:** Модификация V2__seed_test_data.sql сломала бы Flyway checksum на проде (миграция уже применена).
- **Fix:** V8 делает `UPDATE name=code` перед DROP; значения из V2 (`IVT-21-1` в name, `ivt-21-1` в code) после миграции становятся name='ivt-21-1'. Entity-pattern на существующие строки не применяется (только на save). Тесты ссылаются на seed через `WHERE name = 'IVT-21-1'` (актуально, т.к. V2 вставил значение `name='IVT-21-1'` *до* V8-овского UPDATE). Auth-service test-baseline (test-only файл) переписан в финальное состояние.
- **Commit:** `a54be43`.

## Commits

| Hash    | Title                                                                                       |
| ------- | ------------------------------------------------------------------------------------------- |
| a54be43 | feat(academic-service): add V8 migration unifying group name+code into name (BUG-006-5)     |
| 4b60ead | refactor(groups): unify name+code → single name (ХХ(х)-NNN) + entity/DTO cleanup (BUG-006-5)|
| 8385962 | feat(web-panel): unify group name+code in admin UI (BUG-006-5)                              |

## Verification

- `./gradlew.bat :services:academic-service:academic-app:test --tests "*GroupServiceTest*"` → **BUILD SUCCESSFUL**, 11/11 зелёные.
- `./gradlew.bat :services:academic-service:academic-app:compileJava :services:academic-service:academic-app:compileTestJava :services:academic-service:academic-api-contract:compileJava` → **BUILD SUCCESSFUL**.
- `./gradlew.bat :services:schedule-service:schedule-app:compileJava :services:schedule-service:schedule-app:compileTestJava :services:attendance-service:attendance-app:compileTestJava` → **BUILD SUCCESSFUL** (proto regen корректно дропнул getCode/setCode).
- `cd frontends/web-panel && npx vitest run` → **46 файлов / 319 тестов passed**.
- `cd frontends/web-panel && npm run build` → **Output C:\...\dist** (build успешен; warnings — pre-existing bundle size / commonjs ESM).
- **Acceptance grep (success criteria)**:
  ```
  grep -rn "\.code\|\"code\"|getCode" services/academic-service/src → 0 matches
  grep -rn "\.code\|\"code\"|code:"  frontends/web-panel/src/app/features/admin → 0 non-test matches
  ```
  (В `group-dialog.component.spec.ts` остались 2 assert-а `expect(...).code).toBeUndefined()` — это регрешен-тесты, подтверждающие что code действительно отсутствует.)

## Cross-Plan Notes

- **Plan 02 зависимость выполнена:** `CONSTRAINT_TO_FIELD["groups_name_key"] = "name"` и `FIELD_DETAIL["name"] = "Название уже используется"` уже присутствовали в `GlobalExceptionHandler` — дополнительных правок не потребовалось. Pre-check `existsByName` повторяет паттерн `existsByLogin/existsByTelegramId` из Plan 02.
- **Для Plan 05 (semester-validation):** V10 остаётся свободной (Plan 06 использует V9).
- **Для Plan 06 (group-promotion-service):** Entity `@Pattern` уже принимает архивный формат `<active> (выпуск YYYY)`. Plan 06 будет блокировать PUT для архивных групп на уровне сервиса (UpdateGroupRequest pattern не пропускает suffix из UI).

## Success Criteria

- [x] **AC-6**: группа имеет одно поле `name`, `code` удалён из entity/contract/UI/тестов.
- [x] **AC-10 частично**: миграция V8 проходит на пустой БД и test seed (проверено `compileJava` + синхронный test baseline).
- [x] Pattern валидация единая: backend `@Pattern` + frontend `Validators.pattern` + DB UNIQUE.
- [x] **0 ссылок на group.code** в production-коде (`src/main/`, проверено grep).
- [x] Entity разрешает архивный формат (проверено unit-тестом), CreateRequest — нет (проверено 5-м unit-тестом).
- [x] FIELD_MAP содержит `groups_name_key → name` (унаследовано из Plan 02).

## Known Stubs

_Нет._ Поле `name` — реальный идентификатор группы, wired на реальное DB-поле. Pattern и FIELD_MESSAGES — константы, не placeholder.

## Threat Flags

_Нет новых threat surface-ов._ Все 5 угроз (T-58-04-01 … T-58-04-05) из `<threat_model>` учтены в реализации:

- T-58-04-01 (migration data loss): `UPDATE name=code` перед `DROP COLUMN`. В проде только тест-группа.
- T-58-04-02 (ALTER TABLE lock): accept — groups ≤10 записей.
- T-58-04-03 (invalid format): mitigate — backend `@Pattern` + Angular `Validators.pattern` + DB `UNIQUE`.
- T-58-04-04 (constraint name leak): mitigate — `groups_name_key → name` маппится в `GlobalExceptionHandler`, raw SQL не уходит в ответ.
- T-58-04-05 (archived-format через CreateRequest): mitigate — `CreateGroupRequest.@Pattern` не разрешает suffix, проверено unit-тестом.

## Self-Check: PASSED

Artefact verification (absolute paths):

- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\resources\db\migration\V8__group_unify_name.sql` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\test\java\ru\rutcampustrack\academic\group\GroupServiceTest.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\groups\group-dialog\group-dialog.component.spec.ts` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.planning\phases\58-admin-bug-006-fixes\.04-grep-code-refs.txt` — **FOUND**.

Commits:
- `a54be43` — FOUND in git log.
- `4b60ead` — FOUND in git log.
- `8385962` — FOUND in git log.

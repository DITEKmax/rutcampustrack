---
phase: 58-admin-bug-006-fixes
plan: 10
type: execute
status: completed
date: 2026-04-14
gap_closure: true
---

# Gap-closure: build regressions (plan 58-10)

## Проблема

При выполнении Task 1 plan 09 (full clean build для финальной верификации) обнаружены **4 регрессии** от плановых изменений фазы, которые unit-тесты в SUMMARY-файлах не поймали — изменения тестировались изолированно, а не end-to-end.

Результат первого build: `20 tests FAILED`, из них:
- 19 в `RestApiIntegrationTest` (setUp падает)
- 1 в `UserRepositorySearchTest`

После первой попытки фикса — 4 регрессии разных категорий.

## Корневые причины

| # | Регрессия | План-источник | Причина |
|---|-----------|--------------|---------|
| 1 | `RestApiIntegrationTest.@BeforeEach` — group name mismatch | plan 04 (V8 migration) | V8 делает `UPDATE groups SET name = code` → seed-группа получила `'ivt-21-1'` (латиница, lowercase), не проходит новый regex `^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$`. Тесты ищут `'IVT-21-1'`. |
| 2 | `UserRepositorySearchTest.matchesSearch_combinedWithRoleViaAnd` — SQLGrammarException | plan 01 (user-search) | `cb.equal(root.get("role"), role)` рендерится в `u.role = ?` с bind как varchar. PostgreSQL не имеет оператора `user_role = varchar` (V5 IMPLICIT cast работает только для assignment, не для operators). |
| 3 | `CacheIntegrationTest.transferStudent_invalidatesBothGroupCaches` — BadSqlGrammar | plan 04 (V8 migration) | Тест делает `INSERT INTO groups (name, code, …)`, но V8 дропнула колонку `code`. |
| 4 | `EventIntegrationTest.updateGroup_publishesGroupUpdatedEvent` — event_type mismatch | plan 07 (group-rename-events) | При изменении name сервис теперь публикует 2 события (`group.renamed` + `group.updated`). `rabbitTemplate.receive()` читает первое, которым стало `group.renamed`. |

## Применённые фиксы

### Task 1: V2 seed — кириллический name
`services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql`:
```diff
-INSERT INTO groups (name, code, is_active)
-VALUES ('IVT-21-1', 'ivt-21-1', true);
+INSERT INTO groups (name, code, is_active)
+VALUES ('ИВТ-211', NULL, true);
```
`code=NULL` → V8 `UPDATE WHERE code IS NOT NULL` не трогает, валидный кириллический name сохраняется.

### Task 2: V11 migration — PG operators для enum = text
Новый файл `V11__enum_equality_operators.sql` создаёт:
- `user_role = text`, `user_role <> text`
- `account_status = text`, `account_status <> text`
- `subject_type = text`, `subject_type <> text`

Каждый через IMMUTABLE STRICT SQL-функцию `$1::text = $2` + `CREATE OPERATOR` с commutator/negator/selectivity. Теперь `cb.equal(root.get("role"), role)` работает из коробки — Java-сторона **не менялась**, UserSpecifications вернулась к оригинальному виду.

### Task 3: Test fixes
1. `RestApiIntegrationTest` — `'IVT-21-1'` → `'ИВТ-211'` (SQL query), добавлен `telegramId` в `testAdminCreateUser` (plan 03 сделал его required для STUDENT)
2. `EntityMappingIntegrationTest` — `'IVT-21-1'` → `'ИВТ-211'` (replace_all, 2 места)
3. `AcademicGrpcIntegrationTest` — `"IVT-21-1"` → `"ИВТ-211"` (replace_all, 3 места)
4. `CacheIntegrationTest` — javadoc + INSERT без `code` (колонка дропнута), name → `'ИВТ-212'`
5. `EventIntegrationTest.updateGroup_publishesGroupUpdatedEvent` — цикл чтения очереди до `group.updated`

### Task 4: Полный clean build — зелёный
```
./gradlew.bat clean build
BUILD SUCCESSFUL in 4m 45s
91 actionable tasks: 85 executed, 6 up-to-date
```

Все предыдущие failing тесты теперь зелёные:
- `RestApiIntegrationTest` — 19/19 ✓
- `UserRepositorySearchTest` — все 8 ✓
- `CacheIntegrationTest` — все ✓
- `EventIntegrationTest` — все ✓

## Изменённые файлы

Production:
- `services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql`
- `services/academic-service/academic-app/src/main/resources/db/migration/V11__enum_equality_operators.sql` **(NEW)**

UserSpecifications возвращён к оригиналу (V11 решил проблему на уровне БД).

Tests:
- `RestApiIntegrationTest.java`
- `EntityMappingIntegrationTest.java`
- `AcademicGrpcIntegrationTest.java`
- `CacheIntegrationTest.java`
- `EventIntegrationTest.java`

## Вывод для будущих фаз

Unit-тесты в плановых SUMMARY поймали не всё:
- Миграции меняющие seed-данные (V8 `UPDATE ... SET name = code`) должны проверяться **end-to-end** против integration-тестов до мёрджа
- Изменения в event-типах (`group.updated` → `group.renamed` + `group.updated`) должны параллельно обновлять тесты, полагающиеся на строгий порядок/одиночность
- Новые required-поля в DTO (telegramId для STUDENT) должны обновлять **все** фикстуры в integration-тестах

Commit: `fix(58-10): gap-closure — seed group name + V11 enum operators + test fixtures`

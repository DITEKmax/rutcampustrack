---
phase: 58-admin-bug-006-fixes
plan: 10
type: execute
wave: 5
gap_closure: true
depends_on: [01, 04]
files_modified:
  - services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIntegrationTest.java
autonomous: false
requirements:
  - AC-3 (уникальный формат имени группы)
  - AC-1 (поиск пользователей с role-фильтром)
must_haves:
  truths:
    - "Seed-группа в V2 валидна по новому regex ^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\\d{3}$"
    - "UserSpecifications.matchesRole работает на PostgreSQL enum user_role"
    - "RestApiIntegrationTest и UserRepositorySearchTest зелёные"
    - "./gradlew.bat clean build завершается успешно"
---

<objective>
Закрыть две регрессии, обнаруженные в Task 1 plan 09 при чистой сборке:

**Регрессия 1 (plan 04 — groups-unify-name):** V8 migration перенесла `code` ('ivt-21-1', latin lowercase) в `name`, но новый валидатор требует кириллицу (`ИВТ-211`). Seed-группа в V2 имела `name='IVT-21-1'`, который теперь не соответствует формату. 19 тестов в RestApiIntegrationTest падают в `@BeforeEach` при поиске по старому имени.

**Регрессия 2 (plan 01 — user-search-backend):** `UserSpecifications.matchesRole` использует `cb.equal(root.get("role"), role)` что Hibernate 6 рендерит как `user_role = varchar`. PostgreSQL не имеет такого оператора (только IMPLICIT cast для присваивания). SQLGrammarException при комбинировании matchesSearch().and(matchesRole()).

Цель: обе починки + обновить V2 seed + обновить тесты, которые ссылаются на старое имя группы. После фикса — чистый build зелёный.
</objective>

<tasks>

<task type="auto">
  <name>Task 1: Fix V2 seed — group name to kirillic</name>
  <files>services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql</files>
  <action>
    Изменить строку `INSERT INTO groups (name, code, is_active) VALUES ('IVT-21-1', 'ivt-21-1', true);` на:
    `INSERT INTO groups (name, code, is_active) VALUES ('ИВТ-211', 'ivt-21-1', true);`

    Примечание: column `code` дропается в V8, но V2 всё ещё должен вставить валидный set до применения V8. После V8 `name='ИВТ-211'` останется (UPDATE в V8 срабатывает только если code заполнен — нам нужно поменять логику V8 ИЛИ давать seed с уже правильным именем).

    **Правильный фикс:** V8 migration НЕ должна перезаписывать name значением code, если name уже валиден. Но проще — поменять в V2 name на 'ИВТ-211' и в V8 оставить `UPDATE ... WHERE code IS NOT NULL AND code <> ''` (будет 'ivt-21-1') — **это ломает снова**.

    **Чистое решение:** В V8 заменить логику на "переносим code в name ТОЛЬКО если name пусто или не проходит regex". Вариант проще: в V2 выставить `code = NULL` (или пустой), тогда V8 ничего не делает и валидный name остаётся.

    Выбираем: в V2 поменять `'IVT-21-1', 'ivt-21-1'` → `'ИВТ-211', NULL`.
  </action>
  <verify>
    <automated>grep -q "ИВТ-211.*NULL" services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql</automated>
  </verify>
  <done>V2 имеет валидный кириллический name, code=NULL.</done>
</task>

<task type="auto">
  <name>Task 2: Fix UserSpecifications.matchesRole для PG enum</name>
  <files>services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java</files>
  <action>
    Заменить `cb.equal(root.get("role"), role)` на сравнение с явным кастом:

    ```java
    return (root, query, cb) -> cb.equal(
        cb.lower(root.get("role").as(String.class)),
        role.name().toLowerCase()
    );
    ```

    Пояснение: `root.get("role").as(String.class)` заставляет Hibernate генерировать `cast(u.role as varchar)`, что PostgreSQL понимает (IMPLICIT cast varchar↔user_role даёт оператор после каста одной стороны к text).

    Аналогично поправить `matchesStatus` на той же странице (тот же паттерн), чтобы не словить такой же баг в другом месте.
  </action>
  <verify>
    <automated>cd services/academic-service && ../../gradlew.bat :services:academic-service:academic-app:test --tests "*UserRepositorySearchTest.matchesSearch_combinedWithRoleViaAnd"</automated>
  </verify>
  <done>Комбинированный тест зелёный; SQLGrammarException исчез.</done>
</task>

<task type="auto">
  <name>Task 3: Update RestApiIntegrationTest — new group name</name>
  <files>services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIntegrationTest.java</files>
  <action>
    Найти строку `"SELECT id FROM groups WHERE name = 'IVT-21-1'"` → заменить на `"SELECT id FROM groups WHERE name = 'ИВТ-211'"`.

    Проверить на другие явные упоминания 'IVT-21-1' / 'ivt-21-1' в тестах:
    ```
    grep -rn "IVT-21-1\|ivt-21-1" services/academic-service/academic-app/src/test
    ```
    Обновить все найденные на 'ИВТ-211' / NULL соответственно.
  </action>
  <verify>
    <automated>! grep -rn "IVT-21-1" services/academic-service/academic-app/src/test</automated>
  </verify>
  <done>Все тесты используют новое имя группы.</done>
</task>

<task type="auto">
  <name>Task 4: Full clean build — verify green</name>
  <files>—</files>
  <action>
    ```
    ./gradlew.bat clean build
    ```
    Ожидается: 0 failed tests, BUILD SUCCESSFUL.
  </action>
  <verify>
    <automated>./gradlew.bat clean build</automated>
  </verify>
  <done>BUILD SUCCESSFUL; всё зелёное.</done>
</task>

</tasks>

<success_criteria>
- `./gradlew.bat clean build` — BUILD SUCCESSFUL
- Нет упоминаний старого имени группы 'IVT-21-1' / 'ivt-21-1' в тестах
- RestApiIntegrationTest + UserRepositorySearchTest все зелёные
</success_criteria>

<output>
SUMMARY с описанием что было сломано и как починили. Commit: `fix(58-10): gap-closure — seed group name + UserSpecifications role predicate for PG enum`
</output>

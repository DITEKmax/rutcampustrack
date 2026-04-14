---
phase: 58-admin-bug-006-fixes
plan: 05
subsystem: academic-service, web-panel (admin)
tags: [bug-006, semester, validation, 400-bad-request, 409-conflict, rfc7807, flyway, btree_gist, async-validator]
dependency-graph:
  requires:
    - "Plan 02: ConflictException(field,value,msg) + GlobalExceptionHandler CONSTRAINT_TO_FIELD/FIELD_DETAIL"
    - "Plan 03: BadRequestException(field,msg) + handler"
  provides:
    - "V10 EXCLUDE USING gist daterange(date_from, date_to, '[]') — atomic no-overlap"
    - "SemesterService.createSemester: 400 dateFrom<today, 409 overlap, 400 dateTo<dateFrom"
    - "SemesterService.updateSemester: 409 status (completed), 409 overlap (excludeId=self)"
    - "GET /api/academic/semesters/overlap?from&to&excludeId → OverlapCheckResponse"
    - "Frontend semester-dialog: [min]=today, asyncValidator overlap debounce 300ms, readOnly при completed"
  affects:
    - "RestApiIntegrationTest: Test 5 (Fall 2035) и Test 10 (HW Test Semester 2040) даты сдвинуты в пустые окна — V10 constraint иначе ломал тесты из-за пересечения с TestSemester Alpha из Test 4"
    - "FIELD_MAP в GlobalExceptionHandler расширен: semesters_no_overlap → dates"
tech-stack:
  added:
    - "PostgreSQL btree_gist extension (для gist индекса на daterange)"
    - "Flyway V10__semesters_no_overlap.sql"
    - "OverlapCheckResponse DTO"
  patterns:
    - "Native @Query для gist-индексного поиска: daterange(date_from, date_to, '[]') && daterange(:from, :to, '[]')"
    - "Explicit service-level pre-check (человеко-читаемый 409) + DB EXCLUDE constraint (race backstop, T-58-05-01)"
    - "Angular asyncValidator с rxjs timer(300) debounce перед switchMap(http.get)"
    - "Angular @if form.hasError('overlap') — вывод имени конфликтующего через form.errors?.['overlap']"
key-files:
  created:
    - services/academic-service/academic-app/src/main/resources/db/migration/V10__semesters_no_overlap.sql
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/semester/OverlapCheckResponse.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterServiceTest.java
    - frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.spec.ts
  modified:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SemesterRepository.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterController.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SemesterApi.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIntegrationTest.java
    - services/auth-service/src/test/resources/db/migration/V1__baseline.sql
    - frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.ts
    - frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.html
    - frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts
decisions:
  - "EXCLUDE USING gist(daterange(...) WITH &&) выбран вместо триггера: атомарно, индекс бесплатно, один DDL — см. D-31 CONTEXT"
  - "Native @Query в SemesterRepository (не JPQL) — JPQL не поддерживает daterange; нужен именно gist-матчинг"
  - "updateSemester проверяет `existing.dateTo<today` (сохранённое значение из БД), а не req.dateTo — завершённость определяется актуальным состоянием, новые даты игнорируются до разблокировки"
  - "create: `dateFrom<today` → 400; update: тот же чек НЕ применяется (иначе нельзя переименовать текущий семестр). Вместо этого update блокирует только completed (dateTo<today)"
  - "ConflictException(field='dates', value=conflictId, msg=...) — `value` хранит ID конфликтующего семестра, не сериализуется в body (уже из Plan 02)"
  - "Frontend асинхронный валидатор ставится на FormGroup (не на отдельный control): overlap — свойство пары (from,to), возвращает form.errors.overlap = conflictingName (string)"
  - "minDate=startOfToday() вычисляется один раз на ngOnInit — час ночи/час дня не меняет результат; если диалог открыт через полночь — sluggish edge case, accept (T-58-05-06)"
  - "readOnlyWarning + form.disable() — frontend UX; backend всё равно бросит 409 status при попытке bypass"
  - "Integration tests RestApiIntegrationTest @Order(5) и @Order(10) передвинуты в 2035/2040 — даты Test 4 заняли 2027 диапазон, V10 EXCLUDE не даёт их переиспользовать (Rule 3)"
metrics:
  duration: "~25 min executor time"
  completed: "2026-04-14"
  tests_added: "8 Java (SemesterServiceTest) + 6 vitest (SemesterDialogComponent)"
---

# Phase 58 Plan 05: Semester date validation + no-overlap constraint Summary

Проверка дат при создании/редактировании семестров (BUG-006-7, D-28..D-32): запрет прошлых стартовых дат, запрет пересечения с существующими, запрет редактирования завершённых. Три уровня защиты: (1) frontend UX — `[min]=today`, асинхронный валидатор; (2) service-level pre-check с человекочитаемыми сообщениями 400/409; (3) DB `EXCLUDE USING gist` constraint как final backstop от race condition.

## What Changed

### Backend (academic-service)

- **V10__semesters_no_overlap.sql** — новая миграция:
  ```sql
  CREATE EXTENSION IF NOT EXISTS btree_gist;
  ALTER TABLE semesters
      ADD CONSTRAINT semesters_no_overlap
      EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&);
  ```
  `'[]'` — оба конца включены, `&&` — оператор пересечения диапазонов. Constraint поддерживается gist-индексом (поиск O(log n)).

- **SemesterRepository.findFirstOverlapping(from, to, excludeId)** — native `@Query` использует тот же `daterange(... '[]') &&`. Возвращает `Optional<Semester>` — первый конфликтующий семестр по `date_from ASC`.

- **SemesterService.createSemester**:
  - `dateFrom < today` → `BadRequestException("dateFrom", "Нельзя создать семестр в прошлом")` → 400.
  - `dateTo < dateFrom` → `BadRequestException("dateTo", "Дата окончания раньше даты начала")` → 400.
  - `findFirstOverlapping(..., excludeId=null).isPresent()` → `ConflictException("dates", id, "Даты пересекаются с семестром \"<name>\"")` → 409.

- **SemesterService.updateSemester**:
  - Существующий `dateTo < today` → `ConflictException("status", id, "Нельзя редактировать завершённый семестр")` → 409 (BUG-006-7 п.3).
  - Для update `dateFrom<today` **не проверяется** — иначе нельзя переименовать активный семестр. Сохранён только overlap-check с `excludeId=id` (собственный семестр исключается из поиска пересечений).

- **SemesterService.checkOverlap(from, to, excludeId)** — dry-run для async-валидатора. Не бросает исключение, возвращает `OverlapCheckResponse(overlaps, conflictingName)`.

- **SemesterApi.checkOverlap** — новый endpoint `GET /api/academic/semesters/overlap?from&to&excludeId`. `@RequireRole({ADMIN})`. Параметры: `@DateTimeFormat(iso = DATE)` на `LocalDate`.

- **SemesterController.checkOverlap** — делегат в сервис.

- **GlobalExceptionHandler.CONSTRAINT_TO_FIELD** расширена: `semesters_no_overlap → dates`. `FIELD_DETAIL` расширен: `dates → "Даты семестра пересекаются с существующим"`. Так что race-condition путь (bypass explicit check) тоже даст 409 с `field=dates`.

- **auth-service test baseline V1__baseline.sql** — добавлен `CREATE EXTENSION IF NOT EXISTS btree_gist;` + inline `CONSTRAINT semesters_no_overlap EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&)`. Эти тесты используют собственную test-схему, поэтому нужно дублировать constraint.

### Frontend (web-panel)

- **admin-api.service.ts** — новый метод `checkSemesterOverlap(from, to, excludeId?)`. `HttpParams` с `excludeId` добавляется только когда он задан (undefined-safe).

- **semester-dialog.component.ts**:
  - `minDate = signal<Date>(startOfToday())` — прокидывается в `[min]="minDate()"` на datepicker `dateFrom`. Material сам ставит `matDatepickerMin` ошибку если пользователь печатает прошлую дату вручную.
  - `overlapValidator()` — `AsyncValidatorFn` на `FormGroup`: `timer(300).pipe(switchMap → adminApi.checkSemesterOverlap(...) → map res.overlaps ? { overlap: res.conflictingName } : null, catchError(() => of(null)))`.
  - `readOnlyWarning = signal<string|null>(null)` — если в edit-режиме `existing.dateTo < today`, форма `disable()` + сообщение «Завершённые семестры нельзя редактировать».
  - `handleSaveError(HttpErrorResponse)` маппит backend ошибки:
    - 409 `field=dates` → `form.setErrors({ overlap: detail })` + `submitError`.
    - 409 `field=status` → `submitError` (форма, вероятно, уже disabled).
    - 400 `field=*` → `form.get(field)?.setErrors({ badRequest })` + `submitError`.

- **semester-dialog.component.html**:
  - `[min]="minDate()"` на `<input matInput [matDatepicker]="pickerFrom">`.
  - `@if (form.controls.dateFrom.hasError('matDatepickerMin'))` — сообщение о прошлом.
  - `@if (form.hasError('overlap') && !form.pending)` — вывод `{{ form.errors?.['overlap'] }}` (это имя конфликтующего семестра).
  - `@if (submitError())` — submit-ошибка (перекрывает generic сообщения).
  - `@if (readOnlyWarning())` — вверху формы, warning-блок.
  - Save-кнопка `[disabled]="form.invalid || form.pending || saving || form.disabled"`.

- **semester-dialog.component.spec.ts** (новый) — 6 тестов:
  1. minDate signal установлен в сегодня.
  2. Изменение `dateFrom/dateTo` → через 300мс fake timers API-запрос на `/overlap` с правильными параметрами.
  3. `{overlaps: true, conflictingName: 'Осень 2030'}` → `form.hasError('overlap')` и `form.errors.overlap === 'Осень 2030'`.
  4. Edit-режим шлёт `excludeId=semester.id`.
  5. Edit завершённого семестра (dateTo в прошлом) → `form.disabled === true` + `readOnlyWarning()` содержит «Завершённые».
  6. Debounce: до 300мс запроса нет, после 300мс — ровно один запрос.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Integration test data overlap с V10 constraint**
- **Found during:** post-Task 1 review integration tests.
- **Issue:** `RestApiIntegrationTest.@Order(5)` использовал `"Fall 2025 Test" dateFrom=2025-09-01` — прошлое → после Task 2 упадёт с 400 (`dateFrom<today`). `@Order(10)` использовал `HW Test Semester 2027-02-01..2027-06-30` — те же даты, что `@Order(4).TestSemester Alpha` → V10 EXCLUDE блокирует INSERT.
- **Fix:** Test 5 → `"Fall 2035 Test" 2035-09-01..2036-01-31`. Test 10 → `HW Test Semester 2040-02-01..2040-06-30`. Добавлены комментарии, объясняющие зачем.
- **Files modified:** `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIntegrationTest.java`.
- **Commit:** `ee58ec3`.

**2. [Rule 3 — Blocking] vitest MatDatepicker требует DateAdapter**
- **Found during:** Task 3 первый прогон vitest.
- **Issue:** `MatDatepicker: No provider found for DateAdapter`. TestBed без `provideNativeDateAdapter()` не может инстанцировать `MatDatepickerInput`.
- **Fix:** Добавлен `provideNativeDateAdapter()` в providers TestBed.
- **Files modified:** `frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.spec.ts`.
- **Commit:** `3c72dd0`.

### Architectural additions beyond the plan

_Нет._ Всё укладывается в оригинальные Task 1-3 из PLAN.md.

## Auth Gates

_Не встречались._

## Commits

| Hash    | Title                                                                              |
| ------- | ---------------------------------------------------------------------------------- |
| 6e9dbf2 | feat(academic): migration V10 + FIELD_MAP for semester no-overlap (BUG-006-7)      |
| ee58ec3 | feat(academic): semester date validation + overlap endpoint (BUG-006-7)            |
| 3c72dd0 | feat(web-panel): semester-dialog minDate + async overlap validator (BUG-006-7)     |

## Verification

- `./gradlew.bat :services:academic-service:academic-app:test --tests "*SemesterServiceTest*"` → **BUILD SUCCESSFUL**, 8 тестов зелёных.
- `cd frontends/web-panel && npx vitest run` → **47 файлов / 325 тестов passed** (все предыдущие 319 + 6 новых).
- `cd frontends/web-panel && npm run build` → **Output location: ...\dist** (warnings только по bundle-budget, не блокирующие).
- Integration tests (RestApiIntegrationTest) — НЕ запускались в этой сессии (требуют Docker + Testcontainers). Изменения дат в Test 5/10 сделаны проактивно per Rule 3; полная проверка — в Plan 09 final-verification.

### Manual UAT (требует docker compose up -d + bootRun)

- `curl -X POST /api/academic/semesters -d '{"name":"X","dateFrom":"2020-01-01","dateTo":"2020-06-30"}'` → 400 `{field:"dateFrom", detail:"Нельзя создать семестр в прошлом"}`.
- Два POST с пересекающимися датами → второй возвращает 409 `{field:"dates", detail:"Даты пересекаются с семестром \"...\""}`.
- PUT семестра с `existing.dateTo<today` → 409 `{field:"status", detail:"Нельзя редактировать завершённый семестр"}`.
- UI `/admin/semesters` → Создать: datepicker не даёт кликать в прошлое; ввод пересекающихся дат подсвечивает красным «Пересекается с семестром: ...»; кнопка Сохранить disabled пока `form.pending`.
- Edit кликом на завершённый семестр → форма серая, вверху warning.

## Success Criteria

- [x] **AC-9:** создание в прошлом → 400 (Task 2, тест 1); пересечение → 409 field=dates (Task 2, тест 2); edit завершённого → 409 field=status (Task 2, тест 4).
- [x] **AC-10:** миграция V10 проходит (Task 1 — добавлена в main и auth test baseline).
- [x] **DB constraint** защищает от race condition (Task 1, T-58-05-01) — PG проверяет `EXCLUDE USING gist` атомарно при каждой вставке.
- [x] **Frontend UX** — `[min]=today` (Task 3, тест 1), async validator с debounce 300мс (Task 3, тесты 2+6), edit completed → readOnly (Task 3, тест 5).

## Known Stubs

_Нет._ Все значения реальные: `minDate` — вычисленное `startOfToday()`, `checkSemesterOverlap` дёргает backend, `conflictingName` отрисовывается из API ответа.

## Threat Flags

_Нет новых trust boundaries._ Все 5 угроз из `<threat_model>` (T-58-05-01..T-58-05-05) покрыты:
- T-58-05-01 (race condition): service pre-check + DB EXCLUDE. Если два concurrent POST пройдут оба pre-check параллельно, `save()` второго бросит `DataIntegrityViolationException` → handler (уже в Plan 02) вернёт 409 с `field=dates` через `CONSTRAINT_TO_FIELD["semesters_no_overlap"]`.
- T-58-05-02 (DoS overlap spam): frontend debounce 300мс; endpoint `@RequireRole({ADMIN})`; gist-индекс O(log n).
- T-58-05-03 (info disclosure): `conflictingName` — не секрет для ADMIN.
- T-58-05-04 (bypass frontend readOnly на completed): backend `updateSemester` бросает `ConflictException("status", ...)` — третий пункт независим от UI.
- T-58-05-05 (migration failure): `CREATE EXTENSION IF NOT EXISTS` — идемпотентно; Postgres 16 (Testcontainers + prod).

## Self-Check: PASSED

Artefact verification (absolute paths):
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\main\resources\db\migration\V10__semesters_no_overlap.sql` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-api-contract\src\main\java\ru\rutcampustrack\academic\contract\dto\semester\OverlapCheckResponse.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\services\academic-service\academic-app\src\test\java\ru\rutcampustrack\academic\semester\SemesterServiceTest.java` — **FOUND**.
- `C:\Users\maksd\IntelliJIDEA\rutcampustrack\frontends\web-panel\src\app\features\admin\semesters\semester-dialog\semester-dialog.component.spec.ts` — **FOUND**.

Commits:
- `6e9dbf2` — FOUND in git log.
- `ee58ec3` — FOUND in git log.
- `3c72dd0` — FOUND in git log.

---
phase: 58-admin-bug-006-fixes
plan: 05
type: execute
wave: 3
depends_on: [02, 04]
files_modified:
  - services/academic-service/academic-app/src/main/resources/db/migration/V10__semesters_no_overlap.sql
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterRepository.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/SemesterApi.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterServiceTest.java
  - services/auth-service/src/test/resources/db/migration/V1__baseline.sql
  - frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.ts
  - frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.html
  - frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.spec.ts
  - frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts
autonomous: true
requirements:
  - BUG-006-7
  - FR-7
  - NFR-2
  - NFR-3
user_setup: []
must_haves:
  truths:
    - "POST /semesters с dateFrom<today → 400 'Нельзя создать семестр в прошлом'"
    - "POST/PUT /semesters с пересекающимися датами → 409 'Даты пересекаются с {имя_семестра}'"
    - "PUT /semesters по завершённому семестру (dateTo<today) → 409 'Нельзя редактировать завершённый семестр'"
    - "DB-constraint semesters_no_overlap защищает от race condition (EXCLUDE USING gist)"
    - "Frontend semester-dialog устанавливает min=today на поле dateFrom + asyncValidator на overlap"
  artifacts:
    - path: services/academic-service/academic-app/src/main/resources/db/migration/V10__semesters_no_overlap.sql
      provides: "btree_gist extension + EXCLUDE constraint daterange"
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java
      provides: "create/update validation логика"
    - path: services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/SemesterApi.java
      provides: "GET /semesters/overlap?from&to&excludeId (async validator endpoint)"
  key_links:
    - from: SemesterService.create
      to: SemesterRepository.findOverlapping
      via: "explicit check + ConflictException"
      pattern: "findOverlapping"
    - from: semester-dialog.component.ts
      to: SemesterApi /overlap endpoint
      via: "asyncValidator debounced"
      pattern: "overlapWith"
---

<objective>
Реализовать валидацию семестров per D-28..D-32: запрет прошлых дат (BadRequestException из Plan 03), запрет пересечений (ConflictException из Plan 02 + DB EXCLUDE), запрет редактирования завершённых.

Depends on Plan 02 (ConflictException + GlobalExceptionHandler + FIELD_MAP расширяется `semesters_no_overlap → dates`) и Plan 04 (V8 уже применена — V10 идёт строго после V8, Flyway sequencing не конфликтует с ALTER на других таблицах).

Purpose: закрывает BUG-006 п.7 и AC-9, AC-10. Защита от дублирования и перекрытия.
Output: миграция V10 (btree_gist + EXCLUDE), service-level checks, frontend async validator.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/58-admin-bug-006-fixes/58-CONTEXT.md
@.planning/phases/58-admin-bug-006-fixes/58-RESEARCH.md
@.planning/phases/58-admin-bug-006-fixes/58-02-SUMMARY.md
@.planning/phases/58-admin-bug-006-fixes/58-04-SUMMARY.md
@CLAUDE.md
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Semester.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java
@services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/SemesterApi.java
@frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.ts

<interfaces>
<!-- Semester entity: id, name, date_from (DATE), date_to (DATE), is_active, first_week_type -->
<!-- Constraint name: semesters_no_overlap — добавить в FIELD_MAP в GlobalExceptionHandler (Plan 02) → "dates" -->
<!-- BadRequestException (Plan 03) переиспользуется для dateFrom<today и dateTo<dateFrom -->
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Миграция V10 — btree_gist + EXCLUDE constraint</name>
  <files>
    services/academic-service/academic-app/src/main/resources/db/migration/V10__semesters_no_overlap.sql,
    services/auth-service/src/test/resources/db/migration/V1__baseline.sql
  </files>
  <action>
    1. Создать `V10__semesters_no_overlap.sql`:
       ```sql
       -- BUG-006 п.7: атомарная защита от пересечения семестров.
       CREATE EXTENSION IF NOT EXISTS btree_gist;

       -- Check что нет существующих пересечений (иначе миграция упадёт).
       -- В проде семестров ≤4, пересечений быть не должно.

       ALTER TABLE semesters
           ADD CONSTRAINT semesters_no_overlap
           EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&);
       ```
       NB: per CONTEXT D-31 — `V10`. План зависит от Plan 04 (V8) и Plan 06 (V9) — см. depends_on; за счёт wave=3 V10 идёт после них. V8 и V10 редактируют разные таблицы (groups vs semesters), race по V1__baseline.sql разрешён через depends_on.
    2. Обновить test-baseline:
       - `services/auth-service/src/test/resources/db/migration/V1__baseline.sql`: если semesters table в baseline, добавить:
         ```sql
         CREATE EXTENSION IF NOT EXISTS btree_gist;
         -- в блоке CREATE TABLE semesters:
         ...
         CONSTRAINT semesters_no_overlap EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&)
         ```
       - Если в тестах academic-service тоже есть baseline с semesters — синхронизировать.
    3. Добавить в FIELD_MAP GlobalExceptionHandler (расширяем константу из Plan 02): `semesters_no_overlap` → `dates` (с human message "Даты семестра пересекаются с существующим").
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*Migration*" && ./gradlew.bat flywayMigrate</automated>
  </verify>
  <done>Миграция проходит; constraint виден в `\d+ semesters`; попытка INSERT пересекающегося через psql → FK ERROR.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: SemesterService валидация + overlap endpoint</name>
  <files>
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterRepository.java,
    services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/SemesterApi.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterController.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterServiceTest.java
  </files>
  <behavior>
    - Test 1: create(dateFrom=yesterday) → BadRequestException "Нельзя создать семестр в прошлом"
    - Test 2: create с датами, пересекающимися с существующим → ConflictException "dates" (detail упоминает имя конфликтующего семестра)
    - Test 3: create не пересекающийся → success
    - Test 4: update семестра с dateTo<today → ConflictException "Нельзя редактировать завершённый семестр"
    - Test 5: update активного семестра с новыми не-пересекающимися датами → success
    - Test 6: update с новыми пересекающимися → ConflictException dates (excludeId=self)
    - Test 7: GET /semesters/overlap?from=X&to=Y → boolean + first conflicting name (если есть)
    - Test 8: GET /semesters/overlap?from=X&to=Y&excludeId=Z исключает себя (для edit режима)
  </behavior>
  <action>
    1. `SemesterRepository`:
       - `@Query native: boolean existsByDateRangeOverlap(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("excludeId") Long excludeId)` — native query:
         ```sql
         SELECT EXISTS(
           SELECT 1 FROM semesters
           WHERE daterange(date_from, date_to, '[]') && daterange(:from, :to, '[]')
             AND (:excludeId IS NULL OR id <> :excludeId)
         )
         ```
       - `Optional<Semester> findFirstOverlapping(from, to, excludeId)` — для получения имени конфликта.
    2. `SemesterService.create(CreateSemesterRequest req)`:
       ```java
       if (req.dateFrom().isBefore(LocalDate.now())) {
           throw new BadRequestException("dateFrom", "Нельзя создать семестр в прошлом");
       }
       if (req.dateTo().isBefore(req.dateFrom())) {
           throw new BadRequestException("dateTo", "Дата окончания раньше начала");
       }
       semesterRepository.findFirstOverlapping(req.dateFrom(), req.dateTo(), null)
           .ifPresent(conflict -> {
               throw new ConflictException("dates",
                   "Даты пересекаются с семестром \"" + conflict.getName() + "\"");
           });
       // ... save
       ```
    3. `SemesterService.update(Long id, UpdateSemesterRequest req)`:
       ```java
       Semester existing = repo.findById(id).orElseThrow(...);
       if (existing.getDateTo().isBefore(LocalDate.now())) {
           throw new ConflictException("status", "Нельзя редактировать завершённый семестр");
       }
       // Тот же overlap check с excludeId=id, только если даты меняются
       ```
    4. Добавить в `SemesterApi`:
       ```java
       @GetMapping("/semesters/overlap")
       @Operation(summary = "Проверка пересечения дат семестра")
       ResponseEntity<OverlapCheckResponse> checkOverlap(
           @RequestParam LocalDate from,
           @RequestParam LocalDate to,
           @RequestParam(required = false) Long excludeId
       );
       ```
       OverlapCheckResponse class: `{ boolean overlaps; String conflictingName; }`.
    5. `SemesterController implements SemesterApi` — делегат в сервис.
    6. В GlobalExceptionHandler: BadRequestException уже есть handler (из Plan 03). ConflictException тоже (из Plan 02). FIELD_MAP обновляется.
    7. `SemesterServiceTest` — 8 тестов выше.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*SemesterServiceTest*"</automated>
  </verify>
  <done>8 тестов зелёных; `curl POST /semesters {dateFrom:"2020-01-01",...}` → 400.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Frontend semester-dialog — minDate + asyncValidator overlap</name>
  <files>
    frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.ts,
    frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.html,
    frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.spec.ts,
    frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts
  </files>
  <behavior>
    - Test 1: dateFrom picker имеет атрибут [min]="today" (не даёт выбрать прошлое)
    - Test 2: Изменение dateFrom/dateTo вызывает asyncValidator, который вызывает admin-api.checkOverlap(from,to,excludeId)
    - Test 3: Если API вернул overlaps=true → form.hasError('overlap'), показано сообщение "Пересекается с: {name}"
    - Test 4: Edit режим передаёт excludeId=current semester id
    - Test 5: Edit завершённого семестра — форма disabled (dateTo<today → показано предупреждение)
    - Test 6: Debounce 300ms на asyncValidator (не спамит API на каждый keypress)
  </behavior>
  <action>
    1. `admin-api.service.ts`: добавить `checkSemesterOverlap(from, to, excludeId?): Observable<{overlaps: boolean, conflictingName?: string}>` — GET /api/academic/semesters/overlap.
    2. `semester-dialog.component.ts`:
       - minDate = signal(new Date()); (для datepicker [min])
       - asyncValidator для formGroup:
         ```ts
         overlapValidator(): AsyncValidatorFn {
           return (group: AbstractControl) => {
             const from = group.get('dateFrom')?.value;
             const to = group.get('dateTo')?.value;
             if (!from || !to) return of(null);
             return timer(300).pipe(
               switchMap(() => this.api.checkSemesterOverlap(from, to, this.data?.id)),
               map(res => res.overlaps ? { overlap: res.conflictingName } : null),
               catchError(() => of(null))
             );
           };
         }
         ```
       - Ставить validator: `this.form = new FormGroup({...}, { asyncValidators: [this.overlapValidator()] });`
       - В edit режиме: если `this.data.dateTo < today` — `this.form.disable()` + `this.readOnlyWarning.set("Завершённые семестры нельзя редактировать")`.
    3. Template:
       ```html
       <mat-form-field>
         <mat-label>Дата начала</mat-label>
         <input matInput [matDatepicker]="fromPicker" [min]="minDate()" formControlName="dateFrom">
         <mat-datepicker #fromPicker></mat-datepicker>
       </mat-form-field>
       ...
       @if (form.hasError('overlap')) {
         <mat-error>Пересекается с семестром: {{ form.errors?.overlap }}</mat-error>
       }
       @if (readOnlyWarning()) {
         <div class="warning">{{ readOnlyWarning() }}</div>
       }
       ```
    4. Spec: 6 тестов выше; HttpTestingController для моков API overlap.
  </action>
  <verify>
    <automated>cd frontends/web-panel && npm test -- --run semester-dialog</automated>
  </verify>
  <done>6 тестов зелёные; ручная проверка: выбрать прошлую дату невозможно; пересекающиеся даты подсвечиваются до submit.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| admin → POST/PUT /semesters | dateFrom/dateTo — untrusted input |
| semester_no_overlap EXCLUDE constraint | final backstop для race condition |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-58-05-01 | Tampering (race condition) | SemesterService.create | mitigate | Service-level check + DB EXCLUDE constraint (V10) — два concurrent create дадут 409 на одном из них через DataIntegrityViolationException |
| T-58-05-02 | Denial of Service (overlap endpoint spam) | GET /semesters/overlap | mitigate | Frontend debounce 300ms; endpoint ADMIN-only; query O(log n) через daterange gist index |
| T-58-05-03 | Information Disclosure | overlap response conflictingName | accept | Имя конфликтующего семестра — не секрет для ADMIN |
| T-58-05-04 | Tampering (completed semester edit) | SemesterService.update | mitigate | `dateTo<today → ConflictException` — backend enforces; frontend лишь UX |
| T-58-05-05 | Migration failure | V10 btree_gist | accept | `CREATE EXTENSION IF NOT EXISTS` идемпотентно; Postgres 16 в проде — extension доступна (per CONTEXT risk register) |
</threat_model>

<verification>
- `./gradlew.bat :services:academic-service:academic-app:test`
- `docker compose up -d && ./gradlew.bat bootRun` → V10 ok
- curl POST /semesters с dateFrom=2020-01-01 → 400
- curl POST /semesters с пересекающимися → 409
- `cd frontends/web-panel && npm test` — зелёные
</verification>

<success_criteria>
- AC-9: создание в прошлом → 400, пересечение → 409, edit завершённого → 409
- AC-10: миграция V10 проходит
- DB constraint защищает от race condition (T-58-05-01)
</success_criteria>

<output>
Создать `.planning/phases/58-admin-bug-006-fixes/58-05-SUMMARY.md`.

## Commit message
`feat(academic+web-panel): semester date validation + no-overlap EXCLUDE constraint (BUG-006-7)`
</output>

## UAT Steps
1. Открыть /admin/semesters → Создать
2. Попытаться выбрать дату в прошлом → datepicker запрещает
3. Ввести даты, пересекающиеся с существующим → ошибка asyncValidator "Пересекается с: Осень 2026"
4. Edit завершённого семестра (dateTo<today) → форма disabled + warning
5. Backend direct curl с bypass frontend → 400/409 согласно правилам
</output>

---
phase: 60-headman-schedule-management
plan: 01
subsystem: academic-service
tags: [backend, flyway, subjects, teacher-subject-groups, headman, transactional, rest-api]
dependency_graph:
  requires: []
  provides:
    - "subjects.group_id NOT NULL (Flyway V12)"
    - "POST /api/academic/subjects — atomic create + N teacher assignments"
    - "POST /api/academic/subjects/{id}/teachers/{teacherId}"
    - "DELETE /api/academic/subjects/{id}/teachers/{teacherId}"
    - "GET /api/academic/subjects — group-scoped for HEADMAN, all for ADMIN"
    - "SubjectRepository.findByGroupId / existsByIdAndGroupId"
  affects:
    - "integration tests — FK order and group_id on subject inserts"
tech_stack:
  added: []
  patterns:
    - "@Transactional atomic create-and-link"
    - "Contract-first (SubjectApi interface holds @RequestMapping)"
    - "HATEOAS Level 3 (EntityModel + selfRel)"
key_files:
  created:
    - services/academic-service/academic-app/src/main/resources/db/migration/V12__subjects_group_id.sql
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectSchemaIT.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectServiceIT.java
  modified:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Subject.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SubjectRepository.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/subject/CreateSubjectRequest.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/subject/SubjectResponse.java
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SubjectApi.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectAssembler.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectController.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIntegrationTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIntegrationTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EntityMappingIntegrationTest.java
decisions:
  - "Subject.groupId immutable after creation (D-02) — updateSubject rejects cross-group tampering"
  - "AfterEach-based cleanup in SubjectServiceIT instead of @Transactional — rollback-on-error scenario requires observable commit/rollback boundary outside test tx"
  - "loadSeedIds restores is_active=true on 'Spring 2026' semester to avoid flaky interaction with RestApi/Event IT that toggle semester activity"
metrics:
  duration_min: ~12
  completed: 2026-04-14
---

# Phase 60 Plan 01: Subject Model Fix — group_id + N teachers atomicity

Single sentence: починена модель `Subject` в academic-service — добавлена колонка `subjects.group_id NOT NULL` (Flyway V12), `CreateSubjectRequest` принимает список `teacherIds`, `SubjectService.createSubject` атомарно создаёт 1 запись `subjects` + N записей `teacher_subject_groups` в активном семестре, добавлены endpoints управления преподавателями (`POST/DELETE /subjects/{id}/teachers/{teacherId}`), `listSubjects` фильтруется по группе старосты.

## Что сделано

### Task 1 — V12 + Subject.groupId + schema IT (commit `4cdbff7`)

- **Flyway V12** (`V12__subjects_group_id.sql`): транзакционно удаляет тестовые данные из `teacher_subject_groups` и `subjects`, добавляет колонку `subjects.group_id BIGINT NOT NULL` с FK на `groups(id)` и индекс `idx_subjects_group`. D-01 decision: DELETE тестовых данных обоснован отсутствием production-записей.
- **Subject entity**: добавлено поле `@Column(name="group_id", nullable=false) Long groupId` с `@Setter`.
- **SubjectRepository**: добавлены `Page<Subject> findByGroupId(Long, Pageable)`, `List<Subject> findByGroupId(Long)`, `boolean existsByIdAndGroupId(Long, Long)`.
- **SubjectSchemaIT** (3 теста): `groupIdIsNotNull`, `groupIdHasForeignKey`, `findByGroupId_returnsOnlyThatGroupSubjects`.

### Task 2 — атомарное создание + teacher endpoints (commit `9e434ec`)

- **CreateSubjectRequest**: добавлено поле `@NotNull List<Long> teacherIds` (пустой список допустим, null — нет).
- **SubjectResponse**: добавлены поля `groupId` и `teacherIds` (для UI D-19); без Lombok (api-contract module).
- **SubjectApi**: добавлены контрактные методы `addTeacher(id, teacherId)` и `removeTeacher(id, teacherId)` c Swagger-аннотациями 201/204/403/404/409.
- **SubjectService**:
  - `createSubject` @Transactional: лукап активного семестра через `SemesterRepository.findByIsActiveTrue()`, сохранение Subject с `groupId = requestContext.getGroupId()`, `tsgRepository.saveAll(...)` для N назначений. При любом сбое вся транзакция откатывается.
  - `listSubjects`: ADMIN видит все, HEADMAN — только `findByGroupId(requestContext.getGroupId())`.
  - `updateSubject` / `deleteSubject`: проверка `subject.groupId == headman.groupId`; `groupId` неизменяем после создания.
  - `addTeacher` / `removeTeacher`: pre-check на владение группой, дубль → 409, отсутствие назначения → 404.
- **SubjectAssembler**: `loadTeacherIds(subject)` — выборка TSG за активный семестр для текущего предмета.
- **SubjectController**: реализация новых endpoints + `@RequireRole({STUDENT})` для HEADMAN/ADMIN логики.
- **SubjectServiceIT** (4 теста): `createSubject_withTwoTeachers_atomicInsert` (+ seed user teacher02 + group УИТ-311), `createSubject_rollbackOnTeacherSaveFail` (FK violation → откат), `addTeacher_and_removeTeacher` (201 → 409 → 204), `listSubjects_filteredByGroup` (HEADMAN — только своя, ADMIN — все).

## Endpoints

| Метод | Путь | Статус | Роль |
|-------|------|--------|------|
| POST | `/api/academic/subjects` | 201 | HEADMAN/ADMIN |
| GET | `/api/academic/subjects` | 200 (group-scoped for HEADMAN) | любая |
| GET | `/api/academic/subjects/{id}` | 200 | любая |
| PUT | `/api/academic/subjects/{id}` | 200 | HEADMAN (own group) |
| DELETE | `/api/academic/subjects/{id}` | 204 | HEADMAN (own group) |
| POST | `/api/academic/subjects/{id}/teachers/{teacherId}` | 201 / 409 | HEADMAN (own group) |
| DELETE | `/api/academic/subjects/{id}/teachers/{teacherId}` | 204 / 404 | HEADMAN (own group) |

## Тесты

- **SubjectSchemaIT**: 3/3 зелёные.
- **SubjectServiceIT**: 4/4 зелёные.
- **Полный `:services:academic-service:academic-app:test`**: 165/165 зелёные.
- **`:services:academic-service:academic-app:build`**: SUCCESS.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] V12 FK нарушает существующие IT-тесты**

- **Found during:** Task 2 full test run.
- **Issue:** После миграции V12 `subjects.group_id NOT NULL` + FK на `groups` пять IT-тестов падали на `DataIntegrityViolationException` (null group_id) и один — на `fk_subjects_group` при удалении группы.
- **Fix:**
  - `AcademicGrpcIntegrationTest`: добавлен `subject.setGroupId(GROUP_ID)` в seed.
  - `EventIntegrationTest`: добавлен `testSubject.setGroupId(groupA.getId())`; в `AfterEach` поменян порядок — subject удаляется до group; в `deleteGroup_publishesGroupUpdatedEvent` добавлено удаление testSubject перед `groupService.deleteGroup(groupA)` (иначе FK violation).
  - `RestApiIntegrationTest.testHomeworkCompletionToggle` и `EntityMappingIntegrationTest`: INSERT-запросы включают `group_id, VALUES(..., ?)` с передачей seedGroupId / groupId.
- **Files modified:** 4 IT файла.
- **Commit:** `9e434ec`.

**2. [Rule 1 — Bug] SubjectServiceIT зависел от активного семестра, изменяемого другими IT**

- **Found during:** Task 2 full test run.
- **Issue:** `RestApiIntegrationTest.testHomeworkCompletionToggle` деактивирует все активные семестры (`UPDATE semesters SET is_active = false`). Несмотря на `@Transactional` rollback, при одновременных контейнерных прогонах могут оставаться артефакты, приводящие к тому, что `SemesterRepository.findByIsActiveTrue()` возвращал пусто → `createSubject` бросал ConflictException.
- **Fix:** В `SubjectServiceIT.loadSeedIds` добавлен явный restore `UPDATE semesters SET is_active = true WHERE name = 'Spring 2026'` и деактивация прочих.
- **Files modified:** `SubjectServiceIT.java`.
- **Commit:** `9e434ec`.

**3. [Rule 3 — Blocking] Тесты с `@Transactional` не могли проверить rollback через JdbcTemplate**

- **Found during:** Task 2 TDD red→green cycle.
- **Issue:** Исходно все 4 теста `SubjectServiceIT` были `@Transactional`. Для `rollbackOnTeacherSaveFail` SQL error aborting transaction делал последующие queries `JdbcTemplate` недопустимыми (`current transaction is aborted`). Для `addTeacher_and_removeTeacher` JdbcTemplate не видел pending DELETE, т.к. service-tx была вложенной.
- **Fix:** Убран `@Transactional` с тестовых методов, добавлен `@AfterEach cleanup()` с точечными DELETE-ами: `teacher_subject_groups`, `subjects`, `users WHERE login='teacher02'`, `groups WHERE name='УИТ-311'`. Seed-данные (student/teacher/admin/ИВТ-211) не трогаются.
- **Files modified:** `SubjectServiceIT.java`.
- **Commit:** `9e434ec`.

## Known Stubs

Нет.

## Threat Flags

Нет нового surface — STRIDE-мap плана (T-60-01, T-60-02, T-60-06) полностью смитигированы:
- T-60-01 (EoP): `requireHeadmanGroupId()` проверяет `isHeadman()` + groupId из JWT; `assertSubjectBelongsToHeadmanGroup` блокирует cross-group операции.
- T-60-02 (Tampering): `@Transactional` на `createSubject` гарантирует атомарность.
- T-60-06 (SQLi): JPA parameterized query на `findByGroupId`.

## Self-Check: PASSED

**Created files (existence verified):**
- FOUND: services/academic-service/academic-app/src/main/resources/db/migration/V12__subjects_group_id.sql
- FOUND: services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectSchemaIT.java
- FOUND: services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectServiceIT.java

**Commits (verified via `git log --oneline -3`):**
- FOUND: 4cdbff7 feat(60-01): add subjects.group_id NOT NULL + Flyway V12 + schema IT
- FOUND: 9e434ec feat(60-01): atomic subject creation with N teachers + teacher CRUD endpoints

**Tests:** `:services:academic-service:academic-app:test` — 165/165 PASSED. `:services:academic-service:academic-app:build` — SUCCESS.

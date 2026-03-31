---
phase: 05-entity-and-repository-foundation
verified: 2026-03-30T12:00:00Z
status: passed
score: 11/11 must-haves verified
re_verification: false
gaps: []
human_verification: []
---

# Phase 5: Entity and Repository Foundation — Verification Report

**Phase Goal:** All JPA entities match the existing Flyway schema and are query-ready with correct soft delete, no JPA associations, and a race-condition-free login generator.
**Verified:** 2026-03-30T12:00:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | All 11 JPA entities load without Hibernate validation errors against live academic_db schema | VERIFIED | EntityMappingIntegrationTest Test 1 asserts context loads with all 11 repositories non-null; V4 migration resolves SERIAL/BIGINT mismatch identified during testing |
| 2 | V3 Flyway migration adds PostgreSQL login sequences; student_login_seq and teacher_login_seq exist and increment without concurrent conflicts | VERIFIED | V3__login_sequences.sql creates both sequences; Test 4 asserts nextStudentLoginSeq returns seq1, seq2=seq1+1 |
| 3 | Spring Data repositories return correct results for soft-deleted users (status = 'archived' excluded from default queries via @SQLRestriction) | VERIFIED | User.java has @SQLRestriction("status <> 'archived'"); Test 3 archives a user, verifies findByLogin returns empty, findByIdIncludingArchived bypasses filter |
| 4 | Auth Service continues to start without schema validation errors (no breaking column changes) | VERIFIED | No DDL changes to existing columns; V4 migration only widens campus_settings.id from INT4 to BIGINT (safe); Auth Service does not map campus_settings |

**Score:** 4/4 success criteria verified

### Must-Haves — Plan 01

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Flyway V3 migration runs and creates student_login_seq + teacher_login_seq sequences | VERIFIED | V3__login_sequences.sql lines 5-6: CREATE SEQUENCE student_login_seq START WITH 1 INCREMENT BY 1; CREATE SEQUENCE teacher_login_seq START WITH 1 INCREMENT BY 1 |
| 2 | Group, Semester, Subject, CampusSetting entities load without Hibernate validation errors | VERIFIED | All 4 entities match V1 schema exactly; CampusSetting PK widened via V4 migration |
| 3 | GroupRepository, SemesterRepository, SubjectRepository, CampusSettingRepository return correct query results | VERIFIED | All 4 repos substantive with domain-specific query methods; EntityMappingIntegrationTest validates against real DB |
| 4 | Testcontainers integration test base class starts a real PostgreSQL and runs Flyway migrations | VERIFIED | AbstractAcademicIntegrationTest.java: static PostgreSQLContainer with @DynamicPropertySource; @ActiveProfiles("test"); application-test.yml enables Flyway |

### Must-Haves — Plan 02

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 5 | User entity has @SQLRestriction that filters archived users from default queries | VERIFIED | User.java line 14: @SQLRestriction("status <> 'archived'") |
| 6 | UserRepository.findByIdIncludingArchived bypasses @SQLRestriction for admin operations | VERIFIED | UserRepository.java lines 38-39: @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true) |
| 7 | UserRepository.nextStudentLoginSeq and nextTeacherLoginSeq return incrementing values from PostgreSQL sequences | VERIFIED | UserRepository.java lines 28-31: native queries SELECT nextval('student_login_seq') and SELECT nextval('teacher_login_seq'); Test 4 proves incrementing |
| 8 | HeadmanAssistant.permissions String[] persists and retrieves correctly as PostgreSQL VARCHAR(64)[] array | VERIFIED | HeadmanAssistant.java line 29-31: @JdbcTypeCode(SqlTypes.ARRAY), columnDefinition = "varchar(64)[]"; Test 5 round-trips array values |
| 9 | AttendanceThresholdRepository supports most-specific-wins resolution (subject > group > global) | VERIFIED | AttendanceThresholdRepository.java: findByGroupIdAndSubjectId, findByGroupIdAndSubjectIdIsNull, findByGroupIdIsNullAndSubjectIdIsNull; Test 6 verifies 90/80/70 resolution |
| 10 | All 11 entities pass Hibernate schema validation against academic_db with Flyway V1+V2+V3 | VERIFIED | EntityMappingIntegrationTest Test 1 asserts schema validation succeeds; V4 migration auto-fixed SERIAL/BIGINT mismatch |
| 11 | Auth Service shared columns (id, login, password_hash, role, status, is_headman, group_id, telegram_id) are unchanged | VERIFIED | V1 schema columns intact; V4 only alters campus_settings.id type — not in users table; User.java maps all shared columns correctly |

**Total Score:** 11/11 must-haves verified

---

## Required Artifacts

### Plan 01 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/academic-service/academic-app/src/main/resources/db/migration/V3__login_sequences.sql` | Login sequences for atomic login generation | VERIFIED | 6 lines; contains CREATE SEQUENCE student_login_seq START WITH 1 INCREMENT BY 1 and teacher equivalent |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java` | Group JPA entity | VERIFIED | @Table(name = "groups"), IDENTITY generation, isActive + createdAt, no @ManyToOne/@Enumerated |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Semester.java` | Semester JPA entity | VERIFIED | @Table(name = "semesters"), LocalDate for DATE columns, OffsetDateTime for TIMESTAMPTZ, isActive |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicIntegrationTest.java` | Shared Testcontainers base class | VERIFIED | PostgreSQLContainer<?>; @DynamicPropertySource; excludes Rabbit/Redis autoconfiguration |

### Plan 02 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/User.java` | User JPA entity with @SQLRestriction soft delete | VERIFIED | @SQLRestriction("status <> 'archived'"); maps all 17 columns from V1 users table; uses LowercaseEnumConverter autoApply for UserRole and AccountStatus |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/HeadmanAssistant.java` | HeadmanAssistant entity with PostgreSQL array mapping | VERIFIED | @JdbcTypeCode(SqlTypes.ARRAY); String[] permissions; columnDefinition = "varchar(64)[]" |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRepository.java` | UserRepository with login sequences and archived-user queries | VERIFIED | nextStudentLoginSeq, nextTeacherLoginSeq (native nextval); findByIdIncludingArchived (native bypass); findAllArchived; findByLogin/TelegramId/EmployeeNumber/GroupId; pagination by role |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/AttendanceThresholdRepository.java` | Threshold resolution queries | VERIFIED | findByGroupIdAndSubjectId (subject-level); findByGroupIdAndSubjectIdIsNull (group-level); findByGroupIdIsNullAndSubjectIdIsNull (global) |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EntityMappingIntegrationTest.java` | Comprehensive integration test validating all entities against real PostgreSQL | VERIFIED | extends AbstractAcademicIntegrationTest; 7 @Test methods; covers schema validation, seed data, @SQLRestriction, sequences, array persistence, threshold resolution, EXCLUDE constraint |

---

## All 11 Entities — Schema Alignment Spot-Check

| Entity | Table | Key Columns Verified | No JPA Associations | No @Enumerated |
|--------|-------|---------------------|--------------------|-|
| Group | groups | id BIGSERIAL, name, code UNIQUE, is_active, created_at TIMESTAMPTZ | PASS | PASS |
| Semester | semesters | id, name, date_from DATE->LocalDate, date_to DATE->LocalDate, is_active, created_at | PASS | PASS |
| Subject | subjects | id, name, type->SubjectType (autoApply converter), created_at | PASS | PASS |
| CampusSetting | campus_settings | id (SERIAL->BIGINT via V4), name, lat double, lng double, radius_m int, updated_at | PASS | PASS |
| User | users | id, login, password_hash, display_name, telegram_id BIGINT, role->UserRole, status->AccountStatus, is_headman, group_id Long FK, initial_password, password_changed, created_at, updated_at | PASS | PASS |
| StudentGroupHistory | student_group_history | id, user_id, group_id, joined_at DATE->LocalDate, left_at DATE->LocalDate, reason, created_at | PASS | PASS |
| TeacherSubjectGroup | teacher_subject_groups | id, teacher_id, subject_id, group_id, semester_id, assigned_at; 4-col UNIQUE constraint | PASS | PASS |
| Homework | homeworks | id, group_id, subject_id, semester_id, lesson_id nullable, title, description TEXT, link, published_by, created_at, updated_at | PASS | PASS |
| HomeworkCompletion | homework_completions | id, homework_id, student_id, completed_at; (homework_id, student_id) UNIQUE | PASS | PASS |
| HeadmanAssistant | headman_assistants | id, group_id, student_id, permissions varchar(64)[], assigned_by, is_active, assigned_at, revoked_at nullable; (group_id, student_id) UNIQUE | PASS | PASS |
| AttendanceThreshold | attendance_thresholds | id, group_id nullable, subject_id nullable, threshold_pct int, set_by, created_at; (group_id, subject_id) UNIQUE | PASS | PASS |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| build.gradle.kts | Testcontainers runtime | testImplementation dependencies | WIRED | Lines 47-49: spring-boot-testcontainers, junit-jupiter, postgresql; BOM 1.20.4 in dependencyManagement |
| AbstractAcademicIntegrationTest.java | PostgreSQL Testcontainer | @DynamicPropertySource overrides | WIRED | Lines 23-32: @DynamicPropertySource overrides datasource url/username/password from POSTGRES container |
| User.java | academic_db users table | @SQLRestriction + Hibernate validate | WIRED | @SQLRestriction("status <> 'archived'") on @Entity; application-test.yml ddl-auto:validate proves schema match |
| UserRepository.java | V3 login sequences | native nextval queries | WIRED | nativeQuery=true SELECT nextval('student_login_seq') and nextval('teacher_login_seq') |
| HeadmanAssistant.java | academic_db headman_assistants.permissions | @JdbcTypeCode(SqlTypes.ARRAY) | WIRED | @JdbcTypeCode(SqlTypes.ARRAY) with columnDefinition = "varchar(64)[]" |
| EntityMappingIntegrationTest.java | AbstractAcademicIntegrationTest | class inheritance | WIRED | class EntityMappingIntegrationTest extends AbstractAcademicIntegrationTest |

---

## Data-Flow Trace (Level 4)

Not applicable. Phase 5 is a pure data layer (entities, repositories, migrations). There are no UI components or API responses serving dynamic data to end users. Repositories are query infrastructure only — data flow verification belongs to Phase 6 (REST API layer).

---

## Behavioral Spot-Checks

Step 7b: SKIPPED for production code (no runnable entry points without infrastructure). Testcontainers integration tests serve as the behavioral proof. The 7 tests in EntityMappingIntegrationTest cover all key behaviors (schema validation, soft delete, sequence generation, array persistence, threshold resolution, EXCLUDE constraint) and passed per commit 788eec0.

---

## Requirements Coverage

Phase 5 scope: entity/repository foundation (data layer only). All listed requirements span "Phase 5 + Phase 6" in REQUIREMENTS.md — Phase 5 provides the structural foundation; Phase 6 will implement the service and REST layer.

| Requirement | Source Plan | Description | Phase 5 Contribution | Status |
|-------------|------------|-------------|---------------------|--------|
| USER-01 | 05-02 | Auto-generated login (student00001/teacher00001) | UserRepository.nextStudentLoginSeq/nextTeacherLoginSeq + V3 sequences | FOUNDATION |
| USER-02 | 05-02 | View, update, soft-delete users | User entity @SQLRestriction; UserRepository findByIdIncludingArchived | FOUNDATION |
| USER-03 | 05-02 | Assign headman flag | User.isHeadman field; UserRepository.findByGroupId | FOUNDATION |
| USER-04 | 05-02 | Revoke headman (auto-deactivates assistants) | HeadmanAssistantRepository.revokeAllByGroupId | FOUNDATION |
| USER-05 | 05-02 | Transfer student with history | StudentGroupHistory entity; StudentGroupHistoryRepository | FOUNDATION |
| GSEM-01 | 05-01 | CRUD groups | Group entity; GroupRepository with findByIsActive/findByCode | FOUNDATION |
| GSEM-02 | 05-01 | CRUD semesters | Semester entity; SemesterRepository findByIsActiveTrue | FOUNDATION |
| GSEM-03 | 05-01 | Activate semester (only one active) | Semester EXCLUDE constraint in DB; SemesterRepository.deactivateAllActive | FOUNDATION |
| GSEM-04 | 05-01 | Delete semester | SemesterRepository extends JpaRepository (delete available) | FOUNDATION |
| SUBJ-01 | 05-01 | CRUD subjects with type | Subject entity with SubjectType; SubjectRepository findByType | FOUNDATION |
| SUBJ-02 | 05-02 | Assign teacher to subject+group | TeacherSubjectGroup entity; TeacherSubjectGroupRepository | FOUNDATION |
| SUBJ-03 | 05-02 | Remove teacher-subject-group assignment | TeacherSubjectGroupRepository.deleteByTeacherIdAndSubjectIdAndGroupIdAndSemesterId | FOUNDATION |
| ASST-01 | 05-02 | Assign assistant with permissions | HeadmanAssistant entity with String[] permissions; HeadmanAssistantRepository | FOUNDATION |
| ASST-02 | 05-02 | Revoke assistant | HeadmanAssistantRepository.revokeAllByGroupId | FOUNDATION |
| ASST-03 | 05-02 | Update assistant permissions | HeadmanAssistant.permissions has @Setter | FOUNDATION |
| HW-01 | 05-02 | CRUD homeworks | Homework entity; HomeworkRepository | FOUNDATION |
| HW-02 | 05-02 | Student views group homeworks | HomeworkRepository.findByGroupIdAndSemesterId | FOUNDATION |
| HW-03 | 05-02 | Mark/unmark homework completed | HomeworkCompletion entity; HomeworkCompletionRepository findByHomeworkIdAndStudentId, existsByHomeworkIdAndStudentId | FOUNDATION |
| THRSH-01 | 05-02 | Global attendance threshold | AttendanceThreshold nullable group_id/subject_id; findByGroupIdIsNullAndSubjectIdIsNull | FOUNDATION |
| THRSH-02 | 05-02 | Group-level threshold | findByGroupIdAndSubjectIdIsNull | FOUNDATION |
| THRSH-03 | 05-02 | Subject-level threshold | findByGroupIdAndSubjectId | FOUNDATION |
| THRSH-04 | 05-02 | Most-specific-wins resolution | All 3 findBy methods enable service layer to implement chain logic | FOUNDATION |

**Note:** Status "FOUNDATION" means Phase 5 delivers the correct entities and query methods. Full requirement satisfaction (including REST endpoints, auth checks, service logic) is completed in Phase 6. All 22 requirement IDs from the PLAN frontmatter are accounted for.

**No orphaned requirements detected.** All 22 requirement IDs claimed in plans are mapped in REQUIREMENTS.md Traceability table under "Phase 5 + Phase 6".

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | — | — | — | — |

No TODO/FIXME/placeholder comments found. No @ManyToOne, @OneToMany, @ManyToMany, @OneToOne, or @Enumerated annotations present in any entity. No hardcoded empty returns. No stub implementations.

---

## Human Verification Required

None. All automated checks passed. This phase is pure data layer — no UI, no real-time behavior, no external service integration that cannot be verified programmatically.

---

## Commit Verification

All 6 commits documented in SUMMARY files confirmed to exist in git history:

| Commit | Description | Plan |
|--------|-------------|------|
| 90007b1 | feat(05-01): build config, V3 migration, EnumConverters, test base class | 05-01 |
| 31060d8 | feat(05-01): Group, Semester, Subject, CampusSetting JPA entities | 05-01 |
| 751ebdc | feat(05-01): GroupRepository, SemesterRepository, SubjectRepository, CampusSettingRepository | 05-01 |
| 4ded972 | feat(05-02): add User, StudentGroupHistory, TeacherSubjectGroup, Homework, HomeworkCompletion entities | 05-02 |
| bbcd43a | feat(05-02): add HeadmanAssistant, AttendanceThreshold entities and all 7 repositories | 05-02 |
| 788eec0 | test(05-02): add EntityMappingIntegrationTest covering all 11 entities + V4 migration | 05-02 |

---

## Gaps Summary

No gaps. All must-haves verified at all levels (exists, substantive, wired). The single auto-fixed deviation (V4 migration for SERIAL/BIGINT mismatch on campus_settings) was correctly handled and documented.

---

_Verified: 2026-03-30T12:00:00Z_
_Verifier: Claude (gsd-verifier)_

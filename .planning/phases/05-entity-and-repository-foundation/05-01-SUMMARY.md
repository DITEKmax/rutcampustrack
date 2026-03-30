---
phase: 05-entity-and-repository-foundation
plan: "01"
subsystem: academic-service
tags: [jpa, entities, repositories, flyway, testcontainers]
dependency_graph:
  requires: []
  provides:
    - Group JPA entity + GroupRepository
    - Semester JPA entity + SemesterRepository
    - Subject JPA entity + SubjectRepository
    - CampusSetting JPA entity + CampusSettingRepository
    - V3 Flyway migration (student_login_seq, teacher_login_seq)
    - AbstractAcademicIntegrationTest (Testcontainers base class)
  affects:
    - academic-app entity/repository layer
    - Plan 05-02 (integration tests depend on this base class)
tech_stack:
  added:
    - org.testcontainers:testcontainers-bom:1.20.4
    - org.testcontainers:postgresql
    - org.testcontainers:junit-jupiter
    - org.springframework.boot:spring-boot-testcontainers
  patterns:
    - JPA entities with @Getter/@NoArgsConstructor/@Setter on mutable fields only
    - LowercaseEnumConverter autoApply pattern
    - Testcontainers PostgreSQLContainer static init pattern
    - Spring @DynamicPropertySource for container property overrides
key_files:
  created:
    - services/academic-service/academic-app/src/main/resources/db/migration/V3__login_sequences.sql
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Semester.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Subject.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/CampusSetting.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/GroupRepository.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SemesterRepository.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SubjectRepository.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/CampusSettingRepository.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicIntegrationTest.java
    - services/academic-service/academic-app/src/test/resources/application-test.yml
  modified:
    - services/academic-service/academic-app/build.gradle.kts
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/EnumConverters.java
decisions:
  - "Semester.date_from/date_to use LocalDate (DATE columns), not OffsetDateTime — aligns with V1 schema"
  - "CampusSetting PK is SERIAL (not BIGSERIAL) but mapped to Long — JDBC widens safely"
  - "AbstractAcademicIntegrationTest excludes RabbitMQ and Redis autoconfigurations to avoid connection failures in tests"
  - "AssistantPermissionConverter added to EnumConverters despite not being used in this plan's entities — needed for headman_assistants table in Plan 02"
metrics:
  duration: "4m 9s"
  completed_date: "2026-03-30"
  tasks_completed: 3
  files_created: 11
  files_modified: 2
requirements_satisfied:
  - GSEM-01
  - GSEM-02
  - GSEM-03
  - GSEM-04
  - SUBJ-01
---

# Phase 05 Plan 01: Entity and Repository Foundation Summary

## One-liner

4 JPA entities (Group, Semester, Subject, CampusSetting) + 4 Spring Data repositories + V3 Flyway login sequences + Testcontainers base class for academic-service.

## What Was Built

### Task 1: Build Config + V3 Migration + EnumConverters + Test Infrastructure

- **build.gradle.kts**: Added Testcontainers BOM `1.20.4` in `dependencyManagement` block; added `spring-boot-testcontainers`, `testcontainers:junit-jupiter`, `testcontainers:postgresql` test deps — mirrors auth-service pattern.
- **V3__login_sequences.sql**: Creates `student_login_seq` and `teacher_login_seq` starting at 1. V2 seed data only has non-numeric logins (`admin`, `teacher`, `student`), so no collision.
- **EnumConverters.java**: Added `AssistantPermissionConverter` as 4th inner class (keeps existing 3 converters unchanged). Import added for `AssistantPermission` from contract module.
- **AbstractAcademicIntegrationTest.java**: `@SpringBootTest`, `@ActiveProfiles("test")`, static `PostgreSQLContainer<?>` with `postgres:16` / `academic_db` / `rct_user` / `rct_dev_pass`. `@DynamicPropertySource` overrides datasource URL/user/pass and excludes `RabbitAutoConfiguration`, `RedisAutoConfiguration`, `RedisRepositoriesAutoConfiguration`.
- **application-test.yml**: `ddl-auto: validate`, Flyway enabled with `classpath:db/migration`, DEBUG logging for Hibernate SQL.

### Task 2: JPA Entities

All entities follow the Auth Service `User.java` pattern: `@Getter`, `@NoArgsConstructor`, `GenerationType.IDENTITY`, `Long id`, `OffsetDateTime` for TIMESTAMPTZ, `@Setter` only on mutable fields. No `@ManyToOne`/`@OneToMany` (Long FK fields per D-08). No `@Enumerated` (LowercaseEnumConverter autoApply).

- **Group**: `groups` table. Fields: id, name, code (unique), isActive, createdAt.
- **Semester**: `semesters` table. Uses `LocalDate` for `date_from`/`date_to` (DATE columns), `OffsetDateTime` for `created_at` (TIMESTAMPTZ). DB-enforced EXCLUDE constraint for single active semester not represented in JPA.
- **Subject**: `subjects` table. `SubjectType type` field — converted to/from lowercase by `SubjectTypeConverter` (autoApply).
- **CampusSetting**: `campus_settings` table. `double lat`, `double lng`, `int radiusM`, `OffsetDateTime updatedAt`. No `created_at` column (per V1 schema). PK is SERIAL (mapped to Long — safe widening).

### Task 3: Spring Data Repositories

- **GroupRepository**: `findByIsActive(boolean)`, `findByCode(String)`, `existsByCode(String)` — for GSEM-01 group listing/lookup.
- **SemesterRepository**: `findByIsActiveTrue()`, `deactivateAllActive()` (`@Modifying` JPQL) — for GSEM-02/03/04 semester management.
- **SubjectRepository**: `findByType(SubjectType)`, `findByNameContainingIgnoreCase(String)` — for SUBJ-01 subject search.
- **CampusSettingRepository**: Basic `JpaRepository<CampusSetting, Long>` — single-row table, callers use `findById(1L)`.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — this plan establishes pure infrastructure (entities, repositories, migrations). No data flows to UI.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 90007b1 | feat(05-01): build config, V3 migration, EnumConverters, test base class |
| 2 | 31060d8 | feat(05-01): Group, Semester, Subject, CampusSetting JPA entities |
| 3 | 751ebdc | feat(05-01): GroupRepository, SemesterRepository, SubjectRepository, CampusSettingRepository |

## Self-Check: PASSED

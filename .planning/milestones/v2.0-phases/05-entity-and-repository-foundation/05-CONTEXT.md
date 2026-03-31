# Phase 5: Entity and Repository Foundation - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

<domain>
## Phase Boundary

JPA entities and Spring Data repositories for all academic_db tables (users, groups, semesters, subjects, teacher_subject_groups, headman_assistants, campus_settings, homeworks, homework_completions, student_group_history, attendance_thresholds). Flyway V3 migration for login sequences. No REST endpoints, no gRPC, no caching — pure data layer.

</domain>

<decisions>
## Implementation Decisions

### Soft Delete Strategy
- **D-01:** Use Hibernate `@Where(clause = "status <> 'archived'")` on User entity for automatic filtering of archived users from all default queries
- **D-02:** Admin operations that need archived users (e.g., reactivation) must use native queries or `@Query` with explicit status filter

### Login Generation
- **D-03:** Use PostgreSQL SEQUENCE — `student_login_seq` and `teacher_login_seq` created in V3 Flyway migration
- **D-04:** `nextval()` is atomic, no application-level locking needed. Format login as `String.format("student%05d", nextval)` / `String.format("teacher%05d", nextval)`
- **D-05:** Sequences must start after the highest existing login number (accounting for V2 seed data: student/teacher/admin test accounts)

### Permission Array Mapping
- **D-06:** Use Hibernate `@JdbcTypeCode(SqlTypes.ARRAY)` with `String[]` field for `headman_assistants.permissions` column (native PostgreSQL array support)
- **D-07:** Convert between `String[]` and `List<AssistantPermission>` in the service layer, not in the entity

### Entity Packaging (Claude's Discretion)
- Entity classes in `ru.rutcampustrack.academic.entity` (flat package, consistent with Auth Service pattern)
- Repositories in `ru.rutcampustrack.academic.repository`

### Entity Design (from Auth Service pattern)
- **D-08:** Use Long FK fields (no @ManyToOne / @OneToMany associations) — consistent with Auth Service User.java
- **D-09:** Lombok @Getter + @NoArgsConstructor + @Setter (where needed) on entities — app module only
- **D-10:** GenerationType.IDENTITY for all BIGSERIAL PKs
- **D-11:** OffsetDateTime for all TIMESTAMPTZ columns

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Database Schema
- `docs/database-schema.md` — Full schema for all 12 academic_db tables, indexes, constraints, enum types
- `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql` — Existing DDL (immutable)
- `services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql` — Seed data (immutable)

### Entity Pattern (reference implementation)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/entity/User.java` — Auth Service User entity (Long FK fields, no associations, Lombok pattern)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/repository/UserRepository.java` — Auth Service repository pattern

### Enum Conversion
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/LowercaseEnumConverter.java` — Abstract base converter
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/EnumConverters.java` — Existing converters (UserRole, AccountStatus, SubjectType)

### Contract Enums
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/` — All defined enums (UserRole, AccountStatus, SubjectType, AssistantPermission)

### Build Configuration
- `services/academic-service/academic-app/build.gradle.kts` — Dependencies (JPA, Redis, AMQP, Flyway already present)

### Project Rules
- `CLAUDE.md` — Coding conventions, contract-first rules, enum rules, DB rules

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LowercaseEnumConverter<E>` abstract base class — reuse for new enum converters (AssistantPermission not yet covered)
- `EnumConverters.java` — add AssistantPermission converter here
- `ErrorResponse` record + `ResourceNotFoundException` — already in academic-api-contract
- Auth Service User.java — reference pattern for Academic Service entities

### Established Patterns
- Long FK fields (not JPA @ManyToOne) — Auth Service established this
- OffsetDateTime for timestamps
- GenerationType.IDENTITY for BIGSERIAL
- @Column annotations with explicit names for snake_case mapping
- Flyway manages schema, Hibernate validates only (ddl-auto: validate)

### Integration Points
- Auth Service reads `users` table from academic_db — columns id, login, password_hash, role, status, is_headman, group_id, telegram_id are a shared contract. DO NOT rename or remove these columns.
- V1 and V2 migrations are immutable (already deployed). All changes start at V3.

</code_context>

<specifics>
## Specific Ideas

- attendance_thresholds table not in V1 baseline — need V3 migration to CREATE TABLE
- homework_completions table not in V1 baseline — need V3 migration to CREATE TABLE
- Login sequences (student_login_seq, teacher_login_seq) — V3 migration must set START WITH to account for V2 seed data

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-entity-and-repository-foundation*
*Context gathered: 2026-03-30*

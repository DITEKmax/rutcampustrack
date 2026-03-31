# Phase 5: Entity and Repository Foundation - Research

**Researched:** 2026-03-30
**Domain:** JPA entities, Spring Data repositories, Flyway migration, PostgreSQL arrays, soft delete
**Confidence:** HIGH

## Summary

Phase 5 establishes all JPA entities and Spring Data repositories for the `academic_db` schema (12 tables). The schema already exists in V1 and V2 migrations which are immutable. The only new Flyway migration needed is V3, which adds two PostgreSQL sequences for race-condition-free login generation. All entities follow an established pattern from the Auth Service: Long FK fields (no `@ManyToOne`/`@OneToMany`), Lombok `@Getter`/`@NoArgsConstructor` on entities in the `*-app` module, `GenerationType.IDENTITY`, and `OffsetDateTime` for timestamps.

The one technically non-trivial problem is the `headman_assistants.permissions` column, a native PostgreSQL `VARCHAR(64)[]` array. Hibernate 6 supports this via `@JdbcTypeCode(SqlTypes.ARRAY)` with a `String[]` field and a Testcontainers integration test must verify the mapping works before any service code depends on it. The soft delete filter on `users` should use `@SQLRestriction` (the Hibernate 6.3+ replacement for the deprecated `@Where`). Login generation uses PostgreSQL sequences via a native `@Query` in a custom repository — `nextval()` is atomic and requires no application-level locking.

**Primary recommendation:** Implement one entity at a time following the Auth Service reference pattern, add the V3 migration last (login sequences), and validate the `headman_assistants` array mapping with a Testcontainers test early in the wave.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Use Hibernate `@SQLRestriction(clause = "status <> 'archived'")` on User entity for automatic filtering of archived users from all default queries (note: CONTEXT.md says `@Where` but this is deprecated in Hibernate 6.3 — `@SQLRestriction` is the correct modern form; functionally identical)
- **D-02:** Admin operations that need archived users (e.g., reactivation) must use native queries or `@Query` with explicit status filter
- **D-03:** Use PostgreSQL SEQUENCE — `student_login_seq` and `teacher_login_seq` created in V3 Flyway migration
- **D-04:** `nextval()` is atomic, no application-level locking needed. Format login as `String.format("student%05d", nextval)` / `String.format("teacher%05d", nextval)`
- **D-05:** Sequences must start after the highest existing login number (accounting for V2 seed data: student/teacher/admin test accounts)
- **D-06:** Use Hibernate `@JdbcTypeCode(SqlTypes.ARRAY)` with `String[]` field for `headman_assistants.permissions` column
- **D-07:** Convert between `String[]` and `List<AssistantPermission>` in the service layer, not in the entity
- **D-08:** Use Long FK fields (no `@ManyToOne` / `@OneToMany` associations) — consistent with Auth Service User.java
- **D-09:** Lombok `@Getter` + `@NoArgsConstructor` + `@Setter` (where needed) on entities — app module only
- **D-10:** `GenerationType.IDENTITY` for all BIGSERIAL PKs
- **D-11:** `OffsetDateTime` for all TIMESTAMPTZ columns

### Entity Packaging (Claude's Discretion)

- Entity classes in `ru.rutcampustrack.academic.entity` (flat package, consistent with Auth Service pattern)
- Repositories in `ru.rutcampustrack.academic.repository`

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| USER-01 | Admin can create user with auto-generated login (student00001/teacher00001) and initial password | Sequence-based login generation (D-03/D-04); UserRepository with native nextval query; User entity with all fields |
| USER-02 | Admin can view, update, and soft-delete (archive) users | User entity with `@SQLRestriction`; UserRepository with methods for active queries + native query for archived |
| USER-03 | Admin can assign headman flag to a student in a group | User entity `isHeadman` + `groupId` fields; UserRepository update methods |
| USER-04 | Admin can revoke headman (auto-deactivates all assistants) | HeadmanAssistantRepository `findByGroupId` + `revokeAll` methods |
| USER-05 | Admin can transfer student between groups with reason (history tracked) | StudentGroupHistory entity + StudentGroupHistoryRepository |
| GSEM-01 | Admin can CRUD groups (name, code, active flag) | Group entity + GroupRepository with `findByIsActive` |
| GSEM-02 | Admin can CRUD semesters (name, date range) | Semester entity + SemesterRepository |
| GSEM-03 | Admin can activate semester (only one active at a time, DB-enforced) | Semester entity; DB-enforced via EXCLUDE constraint in V1; SemesterRepository query methods |
| GSEM-04 | Admin can delete semester with confirmation phrase guard | SemesterRepository `deleteById` — guard logic in service; entity just needs standard delete |
| SUBJ-01 | Headman can CRUD subjects with type (lecture/practice/lab) | Subject entity + SubjectRepository; SubjectType enum already in contract |
| SUBJ-02 | Headman can assign teacher to subject+group (search by employee number) | TeacherSubjectGroup entity + repository; UserRepository `findByEmployeeNumber` |
| SUBJ-03 | Headman can remove teacher-subject-group assignment | TeacherSubjectGroupRepository `deleteById` or `deleteByTeacherIdAndSubjectIdAndGroupIdAndSemesterId` |
| ASST-01 | Headman can assign assistant with granular permissions | HeadmanAssistant entity with `String[]` permissions mapped via `@JdbcTypeCode(SqlTypes.ARRAY)` |
| ASST-02 | Headman can revoke assistant | HeadmanAssistantRepository `save` with `isActive=false` and `revokedAt` timestamp |
| ASST-03 | Headman can update assistant permissions | HeadmanAssistantRepository `save` with updated `String[]` permissions |
| HW-01 | Headman can CRUD homeworks (title, description, optional link) | Homework entity + HomeworkRepository |
| HW-02 | Student can view group homeworks | HomeworkRepository `findByGroupIdAndSemesterId` |
| HW-03 | Student can mark/unmark homework as completed (personal tracker) | HomeworkCompletion entity + HomeworkCompletionRepository |
| THRSH-01 | Admin can set global attendance threshold | AttendanceThreshold entity with both `groupId` and `subjectId` nullable; ThresholdRepository |
| THRSH-02 | Headman can set group-level threshold | AttendanceThreshold with `subjectId = NULL` and `groupId` set |
| THRSH-03 | Headman can set subject-level threshold | AttendanceThreshold with both `groupId` and `subjectId` set |
| THRSH-04 | System resolves threshold with most-specific-wins logic | ThresholdRepository queries: findByGroupIdAndSubjectId, findByGroupIdAndSubjectIdIsNull, findByGroupIdIsNullAndSubjectIdIsNull |
</phase_requirements>

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-data-jpa | 3.4.1 (managed) | JPA/Hibernate ORM | Already in build.gradle.kts |
| hibernate-core | 6.6.x (via Spring Boot 3.4 BOM) | ORM engine, `@SQLRestriction`, `@JdbcTypeCode` | Bundled with Spring Boot |
| postgresql | 42.x (managed) | JDBC driver | Already `runtimeOnly` in build.gradle.kts |
| flyway-core | managed by Spring Boot | Schema migrations | Already in build.gradle.kts |
| flyway-database-postgresql | managed | PostgreSQL Flyway dialect | Already in build.gradle.kts |
| lombok | managed | `@Getter`, `@NoArgsConstructor`, `@Setter` on entities | Already `compileOnly` in academic-app |

### Testing

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-boot-testcontainers | 3.4.1 (managed) | Testcontainers integration with Spring Boot | Must add to academic-app build.gradle.kts |
| testcontainers-bom | 1.20.4 | Manages Testcontainers dependency versions | Add BOM like Auth Service |
| testcontainers:postgresql | 1.20.4 | Real PostgreSQL for integration tests | Array mapping validation, Flyway validation |
| testcontainers:junit-jupiter | 1.20.4 | JUnit 5 Testcontainers support | All integration tests |

**Installation (add to academic-app build.gradle.kts):**
```kotlin
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

// In dependencies:
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:postgresql")
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
```

**Version verification:** Spring Boot 3.4.1 bundles Hibernate 6.6.x — confirmed from root `build.gradle.kts`. Testcontainers BOM 1.20.4 matches Auth Service pattern — confirmed from `services/auth-service/build.gradle.kts`.

---

## Architecture Patterns

### Recommended Project Structure

```
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/
├── entity/              # All JPA entities (flat package)
│   ├── User.java
│   ├── Group.java
│   ├── Semester.java
│   ├── Subject.java
│   ├── TeacherSubjectGroup.java
│   ├── HeadmanAssistant.java
│   ├── CampusSetting.java
│   ├── Homework.java
│   ├── HomeworkCompletion.java
│   ├── StudentGroupHistory.java
│   └── AttendanceThreshold.java
├── repository/          # Spring Data JPA repositories
│   ├── UserRepository.java
│   ├── GroupRepository.java
│   ├── SemesterRepository.java
│   ├── SubjectRepository.java
│   ├── TeacherSubjectGroupRepository.java
│   ├── HeadmanAssistantRepository.java
│   ├── CampusSettingRepository.java
│   ├── HomeworkRepository.java
│   ├── HomeworkCompletionRepository.java
│   ├── StudentGroupHistoryRepository.java
│   └── AttendanceThresholdRepository.java
└── config/
    ├── LowercaseEnumConverter.java   # already exists
    └── EnumConverters.java           # already exists — add AssistantPermission converter

services/academic-service/academic-app/src/main/resources/db/migration/
├── V1__baseline.sql                  # IMMUTABLE
├── V2__seed_test_data.sql            # IMMUTABLE
└── V3__login_sequences.sql           # NEW — sequences only
```

### Pattern 1: Standard Entity (Reference — Auth Service User.java)

**What:** Lombok-annotated entity with explicit `@Column` names, Long FK fields, `OffsetDateTime` timestamps, enum converted via autoApply converters.
**When to use:** All entities in this phase.

```java
// Source: services/auth-service/src/main/java/ru/rutcampustrack/auth/entity/User.java
@Entity
@Table(name = "table_name")
@Getter
@NoArgsConstructor
public class ExampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snake_case_column", nullable = false)
    private String fieldName;

    // Long FK — NOT @ManyToOne
    @Column(name = "parent_id")
    private Long parentId;

    // Enum — autoApply converter handles this automatically
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter
    private OffsetDateTime updatedAt;
}
```

### Pattern 2: Soft Delete Filter on User Entity

**What:** `@SQLRestriction` on the User entity automatically appends `status <> 'archived'` to all JPQL/Criteria queries against the `users` table.
**When to use:** Only on the User entity. No other tables have soft delete.

```java
// Source: Hibernate 6.3+ @SQLRestriction (replaces deprecated @Where)
// Reference: https://medium.com/@kulshresthjangid/goodbye-where-hello-sqlrestriction-hibernates-hidden-gem-2542da325b4e
@Entity
@Table(name = "users")
@SQLRestriction("status <> 'archived'")   // auto-applied to all standard queries
@Getter
@NoArgsConstructor
public class User {
    // ...
}
```

For admin queries that must see archived users, use native queries:
```java
// In UserRepository
@Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
Optional<User> findByIdIncludingArchived(@Param("id") Long id);
```

### Pattern 3: PostgreSQL Array Mapping (headman_assistants.permissions)

**What:** `@JdbcTypeCode(SqlTypes.ARRAY)` on a `String[]` field maps to PostgreSQL native `VARCHAR(64)[]` column. Enum conversion happens in the service layer.
**When to use:** Only for `HeadmanAssistant.permissions`. Do NOT use for any other field.

```java
// Source: Hibernate 6 @JdbcTypeCode with SqlTypes.ARRAY
// Reference: https://www.baeldung.com/java-hibernate-map-postgresql-array
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "headman_assistants")
@Getter
@NoArgsConstructor
public class HeadmanAssistant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "permissions", nullable = false, columnDefinition = "varchar(64)[]")
    @Setter
    private String[] permissions;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "is_active", nullable = false)
    @Setter
    private boolean isActive;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private OffsetDateTime assignedAt;

    @Setter
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;
}
```

### Pattern 4: Login Sequence Generation

**What:** Native PostgreSQL sequence call via `@Query(nativeQuery = true)` in a repository interface. `nextval()` is atomic and safe for concurrent use.
**When to use:** Only in `UserRepository` for login generation. Do NOT use `MAX()+1` — it has a race condition under concurrent inserts.

```java
// Source: https://www.baeldung.com/spring-jpa-sequence-nextval
public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT nextval('student_login_seq')", nativeQuery = true)
    Long nextStudentLoginSeq();

    @Query(value = "SELECT nextval('teacher_login_seq')", nativeQuery = true)
    Long nextTeacherLoginSeq();

    // Caller formats: String.format("student%05d", repo.nextStudentLoginSeq())
}
```

### Pattern 5: V3 Flyway Migration (Login Sequences)

**What:** Creates two PostgreSQL sequences starting after the highest login number in V2 seed data. V2 seeds only test accounts (`student`, `teacher`, `admin`) with non-numeric logins — so sequences can start at 1.

```sql
-- V3__login_sequences.sql
-- Sequences for race-condition-free login generation
-- V2 seed data has test accounts: 'admin', 'teacher', 'student' (no numeric logins)
-- Therefore sequences start at 1.

CREATE SEQUENCE student_login_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE teacher_login_seq START WITH 1 INCREMENT BY 1;
```

### Pattern 6: AssistantPermission Converter (add to EnumConverters.java)

**What:** While `permissions` field stays as `String[]` in the entity (per D-07), we still need a converter for any future enum-typed column that uses `AssistantPermission` as a scalar. Add it to `EnumConverters.java` now.

```java
// In services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/EnumConverters.java
@Converter(autoApply = true)
public static class AssistantPermissionConverter extends LowercaseEnumConverter<AssistantPermission> {
    public AssistantPermissionConverter() { super(AssistantPermission.class); }
}
```

Note: This converter does NOT apply to the `String[]` permissions array — that is handled by `@JdbcTypeCode`. The converter is added for completeness per project pattern.

### Pattern 7: Threshold Resolution (THRSH-04 query pattern)

**What:** "Most specific wins" means: subject-level (both groupId + subjectId set) > group-level (groupId set, subjectId NULL) > global (both NULL). Repository needs three query methods.

```java
public interface AttendanceThresholdRepository extends JpaRepository<AttendanceThreshold, Long> {
    // Subject-level (most specific)
    Optional<AttendanceThreshold> findByGroupIdAndSubjectId(Long groupId, Long subjectId);

    // Group-level (no subject)
    Optional<AttendanceThreshold> findByGroupIdAndSubjectIdIsNull(Long groupId);

    // Global (admin-set, both null)
    Optional<AttendanceThreshold> findByGroupIdIsNullAndSubjectIdIsNull();
}
```

### Anti-Patterns to Avoid

- **`@ManyToOne` / `@OneToMany` associations:** The project explicitly forbids JPA associations. All FK relationships are stored as plain `Long` fields. Violating this would contradict D-08 and the Auth Service reference pattern.
- **`@Enumerated(EnumType.ORDINAL)`:** CLAUDE.md prohibits this. Always use the `LowercaseEnumConverter` with `autoApply = true`.
- **`@Enumerated(EnumType.STRING)`:** Do NOT use this either — it stores `"ADMIN"` uppercase, but the DB has `'admin'` lowercase. Use only the custom `LowercaseEnumConverter`.
- **`@Where` annotation:** Deprecated since Hibernate 6.3 (part of Spring Boot 3.2+). Use `@SQLRestriction` instead. The CONTEXT.md D-01 mentions `@Where` — this research supersedes it with the correct modern form.
- **`MAX(login_number) + 1` for login generation:** Classic race condition under concurrent inserts. Use the PostgreSQL sequence (D-03/D-04).
- **`ddl-auto: create` or `ddl-auto: update`:** CLAUDE.md mandates `validate`. Hibernate must not modify schema — only Flyway does.
- **`GenerationType.AUTO` or `GenerationType.SEQUENCE` for BIGSERIAL PKs:** Use `GenerationType.IDENTITY` (D-10). `AUTO` maps to a Hibernate internal sequence with allocation size 50 and will create a `hibernate_sequence` table.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Soft delete filtering | Custom `findAll` with status check everywhere | `@SQLRestriction("status <> 'archived'")` on User | Applied automatically to all JPQL/Criteria queries |
| Atomic login counter | `SELECT MAX(login)`, parse, increment, INSERT | PostgreSQL `nextval('sequence_name')` | Race-free by design; DB-level atomicity |
| PostgreSQL array mapping | Custom `AttributeConverter` with `java.sql.Array` | `@JdbcTypeCode(SqlTypes.ARRAY)` | Hibernate 6 native support; handles nulls, wrapping |
| Enum-to-lowercase mapping | Lowercase in every `@Column` or DTO mapper | `LowercaseEnumConverter` with `autoApply = true` | Already implemented in `EnumConverters.java`; auto-applied |
| Schema creation | Hibernate DDL generation | Flyway V1 (already deployed) | V1 is immutable; ddl-auto is `validate` |

**Key insight:** The most dangerous hand-roll in this phase is array mapping for `headman_assistants.permissions`. The PostgreSQL `VARCHAR(64)[]` type requires explicit `@JdbcTypeCode(SqlTypes.ARRAY)` — any other approach (serialized string, separate table) contradicts the existing schema.

---

## Common Pitfalls

### Pitfall 1: `@Where` vs `@SQLRestriction` — using deprecated annotation

**What goes wrong:** `@Where(clause = "status <> 'archived'")` compiles and runs under Spring Boot 3.4 / Hibernate 6.6, but generates deprecation warnings and may be removed in a future Hibernate 7 upgrade.
**Why it happens:** CONTEXT.md D-01 mentions `@Where` — this was the correct annotation in Hibernate 5 and Spring Boot 2.x. Hibernate 6.3 deprecated it.
**How to avoid:** Use `@SQLRestriction("status <> 'archived'")` — same semantics, correct for Hibernate 6.3+.
**Warning signs:** IntelliJ shows strikethrough on `@Where`; build log shows deprecation warnings.

### Pitfall 2: `@SQLRestriction` blocks queries for admin archived-user operations

**What goes wrong:** After adding `@SQLRestriction` to User, any JPQL query like `findById` will silently return `Optional.empty()` for archived users — causing 404s when admin tries to reactivate an archived user.
**Why it happens:** `@SQLRestriction` is always applied to JPQL/Criteria — it cannot be disabled per-query.
**How to avoid:** All queries that must see archived users (reactivation, admin audit) MUST use `nativeQuery = true` with explicit SQL. Document this at the repository method level with a Javadoc comment.
**Warning signs:** `findById(archivedUserId)` returns empty in integration test.

### Pitfall 3: Hibernate `@JdbcTypeCode(SqlTypes.ARRAY)` with `String[]` — schema mismatch

**What goes wrong:** If `@Column(columnDefinition = "varchar(64)[]")` is omitted, Hibernate schema validation (`ddl-auto: validate`) may fail or behave unexpectedly depending on how Hibernate resolves the array column type.
**Why it happens:** Hibernate needs to know the exact PostgreSQL column type for validation. Without `columnDefinition`, it uses its default resolution which may not match `varchar(64)[]`.
**How to avoid:** Always include `columnDefinition = "varchar(64)[]"` on the `permissions` field. Validate with a Testcontainers test that persists and retrieves a `HeadmanAssistant` with a non-empty permissions array.
**Warning signs:** Flyway runs successfully but `ddl-auto: validate` throws `SchemaManagementException` at startup.

### Pitfall 4: V2 seed data and sequence START WITH

**What goes wrong:** If V3 sequences start at 1 but numeric student/teacher logins were somehow inserted in V2, the sequence generates a login that already exists, causing a unique constraint violation.
**Why it happens:** Misreading V2 seed data. V2 inserts `admin`, `teacher`, `student` (text logins, not `student00001`). No numeric logins exist.
**How to avoid:** Confirm V2 seed data carefully. V2 confirmed: only `admin`, `teacher`, `student` logins. Sequences can start at 1 safely. Add a comment in V3 migration explaining this.
**Warning signs:** `DataIntegrityViolationException` on first user creation after sequences are created.

### Pitfall 5: `GenerationType.IDENTITY` vs `GenerationType.SEQUENCE` for BIGSERIAL

**What goes wrong:** Using `GenerationType.SEQUENCE` without a named `@SequenceGenerator` causes Hibernate to create a `hibernate_sequence` in the DB or use a shared sequence — mismatching BIGSERIAL behavior.
**Why it happens:** `GenerationType.AUTO` on PostgreSQL is resolved differently in Hibernate 6 than Hibernate 5.
**How to avoid:** Always use `GenerationType.IDENTITY` for BIGSERIAL PKs (D-10). This correctly delegates to PostgreSQL's auto-increment behavior.
**Warning signs:** Hibernate DDL output shows `CREATE SEQUENCE hibernate_sequence`; validation fails.

### Pitfall 6: Boolean field naming — `isHeadman` vs `headman`

**What goes wrong:** Java boolean fields named `isX` generate getter `isX()` normally, but Lombok `@Getter` may generate `isIsHeadman()` if the field is named `isHeadman`, depending on version.
**Why it happens:** Java Bean specification strips `is` prefix for boolean getters only for `boolean` primitives named `isX` — Lombok behavior varies.
**How to avoid:** Inspect the Auth Service `User.java` reference: field is named `isHeadman` with Lombok. Confirmed from source: Lombok generates `isHeadman()` for `private boolean isHeadman` — this is correct. Mirror the pattern exactly.
**Warning signs:** Jackson serialization produces `{"isHeadman": true}` vs `{"headman": true}` mismatch with API contract.

### Pitfall 7: `CampusSetting` uses `SERIAL` (Integer), not `BIGSERIAL`

**What goes wrong:** Using `Long` for the `campus_settings.id` field when the column is `SERIAL PRIMARY KEY` (returns `Integer` range values). Hibernate `GenerationType.IDENTITY` can accept both, but schema validation may flag type mismatch in strict mode.
**Why it happens:** All other tables use `BIGSERIAL` — this one is the exception (single-row settings table).
**How to avoid:** Use `Integer` for `CampusSetting.id` OR use `Long` (PostgreSQL SERIAL is safely widened to Long by JDBC). The Auth Service pattern uses Long for everything — use Long and let JDBC widen the value.
**Warning signs:** Schema validation warns about INTEGER vs BIGINT column type.

---

## Code Examples

### Verified: Group Entity
```java
// Pattern: same as User entity, no soft delete, no array fields
@Entity
@Table(name = "groups")
@Getter
@NoArgsConstructor
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Setter
    @Column(unique = true, length = 32)
    private String code;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
```

### Verified: Semester Entity with EXCLUDE constraint note
```java
// DB enforces: CONSTRAINT only_one_active_semester EXCLUDE USING btree (is_active WITH =) WHERE (is_active = TRUE)
// JPA does NOT model constraints — they are enforced by the DB. Service sets all others to false before activating.
@Entity
@Table(name = "semesters")
@Getter
@NoArgsConstructor
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    @Setter
    private String name;

    @Column(name = "date_from", nullable = false)
    @Setter
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    @Setter
    private LocalDate dateTo;

    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
```

### Verified: V3 Migration
```sql
-- V3__login_sequences.sql
-- PostgreSQL sequences for atomic, race-condition-free login generation.
-- V2 seed data contains only non-numeric logins: 'admin', 'teacher', 'student'.
-- No student00XXX or teacher00XXX logins exist; sequences safely start at 1.
CREATE SEQUENCE student_login_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE teacher_login_seq START WITH 1 INCREMENT BY 1;
```

### Verified: UserRepository with nextval and archived-user support
```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLogin(String login);

    Optional<User> findByTelegramId(Long telegramId);

    Optional<User> findByEmployeeNumber(String employeeNumber);

    // Login sequence access
    @Query(value = "SELECT nextval('student_login_seq')", nativeQuery = true)
    Long nextStudentLoginSeq();

    @Query(value = "SELECT nextval('teacher_login_seq')", nativeQuery = true)
    Long nextTeacherLoginSeq();

    // Admin: find archived users (bypasses @SQLRestriction)
    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<User> findByIdIncludingArchived(@Param("id") Long id);

    // Admin: find all users of a role including archived (for audit)
    @Query(value = "SELECT * FROM users WHERE status = 'archived'", nativeQuery = true)
    List<User> findAllArchived();
}
```

### Verified: Testcontainers AbstractIntegrationTest for Academic Service
```java
// Mirror of Auth Service pattern — add to academic-app test directory
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class AbstractAcademicIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("academic_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // RabbitMQ and Redis not needed for entity tests
        registry.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration," +
                  "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                  "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@Where(clause = "...")` | `@SQLRestriction("...")` | Hibernate 6.3 (Spring Boot 3.2) | `@Where` is deprecated; same semantics |
| `@SoftDelete` (boolean only) | N/A for this project | Hibernate 6.4 | `@SoftDelete` only supports boolean columns natively; our `status` is an enum VARCHAR — use `@SQLRestriction` |
| `GenerationType.AUTO` on PostgreSQL (Hibernate 5) | `GenerationType.IDENTITY` | Hibernate 6 | AUTO behavior changed; IDENTITY is explicit and correct for BIGSERIAL |
| Serialized arrays (`VARCHAR` JSON column) | `@JdbcTypeCode(SqlTypes.ARRAY)` | Hibernate 6.0 | Native PostgreSQL array support; no manual serialization |

**Deprecated/outdated:**
- `@Where`: Deprecated Hibernate 6.3. Replaced by `@SQLRestriction`. Still compiles under 6.6 but use the modern form.
- `@Enumerated(EnumType.STRING)`: Still valid but stores UPPERCASE — incompatible with this project's lowercase PostgreSQL convention. Use `LowercaseEnumConverter` exclusively.

---

## Open Questions

1. **RabbitMQ autoconfiguration in tests**
   - What we know: `academic-app` declares `spring-boot-starter-amqp` as a runtime dependency. AbstractIntegrationTest in Auth Service does not exclude it.
   - What's unclear: Will Spring Boot attempt to connect to RabbitMQ at test startup for academic-app integration tests? Auth Service does not have AMQP, so its AbstractIntegrationTest has no exclusion.
   - Recommendation: Add RabbitMQ autoconfiguration exclusion in `AbstractAcademicIntegrationTest` using `spring.autoconfigure.exclude` or `@TestPropertySource(properties = "spring.rabbitmq.host=...")` with a fake host. Alternatively add Testcontainers for RabbitMQ (heavy) or mark `spring.rabbitmq.host=localhost` with a test-only application-test.yml.

2. **`attendance_thresholds` in V1 vs CONTEXT.md conflict**
   - What we know: CONTEXT.md `<specifics>` says "attendance_thresholds table not in V1 baseline — need V3 migration to CREATE TABLE". However, reviewing V1__baseline.sql directly shows `CREATE TABLE attendance_thresholds` IS present in V1.
   - What's unclear: This was verified by reading V1__baseline.sql lines 124-133 — the table exists. Similarly, `homework_completions` is in V1 at lines 153-160.
   - Recommendation: V3 migration needs ONLY the two sequences. Do NOT recreate these tables — they already exist in V1. The CONTEXT.md specifics section appears to have been written before V1 was finalized. This is HIGH confidence — read from the actual file.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | Flyway V3 migration, Testcontainers | ✓ (Docker) | 16 (in Testcontainers) | — |
| Java 21 | All compilation | ✓ | ms-21.0.9 (from CLAUDE.md) | — |
| Gradle | Build | ✓ | wrapper in repo | — |
| Docker | Testcontainers | assumed ✓ | used by Auth Service tests | — |

**Missing dependencies with no fallback:** None.

---

## Validation Architecture

`workflow.nyquist_validation` is not set in `.planning/config.json` — treat as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers |
| Config file | No `junit-platform.properties` exists; standard Spring Boot test config |
| Quick run command | `./gradlew :services:academic-service:academic-app:test --tests "*EntityMappingTest*"` |
| Full suite command | `./gradlew :services:academic-service:academic-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| USER-01 | nextval sequence returns incrementing Long | integration | `./gradlew :services:academic-service:academic-app:test --tests "*UserRepositoryTest*"` | Wave 0 |
| USER-02 | `@SQLRestriction` filters archived users from findAll/findById | integration | `./gradlew :services:academic-service:academic-app:test --tests "*UserRepositoryTest*"` | Wave 0 |
| USER-02 | native query bypasses restriction for archived user | integration | same | Wave 0 |
| ASST-01 | `HeadmanAssistant.permissions` String[] persists and retrieves as PostgreSQL array | integration | `./gradlew :services:academic-service:academic-app:test --tests "*HeadmanAssistantRepositoryTest*"` | Wave 0 |
| GSEM-03 | Semester EXCLUDE constraint prevents two active semesters | integration | `./gradlew :services:academic-service:academic-app:test --tests "*SemesterRepositoryTest*"` | Wave 0 |
| THRSH-04 | Threshold resolution queries return correct specificity level | integration | `./gradlew :services:academic-service:academic-app:test --tests "*AttendanceThresholdRepositoryTest*"` | Wave 0 |
| All entities | Flyway V3 migration runs cleanly, schema validates | integration | `./gradlew :services:academic-service:academic-app:test --tests "*AbstractAcademicIntegrationTest*"` | Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :services:academic-service:academic-app:test --tests "*RepositoryTest*"`
- **Per wave merge:** `./gradlew :services:academic-service:academic-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicIntegrationTest.java` — shared base with Testcontainers Postgres + autoconfiguration exclusions
- [ ] `src/test/java/ru/rutcampustrack/academic/integration/UserRepositoryTest.java` — covers USER-01, USER-02
- [ ] `src/test/java/ru/rutcampustrack/academic/integration/HeadmanAssistantRepositoryTest.java` — covers ASST-01/02/03 array mapping
- [ ] `src/test/java/ru/rutcampustrack/academic/integration/SemesterRepositoryTest.java` — covers GSEM-03 EXCLUDE constraint
- [ ] `src/test/java/ru/rutcampustrack/academic/integration/AttendanceThresholdRepositoryTest.java` — covers THRSH-01/02/03/04
- [ ] Add Testcontainers BOM + dependencies to `academic-app/build.gradle.kts`
- [ ] Add `src/test/resources/application-test.yml` with RabbitMQ/Redis stubs or autoconfiguration exclusions

---

## Project Constraints (from CLAUDE.md)

These constraints are binding on all implementation in this phase:

1. **Contract-first:** `*-api-contract` modules are pure `java-library`. No Lombok, no Spring annotations allowed there. Entities and repositories live ONLY in `academic-app`.
2. **No Lombok in contracts:** AssistantPermission enum is in `academic-api-contract` — no Lombok annotations there (already correct, it's a plain enum).
3. **Enums:** Java `UPPER_CASE`, PostgreSQL `lowercase`. Never `@Enumerated(EnumType.ORDINAL)`. Always `LowercaseEnumConverter`.
4. **DB conventions:** All values lowercase in PostgreSQL. Flyway for migrations. `ddl-auto: validate`. BIGSERIAL = Long in Java. TIMESTAMPTZ = OffsetDateTime.
5. **Soft delete:** Users are never physically deleted. `status = 'archived'`.
6. **PK strategy:** `BIGSERIAL` = `GenerationType.IDENTITY`.
7. **No JPA associations:** No `@ManyToOne`, `@OneToMany`, `@ManyToMany`. Long FK fields only.
8. **Flyway immutability:** V1 and V2 are deployed and immutable. Only V3+ can be created.
9. **Auth Service shared contract:** `users` table columns `id`, `login`, `password_hash`, `role`, `status`, `is_headman`, `group_id`, `telegram_id` are read by Auth Service. Do NOT rename or remove these columns.

---

## Sources

### Primary (HIGH confidence)

- `services/auth-service/src/main/java/ru/rutcampustrack/auth/entity/User.java` — Reference entity pattern (read directly)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/repository/UserRepository.java` — Reference repository pattern (read directly)
- `services/academic-service/academic-app/src/main/resources/db/migration/V1__baseline.sql` — Full schema (read directly)
- `services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql` — Seed data (read directly)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/EnumConverters.java` — Converter pattern (read directly)
- `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/AbstractIntegrationTest.java` — Testcontainers pattern (read directly)
- Root `build.gradle.kts` — Spring Boot 3.4.1 confirmed (read directly)
- `services/auth-service/build.gradle.kts` — Testcontainers BOM 1.20.4 confirmed (read directly)

### Secondary (MEDIUM confidence)

- [From @Where to @SQLRestriction](https://medium.com/@kulshresthjangid/goodbye-where-hello-sqlrestriction-hibernates-hidden-gem-2542da325b4e) — @Where deprecation in Hibernate 6.3, verified against Hibernate official Javadoc
- [Hibernate @SoftDelete Javadoc](https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/annotations/SoftDelete.html) — confirms @SoftDelete uses boolean columns by default; custom converter possible but complex
- [Baeldung: Mapping PostgreSQL Array With Hibernate](https://www.baeldung.com/java-hibernate-map-postgresql-array) — @JdbcTypeCode(SqlTypes.ARRAY) pattern
- [Baeldung: Get Nextval From Sequence With Spring JPA](https://www.baeldung.com/spring-jpa-sequence-nextval) — native query nextval pattern
- [PostgreSQL Sequence Documentation](https://www.postgresql.org/docs/current/functions-sequence.html) — nextval atomicity guarantee

### Tertiary (LOW confidence)

- None required for this phase — all findings verified via primary sources.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — confirmed from existing build files and Auth Service reference
- Architecture: HIGH — confirmed from existing code, V1 migration, and project conventions
- Pitfalls: HIGH — confirmed from Hibernate changelog (deprecation), direct schema inspection, and Auth Service patterns
- PostgreSQL array mapping: MEDIUM — confirmed via Baeldung + Hibernate docs but requires Testcontainers validation (noted in blockers)

**Research date:** 2026-03-30
**Valid until:** 2026-04-30 (stable Spring Boot 3.4 / Hibernate 6.6 stack; 30-day window)

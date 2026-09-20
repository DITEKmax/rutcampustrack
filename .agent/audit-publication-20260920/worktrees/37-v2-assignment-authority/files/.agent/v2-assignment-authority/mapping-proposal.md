# Subject type JDBC mapping proposal — 2026-09-19

The approved source correction is recorded below. It does not authorize
another Gradle, Docker, or PostgreSQL run; runtime verification remains a
separate lease.

## Confirmed evidence

H48 reached PostgreSQL and failed while `SubjectLessonTypeRepository.saveAll`
loaded a composite key. The preserved XML records
`subject_type = smallint` (SQLState 42883), because the `SubjectType` member of
the `@Id` mapping had no explicit converter and Hibernate used its ordinal
binding. The raw H48 log SHA256 is
`D0CE27AA024532248854F59E0D5433738522ED06E51C6D5B14D4405B2B72A9F9`; the
preserved gRPC XML SHA256 is
`18DE6E1FEE9CD40C8019C33BED608A36E184A2E7F4B5808845887EC67E0D2E70`.

H49 tested the bounded `@Convert` plus `@JdbcTypeCode(SqlTypes.OTHER)`
correction. All four selectors reached the application but 14 of 15 cases
failed with the same first cause: `ClassCastException` from
`SubjectType` to `byte[]` in `VarbinaryJdbcType$1.doBind:100`. The trace occurs
in the composite-key subject-type load and in assignment authority's derived
`existsBySubjectIdAndLessonType` parameter path. Fresh XML hashes are recorded
in `checks.json`; the H49 raw log is
`evidence/integration-tests-h49-2026-09-19.log`.

H49 logged Hibernate ORM `6.6.53.Final`. Its installed `UserType<J>` API has
`getSqlType`, `returnedClass`, typed `equals`/`hashCode`, `nullSafeGet`,
`nullSafeSet`, `deepCopy`, `isMutable`, `disassemble`, `assemble`, and a
default `replace`. Hibernate's `@Type` annotation targets fields and methods.
The API was inspected from the resolved hibernate-core 6.6.53.Final jar; no
dependency change is proposed.

H51 ran the exact four integration selectors once after the first UserType
correction. AssignmentAuthorityIT (5) and SemesterAssignmentAuthorityIT (2)
passed; SubjectAssignmentAuthorityIT had 4 failures of 5 and
AcademicAssignmentGrpcIT had 3 failures of 3 because subject creation returned
500. The fresh raw log is
`evidence/integration-tests-h51-2026-09-19.log` (SHA256
`D05F6EF3DBD319E22B61F5866E2EFF08251C29300306A087A0458C918C5FC2F`), with
fresh XML under `evidence/h51-xml/`. The first application cause is
PostgreSQL SQLSTATE `0A000` for Hibernate's composite-id lookup:
`(lesson_type, subject_id) IN ((?, ?))` cannot determine the row comparison
operator because the V11 `subject_type = text` operator has no cross-type btree
operator family. The rollback against the broken connection and HTTP 500 are
secondary. H51-owned containers were absent by targeted post-run cleanup; the
cleanup log is `evidence/h51-owned-cleanup-2026-09-19.log` (SHA256
`8F58DBD72D98775BC2E71048E94F9953314956E840877197B78FEDB457F07A72`).

## Approved bounded correction

Added one academic-app-local `UserType<SubjectType>` at
`services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/SubjectTypeUserType.java`
and applied it explicitly to
the `SubjectLessonType.lessonType` entity member, its `SubjectLessonTypeId`
mirror. Main's scope refinement leaves `Assignment.lessonType` unchanged;
H49's assignment trace was the `SubjectLessonTypeRepository` path despite the
similar method name. The user type:

* return `java.sql.Types.OTHER` from `getSqlType`;
* call `setNull(index, Types.OTHER, "subject_type")` for null;
* create a PostgreSQL `PGobject` with type `subject_type` and the lower-case
  label, then bind it with `setObject(index, pgObject)` so the driver carries
  the enum type OID into composite tuple parameters;
* read `ResultSet.getString(position)` and map the lower-case PostgreSQL label
  with `SubjectType.valueOf(value.toUpperCase(Locale.ROOT))`;
* be immutable, use enum identity for equality, and implement the required
  cache/deep-copy methods without changing the public enum or composite key.

The explicit `@Type` mapping replaces the ineffective `@Convert` plus
`@JdbcTypeCode` pair on the two affected fields. It keeps V25's native,
lower-case `subject_type` and makes the parameter binder deterministic for
the `@Id` composite lookup. The local PostgreSQL JDBC 42.7.12 `PGobject` API
was confirmed with `javap` (`setType`, `setValue`, `getType`, `getValue`);
the academic app adds only an unversioned `compileOnly` declaration matching
the existing managed `runtimeOnly` driver. The unimplicated `Subject.type` and
`Assignment.lessonType` fields remain on the existing converter; expanding
this scope requires a root decision.

The focused pure unit regression is
`services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/config/SubjectTypeUserTypeTest.java`.
It covers lowercase `lecture`/`practice`/`lab` reads, null reads, lowercase
`lab` binding as a typed `PGobject`, and typed null `Types.OTHER` binding. It
makes no PostgreSQL or authority claim.

## Verification gate

Source-only static review is complete after the PGobject correction; the
correction itself remains NOTRUN by Gradle/Docker. H50 before this correction
passed the focused unit set (15 tests, zero failures/errors/skips); H51 then
failed in the composite tuple lookup as recorded above. The next separately
leased check must use the existing four IT selectors and fresh XML. No schema,
dependency version, global converter, proto, generated file, Assignment
mapping, or integration assertion change is part of this correction.

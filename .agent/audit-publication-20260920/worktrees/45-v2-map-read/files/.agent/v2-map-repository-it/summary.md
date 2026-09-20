# M3 campus-map repository integration proof — source freeze

Status: `H30_FOCUSED_RUNTIME_VERIFIED`. This is the bounded S3 runtime milestone
for the maps direction. The sole writer is the Luna max implementation leaf in
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-map-read`, branch
`codex/v2-map-read`, based at E revision
`b8220ac92125a8afa37598b270aa4fab7aa1f470`. The applicable rules file is
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`
with SHA256
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`; `CURRENT.md`,
the resume and safe-stop checkpoint, `MAP-READ-M2.md`, the M3 contract, backend
and tests instructions, and `rct-verification` were read before authoring.

## Scope and criteria

The only new product source is
`services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java`.
The only other M3 changes are this summary, `evidence/checks.json`, and
additive M3 entries in the parent `.agent/v2-map-read/source-manifest.json`.
The accepted M2 source/test records and hashes are preserved. No production
source, migration, schema, proto, generated output, build/config/dependency,
shared harness, seed, or real map data was changed.

The test owns one disposable `postgres:16` container, creates a fresh uniquely
named schema per test invocation, runs Flyway through target 26 with
`cleanDisabled(true)`, and stops its own container in `@AfterAll`. It uses the
existing JDBC driver, Flyway, Testcontainers, Spring AOP/JDBC, JUnit, AssertJ,
and Mockito dependencies already present in the module.

`insertGraph` uses valid V26 rows: two catalog revisions (R1 and R2), buildings
and floors whose display order differs from generated IDs, a retained published
R1 plan, an R1 changed plan, an R2 changed plan, and a floor with a null current
pointer. Assets and both format rows are inserted before publication. The
physical generated plan IDs are checked against their logical versions. The
fixture covers independent READY PNG/SVG metadata, SHA-256, dimensions,
PostgreSQL `view_box`, and timestamp mapping. The non-ready parameterized
fixture uses valid ABSENT, PROCESSING, and FAILED rows with null `asset_id`, so
a positive asset request returns `NOT_FOUND` before metadata or BYTEA access.

## Observable proof

`realJdbcReadsOrderedGraphHistoricalAssetAndMaterializationTrace` calls the
real `CampusMapReadRepository` and `CampusMapReadService` through
`JdbcTemplate`. It asserts ordered full and unchanged manifests, both format
slots, historical logical version and catalog parent identity, current pointer
resolution, no-plan representation, exact metadata, view boxes, timestamp, and
asset bytes. A delegating `ObservedDataSource` records actual SQL and wraps
`ResultSet`; it records the metadata query, a true metadata `ResultSet.next()`
row event, the bounded content query containing
`octet_length(content) <= ?`, and `getBytes("content")`. Assertions require the
metadata row event before the content query and the content read after it.
No repository rows are substituted.

`realJdbcNegativeReadsReturnTypedBoundariesWithoutContentMaterialization`
checks foreign building/floor, unknown floor, unknown version, wrong asset,
null current pointer, and an empty catalog. These use real queries and assert
typed boundaries plus no content query/read; no-plan also asserts no plan query.

`repeatableReadSnapshotStaysAtR1AcrossCommittedCatalogSwitch` applies Spring's
original service annotations through test-local
`AnnotationTransactionAttributeSource`, `TransactionInterceptor`, and
`DataSourceTransactionManager`. It does not use a manually forced
`TransactionTemplate`. After the first real current-catalog query returns, a
second actual JDBC connection atomically switches the current catalog and one
floor pointer to R2, retains the other R1 plan, commits, and releases the
reader. The first read must remain a coherent R1 graph; the following read must
observe R2 with the retained R1 historical plan. Latches, bounded futures,
`finally` rollback/close, and executor termination prevent unbounded waiting.

## Verification state

Source/static checks, JSON parsing, hash inventory, and `git diff --check` are
recorded in `evidence/checks.json` with exact commands and exit codes. H27
stopped before the focused unit tests at the shared-outbox compile blocker; H28
then ran only the narrow shared-outbox compile and succeeded. The focused unit
and integration selectors, Docker/container startup, and application runtime
remain `NOT_RUN` in this allocation. The queued commands are:

```powershell
.\gradlew.bat :services:academic-service:academic-app:test --tests ru.rutcampustrack.academic.map.CampusMapReadServiceTest --tests ru.rutcampustrack.academic.grpc.CampusMapGrpcReadTest --tests ru.rutcampustrack.academic.grpc.StudentHomeworkGrpcIdentityInterceptorTest
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.map.CampusMapReadRepositoryIT --max-workers=1
```

The later integration run must verify a nonzero class/test count in
`services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.map.CampusMapReadRepositoryIT.xml`.
A fresh independent Sol high review, root integration, and any heavy/database
runtime gates remain open. This proof does not claim the publication producer,
real inventory acceptance, full user repository, gRPC interceptor/handler
chain, or a passing test run.

## H27 execution result

Root's exclusive H27 heavy GO was used for the exact focused unit command with
`--no-daemon --no-parallel --max-workers=1 --no-problems-report`. It stopped at
the first actual failure, `:services:shared:shared-outbox:compileJava`, with
exit code `1`, before the focused Academic tests ran. The compiler reported 13
errors and 4 warnings because the shared-outbox compile classpath did not expose
`ru.rutcampustrack.shared.events.IdempotencyStore` and
`ru.rutcampustrack.shared.observability.MetricNames`; the failure is recorded as
a shared/baseline compile blocker and was not changed in this scope.

The integration selector was not started after that failure. No unit or
integration XML was produced by this attempt, so no test count or PASS is
claimed. The local PowerShell transcript is
`.agent/v2-map-repository-it/evidence/h27-unit.log` with SHA256
`70B80556F7E0AFF4CB9AAFD3F89CCF59295B00971D2768EA881495015A5C738B`.
It is a partial/tail-only transcript and is not claimed as complete
stdout/stderr; the recorded compiler summary is retained in the checks
evidence. The failed compile occurred before Testcontainers tests; cleanup
observation found no owned Java process, PostgreSQL/Ryuk container, or held
resource. Production and test source remain unchanged after source freeze; H27
is released pending the root's future focused-selector GO.

## H28 narrow compile result

After root confirmed that the observed missing `IdempotencyStore` and
`MetricNames` pattern matched the prior H20 shared-outbox boundary, the exact
conditional H28 command ran once with the requested escalated execution:

```powershell
.\gradlew.bat :services:shared:shared-outbox:compileJava --no-daemon --no-parallel --max-workers=1 --no-problems-report
```

It exited `0` after 23 seconds. The full stdout and stderr are retained in
`.agent/v2-map-repository-it/evidence/h28-shared-outbox.log`, with the split
streams in `h28-shared-outbox.stdout.log` and `h28-shared-outbox.stderr.log`,
plus the pre-command user/cache context in `h28-shared-outbox.context.log`.
The build emitted four Mongo deprecation warnings and reported three shared
compile tasks successful. This confirms the H20 pattern is reproducibly cleared
by the narrow command, without asserting a sole cause for the earlier H27
failure. H28 is released. No focused unit or integration test selector was
retried or started after H28; a new root heavy GO is required. Production and
test source remain unchanged after source freeze. No Java process,
PostgreSQL/Ryuk container, or held resource remained after H28.
## H29 focused unit result and bounded correction

H29 used the same escalated scoped execution context and ran the exact focused
M2 unit selector once. It exited `1` after 1 minute at
`:services:academic-service:academic-app:compileTestJava`, before any focused
test executed. The compiler reported one error in
`CampusMapReadServiceTest.java:754`: the `assertCode` helper passed a
`Runnable` to AssertJ `assertThatThrownBy`, which requires a
`ThrowingCallable`; the compile diagnostic is retained in the complete H29
stdout/stderr logs. The attempt produced no test XML (`xmlCount=0`), and the
integration selector was not started under the first-failure rule.

Root authorized the single bounded frozen-M2 correction: the helper now passes
`action::run` to `assertThatThrownBy`; no assertion or product behavior changed.
The updated test source SHA256 is
`39D7AD63EB5D807FA7A869AC4FCC8FEFAABAF55C84DBD7570A077159284191FB`.
H29 is released after cleanup. Docker reported an access warning while the
post-failure inventory queried Testcontainers; no owned Java process or
PostgreSQL/Ryuk container was found. H30 may run only under its allocated
exclusive GO, with the same first-failure stop rule.

## H30 focused runtime result

After the root-authorized `action::run` correction, H30 ran the exact focused
M2 unit selector and then, only after unit exit `0`, the exact M3 integration
selector in the same escalated scoped context. The unit command exited `0` after
1 minute 2 seconds and produced three nonzero XML files: `CampusMapReadServiceTest`
69 tests, `CampusMapGrpcReadTest` 12 tests, and
`StudentHomeworkGrpcIdentityInterceptorTest` 17 tests. All 98 tests reported
zero failures, errors, and skips. The complete merged unit log and split
stdout/stderr/context files are under `.agent/v2-map-repository-it/evidence/`
with their SHA256 values in `checks.json`.

The integration command exited `0` after 51 seconds and produced the required
`TEST-ru.rutcampustrack.academic.map.CampusMapReadRepositoryIT.xml` with 6 tests,
zero failures, errors, and skips. The run exercised the test-owned `postgres:16`
Testcontainers path; transient container IDs were not printed by Gradle, and
the escalated post-run inventory exited `0` with no Java, Testcontainers, or Ryuk
rows retained. H30 is released and no further map Gradle/Docker run is queued
in this slot. A fresh independent Sol review and root's follow-up integration
review remain open; the results here cover the focused selectors only.

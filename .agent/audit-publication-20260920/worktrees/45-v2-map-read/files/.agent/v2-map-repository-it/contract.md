# M3 campus-map repository integration proof

Status: frozen source-only packet, authored by the assigned M2/M3 Luna writer.
Risk: S3. Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-map-read`.
Branch: `codex/v2-map-read`. Base: `b8220ac92125a8afa37598b270aa4fab7aa1f470`.
Rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`,
SHA256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`;
also read `CURRENT.md` and `MAP-READ-M2.md`.

## Goal

Add a real PostgreSQL repository/service proof for the Academic campus-map
read path. This milestone proves immutable V26 graph reads, JDBC materialization,
asset metadata/content ordering, typed negative boundaries, and the production
repeatable-read snapshot. It does not prove publication producers or real
inventory acceptance.

## Context/evidence

M2 source is frozen and accepted in this worktree with the four imported map
models and the existing repository/service/handler implementation. The source
baseline is E revision `b8220ac92125a8afa37598b270aa4fab7aa1f470`; candidate
models originated at `86263384ae81353af087e61e9158af61f15e0999` and their source
and destination hashes remain in `.agent/v2-map-read/source-manifest.json`.
V26 defines immutable published plan identity, catalog parent FKs, current
floor pointers, exact PNG/SVG format constraints, `view_box`, and bounded
BYTEA metadata. `StudentFoundationMigrationIT` is the PostgreSQL16/Flyway
pattern (`target(26)`, per-method schema, `cleanDisabled(true)`); the homework
concurrency IT supplies bounded latch/executor cleanup patterns.

## Relevant scope

The sole product test file is:
`services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java`.
Nested test-local helpers may provide fresh schemas, Flyway, JDBC observation,
transaction advice, and graph fixtures. Local evidence is limited to
`.agent/v2-map-repository-it/contract.md`, `evidence/checks.json`, and
`summary.md`. The parent M2 manifest may receive only new test/evidence entries;
the accepted 11 product/test source records and hashes remain unchanged.

## Required behavior

Use one disposable `postgres:16` container with dynamic Testcontainers ports,
fresh schema per test, and real `JdbcTemplate`, `CampusMapReadRepository`, and
`CampusMapReadService` calls. A valid graph must include R1/R2 catalogs,
multiple buildings/floors whose display order differs from generated IDs, a
published retained R1 plan under an R2 current catalog, a changed floor, and a
no-plan floor. Draft assets/formats are inserted before publication and physical
plan IDs are asserted distinct from logical versions. Verify ordered/full and
unchanged manifests, catalog parents/current pointer and historical logical
version reads, independent PNG/SVG metadata, hashes/bytes/dimensions,
PostgreSQL `view_box` and timestamps, and exact bytes.

Exercise valid-data negatives: foreign parent, unknown floor/version and wrong
asset are `NOT_FOUND` without content reads; a null current pointer is `no_plan`;
an absent catalog is `UNAVAILABLE`; valid nonREADY ABSENT/PROCESSING/FAILED
slots retain null `asset_id`, so a positive asset request is `NOT_FOUND` before
metadata/BYTEA. Add a delegating JDBC observer proving metadata query and row
materialization precede the bounded content query and `ResultSet.getBytes("content")`.
Use Spring transaction advice over the original service annotations
(`AnnotationTransactionAttributeSource` + `TransactionInterceptor` +
`DataSourceTransactionManager`), never a manually forced RR `TransactionTemplate`.
Gate after the real first catalog query returns, switch current catalog and one
floor pointer on another actual connection, commit, resume, and assert the first
read is all R1 while the next read is R2 with retained R1 data.

## Constraints

Author source only now. Do not run Gradle, Docker, builds, runtime, or start a
container during this source-only allocation; a separate explicit root heavy
GO is required after source freeze (H27 is reserved, not active). Do not change production source,
schema, migration, proto, generated files, build/config/dependencies, or shared
harnesses. A test-local mock for `UserRepository` existence is explicitly
allowed; map repository/JDBC/service/transaction behavior must be real. Do not
disable database constraints, invent corrupted rows, reuse a shared database,
drop/clean schemas, read secrets, push, deploy, or merge main. If a production
finding appears, stop affected work and report it to root.

## Existing patterns

Follow `StudentFoundationMigrationIT` for Testcontainers PostgreSQL16,
`DriverManagerDataSource`, schema-qualified Flyway target 26, `cleanDisabled(true)`,
and bounded SQL parameters. Follow
`HomeworkStudentCompletionConcurrencyIT` for latches, executor timeouts, and
`finally` cleanup. Keep JDBC observation as a delegating test-local wrapper;
production code receives no hook.

## Acceptance criteria

The source contains meaningful real JDBC queries and service materialization,
not substituted repository rows. Later, the focused selector must produce a
nonzero integration-test XML result for
`CampusMapReadRepositoryIT` (Gradle `isFailOnNoMatchingTests=false` is not
evidence by itself). Snapshot assertions must distinguish the first coherent R1
read from the subsequent R2 read. Current proof remains bounded: it does not
claim the full gRPC/user/publication producer chain. Tests are authored and
remain unrun in this source-only milestone.

## Verification

Now run only source/static checks, `git diff --check`, JSON parsing, and hash
inventory. Record exact revision, worktree, commands, exit codes, Windows/JDK
environment, and preserved M2 hashes. Planned main commands are:

```powershell
.\gradlew.bat :services:academic-service:academic-app:test --tests ru.rutcampustrack.academic.map.CampusMapReadServiceTest --tests ru.rutcampustrack.academic.grpc.CampusMapGrpcReadTest --tests ru.rutcampustrack.academic.grpc.StudentHomeworkGrpcIdentityInterceptorTest
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.map.CampusMapReadRepositoryIT --max-workers=1
```

The IT XML is expected at
`services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.map.CampusMapReadRepositoryIT.xml`;
after execution main must assert class/count nonzero. Heavy execution remains
queued behind source freeze and a separate explicit root GO. A fresh full
changed-scope Sol high review remains required.

## Do not

Do not create children or reviewers, use Terra, edit accepted M2 source/tests,
replace existing module ITs, add dependencies, alter schema/build/config/proto,
use fixtures as producer proof, force transaction isolation manually, run a
container/heavy check in this allocation, or claim PASS from no run. Release with source,
evidence, preserved-hash proof, queued commands, open gates, and no resources
held.

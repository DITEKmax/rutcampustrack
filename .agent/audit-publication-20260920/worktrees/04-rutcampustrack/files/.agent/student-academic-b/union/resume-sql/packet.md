# SQL7 resume packet — compact contract

Date: 2026-09-08
Agent: fresh bounded implementation leaf `/root/resume_sql_foundation`
Model/effort: `gpt-5.6-luna` / `max`
Risk: S3 (schema migration, data preservation, transaction and concurrency invariants)
Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
Owner: this leaf is the sole writer for the seven paths below in worktree
`C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack`.

## 1. Goal

Finish the four WIP SQL migrations and three migration integration test files
as the B0 source/schema boundary. Prove the accepted SQL invariants in fresh
PostgreSQL 16 databases and preserve the pre-existing dirty worktree. This
does not implement the student role, auth wiring, service entities, endpoints,
or cross-service runtime.

## 2. Context and evidence

The frozen canonical contract is `.agent/student-academic-b/union/contract.md`
SHA `C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74`.
The bounded correction gate is `sql-correction-gate.md`; it records the V25,
V17 and V26 corrections and does not expand scope. Accepted source recovery is
`.agent/student-academic-b/union/resume-recovery/manifest.json` SHA
`67DFF5431BA38624675572092A464CF366E0AE8D40352E03C1201C36E5ADE912`.
The A authority source is the shared integration contract at the 1456 worktree,
SHA `47FC36F57AFCEEC489B5C95C6C169C2CBA9F53FEED2287CF6946B1CB150D807E`.
The D authority source is the map backend patch contract at the d650 worktree,
SHA `F7DF5F4074AE4C205889A2EFCB4D3B8189ECCD7FCB8BE43A0C5AB1E4BE35D659`.
The root gave an explicit GO after source recovery and stopped the previous
numeric writer. Existing dirty files, imported bytes, numeric5 and the
separate UI worktree remain outside this leaf's ownership.

## 3. Relevant scope

Only these seven product paths may be edited:

1. `services/academic-service/academic-app/src/main/resources/db/migration/V24__auth_session_authority.sql`
2. `services/academic-service/academic-app/src/main/resources/db/migration/V25__student_subject_homework_foundation.sql`
3. `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql`
4. `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql`
5. `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java`
6. `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java`
7. `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/FlywayMigrationIT.java`

New evidence and records are confined to `.agent/student-academic-b/union/resume-sql/**`.
No other union evidence, source originals, manifests, or foreign worktrees are
rewritten.

## 4. Required behavior

V24 must validate legacy role/status/headman facts explicitly, convert only
those facts, provide positive role versions, composite grant/session/event
ownership foreign keys, a role-version trigger, and append-only security
events. It must reject inconsistent source before partial DDL and introduce no
inferred grants or runtime authority.

V25 must abort before DDL when legacy assignments/homeworks lack provenance;
seed only the exact legacy subject type; support the closed lecture/practice/lab
set and deferred parent-plus-child minimum of one type, including deterministic
concurrent deletion coverage; enforce assignment identity, interval closing and
same-teacher overlap rules; reject assignment deletion; and enforce immutable,
positive, hashed, unique homework binding identity.

V17 must require positive non-null `schedule_items.assignment_id`, guard
unbound legacy templates and missing physical/one-off provenance before DDL,
use stable logical occurrence identity while retaining old physical generations,
enforce immutable physical snapshots and origin, permit multiple distinct
pending/active bindings per occurrence, preserve archived history, and provide
durable replay/lifecycle constraints. Restore generation+1 and recurring
template/date uniqueness must be representable.

V26 must create tables before triggers, use positive generated identifiers and
versions, retain immutable map assets, validate ready asset MIME/hash/bytes,
maintain floor-current-version ownership, make open intents immutable with
explicit `accepted_at + 48h` expiry and UTC-day dedupe/retention, reject early
delete, and atomically maintain per-floor/day aggregates. No seed inventory,
provider, HTTP or map configuration wiring belongs here.

## 5. Constraints

Preserve all foreign changes and numeric5. Do not commit, stage, reset, clean,
drop, repair, deploy, migrate production data, read secrets, or change proto,
DTO, entity, controller, build, generated or global configuration files. Do not
run Gradle, containers or PostgreSQL commands until root grants the runtime
lease. Do not create children or send application messages. WARN/ERROR requires
a request-linked reproduction and evidence before code changes. Terra is not
permitted without the recorded defect/complexity gate and root decision.

## 6. Existing patterns

The project uses Java 21, JUnit 5, Flyway and PostgreSQL 16 Testcontainers.
Baseline migrations are ordered V1..V23 (academic) and V1..V16 (schedule).
Tests must use fresh isolated databases or transaction rollback, no reuse, and
must not destroy another process's container. Existing `FlywayMigrationIT`
must preserve its V1-to-V16 data case while asserting an intentional V17
abort for unbound legacy data.

## 7. Acceptance criteria

The seven-path diff is stable and every required invariant has a meaningful
SQL behavior test: clean full-chain migration/validate/repeat-zero, malformed
source atomic abort with no applied migration or partial DDL, V24 grant/session
ownership and version/event rules, V25 subject minimum/interval/assignment and
homework rules (including reproduced concurrent minimum), V17 retained
generation/multiple-binding/ownership/replay rules, and V26 cross-day/retry TTL
and aggregate rules. B1 auth/transfer/event/attendance runtime remains OPEN.

## 8. Verification

First run deterministic static checks and SQL/test compilation after edits.
After root's exclusive lease, run the exact integration selectors validated
against the current wrappers, using isolated database names and dynamically
assigned ports. Record revision, command, exit code, environment, test XML and
runtime evidence under `resume-sql/`. Include checks for the two adjacent
existing `MigrationConcurrentlyTest` selectors as a gate only; those files are
outside scope and must not be changed to satisfy a regex. Save final seven-file
SHA/diff and stop writes after the stable result for root acceptance and fresh
Sol review.

## 9. Do not

Do not redesign the B0 contract, infer legacy provenance, add cross-database
foreign keys/backfills, add a full student role, wire endpoints or events,
weaken constraints for a green test, fabricate map catalog rows, or claim
runtime PASS before the root lease and actual PostgreSQL execution.


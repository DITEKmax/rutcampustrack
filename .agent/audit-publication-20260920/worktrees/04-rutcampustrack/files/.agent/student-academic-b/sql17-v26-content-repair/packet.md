# SQL17/V26 content repair packet

Date: 2026-09-10. Agent: `/root/sql17_26_content_repair`. Model/effort:
`gpt-5.6-luna` / `max`. Risk: S3 (migration constraints, immutable graph,
retention and origin ownership). Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
This packet is the bounded correction contract for the current sole writer.

## 1. Goal

Correct the proven V17 logical/physical origin crosswire, add two behavior
cases for recurring and one-off origin rejection, and repair the explicitly
accepted V26 content defects while adding focused PostgreSQL 16 behavior
coverage. Preserve all foreign work and keep the change inside the four paths
listed below.

## 2. Context/evidence

The prior SQL contract and evidence are in
`.agent/student-academic-b/union/contract.md` and
`.agent/student-academic-b/union/resume-sql/packet.md`. The prior V17 source
SHA was `098E21966A3BB5999D8B38CC71587AFBE3C74CA70808F44CBE65256BA35CF009`
(18,592 bytes); its trigger compares occurrence snapshot fields but omits
`schedule_item_id` and `one_off_lesson_id`. Reproduction: two recurring
templates or two one-off origins with identical snapshots allow a lesson to be
bound to the wrong origin.

Root recorded a fresh independent Sol FAIL for V26 and explicitly expanded
scope to V26. Reproduction 1: a second asset for a plan/version can replace a
READY format slot after publication, and plan-version `floor_id`, `version` or
`catalog_revision_id` can be updated. Reproduction 2: the persistent daily
dedupe FK with `ON DELETE RESTRICT` prevents an expired open intent from being
deleted while its UTC-day retention key must remain. Required correction is
published graph freezing with parent-row lock serialization, legitimate draft
format transitions, and decoupled intent/dedupe TTLs with insert-time locked
matching validation.

Pre-write guards matched exactly:

| Path | SHA256 | Bytes |
| --- | --- | ---: |
| `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql` | `098E21966A3BB5999D8B38CC71587AFBE3C74CA70808F44CBE65256BA35CF009` | 18592 |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java` | `8348290F0440C713E144FC28BD998A65BDA38D368ABEA0D560646DF65FF8ECF5` | 20416 |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java` | `27FAE6FD189C1B13F7C192D4C7641FE6EB95BBEE0F5DA6E00BCD81B9F6FAA5F1` | 24922 |
| `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` | `315E3F72392404622901E0EA88B24C3F9FBF6E49FAFBDE646377B23AD06BB01B` | 15099 |

`java`/`javaw` process preguard was clear. Existing Git modifications and
untracked files are foreign work and remain preserved.

## 3. Relevant scope

Product writes are limited to exactly:

1. `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql`
2. `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java`
3. `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java`
4. `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql`

Evidence writes are limited to this new directory:
`.agent/student-academic-b/sql17-v26-content-repair/`.

## 4. Required behavior

- V17 physical snapshot validation must use null-safe `IS DISTINCT FROM`
  equality for both origin columns as well as existing occurrence identity
  fields.
- Schedule IT must independently reject a recurring lesson wired to another
  identical-snapshot template and a one-off lesson wired to another identical-
  snapshot origin, while retaining valid cases.
- V26 plan-version identity/ownership (`floor_id`, `version`,
  `catalog_revision_id`) is immutable. Publication is one-way
  `NULL -> non-NULL`; a published plan graph is frozen, including format row
  insert/update/delete and asset link changes. Format edits lock their parent
  plan row so publication/edit races serialize. Draft format state transitions
  remain valid.
- V26 ready format rows must match immutable asset content type, hash, bytes and
  dimensions. Assets remain immutable.
- V26 open intent identity/payload is immutable and conflicting `(owner_hmac,
  intent_id)` payloads fail. Daily dedupe insertion locks and validates its
  matching intent but has no persistent FK that blocks expired intent cleanup;
  immutable dedupe keys increment the floor/UTC-day aggregate once.
- V26 deletion is blocked before `accepted_at + 48h` for intents and before UTC
  day-end plus 48 hours for dedupe keys; after both deadlines the dedupe row can
  be removed first and the intent can be removed while aggregate history stays.
- Academic IT must assert database effects and SQLSTATE-classified failures
  against the migrated PG16 schema, with deterministic database-controlled or
  fixed timestamps.

## 5. Constraints

Do not edit V24/V25, FlywayMigrationIT, services/proto/frontend/build/generated
files, shared status, or foreign `.agent` state. Do not run Gradle, Docker,
Testcontainers or PostgreSQL runtime in this turn. Do not stage, commit, reset,
clean, drop, repair or deploy. Do not read secrets. Preserve dirty work and do
not create children. WARN/ERROR only matters after request-linked reproduction
and evidence; Terra is disallowed unless root records a new gate.

## 6. Existing patterns

Reuse current schema-isolated `StudentFoundationMigrationIT` and
`StudentOccurrenceMigrationIT` helpers, `Flyway.configure` target selection,
`SAME_THREAD`, and AssertJ/Spring JDBC style. Use actual constraints, triggers,
functions and returned database rows; no SQL-text-only assertions or broad
exception weakening. Use PG16-compatible SQL and deterministic timestamps.

## 7. Acceptance criteria

- Exactly the four product paths above and this evidence directory are changed
  by this leaf.
- V17 has both origin comparisons and two explicit crosswire rejection tests.
- V26 freezes published plan identity/graph with draft transitions and parent
  lock, decouples intent/dedupe TTLs, and preserves exactly-once aggregate
  behavior.
- Academic IT covers ready metadata/asset immutability, intent identity and
  conflicting payloads, dedupe/aggregate idempotency, and both retention gates.
- V26 post-SHA is recorded and no unrelated path is changed.
- Java source is syntactically coherent by readback; scoped `git diff --check`
  passes. Runtime remains pending root's heavy lease.

## 8. Verification

Run only static/read-only checks: guarded post-SHA and byte counts for all four
product paths, targeted `rg` markers, full readback of changed methods, Java
brace/parenthesis balance as a smoke check, and scoped `git diff --check` for
the four paths plus this evidence directory. Record command, exit code,
environment, revision, posthashes and limitations in `evidence.md` and
`checks.md`. Do not claim PG16 runtime PASS; root owns the later runtime lease
and fresh independent Sol recheck.

## 9. Do not

Do not redesign the SQL contract, broaden ownership, change V26 behavior beyond
the two root-recorded defects, weaken checks for green tests, or alter foreign
work. Do not claim runtime evidence without an actual run. If a new V26 defect
requires another path, stop product writes and report the exact reproduction and
bounded expansion request to root.

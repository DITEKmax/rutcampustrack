# B0 implementation packet

Date: 2026-09-08
Role: fresh bounded implementation leaf, sole writer for the B0 union scope
Model/effort: gpt-5.6-luna / max
Risk: S3 (schema, migration abort semantics, and additive contracts)
Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
Frozen contract: `contract.md`, SHA256 `C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74`
Path/guard manifest: `paths.json`, SHA256 `14219D88304A419E4FBDBB4FC62BBCCC190D34A3E78ED79EB6E331B6987E40EC`

## 1. Goal

Implement the approved staged B0 source boundary: import the exact accepted
source union, add V24/V25/V26 and V17 SQL migrations, extend `academic.proto`
and `schedule.proto`, and add the standalone Java DTO/API declarations in the
32 owned paths. Add meaningful migration/contract checks without implementing
B1 services or runtime endpoints.

## 2. Context and evidence

The accepted source union is the exact 42-file delta at
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e`, followed by the 45-file
`dependency-combined-review` snapshot and 32-file `requests-review-3` snapshot,
then the four `auth-purpose` files. The three delta/dependency overlaps are
dependency-owned. Source manifest hashes and per-path raw SHA guards are in
`paths.json`; the source preflight is adjacent. Before this packet was written,
all 81 source SHA checks matched and all import destination preimages matched.
The initial destination guard is the clean 8002 working tree; CRLF/raw working
tree hashes are retained separately from Git blobs.

The accepted original artifacts remain the source of truth. A live map TypeScript
file is informational drift only: old recorded hash
`CB2B037252953B324F0A4375B367662DFD567A50A42ADC04DD219AF4D9602CD1` is retained,
while the independently observed live hash is
`31A32B3BF2539F2D060B0DB3DA6B2BD4E6BDA724A019B38EBEB168109DCF90AA`.

## 3. Relevant scope

Only the exact imports in `paths.json`, the 32 reserved destination paths, and
new evidence/status files under `.agent/student-academic-b/union/` are in scope.
The 32 paths are V24/V25/V26, V17, the two protos, 13 auth contract files, two
map contract files, eight academic/schedule homework and lesson files, and
three migration tests. Existing `academic.proto`, `schedule.proto`, and
`FlywayMigrationIT.java` are treated as guarded preimages. Numeric production
and test artifacts and the independent UI worktree are foreign work.

## 4. Required behavior

Preserve accepted imports byte-for-byte. Implement the SQL invariants from the
canonical contract: V24 session/grant authority with append-only events and
version trigger; V25 subjects, closed lesson types, deferred minimum-one type,
immutable bounded assignments with same-teacher overlap exclusion and
different-teacher allowance, homework binding request idempotency, and atomic
ambiguous-row abort; V26 private immutable map assets, readiness metadata,
versioned catalog/floor ownership, and durable HMAC-keyed publish intents; V17
recurring and one-off physical lessons, immutable occurrence assignment
snapshots, revision-one logical occurrences, partial slot/occurrence uniqueness,
append-only lifecycle, durable replay and binding structures, and transfer-safe
constraints. Migration history and legacy tables must remain intact.

Keep proto additions finite and tag-compatible with the frozen fields and RPC
names: assignment batch lookup, homework binding reserve/confirm/read, and
occurrence history. Preserve `teacher_id` tag 5/name as reserved and
`HomeworkInfo.completed_at` tag 10. IDs, versions and revisions are positive
int64 on the wire and decimal strings in Java DTOs; optional presence is
explicit. Map metadata omits only absent width/height/viewBox properties and
preserves required non-ready null/zero values.

Keep APIs as declarations only. Use Java 21 records/interfaces and existing
Java-first contract patterns. Do not wire controllers, services, auth admission,
OpenAPI/TypeScript generation, or B1 transfer/binding behavior.

## 5. Constraints

This leaf is the sole writer for this scope. Preserve foreign dirty and untracked
work. Do not modify `proto/attendance.proto`, temporary C-owned DTO files,
numeric files/evidence, historical migrations, B1 domain/controller files, or
the live informational map TS. Do not deploy, migrate production data, delete
data, read secrets, change global configuration, or run Gradle before root grants
the separate lease. Any path outside the exact 32 plus accepted imports and
union evidence requires a bounded root decision before writing.

## 6. Existing patterns

Follow the current Java 21 record/API contract layout, Flyway version sequence,
PostgreSQL 16 syntax, generated protobuf Java build conventions, and existing
migration integration test style. Generated Java remains build output. Use the
canonical D map backend patch authority and root B0 corrections; do not invent
an OpenAPI exporter or hand-generated runtime spec.

## 7. Acceptance criteria

The exact source union and all 32 owned paths are present with clean scoped
diffs and stable provenance. V24/V25/V26/V17 validate on a fresh PostgreSQL 16
database, including invariant and atomic ambiguous-row abort tests; the existing
schedule chain still preserves V1 through V16 and proves V17 abort/history
behavior. Protobuf compilation and standalone contract compilation pass. The
result has no B0 critical findings and is ready for a fresh independent Sol
high review. Runtime endpoints/auth admission, service wiring, events,
concurrency integration, and attendance remain explicitly open for B1/B2.

## 8. Verification

Record source/destination before/after SHA256, import order, revision, command,
exit code, environment, and evidence under `union/`. Run lightweight static
checks while implementing. After root grants the lease, run the exact proposed
Gradle contract compilation/protoc and isolated migration integration tasks
against fresh PostgreSQL 16 databases with task-owned ports and no cleanup or
repair after failures. Product runtime is N/A until B1 endpoints/services exist.

## 9. Do not

Do not expand scope, redesign contracts, add stubs or fake endpoints, invent
legacy provenance, reuse reserved proto tags, dual-write legacy authority,
quietly import live TS drift, rewrite numeric evidence, edit attendance proto,
create children, or claim runtime PASS from compilation alone.

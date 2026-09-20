# L5B v3 correction: binding concurrency
## Goal
S3 close independent full-v2 HIGH finding before design acceptance. Docs only; no lifecycle implementation.
## Context/evidence
RULES C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. v2 proposal04AF82B8086AD41AECB871E02F20837390ABC0350A4C250E4B0BF2AE07D7239D; sourcesFAEAAA26E084F819957AD76650EBC21F8D93D4BD4BBB4784CC0B9437131D8F6B. Fresh Sol full review FAIL1 HIGH. Proposal59/112/165 lacks common binding lock/CAS. Confirm request schedule.proto180 carries binding/homework/key, no expected revision. V17 binding guards343 allow pointer change and equal revision; do not prevent stale Confirm publication.
## Relevant scope
Lessons lead sole docs writer .agent/lessons-l5b-design. Preserve exact v2 snapshots. Source bases accepted Academicf59 and Schedule426a15b6, no product mutation. Root owns this packet/shared status.
## Required behavior
Specify one shared ordering for Reserve/Confirm/Transfer/Cancel/Restore: fence/origin then occurrence/current physical then bindings sortedID then replay/outbox. Confirm may first read immutable routing identity but must acquire ordered locks and reread current state/pointer/revision/actor/request hash. Publish only intended fields with persisted revision CAS; never write stale current_lesson_id from detached entity. State explicit ARCHIVED terminal/replay outcomes, transfer target publication, cancellation/restore races and lost-response recovery. No binding-first then occurrence order.
## Constraints
Astra low lead docs only; no new leaf required. No children/runtime/Docker/Gradle/product writes/deploy/migration. Preserve owner B2 and retained history decisions. Actual L5A current deadlock correction remains separate unproven-runtime gate.
## Existing patterns
V17 physical/binding invariants, Schedule binding ownership, BEFORE_COMMIT outbox, durable request replay. Prior v1 B2 and Academic global lock-graph findings closed at design level; do not regress them.
## Acceptance criteria
Eliminate concrete interleaving: Confirm reads PENDING L1 rev1, Transfer commits L2 rev2, stale Confirm writes ACTIVE L1 rev2. Eliminate opposite binding/occurrence lock cycle. Complete design describes authorized state outcomes and replay for every participating writer. Fresh FULL proposal review after freeze, not finding-only review.
## Verification
Source/protocol review; runtime N/A for docs. Add planned real-PG controlled tests confirm versus transfer, confirm versus cancel, reserve versus restore, duplicate/lost response and persisted CAS failure. Verify source/hash map and send frozen proposal/sources/checks. Implementation proof remains future gate.
## Do not
No claims actual race reproduced in runtime, product readiness, implicit Homework resurrection, weakened revision guards, speculative Mongo migration, or treating acknowledged engineering decisions as accepted owner decisions.

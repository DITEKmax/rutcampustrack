# Lesson cancellation and restoration — staged lifecycle decision

07.09.2026. S3. Fresh Sol xhigh read-only consultation under owner routing; not dispatched. No writes or children. Coordinate after the subject/assignment/occurrence decision is available.

## Goal
Freeze the minimum lifecycle contract needed for correct student Today, attendance, Homework and statistics when a lesson is cancelled retroactively and later restored.

## Context/evidence
Read active-contract.md, lesson-lifecycle-source-note.md, backend-conflicts R7 and accepted subject/occurrence decision when available. R7 requires retained invalidated marks, visible cancellation/reason, denominator exclusion, linked Homework archive and an empty grid on restoration. Root's prior source reads found Schedule cancel emits an event without semesterId, restore emits none, Attendance currently has one lesson/user row without generation history, one-off cancellation can delete marks, and Academic Homework binds a tuple rather than an authoritative occurrence ID. These are source gaps, not verified new behavior. Preserve accepted Homework API and Requests32 invariants.

## Relevant scope
Read current Schedule recurring/one-off cancel/restore/delete/service/events/outbox, Attendance lifecycle consumers/read/write/pair lock/request decision paths, Academic Homework storage/query/events, corresponding proto/contracts/migration inventories and focused tests. Final future ownership requires one writer for shared contracts/generation/lifecycle state and an actual integrated baseline. No implementation now.

## Required decision
Choose explicit occurrence/generation and event revision semantics that preserve invalidated historical marks but prevent old values from reappearing on restoration. Define idempotency and out-of-order handling, cancellation/restore payload, transactional outbox boundaries, one-off parity and snapshot/read representation. Explain how a restored past lesson becomes PLANNED/ACTIVE/CLOSED under scheduling without reviving prior attendance. Define authoritative Homework binding/archive/filter semantics and what restoration may expose under R7 without inventing a new product policy. Preserve request archive and ensure pending approval cannot resurrect a CANCELLED or prior-generation mark. State minimal changes in required write boundaries that make this data real, not nullable test-only fields.

## Constraints
No destructive cleanup, production migration, blanket attendance deletion, old-record auto-revival, guessed event order by arrival time, arbitrary teacher/assignment backfill, or unrelated role UI. Do not erase audit history or synthesize absent marks as eligibility. Subject/assignment/transfer identity is a coordinated prerequisite, not a separate competing model. Terminal role/roster authority belongs to the profile decision.

## Existing patterns
Schedule and Academic PostgreSQL migrations/outbox, Attendance Mongo transactions and pair locks, accepted Requests receipt/decision guards, read ports for reporting. Reopen actual producer/consumer ordering and event schemas. Preserve Java-first/gRPC contracts and field numbers. Root notes and historical comments cannot substitute for originals.

## Acceptance criteria
Return nine-section implementable bounded packet with exact lifecycle model, fields, service/API/event/migration ownership, ordering and root-visible remaining product ambiguity. Cases: current/past/future recurring and one-off cancellation, duplicate/reordered events, restore then delayed old cancel, concurrent approval/cancel/restore, repeated restore, preserved archive/history, no stale completion visibility and correct statistics denominator. Name precise data migrations but never run them.

## Verification
Consultation runtime N/A. Future real PostgreSQL/Mongo/outbox/consumer tests, duplicate/reordered delivery, cross-service Homework/archive and Attendance/report behavior, authz and request-race tests, then fresh Sol high review. Record actual revision/commands/exits/evidence and clear scope limits.

## Do not
No writes, children, production actions, missing-data guesses, new compatibility layer, collapsing cancelled history into ordinary ABSENT, full-role PASS or changing active writers. Root will freeze sequential integration after source decisions.
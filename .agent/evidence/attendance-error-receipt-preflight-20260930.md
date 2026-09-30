# Attendance tracked-effect ERROR receipt preflight — 2026-09-30

## Goal

Make tracked Schedule effect scope rejections persist an immutable `ERROR` receipt and its ACK in the same Mongo transaction, while leaving attendance and cancellation-marker domain state unchanged.

## Context/evidence

- Current correction base: `9a25b3d3b8661d00eeee5d4a607a9b6ac82b4cb2` on `codex/semester-archive-attendance-20260929`.
- Canonical project instructions: `.agent/orchestration-v2/RULES.md`, SHA-256 `397C1B0984648CA2EE7066DEFCB5DEE797DCC546B3C760443012F817ECFAD976`; current handoff and frozen semester-archive contract were read.
- Independent Sol bounded recheck found that an `ATTENDANCE_SCOPE_MISMATCH` thrown through nested REQUIRED `LessonEventService` marks the outer receipt transaction rollback-only. A cancellation marker upsert and legacy-semester update can also precede a later scope check.
- Root authorized one connected Attendance correction: preflight all affected lesson rows/markers under existing fences before nested effect calls; preserve domain/receipt/ACK atomicity and exact replay identity. Root currently owns the HEAVY runtime lease, so no Gradle/Docker command has been run for this correction.

## Relevant scope

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/SemesterArchiveEffectService.java`
- Existing compatibility stub in `EventConsumerTest` and one focused real-Mongo regression in `LessonTransferParticipantIT`.

## Required behavior

- Event effect transaction acquires the authoritative semester fence, then runs a no-domain-write preflight that takes the existing accepted-effect semester/lesson fences and validates every relevant attendance row and cancellation marker.
- Only after preflight succeeds may the nested transactional domain effect run. A typed scope rejection is caught by the outer transaction and commits its durable `ERROR` receipt plus ACK.
- Cancellation and close validate attendance and marker scope before legacy-row updates or marker upserts. Batch deletion validates every lesson ID before deleting any attendance. One-off cancellation validates every natural-key match before deleting.
- Exact receipt replay remains receipt-only and re-enqueues the saved ACK without preflight, RPC, or another domain mutation.

## Constraints

- Keep changes in `services/attendance-service/**` plus this task-owned evidence note.
- Retain existing semester-before-lesson lock order and transaction boundaries. Do not use `noRollbackFor` to commit partial state.
- Keep external Schedule/Academic calls outside the Mongo transaction. Do not change replay identity, event schema, shared outbox, Academic, Schedule, protobuf, public API, or production data.
- Do not run Gradle/Docker until root releases the HEAVY lease. Do not push, merge, or touch preserved foreign `.agent` changes.

## Existing patterns

- `SemesterArchiveEffectService` owns the local effect receipt/ACK transaction and exact receipt matching.
- `PairWriteCoordinator.lockAcceptedScheduleEffectLessons` reuses the accepted Schedule-effect semester fence and lesson fences.
- `LessonEventService` remains the domain owner; preflight methods are non-transactional entrypoints called while the outer effect transaction is already active.

## Acceptance criteria

- Real Mongo mismatch scenario commits `ERROR/ATTENDANCE_SCOPE_MISMATCH` receipt and ACK; the conflicting attendance row and marker state remain unchanged.
- Existing legacy attendance plus a conflicting cancellation marker does not acquire a new semester value before the durable rejection.
- A `lesson.deleted` batch with a valid first ID and mismatched second ID is rejected before either attendance row is deleted.
- The existing `EventConsumerTest` remains source-compatible with the new callback overload; replay still uses the receipt-only overload.
- No typed scope rejection is intentionally thrown by a nested service after its first domain write.

## Verification

- `git diff --check -- services/attendance-service`: exit 0; line-ending conversion warnings only.
- Runtime check: **not run**. Waiting for root to release the HEAVY lease; no result is claimed for unit or real-Mongo execution.
- Planned bounded runtime, subject to root lease: existing `EventConsumerTest` and only `LessonTransferParticipantIT.mismatchedTrackedEffectScopeCommitsErrorAckWithoutPartialDomainWrites` with JDK 21 and one Gradle worker. No broad suite or unrelated passing method will be repeated.

## Do not

- Do not change the existing fence/latch criterion, transaction rollback rules, receipt identity, or delivery count.
- Do not delete request-owned evidence, alter foreign files, introduce a distributed lock/framework, or treat a typed scope mismatch as successful application.

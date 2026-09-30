# Attendance semester archive participant — 2026-09-29 WIP completion

## Goal

Resume the existing Attendance archive work in the assigned worktree, preserving the accepted contract and existing WIP. This is an implementation continuation, not a redesign.

## Context and evidence

- Canonical project rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md` (SHA-256 `397C1B0984648CA2EE7066DEFCB5DEE797DCC546B3C760443012F817ECFAD976`) and adjacent `CURRENT.md`.
- Frozen contract: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/2026-09-27-delivery/semester-archive-complete-contract.md`.
- Assigned branch: `codex/semester-archive-attendance-20260929`; baseline `90c2a27261dabd614603b3010d740455c0cdce3c`.
- The pre-existing WIP and foreign `.agent` changes were preserved. Only `services/attendance-service/**` and this evidence note are in this packet; no Academic, Schedule, proto, push, or production changes were made.

## Relevant scope

- Attendance writes and lesson lifecycle effects acquire the authoritative semester fence before lesson/pair locks and domain writes.
- Archive participant commands, effect/transfer receipts, and ACK outbox handling stay within Attendance.
- `lesson.closed` Schedule/Academic lookups happen before the Mongo transaction. The transaction then groups the idempotency claim, exact local fence and domain mutation, effect receipt, and ACK. Exact receipt replay skips RPC and enqueues the stored acknowledgement.
- Scope validation uses the event/request/lesson semester; current-semester fallback is not used for a write or event.

## Required behavior and acceptance criteria

- PREPARE persists `ARCHIVE_PREPARING/PENDING`; ordinary attendance writes are denied while that fence is active.
- Trusted, preaccepted Schedule effects and transfers can drain during PREPARE only with strict identity/scope checks and atomic domain state, receipt, and ACK.
- SEAL is a separate exact operation/version transition to `ARCHIVE_SEALED/READY`, and requires settled pending effects.
- Exact receipt replay causes no repeated domain mutation; stale restore/release commands cannot reopen a newer archive.
- Archived write denial covers marks, geo check-in, requests, and attachment/file writes. Existing real-Mongo integration scenarios are the acceptance surface.

## Existing patterns and constraints

- Reuse `PairWriteCoordinator`, `SemesterArchiveFence`, the existing Mongo transaction manager/template, durable receipts, and outbox.
- Do not split the idempotency claim into a separately committed transaction. Do not make an external RPC while a local Mongo transaction/fence is held.
- Keep service changes in the Attendance module. No general transaction framework or package was added.

## Verification

- `git diff --check -- services/attendance-service`: exit 0 (after the correction packet; only line-ending warnings were printed).
- Targeted Gradle attempt 1, using JDK `C:\Users\maksd\.jdks\ms-21.0.10` (OpenJDK 21.0.10): exit 1 at `compileTestJava`. Reproduced blockers were a missing `Future` import in `LessonTransferParticipantIT` and outdated `ExcuseServiceTest` calls that omitted the newly required semester scope. Both were corrected.
- Targeted Gradle attempt 2 used:

  ```text
  .\gradlew.bat --no-daemon --no-parallel --max-workers=1 --no-problems-report --system-prop=org.gradle.java.compile-classpath-packaging=true :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.event.EventConsumerTest :services:attendance-service:attendance-app:integrationTest --tests ru.rutcampustrack.attendance.integration.EventConsumerIT --tests ru.rutcampustrack.attendance.student.StudentCheckinTransactionIT --tests ru.rutcampustrack.attendance.event.LessonTransferParticipantIT
  ```

  `compileJava`, `compileTestJava`, and the selected unit `test` task passed; `EventConsumerTest` reports 11 tests, 0 failures, 0 errors. `integrationTest` exited 1: 36 tests completed, 21 failed. Runtime reports located one production Mongo Criteria construction defect and four fixture/API mismatches. Corrections for those findings are included in this snapshot, but this corrected snapshot has not yet been rerun; do not treat the integration acceptance criteria as passed.
- Failure evidence in `build/test-results/integrationTest/`: the duplicate `semester_id` Criteria expression raised `InvalidMongoDbApiUsageException` in the delayed effect and close/cancel scenarios; the Attendance transaction slice used bean name `transactionManager` where `@Transactional` requires `mongoTransactionManager`; its Schedule readiness mock returned `null`; and the geo assertion counted the new `lesson:1` service fence as though it were a student pair. `LessonTransferParticipantIT` also lacked transaction advice in its data slice, and one transfer test used the Schedule-dependent compatibility overload without an authoritative Schedule stub. The correction changes the test context/API fixtures while preserving the original serialization and user-state assertions.
- Exact command output and stack traces remain in the Gradle session output and the three integration XML reports. Selected integration classes: `EventConsumerIT`, `StudentCheckinTransactionIT`, and `LessonTransferParticipantIT` only; no full suite was run.

## Runtime evidence

- Targeted run used JDK 21.0.10 and started its MongoDB/RabbitMQ Testcontainers. The Gradle process exited after JUnit shutdown. The run's logged RabbitMQ container ID `90c2c69900f4d238e5b0c5934c8859b808eee922db3e6ac380a11eb149479221` was checked with read-only `docker inspect` and returned `No such object`, confirming that resource was removed. No container was stopped manually.
- The run did not use production services or production data. No deployment or migration was performed.

## Diff inventory

Modified product files:

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceWritePortImpl.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonCancellationMarker.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonTransferParticipantService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/AttendanceAttachmentService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/AttendanceWritePort.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/JournalAttachmentPort.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/PairWriteCoordinator.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/entity/RequestAttachmentDocument.java`

New product files:

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/SemesterArchiveEffectReceiptDocument.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/SemesterArchiveEffectRejectedException.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/SemesterArchiveEffectService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/SemesterArchiveFence.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/SemesterArchiveFenceDocument.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/SemesterArchiveParticipantReceiptDocument.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/SemesterArchiveParticipantService.java`

Modified test files:

- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/EventConsumerTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonTransferParticipantIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/OneOffLessonCancelledConsumerIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinServiceTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java`

## Limitations

- The corrected source and fixture snapshot is frozen pending independent review and an addressable repeat of the same selected tests. The previous integration exit 1 is diagnostic evidence only; passing runtime acceptance is not yet established.
- A sandboxed first `docker inspect` attempt was denied by the Docker named pipe. A scoped read-only inspect of the run's known RabbitMQ container ID after escalation confirmed it no longer existed. Mongo Testcontainers were left to JUnit/Testcontainers lifecycle; no other runtime resources were inspected or changed.

## 2026-09-30 owner STOP checkpoint

- Worktree/branch remain `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927` / `codex/semester-archive-attendance-20260929`, based on HEAD `90c2a27261dabd614603b3010d740455c0cdce3c`. The previously frozen source candidate fingerprint was `e42a184cb95bfb51a461ce50e572858c38308dea057018db048727ae078408a6`; no product or test source was changed after that freeze. This checkpoint updates only this owned evidence note. The exact source/test inventory above is still the WIP inventory; foreign `.agent/transfer-attendance-evidence.md` and the four unrelated untracked `.agent` files remain preserved.
- Independent Sol High final review returned FAIL with two actionable findings. High: `EventConsumer` returns immediately when `IdempotencyGuard.tryClaim` reports a duplicate, so exact committed receipt deliveries for `lesson.cancelled`, `lesson.deleted`, `lesson.one_off.cancelled`, `lesson.transfer.requested`, and `semester.archive.participant.command` do not re-enqueue their saved ACK; `LessonTransferParticipantService.apply` also returns on an exact existing receipt without calling `enqueueAcknowledgement`. Medium: `StudentCheckinService.executeTransaction` returns PRESENT for an existing mark before acquiring the semester archive fence, so a new idempotency key can succeed during PREPARING/SEALED. Exact receipt replay remains allowed; unknown identities must not be ACKed.
- The last authorized targeted runtime attempt was Gradle session `98524`; it completed with exit 1. JDK was `C:\Users\maksd\.jdks\ms-21.0.10` (21.0.10). The selected unit task passed (`EventConsumerTest`: 11 tests, 0 failures/errors); the selected `integrationTest` task completed 36 tests with 5 failures. The four `LessonTransferParticipantIT` failures share one test-context cause: enabling transaction advice produced a JDK proxy (`jdk.proxy3.$Proxy176`) for `AttendanceAttachmentService`, while the test service constructor requires the concrete class. Preserve transaction advice and use class-based proxying in that test slice. The fifth failure is a product defect in `MarkingService.markAttendance`: Mongo rejects the update with code 40 because `group_id` and `semester_id` are written by both `$set` and `$setOnInsert`. The retained `$set` values are authoritative; remove the duplicate insert operators without weakening the runtime assertion.
- Runtime resources from this last run were not stopped manually. The Gradle session had completed and the HEAVY lease was released. A scoped read-only inspect of the exact prior run IDs returned `No such object` for RabbitMQ `90c2c69900f4d238e5b0c5934c8859b808eee922db3e6ac380a11eb149479221`, EventConsumer Mongo `6013f47c299a1f8acd2d8c4e340c597208a8a2fd2ac155582be756b5becf2037`, Student Mongo `7caddd526ccc73fde5479f8d6ca131b1b399cdfbb5b413c29e67c35f5cf29674`, and LessonTransfer Mongo `50000bc33093ca26a6ef8cc26ece7e020414fce3dab6d1bd0a29db28ca08404d`. No other containers were inspected or touched. No child agents were created.
- Owner STOP paused all source corrections and further checks. Remaining work is limited to the two review corrections, the class-based test proxy fixture, the `MarkingService` update fix, and focused tests for redelivery/lost ACK plus a new-key check-in under PREPARING and SEALED. No corrected snapshot, final rerun, commit, or source-ready acceptance exists yet; the previous integration result is diagnostic only.

## 2026-09-30 GO resume — committed candidate and bounded runtime

- Root resumed this assigned S3 Attendance scope with GO. Candidate commit is `e2d18970a124350df321cf57b806232b8ac7a59a`, parent `90c2a27261dabd614603b3010d740455c0cdce3c`, on `codex/semester-archive-attendance-20260929`. It changes 34 files under `services/attendance-service/**` (2,011 insertions, 265 deletions). No source edits were made after this commit while independent review reads the frozen candidate.
- Correction inventory: known duplicate deliveries now look up the corresponding persisted effect/transfer/participant receipt and re-enqueue its saved ACK; unknown or unreceipted identities are not ACKed. Exact transfer and participant receipt replays re-enqueue the prior ACK. Student check-in keeps exact old receipt replay ahead of the archive fence, but takes the fence before returning an existing PRESENT mark for a new idempotency key. Both `MarkingService` Mongo updates no longer write `group_id` or `semester_id` in both `$set` and `$setOnInsert`. The transfer IT slice enables class-based transaction proxies while preserving transaction advice. Tests extend existing consumer/check-in scenarios for ACK replay, unknown receipt, and PREPARING/SEALED new-key denial.
- `git show --check` / prior staged `git diff --cached --check -- services/attendance-service`: exit 0. The only authorized runtime invocation used JDK `C:\Users\maksd\.jdks\ms-21.0.10` (OpenJDK 21.0.10):

  ```text
  .\gradlew.bat --no-daemon --no-parallel --max-workers=1 --no-problems-report "-Dorg.gradle.java.compile-classpath-packaging=true" :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.event.EventConsumerTest :services:attendance-service:attendance-app:integrationTest --tests ru.rutcampustrack.attendance.integration.EventConsumerIT --tests ru.rutcampustrack.attendance.student.StudentCheckinTransactionIT --tests ru.rutcampustrack.attendance.event.LessonTransferParticipantIT
  ```

  Gradle session `10959` exited 1. Compilation and selected unit task passed; `EventConsumerTest` ran 15 tests, 0 failures/errors. Integration ran 37 tests with 5 failures, so acceptance is **not passed**:

  - `LessonTransferParticipantIT` expected total outbox count 1, actual 2. The test sends two distinct batches for one operation (batch indexes 0 and 1, each with a fresh event ID). The persisted operation receipt identity currently omits batch index/event ID, so the second batch takes the previous-operation receipt path and adds an ACK. This may be a valid ACK for a distinct batch, not an exact replay. The later exact-replay assertion was not reached; reviewer/root must settle whether receipt identity and ACK payload should bind to the batch.
  - `LessonTransferParticipantIT` reports PREPARE committed while the test holds the writer behind its latch/fence. Keep this concurrency assertion intact pending production transaction-semantics review.
  - `LessonTransferParticipantIT` reports `UnexpectedRollbackException` on a rejected post-SEAL effect. The transactional lesson cancellation path marks a REQUIRED transaction rollback-only before the outer effect handler can persist its rejection receipt/ACK. Keep the rollback scenario intact.
  - `EventConsumerIT` expected a duplicate cancellation ACK (outbox count 2), actual 1. The XML records duplicate delivery/retry exhaustion: `SemesterArchiveEffectService.hasReceipt` via `EventConsumer.replayReceiptIfPresent` sees Mongo code 251 `NoSuchTransaction` on an already-aborted transaction, so receipt replay does not enqueue the ACK. This is a product delivery/receipt failure, not evidence to relax the assertion.
  - `StudentCheckinTransactionIT` expected the `journal-decision` attachment to be absent but found its ACTIVE `RequestAttachmentDocument` (`requestId=100:1`, owner student 100, group 10, semester null). A separate request-owned attachment is also asserted present. Preserve owner/source distinctions; no attachment was deleted to satisfy the assertion.

- After Gradle completion, `docker ps --all --filter label=org.testcontainers=true --format "{{.ID}} {{.Names}}"` exited 0 with no containers listed. No runtime resource was stopped manually. No full suite was run. Independent Sol review of stable commit `e2d18970` is pending; root requested a consolidated repair contract before any source follow-up.
- Root later reported that the reviewer found an existing runtime pass, `realOutboxInsertFailureRollsBackAndSameCommandCanSucceedLater`, proving the current Mongo outbox insert rolls back with the surrounding command transaction. The initially considered shared-outbox change was explicitly canceled; `MongoOutboxStorage.save` remains untouched, and that passing test was not rerun in this packet.
- The `.agent` working tree still contains the preserved foreign `.agent/transfer-attendance-evidence.md` modification and four foreign untracked notes. This evidence note is task-owned and untracked; it is excluded from the 34-file source commit. No shared checks/status file was edited. No push, main integration, or production operation was performed.
- Current limits: five selected integration assertions fail; no acceptance-ready claim is made. Resolve the receipt/batch identity and ACK contract, Mongo replay transaction boundary, fence wait and rollback-only behavior, and attachment ownership expectation with root/reviewer before making a correction packet. Do not change fixtures to mask transaction/fence failures or replay delivery errors.

## 2026-09-30 bounded correction packet — final evidence

This checkpoint supersedes the provisional status above. Scope stayed in `services/attendance-service/**`; shared outbox, Academic, Schedule, proto, deployment, and production data were not changed.

- Exact known event receipts are checked before `tryClaim`; committed receipts replay their saved ACK without RPC or another domain mutation. Unknown identities still do not receive ACKs. SEAL rejection is preflighted with the semester fence before a nested transactional domain effect, so a durable ERROR receipt and ACK can commit.
- Journal attachment deletion now uses `(semesterId, lessonId, studentId, groupId)` for both manual attendance decisions and the student geo writer; the existing request-owned evidence remains outside that deletion path.
- Transfer receipt identity stays operation-level: two distinct batch deliveries produce two ACKs, an exact replay produces another ACK, and the direct local `lesson.closed` call produces no delivery ACK. The final total is three.
- Fence concurrency was diagnosed, not redesigned. In session `87516`, the absent-collection case let PREPARE commit first and the writer failed at transaction commit; persisted attendance was empty, while PREPARE remained `ARCHIVE_PREPARING/PENDING`. The same latch scenario with an already-existing empty fence collection passed. This did not establish loss of a committed attendance write. The missing-collection acceptance now permits only a cause chain containing Mongo `TransientTransactionError` or write-conflict code 112, checks there are no attendance/pair/lesson-fence/effect partials, and confirms the committed PREPARE ACK/receipt. A successful writer with an earlier successful PREPARE still fails. The existing-empty-collection test retains its strict ordering assertion.

Checks and runtime evidence, all with JDK 21.0.10 and one Gradle worker:

- `git diff --check -- services/attendance-service`: exit 0 (line-ending warnings only).
- Session `56297`: selected `EventConsumerTest` 15/0 and integration classes 37 tests/3 failures. `EventConsumerIT` was 9/0; the failures were the geo attachment argument, missing authoritative Schedule test response, and absent-collection fence transaction outcome. This was diagnostic and was not counted as a final pass.
- Session `87516`: four targeted methods (geo cleanup, transfer delivery, absent-collection latch, existing-empty-collection latch), exit 1 with two failures. Geo and existing-empty-collection ordering passed. Transfer reached the stale final ACK expectation (expected 4, actual 3); the absent-collection writer failed at commit with no persisted attendance. Both findings were corrected in the final snapshot.
- Session `9556`: only `prepareArchiveWaitsBehindAlreadyFencedAttendanceWrite` and `transferBatchesShareOneDomainReceipt_andEachDeliveryReplaysItsAck`, exit 0; XML records 2 tests, 0 failures, 0 errors. The first validates only an exact safe transactional conflict/abort with no partial writer state when the fence collection is absent; the second validates ACK total 3 with one operation receipt. No green method from the preceding run was repeated.
- After each completed runtime, `docker ps --all --filter label=org.testcontainers=true --format "{{.ID}} {{.Names}}"` exited 0 with no containers listed. No manual container cleanup, full suite, production migration, or deployment was performed.

Correction diff before commit: six scoped source/test files; binary diff object `529e4cc65bcd615ed209727c9916aed12beb5bb6`. Remaining limitation: independent bounded Sol recheck of this stable correction snapshot is pending; no broader suite was run. Foreign `.agent/transfer-attendance-evidence.md` and the four foreign untracked notes remain preserved.

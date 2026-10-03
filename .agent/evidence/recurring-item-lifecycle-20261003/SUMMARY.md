# Recurring item lifecycle — scoped verification PASS, ready for root integration

## Goal
JS-HEADMAN-08/22, JS-SYSTEM-05, screen117: headman updates room/parity or removes a repeating slot. Future stored calendar changes; past/started/manual exceptions and historical homework remain. Same slot re-add continues its series without filling the absence.

## Context/evidence
Base3e0b7efd3d64132dcc6792fc8d59760fb584fce1. Assigned developer gpt-6.1-sol/high; runtime metadata not visible. RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. Source117 sections4.5/4.6. Root approved V21 transfer reuse, prepared semester authority, V27 narrow template restore authority.

## Relevant scope
Sole writer in R3, codex/recurring-item-lifecycle-1003. Inventory below. No shared runtime/config/generated consumers touched.

## Required behavior
POST lifecycle-preview returns revision and actual updated/removed/restored/created counts. PUT requires Idempotency-Key and expectedRevision from preview. DELETE requires Idempotency-Key and If-Match preview revision. Subject/day/number/time are immutable; group/semester/assignment provenance remains guarded. Actor is exact headman or existing ADMIN override. Room uses V21 same-date/same-number generation and participant batches; response transfers exposes PENDING/status IDs. Parity/delete cancels only eligible future planned rows, publishes existing lesson.cancelled and homework.binding.archived events. Template-cancelled future rows resume with NEW physical IDs and exact authority; old cancellations/ARCHIVED bindings remain. Stable response replay does not reread live mutable data.

## Constraints
S3 history/authz/atomicity/concurrency. No remote RPC in these new local business transactions; prepare before writer and local participant recheck under semaphore. Old callers retain existing behavior. All child heavy invocations terminal and lease released; product frozen at f5ded4cc. No push/deploy/main integration by child.

## Existing patterns
V21 LessonTransferWriter reused with exact same occurrence/date/number; existing authority/triggers/batch hashes/retry/participant receipts/outbox retained. V27 adds replay/template markers and a narrow cancelled-template restore authority, preserving V26 ONE_OFF/manual function bodies. Events remain existing v1 schemas/routing; no new consumer/proto/protocol.

## Acceptance criteria
Past/started unchanged; manual transfer/cancel unchanged. Future room new generation and durable homework pointer batches. Future removal archives bindings in same local transaction. Re-add same stable tuple (room and teacher are not identity) uses replacement-linked series/effective creation caps and excludes elapsed absence. Conflicts are atomic; stale preview and concurrent keys reject stale revision. Selected DB verification and independent rechecks PASS within the scope below. Browser/backend stand and real Homework/Attendance consumer completion remain outside this child verification.

## Verification
Final product revision f5ded4cc; independent reviews/rechecks PASS per root. integration-04 PASS: exit 0, elapsed 54.2378522 seconds, Java 21.0.10, Gradle no-daemon/no-parallel/max-workers=1, require_escalated only own ignored build artifacts after observed default sandbox JAR read denial. Fresh disposable PostgreSQL 16.13 (port 60890), Flyway V27 migration PASS. Exact eight affected methods: RecurringScheduleItemIT 6, ScheduleItemApiIT 2; failures/errors/skipped all zero. Source/tests compiled successfully in integration-03 and remained UP-TO-DATE in integration-04. No standalone duplicate compile or full suite.

Final command: .\gradlew.bat --no-daemon --no-parallel --max-workers=1 --system-prop=org.gradle.java.compile-classpath-packaging=true :services:schedule-service:schedule-app:integrationTest with eight exact --tests filters:
- ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT.deletingAndReturningSameSeriesNeverBackfillsAbsenceAndRoomIsNotIdentity
- ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT.capNarrowingCommitIsFreshBeforeBlockedPhysicalInsert
- ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT.parityRemovalArchivesFutureHomeworkAndRestoresAsNewPhysicalGeneration
- ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT.templateReturnAfterCommittedTeacherReplacementUsesExistingCloneAndEffectiveTeacher
- ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT.concurrentDifferentCreateKeysCannotEditAlreadyResumedSeries
- ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT.templateRoomUpdateAdmitsTodaysNotStartedSlotUsingExactAuthorityTime
- ru.rutcampustrack.schedule.integration.ScheduleItemApiIT.updateTemplate_requiresReplayAndPreviewContract
- ru.rutcampustrack.schedule.integration.ScheduleItemApiIT.deleteTemplate_requiresReplayAndPreviewContract

28 unchanged methods passed in the earlier 35-method integration-02 run at e748f3ea; root explicitly selected the eight affected methods for final verification after corrections (today room method overlaps the earlier passing set). This is scoped evidence across revisions, NOT a final full-suite PASS. Covers stored future/history, room transfer batches and deterministic replay, parity cancellation/archive/new-ID restore, no gap backfill, committed teacher replacement, manual exceptions, authority/stale-preview denial, rollback and concurrency. Real external participant completion was not tested: durable transfer status stays PENDING until the existing participant protocol accepts; integration acknowledges local exact receipts only.

Integration series from baseline3e0b7efd: b350624c → e8ac84ce → e748f3ea → 93cda2e2 → 18a60ec2 → f5ded4cc. Root sole integrator; this evidence-only checkpoint follows product freeze. Heaviest artifact impact is Schedule app: new writer/changed lifecycle entrypoints and V27 require a fresh Schedule JAR and migration at the authorized test stand. No shared proto/events/consumer/frontends/config/lockfiles changed.

The entries below retain the failure/correction history; their pending statuses describe those checkpoints, not the final scope above.

Source git diff --check PASS on original source commits b350624c/e8ac84ce. Compile PASS on e8ac84ce: .\gradlew.bat --system-prop=org.gradle.java.compile-classpath-packaging=true :services:schedule-service:schedule-app:compileTestJava, exit 0, 51 seconds, compile-test-java-01.log. Correction bundle below has not been recompiled. DB integration NOT EXECUTED; wait root recheck/HEAVY lease. Selected command: .\gradlew.bat --system-prop=org.gradle.java.compile-classpath-packaging=true :services:schedule-service:schedule-app:integrationTest --tests ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT --tests ru.rutcampustrack.schedule.integration.ScheduleItemApiIT. DB assertions cover room/history/bindings/PENDING+stable replay, parity+newID restore, no-backfill reactivation, stale/denied authority, collision rollback, manual exceptions and concurrent revisions. No full suite.

Independent review reported two P2 defects: V21 SQL future-date guard rejected today's not-started same-slot template room update; concurrent new POST keys could reactivate an already resumed series without preview. Corrections: template-only same-slot/start-time authority branch preserves the public manual future-date restriction; after-lock active-series rejection occurs after deterministic same-key replay. Added DB regressions for today's future slot and concurrent restore keys. Existing manual-transfer fixtures now use actual future dates consistent with the unchanged database guard. ScheduleItemApiIT prepares explicit valid semester archive authority. Affected recheck and DB verification remain pending.

Affected review e748f3ea PASS (both P2 closed). integration-01 exit 1, 46.14 seconds, before DB: javac AccessDeniedException for own shared-security JAR under default sandbox; File.Open/read and Java21 jar inventory PASS. Root allowed exact same selected command with require_escalated. integration-02 on e748f3ea: compile PASS, DB PostgreSQL/Flyway V27 executed, 35 tests / 7 failures, exit 1, 84.12 seconds. There are two product causes and one old fixture cause: four restore/return failures share omitted V20 lifecycle-entry guard branch; two missing-header requests incorrectly map to shared catch-all 500; original cap concurrency fixture updates legacy cap without current creation cap and violates V22 interval constraint. Correction: exact template authority/current-pointer/source/target/actor/time checks in V20 history guard with original legacy body retained; deferred commit also binds accepted operation actor/time and committed replacement-linked item. Added forged-setting rejection inside parity regression. Missing header maps locally in ScheduleItemController to standard 400 Problem Details, contract remains required. Fixture narrows both caps. This correction awaits review/affected checks; no full-DB PASS claimed. Logs integration-01/02 and outcomes preserved locally; no clean/cache/process stop performed.

Affected review of 93cda2e2 found the paired direct fixture cap update still violates V19 exact prepared replacement authority. Corrected the same concurrency fixture through canonical AssignmentReplacementService.install with exact prepared Academic authority; current creation cap narrows to effective date while retained-row cap stays unchanged. No production trigger or service changed. The blocked physical insert still races the committed cap under its retained source fence lock. Review/8 affected methods pending; remaining 28 passing methods are not rerun without impact. Testcontainers XML confirms reuse disabled in this environment and Ryuk owns disposal; no migration clean/repair is needed.

93cda2e2 SQL/header review and 18a60ec2 fixture recheck PASS. integration-03 on 18a60ec2: compile PASS, exit 1, 48.39 seconds, eight context failures before assertions from one V27 SQL syntax error (extra closing parenthesis in accepted operation series membership). Exact one-character correction removes the extra parenthesis; all authority conditions remain unchanged. Fresh affected review/selected eight-method verification pending. Heavy released after every terminal invocation.

## Do not
No UI/proto/other services/config/build files/generated artifacts. No second saga or generic snapshot mutation. No reopening archived homework. Root integrates and independently reviews.

## Inventory
Created:
- services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/ScheduleItemLifecyclePreviewResponse.java
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java
- services/schedule-service/schedule-app/src/main/resources/db/migration/V27__recurring_item_lifecycle.sql
- .agent/evidence/recurring-item-lifecycle-20261003/SUMMARY.md
- .agent/evidence/recurring-item-lifecycle-20261003/INTEGRATION.tsv
- .agent/evidence/recurring-item-lifecycle-20261003/integration-01.outcome.txt
- .agent/evidence/recurring-item-lifecycle-20261003/integration-02.outcome.txt
- .agent/evidence/recurring-item-lifecycle-20261003/integration-03.outcome.txt
- .agent/evidence/recurring-item-lifecycle-20261003/integration-04.outcome.txt

Changed:
- services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/ScheduleItemApi.java
- services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/ScheduleItemResponse.java
- services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/item/UpdateScheduleItemRequest.java
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleSemesterArchiveWriteFence.java
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemAssembler.java
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemController.java
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonTransferWriter.java
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemCoordinator.java
- services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemWriter.java
- services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java
- services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiIT.java

Deleted: none. Unrelated untracked evidence preserved.

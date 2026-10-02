# Schedule homework DATE/manual placement — component accepted, integration pending

## Goal / observable result
Schedule supports homework on a selected date without a fake lesson and safe exact manual placement changes. Binding identity is stable; transfer waits for Academic finalization/ACK; DATE reaches terminal archive at next-day00:00 Europe/Moscow.

## Context / scope
RiskS3, sole writer /root/v_homework_schedule_1002, reused assigned admin-group-promotion-20260927 worktree.
Canonical RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA; root contract is map-usage-delivery-20260922/.agent/evidence/homework-lifecycle-20261002/CONTRACT.md plus exact NOT_ACCEPTED tombstone refinement.
Original author WIP seed7ee3db1f426a42719078d9a449d007bf8390628e preserved; this author completed it without rewrite.
Source chain:5e6482a5 →9f75add0 →736d1675(fixture-only) →2e805649(final source).
Own12 Schedule files listed in inventory.txt: created HomeworkDateDeadlineJob.java, modified other11, deletednone. PlacementService/V24 were originally created by handed-off author, extended here.
Academic/V45/proto author remains original owner; root integrates main. Proto SHA1945B5F8AEDDDB20CC2771E12C3B7678245474C7CFAF6E98D657ADA6D3B10A3C unchanged.
Foreign transfer/evidence/cache/instructions preserved. No children/Terra/protectedconfig/deploy/push/main changes.

## Required behavior / criteria → evidence
- DATE reserve/confirm has occurrence0 and no currentLesson; immutable create intent survives manual move/replay. R1 datePlacementReceiptGatesTransferAndKeepsCreateIntentAcrossManualMove PASS.
- Exact durable result+gate; transfer refuses before accepting operation, wrong ACK tuple denied, exact ACK permits transfer; stored receipt stays exact. Same R1 scenario PASS.
- Missing Continue cannot cancel; Abort NOT_ACCEPTED CAS fences delayed Move; existing applied result wins; hash mismatch, receipt mutation and gate clear rejected. R1 abortTombstoneFencesDelayedMoveAndAppliedResultWinsAbort PASS.
- Semester PREPARE sees DATE pending publication and admitted edit, exact continuation/ACK drains; canonical digest normalizes only pending gate/ACK bookkeeping. R1 datePendingPublicationAndAdmittedEditDrainArchiveWithoutOccurrence PASS.
- DATE inclusive cutoff at next Moscow midnight; terminal retry denied; retained publication identity. R1 dateCutoffIsNextMidnightInMoscowAndTerminalReplayIsDenied PASS.
- DATE deletion inventory differs from empty digest, exact commit/replay retains immutable binding but clears homework reference. R2 dateBindingDeletionIncludesInventoryAndRetainsImmutablePublicationIdentity PASS.
- Closed/elapsed lesson source and target refused without receipt/revision/placement; future target permitted, existing end+5min closure policy. R1 closedTargetAndElapsedLinkedSourceRejectWithoutPlacementReceiptOrRevision PASS.
- Blocked256 rows do not starve writable1, blocked rows preserved. R1 blockedExpiredBatchDoesNotStarveWritableSemester PASS.
- Reversed origin/occurrence IDs: concurrent reader+Move use common origins→physical→binding locks and both complete. Actual PostgreSQL writer lock wait observed before reader release. R2 batchReadAndMoveWithReversedOriginIdsCompleteUnderConcurrentLocks PASS.
- Fixed directed Academic service credential/TLS required for added RPCs; initial Get/Move also signed current user. R1 AssignmentCloseServiceIdentityInterceptorTest8/8 PASS.

## Constraints / existing patterns
Stable binding_id/actor/createkey/hash/originalintent, retention/terminal DB guards, existing BEFORE_COMMIT outbox/event, semester/origin/physical/binding lock graph preserved. No timeout releases applied gate. Exact service recovery carries no saved user JWT. Digest retains business state/revision/homework/placement; business drain changes intentionally stale preview.

## Checks / exit codes / immutable logs
R1 frozen736d1675, session2972: combined Gradle exit1,1m27s; compileJava/compileTestJava complete; unit8/8 PASS, DB12:9 PASS/3 FAIL. gradle-r1.log, r1/raw XML, r1/exec-start.json preserved.
Confirmed productfailure: DATE COMMIT_DELETE SQL55000, V24 lacked reverse NEW=OLD null-safe substitutions in deletion-neutralize branch. Two fixturefailures: initialauthority stub missing and duplicate active natural slot. Correction2e805649 keeps all retention/deletion context guards and changes only null-safe comparisons/fixtures.
R2 frozen2e805649, session15995: Gradle exit0,1m2s; ONLY3 previously failed DB methods3/3 PASS. gradle-r2.log, r2/raw XML, r2/exec-start.json. Accepted9 DB and8 unit checks not repeated. Necessary compilation incremental, no fullsuite.
Independent review initially FAIL5e6482a5 (terminalLESSON, starvation, batchdeadlock); correction9f75add0 +fixture736d1675 affected recheckPASS. Productnullableguard2e805649 affected recheckPASS per root. Final scoped diff check/source status/proto hash exit0.
Git initial sandbox index write denied exit1; scoped auto-review-approved Git add/commit exit0. Read diagnostics/warnings were not treated as product defects. Compiler unchecked/JVM sharing warnings caused no code changes.

Exact R1 command:
`./gradlew.bat :services:schedule-service:schedule-app:compileJava :services:schedule-service:schedule-app:compileTestJava :services:schedule-service:schedule-app:test --tests ru.rutcampustrack.schedule.grpc.AssignmentCloseServiceIdentityInterceptorTest :services:schedule-service:schedule-app:integrationTest --tests ru.rutcampustrack.schedule.grpc.HomeworkBindingServiceIT --continue --max-workers=1 --no-daemon --no-parallel --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true`
Exact R2 command:
`./gradlew.bat :services:schedule-service:schedule-app:compileJava :services:schedule-service:schedule-app:compileTestJava :services:schedule-service:schedule-app:integrationTest --tests ru.rutcampustrack.schedule.grpc.HomeworkBindingServiceIT.dateBindingDeletionIncludesInventoryAndRetainsImmutablePublicationIdentity --tests ru.rutcampustrack.schedule.grpc.HomeworkBindingServiceIT.archiveCancellationCommitsBindingLedgerAndOutbox_andReplayReturnsOriginalEvent --tests ru.rutcampustrack.schedule.grpc.HomeworkBindingServiceIT.batchReadAndMoveWithReversedOriginIdsCompleteUnderConcurrentLocks --max-workers=1 --no-daemon --no-parallel --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true`
JDK C:/Users/maksd/.jdks/ms-21.0.10; TESTCONTAINERS_REUSE_ENABLE=false; one root heavy lease.

## Runtime evidence / handles / cleanup
Real fresh isolated PostgreSQL16+Spring service transactions, migrations applied. Directed auth test uses in-process transport mocks/TLS attributes; DB scenarios use signed-context fixture and mocked remote Academic authority/Rabbit, not full cross-service HTTP acceptance.
R1terminal2972 exit1; R2terminal15995 exit0. Tomcat/Hikari/JPA shutdown logs retained. Elevated docker ps after each terminal exit0/empty, Testcontainers/Ryuk removed. Initial sandbox docker-read exit1 permission, corrected exact read without ACL changes. No own running resources; heavy lease released to root afterR2.

## Diff / limitations / do not
SOURCE-DIFF.patch is full owned delta seed7ee3db1f→2e805649; inventory.txt exact12files. checks.json connects revision/command/exit/XML/cleanup.
Component source and sufficient checks accepted. Root integration and whole Academic↔Schedule package/full user-path acceptance remain; no fullbackend/product DONE claimed. Frontend/Figma/manualclient/externalproviders/deploy/push excluded. Do not clear gate by timeout or relax preview digest to force deletion.

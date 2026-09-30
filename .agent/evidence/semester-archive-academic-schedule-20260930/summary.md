# Semester archive/restore — scoped GO S3 package

## Goal, scope, and status

Complete the authorized Academic + Schedule portion of the administrator semester archive/restore operation. Archive keeps accepted work recoverable while the operation is pending, then fences ordinary domain writes after participant proof. Restore releases fences only for the exact restore epoch and leaves the semester inactive. This leaf owns Academic and Schedule archive behavior, archive proto/security wiring, and migrations V40 Academic / V22 Schedule.

Excluded from this leaf: Attendance implementation, Auth/recovery, Academic V41, the historical LessonRepository query already corrected on main, and shared Mongo outbox storage/tests. The foreign `.agent/evidence/semester-activation-ac39/` remains untouched.

The three root-approved orphan repairs are implemented in the owned source diff. Source is stable for independent review. Compilation, PostgreSQL integration checks, and runtime evidence are still pending; this is not a production-readiness claim.

## Acceptance criteria for this scoped delta

1. A durable Academic materialized PENDING publication is retried in bounded scheduled batches even when Schedule is ACTIVE or READY. Academic activates only after exact Schedule confirmation/replay.
2. A no-content Schedule reservation can be cancelled only after an exact Academic semester-then-binding fence proves absence. The operation/version/binding/actor/request-key/hash and Schedule occurrence/revision are checked. A durable tombstone blocks late persistence, survives restore, and is not recaptured by a later archive. Exact cancellation replay retains the original event identity.
3. A completed empty transfer marker is cancelled only with matching APPLIED transfer receipt and history. Transfer source event and cancellation terminal event remain separate. Schedule commits the binding change, effect ledger, and outbox in one transaction; exact replay returns the original cancellation event ID.
4. Schedule-only protected RPC carries confirm/cancel; there is no archive cancellation on the Attendance command stream. RPCs run outside local DB transactions/locks. Retries remain bounded; no timeout success or synthetic ACK is introduced.

## Implementation evidence

- `AcademicSemesterArchiveBarrierTransaction` performs fenced resolution, durable tombstone transitions, exact transfer proof, bounded pending-publication/cancellation reads, and archive recapture exclusion. It verifies actor/request key on all admission replays and occurrence/revision/event identity for terminal replay.
- `SemesterArchiveCoordinator` retries live operations without a client request, reconciles exact pending bindings, redrives materialized PENDING publications after Schedule activation, records cancellation event identity, then seals Academic only after Schedule READY.
- `ScheduleSemesterArchiveBarrierTransaction` validates exact reservation identity, confirms materialized content, cancels unpublished content atomically with existing `BEFORE_COMMIT` effect-ledger/outbox handling, and returns the original terminal event on replay.
- Academic V40 stores immutable admission resolution, separate source/terminal event IDs, and transfer receipt/history proof. Schedule V22 cancellation trigger declares and uses the authority version. Protected method maps `SetSemesterArchiveBarrier` to the Academic service principal.
- Static scenario trace: crash after Schedule ACTIVE/READY leaves Academic PENDING queryable for scheduled redrive; cancellation commits `CANCEL_REQUESTED` before RPC and later settles only on exact effect receipt; terminal archive marker excludes that binding from a later capture after restore; completed empty transfer requires its exact batch receipt/history and cancellation preserves transfer provenance.

## Worktree and revision

- Worktree: `.agent/worktrees/v2-l5b-retrospective-union`; branch `codex/semester-archive-complete-20260929`.
- Base HEAD: `3312f33e84668eb8d70b1c164a3e914ea9b7f51b`.
- The scoped package is committed locally; its final commit ID is reported in the handoff message.
- Canonical root RULES SHA from the task packet: `397C1B0984648CA2EE7066DEFCB5DEE797DCC546B3C760443012F817ECFAD976`.
- The root-approved delivery contract is `.agent/orchestration-v2/evidence/2026-09-27-delivery/semester-archive-complete-contract.md`; the bounded repair is the 2026-09-30 abandoned-publication recovery amendment plus the approved three-finding review result.

## Checks and runtime

- `git diff --check` — exit 0. Git emitted only existing LF-to-CRLF notices for three archive WIP files.
- Narrow symbol/enum/call-site scan for `reconcileArchiveHomeworkBinding`, `BindingResolutionKind`, and its four handled cases — exit 0; one declaration, four coordinator calls, all enum cases handled.
- Narrow migration scan for `authority_version`, `source_event_id`, `batch_index`, `result = 'APPLIED'`, and admission `resolution_state` — exit 0; the Schedule variable is declared/read, and Academic schema/code contain the referenced identity/proof fields.
- Protected Schedule RPC scan — exit 0; `SetSemesterArchiveBarrier` is an exact service-identity method and allows `ACADEMIC_SERVICE`.
- Gradle compile/tests and PostgreSQL runtime — not run. HEAVY is currently assigned to the Attendance/recovery short block; no process, database, or container was started by this leaf. Fresh Sol source review has not yet returned. See `checks.json` for command/environment records.
- Focused runtime cases still required after review and HEAVY handoff: Schedule ACTIVE/READY before Academic activation; cancellation → restore → new archive plus late persist/exact replay; completed empty transfer → cancellation with distinct source/terminal event IDs. No full suite was run.

## Diff inventory

Tracked modified paths (44): `proto/academic.proto`, `proto/schedule.proto`; Academic: `HomeworkBindingArchiveMarker.java`, `Semester.java`, `HomeworkBindingArchivedEventConsumer.java`, `LessonTransferEventConsumer.java`, `RabbitConfig.java`, `AcademicGrpcServiceImpl.java`, `AssignmentCloseServiceIdentityInterceptor.java`, `ScheduleGrpcClient.java`, `HomeworkBindingArchiveCoordinator.java`, `HomeworkBindingTransferCoordinator.java`, `HomeworkNotificationJob.java`, `HomeworkPublicationPersistence.java`, `HomeworkService.java`, `HomeworkStudentService.java`, `LessonTransferBatch.java`, `SemesterAssembler.java`, `SemesterController.java`, `SemesterService.java`, `HomeworkStudentServiceTest.java`; Schedule: `DomainEventListener.java`, `EventConsumer.java`, `HomeworkBindingArchivedEvent.java`, `LessonCancelledEvent.java`, `LessonClosedEvent.java`, `LessonDeletedEvent.java`, `OneOffLessonCancelledEvent.java`, `AcademicGrpcClient.java`, `AssignmentCloseServiceIdentityInterceptor.java`, `ScheduleGrpcServiceImpl.java`, `HomeworkBindingService.java`, `LessonGenerationService.java`, `LessonReminderJob.java`, `LessonService.java`, `LessonStatusTransitionJob.java`, `LessonTransferWriter.java`, `RecurringLessonLifecycleWriter.java`, `OneOffLessonService.java`, `RecurringScheduleItemWriter.java`, `AssignmentReplacementService.java`, `SubjectDeletedCascadeService.java`, `RecurringLifecycleGateTest.java`; Shared: `ServiceIdentityServerInterceptor.java`.

New source/migration paths (15): Academic `SemesterArchiveOperation.java`, `SemesterArchiveEffectAcknowledgementEvent.java`, `SemesterArchiveParticipantAcknowledgementConsumer.java`, `SemesterArchiveOperationRepository.java`, `AcademicSemesterArchiveBarrierTransaction.java`, `SemesterArchiveCommandTransaction.java`, `SemesterArchiveCoordinator.java`, `SemesterArchiveOperationController.java`, `SemesterArchiveService.java`, `V40__semester_archive_operations.sql`; Schedule `SemesterArchiveEffectLedger.java`, `ScheduleSemesterArchiveBarrierService.java`, `ScheduleSemesterArchiveBarrierTransaction.java`, `ScheduleSemesterArchiveWriteFence.java`, `V22__semester_archive_barriers.sql`.

This evidence directory contains `summary.md` and `checks.json`. Do not stage or modify the separate four-file foreign evidence directory `.agent/evidence/semester-activation-ac39/`.

## Limitations

- No compile, integration runtime, or independent Sol source review has passed yet; source readiness is limited to static reconciliation and diff whitespace checks.
- Legacy unscoped Schedule effects and transfer markers without source-event proof remain fail-closed and may keep an operation pending. No production data migration is part of this task.
- Attendance, Auth/recovery, and the already corrected historical-page query are outside this leaf and are not covered by its runtime evidence.
- No push, main merge, production migration, or full-suite run.

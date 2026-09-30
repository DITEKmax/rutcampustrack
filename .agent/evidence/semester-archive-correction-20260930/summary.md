# Semester archive correction package

## Goal and scope

Complete the approved archive/restore correction set on top of `6835fe8243ccd3969d42009b31bd676723d586d3`. The user-visible behavior is ADMIN archive and restore that completes durably across Academic, Schedule, and archive protocol state; restore leaves the semester inactive. This leaf owned the Academic + Schedule archive implementation and wire changes. Attendance, Auth/recovery, Academic V41, the shared outbox, historical LessonRepository queries, production migration, push, and merge are excluded.

The frozen contract allows only the uncached, fail-closed Academic authority read before Schedule mutation/advisory locks. The legacy unscoped Schedule outbox remains a production migration limit. The foreign untracked `.agent/evidence/semester-activation-ac39/` was left untouched.

## Required behavior and corrections

1. Academic cancellation admission reads retain `actor_id` and `request_key`; the exact identity guard remains enforced. Schedule cancellation consumes all eight transaction-local settings through a multi-column result extractor. Binding, effect ledger, and outbox commit atomically; replay returns the original terminal Schedule event ID.
2. Materialized Academic PENDING content is redriven even after Schedule is READY. No-content cancellation is fenced by the Academic semester lock and exact negative proof; late content persistence is rejected, and the cancellation tombstone survives restore and prevents recapture on a later archive.
3. A completed empty transfer is terminally cancelled after its exact transfer receipt. Transfer and cancellation keep separate source/terminal identities and receipts. Each acknowledgement delivery gets a fresh envelope event ID while payload source ID, hash, and result remain stable; specialized effect receipts provide transactional idempotency, so no generic consumer claim was added.
4. V40 now scopes trigger `NEW` fields by source table and widens the transfer marker state to `VARCHAR(32)` for `CANCELLED_UNPUBLISHED`. The SQL guards and proof predicates were retained.
5. Compiler/test-fixture corrections were limited to the archive path: use the Academic `ResourceNotFoundException`, add the existing uncached semester lookup delegate, import the two referenced Schedule symbols, repair the duplicate/missing archive fence local in `RecurringLifecycleGateTest`, and include the V19-required `creation_cap_until_exclusive` in the target Schedule fixture with the same bound as `cap_until_exclusive`.

## Acceptance evidence

- Academic `HomeworkBindingArchiveIT.eventBeforeContentStoresArchivedHistoryAndMakesActivationMonotonic` passed against PostgreSQL (1/1 selected test).
- Academic `HomeworkBindingTransferIT.completedEmptyTransferCancellationPreservesTransferAndTerminalEventIdentities` passed against PostgreSQL (1/1 selected test).
- Schedule `HomeworkBindingServiceIT` passed against PostgreSQL (2/2 tests), including atomic cancellation/replay. Schedule `compileJava` and `compileTestJava` passed.
- Final Schedule run was terminal and its Testcontainers container is absent. No full suite was run.

## Checks and runtime

See `checks.json` for commands, exit codes, failed-attempt root causes, and the final bounded runtime result. The final targeted run used JDK 21, one Gradle worker, and disabled parallel execution. The earlier Schedule run exposed a test fixture missing the schema's required creation cap; the fixture was corrected from the existing V19 backfill rule and the same IT then passed.

## Diff inventory

- Academic: `HomeworkBindingArchivedEventConsumer.java`, `SemesterArchiveService.java`, `SemesterService.java`, `V40__semester_archive_operations.sql`, `HomeworkBindingArchiveIT.java`, `HomeworkBindingTransferIT.java`.
- Schedule: `EventConsumer.java`, `ScheduleSemesterArchiveBarrierService.java`, `HomeworkBindingServiceIT.java`, `RecurringLifecycleGateTest.java`.
- Evidence: this summary and `checks.json`.

## Limitations

Only the selected archive, transfer, and Schedule integration scenarios were run. No full suite, production migration, push, or main integration was performed. Independent Sol review of the final correction diff is pending at the root.

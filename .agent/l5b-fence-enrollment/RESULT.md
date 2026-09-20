# Fence enrollment — bounded source handoff

Baseline 13e5fd1985b798bbb61bcc85969e6a74b1fc6837, stable v2-runtime-build-r3. RULES B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. Scout fence_enrollment_scout_20sep RELEASED: twelve direct files, read-only, no runtime/writes. Lead opened critical V17, generation, recurring-create and one-off originals. Static mismatch is not a new runtime reproduction. No auth scope or writer allocated.

All paths below relative to services/schedule-service/schedule-app/src/main/ unless stated otherwise.

| Entrypoint / original | Existing path and provenance | Minimal participation / dependency |
|---|---|---|
| java/ru/rutcampustrack/schedule/item/ScheduleItemService.java:86–118; lesson/LessonGenerationService.java:126–138,224–235 | create saves group/subject/semester template then saveAll(buildLessons); physical builder sets only item/date/status/flags | Exact assignmentId input and Academic immutable tuple/interval retrieval before local locks; same transaction fence→origin→occurrence→physical with full snapshot. Existing template provides explicit start/end; no bells prerequisite |
| item/ScheduleItemService.java:147–208; lesson/LessonGenerationService.java:152–203,214–219 | update/deactivate and regeneration physically delete/reinsert retained rows | Must use canonical fence/lifecycle writer or explicit fail-closed guard under future root contract. Cannot call this excluded and claim all writers protected; do not disable V17 |
| lesson/IsoParityReconciler.java:65–131 | application-ready active-template reconciliation invokes same delete/reinsert; marker after success | Same generation admission/guard; no successful marker on skipped/failed enrollment. Calendar already present, no bell values |
| oneoff/OneOffLessonService.java:98–149,165–201 | stores only one-off origin, active semester auto-resolved, no canonical physical row or assignment/time provenance | Future physical creation must use same writer. Cannot infer start/end from lesson number. Bell directory/time source remains product-dependent; no new one-off activation in recurring slice |
| lesson/LessonService.java:91–155; lesson/LessonStatusTransitionJob.java:54–88 | in-place cancel/restore/status writes, group resolved through template; no new physical insertion today | Future common lock order and snapshot-based authority; restore/new generation remains separate lifecycle gate. Cancelled rows still block cap. Past restore final state unresolved |
| subject/SubjectDeletedCascadeService.java:58–84 | deletes one-offs/templates assuming lesson cascade | V17 RESTRICT/history protection retained. No cascade rewrite or history deletion as bootstrap workaround; explicit future admission needed |

Remaining opened direct originals: lesson/entity/Lesson.java, item/entity/ScheduleItem.java, oneoff/entity/OneOffLesson.java lack corresponding V17 mappings; grpc/AcademicGrpcClient.java:38–105 has group/semester/headman calls but no GetAssignmentsByIds wrapper. Twelfth original resources/db/migration/V17__student_occurrence_homework_binding.sql:7–22 rejects legacy nonempty schedule tables; :40–148 creates required occurrence/physical identities; :150–210 rejects physical DELETE/identity rewrite; remaining retained binding/replay constraints remain enabled.

## Concrete first closed dependency

Assignment-backed recurring template creation and canonical physical generation is a useful next domain dependency, with a Schedule-local fence/DB insertion guard. It is not full assignment closing. Public close remains typed409. Root must freeze exact DTO/entity/proto-client additions and the handling of every caller of generation before activation. A create-only implementation may be tested as a dependency but cannot be declared globally enrolled while updater/reconciler paths remain unguarded. Internal cap repository tests are not permission to expose InstallAssignmentCloseCap without persisted Academic PREPARED validation and service-auth.

On a clean DB: apply existing V17 normally before rows; retrieve exact Academic assignment via existing proto/academic.proto:55; verify tuple/effective end; ensure fence without widening; lock it; create canonical origin/occurrence/physical snapshots and current pointer atomically. Retry must not duplicate logical occurrence or physical generation; exact request/replay mechanism is a root implementation choice, not something the existing legacy generator already guarantees.

For existing canonical rows, bootstrap must verify all provenance and dates against exact identity/cap before installing a fence. Missing/ambiguous provenance is a refusal, never guessed backfill/reset. Future migration number/trigger name not assigned here. Database guard should serialize physical inserts with cap installation, and missing fence must fail closed. Installing a narrower cap checks ALL retained physical rows at/after D after obtaining the fence lock; cancelled/transferred/superseded rows are not filtered away.

## Observable checks for future freeze

Valid assignment and explicit recurring times produce one canonical occurrence/current physical row with exact immutable tuple and date within effective interval/cap. Missing/foreign/mismatched/incomplete/unavailable authority yields no committed origin/occurrence/physical writes. Insert at/after cap is rejected, including direct persistence bypass; smaller cap cannot be widened by stale Academic response. Creator-versus-cap interleavings prove either committed retained-reference refusal or denied late insertion. Regeneration/startup callers cannot delete history or bypass the guard. No test executed in this planning task.

## Product answers and scope limits

No unanswered bell/study-day choice blocks service-auth, date cap, ledger, retained-reference query or recurring writer using explicit existing start/end. One-off clock times and calendar authority remain unresolved for its activation. Past restore behavior remains outside recurring slice. Scout observed recurring ISO parity vs one-off semester-relative parity; this is a bounded source discrepancy, not authorization for a parity rewrite or another broad search.

Transfer target and Homework binding callers were not opened within twelve-file budget; existing accepted design maps them as future participants. Do not present this bounded list as an exhaustive repo audit. Root can freeze narrow concrete implementation from this map; no additional scout or full design review is required merely to restate accepted v3.

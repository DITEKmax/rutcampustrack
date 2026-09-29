# JS-HEADMAN21 lesson transfer — scoped package

## Goal and state

Implement the Schedule durable transfer operation and Academic bound-homework participant from the frozen S3 contract. This scoped package is implemented and its targeted PostgreSQL integration checks pass. Combined Schedule/Attendance/Academic API acceptance and independent Sol recheck remain with root, so this is not a claim that the whole cross-service feature is complete.

## User-visible behavior

Schedule keeps `revision` as the physical lesson revision and now exposes `occurrenceRevision` in lesson reads (`proto/schedule.proto` field 28). After A→B, reloading B reports `revision=1`, `occurrenceRevision=2`; a transfer using the reloaded occurrence revision succeeds for B→C. Schedule persists immutable participant batches and outbox events before moving bound rows, within the same SQL transaction, so the V21 guard verifies each update against its snapshot. Physical lesson weekdays use the persisted 1–7 convention. Academic retains the original publication source across A→B→C, while the latest target date/slot advances; active/pending homework state, content, identity and publication hash remain intact.

## Product inventory

- `proto/schedule.proto`: additive `LessonResponse.occurrence_revision = 28`; fields 26 and 27 remain in preceding proto commit `a6124b389a134a53f8d2bcd2dc573060c04f3d01`.
- Schedule API/runtime: `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/LessonResponse.java`; `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/EventConsumer.java`, `grpc/ScheduleGrpcServiceImpl.java`, `lesson/LessonController.java`, `lesson/LessonTransferService.java`, `lesson/LessonTransferWriter.java`.
- Schedule verification: `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonControllerTransferTest.java`, `lesson/LessonTransferWriterIT.java`, `migration/FlywayMigrationIT.java`.
- Academic correction and verification: `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/LessonTransferEventConsumer.java`, `event/LessonTransferParticipantAppliedEvent.java`, `homework/HomeworkBindingTransferCoordinator.java`, `homework/LessonTransferBatch.java`; `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkBindingTransferIT.java`, `migration/FlywayMigrationIT.java`.
- V21/V38 migration guards were retained; no migration source was changed in this correction package.
- The two foreign dirty files under `.agent/evidence/headman-trend-export-20260926/` remain unstaged and untouched by this package.

## Acceptance coverage

`LessonTransferWriterIT` exercises the real PostgreSQL request/replay path, two immutable binding batches (64/1), actual Schedule outbox payload, duplicate/reordered ACK receipt behavior, completion, HTTP schedule reload and a second B→C transfer using the returned occurrence revision. `HomeworkBindingTransferIT` exercises active and pending publication across chained transfers, materialization from the original A source at C, preserved publication state/content/hash and durable receipts/ACKs. Each service's `FlywayMigrationIT` validates fresh migration and checksum/data preservation at V21/V38.

Attendance mark and attachment preservation, the actual combined multi-service API flow, archived-semester coordinator gate, and independent Sol review are not covered by these two service-local checks and remain root-owned integration work.

## Commit

Corrected source commit SHA is included in the delivery message. Base at start of this correction: `d61ba641a52432b41b98de598f1aca5b8edbb732`.

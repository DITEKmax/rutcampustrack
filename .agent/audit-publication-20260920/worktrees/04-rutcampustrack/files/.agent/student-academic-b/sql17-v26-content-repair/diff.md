# Scoped diff

This is the leaf's bounded source diff summary. The product files are existing
dirty/untracked source recovered by the prior B0 work; no stage/commit was
performed, so this record describes the exact edits made in this turn.

## `V17__student_occurrence_homework_binding.sql`

- In `validate_lesson_physical_snapshot()`, added null-safe comparisons of
  `NEW.schedule_item_id` and `NEW.one_off_lesson_id` with the selected
  `lesson_occurrences` row.

## `StudentOccurrenceMigrationIT.java`

- Added `crossTemplateRecurringOriginIsRejectedEvenWhenSnapshotsMatch`.
- Added `crossOneOffOriginIsRejectedEvenWhenSnapshotsMatch`.
- Both create distinct origins with matching physical snapshots, assert the
  trigger raises a Spring `DataAccessException`, and verify no lesson row was
  inserted.

## `V26__campus_map.sql`

- Added immutable plan-version identity/publication guard with parent-row
  locking and published plan deletion protection.
- Extended the format guard to lock the parent row, retain draft transitions,
  reject published format insert/update/delete, and protect format identity.
- Removed the daily dedupe to open-intent foreign key so the two retention
  lifetimes can be cleaned independently.
- Added `FOR UPDATE` matching-intent validation before the dedupe aggregate
  increment.

## `StudentFoundationMigrationIT.java`

- Added focused ready asset metadata and asset immutability behavior coverage.
- Added draft transition, plan identity, publication freeze and deterministic
  lock serialization coverage.
- Added intent identity/conflicting payload, exactly-once dedupe/aggregate and
  independent retention boundary coverage with SQLSTATE assertions.
- Added only local fixture/assertion helpers needed by these cases.

## Evidence-only files

`packet.md`, `source-ready.md`, `manifest.json`, `evidence.md`, `checks.md` and
this file are confined to `.agent/student-academic-b/sql17-v26-content-repair/`.

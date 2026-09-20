# L5B recurring completion checkpoint

## Scope

Assigned recurring Schedule writer batch only. The existing 31-path WIP and
prior author release remain preserved. Academic, proto, and Attendance areas
are outside this batch.

## Criteria addressed

- Coordinator rejects an active group authority response whose id differs from
  the requested group, before any local effects.
- Endpoint, replay, fence, rollback, retention, stale/remote cap, slot
  conflict, and both physical-insert/cap-narrowing race orders have focused
  coverage in `RecurringScheduleItemIT`.
- A populated V17 row path is covered both by a post-migration fence bootstrap
  scenario and by `StudentOccurrenceMigrationIT` applying V18 after rows exist;
  cancelled/current physical references are retained without guessed snapshots.
- Lifecycle and legacy generation gates fail closed before mutation.

## Evidence and checks

- Worktree: `codex/l5b-recurring-writer-20260920`, base
  `3d4115f3a4c4ddba473689e27ac1a0efb519a202`.
- `git diff --check`: exit code 0. Git reported only existing LF/CRLF
  normalization warnings.
- Gradle, PostgreSQL integration runtime, and OpenAPI/type generation were not
  run in this leaf because the root lease is required; their exit codes are
  therefore pending root execution.
- Root-owned focused selectors are recorded in the handoff message:
  `RecurringDateCalculatorTest`, `RecurringScheduleItemCoordinatorTest`,
  `RecurringLifecycleGateTest`, `ScheduleItemSecurityTest`, plus
  `RecurringScheduleItemIT`, `LessonStatusTransitionJobIT`, and
  `StudentOccurrenceMigrationIT` under their designated tasks.

## Diff and limits

The source/test additions and existing recurring WIP are visible in the writer
worktree status. No reset, copy-over, commit, review, generation, deploy, or
push was performed. Final raw inventory/hash capture and runtime evidence are
left to the root freeze after compile and integration leases.

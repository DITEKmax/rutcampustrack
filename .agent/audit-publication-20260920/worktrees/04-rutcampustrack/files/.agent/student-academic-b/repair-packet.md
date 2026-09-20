# Bounded repair packet — numeric foundation

## Request and reference

Repair the fresh independent Sol review FAIL for the S3 numeric slice in
`review-1-result.md`. Root accepts a MEDIUM included-lifecycle mark defect and
a LOW stale-evidence defect. The source contract remains the frozen numeric
scope on baseline `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

## Reproduction

Before correction,
`AttendanceMetricCalculator.calculate(List.of(new Occurrence(1L,
OccurrenceState.ACTIVE, AttendanceStatus.CANCELLED)))` returned
`plannedCount=1, heldCount=0`. The same occurred for `PLANNED`. That silently
accepted a system-only cancellation mark on an included lifecycle.

The evidence defect was reproducible by counting the manifest and the five
available Gradle XML suites: the recorded `checks.json`/`summary.md` values did
not match the then-current 13-entry manifest and 19 passing test results.

## New evidence

The independent review identifies the exact production branch and cites
`MarkingService.java:62,94,201` plus `LessonEventService.java:138`. Existing
source behavior also shows that ACTIVE may retain P/A/E marks; no date-based
future inference is authorized. The unchanged review is preserved in
`review-1-result.md`.

## Correction

Apply this explicit matrix in `AttendanceMetricCalculator`:

- `PLANNED` and `ACTIVE` accept `null`, `PRESENT`, `ABSENT`, and `EXCUSED`;
  all remain outside `H`.
- `PLANNED` and `ACTIVE` reject `CANCELLED` with typed
  `INVALID_OCCURRENCE`.
- `CLOSED` keeps null/P/A/E handling and rejects CLOSED+CANCELLED as it did.
- `CANCELLED` and `TRANSFERRED_OUT` retain any non-FREE invalidated mark and
  remain excluded.
- `FREE_ATTENDANCE` always raises typed
  `UNSUPPORTED_ATTENDANCE_STATUS`.

Add observable focused tests for ACTIVE+CANCELLED and PLANNED+CANCELLED,
retain positive ACTIVE/PLANNED P/A/E tests, and keep excluded history marks
excluded. Refresh checks, five XML snapshots, log, summary, and manifest after
the leased three-selector Gradle run.

## Bounded scope

Production correction: only
`services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/AttendanceMetricCalculator.java`.
Matching test correction: only
`services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/studentprojection/AttendanceMetricCalculatorTest.java`.
Evidence-only updates stay under `.agent/student-academic-b/`.

No MarkingService, ReportService, API/DTO, proto, persistence, schema,
generated code, migration, or UI worktree changes. The authorized nested
`.agent/worktrees/student-academic-ui/` remains untouched and excluded from
the manifest.

## Root decision and verification gate

Root explicitly adopts the matrix above and rejects the review suggestion to
forbid PLANNED+PRESENT/ABSENT/EXCUSED. Root requires the same three Gradle
selectors after the correction, with `--no-parallel --max-workers=1`, followed
by five XML snapshots, repeatable checks commands, manifest verification, and
a fresh independent recheck. No Terra escalation is permitted or needed.

## Evidence replay repair addendum

Fresh Sol review found a LOW evidence defect in the recorded `domain-boundary`
command: its decoded PowerShell regex had two backslashes before dotted
package separators, so a forbidden dotted import could evade the check. The
reproduction was `[regex]::IsMatch('import org.springframework.stereotype.Service;', $pattern)`
after parsing `checks.json`, which returned false for the stale pattern.

The evidence-only correction changes the decoded pattern to one backslash per
literal dot: `attendance\.checkin|RequestContext|org\.springframework|javax\.persistence|jakarta\.persistence|grpc`.
The corrected command was executed against all three production files and five
positive samples (checkin, Spring, JPA, `RequestContext`, and gRPC); production
hits were 0 and all five probes matched, exit code 0. No production file, test,
or Gradle result changed. The same manifest validation and JSON round-trip
checks were rerun, and the refreshed SHA-256 manifest covers the corrected
evidence.

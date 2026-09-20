# Student projection numeric foundation — handoff summary

## Result

The bounded pure numeric foundation is implemented in
`attendance.report.studentprojection`:

- `AttendanceMetricCalculator` consumes explicit physical occurrence ids,
  rejects duplicate ids and `FREE_ATTENDANCE`, excludes cancelled and
  transfer-out rows, counts included lifecycle rows once, projects a missing
  `CLOSED` mark as read-only `ABSENT` with a diagnostic, and derives all four
  scale-2 `0..100` percentages from integer counts.
- `PLANNED` and `ACTIVE` accept null, `PRESENT`, `ABSENT`, and `EXCUSED`
  marks while remaining outside held metrics; `CANCELLED` marks on those
  included lifecycle states fail with typed `INVALID_OCCURRENCE`.
- `OwnRankCalculator` consumes the caller's active roster as supplied, retains
  participants with no records in `participantCount`, never injects the current
  user, compares exact fractions with `BigInteger`, and returns only nullable
  position, participant count, and availability.
- `StudentProjectionException` provides typed invalid occurrence, duplicate
  occurrence, unsupported status, invalid roster, and invalid metrics errors.

## Scope and ownership

The only numeric production files are the three files in
`attendance/report/studentprojection`; the only numeric code tests are the two
matching test files. Evidence ownership is `.agent/student-academic-b/`.
The separately owned union packet is under `.agent/student-academic-b/union/`
and is intentionally excluded from the numeric manifest. The nested
`.agent/worktrees/student-academic-ui/` worktree and its five relocated files
were preserved; no UI content was edited here.

## Evidence

- Frozen baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Latest focused command:
  `.\\gradlew.bat :services:attendance-service:attendance-app:test
  --tests '*AttendanceMetricCalculatorTest' --tests '*OwnRankCalculatorTest'
  --tests '*ReportDomainIsolationTest' --no-parallel --max-workers=1
  --console=plain`.
- The latest run passed 21 tests: metric 10, rank 8, report-domain isolation 1,
  and the two selected architecture suites 1 each. All five XML snapshots have
  zero failures, errors, and skipped tests.
- Stable XML copies and the successful 46-second Gradle log are under
  `.agent/student-academic-b/evidence/`; run metadata records UTC start/end and
  exit code 0. The first sandbox-only lock failure is recorded in `checks.json`
  and was followed by the exact command with approved escalation.
- `checks.json` records seven checks with exit codes. `manifest.sha256` records
  18 stable numeric files and excludes itself, `union/**`, and UI relocation
  evidence. `ui-relocation.json` separately records the guarded five-file
  relocation and hash verification.
- `repair-packet.md` and `review-1-result.md` preserve the bounded defect,
  correction, and independent review context.

## Runtime and limitations

Product/service runtime is `N/A` for this pure calculator scope: no network,
persistence, servlet, or external application behavior changed. The focused
unit/build run is the applicable behavioral evidence.

Schedule occurrence lineage, Academic roster authorization and terminal
membership, transfer lifecycle, persistence fallback writes, public DTOs,
proto/migrations, and `ReportService` projection wiring remain integration
responsibilities. The authoritative adapter must turn closed schedule
occurrences with missing marks into the calculator's read-only absent
projection before ranking. The numeric foundation does not claim full
statistics role/API or product runtime readiness.

## Handoff state

The numeric code diff is stable after the correction, the fresh focused run,
XML/log capture, checks refresh, and manifest refresh. No Terra escalation was
used or needed. The Gradle lease is complete and no active process remains.
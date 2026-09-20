# L5B historical attendance freeze evidence

## Scope

Academic managed group coverage, enrollment/transfer history, dated paired
GroupMembers RPC, Attendance canonical Schedule snapshot and dated roster
consumer, durable cancellation marker, and authoritative report filtering.
No Schedule source files were changed.

## Criteria and evidence

- New managed groups persist coverage from the active semester; student create
  and transfer persist coherent membership intervals atomically.
- Dated roster reads use one repeatable-read snapshot, validate coverage,
  orphan/overlap/current-assignment consistency, and echo exact date/semester.
- Direct membership mutations are typed gates before effects; historical output
  does not use current role/status/group as an eligibility filter.
- Close reads canonical Schedule identity/date/semester/status first, calls the
  paired dated Academic RPC, materializes only ABSENT/AUTO_SCHEDULER rows, and
  honors the durable cancellation marker before and after materialization.
- Reports validate authoritative Schedule lesson status and exclude
  CANCELLED/TRANSFERRED rows fail-closed on malformed or incomplete authority.

Focused unit/IT sources are present in `HistoricalMembershipServiceTest`,
`HistoricalMembershipGrpcTest`, `HistoricalMembershipIT`, `LessonEventServiceTest`,
`LessonEventServiceParallelTest`, `EventConsumerIT`, `AcademicGrpcClientTest`,
and `ReportServiceTest`; `CacheIT`/`EventIT` managed writer fixtures cover
GroupService -> UserService creation and transfer paths.

## Checks

- `git diff --check` — exit 0; static whitespace check passed (Git emitted
  expected LF/CRLF normalization warnings only).
- Gradle/protobuf generation/PG IT/Mongo IT — not run in this leaf; root heavy
  lease and host checks own execution.

## Runtime evidence

No product runtime was started here. Root must run the approved shared-proto
compile, focused Academic PG selectors, focused Attendance unit selectors, and
ordered Mongo close/cancel/report selectors after freeze.

## Diff and limitations

The working tree contains only the assigned Academic, `proto/academic.proto`,
Attendance, and focused test paths plus this evidence file. Real Mongo
concurrent close/cancel barrier coverage is still a root check; current IT
coverage proves ordered cancel-before-close and close-then-cancel convergence.
Schedule producer integration remains pending the separate Schedule writer and
the combined union check.

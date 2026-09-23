# Teacher replacement delivery evidence

## Scope and criteria

Risk: S3. This batch completes the saved replacement contract across Academic,
Schedule, SQL guards, and the shared request/status contract. Acceptance is the
owner-frozen two-phase protocol: durable Schedule barrier; Academic exact
`PREPARED → APPLIED` activation and source close at `D`; Schedule commit after
reading exact Academic authority outside locks; final Academic receipt commit.
Only future unstarted `PLANNED` rows move, physical IDs and history remain,
target recurring writes stay fenced while pending, and same actor/key/hash
replays the same durable operation.

## Evidence in source

- Academic replacement/status endpoints are in `AssignmentController`; the
  coordinator persists the idempotency tuple, validates actor/group/teacher
  grant, applies the exact `D`, and keeps RPC calls outside row-lock
  transactions in `AssignmentReplacementService`.
- Schedule `install` now only establishes the `APPLIED` barrier and narrows
  source creation cap. `commit` fetches the Academic APPLIED tuple first, then
  performs eligible lesson rebind, per-date ledger, and recurring-template
  activation in one local transaction. Active-state snapshots prevent inactive
  source templates from being activated.
- V19 guards provenance changes, pending-target inserts, and append-only
  operation/date history. V34 protects operation identity/hash and monotone
  receipts, rejects delete/reverse transitions, requires an exact APPLIED
  operation for replacement source close/target activation, rejects unknown
  operation GUCs, and requires positive intervals on every assignment INSERT.
- Academic and Schedule integration tests encode authorization, retry/hash
  replay, raw-SQL guard failures, retained history and IDs, concurrent exact
  installs/commits, active-slot uniqueness, and ordinary create after a
  replacement. They verify neither an originally inactive clone nor a later
  deactivated clone is reused as an active template. `removeAssignment` and
  `SubjectService.removeTeacher` remain typed fail-closed 409 paths.
- REST route in the frozen contract is POST
  `/academic/assignments/{sourceAssignmentId}/replace` and GET
  `/academic/assignments/replacements/{operationId}`.

## Checks and runtime

- Targeted Academic/Schedule integration batch passed on Windows PowerShell in
  the assigned worktree using Gradle 8.12. Command:
  `& './gradlew.bat' '-Dorg.gradle.java.compile-classpath-packaging=true' :services:academic-service:academic-app:integrationTest --tests=ru.rutcampustrack.academic.assignment.AssignmentReplacementIT :services:schedule-service:schedule-app:integrationTest --tests=ru.rutcampustrack.schedule.integration.AssignmentReplacementServiceIT --init-script '.agent/headman-assignment-close-delivery/academic-compile-diagnostic.init.gradle' --no-parallel --max-workers=1 --no-problems-report --no-daemon --console=plain`.
  The bounded init script confirmed the compile classpath used
  `academic-api-contract-0.1.0.jar` and that `AssignmentApi.class`,
  `ReplaceAssignmentRequest.class`, and `AssignmentReplacementResponse.class`
  are present. Result: exit 0, `BUILD SUCCESSFUL` in 2m27s, 49 tasks (14
  executed, 35 up-to-date). JUnit XML records Academic
  `AssignmentReplacementIT`: 1 test, 0 failures/errors/skips; Schedule
  `AssignmentReplacementServiceIT`: 3 tests, 0 failures/errors/skips.
  Console log: `academic-schedule-targeted-it-jar-sandboxretry.log`.
- The same JAR-mode command first hit a local
  `AccessDeniedException` reading `shared-logback-0.1.0.jar` in the restricted
  shell; root authorized one exact retry with elevated sandbox access. That
  retry passed above. Earlier class-directory compilation diagnostics are
  retained in `academic-schedule-targeted-it.log` and
  `academic-schedule-targeted-it-jar.log`; no source changes were made in
  response to those environment/classpath errors.
- `git diff --check`: exit 0 after this evidence update (LF-to-CRLF warnings
  only; no whitespace errors). The source batch has scoped Sol source PASS for
  the agreed replacement guards and recurring-template corrections.
- Runtime evidence covers the two backend integration suites and their test
  scenarios. Live UI end-to-end evidence remains pending the separate UI
  owner's runtime flow; no production deployment/migration, Docker/full suite,
  push, or merge was performed.

## Diff boundary and limitations

The implementation diff is limited to the assignment replacement REST/proto,
Academic operation/coordinator/repository and V34, Schedule coordinator/writer
and V19, related identity interceptors, and two targeted integration tests.
UI is owned in a separate worktree. Root-owned orchestration rule/packet edits
are present in the shared checkout but are excluded from this leaf's diff.
Build outputs and full console logs are local evidence artifacts and are excluded
from the product commit.

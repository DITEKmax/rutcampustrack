# R3 EligibilityReason wire-guard evidence

Status: FROZEN/READY_FOR_RECHECK
Recorded: 2026-09-08, Europe/Moscow
Worktree: C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract
Revision: d3c31acb8cce53791a4981e5858a37d44fdc9a0e (detached)
Runtime metadata surfaced by the launch packet: gpt-5.6-luna / max. No new model or
execution metadata was exposed by the local static commands.

## Goal

Close the sole MEDIUM finding from the fresh Sol R3 recheck by adding one
executable Jackson JSON/wire guard for the public `EligibilityReason` list.
Production behavior and all other files remain frozen. The frozen correction
contract is `.agent/student-gateway-c/reviews/p2-r3-wire-guard-contract-2026-09-08.md`
with SHA-256
`8FC242A80EF3EB48CA902FA107A6AA536F3F87323B294DD36BE68BEA71742279`.

## Context and evidence

The canonical public list is independently present in
`proto/attendance.proto:118-128` and `docs/openapi/mobile-bff.json:1525`:

`["ELIGIBLE","ALREADY_PRESENT","LESSON_CANCELLED","TOO_EARLY","WINDOW_CLOSED","GEO_BLOCKED","PENDING_CONFIRMATION","COOLDOWN","HEADMAN_ABSENT_REQUIRES_APPEAL","HEADMAN_USES_JOURNAL","DEPENDENCY_UNAVAILABLE"]`

`StudentApiModels.EligibilityReason` was read at lines 36-48 and has the same
eleven values in the same order. `INTERNAL_ERROR` is absent from those
eligibility contracts; it remains a separate `ProblemCode` value.

The exact5 baseline manifest was supplied at
`.agent/student-requests-bff-c/r3/exact5-manifest.json` with SHA-256
`A80F0C12505AC0D01495D08BB31977B01A98FB78BE5A565296E309FD1F3E3973`.
The prior target hash in that manifest was
`3F6027AE9E51D4EF743FEE0ACCF20B1CFC7AFCC32FE0B7B67765A9307B44D2C9`.

## Relevant scope and ownership

The sole product/test path changed by this leaf is:

`services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentRequestDetailJsonTest.java`

Evidence is limited to `.agent/student-requests-bff-c/r3-wire-guard/**`.
No production, config, generated, package, proto, OpenAPI, TypeScript, or other
test path was edited. Existing product and test dirt from the shared cumulative
checkout was preserved.

## Required behavior and criteria

The new test serializes `StudentApiModels.EligibilityReason.values()` with the
file's existing `ObjectMapper` and compares the complete JSON array in canonical
enum order. The assertion fails for any extra, missing, renamed, or reordered
public wire value, including `INTERNAL_ERROR`.

The two existing nullable-detail tests remain present and unchanged. The
focused diff contains exactly one new import and one new `@Test` method.

## Implementation

Added `eligibilityReasonsHaveExactPublicWireNamesInCanonicalOrder()`. It calls
`mapper.writeValueAsString(StudentApiModels.EligibilityReason.values())` and
compares the resulting wire string with all eleven canonical names. No source
text parsing or count-only assertion is used.

## Before/after hashes

The before snapshot is the exact five-file manifest target hash. Read-only before
and after snapshots are retained under this evidence folder:

- before: `3F6027AE9E51D4EF743FEE0ACCF20B1CFC7AFCC32FE0B7B67765A9307B44D2C9`
- after: `A1E9C55E819706852B2A4D51BA502E1344C42C4C746BBFB2A06D116624733CE9`

## Narrow diff

The retained `StudentRequestDetailJsonTest.before.java` →
`StudentRequestDetailJsonTest.after.java` diff has exactly these changes:

- add import `ru.rutcampustrack.mobilebff.contract.model.StudentApiModels`;
- add one test method `eligibilityReasonsHaveExactPublicWireNamesInCanonicalOrder`;
- the method serializes runtime `EligibilityReason.values()` and compares the
  eleven-name JSON array shown above.

No existing method body or other line changed.

## Checks and exit codes

All commands ran from the detached requests-bff-contract worktree in Windows
PowerShell, with no product process, Gradle lease, Docker, Testcontainers,
network, HTTP/RPC, Mongo, or runtime listener.

- SHA-256 after-target assertion: exit 0 (`after-hash=PASS`).
- Static wire-guard shape assertion for import, runtime enum serialization call,
  and all eleven canonical fragments: exit 0 (`wire-guard-static-shape=PASS`).
- An auxiliary contiguous-literal probe returned exit 1 because the expected
  JSON is split across three Java string fragments; the fragment-based shape
  check above is the applicable static check and passed. No product change was
  made for that probe.
- Test annotation count: exit 0 (`test-annotations=3`, preserving two existing
  tests plus one new guard).
- `git diff --no-index --check` over the retained before/after snapshots:
  exit 1 because `--no-index` reports the intentional file difference; it
  emitted no whitespace-error diagnostics. The command was not treated as a
  PASS check.
- Prior fresh focused session evidence of translator 23 + JSON 2 tests is
  historical context only; it predates this guard and is not claimed as a new
  runtime result.

## Proposed focused verification

Root-owned selector, explicitly NOT RUN in this leaf pending a new GO:

`.\\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks`

Status: NOT RUN; exit code: N/A. Expected fresh test count after this change is
translator 23 plus JSON contract 3, with zero failures, errors, and skips. Root
must record actual start/end, exit code, XML evidence, updated cumulative
manifest, and fresh independent Sol/high recheck after granting the lease.

## Runtime evidence and limitations

Runtime is N/A at this static implementation handoff. No Gradle, service,
HTTP/RPC, authorization, persistence, queue, Docker, Testcontainers, or live
integration claim is made. This evidence proves only the bounded test source
change, its exact before/after hashes, the independently grounded expected list,
and static shape checks. The exact5 manifest still contains the pre-change target
hash and is intentionally untouched; root owns manifest refresh after the
authorized focused run.

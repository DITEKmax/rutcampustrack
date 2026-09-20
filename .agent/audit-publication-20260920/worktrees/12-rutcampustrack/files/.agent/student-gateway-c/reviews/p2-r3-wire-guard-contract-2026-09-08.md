# P2 R3 eligibility wire guard — frozen correction contract

Date: 2026-09-08. Risk: S3. Writer: fresh Luna/max leaf
`/root/requests_bff_wire_guard_r3`.

## Goal

Close the sole MEDIUM verification finding from the fresh Sol R3 recheck by
adding an executable JSON/wire regression guard for the public
`EligibilityReason` list. Production code remains frozen.

## Context / evidence

The earlier real drift introduced `EligibilityReason.INTERNAL_ERROR` into the
mobile API while the canonical attendance proto and generated OpenAPI did not
contain it. The current production enum is corrected, and the fresh 23+2
focused tests pass, but none of those tests observes the public enum list. The
full review is `p2-r3-sol-recheck-2026-09-08.md`, SHA-256
`74268BFCCDC43AD03EABC6757D8DC754F7B478D1FD479D38B4329254E852980F`.

## Relevant scope

The sole writable test path is
`services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentRequestDetailJsonTest.java`.
Evidence may be written only below
`.agent/student-requests-bff-c/r3-wire-guard/`. All production, config, package,
generated and other test files are read-only.

## Required behavior

Add one material Jackson serialization assertion over
`EligibilityReason.values()` whose expected JSON is exactly:

`["ELIGIBLE","ALREADY_PRESENT","LESSON_CANCELLED","TOO_EARLY","WINDOW_CLOSED","GEO_BLOCKED","PENDING_CONFIRMATION","COOLDOWN","HEADMAN_ABSENT_REQUIRES_APPEAL","HEADMAN_USES_JOURNAL","DEPENDENCY_UNAVAILABLE"]`

The expected value comes from independent public contracts:
`proto/attendance.proto:118-128` and
`docs/openapi/mobile-bff.json:1525`. The assertion must fail for an extra,
missing, renamed or reordered wire value, including `INTERNAL_ERROR`.

## Constraints

Preserve the two existing nullable-detail tests. Use existing Jackson and JUnit
patterns/dependencies. Do not edit the Java API enum, proto, OpenAPI, generated
TypeScript, client translator, packages, build files or ACLs.

## Existing patterns

`StudentRequestDetailJsonTest` already owns mobile BFF JSON contract assertions
and creates an `ObjectMapper`. Enum serialization therefore belongs in this
existing contract test rather than in a production mapping or source-text test.

## Acceptance criteria

Exactly one focused test is added; it serializes runtime enum values and compares
them with the independent canonical wire list. The one-file diff is clean and
all other exact-five hashes remain frozen.

## Verification

Before Gradle, record before/after SHA-256 and a narrow diff. After parent GO,
run the same two-class selector with `--no-daemon --no-parallel --max-workers=1
--console=plain --rerun-tasks`. Expected fresh result: translator 23 plus JSON
contract 3, with zero failures, errors or skips. Recompute XML, exact-five and
finite-guard hashes, then obtain a fresh independent Sol/high recheck.

## Do not

Do not broaden API behavior, edit production/generated/config files, reuse the
previous reviewer as writer, run Gradle without GO, claim live HTTP/gRPC/Docker
runtime, or accept the correction without a fresh independent recheck.

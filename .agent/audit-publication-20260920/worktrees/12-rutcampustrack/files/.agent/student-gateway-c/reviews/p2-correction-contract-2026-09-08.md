# Requests BFF review correction — frozen contract

Date: 2026-09-08. Risk S2. Root/parent accepted both independent MEDIUM findings. Parent explicitly authorized C coordinator to save this packet and verbatim review as evidence; this does not authorize root product writes. Fresh Luna max developer remains required. Current fresh spawn is BLOCKED by actual tool thread limit; no implementation writer has been assigned.

## 1. Goal

Close the two BFF review findings and the parent's explicit malformed/raw-status and DATA_LOSS criteria before handing off Requests BFF.

## 2. Context / evidence

Worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract`.
HEAD: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.
Read `.agent/student-requests-bff-c/{packet.md,repair-manifest.json,checks.json}` and sibling coordinator `reviews/bff-sol-review-2026-09-08.md`.
Root and independent reviewer verified all five frozen hashes, both XML hashes (17+2 PASS), and accepted import guards. The original 82 imported paths plus two accepted isolation repairs are dependencies, not repair authorship. Previous unit PASS is historical and does not close the findings.
Root opened the critical StudentApiModels and translator originals. Parent confirmed the accidental eligibility enum, selected raw precedence even with malformed metadata, and added DATA_LOSS not being promoted by typed details.

## 3. Relevant scope

Only these three product/test files in the worktree are writable:

- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java`

The assigned developer owns `.agent/student-requests-bff-c/**` evidence. The other two files in the existing five-file repair manifest are frozen. No other product changes are authorized.

## 4. Required behavior

Remove only `EligibilityReason.INTERNAL_ERROR`; retain `ProblemCode.INTERNAL_ERROR`.
Raw `UNAUTHENTICATED` takes precedence as 401/INVALID_SESSION, `PERMISSION_DENIED` as 403/OUT_OF_SCOPE, and `UNAVAILABLE`/`DEADLINE_EXCEEDED` as 503/DEPENDENCY_UNAVAILABLE, even if metadata is contradictory or malformed.
Raw `INTERNAL`, `UNKNOWN`, and `DATA_LOSS` remain safe 500/INTERNAL_ERROR regardless of plausible typed 4xx metadata. Preserve generic unmapped and local-error 500 behavior.
Preserve valid typed domain 4xx including 409 and 429/retryAt. Preserve explicit-null request Detail behavior.
Add observable contradictory-detail, malformed auth/transport metadata, and DATA_LOSS checks in the owned test class. Update historical malformed UNAVAILABLE expectation consistently with raw 503 precedence.

## 5. Constraints

No public schema expansion, proto, OpenAPI, generated files, TypeScript, config, lockfiles, dependencies, secrets, deployments, or other product paths. No Gradle, Testcontainers, or product runtime before a new explicit lease. Preserve prior artifacts and imported changes; one writer only.

## 6. Existing patterns

Reuse bounded translator helpers and existing StatusProto test builders. Use immutable import and accepted repair manifests for cumulative scope auditing. Do not attribute the complete HEAD diff to this repair.

## 7. Acceptance criteria

Both MEDIUM findings and the parent's additional raw-status cases are closed. Valid typed semantics and nullable JSON stay intact. Current five-file hashes and finite import/scope audit are recorded. Required focused checks pass, followed by fresh independent Sol high recheck of affected paths. Full live integration is a separate gate.

## 8. Verification

Before product writes, save this fresh nine-section contract and exact before hashes in the developer's evidence directory, retaining the original review verbatim.
Static hash/import checks and git diff --check are permitted. Report READY with changed hashes and this command; do not execute until new GO:

`.\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest --no-parallel --max-workers=1 --console=plain`

After GO, record revision, command, environment, start/end, exit code, XML counts/hashes, explicit process/lease release, and updated cumulative five-file manifest. No automatic rerun after failure.

## 9. Do not

No Throwable handler, optional retry-policy redesign, broad refactor, commits, generated-schema propagation of the accidental enum, silent model substitution, reuse of the old author as a fresh leaf, repeated spawn without changed evidence, or hidden orchestration. Do not present historical 19-test PASS as verification of the correction.

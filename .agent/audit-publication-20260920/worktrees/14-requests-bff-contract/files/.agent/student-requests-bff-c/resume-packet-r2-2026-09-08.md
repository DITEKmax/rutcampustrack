# Fresh r2 resume packet: Requests BFF correction

## 1. Goal

Закрыть два принятых MEDIUM finding из P2 для Requests BFF: удалить случайный
`EligibilityReason.INTERNAL_ERROR` и обеспечить приоритет сырых auth/transport
gRPC статусов над противоречащими или повреждёнными typed details. Риск S2;
исполнитель — sole writer свежего bounded Luna max leaf; детей не создаёт.

## 2. Context / evidence

- Worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract`.
- Base HEAD: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached).
- Frozen correction contract SHA-256:
  `887F7C949491C91C5E655D67981C237E393000B30BCC2CBC8EC7EE5DE60D7C9C`.
- Independent P2 FAIL review SHA-256:
  `4D37F06BE84606436FE8D375C70484838F0C3D195222C3698F142DABDFC6C93C`.
- Before hashes for all five manifest files are recorded in
  `.agent/student-requests-bff-c/before-hashes-r2-2026-09-08.json` before any
  product edit. Existing WIP and the large inherited dirty set are preserved.
- Review repros: typed conflict under raw `UNAUTHENTICATED` can become 409;
  typed not-found under raw `UNAVAILABLE` can become 404; malformed status
  metadata can bypass the raw dependency result. `DATA_LOSS` also requires the
  safe 500 branch.

## 3. Relevant scope

Only these three product/test files may change in this correction:

- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java`

All evidence, checks, runtime note, diff and summary are owned under
`.agent/student-requests-bff-c/**`. The other two five-manifest files are
frozen, including nullable `StudentRequestApiModels.Detail` behavior.

## 4. Required behavior

- Remove only `EligibilityReason.INTERNAL_ERROR`; retain
  `ProblemCode.INTERNAL_ERROR` and unrelated imported enum values.
- Raw `UNAUTHENTICATED` → 401/`INVALID_SESSION`, `PERMISSION_DENIED` →
  403/`OUT_OF_SCOPE`, and `UNAVAILABLE`/`DEADLINE_EXCEEDED` →
  503/`DEPENDENCY_UNAVAILABLE`, even with contradictory or malformed details.
- Raw `INTERNAL`, `UNKNOWN`, and `DATA_LOSS` → safe 500/`INTERNAL_ERROR` with
  generic Russian detail, regardless of typed 4xx metadata.
- Preserve valid typed 4xx/409/429 mappings and `retryAt`, valid transport
  fallbacks, local safe 500 behavior, and explicit-null Detail semantics.
- Add observable tests for contradictory auth/transport details, malformed
  auth/transport metadata, and raw `DATA_LOSS`; update the malformed
  `UNAVAILABLE` expectation to raw 503 precedence.

## 5. Constraints

No proto/OpenAPI/TS/generated/config/lockfile/dependency/handler/runtime/
Testcontainers changes; no commits or global changes; no broad refactor or
`Throwable` catch; preserve every foreign edit and all five manifest paths.
No Gradle or product runtime until a new explicit root GO/lease.

## 6. Existing patterns

Reuse current `MobileAttendanceClient` helpers, `StatusProto` builders,
`MobileBffException`, public `ProblemCode` mapping and existing JUnit 5/AssertJ
style. Raw status precedence is a bounded guard before typed detail extraction;
typed valid domain semantics remain unchanged.

## 7. Acceptance criteria

Both P2 MEDIUM findings and the parent raw-status additions are closed with
observable tests. `EligibilityReason` has no server-error value while
`ProblemCode.INTERNAL_ERROR` remains. Raw auth/transport/server statuses match
the compact contract under contradictory and malformed metadata. Five current
manifest hashes, finite import/scope guard, diff check, focused checks and
limitations are recorded; no outside-owned product drift is introduced.

## 8. Verification

Before GO, only static/hash/scope/diff checks are allowed. After root grants a
new lease, run exactly:

` .\\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest --no-parallel --max-workers=1 --console=plain`

Record command, environment, start/end, exit code and XML counts/hashes in the
r2 checks evidence. Runtime is N/A for this isolated unit/serialization lane.
Root arranges a fresh independent Sol high recheck after a stable diff.

## 9. Do not

Не менять `StudentRequestApiModels.java` или `StudentRequestDetailJsonTest.java`,
proto/OpenAPI/TS/generated files, other product paths, shared configs or
lockfiles; не менять raw semantics через optional retry redesign; не делать
Terra escalation without a recorded defect/complexity gate with request,
reproduction, new evidence, correction, bounded scope and root decision.

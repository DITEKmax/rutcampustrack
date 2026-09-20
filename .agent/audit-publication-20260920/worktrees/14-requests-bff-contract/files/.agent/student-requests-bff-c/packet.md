# Compact contract: student requests BFF repair

## 1. Goal

Исправить классификацию ошибок в `MobileAttendanceClient` для подтверждённого
BFF500/503-дефекта и закрепить JSON-форму `StudentRequestApiModels.Detail`:
`reason`, `comment`, `decision` всегда присутствуют и допускают `null`.

Риск: S2. Исполнитель: sole writer своего worktree, свежий bounded implementation
leaf `gpt-5.6-luna`, effort `max`; детей не создаёт. Базовая ревизия worktree:
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached HEAD).

## 2. Context / evidence

- Root/parent opened the critical originals and recorded a fresh Sol FAIL.
- `MobileAttendanceClient` currently maps typed `UNSPECIFIED` to no enum value,
  breaks into a dependency fallback (503), and maps generic gRPC failures to
  `DEPENDENCY_UNAVAILABLE` (503).
- `StudentApiModels.ProblemCode` has no server-error code; its exhaustive HTTP
  switch therefore has no explicit 500 branch.
- `MobileProblemHandler` already renders `MobileBffException` with the public
  RFC problem shape and `no-store`; this lane does not edit it.
- `StudentRequestApiModels.Detail` currently has nullable `reason`, `comment`,
  and `decision` that are omitted by `NON_NULL`; required nullable keys are the
  accepted product contract.
- Source-resolution artifacts: immutable transport handoff
  `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-transport/.agent/student-role-02/diff.json`,
  SHA-256
  `4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`, 91
  entries (82 product, 9 `.agent`); repair manifest
  `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-isolation/.agent/student-requests-isolation-c/repair-manifest.json`,
  SHA-256
  `7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3`.
  The exact 82 product paths and accepted repair files are imported with source
  and destination hash guards before implementation. No source conflicts found.
- An addressable ownership/memory gateway was not available in this tool surface;
  ownership is therefore limited to the explicit parent packet and clean WT
  baseline checks.

## 3. Relevant scope

Own production paths (only these code changes):

1. `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
   — additive `ProblemCode.INTERNAL_ERROR` only.
2. `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java`
   — `Detail` required nullable properties only.
3. `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
   — gRPC error classification and bounded helper only.

Exact new tests owned by this lane:

- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentRequestDetailJsonTest.java`

Evidence/checks/summary belong under `.agent/student-requests-bff-c/`. The 82
immutable handoff product paths and the two accepted IT repair files are inherited
and preserved; they are not additional owned implementation scope.

## 4. Required behavior

- Add `ProblemCode.INTERNAL_ERROR`.
- A typed `StudentCheckinErrorDetail` with `UNSPECIFIED`, `INTERNAL`, an unknown
  or unrecognized code, an invalid retry timestamp, or malformed detail falls back
  to HTTP 500 / `INTERNAL_ERROR` with a generic Russian message. Do not include
  exception or server-detail text in the public message.
- Raw gRPC `INTERNAL`, `UNKNOWN`, and other unrecognized/unmapped status failures
  map to HTTP 500 / `INTERNAL_ERROR` with the same generic Russian message.
- Raw `UNAVAILABLE` and `DEADLINE_EXCEEDED` map to HTTP 503 /
  `DEPENDENCY_UNAVAILABLE`.
- Preserve valid typed request codes, check-in 4xx status, `retryAt`, and existing
  transport 4xx semantic fallback. `UNAUTHENTICATED` remains invalid-session.
- Do not add a generic `Throwable` handler.
- `Detail` serializes `reason`, `comment`, `decision` as explicit `null` while a
  pending/late request has no values, and serializes populated values unchanged;
  all other DTOs retain their existing behavior.

## 5. Constraints

- Java-first public shape; only additive error enum change is allowed.
- No OpenAPI, TypeScript, protobuf, generated, config, Gateway, P1 bot, or other
  `StudentApiModels` section writes.
- No external services, secrets, production, Gradle/Testcontainers, or broad
  battery before a fresh GO/lease from root.
- Preserve every imported path and pre-existing change, including the empty
  imported `services/notification-bot/uv.lock`; do not edit or delete it.
- No edits outside the listed production/test paths and own `.agent` directory.

## 6. Existing patterns

- `MobileBffException(status, code, message[, retryAt])` is consumed by the
  existing `MobileProblemHandler`, which owns RFC 9457 output and `no-store`.
- `MobileAttendanceClient` already extracts `StudentCheckinErrorDetail` with
  `StatusProto` and parses `retryAt`; the repair keeps valid mappings and narrows
  fallback handling.
- `StudentRequestApiModels` uses Jackson records and OpenAPI `requiredProperties`;
  use explicit `@JsonInclude(JsonInclude.Include.ALWAYS)` on the three required
  nullable fields, matching existing contract patterns.
- Tests use JUnit 5/Spring Boot conventions; assertions must exercise observable
  `MobileBffException` status/code/message/retryAt and actual Jackson JSON keys.

## 7. Acceptance criteria

- Typed `UNSPECIFIED` + typed `INTERNAL`, raw `INTERNAL`, raw `UNKNOWN`, and
  malformed/invalid detail produce 500 / `INTERNAL_ERROR`, generic RU message,
  and no server-detail leak.
- Raw `UNAVAILABLE` and `DEADLINE_EXCEEDED` produce 503 /
  `DEPENDENCY_UNAVAILABLE`.
- Existing typed 404/409/expired-role/cooldown behavior and `retryAt` remain
  unchanged; valid transport 4xx semantic fallback remains unchanged.
- Pending/late `Detail` JSON contains `reason`, `comment`, `decision` with null
  values; populated excuse values and `summary`/`attachments` are retained.
- Stable scoped diff has only the three production changes, two exact new tests,
  inherited imported paths, accepted IT repairs, and `.agent` evidence.
- All outside-owned imported product hashes equal the immutable source/repair
  manifests; no unrelated baseline file changes are introduced.

## 8. Verification

- Before code: verify source manifest SHA and repair manifest SHA; import exactly
  82 source product paths plus the two accepted IT files with source/destination
  hash guards, recording results in this lane's evidence.
- Before root GO: static inspection and finite diff only; no Gradle,
  Testcontainers, service startup, or external runtime.
- After fresh GO/lease: run the exact focused Gradle selectors for the two owned
  tests (estimated 20–60 seconds warm, 2–4 minutes cold), record revision,
  command, exit code, environment, and evidence in `checks.json`.
- If permitted, run only the focused mobile-bff app test selectors; runtime
  service integration is N/A for this isolated unit/serialization lane.
- Verify no server-detail leak by assertions and inspect the stable diff. Root will
  arrange fresh independent Sol review after checks.

## 9. Do not

Do not create children; edit another worktree; alter global handlers, other DTO
sections, OpenAPI/TS/proto/generated outputs, build/config/lock files, Gateway or
bot code; add a generic Throwable handler; run heavy checks before GO; leak secrets
or server exception text; redesign the contract; or escalate to Terra without a
recorded defect/complexity gate containing request, reproduction, new evidence,
correction, bounded scope, and root decision.

# Resume packet: student requests BFF repair

Снимок возобновления: 2026-09-08. Это свежий bounded packet для sole writer в
`C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract`;
старый author transcript не используется.

## 1. Goal

Завершить только существующий WIP по BFF500/503-классификации ошибок и
nullable JSON-контракту `StudentRequestApiModels.Detail`. Риск S2; назначение
`gpt-5.6-luna/max`, детей не создавать. Критерий результата: exact focused
tests, finite scoped diff, checks/evidence и корректный handoff root.

## 2. Context / evidence

- Base revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached HEAD).
- Immutable transport source:
  `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-transport`;
  source manifest:
  `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-transport/.agent/student-role-02/diff.json`;
  SHA-256 `4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`.
- Accepted repair manifest:
  `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-isolation/.agent/student-requests-isolation-c/repair-manifest.json`;
  SHA-256 `7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3`.
- The current checkout preserves the imported 82 product paths and 2 accepted
  IT repairs. Current product dirty set is 82 paths; current own metadata is
  `packet.md` and `shutdown-checkpoint.md` (84 full porcelain entries before
  this file). WIP hashes before implementation remain:
  `StudentApiModels.java`
  `6D4F1AAA69A2112358459041BD80C1917755476707E118E9EDE6F5DED0AF91C1`,
  `StudentRequestApiModels.java`
  `651B83E0E7B629F2750EE51231915B9A55D072E3F04FB31EDD6C0D4864198286`,
  `MobileAttendanceClient.java`
  `4940C439B74F8A92B606D5B37DFC9660A6A68AEDA12F34D67B64E8F809768C17`.
- Critical originals read directly: source `MobileAttendanceClient`,
  `StudentRequestGrpcErrors`, `StudentCheckinGrpcErrors`, and `attendance.proto`.
  The current WIP lacks the required two exact tests and does not yet guard
  `StatusProto.fromThrowable` against malformed transport metadata.

## 3. Relevant scope

Production changes are limited to:

1. `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
   — additive `ProblemCode.INTERNAL_ERROR` only.
2. `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java`
   — required nullable `Detail.reason`, `comment`, `decision` only.
3. `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
   — bounded classification/extraction helpers only.

Exact new tests are limited to
`MobileAttendanceClientErrorTest.java` and `StudentRequestDetailJsonTest.java`
under the corresponding mobile-bff app test packages. All evidence/review
artifacts stay under `.agent/student-requests-bff-c/`.

## 4. Required behavior

- Typed `UNSPECIFIED`, `INTERNAL`/unknown/unrecognized, malformed detail, or
  invalid `retryAt`, and raw `INTERNAL`/`UNKNOWN`/other unrecognized statuses
  produce HTTP 500, `INTERNAL_ERROR`, and generic Russian detail
  `Внутренняя ошибка сервера`; no server detail leaks.
- Raw `UNAVAILABLE` and `DEADLINE_EXCEEDED` produce HTTP 503 and
  `DEPENDENCY_UNAVAILABLE`.
- Valid typed 4xx codes retain status, code, request messages, check-in
  cooldown `retryAt`, and existing transport 4xx fallback semantics.
- `Detail.reason`, `comment`, and `decision` serialize as keys with `null`
  values when absent and preserve populated values; other DTO behavior stays.
- `StatusProto` extraction failures are converted to the bounded internal
  result and cannot escape the translator.

## 5. Constraints

No handler/config/proto/generated/OpenAPI/TypeScript/lockfile/Gateway/bot
changes, no other `StudentApiModels` sections, no global Throwable handler, no
real RPC/runtime or heavy Gradle checks before a fresh root GO/lease, no other
worktree edits, no import/reset/rewrite of inherited files, no secrets.

## 6. Existing patterns

Use `MobileBffException`, existing `MobileProblemHandler` no-store behavior,
Jackson record annotations, `StatusProto` typed details, and JUnit 5/AssertJ
test conventions. Source transport owns typed error names and gRPC status
semantics; this leaf only maps them to the public BFF contract.

## 7. Acceptance criteria

Focused tests observe status/code/generic message/retryAt and actual Jackson
JSON keys. Valid 404/409/cooldown/session behavior remains covered. Stable diff
contains only the three owned production paths, two exact test paths, inherited
paths and own evidence. Manifest/repair hash guards show zero inherited drift;
no critical review findings remain.

## 8. Verification

Before GO: static inspection and finite diff only. After root lease, run exactly:

`./gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest --no-parallel --max-workers=1 --console=plain`

Record revision, command, exit code, environment and evidence in
`.agent/student-requests-bff-c/checks.json`; runtime service integration is N/A
for this unit/serialization lane. Root arranges fresh independent Sol review.

## 9. Do not

Не создавать детей; не использовать старый transcript; не трогать чужую
работу/другие worktrees; не расширять product contract или redesign; не
эскалировать Terra без recorded defect/complexity gate с request,
reproduction, новым evidence, correction, bounded scope и root decision.

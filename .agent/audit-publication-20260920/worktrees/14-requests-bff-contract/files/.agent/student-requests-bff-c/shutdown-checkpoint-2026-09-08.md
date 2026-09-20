# Shutdown checkpoint — student-requests-bff-c

Дата: 2026-09-08 (Europe/Moscow)
Статус: `PAUSED_BY_OWNER`

## Current state

- CWD/worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract`
- HEAD: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached)
- Worktree dirty до этого checkpoint: да; checkout содержит ранее импортированный/WIP diff по нескольким backend, frontend, contract, generated, OpenAPI, proto, event и agent-путям. Этот checkpoint не меняет и не очищает его.
- Старый author: released.
- Собственные процессы: отсутствуют; Gradle lease не выдавался и не запускался.
- Fresh correction: не начиналась. Product files, new checks, runtime и Gradle после owner pause не менялись/не запускались.

## Source/review state

- Frozen correction contract: `.agent/student-gateway-c/reviews/p2-correction-contract-2026-09-08.md`, SHA-256 `887F7C949491C91C5E655D67981C237E393000B30BCC2CBC8EC7EE5DE60D7C9C`.
- Independent FAIL review: `.agent/student-gateway-c/reviews/p2-final-2026-09-08.md`, SHA-256 `4D37F06BE84606436FE8D375C70484838F0C3D195222C3698F142DABDFC6C93C`.
- Historical verification: 19 PASS retained as historical evidence; independent Sol review has 2 FAIL findings. Existing prior BFF-C WIP is historical/author-completed and remains untouched while paused.

## Current hashes of the three requested correction paths

- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`: `6D4F1AAA69A2112358459041BD80C1917755476707E118E9EDE6F5DED0AF91C1`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`: `450845E563AC7A37091A027DC5E717D0B19331327DA75E7473CF25261A05CDAC`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java`: `6471F65AB71698F27B2B7FDD2A9E79741EB7E558F4DF833B2613B9BBAFDB2C40`

## Open correction criteria

- Remove accidental `EligibilityReason.INTERNAL_ERROR` / `retainProblemCode`; keep the established compact contract.
- Enforce raw `401`/`403`/`503` precedence even when typed metadata is contradictory or malformed.
- Keep raw `INTERNAL`/`UNKNOWN`/`DATA_LOSS` HTTP 500 from being overridden by typed 4xx metadata.
- Preserve valid typed 4xx mappings, including `409`, `429` and `retryAt`, plus the two frozen manifest files carrying null JSON behavior.
- Add bounded observable tests for the correction.
- Static guard and `git diff --check` are allowed while paused only if a new owner task authorizes execution; no Gradle until a new explicit lease/GO.

## Exact next steps after a new direct owner task

1. Re-read and verify the frozen contract and independent FAIL review hashes before any product write.
2. Save a fresh nine-section packet under `.agent/student-requests-bff-c/` with base SHA, scope, criteria and ownership; copy review evidence there.
3. Edit only the three exact manifest paths above and the owned agent evidence files; preserve all other dirty paths.
4. Run only authorized static guard/diff checks first; request/await explicit Gradle lease before the exact two-class selector.
5. Record current SHA-256 values, checks with exit codes, runtime `N/A` if still not applicable, diff and limitations; send READY plus five current hashes and the exact two-class selector to `/root`.
6. Stop for the fresh independent Sol recheck; do not claim completion before it.


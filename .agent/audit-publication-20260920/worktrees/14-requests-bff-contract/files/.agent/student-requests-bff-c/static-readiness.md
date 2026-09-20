# BFF C static readiness

Дата: 2026-09-08. Состояние: `READY_FOR_GO`, runtime и Gradle ещё не запускались.

## Scope

S2 bounded delta в выделенном worktree `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract`:

- `StudentApiModels.java` — additive `ProblemCode.INTERNAL_ERROR` в текущем
  импортированном контракте.
- `StudentRequestApiModels.java` — nullable `Detail.reason`, `comment`,
  `decision` с сериализацией отсутствующих полей как `null`.
- `MobileAttendanceClient.java` — bounded gRPC error classification and
  malformed `StatusProto`/local failure fallback.
- `MobileAttendanceClientErrorTest.java` — exact focused error mapping tests.
- `StudentRequestDetailJsonTest.java` — exact focused nullable JSON tests.

Inherited 82 product paths and 2 accepted IT repairs remain preserved. No
other production scope was edited by this leaf.

## Criteria and evidence

- Raw `INTERNAL`, `UNKNOWN`, `DATA_LOSS`, malformed/unknown typed details and
  invalid cooldown timestamps map to HTTP 500, `INTERNAL_ERROR`, and generic
  Russian detail.
- Raw `UNAVAILABLE` and `DEADLINE_EXCEEDED` map to HTTP 503 and
  `DEPENDENCY_UNAVAILABLE`.
- Valid request 404/409, auth/scope 4xx and cooldown `retryAt` keep their
  public mapping.
- Absent `Detail.reason`, `comment`, `decision` remain explicit JSON `null`;
  populated values and ISO `Instant` remain unchanged.
- Current HEAD: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.
- SHA-256 evidence:
  - `StudentApiModels.java`: `6D4F1AAA69A2112358459041BD80C1917755476707E118E9EDE6F5DED0AF91C1`
  - `StudentRequestApiModels.java`: `651B83E0E7B629F2750EE51231915B9A55D072E3F04FB31EDD6C0D4864198286`
  - `MobileAttendanceClient.java`: `450845E563AC7A37091A027DC5E717D0B19331327DA75E7473CF25261A05CDAC`
  - `MobileAttendanceClientErrorTest.java`: `6471F65AB71698F27B2B7FDD2A9E79741EB7E558F4DF833B2613B9BBAFDB2C40`
  - `StudentRequestDetailJsonTest.java`: `3F6027AE9E51D4EF743FEE0ACCF20B1CFC7AFCC32FE0B7B67765A9307B44D2C9`

## Checks and limitations

- `git diff --check`: exit `0`; only expected LF/CRLF warnings from inherited
  imported files were emitted.
- Guarded immutable import verification: exit `0`,
  `IMPORT_VERIFY_PASS productPaths=82 repairs=2`, recorded in the prior packet.
- Focused Gradle command is pending explicit root GO/lease.
- Runtime service integration is N/A for this unit/serialization lane; no
  process, container, RPC, or external service was started.
- Independent Sol review remains root-owned after stable diff and checks.

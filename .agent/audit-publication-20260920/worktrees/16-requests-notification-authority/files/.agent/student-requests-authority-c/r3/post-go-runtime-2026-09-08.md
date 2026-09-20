# R3 post-GO runtime addendum — 2026-09-08

This addendum is evidence-only and preserves the pre-GO history in
`checks.md` and `runtime-evidence.md`. No product or test target was edited for
this closeout.

## Accepted root run

- Session: `54171`.
- Exact selector: the focused three-class Gradle selector from `packet.md`.
- Result: exit `0`; `BUILD SUCCESSFUL`; elapsed `1m53s`; 37 tasks executed.
- Exact process bounds: unavailable. The only retained daemon timing is
  approximate creation/last-write `21:42:49–21:44:39 +03:00`.
- XML totals: 28 tests, 0 failures, 0 errors, 0 skipped.

| XML suite | Tests | Failures | Errors | Skipped | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `AttendanceRequestBotGrpcServiceTest` | 8 | 0 | 0 | 0 | `62C4AA6A91DF3348C5ABF1897BC6461F588D7285827822E8517BF781965C9108` |
| `StudentRequestServiceAuthorizationTest` | 18 | 0 | 0 | 0 | `A0B0DDF3D5516EF0457EF9B25D94488495C93B097485FBE184F5D4793FA0A222` |
| `StudentRequestGrpcErrorsTest` | 2 | 0 | 0 | 0 | `FD2659C3639AA591911C78CE19F647B1D05C0C10F89DE77B88D7A3043A689435` |

## Stable target and review evidence

The exact two product/test target hashes after the accepted run remain:

- `StudentRequestService.java` —
  `B962498813AD3CC4E77EDBFE81E5E82E3013CFE1CF7B0490762880D23FCD3960`.
- `StudentRequestServiceAuthorizationTest.java` —
  `D1DDCDE648C3186914BA52846973497F08D82BBA6C198743A5254EC37E8B0B22`.

Fresh independent Sol review: PASS. Immutable artifact:
`C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/student-gateway-c/reviews/p1-r3-sol-recheck-2026-09-08.md`;
SHA-256 `EDBF43DC2B7162CBB0BA99147EB1890211B6F748019C09B0D56FD621E2E5FE8D`.

## Runtime boundary

The accepted run proves the selected in-process Java tests only. No live
Rabbit, Telegram, Mongo or external product runtime claim is made. No Docker,
Testcontainers, deployment or migration was performed by this task.

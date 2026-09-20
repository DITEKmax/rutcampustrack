# R3 final manifest — 2026-09-08

Status: `FINAL_MANIFEST_READY`. This is an evidence-only closeout. Product and
test targets are frozen and were not modified after the accepted root run.

## Scope and stable targets

Base revision remains detached HEAD
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`
  — `B962498813AD3CC4E77EDBFE81E5E82E3013CFE1CF7B0490762880D23FCD3960`.
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java`
  — `D1DDCDE648C3186914BA52846973497F08D82BBA6C198743A5254EC37E8B0B22`.

The exact two target hashes were recomputed read-only after the accepted run
and remain equal to the frozen values. The implementation diff stays bounded
to notification attachment reconciliation and its focused observable tests;
foreign dirty work remains preserved.

## Accepted root verification

- Session `54171`; exact focused selector is recorded in `packet.md`.
- Exit `0`, `BUILD SUCCESSFUL`, elapsed `1m53s`, 37 tasks executed.
- Exact process bounds unavailable; retained daemon creation/last-write timing
  is approximate `21:42:49–21:44:39 +03:00` only.
- XML total: 28 tests, 0 failures, 0 errors, 0 skipped.

| Suite | Tests | Failures | Errors | Skipped | XML SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `AttendanceRequestBotGrpcServiceTest` | 8 | 0 | 0 | 0 | `62C4AA6A91DF3348C5ABF1897BC6461F588D7285827822E8517BF781965C9108` |
| `StudentRequestServiceAuthorizationTest` | 18 | 0 | 0 | 0 | `A0B0DDF3D5516EF0457EF9B25D94488495C93B097485FBE184F5D4793FA0A222` |
| `StudentRequestGrpcErrorsTest` | 2 | 0 | 0 | 0 | `FD2659C3639AA591911C78CE19F647B1D05C0C10F89DE77B88D7A3043A689435` |

## Independent review

Fresh independent Sol review: `PASS`.
Immutable artifact:
`C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/student-gateway-c/reviews/p1-r3-sol-recheck-2026-09-08.md`.
SHA-256: `EDBF43DC2B7162CBB0BA99147EB1890211B6F748019C09B0D56FD621E2E5FE8D`.

## Read-only status guard

Command: `git status --porcelain=v1 --untracked-files=all`; exit `0`.
Environment: Windows PowerShell in the assigned
`requests-notification-authority` worktree. Snapshot taken immediately before
this manifest was written: 114 rows, including 8 existing r3 evidence rows,
the 2 frozen target paths and the inherited 106-row dirty scope. No reset,
clean, checkout, commit or foreign path mutation occurred. Writing this
manifest adds one evidence row; no product/test row changes.

## Current r3 evidence hashes

These hashes were recomputed read-only in the same 114-row snapshot, before
adding this manifest:

| Evidence file | SHA-256 |
| --- | --- |
| `after-hashes.txt` | `74EDA3F9AA043DF1D899D77AB2789BA7DEED0B00CFF767C6519CF520F63E19CC` |
| `before-hashes.txt` | `FC75C24F24A84DF74F2FF9D5A735123350AE2183BF6692ED6D9D2D6700C05B58` |
| `checks.md` | `09A6D2B4715C4648C3A68431D530596E27CD171D4E8581E23D46E7691690D794` |
| `diff.md` | `72552199E6CD0464E63A1DA4A4091A2E6F57D5A1AD0DB6AE2A75FEC89529FB0F` |
| `packet.md` | `577B6CBB8F454BF911AA60942CAE3C2929F374D9BDF4272BC51CE0620B262F2F` |
| `post-go-runtime-2026-09-08.md` | `B2BCAE4C1FD379C77DEB5B5EE7A4BDB5F33B43408D2B53C7DFEB9532A61CE290` |
| `runtime-evidence.md` | `6F80D6B4C3F162CD767DAE4224572AF5FCFCAA93C347F14A348CCBA772EB2C70` |
| `summary.md` | `A2269EFE9A2190557A5F837B5972FED17B02FA2F81BB71B3FFA443861436FA17` |

The final manifest itself is intentionally excluded from this pre-write list;
its post-write hash can be recomputed by the receiving root.

## Checks and runtime limits

Pre-GO static checks and their exit codes remain in `checks.md`. Its recorded
repository-wide `git diff --check` exit `2` is attributable to pre-existing
foreign tracked/imported whitespace; the target-specific scanner exited `0`,
and no unrelated warning changed code. The accepted root selector is the only
post-GO runtime evidence for this correction.

No live Rabbit, Telegram, Mongo or external product runtime is claimed. No
Docker, Testcontainers, deployment, migration or external listener was
started by this task.

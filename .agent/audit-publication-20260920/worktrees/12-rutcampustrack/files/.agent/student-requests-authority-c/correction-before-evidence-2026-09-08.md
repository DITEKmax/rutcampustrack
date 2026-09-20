# Attachment reconciliation correction — before evidence

Recorded 2026-09-08 in detached worktree
`C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-notification-authority`.

## Git baseline

- `git rev-parse HEAD` → `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (exit 0).
- `git status --short --branch` → detached HEAD with inherited dirty tracked
  and untracked import rows; target source/test are untracked imported files.
  The full foreign status is preserved by the existing baseline artifacts and
  was not cleaned or reset.

## Target before hashes

| Path | SHA-256 before correction |
| --- | --- |
| `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java` | `BD054202143BEB11265608080373BD874228C4057DB64ABC8F17A3C141B9DE1F` |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java` | `0591DAC19F2FD5BDB7C3E41C9F66106FD9D0799945F8CFBD66141FEE9261BD7C` |

## Independent finding retained

Review artifact:
`C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/student-gateway-c/reviews/p1-attachment-final-2026-09-08.md`
(SHA-256 `67F540C9BBD485E618D3DE590AB8A96590B4A0A4E8CD626B0B515874AA387C7A`).
It records MEDIUM: `resolveRequestNotification()` calls the public-shaped
`toDetail(ExcuseTicket)`, whose non-empty stored list silently replaces the
complete embedded descriptor list. Reproduction is embedded `[A,B]`, query
result `[A]`; current code returns A and allows downstream queueing without B.

## Scope guard

Only the two target source/test paths may receive product/test edits. Evidence
may be added below `.agent/student-requests-authority-c/`. Imported transport,
accepted repair files, generated files and all other foreign rows remain
unchanged.

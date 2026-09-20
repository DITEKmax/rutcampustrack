# Shutdown checkpoint — attachment reconciliation correction R2

Дата: 2026-09-08. OWNER PAUSE принят до новых product/test edits, checks,
Gradle, runtime и review. Worktree:
`C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-notification-authority`.

## State

- HEAD: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached).
- `git status --porcelain=v1 --untracked-files=all`: exit `0`, 105 inherited
  dirty/untracked rows. Foreign imported work was preserved; no reset, cleanup,
  checkout, commit or main/shared-worktree write was performed.
- The only product/test target paths are still the imported untracked files
  below; their hashes equal the before snapshot, so product changes since
  resume: **none**.

| Path | Current SHA-256 |
| --- | --- |
| `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java` | `BD054202143BEB11265608080373BD874228C4057DB64ABC8F17A3C141B9DE1F` |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java` | `0591DAC19F2FD5BDB7C3E41C9F66106FD9D0799945F8CFBD66141FEE9261BD7C` |

## Mutation outcome

The requested correction was not applied. A pending evidence-only patch was
interrupted by the OWNER PAUSE; it created only
`.agent/student-requests-authority-c/correction-packet-2026-09-08.md` and did
not alter either product/test path. No follow-up mutation was attempted.

## Defect and historical evidence

The independent review finding remains open: notification resolution can return
stored `[A]` for embedded `[A,B]` and silently drop B. Existing historical
evidence remains context only: selected Java `20/20` PASS and Python `18/18`
PASS, with generated/hash guards recorded in the authority-c evidence. This
attachment correction is **UNTESTED**; no new checks were run.

## Scope/process guard

The current status is intentionally dirty because of imported/foreign work;
only the two named paths plus their evidence directories are in scope. No
Gradle, Java runtime, Python runtime, Docker, Testcontainers, Telegram, Rabbit,
Mongo, external port or listener was started or owned by this task. There are
therefore no task processes or listeners to close.

## Open handoff

1. Await root/parent explicit Gradle lease.
2. Apply only notification-specific inventory reconciliation and focused
   regression tests in the two named paths.
3. Run the exact focused Java selector from the correction packet, record
   command/environment/exit/XML evidence and updated hashes.
4. Request fresh independent Sol recheck of the stable correction diff.

No DONE/READY or runtime claim is made while OWNER PAUSE is active.

# Review correction 01 checks

Environment: Windows PowerShell, checkout
`C:\Users\maksd\.codex\worktrees\1456\rutcampustrack`, base revision
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. These are source-stage checks only.

| Check | Command/evidence | Exit | Result |
|---|---|---:|---|
| Five-path exact manifest | `Get-FileHash -Algorithm SHA256` plus `Get-Item.Length`; output is in `manifest.json` | 0 | Recorded |
| Auth13 preservation | Manifest hash guard over the 13 accepted contract rows; `auth13_count=13;mismatch=0` | 0 | Recorded |
| Producer time-source structure | Scoped PowerShell assertions: one snapshot call/command, `initialNow` only in the command, fresh liveness/expiry inputs, post-sign `returnNow` guard, same snapshot signer path | 0 | Recorded |
| Review test markers | Scoped assertions for delayed success, access/session expiry denial, signing-delay expiry denial, no signer, typed matrix, HTTP matrix, internal required-claim matrix, session-access required/missing/optional/semantic matrix, and no sleep | 0 | Recorded |
| Focused auth failure correction | Root run: 36 tests, 35 succeeded; one failure at `JwtTokenPurposeTest` line 144 because `Claims.getAudience()` is `Set<String>`; corrected to exact singleton `containsExactly(AUDIENCE)` | 0 | Correction recorded; root rerun required |
| Owned-source whitespace/NUL | Scoped scan over the five paths | 0 | Recorded |
| Git diff whitespace | `git diff --check --` over the five paths | 0 | Recorded |
| Git ownership/status | Path-scoped `git status --short`; only the expected orphan/shared and five producer paths are visible, with no evidence outside the assigned directory created by this leaf | 0 | Recorded |
| Gradle, Docker, Testcontainers, PostgreSQL, service runtime | Prohibited by the source-only packet; separate root lease is required | N/A | `HEAVY NOT RUN/NOT ACQUIRED` |

The Git status command emitted the pre-existing inaccessible global-ignore
warning. It did not alter files or change any check exit code. Static exit 0
is recorded as evidence; this package makes no runtime PASS claim.

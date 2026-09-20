# R3 checks

Revision stayed at `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached).
Environment: Windows PowerShell, worktree
`C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-notification-authority`.

| Check | Command/result | Exit | Evidence |
| --- | --- | ---: | --- |
| Before status/hash snapshot | `git status --porcelain=v1 --untracked-files=all`; `Get-FileHash` for the two target paths | 0 | `before-hashes.txt`; 106 rows, expected before SHA values |
| Target whitespace scan | PowerShell `File.ReadAllLines` scan for `[ \\t]+$` on the two target paths | 0 | `TARGET_TRAILING_WHITESPACE=NONE` |
| Structural static guard | PowerShell checks for notification helper, reconciliation, stored projection, public fallback and three focused tests | 0 | All seven booleans `True` |
| After status/hash snapshot | `git status --porcelain=v1 --untracked-files=all`; `Get-FileHash` for the two target paths | 0 | `after-hashes.txt`; 113 rows = 106 baseline + 7 r3 evidence rows |
| Repository-wide `git diff --check` | `git diff --check` | 2 | Foreign pre-existing tracked/imported rows report trailing whitespace; target paths are untracked and are covered by the target scanner. No unrelated warning changed code. |
| Focused Gradle selector | Exact selector in packet | NOT RUN | Requires explicit root GO; no claim made |

No Docker/Testcontainers/Telegram/Mongo/Rabbit/product runtime was started.

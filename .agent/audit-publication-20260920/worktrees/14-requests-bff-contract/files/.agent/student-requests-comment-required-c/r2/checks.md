# r2 checks

Environment: Windows PowerShell in
C:\Users\maksd\.codex\worktrees\e31c\rutcampustrack\.agent\worktrees\requests-bff-contract;
revision d3c31acb8cce53791a4981e5858a37d44fdc9a0e; checkout detached and
pre-dirty. Checks are static/hash/diff/scope only.

| Check | Command / evidence | Exit |
|---|---|---:|
| Frozen precondition | Guarded Get-FileHash SHA256 for target and sibling P1 source, plus zero-declaration and boolean-ReasonOption assertions. | 0 |
| Exact source transplant | Guarded UTF-8 text comparison: source with its one exact two-field ReasonOption anchor replaced by the target boolean form equals target; expected and actual post SHA C5FF83BB1ABA886BA89DA94CD0A2832D7F88E566DBD37B3E2E820A2AD4C76EDD. | 0 |
| Record shape | Guarded assertions found exactly one NotificationResolution, exact four fields/order/types, two canonical Javadoc lines, and boolean ReasonOption. | 0 |
| Whitespace | Guarded Select-String [ \t]+$ found zero trailing-whitespace matches. | 0 |
| Source comparison | git diff --no-index --unified=3 source target returned exit 1 as expected for the one intentional ReasonOption source/target difference; no notification block diff remained. | 1 (expected) |
| Scope guard | Post-bundle porcelain status guard checks the target and seven expected r2 evidence files only; foreign pre-dirty paths are preserved. | 0 |

No Gradle, Java compile, test, Docker, generation or product runtime command was
run by this leaf.
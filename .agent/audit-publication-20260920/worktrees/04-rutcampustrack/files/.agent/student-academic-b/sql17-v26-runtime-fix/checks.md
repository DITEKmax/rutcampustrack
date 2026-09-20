# Static checks

Environment: Windows PowerShell, worktree
`C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack`.
Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`, exit code `0` from
`git rev-parse HEAD`.

| Check | Command / evidence | Exit |
| --- | --- | ---: |
| Raw posthashes and bytes | PowerShell `Get-FileHash -Algorithm SHA256` plus `Get-Item.Length` for V26 and Academic IT; output recorded in `manifest.json` | 0 |
| Failure XML preservation | PowerShell `Get-FileHash -Algorithm SHA256` plus `Get-Item.Length`; XML SHA `D474F46C3F2976D4119BB590B3FB75C19C9E4AE9EA1CCB0D9264651F56044C2C`, bytes `133618` | 0 |
| pgcrypto/digest selectors | `rg -n "CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;|public\\.digest\\(|digest\\(" <V26> <AcademicIT>`; lines V26 5/152 and IT 99/183/191 | 0 |
| Unqualified digest guard | PowerShell wrapper around `rg -n -P "(?<!public\\.)digest\\(" <V26> <AcademicIT>`; no matches, wrapper exit `0` | 0 |
| Barrier/assertion selectors | `rg -n` for both race call modes, exact 2/1 commits, expected failure, trigger/function names, advisory calls and `pg_locks` | 0 |
| Changed-method readback | PowerShell `Get-Content` readback of V26 hash constraint and Academic hash expressions, pre-fix test, corrected test, trigger install, race helper and barrier helpers | 0 |
| Java delimiter balance | PowerShell character count: Academic IT `parens=893/893`, `braces=90/90` | 0 |
| Trailing whitespace | PowerShell wrapper around `rg -n "[ \\t]+$"` over both products and this evidence directory; no matches | 0 |
| Scoped whitespace diff | `git diff --check -- <V26> <AcademicIT> .agent/student-academic-b/sql17-v26-runtime-fix` | 0 |
| Evidence manifest syntax | PowerShell `Get-Content manifest.json | ConvertFrom-Json` with required path/hash/status fields | 0 |
| Second runtime evidence readback | PowerShell `Get-Content runtime-fail-2-2026-09-10.md`; session `85479`, XML SHA/bytes and SQLSTATE `42702` markers present | 0 |
| Exact-four source preservation | Read-only PowerShell SHA256/byte readback for V17, Schedule IT, V26 and Academic IT; values are recorded in `runtime-fail-2-2026-09-10.md` | 0 |
| Updated stage manifest | PowerShell `Get-Content manifest.json | ConvertFrom-Json`; latest session `85479`, status `FAILED_ACADEMIC_LEASE`, scope `RELEASED` | 0 |
| Evidence whitespace diff | Scoped `git diff --check` after the evidence-only update | 0 |
| Exact-four source-ready guard | Fail-closed PowerShell SHA256/byte comparison for V17, V26, Schedule IT and Academic IT against the root-provided values | 0 |
| V26 alias source readback | `rg -n -C 12 "SELECT asset\\.\\*|FROM campus_map_asset AS asset|asset\\.id|asset\\.plan_version_id|asset\\.format" <V26>` | 0 |
| Immutable readiness artifact readback | PowerShell `Get-Content source-ready-2-2026-09-10.md`; alias hunk, exact-four table, prior XML evidence and handoff status present | 0 |

No new Gradle, Java compilation, Docker, Testcontainers, PostgreSQL, migration,
staging, commit, reset or clean command was run in this evidence-only
follow-up. The recorded session `85479` was run before this follow-up and its
failure output is preserved without rerun.

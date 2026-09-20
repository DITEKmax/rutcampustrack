# H95 escalated differential preflight

- Purpose: isolate the sandbox classpath boundary observed in H95-01/H95-02/H95-03 using the same exact date-unit selector under the established H94 host context.
- Root authorization: legitimate `require_escalated` exact command after recorded sandbox dependency/classpath failure; this does not authorize ACL, dependency, buildscript, cache, or source workarounds.
- Captured: `2026-09-20T17:25:20.6778976+03:00`.
- Worktree: `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-l5b-recurring-writer`
- Branch/HEAD: `codex/l5b-recurring-writer-20260920` / `3d4115f3a4c4ddba473689e27ac1a0efb519a202`
- Governing implementation SHA-256: `58540BB77D1DDB03F22740996DC25675287A7FED01E577D8724347C07FD0BDFC`
- H95 packet SHA-256: `1FC6ACF6444B574E702352BFEC777D4230D005A95F63AED2ABE256C99D946BFB`
- Environment: Java 21 context, PowerShell `login=false`, no secrets, one process/worker; escalation changes only filesystem/cache host, not command arguments.
- Command (exact): `.\gradlew.bat :services:schedule-service:schedule-app:test --tests *RecurringDateCalculatorTest --continue --no-daemon --no-parallel --max-workers=1 --no-problems-report`
- Raw source inventory: identical to the immediately preceding H95-03 preflight and reverified before execution; authoritative machine-readable `PATH|SHA256|BYTES` table is in `../h95-03/PREFLIGHT.md`. No source files changed after H95-03.
- H95-04 artifacts: `stdout.log`, `stderr.log`, `run.meta`; fresh XML is inspected only if the test task reaches execution.

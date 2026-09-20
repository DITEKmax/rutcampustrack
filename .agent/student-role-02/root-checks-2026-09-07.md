# Root checks — 2026-09-07

Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; working diffs uncommitted. Windows PowerShell, Node24.14.0. No product PASS.

- Shared-shell worktree: `frontends/node_modules/.bin/eslint.cmd mobile-core/src/test-adapter/fixture-transport.test.ts --max-warnings=0` (cwd frontends), exit1: line94 `@typescript-eslint/no-this-alias`. `git diff --exit-code 8002b9ea -- frontends/mobile-core/src/test-adapter/fixture-transport.test.ts`, exit0: file unchanged from baseline. Bounded repair assigned to API writer: preserve injected-fetch receiver assertion using mock invocation context, rerun test/lint. No lint suppression/config change.
- Actual MobileShell Vue probe by foundation writer reproduced absent optional Boolean prop overriding host keyboard signal. Correction and focused regression check pending acceptance. This is not covered by earlier pure policy-helper tests.
- Root browser opened fixture PWA `http://127.0.0.1:5175/`, live shared-shell worktree, viewport390x844. First screenshot after resize was stale; subsequent settled frame used. Today content renders, dock five entries, four disabled until feature integration. Extra semester details remains tracked design debt. Dock labels visibly serif after extraction; `.today-shell` owns Onest while new dock is its sibling. Font inheritance correction sent to foundation writer, pending verification. Browser fixture is not real-service or Telegram-host evidence.
- New Sol xhigh excuse-source consultant spawn failed with `agent thread limit reached`; no model substitution. Decision remains pending until a child slot frees. Current two writers continue independently.

No production resources changed. No main product file changed by these checks.

## Java build environment diagnosis

API writer reported javac missing BusinessMetrics/IdempotencyStore/ErrorResponse in unchanged shared modules. Root reproduced `:services:shared:shared-security:compileJava` exit1, including fresh single-use daemon/no-cache/max-workers1/rerun exit1. `inspect-classpath.gradle` diagnostic confirms correct existing shared-observability classes directory. Diagnostic logs live beside this file.

Decisive controlled probe `ClasspathProbe.java` imports BusinessMetrics. Working directory is homework-api worktree; Java21.0.10:

1. `javap -classpath services/shared/shared-observability/build/classes/java/main ru.rutcampustrack.shared.observability.BusinessMetrics`: exit0.
2. `javac -cp services/shared/shared-observability/build/classes/java/main -d ../../../student-role-02 ../../../student-role-02/ClasspathProbe.java`: exit0.
3. Same javac with resolved absolute classpath: sandbox exit1, package not found. Forward slashes also exit1.
4. Exact absolute-path javac command with `require_escalated`: exit0.

Conclusion: reproduced sandbox absolute-path class lookup restriction, not proven source defect. API writer instructed to run Gradle checks/export with required escalation and `--no-daemon` to avoid reusing sandbox daemon, and to finish Java-first export/generated types in its own lane. No Java/build/config correction needed based on this evidence. Previous failures remain failures; they must not be relabelled source baseline defects.

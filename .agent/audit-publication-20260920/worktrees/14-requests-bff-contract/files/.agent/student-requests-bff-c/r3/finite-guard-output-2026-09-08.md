# Finite guard evidence — 2026-09-08

Worktree: C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract
Revision: d3c31acb8cce53791a4981e5858a37d44fdc9a0e
Environment: Windows PowerShell, local detached checkout; no product process, Gradle lease, Docker, Testcontainers, network, or runtime listener.

## Command

& .\.agent\student-requests-bff-c\r3\finite-guard.ps1

## Final result

Exit code: 0

Verbatim stdout:

finite-guard=PASS
revision=d3c31acb8cce53791a4981e5858a37d44fdc9a0e
sourceManifest=C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-transport/.agent/student-role-02/diff.json sha256=4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4
acceptedRepairManifest=C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-isolation/.agent/student-requests-isolation-c/repair-manifest.json sha256=7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3
productPaths=82
acceptedRepairPaths=2
bffPaths=5
mismatches=0

## Check mapping

- Exact five-file manifest/hash check: PASS, exit 0 (included by the finite guard).
- Immutable source manifest hash: PASS, exit 0.
- Accepted repair manifest hash: PASS, exit 0.
- Finite source/import comparison: PASS, exit 0; 82 source product paths, exactly 2 accepted repairs, exactly 5 BFF paths, zero residual mismatches.
- Scoped ownership: r3 evidence files only; inherited product/test/foreign dirty paths were not modified.
- Focused Gradle selector: NOT RUN by the explicit NO_GRADLE handoff; root owns that lease.
- Product runtime, HTTP/RPC, Mongo, Docker/Testcontainers, and external services: N/A at this static manifest/guard stage.

## Correction note

A first local guard attempt returned exit 1 because the newly written evidence
manifest had one slash typo in the StudentRequestDetailJsonTest relative path.
The reproducible error was missing-bff:services\mobile-bff\mobile-bff-app\src\test\java\ru.rutcampustrack\mobilebff\contract\StudentRequestDetailJsonTest.java.
Only the r3 evidence manifest was corrected; no product or test byte changed.
The final guard above was then run once and returned exit 0.

## Limitations

This evidence proves hashes and finite imported-scope accounting at the recorded
revision. It does not prove compilation, unit behavior, HTTP/RPC behavior,
authorization at runtime, persistence, queues, or live service integration.
The shorter error-test SHA in the r2 checkpoint is a transcription typo; the
exact5 manifest uses the independently confirmed full 64-hex SHA ending ECA9.

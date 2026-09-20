# Requests H59 metrics configuration correction — 2026-09-19
## Goal
S3 bounded harness correction: allow real Spring Boot startup with runtime-only metrics diagnostics, preserving I1/I2 and production settings.
## Context/evidence
H57 trusted sixjar/PWA build PASS, H58 ValidateOnly PASS. Actual H59 run20260919-183652649-mnkcr-fz Academic exited1 during context initialization. Root captured evidence/h59-academic-filtered.log: management.endpoint.metrics.access and management.endpoint.metrics.enabled mutually exclusive. Root opened runner1618-1620 which sets both. Product source/build unchanged. Runtime cleanup must finish before source writer starts.
## Relevant scope
Attendance lead receives one replacement leaf slot. Fresh gpt-5.6-luna max developer fork none, sole writer existing C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runner.ps1 plus own h59-metrics-fix evidence subdirectory. Current runnerSHA67F6D0E471FCE5DC7835023D32BB5BD3D26EEB00426AD9AD7A7C2C62409FCA3D; baseHEAD73fd5f27ceb429ad0692b073189f8a527c550830 contains accepted WIP to preserve. No other writer.
## Required behavior
Remove obsolete MANAGEMENT_ENDPOINT_METRICS_ENABLED=true from runtime env, retain MANAGEMENT_ENDPOINT_METRICS_ACCESS=unrestricted and exposure health,info,metrics. Confirm bounded one-line functional diff against actual frozen runner; no other behavior change. Save before/after source hash, patch, root failure evidence reference and applicable parser checks. No Docker/Gradle invocation by author. Mark startup/runtime unverified until root real rerun.
## Constraints
Read C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A and CURRENT plus this contract before work. Terra NEVER; no children; preserve others; stop on owner STOP. Root owns shared docs/h57build/h59run evidence. Do not write into completed run folders or source-validation.json. Wait root explicit H59 RELEASE before editing.
## Existing patterns
Existing runtime-only env shared by Java containers and loopback actuator diagnostics. Modern access property explicitly requested by actual Spring error. No product security or endpoint changes. Existing accepted redaction and owned cleanup unchanged.
## Acceptance criteria
Exact diagnosed conflict removed without losing metrics needed for I2. AST PASS, clean bounded task diff and preserved existing WIP. Author release followed by fresh Sol high independent review full repaired harness/contract/evidence, no author transcript. Full runtime PASS only after new root lease.
## Verification
Record revision, commands/exits, actual environment, hashes and evidence. No dependency rebuild, network-proof rerun, giant test round or wording-only test needed for this single env fix. Reviewer must open critical originals, include all accepted source scope and check correction preserves I1/I2, cleanup and no product weakening.
## Do not
No product code, secrets, global config, build artifacts, old core harness edits; no test suppression, alternate framework, push/deploy/main merge or automatic heavy retry. Other Lessons writer is active in separate checkout; never revert its work.

# H44 bounded build execution — 2026-09-19

## Goal
Build the accepted exact58 integration revision, six executable jars and PWA. Risk S2. This is a verification stage, not I6 completion or deployment.

## Context/evidence
INTEGRATION-I6-BUILD.md and RESUME-2026-09-19.md remain applicable. Fresh FULL Sol FIX4 review closed all previous producer findings. Frozen producer SHA256 DB8A4B84E4D042F72F5E0912C2F270CA063AB4735228CB9299A45748DFAC7A83. Remaining FAIL2 concerns pure-checker fixture ownership and an evidence hash; those are assigned separately and the checker is not invoked by H44. Root opened current producer preflight, publication and command execution. H42 locked offline dependencies PASS. This scoped decision permits the reviewed producer build after H49 resource release while full I6 acceptance remains open.

## Relevant scope
Root is the sole build executor in C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-runtime-build at d7ec16572db325d944f1a1fbd3b4960b827d09c3. Root exclusively owns build-i6/h44-build/ output and orchestration-v2/evidence/h44-* capture. Access author owns only pure checker and other scoped evidence; it must not touch h44-build or producer. Lessons source correction remains in its separate worktree with no heavy lease.

## Required behavior
Verify producer hash, exact clean revision and absence of H44 destination before execution. Invoke normal producer without PublicationHarness, with process-local fault/harness markers absent. Use custom ManifestPath build-i6/h44-build/requests-build-manifest.v1.json and RunsRoot build-i6/h44-build/runs. Record actual identity and Java context. Six sequential bootJar commands and PWA build are defined in the frozen producer. Stop first failure and retain diagnostics; no product correction or retry under this packet.

## Constraints
RULES.md SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. One heavy queue, H49 release first. Scoped require_escalated execution follows proven named-pipe/cache context failures; no ACL/global configuration changes. No Docker launch, runtime, push, deploy, main merge, secret read or foreign cleanup.

## Existing patterns
Use frozen producer asynchronous stdout/stderr capture and canonical manifest, existing Gradle wrapper and locked npm workspace build. Preserve source worktree cleanliness and all existing artifacts. Never delete stale outputs to make a retry pass.

## Acceptance criteria
Actual exit0, seven successful build commands, clean before/after exact revision, six jars plus full PWA with recorded hashes, canonical manifest published once. Failure is not PASS and does not authorize subsequent runtime. Full I6 remains pending pure-checker correction and independent recheck.

## Verification
Read raw logs and run report, independently pin final manifest hash and verify source cleanliness. Consumer ValidateOnly and Requests full runtime require subsequent bounded leases. No fabricated provenance or reused historical manifest.

## Do not
Do not invoke the pure checker, PublicationHarness, or injected fault points. Do not change product code, the producer, dependency versions, or locks. Do not run concurrently with another Gradle/Docker lease.

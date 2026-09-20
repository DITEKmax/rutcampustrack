# H67 — corrected Nginx configuration validation
## Goal
S2 actual pinned nginx -t for reviewed H62 edge correction, before H68 full Requests.
## Context/evidence
H65 proved BOM; H66 proved missing landing upstream. Author runner9A2D703548110A142597B02196F5483F0F61D7329DFBFED313E38F1AFC0B4B9D pure PASS; fresh fullSol required before execution.
## Relevant scope
Root owns evidence/h67-* and one unique diagnostic container. Readonly fixture under C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/h62-edge-diagnosis/fixture. Source untouched.
## Required behavior
Verify runner hash and nginx/config hashes BC18F838FAE0D8AA9E99C2EC5D99C2669EFE8A9DFF47068BAB6B34985184BE20 / D0CCFAE3573BC6A233354288C47D1B0EB3601CE6EF9F7CB2887FA5690F0E1B17; mount both configs and existing ephemeral TLS files readonly. No key read/output. Pinned nginx sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10, network none, only api-gateway:127.0.0.1 host alias, no ports; entrypoint nginx -t.
## Constraints
Read RULES SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A and CURRENT. Execute only after fullSol PASS and exclusive root heavy lease. No pulls/builds/source edits/production operations or foreign cleanup.
## Existing patterns
docker create exact owned unique labels rct.runtime-owner=student-requests-edge-diagnostic and rct.runtime-run=<h67-guid>; record full ID before start --attach. Finally verify labels before rm-f exactID and verify absence. Separate raw stdout/stderr/actual exit from wrapper result.
## Acceptance criteria
nginx -t actual0 including TLS configuration; exact owned cleanup verified; config hashes unchanged. I1/I2 still open until H68.
## Verification
Record command/environment/start/end/exit/runner+config hashes and labels/ID/cleanup in h67-result.json. Root reads concrete nginx output. If failed, preserve evidence and return bounded defect, do not start H68.
## Do not
No unlabelled --rm, key material output, missing UI host aliases, weakened security caps, fake runtime PASS or concurrent Gradle/Docker.


Root explicit fixture-retention override reaffirmed during H68review: h62-edge-diagnosis/fixture/keys are generated task-owned testTLS files, KEEP until current harnesscorrection review/runtime logical gate completes. No reads/output. H71 uses separate run-ownedkeys withfinallycleanup. Retention is authorized, not cleanupdefect; later exactownedfixture cleanup remainsroot/author action.

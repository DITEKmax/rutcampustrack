# L5B: directed service identity dependency — 2026-09-20

Status: ACCEPTED dependency; checks PASS, fresh independent full review PASS0. This report is not full L5B or production readiness acceptance.

Base: 13e5fd1985b798bbb61bcc85969e6a74b1fc6837. Isolated implementation: .agent/worktrees/v2-l5b-service-identity, branch codex/l5b-service-identity-20260920. Full16 source freeze: evidence/h93-source-freeze.json SHA738036ECFE075EFD3BF356188EC2045084D59D9ABA3F324F973E118A527DD4C0. Accepted local commit: 3d4115f3a4c4ddba473689e27ac1a0efb519a202, parent13e5fd. Root verified exact16paths/rawhash0drift/trackedclean; only four own evidence files untracked. See evidence/l5b-service-identity-commit-verification.json.

Implemented: shared directed canonical32byte service credentials, fixed verified service principal, strict single metadata value, actual TLS transport admission, exact reserved full RPC policies, target/method/security-level scoped CallCredentials, client metadata sanitation, explicit receiver beans in Academic and Schedule. Existing user identity and close routes remain unchanged. No domain RPC, cap, ledger, migration or deploy.

Verification: H90 compile-test FAIL with no tests executed. H91 shared-test compilation FAIL; independent Academic+Schedule 42 tests PASS. Same-author test corrections preserved assertions. H92 shared22 PASS including6 real local TLS tests: positive both directions, plaintext server/client rejection, untrusted peer and wrong-host rejection. No skipped/failed tests in retained successful module results; source0drift. H91 to H92 changed only one shared TLS test signature, all other15 hashes unchanged. Actual commands, environments, timestamps, exit codes and fresh XML retained in evidence/h90-*, h91-*, h92-*. No full six-service rebuild or repeated Requests runtime needed for this isolated scope.

Initial author relative-path incident: nine own new files temporarily created in parent checkout, removed and recreated in assigned worktree; root checked exact parent scope empty. Pre-delete raw parity was not retained and is not claimed. See evidence/l5b-initial-path-incident.md.

Next dependency: assignment-backed recurring creation with canonical physical snapshots and shared insertion/locking rules. Enroll or safely guard update/regeneration/startup callers together; retain history. Bounded source map .agent/l5b-fence-enrollment/RESULT.md is static evidence only, no domain writer authorized in this batch. Then durable Academic PREPARE ledger, Schedule cap/retained reference validation, FINALIZE and idempotent recovery, full writer enrollment and public activation. One-off bell times/calendar authority remain owner decisions; they do not block recurring explicit-time work.

Overall previous estimate ~45% (40–50%, low confidence) is not recalculated by this narrow auth result. Broad production backlog remains in checkpoints/2026-09-20-safe-stop/REPORT.md. E and published/deployed state unchanged; no push/main merge/deploy. Trusted full runtime remains H88/H89 on13e5fd; new auth dependency has only its recorded focused evidence.

H92 independent full review found one Medium mandatory-test coverage gap: reverse credential was declared but not configured in its negative test. Same author corrected only this test fixture; assertions and production guard unchanged. H93 shared22 PASS exit0, source0drift; remaining15 files unchanged. New independent full review PASS0: evidence/h93-full-recheck.md. Prior review preserved verbatim in evidence/h92-full-review.md; its mutation reproduction was proposed, not executed.



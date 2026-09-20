# Repair-01 evidence

Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Frozen packet and adjacent review reports are unchanged; their hashes are recorded in `manifest.json`.

The repair closes three accepted boundary findings in the assigned pure session domain. `SessionSnapshot` now rejects a nonselectable active grant, `RevokeResult` requires a revoked snapshot for both durable and idempotent success, and the public policy path validates ownership because `select(Evaluation, role)` is private. `SessionLifecycleService` documents the accepted B1 trusted integration obligation: `replacementHash` is derived from the exact validated password, with no normalization or truncation; BCrypt stays outside pure domain.

Focused negatives cover suspended active snapshots, live revoke results with both `alreadyRevoked` values, foreign grants through the remaining public policy API, symbol-only special-category passwords, a lone low surrogate, current logout/idempotency, expired and revoked session commands, and authority failure without state or event mutation. Existing combining-mark and UTF-8 byte-edge coverage remains.

The one explicit ROOT GO command passed with exit code 0. JUnit XML is copied byte-identically under `evidence/junit/`: ActiveRolePolicyTest 4, PasswordPolicyTest 6, SessionLifecycleServiceTest 11; total 21 tests, 0 failures, 0 errors, 0 skipped. Source drift comparison against the pretest 16-file manifest passed with 0 mismatches.

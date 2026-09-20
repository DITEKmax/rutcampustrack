# Full-check failure index (preserved, out of scope)

Baseline check: the selected/full clean verification run captured before the
second-scan correction. These groups are retained for root review and are not
changed by this dependency task:

1. Five OpenAPI snapshot findings with semantic/format drift after the
   dependency/Springdoc update. Root's semantic comparison separates actual
   schema changes from line-ending and representation changes.
2. Mobile BFF gRPC runtime tests fail because the test environment binds the
   fixed `0.0.0.0:9090` port while production uses the client path.
3. Document-renderer JaCoCo remains below the existing `0.60` line threshold.
4. Shared-events coverage whitelist does not include existing
   `homework.due_reminder` and `homework.weekly_digest` entries.

No snapshot, test, coverage threshold, or business-source correction belongs to
this contract. Focused dependency/runtime checks and the corrected Trivy gate
are recorded separately in `runtime/checks.json`.

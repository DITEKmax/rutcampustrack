# Repair summary

R3 changes `late_checkin.decision.decision_by` to the required positive internal academic user id. The bot resolves the same academic record it authorizes; attendance rejects malformed commands and confirms group-scoped headman authority before pair coordination or writes. Web decisions retain their established authenticated request-context guard. Tests cover canonical audit actor, absent/invalid actor, foreign group, schema/consumer agreement, transactional Mongo state, cancellation/idempotency, and the R5 snapshot ordering repair.

R5 moves the specific pending-request eligibility branch before generic cooldown. A future retry now distinguishes `PENDING_CONFIRMATION` from ordinary `COOLDOWN`; the equality boundary remains eligible.

Diff: 18 scoped files, 384 additions and 32 deletions. It contains the two bot handlers and focused tests, event schema/consumer, late-checkin authorization and its focused unit/Mongo tests, snapshot ordering and its focused unit test, plus this repair packet. No generated contract, lockfile, configuration, PWA, or unrelated service file changed.

Limitations: no full unrelated suite or PWA checks were run. No production service was launched; the applicable runtime proof is the isolated Mongo/Testcontainers integration test.

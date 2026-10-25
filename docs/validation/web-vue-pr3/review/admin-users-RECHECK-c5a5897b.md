# Final narrow Q3 grant-window correction

**PASS — last P2 in RECHECK-716b6e84 closed. Combined reviewed Q3 harness source has no remaining confirmed P1/P2. No actual runtime result is claimed.**

Verified immutable0444 original716 SHA256 `716b6e844b39d25129111f5a9a9cdfc0923ce554f6ea3b0bec6b31caad95ad35` and current `/tmp/admin-users-unknown-review.cjs` SHA256 `c5a5897b76496df3c9c657bc78f7201ef653de9bcb8f97b7a1192c685ce267fc`. Removing exactly the one added line reproduces original716 byte-for-byte; saved diff agrees. `node --check` PASS.

New line94 checks `Date.now() < Date.parse(a.expiresAtUtc)` inside the first-new-FULL-grant branch before body/key capture, persistence or forwarding. This closes the expired-window grant path. Known original same-key result retrieval, original status reads, hold/handoff and mandatory canonical cleanup have no added expiry restriction and remain byte-identical, available after expiry. The five earlier causal corrections remain unchanged and accepted.

Owner consent remains valid; no new permission question is needed. Actual fresh MAIN8 runtime qualification/envelope, exact private admission and separate root technicalGO remain execution prerequisites. This pure check did not read private values or requalify the actual baseline.

Only read/pure hash/diff/syntax checks and this immutable external report; no harness/browser/API/login, DB/DML, build, container or CI execution, no product/repository edit. Explicit assignment remains GPT-6.1 Sol/high; runtime model telemetry is unavailable. STOP.

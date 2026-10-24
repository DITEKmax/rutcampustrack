# ADMIN narrow recheck — 3d0a30225

Verdict: **PASS**. All three P2 findings in `REVIEW-6ddcad986.md` are closed; no new confirmed P1/P2 found in this six-file correction delta.

Reviewed frozen original `3d0a30225cda7cee22f2652367390001d8ec96f8` (root integration `7fa0` as supplied), limited to the three causal fixes and their touched compatibility boundaries. No full ADMIN/backend baseline review was repeated.

## Causal closure

1. **ADMIN export command recovery:** the shared adapter adds an explicit opt-in, default false, for exactly ADMIN_USERS, ADMIN_GROUPS and ADMIN_GROUP_ATTENDANCE. Recovery still requires a nonempty captured scope. ExportControl supplies a kind-and-authority-specific scope, restores the pending frozen request, locks format changes and presents explicit recovery of the previous export. Unknown acceptance reuses the original requestId and selector; once jobId is known, recovery reads that job. Delivery or explicit terminal handling clears the pending intent before another export can be requested. Existing weekly default recovery remains intact, and other consumers do not automatically opt in.

2. **Resumed create receipt:** UsersPage checks its current owner after the resumed response, publishes a created user's returned login/password into the existing volatile `initialReceipt`, and acknowledges only after the enclosing task completes. Receipt data is not added to durable recovery references. A retired owner cannot publish the receipt or acknowledge it.

3. **Group rename followed by archive:** after acknowledged rename, the old selected object is retired and the group is resolved again by stable id. The returned authoritative object replaces selected and refreshes name/duration. If readback fails, no stale selected object remains available for Archive. Subsequent archive uses the confirmed group's current name and lifecycle.

## Independent bounded checks

Dependency-light probes loaded frozen source in memory, using installed TypeScript/Vue and dummy adapters/fetch/storage only. They confirmed:

- All three ADMIN kinds preserve the original selector/requestId across a new client instance, disclose no pending request to a different scope, and clear pending after delivery.
- Default opt-out leaves ADMIN recovery disabled; existing weekly recovery remains enabled with its previous scope behavior.
- A known-job ticket failure recovers with one GET and only one enqueue in total.
- The resumed create receipt is populated before ACK for USERS_VIEW/USERS_MANAGE without credential-disclosure authority.
- A retired source owner produces neither receipt nor ACK.
- Rename УИТ-311 → УИТ-312 rebinds selected to the authoritative object; the next archive uses the same stable id and УИТ-312.

Read the new causal/compatibility regression source. `git show --check 3d0a30225` passed. Author reports 29 targeted behavior/compatibility cases plus build, lint and types passing; those suites were not rerun here.

Read `/workspace/rct-pr3-runtime/admin-web/export-recovery.json`: it records identical enqueue identity/selector across lost acceptance, known-job recovery without fresh enqueue, and the original selector in the actual downloaded HTML. Read `create-recovery-ordinary.json`: it records an ordinary user18 create with lost ACK and explicit same-key resume yielding a volatile initial-access receipt under the existing seed session.

The LIMITED actor runtime scenario was blocked/unexecuted, as the evidence explicitly states. Its capability-specific closure above is an in-memory component/source check, not a LIMITED runtime PASS. Actual group rename/archive/restore was queued by root and is not claimed as executed by this reviewer. The earlier unrelated runtime gaps remain outside this narrow verdict.

No browser suite, build, Gradle, Docker, install, CI, server mutation, product source write or child agent was used. Requested model remains `gpt-6.1-sol/high`; runtime telemetry is unavailable and no fallback/telemetry claim is made.

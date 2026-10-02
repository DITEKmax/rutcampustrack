# ACK deltas after frozen EC143580 contract

Root independent protocol PASS received 2026-10-02. Root ACK boundaries included direct attendance.marked sends and tracked decision replies in staging.

- New tracker entries include canonical user_id. Legacy unknown owner entries remain unresolved with bounded count diagnostic, no delivery/re-attribution/removal, existing tracker TTL unchanged; identified tasks proceed. This is an explicit unrecoverable legacy identity limitation.
- claim(None) now checks completed vs busy. Only completed ACK; processing delayed nack/requeue, existing CAS/TTL preserved. Failed release still retains event for replay after lease expiry.
- MUTE_FOR durationSeconds exactly86400/604800 accepted by root. Server computes first until in receipt transaction; normalized fingerprint includes duration; same callback replay returns original snapshot without extending mute. SET_MUTE remains absolute.
- Shared group/headman and normal personal WS frames remain data invalidation, not external notifications; canonical authority is read before frames/enqueue/commit, external WebPush uses admitted snapshot. Existing reminder personal eligibility is preserved. Durable history remains independent of mute.
- Actual baseline already wires Notification GRPC_SECRET dev/prod. Only absent dev Bot reference added; no existing credential value changed. Dedicated Bot→Notification token startup/Compose/examples/validator changes only, no generated secrets.

Scope S3: one author worktree; no children, no main/shared status/ADR/recovery modifications. Product diff locally frozen for source review; tests remain subsequent scoped delta. Bot AST syntax and git diff --check exit0. Heavy/runtime checks pending root lease. No Mongo/Redis/provider/production execution claimed; in-memory queued task crash and Academic snapshot race remain, no provider exactly-once guarantee. Java bounded retry exhausts to existing durable DLQ; automatic eventual delivery needs manual replay within existing retention.

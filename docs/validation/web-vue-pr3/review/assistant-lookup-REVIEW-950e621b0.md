# Backend assistant CAS and group-options review

Verdict: **PASS**. No confirmed P1/P2 found in the bounded frozen range `b32d47b0e..950e621b074affdda7063b343cf3c6f3a079279b` (commits `072486ca4` and `950e621b0`, ten changed files).

## Assistant permission CAS

- `AssistantService.updatePermissions` keeps the existing current-headman/own-group admission, locks the group and rechecks the durable active headman grant, then obtains the assistant `PESSIMISTIC_WRITE` lock. Its transactional method compares the complete observed rights set only after those locks. Comparison ignores ordering/case representation; stale snapshots throw 409 `ASSISTANT_PERMISSIONS_CONFLICT` before assistant state or notification publication is changed.
- Conditional empty replacement uses the same private revoke implementation as DELETE: inactive state and revocation timestamp are stored, historical permissions are retained, and the effective after-rights in the personal event are empty. Inactive records cannot be reactivated through this update path. Current effective permission lookup filters active records.
- The new branch preserves the existing transaction. Domain-event persistence uses the existing BEFORE_COMMIT outbox listener, so its failure propagates within the transaction. No new independent transaction, notification side effect before CAS, or response ACK before service completion was introduced.
- Required `permissions` and nonnull list elements are validated through the existing `@Valid` HTTP contract; nullable/missing `expectedPermissions` preserves unconditional legacy replacement. Legacy empty replacement remains invalid; the old one-list Java constructor and idempotent DELETE path remain. Unknown enum values becoming null are rejected by element validation. Client input contains no writable active/revoked/actor/group fields.
- Inspected the named consumer deltas `18f800412` and `ba3853fd0` for contract compatibility: the shared adapter sends the confirmed snapshot, permits empty replacement only with a snapshot, and accepts an inactive response; mobile retains that confirmed before-set. This does not constitute another complete consumer UI review.

## Names-only group lookup

- The permission union is confined to `GroupOptionsController.options`: active ADMIN capability admission accepts exactly `USERS_MANAGE`, `GROUPS_VIEW`, or `USERS_VIEW`. The existing `RoleCheckAspect` uses authoritative admitted admin access. No group registry/profile permission annotation or endpoint is broadened in the delta.
- Both count and rows use the identical bound `(? OR archived_at IS NULL)` predicate and escaped bound search pattern. Existing page/size/offset bounds and deterministic `ORDER BY name,id` remain. No user-controlled SQL is interpolated.
- HTTP `includeArchived` defaults to false; the legacy three-argument controller method delegates with false. The response exposes only id, name and the additive archive flag. Inactive, non-archived drafts remain in the default lookup as before. The old two-argument Item constructor remains source-compatible.

## Independent checks and evidence

Read frozen source, the two summary/check manifests, changed test source and generated TypeScript diff. A lightweight frozen OpenAPI JSON assertion confirmed required `permissions` without the old nonempty constraint, optional `expectedPermissions` with matching enum, 409 ErrorResponse, optional archive=false and an exact three-field item with required `archived`. `git show --check` passed for both commits.

Independently parsed preserved XML:

- `/tmp/assistant-cas-run1-xml/academic-integration/TEST-ru.rutcampustrack.academic.assistant.AssistantPermissionsCasIT.xml`: 3 tests, 0 failures, 0 errors. The source exercises real HTTP/PostgreSQL lock contention, a winning right removal versus stale addition, final stored rights and exactly one added outbox fact; stale final-right removal; matching revoke lifecycle; legacy PATCH/empty/DELETE; null/unknown enums and ordinary-student denial without state/fact changes.
- `/tmp/group-options-run1-xml/academic-integration/TEST-ru.rutcampustrack.academic.group.GroupOptionsHttpIT.xml`: 2 tests, 0 failures, 0 errors. The source checks USERS_VIEW-only admission, default/archived paging and counts, exact safe fields, unrelated-capability ADMIN and STUDENT denial, and invalid page size.
- Final CAS metadata snapshot and lookup snapshot XML: one test each, zero failures/errors.

These are inspected author runtime results, not fresh reviewer integration executions. No Gradle, build, Docker, install, browser suite, CI, server mutation, product edit, or child agent was used. Existing session/read-only admission and identity-operation lifecycle were inspected where relevant but not revalidated with a new broader suite. Artifact/stand rollout and combined frontend runtime acceptance remain root-owned and are not claimed here. Requested model: `gpt-6.1-sol/high`; runtime model telemetry is unavailable and no fallback/telemetry claim is made.

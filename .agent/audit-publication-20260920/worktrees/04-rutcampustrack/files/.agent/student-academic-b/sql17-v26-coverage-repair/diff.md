# Scoped diff — SQL17/V26 exact coverage repair

The shared checkout contains unrelated dirty and untracked work; it was
preserved. This leaf wrote only the two assigned IT files and this evidence
directory.

## StudentOccurrenceMigrationIT.java

- Added `SQLException` import and the exact V17 snapshot mismatch marker.
- Expanded recurring cross-origin coverage with a successful `templateA`
  control row, a `cancelled` cross-template INSERT, and a cross-template origin
  UPDATE. Each failure requires nested SQLSTATE `P0001` and the exact trigger
  message, then checks row count, origin ID and status.
- Expanded one-off cross-origin coverage with the analogous `oneOffA` control
  row, `cancelled` cross-one-off INSERT, and cross-one-off origin UPDATE.
- Added focused `SqlFailure`/`nestedSqlFailure`/`assertExactSqlFailure` helpers;
  unrelated broad assertions remain unchanged.

## StudentFoundationMigrationIT.java

- Added `List` import and exact V26 trigger message constants.
- Added READY width mismatch coverage with exact state/message and unchanged
  plan-format count.
- Added post-publication new `SVG ABSENT` format INSERT coverage with exact
  state/message and unchanged format/asset graph and existing format/asset
  metadata.
- Added missing-intent and mismatched-day demand-dedupe cases using `OWNER_B`
  and a new UUID, with exact state/message, no attempted dedupe row, and full
  daily aggregate snapshot invariants. Existing exactly-once assertions remain.
- Added focused exact nested SQL helper while preserving the existing broad
  `assertSqlFailure` helper.

## Excluded

V17/V26 SQL, production Java, proto, repository, configs, build files,
FlywayMigrationIT, runtime artifacts, shared status and all unrelated dirty
paths were not edited by this leaf.

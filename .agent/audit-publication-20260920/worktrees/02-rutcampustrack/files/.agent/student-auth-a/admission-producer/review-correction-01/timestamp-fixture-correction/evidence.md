# Evidence

## Recorded defect

The immutable prior PostgreSQL run in
`../runtime-final/failure-05-postgres-it.md` reported 6 tests, 5 passed and 1
failure. `httpTypedFailuresMapToProblemDetailsAndNoStore()` expected 403 for a
suspended grant and observed 503. The immutable XML evidence is SHA-256
`E6942C019856054243E3CF1064DD5BDC9AD17AAD514A62CD5A6A70D4B1FF64C2`, 12590
bytes.

The bounded reproduction is the multi-fixture setup: method `now` is captured
once, while `seedUser` later persists grant timestamps from its own truncated
`Instant.now()`. A later fixture can therefore have `created_at` after the
shared `now.plusSeconds(1)`. `RoleGrant` rejects that ordering and the real JDBC
transaction exposes it as the 503 authority failure.

## Correction evidence

The four former inline grant updates now call the helper at source lines 139,
168, 279 and 284. The helper is at lines 460–478, resolves `RoleGrant` through
`grant(...)` at lines 480–485, writes the lower-case status at line 465, derives
the safe instant at line 462, and runs the direct boolean SQL invariant query at
lines 469–473. `grantId(...)` delegates to the exact lookup at lines 487–488.

Post-edit target: SHA-256
`FB13C6300FB7EDB776135BCE4C3FE7276E8FCB220411C75B277A9EAEB6621389`, 28120
bytes. Delta from the recorded precondition is +554 bytes.

No product/runtime source was changed. Runtime evidence for this leaf is
explicitly `NOT RUN / root-owned`; see `runtime-evidence.md`.

# Session-domain evidence

Revision `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` was the frozen baseline. The
packet and shared integration contract were verified before implementation:

- packet SHA-256 `6352E0D3A870C88CD9FE9796F860E9C98B5D9A32D0239FA48130D2286C29658F`, 6110 bytes;
- shared contract SHA-256 `47FC36F57AFCEEC489B5C95C6C169C2CBA9F53FEED2287CF6946B1CB150D807E`, 20188 bytes.

The implementation is limited to the reserved new session domain. `RoleGrant`,
`SessionState`, and `SessionSnapshot` validate positive identities/versions,
ownership, timestamps, duplicate role/grant membership, and exact active-grant
consistency. Terminal statuses are selectable read-only; suspended is denied.
`ActiveRolePolicy` applies STUDENT then TEACHER default precedence and keeps
ADMIN/HEADMAN-only sessions neutral.

`SessionStatePort` exposes only atomic create, snapshot, role selection, strict
refresh, current revoke, and all-session revoke operations. Its Javadocs specify
the user-lock/re-read obligations, expected-version CAS, fixed refresh expiry,
previous/unknown JTI outcomes, and durable event boundaries for the later SQL
adapter. `CredentialSessionTransactionPort` exposes one password transaction
that rechecks the expected hash and revokes all sessions before commit. No
get-then-set composition is used by `SessionLifecycleService`.

Focused tests cover foreign/duplicate grants, terminal read-only selection,
suspended denial, neutral bootstrap, stale create snapshots, role-switch
idempotency/version conflict, one-winner refresh with previous/unknown outcomes,
fixed expiry, unavailable active-grant clearing without fallback, logout-all,
password wrong-hash/no-partial-revoke, password authority failure, exact
snapshot consistency, and Unicode password boundaries (scalar count, Nd, P/S,
space/combining exclusions, 72/73 UTF-8 bytes, and unpaired surrogate).

The focused Gradle run passed 17 tests with 0 failures, errors, or skips. The
three JUnit XML files were copied byte-for-byte into `evidence/junit/`; source
and copy hashes/lengths are recorded in `manifest.json`.

The focused process ended successfully and no active build remains. No further
Gradle invocation was made. This leaf surface did not expose a callable
collaboration sender; coordination facts are preserved in `status.md`.

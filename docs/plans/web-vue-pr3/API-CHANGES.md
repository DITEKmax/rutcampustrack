# API changes — PR3

## API-001 — device theme preference mirror

Status: implemented and integrated; affected Auth/PostgreSQL/Gateway checks
PASS, source and narrow generated-contract review PASS; actual browser GET/PUT/
CAS and PWA application logout/account-change mirror PASS. Author source f92cf2ba5 +3c02a3241; root integration
04fc51b2d +98214275e. Production migration/deployment not performed.

Purpose: preserve device-level light/dark/system across logout/login and account
changes without linking the preference to an account or using fingerprinting.
Validated local preference remains available offline and takes precedence;
backend is a durable mirror, not an authentication method.

| Method/path | Request | Success |
|---|---|---|
| GET /api/auth/device-preferences | live bearer; optional opaque cookie | theme:null/light/dark/system, revision:canonical decimal string; creates/renews missing or expired context |
| PUT /api/auth/device-preferences | live bearer + established cookie; {theme,expectedRevision} | committed theme/revision after atomic CAS |

Internal Auth paths omit `/api`; existing Gateway routing is reused. Bootstrap
allowlists contain only these exact GET/PUT paths in both filters. Existing
admission rules remain: live bearer required; stale/revoked/terminal grants
follow the existing session/bootstrap protocol. Cookie alone grants nothing.

Errors use RFC9457 and no-store: validation400 INVALID_REQUEST;
409 DEVICE_CONTEXT_LOST / DEVICE_REVISION_CONFLICT;
503 DEVICE_PREFERENCES_UNAVAILABLE, including failed commit. Existing session
401/409/503 errors retain their meaning. Unknown PUT result must be read back;
clients do not blindly repeat a write. Expected revision is a canonical
nonnegative decimal string bounded by Long.MAX_VALUE.

The `__Host-rct_device` cookie is random32-byte base64url, Secure/HttpOnly/
SameSite=Strict/Path=/, without Domain; logout preserves it. Auth FlywayV15 adds
`auth_device_preferences` with selector SHA256, nullable theme, revision and
expiry, without account/session/IP/UA. REQUIRED transaction holds admission
locks through commit, then device lock; bounded expired-context cleanup reuses
the existing job pattern. No preference events or changes to Web Push.

Compatibility: additive endpoint/table/schema. Existing refresh cookie, CSRF,
logout, auth/session and PWA/TMA operations are preserved. PWA/TMA/web consume
the common frontend mirror; older clients can ignore the new API. The Java
contract remains the source for Auth OpenAPI3.1 and generated TypeScript;
nullable theme and explicit problem schemas are represented in generated files.

Validation: real PostgreSQL/Redis Auth HTTP tests for persistence/account jars,
cookie-without-bearer/revoked/stale/bootstrap, CAS, expiry, unavailable storage,
deferred commit failure, concurrent PUT versus logout; SameSite contract,
Auth/Gateway exact-path filters, OpenAPI snapshot and generation/schema checks.
Evidence `/tmp/device-api-summary-20261024.md`,
`/tmp/device-api-checks-20261024.json`; independent report
[immutable source review](../../validation/web-vue-pr3/review/device-api-REVIEW-f92cf2ba5.md)
and [metadata recheck](../../validation/web-vue-pr3/review/device-api-RECHECK-3c02a3241.md).
Browser evidence: `/workspace/rct-pr3-runtime/theme-api-smoke-20261024.log` and
`theme-api-immutable-smoke-20261024.log`; PWA owner evidence under
`/workspace/rct-worktrees/theme-foundation/.agent/evidence/pr3-theme/20261024/`.
HTTP Secure-cookie readback uses the browser page fetch; a separate APIRequestContext
is not equivalent to the application cookie behavior. No cookie flag was relaxed.

## API-002 — administrator credential generation/readback

Status: source52eb8520 integrated after independent backend PASS. New web
consumer is in progress; ACT-ADMIN-REGENERATE-PASSWORD not counted complete.
Real Auth/PostgreSQL HTTP6 + shared Headman regression4 + Authfilter9 + OpenAPI
snapshot/schema/types PASS. Production migration/deployment not performed.

Adds GET /api/auth/admin/users/{userId}/initial-password (audited permitted
readback/disclosure) and POST /api/auth/admin/users/{userId}/generated-password.
Existing password-reset-link route is unchanged. POST requires
{expectedCredentialsVersion:positive canonical decimal string}. Both return
{userId,credentialsVersion,state:GENERATED|USER_CHANGED|UNAVAILABLE,
origin:initial|headman_reset|admin_reset|null,password:string|null}, no-store;
only GENERATED discloses generated material. Own changed password is never read.

Live ADMIN + USER_CREDENTIALS_MANAGE and AuthAdminAccess rules are preserved:
FULL can target active ADMIN including self; LIMITED cannot target active ADMIN.
Missing404 CREDENTIAL_TARGET_NOT_FOUND, fenced/archived409 CREDENTIAL_TARGET_UNAVAILABLE,
stale version409 CREDENTIAL_VERSION_CONFLICT, invalid400 INVALID_REQUEST,
authority/commit503. RFC9457 codes normalize existing flat code and extras.code.
Identity/session locks remain held through commit; secure generation and one
shared mutation retain hash/CAS/reset-ticket invalidation/all-session revocation,
audit and outbox atomicity. No secret/hash in event/audit/log/error payload.

Unknown POST result must be read back before any further decision; never blindly
regenerate with a fresh version. Same version cannot rotate twice. Successful
self target revokes the actor too; UI must warn and show returned material only
in volatile memory. Unknown self result requires fresh permitted administrator
or existing recovery; an old bearer cannot read back after session revocation.

Compatibility: additive endpoint + Auth FlywayV16 enum check extension admin_reset;
existing endpoints and historical Academic migrations unchanged. Shared Headman
reader/mutation extracted; relevant lifecycle/rollback/concurrentCAS regressions
PASS. PWA/TMA manual origin decoder and caption now accept admin_reset (web owner
compatibility commits integrated); generated Auth types come from backend owner.
No new web operation is silently ported into mobile navigation.

[Independent review](../../validation/web-vue-pr3/review/admin-generation-REVIEW-52eb8520.md)
records source acceptance and UI/runtime limits. Original summary and checks:
`/tmp/admin-generated-password-summary-20261024.md`,
`/tmp/admin-generated-password-checks-20261024.json`. Final source-matched AuthJAR
SHA256 3f8d9d0e9ea3bd944b774c2a05246df9b3a35caa2d19aae8b8570aba68c5dc05;
stand rollout remains runtime-owner coordinated, not proof of web UI completion.

## API-003 — retained group and account status in user registry/export

Status: author f3c03bd81 +6289ab528 +b32d47b0e integrated, independent review
PASS including async selector correction. Source-matched immutable Auth/Academic
artifacts verified/copied; stand rollout13healthy and actual filtered UI/history/HTML export PASS.

Existing administrator user list and export selectors accept optional groupId;
existing status filter is forwarded by the shared client. Membership means the
current or retained STUDENT/HEADMAN grant, including archived accounts/group
links, rather than any group ever visited. Role/status/group refer to the same
grant; TEACHER/ADMIN role filtering retains its separate group-link semantics.
Invalid selectors retain existing400 behavior; granular USERS_VIEW/export rights
remain enforced. Omitted groupId preserves older query behavior and export hash
shape, including older queued jobs. Paged totals/rows and sync/async render reuse
the same selector. This enables retained composition history without inventing
an as-of dated-membership endpoint.

The async export lease compares complete normalized JSON trees. Canonical parsing
on both sides prevents a valid small Long ID from falsely mismatching IntNode;
changed/missing/extra selectors and changed array order still fail admission403.
Actor/session/version/kind/private lease/state/expiry checks are unchanged.

PWA/TMA: additive optional query and shared client forwarding; omitted selectors
keep previous behavior. No new web registry/navigation is ported to mobile.
Real Auth/Academic authority/selector/snapshot checks and async enqueue202→claim→
render200 with changed/missing selectors403 passed. Independent immutable reports:
[group selector review](../../validation/web-vue-pr3/review/group-filter-REVIEW-f3c03bd81.md),
[async correction recheck](../../validation/web-vue-pr3/review/group-filter-RECHECK-b32d47b0e.md).

## API-004 — conditional existing assistant permissions (in progress)

Source072486ca4 integrated; real HTTP3/snapshot/types/JAR PASS. Independent
review pending; not deployed. Contract: Existing PATCH assistant permissions
adds optional expectedPermissions set. It compares authoritative rights under
existing group/headman then assistant locks; mismatch409
ASSISTANT_PERMISSIONS_CONFLICT without mutation/outbox. Null/unknown enum400.
Existing nonempty {permissions} calls remain compatible. Conditional permissions[]
with expected snapshot invokes the existing revoke lifecycle, returns inactive200
and preserves historical permissions. Legacy empty PATCH remains400 and DELETE204
is unchanged. Effective rights require active assignment. Web and existing mobile
confirmation use frozen before snapshot, including removal of the last right;
new helper assignment behavior is unchanged. No new mobile feature/UI is added.

Targeted real PostgreSQL/HTTP concurrent stale add versus revoke, conflict rollback,
conditional last-right revoke and legacy/invalid/forbidden cases PASS3tests;
source/schema/independent review950 PASS; actual stand CAS/lookup rollout gate pending.

## API-005 — names-only group options lookup (source accepted, rollout pending)

Source950e621b0 and real HTTP2/snapshot/types/JAR PASS, independent combined
CAS/lookup review PASS. Reuse GET /api/academic/groups/options; allow USERS_VIEW in that endpoint's existing
permission union, without granting GROUPS_VIEW. Optional includeArchived=false
preserves old pickers. includeArchived=true returns retained archive entries;
additive archived:boolean accompanies existing id/name only. Count and rows use
the same predicate; existing Java constructor retained. No profiles/contacts/grants
are exposed. Shared manual adapter owned admin author; generated types backend.
Directory history/filter uses archive lookup, create-user picker keeps default.
PWA/TMA omitted flag stays active-only. No new mobile page or permission widening.


## API-006 — whole-scheme map final deletion (implementation in progress)

Canonical owner decision2026-10-02 already authorizes deleting all scheme versions
and PNG/SVG bytes with preview/password proof. Current format=null endpoint only
cleared a pointer; whole-chain inspection confirms there is no hidden file GC.
Approved compatible fix keeps existing routes/request/result/granular permissions,
Auth confirmation/catalog lock and durable receipts. All-format preview counts
and hashes the whole version/slot/asset graph, including orphan retained history;
old narrow digest cannot authorize wider deletion. Within one PostgreSQL transaction
it removes only owned versions/format rows/BYTEA assets, preserves floor/building/
usage inventory and receipts, and rolls back on failure. Internal additive high-water
version counter prevents reusing old metadata URLs after reupload. Exact replay
cannot delete fresh content. Selected-format availability branch is preserved.

PWA/TMA: existing DTO shapes remain; all-format deletion now implements the already
agreed meaning, preview totals become exact for the full graph. No new mobile page.
Real HTTP/PostgreSQL multi-version/equal-hash other-floor, stale/replay/reupload and
forced rollback cases plus independent review are required before acceptance.
No production migration/deletion; synthetic resources only. Web final-scheme
operation must send format=null, not substitute floor inventory deletion.

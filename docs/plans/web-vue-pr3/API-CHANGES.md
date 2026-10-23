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

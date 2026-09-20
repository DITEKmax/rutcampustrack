# Acceptance criteria

Source criteria addressed:

1. The frozen 13 Auth13 files remain byte-equal to the accepted manifest.
2. Admission accepts only strict session-bound access wire and performs one live `SessionStatePort.snapshot` per call.
3. The internal token and response are derived from one accepted snapshot, with exact identity fields and a capped whole-second expiry.
4. Revoked, stale, foreign, missing, expired, changed-grant, and non-selectable authority states fail with the typed contract codes.
5. A previously issued access claim whose authoritative matching grant is now `SUSPENDED`/non-selectable maps to `ROLE_NOT_SELECTABLE`.
6. Shared validation returns only the frozen nine-field `InternalJwtClaims` record and rejects purpose, audience, type, canonicality, semantic, and time violations.
7. The old caller-claim issuer controller/route is removed; service-secret guard failures remain distinct.
8. Admission success and typed error responses use `Cache-Control: no-store`; bearer material is redacted from DTO/exception text.

The source evidence below passes the applicable static criteria. Focused Gradle
tests, compile, and PostgreSQL runtime remain root-owned after the runtime lease.

# Purpose correction summary

Implemented the fresh compact Auth token-purpose contract in the assigned
scope. Access, refresh, and internal issuers now sign explicit `token_use`
claims. Access parsing enforces access purpose, positive numeric subject,
future expiration, and known `UserRole`; refresh parsing enforces refresh
purpose, positive numeric subject, future expiration, and nonblank `jti`.
Legacy `parseToken` is a strict access alias, and refresh extractors no longer
accept access tokens. The Auth filter uses strict access parsing and clears the
security context on invalid bearer input, preventing `ROLE_null`.

Verification: focused auth-app unit command exited `0`; 13 focused tests passed
with zero failures/errors; `git diff --check` exited `0`. Exact commands and
environment are recorded in `checks.json` and `runtime-evidence.md`.

Limitations: full Gateway/HTTP ingress, downstream token exchange, revocation
semantics, and security scanners remain OPEN for root's S3 integration packet.
No external product runtime, real keys, secrets, Redis, DB, network,
Testcontainers, migration, config/dependency edit, or commit was used.

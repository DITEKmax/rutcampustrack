**FAIL — STATIC ONLY.**

Confirmed finding:

- **HIGH — issuer/verifier contract mismatch disables every protected API.**
  - **Location:** `services/api-gateway/.../JwtAuthenticationFilter.java:149-152`; critical original `services/auth-service/.../JwtService.java:126-141`.
  - **Evidence:** Gateway requires exact `token_use=access`, while canonical `generateAccessToken` emits `sub`, `iss`, `aud`, `role`, `group_id`, `is_headman`, `iat`, and `exp`, but no `token_use`. Repository-wide search found the claim only in Gateway tests.
  - **Impact:** access tokens returned by login, OTP, TMA, and refresh are rejected with 401 on every protected Gateway route.
  - **Reproduction:** obtain an access token through `/api/auth/login`, then send it as Bearer to any protected route such as `/api/v1/student/requests`; `validateAccessClaims` observes a null `token_use` and returns 401 without downstream dispatch.

Repair contract:

- **Defect:** production issuer and Gateway verifier implement incompatible access-token schemas.
- **Evidence:** source lines above; existing Gateway tests mint synthetic tokens containing a claim that the real issuer never produces.
- **Correction:** add exact string claim `token_use=access` in `JwtService.generateAccessToken`; preserve Gateway’s fail-closed validation and refresh-token rejection.
- **Scope:** root must authorize a frozen scope addendum for `JwtService.java` plus focused auth/Gateway compatibility tests, because auth-service is outside the frozen 24 repair paths.
- **Verification:** generate a token with the real `JwtService`, parse and assert the exact claim, pass it through Gateway to a protected route, verify downstream dispatch and internal user attribute, retain negative refresh/missing/wrong-type cases, then perform a fresh independent recheck.

Manifest SHA-256 and all 24 repair-file hashes matched. The snapshot manifest also matched. I found no other confirmed HIGH/MEDIUM defect in the stable static slice.

Runtime remains open: image-backed `nginx -t`, real Nginx header overwrite and restart behavior, Gateway/Redis bucket isolation and 429 threshold, login HTTP replay, security scans, exact 24 MiB fixed/chunked behavior, and BFF/Attendance/Mongo/outbox no-write evidence.

For direct chunked streaming, `StreamingRequestSizeGatewayFilterFactory.java:84` invokes downstream before overflow is known, so a prefix may reach BFF. The stale zero-upstream-count wording in `runtime-readiness.md:34` and `evidence.md:35` must not be used as acceptance evidence; verification must prove external 413/no-store, multipart cancellation, early-response/error handling, and zero domain/Mongo/outbox writes.

После исправления обязательна независимая recheck затронутого issuer/Gateway контракта.

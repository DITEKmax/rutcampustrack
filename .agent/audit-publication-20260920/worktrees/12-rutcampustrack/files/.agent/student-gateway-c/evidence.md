# Gateway C evidence

## Scope and source state

- Risk: S3. Sole writer scope is the Gateway client-IP/auth/body/rate-limit
  filters, exact Requests route, prod/e2e Gateway and Nginx wiring, and focused
  Gateway tests.
- Base HEAD: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Accepted imports were frozen before implementation: the exact 42-path delta
  ending at `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`, followed by the exact
  45-file snapshot with manifest SHA-256
  `3E795CB3F0CB8E95944BCBF1DC623E197554FAC24C5F5D2F5BB65ADFDDEF1D6A`.
  Snapshot overlaps use snapshot-wins semantics; per-file equality is recorded
  in `import-evidence.md`.
- The current repair file list and SHA-256 values are frozen in
  `repair-manifest.json`. Imported and parallel foreign work remains in the
  shared tree and was preserved.

## Implemented contract delta

- Raw socket peer is authoritative unless it is an explicitly configured,
  literal trusted edge address. Forwarding identity accepts exactly one strict
  XFF literal, canonicalizes IPv4/IPv6/mapped IPv6, rejects chains/duplicates,
  and stores the canonical client and peer addresses in exchange attributes.
  Forwarded and X-Real-IP never select identity; the normalization order is
  `-300`.
- JWT access validation now requires exact issuer/audience, `token_use=access`,
  future expiration, a positive Java Long subject, and role
  `STUDENT|TEACHER|ADMIN`. The authenticated subject is an internal exchange
  attribute consumed by the user key resolver after identity headers are
  sanitized. The existing single login limiter remains unchanged.
- Login body extraction is ordered at `-90`, reads at most 4096 bytes once,
  replays exact bytes with a corrected content length, normalizes login using
  `Locale.ROOT`, and returns one 413 Problem Details/no-store response for
  known or chunked overflow without a downstream call.
- The exact `POST /api/v1/student/requests/excuse` route precedes the generic
  student route and uses `StreamingRequestSize=25165824` while retaining the
  12 MiB codec limit. The Requests route receives `no-store` from Gateway and
  the Nginx URI map.
- Prod/e2e Compose require explicit `GATEWAY_PRIVATE_SUBNET` and
  `GATEWAY_NGINX_IPV4`; Nginx assigns the static edge address and overwrites
  forwarding headers. The exact Requests location is `client_max_body_size
  24m`; global Nginx cap remains `2m`. Prod CORS includes `Idempotency-Key` and
  the existing CSP gets only the accepted `img-src blob:` addendum.

## Executed checks

`checks.json` contains every command, exit code, environment and limitation.
The focused pre-JWT-delta selector passed at exit code 0 with 7 XML suites,
47 tests, 0 failures, 0 errors and 0 skipped. The final JWT-only selector
passed at exit code 0 with 32 tests, 0 failures, 0 errors and 0 skipped; its
XML is `services/api-gateway/build/test-results/test/TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml`.
The 32 cases include signed wrong-type issuer, audience and subject fixtures,
missing claims, audience multiplicity, subject bounds, token use and role.

Both config-only checks passed with exit code 0:

```text
docker compose -f docker-compose.prod.yml config --no-interpolate --quiet
docker compose -f docker-compose.e2e.yml config --no-interpolate --quiet
```

The scoped `git diff --check` passed with exit code 0. Host Nginx is not
installed and the leaf Docker shell cannot access the daemon pipe, so the
image-backed `nginx -t`, Gateway → Redis → fake-upstream run, Nginx edge run,
and security scans were not executed.

## Closed check diagnostics

- AssertJ attribute assertions had an ambiguous overload; explicit Java String
  casts fixed the three owned test assertions.
- A bounded edit briefly emitted PowerShell `[string]` into Java; it was
  replaced with `(String)` and the focused selector passed.
- JJWT's builder rejects wrong-type standard claims before filter invocation;
  the parameterized negative cases now use a test-only raw RS256 JSON signer,
  preserving invalid claim types. Production validation was not weakened.

## Open acceptance gates

The direct filter tests cannot prove Netty framing, Nginx overwrite behavior,
Redis bucket isolation/429 timing, response header inheritance, or downstream
write absence. The integrated runtime packet remains open: real HTTP through
Nginx/Gateway/Redis/fake upstream, image-backed Nginx syntax checks, restart
checks, and the separate BFF → Attendance → Mongo/outbox 24 MiB no-write proof.
See `runtime-readiness.md` for the frozen matrix and bounded runtime resources.

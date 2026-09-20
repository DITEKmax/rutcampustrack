# Gateway C — frozen compact contract

## Goal

Устранить spoofing identity для rate limit через trusted raw peer, гарантировать
single dispatch для login body и добавить точный Requests ingress: два файла по
10 MiB, domain total 20 MiB и transport cap 25,165,824 bytes (24 MiB), с
`no-store` для всего Requests route.

Risk: S3. Исполнитель: свежий bounded implementation leaf C, `gpt-5.6-luna`,
effort `max`; детей не создавать. Base revision:
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

## Context and evidence

Root provided the frozen decision
`.agent/student-role-02/gateway-forwarding-decision-result.md`; it was read in
full. It records four confirmed defects: raw XFF rate-limit key, double login
subscription, lost per-user identity after header sanitization and the built-in
RequestSize chunked gap. The decision is binding implementation direction.

The accepted dependency import is two ordered operations: exact 42-path Git
delta `8002b9e...` → `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`, followed by the
exact 45-file snapshot described by
`.agent/student-role-02/dependency-combined-review-source/manifest.json`
(reported manifest SHA-256
`3E795CB3F0CB8E95944BCBF1DC623E197554FAC24C5F5D2F5BB65ADFDDEF1D6A`). Source
and destination bytes are checked per path; the snapshot source directory is
read-only.

Applicable sources read before implementation: root `AGENTS.md`,
`services/AGENTS.md`, `tests/AGENTS.md`, `docs/agent-workflow.md`, and the
`rct-source-resolution` and `rct-verification` skills. Current worktree was
clean at the frozen base before imports.

## Relevant scope and ownership

Owned Gateway/config/nginx paths:

- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/clientip/**`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfig.java`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/LoginBodyExtractionFilter.java`
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java`
- new `StudentRequestsNoStoreFilter.java` and
  `StreamingRequestSizeGatewayFilterFactory.java`
- `services/api-gateway/src/main/resources/application.yml` and
  `application-prod.yml`
- prod/e2e Compose Gateway env and nginx network/IPAM only; nginx configs
- focused Gateway tests in the corresponding packages.

`.agent/student-gateway-c/**` is owned evidence/status scope. Imported
out-of-scope paths remain accepted dependencies and are not modified after the
ordered import. No other worktree or writer is touched.

## Required behavior

1. `server.forward-headers-strategy: none`; raw socket peer is authoritative.
2. Trusted proxies are strict IP literals only; empty base/dev default; prod
   requires a nonempty valid list and fails startup otherwise.
3. Prod/e2e own explicit subnet and stable Nginx IPv4 from required environment
   variables; Gateway receives exactly that address.
4. Nginx overwrites XFF, Real-IP, proto, host, port and Forwarded (empty); no
   `$proxy_add_x_forwarded_for` on Gateway locations.
5. `TrustedClientIpFilter` order `-300` is the sole normalization owner and
   stores one canonical IP in an exchange attribute before later filters.
6. A trusted peer accepts one XFF header with one strict literal only; trim and
   canonicalize IPv4/IPv6/mapped IPv6. Reject chains, duplicate lines, empty,
   ports, brackets and zones. Invalid trusted input falls back to peer. An
   untrusted/null peer ignores all forwarding headers and uses peer/`unknown`.
   Never use Forwarded or X-Real-IP as identity.
7. Redis key shapes stay unchanged; authenticated user id is an internal
   exchange attribute consumed before legacy header fallback.
8. Preserve one login IP limiter; no second RequestRateLimiter, no composite
   restore or enablement.
9. Login extraction order `-90`: default empty before `flatMap`, exactly once,
   exact-byte replay, Content-Length removes Transfer-Encoding, Locale.ROOT
   stripping. >4096 known/chunked returns one 413 Problem Details/no-store,
   releases buffers and makes zero downstream calls.
10. Requests no-store filter order `-200` covers exact route and descendants.
11. Exact POST excuse route precedes generic mobile BFF and uses a custom
   streaming cap 25,165,824; known and chunked overflow release discarded
   buffers and return 413/no-store. Codec remains 12 MiB.
12. Nginx exact excuse location is `client_max_body_size 24m`; global remains
   2m. An http-level URI map adds no-store without hiding inherited security
   headers. Prod CORS allows `Idempotency-Key`.

Root-frozen JWT interface: signed token must have exact `token_use=access`,
issuer `rutcampustrack-auth`, audience `rutcampustrack`, future `exp`, positive
Java Long subject and exact role STUDENT/TEACHER/ADMIN. Missing, wrong-type or
wrong-value claims fail closed; refresh/internal tokens are never external
access. Internal issuer implementation remains preserved.

## Constraints

No Requests transport/backend/domain/BFF/proto/bot/auth-service/shell writes,
no dependency upgrades, broad proxy trust, composite limiter, codec increase,
deploy, production migration, secrets, real OTP/Telegram, or production run.
Do not invent production IPs. Runtime resources are reserved by root as
`18500–18539`, network/DB prefix `rct_student_gateway`; no launch until root
grants a lease. A streaming cap may expose a prefix; zero domain writes are an
integration invariant, not a filter-only claim.

## Existing patterns

Preserve filter orders PWA `-110`, JWT `-100`, Internal issuer `-50`, response
rate-limit `-40`, existing thresholds/429 Problem Details/Retry-After,
fail-open Redis policy, header sanitization, codec 12 MiB, route-before-generic
ordering, Nginx buffering/TLS/security headers. Final order:
client IP `-300` → Requests no-store `-200` → PWA `-110` → JWT `-100` → login
`-90` → internal issuer `-50` → rate-limit `-40`.

## Acceptance criteria

Rotating/spliced/multiple XFF from one untrusted peer never changes a Redis
bucket; two actual edge source IPs stay separate; malformed/missing trusted
input collapses to the peer bucket. IPv4, IPv6, mapped IPv6, null and malformed
forms are deterministic. Five accepted attempts and the sixth 429 preserve
threshold, Problem Details and Retry-After. Authenticated Requests resolves
`user:<id>` after legacy headers are stripped. Login valid/empty/malformed and
non-login dispatch once; oversize dispatches zero. Composite stays disabled.
Exact two × 10 MiB succeeds; known/chunked >24 MiB is external 413/no-store
with zero domain/Mongo/outbox writes. CORS preflight accepts Idempotency-Key;
auth/CORS/security headers survive. Nginx restart retains identity; invalid
prod trust config fails startup.

## Verification

Record revision, command, exit code, environment and evidence in
`.agent/student-gateway-c/checks.json`. Required checks: ordered import hash
verification; focused Gateway unit tests for parsing, resolution, headers,
user attribute, body replay and cleanup; Gateway/Redis/synthetic downstream
integration for spoofing/429/dispatch; isolated nginx edge/direct-peer cases;
`nginx -t` for both prod/e2e configs; Compose config validation without secret
expansion; restart/recreate check; runtime exact/oversize cases after lease.
No full Gradle battery. Runtime gaps must be reported honestly. Stable diff is
handed to fresh independent Sol review by root.

## Do not

Do not trust arbitrary headers, CIDR/DNS/private-range guesses, raw first/last
XFF, Forwarded or X-Real-IP; do not restore two sequential limiters, rely on
built-in RequestSize for chunked input, add location headers that suppress
security headers, claim fixture-only PASS, or alter out-of-scope imported files.

## Contract addenda

- 2026-09-08 root decision: the exact `POST /api/v1/student/requests/excuse`
  route preserves the inherited single per-user limiter (`replenishRate=1`,
  `burstCapacity=60`, `requestedTokens=12`, `userIdKeyResolver`) and adds the
  streaming transport cap before the generic mobile BFF route. The generic route
  remains unchanged. Forged identity headers without the authenticated exchange
  attribute must not select a user bucket, and an unauthorized upload must not
  reach the route.
- 2026-09-08 root decision: the existing CSP `img-src` may gain `blob:` for
  validated local object URLs required by the parallel Requests UI work. No
  other CSP source is widened; browser runtime verification remains outside this
  leaf's config-only evidence.
- 2026-09-08 source verification: Spring Cloud Gateway Server WebFlux 4.3.5
  metadata in the pinned local JAR confirms the active keys
  `spring.cloud.gateway.server.webflux.forwarded.enabled` and
  `spring.cloud.gateway.server.webflux.x-forwarded.enabled`, both disabled so
  `TrustedClientIpFilter` remains the sole normalizer. The same metadata records
  per-header append defaults; no automatic append is allowed here.

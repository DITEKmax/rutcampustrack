# Gateway C admission correction packet

## Goal

Make external and internal JWT NumericDate admission robust to JJWT 0.12.6
claim representation and preserve downstream error identity after live
admission. Risk remains S3. This is a bounded correction to the frozen Gateway
C contract.

## Context/evidence

JJWT 0.12.6 exposes raw `Claims.get("iat")` and `Claims.get("exp")` as
canonical `Long` values while typed getters expose `Date`. The prior Date-only
check rejected genuine signed tokens. `InternalJwtIssuerFilter` also applied a
catch-all `onErrorResume(RuntimeException.class)` after `chain.filter`, which
converted downstream failures into 503 authority errors.

## Relevant scope

`JwtAuthenticationFilter`, `InternalJwtIssuerFilter`, and the six current
admission-focused tests: `JwtAuthenticationFilterTest`,
`InternalJwtIssuerClientTest`, `InternalJwtIssuerFilterTest`,
`InternalIssuerClientPropertiesTest`, `InternalJwtIssuerIT`, and
`RedisRateLimiterConfigTest` where its authenticated-user boundary is asserted.
The exact Auth13 imports and stage evidence are listed in
`canonical-changed-paths.json`.

## Required behavior

Both validators receive the compact token, decode the original payload with
`ObjectMapper`/`JsonNode`, require integral JSON NumericDate `iat` and `exp`,
then use `Claims.getIssuedAt()`/`getExpiration()` for live `Instant` semantics.
Authority and validation failures are normalized before forwarding. The
downstream chain runs behind a private sentinel wrapper and is unwrapped after
authority handlers so the original downstream exception propagates unchanged.

## Constraints

Preserve the frozen route, identity, limiter, and response contracts. Do not
change Auth13 imported bytes, unrelated Gateway/config paths, runtime
resources, secrets, deployment, or production state.

## Existing patterns

Keep RS256 signature verification, exact issuer/audience/token-use checks,
typed claim bounds, no-store Problem Details, filter orders, and one per-request
live admission call. Tests use genuine signed compact tokens; invalid claim
types use a test-only raw RS256 signer.

## Acceptance criteria

Valid signed access/bootstrap/internal tokens with integral NumericDate claims
pass their existing admission path. Signed string and fractional `iat`/`exp`
claims fail closed. A downstream sentinel error reaches the caller with the
same exception identity and is not rewritten as 503. No old issuer/cache API or
legacy header authority remains.

## Verification

Static guards, JSON syntax, exact Auth13 SHA-256 destination checks, changed
path hashes, and scoped `git diff --check` are recorded with exit codes in the
stage evidence. Root runs the focused Gateway Gradle selector after this
correction and records the result before runtime/review.

## Do not

Do not treat a raw `Claims.get` Date cast as NumericDate validation, accept
string/fraction time claims, apply a broad error recovery after forwarding, or
claim unit/runtime PASS before the post-correction selector and required
runtime evidence.

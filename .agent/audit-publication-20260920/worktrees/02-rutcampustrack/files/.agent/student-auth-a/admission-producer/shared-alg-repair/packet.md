# Shared-security algorithm/header repair packet

Status: SOURCE_READY / RELEASE

Baseline revision: 8002b9ea4356b10779c5bb9a6d99746d32d78ae2
Risk: S3 (authentication boundary and frozen identity contract)
Assigned area: shared-security algorithm/header repair
Writer: shared-alg-repair
Environment: Windows checkout; source stage; no Gradle/Docker/Testcontainers lease.

## 1. Goal

Repair the frozen internal JWT consumer boundary after the independent shared-security
review. The consumer must accept only the permitted RS256 JOSE wire, reject raw JSON
duplicate ambiguity, and prove complete frozen-identity propagation through the filter.

## 2. Context / evidence

The immutable review verdict is copied to shared-review-verdict.md. It identified
missing RS256 allowlisting, insufficient JOSE/raw-JSON coverage, a full-record filter
assertion gap, and two findings outside the accepted root delta. The root adjudication
accepts the algorithm/header/coverage corrections and explicitly rejects the record
accessor rename and the zero-skew change.

The producer wire decision is: header fields are alg and kid, with optional typ=JWT;
kid is required and nonblank. The existing producer/test fixture signs RS256 and uses
kid=test-kid. The frozen record accessor remains isHeadman() in this slice.

## 3. Relevant scope

Only these four files are owned:

1. services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java
2. services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/InternalJwtValidatorTest.java
3. services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/DualModeUserContextFilterTest.java
4. services/shared/shared-security/src/testFixtures/java/ru/rutcampustrack/shared/security/InternalJwtTestFactory.java

The repository contains unrelated dirty Auth/Profile/Contracts work. InternalJwtClaims
is reserved/foreign for this repair and remains untouched.

## 4. Required behavior

- Restrict parser signatures to Jwts.SIG.RS256 and require the raw header alg=RS256.
- Require raw JOSE names alg and kid, allow only optional typ, require nonblank kid,
  and accept typ only when it is the plain JSON string JWT.
- Parse the signed header and payload with bounded, strict UTF-8/JSON handling and
  reject duplicate object keys before identity extraction.
- Preserve complete frozen tuple extraction: userId, sessionId, sessionVersion,
  rolesVersion, role, status, groupId, isHeadman, and readOnly.
- Extend observable unit coverage for alternate algorithms, JOSE/header variants,
  duplicate header/payload keys, strict claim types/ranges, semantic identities,
  and full filter propagation.
- Keep test signing helpers aligned with the producer wire, including kid=test-kid.

## 5. Constraints

This is a source-stage repair. Do not run Gradle, Docker, Testcontainers, product
runtime, staging, commit, reset, or deploy from this leaf. Do not modify Auth,
Gateway, downstream consumers, configuration, contracts, migrations, or the shared
record accessor. Preserve all dirty foreign changes.

## 6. Existing patterns

InternalJwtValidator already uses JJWT 0.12.6, PublicKeyProvider, InternalJwtProperties,
and InternalJwtException. InternalJwtTestFactory is the shared test signer. The filter
test captures InternalJwtClaims through applyInternalJwt and is the observable
propagation boundary.

## 7. Acceptance criteria

- RS384, RS512, and PS256 tokens signed by the same RSA key are rejected.
- Header missing/extra/wrong-type/duplicate fields are rejected; canonical optional
  typ=JWT is accepted.
- Duplicate JOSE and payload keys are rejected even when the raw signature verifies.
- Every frozen claim invariant and the complete filter tuple are covered by focused
  tests.
- Only the four listed files change in this worker area.
- No token value appears in evidence, log text, exceptions, or DTO output.

## 8. Verification

Leaf checks are static only: revision, scoped diff, diff --check, source hashes/byte
counts, and API/static inspection. Existing XML test artifacts are historical and do
not prove the post-repair source; runtime is N/A under the source-stage lease. Root
owns the focused Gradle run, downstream compile, runtime, and fresh independent Sol
recheck.

## 9. Do not

Do not rename isHeadman() to headman(), introduce an alias/default constructor, or
change configurable clock-skew semantics. Do not redesign the record, producer,
session authority, or downstream adapters. Do not treat WARN/ERROR output as a
product defect without reproduction and root decision.

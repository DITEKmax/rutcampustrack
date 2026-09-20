# Dependency check reconciliation contract

Dispatch: 2026-09-07; role: fresh bounded developer; model/effort: Luna/max; risk: S2.
Baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.
Checkout: `codex/student-role-02-dependency-checks`.

## 1. Goal

Reconcile the repaired dependency baseline's compatibility checks while preserving API
semantics, event/security boundaries, coverage floors, and the eight-JAR H/C gate.

## 2. Context/evidence

Frozen dependency input is the immutable root handoff at
`.agent/student-role-02/dependency-review-source/manifest.json` with SHA-256
`B4F2D0D344C0CBA851D4EEF6A2B1A36139CD7D63A5E0A99A11DD03B936EAF1EB`; 26 files
were imported and all 26 file hashes/byte counts matched. Root findings identify
OpenAPI snapshot drift, BFF test server binding, two homework schema whitelist gaps,
generated-source coverage filtering, and missing real push-library coverage.

## 3. Relevant scope

OpenAPI snapshots/helpers and mobile-core generated BFF types; BFF runtime test ports;
shared event whitelist plus real Academic homework producer contract tests; root JaCoCo
generated protobuf/gRPC source-provenance filtering; renderer converter tests; and
notification push compatibility test. Evidence/checks remain inside this worktree.

## 4. Required behavior

Regenerate snapshots from actual services with deterministic JSON and CRLF normalization
on both sides; accept only documented semantic deltas. Set test-only gRPC server port
dynamically while retaining signed boundaries. Validate serialized `homework.due_reminder`
and `homework.weekly_digest` envelopes from the actual producer. Exclude generated
protobuf/gRPC classes by generated source provenance, including nested classes, while
retaining handwritten gRPC implementations. Exercise converter success/error/timeout/
cleanup paths and actual Web Push encryption/signing/request/response against loopback.

## 5. Constraints

No production behavior changes, dependency repinning, skips/suppressions, floor changes,
wildcard handwritten gRPC exclusions, real notifications, secrets, or external writes.
Do not hand-edit snapshots or hide additional semantic deltas.

## 6. Existing patterns

Use Java-first OpenAPI export and existing generated-type script; reuse Academic
`AbstractAcademicEventIntegrationTest` and `EventSchemaValidator`; use task-owned
Testcontainers/ports; record checks per `rct-verification`.

## 7. Acceptance criteria

Frozen 26 inputs remain verified; affected snapshots/generated drift checks pass with
known deltas only; both homework schemas validate real serialized producer output; both
BFF HTTP→signed-gRPC tests use dynamic test ports; renderer handwritten coverage meets
the unchanged 60% floor with explicit class provenance; and push library compatibility
is proven without real push delivery. Security H/C evidence remains 1,141 packages in
8 JARs with 0 HIGH/0 CRITICAL (57 MEDIUM records remain visible).

## 8. Verification

Run focused repaired checks first, then applicable Gradle checks; capture revision,
command, exit code, Windows environment, and artifact paths in `checks.json`. Preserve
prior failing XML, inspect exact scope diff and `git diff --check`, report JaCoCo class
counters/source provenance, and hand off a stable diff for fresh independent Sol review.

## 9. Do not

Do not write main/other worktrees, create children, commit, claim full-role acceptance,
or use Terra without a recorded defect/complexity gate containing request,
reproduction, new evidence, correction, bounded scope, and root decision.

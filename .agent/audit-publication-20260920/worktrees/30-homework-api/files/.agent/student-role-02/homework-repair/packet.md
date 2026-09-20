# Homework API bounded repair packet

Date: 2026-09-07. Risk: S3. Assigned model: `gpt-5.6-luna`, effort `max`.
Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Worktree:
`codex/student-role-02-homework-api`. This leaf is the sole writer for the
bounded repair scope and creates no children.

## 1. Goal

Correct the four confirmed Homework BFF defects from the frozen review before
integration while preserving successful Homework, Today and check-in behavior.

## 2. Context / evidence

The accepted review `.agent/student-role-02/homework-api-review-1.md` records:
Long overflow reaching an unmapped `NumberFormatException`, scalar Boolean
coercion, active-semester `NOT_FOUND` mapping to 403, and missing
`Cache-Control: no-store` on Homework errors. The pre-fix servlet/local-gRPC
reproduction is recorded in `reproduction.md` with exit code 1 and observed
500/200/403/header gaps. The previous feature implementation, database
verification and review artifacts are already present in this worktree and are
preserved.

## 3. Relevant scope

Own only Mobile BFF Homework parsing, request deserialization, active-semester
mapping, response cache policy, Java-first response headers and focused
Homework HTTP/unit tests, plus the canonical OpenAPI export and generated
mobile-core OpenAPI types. Do not alter Academic domain/proto behavior or the
other student APIs.

## 4. Required behavior

- Overflowing Homework IDs return typed 400 before any Academic Homework RPC.
- Only JSON `true` and `false` deserialize for `completed`; number, string,
  null and missing values return typed 400 before mutation RPC.
- Homework GET/PUT success and error responses, including pre-auth 401, carry
  `Cache-Control: no-store`; Today, schedule and check-in cache/auth behavior
  stays unchanged.
- Homework-specific active-semester `NOT_FOUND` maps to 503
  `DEPENDENCY_UNAVAILABLE` and stops before Homework read/mutation RPCs;
  generic Today/schedule active-semester mapping remains unchanged.
- Java annotations remain the contract source; the snapshot is exported and
  generated mobile-core types are checked for drift.

## 5. Constraints

Preserve all pre-existing dirty files and worktree ownership. No global
ObjectMapper scalar policy, Academic/proto changes, frontend behavior,
dependencies, lockfiles, configs, migrations, deploys, data operations or Git
settings. No commits or main integration in this leaf.

## 6. Existing patterns

Use `MobileBffException` and `MobileProblemHandler` typed ProblemDetails,
`MobileIdentityFilter` signed JWT boundary, `MobileAcademicClient` gRPC status
mapping, Java-first `StudentApi` annotations, and existing Gradle/npm checks.
Apply no-store before authentication with a bounded Homework-only servlet
filter. Apply strict Boolean handling through an app-scoped Jackson mixin so
the contract module keeps its annotations-only dependency surface.

## 7. Acceptance criteria

The focused real-servlet/local-gRPC matrix passes overflow, scalar/missing
Boolean, true/false, active-semester dependency and no-store 200/400/401/403/
404/503 cases, with no downstream RPC for rejected input or missing semester.
Affected unit tests, OpenAPI update/no-update, generated TS drift, typecheck,
lint and neighboring check-in/auth tests pass. The final scope is ready for a
fresh independent Sol recheck.

## 8. Verification

Record every applicable command, exit code, revision, environment and evidence
in `checks.json`; retain the pre-fix reproduction in `reproduction.md`, the
runtime limitation in `evidence.md`, and the current repair-owned diff in
`diff.md`. Use Gradle `--no-daemon --no-problems-report` with the confirmed
escalated classpath gate. Build both backend boot JARs for the parent scan.

## 9. Do not

Do not redesign the completion contract, add timestamps or fields, touch the
new UI source decision, rerun unchanged Academic concurrency tests, normalize
the generated OpenAPI newline style, claim product/deployment/TMA coverage, or
escalate to Terra without a new recorded defect/complexity gate.

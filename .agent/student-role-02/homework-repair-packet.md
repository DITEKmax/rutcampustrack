# Homework API bounded repair — four confirmed review defects

07.09.2026, S3 boundary, four MEDIUM findings. Fresh Luna max developer; no Terra escalation needed. Baseline8002b9ea and current frozen homework-review-manifest.json. Wait until reviewer final before any write.

## Goal
Correct four independently confirmed Homework API error/input defects before integration, preserve all successful behavior and DB invariants.

## Context/evidence
Read main homework-review-packet.md, homework-review-findings.md and final review when saved. Root opened critical originals and accepted each bounded correction. Oversized ID currently NumberFormatException500; scalar values coerce into Boolean; Homework error responses omit no-store; missing active semester maps403. Existing PostgreSQL3/3 and HTTP5/5 plus snapshot4/4 PASS are limited evidence, not proof of these cases.

## Relevant scope
Sole writer existing `.agent/worktrees/student-role-02/homework-api`, previous implementers and reviewer finished. Own Homework portions of StudentQueryService, MobileAcademicClient, new type-scoped BFF Jackson support and request-scoped no-store handling, StudentApi contract response headers, StudentHomeworkHttpGrpcIT and focused tests, Java-exported OpenAPI and regenerated TS. If necessary adjust only affected homework query test mocks for method-specific semester lookup. No Academic domain/proto, frontend source behavior, configs/dependencies/lockfiles, other-role APIs or generic Today mappings. Main evidence root-owned; own worktree `.agent/student-role-02/homework-repair`. You are not alone, preserve all other files; no children.

## Required behavior
1. Homework ID greater than Long.MAX_VALUE ->400 INVALID_REQUEST before gRPC, positive valid IDs unchanged. Max+1/verylong regression.
2. Literal JSON true/false accepted; number1/stringtrue/null/missing rejected400 before RPC. Type/field-scoped strict deserialization in BFF; no global scalar-coercion switch. Contract module currently depends only on Jackson annotations, avoid new dependency solely for serializer implementation.
3. Homework GET/PUT success and all error paths, including pre-controller401, carry Cache-Control:no-store. Bounded URI/operation handling; preserve Today/checkin behavior and existing Retry-After. Declare error-response header via Java-first annotations and regenerate canonical OpenAPI/TS as needed; never hand edit generated content.
4. Homework-specific activeSemester preflight maps missing active semester to503 DEPENDENCY_UNAVAILABLE; generic Today/schedule mapping unchanged. Both GET/PUT tests with GetActiveSemester NOT_FOUND; no homework read/mutation RPC. Other error mappings preserved.

## Constraints
No unrelated cleanup/refactor, weakening tests, bypassing validation/auth or product source edits outside boundary. Keep record schema Boolean required, public interface consistent. Default full git diff --check detects CRLF in generated snapshot; root `git -c core.whitespace=cr-at-eol diff --check` exit0 proves newline-only issue. Record both honestly; no global Git config changes or unrelated normalization.

## Existing patterns
StudentApi/StudentApiModels Java-first, MobileProblemHandler typed400 and MobileIdentityFilter401, MobileGrpcAuth signed forwarding. Prefer BFF mixin/deserializer scoped only to HomeworkCompletionRequest and no-store applied before authentication for Homework paths. Implementation choice remains yours within accepted behavior.

## Acceptance criteria
New HTTP real-servlet/local-gRPC negative matrix for all four defects passes and proves no RPC for rejected command input; valid true/false200; headers200/400/401/403/404/503; existing Homework/Today/checkin focused tests unaffected. Canonical snapshot without update matches after exporter; generated TS drift/typecheck/lint remain green. No changes to DB concurrency implementation; don't rerun unchanged Academic suite without reason.

## Verification
Record pre-fix reproduction where practical, exact commands/exits/environment/diff hashes. Gradle requires escalation + --no-daemon due proven sandbox absolute classpath restriction; --no-problems-report allowed for reproduced report collision. Export via integrationTest OpenApiSnapshotIT -Popenapi.snapshot.update=true only if Java contract changes, then no-update check and npm generate:types / generate:types:check. Run targeted BFF HTTP and affected unit/error tests, typecheck/lint. Stop writer with stable final handoff for fresh Sol high recheck; no commits yet.

## Do not
No main integration, deploy, shared configs/dependencies, broad ObjectMapper/Git changes, repeat prior checks without relevance, changing reviewer text or claiming full role/browser/TMA PASS.

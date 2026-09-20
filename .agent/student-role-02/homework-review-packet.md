# Homework API independent review — staged

2026-09-07. S3. Fresh Sol high read-only reviewer; dispatch after verification writer confirms a stable diff. Baseline `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`, worktree `.agent/worktrees/student-role-02/homework-api`.

## Goal
Independently review the student Homework API vertical for PWA and TMA before integration. This is a bounded API acceptance, not whole-role acceptance.

## Context/evidence
Owner requires complete student role with real API, private reversible completion, Java-first contracts and preservation of existing Today/checkin. Final Homework feed frames: 4601:142, 4601:848562, 4601:848636, 4788:146, 4922:343; exact sources in main `.agent/student-role-02/design-context/`. Read worktree `.agent/student-role-02/homework-api/{packet.md,evidence.md,checks.json,diff.md}` and additional verification evidence once stable. Do not use the author's conversation or hidden reasoning.

## Relevant scope
Changed academic.proto, Academic homework domain/repository/gRPC identity boundary, BFF contract/controller/query/gRPC clients, exported OpenAPI, generated TS and typed StudentApi. Existing fixture transport test has a minimal lint correction. New focused tests plus real PostgreSQL concurrency and HTTP/local-gRPC integration tests are in scope. Root will supply the stable manifest at dispatch.

## Required behavior
GET `/api/v1/student/homework?from&to`: defaults from server Moscow date clamped to active semester through semester end; explicit dates must lie within the active semester. Response contains semester/from/to/serverNow/items, IDs strings, chronological order then incomplete/completed then lesson number/id. PUT `/{id}/completion` accepts required boolean desired state; repeated/concurrent same-state commands are safe. Mutation actor/group come from validated signed Internal JWT and current active DB student, never request actor IDs. Homework belongs to own group and active semester. Responses use no-store and mapped ProblemDetails.

## Constraints
Read-only repository/data/external state. Preserve owner changes. Existing shared-secret read RPC remains internal; assess whether BFF's trusted claims and current guards meet the new public boundary. Do not assume a test passing proves authz or concurrent correctness.

## Existing patterns
Existing StudentApi typed transport and Java → OpenAPI → generated TS; InternalJwtValidator and shared gRPC authentication. HomeworkCompletion unique (homework_id, student_id). Existing Today/checkin error mappings remain compatible.

## Acceptance criteria
No unclosed critical/high functional/authz/data findings. Check identity forwarding, forged/missing token, foreign scope, inactive student/semester, date and ID parsing, missing/null boolean, DB desired-state behavior, error/no-store responses, exported contract parity, and regression risk to Today/checkin.

## Verification
Open critical originals yourself and inspect the stable diff/tests. Return PASS or FAIL with severity, exact file:line, evidence, impact and reproduction for every actionable defect. Separate product defects from environment failures and questions. Root accepts review only after required test command exits and limitations are recorded.

## Do not
Do not edit files, spawn children, run shared Gradle concurrently, commit, integrate, deploy, or claim browser/real Telegram/whole-role PASS.

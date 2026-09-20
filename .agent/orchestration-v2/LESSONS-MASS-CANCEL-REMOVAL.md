# Remove obsolete public mass cancellation — S2 implementation contract

## Goal
Remove the public bulk lesson cancellation operation explicitly removed by the owner; keep individual cancellation unchanged.

## Context/evidence
Read RULES at C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md, SHA256 B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A, CURRENT and this packet. Frozen baseline ed9b63c9449b23fa3f5dbf2feb52cd8f9e9cfe83. Root personally opened owner decision docs/wireframes/headman/118-headman-lesson-management.md:23,179–181 and actual LessonApi, LessonController, LessonService. Source map .agent/lessons-mass-cancel-removal/RESULT.md. No new lifecycle design is implied.

## Relevant scope
One fresh Luna max developer, fork none, sole writer in NEW isolated worktree C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-mass-cancel-removal, branch codex/mass-cancel-removal-20260920, from exact baseline above. Create it with normal managed permissions; do not alter accepted union or dirty original. Eleven product/test/generated paths in RESULT.md are assigned. Own task evidence allowed. Read services/AGENTS.md, tests/AGENTS.md and frontends/AGENTS.md. Lead owns its separate .agent/lessons-mass-cancel-removal docs; root owns orchestration status.

## Required behavior
Remove public mapping/controller delegation, obsolete batch service method and unused dedicated DTOs. Remove corresponding checked-in OpenAPI route/schemas, regenerate only Schedule types for all three clients and remove web-panel aliases. Preserve individual cancel/restore, their audit/events, shared repository query used by gRPC, all database history and other contracts. Do not implement or remove unrelated lifecycle operations.

## Constraints
Terra NEVER; no children; preserve others; one writer. No push/deploy/main merge, global configuration, credentials or data deletion. No heavy Gradle/Docker until root lease; lightweight syntax/generation checks allowed on own source. Max3 project leaves; this author owns slot2. No extra scout or new framework. Runtime/report results are not whole-product readiness.

## Existing patterns
Use existing LessonApiIT auth/fixture conventions. Existing client generators call openapi-typescript 7.13 with immutable:false, astToString and the existing header. They generate four services, so use the same installed generator API in a task-local Schedule-only invocation to avoid unrelated generated changes; no dependency/lockfile/generator infrastructure changes. If dependencies are unavailable, report the exact missing prerequisite instead of fetching/upgrading. Checked-in Schedule OpenAPI edit must be narrowly scoped to the removed route and DTO schemas; unrelated serialization unchanged.

## Acceptance criteria
Authenticated calls cannot invoke the removed operation or change lesson state/audit/events. Replace obsolete success test with a meaningful removed-route regression; assert actual precise MVC missing-route status, not an auth failure. Keep individual cancellation regression coverage. Runtime OpenAPI excludes removed route/schemas; generated clients do not expose them. Eleven-path bounded diff, no shared repository query deletion, no new unresolved critical findings. All changes accepted together after applicable checks and one independent Sol high review.

## Verification
Author first completes related changes and lightweight targeted checks, returns frozen hashes/diff plus concrete test command. Root reserves one Schedule LessonApiIT/PostgreSQL run and relevant contract/type checks; no repeat of accepted 117 L5A tests. A runtime API contract assertion may share the existing integration test context. Record revision/command/exit/environment and unique evidence paths. Important review only after related corrections are batched; reviewer reads the entire scoped diff and original requirement, not author transcript.

## Do not
Do not touch database migrations, internal iteration used by other features, event/history semantics, accepted source snapshots, broad frontend UI, Figma, unrelated generated types or lockfiles. No artificial compatibility endpoint for a feature the owner removed. Report any additional live caller or genuine scope blocker before expanding the eleven-path contract.

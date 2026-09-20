# STUDENT-ROLE-02 — active root contract

2026-09-07. S3. Baseline main `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

## Goal
Implement the complete agreed student role in PWA and TMA, with shared mobile-core UI/domain/API and separate shell adapters. Deliver working controls, transitions and real-service scenarios against final Figma.

## Context/evidence
Owner confirmed final board `VgVjQYWILLG9AC7Eh12VMk / 4572:3062`. Root read metadata and design_context/screenshots for all 39 frames; exact reference text is in `design-context/`. Reads are non-atomic and not revision-pinned. Current owner instruction and later accepted decisions override older materials. Sol xhigh source resolution is accepted, not a product PASS. Preparation artifacts are historical evidence.

## Relevant scope
Root owns main evidence/status. Product writers use separate worktrees. shared_shell_foundation owns core shell/navigation/Today/index; homework_api owns academic homework/gRPC/BFF/OpenAPI and core API/generated types. Both start at the baseline. API and common component changes integrate before dependent UI/adapters. Each additional scenario receives a nine-section packet before implementation. Other roles, push/deploy are excluded.

## Required behavior
Cover 39 final states plus necessary loading/empty/error/access/offline transitions on both shells. PWA offline: read-only schedule/homework; TMA online. No attendance outbox or persistent bearer token. Manual н-to-+ petitions: one past lesson, five submissions per semester; auto-geo requests separate/unlimited; multi-lesson у tickets unlimited. Blockage permits only у. File retention one year with explicit expiry. Homework is the final date feed, not old calendar modes. Metrics: server four percentages/counts, final UI subset and own rank without peer personal details. Light/system retain composition and canonical mode values.

## Constraints
Vue strict TS, separate PCSS/rem, canonical semantic/component tokens. Figma read-only; no invented UI/new design tokens/Tailwind/compatibility layer. Preserve dirty owner files byte-for-byte, exclude from commits. One writer per checkout and explicit ownership of API/generated/config/lockfiles. Real Telegram host is separate from browser simulation.

## Existing patterns
Java-first -> OpenAPI -> generated TS. StudentApi typed transport/unauthorized retry; Vue Query response owner. Preserve Today server eligibility/cooldown/idempotency. Academic getHomeworksForWeek already supplies dated group-scoped data; safe desired-state completion is added. MobileBottomNav uses items/activeId and derives count.

## Acceptance criteria
All agreed frames map to implemented scenarios/transitions on both shells, real API state changes, Figma screenshot comparison at 390x844 plus responsive/font/theme checks. Authz/foreign scope/expiry/logout/retries/failure recovery/offline boundaries pass. Each integrated scenario gets fresh checks for each shell and independent Sol high review. Whole role remains IN_PROGRESS until complete. PWA may be accepted with explicit TMA host OPEN if genuine Telegram unavailable.

## Verification
Record revision, exact command, exit, environment, evidence and limitations. Focused unit/integration/contract/typecheck/lint/build/browser checks; fixtures never replace real services. Previous composed evidence is not new full PASS. Review stable diff, fix bounded defects and independently recheck. Local commits/integration after scoped PASS; verified backup for overlapping owner files before main integration.

## Do not
Do not stop at preparation/one screen, infer fullness from old navigation, fabricate ownership-gateway success, create hidden orchestration, delete data/backups or alter global protections. Consultant decisions are not attributed directly to owner.

07.09.2026, latest owner override: PAUSED by explicit request. Do not continue automatically; resume only after owner returns. Exact continuation state is pause-2026-09-07-1850Z/CHECKPOINT.md; source/criteria remain preserved. This supersedes continuous-execution requirements while paused.


07.09.2026 19:47UTC: owner explicitly RESUMED. This cancels the preceding pause for current execution. Original full39 goal and scope apply. Pause snapshot preserved; resume audit195 source +47 owner hashes0mismatch. Resume the three original unfinished scopes, then continue staged contracts to completion.


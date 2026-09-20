# Shared shell + Homework API integration — staged contract

S3. Dispatch only after homework-api-review-2 acceptance and any bounded recheck. Fresh Luna max developer, no children. Root supplies final accepted source hashes at dispatch.

## Goal
Integrate accepted shared shell and Homework API/date delta into clean task integration checkout, yielding a frozen revision for shared Homework UI. Do not merge main yet.
## Context/evidence
Main root .agent/student-role-02/active-contract.md; shared-shell-final-manifest.json12files and shared-shell-review-2.md PASS; Homework acceptedmanifest supplied at dispatch (pause snapshot25files is preservation, not acceptance). Baseline8002b9ea4356b10779c5bb9a6d99746d32d78ae2. Integration checkout .agent/worktrees/student-role-02/integration had clean tracked status at resume.
## Relevant scope
Sole writer integration checkout. Copy only accepted12shell and acceptedHomework paths from their worktrees after hash checks. Own integration evidence and local commit of accepted paths only after checks. Source worktrees and main readonly. No requests domain, dependencies/config/locks or owner docs integration.
## Required behavior
Union accepted files, preserve all existing checkouts and main dirty state. Mobile-core package.json in shell snapshot is outside final12 acceptedmanifest and must not be swept into integration. Academic OpenAPI raw-only residue excluded after filtered comparison with HEAD; BFF OpenAPI semantic delta included. Disclose any conflicting file or unexpected delta before overwriting it. Root supplies paths/review acceptance, never blanket git add.
## Constraints
No main integration, cleanup/reset, cherry-pick of unknown commits, global configs, deploy, production data or secrets. Do not add compatibility code. Do not silently regenerate entire contracts or adopt unrelated source drift. You are not alone; preserve others' changes.
## Existing patterns
Java-first canonical OpenAPI/generated TS; core typed StudentApi, shared navigation/host boundaries. Prior full-lint baseline defect fixture-transport.test.ts:94 must be compared with now accepted Homework fixture delta before attributing a failure. Use current workspace toolchain/scripts.
## Acceptance criteria
Accepted source hashes match destination; sourcecheckouts remain unchanged; union compiles without new conflicts; applicable core tests/typecheck/scopedlint and both shell builds pass, generated contract drift absent. No fake full-role PASS. Stable integration source manifest and commit baseline available for subsequent UI worktree.
## Verification
Read frontends/services/tests AGENTS and rct-verification. Record exactcommands/exit/env/revision and ownedfile manifest. Do not rerun152Academic+Mongo suites merely to copy identical nonoverlapping files; run targeted checks if integration changes behavior or contracts. Browser/realAPI remains downstreamUI/adapter gate. BackenddependencyFAIL remains separate and blocks fullrole acceptance.
## Do not
No UI implementation in integration step, no copying packageconfig from preservation snapshot, no unreviewed requests/config files, no writes to main or sourceworktrees, no review claim beyond accepted scope.

## Resume source selection, pending final recheck verdict

Canonical Homework preservation manifest is `.agent/student-role-02/pause-2026-09-07-1232/homework-api/manifest.json`: 31 files. Integration selection excludes only `docs/openapi/academic.json` (already reviewed as raw-only residue), leaving 30 paths. Shell acceptance manifest remains 12 paths. Root recomputed all 43 source hashes after resume: 0 mismatch; no overlap between source path sets. Integration checkout tracked status is clean. Use these manifests rather than the target local contract-repair source-manifest.json, which contains one evidence-only SHA typo for HomeworkJsonConfiguration.java. No source file mismatch was observed. This appendix does not itself replace the final independent review acceptance gate.

07.09.2026 ROOT ACCEPTANCE: immutable homework-contract-review-3.md is PASS for generated completion-date repair, combined with prior homework-api-review-2.md acceptance of domain/API and shared-shell-review-2.md PASS. Dispatch source selection is now FROZEN: 12 shell + 30 Homework paths described above. LOW local evidence SHA typo does not affect canonical verified source selection. Integration may proceed in assigned checkout only; full role/dependency gates remain open.

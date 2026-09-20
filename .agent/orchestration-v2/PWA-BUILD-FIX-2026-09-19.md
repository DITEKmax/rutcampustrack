# PWA build correction — 2026-09-19

## Goal
S1 fix the two confirmed strict TypeScript failures in Requests components without changing runtime behavior.

## Context/evidence
H44 built all six jars from d7ec16572db325d944f1a1fbd3b4960b827d09c3, then npm run build --workspace @rct/pwa-vue failed exit2. Exact raw: C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/build-i6/h44-build/runs/20260919T174740067Z-85499072d7b8444ca6e2f3e51af9059b/012-npm-pwa-build.stdout.log. TS2379 in RequestCard.vue and RequestsScreen.vue: explicit attachmentStates: undefined default conflicts with exactOptionalPropertyTypes. Root opened both originals and PWA tsconfig. Six jars and failed-run evidence must be preserved; no manifest exists.

## Relevant scope
Attendance lead has one allocated fresh Luna max slot. Create a fresh isolated worktree branch codex/v2-pwa-build-fix from exact d7ec16572db325d944f1a1fbd3b4960b827d09c3, location C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-pwa-build-fix. Fail if occupied, preserve existing checkouts. Sole product writer owns only frontends/mobile-core/src/features/requests/RequestCard.vue and RequestsScreen.vue; scoped .agent evidence and instruction pointer allowed separately. Root owns shared orchestration status. No other worktree edits.

## Required behavior
Remove unnecessary explicit undefined defaults or equivalent minimal type-correct correction preserving optional absence and all attachment UI behavior. Do not add empty-map defaults merely to silence TypeScript. Examine parent-to-child bindings under exactOptionalPropertyTypes; fix only causally necessary typing in these two files. No broadened API, any/ts-ignore, compiler weakening, dependency or lock change.

## Constraints
Read absolute C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A and this packet. Fresh Luna max/fork none, no children/Terra, not alone preserve others. Existing checkout AGENTS overridden only where owner v2 applies. No push/deploy/main merge, no global config/ACL changes, no Docker/Gradle. H44 build directory and producer/harness are read-only.

## Existing patterns
Read frontends/AGENTS.md, docs/agent-workflow.md, rct-verification and rutcampustrack-design before UI edits. This is type correctness only; visual design and behavior unchanged, no Figma write. Reuse actual package scripts and existing Requests component tests. H42 npm ci --offline cache availability confirmed; a locked offline install into this new worktree is permitted, no dependency upgrade or network install.

## Acceptance criteria
PWA actual npm workspace build passes vue-tsc and Vite, relevant existing Requests tests pass, two-file diff preserves semantics. Capture raw stdout/stderr/exit/source hashes. No whole-product or browser/runtime claim. Full two-file independent review after author release.

## Verification
Run locked offline dependency preparation if needed, PWA build and scoped existing tests with real logs; exact scoped escalation only if an actual sandbox failure requires it. Stop first meaningful failure, report new evidence before scope expansion. No extra test mirroring a removed default line. Stable diff then fresh Sol high full bounded review allocated in the same slot after author release. After review PASS, exact two-file local commit is authorized; do not include metadata or unrelated files. Return new commit and hashes. Root will choose clean build checkout for next producer run; do not delete dist/jars for retry.

## Do not
Do not reuse dirty original/E or modify accepted build worktree; do not import other WIP, weaken strict typing, change styles, install new dependencies, run backend checks, or edit source while review is active.

## Root amendment after actual checks — 2026-09-19
Actual remove-default + conditional binding passed PWA build and54 tests but failed lint vue/require-default-prop. Root approves explicit optional property union with undefined plus existing undefined default if type-correct; preserve actual runtime behavior, no casts/suppressions/compiler weakening. Restore direct binding and remove unnecessary computed wrapper if supported. All build/lint/tests gates and fresh full review remain required.
After PASS and exact2file localcommit, assigned developer may create new clean C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-runtime-build-r2 branch codex/v2-runtime-build-r2 from actual new commit, fail if occupied. No dist/jars/node_modules copies or source/metadata edits in cleanbuild; absolute owner rules apply. Return exact commit/diff/clean status; root takes sole build execution ownership. Preserve original H44 worktree/artifacts.

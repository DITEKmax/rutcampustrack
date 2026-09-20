# I5 R4 integration packet — accepted R4 UI over accepted I4

Date: 2026-09-15 (Europe/Moscow). Assigned developer: `gpt-5.6-luna`,
`max`; sole writer for `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-integration-a2-l3`.
Base/revision: `b8220ac92125a8afa37598b270aa4fab7aa1f470`.
Rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`
SHA256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
Risk: S2 integration of an accepted frontend slice; browser/fullservice gates remain open.

## Goal

Integrate the accepted R4 Requests UI source into the accepted I4 Academic/map
union as an isolated 58-path product union, preserving all I4 behavior and
recording the required client test environment separately.

## Context/evidence

I4 is accepted with 44 product paths and manifest
`28447CAB436CE49C2AADEC9103A4293E358D5BCBE569C9B61C85F7C655FABDA9`, based on
the same E revision. R4 is accepted by fresh Sol review for 14 product paths;
its source ledger is `.agent/v2-requests-ui/diff.md` and source revision is
the same E revision. Before copying, all 44 I4 hashes, all 14 R4 Git blob
hashes, the client environment SHA, and the zero-overlap gate passed.

## Relevant scope

Copy exactly the 14 R4 product paths listed in `i5-diff.md` from the read-only
source worktree `v2-requests-ui`. Copy only
`.agent/v2-requests-ui/vitest-client-environment.ts` as supplemental harness
input. Own I5 provenance, checks, runtime status, manifest, and summary under
`.agent/integration-i1/`. I4 product paths remain preserved.

## Required behavior

Keep R4's independent request resource freshness, stale-owner fences, exact
MIME/extension validation, per-attachment pending/error/retry state, popup
gesture and ObjectURL cleanup behavior. Keep I4's Java MIME/filename parity,
Owner single-flight behavior, resource epochs, auth suppression, attachment
popup cleanup, and all accepted Academic/map/Homework/projection behavior.

## Constraints

Source, E, and the accepted I4 product union are read-only inputs. No
generated/schema/config/lockfile/dependency changes, installs, Gradle, Docker,
browser, deploy, push, merge, secrets, or main promotion. No source evidence,
history, caches, or source instructions are copied. One writer, one frontend
worker, and no child/Terra escalation.

## Existing patterns

Use the existing Vue config `frontends/pwa-vue/vite.config.ts`, the source's
exact client environment, current mobile-core workspace scripts, and the I4
union manifest/provenance. R4's accepted files are copied byte-for-byte; no
behavioral rewrite or generated artifact handling is introduced.

## Acceptance criteria

The target contains exactly 58 unique product paths: the accepted I4 44 plus
the accepted R4 14, with canonical manifest
`D3FF3DFDC7C093935CCE3DEF4B4C0A4151F876F1572C3A3851A4480A588FC204` and zero
hash drift. The supplemental environment is recorded separately with its
exact SHA and is excluded from the product count. The actual focused union has
5 files/52 tests, typecheck and lint pass, and the diff has no whitespace
errors. Browser/fullservice and fresh full58 Sol review remain separate gates.

## Verification

Run from `frontends`, one worker, offline existing dependencies:

```text
npm exec --offline -- vitest run --config pwa-vue/vite.config.ts --environment ../.agent/v2-requests-ui/vitest-client-environment.ts mobile-core/src/features/requests/requests-controller.test.ts mobile-core/src/features/requests/attachment-validation.test.ts mobile-core/src/features/requests/request-attachment-action.test.ts mobile-core/src/features/requests/RequestsScreen.test.ts mobile-core/src/shared/components/StudentFeatureOwner.test.ts --maxWorkers=1
npm run typecheck --workspace @rct/mobile-core
npm run lint --workspace @rct/mobile-core
git diff --check
```

The first Vitest sandbox probe failed before collection with esbuild access
denied; the exact same command then passed under the narrow scoped escalation.
Both outcomes and raw output are recorded in `i5-checks.json` and `i5-*.log`.

## Do not

Do not import the R4 packet/evidence/history, alter accepted I4 or R4 source,
change Owner/test fences, add dependencies, claim browser coverage from
mounts, claim backend/runtime/fullserver readiness, or promote to E/main.

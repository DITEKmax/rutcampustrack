# Homework presentation repair — compact packet

Repair revision: 2026-09-07, S2, fresh Luna max implementation leaf, no children.  
Frozen product baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.  
Frozen 25-path source: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/homework-combined-review-source/manifest.json`.  
Sole product writer: this leaf, limited to `HomeworkScreen.vue` and `homework-screen.pcss`.

## Goal

Close the two independently confirmed MEDIUM presentation findings in the shared student Homework card while preserving the accepted API, date, completion, theme and shell contracts.

## Context/evidence

The released review and root dispatch confirmed that `link=null` must leave no materials action and place the remaining disclosure control at the action-row right edge. The API's required `title` is the always-visible brief; optional `description` is expanded detail before actions. Baseline reproduction is preserved in `baseline-evidence.json` and `baseline-repro.mjs`; final browser evidence is `final-visual-evidence.json`.

## Relevant scope

Product scope is exactly:

- `frontends/mobile-core/src/features/homework/HomeworkScreen.vue`
- `frontends/mobile-core/src/features/homework/homework-screen.pcss`

Evidence and copied fixture files are confined to this directory. Frozen source paths outside the two owned files must remain byte-identical.

## Required behavior

Always render `item.title` as the 13px brief. Render `item.description` only when it is nonblank and expanded, at 12px before the actions row, with a matching detail-region id. Render the disclosure control only for nonblank detail; collapsed controls must not reference an absent region. For `link=null`, omit materials copy/action and right-align the disclosure; omit an empty actions row when both materials and detail are absent. Preserve supported and unsafe material behavior, touch targets, keyboard completion, retries, focus restoration, offline read-only state and text escaping.

## Constraints

Use existing semantic tokens and rem-authored PCSS. Do not change APIs/generated types, query/cache logic, adapters, shell/Today components, tokens, assets, configs, lockfiles or unrelated working-tree files. Do not add dependencies, commit, integrate, deploy or write to Figma.

## Existing patterns

Reuse `StudentHomeworkItem`, `MobileShell`, existing `hasDescription`-local presentation boundary, `validateHomeworkLink`, semantic card/action classes and the current Vue typed props/emits. The copied fixture mounts the actual shared component and `useHomework` with a deterministic fetcher.

## Acceptance criteria

Both prior findings are reproduced before repair and resolved after it. Distinct title/detail values remain intact; empty and whitespace-only details create no region/control; null-material detail is right-aligned; available/unsafe material controls remain safe and usable. Final states, keyboard/reversal/failure/offline/historical transitions, themes, root-font matrix and narrow/wide overflow remain healthy. Exactly two frozen product SHA entries change.

## Verification

Use `checks.md`, `runtime.md`, `diff.md` and `summary.md` as the handoff record. Run mobile-core tests/typecheck/lint, workspace typecheck/lint, PWA/TMA builds, syntax/diff checks, frozen25 scope verification and Edge fixture runtime at 390×844 plus light/dark, root 16/20/24 and widths 320/390/430. Fresh independent Sol recheck remains downstream.

## Do not

Do not reopen the withdrawn historical-range finding or alter completion/API/date/theme/session behavior. Do not claim whole-role, real-adapter, real-API or Telegram acceptance from this bounded fixture.

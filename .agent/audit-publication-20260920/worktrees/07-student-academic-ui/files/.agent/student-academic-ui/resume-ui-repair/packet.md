# Fresh compact contract — root box sizing repair

Date: 2026-09-08
Status: IMPLEMENTATION
Risk: S1 presentation
Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
Branch/worktree: `codex/student-academic-ui` / `.agent/worktrees/student-academic-ui`
Assigned writer: fresh bounded FE leaf (`gpt-5.6-luna`, `max`)

## 1. Goal

Исправить подтверждённый overflow корневых mobile-контейнеров Attendance и
Statistics одним локальным CSS-контрактом: корни должны считать заданную
`inline-size` вместе с padding и border.

## 2. Context / evidence

- Root browser evidence: `http://127.0.0.1:18210/?fixture=4593-142&theme=dark`,
  viewport `390x844`; `document.clientWidth=375`, `scrollWidth=407`,
  `main` rect width `406.8`, root computed `boxSizing=content-box` and
  padding `20px 16px 108px`.
- The same horizontal overflow was observed for all 13 known fixture states.
- `attendance-screen.pcss` and `statistics-screen.pcss` already set
  `box-sizing: border-box` for descendants and pseudo-elements; root selectors
  omit it while declaring `inline-size: min(100%, 26.625rem)` plus padding.
- Historical evidence caveat: one generated-font checkpoint SHA has 63 hex
  characters; current full SHA is
  `391A9B24B5C46EBFF5D21F53CDB2EDAA31C3446DA3B722F038AB07D75D02A82D`, matching
  the prefix. This is recorded as a new evidence correction, not product drift.

## 3. Relevant scope

Product writer scope is exactly:

- `frontends/mobile-core/src/features/attendance/attendance-screen.pcss`
- `frontends/mobile-core/src/features/statistics/statistics-screen.pcss`

Evidence and verification may be added only below
`.agent/student-academic-ui/resume-ui-repair/`. The other 21 application paths
from the frozen FE13 manifest are read-only. The parent checkout's foreign
changes are outside this worktree and must remain untouched.

## 4. Required behavior

- Add `box-sizing: border-box` to `.attendance-screen`.
- Add `box-sizing: border-box` to the grouped `.statistics-screen,
  .statistics-detail` root rule.
- Preserve all existing descendants, dimensions, tokens, colors, props, data,
  requests, focus/keyboard behavior and local theme composition.
- At the approved `390x844` narrow viewport and increased root-font checks, root
  width must include padding without horizontal overflow while metrics/history
  remain visible; light/dark composition remains identical.

## 5. Constraints

- Minimal two-file PCSS change only; no redesign, hardcoded width,
  `overflow:hidden` masking, global style, token, fixture, Vue, API, or shell
  change.
- No server or browser runtime from this leaf; root owns post-freeze browser QA.
- Do not stage, commit, reset, clean, rollback, or message external apps.
- Preserve all pre-existing WIP and foreign files; normal `git diff` does not
  expose untracked paths, so use explicit SHA manifests.

## 6. Existing patterns

- Vue + PostCSS local feature styles already scope `box-sizing` to descendants
  and pseudo-elements.
- Root geometry is local: `inline-size: min(100%, 26.625rem)` and responsive
  rem padding. Making the roots border-box completes the existing local sizing
  pattern without changing layout rules.
- The design sources require mobile overflow checks, preserved state anatomy,
  two-theme value changes only, and visible metrics/history.

## 7. Acceptance criteria

- Exactly one declaration is added to each of the two allowed root rules.
- The two files have no unrelated changes; no other 21 application files change.
- A fresh final SHA manifest covers all 23 application paths, with a separate
  evidence subtree manifest/correction record.
- Scoped typecheck, ESLint and harness production build exit 0.
- Root independently confirms responsive/narrow/root-font, light/dark,
  visible-metrics and keyboard/back behavior after this leaf freezes code.
- Fresh Sol FE review receives stable diff, packet, manifests and checks; no
  browser PASS is claimed by this leaf.

## 8. Verification

Run from `frontends` in this nested worktree:

1. `npx --no-install vue-tsc -p mobile-core/harness/attendance-statistics/tsconfig.json --noEmit`
2. `npx --no-install eslint mobile-core/src/features/attendance mobile-core/src/features/statistics mobile-core/harness/attendance-statistics --max-warnings=0`
3. `npx --no-install vite build --config mobile-core/harness/attendance-statistics/vite.config.ts --configLoader runner`

Record revision, environment, exact command, exit code and raw output summary
in the repair subtree. Also record pre/post dirty scope, `git diff --check`,
and hashes for all 23 application paths. Product runtime status is
`PENDING_ROOT_BROWSER_QA`; this leaf does not start servers.

## 9. Do not

Do not touch any other product path, rewrite old checkpoints, claim HTTP or
browser PASS, alter global box sizing, hide overflow, redesign responsive
geometry, change the Requests slot/API/backend, inspect secrets, create
children, stage/commit, or escalate to Terra without a recorded defect or
complexity gate containing new evidence and a root decision.

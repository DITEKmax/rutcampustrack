# Shared mobile shell — compact contract

## Goal

Extract the reusable mobile shell and bottom navigation contract from the Today
feature so the mobile entry points can share route-aware shell behavior without
duplicating dock, Back, keyboard, and host integration logic.

## Context / evidence

- Baseline revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Design source: Figma file `VgVjQYWILLG9AC7Eh12VMk`, node `4581:3062`.
- The source defines five bottom-nav roots: Today, Homework, Attendance, More,
  Profile. Unimplemented roots must remain visibly disabled.
- The source shows the dock on root surfaces and removes it for detail/editor/task
  surfaces and while the keyboard owns the lower viewport.
- Host back and keyboard behavior must cross a callback boundary; this scope does
  not import a Telegram or browser SDK.
- The canonical Figma variable for glass background blur is `25`; the `12.5`
  value returned in generated CSS context is a conversion representation, not a
  source-token mismatch.

## Relevant scope

- `frontends/mobile-core/src/shared/**`
- `frontends/mobile-core/src/features/today/TodayScreen.vue`
- `frontends/mobile-core/src/features/today/today-screen.pcss`
- `frontends/mobile-core/src/index.ts`
- `frontends/mobile-core/tests/navigation.test.mjs`

## Required behavior

1. Export typed root/nested routes and a stack with root navigation, nested push,
   and one-step back semantics.
2. Render a reusable five-slot bottom nav with active state, disabled state, and
   accessible disabled reasons.
3. Keep dock visibility and product/host Back ownership as pure shell policy.
4. Let a host adapter subscribe to keyboard/back and receive Back visibility,
   while keeping all host callbacks optional.
5. Integrate Today through `MobileShell`, preserving its existing data, props,
   check-in, and date-selection behavior.
6. Keep shared shell typography at the project font and numeric settings when
   the shell is rendered outside a feature's local typography scope.

## Constraints

- Russian user-facing labels and existing project PCSS/token conventions.
- Keep unimplemented routes disabled; do not invent route screens or backend
  behavior.
- Do not depend on an external host SDK in `mobile-core`.
- Preserve unrelated work in the shared checkout and do not change the source
  design decisions outside this shell contract.

## Existing patterns

- Vue `<script setup lang="ts">` components and typed `defineEmits`.
- Existing Today SVG assets and PCSS variables for dock/content dimensions.
- `frontends/package.json` workspace scripts and Node test adapter conventions.

## Acceptance criteria

- Today has one shell-owned dock and keeps Today active while other roots are
  disabled with the reason `Раздел пока недоступен`.
- Root navigation clears nested state; nested back returns to its root, then
  stops at the root.
- Dock hides for detail/editor/task routes and whenever keyboard visibility is
  true; host Back visibility is delegated only when the host owns Back.
- Dock labels inherit `--rct-font-family-sans` and the project's tabular numeric
  settings.
- Existing mobile-core, PWA, and TMA typechecks/builds remain green.
- Runtime at the existing fixture server visibly exposes the five-slot dock and
  the expected disabled routes.

## Verification

- Run the checks recorded in `checks.json` from the frozen worktree.
- Capture runtime DOM evidence from `http://localhost:5175/` without starting a
  competing dev server.
- Record the complete scoped diff and known limitations in `diff.md` and
  `summary.md`.

## Do not

- Do not change backend contracts, generated types, app-level router ownership,
  host SDK setup, or unrelated fixture lint debt.
- Do not redesign the role control, semester details, or source blur/gradient
  values; the canonical blur is already represented by the existing project
  token and the conversion distinction is recorded above.

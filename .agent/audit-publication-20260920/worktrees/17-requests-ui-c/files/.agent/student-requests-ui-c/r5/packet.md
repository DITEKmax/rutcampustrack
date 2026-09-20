# Requests UI R5 compact contract

Date: 2026-09-08 (Europe/Moscow)
Owner: `/root/requests_ui_repair_r5` (fresh bounded implementation leaf; sole writer in this worktree)
Assigned model/effort: `gpt-5.6-luna / max`; no child agents
Baseline revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`; detached worktree `requests-ui-c`
Risk: S2 (retained draft recovery, form gating, local contract metadata and runtime evidence)

## 1. Goal

Close only R4 findings 1, 3, 4 and 5 in the frozen Requests UI. Give a user an
explicit, reversible recovery path for retained lesson IDs that are now
ineligible or missing, show visible keyboard focus for the file picker, make a
task-owned `vue-tsc` check cover Requests SFCs, and produce independently
verifiable final source/diff/runtime evidence. Preserve the already-correct
valid selection flow and server eligibility authority.

## 2. Context/evidence

- R4 independent review is
  `.agent/student-gateway-c/reviews/ui-r4-sol-final-2026-09-08.md`, SHA
  `3C4D553047A877114393EEABD32E15D35DEBAD1D66F2C22075ADB8384B3F679F`.
- R4 reproduces a retained ineligible option that remains checked and disabled,
  and a retained ID absent from refreshed options that cannot be removed. The
  current `ExcuseRequestScreen.vue` gates submit on every retained ID being
  eligible, so stale IDs can permanently block the form while draft reason,
  comment and files are retained.
- R4 reproduces the file-picker focus boundary: focus reaches the nested
  visually-hidden input while the visible label has no `:focus-within` rule.
- R4 records the plain `tsc` include boundary (`src/**/*.ts`) and requires a
  scoped SFC-aware `vue-tsc` configuration without root/package edits.
- R3 evidence is under `.agent/student-requests-ui-c/r3/**`; its corrected
  targeted RequestsScreen command passed 4/4, but its runtime screenshot entries
  have no persisted image paths or hashes and its baseline source manifest omits
  five guarded `source/design-assets/*.svg` files.
- Guarded sources are under `.agent/student-requests-ui-c/source/**`, including
  design context, design references, wireframe source and five functional SVGs.

Owner extension: the local feature contract now owns
`RequestReasonOption.commentRequired: boolean` in `types.ts` and the selected
server option controls blank-comment gating. Backend/proto/OpenAPI/BFF/generated
and transport producers remain a separately routed integration obligation.

## 3. Relevant scope

Writable product/test files are exactly:

- `frontends/mobile-core/src/features/requests/RequestLessonSelector.vue`
- `frontends/mobile-core/src/features/requests/ExcuseRequestScreen.vue`
- `frontends/mobile-core/src/features/requests/RequestsScreen.test.ts`
- `frontends/mobile-core/src/features/requests/state.ts`
- `frontends/mobile-core/src/features/requests/state.test.ts`
- `frontends/mobile-core/src/features/requests/RequestAttachmentField.vue`
- `frontends/mobile-core/src/features/requests/requests.pcss`
- owner-frozen extension: `frontends/mobile-core/src/features/requests/types.ts`

The existing 12 Requests product files and three harness files may be read and
hashed for scope evidence. Harness write scope is only
`frontends/.requests-harness/main.ts` and only when needed to exercise recovery,
focus or metadata runtime state. Evidence/config writes are only
`.agent/student-requests-ui-c/r5/**`; the only new config is the task-owned
`.agent/student-requests-ui-c/r5/tsconfig.requests.json`.

## 4. Required behavior

- A selected present option with server `excuseEligible !== true` remains
  visible with the supplied unavailable reason and has an explicit enabled
  recovery control that removes only that lesson ID from the draft.
- A retained selected ID absent from refreshed server options renders a clear
  missing-lesson recovery row/control; activating it removes only that ID.
  Reason, comment and file refs survive both removals. No invalid lesson ID can
  be submitted.
- Valid eligible selected lessons keep their existing checkbox/update behavior;
  server option membership and eligibility flags remain authoritative.
- The selected reason uses its server-provided `commentRequired` boolean. When
  true, a blank/whitespace comment blocks submit and explains the requirement;
  when false, a blank comment does not block submit because of local reason-code
  inference. Existing reason/comment draft values remain intact.
- Add executable tests for actual update semantics plus SSR markup and submit
  gating for present-ineligible and missing retained IDs, and for independent
  `commentRequired` true/false metadata.
- A focused file-picker input exposes a visible ring through owned PCSS
  `:focus-within`/equivalent, while disabled styling and current tokens remain.
- The R5 task tsconfig includes all Requests `.ts` and `.vue` files plus needed
  existing declarations/assets and passes `vue-tsc -p ... --noEmit` without
  package/root config changes.
- Final evidence contains stable hashes for the exact 12 Requests product files
  and three harness files, a source manifest containing the existing guarded
  sources plus all five `source/design-assets/*.svg`, checks with exits and
  environments, and five actual 390x844 runtime screenshot paths/hashes when
  persistence is supported. The owned preview binds only to `127.0.0.1:18540`
  or, if occupied, `127.0.0.1:18541`; it is stopped and both ports are proven
  free.

## 5. Constraints

- Preserve all foreign work and the frozen baseline behavior outside this
  contract. Do not reset, clean, commit, deploy, install dependencies, use
  network, start full app/backend/Gradle/Docker, or edit generated/API/BFF/proto,
  package, root tsconfig, shared navigation or design source assets.
- Keep Vue 3 `<script setup>`, strict TypeScript, existing PCSS token patterns,
  authored lengths in `rem`, native form semantics and existing accessibility
  behavior. No new dependencies or unrelated polish.
- Do not silently clear a full draft, invent eligibility/reasons, or claim
  backend integration. Do not alter commentRequired producer ownership.
- WARN/ERROR becomes a code change only with a request link, reproduction and
  new evidence. Terra escalation is unavailable absent a recorded defect or
  complexity gate containing request, reproduction, new evidence, correction,
  bounded scope and root decision.

## 6. Existing patterns

Use the feature's controlled props/emits, `RequestsDraft` state helpers,
`RequestLessonOption` server flags, `rct-focus-ring` and semantic request tokens.
The temporary harness mounts real Vue components and is the runtime fixture; it
does not own transport or navigation. Use SSR rendering in the targeted tests
to verify recovery markup and button/submit gating, and use the existing Vite
Vue plugin path for browser capture.

## 7. Acceptance criteria

1. Present-ineligible and missing retained lessons are both visibly removable;
   only the selected ID is removed, all other draft fields survive, and invalid
   submit stays disabled.
2. Eligible selections still update through the existing controlled event path;
   tests exercise behavior rather than only wording.
3. `commentRequired` true/false metadata independently controls blank-comment
   gating and is represented in the local type contract; backend integration is
   explicitly OPEN outside this leaf.
4. File-picker keyboard focus has a visible nested-input focus state and
   disabled style remains intact.
5. Task-owned `vue-tsc` check exits 0 and includes Requests SFCs.
6. Targeted Vitest/SSR and state tests pass; applicable lint/source/hash guards
   are recorded with exact commands, exit codes, revision and evidence.
7. Five stored 390x844 captures have paths and SHA-256 hashes, or the evidence
   records the exact bounded capture-tool limitation; after cleanup both
   `18540` and `18541` have no listeners.
8. Final scope manifest/hash evidence covers exactly 12 product + 3 harness
   files and no out-of-scope product file is changed.

## 8. Verification

- Before and after, record `git status`, baseline/final revision, exact scoped
  hashes and diff guard under `r5/**`.
- Run corrected targeted command from `frontends`:
  `npx vitest --config pwa-vue/vite.config.ts --configLoader runner --root . run mobile-core/src/features/requests/RequestsScreen.test.ts --reporter=verbose`
  plus state tests, scoped `vue-tsc`, and applicable mobile-core lint/CSS/source
  guards. Record exit codes, environment and evidence paths.
- Run an owned harness on `127.0.0.1:18540` with fallback `18541` only if needed;
  capture open, archive, type, excuse (including recovery and focus), and late
  at 390x844; inspect keyboard/focus, overflow, dark/light and reduced motion
  observations. Stop it and prove both ports are free.
- If native screenshot persistence is unavailable, investigate a bounded
  installed headless browser capture and record the exact result; never mark a
  screenshot captured without a local path and hash.

## 9. Do not

Do not redesign Requests, change server eligibility policy, infer
`commentRequired` from `OTHER` or another code, edit backend/API/generated
chains, modify package/root config, overwrite source assets, touch foreign
worktrees, broaden harness scope, or report unpersisted screenshots as stored.
Do not escalate to Terra without the required recorded evidence gate.

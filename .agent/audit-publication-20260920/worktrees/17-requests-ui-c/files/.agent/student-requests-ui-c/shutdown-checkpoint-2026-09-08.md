# Shutdown checkpoint — 2026-09-08

Status: PAUSED by root owner directive. No further product edits, checks, or browser verification after this checkpoint.

## Workspace and baseline

- Exact cwd: C:\Users\maksd\.codex\worktrees\e31c\rutcampustrack\.agent\worktrees\requests-ui-c
- HEAD at pause: d3c31acb8cce53791a4981e5858a37d44fdc9a0e
- Nested worktree has no tracked modifications. `git status --short --untracked-files=all` reports only the owned untracked scope:
  - 12 feature files under `frontends/mobile-core/src/features/requests/`
  - 3 temporary harness files under `frontends/.requests-harness/`
  - 20 packet/source files under `.agent/student-requests-ui-c/`
- Parent worktree has unrelated dirty work owned elsewhere; it was not touched.

## Current scoped file hashes (SHA-256)

Feature:
- ExcuseRequestScreen.vue 4CACF086BF1F23AD5019C49723A3A77E5C2FCC2C94F8906F8DDC4C71C68DFDC0
- LateCheckinRequestScreen.vue BA1562608D624F4BD214A16F64F466EE5FC1D414A138E78271D4E5F4E50389A1
- RequestAttachmentField.vue 435415BF6C7BAE75E7C944E4E039FC26F81EDF56FEBBD3F16C9884948B74E3D5
- RequestCard.vue E7C5D2C8672DC378B275C821CB95DC60618E7C187B361546F1A76B1E9775C54C
- RequestLessonSelector.vue 61931F6E4278695BAAF0A051CAA27E2AE742BA2C2E17D9E5C2B1B52654B6811E
- requests.pcss A039B01D4E721E3C845867FA50C4760883AAFADE306BC348C143F1B2B642B066
- RequestsScreen.test.ts 324A1B4AF08CB495467349F9BB42C06D2BD89296B38972A0222492EEAEAEC964
- RequestsScreen.vue 1DE1A0D8E2A7749B0F4F3F92740D7B779DB091568E57BC3B39CD272DFC9F154E
- RequestTypeScreen.vue 06E665C7C4E922F8A299B07FEFB6E01593C136CF746BCFEAA4893DAE9B49749A
- state.test.ts EBE3B04372DF6062152E2D014D384AA6D0909FD546A9C3B2A54A561FCF589999
- state.ts C8209A596531D17E6AE72F9345CA4DD482F26523E23208EE04BC8B4ABAF1ECDE
- types.ts 74FC73127D066B952D7D22B0A7C278E718295A170F998A068478152986812071

Harness:
- harness/harness.css 2922EB784D906E93E05ECD37669B0740BED8599A8F9F75D1E5C42752142C81BE
- harness/index.html CF6D831297750E0796548ACC00F86A1FC2BF114F2D0292A507AFF67A57C657D7
- harness/main.ts 3D91ED7E1361BC0779DF2C1D6DB69E96D89AD5B7D9740AF83766DFA5AB657BB5

Guarded source hashes remain recorded in `.agent/student-requests-ui-c/source-sha256.json`.

## Last actual checks and runtime evidence

PASS:
- `npx vitest run mobile-core/src/features/requests/state.test.ts` from nested `frontends/`: exit 0; 1 test file, 4 tests passed.
- `npm run lint` for `@rct/mobile-core`: exit 0; this was before the final RequestType rewrite and the pause-time RequestCard/test edits.

FAIL:
- `npx vitest run mobile-core/src/features/requests/RequestsScreen.test.ts` without Vue plugin: exit 1; Vite import analysis rejected the .vue SFC at ExcuseRequestScreen.vue:83.
- Temporary scoped `vue-tsc` run: exit 1 initially on two RequestLessonSelector exactOptionalPropertyTypes errors plus baseline Today missing SVG declarations; after the selector helper correction, the remaining reported errors were baseline Today missing SVG declarations. The temporary config was removed.
- During an earlier harness HMR edit, Vite logged PostCSS invalid-source errors caused by a transient literal newline replacement; the current pcss was later inspected and contains no literal backtick-n or backslash-n sequence. No final runtime check was rerun.

Runtime:
- Owned Vite harness was started from nested `frontends/` on port 18540, exec session 86296. It was stopped with Ctrl-C; a subsequent listener query showed no listeners on ports 18540 or 18541.
- CUA IAB visual checks used viewport 390x844. Open state and an earlier type state were inspected; final TypeScreen rewrite and all final states were not rechecked.

## Five-view readiness at pause

- Inbox/open: earlier screenshot was close to guarded composition, but final code/runtime was not rechecked after pause-time edits.
- Archive: harness state exists; final screenshot evidence is incomplete.
- Request type: final split late-mark symbol rewrite exists; final screenshot not rechecked.
- Excuse form: implementation exists; final visual/runtime evidence incomplete.
- Late-checkin form: implementation and dynamic budget copy exist; final visual/runtime evidence incomplete.

## Pending root corrections and known in-progress state

- Root correction: RequestCard must use guarded source SVG geometry for date/time/lesson-type/previous/chevron where applicable; current card/selector/form icons still use custom inline geometry.
- Root correction: pending requests with `decision === null` and `detail.reason` must not show decision text. RequestCard code now uses only trimmed `decision.comment` and prefers `decision.decidedAt`, but the existing test edit is incomplete: pending `detail.reason` was not added and duplicate absence assertions remain.
- Reinspect RequestCard decision behavior and add one observable pending-null-decision/reason assertion inside existing RequestsScreen.test.ts after owner resumes.
- Finish CSS/type fidelity tweaks and verify the final five states.
- Rerun applicable lint/type/SSR/runtime checks only after owner resumes; obtain required independent review through root route.

## Next steps for owner

1. Repair the incomplete test edit and verify RequestCard decision semantics.
2. Replace inline icon geometry with guarded source geometry while preserving currentColor/token adaptation.
3. Validate the pcss and final TypeScreen/AttachmentField changes.
4. Run final checks and record exit codes, runtime evidence, scoped diff, and limitations in the student evidence directory.
5. Preserve the untracked scoped files and parent-owned work.


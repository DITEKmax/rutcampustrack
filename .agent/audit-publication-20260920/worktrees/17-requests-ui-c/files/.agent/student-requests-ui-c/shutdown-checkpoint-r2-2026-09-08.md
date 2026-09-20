# Shutdown checkpoint r2 — 2026-09-08

Status: PAUSED by root owner directive. No further product edits, tests, checks,
browser verification or review are authorized after this checkpoint.

## Workspace and state

- Absolute cwd: `C:\Users\maksd\.codex\worktrees\e31c\rutcampustrack\.agent\worktrees\requests-ui-c`
- HEAD: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached, unchanged)
- Sole writer scope remains the 12 feature files below, the 3 existing temporary
  harness files, and this agent's `.agent/student-requests-ui-c/**` evidence.
- Parent checkout foreign work was not touched. Nested `git status` reports only
  the owned untracked scope: feature files, harness files, and agent evidence.

## Product hashes after r2 changes

These are the exact current SHA-256 values for all 12 product files:

```text
frontends/mobile-core/src/features/requests/ExcuseRequestScreen.vue 566AF4BBEE6A056B4247B343527B7D29E12C7618CEA6C0C8B77AC564D216CEB7
frontends/mobile-core/src/features/requests/LateCheckinRequestScreen.vue D44020D947B65479AEFA215A4F0268DE38A41FD720A56B553F8D7CDE8B6190DE
frontends/mobile-core/src/features/requests/RequestAttachmentField.vue 435415BF6C7BAE75E7C944E4E039FC26F81EDF56FEBBD3F16C9884948B74E3D5
frontends/mobile-core/src/features/requests/RequestCard.vue 5C75A728A27B1262FEC30806FF6D49F8A7C6D3AD65861C39166BAAEF5C69A02A
frontends/mobile-core/src/features/requests/RequestLessonSelector.vue 0625805CEA95BC5F622B8DF20A3BDFF4E9BB498219E97A8059546833B92BF046
frontends/mobile-core/src/features/requests/RequestsScreen.vue 1DE1A0D8E2A7749B0F4F3F92740D7B779DB091568E57BC3B39CD272DFC9F154E
frontends/mobile-core/src/features/requests/RequestTypeScreen.vue A8E04F078A8A1B945D93EFA7810BCDA7AFF65D2313FA06731E12000D948D3A93
frontends/mobile-core/src/features/requests/RequestsScreen.test.ts 324A1B4AF08CB495467349F9BB42C06D2BD89296B38972A0222492EEAEAEC964
frontends/mobile-core/src/features/requests/state.test.ts EBE3B04372DF6062152E2D014D384AA6D0909FD546A9C3B2A54A561FCF589999
frontends/mobile-core/src/features/requests/state.ts C8209A596531D17E6AE72F9345CA4DD482F26523E23208EE04BC8B4ABAF1ECDE
frontends/mobile-core/src/features/requests/types.ts 74FC73127D066B952D7D22B0A7C278E718295A170F998A068478152986812071
frontends/mobile-core/src/features/requests/requests.pcss A039B01D4E721E3C845867FA50C4760883AAFADE306BC348C143F1B2B642B066
```

Harness files remain unchanged from the prior checkpoint:

```text
frontends/.requests-harness/harness.css 2922EB784D906E93E05ECD37669B0740BED8599A8F9F75D1E5C42752142C81BE
frontends/.requests-harness/index.html CF6D831297750E0796548ACC00F86A1FC2BF114F2D0292A507AFF67A57C657D7
frontends/.requests-harness/main.ts 3D91ED7E1361BC0779DF2C1D6DB69E96D89AD5B7D9740AF83766DFA5AB657BB5
```

Guarded design originals and source hashes remain unchanged in
`.agent/student-requests-ui-c/source-sha256.json`; all five PNG/text contexts,
brand direction, accessibility requirements and `107-student-tickets.md` were
read before product edits. Guarded source SVGs remain under
`.agent/student-requests-ui-c/source/design-assets/`.

## Changes after the prior pause

- `RequestCard.vue`: decision display is now gated by a non-null decision and
  uses only trimmed `decision.comment` and valid `decision.decidedAt`; it no
  longer falls back to `summary.updatedAt`. Date, time and lesson-type SVGs
  use the guarded source paths with `currentColor`.
- `RequestLessonSelector.vue`: date/time/lesson-type source paths were applied;
  late select gained the guarded chevron path with a native-select wrapper.
- `ExcuseRequestScreen.vue`: back control uses the guarded previous path and the
  reason select uses the guarded chevron path.
- `LateCheckinRequestScreen.vue`: back control and date/time/lesson-type icons
  use guarded source paths.
- `RequestTypeScreen.vue`: back control uses the guarded previous path, the
  absent mark is lower-case `н`, and the non-source trailing text chevron was
  removed.
- A `requests.pcss` patch for icon/select/type-choice styling was attempted but
  rejected by `apply_patch` context verification; it made no mutation.
- The incomplete pending-reason test edit was not made; duplicate assertions in
  `RequestsScreen.test.ts` remain for the next authorized resume.

## Latest evidence (before r2 changes)

Historical PASS, stale after the r2 product edits:

- From `frontends/`, `npx vitest run mobile-core/src/features/requests/state.test.ts`
  — exit 0; 1 file, 4 tests passed.
- `npm run lint` for `@rct/mobile-core` — exit 0; run before the final
  RequestType rewrite and pause-time RequestCard/test edits.

Historical FAIL:

- From `frontends/`, `npx vitest run mobile-core/src/features/requests/RequestsScreen.test.ts`
  without the Vue plugin — exit 1; Vite import analysis rejected the `.vue`
  SFC at `ExcuseRequestScreen.vue:83`.
- Temporary scoped `vue-tsc` — exit 1; after selector correction only the
  existing baseline Today missing-SVG declarations remained.

UNTESTED after r2 changes:

- Targeted Vitest, lint/typecheck, contract command, CSS/PostCSS validation,
  pending null-decision observable test and final diff review.
- Final five-state browser screenshots, keyboard/focus/state/theme/reduced
  motion/overflow checks. Earlier harness screenshots covered open and an
  earlier type state only; final TypeScreen and all post-r2 states are not
  rechecked.

## Runtime and cleanup

- Historical owned Vite session `86296` on port `18540` was stopped at the prior
  checkpoint. No Vite/browser session was started after resume.
- Current listener cleanup query: `Get-NetTCPConnection -State Listen
  -LocalPort 18540,18541` — no listeners (`NO_LISTENERS_18540_18541`).
- No current owned PID or CUA browser session remains to terminate.

## Exact next steps after owner resumes

1. Repair `RequestsScreen.test.ts` with one pending `detail.reason` plus
   `decision: null` assertion and remove duplicate absence assertions.
2. Apply the pending PCSS source-size/select/type-choice styling patch and
   inspect all 12 scoped files for strict Vue/PCSS correctness.
3. Run only applicable frontend checks with exact commands and exit codes;
   preserve historical failures as limitations unless the Vue plugin/tooling
   boundary is repaired within the declared scope.
4. Free-check and strictly bind Vite to 18540 or 18541, capture all five mounted
   states at 390x844, run proportional accessibility/responsive checks, clean
   the server, and record screenshots/listeners.
5. Record final evidence, diff manifest and limitations, then release for the
   independent Sol review route. No backend/full-PWA/TMA integration claim.

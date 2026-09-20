# Homework visual handoff

Risk: S1. Scope: visual/runtime evidence for the frozen shared Homework UI only; no product correction or whole-role acceptance.

Evidence is at [`visual-evidence.json`](visual-evidence.json) with PNGs in [`screenshots/`](screenshots/). The five reference pairs (`4601:142`, `4601:848562`, `4601:848636`, `4788:146`, `4922:343`) and two recovery scenes were captured at `390x844`, dpr1. Onest is loaded, axe is clean, offline controls are disabled while disclosure/material actions remain available, and root-font `16/20/24` plus responsive `320/390/430` have no horizontal overflow.

Known gaps to carry to root:

- **FAIL, theme:** light and no-preference matrix still computes dark `rgb(11, 11, 12)`; the current semantic layer is dark-only. Theme foundation is a separate root-owned correction.
- **FAIL, geometry:** group heading computes `18px` while the Figma reference is `17px`; current card tops are about `181.14` and `447.03` versus reference about `179` and `441`. Do not patch this leaf.
- **FAIL, navigation icon inputs:** fixture lines `runtime/main.ts:14-15` pass black `today-tab-active.svg` to inactive Today and white `schedule-tab.svg` to active Homework, opposite the reference. This is fixture/adapter integration wiring, not a visual-leaf product edit.
- **FAIL, keyboard focus:** Space completes Math, but after the completion reorder `activeElement` is `BODY` (`focusedCompletion=false`). Reproduce in `visual-check.mjs` `keyboardAndFailure`; likely product location is `frontends/mobile-core/src/features/homework/HomeworkScreen.vue` completion v-for/reorder.

Reference content and deterministic fixture data differ in some expanded/historical labels, so the pair comparison establishes state/geometry and interaction evidence rather than pixel identity. Real PWA/TMA adapters, real API/session scope, genuine Telegram, independent Sol review and whole-role status remain downstream. Source 15/15 SHA check is green; fixture server `5181` is stopped and free.

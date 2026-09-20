# Diff and ownership

Baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`. No commit was created. Current changes are intentionally uncommitted for parent integration; `git diff --check` exited `0`.

The scoped combined diff contains:

- `frontends/mobile-core/src/shared/theme.ts` and `theme.test.ts` — caller-owned light/dark/system controller, media subscription, cleanup and target ownership tests.
- `frontends/mobile-core/src/styles/tokens.pcss` — canonical light/system aliases, `color/text/on-fill-strong`, dock opacity/blur aliases and already accepted Homework size/surface aliases. No new palette values.
- `frontends/mobile-core/src/features/today/TodayScreen.vue`/`today-screen.pcss` and `shared/components/MobileBottomNav.vue`/`mobile-bottom-nav.pcss` — existing SVGs are bound through currentColor masks; Today gradient controls use the canonical strong-fill foreground; geometry and navigation contract remain intact.
- `frontends/mobile-core/src/features/homework/HomeworkScreen.vue`/`homework-screen.pcss` plus `homework-*-chevron.svg` — compound disclosure layers preserve the original surface and chevron path/viewBox; completion handle/icon masks use semantic foregrounds.
- `frontends/mobile-core/src/features/homework/homework-focus.ts`/`homework-focus.test.ts` — focus restoration predicates for confirmed ACK, deliberate focus moves and stale owner/feed identity.
- `frontends/mobile-core/src/features/homework/use-homework.ts`/`use-homework.test.ts` — synchronous saved-command scope guard for the root-recorded same-tick retry defect.
- `frontends/mobile-core/src/domain/homework.*` and the rest of the Homework feature are inherited frozen implementation inputs from the released Homework UI leaf and are preserved for the combined handoff; their existing reports remain at `.agent/student-role-02/homework-ui/`.
- `.agent/student-role-02/mobile-theme/` — contract, runtime fixtures/probes, screenshots, JSON evidence, source hash audit, checks, diff and summary owned by this leaf.

The seven original Homework assets match the immutable UI15 manifest. The two new derivatives are path-only copies and do not mutate the source files. No API/generated contract, PWA/TMA adapter, backend, package/lockfile, Figma document, or unrelated role was changed. The known generated-type drift is outside this scope and remains unmodified.

Parent integration must preserve the pre-existing dirty state, recheck this stable diff, run real adapter/API scenarios, and obtain the required independent Sol review before merging.

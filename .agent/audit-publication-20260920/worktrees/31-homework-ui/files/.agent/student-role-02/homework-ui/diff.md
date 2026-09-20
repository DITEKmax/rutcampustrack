# Diff and ownership

Baseline `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`; no commit was created. `git diff --check` exited 0. The working tree contains only the following feature/evidence paths in this leaf:

- `frontends/mobile-core/src/domain/homework.ts` and `homework.test.ts` — server-date grouping, dedupe, bounded ranges and material validation.
- `frontends/mobile-core/src/features/homework/use-homework.ts` and `use-homework.test.ts` — scoped query/mutation owner, ACK validation, per-ID state/retry and range controls.
- `frontends/mobile-core/src/features/homework/HomeworkScreen.vue` and `homework-screen.pcss` — shared mobile composition, states, disclosure, material boundary, swipe/keyboard completion and shell boundary.
- `frontends/mobile-core/src/assets/homework-*.svg` — seven canonical SVG contents copied from parent `design-context/homework-svg` by preserved hashes.
- `frontends/mobile-core/src/styles/tokens.pcss` — aliases for canonical `title-screen`, `title-block` and success surface values with source mapping comments.
- `frontends/mobile-core/src/index.ts` — homework-only public exports appended.
- `.agent/student-role-02/homework-ui/` — scoped contract, checks, runtime, diff and summary plus disposable fixture.

No API/generated, shell/Today, adapter, package, lockfile or unrelated role files were changed by this leaf. Existing owner changes outside these paths were preserved.

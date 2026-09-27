# Headman navigation core — result

Contract: [headman-navigation-next-contract.md](../../orchestration-v2/evidence/2026-09-27-delivery/headman-navigation-next-contract.md).
Scope: HeadmanScheduleScreen shell, headman home/More, existing headman journal/request/stats screens, semantic navigation and focused tests.
Criteria: five roots (Сегодня, Учёт, Заявки, Ещё, Профиль); карта opens as its own root while keeping the dock and Ещё selected.
Today loads actual dated lessons via HeadmanJournalApi; selecting a lesson passes its exact date and ID to the existing journal.
More exposes the agreed Разделы and Управление entries; unavailable APIs stay visible as unavailable and do not open fake routes.
Journal and requests now show missing-source/offline/permission states; requests retain existing permission-gated decision and attachment actions.
Profile uses existing ProfilePort/ProfileState routes; optional integration props are documented in the wrapper signature.
Checks: `frontends/pwa-vue:npm run typecheck` — exit 0.
Checks: strict scoped ESLint for changed core files — exit 0; request screen ESLint `--quiet` (errors) — exit 0.
Checks: focused navigation Vitest files — exit 0 (2 files, 4 tests); `git diff --check` — exit 0.
Full `frontends/pwa-vue:npm run lint` — exit 1 on existing `vite.config.ts:28` unused `_` and `bundle`; scoped changed-file lint passes.
Runtime: no app/browser run here; App/index wiring and end-to-end acceptance remain with root.
Diff: `HeadmanScheduleScreen.vue`, `headman-home/{HeadmanHomeScreen.vue,HeadmanMoreScreen.vue,*screen.pcss}`, journal, requests, stats.
Diff: `shared/{navigation.ts,mobile-navigation-items.ts}` and their focused tests.
Limits: PWA/TMA app entrypoints are not wired in this leaf; do not claim full role production readiness before root runtime acceptance/review.
Preserved: unrelated dirty `.agent/evidence/headman-trend-export-20260926/{contract.md,result.md}`.

# Headman App wiring evidence

- Scope: bind the reviewed Headman owner to PWA and TMA auth/profile flows.
- Contract: `.agent/orchestration-v2/evidence/2026-09-27-delivery/headman-navigation-next-contract.md` (Constraints).
- Files: `frontends/pwa-vue/src/App.vue`, `frontends/tma-vue/src/App.vue`, `frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue`.
- Behavior: generation-bound homework/profile ports; positive safe actor ID; profile role selection preserves old owner before ACK and clears it after ACK.
- TMA bootstrap and selectRole share the same Headman activation; existing Telegram report download remains connected.
- PWA keeps its browser report fallback; theme, map and owner guards remain wired.
- Entry unavailable when authenticated homework actor ID is absent or invalid.
- Criteria: both entry paths supply API, profile port, role callback, theme and actor ID; no teacher/student/admin behavior was intentionally changed.
- Checks: PWA `npm run typecheck` exit 0; TMA `npm run typecheck` exit 0; mobile-core `npm run typecheck` exit 0.
- Checks: scoped ESLint on both App.vue files and HeadmanScheduleScreen.vue, `--max-warnings=0`, exit 0; `git diff --check` exit 0.
- Runtime: not run; root performs integrated PWA/TMA acceptance after integrating the wiring commit.
- Diff: three source files, bounded App bindings plus a shared TMA Headman activation and actor guard.
- Limits: no end-to-end/browser run here; no backend, generated, lockfile, student, teacher or admin implementation changed.

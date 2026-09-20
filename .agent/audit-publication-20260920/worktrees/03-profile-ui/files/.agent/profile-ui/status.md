# Profile UI leaf status — 2026-09-08

- scope: nested `profile-ui` worktree; no product files outside the frozen profile scope
- current step: root's independent Chrome QA for the mounted harness; SecurityScreen remains at baseline until the initial repro is recorded
- baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; foreign work preserved under `.agent/profile-ui/evidence/resume-2026-09-08/preserved/`
- state correction: focused profile-state Vitest 20/20, exit 0 (Vitest 4.1.4, Node 24.14.0)
- harness: `evidence/resume-2026-09-08/harness/{index.html,main.ts,App.vue,vite.config.mjs,postcss.config.mjs}`; Vite API build exit 0, 44 modules, Vite 7.3.6
- lease: exact `18110`; fresh `.NET IPGlobalProperties.GetActiveTcpListeners()` check exit 0 at `2026-09-08T09:01:59.1017509Z`, free=true
- runtime: STARTED `2026-09-08T09:02:16.2860962Z`; own evidence-only Vite PID `47364`; strictPort/127.0.0.1; command `node .\\serve.mjs`
- Chrome UI evidence is owned by root's separate session; server remains task-owned and must be stopped after QA

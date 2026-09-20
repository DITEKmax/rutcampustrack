# Checks

Environment: Windows 11, PowerShell, Node `v24.14.0`, npm `11.9.0`, Edge headless, branch `codex/student-role-02-homework-ui`, HEAD `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` with the uncommitted scoped diff. Commands that use npm run from this table run in `frontends`; Git/probe commands run at the worktree root.

| Criterion/check | Command | Exit | Evidence |
| --- | --- | ---: | --- |
| Focused theme/scope tests | `\.\node_modules\.bin\vitest.cmd run mobile-core/src/shared/theme.test.ts mobile-core/src/features/homework/use-homework.test.ts` | 0 | 2 files, 7 tests passed. |
| Full mobile-core tests | `\.\node_modules\.bin\vitest.cmd run mobile-core/src` | 0 | 9 files, 28 tests passed. |
| Strict frontend typecheck | `npm run typecheck` | 0 | mobile-core `tsc`, PWA `vue-tsc`, TMA `vue-tsc` passed. |
| Frontend lint | `npm run lint` | 0 | mobile-core, PWA and TMA ESLint passed with `--max-warnings=0`. |
| PWA/TMA production builds | `npm run build` | 0 | PWA 120 modules and TMA 121 modules transformed and built. |
| Fixture validation | `npm run validate:fixtures` | 0 | 12 fixtures validated against `JS-STUDENT-01-r1`. |
| Contract tests | `npm run test:contract` | 0 | 11 tests passed. |
| Generated types drift check | `npm run generate:types:check` | 1 | **BLOCKED/outside scope:** pre-existing generated mobile-BFF drift; API/generated files are unchanged and forbidden by this contract. |
| Probe syntax | `node --check .agent/student-role-02/mobile-theme/theme-probe.mjs`; same for `focus-probe.mjs` and `today-probe.mjs` | 0 | All three probes parse. |
| Today theme runtime | `node .agent/student-role-02/mobile-theme/today-probe.mjs .agent/student-role-02/mobile-theme/today-evidence.json today` | 0 | 8 cases at 390×844/DPR1; see `evidence.md` and `today-evidence.json`. |
| Homework theme runtime | `node .agent/student-role-02/mobile-theme/theme-probe.mjs` | 0 | 8 cases at 390×844/DPR1; masks, palette, contrast and geometry recorded in `runtime-evidence.json`. |
| Homework keyboard runtime | `node .agent/student-role-02/mobile-theme/focus-probe.mjs` | 0 | Focus remained on the ACK-reordered completion control at 0/1/20/100/500ms. |
| Workspace whitespace | `git diff --check` | 0 | No whitespace errors; Git only reported existing LF/CRLF normalization warnings. |
| Frozen asset hash audit | `Get-ChildItem frontends/mobile-core/src/assets/homework-*.svg ... Get-FileHash -Algorithm SHA256` | 0 | Seven manifest source hashes match; derivative hashes are recorded in `source-hashes.json`. |

No dependency, lockfile, API/generated, backend or production runtime check was needed for this scope. The inherited `npm ci` check is already recorded green in `.agent/student-role-02/homework-ui/checks.md`; no dependency files changed afterward.

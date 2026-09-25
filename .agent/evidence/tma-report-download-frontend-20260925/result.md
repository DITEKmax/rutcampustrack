# TMA report-download frontend evidence

## Scope and criteria

Implemented the frozen frontend capability on branch `codex/tma-download-frontend-20260925`, based on `e185dc1982eadf00c909283459ce2c2b1e1510b9`. Teacher journal, teacher stats and headman weekly screens can use an optional report port; the existing Blob path remains active when the port is absent. The shared request boundary includes the frozen `HEADMAN_STATS` DTO, but that screen/client is outside this package.

The request client validates exactly one selector, format, ticket response, opaque relative route and expiry. The Telegram adapter builds an HTTPS URL only from the app origin, checks session and report-selection freshness before native dispatch, maps callback true/false to accepted/cancelled, and reports old-host unsupported capability. First 401 triggers the existing generation-bound session refresh exactly once; all other errors and a second 401 are returned without retry. Access tokens and tickets remain in memory for the request.

## Evidence and checks

Environment: Windows PowerShell, Node from the configured frontend dependency tree, Vitest 4.1.4, Vite 7.3.6. Temporary `frontends/node_modules` junctions to the existing dependency tree were removed after each command. No package install or lockfile mutation occurred. Check output was not persisted as separate log files; the command, exit code and result are recorded here.

| Check | Command / setup | Exit | Result |
|---|---|---:|---|
| Focused security/semantics tests | From `frontends/tma-vue`: `vitest run src/report-download-adapter.test.ts --configLoader runner` | 0 | 1 file, 22 tests passed. Includes exact DTO/query mapping, URL and filename trust checks, visible ticket status errors, 401 refresh-once with new bearer, stale session/query suppression, callback consent/cancel and unsupported old host. |
| mobile-core TypeScript | From `frontends/mobile-core`: `tsc -p tsconfig.json --noEmit` | 0 | Passed. |
| TMA Vue TypeScript | From `frontends/tma-vue`: `vue-tsc -p tsconfig.json --noEmit` | 0 | Passed with the root-authorized baseline `AdminSemesterScreen.vue` correction from MAIN `cfcbd12f` applied only during the check. Original worktree bytes were restored; their normalized Git blob matches this branch's `HEAD`. The baseline correction is not part of this inventory. |
| Targeted ESLint | `eslint --max-warnings=0` on the nine changed source/test files | 0 | Passed with zero warnings. |
| TMA production bundle | From `frontends/tma-vue`: `vite build --configLoader runner` | 0 | Passed; transformed 277 modules. Vite reported a 638.95 kB minified JS chunk over its 500 kB advisory threshold. It was left unchanged because it is outside this request. |

The unit tests use a fake Telegram host. No app/browser runtime was started and no genuine Telegram `downloadFile` callback or saved file was observed. Source and bundle evidence do not establish a successful Telegram save.

## Diff inventory

Product source:

- `frontends/mobile-core/src/shared/report-download-client.ts` — typed five-kind boundary, strict request/ticket validation, generation-bound issuance and one guarded 401 refresh/retry.
- `frontends/mobile-core/src/features/teacher/teacher-client.ts` — canonical journal/stats selector mapping; shared stats query serialization preserves existing ordered filters/sorts.
- `frontends/mobile-core/src/features/teacher/TeacherJournalScreen.vue` and `TeacherStatsScreen.vue` — optional native port paths; existing Blob paths remain the fallback when no port is injected.
- `frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts` and `HeadmanJournalScreen.vue` — current/selected week mapping and optional native port path, with existing Blob export retained.
- `frontends/tma-vue/src/telegram.ts` and `report-download-adapter.ts` — Telegram `downloadFile` edge adapter and fixed-origin HTTPS/path validation.
- `frontends/tma-vue/src/report-download-adapter.test.ts` — focused tests for trust boundary, generation refresh/staleness and native result semantics.

Evidence:

- `packet.md` — frozen contract and acceptance criteria, including refresh behavior.
- `result.md` — checks, runtime limits and this inventory.

No App/session/index/navigation/headman-stats wiring, backend, package manifest or lockfile changes are included. Root retains final wiring ownership pending the stats author's completion and explicit transfer; `HEADMAN_STATS` wiring remains pending. The root will run the independent Sol review once the whole user path is assembled.

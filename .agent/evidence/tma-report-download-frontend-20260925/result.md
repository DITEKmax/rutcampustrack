# TMA report-download frontend — final wiring evidence

## Scope and outcome

Verified the final frontend tree on branch `codex/tma-download-frontend-20260925`. Frozen product source is `e185dc1982eadf00c909283459ce2c2b1e1510b9`; PK-113 imported stats baseline is `6d90d2b0`. The original native-boundary work is commit `86edbe10`; the root-frozen comparison tree is `d223c8e9d606aab9ddbc72c9e59aa2c24246bb5c`. The authorized final correction tree is the changed product files recorded below on top of `6d90d2b0`.

TMA App now creates a report-ticket client from the existing generation-bound session owner, retaining the established single guarded refresh on ticket 401, then passes the Telegram port into teacher, headman, and assistant feature owners. Teacher journal/stats, headman weekly exports, and PK-113 stats select the same canonical query parameters as their screens. PK-113 ticket mapping omits group, semester, page, and null filter values. PWA App still supplies no report port, so the screens retain their existing Blob/filename path.

PK-113 hides data and export options unless their exact query key matches the successfully loaded response. Every new slice request clears the loaded key; after a failed request, the prior response remains hidden. Reset clears filters and sorts and remains available when only sorting is active. The focused regression check covers slice-key gating and sort-only reset state.

## Verification

Environment: Windows PowerShell, Node `v24.14.0`, Vitest `4.1.4`, Vite `7.3.6`. No dependency installation or lockfile changes. During checks only, a junction inside this owned worktree mapped `frontends/node_modules` to the existing read-only dependency tree at `C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/node_modules`; a second temporary worktree-local junction mapped `frontends/tma-vue/node_modules/@rct/mobile-core` to this worktree's `frontends/mobile-core`. Both junctions were removed after checks and both paths verified absent. No shared dependency entry was modified.

| Check | Command (run from package unless stated) | Exit | Result |
|---|---|---:|---|
| mobile-core TypeScript | `..\node_modules\.bin\tsc.cmd -p tsconfig.json --noEmit` | 0 | Passed. |
| PWA Vue types | `..\node_modules\.bin\vue-tsc.cmd -p tsconfig.json --noEmit` | 0 | Passed. Existing Blob export path remains the only PWA path. |
| TMA Vue types | `..\node_modules\.bin\vue-tsc.cmd -p tsconfig.json --noEmit` | 0 | Passed. |
| Focused native/session and slice/reset checks | `..\node_modules\.bin\vitest.cmd run src/report-download-adapter.test.ts src/tma-auth.test.ts --configLoader runner` | 0 | 2 files, 31 tests passed. Covers request mapping, URL trust boundary, refresh-once, stale request/session suppression, native callback semantics, unsupported host, PK-113 slice gating, and reset with sort-only state. |
| Scoped ESLint, task-owned source excluding inherited lint defects | `eslint --max-warnings=0` on all other changed task source/test files | 0 | Passed. |
| AssistantActionsScreen lint | ESLint with `--max-warnings=10` | 0 | Ten existing formatting warnings at lines 76–87, no errors. The report-port changes are elsewhere in the file. |
| StudentFeatureOwner lint | ESLint with `@typescript-eslint/no-unused-vars` disabled for this file only | 0 | The imported but unused `AssistantHomeworkScreen` at line 18 predates this task delta; report-port additions introduce no lint findings. |
| TMA production bundle | `..\node_modules\.bin\vite.cmd build --configLoader runner` | 0 | Passed; 283 modules. Vite emitted its existing >500 kB advisory for the 677.08 kB JS chunk; not changed in this request. |
| `git diff --check` | worktree root | 0 | Passed. Git also reported its normal LF-to-CRLF working-copy notice for changed files. |

The first Vitest attempt exited 1 because Vite resolved `@rct/mobile-core` through the main checkout's workspace junction, whose package did not contain this worktree's new export. I corrected only the owned worktree's temporary package junction, reran the same tests, and got 31/31 passing. The first unfiltered lint attempt exited 1 on the inherited AssistantActionsScreen formatting warnings and the pre-existing unused AssistantHomeworkScreen import. Those unrelated lines were verified unchanged in the task diff; no unrelated source cleanup was made. The scoped lint checks above verify the changed code without suppressing unrelated findings globally.

## Runtime evidence and limits

The tests use a fake Telegram host and synthetic in-memory bearer values. The TMA bundle and type checks establish source integration, not genuine Telegram host acceptance or a saved file. No live Telegram `downloadFile` callback/save was observed. PWA typecheck passed; no PWA production bundle/runtime was repeated because its bundler and PWA bootstrap were unchanged. Real Telegram runtime acceptance remains pending.

## Diff inventory

Product paths owned by this task (19):

- `frontends/mobile-core/src/index.ts`
- `frontends/mobile-core/src/shared/report-download-client.ts`
- `frontends/mobile-core/src/features/teacher/teacher-client.ts`
- `frontends/mobile-core/src/features/teacher/TeacherJournalScreen.vue`
- `frontends/mobile-core/src/features/teacher/TeacherStatsScreen.vue`
- `frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts`
- `frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue`
- `frontends/mobile-core/src/features/headman-stats/headman-stats-client.ts`
- `frontends/mobile-core/src/features/headman-stats/HeadmanStatsScreen.vue`
- `frontends/mobile-core/src/features/headman-group/AssistantActionsScreen.vue`
- `frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue`
- `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue`
- `frontends/mobile-core/src/shared/components/TeacherFeatureOwner.vue`
- `frontends/tma-vue/src/App.vue`
- `frontends/tma-vue/src/telegram.ts`
- `frontends/tma-vue/src/report-download-adapter.ts`
- `frontends/tma-vue/src/report-download-adapter.test.ts`
- `frontends/tma-vue/src/tma-session.ts`
- `frontends/tma-vue/src/tma-auth.test.ts`

Task evidence paths: `.agent/evidence/tma-report-download-frontend-20260925/packet.md` and `result.md`. No backend or package/config/lockfile changes. The imported PK-113 stats package is not counted in this task inventory.

For one review of the complete frontend task without treating the imported stats package as this task's change, compare `d223c8e9d606aab9ddbc72c9e59aa2c24246bb5c` to the final correction revision. This tree comparison includes the original native WIP paths from `86edbe10` and the final wiring/UI correction; `6d90d2b0` remains the imported stats baseline. The root will record the final correction revision in its review handoff.

## PK-113 review correction

After Sol review of `63da0435`, navigation/selection now remain rendered from the retained response while a request is loading or fails; filter/reset controls remain available when the retained response matches the same block+subject scope. Summary, table, and export remain hidden unless the exact current query has succeeded. With no subjects, the response still renders the block navigation and its «Вся группа» action. No installed Vue component-test/DOM harness is available, so no new framework was added: this transition was checked against the actual template conditions and load/reset/return handlers; live UI runtime remains pending. A first typecheck found the now-obsolete `Block` type at the changed handler; replacing it with `HeadmanStatsBlock` was followed by passing mobile-core, PWA, and TMA typechecks (exit 0 each). No full test suite or bundle was rerun.

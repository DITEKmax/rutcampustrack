# FE13 shutdown checkpoint

Дата: 2026-09-08. Работа остановлена по прямой команде владельца через root.
После этой записи не выполнять разработку, browser/UI QA, manifest, staging,
commit или новые проверки до нового прямого задания владельца.

## Exact state

- Assigned worktree / exact cwd: `C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack/.agent/worktrees/student-academic-ui`
- Branch: `codex/student-academic-ui`
- HEAD and immutable baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- Scope: FE13 Attendance8 + Statistics5; 23 assigned application paths plus
  `.agent/student-academic-ui/**` evidence.
- No reset, clean, rollback, move, stage or commit was performed during
  shutdown. Parent checkout foreign work was preserved.

The nested worktree `git status --short --untracked-files=all` snapshot below
contains every dirty path at shutdown. All entries are untracked WIP/evidence
(`??`) relative to the baseline; SHA256 is the file content at shutdown.

| status | path | SHA256 |
|---|---|---|
| ?? | `.agent/student-academic-ui/checks.json` | `83B7040C018A78CF6E8D14E5D0CDDB88D12999FE8782E80E674818B5010DC63D` |
| ?? | `.agent/student-academic-ui/decisions.md` | `44EDD167F980705C222680754BEC66A96B77CD226A0294B97DA923F37F3AD145` |
| ?? | `.agent/student-academic-ui/evidence.md` | `1ECE1F7012E4EB8F81372EE190654828D93F2D6A906AB150C30AB6BB690E8443` |
| ?? | `.agent/student-academic-ui/incident-audit.md` | `A195D5E98186A4F60EACA950FA2E97AB256BD49008DAD9309B17CF8288798E20` |
| ?? | `.agent/student-academic-ui/packet.md` | `E436701BAD1EB4D1BCED3E805D4D8613CE1F59AB107F1B150A024C516B927E7D` |
| ?? | `.agent/student-academic-ui/runtime-evidence.json` | `0102FE1CB71244EF8CBCEE2C7AA9CEE0571642FC4CFE8F19405FCB5C60EC2DA9` |
| ?? | `.agent/student-academic-ui/runtime/harness-dist/assets/index-Bkxtr7w-.js` | `B02ECED69546C26F1A72E1F30EEC6B44DA2A48C774960E6CF4244AEC07659B38` |
| ?? | `.agent/student-academic-ui/runtime/harness-dist/assets/index-Czca_ZBD.css` | `D9770021588FFE186C17AA49FDAA29635D67E695B3D9631AA6632B1BB56AE919` |
| ?? | `.agent/student-academic-ui/runtime/harness-dist/assets/onest-cyrillic-wght-normal-DXI_y_WF.woff2` | `37BC16874135C16134679B1DB25B87FE80EB9FCD4EF3666AF7C531BFDE204FE2` |
| ?? | `.agent/student-academic-ui/runtime/harness-dist/assets/onest-latin-ext-wght-normal-CnNj8hVb.woff2` | `391A9B24B5C46EBFF5D21F53CDB2EDAA31C3446DA3B722F038AB07D75D02A82` |
| ?? | `.agent/student-academic-ui/runtime/harness-dist/assets/onest-latin-wght-normal-CUIqqgP9.woff2` | `67849BCC11E02177442DA14AD954BFE1CC709553DAD137B5003449B303E83FC3` |
| ?? | `.agent/student-academic-ui/runtime/harness-dist/index.html` | `07DCA63D172C651505CEDC83DB6217D6F410D8E7D032E09F1675858E6DA50BFA` |
| ?? | `.agent/student-academic-ui/source-map.md` | `7227FC3C16A12D2EB479351974C710159E3A3A41C71680C71161DF31FAE32975` |
| ?? | `.agent/student-academic-ui/summary.md` | `CCF3BD4F17400F14B8526EE36981DD78273681F713FF5D304A14E8F5AC04A326` |
| ?? | `frontends/mobile-core/harness/attendance-statistics/Harness.vue` | `8EE4A0D403C6D8261FF0662B3428C342E8417F9F71008CA9B98835A26AB9E600` |
| ?? | `frontends/mobile-core/harness/attendance-statistics/fixtures.ts` | `BFBD824013214C20708894C6FCD73EABF40366CFB29FF81B86FA25C3EEDC76FE` |
| ?? | `frontends/mobile-core/harness/attendance-statistics/harness.pcss` | `CA6D1BD276168A1EEC7E3283D4EEA631885E73B4B878FD224AD17C90C0917949` |
| ?? | `frontends/mobile-core/harness/attendance-statistics/index.html` | `D5C75E85A6094B5C0C09D0A7DC7B29A05128D571BC9D8E859E410B5A6EECD9BD` |
| ?? | `frontends/mobile-core/harness/attendance-statistics/main.ts` | `1CB4A4736117F983FB2C5C846509B41E7A89F4896D43F0CE841914B59967E3E7` |
| ?? | `frontends/mobile-core/harness/attendance-statistics/tsconfig.json` | `692971CF8E01C21773CF92AB981E6136927B3D9F96CF1FC48B8C01BBB5F03756` |
| ?? | `frontends/mobile-core/harness/attendance-statistics/vite.config.ts` | `A1CDDE90D8D35735BFF5C8720DD590D834284618D7E40F3B8C64AB6167298636` |
| ?? | `frontends/mobile-core/src/features/attendance/AttendanceGraph.vue` | `217147BF1BDEEEBF365491800502DC82C5859FFEDDA4B49F4F1C8035359D722A` |
| ?? | `frontends/mobile-core/src/features/attendance/AttendanceLessonRow.vue` | `77D61D0549C1B2F6FD94D6D0035F88B11FEC749D9321A9D6A49E1510E7602F97` |
| ?? | `frontends/mobile-core/src/features/attendance/AttendanceScreen.vue` | `5B989216208768A6AC983E1C2CE6C775E86E57A15CA1DF75D3D92C68D39E8277` |
| ?? | `frontends/mobile-core/src/features/attendance/AttendanceSubjectList.vue` | `0139DAF91491D21ADDA1023BF911693647D636D95672C8D8B8F984739ED00BAC` |
| ?? | `frontends/mobile-core/src/features/attendance/attendance-screen.pcss` | `C0CE846FB39BA74AEC88A3B08196C5458E7A387C03153161D4881D98AA6C1283` |
| ?? | `frontends/mobile-core/src/features/attendance/attendance-tokens.pcss` | `06496BD820FF76F948BB3B219FDA037F7FE06DDA5D18EAC826B837A4C0EF80C7` |
| ?? | `frontends/mobile-core/src/features/attendance/attendance-view-model.test.ts` | `61E0C9298A09D12A0BB54453976574EA2BE88AEB6C8B2482A2CF52AAFE30E53C` |
| ?? | `frontends/mobile-core/src/features/attendance/attendance-view-model.ts` | `988E5FBC9BA22852E2341A6DC56B9910FD76B9BBCCB93BA6703DCDCB2C520BAE` |
| ?? | `frontends/mobile-core/src/features/statistics/StatisticsScreen.vue` | `34EF4D06F23D4CEA3E433F39B6E32ACBE3C30CD9624DEF9DBDD49B752978EF05` |
| ?? | `frontends/mobile-core/src/features/statistics/StatisticsSemesterChart.vue` | `C51A5A480244161CBE487764A1CF371807A9FD265A9D11F8F9E0EFFB3F5D39D3` |
| ?? | `frontends/mobile-core/src/features/statistics/StatisticsSubjectDetail.vue` | `7DCA5C918135CD7F216E7BF464A7416BE93A6A9800F973D279F4A9E0B40D1F85` |
| ?? | `frontends/mobile-core/src/features/statistics/StatisticsTypeCard.vue` | `51E77D9A7D450822D7D142D119C6A0CC87DA4973CA2979A4BEBB990C71E046FF` |
| ?? | `frontends/mobile-core/src/features/statistics/statistics-screen.pcss` | `45B5CE2DBD4360E9EEAA32CEBF68D321AF0224628EFBAD7301B2F84489D5A2A3` |
| ?? | `frontends/mobile-core/src/features/statistics/statistics-tokens.pcss` | `55B4DD7C29F174165E0C1408793B0DD4C0545EED974606A4322EF71DF8338489` |
| ?? | `frontends/mobile-core/src/features/statistics/statistics-view-model.test.ts` | `DC5935FF2841A3A77F7B2F169604FE25BC4940BC0652D87AE6F6666CB509674E` |
| ?? | `frontends/mobile-core/src/features/statistics/statistics-view-model.ts` | `5A00CA36345A5621D62C8D8F29135BC1AACAC99C6DA70525B6FFE72D28A69547` |

## Ready / WIP / remaining

Ready at pause: source code was frozen after the requested Attendance and
Statistics corrections. The scoped typecheck, lint, focused tests, production
build and loopback fixture smoke all passed. The harness renders real Vue
components, and the contract/evidence files are present.

WIP/remaining: final immutable 23-path manifest, final diff record and staging
were intentionally not performed because the owner paused all development.
Independent root browser evidence at 390x844 (screenshots, paint, keyboard and
focus return) is still required before fresh Sol review. The named Requests
form slot remains `OPEN`; live backend, auth, shell, PWA/TMA and external state
remain outside this lane.

Next step after a new direct owner command: root attaches independent CUA
evidence; then the assigned integration owner records the bounded manifest and
diff, stages only the allowed paths, runs the required final checks and sends
the stable packet to fresh Sol review. Resume code edits only for a recorded
review finding with a bounded correction packet.

## Last actual checks (before pause)

All commands below were run in the nested worktree's `frontends` directory and
returned exit code 0, as recorded in `.agent/student-academic-ui/checks.json`:

1. `npx --no-install vue-tsc -p mobile-core/harness/attendance-statistics/tsconfig.json --noEmit`
2. `npx --no-install eslint mobile-core/src/features/attendance mobile-core/src/features/statistics mobile-core/harness/attendance-statistics --max-warnings=0`
3. `npx --no-install vitest run mobile-core/src/features/attendance/attendance-view-model.test.ts mobile-core/src/features/statistics/statistics-view-model.test.ts` — 2 files / 7 tests passed.
4. `npx --no-install vite build --config mobile-core/harness/attendance-statistics/vite.config.ts --configLoader runner` — Vite transformed 40 modules and emitted `harness-dist`.
5. Harness fixture HTTP matrix: Vite loopback servers on `127.0.0.1:18210` and
   `:18211`; all 13 fixture URLs plus `/main.ts` returned HTTP 200.

Earlier concurrent Vite startup produced one EPERM rename in the shared local
`.vite` optimize cache (exit 1). The affected server became ready, HMR later
succeeded, the HTTP matrix and production build passed, and no product code was
changed for that local-cache diagnostic.

`git diff --check` was recorded as exit 0 before the pause; the planned final
`git diff --cached --check` after staging was not run because staging was
explicitly cancelled.

## Runtime ownership and shutdown

- Task-owned root-QA Attendance server: PID `26284`, port `18210`, stopped at
  shutdown with `Stop-Process -Id 26284 -Force`.
- No other owned process is intentionally left running. Earlier probe PIDs
  `42268` and `36000` were already recorded as stopped in `checks.json`.
- Generated `runtime/harness-dist` and logs were retained as WIP; no data or
  volumes were deleted.
- Leaf CUA had no browser provider (`listBrowsers`/`listApps` empty; browser
  creation unavailable), so this leaf makes no screenshot, DOM-paint or
  keyboard PASS claim.

Parent checkout `C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack` was already
dirty with unrelated BE/contracts/evidence paths. Those foreign paths were not
edited, staged, reset, cleaned, rolled back, committed or removed.


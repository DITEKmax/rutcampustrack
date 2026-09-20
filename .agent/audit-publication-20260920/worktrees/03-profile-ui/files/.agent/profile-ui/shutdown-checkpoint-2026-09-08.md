# Shutdown checkpoint — profile-ui leaf — 2026-09-08

## State

- owner instruction: PAUSE; this checkpoint is the final action for this run
- worktree: nested `profile-ui`; detached HEAD at `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`
- children: none; commits: none; deployment/migration: none
- dirty scope: `frontends/mobile-core/src/features/profile/` and `.agent/profile-ui/` only; all pre-existing nested/outer work was preserved under `evidence/resume-2026-09-08/preserved/` with manifest SHA256 `6777B39AF8B07F56AF2E57C9667310B89CADE227F63E4641333BFFE22566B95A`
- current Git status: `?? .agent/profile-ui/` and `?? frontends/mobile-core/src/features/profile/`; Git also emitted the known read-only global ignore warning for `C:\Users\maksd/.config/git/ignore`

## Runtime shutdown

- first failed runner PID `41992` was stopped at `2026-09-08T08:49:32.7844424Z`; it is absent
- actual task-owned server: Node PID `47364`, start `2026-09-08T09:02:16.2860962Z`, command `node .\\serve.mjs`, serving `127.0.0.1:18110` with strictPort
- ownership evidence: `Get-Process -Id 47364` returned `node.exe` at `C:\\Program Files\\nodejs\\node.exe`; own `server-api.stdout.log` printed `{"pid":47364,"host":"127.0.0.1","port":18110,"strictPort":true}` and listener was `127.0.0.1:18110`
- stopped only PID `47364` at `2026-09-08T09:18:39.9397760Z` using `Stop-Process -Id 47364 -Force`
- post-stop check exit `0` at `2026-09-08T09:18:52.0464763Z`: `alive=false`, exact port `18110`, `.NET IPGlobalProperties.GetActiveTcpListeners()` `free=true`, listeners empty
- task-owned preview close: CUA close returned browser unavailable because the preview had already been closed; no preview remains owned by this leaf

## Checks and evidence

- pre-correction state regression: focused Vitest exit `1`, `18 tests / 2 failed`; recorded in `evidence/resume-2026-09-08/reproduction-before-correction.md`
- state correction focused Vitest: `20/20`, exit `0`, Node `24.14.0`, Vitest `4.1.4`; raw output still needs to be copied into final checks evidence
- evidence harness build after coverage additions: Vite API build exit `0`, `44 modules`, Vite `7.3.6`; command used `node --input-type=module -e "import { build } from '.../vite/dist/node/index.js'; ... await build({...config,configFile:false})"`
- initial CLI `--configLoader runner` server returned a Vite module-runner-closed overlay for `App.vue`; this was isolated to the loader and replaced by evidence-only `serve.mjs` using Vite `createServer` with `configFile:false`
- mounted baseline: root and leaf IAB evidence showed `ACCOUNT_INVALIDATED` visible while three fields retained values, submit enabled, and fixture callback counter `Отправок: 0 -> 1`; root copy is `C:\\Users\\maksd\\.codex\\worktrees\\1456\\rutcampustrack\\.agent\\student-auth-a\\profile\\root-mounted-baseline.md`
- strict `vue-tsc` run before the callback-default cleanup: exit `1` with six `TS2379` errors caused by optional callback defaults set to `undefined`; cleanup was applied to the seven owned SFCs after that run, but no replacement type check was run before pause
- product behavioral correction still open: `SecurityScreen.vue` invalidation watch/secret clearing/submit gate has not been applied; no post-correction browser claim is made

## Current source hashes

| Path | Bytes | SHA256 |
|---|---:|---|
| `frontends/mobile-core/src/features/profile/MoreScreen.vue` | 1565 | `5675115D7535BC73A523C9A785F108EE2597831D7A368E0A4CFDBED464E8599E` |
| `frontends/mobile-core/src/features/profile/ProfileScreen.vue` | 4090 | `90257F0ABCDAF530ABADF3D55B33EA96DFA3B54E672CBCDD11A1A78CE0334EC1` |
| `frontends/mobile-core/src/features/profile/RoleSwitchScreen.vue` | 3930 | `8DD1D6FBFE83506B1A6A61D622BAAEDA8A5EB9DB65238B7ECFDA37EB4B690D34` |
| `frontends/mobile-core/src/features/profile/AppearanceScreen.vue` | 2495 | `C328BA5A6B45E98B32F1B7E0F4B192C4E7F5008AB380A98ED80BFC4929D237DE` |
| `frontends/mobile-core/src/features/profile/SecurityScreen.vue` | 10437 | `BCC12C11D21BD9F0F13544C5FBA5E6B34BD8FD5EAFED6D61D2E0D8208C5139AD` |
| `frontends/mobile-core/src/features/profile/SessionsScreen.vue` | 4893 | `42170FEF23273AC58AC3ED8E2E2B80C2D941CF5B511491A1ACF2777EDE40671` |
| `frontends/mobile-core/src/features/profile/AccountHistoryScreen.vue` | 3830 | `14C0DA205E751F36E92539CD2FD5B0B49F377237AD6B663E4EC092D4F96D9C74` |
| `frontends/mobile-core/src/features/profile/profile-screen.pcss` | 15180 | `D9AED24FE43CDB3CF0DBE44775B34CE28C1819D31E17494FAB7B1FB30447A142` |
| `frontends/mobile-core/src/features/profile/profile-types.ts` | 7314 | `C3939AA0025FA80526CA2659C9892089F8B25E8C6D1972278ED856154905B98A` |
| `frontends/mobile-core/src/features/profile/profile-state.ts` | 15865 | `44D73CEA725538BC955DC2ACDA42A8AA7B4B87EC7C6F859F53716F7F4FB6338B` |
| `frontends/mobile-core/src/features/profile/profile-state.test.ts` | 18156 | `EB082F29BE135D28C25BBFA8A423B9749E28BFEC10102AFDBC53E236AD7AE7A0` |
| `.agent/profile-ui/evidence/resume-2026-09-08/harness/App.vue` | 16626 | `56D9389BAE087410209E2224549729C23EE02E4E67A703173EE831EE41E452F7` |
| `.agent/profile-ui/evidence/resume-2026-09-08/harness/vite.config.mjs` | 928 | `B67B1828C23ADE53DEA15104A3CB144108AAE084D9CE43B3E64F10871CB37A8D` |
| `.agent/profile-ui/evidence/resume-2026-09-08/harness/postcss.config.mjs` | 171 | `8AE7B64F899DD593E456BBA28C821802FAFD3124EB66BD79AFFE81CA8BA9D744` |
| `.agent/profile-ui/evidence/resume-2026-09-08/harness/serve.mjs` | 511 | `DEC5D01E38A87F767785DF6F7F617CF9B5EB80D9A0788B34A3F2745478C5AF92` |
| `.agent/profile-ui/status.md` | 1053 | `3D7D5CCE875B1CF9D2ACB42950624FB92BCBC3335483B846E69860050BB7B1ED` |

## Open work after resume

- re-run strict `vue-tsc` after the callback-default cleanup
- apply the already scoped `SecurityScreen` correction only after owner resume; run focused tests/build as applicable
- restart exact `18110` only after a fresh free-port check and owner GO; complete post-correction mounted evidence, all seven-screen/theming/keyboard/responsive checks, PCSS source scan, final diff/manifest and required independent review
- update stale status/runtime annotations and preserve all current foreign work before further writes

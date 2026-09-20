# R5 shutdown checkpoint — paused by root

Date: 2026-09-09 (Europe/Moscow)
Owner: `/root/requests_ui_repair_r5`
Worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-ui-c`
Baseline/final checkout revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached)
Risk: S2
Pause reason: root applied the night environment-failure rule after the owned Vite session could not be addressed reliably. No further product edits, checks or browser capture are authorized in this pause.

## Scope status

`git status --short` at checkpoint:

```text
?? .agent/student-requests-ui-c/
?? frontends/.requests-harness/
?? frontends/mobile-core/src/features/requests/
```

The status is the expected untracked bounded feature/evidence scope in this detached baseline. No files outside the frozen 12 Requests product files, three harness files and `.agent/student-requests-ui-c/r5/**` were written by this leaf.

## Exact current 12 product hashes

SHA-256 values were read with `Get-FileHash -Algorithm SHA256` immediately before this checkpoint:

```text
8A05A2599D002D93E510989E611F9CD9F4B2FCE90CB047FBC65B654521E10070  frontends/mobile-core/src/features/requests/RequestLessonSelector.vue
773752B61836D349019D8A21A3F8407F2C2E5B09DE381A7826BA6C9A5CC44E3D  frontends/mobile-core/src/features/requests/ExcuseRequestScreen.vue
A45E97502A2817E406C7D71EDC95ADB2967BDB01E081B18FFE1D13614C3E6FEB  frontends/mobile-core/src/features/requests/RequestsScreen.test.ts
AB360F4A8B7BA7FC928FCFF762A3EB5EBC1B85E25CCAF615B9570BA06E37C5D6  frontends/mobile-core/src/features/requests/state.ts
ADBB12458A1DEDCBA087183AC3ACFD27EE03BD387AFD71D14855B10965E0D633  frontends/mobile-core/src/features/requests/state.test.ts
435415BF6C7BAE75E7C944E4E039FC26F81EDF56FEBBD3F16C9884948B74E3D5  frontends/mobile-core/src/features/requests/RequestAttachmentField.vue
611CD24AE5A4FDBDEB0ABE996989AE795C43C3C17A838DE7DAD5D149923C77FE  frontends/mobile-core/src/features/requests/requests.pcss
38AC351414EC1A8B5FA370C227EA433708753873193BA2CE3DECCDDF353A9ACA  frontends/mobile-core/src/features/requests/types.ts
1DE1A0D8E2A7749B0F4F3F92740D7B779DB091568E57BC3B39CD272DFC9F154E  frontends/mobile-core/src/features/requests/RequestsScreen.vue
5C75A728A27B1262FEC30806FF6D49F8A7C6D3AD65861C39166BAAEF5C69A02A  frontends/mobile-core/src/features/requests/RequestCard.vue
A8E04F078A8A1B945D93EFA7810BCDA7AFF65D2313FA06731E12000D948D3A93  frontends/mobile-core/src/features/requests/RequestTypeScreen.vue
7448D19063DEBDEAC5E8D0FDF44B52F61C4A13A448C07F99114944F38198DB3E  frontends/mobile-core/src/features/requests/LateCheckinRequestScreen.vue
```

## Exact current three harness hashes

```text
E22576A4DABFD28530D79401985AB37B274EF8A1BE8D76BBD008447288673958  frontends/.requests-harness/main.ts
CF6D831297750E0796548ACC00F86A1FC2BF114F2D0292A507AFF67A57C657D7  frontends/.requests-harness/index.html
2922EB784D906E93E05ECD37669B0740BED8599A8F9F75D1E5C42752142C81BE  frontends/.requests-harness/harness.css
```

## Completed checks before pause

All commands ran in Windows PowerShell on Node `v24.14.0`, npm `11.9.0`, Vite `7.3.6`, Vitest `4.1.4` unless noted. The latest successful results are:

| Criterion/evidence | Command | Exit | Result |
|---|---|---:|---|
| package typecheck | `npm run typecheck --workspace @rct/mobile-core` from `frontends` | 0 | `tsc -p tsconfig.json --noEmit` passed after removing the test-only `node:fs` assertion that was incompatible with the existing package `types: []` boundary |
| package lint | `npm run lint --workspace @rct/mobile-core` from `frontends` | 0 | ESLint source/scripts/tests passed with `--max-warnings=0` |
| Requests SSR | `node_modules/.bin/vitest.cmd --config pwa-vue/vite.config.ts --configLoader runner --root . run mobile-core/src/features/requests/RequestsScreen.test.ts --reporter=verbose` from `frontends` | 0 | 1 file, 6 tests passed |
| state behavior | same Vitest command with `mobile-core/src/features/requests/state.test.ts` | 0 | 1 file, 6 tests passed; includes two-ID draft survival through remove + update |
| task SFC typecheck | `frontends/node_modules/.bin/vue-tsc.cmd -p .agent/student-requests-ui-c/r5/tsconfig.requests.json --noEmit` from worktree root | 0 | Requests `.ts`/`.vue` files and declaration boundary passed |
| PCSS transform | PostCSS `postcss-mixins` transform over existing `mobile-core/src/styles/tokens.pcss` plus Requests PCSS (the source import is intentionally resolved by Vite) | 0 | `POSTCSS_REQUESTS_OK 26757` |
| PCSS unit guard | `rg -n "\b[0-9]+(?:\.[0-9]+)?px\b" mobile-core/src/features/requests/requests.pcss` from `frontends` | 0 | `NO_PCSS_PX` |
| focus rule source guard | multiline `rg` for `.request-attachment-picker:focus-within` and accent outline | 0 | found lines 757–758 |
| scope marker guard | `rg -n "TODO|FIXME|console\." frontends/mobile-core/src/features/requests frontends/.requests-harness -g '!*.map'` | 0 | `NO_SCOPE_MARKERS` |
| contract tests | `npm run test:contract` from `frontends` | 0 | 11 tests passed |
| initial port free check | `Get-NetTCPConnection -State Listen -LocalPort 18540,18541` | 0 | `NO_LISTENERS_18540_18541` before starting owned Vite |

An earlier direct PostCSS call without the Vite-resolved token import returned an undefined-mixin error; it was a check invocation limitation, not a product failure. The bounded merged-token transform above passed, and the actual Vite server reached ready state.

## Runtime/tool facts and unfinished work

- Owned Vite was started from `frontends` with `vite.cmd --host 127.0.0.1 --port 18540 --strictPort`; session `15183` reported `VITE v7.3.6 ready` and `http://127.0.0.1:18540/`.
- Root attempted to interrupt session `15183` and received `Unknown process`; root separately confirmed `NO_LISTENERS_18540_18541` after the attempt. This leaf performed no process kill or destructive cleanup.
- Browser/headless capture did not start before the pause. There are no R5 screenshot paths or hashes. The five required captures (open, archive, type, excuse recovery/focus, late), AX/focus inspection, overflow/theme/reduced-motion observations and final listener proof remain unfinished pending root resume/decision.
- `tsconfig.requests.json` exists only under this evidence scope and points its `typeRoots` at the existing frontend Node declarations; no package/root config was changed.
- Backend/proto/OpenAPI/BFF/generated integration remains OPEN by contract and was untouched.

# R6 resume addendum — preflight before runtime

Date: 2026-09-09 (Europe/Moscow)
Worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-ui-c`
Baseline revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`
Immutable R5 checkpoint SHA-256:
`1ED29E81E3B802993044BF81064ADE3FCCF58F73B8748C9447B03C12356CBE75`

The preflight was run before any R6 harness/runtime action. All 15 frozen
product/harness files matched the R5 checkpoint hashes (15/15):

| Group | SHA-256 | Path |
|---|---|---|
| product | `8A05A2599D002D93E510989E611F9CD9F4B2FCE90CB047FBC65B654521E10070` | `frontends/mobile-core/src/features/requests/RequestLessonSelector.vue` |
| product | `773752B61836D349019D8A21A3F8407F2C2E5B09DE381A7826BA6C9A5CC44E3D` | `frontends/mobile-core/src/features/requests/ExcuseRequestScreen.vue` |
| product | `A45E97502A2817E406C7D71EDC95ADB2967BDB01E081B18FFE1D13614C3E6FEB` | `frontends/mobile-core/src/features/requests/RequestsScreen.test.ts` |
| product | `AB360F4A8B7BA7FC928FCFF762A3EB5EBC1B85E25CCAF615B9570BA06E37C5D6` | `frontends/mobile-core/src/features/requests/state.ts` |
| product | `ADBB12458A1DEDCBA087183AC3ACFD27EE03BD387AFD71D14855B10965E0D633` | `frontends/mobile-core/src/features/requests/state.test.ts` |
| product | `435415BF6C7BAE75E7C944E4E039FC26F81EDF56FEBBD3F16C9884948B74E3D5` | `frontends/mobile-core/src/features/requests/RequestAttachmentField.vue` |
| product | `611CD24AE5A4FDBDEB0ABE996989AE795C43C3C17A838DE7DAD5D149923C77FE` | `frontends/mobile-core/src/features/requests/requests.pcss` |
| product | `38AC351414EC1A8B5FA370C227EA433708753873193BA2CE3DECCDDF353A9ACA` | `frontends/mobile-core/src/features/requests/types.ts` |
| product | `1DE1A0D8E2A7749B0F4F3F92740D7B779DB091568E57BC3B39CD272DFC9F154E` | `frontends/mobile-core/src/features/requests/RequestsScreen.vue` |
| product | `5C75A728A27B1262FEC30806FF6D49F8A7C6D3AD65861C39166BAAEF5C69A02A` | `frontends/mobile-core/src/features/requests/RequestCard.vue` |
| product | `A8E04F078A8A1B945D93EFA7810BCDA7AFF65D2313FA06731E12000D948D3A93` | `frontends/mobile-core/src/features/requests/RequestTypeScreen.vue` |
| product | `7448D19063DEBDEAC5E8D0FDF44B52F61C4A13A448C07F99114944F38198DB3E` | `frontends/mobile-core/src/features/requests/LateCheckinRequestScreen.vue` |
| harness | `E22576A4DABFD28530D79401985AB37B274EF8A1BE8D76BBD008447288673958` | `frontends/.requests-harness/main.ts` |
| harness | `CF6D831297750E0796548ACC00F86A1FC2BF114F2D0292A507AFF67A57C657D7` | `frontends/.requests-harness/index.html` |
| harness | `2922EB784D906E93E05ECD37669B0740BED8599A8F9F75D1E5C42752142C81BE` | `frontends/.requests-harness/harness.css` |

Preflight command: PowerShell `Get-FileHash -Algorithm SHA256` over the exact
15 paths above, followed by `Get-NetTCPConnection -State Listen
-LocalPort 18540,18541 -ErrorAction SilentlyContinue`; exit `0`.
Evidence: command output recorded in the R6 task log; result `15/15 MATCH`
and `NO_LISTENERS_18540_18541`.

Git status at preflight contained only the expected untracked bounded feature,
harness, and agent scopes from the frozen detached worktree; no R6 runtime had
started and no product/harness file had been written.

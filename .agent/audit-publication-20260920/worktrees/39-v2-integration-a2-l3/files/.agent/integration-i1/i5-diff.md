# I5 R4 integration diff and provenance

Target: `codex/v2-integration-a2-l3` at
`b8220ac92125a8afa37598b270aa4fab7aa1f470`. Source: read-only
`codex/v2-requests-ui` at the same revision. Source ledger blob:
`7f6071b49c11cbb1933e125bf13f0ac8f46f0c84`.

## Product inventory

The accepted I4 product union has 44 paths and remained byte-identical before
and after transfer. The R4 source contributes exactly 14 product paths: ten
modified files and four added files. Their source Git blob hashes and final
target SHA256 values are:

| Product path | Source Git blob | Final target SHA256 |
| --- | --- | --- |
| `frontends/mobile-core/src/features/requests/RequestAttachmentField.vue` | `de027277796478aecfbb0984da377b9ad90cae17` | `E3FB0F2CB1567A9BA26D63C451153D0DD4176BD6CC618235472C87120A403B37` |
| `frontends/mobile-core/src/features/requests/RequestCard.vue` | `07691522d7f1c99574c8561cd6c683c7074fed82` | `A82FB839AB93FC15B35E54A54020C64583E4502D1C57A8BC733D63809102BFC9` |
| `frontends/mobile-core/src/features/requests/RequestsScreen.test.ts` | `506ef767ac2beef1c2980fc7b1ef5b36cadac33e` | `3780B1FB3E9A0BBEBD227A3368E169CD03B20D37DC683DA172F160F6C2FC314D` |
| `frontends/mobile-core/src/features/requests/RequestsScreen.vue` | `ac4be7422419f28cbefc340f992c51cdf76be4be` | `2F1561D3B98714C46CD63E3D38990E5DB945909AFB38B7B8722E289217D098D8` |
| `frontends/mobile-core/src/features/requests/requests-controller.test.ts` | `22ae429ded7b18f992f61e383f6dac6be8665eed` | `9AE7D8598FFD3BCC4FB7253DF8E646BEA7F514B89C878A823C24174AD0F6568A` |
| `frontends/mobile-core/src/features/requests/requests-controller.ts` | `01df8922d4ccb8ca58df1ea6067a74ff9d587ef1` | `3AF75AA67AA0F27BEA21CC393D0CFDB184748710E2ADAE397ECC1E4726C64F85` |
| `frontends/mobile-core/src/features/requests/requests.pcss` | `76b9ebd6cf7050ec7f139fd992eb99cc360cb488` | `9A8C139BBA15D931D39F05CB55579E9396AF3BB04E570DA8EBBC8FA2FD8EFF48` |
| `frontends/mobile-core/src/features/requests/types.ts` | `56f600b1c6b87e4d48686712c125304caf6838af` | `0ABE090F32EDA8AC08260C537CA3C38816A4A8F14750A1AD46313F6B94027F52` |
| `frontends/mobile-core/src/features/requests/attachment-validation.ts` | `5def017488bf1ef660a011882c19f547be373e64` | `7953EC9B0A4F558EE99473825B18D61711FD424675F262D17C300A02E4A4B9C6` |
| `frontends/mobile-core/src/features/requests/attachment-validation.test.ts` | `8afa541b6110b38636386a55847a4f21054cc708` | `B04D4FF29FEE30C644FB214CB263CFD59BA3D94D9AE852EF08364AAD846093F6` |
| `frontends/mobile-core/src/features/requests/request-attachment-action.ts` | `a57eb823083c3cfef2f7fe4eba886b6647ddcef0` | `D34D0319DBC217F8B11A0194BEB00705F17C823E33DB33E81FC2061F97601FEF` |
| `frontends/mobile-core/src/features/requests/request-attachment-action.test.ts` | `a1ba87f61cc1ac74294de30664c60e24d3cf923d` | `1FC0DBAD10232F474A7A1F73DEECFB4273DA2B8878A04A19234A380E88366665` |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts` | `387035a7075c501bc22a2bb3ac7771ac945dd64b` | `D90E583F3D5C90E01E8351C211B1F89052A67A5297D841BA4CDC764010681348` |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue` | `f1690c1fda0658ff68482d5dd6b56f0b4c1ce475` | `655F9CC6156DA9CB72BE91BFF9E2EE76A1348E7FF645328E50269EDFB8A72F7F` |

No R4 product path overlapped a changed I4 path. The target product set is the
exact 44+14 union; no unrelated frontend path was added. The four source-added
files remain classified as added in R4's accepted inventory.

## Supplemental harness input

`.agent/v2-requests-ui/vitest-client-environment.ts` was copied exactly as
required test environment input: SHA256
`CF3212D7D65917AB03161C487471EFD6A088B7463A0EF76C542D93CA65782205`, 221
bytes. It is intentionally excluded from the 58 product paths and from the
product manifest; the separate classification prevents a misleading count.

## Preserved scope and limits

I4's 44 rows matched their frozen manifest after transfer. The source R4
worktree was not edited. Only the accepted R4 product bytes and the one
required harness file were copied. No source packet, evidence, history,
cache, dependency, generated file, or backend path was imported. Browser,
fullservice, backend, deployment, and E/main promotion remain open.

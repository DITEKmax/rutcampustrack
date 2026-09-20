# R4 source diff ledger

Initial state: no product diff at worktree creation; baseline is E
`b8220ac92125a8afa37598b270aa4fab7aa1f470`.

Planned bounded files:

- controller freshness tokens and MIME/extension validation;
- deferred controller tests for independent resource completion;
- owner attachment action state, synchronous popup/open fence, and teardown;
- RequestCard/RequestsScreen inline attachment pending/error projection;
- RequestAttachmentField matching pair validation;
- focused PCSS additions using existing Requests semantic variables only.

R5 paths, generated files, backend sources, lockfiles, and unrelated features are
excluded. Older counts in this ledger are historical milestone snapshots. The
current source delta is exactly 14 product files: 10 modified and 4 added.
The tracked portion remains 10 files, `685` insertions and `88` deletions; the
four added feature files are included in the product total below.

Complete current product inventory (10 modified + 4 added):

- Modified: `frontends/mobile-core/src/features/requests/RequestAttachmentField.vue`,
  `frontends/mobile-core/src/features/requests/RequestCard.vue`,
  `frontends/mobile-core/src/features/requests/RequestsScreen.test.ts`,
  `frontends/mobile-core/src/features/requests/RequestsScreen.vue`,
  `frontends/mobile-core/src/features/requests/requests-controller.test.ts`,
  `frontends/mobile-core/src/features/requests/requests-controller.ts`,
  `frontends/mobile-core/src/features/requests/requests.pcss`,
  `frontends/mobile-core/src/features/requests/types.ts`,
  `frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts`,
  `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue`.
- Added: `frontends/mobile-core/src/features/requests/attachment-validation.ts`,
  `frontends/mobile-core/src/features/requests/attachment-validation.test.ts`,
  `frontends/mobile-core/src/features/requests/request-attachment-action.ts`,
  `frontends/mobile-core/src/features/requests/request-attachment-action.test.ts`.

Current product blob hashes are recorded in `evidence.md` and repeated here
for the integration handoff:

| Product path | Git blob hash |
|---|---|
| `frontends/mobile-core/src/features/requests/RequestAttachmentField.vue` | `de027277796478aecfbb0984da377b9ad90cae17` |
| `frontends/mobile-core/src/features/requests/RequestCard.vue` | `07691522d7f1c99574c8561cd6c683c7074fed82` |
| `frontends/mobile-core/src/features/requests/RequestsScreen.test.ts` | `506ef767ac2beef1c2980fc7b1ef5b36cadac33e` |
| `frontends/mobile-core/src/features/requests/RequestsScreen.vue` | `ac4be7422419f28cbefc340f992c51cdf76be4be` |
| `frontends/mobile-core/src/features/requests/requests-controller.test.ts` | `22ae429ded7b18f992f61e383f6dac6be8665eed` |
| `frontends/mobile-core/src/features/requests/requests-controller.ts` | `01df8922d4ccb8ca58df1ea6067a74ff9d587ef1` |
| `frontends/mobile-core/src/features/requests/requests.pcss` | `76b9ebd6cf7050ec7f139fd992eb99cc360cb488` |
| `frontends/mobile-core/src/features/requests/types.ts` | `56f600b1c6b87e4d48686712c125304caf6838af` |
| `frontends/mobile-core/src/features/requests/attachment-validation.ts` | `5def017488bf1ef660a011882c19f547be373e64` |
| `frontends/mobile-core/src/features/requests/attachment-validation.test.ts` | `8afa541b6110b38636386a55847a4f21054cc708` |
| `frontends/mobile-core/src/features/requests/request-attachment-action.ts` | `a57eb823083c3cfef2f7fe4eba886b6647ddcef0` |
| `frontends/mobile-core/src/features/requests/request-attachment-action.test.ts` | `a1ba87f61cc1ac74294de30664c60e24d3cf923d` |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts` | `387035a7075c501bc22a2bb3ac7771ac945dd64b` |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue` | `f1690c1fda0658ff68482d5dd6b56f0b4c1ce475` |

Owner/harness fence SHA256 values are part of the current evidence:

| Path | SHA256 | Bytes |
|---|---|---:|
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue` | `655F9CC6156DA9CB72BE91BFF9E2EE76A1348E7FF645328E50269EDFB8A72F7F` | 36033 |
| `frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts` | `D90E583F3D5C90E01E8351C211B1F89052A67A5297D841BA4CDC764010681348` | 11148 |
| `.agent/v2-requests-ui/vitest-client-environment.ts` | `CF3212D7D65917AB03161C487471EFD6A088B7463A0EF76C542D93CA65782205` | 221 |

Complete handoff inventory: `.agent/v2-requests-ui/AGENTS.md`,
`checks.json`, `diff.md`, `evidence.md`, `packet.md`, `runtime-evidence.json`,
`summary.md`, and `vitest-client-environment.ts`. The artifact hashes
(excluding this self-referential ledger) are recorded below after the last
edit. Current union checks are 5 files / 52 tests plus typecheck, lint, and
diff-check exit 0; runtime, integration, and independent Sol remain open.

Handoff artifact blob hashes:

| Path | Blob hash |
|---|---|
| `.agent/v2-requests-ui/AGENTS.md` | `1bcfd57b457f54129ae6b4db305f0cdb3c0373e3` |
| `.agent/v2-requests-ui/checks.json` | `227ef886f29c4d21b0f484476f6c5ee71cb0ce7b` |
| `.agent/v2-requests-ui/evidence.md` | `2b32a172aeb9f793381e42a09e4eb30ab6fe8167` |
| `.agent/v2-requests-ui/packet.md` | `200d68314aa1cb3bba6fab69917e9089ac5fe8c4` |
| `.agent/v2-requests-ui/runtime-evidence.json` | `8db2d664b0c2a7a6a479fe200141f30f6a75a2ce` |
| `.agent/v2-requests-ui/summary.md` | `89b890e07eae51772b6c91911468f9ba02ab1041` |
| `.agent/v2-requests-ui/vitest-client-environment.ts` | `79941beda8065851d5f2681020ca3daaf09b7a4c` |

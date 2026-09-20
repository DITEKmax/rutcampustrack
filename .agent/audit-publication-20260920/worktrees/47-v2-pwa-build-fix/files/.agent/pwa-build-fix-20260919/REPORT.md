# PWA build fix evidence — 2026-09-19

## Scope

S1 strict TypeScript repair in exactly two Requests Vue components. The working
tree is a fresh checkout at `d7ec16572db325d944f1a1fbd3b4960b827d09c3`; the
dirty original checkout and accepted H44 build outputs were not touched.

## Criteria

- `npm run build --workspace @rct/pwa-vue` passes `vue-tsc` and Vite.
- Existing Requests feature tests pass through the штатный PWA Vite config.
- Optional `attachmentStates` stays absent when no value is supplied; attachment
  UI behavior and all other defaults remain unchanged.
- Diff contains only `RequestCard.vue` and `RequestsScreen.vue`; no dependency,
  lockfile, style, API, or compiler changes.

## Evidence

The baseline build reproduced TS2379 in both components for explicit
`attachmentStates: undefined` under `exactOptionalPropertyTypes: true`. Root's
recorded follow-up decision approved the bounded contract correction: make the
optional prop explicitly accept `undefined`, retain the existing runtime default
absence, and keep the direct Screen→Card binding. No empty map is created and
no assertion or suppression is used.

Source hashes after the final correction:

- `RequestCard.vue`: `AE8C30BDAFC1FC53F3886126CD670B3D7623E7912350CE56F984FE3818AD609E`
- `RequestsScreen.vue`: `94C3C7C440E9DE34D88A0840AAD305D6430533B0A89635B5E5A0F4C86D638AD6`

## Checks

See `checks.json` and the raw logs under `logs/`. Final PWA build is PASS exit
0; final mobile-core lint is PASS exit 0; final Requests suites are PASS exit 0
with 5 files and 54 tests; `git diff --check` is PASS exit 0. Offline `npm ci`
was PASS exit 0. The earlier lint exit 1 from the default-less intermediate
variant is preserved in `logs/lint-final.log` and corrected by the approved
explicit `| undefined` prop type plus ordinary default.

The first test invocation without a Vue config failed before test execution;
the штатный `pwa-vue/vite.config.ts` then ran the same suites successfully after
the exact sandbox access denial was escalated narrowly. No code or config was
changed by the escalation.

## Runtime evidence

The PWA production build and SSR Requests tests are the applicable runtime
evidence. No browser, deployed product, backend, Docker, or Gradle runtime claim
is made; those surfaces are outside this contract.

## Diff

```diff
frontends/mobile-core/src/features/requests/RequestCard.vue
-  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>>
+  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>> | undefined

frontends/mobile-core/src/features/requests/RequestsScreen.vue
-  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>>
+  attachmentStates?: Readonly<Record<string, RequestAttachmentViewState | undefined>> | undefined
```

Final `git diff --stat`: 2 files changed, 2 insertions, 2 deletions.

## Limitations

The test harness requires the штатный PWA Vite config and a narrow sandbox
escalation to load that config; no code or config changed during escalation.
No browser, deployed product, backend, Docker, or Gradle runtime claim is made.
Fresh independent review is still required before any commit or integration.

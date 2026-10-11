# Frontend practices

Status: `verified / independent audit PASS`  
Work unit: `WU-020-FRONTEND`  
Anchors: four immutable REF-PRIMARY commits plus `REF-SAMPLES@1288bb81051c332f53fc26fc561ae3f16d1063e53f93d92dcef5c5ff2afc55ae`  
Collected by: `frontend_conventions_analyst`  
Canonicalized by: `main`

## Snapshot and coverage

Coverage follows the exact assignments in `research/control/scope.yaml`: shared-library exports and styles, shell composition/router/tooling, two representative remotes, nine sample applications, and the approved frontend guidance subset. The quarantined dashboard `app/src/App.vue` was excluded entirely. Assets/media, `.npmrc`, generated/build output, product-semantic guidance, and all Figma surfaces were not read. No code, tests, linters, builds, or package managers were executed.

The four primary locators use immutable commit OIDs. SAMPLE findings are snapshot-only and cannot establish history. Branch evolution remains reserved for `WU-050-BRANCHES`.

## Architecture and boundaries

Assigned frontend units are independently buildable Vite packages; reuse crosses package/repository boundaries through a published shared library and module federation (`E-FE-000001`). This is a deployability tendency, not proof that every target feature needs a separate repository.

The shell declares remotes, while remote applications publish `remoteEntry.js` and route modules; framework/router/state/transport/shared-UI dependencies are configured as shared (`E-FE-000002`). The route module is the dominant remote public API, and the shell registers imported routes dynamically (`E-FE-000003`).

Inference: the shell is the composition root. Remotes own capability routes and UI; global guards, navigation shell, and cross-cutting overlays should have one owner. Exact polyrepo topology should depend on real release/runtime isolation.

## Vue application structure and microfrontends

Eight assigned units show feature ownership with combinations of `components`, `model`, `api` or `queries`, `constants`, `utils`, `types`, and styles (`E-FE-000007`). Shared/library and newer slices expose supported symbols through `index` barrels and package export maps (`E-FE-000008`). Deep imports are observed leakage.

Recommended placement for new target slices is a feature-owned public API with UI, state, transport, types, and pure helpers separated internally. Pages compose features; they should not become transport/service owners.

## Routing and layouts

The host owns global authentication/access/leave guards, while remotes own nested route tables and sometimes local guards (`E-FE-000004`). Route trees tend to use unique names, redirects by name, relative children, and layout/navigation metadata (`E-FE-000005`).

Federation is the primary lazy boundary in current primary applications; most remote pages are eagerly imported. A newer TypeScript sample lazy-imports five pages (`E-FE-000006`). Therefore route-level lazy loading is an adaptation opportunity, not a dominant observed rule.

The target recipe should validate route-name/path uniqueness, await remote registration before mount, and keep metadata vocabulary small and standardized. One sample duplicates a child path/name; another exposes a stale file extension (`E-FE-000018`).

## Components, composables, stores, and services

Sampled components expose bounded interfaces through typed props/emits in newer TypeScript code and through props, slots, model bindings, and attribute forwarding in other assigned components (`E-FE-000009`). Feature state usually lives in Composition-API Pinia stores under `model`, transport functions under `api` or `queries`, and reusable reactive helpers follow `useX` (`E-FE-000010`).

Observation: this is a repeated placement tendency, but naming is not absolute; service/store variants and module-scoped reactive state exist. Recommendation: new code should use typed props/emits and keep transport calls outside pages/components.

## Naming and placement rules

PascalCase components, camelCase symbols, `useX` composables/stores, kebab-case feature directories, and uppercase constants are dominant across ten corroborating units (`E-FE-000011`). Prescriptive documentation alone is insufficient; code also contains store-name, spelling, and singular/plural exceptions.

Public barrels are the intended cross-feature boundary (`E-FE-000008`). The snapshot also contains explicitly named old directories, generated-looking JavaScript siblings, duplicate routes, and unmatched federation paths (`E-FE-000018`); their chronology is unknown. Recommendation: do not reproduce those exception classes in new structure.

## PCSS/PostCSS, tokens, and responsive styles

All thirteen assigned PostCSS configs repeat imports, prefixing, advanced variables, nesting, production minification, and px-to-rem conversion (`E-FE-000012`). Shared variables and max-width responsive mixins centralize breakpoints, colors, and dimensions (`E-FE-000013`).

Two generations coexist: legacy global PCSS/BEM aggregation and newer `ui` plus colocated module PCSS; 39 module files and 44 scoped Vue style blocks were counted in samples (`E-FE-000014`). Neither generation alone is universal. New code can standardize modules while isolating legacy global styles behind migration boundaries.

## TypeScript, aliases, linting, and formatting

All thirteen assigned package surfaces map `@` to `src`; strict TypeScript appears in four newer samples rather than as a primary baseline (`E-FE-000015`). Flat ESLint is repeated in thirteen configs. Newer TypeScript samples use stronger Vue/TypeScript presets and Prettier conflict suppression (`E-FE-000016`).

No Stylelint config was found in approved surfaces. Several `lint` scripts mutate files with `--fix`, and one legacy package lacks a lint script. Target CI should separate non-mutating `lint` from `lint:fix`, apply strict TypeScript to new code, and add style linting deliberately.

## Testing, fixtures, mocks, and quality gates

No test/spec file or test script was found across thirteen approved package surfaces (`E-FE-000017`). Guidance prescribes Vitest/Vue Test Utils, but it has no code corroboration. This is a verified-scope gap candidate, not a reference practice.

Target work must add unit/component/router/store/remote-contract tests and define a fixture/mock layout. Exact fixture placement is deferred because the sources provide no implemented example.

## Branch differences and legacy patterns

Branch evolution is not inferred here. Snapshot samples show four exception classes: duplicate routes, unmatched federation paths, generated-looking JS siblings, and explicit old directories (`E-FE-000018`). JavaScript/TypeScript, global/module/scoped styling, and eager/lazy routing coexist; the unversioned SAMPLE source does not establish migration intent or chronology.

## Transfer candidates

| Practice | Mode | Evidence IDs | Preconditions | Risks |
|---|---|---|---|---|
| One shell-owned composition and guard boundary | adopt | `E-FE-000002`–`E-FE-000004` | justified remote boundaries | shell coupling |
| Remote route module as a small public contract | adopt | `E-FE-000003` | route registration protocol | stale exposes |
| Feature ownership plus public barrels | adopt | `E-FE-000007`, `E-FE-000008` | lintable boundary rules | deep-import leakage |
| Typed component APIs and colocated state/transport separation | adopt | `E-FE-000009`, `E-FE-000010` | strict TS for new code | mixed legacy APIs |
| Shared tokens, mixins, reproducible PostCSS | adopt | `E-FE-000012`, `E-FE-000013` | owned token package | global coupling |
| Independent deployable packages only for real isolation | adapt | `E-FE-000001` | release/runtime need | polyrepo drift |
| Module PCSS for new slices; isolate global legacy styles | adapt | `E-FE-000014` | migration boundary | cascade conflicts |
| Lazy-load local pages while retaining federation boundary | adapt | `E-FE-000006` | loading/error UX | fragmentation |
| Strict TS, split lint/lint:fix, add Stylelint | adapt | `E-FE-000015`, `E-FE-000016` | staged migration | initial churn |
| Add Vitest/Vue Test Utils and remote-contract tests | adapt | `E-FE-000017` | target-owned test design | no reference layout |
| Deep imports and stale/generated artifacts | avoid | `E-FE-000008`, `E-FE-000018` | none | boundary erosion |
| Exact polyrepo topology | defer | `E-FE-000001` | deployment decision | premature overhead |
| Canonical fixtures/mocks layout | defer | `E-FE-000017` | target test strategy | unsupported invention |

## Counterexamples and unknowns

- Standalone and consolidated applications disprove universal federation/polyrepo rules (`E-FE-000001`, `E-FE-000002`).
- Guard metadata and store/service naming are inconsistent (`E-FE-000004`, `E-FE-000010`, `E-FE-000011`).
- Eager and lazy routes coexist (`E-FE-000006`).
- Global PCSS, modules, and scoped styles coexist (`E-FE-000014`).
- Strict TypeScript is not yet universal (`E-FE-000015`).
- Tests, fixtures, mocks, and Stylelint have no implemented approved example (`E-FE-000016`, `E-FE-000017`).
- The quarantined dashboard root component creates a deliberate layout-coverage gap.
- Federation options do not by themselves prove runtime singleton enforcement.

## Evidence index

| Evidence ID | Primary locator | Count | Confidence |
|---|---|---:|---|
| `E-FE-000001` | `RDP-VSM-MAIN:app/package.json:2` | 13 | high |
| `E-FE-000002` | `RDP-VSM-MAIN:app/vite.config.js:28` | 10 | high |
| `E-FE-000003` | `RDP-VSM-MAIN:app/src/router/index.js:95` | 7 | high |
| `E-FE-000004` | `RDP-VSM-MAIN:app/src/router/index.js:52–92` | 5 | high |
| `E-FE-000005` | `RDP-VSM-DASHBOARDS:app/src/router/routes.js:117` | 6 | high |
| `E-FE-000006` | `RDP-VSM-DASHBOARDS:app/src/router/routes.js:1` | 7 | high |
| `E-FE-000007` | `RDP-VSM-DASHBOARDS:app/src/features/trains-new` | 8 | high |
| `E-FE-000008` | `RDP-GENERAL-VSM-COMMON:src/index.js:1` | 6 | high |
| `E-FE-000009` | `RDP-VSM-DASHBOARDS:app/src/features/common/components/CamerasInput.vue:5` | 4 | high |
| `E-FE-000010` | `RDP-VSM-BLOG:app/src/features/blog/model/useBlogStore.js` | 12 | high |
| `E-FE-000011` | `ai-agents-rules-master/front/docs/code-style.md:4` | 10 | medium inference |
| `E-FE-000012` | `RDP-VSM-MAIN:app/postcss.config.cjs:7` | 13 | high |
| `E-FE-000013` | `RDP-GENERAL-VSM-COMMON:src/styles/variables.json:2` | 7 | high |
| `E-FE-000014` | `RDP-VSM-MAIN:app/src/main.pcss:1` | 83 | high |
| `E-FE-000015` | `frontend-monitoring-master/tsconfig.json:6` | 13 | high |
| `E-FE-000016` | `frontend-cargo-main-master/eslint.config.ts:12` | 13 | high |
| `E-FE-000017` | assigned package scripts and filename scan | 13 | high |
| `E-FE-000018` | `frontend-cargo-dashboards-master/src/router/routes.ts:40` | 4 | medium |

All 18 records were independently verified by `evidence_auditor` at `2026-08-30T21:37:32.0237643+03:00`.

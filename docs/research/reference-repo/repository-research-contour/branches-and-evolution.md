# Branches and evolution

Status: `verified / independent audit PASS`

## Immutable ref inventory

| Repository ID | Ref | Commit OID | Tree OID | Disposition | Reason |
|---|---|---|---|---|---|
| RDP-VSM-BLOG | `refs/heads/master` | `c1411b87e29ec1094b90ca71e3555a7d17330186` | `4e7e8fca19f5b39b45fbdb715b5e549da2f9029d` | sample | adaptive baseline; ancestor of selected feature ref |
| RDP-VSM-BLOG | `refs/heads/feat/adaptive-layout-blog` | `65c0e25270b2e397ad8c18b70b42e0293e48e54c` | `9d5323faee18dc0c133b9545d4139d0e2e057517` | include | selected adaptive endpoint |
| RDP-VSM-DASHBOARDS | `refs/heads/master` | `237088d97f320606dbef510aaed5184d7d2bdfd2` | `30653f74558dfb4cff5b1d504d55b1da20a2c7de` | include | canonical endpoint, divergent from generic adaptive |
| RDP-VSM-DASHBOARDS | `refs/heads/feat/adaptive-layout` | `ad1d4b4275a4269ed448532eeae1549d0e8c6dc2` | `89c5432ff5d28751626352e73a8ebd1cc78819df` | include | generic adaptive base for sampled specialized refs |
| RDP-VSM-DASHBOARDS | `refs/heads/feat/adaptive-layout-construction` | `c6d909a620d12599fa216e29385d08939a698c74` | `a627b2427bebf658d1c20e39550287370abed14a` | sample | narrow adaptive vertical slice |
| RDP-VSM-DASHBOARDS | `refs/heads/feat/adaptive-layout-land-plots` | `d77e57ea2f4a4e6a4162777bb0fd51c8f3f5044e` | `c49ab10cfa70ff9d02b705603b1164057c62f983` | sample | mixed adaptive and operational evolution |

Ancestry was verified from Git objects. Blog master is an ancestor of its adaptive endpoint. Dashboards generic adaptive is an ancestor of construction and land-plots. Dashboards master and generic adaptive are neither ancestor nor descendant; their differences are branch-specific alternatives, not a chronological migration.

## Relevant-tree fingerprint groups

No selected endpoint has an identical whole scoped tree. The fingerprint is SHA-256 over sorted UTF-8 `git ls-tree -r <tree OID>` records for assigned paths, LF-separated; quarantined `app/src/App.vue` was removed before hashing.

| Endpoint | Scoped fingerprint | Entries |
|---|---|---:|
| Blog master | `66793ad97d1a02e9d2487eb0256e31a890178661690df9fe2658f93b52df7057` | 41 |
| Blog adaptive | `599ced3d30b0626802ad5f4860ff2d89142f3f16d7e7142814c6108c914d0334` | 41 |
| Dashboards master | `956fe958eecc0659be78f0d15198c567fc9aaeb6ff642cffd368690f5a8974aa` | 648 |
| Dashboards generic adaptive | `f3d5daced8944e35f8506a868c6d68a351cea56c75fd0a758cfef628727549d4` | 633 |
| Dashboards construction | `49aa37d52671403ae186c352d3e6da0857c16889450adea15971175d3754b18d` | 633 |
| Dashboards land-plots | `32ed0696e3dfa4253a54b1263f88f4eadf5f1cf44f3a20ec295bed6cccc19220` | 633 |

Stable subgroups are still meaningful. Blog keeps package, Vite, PostCSS, and ESLint blobs identical. All four Dashboards endpoints keep Vite, PostCSS, and ESLint identical. Dashboards generic plus construction share the same CI, package, and chart cohort; master plus land-plots share another cohort.

## Architectural/configuration changes

| Comparison | Added | Deleted | Modified | Renamed | Shortstat |
|---|---:|---:|---:|---:|---|
| Blog master → adaptive | 0 | 0 | 5 | 0 | 134 insertions, 6 deletions |
| Dashboards master ↔ generic adaptive | 11 | 26 | 97 | 6 | 2,569 insertions, 3,791 deletions across 140 files |
| Dashboards generic → construction | 0 | 0 | 10 | 0 | 1,163 insertions, 120 deletions |
| Dashboards generic → land-plots | 0 | 0 | 40 | 0 | 1,642 insertions, 714 deletions |

Blog introduces adaptive route metadata, fluid bounded width, breakpoint rules, and narrow-screen read-only modifiers entirely inside existing router/component/style boundaries (`E-BR-000002`). Construction follows a narrow vertical-slice pattern and extends one reusable chart component (`E-BR-000006`). Land-plots crosses router, navigation, shared interaction, feature styles, CI, dependency, and Helm boundaries (`E-BR-000007`).

The divergent Dashboards master and generic endpoint also expose alternative ownership models: a common ObjectSearch slice in master versus three feature-local implementations and consolidated feature type contracts in generic (`E-BR-000004`). This is not evidence that one model replaced the other.

## Stable conventions

- Base Vite/PostCSS/ESLint configuration remains stable across all selected adaptive changes (`E-BR-000003`).
- Responsive changes are placed in route metadata, feature/component code, and colocated PCSS rather than a branch-specific toolchain fork (`E-BR-000002`, `E-BR-000006`, `E-BR-000007`).
- Shared components are extended when behavior is reusable, as shown by the chart APIs in both specialized refs, but those extensions are not cumulative (`E-BR-000006`, `E-BR-000008`).

## New or experimental conventions

- Route-level `isAdaptive` metadata and colocated breakpoint rules are newly introduced relative to verified ancestors.
- Construction is the cleaner feature-local sample: one route, one feature family, and one shared chart extension.
- Land-plots introduces cross-route navigation metadata and touch interaction, but mixes that work with an operational configuration migration.
- Generic/prerelease package and earlier CI/chart artifacts form an experimental configuration cohort; lineage does not prove it is simply legacy.

## Legacy/deprecated conventions

No selected branch supports a defensible legacy/deprecated classification. In particular, the later-dated Dashboards master and generic adaptive endpoint diverge in the commit graph. Branch names, dates, prerelease labels, and version numbers are signals only; the playbook must not describe one divergent endpoint as superseding the other.

## Branch-specific contradictions

- Construction contains y-axis range controls but no land-plots touch-interaction family; land-plots contains touch interaction and inertia but no construction y-axis range capability (`E-BR-000008`).
- Construction keeps the generic operational cohort, whereas land-plots converges on the divergent master's CI/package/chart cohort (`E-BR-000005`).
- Generic contains several feature-local ObjectSearch implementations where the divergent master endpoint contains a common slice; other common components and central type exports remain shared (`E-BR-000004`).
- Therefore `adaptive-layout-*` names do not describe one cumulative framework; they identify independently evolving vertical slices.

Three refs were intentionally skipped after surface classification, per approved scope: `feat/adaptive-layout-expertise` (`6b1a104…`), `feat/adaptive-layout-land-allocation` (`6ddaa1bd…`), and `feat/adaptive-layout-trains-construction` (`842ea073…`). Their surface diffs add little architectural/configuration signal; no deep claims are made from them.

## Evidence index

| Evidence ID | Signal | Confidence before audit |
|---|---|---|
| `E-BR-000001` | immutable ancestry and divergence | high |
| `E-BR-000002` | Blog adaptive evolution | high |
| `E-BR-000003` | stable tooling core | high |
| `E-BR-000004` | alternative feature ownership | medium |
| `E-BR-000005` | two operational cohorts | high |
| `E-BR-000006` | construction vertical slice | high |
| `E-BR-000007` | land-plots mixed-scope evolution | high |
| `E-BR-000008` | non-cumulative specialized refs | high |

# Repository map and scope

Status: `verified / scope approved`  
Work unit: `WU-010-INVENTORY-SCOPE`  
Reviewed by: `main`  
Approved at: `2026-08-30T20:09:21.4489538+03:00`
Independent evidence audit: `PASS / evidence_auditor / 2026-08-30T21:37:32.0237643+03:00`

## Source snapshot

| Source | Canonical root | Snapshot level | Anchor | Surface result |
|---|---|---|---|---|
| `REF-PRIMARY` | `C:\Users\maksd\VisualStudioCode\rutdigital` | `SAFE-SNAPSHOT-V2` + Git metadata | `d0c2f804…044f` | 7 Git repositories, 14 local heads, 14 distinct tree OID |
| `REF-SAMPLES` | `C:\Users\maksd\OneDrive\Desktop\SAMPLE` | `SAFE-SNAPSHOT-V2` | `1288bb81…55ae` | 45 top-level directory units, no Git repositories |
| `TARGET-CONTEXT` | `C:\Users\maksd\OneDrive\Desktop\rutcampustrack-design` | metadata only | `cec9940e…8f21` | 8 directories + 2 files; no manifests at allowed surface |

Full baseline is stored in `research/control/integrity-snapshot-before.yaml`. The output and source roots are physically separate and have no ancestor reparse points (`E-SCOPE-000001`). All seven Git worktrees were clean when snapshotted; the repository/head/tree inventory is anchored by `E-SCOPE-000002`.

The prepared expectation of 44 SAMPLE units was corrected to the observed count of 45 (`E-SCOPE-000003`). Inventory completeness takes precedence over the stale expected counter. SAMPLE fingerprint provenance and the cross-source scope matrix are recorded by `E-SCOPE-000005`.

## REF-PRIMARY repositories and local heads

| Repository ID | Ref | Commit OID | Tree OID | Disposition | Reason |
|---|---|---|---|---|---|
| `RDP-GENERAL-VSM-COMMON` | `refs/heads/master` | `2733b4ecbc96e6200b39a7575f588c81cb091579` | `60b3788d3ca9c0376735f95f9b4bacc3a955dbf0` | include | shared Vue/common library and tooling |
| `RDP-VSM-BASEMAPS-ASSETS` | `refs/heads/master` | `13b1b7e2e987764b690a3c3d16c71f2f2cd4fd54` | `6138b0704c7a7d664aff5f1f3b787b8e36c44d55` | sample | asset/deployment unit without Vue package manifest |
| `RDP-VSM-BLOG` | `refs/heads/master` | `c1411b87e29ec1094b90ca71e3555a7d17330186` | `4e7e8fca19f5b39b45fbdb715b5e549da2f9029d` | sample | branch baseline |
| `RDP-VSM-BLOG` | `refs/heads/feat/adaptive-layout-blog` | `65c0e25270b2e397ad8c18b70b42e0293e48e54c` | `9d5323faee18dc0c133b9545d4139d0e2e057517` | include | adaptive component/layout variant |
| `RDP-VSM-DASHBOARDS` | `refs/heads/master` | `237088d97f320606dbef510aaed5184d7d2bdfd2` | `30653f74558dfb4cff5b1d504d55b1da20a2c7de` | include | baseline for evolution |
| `RDP-VSM-DASHBOARDS` | `refs/heads/feat/adaptive-layout` | `ad1d4b4275a4269ed448532eeae1549d0e8c6dc2` | `89c5432ff5d28751626352e73a8ebd1cc78819df` | include | generic adaptive architecture |
| `RDP-VSM-DASHBOARDS` | `refs/heads/feat/adaptive-layout-construction` | `c6d909a620d12599fa216e29385d08939a698c74` | `a627b2427bebf658d1c20e39550287370abed14a` | sample | narrow countercheck |
| `RDP-VSM-DASHBOARDS` | `refs/heads/feat/adaptive-layout-expertise` | `6b1a104b08d1a91808a2a2a27ec7a195a2eb5178` | `39fefeb901fe5667851013bc377169cb11da816f` | exclude | no surface config change; low incremental value |
| `RDP-VSM-DASHBOARDS` | `refs/heads/feat/adaptive-layout-land-allocation` | `6ddaa1bd079d92297277b242d0637876ced32999` | `7466e0751307dbf833cd30f494644f91657e47f2` | exclude | no surface config change; low incremental value |
| `RDP-VSM-DASHBOARDS` | `refs/heads/feat/adaptive-layout-land-plots` | `d77e57ea2f4a4e6a4162777bb0fd51c8f3f5044e` | `c49ab10cfa70ff9d02b705603b1164057c62f983` | sample | CI/package/chart divergence |
| `RDP-VSM-DASHBOARDS` | `refs/heads/feat/adaptive-layout-trains-construction` | `842ea0734ac68ce0869bbe5b8b550e81c3a41ef4` | `798eb82a75de6757d9bcd98dbf78b6d0156ccd6a` | exclude | no surface config change; low incremental value |
| `RDP-VSM-MAIN` | `refs/heads/master` | `515970160fd77192cfb3474c0e4eda9b770d5cde` | `f7215a588118fe1766529e70413949a108673b49` | include | shell/router/federation/API boundary |
| `RDP-VSM-MAP` | `refs/heads/master` | `7958e757d4925d0bf7d71c1564ce2fe2f4cad3eb` | `73f7c52851dd0708715200aa257d577ed2066fe5` | sample | specialized frontend countercheck |
| `RDP-VSM-MONITORING` | `refs/heads/master` | `0ca0a3750b12f0cb77a86033bc290769048a7881` | `4e51c745a9bd8487d49de6a0b4b3c9ca823e021e` | sample | TypeScript/tooling variant |

Git repository classifications:

| Repository ID | Relative path | Primary class | Surface manifests/configs | Repository disposition |
|---|---|---|---|---|
| `RDP-GENERAL-VSM-COMMON` | `general/frontend/vsm-common` | contracts/common, frontend-secondary | package, Vite, PostCSS, ESLint, CI | include |
| `RDP-VSM-BASEMAPS-ASSETS` | `vsm/frontend/basemaps-assets` | frontend-assets, devops-secondary | CI, application, Docker, compose, chart | sample |
| `RDP-VSM-BLOG` | `vsm/frontend/blog` | frontend | package/lock, Vite, PostCSS, ESLint, nginx, Docker, chart, CI | include |
| `RDP-VSM-DASHBOARDS` | `vsm/frontend/dashboards` | frontend | package/lock, Vite, PostCSS, ESLint, nginx, Docker, chart, CI | include |
| `RDP-VSM-MAIN` | `vsm/frontend/main` | frontend | package/lock, Vite, PostCSS, ESLint, nginx, Docker, chart, CI | include |
| `RDP-VSM-MAP` | `vsm/frontend/map` | frontend | package/lock, Vite, PostCSS, ESLint, nginx, Docker, chart, CI | sample |
| `RDP-VSM-MONITORING` | `vsm/frontend/monitoring` | frontend | package/TS/Vite/PostCSS/ESLint, nginx, Docker, chart, CI | sample |

Structural units `.agents` and the empty root `.git` marker are excluded. `general`, `general/frontend`, `vsm`, and `vsm/frontend` are containers whose scope is inherited from their children.

## REF-SAMPLES full inventory

The suffix `-master` is part of exported directory names and is not treated as Git provenance. Every locator in this source uses the immutable source fingerprint rather than an invented branch or commit.

| Directory unit | Classification | Disposition | Reason / evidence value |
|---|---|---|---|
| `ai-agents-rules-master` | documentation/context | sample | prescriptive candidates, valid only when corroborated by code |
| `ansible-master` | devops | include | Ansible config, playbooks, roles and CI |
| `api-gateway-master` | backend | include | gateway/BFF API, tests and specs |
| `argocd-deploy-master` | devops | sample | README-only candidate; never a hard rule alone |
| `authentication-service-master` | backend | include | representative four-layer service |
| `basemaps-assets-master` | frontend-assets/devops | sample | asset packaging counterpart |
| `blognote-master` | backend | sample | layered service confirmation |
| `camera-endpoint-master` | backend | sample | thin proxy counter-pattern |
| `common-master` | contracts/common | include | shared .NET boundaries |
| `contracts-master` | contracts/common | include | dual Go/.NET protobuf packaging; `gen/` excluded |
| `dashboards-master` | backend | include | representative service and API boundary |
| `dns-zones-master` | devops | sample | DNS configuration variant |
| `event-store-master` | devops | exclude | narrow compose snapshot |
| `filebeat-master` | devops | sample | log-shipping confirmation |
| `frontend-blog-master` | frontend | include | representative Vue microfrontend |
| `frontend-cargo-dashboards-master` | frontend | sample | TypeScript template confirmation |
| `frontend-cargo-main-master` | frontend | include | representative TypeScript shell variant |
| `frontend-dashboards-master` | frontend | include | representative Vue microfrontend |
| `frontend-main-master` | frontend | include | representative shell snapshot |
| `frontend-map-master` | frontend | sample | specialized frontend countercheck |
| `frontend-master` | frontend | sample | consolidated/legacy counterexample |
| `frontend-monitoring-master` | frontend | sample | TypeScript/tooling variant |
| `geo-master` | backend | sample | layered spatial service confirmation |
| `geo-objects-master` | backend | include | representative layered spatial service |
| `go-restart-container-master` | devops/backend-secondary | sample | operational helper pattern |
| `helm-chart-rut-master` | devops | include | reusable Helm chart structure; secret candidate excluded |
| `kafka-master` | devops | sample | data-infrastructure confirmation |
| `logging-master` | devops | include | observability stack structure |
| `mediamtx-master` | devops | exclude | service-specific, low incremental value |
| `minio-master` | devops | sample | data-infrastructure confirmation |
| `nginx-master` | devops | include | reverse-proxy/environment structure; `ssl/` excluded |
| `obraz-frontend-master` | frontend | exclude | redundant template; naming mismatch retained as counter-signal |
| `pipelines-master` | devops | include | reusable CI pipeline templates |
| `postgres-deploy-master` | devops | sample | compose fleet; all `.env` paths excluded |
| `projectdocuments-master` | backend | sample | layered service confirmation |
| `rating-region-master` | frontend | sample | generic package-name counterexample |
| `releases_docker-master` | devops | sample | release CI variant |
| `releases-master` | devops | sample | release CI variant |
| `s3fileshelf-master` | backend | sample | flat service counterexample |
| `test-contracts-master` | contracts/common | exclude | duplicate/test snapshot |
| `tileserver-master` | devops | exclude | service-specific, low incremental value |
| `titiler-master` | devops | exclude | service-specific, low incremental value |
| `vault-template-master` | devops | sample | README/CI single-example candidate |
| `vsm-deploy-master` | documentation/context, devops-secondary | sample | topology cross-check only; not primary practice evidence |
| `zabbix-master` | devops | sample | observability counterpoint; env files excluded |

## TARGET-CONTEXT inventory

This source constrains adaptation only; it cannot prove a reference engineering rule. No content was read during cartography (`E-SCOPE-000004`).

| Path | Classification | Disposition | Reason |
|---|---|---|---|
| `_work` | documentation/context | exclude | process artifacts |
| `00-scans` | documentation/context | exclude | visual/product semantics |
| `01-wireframes` | documentation/context | exclude | UX/product semantics |
| `02-backend` | documentation/context | sample | only transport/conflict/API context files allowed |
| `03-brand` | documentation/context/design | exclude | design semantics; `figma-spec.md` explicitly excluded |
| `04-figma` | forbidden | exclude | unvisited; immutable Figma ban |
| `05-adaptive` | unknown | exclude | surface-empty |
| `knowledge` | documentation/context | sample | project/transport/accessibility constraints only |
| `README.md` | documentation/context | sample | project boundary only; design/Figma sections skipped |
| `Onest.zip` | archive | exclude | archive default exclusion |

Exact allowed context files are recorded in `research/control/scope.yaml`. All other target-context content remains excluded.

## Default exclusions and quarantine

- 55 paths are quarantined in `research/control/quarantine.yaml`: 8 under REF-PRIMARY and 47 under REF-SAMPLES (`E-SCOPE-000006`). Six paths were added by controlled detections (two `application.yaml`, one frontend component, two Helm files, and one PostgreSQL compose file); analysts stopped, no value was emitted/stored, and safe anchors were recomputed without hashing those files.
- `TARGET-CONTEXT/04-figma`, `TARGET-CONTEXT/03-brand/figma-spec.md`, all browser/API Figma surfaces and `C:\Users\maksd\ruttrack` are forbidden.
- Generated/vendor/build/cache directories, minified files, sourcemaps, archives, dumps, logs, binaries and media are excluded.
- `contracts-master/gen`, `nginx-master/ssl`, non-template `.env*`, `.npmrc`, keys and certificates are excluded.
- Generated content may establish a boundary exists but cannot support a transferable rule.

## Selected work units

| Work unit | Owner | Immutable/snapshot assignments | Scope |
|---|---|---|---|
| `WU-020-FRONTEND` | `frontend_conventions_analyst` | 4 REF-PRIMARY commit OID + REF-SAMPLES `1288…` | Vue architecture, routing, federation, components, PCSS/PostCSS, naming, lint/tests, counterexamples |
| `WU-030-BACKEND` | `backend_conventions_analyst` | REF-SAMPLES `1288…` + TARGET-CONTEXT `cec9…` | layers, API/BFF, contracts/common, errors/mapping, persistence/messaging, tests; target only constrains adaptation |
| `WU-040-DEVOPS` | `devops_conventions_analyst` | 3 REF-PRIMARY commit OID + REF-SAMPLES `1288…` | CI/CD, Docker, Helm, Ansible, release/environment layering, observability and recovery |
| `WU-050-BRANCHES` | `git_branch_mapper` | Blog pair + Dashboards baseline/adaptive/two samples | Git-object comparison of selected unique trees only |

The full per-assignment path matrix and OID are canonical in `research/control/scope.yaml`.

## Coverage risks and counter-signals

- REF-SAMPLES is an unversioned export. Evidence must cite its source fingerprint and path with `ref_name`/`commit_oid` null.
- Backend history is unavailable; backend branch evolution cannot be inferred.
- `vsm-common` crosses frontend/shared-library boundaries and is assigned to the frontend analyst with contracts/common classification.
- `basemaps-assets` is mixed asset/deployment, not a conventional Vue application.
- `argocd-deploy-master` and `vault-template-master` are documentation/CI single examples and remain candidates.
- `vsm-deploy-master` contains report-like material; it is topology context, not primary operational proof.
- Primary CI, Docker and PostCSS files have large hash-identical families; representative sampling prevents duplicate inflation.
- Manifest counter-signals include generic/mismatched package names, wildcard dependency pinning, unusual runtime dependency placement and mutating lint scripts. None becomes a rule without deep evidence and counterexample review.
- Surface inventory found an explicit test project only in the gateway sample; testing conclusions must remain cautious until deep analysis.
- Product entities, roles, workflows, texts and source DTO semantics remain out of scope.

## Main review decision

The proposed inventory is complete and the scope is bounded enough for parallel domain analysis. All included/sample sources have an immutable Git OID or a recorded snapshot fingerprint, every discovered top-level unit has a disposition, and all exclusions/skips have reasons. Domain analysis is therefore unlocked at checkpoint `CP-020-SCOPE-APPROVED`.

# Transferable reference playbook

Status: `verified synthesis / WU-070-SYNTHESIS`  
Evidence basis: `71 verified records / independent evidence audit PASS`  
Audience: RutCampusTrack architecture, frontend, backend, platform, security, QA, and SRE owners.

## How to read this playbook

Only verified evidence supports `adopt` or `adapt`. `Adopt` means a repeated and target-compatible default; `adapt` means retain the engineering intent while correcting a source weakness or fitting a target constraint; `avoid` identifies a verified counterexample; `defer` marks a single example, an evidence gap, or an unresolved owner choice.

This playbook transfers engineering structure, not source-product entities, roles, workflows, texts, visual semantics, or business decisions. `TARGET-CONTEXT` constrains adaptation but never proves a reference practice. Backend and most DevOps samples are snapshot-only, so they support cross-project comparison but not chronology. Generated artefacts and quarantined paths have zero evidentiary weight.

## Target architecture

```text
browser
  -> frontend shell (global composition, navigation, access guards)
       -> feature-owned modules/remotes (routes, UI, state, transport)
  -> public REST/OpenAPI BFF (public DTOs, validation, RFC 9457, composition)
       -> application use cases
            -> internal gRPC adapters and capability services
                 -> capability-owned persistence and integrations

deployable owner
  -> versioned CI component -> immutable artefact digest
       -> validated environment overlay -> health/smoke gate -> promotion/rollback
```

The shell and BFF are composition owners, not universal homes for feature policy. A module becomes a remote, service, or repository only when ownership, release cadence, scaling, security, or runtime isolation justifies the boundary (`E-FE-000001`–`E-FE-000004`, `E-BE-000001`, `E-BE-000004`, `E-DO-000001`).

## Frontend blueprint

### Ownership and structure

Use a feature-owned public surface and keep pages as composition points:

```text
src/
  app/                         # bootstrap and application-wide providers
  pages/<feature>/             # route-level composition
  features/<feature>/
    index.ts                   # supported public API only
    routes.ts                  # route/remote contract
    ui/                        # Vue components
    model/                     # Pinia stores and composables
    api/                       # transport adapters
    types/                     # feature-owned types
    lib/                       # pure feature helpers
    styles/                    # feature-local PCSS where needed
  shared/
    ui/ lib/ styles/ types/    # only genuinely cross-feature mechanics
```

This placement reflects repeated feature slices and public barrels (`E-FE-000007`, `E-FE-000008`). Do not move feature-specific code into `shared`, import another feature's internals, or let a page own raw transport. A standalone application may keep the same module boundary without module federation.

### Routing and microfrontends

- The shell owns remote declarations, registration, global authentication/access/leave guards, navigation, and cross-cutting overlays (`E-FE-000002`–`E-FE-000004`).
- A feature/remote exposes a small route module, with unique names and resolved paths, relative children, named redirects, and a small typed metadata vocabulary (`E-FE-000003`, `E-FE-000005`).
- Await remote registration before mounting the router. Validate route-name/path collisions and stale federation exports (`E-FE-000018`).
- Use route-level lazy loading when loading and error states are owned. It is an adaptation opportunity, not a universal observed rule (`E-FE-000006`).
- Keep responsive work inside route, feature, component, and style boundaries. Verified branch examples changed those surfaces without forking the toolchain (`E-BR-000002`, `E-BR-000003`, `E-BR-000006`).

### Components, stores, composables, and transport

- New components use typed props and emits; slots and attribute forwarding remain explicit extension points (`E-FE-000009`).
- Feature state belongs in Composition-API Pinia stores under `model`; reusable reactive behavior uses `use<Name>` composables; transport belongs under `api` (`E-FE-000010`).
- Components consume feature services or store actions rather than issuing raw requests.
- Cross-feature consumers import only from the feature barrel. Generated files and transport DTOs do not become the feature API (`E-FE-000008`, `E-FE-000018`).

### PCSS/PostCSS and tokens

- Keep one reproducible PostCSS pipeline with imports, prefixing, variables, nesting, production minification, and the approved px/rem policy (`E-FE-000012`).
- Put breakpoints, colors, dimensions, and responsive mixins in an owned token/foundation layer (`E-FE-000013`).
- Prefer component modules such as `<ComponentName>.module.pcss`; isolate legacy global/BEM aggregation behind a migration boundary (`E-FE-000014`).
- Do not globalize feature-specific selectors. Token ownership and accessibility constraints must be explicit.

### TypeScript, linting, and tests

- Use strict TypeScript for new code and the stable `@ -> src` alias; migrate legacy JavaScript incrementally (`E-FE-000015`).
- Use flat ESLint and separate non-mutating `lint` from `lint:fix`. Mutating CI lint is an avoid signal (`E-FE-000016`).
- Stylelint configuration is an owner decision because no verified implementation exists.
- No reusable frontend test convention was implemented in the approved surfaces (`E-FE-000017`). The target must define unit, component, router, store, and remote-contract tests, but must label their exact framework, fixture layout, and thresholds as target-owned rather than copied practice.

## Backend and BFF blueprint

### Capability boundary and proportional layering

Start from a capability-owned module. A thin service may remain one project; a more complex service may use:

```text
<Capability>.Api
<Capability>.Application
<Capability>.Domain
<Capability>.Infrastructure
```

The reference repeats layered project sets but also has valid single-project counterexamples (`E-BE-000001`). Therefore project count is proportional, and the source must not be described as strictly hexagonal because Application/API dependencies sometimes point to persistence and generated contracts (`E-BE-000002`).

Transport adapters should parse, map, dispatch a command/query, propagate cancellation/deadlines, and map the response. Use cases own policy; narrow shared packages own technical hosting, EF, logging, storage, or text mechanics only (`E-BE-000003`, `E-BE-000006`, `E-BE-000015`).

### Public REST BFF over internal gRPC

The target boundary is:

```text
Bff/Features/<Feature>/
  Endpoints/
  Contracts/                 # public REST request/response DTOs
  Mappings/
  Clients/                   # internal gRPC/application adapters
```

Use the verified edge-composition idea, not the source GraphQL technology (`E-BE-000004`). The BFF owns REST/OpenAPI DTOs and feature-local mappings; generated gRPC types stay inside adapters (`E-BE-000005`, `E-BE-000007`). Avoid both one downstream call per loader key and a giant cross-feature mapper: composition needs batching/failure tests and local ownership.

For public collections, define deterministic sort, filters, pagination input, items, and total/page metadata. Offset versus cursor vocabulary and compatibility tooling remain owner decisions because only one inconsistent contract family was observed (`E-BE-000008`).

### Validation, errors, persistence, and security

- Validate at the public boundary and translate application/domain failures centrally to stable, safe RFC 9457 responses. Never return raw internal exception messages (`E-BE-000010`).
- Direct ORM access is acceptable for simple local use cases when transactions are explicit. Do not add repository/unit-of-work wrappers without a demonstrated need (`E-BE-000009`).
- Authentication and authorization are invariant across environments; local/test uses an explicit identity strategy, not disabled enforcement (`E-BE-000011`).
- Bind typed configuration, fail fast on invalid settings, expose meaningful readiness, and control migration execution (`E-BE-000015`).

### Resilience, messaging, and observability

- Propagate cancellation, deadlines, and correlation identifiers across REST, application, and gRPC boundaries (`E-BE-000003`, `E-BE-000012`, `E-DO-000013`).
- Redact and allowlist log fields. Do not generically log bodies, authorization headers, user identifiers, or SQL arguments (`E-BE-000012`, `E-DO-000014`).
- Exact retry/idempotency policy is deferred: the only candidate retries too broadly and without operation classification (`E-BE-000013`).
- Exact messaging/outbox design is deferred: one unused post-save publisher is not an asynchronous consistency architecture (`E-BE-000014`).
- A schema snapshot alone is not a test strategy; contract behavior, compatibility, failure, and cancellation require executable tests (`E-BE-000016`).

## DevOps blueprint

### Ownership, CI, and immutable delivery

Deployables own a small wrapper and local deployment artefacts; specialized platform units own reusable CI, chart, automation, and observability mechanics (`E-DO-000001`). Pin the central CI component version (`E-DO-000002`) and make validation specific to the artefact family: non-mutating static checks, tests, manifest/config validation, no-push build, immutable publication, non-production deploy, smoke check, approval, then promotion (`E-DO-000003`, `E-DO-000018`).

Build once and promote the same digest. Record provenance, environment, approval, and rollback target. Avoid mutable branch tags, `latest`, commit-message-only release authority, direct unreviewed publishing, and `down/up` deployment without a tested previous image (`E-DO-000004`, `E-DO-000005`).

### Containers and runtime controls

- Pin builder/runtime images and use lock-enforcing dependency installation.
- Use multi-stage builds and a minimal runtime image where appropriate (`E-DO-000006`).
- Do not install packages at runtime or expose the Docker socket without a documented isolated exception (`E-DO-000006`, `E-DO-000007`).
- Define container/runtime health and align it with orchestrator readiness/liveness. Avoid fixed container names and host assumptions when the orchestrator owns identity (`E-DO-000007`).

### Helm, overlays, and secrets

- A versioned base chart needs an owned release process and values schema (`E-DO-000008`, `E-DO-000009`).
- Environment overlays contain differences only. They must explicitly activate resources, readiness, liveness, metrics, autoscaling, and network-policy decisions; template capability alone is not evidence of active use (`E-DO-000015`, `E-DO-000021`, `E-DO-000023`).
- Reference secrets externally and define store scope, rotation owner, TTL, and revocation. Never render values into source or CI output (`E-DO-000010`, `E-DO-000018`).
- Short-lived workload identity is a candidate; the exact Vault/OIDC flow remains deferred because the safe evidence is single-example (`E-DO-000022`, `Q-002`).

### Ansible, GitOps, observability, and recovery

Use thin playbooks and reusable roles, but pin tools/collections, keep SSH host-key and repository-signature verification enabled, prefer modules to broad shell tasks, and put assertions in role tests (`E-DO-000012`).

Exact Argo sync/promotion policy is deferred: one safe executable example uses mutable `HEAD` and disables prune/self-heal (`E-DO-000011`). Exact deployment-inventory generation is also deferred: one generator pushes directly and suppresses failures; generated catalogue output is never practice proof (`E-DO-000019`).

Adopt structured service/environment/correlation identifiers and reusable operational searches (`E-DO-000013`). Define an allowlisted log schema, redaction tests, retention, access, and deletion. Trace IDs alone do not prove distributed tracing. Alert rules, SLOs, dashboards-as-code, and the tracing backend remain owner decisions (`E-DO-000014`, `E-DO-000016`).

The one filesystem-copy backup has no verified restore workflow and cannot be a recovery standard (`E-DO-000017`). Owners must define RPO/RTO, independent encrypted storage, retention, automated restore verification, a recovery environment, and retained exercise evidence.

## Cross-cutting naming and placement

| Element | Proposed convention | Mode and evidence |
|---|---|---|
| Feature directory | `kebab-case` | adopt; `E-FE-000007`, `E-FE-000011` |
| Vue component | `PascalCase.vue` | adopt; `E-FE-000009`, `E-FE-000011` |
| Composable | `use<Name>.ts` | adopt; `E-FE-000010`, `E-FE-000011` |
| Pinia store | `use<Feature>Store.ts` | adopt with documented legacy exceptions; `E-FE-000010` |
| Feature public API | `features/<feature>/index.ts` | adopt; `E-FE-000008` |
| Feature transport | `features/<feature>/api/<verb><Resource>.ts` | adapt; `E-FE-000007`, `E-FE-000010` |
| Component style | `<ComponentName>.module.pcss` beside owned component | adapt; `E-FE-000014` |
| Route module | `features/<feature>/routes.ts` | adopt; `E-FE-000003`, `E-FE-000005` |
| Route name | proposed `<feature>.<page>` | owner decision; evidence proves uniqueness, not punctuation |
| Backend module | `<Capability>.<Layer>` | adapt; `E-BE-000001`, `E-BE-000002` |
| Use case | `<Action>Command` / `<Action>Query`, `<Action>Handler` | adapt; `E-BE-000003` |
| BFF mapping | `Bff/Features/<Feature>/Mappings` | adapt; `E-BE-000004`, `E-BE-000005` |
| Public REST DTO | `<Action>Request` / `<Action>Response` | target convention; exact vocabulary owner-approved |
| gRPC source | `contracts/grpc/<capability>/v1` | defer exact placement; `E-BE-000007`, `E-BE-000008` |
| Unit/component test | proposed `*.spec.*` beside owned source | defer exact layout; `E-FE-000017`, `E-BE-000016` |
| Integration/contract test | proposed `tests/<scope>` | defer exact framework; same evidence |
| CI wrapper | deployable-root `.gitlab-ci.yml`, pinned template version | adopt; `E-DO-000002` |
| Helm overlay | `deploy/helm/values/<environment>.yaml` | adapt; `E-DO-000008`, `E-DO-000009` |
| Ansible role | `roles/<role-name>` with thin playbook | adapt; `E-DO-000012` |
| Observability config | `observability/<logs|alerts|dashboards>` | logs adapt; alerts/dashboard layout defer |

## Six executable recipes

### Recipe 1 — Add a Vue component

Mode: `adopt/adapt`; evidence: `E-FE-000007`–`E-FE-000011`, `E-FE-000014`.

1. Identify the owning feature; do not place feature-specific UI in `shared`.
2. Create `ui/<ComponentName>.vue` and a colocated `<ComponentName>.module.pcss`.
3. Define typed props and emits; expose optional behavior through slots or explicit props.
4. Put reusable reactive behavior in `model/use<Name>.ts`; keep raw transport outside the component.
5. Export only the intended surface from the feature `index.ts`; consumers use that barrel.
6. Add a colocated component test under the target-owned test standard.
7. Verify no deep import, feature-global PCSS, duplicate state/transport ownership, or copied source-product semantics.

### Recipe 2 — Add a frontend feature and route

Mode: `adopt/adapt`; evidence: `E-FE-000003`–`E-FE-000008`, `E-FE-000018`, `E-BR-000002`, `E-BR-000003`.

1. Create `index.ts`, `routes.ts`, `ui`, `model`, `api`, `types`, and `lib` under the owning feature.
2. Add the route-level page under `pages/<feature>`.
3. Define a unique named route, relative children, named redirects, and approved typed metadata.
4. Lazy-import the page only with owned loading/error UX.
5. For a remote, expose the route module; in the shell, await registration before mount.
6. Keep global guards in the shell and local feature conditions in the feature.
7. Test duplicate names/paths, stale exports, and registration failure.
8. Split unrelated UI and operational migrations into separately reviewable changes (`E-BR-000006`, `E-BR-000007`).

### Recipe 3 — Add a backend capability or BFF module

Mode: `adopt/adapt`; evidence: `E-BE-000001`–`E-BE-000006`, `E-BE-000010`, `E-BE-000011`, `E-BE-000015`.

1. Name the owning capability; do not begin with a technical shared project.
2. Choose one project for a thin service or the proportional Api/Application/Domain/Infrastructure split.
3. Add an action command/query and handler. Keep transport at parse → map → dispatch → response.
4. Propagate cancellation and an explicit deadline.
5. Under `Bff/Features/<Feature>`, create endpoints, public REST contracts, mappings, and clients.
6. Keep generated gRPC DTOs out of public REST and application/domain models.
7. Map failures centrally to safe RFC 9457 responses; keep auth enabled everywhere.
8. Register typed configuration, readiness, and controlled migration behavior.
9. Verify no domain policy in the BFF, giant mapper, per-key unbatched fan-out, or raw exception text.

### Recipe 4 — Add or change a REST/gRPC contract

Mode: target constraint plus `defer` for exact source-derived layout; evidence: `E-BE-000004`, `E-BE-000005`, `E-BE-000007`, `E-BE-000008`, `E-BE-000010`.

1. Define public REST/OpenAPI independently from internal gRPC.
2. Keep public DTOs with the BFF feature and protobuf source in an owner-approved versioned directory.
3. Keep generated output in an adapter boundary and never hand-edit it.
4. Add feature-local REST ↔ application ↔ gRPC mappings.
5. Define deterministic collection sorting, filters, pagination input, and response metadata.
6. Choose cursor versus offset and compatibility policy explicitly; do not copy a single source vocabulary.
7. Define stable error codes/RFC 9457 responses and compatibility checks for removed or renamed fields.
8. Add behavior and compatibility tests; a snapshot alone is insufficient.

### Recipe 5 — Add tests and quality gates

Mode: `defer / target-owned standard`; gap evidence: `E-FE-000017`, `E-BE-000016`, `E-DO-000003`.

1. Classify the test: unit, component, integration, public contract, internal transport, smoke, or recovery.
2. Place owned tests beside the feature/capability; put cross-system suites under top-level `tests`.
3. Name fixtures by technical scenario rather than copied product stories.
4. Test failures, cancellation, route uniqueness, remote registration, REST/gRPC isolation, and RFC 9457 mapping.
5. Validate manifests and perform no-push builds before publication.
6. Keep CI lint/tests non-mutating.
7. Record framework, coverage threshold, fixture hierarchy, and test-data policy as explicit owner decisions.
8. Do not claim the proposed layout is an observed reference convention; it remediates a verified gap.

### Recipe 6 — Add a deployment element

Mode: `adopt/adapt`; evidence: `E-DO-000001`–`E-DO-000010`, `E-DO-000015`, `E-DO-000018`, `E-DO-000021`–`E-DO-000023`.

1. Add deployable-local `Dockerfile`, small pinned CI wrapper, versioned chart, and minimal environment overlay.
2. Pin build/runtime images and use lock-enforcing installation.
3. Validate Docker, Helm, nginx, and configuration before publication.
4. Publish one immutable digest; reference secrets externally.
5. Define resources, readiness/liveness, metrics, autoscaling, and network policy decisions.
6. Deploy to non-production and run smoke/health verification.
7. Promote the same digest after approval; retain and test a known-good rollback target.
8. Add correlation identifiers and data-minimized structured logs.
9. Defer Argo-specific implementation until the owner approves a GitOps model.
10. Reject `latest`, mutable promotion, broad Docker-socket privilege, secret-bearing output, inactive runtime controls, and ignored deployment failures.

## Transfer matrix

| Practice | Mode | Domain | Verified evidence | Owner condition |
|---|---|---|---|---|
| Provenance, immutable locators, and quarantine | adopt | cross-cutting | `E-SCOPE-000001`, `E-SCOPE-000002`, `E-SCOPE-000003`, `E-SCOPE-000004`, `E-SCOPE-000005`, `E-SCOPE-000006` | preserve snapshot/branch scope |
| Ownership-aligned module/deployable boundary | adapt | cross-cutting | `E-FE-000001`, `E-BE-000001`, `E-DO-000001` | owner/release/runtime need |
| Exact polyrepo or universal four-project topology | defer/avoid | architecture | same plus `E-BE-000002` | no universal topology |
| Shell-owned composition and guards | adopt | frontend | `E-FE-000002`–`E-FE-000004` | stable registration contract |
| Route module and feature barrel as public API | adopt | frontend | `E-FE-000003`, `E-FE-000005`, `E-FE-000007`, `E-FE-000008` | collision/deep-import checks |
| Typed component/state/transport separation | adopt | frontend | `E-FE-000009`–`E-FE-000011` | strict TS for new code |
| Shared PostCSS/tokens; module-first PCSS | adopt/adapt | frontend | `E-FE-000012`–`E-FE-000014` | token owner and legacy boundary |
| Strict TS and non-mutating flat ESLint | adapt | frontend | `E-FE-000015`, `E-FE-000016` | staged migration |
| Exact Stylelint/test framework | defer | frontend | `E-FE-000016`, `E-FE-000017` | frontend/QA decision |
| Duplicate routes, stale exposes, deep imports | avoid | frontend | `E-FE-000008`, `E-FE-000018` | none |
| Capability ownership and thin transport | adopt | backend | `E-BE-000001`, `E-BE-000003` | proportional layering |
| Strict hexagonal claim or universal layer count | avoid | backend | `E-BE-000001`, `E-BE-000002` | dependency graph contradicts it |
| Narrow shared technical mechanics | adopt | backend | `E-BE-000006`, `E-BE-000015` | stable owner/safe defaults |
| Public-edge composition adapted to REST BFF | adapt | BFF | `E-BE-000004` | REST-over-gRPC target constraint |
| Feature-local mapping; no giant mapper | adapt/avoid | BFF | `E-BE-000005` | mapping ownership |
| Generated contract boundary | adapt; exact layout defer | backend | `E-BE-000007` | API/platform decision |
| Pagination and compatibility vocabulary | defer | BFF | `E-BE-000008` | API owner decision |
| Direct ORM proportional to need | adapt | backend | `E-BE-000009` | explicit transaction boundary |
| Central safe error translation | adopt/adapt | BFF | `E-BE-000010` | RFC 9457 catalogue |
| Environment-invariant authentication/authorization | adopt | backend | `E-BE-000011` | local/test identity strategy |
| Correlation and data-minimized logging | adopt/adapt | cross-cutting | `E-BE-000012`, `E-DO-000013`, `E-DO-000014` | log schema/redaction policy |
| Broad retries or post-save event as architecture | defer/avoid | backend | `E-BE-000013`, `E-BE-000014` | idempotency/outbox decisions |
| Snapshot-only contract testing | avoid as sole gate | backend | `E-BE-000016` | behavioral tests required |
| Versioned central CI and family validation | adopt | DevOps | `E-DO-000002`, `E-DO-000003`, `E-DO-000018` | released templates |
| Immutable build-once promotion and rollback | adapt | DevOps | `E-DO-000004`, `E-DO-000005` | digest/provenance/environment owner |
| Reproducible containers and active health | adapt | DevOps | `E-DO-000006`, `E-DO-000007` | pinned images/locked install |
| `latest`, runtime installs, Docker socket shortcuts | avoid | DevOps | same | documented isolated exception only |
| Versioned chart and minimal overlays | adapt | DevOps | `E-DO-000008`, `E-DO-000009`, `E-DO-000021`, `E-DO-000023` | schema and safe defaults |
| External secret references | adapt | DevOps | `E-DO-000010`, `E-DO-000022` | store/rotation/TTL owner |
| Exact Argo/Vault/OIDC mechanics | defer | DevOps | `E-DO-000011`, `E-DO-000022` | single safe examples |
| Thin Ansible playbooks and hardened roles | adapt | DevOps | `E-DO-000012` | pinned tools, SSH/GPG, assertions |
| Alert/SLO/dashboard standard | defer | SRE | `E-DO-000016` | SRE owner absent |
| Filesystem copy as recovery standard | defer/avoid | SRE | `E-DO-000017` | RPO/RTO and restore evidence |
| Generated catalogue as practice proof | avoid | cross-cutting | `E-DO-000019` | generated content has zero proof count |
| Old/backup/specialized artefacts as standards | avoid | cross-cutting | `E-DO-000020` | adoption/history unknown |
| Ancestry before evolution claims | adopt | cross-cutting | `E-BR-000001`, `E-BR-000002`, `E-BR-000003`, `E-BR-000004`, `E-BR-000005`, `E-BR-000006`, `E-BR-000007`, `E-BR-000008` | immutable OIDs |
| Stable toolchain and coherent change scope | adopt/adapt | frontend/DevOps | `E-BR-000002`, `E-BR-000003`, `E-BR-000006`, `E-BR-000007` | separately reviewable migrations |
| Divergent/specialized refs as linear cumulative history | avoid | cross-cutting | `E-BR-000001`, `E-BR-000004`–`E-BR-000008` | none |

## Deferred owner decisions

| ID | Decision | Owner | Why unresolved |
|---|---|---|---|
| `OD-001` | Repository and microfrontend topology | architecture/frontend/platform | boundaries are supported, exact repository count is not |
| `OD-002` | Route-name syntax and metadata vocabulary | frontend | uniqueness is supported; syntax varies |
| `OD-003` | Test frameworks, fixtures, and thresholds | frontend/backend/QA | no reusable implemented standard |
| `OD-004` | Stylelint and formatting policy | frontend | no verified Stylelint implementation |
| `OD-005` | REST pagination and compatibility model | API/BFF | single inconsistent source family |
| `OD-006` | Generated gRPC placement/version checks | backend/platform | single contract-generation example |
| `OD-007` | Persistence ports and transaction ownership | backend | complex transaction evidence absent |
| `OD-008` | Retry, deadline, idempotency, deduplication | backend/SRE | one broad retry candidate |
| `OD-009` | Messaging/outbox architecture | backend/data | one unused publisher |
| `OD-010` | Release authority and environment protection | platform/release | source mechanisms conflict |
| `OD-011` | Chart schema, mandatory runtime controls, secret TTL | platform/security | defaults quarantined; identity flow is single-example |
| `OD-012` | Argo/GitOps ownership and sync policy | platform | one safe executable example |
| `OD-013` | SLOs, alerts, tracing, log retention | SRE/security | no verified reusable standard |
| `OD-014` | Backup technology, RPO/RTO, restore verification | data/SRE | no restore evidence |
| `OD-015` | Deployment inventory generation/review | platform | one failure-suppressed generator |

## Principal risks and exceptions

- Shared packages and platform components improve consistency but enlarge blast radius; they require named owners, released versions, and safe defaults.
- A microfrontend, service, repository, or abstraction without independent ownership becomes coordination overhead.
- Testing, compatibility, alerting, recovery, and GitOps need explicit target decisions; evidence gaps must not be filled with synthetic certainty.
- Security enforcement, redaction, probes, metrics, and rollback count only when active and tested in consumers.
- Branch names, dates, `old`, `backup`, and prerelease labels do not establish replacement or deprecation (`D-009`).
- Generated code/catalogues and quarantined paths never support transfer claims (`D-001`, `D-010`).

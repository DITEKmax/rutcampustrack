# Cross-reference patterns

Status: `verified synthesis / WU-070-SYNTHESIS`  
Evidence gate: `71 verified records / independent audit PASS`  
Transfer boundary: engineering practices only; source-product entities, roles, workflows, texts, and business decisions are excluded.

## Scope aliases

- `SAMPLE`: `REF-SAMPLES@1288bb81051c332f53fc26fc561ae3f16d1063e53f93d92dcef5c5ff2afc55ae`; unversioned snapshot.
- `SHELL`: `RDP-VSM-MAIN@515970160fd77192cfb3474c0e4eda9b770d5cde`.
- `COMMON`: `RDP-GENERAL-VSM-COMMON@2733b4ecbc96e6200b39a7575f588c81cb091579`.
- `BLOG`: `c1411b87e29ec1094b90ca71e3555a7d17330186` → `65c0e25270b2e397ad8c18b70b42e0293e48e54c`.
- `DASHBOARDS`: divergent master `237088d97f320606dbef510aaed5184d7d2bdfd2`; generic adaptive `ad1d4b4275a4269ed448532eeae1549d0e8c6dc2`; descendants `c6d909a620d12599fa216e29385d08939a698c74` and `d77e57ea2f4a4e6a4162777bb0fd51c8f3f5044e`.
- `ASSET-RUNTIME`: `RDP-VSM-BASEMAPS-ASSETS@13b1b7e2e987764b690a3c3d16c71f2f2cd4fd54`.
- `TARGET-CONTEXT` constrains adaptation only and is not reference-practice evidence.

## Summary

| Pattern | Status | Mode | Applicability | Neutral name |
|---|---|---|---|---|
| `PAT-001` | accepted | adapt | cross-cutting | Ownership-aligned deployable boundary |
| `PAT-002` | accepted | adopt/adapt | frontend, BFF | Single composition owner |
| `PAT-003` | accepted | adopt/adapt | cross-cutting | Explicit public surface |
| `PAT-004` | accepted | adopt | frontend, backend | Feature/capability ownership |
| `PAT-005` | accepted | adopt/adapt | frontend | Route-module contract |
| `PAT-006` | accepted | adopt | frontend | Bounded component and state API |
| `PAT-007` | accepted | adopt/adapt | frontend | Shared styling foundation with local scope |
| `PAT-008` | accepted | adapt | frontend | Non-mutating static quality gate |
| `PAT-009` | deferred | defer | cross-cutting | Target-owned testing standard |
| `PAT-010` | accepted | adopt/adapt | backend | Thin transport and proportional layering |
| `PAT-011` | accepted | adapt | BFF/backend | Edge composition with feature-local mapping |
| `PAT-012` | accepted | adopt/adapt | BFF/backend | Central error conversion and validation |
| `PAT-013` | deferred | defer | BFF/backend | Contract versioning and pagination |
| `PAT-014` | accepted | adapt | backend | Persistence isolation proportional to need |
| `PAT-015` | deferred | defer | backend | Asynchronous consistency architecture |
| `PAT-016` | accepted | adopt | BFF/backend | Environment-invariant security boundary |
| `PAT-017` | accepted | adopt/adapt | cross-cutting | Correlated, data-minimized observability |
| `PAT-018` | deferred | defer | backend | Operation-aware retry policy |
| `PAT-019` | accepted | adopt | DevOps | Versioned CI composition and validation |
| `PAT-020` | accepted | adapt | DevOps | Immutable promotion and tested rollback |
| `PAT-021` | accepted | adapt | DevOps | Reproducible containers with active health |
| `PAT-022` | accepted | adapt; exact implementations defer | DevOps | Versioned deployment base and secret references |
| `PAT-023` | accepted | adapt; exact implementations defer | DevOps | Reusable operations with verified recovery |
| `PAT-024` | accepted | adopt | cross-cutting | Ancestry-safe evolution |
| `PAT-025` | rejected | avoid | cross-cutting | Unbounded centralization and shortcuts |

## `PAT-001` — Ownership-aligned deployable boundary

- Status/mode: `accepted / adapt`.
- Problem: avoid both inseparable systems and unnecessary repository/service proliferation.
- Observation: independently buildable frontend packages, application-local deployment artifacts, specialized operational units, six layered backend services, and two single-project counterexamples coexist.
- Inference: boundaries correlate with ownership, release cadence, runtime isolation, security, and complexity—not with every feature or layer.
- Recommendation: start with a capability-owned module; extract an independent deployable only when an explicit isolation requirement exists.
- Evidence: `E-FE-000001`, `E-BE-000001`, `E-DO-000001`.
- Counterexample/exception: standalone applications and thin services disprove universal polyrepo and four-project rules.
- Branch scope/confidence: selected primary frontend commits plus `SAMPLE`; `high` for observed structures, `medium` for the boundary-selection inference. Backend history remains unavailable (`Q-001`).

## `PAT-002` — Single composition owner

- Status/mode: `accepted / adopt` for composition ownership; `adapt` for the target BFF transport.
- Observation: the frontend host registers remotes and owns global guards; the public gateway explicitly composes feature extensions and downstream clients.
- Inference: each public boundary benefits from one composition owner while feature behavior remains independently owned.
- Recommendation: assign one shell owner for global routing/guards and one REST BFF owner for public composition; expose small feature registration contracts.
- Evidence: `E-FE-000002`–`E-FE-000004`, `E-BE-000004`.
- Counterexample/exception: standalone applications need no federation; a loader abstraction does not guarantee downstream batching.
- Branch scope/confidence: `SHELL`, selected remotes, assigned gateway paths in `SAMPLE`; `high`. TARGET requires public REST over internal gRPC; GraphQL is composition evidence only.

## `PAT-003` — Explicit public surface

- Status/mode: `accepted / adopt`; exact generated-contract placement `defer`.
- Observation: frontend barrels, package export maps, remote route modules, and physically separated contract/generated boundaries expose intended public surfaces.
- Inference: small entry points make ownership and compatibility reviewable.
- Recommendation: every feature, remote, shared package, and adapter exports a supported entry point; consumers do not deep-import internals or generated transport types.
- Evidence: `E-FE-000003`, `E-FE-000008`, `E-BE-000002`, `E-BE-000007`.
- Counterexample/exception: deep imports and direct Application dependencies on generated contracts demonstrate leakage; backend contract generation has one verified family only.
- Branch scope/confidence: `COMMON`, `SHELL`, selected remotes and `SAMPLE`; `high` for frontend public APIs, `medium/deferred` for exact backend generation layout.

## `PAT-004` — Feature/capability ownership

- Status/mode: `accepted / adopt`.
- Observation: frontend features commonly own UI, model, transport, types, helpers, and styles; backend services group transport, application, domain, and persistence concerns by capability.
- Inference: ownership should be vertical first; horizontal shared layers should contain only reusable mechanics.
- Recommendation: place changeable capability code under one feature/capability root; pages and transport adapters compose or dispatch rather than owning rules.
- Evidence: `E-FE-000007`, `E-FE-000010`, `E-BE-000001`, `E-BE-000003`, `E-BR-000004`.
- Counterexample/exception: flat legacy units and thin services are valid exceptions; divergent dashboard endpoints use alternative common-versus-feature-local boundaries.
- Branch scope/confidence: selected frontend commits, divergent `DASHBOARDS` endpoints, `SAMPLE`; `high` for placement tendency, `medium` for choosing between shared and local ownership.

## `PAT-005` — Route-module contract

- Status/mode: `accepted / adopt`; lazy loading and metadata vocabulary `adapt`.
- Observation: hosts dynamically register remote route modules; route trees favor unique names, relative children, named redirects, and bounded layout/navigation metadata.
- Inference: routing should be validated as an external contract rather than incidental page configuration.
- Recommendation: export one route module per feature/remote, register before mount, keep global guards in the shell, and validate route names, paths, and exposed files.
- Evidence: `E-FE-000003`–`E-FE-000006`, `E-FE-000018`.
- Counterexample/exception: duplicate names/paths, stale federation exports, differing metadata keys, and eager/lazy coexistence.
- Branch scope/confidence: `SHELL`, selected remotes, `SAMPLE`; `high`. Exact route-name syntax and metadata vocabulary are owner decisions.

## `PAT-006` — Bounded component and state API

- Status/mode: `accepted / adopt`.
- Observation: newer components use typed props/emits; reusable components also use slots/models; state commonly lives in Composition-API Pinia stores under `model`, with transport in `api` or `queries`.
- Inference: explicit interfaces and transport separation reduce coupling despite legacy syntax differences.
- Recommendation: use typed props/emits/slots, `useX` composables, feature-owned stores, and transport functions outside components/pages.
- Evidence: `E-FE-000009`–`E-FE-000011`.
- Counterexample/exception: legacy runtime-only props, mixed service/store naming, module-scoped state, and naming exceptions remain.
- Branch scope/confidence: selected primary frontend commits and `SAMPLE`; `high` for separation, `medium` for normalized naming.

## `PAT-007` — Shared styling foundation with local scope

- Status/mode: `accepted / adopt` for tokens/pipeline; `adapt` for module-first placement.
- Observation: thirteen PostCSS configurations repeat imports, prefixing, variables, nesting, minification, and px-to-rem; shared tokens/mixins coexist with global PCSS, modules, and scoped styles.
- Inference: shared foundations transfer; legacy global aggregation does not.
- Recommendation: centralize tokens/mixins, colocate module PCSS with new components, and limit global styles to base primitives and isolated legacy bridges.
- Evidence: `E-FE-000012`–`E-FE-000014`, `E-BR-000002`, `E-BR-000003`.
- Counterexample/exception: global BEM, modules, scoped styles, and indirect token consumption coexist.
- Branch scope/confidence: `COMMON`, selected applications, `BLOG`, `DASHBOARDS`, `SAMPLE`; `high`. Stylelint and exact migration policy remain owner decisions.

## `PAT-008` — Non-mutating static quality gate

- Status/mode: `accepted / adapt`.
- Observation: flat ESLint repeats across thirteen packages; strict TypeScript exists in four newer samples; several `lint` scripts mutate files; no Stylelint configuration was found.
- Inference: strictness can start with new code while legacy code migrates incrementally.
- Recommendation: run non-mutating lint and type checks in CI, keep `lint:fix` developer-only, and enable strict TypeScript for new modules.
- Evidence: `E-FE-000015`, `E-FE-000016`.
- Counterexample/exception: JavaScript remains common, one package lacks a lint script, and Stylelint is not reference-proven.
- Branch scope/confidence: selected frontend commits and `SAMPLE`; `high` for observed tooling, `medium` for staged migration details.

## `PAT-009` — Target-owned testing standard

- Status/mode: `deferred / defer`.
- Observation: no frontend test/spec file or test script was found across thirteen approved package surfaces; backend evidence contains one active schema snapshot and a commented test.
- Inference: the corpus proves a quality gap, not a reusable testing architecture.
- Recommendation: define target-owned unit, component, integration, transport-contract, smoke, and recovery tests; defer exact frameworks, placement, fixtures, and thresholds.
- Evidence: `E-FE-000017`, `E-BE-000016`.
- Counterexample/exception: one schema snapshot is useful but cannot establish behavioral coverage; prescriptive documentation lacks executable corroboration.
- Branch scope/confidence: assigned frontend package surfaces and one gateway test root in `SAMPLE`; `high` for the gap, `low` for any exact proposed standard.

## `PAT-010` — Thin transport and proportional layering

- Status/mode: `accepted / adopt` for thin adapters; `adapt` for project count.
- Observation: six services repeat layered projects; sampled gRPC services map and dispatch through handlers; two services remain single-project; direct dependencies disprove strict ports/adapters isolation.
- Inference: explicit use cases and thin transport transfer, while layer count follows complexity.
- Recommendation: transport adapters parse/map/dispatch and propagate cancellation; application handlers own use cases; domain rules stay transport-neutral.
- Evidence: `E-BE-000001`–`E-BE-000003`.
- Counterexample/exception: small services may stay single-project; Application and Api projects sometimes reference persistence/generated contracts directly.
- Branch scope/confidence: assigned backend paths in `SAMPLE`; `high` for structure, `medium` for complexity-based adaptation. No history claim (`Q-001`).

## `PAT-011` — Edge composition with feature-local mapping

- Status/mode: `accepted / adapt`.
- Observation: the gateway composes feature registrations and maps edge models to downstream requests; one loader performs one call per key and one mapper concentrates roughly 325 methods.
- Inference: explicit composition and mapping transfer; GraphQL, giant mapping classes, and nominal loaders do not.
- Recommendation: make the REST BFF own public DTOs and orchestration, but keep mappings and internal gRPC clients feature-local.
- Evidence: `E-BE-000004`, `E-BE-000005`.
- Counterexample/exception: N+1 downstream behavior and the giant cross-feature mapper are avoid signals.
- Branch scope/confidence: assigned gateway paths in `SAMPLE`; `high`. TARGET's REST-over-gRPC boundary is an adaptation constraint.

## `PAT-012` — Central error conversion and validation

- Status/mode: `accepted / adopt` for central conversion; `adapt` for RFC 9457 and validator placement.
- Observation: gRPC, GraphQL, and HTTP boundaries centrally translate exceptions, but status/message handling varies and systematic validators were not found.
- Inference: conversion is useful only with stable codes, safe details, and layered validation.
- Recommendation: validate edge shape, application preconditions, and domain invariants separately; map failures centrally to stable RFC 9457 responses.
- Evidence: `E-BE-000010`.
- Counterexample/exception: generic catches expose exception messages and one HTTP path can continue after writing an error.
- Branch scope/confidence: three transport mappings in `SAMPLE`; `high` for centralization, `medium` for exact target taxonomy.

## `PAT-013` — Contract versioning and pagination

- Status/mode: `deferred / defer`.
- Observation: one contract family shows request/response and pagination structures, but compatibility markers and vocabulary are inconsistent.
- Inference: the evidence cannot choose the target's versioning, pagination, or generated-code conventions.
- Recommendation: define REST and gRPC contracts before implementation, but make cursor/offset choice, field vocabulary, compatibility gates, and generated placement explicit owner decisions.
- Evidence: `E-BE-000007`, `E-BE-000008`.
- Counterexample/exception: generated types leak into Application; one filter is empty; no systematic reserved/deprecated/versioned declarations were found.
- Branch scope/confidence: one contract family in `SAMPLE`, generated output excluded; `medium/low` for transfer.

## `PAT-014` — Persistence isolation proportional to need

- Status/mode: `accepted / adapt`.
- Observation: handlers in six layered roots inject concrete `DbContext`; no repository/unit-of-work files were found in assigned paths.
- Inference: repository abstractions are not a reference default.
- Recommendation: allow direct ORM use for simple capability-local work; introduce an application port for complex transactions, multiple stores, storage substitution, or deterministic testing.
- Evidence: `E-BE-000009`, `E-BE-000015`.
- Counterexample/exception: persistence assemblies remain distinct; transaction orchestration is unproven; configuration and migration failure handling contain unsafe defaults.
- Branch scope/confidence: six layered roots and shared hosting helpers in `SAMPLE`; `high` for direct EF use, `medium` for the isolation threshold.

## `PAT-015` — Asynchronous consistency architecture

- Status/mode: `deferred / defer`.
- Observation: one shared interceptor publishes persistence-shaped events after save; no representative adoption or atomic outbox was found.
- Inference: this is a consistency-risk candidate, not a messaging standard.
- Recommendation: introduce messaging only for a concrete asynchronous need, then define versioned events, atomic delivery, idempotent consumers, replay, and sensitive-data rules.
- Evidence: `E-BE-000014`.
- Counterexample/exception: commit can precede publication failure; representative services use ordinary persistence registration.
- Branch scope/confidence: one shared candidate in `SAMPLE`; `low`.

## `PAT-016` — Environment-invariant security boundary

- Status/mode: `accepted / adopt`; environment-dependent enforcement `avoid`.
- Observation: authentication is capability-isolated and the public gateway performs token/permission decisions; one interceptor registration is production-only.
- Inference: edge enforcement transfers only if it remains invariant across environments.
- Recommendation: authenticate and authorize at the public boundary everywhere; propagate explicit trusted identity internally; tests replace providers rather than bypass policy.
- Evidence: `E-BE-000011`.
- Counterexample/exception: production-only registration creates security divergence.
- Branch scope/confidence: assigned authentication and gateway paths in `SAMPLE`; `medium`. Source roles/permissions are not transferred.

## `PAT-017` — Correlated, data-minimized observability

- Status/mode: `accepted / adopt` for identifiers; `adapt` for policy; broad payload logging `avoid`.
- Observation: request/correlation identifiers propagate through headers, logging context, gRPC metadata, and the logging stack; independent implementations serialize sensitive payload classes broadly.
- Inference: identifier propagation is corroborated across backend and operations, while payload collection is a cross-layer risk.
- Recommendation: propagate structured trace/correlation/request IDs; log allowlisted operational fields; define redaction, retention, access, and deletion.
- Evidence: `E-BE-000012`, `E-DO-000013`, `E-DO-000014`.
- Counterexample/exception: bodies, headers, user identifiers, and SQL arguments lack visible masking; trace IDs do not prove distributed tracing.
- Branch scope/confidence: shared backend logging and logging-stack units in `SAMPLE`; `high`.

## `PAT-018` — Operation-aware retry policy

- Status/mode: `deferred / defer`.
- Observation: one helper applies four retries for `Unavailable` at default gRPC method scope; no method-specific idempotency, deduplication, or consistent deadline policy was found.
- Inference: the example is a risk signal rather than a standard.
- Recommendation: define retries only after classifying idempotency, deadline budget, backoff, and duplicate-effect handling.
- Evidence: `E-BE-000013`.
- Counterexample/exception: write operations have no observed exclusion or idempotency safeguard.
- Branch scope/confidence: one shared helper in `SAMPLE`; `medium` as candidate, insufficient for adoption.

## `PAT-019` — Versioned CI composition and validation

- Status/mode: `accepted / adopt`.
- Observation: thirteen wrappers delegate to central templates; immutable primary wrappers pin a version while ten snapshot wrappers do not; container, DNS, and nginx families validate before deployment.
- Inference: small versioned wrappers and deterministic family-specific validation reduce duplication without hiding deployable ownership.
- Recommendation: pin CI components to released versions and require relevant validation before publication/deployment.
- Evidence: `E-DO-000002`, `E-DO-000003`, `E-DO-000018`.
- Counterexample/exception: an inline pipeline exists; no universal lint/test/security sequence is established; unpinned includes drift.
- Branch scope/confidence: selected primary wrappers and assigned `SAMPLE` pipeline families; `high`.

## `PAT-020` — Immutable promotion and tested rollback

- Status/mode: `accepted / adapt`.
- Observation: several artifact families expose versions and environment targets, but use mutable tags, commit-message-driven release changes, and deployment without a rollback selector.
- Inference: visible environments and approvals transfer; artifact identity and recovery require correction.
- Recommendation: build once, promote the same digest, protect sensitive environments, smoke-test deployments, and retain a tested previous known-good target.
- Evidence: `E-DO-000004`, `E-DO-000005`.
- Counterexample/exception: `compose pull/down/up`, mutable branch tags, and direct publishing weaken immutable promotion.
- Branch scope/confidence: three artifact families and four environment targets in `SAMPLE`; `high` for observed mechanisms, `medium` for the target promotion model.

## `PAT-021` — Reproducible containers with active health

- Status/mode: `accepted / adapt`.
- Observation: build families contain unlocked installs, mutable base tags, or runtime installation; 23 Compose manifests lack healthchecks; central Helm capabilities exist but selected overlays leave probes, metrics, or autoscaling inactive.
- Inference: controls must be reproducible and active in consumers, not merely present in templates.
- Recommendation: pin builders/runtimes, use lock-enforcing installs, avoid privilege, declare resources and probes, and verify each overlay activates required controls.
- Evidence: `E-DO-000006`, `E-DO-000007`, `E-DO-000015`, `E-DO-000021`, `E-DO-000023`.
- Counterexample/exception: `latest`, Docker socket mounts, fixed host assumptions, and inactive probe configuration.
- Branch scope/confidence: selected primary deployables, `ASSET-RUNTIME`, and assigned `SAMPLE` infrastructure; `high`.

## `PAT-022` — Versioned deployment base and secret references

- Status/mode: `accepted / adapt`; exact Vault and GitOps implementations `defer`.
- Observation: three deployables consume a versioned OCI chart; eight overlays isolate environments; two chart families use ExternalSecret references; one CI flow uses OIDC/Vault; one safe Argo manifest exists.
- Inference: versioned deployment mechanics, minimal overlays, and external references are supported; exact OIDC and Argo models are not.
- Recommendation: version reusable deployment bases, keep overlays difference-only, reference secrets externally, and prohibit secret material in source or CI output.
- Evidence: `E-DO-000008`–`E-DO-000011`, `E-DO-000021`–`E-DO-000023`.
- Counterexample/exception: chart defaults are quarantined, overlay content is duplicated, controls are disabled, and OIDC/Argo each have one safe executable example.
- Branch scope/confidence: selected primary deployables, `ASSET-RUNTIME`, one central chart and one pipeline family; `high` for chart consumption/references, `low/deferred` for exact Argo/Vault implementation (`Q-002`).

## `PAT-023` — Reusable operations with verified recovery

- Status/mode: `accepted / adapt`; exact backup, alerting, and inventory implementations `defer`.
- Observation: Ansible uses thin playbooks and reusable OS-aware roles; one backup family lacks restore validation; a monitoring stack lacks reusable alert-rule evidence; one inventory generator pushes directly and ignores failures.
- Inference: reusable operational organization transfers after hardening, but the isolated recovery/alert/inventory mechanisms do not establish standards.
- Recommendation: use pinned, assertion-tested operational modules; define recovery by verified restore outcomes; make inventory changes reviewable; assign alert/SLO ownership.
- Evidence: `E-DO-000012`, `E-DO-000016`, `E-DO-000017`, `E-DO-000019`.
- Counterexample/exception: disabled SSH/GPG verification, no restore workflow, no alert-as-code proof, and ignored push failures.
- Branch scope/confidence: assigned operational units in `SAMPLE`; `high` for role organization, `low/medium` for exact recovery, alerting, and inventory mechanisms.

## `PAT-024` — Ancestry-safe evolution

- Status/mode: `accepted / adopt`.
- Observation: ancestry confirms the Blog lineage and two specialized dashboard descendants; dashboard master and generic adaptive endpoints diverge; tooling stays stable; specialized capabilities are non-cumulative; one descendant mixes UI and operational changes.
- Inference: "introduced" is valid only across verified ancestry, and similarly named branches do not form a cumulative framework.
- Recommendation: anchor evolution in immutable OIDs, preserve stable tooling during feature work, and split operational migrations into separately reviewable changes.
- Evidence: `E-BR-000001`–`E-BR-000008`.
- Counterexample/exception: divergent ownership/configuration cohorts, mutually exclusive specialized capabilities, and mixed-scope changes.
- Branch scope/confidence: exact `BLOG` and `DASHBOARDS` endpoints listed above; `high`, except feature-ownership interpretation `medium`. Related decision: `D-009`.

## `PAT-025` — Unbounded centralization and shortcuts

- Status/mode: `rejected / avoid`.
- Observation: counterexamples include deep imports, duplicate/stale routes, a giant mapper, non-batching loaders, raw error messages, environment-dependent authorization, broad payload logging, unpinned CI includes, mutable tags, Docker socket access, secret-bearing CI output, and old/backup pipeline variants.
- Inference: shared infrastructure is safe only when narrow, versioned, observable, and owned.
- Recommendation: reject shared helpers, mappers, templates, loggers, or runtime privileges that bypass public boundaries or hide unsafe defaults.
- Evidence: `E-FE-000008`, `E-FE-000018`, `E-BE-000004`, `E-BE-000005`, `E-BE-000010`–`E-BE-000012`, `E-DO-000002`, `E-DO-000004`–`E-DO-000007`, `E-DO-000014`, `E-DO-000018`, `E-DO-000020`.
- Counterexample/exception: narrow shared packages and versioned templates remain valuable; centralization itself is not rejected. An exception requires an owner, bounded scope, security review, observable failure, and removal plan.
- Branch scope/confidence: selected primary commits and `SAMPLE`; `high` for listed counterexamples. Old/backup names do not establish chronology (`D-004`, `D-009`).

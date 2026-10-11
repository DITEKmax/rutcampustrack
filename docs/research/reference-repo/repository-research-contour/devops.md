# DevOps and tooling practices

Status: `verified / independent audit PASS`  
Work unit: `WU-040-DEVOPS`  
Anchors: three immutable REF-PRIMARY commits plus `REF-SAMPLES@1288bb81051c332f53fc26fc561ae3f16d1063e53f93d92dcef5c5ff2afc55ae`  
Collected by: `devops_conventions_analyst`  
Canonicalized by: `main`

## Snapshot and coverage

The report covers the exact approved application manifests and selected pipeline, release, Helm, Ansible, ingress, deployment-catalogue, logging, monitoring, database, infrastructure, and Vault units. Quarantined `.env`, package-registry, key/certificate, application, frontend, Helm, and PostgreSQL compose paths were never used as evidence. Generated deployment reports were used only as topology cross-checks.

All Git claims use immutable commits and blob OIDs where available; SAMPLE claims use the active safe snapshot anchor and file SHA-256 where recorded. No pipelines, containers, charts, playbooks, scripts, or project code were executed. Network and Figma remained disabled.

## Repository topology and ownership

Deployable application repositories own their Docker, nginx, chart, environment values, and small CI wrapper, while specialized repositories own reusable pipelines, Helm, Ansible, releases, ingress, observability, and deployment catalogues (`E-DO-000001`). Ownership is federated: application repositories still carry deployment details and overlays.

Thirteen inspected wrappers delegate to central templates (`E-DO-000002`). All three immutable primary wrappers pin `1.0.1`; ten snapshot wrappers omit a ref. This supports small versioned wrappers as the target direction and treats unpinned includes as drift.

## CI/CD stages and quality gates

Quality gates are pipeline-family-specific (`E-DO-000003`). Container CI separates build, release-trigger, deploy, and registration and provides a no-push merge-request build. DNS and nginx pipelines validate configuration before deployment. Assigned templates do not establish a universal lint, unit, integration, SAST, or dependency-scan sequence.

Target CI should make the family-specific validation pattern universal where relevant: non-mutating lint/static analysis, tests, manifest/container validation, no-push build, immutable publication, non-production deploy, smoke checks, then approved promotion.

## Versioning, releases, promotion, and rollback

Three artifact families use sanitized branch tags, repository `VERSION` files, and commit-message-driven SemVer updates (`E-DO-000004`). Mutable branch tags and direct package publishing prevent one consistent immutable promotion model.

Four environments are explicit manual targets; production is message/branch gated, but deployment uses `compose pull/down/up` and no rollback job, previous-image selector, or protected environment declaration was found (`E-DO-000005`). Adopt environment visibility and approvals; adapt promotion to reuse one image digest and record/test rollback.

## Containers and build artifacts

Three unique build families were observed (`E-DO-000006`). Frontend images use a two-stage build-to-static-nginx design; asset and operational helpers differ. Reproducibility gaps include `npm install`, runtime package installation, `golang:latest`, and no Dockerfile health checks.

Across 23 non-quarantined Compose files, host-oriented patterns dominate: 20 use `env_file`, 21 an external network, 21 fixed container names, 19 restart policies, and 17 host data binds; no Compose healthcheck was found (`E-DO-000007`). Two Docker-socket mounts and a `latest` image are important avoid signals. Seventeen PostgreSQL manifests count as one structural family, not seventeen independent confirmations.

## Helm, Argo, Ansible, and environment layering

Three application repositories consume a versioned OCI chart (`E-DO-000008`). Separately, one assigned central chart contains conditional deployment, service, ingress/HTTPRoute, HPA, RBAC, PVC, network-policy, ConfigMap, and ExternalSecret template families (`E-DO-000021`). This is a producer capability inventory, not proof of active defaults: sensitive values/test files are quarantined, and copy-identical chart metadata in two consumers indicates drift.

Eight immutable overlays across three repositories isolate environment differences (`E-DO-000009`). Most content is duplicated, image tags are mutable placeholders, and hardening capabilities remain disabled. Preserve explicit overlays but minimize duplication and require production settings.

Only one safe executable Argo manifest exists (`E-DO-000011`): automated sync with `prune=false`, `selfHeal=false`, and mutable `HEAD`. The dedicated Argo sample is boilerplate. Keep Argo conventions candidate/deferred until corroborated.

Ansible separates thin playbooks from reusable, OS-aware roles (`E-DO-000012`). Security and reproducibility exceptions include disabled host-key/GPG verification, unpinned runtime installation, deprecated package setup, shell use, and assertion-free role tests. Transfer the organization, not those mechanics.

## Configuration and secret references

Two chart families use ExternalSecret references (`E-DO-000010`). Separately, one reusable CI template obtains a Vault token through GitLab OIDC before materializing deployment environment data (`E-DO-000022`); no TTL or organization-wide enforcement is inferred. Counterexamples are Helm helpers capable of materializing secrets and CI paths that can log Vault or certificate payloads. Six detected paths remain quarantined and provide zero evidence.

Target policy: no checked-in secret values, no secret-bearing CI output, short-lived workload identity, external secret references, scoped access, rotation ownership, and explicit redaction tests.

## Logging, metrics, tracing, health, and alerts

The logging stack flows Filebeat through Logstash to Elasticsearch and normalizes service/environment identity plus trace, span, correlation, request, and message IDs; Kibana provisioning creates six searches (`E-DO-000013`). Searchable trace IDs do not prove distributed tracing because no collector/backend configuration was found.

The centralized pipeline allowlists bodies, headers, user identifiers, and SQL arguments without visible redaction or retention (`E-DO-000014`). Adopt structured identifiers; avoid broad payload ingestion and define masking, retention, access, and deletion policy.

One central Helm deployment template conditionally supports readiness/liveness probes (`E-DO-000015`), and the chart inventory contains metrics/HPA surfaces (`E-DO-000021`). Six Main/Dashboards overlays disable metrics and autoscaling and leave probes empty; two Basemaps overlays disable autoscaling (`E-DO-000023`). Separately, the verified Compose-family scan found no healthcheck in 23 manifests (`E-DO-000007`). Capability is therefore not an active convention. A Zabbix stack and agent role exist, but no reusable alert rules or dashboard-as-code were found (`E-DO-000016`); external configuration remains possible.

## Operational tests and recovery practices

DNS/nginx configuration checks are useful narrow operational tests (`E-DO-000003`, `E-DO-000018`), but no general post-deploy smoke or recovery verification gate is established.

The PostgreSQL backup template stops Compose, copies a data directory, and restarts, expanded across a broad matrix (`E-DO-000017`). No restore job, validation, retention, or off-host copy was found. Breadth is not recoverability; the target needs automated backup plus restore verification and recovery objectives.

Nginx promotion validates before a six-environment matrix, but both operations are manual, deployment inherits `allow_failure`, and rollback backup was discarded (`E-DO-000018`).

One container CI template generates deployment-topology reports and pushes them to a separate catalogue (`E-DO-000019`). Generated catalogue contents are topology context, not confirmation counts. Direct pushes and ignored commit/push failures are counterexamples; recommendation: any target inventory change should be reviewed and failure-visible.

## Branch differences and legacy pipelines

The sample collection is unversioned, so chronology is unknown. It contains two explicitly named old/backup pipeline families and one service-specialized variant alongside a non-suffixed reusable template (`E-DO-000020`). Their names do not prove runtime adoption or deprecation; recommendation: do not promote them as target standards without current ownership evidence. Branch-level evolution for immutable repositories belongs to `WU-050-BRANCHES`.

## Transfer candidates

| Practice | Mode | Evidence IDs | Preconditions | Risks |
|---|---|---|---|---|
| Versioned central CI components with small wrappers | adopt | `E-DO-000002` | template ownership/releases | hidden central behavior |
| Validate configuration before deployment | adopt | `E-DO-000003`, `E-DO-000018` | deterministic validators | manual-only gates |
| Reusable Helm base plus environment overlays | adapt | `E-DO-000008`, `E-DO-000009`, `E-DO-000021` | safe defaults and schema | one producer; copy drift |
| ExternalSecret and workload-identity Vault references | adapt | `E-DO-000010`, `E-DO-000022` | scoped stores/rotation/TTL | single CI flow; log/materialization leaks |
| Structured logs with correlation identifiers | adopt | `E-DO-000013` | schema and retention | false tracing inference |
| Repository split only for independent ownership/runtime | adapt | `E-DO-000001` | ownership decision | polyrepo overhead |
| Promote immutable digest plus semantic release | adapt | `E-DO-000004` | registry provenance | mutable tags |
| Protected promotion, smoke test, tested rollback | adapt | `E-DO-000005` | environment ownership | downtime rollback |
| Require resources, probes, metrics, HPA, network policy | adapt | `E-DO-000007`, `E-DO-000015`, `E-DO-000021`, `E-DO-000023` | workload SLOs | inactive consumer configuration |
| Pin Ansible/tooling and verify SSH/GPG | adapt | `E-DO-000012` | supply-chain ownership | legacy host support |
| Reviewed generated deployment inventory | adapt | `E-DO-000019` | deterministic generator | direct-push drift |
| `latest`, mutable branch tags, unlocked installs | avoid | `E-DO-000004`, `E-DO-000006`, `E-DO-000007` | none | irreproducibility |
| Docker socket access | avoid | `E-DO-000007` | isolated operator exception | host compromise |
| Compose down/up without rollback | avoid | `E-DO-000005` | none | downtime/data risk |
| Secret rendering or secret-bearing CI logs | avoid | `E-DO-000010`, `E-DO-000018` | none | credential exposure |
| Unredacted bodies/headers/SQL arguments | avoid | `E-DO-000014` | none | sensitive-data exposure |
| Filesystem-copy backup as sole recovery | avoid | `E-DO-000017` | none | unverified restore |
| Old/backup/specialized pipelines as standards | avoid | `E-DO-000020` | none | legacy propagation |
| Canonical Argo model | defer | `E-DO-000011` | more safe executable examples | single-example overfit |
| Alerting/dashboard standard | defer | `E-DO-000016` | actual rules and ownership | unsupported invention |

## Counterexamples and unknowns

- The referenced central primary template implementation is outside approved source scope.
- Helm runtime defaults are unknown because central values/test files are quarantined.
- Argo promotion and rollback are under-evidenced (`E-DO-000011`).
- No executable SLO, alert-rule, retention, trace-backend, or restore-verification standard was found.
- Manual operations and mutable tags coexist with versioned templates.
- Duplicate blobs and derived PostgreSQL manifests are de-duplicated in counts.
- Runtime behavior was not executed or inferred.

## Evidence index

| Evidence ID | Primary locator | Count | Confidence |
|---|---|---:|---|
| `E-DO-000001` | `RDP-VSM-MAIN:.gitlab-ci.yml:1` | 12 | high |
| `E-DO-000002` | `RDP-VSM-MAIN:.gitlab-ci.yml:1` | 13 | high |
| `E-DO-000003` | `pipelines-master/docker-cicd/docker.yml:1` | 3 | high |
| `E-DO-000004` | `pipelines-master/releases/pipeline.yml:17` | 3 | high |
| `E-DO-000005` | `pipelines-master/docker-cicd/docker.yml:39` | 4 | medium |
| `E-DO-000006` | `RDP-VSM-MAIN:app/Dockerfile:1` | 3 | high |
| `E-DO-000007` | `RDP-VSM-BASEMAPS-ASSETS:app/deployment/docker-compose.yml:1` | 7 families | high |
| `E-DO-000008` | `RDP-VSM-MAIN:chart/Chart.yaml:1` | 3 | high |
| `E-DO-000009` | `RDP-VSM-MAIN:chart/values-dev-1.yaml:1` | 8 | high |
| `E-DO-000010` | `RDP-VSM-MAIN:chart/values-dev-1.yaml:8` | 2 chart families | high |
| `E-DO-000011` | `RDP-VSM-BASEMAPS-ASSETS:application.yaml:1` | 1 | low candidate |
| `E-DO-000012` | `ansible-master/roles/init-host/tasks/main.yml:2` | 5 | high |
| `E-DO-000013` | `logging-master/filebeat/filebeat.yml:1` | 3 | high |
| `E-DO-000014` | `logging-master/logstash/pipeline/logstash.conf:90` | 1 | high |
| `E-DO-000015` | `helm-chart-rut-master/templates/deployment.yaml:56` | 1 chart family | high |
| `E-DO-000016` | `zabbix-master/docker-compose.yaml:1` | 2 | medium candidate |
| `E-DO-000017` | `pipelines-master/postgres-deploy/deploy-pg.yml:40` | 1 family | high |
| `E-DO-000018` | `nginx-master/nginx-servers.yaml:7` | 6 | high |
| `E-DO-000019` | `pipelines-master/docker-cicd/docker.yml:178` | 1 | medium |
| `E-DO-000020` | `pipelines-master/docker-cicd_old/docker.yml` | 3 | medium |
| `E-DO-000021` | `helm-chart-rut-master/templates` | 1 chart family | high |
| `E-DO-000022` | `pipelines-master/docker-cicd/docker.yml:21` | 1 | high |
| `E-DO-000023` | `RDP-VSM-MAIN:chart/values-dev-1.yaml:95` | 8 overlays | high |

All 23 records were independently verified by `evidence_auditor` at `2026-08-30T21:37:32.0237643+03:00`.

# Requests harness R9/R10 scoped evidence

Дата: 2026-09-15. Риск: S3. Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness`. Ветка: `codex/v2-requests-harness`. Frozen base R9: `e1c67c7a4ebe0cb6e334a0cac5d108c14298a1ff`; предыдущий R8 evidence checkpoint: `9be3e213d0977abd66478e77af57e04373d7d829`; исходный R7 evidence checkpoint: `13bc053a0f4b88c8b9149ea6b3338aa02fa695b9`. Правила: `RULES.md` SHA-256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`. Sole writer менял только `requests-runtime/**`; E, oldroot, product sources, accepted seeds и foreign worktrees оставлены read-only.

## R7 correction criteria

- F1: `Get-MongoSnapshot` validates a request id as exactly 24 hexadecimal characters. The generated JavaScript uses BSON `ObjectId` only for the ticket `_id`, keeps attachment `request_id` as the original string, and queries outbox `payload.$regex` with that string. The source checker no longer asserts an implementation spelling; `r7-mongo-query-check.ps1` parses and executes the emitted snippet against observable stubs.
- F2: UnionRepo resolution occurs after the report boundary is initialized. An invalid path is persisted as redacted `preflight-failure.json` with exit 1, no run identity, no commands, and cleanup `NOT_RUN`; no runtime resource is allocated.
- F3: Trusted manifest pin and JSON parse both use one `ReadAllBytes` buffer. `Get-Sha256BytesHex` hashes that buffer; the synthetic check proves a content swap cannot be accepted under the previous pin.
- F4: `artifacts.pwaDist` requires a complete, strictly sorted file list and `manifestSha256`. The digest is SHA-256 over UTF-8 bytes of compact JSON array serialization with fields in fixed order `bytes`, `relativePath`, `sha256`; files are sorted by `relativePath`, include `index.html`, `sw.js`, and `sw-assets.js`, and remain path-confined. The reader rejects missing or mismatched digests and compares the current dist digest and file bytes.
- F5: Fixed and chunked oversize requests are response-aware. An HTTP 413 followed by an upload-socket EPIPE is retained as an observed 413; a transport error before any HTTP response rejects. Runtime I2 still requires fixed/chunked HTTP 413, `Cache-Control: no-store`, exact Gateway/BFF counters unchanged, Mongo/outbox unchanged, and Nginx rejection evidence. The probe self-test uses deterministic local `MOCKTRANSPORT` only.

## R8 correction criteria

- F1: `httpsFixedOversize` accepts both explicit `contentType` and the headers-only runtime call shape, emits one valid `Content-Type`, and the local transport rejects undefined/null header values like Node. Both runtime fixed oversize callsites pass the explicit content type.
- F2: The runner creates the exact `/24` bridge with gateway `${subnet-prefix}.1` and `--ip-range ${subnet-prefix}.128/25`, which excludes static edge `${subnet-prefix}.10` from dynamic allocation. It creates the edge endpoint before infrastructure/backend startup without claiming an operational address; only after `docker start` does it inspect and verify the actual `.10` address, then wires the same address to `GATEWAY_TRUSTED_PROXY_ADDRESSES`. An occupied/mismatched reservation fails closed before adding a resource. This follows Docker's documented `--ip-range` sub-range allocation semantics: [Docker network create](https://docs.docker.com/reference/cli/docker/network/create/). Full Docker proof remains separately leased.
- F3: Mongo projection maps the accepted `request_attachments.type` field to canonical `content_type` and carries the exact eight attachment descriptor fields through both stored documents and `excuse.requested` outbox payload. The ticket `_id` alone uses BSON `ObjectId`; attachment `request_id` stays the submitted string. Any document or outbox descriptor corruption fails I1.
- F4: The probe compares `reason`, `comment`, `summary.lessons`, `summary.kind`, `summary.origin`, and the selected lesson against the submitted payload. Six deterministic mutations are rejected by the source self-test.
- F5: The invalid-UnionRepo boundary test runs a copied runner in a disposable temp root, persists the expected redacted report, and proves an existing task report plus a fixture sentinel retain their bytes.
- F6: PWA traversal and artifact path confinement reject reparse roots, descendants, and ancestors before hashing or mounting; a temporary junction proof covers the ancestor case.
- F7: Each authenticated probe performs `GET /api/auth/session`, requires a no-store student session, and the runner waits the frozen 13-second login interval between I1 and I2 using an injectable clock/sleeper. The pure test uses no real sleep.

## R9 correction criteria

- F1: Docker's `create --ip` is not treated as an operational IP allocation. The selected aligned `/24` is validated; explicit gateway `.1` and dynamic `--ip-range=.128/25` are inside the exact subnet, exclude static edge `.10`, and are passed in the network create argv. Edge create-only preparation makes no pre-start inspect claim; post-start inspect verifies `.10`. The pure check exercises both candidate `/24`s, unaligned/invalid boundaries, occupied fail-closed behavior and the actual start-before-inspect state transition.
- F2: The probe validates the accepted `CurrentSessionResponse` wire shape: top-level `userId` matching `[1-9][0-9]*` and `activeRole=STUDENT`. The self-test uses faithful DTO fields from `RoleGrantResponse` and `PasswordPolicyResponse` and rejects nested-user, invalid-id and wrong-role mutations.
- F3: Every owned `docker create` and `docker run` argv contains `--pull=never`. The actual launcher is exercised with exact argv capture; a missing image fails at the launcher boundary without an implicit pull.
- F4: Auth pacing is represented by separate per-probe records attached to each returned probe and report assertion. A deterministic callsite test proves I1 remains `0` ms after I2 records `12000` ms; no shared mutable wait value can overwrite I1 evidence.

## R10 real-Docker network proof preparation

The bounded proof was prepared for a separate H32 root activation. The wrapper
`.agent/student-role-orchestrator/requests-runtime/r10-docker-network-proof.ps1` invokes the runner's
actual `-NetworkProof` branch. That branch calls the shared production `New-RequestsOwnedNetwork`
helper and `Start-OwnedContainer`; it contains no copied Docker helper or product service startup.

Reserved activation command, using the read-only inventory captured before preparation:

```powershell
pwsh -NoProfile -NonInteractive -File C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-requests-harness\.agent\student-role-orchestrator\requests-runtime\r10-docker-network-proof.ps1 -Subnet 172.30.185.0/24 -EvidencePath C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-requests-harness\.agent\student-role-orchestrator\requests-runtime\r10-docker-network-proof.json
```

The proof first requires the existing local pinned image
`nginx@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10`
(read-only inspect exit 0; local tag `nginx:1.27-alpine`). It creates at most one uniquely named
bridge network with subnet `172.30.185.0/24`, gateway `.1`, and dynamic range `.128/25`, then the
expected two uniquely named, owner-labelled, no-host-port containers: one create-only static edge
at `.10`, and one running dynamic nginx container. A third container is allowed only as a recorded
worst-case bound if an unexpected missing-image create succeeds; the proof then fails and owned
cleanup still removes it. It verifies actual IPAM, collision absence before create, ownership
labels/names, dynamic allocation in `.128/25`, static `.10` only after `docker start`, and
`--pull=never` on create, run, and missing-image failure. The runner's owned cleanup removes and
verifies every proof container and the network; cleanup errors change the proof status to FAIL and
remain in the JSON.
No image download, product service, host port, volume, database, key, or secret is used.

Read-only inventory evidence: `docker image inspect` for the pinned nginx exited 0 under the narrow
read-only escalation; `docker network ls/inspect` showed existing subnets `172.17/16`, `172.18/16`,
`172.19/16`, `172.21/16`, and `172.22/16`; `172.30.185.0/24` was absent. Initial sandbox-only
image/network reads were denied by Docker socket permissions (exit 1), with no mutation. Existing
containers, including unrelated `Created` entries, were left untouched before activation. The
actual H32 result is recorded below.

## H32 execution evidence

Frozen source commit `27a5be4ffc16c18067ae5901cfb76d604efbe236` and the prepared source SHA values
were verified before execution. The first exact command attempt in the sandbox exited `1` during the
initial pinned-image inspect because Docker socket access was denied; it created no resource and
wrote a FAIL preflight report with SHA-256
`12FC8BC8EF18B741372E969EB4BEDC5FE622DE1EE739E9A8503636FC5562B8B8`. Per the H32 gate, the same
exact command was then retried once with narrow `require_escalated` Docker access.

The escalated exact command exited `0`. Its stdout was
`NETWORK_PROOF_PASS: evidence written to .../r10-docker-network-proof.json`; stderr contained only
non-fatal PowerShell profile/virtual-terminal warnings. Final report:
`r10-docker-network-proof.json`, SHA-256
`0D07C5C7B346F3C0E0E68173C184A811CF283A004AAD9C7F7916CBC298F2A9BA`.

The proof observed the existing-subnet collision set before creation, created network
`070f7f7f4af3f4c42e3cdf3e021d28098847fc232beac8a7e61f4756e3300f7a` with subnet
`172.30.185.0/24`, gateway `172.30.185.1`, and dynamic range `172.30.185.128/25`. The dynamic
container received `172.30.185.128`; the static edge received `172.30.185.10` only after
`docker start`. Production create/run argv carried `--pull=never`; the zero-digest image inspect
exited `1`, the missing-image create exited `1`, and the missing container name remained absent.
Cleanup removed and verified absent both owned containers and the network, with `cleanup.status=PASS`
and no recorded errors. A final scoped read-only `docker ps`/`docker network ls` query returned no
matching owned names (exit `0`). Product services, host ports, volumes, keys, secrets, Gradle,
ValidateOnly, I1/I2 and browser runtime remain **NOT_RUN**.

## All findings traceability (29)

| Finding | Source/contract anchor | Current correction/evidence |
| --- | --- | --- |
| R2-1 HIGH cleanup | `Remove-OwnedResources` / cleanup contract | Reverse owned-container order, check removal and absence exit codes, aggregate cleanup errors into FAIL; `r3-cleanup-failure-injection.ps1` PASS. |
| R2-2 HIGH metrics | runtime-only actuator exposure and I2 counters | Runner publishes `health,info,metrics` only on disposable services and checks the exact URI counter; source contract PASS; runtime NOT_RUN. |
| R2-3 HIGH outbox key | `Get-MongoSnapshot` / publisher `payload.ticket_id` | Ticket filter uses `ObjectId`; outbox projection filters and reports `payloadTicketId`; R7 Mongo query and R8 Mongo projection checks PASS. |
| R2-4 MEDIUM subnet | `Select-DisposableSubnet` | Docker template output is parsed line-by-line as anchored CIDR; collision and malformed-output pure cases PASS. |
| R2-5 MEDIUM ValidateOnly | `Invoke-ValidateOnlyChecks` | Node syntax/self-test, pinned images, and no-resource boundary remain required; ValidateOnly and Docker prerequisites are NOT_RUN under this lease. |
| R2-6 MEDIUM PNG | `makeDeterministicPng` / `validatePng` | Valid 1x1 RGBA scanline, legal ancillary chunk, CRC/order/inflate checks; probe self-test PASS. |
| R2-7 MEDIUM Auth readiness | backend service readiness | Auth waits for HTTP actuator health 2xx before probe; source contract PASS; runtime NOT_RUN. |
| R2-8 MEDIUM PWA/evidence | trusted manifest and runtime report | Real pinned PWA manifest/mount and redacted phase/report schema remain enforced; R6 manifest/PWA pure check PASS. |
| R6-1 HIGH Mongo id | `Get-MongoSnapshot` request filter | Exactly 24 hex is validated and only ticket `_id` is converted to `ObjectId`; attachment string linkage preserved; R7/R8 Mongo checks PASS. |
| R6-2 HIGH trusted artifacts | `Resolve-TrustedBuildArtifacts` | Expected revision, main SHA pin, provenance, exact jars/PWA bytes and canonical serialization fail closed; R6 manifest check PASS. |
| R6-3 MEDIUM Academic readiness | `Start-BackendServices` | HTTP 2xx readiness is recorded before Flyway/schema query; R6 source contract PASS. |
| R6-4 MEDIUM preflight report | runner finally boundary | Full preflight failure writes redacted JSON before run identity; disposable fixture and task-report sentinel preservation PASS. |
| R6-5 MEDIUM review order | frozen contract/evidence | This evidence requires fresh independent review before ValidateOnly or product runtime; both remain NOT_RUN. |
| R7-1 HIGH emitted Mongo JS | generated `payload['$regex']` | Actual emitted snippet parses and stub-observes ObjectId, string attachment filter, and `$regex`; R7/R8 Mongo checks PASS. |
| R7-2 MEDIUM invalid repo | resolve path after report initialization | Invalid UnionRepo exits 1 with JSON and zero commands/resources; disposable boundary check PASS. |
| R7-3 MEDIUM manifest TOCTOU | one `ReadAllBytes` buffer | Hash and UTF-8 parse derive from the same buffer; content-swap case rejected by R6 check. |
| R7-4 MEDIUM PWA digest | canonical PWA list | Sorted exact file set, path confinement, UTF-8 canonical digest, and missing/wrong digest cases PASS. |
| R7-5 MEDIUM fixed 413 | response-aware oversize transport | Fixed/chunked 413 with early EPIPE is retained; no-response transport fails; R8 probe check PASS. |
| R8-1 HIGH header callsite | `httpsFixedOversize` and both runtime callers | Explicit/header-derived content type and Node-style header validation; `r8-probe-callsite-check.ps1` PASS. |
| R8-2 HIGH edge collision | `Start-Edge` / `Invoke-FullRuntime` | Create-only edge preparation with explicit IPAM exclusion precedes dynamic startup; exact operational inspect/trusted peer occurs after start, occupied failure no leak; `r8-edge-reservation-check.ps1` PASS. |
| R8-3 HIGH attachment projection | `Get-MongoSnapshot` / `Assert-I1MongoDelta` | Exact eight-field canonical descriptors compared to documents and outbox; document and outbox corruption rejected; `r8-mongo-projection-check.ps1` PASS. |
| R8-4 MEDIUM detail projection | `assertRequestDetailContract` | Reason/comment/lessons/kind/origin/selected lesson checks plus six corruption mutations; Node self-test and callsite check PASS. |
| R8-5 MEDIUM preflight fixture | `r7-preflight-boundary-check.ps1` | Only disposable copied runner/report root is removed; existing report and fixture sentinel bytes survive; check PASS. |
| R8-6 MEDIUM reparse ancestor | `Assert-AbsolutePathConfined` / `Get-PwaDistManifest` | Root, ancestor, descendant and traversal reparse points are rejected; temporary junction check PASS. |
| R8-7 MEDIUM session/pacing | probe session GET and runner pacing | Real `/api/auth/session` with no-store student gate plus injectable 13-second interval; `r8-auth-pacing-check.ps1` PASS. |
| R9-1 HIGH Docker edge allocation | network create IPAM and `Start-Edge` | Explicit gateway `.1` and `--ip-range=.128/25` exclude static `.10`; create-only path does not inspect before start, post-start inspect verifies actual address; both candidates and invalid boundaries plus occupied no-leak case PASS in `r8-edge-reservation-check.ps1`. |
| R9-2 HIGH Auth session DTO | `assertStudentSessionContract` / `CurrentSessionResponse` | Top-level positive-decimal `userId` and `STUDENT` role are required; faithful roles/password policy fixture and nested-shape/id/role corruptions rejected by `r8-probe-callsite-check.ps1`. |
| R9-3 MEDIUM image pull policy | `Start-OwnedContainer` | Explicit `--pull=never` is present for both create/run; exact argv and missing-image fail-closed cases PASS in `r8-edge-reservation-check.ps1`. |
| R9-4 MEDIUM pacing evidence | `Invoke-RequestsProbe` and I1/I2 report projection | Distinct probe-local records preserve I1 `0` and I2 `12000` ms; actual callsite check PASS in `r8-auth-pacing-check.ps1`. |
| R10 proof gate | shared network helper, production launcher, proof wrapper | Exact production network argv and `Start-OwnedContainer` passed the activated H32 proof; existing-subnet collision, actual IPAM, post-start `.10`, dynamic `.128`, missing image/name and owned cleanup all PASS in `r10-docker-network-proof.json`. |

## Trusted build manifest format

The producer is outside this bounded source lane and must, after a successful exact-E clean build, provide an external UTF-8 manifest. Top-level keys are exactly `artifacts`, `producer`, `schemaVersion`, `source`; canonical compact JSON has no BOM, CR/LF, or trailing newline. `schemaVersion` is `1`. `source` contains the absolute `absoluteRepo`, exact lowercase 40-hex `revision`, and `cleanBeforeBuild=true`/`cleanAfterBuild=true`. `producer.commands` has at least one `{command,exitCode,finishedAt,startedAt}` entry with exit code 0 and ordered ISO-8601 timestamps; `producer.environment` contains `gradle`, `hostOs`, `java`, `node`, and `powershell`, with no secret-like values.

`artifacts.jars` contains exactly the six expected roles and `{bytes,name,relativePath,sha256}` entries, with canonical paths sorted by ordinal `relativePath`, direct-child bootJar paths, and no duplicate/escaping/non-executable jar. `artifacts.pwaDist` contains `relativeRoot=frontends/pwa-vue/dist`, the sorted `{bytes,relativePath,sha256}` list, and `manifestSha256`. The PWA digest is over the compact canonical JSON array described above, encoded as UTF-8. The runner requires the main-supplied expected union revision, manifest path, and manifest SHA pin; validates provenance, canonical reserialization, exact artifact set, current bytes/hashes, current PWA digest, and source revision before creating runtime resources. No manifest is produced from existing jars/dist or from HEAD in this lane.

## Checks and exit codes

All pure commands below ran in the R9/R10 worktree with PowerShell `7.6.5` and Node `v24.14.0`, with no Docker mutation, Gradle, server, or port activity.

| Check | Command/result | Exit |
| --- | --- | ---: |
| PowerShell AST | `pwsh -NoProfile -NonInteractive -Command '<ParseFile runner.ps1 and all scoped *.ps1 under requests-runtime>'`; all 14 scoped scripts parsed | 0 |
| Node syntax | `node --check .agent/student-role-orchestrator/requests-runtime/probe.mjs` | 0 |
| Probe source self-test and callsites | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r8-probe-callsite-check.ps1`; Node syntax, headers-only/fixed content type, faithful CurrentSessionResponse fixture and corruptions, detail corruption, fixed/chunked EPIPE-after-413 retention, no-response transport failure | 0 |
| Trusted manifest/PWA pure fixture | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r6-build-manifest-check.ps1`; valid six-jar fixture accepted; extra jar, content swap, stale revision, wrong main pin, missing/wrong PWA digest rejected | 0 |
| Generated Mongo query and projection | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r8-mongo-projection-check.ps1`; emitted JS parsed/executed against stubs; BSON/string/regex shapes, `type→content_type`, all eight descriptor fields, and document/outbox corruption rejection observed | 0 |
| Generated Mongo query compatibility | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r7-mongo-query-check.ps1`; emitted JS parsed by Node and executed against stubs; BSON/string/regex shapes and invalid id rejection observed | 0 |
| Invalid UnionRepo boundary | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r7-preflight-boundary-check.ps1`; disposable copied runner exit 1, redacted JSON persisted, runId/resources 0, task report and fixture sentinel bytes preserved | 0 (check; nested runner expected exit 1) |
| Edge/IPAM and owned launch argv | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r8-edge-reservation-check.ps1`; both aligned candidates, invalid boundaries, explicit gateway/range source, no pre-start operational claim, post-start exact inspect, occupied no-leak, Gateway peer, `--pull=never` create/run and missing-image fail-closed | 0 |
| Auth pacing/session source | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r8-auth-pacing-check.ps1`; fake clock 12,000 ms wait after 1 s, zero wait after 13 s, actual callsite preserves 0/12000 records, session GET marker | 0 |
| Reparse ancestor | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r8-reparse-ancestor-check.ps1`; temporary junction ancestor rejected and direct fixture accepted | 0 |
| Source contract | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r6-source-contract-check.ps1`; readiness ordering, preflight diagnostics, one-buffer manifest, PWA digest, response-aware fixed probe wiring present | 0 |
| Cleanup failure injection | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r3-cleanup-failure-injection.ps1`; injected owned removal exit 23 was caught, cleanup status FAIL and error recorded | 0 |
| Subnet parser | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r3-subnet-parser-check.ps1`; existing overlap and malformed output rejected, safe candidate selected | 0 |
| R10 proof source | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r10-network-proof-source-check.ps1`; shared production helper/launcher, pre-create collision check, missing-image/name guards, wrapper and resource bounds observed | 0 |
| Diff whitespace | `git diff --check` | 0 |

## Runtime evidence and limits

Docker network proof mutation is PASS under the isolated H32 lease. Gradle, build producer, ValidateOnly invocation, browser probe, server/port startup, and full I1/I2 runtime are **NOT_RUN**. The proof removed and verified absent all resources it created; unrelated existing resources were not touched. The producer did not create a build manifest in this lane; synthetic fixtures are explicitly not build provenance. Product runtime PASS is unclaimed. Review-before-runtime remains mandatory: a fresh independent Sol recheck of the complete stable R9/R10 diff must PASS before root leases ValidateOnly or exact-E runtime.

## Scoped diff and release

R9/R10 changes are limited to `runner.ps1`, `probe.mjs`, this evidence file, the existing scoped pure checks, `r10-docker-network-proof.ps1`, `r10-network-proof-source-check.ps1`, and the redacted proof report `r10-docker-network-proof.json`, all below `requests-runtime/**`. Accepted R3/R6/R7 source and pure checks, read-only browser/runtime seeds, and foreign worktrees remain unchanged. The preparation and H32 evidence commit hashes, exact diff stats, file SHA-256 values, and clean scoped status are returned to root with this evidence. Fresh Sol review is required before any product runtime lease.

Source file SHA-256 values at the prepared R9/R10 source release (PowerShell `Get-FileHash -Algorithm SHA256`; evidence hash is intentionally omitted to avoid a self-referential value):

| File | SHA-256 |
| --- | --- |
| `runner.ps1` | `3D44671B5FF65AEDCDA452A37780702137112BDA949E5D3BAA7CB912739ED499` |
| `probe.mjs` | `3410DB6173065507D8A0E93B3C79DC763A7594F498DECA215009A9CFFE95584E` |
| `r7-preflight-boundary-check.ps1` | `4C0E3698825E0C345ED559E1CD3101B1412C4AC29A15A1C5C1A37851832DD01D` |
| `r8-probe-callsite-check.ps1` | `39A72AF2665D528BA1037CF9257C36132F53ED0837757815BB8FAA40E6601C67` |
| `r8-edge-reservation-check.ps1` | `A6E6E33541D2EE35BA6C3D4B74FF7480C2FC332500D8557C50DEC5F6651B0577` |
| `r8-mongo-projection-check.ps1` | `D0F9CEE7567572359B5861A5AAAFFA14927D8CF45DBC0476C41655B641BEAD4A` |
| `r8-auth-pacing-check.ps1` | `2D27FC13A0F1B356A968652FFDDF977E4FB8E8419C5E0A1442B3F764EB283327` |
| `r8-reparse-ancestor-check.ps1` | `AC59DF338820F2AF26C204E5F57BEB665D082F9649F6A14022D868B88F6D8445` |
| `r10-docker-network-proof.ps1` | `0D5C828D706424CC063D2D7B6890B98FA4DC9DF15B1C4C884165FBB2B55407CE` |
| `r10-network-proof-source-check.ps1` | `2268854FC9BE38F7405F0B373B18FBC0F6091C4BEADE7F224FD0506C28A46E74` |
| `r10-docker-network-proof.json` | `0D07C5C7B346F3C0E0E68173C184A811CF283A004AAD9C7F7916CBC298F2A9BA` |

## R11 bundled correction (33-finding ledger)

R11 started from the clean H32 source checkpoint `73fd5f27ceb429ad0692b073189f8a527c550830` and changed only the assigned `requests-runtime/**` scope. The original E sources, accepted seeds, product worktrees and historical H32 report remain read-only. No Docker, Gradle, build producer, ValidateOnly, server, browser or product runtime was run in this lane.

The four fresh findings are closed as follows:

- F1 HIGH: `Start-OwnedContainer` now emits `docker create --pull=never`, registers the returned or strictly recovered ID immediately, and then emits `docker start`. Ambiguous create results can be recovered only through an exact container name plus both `rct.runtime-owner=student-requests-gate` and current `rct.runtime-run` labels. A foreign-label match is rejected without adoption or deletion. The updated `r8-edge-reservation-check.ps1` injects start failure, owned create recovery and foreign mismatch; all paths pass and the start failure remains in the cleanup ledger.
- F2 HIGH: source validation records the exact expected revision, clean tracked status, edge source bytes/hashes and revision blob hashes. `New-RuntimeConfig` consumes the validated read-once edge text cache. `Assert-UnionSourceStability` runs before resource creation, before config/edge reservation and before infrastructure/backend/edge starts, rejecting dirty state, changed bytes or changed revision blobs. `r11-source-stability-check.ps1` uses temporary fixtures to reject both a clean-status between-phase byte swap and dirty state.
- F3 MEDIUM: the Node probe now requires the API `summary.status=PENDING` mapping for the domain `SUBMITTED` ticket, accepts only a null/absent decision, requires every I1 attachment to be `ACTIVE`, accepts only a null/absent `expiredAt`, and requires `uploadedAt < expiresAt`. Mongo document and outbox projections carry `expired_at`; canonical comparison rejects non-null lifecycle expiry and equal/reversed upload/expiry timestamps independently in both stored documents and outbox payloads. The self-test and Mongo projection fixture exercise response, document and outbox mutations.
- F4 LOW: a future network proof initializes top-level `runId`, `environment.runtimeMode=network-proof` and a runner SHA source anchor before the first Docker command. Its bounded plan now includes an actual invalid-entrypoint `create → register → start` failure path, with expected/worst-case owned container bounds of `3/4`; all proof commands use `--pull=never`. The wrapper defaults to the new `r11-docker-network-proof.json` path; historical `r10-docker-network-proof.json` remains byte-identical at SHA-256 `0D07C5C7B346F3C0E0E68173C184A811CF283A004AAD9C7F7916CBC298F2A9BA`. The new proof report was not generated in this source-only lane.

## R11 pure checks and release

Environment: PowerShell `7.6.5`, Node `v24.14.0`, worktree branch `codex/v2-requests-harness`, source base `73fd5f27ceb429ad0692b073189f8a527c550830`. Every command below exited `0`; checks use only temporary fixtures/stubs where noted.

| Check | Result |
| --- | --- |
| `node --check .agent/student-role-orchestrator/requests-runtime/probe.mjs` | PASS, exit 0 |
| `node .agent/student-role-orchestrator/requests-runtime/probe.mjs --self-test` | PASS, exit 0; valid PNG, API PENDING/ACTIVE lifecycle, null/absent decision and expiry, invalid decision/timestamp/order mutations, fixed/chunked 413 EPIPE and no-response transport gates |
| `pwsh -NoProfile -NonInteractive -File .../r8-edge-reservation-check.ps1` | PASS, exit 0; actual launcher create/register/start, invalid-entrypoint start failure with `--entrypoint`, injected create failures, owned recovery and foreign mismatch |
| `pwsh -NoProfile -NonInteractive -File .../r11-source-stability-check.ps1` | PASS, exit 0; exact revision/clean state, tracked blob/hash pin, clean-status byte swap and dirty fixture rejection |
| `pwsh -NoProfile -NonInteractive -File .../r8-mongo-projection-check.ps1` | PASS, exit 0; emitted Mongo JS, `expired_at` projection, independent non-null expiry and equal-time lifecycle rejection in documents/outbox, plus descriptor corruption rejection |
| `pwsh -NoProfile -NonInteractive -File .../r8-probe-callsite-check.ps1` | PASS, exit 0; Node syntax/self-test, header and lifecycle callsite gates |
| `pwsh -NoProfile -NonInteractive -File .../r6-source-contract-check.ps1` | PASS, exit 0; preflight diagnostics, trusted provenance and cached edge source/guard assertions |
| `pwsh -NoProfile -NonInteractive -File .../r10-network-proof-source-check.ps1` | PASS, exit 0; shared proof helper, metadata-before-command, invalid-executable start-failure path, 3/4 bounds and new evidence path assertions |
| Existing pure suite (`r3-cleanup`, `r3-subnet`, `r6-build`, `r7-mongo`, `r7-boundary`, `r8-auth`, `r8-reparse`) | PASS, exit 0 for each; no Docker mutation |
| `git diff --check` and scoped PowerShell AST parse | PASS, exit 0 |

Source SHA-256 values at this R11 source/evidence state (the evidence file is omitted to avoid a self-reference):

| File | SHA-256 |
| --- | --- |
| `runner.ps1` | `6FD840FC564AAE8AA3AA537212E7F8C212DA93E4067DF81A318D5536A6E8D334` |
| `probe.mjs` | `BA3D4A459F076A00E8CA8970D871F07DF5F51B93ACEFD7AB19E06AEFC320066E` |
| `r8-edge-reservation-check.ps1` | `614C28C33D8B99A733D49B94C061E3C8A76C4580DC3D3D583365DB6F00E16BD4` |
| `r8-mongo-projection-check.ps1` | `95053E5E8703B7190D5AD8B277718C3C46F73E9C187B1F951061744794EE12D0` |
| `r6-source-contract-check.ps1` | `8F38488EFC21B293BD3020995B92F38A484B98285DA318A68B700E31A29E5091` |
| `r10-docker-network-proof.ps1` | `0918C18D7C6915CC1C8F06EC94E4B86D7EBACC390F7E29F4DFF8255E49D2E2C8` |
| `r10-network-proof-source-check.ps1` | `3D369BA421947D2FC4E7DDDC786552AE7D4257643271E509F4F41D3A9D62B988` |
| `r11-source-stability-check.ps1` | `9600BF2776B8CD4D4BABE8BC706AE8AC6CABD319A9EFC3F67765A04B698124B9` |

Docker proof/runtime remains **NOT_RUN** in R11. Release is ready for root's fresh independent full Sol review; only after that review and a separate root lease may the new proof path and later original I1/I2 runtime be executed.

## R12 strict API/Mongo lifecycle correction — current source evidence (2026-09-15 evening)

### Scope and contract

This replacement-author R3 pass is S3 source/check/proof-proposal work in the sole writer worktree `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness`, branch `codex/v2-requests-harness`, expected base/revision `73fd5f27ceb429ad0692b073189f8a527c550830`. The orchestration rules pin is `A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`. Changes remain confined to `.agent/student-role-orchestrator/requests-runtime/**`; the previously released author had no live ownership, and no foreign worktree or product source was changed.

The current contract closes the four R3 findings in the 33-finding bundle. Container lifecycle must create and register the real ID before start, recover only an exact name with both current owner and run labels, reject foreign matches, and preserve actual start failures. Source provenance must verify the expected revision, clean tracked state, immutable tracked blob/edge bytes and hashes before resource or edge startup, then use the validated read-once cache. I1 must validate the actual BFF DTO (`summary.status=PENDING`, exact camelCase attachment fields, `decision` null/absent, `expiredAt` null/absent, and strict `uploadedAt < expiresAt`) and the persisted Mongo ticket state (`SUBMITTED`) while comparing Mongo documents and outbox descriptors through their separate snake_case projection shape. The future proof must anchor `runId`, `runtimeMode=network-proof`, and its runner SHA before the first Docker command, include invalid-entrypoint create/register/start failure, use `--pull=never`, and pin bounded resources. These checks are source/pure evidence only; they do not claim changed-launcher runtime success.

### Critical source references and correction

The E originals remain read-only: `C:/Users/maksd/.codex/worktrees/6a61/rutcampustrack/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:659` emits `ExcuseTicketStatus.SUBMITTED` and `:679` emits `AttachmentState.ACTIVE`. The BFF contract source is `C:/Users/maksd/.codex/worktrees/6a61/rutcampustrack/services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java`; its public fields are camelCase `contentType`, `sizeBytes`, `uploadedAt`, `expiresAt`, and `expiredAt`, with `summary.status` represented as `PENDING`.

The prior generic DTO fixture accepted aliases and did not independently prove API, Mongo, and outbox mutations; the first final audit also showed that the Mongo snapshot counted the request without checking its persisted state. `probe.mjs` now reads only exact public API names, rejects extra snake_case attachment aliases, requires the exact API enum value `ACTIVE`, enforces the actual summary/lesson fields and lifecycle values, rejects `selectedLesson`, and uses a strict UTC Instant wire form. `runner.ps1` now has explicit `Api` and `Mongo` descriptor formats, so `$Probe.i1.files` is compared as the API shape while stored document and outbox payloads are compared as snake_case; API format also rejects extra snake_case aliases and non-exact state casing. Its request snapshot projects `status`, and I1/I2 assertions require the real persisted `SUBMITTED` state behind API `PENDING`. `r8-mongo-projection-check.ps1` constructs both real shapes, rejects a wrong persisted ticket state, and injects missing/extra-field aliases, non-null expiry, equal-time expiry, content-type, and digest mutations across the API, document, and outbox descriptor paths. `r11-source-stability-check.ps1` adds revision and clean-status byte-swap negatives.

### Pure checks and exit codes

Environment for this evidence: PowerShell `7.6.5`, Node `v24.14.0`, branch `codex/v2-requests-harness`, source revision `73fd5f27ceb429ad0692b073189f8a527c550830`. The complete pure ledger below was rerun after the strict correction; every command exited `0` and no Docker/Gradle/build/server/port operation was issued.

| Check | Evidence | Exit |
| --- | --- | ---: |
| Node syntax | `node --check .agent/student-role-orchestrator/requests-runtime/probe.mjs` | 0 |
| Probe self-test | `node .agent/student-role-orchestrator/requests-runtime/probe.mjs --self-test`; exact API camelCase/`ACTIVE` fixture, PENDING lifecycle, null/absent decision and expiredAt, content/size/timestamp/expiry aliases and lifecycle/order mutations, fixed/chunked 413 EPIPE and no-response failure | 0 |
| Probe callsite | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r8-probe-callsite-check.ps1`; required API field markers and nullable lifecycle gates | 0 |
| Mongo projection and DB state | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r8-mongo-projection-check.ps1`; persisted ticket `SUBMITTED` projection plus separate API camelCase and Mongo/outbox snake_case fixtures with alias, expiry, content, and digest rejection | 0 |
| Source stability | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r11-source-stability-check.ps1`; exact revision/clean state, tracked blob/edge hashes, dirty fixture and between-phase byte-swap rejection | 0 |
| Source contract | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r6-source-contract-check.ps1`; preflight ordering, cached validated source, exact descriptor formats, response lifecycle gates | 0 |
| Edge/lifecycle pure check | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r8-edge-reservation-check.ps1`; create/register/start ordering, injected start failure, owned recovery and foreign-label rejection | 0 |
| Network proof source | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r10-network-proof-source-check.ps1`; pre-command metadata, invalid-entrypoint path, 3/4 resource bounds, `--pull=never`, new report path | 0 |
| Existing pure ledger | `r3-cleanup-failure-injection.ps1`, `r3-subnet-parser-check.ps1`, `r6-build-manifest-check.ps1`, `r7-mongo-query-check.ps1`, `r7-preflight-boundary-check.ps1`, `r8-auth-pacing-check.ps1`, and `r8-reparse-ancestor-check.ps1` | 0 each |
| Scoped PowerShell parse | `pwsh -NoProfile -NonInteractive -Command '<ParseFile all *.ps1 under requests-runtime>'` | 0 |
| Whitespace | `git diff --check` | 0 |

### Current source hashes

These are SHA-256 values from `Get-FileHash -Algorithm SHA256` after the correction. `R3-EVIDENCE.md` is omitted to avoid a self-referential value.

| File | SHA-256 |
| --- | --- |
| `probe.mjs` | `B346B3979AE25AEDD7B1EF43A6F9DA8A162316EA85B4C834174BED3D50DF75B9` |
| `runner.ps1` | `A1585F026768E06EED7E05F480969ABCCDB690D90579859F5E7EFCE5F5340E94` |
| `r8-mongo-projection-check.ps1` | `D557881DF3587F7BBA3EED4814075E9BC2ECB32DFD4C4957B72912645A9A437B` |
| `r8-probe-callsite-check.ps1` | `8CDC6421F202896737513585493A9BD294CF846E252D82B980C1EE32F1875CAC` |
| `r6-source-contract-check.ps1` | `BCCC1026BBCBD8A664BF8E9FCF9F2EF02D42E8027628B9E59B161793ACBEE8CB` |
| `r11-source-stability-check.ps1` | `A743B839CD776C1141B813991234D748F6576BDA0D739AFAC3B9CCD9933D9C6B` |
| `r10-docker-network-proof.ps1` | `0918C18D7C6915CC1C8F06EC94E4B86D7EBACC390F7E29F4DFF8255E49D2E2C8` |
| `r10-network-proof-source-check.ps1` | `E6F0F92C641DBB5FB56D1190F10B8501498631B23C228F2F28B6CDE5355717B5` |
| `r8-edge-reservation-check.ps1` | `63BD96B2C0DA42A1E7680ECEE679E1AD5A48D08E3A41271760C11E62B109E5D3` |

### Runtime, diff, and limitations

Changed-launcher Docker proof, Gradle/build producer, ValidateOnly, browser, server/port startup, and actual I1/I2 API/Mongo/outbox runtime remain **NOT_RUN** in this R3 lane. The historical H32 report `r10-docker-network-proof.json` remains byte-identical at SHA-256 `0D07C5C7B346F3C0E0E68173C184A811CF283A004AAD9C7F7916CBC298F2A9BA`; that isolated historical PASS does not validate the changed launcher. The new R11 proof report is intentionally not generated here. Actual Docker mutation is reserved for root's future lease after fresh independent Sol review.

The current scoped working set is the evidence file plus `probe.mjs`, `runner.ps1`, `r10-docker-network-proof.ps1`, `r10-network-proof-source-check.ps1`, `r6-source-contract-check.ps1`, `r8-edge-reservation-check.ps1`, `r8-mongo-projection-check.ps1`, `r8-probe-callsite-check.ps1`, and the new `r11-source-stability-check.ps1`, all below `requests-runtime/**`. No commit, push, merge, deploy, or foreign cleanup was performed. Fresh independent Sol review must re-open the E/BFF originals, inspect the complete stable diff, and decide whether this source/pure evidence is sufficient before any runtime lease.

## R13 bounded artifact handoff correction — current source evidence (2026-09-15 evening)

### Scope, criteria, and decision

This is the fresh R3 S3 correction in the sole writer worktree
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness`, branch
`codex/v2-requests-harness`, starting at HEAD
`73fd5f27ceb429ad0692b073189f8a527c550830`. The applicable orchestration rules are
`RULES.md` SHA-256
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
Only `.agent/student-role-orchestrator/requests-runtime/**` was touched; existing WIP in
that scope was retained. Product sources, E originals, frozen contract, accepted seeds,
and foreign worktrees remain read-only. No child, Terra, Docker, Gradle, build producer,
ValidateOnly, server, browser, install, secret, push, deploy, or merge operation was used.
Academic diagnostic port `127.0.0.1:18520` was left unchanged.

The three confirmed full-review defects are closed together:

- **F1 HIGH — artifact handoff TOCTOU.** `New-VerifiedArtifactSnapshot` reads each trusted
  JAR and every PWA file into a manifest-checked buffer while holding a read share,
  rejects size/hash/reparse changes at the read boundary, writes a task-owned
  `runDir\artifacts` copy, verifies the copy, and returns only those paths. Backend JAR
  mounts use `artifactSnapshot.Jars`; Nginx uses `artifactSnapshot.PwaDist`; cleanup
  checks the exact owned run/artifact path and verifies absence. A real pure check swaps
  source JAR/PWA files after freeze, proves production mount helpers still receive the
  owned copies, and proves a fresh snapshot rejects both changed inputs.
- **F2 MEDIUM — API enum and pure assertion flow.** `canonicalAttachmentDescriptor` keeps
  the exact API `state: 'ACTIVE'` value instead of lowercasing it. The self-test exposes
  `detailEvidence.attachments` as its serialized API fixture, and
  `r8-mongo-projection-check.ps1` feeds that actual Node output into production
  `Assert-I1MongoDelta` before separately constructing Mongo/outbox snake_case
  projections. A lowercase API state is rejected by the Node corruption matrix.
- **F3 MEDIUM — exact-root edge route.** `Convert-EdgeToRuntimeConfig` replaces the E
  exact-root `/` redirect with a static PWA index route and replaces the panel prefix
  route with the copied PWA tree fallback. The production helper regression check
  proves `/` serves `/index.html`, the prefix serves `$uri`/`$uri/` or `/index.html`,
  the panel proxy is removed, and the source E text remains unchanged.

The cached E edge bytes are also explicitly bound to the expected revision blobs:
`Resolve-EdgeSources` sends the captured bytes to `git hash-object --path=<tracked path>
--stdin`, compares both returned IDs with `HEAD:path`, and retains a raw byte SHA for
the between-phase source guard. The `--path` clean filter makes the comparison correct
for the accepted Windows CRLF checkout while still hashing the exact captured buffer;
the pure check changes one cached byte and observes a different blob ID.

### Pure checks and exit codes

Environment: PowerShell `7.6.5`, Node `v24.14.0`, branch
`codex/v2-requests-harness`, HEAD `73fd5f27ceb429ad0692b073189f8a527c550830`.
All checks below were run after the final source edits and exited `0`.

| Check | Evidence | Exit |
| --- | --- | ---: |
| `r3-cleanup-failure-injection.ps1` | Actual cleanup function with injected owned `rm` exit `23`; error caught, cleanup status `FAIL`, owned failure recorded | 0 |
| `r3-subnet-parser-check.ps1` | Candidate collision/malformed CIDR fixtures fail closed | 0 |
| `r6-build-manifest-check.ps1` | Trusted manifest/PWA fixture, extra JAR/content swap/stale revision/wrong pin/digest negatives | 0 |
| `r7-mongo-query-check.ps1` | Actual generated Mongo JavaScript parses and observes ObjectId ticket/string attachment/regex linkage | 0 |
| `r7-preflight-boundary-check.ps1` | Copied runner invalid-repo exit `1`; redacted report and sentinel preservation | 0 |
| `r8-auth-pacing-check.ps1` | Fake clock and distinct I1 `0`/I2 `12000` records; session GET gate | 0 |
| `r8-edge-reservation-check.ps1` | Actual launcher create/register/start, start-failure/recovery/foreign ownership, IPAM and `--pull=never` gates | 0 |
| `r8-mongo-projection-check.ps1` | Generated Mongo query plus actual Node serialized API fixture through production PowerShell I1 assertion; API/Mongo/outbox mutation negatives | 0 |
| `r8-probe-callsite-check.ps1` | Node syntax/self-test, exact API `ACTIVE`, response-aware 413 and CurrentSessionResponse callsites | 0 |
| `r8-reparse-ancestor-check.ps1` | Temporary junction ancestor rejected; direct path accepted | 0 |
| `r6-source-contract-check.ps1` | Provenance, cached edge source, tracked-path blob binding, lifecycle and snapshot callsite guards | 0 |
| `r10-network-proof-source-check.ps1` | Future proof invokes shared production network/launcher helpers and bounded owned cleanup | 0 |
| `r11-source-stability-check.ps1` | Revision/blob/clean-status and between-phase edge byte swaps rejected | 0 |
| `r12-artifact-snapshot-check.ps1` | Actual snapshot, six JAR/full PWA copy, post-freeze swaps, fresh-input negatives, production Java/edge mounts, edge blob binding, exact-root transform, owned cleanup | 0 |
| `node --check probe.mjs` | Node syntax | 0 |
| `node probe.mjs --self-test` | Deterministic 10 MiB PDF/PNG, API camelCase `ACTIVE`, PENDING/SUBMITTED lifecycle and transport negatives | 0 |
| Scoped PowerShell AST | `ParseFile` for all 16 scoped `*.ps1` files | 0 |
| Historical H32 hash | `r10-docker-network-proof.json` remains SHA-256 `0D07C5C7B346F3C0E0E68173C184A811CF283A004AAD9C7F7916CBC298F2A9BA` | 0 |
| Scoped whitespace | `git diff --check -- .agent/student-role-orchestrator/requests-runtime` | 0 |

The only failed command in preparation was an initial run of
`r3-cleanup-failure-injection.ps1` that exposed the new artifact cleanup fields were
missing from its synthetic report. The check was updated to initialize the production
shape, rerun, and then passed with the injected exit `23`; this was a check-fixture
compatibility correction, not a production failure.

### Source inventory and hashes

`Get-FileHash -Algorithm SHA256` after the final source edits gives the following complete
changed/new code and proof inventory. `R3-EVIDENCE.md` is intentionally omitted from the
hash table to avoid a self-referential evidence hash. The historical H32 JSON is included
and was independently checked unchanged.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `runner.ps1` | 156942 | `063A503957BBF7DE29F6B218D0A66C9491206CC6148B39DCAE0905151591C667` |
| `probe.mjs` | 47147 | `1388EA8F0AF3FEDEB4842C83673BDF9B9A8B208C97C9DE0CFEC5792690DC9109` |
| `r3-cleanup-failure-injection.ps1` | 2736 | `BAF03194D0EEEA59013E05A54832AC89946B071DB9F445468D95F3E7D7FA7E78` |
| `r6-source-contract-check.ps1` | 10067 | `3C36387AD3CDC33E3A26F16CD928CF2E295252E4FF43F3B694C600B375226E94` |
| `r8-edge-reservation-check.ps1` | 13414 | `63BD96B2C0DA42A1E7680ECEE679E1AD5A48D08E3A41271760C11E62B109E5D3` |
| `r8-mongo-projection-check.ps1` | 19968 | `FEAF0244D24DAAB89F177BD2175ADF3C9E7F66EC179796713FDDA87888002C94` |
| `r8-probe-callsite-check.ps1` | 3621 | `AEC6FAB3128C6533C43E403EFE58780C67A502CCA7F3E48C4017F607B0F71CC0` |
| `r10-docker-network-proof.ps1` | 947 | `0918C18D7C6915CC1C8F06EC94E4B86D7EBACC390F7E29F4DFF8255E49D2E2C8` |
| `r10-network-proof-source-check.ps1` | 4828 | `E6F0F92C641DBB5FB56D1190F10B8501498631B23C228F2F28B6CDE5355717B5` |
| `r11-source-stability-check.ps1` | 5314 | `A743B839CD776C1141B813991234D748F6576BDA0D739AFAC3B9CCD9933D9C6B` |
| `r12-artifact-snapshot-check.ps1` | 15563 | `6F4992FA888597B4F5030E63CB97C8CB2D2EC9B4410E68B9D5969FF7F232EC66` |
| `r10-docker-network-proof.json` (historical) | 17960 | `0D07C5C7B346F3C0E0E68173C184A811CF283A004AAD9C7F7916CBC298F2A9BA` |

`git status --short` showed only these request-runtime scoped paths (the evidence file,
the listed modified checks/source, and the two listed new checks); no path outside the
assigned directory was changed. The pre-existing 10 WIP files were preserved and no
commit/push/merge was performed.

### Runtime evidence, proof proposal, and limitations

This was source/pure work only. Changed-launcher Docker proof, build producer/manifest
generation, `ValidateOnly`, server/port startup, browser, and actual Requests I1/I2
API/Mongo/outbox runtime are **NOT_RUN**. The historical H32 report is not reused as
proof of the changed launcher. The synthetic JAR/PWA fixture proves the production
snapshot/mount boundary and fail-closed swaps, but it is not build provenance and does
not prove a real Java or Nginx process started.

After fresh independent Sol full review accepts the stable scoped diff, root may use this
exact bounded proof command under a separate lease (it was not executed here):

```powershell
pwsh -NoProfile -NonInteractive -File C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-requests-harness\.agent\student-role-orchestrator\requests-runtime\r10-docker-network-proof.ps1 -Subnet 172.30.185.0/24 -EvidencePath C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\v2-requests-harness\.agent\student-role-orchestrator\requests-runtime\r11-docker-network-proof.json
```

The bounded proof permits one bridge network, 3 expected/4 worst-case owned containers,
no host ports or volumes, an existing local pinned image, and `--pull=never`; it should verify actual IPAM, the post-start static edge
address, dynamic allocation, invalid-entrypoint create/register/start failure, local
image `--pull=never`, exact ownership labels, cleanup and verified absence. Only then
should the root-owned producer provide a clean expected-revision manifest and decide any
later ValidateOnly/full runtime lease. No runtime PASS is proposed by this evidence.

### Actual core handoff snippet

The mount boundary is visible in the production runner at
`runner.ps1:492-516` (snapshot copy/manifest verification),
`runner.ps1:1488` (PWA snapshot path returned to edge config), and
`runner.ps1:1542` (backend JAR snapshot consumed). The edge byte binding is at
`runner.ps1:924-927`; API enum preservation is at `probe.mjs:230-268` and serialized
Node evidence at `probe.mjs:714`.

## R13 MEDIUM ambiguous network-create recovery correction — 2026-09-15

### Scope and frozen criteria

This bounded correction owns only `requests-runtime/runner.ps1`, the meaningful pure
production-helper injection `r13-network-create-recovery-check.ps1`, and this scoped
evidence. Existing WIP in the worktree and all product/E/original files remain
preserved. The defect was the network analogue of the already-correct container
create/register path: a daemon-side network could exist after a client exception,
non-zero result, empty output, or malformed ID, while `$script:ownedNetwork` was
still unset and `Remove-OwnedResources` had no safe identity to inspect.

Required behavior is now explicit: recover only `script:networkName` whose inspected
`.Name` is exact and whose labels are both
`rct.runtime-owner=student-requests-gate` and `rct.runtime-run=$script:runId`;
validate the inspected ID, register it before later runtime operations, preserve the
original create failure in recovery evidence, and fail closed for foreign or absent
networks. Cleanup must inspect labels again, remove only the registered owned ID, and
verify absence.

### Regression and correction evidence

The exact pure check was first run against immutable `HEAD:runner.ps1` before the
correction. It exited `1`: the exception/non-zero/empty/malformed create cases
exposed the missing recovery/registration, while foreign and absent cases remained
unadopted; the old success path also lacked the new recovery metadata asserted by
the final check. Raw stdout and the empty stderr are retained at
`r13-network-recovery-regression.stdout.log` and
`r13-network-recovery-regression.stderr.log`.

The same production helper extraction and injection then ran against the corrected
working tree and exited `0`. It covered normal success, client exception, non-zero
exit, empty ID, malformed ID, foreign owner label, foreign run label, foreign name,
absent network, original-failure visibility, exact ID registration, and verified
cleanup. Raw output is retained at `r13-network-recovery-pass.stdout.log` and
`r13-network-recovery-pass.stderr.log`.

### Checks, commands, exits, and hashes

Environment: PowerShell `7.6.5`, branch `codex/v2-requests-harness`, current HEAD
`73fd5f27ceb429ad0692b073189f8a527c550830`; no Docker or Gradle lease was used.

| Check | Command | Exit | Evidence |
| --- | --- | ---: | --- |
| Baseline regression | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r13-network-create-recovery-check.ps1 -UseHeadRunner` | 1 | `r13-network-recovery-regression.stdout.log`; SHA-256 `BD9AB322639044076BE2FC4BBD32D89E11AC6535BD3CF0B80FC34B7375075EFF` |
| Corrected production helper injection | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r13-network-create-recovery-check.ps1` | 0 | `r13-network-recovery-pass.stdout.log`; SHA-256 `E48F710E00B80CB6F52E7D51097B92082CE27A4FB90EA8C4A83956B8F02AEE68` |
| Existing cleanup failure injection | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r3-cleanup-failure-injection.ps1` | 0 | actual owned `rm` exit 23 remains FAIL/caught and recorded |
| Existing subnet parser | `pwsh -NoProfile -NonInteractive -File .agent/student-role-orchestrator/requests-runtime/r3-subnet-parser-check.ps1` | 0 | collision and malformed CIDR fixtures |
| Existing source/launcher/provenance checks | `r6-source-contract-check.ps1`, `r8-edge-reservation-check.ps1`, `r8-mongo-projection-check.ps1`, `r8-probe-callsite-check.ps1`, `r10-network-proof-source-check.ps1`, `r11-source-stability-check.ps1`, `r12-artifact-snapshot-check.ps1` | 0 each | all actual stdout PASS |
| Scoped PowerShell AST | `ParseFile` over all 17 scoped `*.ps1` files | 0 | `{"status":"PASS","files":17,"errors":[]}` |
| Scoped whitespace | `git diff --check -- .agent/student-role-orchestrator/requests-runtime/runner.ps1 .agent/student-role-orchestrator/requests-runtime/r13-network-create-recovery-check.ps1` | 0 | no check errors; Git emitted only existing LF/CRLF normalization warning |

Current source hashes are `runner.ps1` SHA-256
`67F6D0E471FCE5DC7835023D32BB5BD3D26EEB00426AD9AD7A7C2C62409FCA3D` and
`r13-network-create-recovery-check.ps1` SHA-256
`46C8E13A596F691BF00188AACE4CDAA92869FE49E9830B57A3CFDC706C72050D`.
The pass stderr log is empty with SHA-256
`E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`; the
baseline stderr log is the same empty file hash.

### Diff and runtime limits

The code delta is limited to the new `Resolve-AmbiguousOwnedNetwork` helper and the
ambiguous create/recovery branch in `New-RequestsOwnedNetwork` at current
`runner.ps1:1154-1241`, plus the scoped pure check and its raw logs. The helper's
recovery inspect is the only adoption path; foreign name/owner/run cases never enter
the cleanup ledger. No Docker mutation, Docker network proof, Gradle/build producer,
ValidateOnly, service startup, browser, or Requests I1/I2 runtime was run here, so
runtime evidence is **N/A** for this pure repair. The historical H32 JSON remains
untouched and is not evidence for this changed launcher.

The source is frozen for fresh independent Sol review at the hashes above; no commit,
push, merge, deploy, product change, or foreign resource cleanup was performed.

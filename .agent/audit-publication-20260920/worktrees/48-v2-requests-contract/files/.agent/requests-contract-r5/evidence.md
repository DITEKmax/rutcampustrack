# R5 evidence

Recorded: 2026-09-13 (Europe/Moscow)  
Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-contract`  
Branch: `codex/v2-requests-contract`  
Base: `b8220ac92125a8afa37598b270aa4fab7aa1f470`  
Rules SHA-256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`

## Recorded findings and correction

Finding 1 (HIGH) is reproduced by the source shape: both
`docker-compose.prod.yml` and `docker-compose.e2e.yml` interpolate required
`GATEWAY_NGINX_IPV4` and `GATEWAY_PRIVATE_SUBNET`, while the versioned
production/E2E input templates and validator did not provide the pair. The
correction adds non-secret `172.30.0.0/24` plus `172.30.0.10`, explicit CI and
documented `--env-file` wiring, and strict validator checks that keep the edge
address inside the exact RFC1918 subnet.

Finding 4 (MEDIUM) is reproduced by the Java/OpenAPI source shape:
`@RequestPart("request")` is required by Spring at runtime, but the generated
operation at `/api/v1/student/requests/excuse` lacks top-level
`requestBody.required`, and `mobile-bff.ts` therefore declares
`requestBody?:`. The correction adds an operation-level Swagger
`RequestBody(required = true)` annotation while preserving Spring's inferred
required `@RequestPart("request")` and optional `files` part, then adds an
OpenAPI snapshot assertion. At this pre-H11 checkpoint, snapshot JSON and generated TypeScript were awaiting canonical generation; H11 later refreshed both through the canonical path with exit 0, as recorded below.

## Source evidence

- `docker-compose.prod.yml` uses `${GATEWAY_NGINX_IPV4:?…}` for both gateway
  trust and the Nginx static address, and `${GATEWAY_PRIVATE_SUBNET:?…}` for
  `private_net`.
- `docker-compose.e2e.yml` uses the same two required interpolations.
- `.env.prod.example` and `tests/e2e/.env.ci` now define the same explicit
  non-secret pair.
- `scripts/validate-env-prod.sh` now checks required presence, canonical IPv4,
  aligned CIDR, RFC1918 containment and usable host membership.
- `.github/workflows/ci.yml` passes the tracked `.env` file to every E2E Compose
  operation and performs `config --quiet` before `up`.
- `StudentApi.java` explicitly declares the operation-level OpenAPI request
  body required while preserving the inferred `request` part and optional
  `files` part.
- `OpenApiSnapshotIT` asserts the runtime operation's top-level required flag.

## Verification status

The granted H3 lane attempted one Windows Gradle command for canonical OpenAPI
snapshot update. The wrapper exited 1 with `Task '.snapshot.update=true' not
found`, because PowerShell/Gradle parsed the unquoted dotted `-P` property as a
task. H3 was released immediately. A fresh H4 grant allowed the same command
with a native PowerShell argument array, which fixed property parsing but then
exited 1 during unrelated `attendance-api-contract` compilation: the source
classpath lacks `ru.rutcampustrack.shared.web.api.exception.ErrorResponse`
referenced by five attendance contract APIs (49 compiler errors). H4 was
released at that source/environment boundary. H10 then used the same native
argument array in the narrow escalated context; compilation passed and the
integrationTest task ran, but Gradle exited 1 moving `problems-report.html`
because the destination already existed in the worktree. H10 was released at
that environment boundary; no blind retry, Docker command, frontend generation
or focused recheck followed.

Before H3, no Gradle, Docker, build, dependency, test, runtime, deployment or
provisioning command was run in this leaf. The granted light lane then passed
Git for Windows Bash syntax validation (exit 0), found `shellcheck` absent
(exit 1, no install attempted), and exercised only synthetic validator files.
The documented private pair passed (script exit 0). Malformed/unaligned/public
CIDR, malformed IP, out-of-subnet, network and broadcast gateway fixtures each
returned script exit 3. No production env or secrets were read.
The source diff whitespace check (`git diff --check`) passed with exit code 0.
The accepted historical H11 lane then completed the queued contract/config sequence in the narrow approved
context:

- Gradle help with `--no-problems-report`: exit 0.
- Canonical snapshot update with native args array, one
  `-Popenapi.snapshot.update=true` element, `--no-problems-report`,
  `--no-daemon`, `--no-parallel`, `--max-workers=1`: exit 0.
- Normal `OpenApiSnapshotIT` assertion with the same Gradle safety flags: exit
  0.
- `npm ci --offline --ignore-scripts --no-audit --no-fund`: exit 0, 222 cached
  packages, no lockfile/version changes.
- `npm run generate:types`: exit 0, spec hash
  `d4f97e0476cd681a9460247d9f904c7901241269b73221a097ec6be45132cda0`.
- `npm run generate:types:check`: exit 0, types match `JS-STUDENT-01-r1`.
- `docker compose --env-file tests/e2e/.env.ci -f docker-compose.e2e.yml
  config --quiet`: exit 0; config-only, no service startup or deployment.

The canonical OpenAPI contains top-level `requestBody.required = true`, the
required `request` schema and optional `files`. Current generated-file hashes:
`docs/openapi/mobile-bff.json` SHA-256
`D4F97E0476CD681A9460247D9F904C7901241269B73221A097EC6BE45132CDA0`;
`frontends/mobile-core/src/api/generated/mobile-bff.ts` SHA-256
`5D697D0D0615BB5BB93F0C4735B090D3420112B7B97F4FA6C3598CC31B2CA66E`.
No secret values were read or recorded. No product/scope decision delta was
needed; the frozen R5 contract was sufficient.

The attempt to stage a commit was rejected by the worktree Git permission
boundary (`.git/worktrees/v2-requests-contract/index.lock`, permission denied,
exit 128). No escalation or bypass was used; the uncommitted stable diff stays
available in this worktree for root integration.

## Bundled2 nginx bootstrap correction

Root's bundled2 review identified four actual `$COMPOSE` invocations in
`nginx/scripts/init-letsencrypt.sh` that expanded the string
`docker compose -f docker-compose.prod.yml` without an explicit env file. The
bounded correction changes only that script: `COMPOSE` is now the Bash array
`(docker compose --env-file .env.prod -f docker-compose.prod.yml)`, and all four
calls use the quoted array expansion. Existing service names, certbot options,
paths, prompts and config swaps are unchanged.

The pre-edit script SHA-256 was
`CABC860805E7B2D6BE14863D69D7B2274C8FFE6869652A02FDECBB12426740AA` and the
post-edit SHA-256 is
`19953F6895AF52E61E26DD97B8CB72435E3ADB7D978E370946D35529D94F5258`.
Git Bash `-n` syntax validation passed with exit 0. A first regex-only static
caller probe returned exit 1 because its pattern did not match the literal
array syntax; the corrected literal assertion passed exit 0 with declaration=1,
quoted array invocations=4, unquoted invocations=0 and docker compose call
sites=1. The script, certbot, openssl, nginx and production Compose were not
executed. At that earlier bundled2 checkpoint, synthetic production Compose config was queued for H12 with an isolated env fixture and --quiet; H16 later recorded exit 0, so that pending state is resolved. No ambient .env.prod or secrets were read.

## Resume source manifest

`source-manifest.json` records the current bounded diff and evidence inventory:
17 product files plus 39 evidence files with byte counts and SHA-256 hashes.
The versioned `tests/e2e/.env.ci` entry is intentionally marked
`sensitive-name-not-read`, matching the safe-stop checkpoint; no environment
value was read or printed. The manifest excludes its own self-hash and is
validated by the recorded replay check after this update.

## Bundled4 preflight, validator and caller correction

The bundled4 source extension covered only scripts/preflight-deploy.sh,
scripts/validate-env-prod.sh, the four named production runbook/checklist
documents and docs/testing/M16-vps-verify.md. R4 UI files and unrelated
documentation were not searched or changed.

Finding F1 (HIGH) is reproduced by the original preflight pipeline shape:
pipefail combined the Compose status with a grep status, so a failing Compose
command whose output lacked error or invalid could be reported as success.
Section 4 now invokes
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config --quiet
directly, preserves its status and reports it in the failure line. Git Bash
syntax returned exit 0. An isolated mock tree exercised Compose exit 0 and
exit 17 (stderr contained no error keyword): the preflight returned 0 and 4
respectively, and the latter log preserved exit 17. No real Docker,
production env, bootstrap or deployment command ran. Raw logs are
preflight-mock/mock-exit0.log and preflight-mock/mock-exit17.log.

Finding F2 (HIGH) is covered by a literal static inventory over the exact
seven scoped paths. All 35 docker compose occurrences now carry
--env-file: production callers use .env.prod with docker-compose.prod.yml,
while M16 uses /opt/rutcampustrack/.env.prod with
/opt/rutcampustrack/docker-compose.prod.yml. Existing service names,
subcommands, flags and rollback order remain unchanged. The inventory command
returned exit 0; no archive or unrelated documentation sweep was performed.

Finding F3 (MEDIUM) is covered by the complete IPv4 grammar guard placed
before IFS='.' read; this rejects trailing-dot forms that Bash can otherwise
normalize during splitting while retaining the existing leading-zero,
RFC1918, alignment, network, broadcast and exact-host checks. Final syntax
returned exit 0. The synthetic validator matrix returned exit 0 for the
documented private pair and exit 3 for nine negative fixtures: malformed,
unaligned, public, malformed-IP, out-of-subnet, network, broadcast,
trailing-dot-IP and trailing-dot-CIDR. No production env or secrets were read.

Finding F4 (MEDIUM) evidence is reconciled with the historical H16 result:
docker compose --env-file
.agent/requests-contract-r5/isolated-synthetic.env.prod -f
docker-compose.prod.yml config --quiet returned exit 0 using fixture SHA-256
1B5EC4E173079019AB38A6ADFD4185FD8EA4B1255B851160E5055C5A7206525E.
It was config-only with no service or container startup, bootstrap, TLS or
deployment. The earlier H12 pending label is cleared in the structured check;
historical raw logs and prior H11 PASS evidence remain unchanged.

The current bundled4 source and pure-check state is ready for parent
integration and a fresh independent Sol review. Shellcheck remains unavailable
(no installation attempted); real preflight, Docker production config after
this source-only extension, Gradle, frontend generation, startup, deployment
and provisioning remain outside this resumed pure lane or are represented by
the accepted historical H11/H16 evidence.
## Historical H11 test-count provenance

The accepted H11 normal OpenApiSnapshotIT run is historical and was not rerun
during bundled4. The parent independently observed the H11 XML result in the
integration context: 4 tests executed, 4 passed, 0 skipped, 0 failures and
0 errors. The source is
services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contractexport/OpenApiSnapshotIT.java;
the requestBody.required assertion is at source line 88. This leaf retains
only the redacted execution summary in gradle-h11.log, which reports BUILD
SUCCESSFUL; raw XML is not present here and no raw XML content is claimed.
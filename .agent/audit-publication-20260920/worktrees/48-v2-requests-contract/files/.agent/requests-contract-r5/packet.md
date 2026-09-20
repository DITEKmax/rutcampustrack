# Requests contract repair R5 — compact packet

Date: 2026-09-13 (Europe/Moscow)  
Role: fresh bounded implementation leaf / sole writer for R5  
Model/effort: `gpt-5.6-luna / max`  
Risk: S3 (configuration trust boundary and public multipart contract)  
Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-contract`  
Branch: `codex/v2-requests-contract`  
Base revision: `b8220ac92125a8afa37598b270aa4fab7aa1f470`  
Rules SHA-256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`

## 1. Goal

Repair R1 Requests findings owned by R5: make documented production and E2E
Compose input complete and strictly validate the private network plus the sole
trusted Nginx address; make the generated multipart `requestBody` required in
the Java-first Student API contract and guard that requirement in the runtime
OpenAPI snapshot test. Keep generated OpenAPI and TypeScript artifacts on the
existing generator path for the main lease.

## 2. Context/evidence

- Frozen contract: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/REQUESTS-R4-R5.md`.
- Current source E: `b8220ac92125a8afa37598b270aa4fab7aa1f470`.
- Fresh R1 review FAIL: `ffde92fdd1a7791ec3fb375102dc878d2fb2adc9..b8220ac92125a8afa37598b270aa4fab7aa1f470`, 116 files, `+13682/-1187`.
- Finding 1: Compose requires `GATEWAY_NGINX_IPV4` and
  `GATEWAY_PRIVATE_SUBNET`, while the production template, validator and CI/E2E
  input path did not define or validate them. Clean documented interpolation
  therefore failed before service start.
- Finding 4: `StudentApi.submitExcuse` uses a required Spring request part but
  the generated operation has no top-level `requestBody.required`, leaving
  generated clients able to omit the part.
- R4 owns findings 2, 3 and 5. R5 does not modify Requests UI/controller,
  attachment presentation or MIME policy.
- Values added to versioned templates are non-secret synthetic network inputs:
  `172.30.0.0/24` and `172.30.0.10`.

## 3. Relevant scope

R5 writes only `.env.prod.example`, `tests/e2e/.env.ci`,
`docker-compose.e2e.yml`, `.github/workflows/ci.yml`, the documented production
Compose invocations in `README.md`, `scripts/validate-env-prod.sh`,
`StudentApi.java`, the focused `OpenApiSnapshotIT`, and this evidence directory.
`docs/openapi/mobile-bff.json` and `frontends/mobile-core/src/api/generated/mobile-bff.ts` are generator-owned; accepted H11 evidence records their canonical refresh, and this leaf did not hand-edit them.

## 4. Required behavior

- Both Compose templates expose the same explicit private subnet and static edge
  address required by their `private_net` and Nginx service declarations.
- The production template and README pass `.env.prod` explicitly to Compose;
  CI passes `.env` explicitly to every E2E Compose operation and runs a clean
  config interpolation gate before bringing up the stack.
- The production validator treats both gateway variables as required, rejects
  malformed or non-canonical IPv4, unaligned or non-RFC1918 CIDRs, subnet
  prefixes without usable hosts, and an Nginx address outside the exact subnet,
  network or broadcast address. Wildcard trust values cannot pass this input
  contract.
- `StudentApi.submitExcuse` uses an operation-level Swagger
  `@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true)`;
  Spring's inferred `@RequestPart("request")` and optional `files` part remain
  unchanged. The OpenAPI snapshot integration test asserts the emitted
  operation has `requestBody.required = true`.
- The accepted H11 lane completed the existing Java snapshot update and mobile-core OpenAPI TypeScript generator refresh; this bundled4 extension did not rerun them.

## 5. Constraints

- Preserve the E source and all unrelated/foreign work. One writer in this
  worktree; no shared checkout edits, full-diff imports or generated hand edits.
- Do not add secrets, alter trust to a wildcard, relax upload limits, change
  auth/business behavior, or introduce a new public API shape.
- Do not run Gradle, Docker, builds, dependency installation, tests, runtime,
  deployment or provisioning in this leaf. Main owns the heavy/check lanes.
- No children, Terra, push, main merge, production migration or secret access.

## 6. Existing patterns

- Java contract interfaces are the source for Springdoc/OpenAPI snapshots.
- `OpenApiSnapshotIT` normalizes `/api-docs` and compares the committed
  `docs/openapi/mobile-bff.json` baseline.
- `frontends/mobile-core/scripts/generate-types.mjs` is the canonical
  generator for `mobile-bff.ts`; generated files carry a spec hash and must be
  refreshed through that script.
- `scripts/preflight-deploy.sh` and deploy workflow already pass an explicit
  `--env-file`; R5 aligns the documented and E2E callers with that pattern.

## 7. Acceptance criteria

- A clean documented synthetic E2E env interpolates both Compose files' gateway
  requirements and keeps gateway trust equal to the sole Nginx edge address.
- Production env validation rejects missing, public, malformed, unaligned or
  out-of-subnet gateway values and accepts the documented private pair.
- Runtime OpenAPI and regenerated artifacts represent a required multipart
  request body; generated TypeScript no longer makes the operation body
  optional.
- No R4-owned UI files, unrelated roles, generated outputs by hand or foreign
  changes are present in the R5 diff.

## 8. Verification

Initial lane commands (later completed in the accepted H11 lane; not rerun during bundled4):

```text
bash -n scripts/validate-env-prod.sh
shellcheck --severity=warning scripts/validate-env-prod.sh
docker compose --env-file .env -f docker-compose.e2e.yml config --quiet
./gradlew :services:mobile-bff:mobile-bff-app:integrationTest --tests "*OpenApiSnapshotIT" -Popenapi.snapshot.update=true --no-daemon --no-parallel --max-workers=1
cd frontends && npm run generate:types && npm run generate:types:check
./gradlew :services:mobile-bff:mobile-bff-app:integrationTest --tests "*OpenApiSnapshotIT" --no-daemon --no-parallel --max-workers=1
```

The first two commands are static validator checks; the Compose command uses
only tracked CI synthetic inputs. Gradle updates and checks the Java snapshot;
the frontend commands regenerate and verify the mobile-core artifact. Main
must record each command's exit code, environment and evidence before accepting
the scope. Runtime product evidence was recorded in the accepted H11 lane; this bundled4 pure extension did not start Docker or any service.

## 9. Do not

Do not modify R4 UI files or findings, hand-edit `docs/openapi/mobile-bff.json`
or `frontends/mobile-core/src/api/generated/mobile-bff.ts`, add ambient env or
secret fallbacks, trust wildcard addresses, run prohibited checks/runtime,
claim generated/runtime PASS before main evidence, or widen this bounded scope.

## Bundled4 owner extension

Root extended this packet with the following frozen bounded scope:
scripts/preflight-deploy.sh; scripts/validate-env-prod.sh;
docs/operations/runbooks/cert-renewal.md;
docs/operations/runbooks/backup-restore.md;
docs/operations/runbooks/loki-major-upgrade.md;
docs/operations/deploy/prod-deploy-checklist.md; and
docs/testing/M16-vps-verify.md, plus synthetic fixtures and evidence under
.agent/requests-contract-r5.

The required behavior is direct Compose config exit propagation in preflight,
canonical dotted-decimal IPv4 validation before IFS splitting, and explicit
--env-file plus compose-file tuples at every named production caller while
preserving operation flags. Controlled mocks must cover Compose exit 0 and a
nonzero exit whose output lacks error keywords; validator tests must cover
trailing-dot IP and CIDR. H16's already accepted isolated synthetic
production Compose config is reconciled as historical evidence. No real
preflight, production env, script/bootstrap/TLS/deploy execution, Docker
recheck after this source-only extension, Gradle or dependency work is part of
this resumed pure lane. The next gate after evidence/manifest coherence is
parent integration and fresh independent Sol review.
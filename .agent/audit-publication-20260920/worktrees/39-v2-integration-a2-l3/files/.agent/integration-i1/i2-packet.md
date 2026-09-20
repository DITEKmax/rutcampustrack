# I2 integration packet — accepted R5 over preserved I1 union

Date: 2026-09-15 (Europe/Moscow). Assigned developer: fresh `gpt-5.6-luna`,
`max`; sole writer in this existing isolated worktree. Rules SHA256:
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.

## 1. Goal

Integrate only the accepted R5 17 tracked paths into the preserved I1 18-path
union on E/base `b8220ac92125a8afa37598b270aa4fab7aa1f470`, producing an exact
34-path source union for root review. This is source integration evidence, not
main merge, deploy, or a product-complete claim.

## 2. Context/evidence

R5 source is the read-only sibling
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-contract`
at the same E base. Its accepted source manifest SHA256 is
`ED35EDC347D30D610F7530DA767C90674A6F1853E4C76331535D8098FE3992F6`, with 17
tracked product rows. The pre-I1 accepted union is 18 rows with canonical SHA256
`EB831B86E3E61559B4B9B3755AD36F69F582039CB6D460F88B003544E210DC85`.
R5 generated artifacts are snapshot `D4F97E0476CD681A9460247D9F904C7901241269B73221A097EC6BE45132CDA0`
and TypeScript `5D697D0D0615BB5BB93F0C4735B090D3420112B7B97F4FA6C3598CC31B2CA66E`.
The old I1 author status remains `ERRORED_CAPACITY_METADATA_CHECKPOINT`; no
author completion is inferred.

## 3. Relevant scope

R5 contributes exactly 17 tracked paths. The only overlap with I1 is
`services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contractexport/OpenApiSnapshotIT.java`.
Its I1 JWT fixture is preserved byte-for-byte; R5 contributes the two-line
`requestBody.required` assertion. The target product union is exactly 34 paths.
This leaf owns only `.agent/integration-i1/*` and the narrow
`docs/sources/manifest.yaml` update.

## 4. Required behavior

Transfer the 16 non-overlapping R5 files exactly and retain the opaque tracked
`tests/e2e/.env.ci` row without opening or printing values. Apply only the
confirmed OpenAPI assertion to the I1 test. Preserve all I1 A2/L3 behavior.
Carry the accepted R5 snapshot and generated TypeScript artifacts exactly; do
not regenerate them. Record the six known I1 time metadata changes plus the new
required request body as the seven bounded OpenAPI deltas; object-key ordering
is a non-semantic source difference.

## 5. Constraints

One writer; preserve root, source siblings, and all unrelated work. No source
evidence/AGENTS/secret/build/cache wholesale copy, no generated-code editing,
no lockfile/config widening, no push/deploy/main merge, no child agents, and
Terra is forbidden. Source worktree remains read-only. No heavy build,
Docker, npm install, generation, or product runtime runs in this SOURCEONLY
allocation.

## 6. Existing patterns

Use the accepted R5 source manifest and I1 source-freeze as authoritative
provenance. Exact-copy rows require source/target SHA equality; the overlapping
test is a bounded composition. The canonical 34-row manifest uses sorted
`path=UPPER_SHA` rows joined by UTF-8 LF without a trailing LF.

## 7. Acceptance criteria

The target has exactly the accepted 34 product paths and no source metadata
copy. All exact rows match accepted R5/I1 hashes; the overlapping test matches
R5 after the two-line assertion and retains the JWT adaptation. Snapshot JSON is
valid and marks the excuse request body required. Source branch/status is
unchanged. Provenance, diff, limitations, and check exit codes are recorded.

## 8. Verification

Run only pure source checks: Git/status/revision, source manifest and file SHA
checks, conflict gate, bounded no-index overlap diff, post-transfer equality,
exact scope count, JSON parse/request-body assertion, `git diff --check`, and
canonical union hash. Product tests, generation, Docker, and runtime are
deferred to root's later heavy lease.

## 9. Do not

Do not import `.agent/requests-contract-r5` evidence, validators, mocks or
source instructions; do not update the snapshot blindly; do not reinterpret
I1 stale metadata as a green author result; do not widen scope for WARN/ERROR;
do not claim runtime, deployment, or production readiness.

## H31 root addendum

After the source freeze and explicit root H31 exclusive GO, the bounded checks
listed in `i2-checks.json` were permitted: the exact BFF OpenAPI integration
selector without snapshot update, offline-only frontend dependency bootstrap
and type guard, R5 shell/caller static checks, and synthetic PROD/E2E Compose
`config --quiet`. No source regeneration, service startup, bootstrap, TLS,
production secret access, or deployment was added to the scope.

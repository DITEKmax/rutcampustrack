# H78 BSON descriptor boundary contract

## 1. Goal

Make the Requests harness `Get-MongoSnapshot` boundary model the complete
Mongo-backed I1 descriptor contract. BSON `Long` attachment sizes must become
JSON integers only when the value is an exact safe JavaScript integer; the I1
assertion must then compare the complete attachment and outbox descriptors
without weakening the existing strict checks.

## 2. Context / evidence

- Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness`.
- Frozen baseline: `73fd5f27ceb429ad0692b073189f8a527c550830`.
- Owner rules: `.agent/orchestration-v2/RULES.md`, SHA-256
  `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
- Current owner checkpoint: `.agent/orchestration-v2/CURRENT.md` (owner GO,
  H78 bounded retry; no Docker/Gradle lease for this leaf).
- Root-provided native BSON evidence:
  `.agent/orchestration-v2/evidence/h79-bson-sample.json` records mongosh
  `Long(10485760)` as `{high:0,low:10485760,unsigned:false}` with
  `_bsontype=Long`, `toString()="10485760"`, `toNumber()=10485760`, and the
  unsafe `9007199254740993` sample. The run exited `0` in pinned Mongo 7 with
  network `none`; `.agent/orchestration-v2/evidence/h79-cleanup.json` records
  an empty owned cleanup set.
- Prior H78 context/report: `.agent/orchestration-v2/evidence/h78-context.json`,
  `.agent/orchestration-v2/evidence/h78-report/owned-cleanup.json` and the
  actual I1 failure showed `attachments[0].size` must be a JSON integer.
- Product source mapping inspected read-only in the trusted runtime worktree:
  `RequestAttachmentDocument.size` and
  `RequestAttachmentDescriptorDocument.size` are `Long` mapped to Mongo
  `size`; the application writes `file.bytes().length` as a `long`, attachment
  state is `ACTIVE`, IDs are strings, dates are `Instant`, and the status
  writer stores `ExcuseTicketStatus.SUBMITTED` as lowercase `submitted`.
- Existing H71 changes and dirty files predate this packet and must remain
  intact.

## 3. Relevant scope

Sole writer scope is:

- `.agent/student-role-orchestrator/requests-runtime/runner.ps1`, limited to
  the emitted `Get-MongoSnapshot` BSON→JSON descriptor boundary;
- `.agent/student-role-orchestrator/requests-runtime/r8-mongo-projection-check.ps1`,
  limited to native-BSON boundary fixtures and strict negative cases;
- this unique `h78-bson-descriptor-boundary/` evidence directory.

Product sources, Docker resources, runtime worktrees and foreign evidence are
read-only dependencies.

## 4. Required behavior

`Get-MongoSnapshot` must serialize Mongo attachment `size` values through one
explicit exact-integer helper:

- accept an existing JavaScript number only when `Number.isSafeInteger` and
  the value is non-negative where the descriptor contract requires it;
- accept a native BSON `Long` only when its decimal `toString()` is an integer,
  `toNumber()` is safe, and converting that number back to `BigInt` exactly
  equals the decimal value;
- reject strings, arbitrary `{high,low,unsigned}` objects, other BSON numeric
  types, NaN/infinity/fractional values and unsafe integers;
- apply the helper to stored Mongo descriptor sizes and to already-JSON outbox
  payload sizes separately; do not imply that the outbox payload is BSON;
- preserve ID/date/state/null/request-id/name/content-type/SHA fields and the
  lowercase persisted ticket status mapping exactly as emitted by the existing
  query.

The PowerShell `Assert-JsonInteger` and complete descriptor equality checks stay
strict. No global normalization, blanket `Number()` conversion or string
acceptance is allowed.

## 5. Constraints

No product change, Docker, Gradle, full runtime, network, ports, secrets,
commit, push, deploy or child agents. Do not overwrite root H78/H79 evidence or
foreign dirty work. Root owns the one heavy native mongosh run after the source
freeze.

## 6. Existing patterns

Reuse `Get-MongoSnapshot`, `Get-ExactObjectProperty`,
`Convert-AttachmentDescriptorToCanonicalObject` and `Assert-I1MongoDelta`.
Keep API camelCase descriptors, stored Mongo snake_case descriptors and
outbox JSON payload descriptors as separate shapes. Use the existing Node
generated-JS harness style; extend it with a real BSON-Long-compatible fixture,
then let root run the same emitted script once against native mongosh BSON.

## 7. Acceptance criteria

1. Generated Mongo JavaScript syntax and pure query/assertion checks pass.
2. A native `Long(10485760)` fixture produces a JSON integer and a complete I1
   descriptor/outbox comparison passes, including all ID, date, state, nullable,
   request-id, name, content-type, digest and size fields.
3. Wrong type, invalid, fractional, unsafe Long and unsafe JSON-number cases
   are rejected; an arbitrary Long-shaped plain object is not accepted.
4. API `PENDING`/`ACTIVE`, persisted lowercase `submitted`/`ACTIVE`, and
   already-JSON outbox payload semantics remain distinct.
5. Existing dirty work remains unchanged outside the scoped edits; no product
   runtime is relabeled PASS.

## 8. Verification

Record exact command, exit code, revision, versions and evidence in
`checks.json` and `summary.md`. Leaf checks are parser/node syntax, native-BSON
fixture/pure assertion checks and scoped `git diff --check`. Root performs the
single pinned `mongosh --nodb` native-BSON emitted-script run and records its
runtime evidence/provenance; no Docker or full harness run belongs here.

## 9. Do not

Do not redesign the request contract, alter product persistence/converters,
weaken `Assert-JsonInteger`, accept aliases or strings, use a guessed BSON
representation as the only evidence, normalize all numbers globally, rerun
full runtime, edit protected/global instructions, or escalate to Terra.

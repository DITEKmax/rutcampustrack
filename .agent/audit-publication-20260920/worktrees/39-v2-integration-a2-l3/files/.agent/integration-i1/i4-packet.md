# I4 integration packet — accepted map read over the frozen I3 union

Date: 2026-09-15 (Europe/Moscow). Assigned developer: `gpt-5.6-luna`,
`max`; sole writer for this existing target worktree. Rules SHA256:
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.

## Goal

Integrate the accepted M2/M3 campus-map reader into the accepted A2/L3/R5/I3
target union, preserving the existing student projection, Homework, and IPAM
work. This source-only handoff freezes a reviewable 44-path product union; it
does not claim the later heavy checks or a main merge.

## Context/evidence

The target is `codex/v2-integration-a2-l3` at the frozen E base
`b8220ac92125a8afa37598b270aa4fab7aa1f470`. The accepted I3 35-path union
has SHA256
`604CD4D36A70710B7C1790805DA70721B97741AB7CA0924E46BAF3850F614C6A`.
The read-only source is `v2-map-read` at the same E base. Its
`.agent/v2-map-read/source-manifest.json` SHA256 is
`E16EEBA8BA3FBF0260E1BEE755D0CC45C859358CDFBE4F097CB8CB687371324C`.
All eight `files[]` hashes and all four candidate model destination hashes
matched before transfer. The source worktree remains dirty only in its
pre-existing bounded map scope and was not edited.

## Relevant scope

The source manifest defines twelve product paths: four model destinations and
eight tracked files. The three existing target overlaps are
`AcademicGrpcServiceImpl.java`, `StudentHomeworkGrpcIdentityInterceptor.java`,
and `StudentHomeworkGrpcIdentityInterceptorTest.java`. Nine new paths were
transferred: eight exact copies (the four models, `CampusMapReadRepository`,
`CampusMapReadService`, `CampusMapReadServiceTest`, and
`CampusMapReadRepositoryIT`) plus the constructor-adapted
`CampusMapGrpcReadTest`. The target also has the allowed constructor-only
adaptation in `StudentProjectionGrpcServiceTest`.

## Required behavior

The Academic gRPC service keeps both `StudentProjectionScopeService` and
`CampusMapReadService`, all existing projection/Homework behavior, and the
three accepted map handlers/converters. The identity interceptor admits the
homework mutation/read, projection, and all three map method names. Its test
keeps all A2 projection/homework assertions and adds the map positive and
negative cases from M2. Constructor changes supply only the new dependency
mocks required by the composed service; no assertion is weakened.

## Constraints

One writer owns this target; source, E, and original canonical contracts are
read-only. No children, Terra, push, deploy, main merge, proto/schema/build
dependency changes, generated output, source instructions, secrets, runtime
cache, or R3/R4/L5A evidence import is allowed. The M3 repository test remains
historical source evidence until the parent opens the later H36 heavy lease.

## Existing patterns

Use the accepted A2 signed STUDENT boundary and archived-aware own-user lookup,
the M2 map service's immutable catalog/plan/asset model, and the source
manifest's destination hash for `CampusMapReadModels`. Preserve the retained R1
plan under the R2 catalog and the physical `plan.id` versus logical asset
version distinction. Existing I1/I2/I3 manifests and critical OpenAPI,
generated-TypeScript, and JWT evidence remain historical accepted inputs.

## Acceptance criteria

The product manifest contains exactly 44 unique paths with zero hash
mismatches. The three overlap files have separate composed provenance, and
the nine non-overlap transfers match the source manifest. The combined
Academic unit selector and the two PostgreSQL integration selectors are
recorded exactly for the parent. Focused runtime and Sol review remain
separate gates; the historical M2/M3 104-test result is not a new I4 pass.

## Verification

This handoff runs only pure source checks: source/target SHA256 comparison,
manifest recomputation, static symbol/method and constructor inventory, and
diff whitespace checks. No Gradle, Docker, npm, network, snapshot generation,
or runtime command is run in I4. The planned commands, with four Gradle
flags, are recorded in `i4-checks.json`; the parent opens H36 only after
accepting this freeze.

## Do not

Do not replace either accepted branch of a composed file, drop identity/map/
Homework/projection negative assertions, claim exact-copy for a composed file,
import stale runtime evidence as current, widen the path set, or make a
producer/data/full-server claim.

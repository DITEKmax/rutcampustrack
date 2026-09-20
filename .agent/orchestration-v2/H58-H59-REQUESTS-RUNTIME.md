# Requests H58/H59 runtime acceptance — 2026-09-19
## Goal
S3 verify accepted Requests integration through real edge/services after H57 trusted build. H58 prerequisite-only, H59 full I1/I2 conditional on H58 PASS and explicit root START.
## Context/evidence
REQUESTS-HARNESS-R3.md and sibling frozen requests-runtime-contract-2026-09-13.md define requirements; explicit diagnostic port127.0.0.1:18520 amendment retained. Full independent Sol harness PASS, H40 network proof PASS/cleanup. PWA fix426a15b6 accepted, H57 currently building; no runtime allowed until actual PASS and manifest SHA pinned in execution evidence.
## Relevant scope
Root sole executor of frozen runner C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runner.ps1, SHA67F6D0E471FCE5DC7835023D32BB5BD3D26EEB00426AD9AD7A7C2C62409FCA3D. UnionRepo C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-runtime-build-r2 revision426a15b6b42e816deaa3ca5c50437e0964aaf85e. Root owns new runner evidence and orchestration-v2/evidence/h58-* and h59-*; no writers there.
## Required behavior
H58 pass exact union revision, absolute H57 manifest and independently computed hash, -ValidateOnly; inspect actual report/node/selftest/pinned images, zero created resources. H59 same args without ValidateOnly after root accepts H58. Record baseline owned inventory. Existing runner handles unique network, 12 containers (six Java, five infra, Nginx), exact ownership labels/IDs, loopback18514-18520 only, pinned local images and finally cleanup. Verify cleanup and actual exit independently.
## Constraints
RULES SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. Exclusive heavy queue; Lessons source-only throughout. Scoped require_escalated/login:false for proven Docker namedpipe execution context. No product code edits or ambient credentials. Generated ephemeral keys/secrets remain redacted and owned by runner.
## Existing patterns
Frozen existing harness/probe, accepted read-only seeds, immutable artifact snapshots and trusted manifest consumer. No alternative framework; source hashes must remain frozen. Preserve previous reports by unique root evidence before any rerun; stop first failure and diagnose.
## Acceptance criteria
H58 exit0 SOURCE_VALID/runtime NOT_RUN. H59 exit0 PASS with I1 exact two10MiB downloads/hash/replay/single ticket+receipt+event; I2 fixed/chunked oversized413/no-store/no upstream or data mutations; owned cleanup PASS and resources absent. No scenario inferred from source or mocks.
## Verification
Root reads redacted report phases/assertions/cleanup and relevant raw logs, pins report/hash/commands/environment. Browser UI remains separate gate; no whole-product readiness claim. Confirm no foreign resource deletion and no source mutation.
## Do not
No pulls, dependency updates, production Compose, schema/data change outside disposable fixtures, foreign cleanup, secret output, main merge/push/deploy. No automatic source fix/retry or overlapping heavy checks after failure.

## H62 conditional retry amendment — 2026-09-19
H59 failed before I1/I2: Academic Spring context rejected mutually exclusive runtime metrics.enabled/access. H59 actual exit1; all seven created containers, network, keys and artifact snapshot removed; root post-run inventories empty. Exact one-line removal accepted for source review, new runner SHA2B47DA9105461EB3C9D11FAE7CA92B319A7B8695725F665D96D8F03930CCD9A0. Root independently compared before/current raw bytes and AST0. H62 reserved, NOT ACTIVE: require fresh full Sol PASS and H61 owned cleanup/release plus root START. Reuse unchanged H57 build/manifest61E73ED658D43CD878AFE6DA6AB703D2C73844E8114329FD991566BF9C009F4F, same426a15b6 source; no rebuild necessary for external runtime env correction. H58 source self-test/pinned prerequisite evidence remains valid for unchanged probe; full runner repeats source/artifact/pinnedimage/port checks. All original I1/I2, ownership, redaction and cleanup requirements remain. New unique runner run and root evidence/h62-* capture; preserve H59 report/error evidence.

## H68 actual full retry authorized after H67 PASS
H67 corrected config+TLS pinned nginx-t actual0, cleanup verified. Fresh fullSol PASS0 runner9A2D703548110A142597B02196F5483F0F61D7329DFBFED313E38F1AFC0B4B9D includes real outer-failure fault injection and redacted cleanup preservation. H68 now root exclusive START; H70 released. Same clean426a15b6 union/H57manifest61E73ED658D43CD878AFE6DA6AB703D2C73844E8114329FD991566BF9C009F4F, all12containers/oneuniqueownednetwork/loopback18514–18520/pinnedimages/originalI1I2 unchanged. Root h68-context/stdout/stderr/report and independently verified owned absence. Existing runner repeats full source/preflight/artifact verification. Preserve all earlier runs; no rebuild. Stop first actualfailure, no blind retry. H67 is configuration success only, not I1/I2 acceptance.

# I6 external build producer compact contract

## Goal

Produce an external, truthful trusted build manifest for six executable service
JARs and the PWA after a successful build of the exact accepted I5 revision.

## Context/evidence

The accepted I5 source has 58 product rows and canonical manifest
`D3FF3DFDC7C093935CCE3DEF4B4C0A4151F876F1572C3A3851A4480A588FC204`.
The local integration commit is `d7ec16572db325d944f1a1fbd3b4960b827d09c3`.
The accepted R3 reader is the sibling
`v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runner.ps1`,
especially `Resolve-TrustedBuildArtifacts` and its canonical serializer.

## Relevant scope

Only the external directory
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/build-i6`
is writable for this lane. `producer.ps1` owns the later build invocation and
manifest output; `producer-pure-check.ps1` owns source and synthetic checks.
The accepted source worktree and new build worktree are read-only inputs here.

## Required behavior

The producer must verify the exact lowercase revision and clean tracked/untracked
status, reject any pre-existing JAR or PWA output, record actual environment,
commands, timestamps, stdout/stderr logs and exit codes, stop at the first real
failure, verify the six bootJARs and complete PWA, and emit canonical UTF-8 JSON
only after a clean post-build check. The manifest must use the exact R3 schema,
canonical key order, ordinal artifact path order, and PWA digest serializer.

## Constraints

Rules SHA-256 is
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
No Terra, children, product edits, harness edits, reset, clean, cache deletion,
dependency install, Docker, service startup, push, merge, deploy, secrets or
global permission changes. H41 remains reserved until root accepts this source
and pure evidence.

## Existing patterns

The producer mirrors `Get-JarSpec`, `Get-PwaDistManifest`,
`Convert-PwaFilesToCanonicalText`, and
`Convert-TrustedManifestToCanonicalObject` from the accepted R3 runner. Gradle
uses six individual `bootJar` commands with
`--no-daemon --no-parallel --max-workers=1 --no-problems-report`; PWA uses
`npm run build --workspace @rct/pwa-vue` from `frontends`.

## Acceptance criteria

The exact 58 source paths are committed locally, the clean build worktree points
to that commit, producer source has a stable hash and reviewable core excerpt,
pure checks pass, and no manifest is created by a failed or unrun build. A later
H41 run must provide real artifacts, hashes, bytes, command logs and per-command
exit codes before root pins the manifest.

## Verification

Run PowerShell AST parsing and `producer-pure-check.ps1` only before H41. Verify
source/worktree status, revision, 58 path hashes and Git blobs. Record every
check command, exit code, environment and evidence. Root may later run the exact
producer command under the one heavy H41 lease after source review.

## Do not

Do not manufacture a PASS, manifest, artifact hash or runtime result; consume
stale output; alter R3 harness behavior; run Gradle/npm/Docker before root GO;
or overwrite occupied paths and foreign work.

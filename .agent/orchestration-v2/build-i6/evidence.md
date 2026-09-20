# I6 evidence ledger

## Scope and source freeze

Risk is S2. The governing rules are
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`
with SHA-256
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
The canonical task contract is
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/INTEGRATION-I6-BUILD.md`.
Only `build-i6/**` was written for the external producer. No harness or product
source changed.

## Exact integration commit

The assigned integration worktree started at
`b8220ac92125a8afa37598b270aa4fab7aa1f470`. Its 58 manifest rows were verified
against actual file SHA-256 values with zero missing files and zero mismatches;
the recomputed canonical manifest was
`D3FF3DFDC7C093935CCE3DEF4B4C0A4151F876F1572C3A3851A4480A588FC204`.
The index was empty before staging. An explicit manifest-derived path list
staged exactly 58 paths, with zero unstaged product paths and
`git diff --cached --check` exit `0`.

The authorized local commit is
`d7ec16572db325d944f1a1fbd3b4960b827d09c3`, parent
`b8220ac92125a8afa37598b270aa4fab7aa1f470`, with exactly 58 changed paths.
After commit the prior foreign status remained visible:
`M AGENTS.md`, `?? .agent/integration-i1/`, `?? .agent/v2-requests-ui/`, and
`?? docs/sources/`; it was not staged or removed.

## Clean build worktree

The previously absent path
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-runtime-build`
was created on branch `codex/v2-runtime-build` at the actual commit above.
Its tracked and untracked status is clean (`0` entries). Each manifest path is
present; source and new-worktree `git hash-object --path` values match
`HEAD:<path>` for all 58 paths (`gitBlobMismatches=0`). Raw bytes differ on 53
paths because the clean worktree checkout normalizes line endings; this is
recorded rather than misreported as a raw manifest match.

## Producer source and core behavior

`producer.ps1` is `26740` bytes, SHA-256
`543CCC3835A61A92348B96B3BEEAE9094D2DFB32C04A2DC19837DD6F1F013F9F`.
`producer-pure-check.ps1` is `10179` bytes, SHA-256
`182C64A6789284ACE9CB87C13F620E046B4DF99D32F6DF2C2B657499B02A84D6`.
Both parse with PowerShell AST exit `0`. The captured core locations are:

- `producer.ps1:131` rejects pre-existing JAR/PWA output.
- `producer.ps1:185` collects exactly one executable JAR per role.
- `producer.ps1:233` captures actual command stdout/stderr, times and exits.
- `producer.ps1:310` checks exact revision and clean status.
- `producer.ps1:344` mirrors the accepted canonical manifest key/array shape.
- `producer.ps1:407` writes no-BOM/no-newline UTF-8 through a run-owned temp.
- `producer.ps1:431-442` resolves caller paths and records the exact manifest path.
- `producer.ps1:478-502` gates artifact verification, clean-after and manifest emission.

The producer has six exact Gradle bootJar commands with
`--no-daemon --no-parallel --max-workers=1 --no-problems-report`, followed by
`npm run build --workspace @rct/pwa-vue`. It records separate raw logs under a
unique run directory and refuses an existing manifest path. It performs no
dependency install, cache copy, Git clean, Docker operation or runtime launch.

## Pure checks and correction gate

`pwsh -NoProfile -NonInteractive -File .agent/orchestration-v2/build-i6/producer-pure-check.ps1`
exited `0`. Its stdout/stderr are preserved in `pure-check.stdout.log` and
`pure-check.stderr.log`; stderr is empty. The final rerun is preserved in
`pure-check-final.stdout.log` (SHA-256
`04298F939D5F56EBFF1905E0DC71644525A461B8FF29F2FB3B75E2749A0C2ACE`) and
`pure-check-final.stderr.log` (empty, SHA-256
`E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`). The
final AST rerun is preserved in `ast-final.stdout.log` (SHA-256
`32687AF217509B8D6ADF13CDA393152B6908C92963857D06E19E6684CAE7BD90`) and
`ast-final.stderr.log` (empty, same empty-file SHA-256). Both final commands
exited `0`. The check exercises producer AST,
accepted R3 serializer parity, canonical PWA digest, required build flags,
stale JAR/PWA rejection and timestamp string preservation without executing a
build command. The first serializer parity run exited `1` and is recorded in
`timestamp-parity-regression.md`; the bounded timestamp and failure-report path
corrections were followed by the passing run.

The final negative producer preflight used an intentionally incorrect expected
revision and a custom manifest path. It exited `1` before Gradle/npm, with
`run-report.json` run id
`20260915T213650170Z-27ac301f7496486c98a2f3c0ad267ad5` recording command exit
`0` for `git rev-parse`, the revision mismatch, and the requested
`negative-failure-final.json` path. Top-level raw stdout is empty
(`negative-final.stdout.log`, empty-file SHA-256
`E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`); raw
stderr is in `negative-final.stderr.log` (SHA-256
`DEDDF517B8F6FEEE116CF8D5E8993495D7948677BA920A8F3B04CA8A084B00F0`). The
negative manifest does not exist.

## Resume publication repair — 2026-09-19

The stopped WIP was first checked before editing: its pure check exited `0`, but
the source still used the misleading `postmoveReadAllBytes` fault point and did
not exercise the actual temporary-read helper. The bounded correction stayed in
`build-i6/producer.ps1` and `producer-pure-check.ps1`; the accepted consumer,
58-source integration commit and clean build worktree were not changed.

The stopped WIP hashes in the earlier ledger are retained as historical
evidence. They were superseded by the bounded FIX4 correction below; no old
PASS is reused.

The final pure command exited `0`; raw stdout is
`pure-repair-20260919.stdout.log` (SHA-256
`3F55E879313A037532B1C1D5F671ED53BC8579799F853DEE88243DF754D7B406`) and
raw stderr is empty (`pure-repair-20260919.stderr.log`, SHA-256
`E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`). The
matrix exercised the actual helper paths
`manifestTempWrite`, `manifestTempRead`, `manifestHash`,
`runreportWriteAllText` and `manifestRename`. Each preserved its original
injected error in a FAIL report, returned no published manifest before the
commit point, and cleaned its run-owned temporary file. The same run exercised
a pre-existing foreign destination and verified its bytes remained unchanged;
the synthetic post-commit diagnostic emitted only a warning and left the
committed PASS manifest/hash authoritative. Canonical JSON parity and command
timestamp string parity with the accepted consumer remained passing.

The final no-build negative preflight exited `1` on the exact revision mismatch
before Gradle/npm. Run
`20260919T164749881Z-c35cd191f77d477c827f3a94bf17d88f` reports status `FAIL`,
the original revision error and the requested custom manifest path; no manifest
was created. Top-level raw stdout is empty
(`negative-repair-20260919.stdout.log`, SHA-256
`E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`); raw
stderr is `negative-repair-20260919.stderr.log` (SHA-256
`B1144EEE5328319D4AEEF685CA7183239589F5BFDC88220B61F03BE710204AFE`).

## Runtime/build evidence

Runtime and H41 are `NOT_RUN` by contract. No Gradle bootJar, npm PWA build,
manifest emission, Docker, service startup, browser, ValidateOnly or Requests
I1/I2 operation was performed. The clean worktree has no JAR output, no PWA
dist and no manifest. Current read-only environment facts are PowerShell
`7.6.5`, Node `v24.14.0`, Java `21.0.10`; wrapper properties declare
`gradle-8.12-bin.zip`. H42 separately completed the locked offline dependency
setup in the clean build worktree: `npm ci --offline --no-audit --no-fund`
exited `0`, added 222 packages, and produced `h42-context.json`,
`h42-exit.json`, `h42-stdout.log` and `h42-stderr.log` under the root evidence
directory. This repair did not install dependencies; the H42 evidence is the
authoritative availability record. The producer still performs no dependency
install or cache copy.

During path debugging, one guarded invocation with the correct revision was
manually interrupted after the permitted Gradle/Java/Node version probes; it
never reached a bootJar or npm build and emitted no manifest. Its partial raw
probe logs remain under run
`20260915T213227207Z-2028c110825f4b0db042695b8dc466fc` and are not used as build
PASS evidence.

## Proposed H41 command

After root accepts this source and the one heavy queue is available, run exactly:

```powershell
pwsh -NoProfile -NonInteractive -File C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/build-i6/producer.ps1 `
  -UnionRepo C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-runtime-build `
  -ExpectedRevision d7ec16572db325d944f1a1fbd3b4960b827d09c3 `
  -ManifestPath C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/build-i6/requests-build-manifest.v1.json
```

The producer must return per-command raw stdout/stderr paths and exits in its
run report. A failed command stops the run and leaves the trusted manifest
absent; only a successful clean build can produce the manifest for root to pin.

## Diff and limitations

The external diff is limited to the producer, pure check, contract, evidence,
checks, correction record and raw pure/AST/preflight logs under `build-i6/**`.
The exact58 source commit and
new clean worktree are complete. Artifact hashes, PWA digest from real output,
manifest SHA, build exits and runtime behavior remain open until H41. No
product/scoped harness correction is proposed.

## FIX4 bounded correction — 2026-09-19

The fresh full review recorded four findings against the stopped source: HIGH
missing-ancestor checks when `build/libs` or `dist` leaves were absent, MEDIUM
eager `$jars[0]` interpolation under `StrictMode`, MEDIUM lack of a
process-level publication failure proof, and LOW stale wording that treated
the frontend dependency setup as unverified. The recorded request, reproduction,
correction and scope are bounded to `build-i6/**`; Terra, product sources,
consumer harness and build/runtime were not involved.

`producer.ps1` now validates all six planned JAR directories, the PWA `dist`,
`ManifestPath` and `RunsRoot` by walking their existing ancestors before any
`New-Item` or build command. The manifest destination is checked again before
the no-overwrite publication move. Actual Windows junction fixtures cover
missing `build` and missing `pwa-vue` leaves; a production-process junction
case exits `1` before creating runs or a manifest and leaves the external
sentinel unchanged. Empty `build/libs` directories pass preflight; a stale JAR
still fails with a lazily computed diagnostic.

The source has a test-only `-PublicationHarness` switch gated by the explicit
`RCT_I6_PUBLICATION_HARNESS=1` environment marker. It uses the production
initialization, `Write-TrustedManifest`, top-level catch/`Handle-ProducerException`
and `finally` paths with a synthetic manifest and never runs Gradle/npm. The
pure checker launches a real child process for each of
`manifestTempWrite`, `manifestTempRead`, `manifestHash`,
`runreportWriteAllText` and `manifestRename`; every child exited `1`, preserved
the injected original error in stderr and FAIL report, left the destination
manifest absent and removed its run-owned temporary file. The existing separate
post-commit case keeps the committed PASS manifest/hash authoritative after a
diagnostic warning; foreign destination bytes remain unchanged.

FIX4 source hashes are producer
`DB8A4B84E4D042F72F5E0912C2F270CA063AB4735228CB9299A45748DFAC7A83` and pure
checker `7E00D3E4B04E759E17A5851EB243FF7FB24ABBB95756251F87CB2984D7D9387D`.
The final AST commands and pure checker are recorded with exit codes and raw
streams in `checks.json`; process fault raw streams are
`process-fault-*.stdout.log` and `process-fault-*.stderr.log`, and the junction
proof is in `process-junction.stdout.log`/`process-junction.stderr.log`.
Product runtime, Gradle, npm build, Docker, ValidateOnly and Requests runtime
remain `NOT_RUN` for this repair gate. H42 offline dependency evidence is
read-only context and does not authorize H41.

## FIX2 pure fixture ownership and truthful evidence — 2026-09-19

The recorded MEDIUM defect was bounded to the pure checker process matrix:
deterministic `process-case-*`/`process-runs-*` paths used `New-Item -Force`
and recursive removal without proving current ownership, while
`Invoke-ProducerProcess` overwrote fixed stdout/stderr logs. The prior low
junction ledger also named hash `55C537700F19E4612714B1B1B574005C44DEB7E215498696237FD806A3030EA2`,
which was stale relative to the preserved raw file hash
`D5EE3B0CC81521C0D7DCA1CC30311A4E1123254203E7196779E3ABE1AA7A5CAF`.

The bounded correction changed only
`build-i6/producer-pure-check.ps1`. Each pure fixture root now has a fresh
GUID name and current-run owner marker under `build-i6`; recursive cleanup
resolves the candidate, verifies the workspace bound, directory/reparse state,
and marker token, then refuses cleanup if any reparse descendant remains.
Process cases are created without `-Force` and cleaned only after their checks
pass. Raw child stdout/stderr is written once into a preserved, run-scoped log
root through a no-overwrite writer. An occupied
`process-case-manifestHash/union/foreign-sentinel` and occupied log path are
explicitly checked for byte preservation.

Final verification used repository `HEAD`
`6a98366b7507e3ee76cc0e13690afffd403a5321` (the assigned `build-i6` scope is
untracked external evidence), PowerShell `7.6.5`, Node `v24.14.0`, and Java
`21.0.10`:

```text
pwsh -NoProfile -NonInteractive -File .agent/orchestration-v2/build-i6/producer-pure-check.ps1
exitCode=0
producer-pure-check.ps1 SHA-256 EDA74A40683D55F8CA561826F870217C5573504CBE596A63DC3C4538346BEE34
raw stdout pure-fix2-ownership-20260919-01.stdout.log SHA-256 E13DC278F5A26E743CB9607A9A635AFD1E73801464A8F1A87BFA3A32239AC189
raw stderr pure-fix2-ownership-20260919-01.stderr.log SHA-256 E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855
```

The final run id was `2a4a6a07c0b94714a303b8758375b445`. Preserved raw child
logs are under
`build-i6/pure-process-logs-2a4a6a07c0b94714a303b8758375b445/`; all five
fault children exited `1` and had empty stdout (`E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`). Their
stderr hashes are:

```text
manifestTempWrite A4C77AA3AFDCAAF742F53EAE93CC973F7FF1DE965E038EF3687268CAF9D96D4A
manifestTempRead D36809A09A70102E690991B816997D392F0572D448E187AD34962D11B954C43A
manifestHash 03B0A36BF068D8B3DC96627D8BA40DD6C41F21B2FBE3E68C4C8DA4EDB89EE22D
runreportWriteAllText 28E5DB0CB658DB2E039555B9A76E24C679D524A2334E5EF70FD620C065D94A34
manifestRename B13F23C73A13E0F3E31E57EB9F12A53F98D2AA170EB0D24CCE33F1C7BB86D3EB
process-junction 9768CBD58B9E78CE8A5D80932043A76EE0D306E649D7889C87542E654FBEEFC9
```

The process-junction stderr above is the actual final run-scoped raw hash;
the prior `D5EE...` file and all older logs remain untouched. The pure run
also passed the occupied sentinel/log byte checks, accepted empty build/libs,
rejected stale JAR/PWA outputs, preserved foreign manifest bytes, and proved
the post-commit diagnostic is warning-only. No Gradle, npm build, Docker,
service, browser, ValidateOnly, or Requests runtime was executed by this
check; product/runtime evidence remains outside this pure scope and is
`N/A` here.

The scoped diff is one checker source plus `checks.json`, this evidence ledger,
and unique run-scoped raw logs. Producer/consumer/product sources, H44 build
outputs, accepted exact58 source, and root runtime evidence were not changed.
Remaining limitation: this gate proves source/pure publication and ownership
semantics only; it does not prove a successful artifact build or runtime.

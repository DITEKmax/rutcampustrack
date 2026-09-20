# I6 publication repair gate — 2026-09-19

## Scope

Risk is S2. The sole writer scope is the external `build-i6` producer, pure
checker and scoped evidence. The accepted consumer, exact58 integration commit,
clean build worktree and all product sources are read-only inputs. Rules SHA-256:
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.

## Defect and reproduction

The stopped WIP had a prior MEDIUM finding: publication could precede later
fallible manifest hash/read/report work, leaving a trusted manifest after a
reported failure. The current WIP had moved the final no-overwrite move after
those checks, but its read fault point was still named `postmoveReadAllBytes`
and was injected before an actual read helper; the failure path was not yet
runtime-checked for original-error preservation and owned-temp cleanup.

## Correction

The producer now uses helpers tied to the real temporary write, temporary read,
hash, report write and no-overwrite rename operations. The report is prepared
before the final move, which is the explicit publication commit point. Fault
points are one-shot so the original failure can be recorded by the failure
path; an owned temporary is cleaned without requiring a hash that may not have
been computed. Once committed, diagnostics are warning-only and cannot rewrite
the PASS report or turn the process result into a failure.

## Criteria and evidence

- Canonical producer output remains byte-identical to the accepted consumer
  serializer, including command timestamp strings.
- Each of the five actual fault points leaves the destination manifest absent
  before commit, preserves the injected original error in a FAIL report, and
  cleans its run-owned temporary file.
- A pre-existing foreign destination remains byte-identical.
- Final source hashes, AST/pure exit codes and raw logs are recorded in
  `evidence.md` and `checks.json`.

## Checks and runtime

The final producer AST and pure checker exited `0`; the final wrong-revision
preflight exited `1` before Gradle/npm and emitted no manifest. No Gradle, npm,
Docker, ValidateOnly, service, browser or Requests runtime was run. H41 remains
reserved and is outside this repair gate.

## Diff and limits

The code diff is limited to `producer.ps1` and `producer-pure-check.ps1`; the
evidence diff adds final source hashes, raw logs, check records and this gate.
There is no consumer change, accepted58 change, build artifact, provenance
claim, commit, push, worktree mutation or product write. This packet is ready
for root's explicit RELEASE decision and fresh independent Sol review.

## FIX4 replacement gate — 2026-09-19

The replacement request records four review findings: missing existing-ancestor
checks for absent `build/libs`/`dist` leaves, eager empty-array JAR diagnostics,
manual-only fault proof, and stale H42 dependency wording. The correction is
bounded to this producer and pure checker scope.

`Assert-PlannedOutputPaths` now walks all six JAR directories, PWA `dist`,
`ManifestPath` and `RunsRoot` before any output-directory creation or build
write; `Write-TrustedManifest` repeats the manifest destination check before
the commit move. Empty output directories pass, stale JARs fail with a lazily
computed first path, and owned junction fixtures reject missing-leaf ancestors.
The production-process junction fixture exited `1` before runs/manifest and
left the external sentinel byte-identical.

`-PublicationHarness` is gated by `RCT_I6_PUBLICATION_HARNESS=1` and uses the
actual producer initialization, publication helper, top-level handler and
cleanup. Five child processes covered temporary write/read, hash, report-write
and rename faults. Every child exited `1`, retained the injected error in raw
stderr and FAIL report, published no manifest and removed its owned temporary;
the separate post-commit case kept PASS authoritative. H42's offline npm setup
is recorded at `.agent/orchestration-v2/evidence/h42-*`; this repair did not
install dependencies or run product build/runtime.

FIX4 sources are producer `DB8A4B84E4D042F72F5E0912C2F270CA063AB4735228CB9299A45748DFAC7A83`
and pure checker `7E00D3E4B04E759E17A5851EB243FF7FB24ABBB95756251F87CB2984D7D9387D`.
Final raw logs, exit codes, scope diff and limitations are recorded in
`evidence.md` and `checks.json`; this packet is ready for root's fresh full Sol
review and conditional release.

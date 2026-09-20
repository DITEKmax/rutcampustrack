# Dependencies and compatibility — staged independent review

07.09.2026. S3 baseline with S2 check reconciliation. Fresh Sol high reviewer, fork none, read-only, no children. Not dispatched until writer handoff and root combined freeze appendix.

## Goal

Review the combined dependency upgrade/configuration and compatibility-check repair against its actual security and runtime evidence. This gate does not accept unrelated student features.

## Context/evidence

Read dependency-security-implementation-packet.md, dependency-check-reconciliation-packet.md including its actual PushService extension, dependency-checks-root-findings.md and dependency-snapshot-root-comparison.json. Base d3c31acb8cce53791a4981e5858a37d44fdc9a0e. Imported26 source is frozen in dependency-review-source/manifest.json, SHA B4F2D0D344C0CBA851D4EEF6A2B1A36139CD7D63A5E0A99A11DD03B936EAF1EB. Root will attach the final combined source manifest, stable diff, check exits and scanner artifact hashes. A pause checkpoint is historical, not the final acceptance record.

## Relevant scope

Read all final changed dependency/catalog/build/config paths and narrow test/snapshot/generated changes in the combined manifest. Open critical original code yourself: Gateway configuration consumers, production web-push send path, renderer/runner, Academic homework producer/outbox, Java DTO constraints and six OpenAPI snapshot helpers. Distinguish imported26 from later reconciliation paths. No product/test/docs/evidence writes by reviewer; return findings for root to save unchanged.

## Required behavior

Verify resolved dependency direction and Springdoc compatibility preserve service behavior; Gateway namespace relocation must actually bind equivalent routes/properties, not merely preserve trimmed text. Version selection must not bypass the HIGH/CRITICAL gate. JaCoCo generated-class exclusions must derive from actual source provenance and retain handwritten gRPC implementations with unchanged floors. Tests must exercise converter outputs/error/cleanup and actual PushService encryption/signature/loopback HTTP execution. Homework event tests must validate real producer serialization/outbox against both schemas, not hand-built payloads. Dynamic gRPC ports must preserve signed identity boundaries. Snapshot regeneration must reflect unchanged Java contracts and only accepted semantic deltas.

## Constraints

No dependency repinning, suppressions, skipped mandatory tests, lowered coverage, secret output, product edits or production actions. No scope expansion to XFF, token purpose, Requests or profile. Those independently confirmed findings remain open and do not become upgrade regressions without evidence. Review uses the frozen final source and explicit artifacts, not author reasoning or full transcript.

## Existing patterns

Java-first OpenAPI, generated TypeScript, Gradle check/integrationTest/JaCoCo, eight executable JAR scan and task-owned Testcontainers/loopback resources. Sandbox Docker/compiler access failures require the same bounded command through normal escalation, not source workarounds; root confirmed Docker availability with an escalated read-only ps. Preserve original failed runs as historical evidence.

## Acceptance criteria

PASS requires stable combined source, clean bounded diff, applicable checks and meaningful compatibility tests, no unresolved critical findings, and security coverage of every rebuilt executable artifact. The prior corrected scan had 8 JAR, 1141 package records, 0 HIGH/CRITICAL and 57 MEDIUM records; the earlier empty scan is invalid. Check actual final JAR/report/database provenance and package coverage rather than trusting a zero summary. Distinguish missing mandatory evidence from a code defect. Return severity/file:line/evidence/impact/reproduction for actionable findings.

## Verification

Read exact commands, exit codes, revisions, environments and artifacts. Independently inspect actual report counters, generated-source exclusion set, contract deltas and scan coverage. Run only bounded rechecks justified by uncertainty and record their limits; do not overwrite author evidence. Rehash final source after review. Full backend check and no-update snapshot checks must have completed exit codes; update-mode output alone is insufficient. No full PWA/TMA security or end-to-end claim.

## Do not

No repair authoring, children, reused stale source, blanket package/generated exclusions, zero-package security PASS, declaration of zero vulnerabilities overall from a HIGH/CRITICAL gate, or acceptance of unrelated application behavior.

DISPATCH FREEZE07.09.2026 20:42UTC: writer FINAL handoff and stopped. Root combined source snapshot is C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/dependency-combined-review-source/manifest.json, SHA3E795CB3F0CB8E95944BCBF1DC623E197554FAC24C5F5D2F5BB65ADFDDEF1D6A. Contains45 union source paths (26 imported plus20 explicitly listed reconciliation, one shared root build script); some listed helpers/snapshots have no content delta. Root validated36 available prior/author hashes plus all45 source/copy pairs, zero mismatches. Nine author-listed helpers lacked per-file hashes; root independently froze them after writer completion. Initial root script had an input-manifest schema error and validated only11 prior hashes; initial diagnostic manifest is preserved, corrected terminating validation.json supersedes it. Do not treat the initial script as a complete verification.

Stable checkout: C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/dependency-checks. Author evidence is copied under root snapshot author-evidence; existing12 XML reports copied with hashes under author-runtime-reports before root test rerun. Some focused report files had already been replaced by later Gradle runs; root full check below will produce complete fresh reports. Review actual stable source against d3c31, not just owner classifications or the count45.

MANDATORY CHECK STILL RUNNING: writer summary called broad root check out of scope, but the frozen root contract required a full applicable backend check. That requirement is NOT waived. Root now runs `.\gradlew.bat check --no-parallel --console=plain` on this isolated checkout, exec session84743, output/result in root .agent/student-role-02/dependency-root-check/{gradle-check.log,result.json}. No UPDATE flag. Root owns this verification and will send actual exit/limitations. You may inspect stable source and existing evidence concurrently, but cannot accept the full dependency gate until the required result arrives. Do not launch conflicting Gradle/test tasks in this checkout while root command runs. Report source findings promptly so bounded next work can be arranged.

Bounded reported checks: six no-update snapshot suites9tests0failures/errors/skipped; generation/check0; shared event whitelist50tests and realproducer2tests0; dynamicBFF19tests0; renderer10tests/75.19%line/75%class/unchangedfloor0; actual PushService loopback201/signature/ciphertext assertions0; diffcheck0. Security artifact remains the original immutable eight-JAR handoff; validate exact relation to current production/dependency source and any rebuilt artifacts, do not infer a new rescan occurred. Root reopened changed JaCoCo source-provenance implementation and real push test before dispatch. Full-role and separate security findings remain open.

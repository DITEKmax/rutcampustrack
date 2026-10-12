# Independent AR-C01/AR-P01 correction recheck

**CHANGES_REQUESTED_SOURCE_OWNERSHIP** for new bounded-child.py SHA256 `7a5420cac523e3a93f671a1ae4839acdbfbb263f7083c61cc81653af5e353a0a`. Original zombie qualification/reaping controls and build-JDK wording are corrected; one new P2 process-ownership source counterexample prevents supervisor approval for future phases. This is not a product/build artifact defect or an actual wrongful signal. No artifact rebuild is required.

Requested existing Sol6.1/high, actual metadata UNKNOWN. Root published731/source producer908/baseline3908. FULL current task/state restored; unchanged contract/decisions/questions full bytes equal the previously fully read versions. Own new scratch only. No control children, JVM/Gradle/npm/Docker/network/service/browser/staging/extraction/artifact mutation or shared/source writes. Only read-only AST/receipt/hash/proc metadata audits were performed.

## COWN01/P2 — numeric group/session bypasses recorded PID identity

`/workspace/scratch/rct-validation/current_artifact_corrections/bounded-child.py:44` admits every record with `pgrp == group && session == group`, even when that PID is already known with a different start_tick. `refresh` then stores the admitted record's current start_tick. The later pidfd check protects the current observed identity from changing during signalling; it cannot repair a wrong ownership decision.

A pure exact-AST source counterexample, with no process creation or signalling:

| Input | Value |
| --- | --- |
| supervisor parent |200|
| original launch group/PID |100|
| recorded known identity |PID100/start_tick10|
| later unrelated record |PID100/start_tick20, PPID999, PGRP100, SID100|
| expected owned result |empty|
| actual owned result |PID100/start_tick20 selected|

An unrelated process that obtains the reused numeric leader PID/session is therefore selected despite its mismatched recorded identity and foreign ancestry. signal_records can then open a pidfd for that current unrelated identity and pass its own start-tick comparison. Actual PID-wrap/reuse occurrence and actual foreign signal are **NOT_RUN/NOT_ESTABLISHED**; the source predicate counterexample is concrete. `/workspace/scratch/rct-validation/current_artifact_correction_review/ownership-reuse-counterexample.json` records exact source hash/function/inputs/results.

Minimum correction: establish ownership through fresh invocation ancestry/adoption or an exact recorded PID/start-tick identity; numeric group/session may corroborate but must not independently admit a mismatched reused launch identity. Do not overwrite recorded identity merely because an unfenced numeric group matched. Preserve escaped-session descendant handling through the process-local subreaper. Add this relevant source counterexample to the controls and retain normal/adopted-Z/deadline/escaped-session/two-signal/reporting-error checks at the final exact helper bytes. No real PID-wrap storm or risky foreign signalling is needed. Reviewer does not patch author source.

## Corrected original findings and sufficient preserved evidence

**AR-C01 original scope addressed, with honest historical limitation.** New helper enables PR_SET_CHILD_SUBREAPER only for its own foreground process, checks no preexisting direct child, captures PID/start-tick metadata, includes Z, reaps direct child only via Popen and adopted children via exact waitpid, uses pidfds, bounds TERM/KILL settlement and returns phase_success=false for interruption/deadline/reporting failure. Repeated SIGINT/SIGTERM only record stop requests; reporting exceptions are caught while settlement continues. Unsettled outcomes remain false. CLI exits2 and starts no dependent child. Explicit string environment map is required; no parent os.environ inheritance occurs in the new helper/controls. Existing future driver/resource/host environment/artifact/TLS guards and separate runtime grant remain required; original build runner must not resume.

Independently opened full actual helper/controller/fixture/signal sources and preserved safe actual receipts, not just HANDOFF. All23 author safe payloads and exact root original ZIP members match manifest SHA256 `486729fbf44eb6770a7504686831f614e15a2d949255a932c2b830b0da18a0a8`; AST parses. Final actual control input hashes equal current final sources. Native/observable cases:

- Exact old group_members AST returned[] while an actual new adopted child was Z; the controller then exact-waitpid reaped that new child. This is meaningful sensitivity to the old false-empty rule, not a mock-only observation.
- Normal: direct native0 and adopted native0; settled=true/phase_success=true; recorded identities absent.
- Deadline: escaped-SID TERM-ignoring child tree, direct/adopted native−9, BOUNDED_DEADLINE; settled=true/phase_success=false; identities absent.
- Two real signals: SIGINT during RUNNING, SIGTERM after SETTLING_TERM; signal_count2, STOP_REQUEST, direct/adopted−9, honest false phase success and no recorded identities remain.
- During-settlement callback TypeError: actual reaping completes, error type retained, phase_success=false; identities absent. Initial failing callback control is preserved separately; no retroactive successful run is invented.
- env=None rejected before launch, own child-before/after empty; standalone false grant native2/no runtime child.

These controls do not exercise the new numeric-group PID-reuse source counterexample. They are sufficient for their exact Z/reaping/signal/callback/env rules and need no additional unrelated app or npm run.

Old PID201385/PID201430 are still Z/PPID1 in the observed original groups; neither author nor reviewer reaped/reparented/signalled them. The old build remains LIVE_ONLY_EMPTY, not ALL_OWNED_REAPED. Current artifact native0/source identity remains valid with that qualification.

**AR-P01 CLOSED.** Canonical run-gradle.py hash and JAVA_HOME selection, public JDK release fields and current executable hash were independently verified. Actual build provenance is Eclipse Adoptium Temurin21.0.12.1+1/LinuxGNU; Debian13 is host OS. Current binary identity observation is not retroactive historical build-time measurement. Future host runtime executable/vendor/version/hash remains UNKNOWN/NOT_RUN. Production TemurinAlpine/container/isolation remains separate. Original wording/receipts were preserved, corrected by a new explicit qualification.

## Preservation and limits

Independent fresh streaming identity checks establish all108 original safe files across24+29+6+13+36, all7350 artifact-WT source SHA/bytes/modes,8272 installed dependencies SHA/bytes/modes/symlink targets/full pathset, current8+old8 JAR hashes/modes,22 Vue output hashes/bytes/modes. Prior independent review23 payload hashes also remain unchanged. No private raw logs/real credential/env/key bodies were read or copied. Current control recorded PID/start-tick identities were checked absent without signalling. Old known Z entries remain observed, never counted as current successful reaping.

Original source/capacity/build seals are immutable; no dedup/extraction/keys/app/client runtime grant or measured capacity gain follows. Existing stage requirement remains NEW public files644/dirs755, exact bytes/pathset and preserved original600 modes, under a separate grant. Future actual app/native/host TLS/readability/cleanup/client matrix acceptance stays pending.

Artifacts: `author23-seal-and-root-identity.json`, `actual-stdlib-outcome-audit.json`, `independent-preservation.json`, `prior23-review-preserved.json`, `provenance-correction-audit.json`, `ownership-reuse-counterexample.json`, own CURRENT/attempts and review audit source. These are source/receipt/proc checks, not new runtime control execution. Overall recheck awaits the exact ownership correction/final seal and bounded relevant control evidence; independent other task scopes remain ready.

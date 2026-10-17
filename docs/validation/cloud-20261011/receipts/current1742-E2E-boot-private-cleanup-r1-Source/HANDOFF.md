Goal: close BOOT-PRIVATE-CLEANUP-METADATA-DRIFT before any actual job-log read.

Context/evidence: original source458a/ec6a privacy review ec89afef8a416a7f5c7e0f213ddd71378297617ba0249666913efc16ed00ffdc contains exactly one P2. Original dev/inode-only finally cleanup removed same-inode UID/mode/nlink drift despite denying the read. No actual logs/native action occurred.

Relevant scope: NEW sibling only. Original15 source files/manifest/archive retained byte-identical. capture-job-private.py copied byte-identical; only parser sys import + consume_private final metadata revalidation/primary error preservation changed. Product/JARs/stand/data/keys/volumes untouched.

Required behavior: before unlink, nofollow stat must match original dev/inode and still be regular UID1000/mode600/nlink1. Drift/ambiguity retains leaf and fails closed for explicit later reconciliation. If read/parse already failed, preserve that primary exception and attach only fixed public reconciliation note. Normal own capture and own malformed parse cleanup stay available. Replacement/foreign/alias is never removed.

Constraints: original caps, clocks, message privacy, normal approval, b78 and exact GET unchanged. This is SOURCE_ONLY, with no actual grant/API/log/native child. Directory FD validation/sole writer/same-UID limits remain original qualified boundaries; no isolation upgrade claimed.

Existing patterns: original /workspace/scratch/rct-validation/current1742_e2e_boot_diagnostic_source/HANDOFF.md and launch-schema.safe.json remain the authoritative ordinary b78 flow, with effective capture/parser paths changed to THIS sibling. Capture source/argv endpoint unchanged. Grant template points this own private directory identity and stays authorized=false; Root must bind this new manifest/recheck before issuing a fresh grant.

Acceptance criteria: same-inode unsafe metadata not unlinked; primary exception retained; successful own read and malformed own cleanup preserved; original algorithms/caps unchanged. Timestamp-only binding.actual.safe.json joins Root authenticated metadata60fed6061bfda3d14e63df1dd7dd7ccd709fd0bc7659bcced77d202105fab9ed: exact job/run/head/attempt/step9,11:04:31–11:05:59Z. It is metadata evidence, not actual log/cause proof.

Verification: 8 targeted full selected old/new consume_private AST RAM cases PASS, including original 3 failures, cleanup-only drift and primary-read-error preservation. Original51 groups reused, not rerun. Original15 hashes checked unchanged; other parser AST and capture bytes identical. Await same reviewer narrow recheck. Actual cause/checkout UNKNOWN; smoke/FIRST3 NOT_RUN.

Do not: API/log/capture/native/startup/rerun/product writes, old grant reuse, automatic drift cleanup/reconciliation, raw exception/note/stack export, B013 action, source architecture or cap change.

Effective future capture argv under original b78 Root wrapper:

`/usr/bin/python3 /workspace/scratch/rct-validation/current1742_e2e_boot_diagnostic_source_r1/capture-job-private.py --grant /workspace/scratch/rct-validation/current1742_e2e_boot_diagnostic_source_r1/private/read-grant.json`

After successful settled capture, load this sibling's corrected closed-boot-parser.py and consume_private against this exact own directory FD using binding.actual.safe.json. Never run capture directly without original b78 resource/status wrapper. Future Root receipt reports only closed fixed fields; cleanup drift is FAILED/RECONCILIATION_REQUIRED, not private-file-absent.

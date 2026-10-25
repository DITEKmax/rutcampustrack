# TS diagnostic producer/reporter — coherent independent review

**CHANGES REQUIRED: one P2.** Reviewed only commit `c1f2d943a551df51670b52c30edd6f45fcba0080` (11 files), with separate shared18-case catalog context. No source edits or author tests were written by reviewer. Existing6-row/20-negative controls and compiler evidence reused. One additional Node-only finite counterexample was justified by a concrete uncovered association risk; no browser/API/Docker/DB/Auth/build/full E2E/owner-local/workflow execution.

## Confirmed findings batch

**P2 — actual throwable provenance is replaced by message/stack equality, permitting a different raw Error to acquire the HTTP event.** `diagnostic-events.ts:154–156` records pending HTTP provenance as SHA256 of the framework serialization's `{message,stack}`. `:216–217` later chooses the event using only that digest. There is no native-object identity brand in this path.

Concrete fresh counterexample: create one branded `original = datasetHttpFailure('POST','/api/academic/homeworks',503)`, create a different `foreign = new Error(original.message)`, copy only `foreign.stack = original.stack`, and throw foreign. No HMAC key/proof is read or forged. The reporter accepts DATASET_HTTP / DATASET_HOMEWORK_CREATE_HTTP /503 for this other actual throwable and sets unsafeArtifact=false. The original Error was never thrown. This violates the agreed actual-error association even though the original test still fails; an unrelated failure can falsely trigger the specific HTTP diagnostic stage.

Repro retained outside source in `exact-clone-repro/clone.spec.ts`, its config, `output-nodepath.log`, and `artifacts/safe-results.json` beside this report. The fresh Node-only runner has one case/one worker/retries0 and no repository config/globalSetup/page/API. First invocation could not find a worktree node_modules path and never loaded tests; the corrected invocation uses existing root dependencies through NODE_PATH and produced the counterexample. No unchanged author control was rerun.

The existing foreign-error controls change the stack creation site, so their different fingerprint does not cover this case. The valid-proof-copy control also does not cover it: extra fields are dropped by the framework serializer while copied message+stack remain. Correction must retain provenance of the actual native throwable reaching framework serialization/capture, not infer identity solely from equal raw or sanitized bytes. Keep the closed event/HMAC/context/privacy bounds and original failing result; no need to broaden into a general framework or rerun product scenarios.

## Reviewed behavior retained

- onBegin snapshots case registration before worker annotations are replaced. Known catalog registration is distinct from untrusted result annotations; duplicate/unknown registration marks the artifact unsafe. HMAC binding includes case/testId/project/retry and a per-run random key.
- Public diagnostic events contain exactly five closed fields with finite catalog combinations. Proofs/keys/signatures/raw exception bodies do not enter public rows. Own-data/plain-object checks reject the finite accessor/nested/private/oversize mutations represented by author controls.
- Signature, actual serialized post-redaction error digest and context checks reject stale runs, foreign IDs/projects/retries, changed error text and changed events. D002 observation remains connected to a branded event and its existing closed observation schema. The finding above is the remaining distinction between equality and actual object provenance.
- Event count ≤8 and per-error proof count≤2; unknown proof/schema/extra fields fail safe. Invalid diagnostics force unsafeArtifact/failure, not product success. Original fail/nonzero semantics remain in the author integration evidence.
- Specs change only semantic registrations plus the existing observed503 throw's producer wrapper; assertions, permissions, CAS/cleanup/business behavior are unchanged. File/line remain optional report diagnostics, not new event identities. No new source pins, line maps or human-message result oracle were added to the new event route.
- Author actual6-row evidence shows two native HTTP events, three foreign/plain/replayed raw errors as FRAMEWORK0, and one passing verifier control. Artifact scan found no canary/proof/key/signature data. Targeted helper/reporter tsc PASS is reused. Full touched-source compile remains EXIT2 at the same pre-existing semester TS2322 union mismatch (line4904→4905 from the import); it is not called a full PASS.

## Cross-boundary followup

The same6-row report still contains legacy `diagnostics` and `reportedFrameworkError`503 hints on the three new FRAMEWORK0 rows. The shared Python consumer must admit authoritative semantics only from new closed diagnosticEvents plus registered case/context; fallback to those legacy hints/file/line/text would preserve the old forgery path. Primary Python author was notified; that implementation is still WIP and is not assessed here as a final defect.

After the same writer's provenance correction, independently recheck only the changed association path and the new exact-clone regression/control. Reuse unchanged registration/schema/privacy/compiler evidence. The final Python/cross-boundary coherent packet remains a separate review extension by this reviewer.

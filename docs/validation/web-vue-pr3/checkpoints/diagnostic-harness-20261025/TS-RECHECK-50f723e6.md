# Actual throwable association — targeted recheck

**PASS. Previous P2 CLOSED; blocking findings0 in the reviewed TS scope.**

Reviewed correction `50f723e6bb28707ab2f77502868c013d3c503672` atop c1; only helper/control files changed. Other c1 registration/HMAC/schema/privacy/spec/compiler conclusions are reused. Reviewer did not repeat author controls or run API/browser/Auth/Docker/DB/build/full E2E/CI.

`diagnosticHttpFailure()` now brands the actual raw Error object in a WeakMap, with the existing finite eight-native-event cap. The narrow wrapper delegates the original installed TestInfo `_failWithError(raw)` with the same receiver/argument and keeps its result. Only a branded raw object whose actual framework call appends exactly one TestError can associate that exact appended serialized object with the native event. `captureDiagnosticFailure()` looks up this object, not message/stack equality. Digests remain integrity/seal checks and no longer authorize HTTP provenance. A different Error with copied original message+stack therefore receives FRAMEWORK0.

Read the installed framework's boundary signature/append behavior; the current narrow call/delegation matches it. Missing boundary is failclosed rather than guessing provenance. The former private vendor serializeError import was removed; no source lines/strings/hashes become scenario or failure identity.

The committed Node-only exact-clone control creates a branded original but never throws/reports it, copies its message+stack onto another raw Error and throws that object. Author actual artifact `/tmp/rct-diagnostic-controls-Xb1KDB/artifacts/safe-results.json` has seven rows: native body and worker-fixture failures retain HTTP503; plain known wording, different throwable, exact clone and replayed raw proof each yield FRAMEWORK0; one finite verifier test passes. Overall status failed, unsafeArtifact=false and runnerErrors0. The deliberate child exit1, unchanged20 finite negatives and key/proof/canary artifact absence are asserted by the actual committed control. This directly covers the previously confirmed risk; no reviewer rerun was needed.

Targeted helper/privacy/reporter tsc PASS evidence `producer-controls/raw-identity-tsc.txt` is reused; the separately recorded full touched-source baseline TS2322 limitation remains unchanged. No claim of full compile/product/runtime PASS is added.

The wrapper adds no raw data to public output. HMAC binding, preregistered case snapshot, test/project/retry/run checks, post-redaction seal, repeated sanitizer/late-field handling and proof stripping remain unchanged. Original failing result/cleanup semantics are not converted to success.

The coherent shared Python/CI/local consumer and finite producer→validator integration remain the next independent review extension. They must admit only the new closed events and must not promote legacy diagnostic503 hints on FRAMEWORK0 rows. No broader unchanged TS re-review or product campaign is required.

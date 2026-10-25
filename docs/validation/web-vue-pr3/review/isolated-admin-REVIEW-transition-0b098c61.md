# Independent source-transition review — 0b098c61

Exact new source package: `0b098c612a8b0726774bd909259d98a95d4305ca207ae27949b851d04a4e959c`.
Exact PREDECESSOR-727.json: `0af9da0913c22ed1b95194f36f4aafaa8e6dae6f0abfe8430a654d6ad970ae8d`.
Path: `/workspace/rct-pr3-runtime/admin-isolated-20261024`.

**Verdict: PASS for this narrow source correction and the proposed separately admitted read-only qualification.** No blocking source finding. This does not establish that qualification or apps-up executed/passed. Root must issue the exact current-source, predecessor-bound qualification phase GO; only after an actual successful new read-only receipt may a separate apps-only GO bind its SHA. Cleanup PASS is a separate report and did not wait for this review.

## Entrypoint correction

The previous verifier used `s.get('entrypoint', actualEntryPoint)`, which returns None when normalized Compose explicitly contains entrypoint:null. Official infrastructure images legitimately inherit their nonnull image default, so the verifier rejected healthy resources even though Compose itself returned0. The correction in `startup.verify_own` handles only that case: if the configured entrypoint is None, inspect the exact image ID from image-pins.json and use its Config.Entrypoint; otherwise retain exact comparison with the explicit configured entrypoint.

This is not a fallback to whatever the container happens to run. Actual image ID is checked against the pin first, and the inherited expected entrypoint is read from that pinned image. Explicit Java app/educational classpath entries remain exact. Wrong inherited defaults, explicit commands, images and all other existing env/mount/label/network/cap/health checks still fail. The timeout/session supervision from727 is retained with its previously reported bounded termination/readback qualification.

## Predecessor/materialization continuity

PREDECESSOR pins the unmodified public Compose/init/materialize/artifact/image/source/baseline files, original materialized receipt68a884937562bb81045234f313c5dfba5dd91b025b1d528d4e5c1f091fcf8972, original raw infra intent/config/failed receipt27bba2e275f3af93e168802941f64bf5affedc5a6f57b9dcea7a4b00c852a522 and public partial checkpoint. It also binds the input-file-hash index,49 expected own credential/TLS/JWT/env entries, external fixture/fixed time/marker, actual1fe typed-control receipt723f7987 and Academic immutable sourcea4331786.

The proposed qualification checks the Root GO's exact predecessor SHA, every pinned fixed hash and every listed own private input hash, then verifies the materialized receipt still names727 and the raw original receipt still says readyfalse / phaseNativeExit1 / compose nativeExit0. It does not relabel, overwrite or remove that failed evidence. Academic artifact SHA and complete exploded ZIP file set/member hashes must still match. Original materialization is preserved; no rekey, new materialize, copied main data or credentials appear.

The private input hash check is authorized own-material validation by the future qualified executor, not permission for the reviewer to inspect key values. The reviewer read only public hash metadata and did not read/hash any actual private key/env/config/receipt file.

## Read-only qualification and later apps admission

`qualify_infra.qualify()` requires its exact `qualify-existing-infra` phase admission. Its call path performs Docker read/inspect and Compose config, owned file/hash/ZIP checks and a NEW exclusive0600 qualification receipt. There is no Compose up, service/database command, stop/reset/recovery, new namespace or raw-receipt write. Existing main13/clock5 must match their baseline and be healthy both before and after the strict own-resource check.

The actual own infra projection must equal the pinned original partial checkpoint (same three CIDs/images/mounts). `verify_own(... INFRA, healthy=True)` then checks exactly the three healthy services against the corrected pinned image defaults and all remaining authoritative predicates. Only after success is qualified-existing-infra.json created, with explicit readOnly/native0/readytrue/current package/predecessor hashes, original727 rawFAIL facts retained, and upReplayed:false. It cannot be overwritten silently.

For the materialized predecessor path, `startup.run` permits only apps-up, never another infra attempt. `accepted_for_apps` rechecks predecessor continuity and requires the new apps GO's exact qualification-receipt SHA, current source package/predecessor, readOnly:true, ready:true and nativeExit0. The subsequent live own projection must still equal the qualified receipt. It never treats the original raw readyfalse receipt as success. Fresh infra/materialize paths remain blocked by existing-resource/fresh-state checks, and exclusive intents/no-recreate/no-pull/no-retry rules remain unchanged. Actual apps still require the>=3GiB before / >=2GiB after gates; this transition performs no implicit artifact cleanup or lowers no guard.

## Independent pure evidence

Recomputed the exact0b098c61 public package digest without traversing private/runtime/evidence directories for content. Recomputed predecessor SHA0af9da09; public fixed file hashes match, relative manifest paths contain no absolute/parent escape, AST parsing of ops/startup/materialize/qualify_infra passed. Verified the public manifest references exact68a materialization and27b raw failure and covers49 own input entries, without opening those files. Read the public ENTRYPOINT-TRANSITION-PROPOSAL and original INFRA-PARTIAL-CHECKPOINT: original Compose0/phase1/readyfalse and same3 reported healthy remain explicit, not rewritten.

No actual Docker socket/config/inspect/up, qualification helper, private-file read, Gradle, browser, HTTP, DB, signal, materialize or cleanup action was executed by this reviewer. Only this immutable report was written. Existing materialized1fe typed-control/TLS/source evidence is reused as author evidence bound by predecessor hashes; no independent rerun or real app/current-password/browser result is claimed.

Requested assignment remains GPT-6.1 Sol/high; no runtime model telemetry or fallback claim is made.

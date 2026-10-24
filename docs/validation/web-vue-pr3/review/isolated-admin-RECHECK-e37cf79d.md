# Independent narrow recheck — e37cf79d

Frozen package: `e37cf79db16c239c4c49db3ad11b92f8ac10131b15040f41bfc38ef039807cf1` at `/workspace/rct-pr3-runtime/admin-isolated-20261024`.

**Materialize-only verdict: PASS for the scoped preparation design.** Previous grouped24ec corrections are accepted. Separate exact materialize phase GO, serial resource/disk preflight and normal admission remain required. No actual materialization or startup readiness is established. A future launcher is not a prerequisite for materialize-only acceptance.

**Infra/apps launcher verdict: CHANGES REQUIRED — one P2 bounded-execution gap.** No other blocking source finding in this delta. Materialized actual-classpath typed controls and exact config/readiness review remain later gates, as acknowledged in the packet.

## P2 — the claimed finite phase bound starts after an unbounded blocking Compose operation

Locations: `startup.py:83–86`, `ops.py:10`, `ops.py:73–74`; PLAN.md stage5.

`compose('up', ...)` executes `subprocess.run(..., capture_output=True, text=True)` without timeout or concurrent progress. `startup.run` sets its360s deadline only after that call returns. In apps-up, Compose itself waits on `service_healthy` dependencies (Auth/Academic/Gateway chain), so the entire dependency startup wait occurs before this deadline and before the30s progress loop. A blocked daemon/CLI can also wait indefinitely. Blocking `docker()` check_output calls in later inspection/readback likewise have no timeout and can outlive the readiness-loop deadline.

Pure AST/source proof confirmed exactly one compose subprocess.run with no timeout and the compose-up call at83 preceding the deadline assignment at86. This is a source/runtime-control defect, not a claim that any daemon or container actually hung: no startup command was executed.

Correct before admitting infra/apps: supervise the single Compose-up command from an explicit phase deadline, retain30s progress while it is running, and bound individual daemon/config/readback calls within declared limits. Timeout must terminate/reap the CLI child only, preserve partial owned resources, record failure/native/timeout output privately, and perform bounded main/clock readback without retries, new namespace, volume reset, cleanup or healthcheck relaxation. Do not solve it by merely starting another360s clock after the same blocking call.

This finding does not block generation of fresh local material. A launcher correction changes the sealed package digest, so refreeze/reissue the exact relevant GO before use; do not silently reuse an old receipt/GO after changing the package.

## Accepted corrections from24ec

1. `materialize.py` now rejects a symlink/nonregular copied public init and fchmods its open FD to0644 before creating runtime material. The public init byte content still exactly equals the repository's canonical public script. Keeping its current pre-materialize mode0600 is expected because no materialize command has run; the previously missing future mode change now exists.
2. `compose_environment` removes all15 names referenced by `${...}` in the exact frozen Compose file, plus COMPOSE_FILE/COMPOSE_ENV_FILES/COMPOSE_PROJECT_NAME/COMPOSE_PROFILES. The child passed to subprocess.run uses that sanitized mapping. Independent pure invocation with hostile synthetic values confirmed all15 names/selectors absent, HTTP_PROXY/HTTPS_PROXY/NO_PROXY/NODE_EXTRA_CA_CERTS/DOCKER_CONFIG and a synthetic runtime-auth marker preserved, and the supplied input mapping unchanged. No actual process secrets were read or printed. The author's public-placeholder actual Compose probe native0 is consistent evidence, not an independently repeated Docker call.
3. PLAN now distinguishes Goal authorization from earlier QENV004 narrow checks, retains separate phase/case GO and does not reuse QENV005 main stop/data-copy permission.
4. PLAN now requires actual `ALL_VERSIONS` for whole deletion and the normal upload that can create missing owned inventory. No mistaken WHOLE_PLAN response value or alternate inventory API is required.
5. Gateway artifacts.json/PLAN now explicitly distinguish embedded603 metadata from historical pre-final-commit attribution3c02, retaining the exact accepted593f47 hash. No independently source-matched3c02 metadata claim or unnecessary rebuild is required; prior immutable receipts remain unchanged.

## Accepted new launcher source boundaries

- Only infra-up/apps-up are dispatchable and each checks the exact package/project/scope/expiry Root GO. A completed exact materialized receipt, fixed external fixture hash/instant/marker and private/public file modes are required before the one up command.
- Fresh preflight rejects existing own project containers and own-prefixed volumes/overlapping subnet. Apps-up requires exactly the healthy own infra service set and the prior successful infra receipt with matching CID/image/mount projection. Unknown, duplicate, missing or extra own service sets are rejected rather than reset or guessed.
- Private config and phase intent are exclusive0600 writes before the single `up -d --no-recreate --pull never` call. There is no retry/reset/down/cleanup loop. Failures retain private output/receipt and partial resources.
- Source checks effective labels/project/clock role, image IDs, memory/CPU caps, privilege/network attachments, entrypoints, unchanged healthcheck tests/timings/retries, declared environment values and exact bind/volume/secret mount sets. Own named volumes must carry the project prefix; only Academic may mount the educational clock. The raw120s health timing is converted explicitly rather than mis-parsing Compose's2m0s rendering.
- After apps-up, every prior own infra CID and projection must remain. Main13/clock5 projection and healthy-state readback is in finally, with no stop/restart/recreate action. The unbounded subprocess issue above must be fixed so this finally/readiness sequence cannot be postponed indefinitely.
- Templates remain seven private services with3392MiB/1.65CPU caps and no new image build/pull, external volume/network, host networking or main/clock bind. Initial/infra4GiB, apps3GiB and post-phase2GiB disk guards are explicit; full-lane caps plus1.5GiB reserve remain conservative. Actual fresh preflight and exclusive heavy lease still control each phase, and failure never authorizes main/clock changes.

These are source conclusions, not actual image/CID/config/startup assertions. New materialized config must still be independently qualified before infra/apps GO; actual1fe production-classpath typed/mixed clock controls are still pending materialize. Accepted prior Auth/TLS/bootstrap/EducationalTime boundary review remains unchanged. The reported offline source fixture/control build44s/four tests PASS and unchanged2b8727 fixture are reused; they do not substitute for that actual-classpath control or real current-password/browser acceptance.

## Evidence and limits

Independently recomputed exact e37 package hash; all three Python ASTs parse. Pure hostile-synthetic child-env isolation and unchanged public-init byte checks passed. Read the author's recheck-source-and-hostile-env.json, revised-freeze.json and fixture-control-retry-success.json. Private/runtime directories were absent when checked. No Docker socket/config/up, Gradle, browser, HTTP/API, DB, materialize/key generation or private-key/config read was performed by this reviewer. No lane/product source was changed; only this immutable report was written.

Explicit assignment remains GPT-6.1 Sol/high; no runtime model telemetry or fallback claim is made.

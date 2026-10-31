# Independent timeout-correction recheck — 727506cd

Exact frozen package: `727506cd8c60ef76e1c789e8d0216f6ca3fc3221403c43d7081ed0b723c991ab`, `/workspace/rct-pr3-runtime/admin-isolated-20261024`.

**Verdict: PASS for the scoped source correction.** The e37 P2 unbounded Compose/dependency-wait finding is closed. Materialize-only PASS and the prior accepted isolation/source boundaries remain valid for this exact revised package. No blocking source finding remains in this review scope. This is not phase permission or startup/runtime acceptance: new exact Root phase GO, live resource preflight, materialized actual-classpath controls/config review and subsequent actual readiness remain required.

## P2 correction accepted

- `startup.py:63` creates a single deadline before preflight and before any Compose-up call. `startup.py:84` passes that same deadline into up; the readiness loop no longer resets it after dependencies start.
- `ops.py:75` bounds ordinary Compose config calls to30s; `docker()` bounds read/inspect calls to30s. The previous unbounded subprocess operations are removed.
- `ops.py:77–95` supervises the single up CLI with Popen in a new session, private captured stdout/stderr and communicate intervals capped at30s/remaining deadline. Safe progress contains elapsed time only, not private output.
- On deadline, the code verifies the own child process-group identity equals its newly created child PID, then signals only that CLI/plugin session group: TERM,5s grace, then KILL with another bounded5s reap. It does not issue Docker stop/rm/down/reset or signal a container/main/clock process. Unexpected group identity fails closed without a foreign signal.
- The outcome records actual child native exit independently from `composeTimedOut`. A timed-out CLI cannot produce readiness PASS even if its exit happens to be0; startup raises and retains partial resources and failure receipt, with no retry/new namespace or volume reset. Current health/pin/source/own infra CID checks and main/clock finally readback remain intact.

Timing qualification:360s is the shared admission/up/readiness attempt deadline. Explicit CLI termination/reap grace and bounded readback/finally work can extend wall-clock process completion. Individual preflight/readback subprocesses have30s caps; this source is not a promise of a hard process exit at exactly360.000s. Source review establishes finite operations and no post-deadline up/readiness acceptance, not observed real daemon timing. No real timeout or process-signal helper was executed by the reviewer.

## Prior corrections and guards retained

The public init future FD fchmod0644 and unchanged canonical bytes remain; all15 Compose interpolation keys/selectors are removed while managed proxy/CA/DOCKER_CONFIG/runtime inputs are preserved. PLAN uses Goal/separate phase-case GO rather than extending QENV004 or reusing QENV005; correct capability is ALL_VERSIONS. Gateway embedded603 versus historical attributed precommit3c02 remains honestly qualified with exact593 immutable bytes.

Only the explicit own project up command can mutate runtime. Exclusive0600 intent/config/receipt files still block retries; fresh preparation rejects existing own containers/volumes/subnet ambiguity, apps-up requires a successful exact prior infra projection and preserves those CIDs. Label/image/env/mount/network/cap/health checks, own-only volume prefixes, Academic-only EducationalTime fixture, main13/clock5 baseline/health finally checks and disk/reserve guards are unchanged. No existing resource stop/recreate/reset/network attach is introduced by this correction.

## Pure independent evidence

Recomputed package digest exactly727506cd; AST parsing of ops.py/materialize.py/startup.py passed. AST assertions confirmed one deadline assignment before preflight/up, propagation of that identifier into the up call, no readiness-deadline reset, bounded30s Docker reads and explicit start_new_session=True for the supervised CLI. A pure hostile-synthetic compose_environment check confirmed15-key isolation and managed public marker preservation still hold. Read the author's timeout-source-correction.json (AST/config placeholder native0; helper/materialize/up not executed). Private/runtime directories were absent when observed.

No Docker socket/config/up, Gradle, browser, HTTP/API, DB, materialization, key/private config read or real child/signal command was executed. Importing public ops solely to exercise the pure environment mapping used PYTHONDONTWRITEBYTECODE; no lane files changed. Only this immutable report was written. Prior e37/24ec reports remain immutable.

## Still separate runtime gates

Actual1fe production-classpath typed/mixed EducationalTime/security controls require materialization and have not been claimed here. Accepted source-control4tests/unchanged external fixture and prior official production-archive isolation remain source/inspection evidence only. Before infra/apps GO, qualify the generated own config and these controls; then require actual7-service labels/images/CIDs/mounts/caps/health, strict TLS/Auth catalog/bootstrap/live current-password checks and unchanged old18. Browser semester/map actions need separate case GO. No main map deletion, promotion, live data copy, QENV005 reuse, production or CI permission follows from this PASS.

Requested assignment remains GPT-6.1 Sol/high; no runtime model telemetry or silent fallback claim is made.

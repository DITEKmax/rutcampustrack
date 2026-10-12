# Privacy producer counterexample and proposed harness correction

Exact source: eb0e02aaf6c7cfb3b80db2d3a3c81b8a2dd79553. No product, vendor, lock, Root, Angular, old57-worktree or foreign1b4 source changed. Browser/API/network were not started. Original three browser failure causes remain UNKNOWN.

## Actual bounded controls

Grant01 expired without launch. Fresh02 grant ran one failed adapter attempt (session65905, native1, PID316953/birth6728759), then one corrected attempt (session81932, expected SIGTERM/native-15, PID317074/birth6735308). Both settled and reaped every owned process; the Node lease is released. First failure is preserved. Its adapter lacked the actual producer's normalizeScreenshotMode helper; corrected extraction includes that exact unchanged helper. No repeated product test was run.

Actual pinned Playwright1.59.1 SnapshotRecorder/ArtifactsRecorder methods and buildErrorContext executed; actual current sanitizeTestErrors and SafeReporter executed from transpiled exact source. Ten expected observations matched. Context-close/final-teardown each capture a synthetic marker without the supported flag; supported PLAYWRIGHT_NO_COPY_PROMPT prevents both captures; unsupported PW_TEST_DISABLE_ERROR_CONTEXT prevents neither. A late error appended after the sanitizer reaches the actual producer despite the supported flag. Canonical reporter removes the marker only when onTestEnd runs. The last control sent actual SIGTERM after the producer write and before the reporter callback, preserving that counterexample until own payload scrub.

The page adapter and late error ordering are source-derived local controls, not a full actual Playwright worker-fixture-runner execution. Native exit-15 is intentional negative evidence, not acceptance PASS; guard phase_success remains false. No original actual error-context/credential was read or reproduced. Synthetic marker values/raw errors/raw artifacts were never output or archived. Ten own public payloads were scrubbed.

## Minimal maintained correction proposal; not implemented

1. In tests/e2e/fixtures/artifact-privacy.ts extend the public Playwright base with one automatic privacy fixture. Setup calls requirePrivateArtifacts before page/browser/dataset creation. Its teardown finally re-sanitizes errors after test/page/context/request fixtures and before built-in _setupArtifacts writes the error context. Export this protected base for MobileDataset. Keep existing afterEach sanitizer and canonical reporter as additional boundaries. Do not clear errors/status, suppress failures, relax assertions, patch private vendor methods or add a daemon.

2. The only necessary consumer change is tests/e2e/fixtures/mobile-dataset.ts: import the base test from artifact-privacy instead of @playwright/test; retain public types/expect import and all dataset/API logic byte-for-byte. This path is outside the current common privacy/config grant: request exact narrow import ownership extension in Root packet before editing. It applies the automatic boundary to existing8 and NEW14 through their current shared dataset test, with no old spec edits.

3. requirePrivateArtifacts must additionally fail closed unless PLAYWRIGHT_NO_COPY_PROMPT is exactly1, with the existing canonical sole reporter, debug rejection and trace/video/screenshot-off checks preserved. Set this supported flag before workers load in the two explicit real-client configs (tests/e2e/playwright.real-mobile.config.ts and tests/e2e/fixtures/cloud-accepted-playwright.ts). The automatic fixture setup also validates before browser creation; the existing worker dataset guard remains before credentials. Keep default Playwright config unchanged.

4. Update only browser-free privacy control wiring (tests/e2e/artifact-controls/privacy.control.spec.ts, tests/e2e/playwright.artifact-control.config.ts and tests/e2e/check-artifact-privacy.mjs) as necessary to exercise the new base, absent/wrong flag rejection, early and real late fixture failure, and actual interrupted producer before reporter. Root should assign these exact chunks together. Tests use public marker only and assert failure remains failure. No duplicated product rule/unit oracle.

5. SafeReporter needs no relaxation. Optional incremental atomic safe-results checkpoint outside Playwright outputDir helps interrupted case metadata, but is separate from producer privacy and must not be claimed to prevent capture. Defer it unless needed by the three-case observer. The minimal privacy correction does not need this optional chunk.

## Required proof before accepting correction

Use a new small sparse tests/e2e-only worktree from exact EB. Old57 and foreign1b4 remain unchanged. First prove real worker fixture ordering with a browser-free late-teardown marker control: uncorrected red, corrected green before reporter, and interrupted-before-reporter green. Both guarded context-close/final capture paths must remain marker-free. The same late error must still be recorded as failure, with no changed gate/status/assertion. Include missing flag/extra reporter/debug/artifact-on rejection before browser/credentials. One scoped current E2E TypeScript check on affected fixture/config/control paths and pinned formatting check suffice after these meaningful controls; no type/full-suite repetition or actual UI needed. Independent review must read fixture ordering and producer-before-write proof.

## Supervisor/finally limits

The reviewed b78 guard settles/reaps the owned child tree; it does not sanitize arbitrary files. Original run-granted-existing8.py writes terminal metadata after run_owned and has no generic artifact finally scrub. The current control runner scrubs its own PUBLIC payload files after run_owned; it does not enclose every post-run receipt step in an outer finally and cannot guarantee cleanup if its Python owner is SIGKILLed or killed between settlement and scrub. This limitation is preserved, not called a safe producer boundary. A future owned supervisor can add a bounded finally scrub of only exact own unallowlisted artifact files after settlement and fail-closed validation; this remains defense in depth. No finally can guarantee cleanup on SIGKILL/power loss. Therefore prevent private payload content at the producer before writing, rather than rely on later cleanup.

## Next authority

Root correction packet must assign exact paths/chunks and grant source writes plus bounded Node ordering/type checks. No correction is implemented yet. Future real diagnosis selects exactly the first three existing cases, with closed coarse stage/method/path-enum/status metadata; requires separate fresh stand readiness/browser grant after privacy fix/review.

# Remaining Vue browser scenarios + relative-has correction

Frozen paired original commits:
- `bb7074facf4c76c2a6b317da9ef9b983569f6d64`: ONLY the two remaining specs, parent `e7bb0a7fd4d4ebd93be0c0f91bbdbb01dbac7641`.
- `c1ec3de72940e7c6ef243446f8334e3dcbddf03e`: ONLY seven relative-has corrections in the existing route oracle, parent bb7074.

Review the combined THREE-file delta against e7bb. Author WT clean. Existing e7 source-only independent PASS remains historical evidence; the newly found locator P2 below was not covered by its fake finite control and is not a product/readiness-count change. Narrow independent review of this frozen pair is pending.

## Contract assessment before the student intent change

Current WEB uses the existing native confirmation form and multipart POST `/api/v1/student/requests/excuse`. `StudentApiController.submitExcuse:146–150` explicitly returns HTTP200/no-store; successful typed detail is EXCUSE/PENDING. Exactly10MiB is VALID per `StudentRequestService.MAX_ATTACHMENT_BYTES`, not a refusal boundary.

Existing unchanged server coverage, inspected as source (not rerun):
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java:967` `exactAttachmentLimitsPersistBinarySeparatelyAndCancelRetainsIt`: two exact10MiB PDFs, separate binary persistence, cancellation retention.
- Same file `:1008` `attachmentSpoofThirdAndOversizeInputsRejectBeforeAnyWrite`: MIME/signature spoof, third file, maximum+1, and empty ticket/receipt/attachment repositories on refusal.
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseControllerIT.java:143,165,189`: legacy PLAIN POST410 with no new ticket / no changed existing ticket, including headman metadata.
- Existing `tests/e2e/specs/mobile-attendance-requests.spec.ts` contains the genuine current EXCUSE multipart/file/readback flow; its accepted one-pixel PNG bytes were reused for format concordance, not its mobile layout or a new domain matrix.

Exact legacy multipart410 gap: bounded search found no actual with-file HTTP IT assertion. Plain POST tests are NOT represented as multipart coverage. `ExcuseController.createExcuseWithFile:63–65` and `ExcuseService.createExcuseWithFile:165–166` immediately throw the existing retired exception, but implementation source is not a new HTTP test PASS. Root received this distinction and explicitly authorized replacement of the obsolete browser UI410 intent with ONE bounded current WEB EXCUSE flow. No retired endpoint, new API410 assertion, backend/domain test or product change was invented.

## Exact changed inventory and meaningful result

1. `tests/e2e/specs/admin-create-user.spec.ts`
   - Current inline named creation region, native role/group comboboxes and the actual group-options search select this exact dataset group.
   - Require genuine owned POST201 and finished body; canonical user GET must bind the returned ID/login/unique name/Telegram ID and active STUDENT group grant to the new namespace.
   - Compare the ephemeral initial-access receipt against that exact response using boolean secret comparisons; dismiss it before a11y, then require no persisted password output after reload. Registry search still verifies the new owned row; no Angular dialog/mat-row/passive plaintext column.
   - The exact returned credential must admit the created user at `/student/home`; current `/api/auth/session` must identify that same user and active STUDENT group grant. No regeneration, baseline user edit or synthetic grant assertion.
   - Close the additional browser context in finally; normal path logs out only the sessions created by this scenario. Root-labelled disposable dataset retention/disposal policy remains unchanged.
2. `tests/e2e/specs/student-excuse.spec.ts`
   - Retain the existing own one-off occurrence setup in the disposable dataset. Poll the CURRENT authoritative options for that exact lesson's EXCUSE eligibility; require its expected subject/date and nonempty current time identity before UI selection. No old attendance/excuse read dependency.
   - Use the current inline WEB form, local date narrowing, uniquely associated owned checkbox, native OTHER reason, exact marker comment, one valid68-byte PNG preview and native confirmation listing that one lesson.
   - Require real current multipart HTTP200/body completion and typed EXCUSE/PENDING detail, exact owned lesson/comment/reason/file. Canonical own detail, OPEN total+1 and exactly one matching ID must confirm persistence; protected attachment bytes/MIME/size/name/identity must match the selected file.
   - Require the actual owned card/status/comment/lesson/file UI, unchanged a11y threshold and normal own UI logout. No limit/spoof/third-file matrix, repeat POST, legacy endpoint or fake success. Same one existing test case, changed to the accepted current platform contract after root ACK.
3. `tests/e2e/fixtures/web-panel-route.ts`
   - Exactly seven `filter({has: owner.getBy...})` inner locators now use `owner.page().getBy...` (student metric/card; headman queue/table/roster/metric/homework). Their owned OUTER scopes are byte-identical.
   - Actual installed Playwright `client/locator.js:41–45` appends the inner selector verbatim; an owner-prefixed inner starts by searching for the owner's ancestor inside a candidate, so it cannot match. The original dashboard helper already used a page-relative inner correctly.
   - This newly identified P2 was disclosed before any edit outside the two-spec packet; root explicitly assigned the seven-site correction and a bounded local static-DOM probe. Current specs likewise use page-relative inner locators under owned outer candidates.

All existing private fixture/credential/capture guards are preserved through `mobile-dataset`'s existing artifact-privacy wrapper and strict disposable HTTPS loopback8443 admission. The redundant afterEach-only sanitizer was removed; the stronger automatic wrapper still sanitizes failures including teardown and rejects unsafe reporters/captures before credentials/page use. Config, lock, products, generated contracts, actual fixture credential values and grants are unchanged.

## Verification receipts and limits

Scoped strict TS final THREE-file check:
`node /workspace/rutcampustrack/frontends/node_modules/typescript/bin/tsc --noEmit --project /tmp/rct-vue-final-three-tsconfig.json`
Exit0. Existing installed Node/Playwright types and an external temporary config only. Initial two-spec check caught nullable native lesson subject/time fields; the spec now explicitly rejects missing/currently ineligible owned identity before UI actions. Targeted checks reran after that source fix, then after the newly assigned helper change. No broad suite or install.

Existing finite route control ran ONCE after the actual seven-site helper correction:
`NODE_PATH=/workspace/rutcampustrack/tests/e2e/node_modules node tests/e2e/fixtures/web-panel-route.control.mjs`
Exit0: positive19/rejected47, requiredCompanionSettlement/renderedValueSensitivity/pendingLegacyIntent true, vueBrowserProof false. It does NOT prove has-locator semantics; that distinct gap is covered below. Exact comparison confirms only seven inner-locator substitutions, outer scopes unchanged; diff-check PASS.

Existing private source collection (no test execution), cwd tests/e2e:
`NODE_PATH=/workspace/rutcampustrack/tests/e2e/node_modules node /workspace/rutcampustrack/tests/e2e/node_modules/@playwright/test/cli.js test --config playwright.legacy-owned.config.ts admin-create-user.spec.ts student-excuse.spec.ts --list`
Exit0. Safe reporter's historical `real-mobile passed; 0 results; 0 runner errors` is NOT counted as a zero-test PASS. One-off read-only import of the unchanged config separately proved nonempty exact file selection: Chromium2, WebKit2, mobile-chrome1(student-excuse), capture-off options unchanged, executedTests0/browserRun false. This proves collection/selection compatibility, not these scenarios' runtime success.

### Bounded static DOM probe — explicitly NOT Vue/SUT proof

One-off script/result only in `/tmp/rct-static-has-scope-probe.cjs` and `/tmp/rct-static-has-scope-probe.json`. Installed Chromium/Playwright opens `about:blank`, uses setContent with owned and external duplicate candidates, aborts all page network requests, and never loads a URL/Auth/cookies/Vue/SUT. No screenshot, trace or video.

Final result: all seven old owner-prefixed locators0, all seven corrected locators exactly1 with the owned ID; after removing only the owned matching labels while keeping outer candidates and foreign matching content, every corrected locator0. pageURL about:blank, networkRequests0, vueLoaded false, auth false. Context/browser closed and process exit0.

Invocation history is retained honestly: first default-sandbox Chromium launch failed BEFORE a page (crashpad setsockopt EPERM); standard additional network execution permission was needed for Chromium's local IPC, without network navigation or TLS/policy changes. Two subsequent diagnostic executions stopped at a degenerate one-cell/one-row table in the temporary probe (installed Playwright deliberately gives that TH no implicit header role). Only `/tmp` fixture changed to the actual source-shaped multi-column table with a body; the same old/new/foreign assertions then passed in the final execution. One bounded probe was developed; four launch attempts / three static-DOM executions, one complete final PASS. This is neither a SUT browser campaign nor a concealed retry of product behavior.

## Future gate / unfinished work

After independent source recheck and separately authorized disposable fixture/runtime coordination, the unchanged supported private runner can select exactly these two specs:
`npx playwright test --config playwright.legacy-owned.config.ts admin-create-user.spec.ts student-excuse.spec.ts`

The dataset additionally requires its existing explicit synthetic flag, HTTPS loopback8443 and task-labelled disposable stack; it deliberately rejects unrelated local/private stands. ADMIN needs its actual existing users-manage/view permission; no test grants it. Owned occurrence eligibility and native file options must come from the real service. Unknown write results fail for reconciliation; no blind repeat was added.

Neither scenario was executed. Real Vue/HTTP201/EXCUSE/browser success remains pending; server IT references were not run in this packet. Exact legacy multipart410 IT remains a disclosed source-coverage gap for its owner. No root14210/stand operation, backend/domain write, Docker/Gradle/build, workflow/CI dispatch, Angular deletion, production switch or product-count increment occurred.

## Independent PNG finding and correction supersession

Paired review subsequently found the borrowed68-byte PNG had an invalid IDAT CRC despite an inflatable stream. The earlier valid-PNG wording above is superseded by fixture-only correction `eb6826904f10eea706a26fb021f1993bbd665ea2` and `/workspace/rct-pr3-runtime/VUE-PNG-CORRECTION-eb6826904.md`. Only the test literal's CRC field changed; all preview/POST/readback assertions remain. Every chunk CRC/signature/IHDR/IEND/full-inflate check now passes without browser/SUT execution. Independent review found no additional confirmed P1/P2 in the admin flow or seven-site helper correction. This does not claim a historical browser failure or a new runtime PASS.

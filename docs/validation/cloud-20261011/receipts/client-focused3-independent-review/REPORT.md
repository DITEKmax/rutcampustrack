# Independent focused-three observer source review

Verdict: **CHANGES_REQUESTED_SOURCE** for the original prepared package. Exactly two P2 source blockers remain, FOCUS3-ASYNC01 and FOCUS3-NET01. No additional blocker was found in the finite requested scope. This is independent read-only source and safe receipt review, not a live observer, browser, API, stand or product acceptance result.

Requested/selected reviewer gpt-6.1-sol/xhigh; actual model/effort UNKNOWN. Reviewer has no authorship of this observer. Root is sole shared-document writer. All reviewer output is in this NEW scratch directory; no author, Root, Angular or product changes, new runtime, helpers, network, credentials, private ENV/key reads or children.

## Exact input and preservation

Prepared product/harness source is 5ced48c466b02097876f4f8aeee6e5c8da92ce1b, read checkout c16a2995a3a830e5c0c4a0acf9cba4073aa212a2. READY.safe.json SHA256 167b8b1b3bd15dc41c4adebb93d73d514c553c8f6bfd59a6bd03c8294323f49e. The source archive focused3-preparation.safe.zip is 31805 bytes /15 members, SHA256 5fe484b3e8f294d18de7adb72a5c7c0da5d85d67c8ef30ff3ed5c8880d504886; manifest SHA256 5899b322179f48bec65ab48f729ec66242aba9821a86f68500987d4d0b109044. Independent CRC/member-byte/hash verification found zero missing, unlisted or mismatched members. The Node archive is 6265 bytes /15 members, SHA256 8f229b46b9465577af52efd1e4bc27eee368b0d4e131b4bb5c0109da3381e28b; its CRC and member inventory also match.

The independently checked 19 Root files and 11 own prepared files match READY bytes and modes. The final harmless Git-blob read proves all19 Root current bytes equal their prepared5ced blobs; this includes the original full mobile-real-server.spec.ts and protected mobile fixture, artifact privacy, canonical reporter, configs, public fixtures and relevant client sources. The 11 own files remain mode600/nlink1. Exact inventories and hashes are in source-identity.safe.json and final-source-recheck.safe.json. No broad whole-stand or dependency-tree identity is implied by this finite19 inventory.

## P2 FOCUS3-ASYNC01 — timed-out work can continue after END

Path: /workspace/scratch/rct-validation/client_real_acceptance/focused3-preparation-5ced/observer.ts. Critical symbols/lines: bounded83, add139, failureRead279–298, failureReads300–316, finish363–378.

bounded() returns when its Promise.race timer wins but does not cancel or settle the underlying operation. finish() settles only the currently tracked passive response callbacks, then bounds DOM work to3s and failureReads to12s. The failure-read work is not tracked for later settlement. failureRead waits without a timeout for response.dispose(), then calls add(), which persists immediately and has no ended guard. failureReads can proceed from its SESSION read to additional GETs after the outer timer has already returned.

The following is a static admissible counterexample, not executed runtime evidence: SESSION GET and JSON succeed and sessionFacts sets actorIsExpected=true; response.dispose stalls beyond12s. finish catches the timeout, records END with diagnosticCompleted=false, clears token and returns to the original sanitizer/fixture teardown. Later dispose resolves, the old operation appends and persists a SESSION event after END; failureReads starts subsequent GETs with the cleared token during teardown or a following test. The outer process guard eventually reaping a worker does not prove that per-case diagnostic operations settled before teardown. DOM work may likewise continue after its outer timeout.

Impact: the claimed per-case asynchronous settlement is false. Observation files can change after END and a diagnostic operation can outlive the case it describes. Inspected serialization remains closed and no actual body/token/name/error leakage was observed; this finding is a harness lifetime and diagnostic correctness blocker.

Required narrow correction: explicitly stop/cancel or settle all started diagnostic work, prevent post-timeout/post-END new requests and writes, and guarantee token clearing in final cleanup. Preserve original assertions, failure statuses, read-only UI-token policy, closed facts and bounded overall runtime. Meaningful source-derived public controls should distinguish the old implementation using delayed disposal/body callbacks and assert no post-END GET/persist. Native browser/API proof remains separately authorized.

## P2 FOCUS3-NET01 — diagnostic bearer requests follow redirects by default

Path: the same observer.ts:282–284. failureRead sends the UI-issued bearer in a request to an initially accepted relative path, but omits maxRedirects.

The actual pinned Playwright1.59.1 sources were opened independently: /workspace/rutcampustrack/tests/e2e/node_modules/playwright-core/lib/server/fetch.js:122 defaults to20 redirects; its282–324 branch clones the headers, removes cookie, and follows new URL(Location, url), retaining Authorization across origin changes. The client/fetch.js:304 dispose path awaits an asynchronous channel operation, supporting the lifetime qualification above. Exact vendor read hashes are in vendor-request-boundaries.safe.json. No vendor code was executed or modified.

Static counterexample only: an unexpected301/302/307/308 response with a foreign-origin Location makes the fetch follow that destination with the copied UI bearer before the observer sees the final response. Neither closed artifact serialization nor the canonical reporter constrains that request destination. This violates the explicit own-GET bearer boundary. No actual redirect, request, credential disclosure or product security failure was performed or observed.

Required narrow correction: set maxRedirects:0 for these new diagnostic GETs and keep unexpected3xx represented by the existing safe OTHER/unavailable facts. Preserve exact loopback destination, zero retries, current-actor verification and no extra login/write; do not alter vendor or original tests.

## Other finite source conclusions

The focused wrapper imports the same protected test object from the real mobile dataset, registers its before/after hooks, then requires the original spec. It does not copy, suppress, replace, soften or swallow the original assertions. The selected genuine original cases are STUDENT source14, HEADMAN61 and HELPER142. They retain real UI login/actions and explicit readback/foreign-scope/revoke outcomes. The observer hook runs before the original afterEach sanitizer; normal producer prevention and canonical privacy fixture semantics are preserved. Success cases have no diagnostic extra GET. Failure-only reads use the actual UI-issued token, perform a fresh session actor identity check before the additional bounded allowlisted read paths, and do not create another login/role/write/grant.

The serialized schema contains only fixed case/phase/endpoint codes, closed status/role/target/availability enums, booleans and bounded counts. API/DOM bodies, names, user IDs, credentials and errors stay out of serialized output. Name equality is returned from the browser as a boolean. Callback parsing failures use fixed unavailable facts without raw errors. The2 positive and7 rejection controls exercise actual validateFacts using public synthetic values; they are meaningful serializer-policy controls, not asynchronous or network lifecycle proof.

Runtime config keeps the supported PLAYWRIGHT_NO_COPY_PROMPT flag, canonical SafeReporter and trace/video/screenshot off in both global and selected project settings. The alternative discovery reporter is selected only by the separate --list config; the runtime driver does not override the reporter. A real fixture would reject that discovery reporter before explicit dataset/browser/credential operations. The focus preserves the original Chromium Desktop viewport for diagnosis; it is not a mobile viewport acceptance result.

Teaching time remains the accepted09:00 Europe/Moscow fixture projection. The original security/server clock is unchanged. Public existing synthetic seeds/signing helpers were inspected as source only. No real TMA key or private ENV was read, and no current stand/provider signer alignment is established here.

The read-only driver requires a fresh exact Root grant and a hash-bound readiness receipt; checks the selected checkout, own/root scoped bytes, dependency/browser/guard identities and fixed loopback; uses explicit child environment without manual dotenv/proxy inheritance; and records native browser status separately from successful supervision. It uses b78 sampled resource checks/own PID-birth cleanup,300s maximum and at least60s stand cleanup margin. Its private TMP is removed only after all owned work is reaped. Shared UID, sampled caps and no hard per-process cgroup remain qualifications. This source attestation cannot replace actual current JAR/renderer/Vue/key/DB/proxy/private-provider readiness, fresh resources and a separate runtime grant.

## Actual evidence and proof limits

The author Node-only terminal78393 is actual evidence read from its original safe receipt, not reviewer execution. TypeScript5.9.3 errors0; closed-field2 positives/7 rejected; native --list exactly3 Chromium original lines14/61/142, no unexpected cases. All3 recorded child PID-births were terminal/reaped; no signals, owned TMP removed, source19+own11 unchanged. The wrapper PID-birth was NOT separately captured; tool/session native0 is qualified and not substituted for a recorded wrapper birth. All Node leases are released.

Node-only checks do not execute UI, server, token reads, redirects, observer callback lifecycle or browser privacy production. They do not resolve the causes of the three previously failed real client cases. The original client FAIL causes remain UNKNOWN; all required broader client families, new14 acceptance and WebKit remain separate. Green current CI, counts, unchanged source and this review do not confer whole-product or eleven-family PASS.

Next: preserve this original review/archive and the author original input as immutable. Recheck the author's NEW sibling correction from frozen bytes, including both P2 fixes and meaningful qualified public source controls. Only Root may approve fresh Node/browser/stand runtime capacity and source binding.

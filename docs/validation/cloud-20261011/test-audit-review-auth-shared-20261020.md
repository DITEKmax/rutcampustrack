# Independent review: auth/shared accepted test audit

Decision: **APPROVE the assigned source changes**. No substantive blocking finding identified. This is source-review approval, not a claim that the Docker-backed integration tests passed. BS-R11 is approved only for the shared fixture/helper portion; its Academic/Attendance/Schedule actual-channel consumers remain a separate integration condition owned by root.

Reviewer requested model/effort: gpt-6.1-sol/high. Actual runtime model metadata: UNKNOWN. Reviewer is independent of the sole author. No children, source edits, builds, tests, application runtime, database/broker operations, CI operations, external messages, private outputs, or production resources were used. The only reviewer-written artifact is this report.

## Revision and scope

- Baseline: `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`.
- Reviewed worktree: `/workspace/work/test-audit-implementation/auth_shared`.
- Governing scope: `.agent/orchestration-v2/RULES.md`, `CURRENT.md`, root's current audit GO, canonical `docs/validation/cloud-20261011/TEST-AUDIT-20261020.md`, and `auth_shared-packet.md`.
- Read each changed diff and each new file, then the relevant production paths and retained counterpart assertions. Review was of the new audit changes, not the entire historical PR.
- All 45 inventory paths match their declared SHA256 values, including the five new files and one removed file. No production, dependency, generated, workflow, exclusion, or threshold change appears in the reviewed inventory. The pre-existing disabled CompositeLoginKeyResolverIT annotation was only reformatted; its three explicitly accepted obsolete methods were removed. No new skip/xfail was introduced.
- Tracked diff SHA256 before and after review: `e2bee525d6d2917a028cfefc53a8663224ea3beeb714c08189a6a912905e2083`.
- Sorted inventory aggregate, hashing `path + NUL + sha256-or-None + newline`: `8077078ebad278cb9b9738f6431ee18a2d5fc1d5b854ecd130e27ed57c9f7908`.

## Per-ID review

“Accepted” below describes independent source review of the prescribed change and retained guarantee. It does not convert compile evidence into integration runtime evidence.

| ID | Review | Independently checked guarantee |
| --- | --- | --- |
| AG-D01 | Accepted | Only duplicate login-cookie oracle removed; SameSiteCookieContractIT still asserts HTTP 200 and all five cookie attributes. |
| AG-D02 | Accepted | Missing refresh cookie still receives 401 in SameSiteCookieContractIT. |
| AG-D03 | Accepted | AuthIT retained refresh sends only the cookie, asserts access/refresh results and rotation, and does not add a CSRF header. |
| AG-D04 | Accepted | AuthOtpFlowIT retained real HTTP 204/null body, six-character Redis proof, and matching Rabbit payload. |
| AG-D05 | Accepted | WsTicketConcurrencyBoundaryIT retains exactly one HTTP winner, one 404, deleted Redis ticket, and another sequential 404. |
| AG-D06 | Accepted | JDBC role-selection survivor asserts grant, SV increment, same-role no-event idempotency, and stale expected-version conflict; HTTP role switch survives. |
| AG-D07 | Accepted | JDBC revoke-current survivor asserts durable later denial and exactly one logout event; bearer logout flow survives. |
| AG-D08 | Accepted | JDBC stale grant/version and foreign-hint survivors retain rejection and no extra session/login event. |
| AG-D09 | Accepted | Only the three named methods in the already-disabled obsolete composite class removed; active resolver and rate-limit counterparts remain. |
| AG-D10 | Accepted | Only redundant fractional fixture cases removed; both fractional invalid claim cases and whole-second positive cases remain. |
| AG-D11 | Accepted | OWNER-role rejection remains in the parameterized invalid-access claim cases. |
| BS-D01 | Accepted | Only noAuthRequired removed; unauthenticated legacy/modern MIME routes retain 204 and metrics assertions. |
| BS-D02 | Accepted | Retained duplicate-call test starts with the same successful first claim. |
| BS-D03 | Accepted | Retained exception-audit test asserts propagated class/message and failed audit entry. |
| BS-D04 | Accepted | Retained empty-OpenAPI test checks components and ErrorResponse schema registration. |
| AG-R01 | Accepted | Two held real BCrypt matches, three distinct existing login identities, HTTP saturation rejection, exact maximum, permit recovery and bounded worker cleanup replace the rate-limit-confounded timing oracle. |
| AG-R02 | Accepted | A trusted control succeeds; the same complete claims are re-signed with the rogue key; 401/code/no-store and zero admission/downstream calls remain. |
| AG-R03 | Accepted | Complete session claims and valid control precede individual purpose/subject/role negatives; response code, chain denial and context clearing are observed. |
| AG-R04 | Accepted | Wrong OTP is guaranteed different from issued proof; request precondition and no session/verified-event delta are asserted. |
| AG-R05 | Accepted | Real HTTP admission independently isolates RV drift with unchanged SV, and SV drift after a role round trip with unchanged identity/RV. Both yield SESSION_STATE_STALE/no-store; current controls succeed. |
| AG-R06 | Accepted | Role-only denial now uses its own fresh actor/challenge/code, separate from binding mutation; exact 409 and no receipt/consume/session/event effects are asserted. |
| AG-R07 | Accepted | Actual runtime connection PID is captured inside the bound transaction; pg_blocking_pids checks the exact waiter/blocker pair; final adapter outcomes remain. |
| AG-R08 | Accepted | New *IT uses HTTP-created real PG sessions and the actual service/Redis store; two sessions and tickets have separate issue/redemption limits, concurrent boundary winner counts, TTL and capped counters. |
| AG-R09 | Accepted | Real PEM-loaded signer, independent JJWT signature parsing, report purpose/binding/SID/SV/RV/expiry assertions, production report validator and ordinary-purpose rejection replace mock argument inspection. |
| AG-R10 | Accepted | Both verify call paths reach MessageDigest.isEqual under the focused structural guard, with its data-flow limitation stated. Real Redis/HTTP tests retain reverse ownership rejection and one session/event with both indexes consumed after the direct/by-code race. |
| AG-R11 | Accepted | Real HTTP/PG/Redis flow corrupts only the actor's cleanup set; committed revocation/hash/event, clear cookie/no-store, old bearer/refresh and WS admission denial, surviving unremoved ticket and foreign-user live control are observed. Cheap response mapping units remain. |
| AG-R12 | Accepted | Exact anonymous security denials; authenticated env/beans/heapdump 404 and structured non-disclosing body; health checks root status. Production exposure/security unchanged. |
| AG-R13 | Accepted | HTTP and document shape checks run before update mode; parsed semantic comparison preserves schema/security data and hidden internal routes while removing the stated documentation fields. |
| AG-R14 | Accepted | TestJwtKeys writes owned temporary PEM/kid files and invokes public init; independent public key retained; existing parser/purpose/raw-date cases remain. |
| AG-R15 | Accepted | Fixed synthetic Telegram specification vector anchors the generator-based state cases; real HMAC acceptance and fixed-vector bit-flip rejection retain the reserved-character boundary. |
| AG-R16 | Accepted | Only exact representation prefix and redundant raw substring oracle removed; secret exclusion, redaction marker, parsed JSON and round trips remain. |
| BS-R01 | Accepted | Actual sender type/payload values distinguish FIFO order and elapsed-budget prefix; storage result, retry, rollback/confirm assertions remain. |
| BS-R02 | Accepted | Replacement *IT starts private real Mongo, initializes production index config, reads actual indexes, rejects duplicate own pair and permits foreign owner; group/last_seen/reminder index contracts retained. Global integrationTest routing includes *IT. |
| BS-R03 | Accepted | Signed actual HTTP with real Mongo asserts exact own IDs, distinct unread state/count and decrement, no foreign payload, unchanged DB after foreign denial, and non-disclosing missing-ID 403. |
| BS-R04 | Accepted | Same-server valid Upgrade controls and observed HTTP 401 replace timeout-as-success; bounded repeated positive delivery replaces fixed sleep; socket/STOMP clients close. |
| BS-R05 | Accepted | Renderer output and task cleanup remain; independent flags are order-insensitive while executable and valued argument sequences remain checked. Native renderer counterparts remain untouched. |
| BS-R06 | Accepted | Public service.attendance response uses the client seam; nested history/day IDs, full comment, cancellation distinction and explicit JSON null fields are asserted. Current records/config do not apply NON_NULL to these fields. |
| BS-R07 | Accepted | Facade result assertions added; signed real HTTP plus actual gRPC validates teacher/headman scope, manifest body/ETag, private no-cache and bodyless matching 304; admin rejection remains. |
| BS-R08 | Accepted | Explicit standalone MVC domain-denial seam checks local 403 problem contract; 400 validation/malformed and 405 cases remain. Signed authorization is separately retained in SecurityIdorIT. |
| BS-R09 | Accepted | Production configureMessageBroker is used; actual broker heartbeat values and scheduler wiring checked, initialized scheduler work/daemon state observed and cleanup retained. |
| BS-R10 | Accepted | All three distinct history scenarios remain; replay helper wraps the original production listener, observes its successful owned delivery return and real AUTO-ACK callback, compares full persisted rows/claims and checks empty DLQ. Initial failure rollback, dated recipients, sanitization and read state remain. |
| BS-R11 | Accepted, shared portion only | Getter exemption removed; same-owner delegation followed; runtime helper requires actual CallOptions deadline; ignored-return and helper-return sensitivity cases exist. Static presence rule is explicitly not a timeout proof. Root must still accept domain consumers observing actual calls and asserting nonzero/exact RPC counts. |
| BS-R12 | Accepted | Both submitted malformed marker and canonical invalid token are excluded from actual denial status/exception; credential privacy, UNAUTHENTICATED and no-handler guarantees remain. Production interceptor has no logging path to capture. |
| BS-R13 | Accepted | Exact cutoff equality and before/after/future records distinguish strict-before retention; legacy missing-last_seen backfill survivor remains. |
| BS-R14 | Accepted | Private non-reused JVM-owned containers, fresh PG/Mongo namespace, lazy connection-property startup and started-only owned shutdown. Direct users of this shared fixture were checked; default sequential test execution does not share destructive state across JVMs. Real persistence/broker implementations remain. |

## Evidence limits and deviations

The author reports nine-module test compilation and focused unit/transport passes, including Map HTTP/gRPC, NotificationErrorHandling and Office conversion. I did not execute or read raw private runtime outputs and do not independently certify those execution results. The author's DB/broker suites are explicitly NOT_RUN locally because Docker was unavailable. Their runtime behavior, startup/cleanup, real migrations, Mongo transaction/index behavior, Redis limits and Rabbit ACK observations still need ordinary isolated integration execution; no production resource access is required or authorized by this report.

Accepted implementation choices: AG-R05 moves the inadequate lower-level stale test to the existing actual HTTP suite; AG-R08 uses real service + PG/Redis without claiming an HTTP/private-RPC authorization proof; BS-R02 renames the ineffective structural unit to a real *IT; BS-R06 preserves the current explicit-null JSON contract; BS-R07 corrects an incoherent STUDENT/headman fixture to canonical HEADMAN; BS-R08 uses the audit's permitted local exception-mapping seam. BS-R11 consumer integration remains explicitly outside this writer's completed portion. No other scope deviation identified.

Frozen B013–B015 and historical rejected Mongo/Chromium controls were not changed or executed. Root retains final confirmation of the original accepted audit items and cross-worktree integration.

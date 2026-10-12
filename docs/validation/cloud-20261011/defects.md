# Defect ledger

Baseline3908fad9624f97aec8891fc79338ceb6b0616bf3. Current reviewed integrated product checkpoint389b1d5a77154e127fe8d2b1bd6f3901cae3fa7e. These are bounded defect outcomes, not whole-family or global acceptance. Synthetic data only; no secret values retained.

| ID / source | Severity and cause | Before / after evidence | Current status |
|---|---|---|---|
| SEC01 / L03 / QR | Candidate ruled out: initial fixture lacked actual scoped advice | Actual QR400/no-store/no proof echo; safe fixture control, production handler intact | RULED_OUT; security-developer/review |
| SEC02 / L04 | P2 default-locale enum conversion changes persisted ADMIN/ACTIVE contract | Meaningful Turkish-locale JPA/HTTP baseline fail→ROOT stablecode/read/query PASS | CLOSED bounded8a9 reviewed/integrated547a |
| SEC03 / TMA admission | P2 unguarded timestamp parse and one-sided age check | Signed missing/nonnumeric/overflow→500, negative/future→200 before; safe401 after; D003 valid24h/current-authority reuse preserved | CLOSED bounded8a9 reviewed/integrated547a |
| SEC04 / helper homework | P2 missing MANAGE_HOMEWORK in closed RPC whitelist | Granted own-group assistant falsely denied; granted/revoked/foreign signedRPC boundaries after PASS | CLOSED bounded8a9 reviewed/integrated547a |
| DEL01 / L02 | P2 non-null semester cache never reconciled after missed change/archive | Actual cache/Academic client/Mongo domain3 rule failures→new authoritative reads/outage fail-closed PASS | CLOSED bounded21e/0e717 independently rechecked, integrated5a2d/393905 |
| DEL02 / L01 | P1 send return treated as durable broker publication without confirmation | Actual adminHTTP/PG3intents SENT with blocked/killed connection and absentqueue; NACK/loss→PENDING/retry/recovery checks PASS after | CLOSED bounded21e/0e717 independently rechecked; consumer/routing cases remain separate |
| DEL03 / DR01 | P2 optional projected-decoration outage invalidated valid options action | Meaningful focused production-handler red1→green1; getter moved into existing optional boundary; current-grant/ordinary query unchanged | CLOSED0e717, fresh DR01/DR02 recheck accepted/integrated393905 |
| RF01 / invalid renderer enum | P2 unrecognized target yielded INTERNAL | Actual0/999→INVALID_ARGUMENT after | CLOSED boundedb75 review accepted/integrated8055 |
| RF02 / process lifetime | P2 timeout/interrupt leaked converter descendants | Real multigeneration process trees and preserved caller interrupt red→green cleanup | CLOSED tested owned trees; renderer-developer/review |
| RF03 / malformed document | P2 malformed ZIP passed native converter as text | Actual PK0506-only malformed input accepted before; secure required OOXML core validation rejects before process after; real valid PDF/PNG remains PASS | CLOSED boundedb75 review accepted/integrated8055 |
| RF04 / RPC cancellation | P2 blocked synchronous listener delayed client cancel/deadline cleanup | Actual Netty cancel/deadline red2→green2 with generation cleanup+next RPC; scoped virtual thread/detach preserves owned interrupt | CLOSED boundedb75 review accepted/integrated8055 |
| SB01 / L03 / OTP | P2 shared validation serialized rejected synthetic credential | Real public send/verify400/no-store/stateunchanged controls PASS but echoed proof in baseline2 failures; scoped advice+DTO redaction after PASS | CLOSED bounded final0c61 independent APPROVE; integrated8d2a23d4+73dce87b, exact scope/limits in security-boundaries-review |
| SB02 / L04 / Schedule | P2 default-locale JPA/Web/status-SQL mapping, empty legitimate HTTP feed | Actual Turkish read/write/query3 red; converter plus exact SQLfilter ROOT correction selected after PASS | CLOSED bounded final0c61 independent APPROVE; integrated8d2a23d4+73dce87b, exact scope/limits in security-boundaries-review |
| SB03 / L04 / Mongo | P2 persisted enum code conversion default locale | Real rawMongo non-ASCII code and canonical-read red2; canonical/unknown/null checks after PASS | CLOSED bounded final0c61 independent APPROVE; integrated8d2a23d4+73dce87b, exact scope/limits in security-boundaries-review |
| SB04 / BFF contracts | P2 locale/unchecked unknown upstream finite mappings yield500 | Real signed HTTP knownactive/unknownstatus/numeric eligibility3 red; explicit canonical/unknown boundary after PASS | CLOSED bounded final0c61 independent APPROVE; integrated8d2a23d4+73dce87b, exact scope/limits in security-boundaries-review |
| SB05 / report proof | P2 issuer-authorized malformed proof echoed in400 | Actual issuerfilter reached handler, anonymous401/no-store/Redisunchanged controls; scoped proof-redaction after PASS | CLOSED bounded final0c61 independent APPROVE; integrated8d2a23d4+73dce87b, exact scope/limits in security-boundaries-review |
| SB06 / initial password cache | P2 permitted credential read lacked no-store | Actual authorized HTTP200 Cache-Control absent; current grants/activeADMINtarget/postchange clear/hash oracles after correction PASS | CLOSED bounded final0c61 independent APPROVE; integrated8d2a23d4+73dce87b, exact scope/limits in security-boundaries-review |
| CC01 / teaching-clock fixture | P1 global primary fixture contaminates session authority | Source counterexample: StudentProjectionScopeService193 refreshExpiresAt check uses teaching instant; pinned old/future instant changes live admission | OPEN blocking test-harness fullstack only; no contaminated app started; author correction/review required |

Actual runtime commands, XML, original WIP source-hash limits and independent review verdicts are preserved in respective receipts directories. Nine integrated author commits have exact stable patch equivalence recorded in integration-patch-equivalence.json; no extra runtime PASS is inferred from equivalence.

Accepted export gaps EA01/03/07/08 are implementation work in progress with D006/D007 resolved, not closed by compilation. DATA01 pending-adoption subject exposure confirmed HTTP/JPA and corrected c115; selected green/exactresume PASS, fresh review pending. Other required matrix cases remain explicitly UNKNOWN where evidence is insufficient.

DATA01 / L05: CONFIRMED P2 publication consistency: actual own-headman ordinary teacher projection empty + pendinghandle, teacher list nevertheless includes unadmitted target subject. T001 aligns existing active/published boundary, narrowquery corrected c115; exact target exclusion/resume/COMMITTED assignment after PASS, fresh review pending. Ordinary page correctness PASS and2*N+2 Hibernate count diagnostic only, no performance defect inferred.

MF01 P2 MAP02/03/P03: header-only24byte and truncatedactualPNG admitted201, expected400 actualPG/MVC2FAIL; validatePng checks only signature/dimensions. MF02 P2 MAP04: animatedhref and relativeCSS foreignresource admitted201 actual3FAIL, literalunsafecontrols deny; served-byte evidence next, no browserexecutionclaim. GatewayGW01 only diagnosticUNKNOWN: actualCI non-whole-exp testNPE, cause not established.

GW01 CONFIRMED P2 testfixture stability/quality: wallmilliseconds000 creates integralrawNumericDate, legitimateforwardthen unconfiguredmocknull NPE in existingnegativefixture; actual6controls2relevantfail at000,001/999rejected. ProductionguardRULED_OUT in these controls. Test-only exactfraction correction/green/review pending.

2026-10-11T22:20:50.151475+00:00: GW01 freshindependentAPPROVE2003948 testfixtureonly, actual43PASS0skip/productionbyteequal; integrationnext. MF01/MF02e689greensactualPASS/freshreviewpending; SVGxml-stylesheet separatehypothesisUNKNOWN. CTR01P2 newtypedclocktestfixture joined2026 vseducational2025 sourcecounterexample INCONSISTENT_SOURCE; authornarrowhistoryfixrequired beforeJava/sourceadmission.

2026-10-11T22:28:01.998587+00:00: NP01P2 accepted ownprivatebot initialcredential missingactual30637; safe narrowtransport pending. NP02P1 actual90021 localprovider500 swallowed→dedupcompleted/ACK/provider0success/DLQ0, meaningfulacceptedOTPdeliveryloss; minimalhandler retrypropagation assignmentnext. Initial62839 infraKeyError correctedNEWtestonly, never productFAIL.

## EBSR01 — P1, source-confirmed cross-layer mismatch

Recorded 2026-10-11T22:50:35.910467+00:00; revision cf6d1a238441cc1b122e205e160aa23a741a3942. Auth job expiry ten minutes is passed as ticketExpiresAt into Gateway legacy <=now+60sec ticket guard; actual real-verifier worker reproduction pending. Preserve legacy guard and give job admission its own explicit expiry validation.
Author/root shared state: bounded correction and independent recheck pending; no broad scenario PASS.

## EBSR02 — P2, source expiry-race candidate

Recorded 2026-10-11T22:50:35.910467+00:00; revision cf6d1a238441cc1b122e205e160aa23a741a3942. ExportJobService.file queryForObject can return zero rows if expiry crosses after prior load/admission; expected expired refusal may escape as EmptyResultDataAccessException/500. Deterministic reproduction pending.
Author/root shared state: bounded correction and independent recheck pending; no broad scenario PASS.

## EBSR03 — P2, source ordinal boundary candidate

Recorded 2026-10-11T22:50:35.910467+00:00; revision cf6d1a238441cc1b122e205e160aa23a741a3942. ReportDownloadKind plain Jackson enum accepts ordinal numeric values/numeric strings under current mapper; string-only stable wire codes require narrow boundary decoder and actual malformed/null controls.
Author/root shared state: bounded correction and independent recheck pending; no broad scenario PASS.

## EBSR04 — P2, source initiation-grant candidate

Recorded 2026-10-11T22:50:35.910467+00:00; revision cf6d1a238441cc1b122e205e160aa23a741a3942. Legacy report-download-tickets expanded admin registry kinds lack current corresponding USERS_VIEW/GROUPS_VIEW initiation check. Later Academic denial prevents a proven data-leak claim; D007 requires initiation refusal.
Author/root shared state: bounded correction and independent recheck pending; no broad scenario PASS.

## CLK-DEP01 — P2, actual compile failure

Recorded 2026-10-11T22:50:35.910467+00:00; revision db9e1a1fcf9bd50db2e6f2e81286c08cc9ff1dfe. CLOCK1 compile_green session29481 native1 cannot resolve EducationalTime in BFF. Academic4/Attendance6 selected tests passed, BFF/context/artifacts incomplete. Direct shared-web dependency brings unwanted auto-configuration; relocate unchanged pure record to existing shared-web-api and direct BFF API library dependency. No app launched.
Author/root shared state: bounded correction and independent recheck pending; no broad scenario PASS.

## NRR-TTL01 — P2, source test-sensitivity candidate

2026-10-11T22:57:57.099486+00:00: Fresh notification review source candidate P2 TTL sensitivity: current WsTicketConcurrencyBoundaryIT accepts anypositive<=30000 and disappearance<=32s, hence1s counterexample wouldpass although storage TTL30s accepted. CurrentrealXML30.461s genuine; NOT currentproductTTLdefect. Finalprecisefinding/publishermanifest pending; need narrowtestcorrection andselectivecontrolledbadTTL proof, preserveproduction.

## ECR-SCOPE01 — P2, source client scope finding

2026-10-11T23:00:09.190289+00:00: AdminUsers exportRequest draft search may differ from displayed/applied registry until submit. Exact component source-counterexample; runtime reproduction and final independent verdict pending. Separate disabled/grant guard sensitivity and PWA trend message findings pending classification.

## NTR01 — P2, independent test-quality CHANGES_REQUESTED

2026-10-11T23:06:19.689202+00:00: NRR-TTL01 earlier alias. Accepted30sec TTLtest lowerbound/expiry response/sensitivity missing. Currentproductionactual30.461s genuine; source-testfinding only. Only WsTicketConcurrencyBoundaryIT.java in own notification-provider WT; independent expected30s issuance/read tolerance and expiresAt; selectivecontrolledshort1sownedRedis counterexample thenrestoredtargetedTTL green; no production TTL/Clock/source changes, no new limits/policy. LIGHTtestprep now, NOTIFY3runtimequeueduntilfree fromCLOCK1/EXPORTS2.

## ECR01 — P2, independent client review

2026-10-11T23:06:19.689202+00:00: AdminUsers draft vs displayed/applied search mustuseacceptedquerysnapshot andactualscreenhandlerregression. Source/testqualitycounterexample, browserrealbackend NOT_RUN; fresh recheck afterauthorcorrection.

## ECR02 — P2, independent client review

2026-10-11T23:06:19.689202+00:00: Independent grant-disabled and scope stimuli, exactBlobbytes, selectivecriticalfrontendstaleness/enum sensitivity actualred/restoredgreen. Source/testqualitycounterexample, browserrealbackend NOT_RUN; fresh recheck afterauthorcorrection.

## ECR03 — P3, independent client review

2026-10-11T23:06:19.689202+00:00: PWA trendsuccess usesTelegramwording rather thanexistingcarrier-aware helper. Source/testqualitycounterexample, browserrealbackend NOT_RUN; fresh recheck afterauthorcorrection.

## FMT-ESLINT-01 / FMT-RUFF-VERSION-01 / FMT-BOOTSTRAP-ORACLE-01 — current correction package

Source65e0ba existing strict ESLint reported 2969 warnings across mobile/PWA/TMA; finite original-source comparison showed 110 formatting regressions and one earlier-warning file. CI38236931589 failed one Ruff format check (unpinned actual0.17 versus configured0.11); Coverage38236931576 failed the exact-single-line bootstrap source oracle while1027 Vue tests passed. These are confirmed source/check failures, not an offline product-policy change.

Corrective author9a77f68205df2cbd41892373e261fa05adae0265: minimal script/style-only Prettier plus unchanged native Vue ESLint formatting, exact literal preservation and meaningful selective controls; pin Ruff0.17 consistently; bootstrap AST oracle verifies the same accepted guard and rejects relevant malformed controls. Actual combined680 check, strict lint, PWA/TMA types and scoped hooks PASS. Independent source review pending; Root integration and new current CI/Coverage/Security remain required. Evidence: receipts/vue-format-corrective-final original safe50 archive. Angular untouched under D010; Windows actual runtime NOT_RUN.

## CRA-EXPORT-T01 / CRA-VIEWPORT-T02 — NEW client-test source quality

Original independent source review found filtered-export oracle false greens and inherited desktop viewport overriding the intended mobile viewport. Corrected cf067 source closes both P2: exact ordinary export columns/values/filter exclusions plus20 meaningful controls, effective390×844 on both projects and five private-suite hooks. Fresh independent verdict APPROVE_SOURCE; Root exact seven files integrated at690324a. Evidence: receipts/client-source-independent-review, client-source-independent-recheck and client-acceptance-source-packages. Actual NEW14/browser/backend scenarios remain NOT_RUN; source closure is not CLIENT PASS.

## HOSTCFG-V5-REDIRECT-QUERY — P2 synthetic runtime source finding

The v5 explicit `/app` and `/mini-app` slash redirects drop query parameters retained by former Nginx automatic redirects. T004 requires unchanged navigation/recovery route semantics. Independent source counterexample; actual HTTP NOT_RUN. Correct only own runtime two redirect targets and meaningful query controls, preserve original source seal, then independently recheck before launch.

## HOSTCFG-V5-MONGO-IDENTITY — P2 synthetic runtime bootstrap boundary

Bootstrap requires the exact live mongod child PID and birth identity. The original source expression accepts a surviving recorded descendant when the original child is absent, reused or zombie (three finite counterexamples). No real-process/runtime defect claim. Correct only this gate, preserve original seals and independently recheck before launch.

## CRA-ARTIFACT-PRIVACY-01 — harness producer protection, OPEN correction

Severity P2; source exactEB eb0e02aaf6c7cfb3b80db2d3a3c81b8a2dd79553. Required boundary: TASK-CONTRACT passwords/artifacts confidentiality, existing real-client private artifact controls. Actual source-derived pinned Playwright producer plus current sanitizer/reporter counterexample shows late error persisted before reporter and survived deliberate interruption. Public synthetic marker only; no real credentials leak established, actual worker ordering remains NOT_RUN. Ten controls matched; intentional childSIGTERM/-15 and allowned processes reaped. Original partial browser context was contained without publishing its content.

Cause: supported snapshot guard was not enforced and afterEach/reporter cleanup cannot cover later fixture errors or interruption before callback. Before evidence: receipts/client-privacy-producer-diagnosis/immutable-original-receipts.zip and REPORT.md. After: pending independent proof of automatic public privacy fixture before producer, fail-closed supportedflag, preserved failure/assertions. Sole author client_real_acceptance authorized exact seven harness paths plus narrow dataset base-import chunk in new EB worktree; no UI/vendor/defaultconfig/Angular/source-stack change.

## Current closure overlay — 2026-10-12T15:29:50.823320+00:00

Historical OPEN/pending records above describe their recording revision; use this overlay and linked final receipts for current dispositions. Formatting FMT-ESLINT/RUFF/BOOTSTRAP corrections have independent source approval and current EB CI/Coverage/Security SUCCESS (receipts/vue-format-corrective-review and ci_eb0_receipts). No Angular source was changed. Accepted export implementation, publication admission, renderer/map validation and typed teaching-clock corrections are integrated; full customer/client family acceptance remains UNKNOWN where the matrix lacks complete evidence, rather than being inferred from compilation or source presence.

CRA-ARTIFACT-PRIVACY-01 is CLOSED for the finite reviewed current-suite producer boundary: actual worker red7018 reproduces before-attachment marker exposure, green25349 retains failed status while producer content is safe; final formatting6571/types4356 and independent ea901 APPROVE_SOURCE qualify original83c1 native snapshot to semantic7b/mechanical97d. Root integrated exact semantic3f2cff98 and mechanical5ced48c4. This is not a claim about arbitrary future beforeAll/worker-auto fixtures, direct worker signal-exit evidence, full browser acceptance or a real credentials leak. New current remote CI and client runs remain required.

Synthetic runtime source HOSTCFG-V5-REDIRECT-QUERY/MONGO-IDENTITY are source-reviewed closed, with actual selected redirects/protocol checks in host-v5-actual-terminal; restart ancestor/import correction9ce5 is independently approved ea745. Native restart/recovery not executed. NP01 native caller preparation remains BLOCKED for its own two source findings: unbound complete baseline and standalone child without parent-birth supervision authority. Author correction pending; native sensitivity NOT_RUN. WebKit availability has concrete missing DSOs, system package privilege unavailable, signed user-local/custom-executable proposal reviewed4ee15 SOURCE-only; extraction/ABI/browser remains NOT_RUN. None of these technical states closes the three original real-client FAIL causes, which remain UNKNOWN.

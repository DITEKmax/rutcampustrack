# Independent matrix completeness review

Read-only early review, product baseline `3908fad9624f97aec8891fc79338ceb6b0616bf3`. Shared task documents were read while root was updating them. No implementation patch, Gradle/Docker/browser execution or runtime PASS is reviewed or claimed. Root is sole shared-doc writer; these files are proposals for integration.

The initial 218 rows are overlapping source families: 147 registered story records, 30 research families and 41 client families. They are useful provenance, not 218 executable acceptance cases or 147 automatically mandatory functions. The **52 finite boundary rows** in `required-boundary-rows.json` identify significant accepted rules and sufficient checks; all remain **UNKNOWN / NOT_RUN**. Existing checks may cover several rows. This count is not a feature denominator.

Independent source checks found no pointer mismatch for all 147 canonical story definitions, all 175 original request rows and all 62 story/request link ID pointers. Candidate mapping status was preserved. All L01–L08 and P01–P21 appear in the initial inventory. Pointer identity does not prove mapping semantics, acceptance or runtime behavior.

## MR-01 — Preserve explicit nonmandatory clauses beside registered stories

P1, source-acceptance finding; product defect not established. Root already corrected the family label and dependent blocking scope after the early message.

Canonical `job-stories.md:751–753`, JS-STUDENT-16: «Идея владельца, не подтверждена как обязательная». `backend-requests.md:257`, AT-17, repeats «Статус: идея владельца, не подтверждена как обязательная» and marks it deferred. R-03 defines formats for accepted exports; it does not accept every proposed export surface. No later acceptance was found in dated product decisions, BACKEND-STAGE/BACKEND-CLOSURE, relevant current-stage clauses or current Attendance/BFF sources.

Retain JS-STUDENT-16 as a source proposal with explicit acceptedness disposition. Do not autonomously implement self-export or declare a blocking GAP from its initial expected field. No owner question is required solely because a derived family label previously said accepted. Similarly, JS-HEADMAN-35's adjacent student-data editing clause at canonical line 692 says «До решения действие считается неподтверждённым»; it gives no ordinary admin user-management grant to HEADMAN. Accepted admin management is checked by BOUNDARY-09/11; accepted exports by BOUNDARY-46.

Minimal resolution: root records the caveat in the executable acceptance view and verifies any claimed later acceptance by exact source. Proposal-only clauses need no fabricated runtime test.

## MR-02 — A linked overlay must change the executable oracle

P1, extraction/acceptance risk. The initial raw expected prose is not the effective result merely because a decision field contains an overlay ID. Current root updates link offline and teacher overlays, but several raw fields retain older text; that is safe only while they are explicitly source-family text.

Concrete originals: backend-conflicts R-02 at lines 93–112 distinguishes unlimited, nonexpiring automatic failed-geolocation requests from manual requests limited to **five** lesson submissions per semester, charged at submission. Decline/cancel gives no refund; a new submission after rejection charges again. Old AT-11 still says three. R-07 invalidates cancellation marks while retaining history; restoration is empty, despite older JS-HEADMAN-11 text asking to delete marks. The 2026-09-23 teacher decision widens active-teacher group attendance history while retaining subject-filtered ticket detail. D002 permits only cached STUDENT schedule/homework read-only offline, superseding JS-STUDENT-10's offline-statistics clause. D003 permits initData reuse for 86400 seconds with fresh authority, superseding historical one-use assumptions. D004 excludes only map legend, although canonical adjacent wording still looks mandatory.

Required executable rows: BOUNDARY-06, 22–23, 26–31, 45 and 47. For example, with exhausted STUDENT manual budget and failed check-in during the Moscow window, the current API still creates one automatic pending request, charges no manual quota and keeps identity on exact retry; a current HEADMAN/MANAGE_EXCUSES decision may confirm later. A DB-backed check plus one real student/headman client path is sufficient; no role × origin × client cross product is needed.

Retain raw source text for provenance, but give executable rows effective oracles after supersession. Family PASS requires actual evidence for all applicable significant boundaries or sourced exclusion/supersession. A generic API receipt cannot pass a whole family.

## MR-03 — Whole-story exclusion cannot remove a mandatory denial

P1, explicit negative boundary. Canonical conventions at line 140: «free_attendance отменён. Новых отметок с этим значением не создавать». Whole JS-STUDENT-04 exclusion is correct for the positive feature but does not remove denial of new retired inputs. Likewise, excluded mass cancellation does not exclude individual cancellation/restoration.

BOUNDARY-32: own STUDENT/current marker calls current catalog/create/mark with retired or unknown values; a legacy caller invokes retired create. Require typed denial/410, unchanged current DB/outbox and explicit compatibility interpretation of a separate historical row. One actual public API check with DB no-diff plus reuse of historical mapping checks is sufficient.

`ExcuseServiceApproveIT.java:118–131` directly seeds and approves FREE_ATTENDANCE with the legacy service. It is not a current public API oracle: `ExcuseController.java:59–70` retires creation with 410; `:105–107` delegates status decisions to StudentRequestService. This inspection establishes **no reachable public defect** and requests no implementation fix. That old direct-service green test cannot prove the current negative rule.

## MR-04 — Generic family verification leaves significant states unproved

P1, necessary execution mapping. The owner contract requires current grants, terminal read-only roles, archive/restore, history, concurrency, dependency failure, retry and events. Initial repeated verification/forbidden fields name categories without an observable oracle or synthetic state, so cannot distinguish a missed boundary from a green happy path.

The 52 rows expand distinct invariants finitely. Highest risks: current grant revocation; last-FULL and waiting-actor races; restore without resurrected grants; genuine missed-event semester-cache convergence; archived foreground/background/RPC barriers; interrupted durable transfer/deletion; Moscow cutoffs; ticket/quota concurrency; broker acknowledgement and durable consumer dedup; current notification audience/preferences; open WebSocket after revoke; actual files/export access. Each JSON row supplies sources, actor/grants, client, prestate, action, observable result, forbidden/data invariant and minimal actual check.

Use G1–G11 in `verification-priorities.md` to assign unproved checks and reuse current applicable evidence. No exhaustive combinations, new SLA or architecture change is requested. A shared production guard can cover several paths when its real use is demonstrated; different lifecycle/transport/trust boundaries need distinct observable evidence.

## MR-05 — Real clients need current Vue, production worker and valid current seed

P1, client evidence risk already recorded by frontend inspection. Current Vue auth specs are useful candidates; legacy Angular role specs and scalar fixture roles do not prove current role-domain acceptance. The Spring2026 V2 seed lacks current assignments, schedule, homework, helpers and LIMITED admin data needed for October2026 operations.

BOUNDARY-47/49: warm the production worker and real IndexedDB with own current synthetic STUDENT schedule/homework, disconnect and **reload**, then expire/revoke server authority. D002 allows cached reads until fresh reconnect admission; writes/new attachments/role switch remain unavailable. Explicit logout and user A → B with late callbacks expose no previous private state. TMA does not load persistent PWA offline state. A production browser with `serviceWorkers: 'allow'` and real server is sufficient. Already-loaded disconnection, auth harness `serviceWorkers: 'block'` and node-VM worker mocks cannot exercise this boundary.

The finite eleven-flow packet is a sensible first slice. Use current Vue selectors and live profiles; add only a specifically uncovered accepted path. Auth-only success does not pass uploads/archive/map/export/helper homework/notification domain paths. Unavailable platform/provider coverage stays scoped UNKNOWN/BLOCKED; Chromium does not prove Safari/provider behavior. No new design or speculative feature is requested.

## MR-06 — Async and file success must be observed beyond the adapter

P1, necessary invariant coverage. BOUNDARY-40–46 connect accepted events/files to durable outcomes. Returning from Rabbit send does not prove confirmation or effect. Queued export is not downloaded bytes; sanitization source assertions are not browser behavior; HTTP revocation is not an already-open socket check.

Sufficient execution: actual broker interruption with PENDING → recovery, original event_id and one durable effect; one audience/binding/preferences retry fixture; one open socket revoke/private-event denial; actual permitted/forbidden attachment bytes with one-year expiry state; SVG marker in real browser plus storage partial replacement/delete retry; distinct accepted renderer formats and each changed model/template's golden content. Reuse unchanged receipts rather than every role × format. Preserve last good map links and shared blob references. Exactly-once provider delivery and invented off-site retention/RPO/RTO are not asserted.

## MR-07 — Research disposition must remain current and bounded

P2, research disposition. Initial RESEARCH-SD-TMA still says replay policy unresolved and future/replay separately decided although D003 is authoritative. Link D003/BOUNDARY-06 rather than reopen Q002.

L05 query count/latency and L06 RPC duplication are diagnostic/performance suggestions; correctness, scope isolation and pending-transfer guards remain required. No numeric SLA, GraphQL/DataLoader migration, cache removal, DB split, Saga or 2PC follows from research. L08 shared auth/academic schema is different-valid with ownership/coupling risk; atomic grants/credentials/session behavior is the executable obligation. Raw lecture files were not personally reopened; the complete included owner research brief was read and repo-critical originals independently checked. Research inventory is complete; actual disposition/runtime remains UNKNOWN pending evidence.

No new accepted-product contradiction requiring an owner answer was found. Critical-test/diff review is a separate next stage after root supplies a stable revision; unfinished code has not been judged.

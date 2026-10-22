# Frontend independent source review — PR2 test audit

**Verdict: APPROVE for the scoped source refactor.** Blocking findings: none. This is not an all-green verification or browser acceptance verdict.

Reviewer is independent of `audit_frontend`; no source edits, tests, builds, browser, DB, CI, network writes, or child agents. Requested model/effort: Sol 6.1/high; actual runtime model metadata: **UNKNOWN**. Process: supplied RULES/CURRENT and current owner packet. The cloud runtime skill was read; runtime status was inspected without credentials or network operations.

Baseline `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`; reviewed worktree `/workspace/work/test-audit-implementation/frontend`. Opened every changed test diff and read the critical production paths and surviving counterparts. The 65 changed paths exactly match `frontend-inventory.json`; every baseline and current SHA-256 matches that inventory. Inventory SHA-256: `7968df3384596813d3baf3f2cf422ad80839278bb9707abac44d1c16f8269da1`. Production, dependency, lock, runner/config and excluded proof files are unchanged. No added skip/todo/xfail, deadline, exclusion or threshold changes were found.

## Qualifications that the integrator must preserve

- **R-A09 is a correct ordinary failing contract test.** The two absolute instants and literal expected educational dates match the explicitly accepted Europe/Moscow contract. Current `headman-api.service.ts:61` derives UTC using `toISOString`, so at 21:05Z it sends the previous educational day. The test refactor is correct; neither skipping/weakening this case nor claiming web PASS is justified. The mismatch predates these test changes and production is outside this packet.
- **MS-R02 has an atomic source dependency.** Read the actual `assertActualCommentLayout` counterpart in `/workspace/work/test-audit-implementation/e2e_infra/tests/e2e/specs/mobile-attendance-requests.spec.ts:259`, including its invocation. It restores owned long text through form remount, measures real clipping, changes viewport/scrollbar/root font, performs keyboard deletion/editing, checks shrinking and permits continued submission. Integrate that counterpart together with removal of the synthetic arithmetic cases. Browser runtime remains **NOT_RUN** under the owner's prohibition; this source review does not turn it into browser evidence.
- Writer-reported results (419 mobile, Node 5, focused App/auth 59 and strict TS PASS; web 250 PASS + R-A09 FAIL) were supplied as context, not rerun or treated as proof of source correctness. Node navigation survivor was inspected in source; its runtime is not established by the Node contract-only five-test result.

## Per-ID review

All rows below are source-approved against the accepted named method/assertion boundaries. `Dependency` means the MS-R02 qualification above; `Known FAIL` means the R-A09 qualification above. No additional source deviations were found.

| ID | Source assessment / preserved behavior |
| --- | --- |
| D-A01 | Only dashboard construction smoke removed; shared TestBed construction and successful stats/loading/error scenario remain. |
| D-A02 | Only groups construction smoke removed; real component construction and ACTIVE group/student load remain. |
| D-A03 | Only semesters construction smoke removed; initialization HTTP outcome remains. |
| D-A04 | Only users construction smoke removed; same initialized fixture and both initial HTTP responses remain. |
| D-A05 | Only duplicate empty-array scenario misnamed as 404 removed; mounted empty-state survivor remains. |
| D-A06 | Only schedule construction smoke removed; same setup and populated matrix scenario remain. |
| D-A07 | Only literal GPS constant copy oracle removed; permission dispatch and mounted denied callback remain. |
| D-A08 | Only duplicated successful check-in removed; survivor checks GPS/options, POST/body and confirmation. |
| D-A09 | Only internal cells-length test removed; rendered 42 cells and selected date remain, strengthened by R-A11. |
| D-A10 | Only active CSS/decorative-dot case removed; lesson state/action cases remain. |
| D-A11 | Only exact skeleton count case removed; empty/data/loading-related scenarios remain. |
| D-A12 | Only journal-grid construction smoke removed; same real mount and meaningful column case remain. |
| D-A13 | Only journal-page construction smoke removed; initialized real fixture/load/default date range remain. |
| D-A14 | Only overall-card construction smoke removed; mounted totals/rate remain. |
| D-A15 | Only static overall-card caption case removed. |
| D-A16 | Only stats-page construction smoke removed; initialization and real rendered flow remain. |
| D-A17 | Only static stats-page heading case removed. |
| D-A18 | Only subject-chart construction smoke removed; mounted canvas/heading and dataset case remain. |
| MCF-D001 | Only repeated direct Moscow helper case removed; homework domain retains both sides of Moscow midnight and completion-clock rules. |
| MCF-D002 | Only attachment-format prose oracle removed; MIME/extension agreement, invalid combinations and suffix cases remain. |
| MCF-D003 | Only QA fixture self-consistency arithmetic removed; real period mapping and server weighted metrics remain. |
| MS-D01 | Only handwritten recovery duplicate removed; domain command/key replay + single flight and mounted explicit retry/no automatic replay remain. |
| MS-D02 | Only generic cross-root navigation duplicate removed; existing Node counterpart checks root seeding and both Back outcomes. |
| MS-D03 | Only selected Response-reader spies and artificial bodyless-304 rejection removed; public 304/null/ETag versus 200/data/ETag remain. |
| MS-D04 | Only injected fetch `this` identity assertion removed; default global receiver regression and injected response remain. |
| MS-D05 | Only dock caption array removed; canonical route IDs and availability remain. |
| MS-D06 | Only fixture object-key enumeration removed; legacy validation and real current DTO mapping remain. |
| MS-D07 | Only accepted caption oracles removed; distinct pending states, numerical 60-second countdown, identity/order, Moscow clocks and update activation/cleanup remain. |
| R-A01 | Distinct renewed JWT; real refresh cookie/body and both bearer replay/results checked. |
| R-A02 | Actual local/session contents disappear; either storage throwing does not prevent the other cleanup. |
| R-A03 | Real input events and confirm clicks observe disabled/allowed behavior and trimmed result. |
| R-A04 | Actual active/inactive delete controls, active guard, real dialog result and DELETE request/body observed. |
| R-A05 | Actual role combobox selects all roles and returns to student; checkbox visibility observed. |
| R-A06 | Real approve/reject observable errors retain pending ticket, clear busy and produce feedback; private mapper call removed. |
| R-A07 | Cancelled row has no action; actual active row click submits correct lesson/user/status. |
| R-A08 | Reversed input with independent full expected ordering; student-record lesson binding retained. |
| R-A09 | **Known FAIL**; fixed clocks and independent literal Moscow dates, correct for the accepted contract. |
| R-A10 | Real LoginComponent/AuthApi/AuthService observe cookie POST, credentials body, decoded owner/token and every role route. |
| R-A11 | Rendered first calendar-cell click emits the independent concrete date, not merely one event. |
| R-A12 | Changed-group subscriptions produce exactly one event/badge increment; disconnect prevents later publication. |
| R-A13 | Actual computed datasets checked for all labels/series values with asymmetric input. |
| R-A14 | Actual selected group loads journal and feeds correct summary and per-student chart values. |
| R-A15 | Actual logout button invokes logout with the injected API/router. |
| R-A16 | Redirect branches return the actual supplied UrlTree sentinel with the appropriate destination; allowed-role cases retained. |
| R-A17 | Independent unequal per-student results protect identity and aggregation. |
| R-A18 | Actions are scoped to the pending ticket; resolved card cannot expose decision buttons. |
| R-A19 | Real approve/reject controls show refreshed resolved state; real child saved event closes form and displays refreshed homework. |
| MCF-R001 | Independent nine mappings, recording real client fetch boundary, repeated sort precedence/paging and preserved server rows/counts. |
| MCF-R002 | Real mounted journal starts deferred destructive clear, replaces API owner, and rejects old ACK effects/reads/errors while new mark stays actionable. |
| MCF-R003 | All ten named declarations/parameter sets remain; real host/settings/colors cover rendering, CAS revisions, explicit one/two PUTs, omitted mute, blocked cross-set/503 and late-context private-data removal. |
| MCF-R004 | Real More controls navigate to Statistics/Requests; disabled Map handler also refuses navigation. |
| MCF-R005 | Separate failed command survives until A→B→A cycle; retry is forgotten without using the same-tick retry guard to delete it. |
| MCF-R006 | Metric-source/gap rules use changed counts/combined percentage, finite coordinates, temporal ordering, null separation and future/group-line presence instead of fixed SVG serialization. |
| MCF-R007 | Endpoint-only narrow-axis budget, normalized positions and calendar position retained; invented browser box arithmetic removed. |
| MCF-R008 | Captured pending Query is removed and aborted on owner change; late result does not publish or resurrect cache/data. |
| MS-R01 | Actual App SFC, real Auth/BFF/coherence/bootstrap and QueryClient; deferred HTTP/browser/snapshot boundaries cover cold recovery, fail-closed HTTP, read-only current-only retry, coalescing, seven cancellations, authority generation reconciliation and late responses. Classifier cases and separate reconnect-recovery suite remain. |
| MS-R02 | **Dependency**; cheap real-component teardown remains, synthetic layout removed only with the actual browser counterpart. |
| MS-R03 | Both schemas accept date/null and reject missing timestamp; TS checker proves required string/null union with no undefined, independent of formatting. |
| MS-R04 | Invalid idempotency key rejects before fetch; binary request URL/method/current bearer/include credentials/Accept and Blob remain checked. |
| MS-R05 | Pure helper truth table plus actual owner/profile/history keeps loaded rows offline, blocks pagination and requires explicit online pagination. |
| MS-R06 | Real export action exposes busy/status/progress/completion; filter/grant changes suppress obsolete completion and preserve availability rules. |
| MS-R07 | Only named message-copy assertions changed; type/status, privacy, retained pending/files, bounded reads/cancellation, retry controls and recovery behavior remain. |

## Critical boundary checks and limits

Read production App bootstrap catch/finally, current-only policy, browser handlers, invalidation and teardown; `usePwaAuth`, real fetch adapter, snapshot seam and alias resolution. The mock replaces the leaf feature display, update/install/recovery UI and storage boundaries; it retains real admission and authority handling. Existing `reconnect-recovery.test.ts` preserves the distinct downstream-401, retained-owner, generation and both late-current response cases. The new uncertain-refresh case correctly follows the real no-blind-refresh policy rather than inheriting the obsolete partial harness's generic rejection replay.

Read destructive journal `clearEntry`/scope checks, notification host CAS/context cleanup and real child template wiring, ranking query cancellation/removal, homework retry forgetting, attachment authorization, Angular cookie AuthApi/interceptor/LoginComponent, storage cleanup and STOMP lifecycle. Compared the named command/key, date, navigation, parser and form survivors directly. No newly concealed sensitive payload, weakening of the accepted ownership/privacy contract, or unauthorized production fix was identified.

This review covers these 62 IDs/65 frontend test paths and the specified textarea counterpart, not all PR2 production changes or all historical proof inventories. Existing frozen B013–15 and rejected Mongo/Chromium equivalents were not changed or run. Browser rendering/runtime reliability and the known ordinary web failure remain explicit limitations for integration acceptance.

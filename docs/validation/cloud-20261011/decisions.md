# Owner decisions

D001 — 2026-10-11: full owner contract is authoritative; attachment history does not expand or restrict it.

## D002 — 2026-10-11: PWA read-only offline session policy

Owner answered Q001: «Разрешить такой просмотр только для чтения до проверки online (рекомендую)».
Supersedes: unresolved offline expired-session branch in docs/implementation/release-surface-matrix.md and parallel-development.md.
Affected scope: cached STUDENT PWA schedule/homework, offline reload/session expiry and reconnect validation.
Accepted behavior: last verified student scope may be read offline without current server-session validation; only read-only schedule/homework. Reconnection requires authoritative online admission before online operations. This does not grant offline writes, new attachment access or TMA offline mode.
Canonical decision: docs/product/decisions/2026-10-11-pwa-offline-read-only-session.md.

## D003 — 2026-10-11: TMA повтор initData

Owner answered Q002: «Сохранить повтор в пределах текущих 24 часов со свежей проверкой доступа (рекомендую)».
Supersedes: historical single-use initData assertion in docs/archive/report-before-v0.0.0/01-auth-service.md only for this TMA admission rule.
Accepted behavior: correctly signed initData may be reused within current 86400-second age window; each admission requires current account/binding/grants/session authority. Reuse does not override invalid/tampered/expired proofs or revoke/status restrictions.
Affected scope: TMA replay/reopen/reauth; no idempotent-same-session promise.
Canonical: docs/product/decisions/2026-10-11-tma-init-data-reuse.md.

## D004 — 2026-10-11: отсрочка легенды карты

Owner answered Q003: «Сохранить отсрочку легенды до отдельной сессии с принятым набором иконок (рекомендую)».
Affected: MAP-06, textual legend part of JS-STUDENT-24 and shared teacher map only.
Accepted disposition: EXCLUDED from required current validation until separate icon set/meaning decision. Other map access/upload/sanitization/view/download/version/history criteria remain required.
Supersedes: ambiguity between canonical mandatory legend wording and later separate-session deferral; no invented icons/meanings.
Canonical: docs/product/decisions/2026-10-11-map-legend-deferred.md.

## D005 — 2026-10-11: запрет гео после ручного «н»
Owner answered Q005: «Сохранить запрет гео после ручного «н»».
Accepted behavior: existing HEADMAN manual ABSENT prevents student geolocation check-in in the live window and the automatic failed-geolocation request path through that blocked attempt; snapshot eligibility reflects the prohibition. Existing accepted past-lesson appeal path, quotas, time windows and other state guards are unchanged.
Supersedes: contradictory manual-ABSENT geo overwrite clause in archived backend-conflicts §3:541–555 for current acceptance.
Affected: StudentCheckinService + StudentAttendanceSnapshotService, current Vue eligibility, tests/manualabsence boundary only.
Canonical: docs/product/decisions/2026-10-11-manual-absence-geo-block.md.

Q004 resource receipt 2026-10-11T20:16:28.937795+00:00: rutcampustrack-cloud-skills-20261011.zip verified116manifestfiles/117entries/8skills; README+all3projectSKILL read. No new productrule/permission. Projectpaths skills/project/*/SKILL.md; supportingfiles recoverable fromcompleteoriginalZIP and extracted /workspace/rct-cloud-skills-20261011. Existing built-inCloudruntime remainspreferred.

## D006/D007 — 2026-10-11: admin export columns/audit/grants

D006. Owner reply to Q006: «Те же поля плюс Telegram ID для FULL admin; аудит факта».
Accepted user-export columns: ordinary registry fields ФИО, login, roles/statuses, group, staff number and Telegram binding state. Telegram ID is an additional column only for current FULL administrator authority. LIMITED export does not include Telegram ID. No initial/reset/user password or recovery link is exported; the accepted initial-password display exception remains separate.
Record the export fact in audit without file contents: actor, time, export kind and row count are safe fact metadata. Do not store credentials or exported personal-data contents in audit/artifacts.
Supersedes: unresolved exported-column/audit branch of canonical JS-ADMIN-13:253–259 and ПК132:202. Does not add an admin attendance-statistics screen or a new web frontend.

D007. Owner reply to Q007: «Разрешить соответствующую выгрузку по USERS_VIEW/GROUPS_VIEW (рекомендую)».
Current LIMITED USERS_VIEW permits the ordinary user-registry export; current LIMITED GROUPS_VIEW permits the ordinary group-registry export. Live grant and allowed scope/filter rules apply at initiation/read/download; revocation denies later operations. FULL admin remains permitted. No new permission code or credential grant is inferred. D006 restricts Telegram ID to FULL.
Supersedes: unresolved LIMITED ordinary bulk-export branch left by 2026-10-08-limited-administrators. Existing separate credentials grants/display rules remain unchanged.
Affected scope: server admin user/group exports in five accepted formats, server format catalogue, audit fact, FULL/LIMITED field and revocation tests, existing accepted mobile carrier where applicable.

Canonical: docs/product/decisions/2026-10-11-admin-registry-export-columns-and-grants.md.

## T001 — technical publication consistency, 2026-10-11

This is an implementation resolution within the already delegated assignment protocol, not a new owner rule or a TASK-CONTRACT amendment. The accepted 2026-09-23 decision requires a current active assignment for teacher group access; current ordinary SubjectAssembler/AssignmentRepository publication excludes ADOPT_UNASSIGNED targets while their operation is PREPARED/APPLIED. DATA3 actual HTTP/JPA shows ordinary headman teacherIds/assignments empty and exact pending handle while teacher subject list exposes the same unadmitted subject. Apply the same published-active predicate to that teacher-list query, preserve existing semester/date/group/order/page/history and headman pending/resume semantics. Do not grant access from an uncompleted technical target. Exact phase protocol is existing implementation ownership; missing standalone historical protocol specification remains recorded, not invented as a new source. Before/after subject list and same-target resume must prove consistency.

Writer: data_lecture_developer owns only SubjectRepository#findAssignedToTeacher predicate and its distinct test; export author retains its separate new findLessonTypeCodes batch method. These are non-overlapping internal implementation hunks in independent frozen worktrees; public API/proto/config/locks still have one writer. Root integration must preserve both and independently review the merged source. No broader SubjectRepository rewrite.

## D008 — 2026-10-11: PNG processing resource budget

# PNG validation resource budget — owner decision 2026-10-11

D008. The owner answered clarified Q008: «Разрешить предел 128 МиБ (рекомендую)».

The clarified question explicitly retained the 10 MiB uploaded PNG file limit and proposed an additional validation budget of 128 MiB of decoded data and one million pixel rows. The earlier request for an explanation was not an approval; this subsequent answer approves the clarified proposal.

Accepted behavior: retain the existing uploaded-file limit of 10 MiB. Before publishing inventory, assets or a new revision, reject a PNG whose validation requires more than 134217728 decoded scanline bytes or 1000000 scanline checks, with a clear field-specific 4xx response. Count decoded scanline data including filter bytes and all nonempty Adam7 passes; count each validated pass row as one scanline check. Overflow or inconsistent sizes must become a safe validation failure, never an unhandled exception. These are processing-resource budgets, not a new width/height canvas limit or a requirement to allocate the decoded image in memory.

Preserve ordinary safe PNG formats, grayscale/indexed/Adam7 support and the already verified wide 100000-pixel one-row positive fixture within these budgets. Keep bounded streaming validation and the 10 MiB upload limit. SVG resource validation, client access, map history and owner D004 legend deferral are separate scopes.

Supersedes: the unresolved decoded-work/row-budget branch of current MAP-04 validation and independent source finding MVR03. It does not increase the accepted uploaded file size or authorize production operations.

Verification: boundary-at-limit, over-limit, overflow-safe size accounting and a scaled strongly compressed many-row counterexample; reject before any data publication and preserve valid positive controls. Do not execute a billion-row input just to prove resource exhaustion. Actual browser/render/network acceptance remains separate.

Canonical: docs/product/decisions/2026-10-11-png-validation-resource-budget.md.
Contract previousSHA a448a2995eb1181a826435ff684ca62f49dd0447d176ec53b4fc33d5e8ed954b; currentSHA c9336b4770425eb1f9c80f3a793c005191b673c97467dc819510ad446ce94d5c. Original immutable input unchanged.

## D009 — 2026-10-12: final documentation receipt equivalence

# Final documentation commit and exact-revision receipts — owner decision 2026-10-12

D009. The owner answered Q009: «Разрешить финальный коммит документов с доказанным совпадением исходников с проверенной revision; явно сохранить оба SHA и CI-квитанции (рекомендую)».

After the final product/config/test/harness/formatter revision has all required current CI/Coverage/Security checks completed successfully and sufficient actual acceptance/review, a final documentation/evidence-only commit may retain those receipts by proven byte identity of all executable/product/configuration/test/generated/API/schema/migration/lockfile/build/gate inputs. The final commit may record the run IDs, URLs, events, producer head SHA and actual PR checkout merge SHA, final branch/PR SHA, source inventory/equivalence and morning handoff. It must introduce no product, test, configuration, gate, dependency, formatting-rule or accepted behavior changes.

Explicitly retain both the tested product revision and final documentation revision. Do not describe the earlier runs as having executed at the later SHA. Final local SHA = remote designated branch SHA = draft PR head SHA remains required, target main remains unmerged. Verify the exact allowed documentation/evidence diff and source byte hashes; run sufficient documentation/parser/security checks without weakening their rules.

Supersedes only the final documentation/evidence-only exact-revision ambiguity in TASK-CONTRACT criteria F/G/H and verification section8. A new executable/product/test/config dependency change invalidates this exception and requires current checks on the new product revision. All required scenarios, fixes, reviews, meaningful sensitivity checks, formatting/type/build and source/security acceptance criteria remain unchanged. This does not authorize artificial product changes, trigger changes, production/main/deploy operations or a partial checkpoint being called DONE.

Canonical: docs/product/decisions/2026-10-12-final-doc-receipt-equivalence.md.

## T002 — accepted own initial credential delivery, technical implementation contract

This resolves implementation within accepted JS-SYSTEM-01/current owner exception; it is not a new owner policy or general credential grant. Root independently opened canonical851/853/868, current registry180 and PasswordChangedOutbox. Preserve current login generation, generic redacted RPC, changed-password hash/clearing, and D006/D007 export boundaries. Own delivery requires private positive sender=chat, fresh exact binding/UID, unarchived account, at least one current ACTIVE own durable grant, unchanged-password state and nonempty accepted initial. Default role activation remains separate; no student-only or administrator-target credential rule is invented.

Use the source-grounded NP01 proposal (receipts/np01-source-resolution) as the technical starting point: two additive bot-only TLS/directed-service-identity RPCs, monotonic credential/binding generations, an Academic-owned metadata-only durable card tracker, explicit password.changed v2 with v1 compatibility, and nonsecret placeholder registered before credential edit. No password/hash/login in tracker; no new credential lifetime, encryption, export permission, global Redis policy or separate storage stack. Reserve next migration after V56. Existing generic profile stays blank. Password events must retain original UID/generation and support cleaning known invalidated cards after unlink without misdirecting notices or affecting a later binding/account/card. Generation/invalidation/outbox rollback is atomic; network calls remain outside DB locks.

Concurrency is a required implementation result, not assumed from the proposal. REGISTER grants a single recorded render attempt with stable revision; no new credential render after invalidation or terminal render. COMPLETE_RENDER records confirmed edit, definite no-edit or uncertain outcome with typed stable codes. LIST_INVALIDATED includes unresolved render records. COMPLETE_CLEANUP may settle an unresolved render only when exact card deletion/absence is confirmed: a late edit cannot recreate that message ID. Sanitizing an old card is allowed only when no credential render is in flight/uncertain, and must remove both login and password. Failed/uncertain cleanup keeps exact metadata and durable retry/DLQ; it never discards the record or acknowledges completed cleanup. Test event-before-register, edit-response loss, in-flight edit/cleanup and restart/outage recovery on observable exact card contents and recorded state. If implementation cannot provide these guarantees for a concrete accepted scenario, report it before silently simplifying the state machine.

Root is shared-doc integrator. Export successor is common proto/schema/migration/generated-contract writer. A separate developer owns designated new Academic domain/receiver and Auth event producer plus bot handlers/transport/tracking. Config successor owns existing config files and generation tooling only. Public/schema/common files have one writer and exact SHA publication before consumers. Independent security/data/concurrency/migration review and real synthetic cross-layer acceptance remain required. No production/manual deploy/private bot operations.

## T003 — Cloud real-client runtime capacity, technical verification choice

This changes synthetic verification resources only, not product architecture or ownerpolicy. The managed32GiB DockerVFS workspace cannot yet run all16service copies with the4GiB safe diskfloor. Prefer the source-grounded host-Java alternative for seven genuine current production BootJARs, Docker data/nativeRenderer/edge and realVue/browser clients, preserving actualTLS/directserviceidentity/teaching-vs-security clocks. Record hostDebian OpenJDK21 vsTemurinAlpine provenance, sharedUID/private-key OS isolation and unavailable perprocesscgroup hardcaps as limits; never claim production-container isolation PASS. NativeRenderer/tools remain genuine unchanged Dockerimage. New ownsynthetic hostkeys600/public644 mustnot reuse/read/chmod oldforeignUID Dockerkeys. No app/browser launch until separate exactsource/resource/TLS/floor grant and review.

Seven now-obsolete OWN eec3Javaimage tags may be retired individually to recover space: exact plan receipts/owned-old-java-image-retirement-plan.json, soleknown tags/IDs, no container uses them, all8originalJAR sourcehash receipts preserved and verified. Retain nativeRenderer, allshared/base/data/frontendimages, everyproduct source/WIP/XML/evidence, Dockerdaemon/store/config and allvolumes/data/backups. Normal no-force docker image rm exacttags only; recheckbefore andafter metadata, measureactualfree delta, preserve errors/partialresult. This avoids retaining unexecuted outdated runtime copies after exports/NP01source changes; it doesnot authorize globalprune/imagewildcards or deleteauthorresources. RemainingnewcurrentJAR/host staging resources depend on semanticfreeze/currentconsumerhash checks.

## D010 — 2026-10-12: Angular frontend remains untouched

Owner instruction: «помни, что фронт на ангуляр мы вообще не трогаем (весь веб сейчас переписывается на vue) и почему-то модель ушла с ULTRA на extra high но ты продолай свою работу с агентами и тд».

Accepted scope: do not change the Angular frontend at all, including formatting, configuration, template whitespace corrections or functionality. Current tracked Angular application is frontends/web-panel/ (package dependency @angular/core ^19.2.0); its complete tracked subtree is excluded from this Cloud task's mutation and formatting inventory. Preserve exact baseline/current Root bytes and file modes, and unknown/foreign WIP. Sole formatter may undo only its own already prepared Angular changes in its isolated worktree, preserving receipts; no Angular change has been integrated into Root. Explicit supported inventory exclusion follows this owner decision, not a formatter or lint failure.

Supersedes: TASK-CONTRACT §3/§4 all-author-source formatting scope ONLY for the current Angular frontend, and earlier technical approvals of Angular formatting/JSON-header/template-preservation corrections. Vue PWA/TMA, server, tests/harness and other accepted author-source formatting remain in scope. The independent local Vue web rewrite does not authorize new web implementation in this Cloud task.

Model preference: Root remains requested/selected Sol6.1/ULTRA under the original contract; user observes an extra-high UI setting. Actual runtime model/effort remains UNKNOWN where tools do not expose it. No silent fallback or claim of measured ULTRA is permitted. Explicit selected child efforts may still vary by scope under the original contract. Continue authorized work with agents.

Verification: net Root changes beneath frontends/web-panel/ must be zero versus the accepted Root revision and baseline; exclude the exact Angular subtree from reproducible formatter inventory/checks, while preserving all required non-Angular lint/build/security gates. Do not weaken unrelated rules or conceal failed required client/server checks.

Canonical: docs/product/decisions/2026-10-12-angular-frontend-untouched.md.

## T004 — Cloud runtime representation after two measured Docker VFS stops

Technical verification choice within the authorized synthetic VM scope, not a product/production architecture rule. Docker9 complete lifecycle stops at the workspace floor despite9.99GB initially free: logicalRootFs2.707GB does not predict5.576GB measured cleanup recovery; allocation split remains UNKNOWN. Keep original5data volumes and allsource/evidence. No identical9retry and no Dockerdaemon/store/config change.

Prefer source-grounded feasibility for the exact retained native Mongo image binary and dependencies running as an eighth bounded host process, with NEW disposable data/tmp under the separately available/tmp filesystem. Preserve database/replSet/auth/client wire behavior and original five volumes; no data deletion or live filesystem copy. SameUID/native-library/container-isolation qualifications must be explicit. Genuine native Renderer remains unchanged in Docker. Consolidate the two readonly Vue asset servers into the already existing HTTPS edge only if exact publicpaths/headers/cache/SW scope/API proxy/trusted .10/origins and bundle bytes remain unchanged. This reduces verified synthetic runtime copies; it adds no webfrontend/endpoint/auth grant or productpolicy.

Native Mongo extraction/tool/library/version/environment/bootstrap and static-edge route changes need bounded source/effectiveness proof, independent review, fresh resources and a new Root runtime grant before launch. If native host dependencies cannot preserve meaningful behavior safely, report that precise blocker and choose another authorized test resource; do not mask unavailable runtime as PASS. ActualMongoexit1 cause remains UNKNOWN; known init-script600UID1000 versusimageUSER1001 mismatch is a separate prepared-input defect.

## T005 — bounded user-local WebKit dependency preparation

Technical verification resource choice within existing authorization for needed VM dependencies, not a product or production architecture decision. OS UID1000 remains after tool escalation, sudo absent and system apt/dpkg state not writable; no privilege bypass through Docker/host mounts or global system/library changes. Seven concrete WebKit SONAMEs missing; Chromium native loader succeeds. Select a new own user-local directory containing only a minimal official signed Debian13 package closure, with InRelease/index/package hash provenance and bounded footprint. First collect safe public signed metadata and exact closure/simulation; extraction and launch require reviewed exact payload/capacity and a fresh resource grant. Scope uses existing public Debian archive keys; never read/dump credentials/config or trust a new unsigned key. Future WebKit child-only launch environment may reference those genuine libraries if compatibility/native loader proof and independent review are sufficient; never silently set global LD_LIBRARY_PATH, affect Chromium/Node/product/server or claim WebKit client acceptance from library availability. Existing cache/source/keys/data/volumes are immutable. No browser launch is yet authorized by this choice.

## Owner clarification — 2026-10-12T18:14:00.981095+00:00 — English backend and locale diagnostic

Owner clarified that the server side uses English and may run in Europe, the USA or Singapore, and asked why Turkish locale is tested. This does not introduce Turkish product localization or authorize deployment/configuration changes. The existing TASK-CONTRACT L04 non-default-locale diagnostic remains a temporary isolated test of stable English machine codes (ADMIN/admin, ACTIVE/active), with original JVM locale restored in finally. Locale.ROOT keeps those codes independent of environment; the accepted Europe/Moscow educational clock remains separate from server location/language. No existing product rule is superseded by this clarification. Native test/fix is still pending; do not claim Turkish production computations.

## 2026-10-12T20:57:08.516219+00:00 — T006 technical SOURCE preparation: permit NEW STAGE-only efficient resource monitor with mandatory cheap fresh floors/shared/net-workspace checks and UTCclock-LAST before every effect; costly allocation full inventories at entry/final and bounded cadence at most1second or64effects, whicheverfirst. All numericcaps/deadline unchanged, failclosed errors, honest sampled/sameUID qualification; no hard OS isolation claim. Exact894partial resume is allowed only after full member/hash/mode/nlink/nofollow/noextra proof; preserve existing files, source/build/JAR/alloldartifacts/data. Source author must establish own-write byte/allocation headroom and relevant counterexamples before independentreview/newRootgrant. No current sourcepatch/reuse oldgrant/deletion/native authority. This reversible verification-resource choice adds no productpolicy or ownerpermission.

## 2026-10-12T21:06:54.707791+00:00 — D011 process coordination

D011 receivedowner processsteering dated13October (actualreceiptclock 2026-10-12T21:06:54.707791+00:00). Appliedtoactive T006 sourcecorrection, original3clientFAIL priority/reusablecohort, suitableCIfeasibility andminimal causalCoverage diagnostics. Samplingnotexactproof; foreignchanges reconcile/stop; coherentpackages/targetedrecheck. ContractSHA now75ee55c6caedd4ca2371818d71031f71d1969f3bcf06a7aa47a5689869acde27 previouse7cbc981e7d1f621f4a6390c0d88f2cbeb5ccfce347a9d324a30389685d2778e. Published docscheckpointdb177beeb9660f5a17d7fadf660e024e28f9f4b9 realpush1284native0/local=Gitreadback=PRhead/DRAFT/mainunmerged; exact99taskdocs only/source inputs equal96. ProductCIreceiptproducer96 distinct, CovFAIL persists; no finalD009/DONE.
Supersedes: inefficient repeated full inventory/review/setup practices only; T006 may use minimal correct approach preserving original limits and accurate qualifications. No product rule superseded.

2026-10-12T23:07:59.029529+00:00: Actual SOURCE CloudFIRST3 date-coherence mismatch reported: focus3-next96-r1 config retained teachingInstant2026-10-12T06Z onlyworker/global/project; oldfocus3 wrapper beforeEach lacks page.clock. Current23:07UTC browserMoscow13October vs domainlesson12October; Headman61 defaultselectedDate queriesTuesday insteadtargetMonday. Do not launch known-incoherent fixture. This is currentSOURCE counterexample, NOT historicalFIRST3cause/runtimeFAIL. Minimal NEW privatewrapper fixedteachingDate analogous acceptedCI technicalchoice beforeobserver/navigation; oldRoot19/originalassertions/observer immutable; source proof/narrowreview beforebrowsergrant. Standstartup independent.


---
## D012 — Q010 explicit isolated synthetic stand startup approval

Owner answered Q010: «Подтверждаю этот изолированный тестовый запуск». Received UTC 2026-10-12T23:15:49.431973+00:00.

Explicitly confirms starting this task-owned isolated project rct-client-host-config-20261012: six Docker containers and eight foreground service processes, only synthetic data, existing test volumes and native Mongo data/key preserved, no reset or repeated bootstrap, no production/deploy. This restates concrete operations already within §5 synthetic-runtime authority after automatic approval rejected the exact original Docker157 caller before effects. It does not waive platform approval or authorize indirect/alternate-launcher bypass. Present this new explicit owner answer to automatic approval for the same reviewed bounded caller; another rejection remains a platform blocker and must be reported. Fresh resource/path/ownership/grant guards, cleanup and all product/security/acceptance criteria continue unchanged.

Affected scope: retained coherent8 Cloud stand and dependent real PWA/TMA scenarios; independent CI/source checks continue. Supersedes only Q010 missing-explicit-startup-authorization branch, no product rule.

Contract previousSHA 75ee55c6caedd4ca2371818d71031f71d1969f3bcf06a7aa47a5689869acde27; currentSHA 44172878e580f00712d9e13a796cfd181a3b2456c034422318fc2433e98ff78c.

## T007 — 2026-10-13T02:13:41.634038+00:00 — one-case closed public Playwright step diagnostics SOURCE preparation

Reversible checking choice within existing D011/real-client acceptance, not owner product rule or TASK-CONTRACT amendment. Original367 timedout with stageUNKNOWN; source375 serviceWorker.ready is only hypothesis. OWN opt-in config may add ONLY previously absent public onStepBegin/onStepEnd methods to canonical SafeReporter in its isolated checking process, explicitly declared/pinned/reviewed. Assert callbacks absent/failclosed if present; do not override existing guard/output/error/status methods or Root bytes. Read only owned data-descriptor static source location/numericline/category and bounded begin/end sequence; never title/args/error/body/URL/IDs/cacheentries. Existing assertions/clocks/timeouts/privateartifact protections remain. Sourcepacket/meaningful closed-output controls/independentprivacyreview/freshRootgrant required before any runtime. No new SW probes initially. This is not permission to bypass reporter identity guards; changed runtime observation scope must be declared in grant. No oldgrant reuse/standdeadline extension.

## T008 — 2026-10-13T04:01:52.169985+00:00 — compatible signed journal semester RPC technical fix scope

Real single journal503 Academic UNAUTH plus completeSource chain identifies proposed GetSemesterState directedSchedule-only/TLS misuse; real Netty causal counterexample still required. Author source-plan verified actual current accepted journal permission VIEW_STATS OR MARK_ATTENDANCE; Root hypothetical VIEW_JOURNAL wording corrected, no new grant. Root approves additive internal GetHeadmanJournalSemesterState(group_id=1,semester_id=2)->existingSemesterStateResponse using original signed actor, live own-group capability and accepted semester scope. Existing Schedule method/TLS/servicecredentials unchanged, publicREST/DB/migrations/old proto fields stable. Related HeadmanStatsService detail state call may use new boundary after its existing VIEW_STATS gate. /root/roster_academic_auth_backend sole proto/Java/test writer in isolated481 WT; Root sharedcontract decision/integration owner. No runtime yet; realwire before/after, freshDB rights/revoke/foreign/archive/writeBlocked and relevant mutation then independentreview/currentreceipts required. Technical implementation choice within accepted behavior, not owner newproductrule/TASK-CONTRACT amendment.

## T009 — 2026-10-13T04:34:33.539343+00:00 — bounded untrusted diagnostic hints Source scope

Root approved Source-only at most one standard reported serialized-class hint plus one finite static public-frame hint through existing annotations/decoder; stage UNKNOWN/http0, no new result fields/guard or status weakening. Descriptor-bounded message singleline512; bounded stack8192/finite lines RAM only, no rawname/prototype/getter/toString/path/stack/body/ID/value hash output. Forged exact frame stays an unauthenticated reported hint, never proof of cause/operation/success; hook END alone is not success. Source snapshots/current15 classifier/private guards/sanitizer/assertions preserved; coherent controls/independentprivacyreview/fresh native plan before execution. Technical checking choice within D011, not owner product rule or TASK-CONTRACT amendment.

## T010 — 2026-10-13T07:43:54.751511+00:00 — focused Auth outage classification sensitivity sequencing

P06actualrecheck61ee/3106/9fcb ACCEPT finite1PGcase. Pending typed-outage503→401 Source fault distinctB013; original/mutantonlyscratch noWTchanges/native. Avoidnewepochhandshake: REDonePG boundedcall+finallyexactSource/freshcompiledrestore, validatefirstcleanup/source/class; then separateunchangedGREENonePG callback/NEWRootgrant+namespace afterretainedforeignbaselinejoin. Eachobserverperrun existingPG1cap, no capraise/no twoPGoneledger/newframework. REDfailsstatusbeforeeffectasserts so noJWT/dataeffectsRED Source-qualified; restoredGREEN provesall. This is a reversible verification process choice inside the accepted test scope, not a new product rule or platform-bypass authorization. Independent review/fresh grants required; B013 remains blocked.

## 2026-10-13T13:28:25.553407+00:00 — bounded artifact reader normal network context

Actual03 exact URLError at URL_OPEN plus c5af explicit environment Source drops standard HTTP(S)/NO_PROXY and CA context that normal download reader retained. Does not prove actual missing key or nested reason/product cause. New own Source sibling may copy only HTTP_PROXY/HTTPS_PROXY/NO_PROXY and lower-case counterparts plus SSL_CERT_FILE/SSL_CERT_DIR from normal inherited context in RAM, never output key presence/values/hashes. Preserve certificate verification, URLs/auth/allowlist/18pins/caps/call/observer; no arbitrary env, credentials, proxy bypass, launcher/platform bypass, product/test replay. Source inverse proof/targeted independent review then separate fresh grant; originalc5af immutable.

## 2026-10-13T13:52:30.726577+00:00 — one official existing-job log read capability

Root independently opened all3 compact Sources and prior existing parser/consumer. Single own-data TextContent selector/ASCII JSON fidelity/strict closed decoder align with accepted privacy. Official connector standard read permitted by TASK§5/§8; transport allocation/cancellation/runtime PID inherently unmeasured and cannot meet prior CLI-specific hard bounds. Root accepts this explicit connector-capability qualification for one fixed existing-job read; no claim512MiB/45s transport enforced. Exact32MiB postresponse/consumer limits and fresh resource/time rejection/private controls unchanged, no original API/log/raw output yet. Source consumer still UNBOUND so no live grant until concrete minimal pinned own-file admission/transfer/consume/cleanup joins ready. No newproduct/ownerpolicy/platformbypass.

## 2026-10-13T14:46:40.529356+00:00 — original artifact fresh official reference

Originalold403reason/equalityUNKNOWN. Fresh normal SDK sameauthorizedartifact metadata/TLS/URLallowlist supplies currentreference; unavailableoldRAMcomparison is technicalhold not product/ownerdecision or platformapproval denial. Root removes extra comparisonrequirement ONLYfornewgrantexactfreshvalidatedreference alreadyobtained (no secondSDK/headers/authbypass/contractcapchange), no claimURLdiffers oroldexpired. Sourceparent/child/18pins unchanged; sourceXML/result stillnotread. NewseparateRootgrant afterholdsafeabsenceaccount required; oldgrants/holdreceipts preserved.

## D013 — Owner critical-path priority, received 2026-10-13

Supersedes D011 sequencing only where incompatible; original goal/scope/DONE/WAITING, models, security and independent review unchanged. First diagnose current CI/Coverage/Unicode FAIL from concrete evidence, classify product versus oracle/fixture versus environment, correct minimally and pass affected normal CI; no unchanged repeat. Then genuine browser headman/helper journal with actual backend containing T008 RPC fix, roster/accepted operations/negative live-rights boundaries. Then remaining matrix, reuse sufficient unchanged checks without promoting incomplete families. Reduce new documents/copies/packets/receipts/checkpoint commits to necessary reproducibility/security/recovery; retain existing historical evidence and shared ledgers, no duplicate registry/framework. Checking changes only for concrete blockers; return promptly to user scenario. Safe completion/cleanup of current operations before queue change. No new product/deploy authority, threshold weakening or platform-control bypass.


---
## D014 — Q011 explicit local Node checking approval

Owner answered Q011: «Подтверждаю эти конкретные локальные проверки». Received UTC 2026-10-13T20:29:47.870838+00:00.

Explicitly confirms the already reviewed package in isolated worktree1fd53710: formatter apply/check of artifact-privacy.ts and existing privacy.control.spec.ts, existing TypeScript check, unchanged browser-free artifact privacy controls with synthetic markers and a short baseline implementation control. No browser, Docker, network, server startup, production/deploy or real credentials. This restates authorized TASK sections4/5 after automaticreview rejected the exact Node caller for ZIP/delegatedcontext mismatch. Present this trusted owneranswer for the SAME reviewed caller with fresh resource/time/source grant; no alternate launcher/indirect execution or approval bypass. Another rejection remains a platform blocker. Original goal/scope/DONE, B013 and independentreview/security constraints unchanged.

2026-10-13T20:30:33.766670+00:00: D013 process clarification applied: one coherent CAP+minimumFIRST3 diagnostics push/currentnormalCI; hashes/granttimes/formatparameters are notnewreview scopes. Use existingmatrix/CURRENT finite remainingcriteria andexistingevidence, no newfullaudit/wrapper/registry/doccleanup. CURRENT pointer only; historyonceattempts/evidence; preservemandatorygates/B013/security/autoReview.

2026-10-13T20:40:16.514650+00:00: Owner clarification broadstandingauthorization applies necessarycurrenttask diagnostics/fixes/acceptedfunctions/dependencies/syntheticchecks/format/build/CI/designatedbranch commits-push-draftPR/usefulagents withinactualslots/resources. No repeatedpermission foralreadyauthorizedactions; realplatform/security/solewriter/review boundaries remain. Correctuserbehavior/data/rights outrankgreenindicators; mandatorygatesnotweakened. No unspecifieddestructiveproductionaction/deploy/merge authorized; preparedconcretechecks/rollback andOwnerpresentation precedeanynecessaryproductionproposal. D013simplification/currentonepackage unchanged.

## T011 — current task technical correction: semester educational clock

Q05 independent future-semester runtime preparation is Source-blocked: SemesterService and SemesterActivationJob use ordinary Clock while the existing isolated fixture overrides only EducationalTime. Production ordinary Clock currently uses Europe/Moscow, so this mismatch alone is a fixture-seam limitation, not an established production defect. Root assigns the same recovery author a bounded domain-clock implementation scope: existing EducationalTime/fallback constructor pattern for semester educational dates and activation, keep real/security Clock bean and public API/proto/schema unchanged. Direct LocalDate.now() checks at SemesterService140/238 are a separate current Moscow/JVM-zone hypothesis; prove a meaningful accepted-date counterexample before a product-defect verdict. Existing direct-test constructor compatibility, coherent affected checks, independent review and current real user flow remain required. Q05 one-file WIP stays preserved/excluded until the clock package is verified; no new product policy/owner authority/clock override for security/production operation.

## T012 — HEADMAN manual-request budget increase, technical contract within closed R-2/AT-11

2026-10-14T10:42:55.890056+00:00: Root opened closed source policy/AT-11, accepted R-2 and current audit/budget carriers. Freeze the existing proposed GET/POST `/attendance/requests/students/{studentId}/late-checkin-budget`: GET current active semester snapshot `{studentId,semesterId,limit,used,remaining}`, POST `{semesterId,increase,requestKey}` returns committed snapshot; positive int32 addition, sum overflow409, invalid400. Current HEADMAN own-group only, authoritative fresh membership/headman and semester checks before each operation/replay; no helper grant inferred. One Mongo transaction with existing semester fence updates only limit and appends permanent audit/outbox; deterministic actor/key audit ID is replay receipt, exact repeat unchanged/payload mismatch409, used never reset. Ordinary audit/security clock retained. Existing HeadmanGroupScreen selected student shows remaining/increase/current readback using design skill/components. q06_replacement_cause sole assigned product/API/DTO/client writer in isolated WT from4058; Root common contract decision/integration owner. No new proto/schema/index/config/lockfile/framework expected; report concrete necessity before expanding. Real Mongo atomicity/replay/concurrency/rollback/rights and actual Vue path, meaningful regression sensitivity and independent whole-package review required. This implements an already accepted missing carrier, not a new owner product rule. Root current CI-failure diagnosis/publication remains critical path; isolated source preparation does not delay it.

## T013 — Accepted mobile homework periods, technical scope resolution

2026-10-14T11:57:37.149323+00:00: Canonical JS-STUDENT-20/21 (knowledge/job-stories775–789) and explicit mobile inheritance in release-surface-matrix16/figma-spec-mobile3–12 have no period/order repeal. Implement accepted day/week/month, defaultToday/one-actionreturn and within-selected-day unfinished/lesson-order/completed-bottom/focus in current ready shared Vue homework, preserving current components/tokens and applying designskill. Missing mockup controls do not exclude the accepted operation; no Q013 permission is required. Existing server range/aggregate proposals are not automatic new API authority; first use actual supported compatible read APIs, no speculative endpoint. Root assigns current requests developer isolated Sourceplan first from its stable d109; no Angular/newweb/commonconfig/API/schema rewrite. Current testauthor retains existing E2Espec until Root integrates/publishes reviewed packet; later transfer explicit, oldassertions preserved with legitimate explicit period selection if necessary. This is implementation of accepted stories, not a new owner rule or TASK-CONTRACT amendment.

## T013 clarification — independent inline material links

2026-10-14T13:00:32.255319+00:00: Existing accepted JS-HEADMAN-16 canonical589–591 (SHA87af4670d5d8f996d940de9a56815cacc4befeaf90e5ea406a60871329bd4981) states one primary link and other links in description clickable; same LinkifiedText is accepted for student104 by wireframe114. A malformed primary link does not cancel separate valid HTTP(S) text links. No new product rule: preserve independent safe-link admission/escaped text/unsafe-main denial. The new B checking oracle that prohibited all anchors on an unsafe-primary card conflicts with this source; replace only that blanket assertion with exact safeinline href set plus retained script/img/unsafeaction/execution prohibitions. Originalfour material/optimistic/order assertions remain. Independent Source resolution q07_client_review; final implementation/hunk review and actualCI still required.


## D015 — 2026-10-14T17:27:58.313140+00:00 — Q013 owner-native client acceptance

Owner answer to Q013: «я проверю это сам на локальном тестовом стенде пока просто логику и код проверь что он согласован корректен и должнн работать в различных ситуациях».

D015 applies only to the native/device/provider part named in Q013: opening TMA from Telegram, real Web Push while PWA is closed/backgrounded, and OS installation/icon/standalone behavior. The owner will execute that part on the local test stand. In this Cloud task, verify the implementation, consistent accepted rules, relevant successful/negative/boundary states and available isolated synthetic/browser/server mechanisms for those paths. Do not present mocks, Source reading or synthetic host events as actual Telegram/provider/OS delivery or installation.

The native runs are transferred to owner acceptance; their absence in Cloud no longer blocks completion of this Cloud checking scope after its retained code/logic criteria pass. Keep their status OWNER_MANUAL_PENDING and give the owner a finite checklist tied to the reviewed revision. This is neither native PASS nor a production-readiness receipt. Remaining genuine server/PWA/TMA browser, data/rights/recovery, independent review, meaningful sensitivity and final gates remain required.

Supersedes only the requirement that Cloud itself obtain native capability/receipts for the Q013 branches (existing finite E14-I1/H2). It does not waive the behavior, defer all push/bot/client logic, allow production/private bot access, authorize merge/deploy, change other product rules or freeze independent work.

TASK-CONTRACT previousSHA 62afda54233d3f27e894d0108c6d12a99a217f11aa686a33550739a93d6c75ed; currentSHA 4f45e414322d3d41192c52baf244bfe6e96a2b76b3597ad714f79674eb0f2757. Canonical: docs/product/decisions/2026-10-14-owner-native-client-acceptance.md.


## D016 — 2026-10-14T17:34:08.654748+00:00 — Q012 replacement-only

Owner answered Q012: «Разрешить только замену на другого старосту (рекомендация по позднему макету)».

D016: the administrator may replace a group's current headman with another eligible headman through the accepted assignment/transfer operation. A standalone administrative request to remove the current headman flag without appointing a replacement must be rejected by the server; the client cannot be the sole enforcement. Replacement continues to revoke the previous headman/helper authority and preserve required membership/history/atomicity rules.

This resolves the specific Q012 ambiguity concerning an administrative isHeadman=false/no-replacement update. It is not a new permission to retain revoked security authority, change unrelated account archive/restore/group-transfer lifecycle rules, rewrite the 12-grant catalogue, or add a new web frontend. Review concrete adjacent paths against existing accepted rules rather than broadening this decision to all automatic authority cleanup.

Supersedes the old standalone-removal behavior and old tests only for this branch. Existing replacement, foreign-scope, live revocation, transaction/history and terminal-state boundaries remain required. API/proto/schema compatibility remains; use the existing typed conflict convention with no partial mutation. Canonical JS-ADMIN-07 plus this direct owner decision now resolves candidate historical wireframe contradictions.

TASK-CONTRACT previousSHA 4f45e414322d3d41192c52baf244bfe6e96a2b76b3597ad714f79674eb0f2757; currentSHA d9132dda1bada9670a01abc5d77649f73b4d794779a56ae0c3b767bf7b79d988.


## D017 — 2026-10-14T17:41:54.427369+00:00 — Q014 temporary existing R install icon

Owner answered Q014: «Использовать существующую R как временную иконку (рекомендую)».

D017 accepts the existing green-blue rounded R RutTrack SVG as a temporary PWA installation icon. Exact chosen source: frontends/landing/dist/favicon.svg, SHA256 85223ceb39a2ebb25dcc77ddb5287ea27b982c2ce5d2cf444a6e3507e573b9e8. Preserve the existing image; copy it byte-exact into the tracked PWA public assets and derive conventional installation sizes192/512 and Apple touch180. Add the manifest/index references required by the existing accepted PWA installation path. No new visual design, brand rewrite, Figma publication or Angular modification is authorized. This temporary asset does not decide the owner's eventual logo.

Supersedes only the missing accepted installation-asset branch of BRAND_DIRECTION's later-logo placeholder for current PWA. D015 still assigns native/device installation acceptance to the owner; Cloud verifies source, dimensions/declarations, build and applicable logic. Do not claim actual OS installation from asset generation. Keep server/browser/data/rights/gates and other accepted behavior unchanged.

TASK-CONTRACT previousSHA d9132dda1bada9670a01abc5d77649f73b4d794779a56ae0c3b767bf7b79d988; currentSHA aefc314a17f230a6c54567c06533a78116342206116c04ae091460107e652fa7.


## T014 — unknown persisted Attendance enum transport boundary

2026-10-14T19:03:16.924871+00:00: Technical implementation choice within TASK-CONTRACT unknown-code/fail-closed/no unexpected5xx rule, not a new owner product decision. Apply existing Attendance typed409 data-conflict convention with a fixed nonsecret message to unknown persisted finite Mongo enum codes; known stable codes, optional null, authorization and stored data remain unchanged. Limit to the Mongo enum readers and existing domain exception convention, not a blanket conversion/runtime catch. Public request validation remains separate. One genuine HTTP known→unknown/no-write→restored-positive test and relevant previous-reader counterexample/restored GREEN plus independent review are required before acceptance. Current generic500 is Source inference only; do not claim actual product defect before reproduction. Assigned c63_enum_http_boundary sole reader/test writer in isolated493 worktree; commonAPI/config/CI/rootdocs remain sole assignedowners.


---
## D018 — Q018 staged mobile theme validation

Owner exact answer: «Пока я не отвалидировал светлую тему и её пока нет но в теории оно всё должно работать а светлую тему потом подключим пока просто сделай чтоб всё работает а сам светлый интерфейс я попозже сделаю (но чтобы переключение темы к этому времени уже работало). ты можешь один экран сделать светлую тему чтоб валидировать».

Prepare functioning light/dark/system preference switching and persistence for future connection of validated light UI. Current global PWA/TMA appearance remains dark until owner validates the light interface; do not silently publish an all-screen light redesign. The owner permits one existing screen to demonstrate and validate the light theme; choose the appearance/settings screen with existing accepted semantic tokens and components, no new design system or Figma publication. Explain the current staged limitation in that screen. Verify switching, persistence/reload, system-mode resolution and scope isolation; the remaining screens stay dark. The previous AuthA visual oracle expecting immediate application-wide light appearance must follow this new staged scope, while password/security assertions remain mandatory.

Supersedes only the unresolved Q018 all-screen selected-theme versus fixed-dark ambiguity and conflicting visual oracle. Angular, server permissions, native acceptance D015, security/data criteria and production prohibitions remain unchanged. Scope: shared mobile theme, PWA/TMA adapters, appearance screen, sufficient affected checks. This is a faithful operational reading of the owner answer; no approval of a complete light interface is inferred.

Canonical: docs/product/decisions/2026-10-15-mobile-theme-staged-validation.md.


---
## D019 — Q017 multiple lessons per EXCUSE ticket

Owner exact answer: «Несколько пар одним тикетом «у» (рекомендую по принятой истории)».

An EXCUSE request may contain several selected eligible lessons and has one indivisible ticket and one headman decision. Preserve accepted eligibility/date-range/attachment/cancellation/history/authority rules. The full existing requests form already has multi-selection; the earlier question premise that all mobile forms select only one lesson was corrected in questions.md. This decision confirms accepted cardinality and does not itself establish a missing implementation. The contextual Today route may preselect one lesson; it does not repeal full-form multiple selection. Manual absence request remains its separately accepted single-lesson behavior.

Supersedes only contradictory candidate later single-pair EXCUSE layout/cardinality ambiguity. Require actual client/server evidence for several selected lessons sharing one ticket and one decision; no speculative new aggregate or endpoint. No change to unrelated product rules or permissions.

Canonical: docs/product/decisions/2026-10-15-excuse-multiple-lessons.md.


---
## D020 — Q016 separate login and current context

Owner answered: «Сохранить вход и отдельную загрузку контекста (рекомендую)».

For current acceptance retain the compatible existing login response with tokens, followed by separate authoritative session-context reads for profile, roles and active semester. The client must remain in a loading/non-operational state until required context is loaded; failure or revocation must not expose a protected screen or enable operations. Every server action still checks current authority; client context is not a grant. One combined response is not required by this acceptance, and no new bootstrap endpoint or Auth DTO expansion is authorized by the old story alone. Verify successful bootstrap plus partial failure/revocation behavior through existing real PWA/TMA/server paths.

Supersedes only the unresolved combined-response assembly clause in historical JS-SYSTEM-14 for current acceptance. Token/session, grants, password, privacy, compatibility and other mandatory criteria remain unchanged. No new web frontend or production operations.

Canonical: docs/product/decisions/2026-10-15-login-context-separation.md.


---
## D021 — Q019 available offline browser capability and owner Safari acceptance

Owner answered: «Chromium offline + WebKit online; Safari offline проверю локально (рекомендую)».

For current Cloud acceptance use genuine Chromium PWA offline/read-only/reconnect checks and genuine WebKit online checks. Transfer actual Safari offline acceptance to the owner local stand, status OWNER_MANUAL_PENDING, not PASS. Preserve the original production offline scenarios, original WebKit failures and exact bb minimal direct-cache-control failure as evidence of an unavailable Cloud emulation path; do not assert actual Safari fails or is unsupported. The isolated control proved only that Playwright WebKit offline reload rejected outside production PWA after successful controlled online cached-document loading.

The mandatory ordinary CI WebKit selection may now cover the retained online session/admission/revocation/recovery scenario instead of the two failing offline-emulation scenarios and their diagnostic control. Keep Chromium offline scenarios and all unrelated gates/assertions. Keep diagnostic/production test implementations unchanged and document their exact historical results; do not make a failing diagnostic look like a passed product check. No test skip or relaxed assertion may replace remaining mandatory client/server behavior. Current Cloud W1 acceptance consists of the available engine checks plus source-qualified known limitation and explicit owner handoff. Actual Safari offline remains a finite local manual check on the final reviewed revision.

Supersedes only the requirement that Cloud prove offline behavior through this unavailable WebKit emulation path. D002 behavior, Chromium actual offline and reconnect, current WebKit online, data/privacy/session rules and all other criteria remain unchanged. This is no production-readiness or deployment approval.

Canonical: docs/product/decisions/2026-10-15-webkit-offline-owner-acceptance.md.


---
## D022 — Q015 current homework management includes nonauthor deletion

Owner answered: «Разрешить удаление любого ДЗ своей группы при MANAGE_HOMEWORK (рекомендую)».

A current headman or helper with effective MANAGE_HOMEWORK may delete any current homework of their own group, regardless of authorship. Keep original creator attribution; record the actual deletion actor, immutable history and audit according to the existing delete/archive operation. Every command, replay and client action requires current authority and own-group scope. Retain existing archived/terminal/read-only, semester, revision/concurrency, placement, idempotency and event/outbox rules. This does not permit foreign-group access, removal of historical audit, hard data purge or a new permission code.

Supersedes only the previously unresolved nonauthor DELETE branch of JS-HEADMAN-17 and any author-only check conflicting with this accepted rule. Existing edit permissions remain. Implement only confirmed missing behavior after Source diagnosis and verify owner/nonowner with live grants, foreign scope, revocation, history/events and appropriate real client/server evidence; do not infer implementation completeness from this decision. No production/new web/Angular change.

Canonical: docs/product/decisions/2026-10-15-homework-nonauthor-delete.md.


## D023 — Q022 — 2026-10-15T13:05:58.831909+00:00

Owner exact answer: «Он видит данные по своим группам по тем предметам где он ведёт (не не видит данные посещаемости по предметам группы где он не ведёт и где он не добавлен как пр5еподаватель)».

Teacher attendance data and summaries are restricted to the teacher's currently authorized groups AND subjects to which that teacher is assigned. Attendance for another subject of an otherwise authorized group must not be returned, aggregated, exported or exposed through drilldown. Apply authoritative current assignment/grant checks on server reads and downloads; client filtering is insufficient. The cross-group summary aggregates only those allowed subjects in each allowed group. Current accepted historical reading within an allowed subject remains; this answer does not impose physical lesson.teacherId equality or grant access to unrelated subjects. Retain existing explicit lesson-type filters and current revocation/semester/history guards; do not invent a new per-type permission from this answer.

Supersedes the Q022 unresolved cross-group measure and any earlier whole-group reading interpretation insofar as it exposes attendance of unassigned subjects. Review existing selected-group reads and exports for the same boundary. No schema migration, new web frontend, Angular change or production operation is authorized. Accepted existing all-own-groups summary may now be implemented with this measure and compatible contracts; meaningful own/other-subject/current-revocation checks and independent review remain required.

TASK-CONTRACT previous SHA256 67b700e171a3b8c0d1f859df20f975f9fb231ace031e2e6cdb49f82c18a7c37a; current SHA256 c6f23c55e318d2e8efcaf5b217de75be809ff9ab03e8f6a3be6f42cd9a96de12.


## D024 — Q021 — 2026-10-15T13:05:58.831909+00:00

Owner exact answer: «PWA и TMA сканируют QR код генерируемый при входе в браузере в веб версию и сколкьо кгодно сеансов может быть. вход в PWA только по паролю или oTP а в TMA исходя из привязанного ТГ id».

The intended QR operation is approval from PWA/TMA of a QR generated by browser-web login. It is not a profile function that generates a transfer QR from an already authenticated session to attach another mobile device. That candidate DeviceLinkQr outgoing-profile operation is superseded and is not required. Preserve compatible existing server QR issuance/proof/approval/exchange, ownership, single-use, expiry, freshness and abuse protections; a QR cannot bypass current authenticated mobile authority.

PWA admission is by password or OTP. TMA admission is by the current bound Telegram ID through validated Telegram initData and current account/grant checks; an untrusted Telegram ID alone is not authentication. Multiple simultaneous sessions are allowed without a product session-count cap; ordinary expiry, revocation, password security events and technical request/resource protections remain. Do not infer one current session per user or revoke earlier sessions merely on a new login.

Confirm actual available PWA/TMA QR approval carrier against accepted sources; this answer does not prescribe an embedded camera UI or a new device-link API. New web client implementation remains outside Cloud scope; coordinate compatible server contracts through their sole owner. Native Telegram/physical-device operations retain D015 owner acceptance, not Cloud PASS. No secrets, production/deploy or Angular changes.

TASK-CONTRACT previous SHA256 c6f23c55e318d2e8efcaf5b217de75be809ff9ab03e8f6a3be6f42cd9a96de12; current SHA256 44a0bf859f999617aa19ca7ab1f25ae9ef9fd3e5895879388d2450419e5ed375.


## D025 — Q020 — 2026-10-15T13:05:58.831909+00:00

Owner exact answer: «Включить сейчас; отдельно согласовать состав и экран».

The distinct administrator group-by-semester average attendance report of JS-ADMIN-12 is included in this current task. Do not defer or mark it EXCLUDED. The owner explicitly requires a separate agreement on its contents and screen before dependent implementation. Prepare a concrete minimal proposal using current components, accepted metrics and existing grant catalogue, and ask the owner to select the carrier/fields/access. Until that agreement only the dependent report implementation and acceptance remain BLOCKED_OWNER; independent product/checking work continues.

The canceled red-zone/threshold report remains canceled. Existing dashboard/activity, student/headman/teacher statistics, ordinary registry exports and their accepted proofs remain separate. No new web frontend, speculative metric, new permission code, Figma publication or production operation is authorized by this answer.

TASK-CONTRACT previous SHA256 44a0bf859f999617aa19ca7ab1f25ae9ef9fd3e5895879388d2450419e5ed375; current SHA256 72b029e86fafbbfbe75b96d81f2997d367b03d8fb0240ec84d887a5edcc85612.


## D026 — Q023 — 2026-10-15T13:10:09.499816+00:00

Owner exact answer: «Принять этот экран и состав (рекомендую)».

Implement the accepted current PWA/TMA screen «Посещаемость групп», opened from the administrator dashboard. It has a semester selector, group search and sorting. The group table contains group, attendance percentage, counted lessons and counts «+ / н / у». Compute on the server by the existing accepted statistics rules, excluding canceled lessons; distinguish no data from a genuine zero percentage. Export the selected report with its active filters in the existing five formats: HTML, PDF, DOCX, XLSX and PNG. Do not add the canceled red-zone/threshold report or student personal-data drilldown.

Use current accepted mobile components, tokens and shared PWA/TMA features/adapters; apply rutcampustrack-design before UI implementation. This resolves D025/Q020 screen and contents and authorizes the missing accepted function in this Cloud task. New web frontend and Figma publication remain outside scope. Compatible API/data aggregation and current authorization, asynchronous export/delivery/download behavior must be specified by their sole writer and tested by observable real paths. Do not silently broaden unrelated dashboard or registry exports.

Canonical: docs/product/decisions/2026-10-15-admin-attendance-report-screen.md.
TASK-CONTRACT previous SHA256 72b029e86fafbbfbe75b96d81f2997d367b03d8fb0240ec84d887a5edcc85612; current SHA256 3ab8101d49663c9d131a3ca92973a31da06ad93f6468eb6025aa0eb1e2a6a59a.


## D027 — Q024 — 2026-10-15T13:10:09.499816+00:00

Owner exact answer: «FULL + LIMITED с DASHBOARD_VIEW (рекомендую)».

The new group-semester attendance report and its export are available to current FULL administrators and current LIMITED administrators with DASHBOARD_VIEW. Without the live grant or after its revocation, the server must deny report reads, export initiation, status/read and downloads under the existing current-capability conventions. UI visibility is not authorization. No new permission code is introduced.

The report contains group totals only, without a list or personal data of individual students. This does not imply USERS_VIEW/GROUPS_VIEW/credential grants or change their existing separate scope rules. Preserve immutable export audit facts and current recipient/ticket privacy. Together with D025/D026, this resolves the dependent report product questions; implementation and sufficient real client/server/data/export checks, regression sensitivity by risk and independent review remain required. No production/main/deploy/new web/Angular authority.

Canonical: docs/product/decisions/2026-10-15-admin-attendance-report-access.md.
TASK-CONTRACT previous SHA256 3ab8101d49663c9d131a3ca92973a31da06ad93f6468eb6025aa0eb1e2a6a59a; current SHA256 a3e5d647657d0a4ea49adfbc0ee0432d4610ab312b1f4b86446826cf64015650.


## D028 — Q027: Retain authoritative attendance calculation; defer stored incremental counters

Recorded UTC 2026-10-15T19:59:41.591452+00:00. Owner exact answer: «Сохранить расчёт при запросе; отдельные счётчики отложить (рекомендую)».

For this current acceptance retain authoritative server calculation from persisted attendance data on requests. Separate stored counters maintained incrementally are deferred until measured load establishes a need and a separately agreed implementation is prepared. This supersedes only the explicit storage-strategy clause of canonical JS-SYSTEM-16:899 (reference job-stories.md SHA87af4670d5d8f996d940de9a56815cacc4befeaf90e5ea406a60871329bd4981). It does not defer correct server-owned percentages/counts, common denominator rules, current authorization, historical membership, exclusion of canceled/future lessons, null versus zero, events/atomicity or any confirmed user-visible performance defect.

Continue the compatible Teacher journal Table/Chart correction: metrics come from the authoritative server, rather than client division, with existing scope/paging/selection/privacy controls. Do not add a new aggregate store, cache architecture, queue or migration merely to satisfy the deferred storage mechanism. This decision changes neither production/merge/deploy prohibitions nor the other task criteria.

Canonical: docs/product/decisions/2026-10-15-attendance-aggregate-storage-deferral.md.


## D029 — Q025: Subject and lesson identity is local to a semester

Recorded UTC 2026-10-15T21:25:11.147945+00:00. Owner exact answer: «Ну вообще у нас староста каждый семестр создаёт новый id у предмета. между семестрами нет в целом никакой связи (кроме как у групп и преподов, остальное всё по id новое и привязано к семестру) у нас пока нет межсеместрового взаимодействия да и по предметной области никак пары не связать из разных семестров».

Accepted domain rule: a headman creates a new subject ID for each semester. Subjects, lessons and their related semester data do not share identity or interaction across semesters; groups and teachers can persist. Full subject deletion must target the exact subject and its own semester data, never match other semesters by subject name, group or teacher. This answer resolves the premise that one intended subject spans semesters; it does not authorize purging another archived semester or production data. Preserve historical data outside the exact accepted target. A legacy or inconsistent cross-semester reference must be detected and handled safely rather than silently broadening the destructive scope. Assess current schema/creation paths against this rule before choosing a compatible implementation or migration; do not claim current enforcement or runtime PASS.

Supersedes Q025 unresolved domain-identity premise and the assumption that cross-semester subject sharing is intended. The existing password plus purpose-specific bot-code, current headman authority, exact preview/confirmation, durable completion/retry and independent review criteria remain. No new mobile destructive screen is authorized solely by this answer.

## D030 — Q026: Preserve mixed-subject EXCUSE tickets when deleting a subject

Recorded UTC 2026-10-15T21:25:11.147945+00:00. Owner exact answer: «Сохранить заявку, убрать только пары удаляемого предмета».

For an EXCUSE ticket covering lessons of several subjects, keep the same ticket and the other subjects' lesson coverage; remove only the lessons belonging to the deleted subject and record the change in history. Preserve the accepted single ticket and single decision, existing attribution and remaining attachments under their current ownership rules. Do not delete the whole mixed ticket, split its decision, reset unrelated quotas or remove other-subject data. Sole-subject request deletion remains the separate accepted full-subject cascade; this decision specifically resolves the mixed-subject branch.

Supersedes Q026 unresolved mixed-ticket deletion policy. Implement only through the reviewed subject-scoped durable deletion protocol with current authority, password and purpose-specific bot proof. Prove exact remaining coverage/history and replay/concurrency behavior with real data; Source readiness is not acceptance. No production deletion, deploy, main merge or Angular/new-web work.

Canonical: docs/product/decisions/2026-10-15-subject-deletion-semester-and-mixed-excuse.md. TASK-CONTRACT previous SHA256 a9b56b2c5f1e18c418f41423d83fbf32c019f3f8b5208b293b5b9f96e60c0d81; current fa70b41b3bc6e152923e9901334119b157ed590b2f26eed4250a4e924d749c30.


## D031 — 17.10.2026: новый этап A01–A04/M01–M05, явный GO

Получено 2026-10-17 21:49 MSK (2026-10-17T18:49:58.701511+00:00). Активный полный контракт TASK-CONTRACT-20261017.md,57651bytes,SHA256614aef1b93ef103f1a9050a622ecf5a54881e80fb4a559044b4a1b8828ae052a byte-exactmatches original. StartingHEAD/remotePR2head753173688d633ca07991f4dd73bdcd2698f45d96; trackedWIPnone, existinguntracked preserved in startingcheckpoint attempts. Продолжить тотже workspace/назначеннуюветку/draftPR2; новый этап отдельно от прежнего WAITING_ENV. B013/14/15FROZEN и прежняя nativeownerприёмка сохранены. Требуются девять пакетов/условныеC01–03, включая actual18weekDOCX/broker/DLQ/boundedautoRetry/Grafana; alert-bot DEFERRED_LOCAL, Cloudtestreceiver required. Повтор старой общей кампании/production/main/newweb/Angular/reset/force запрещён. Ordinary checks/commits/push/designatedPRupdate заранее разрешены. Полный реестр328пунктов не нужен длястарта. Rootsole sharedDocs/integration/contracts; childrenSol6.1/high/forknone; actualUNKNOWN.


## D032/D033/D034 — 17.10.2026: ответы нового этапа Q028–Q030

Received UTC 2026-10-17T18:59:09.922619+00:00. D032/Q028 exact: «Староста — отчёт журнала». M04 reproduces actualHeadmanWeeklyDOCX 18weeks; currentJavaXMLZIP directHTTP isnotRabbitpath, originalcauseUNKNOWN; genuinebroker/DLQ separateactualconsumerpath required, do notinventWordbroker.

D033/Q029 exact: «рассчитай сам исходя из продакшен практик и моей предметной области и моей задачи я не особо понимаю что это и мб у разного сть разные границы и надо отдельно простраивать выступи как инжинер проектировщик продумай и скажи что ты предлагаешь и исплементируй (но адекватное и исходя из требований быстродействия и постоянной работы)». Root isauthorised todesign+explain+implement appropriate per-operation SLO/slow/alert thresholds using currentnorms/actualmeasurements/continuousoperation; no needrepeatownerapproval ofthoseboundedengineeringchoices. Notauthority fornewbusinessrules/deploy.

D034/Q030 exact: «кст хороший вопрос, я мб на claudflare подниму скрипт какой-то который раз в сколько то будет отправлять запрос чтобы проверить состояние. пока запиши в беклог что при дплое надо настроить и протестировать». Onlyoffhostwhole-machineprobe actualconfiguration+test explicitlydeferred todeploybacklog/DEFERRED_LOCAL; prepareboundedproposal/runbook/sourceconfig whereuseful, noCloudhostdeathPASS. Separatefuturealertbot DEFERRED_LOCAL unchanged; on-hostapp/dependencysignals/receiver/DLQ/report stillrequired. Supersedesactivecontractmissingexternalprobe-as-Cloudblocker ONLYthisspecificownertransferredcriterion. B013/14/15FROZEN unchanged.


## T015 — 17.10.2026: delegated SLO/correlation/recovery engineering design

2026-10-17T19:04:56.326977+00:00: D033 engineeringtargets: interactiveavailability99.9%/30d, read+loginp95≤2s/writep95≤3s; normal18weekDOCXp95≤30s toverify/refinewithactualmeasurements; dueasyncfinaleffect≤60sobjective99.5%/30d. Excludeexpectedvalidation/authz4xx/cancellation, countterminalonce notattempts; lowtraffic/missing/noData/terminalerrors distinguished. Promretention≥35d withsize+headroom, logs/traces14d bounded. These areproposedandimplementedengineeringobjectives, notobservedproductionSLA. Existingbusiness100%sampling/quiet22–08MSK respected accordingseverity. PublicGatewaymints canonicalUUID X-Request-Id beforestandardobservation; sharedoptionaltraceparent/request_id persistbeforeoutbox, nofaketraceparentfromlegacyUUID/noarbitrarybaggagecredentials. Sharedstandardtraceprivacyfilter removesrawURLquery/capabilitypath/credentials/exceptionmessage+stack/statusdescription whilekeepingerrorcode/type/IDs. Publisherboundedpolicy8attempts with5→300sexponentialbackoff andretainedFAILED+reason/alert; durableattemptduefieldifneedednewmigration, notinmemory. ConsumertransientDLQmax1automaticredrivecycleafter30s thenexisting3attempts;poisonretained/nohotloop. Actualordering/idempotency/ACK+confirm/return needrealproofbeforePASS. Technicalchoices withinactivecontract, no newbusinessrule/deployauthority.


## T016 — narrow publication recovery correction, 17.10.2026

2026-10-17T19:20:25.587749+00:00: Supersedes ONLY T015 proposed global publisher8-attempt/FAILED/due-storage policy. Current globalFIFO/PENDING has no aggregate ordering key; imposing terminalskip/due-selection would change ordering beyond this task. Retain durablePENDING/globalFIFO without speculative schemas/migrations. Bounded recovery applies to actual synchronous WORD request and transient consumer redrive; publisher confirms/returns and finite attempt-time/batch/lease remain required. Original report is synchronous HEADMAN weekly HTTP, not a durable WORD job. Source review R1–R3 corrections stay assigned reports author.


## D035 — 17.10.2026: durable background reports and responsive PWA/TMA

2026-10-17T19:21:47.113147+00:00: Owner full addendum saved TASK-ADDENDUM-ASYNC-20261017.md; SHA256 553ca473736b0b360ea4994c645996a78376f72546531c78d1d9ac2d528408e9. Supplements M04/M01–M03 (still9packages), preserves all WIP/branch/PR2. Required actual18week HEADMAN durableacceptance→navigateaway/reconnect→status/result; boundedqueue/retry/crashrecovery/currentauthority/retention; compatible oldsyncAPI. Reuse existing auth_export_jobs/ExportJobService/BFFworker and UI mechanisms before introducinganything. New owner load input300–400 simultaneouscheckins +~200active +notifications +3–7reports, VPSnotselected; notmeasuredcapacity. Sufficient priorresults retained. Authauthor assigned exportbackend, reports retainsrender/consumer/outbox, frontendauthor next afterdesignskillread.

## D036 — 18.10.2026: объединённый scope и роль, без дедлайна

2026-10-18T00:12:26.316961+00:00: ПрямойGO владельца на роль/выбранные54IDEA, TASK-CONTRACT-20261018.md normalized сохранён SHA8e3f4c72365684ac398c25849747135f0729ab3c35fa38effaf83d45e07135c1. Прежние A01–A04/M01/M05 6/9/e66 и открытые M02–M04 сохранены. Дополняет17contract; supersedes oldnoAlloy/новоеAuthAcademicразделение in scope, запрещает projectdocs/architecture rewrite; все production/main/FROZEN границы сохраняются. Goal tools read/update/create отсутствуют в доступном ALL_TOOLS: платформенныйGoal UNKNOWN/NOT_MODIFIED, falsecomplete нет. Доступная прежняя persisted цель — файлы17/CURRENT IN_PROGRESS; approvedextended goal saved, сначала прежнийпересекающийсяостаток, параллельноКТ1. После реального завершения старыхкритериев продолжение новойцели заранее GO, не второе подтверждение. Новаяматрицаv0 candidate/weights beforeimplementation; послеКТ1 freeze, explicitversion/denominator onscopechange. Уточненияowners и runtimeчислоагентов полезностью/фактическойcapacity, budget/лимитыаккаунта unchanged.

## D037/Q031 — 18.10.2026: TASK-CONTRACT/CURRENT — сохранённая цель

2026-10-18T00:20:24.607420+00:00: Владелец выбрал «Использовать TASK-CONTRACT/CURRENT как сохранённую цель (рекомендую)». Это разрешённый доступный механизм persistedGoal; отдельныйplatformGoalAPI невыдан/неимитируется. ОбъединённыйGoal теперь хранится в TASK-CONTRACT-20261018.md +CURRENT, прежний17контракт/async и всеего открытыекритерии остаютсячастьюцели; исходные6/9неfalsecomplete. После реального9/9 историческийэтапможно завершитьвфайлах; новыйscopeGOужеесть, повторногоразрешенияненужно. Supersedes D036толькоtechnicalGoal-surface pending, небюджет/лимиты/права.

## D038/Q033 — 18.10.2026: одноразовый initial-admin bootstrap как код

2026-10-18T16:41:33.681661+00:00: Владелец выбрал «Подготовить одноразовый bootstrap в коде (рекомендую)». Требуется operator-only команда без публичного HTTP endpoint/стандартного пароля, секрет из защищённого файла, отказ при уже созданном администраторе. Auth AAA/Academic profile границы и безопасный partial-failure/retry сохраняются; production-команду не выполнять. Новый обязательный результат будет отдельно взвешен до реализации в v3; старые v1/v2 не превращаются в процент расширенной цели. Изолированный synthetic baseline browserstand — отдельная подготовка теста, не способ создания реальных администраторов.

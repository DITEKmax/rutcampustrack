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

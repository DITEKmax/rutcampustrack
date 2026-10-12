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

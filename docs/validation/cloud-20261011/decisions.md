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

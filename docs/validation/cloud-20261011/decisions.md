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

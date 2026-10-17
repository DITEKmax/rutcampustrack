# Source reconciliation — 2026-10-11

Completed bounded read-only scope against frozen product HEAD `3908fad9624f97aec8891fc79338ceb6b0616bf3`. Only this receipt directory was written; no product/shared docs, children, Gradle, Docker, browser, external providers or background processes. Requested model/effort per packet; actual metadata UNKNOWN under owner exception.

This is a complete extraction of registered source rows with semantic reconciliation of all mapping rows and owner overlays. It is not the final executable acceptance matrix. Every live story remains UNKNOWN; source identity, test presence and historical stage closure establish no new runtime PASS.

## Inventory and source identity

- `scenarios.json` and CSV contain147 stable stories: ADMIN26/HEADMAN53/STUDENT37/SYSTEM16/TEACHER15. Verdicts145 UNKNOWN/2 EXCLUDED. Only JS-HEADMAN-10 (mass cancellation) and JS-STUDENT-04 (free attendance) are excluded wholesale with cancellation references. Partial cancellations retain accepted behavior.
- Canonical prose SHA256 `87af4670d5d8f996d940de9a56815cacc4befeaf90e5ea406a60871329bd4981` matches registry provenance. All147 stable IDs occur at cited original lines. `docs/product/job-stories.md` is old82-ID repository history. Latest JS-ADMIN-01 single creation/no email/one role-independent login replaces mass creation/email restoration/role-prefixed login, including retained JS-SYSTEM-01 prefix wording.
- `requests.json`:all175 rows exactly match current referenced source lines. Preparation145 open/30 closed is historical, not145 current owner questions/GAPs. Current verdicts171 UNKNOWN/4 EXCLUDED:AC-05/AC-06 under R-15, MAP-07 under R-05, MAP-06 under D004. 147+175 is not322 features.
- `decision-overlays.json`:all31 R decisions, full combined attendance §3 and request delta §4, all27 current dated product decisions except process-only delivery policy, and canonical login replacement clause:61 provenance overlays. They overlap stories and are not59 new independent functions. D002 offline, D003 TMA reuse and D004 legend deferral are included.
- `mapping-reconciliation.json`:all62 pointer identities verified;53 semantic candidates/7 direct associations/2 explicit R-25 associations. Candidates remain candidates.42 stories/29 requests have any association;105 stories/146 requests have none, even candidate. This limits trace completeness, not readiness.

Rows preserve source text, role/grant variants, client scope, prestate/action/result/forbidden cases, verification method and full owner overlays in `effective_criteria`. Use its precedence, not obsolete literal preparation clauses. DTOs, new endpoints/designs and runtime readiness are not inferred. `source-validation.json` has exact checks and ID lists; `grants-catalog.json` preserves current grant definitions and global constraints.

## Material criteria corrections

1. R-02 (`backend-conflicts.md:93`) separates unlimited/nonexpiring failed-geo automatic requests from manual petitions for past absence:five pairs/semester, consumed at submission including repeat after decline, audited headman increase by N. Old3/shared-limit wording is superseded.
2. R-11 (`backend-conflicts.md:187`) has one blockage:geo and manual petition denied; excuse ticket allowed. Blockage, active ticket and quota exhaustion differ.
3. Combined §3 (`backend-conflicts.md:526`) defines−5/+5-minute attendance window, auto-absence after tail, geo/confirmation overwriting manual absence, EXCUSED only through ticket, PRESENT protected from EXCUSED. Old manual three-status wording requires this overlay.
4. R-07 invalidates marks while preserving cancelled lesson/history; restore yields empty marks. R-25 permits only future transfer, keeps both generations and linked homework. Physical history destruction on cancellation and completed-lesson transfer are not accepted.
5. R-03/R-23:five table formats xlsx/docx/pdf/png/html, graph png/html; async rendering; filters/order in exported file. Older “xlsx not needed” is superseded.
6. R-15 removes common discipline catalogue/name suggestions and one-subject cross-group selection. Teacher multi-group statistics remain per-group ID scopes.
7. 2026-09-23 teacher decision expands attendance READ to the entire currently assigned group/history. It grants no mark writes, ticket decisions, credentials or unfiltered cross-subject ticket detail. JS-TEACHER-04 read-only remains accepted.
8. R-22 explicit headman removal is replaced by replacement/natural departure; draft reasons come as data. This affects AC-35, not dashboard-count request AC-38.
9. Dated lifecycle/time decisions:restore user without rights; archive barriers for all actors/background writers; only containing date expansion of stable archives; unbound homework archived next Moscow midnight; canonical eight time slots; history/IDs preserved.
10. Limited admin catalogue12 codes includes view dependencies, FULL-only ADMIN_ACCESS_MANAGE, protected active ADMIN targets, last-full-admin rule. All26 ADMIN stories have this overlay. VIEW_STATS helper journal/stats export grants no roster/credentials export; roster excludes passwords/Telegram/reset links.
11. D002:last verified STUDENT PWA cached schedule/homework READ until online admission. No offline commands/new attachments/role switching/TMA offline. Original story offline statistics must be resolved against current mobile release boundary, not automatically promoted.

## Traced candidate defect — no runtime FAIL claimed

JS-HEADMAN-16/17 permit HEADMAN and MANAGE_HOMEWORK helpers to publish/edit homework with next matching lesson selection. Current enum includes MANAGE_HOMEWORK (`AssistantPermission.java:6`), and HomeworkManagementAuthorization admits it. Schedule `LessonService.java:112` calls `requireHeadmanForGroup(groupId,"MANAGE_HOMEWORK")`; helper branch137 delegates to live Academic RPC. `AssistantPermissionAuthority.java:26–30` omits MANAGE_HOMEWORK from RPC_PERMISSIONS; line54 returns false for it. Root notified to allocate a writer for reproduction.

Reproduce on same synthetic group/subject/type/next lesson:durable headman positive; helper-only MANAGE_HOMEWORK positive; revoked or foreign helper negative. Save response/log/revision, then correct canonical authority if reproduced and independently review. Mocking helper authority as true would hide this regression.

## Mapping residual gaps

- MAP-07→JS-ADMIN-23/24/25 mixes room catalogue with upload/completeness/replacement of files; room catalogue is deferred.
- AC-05→JS-HEADMAN-12 mixes cross-group discipline identity with teacher/type assignment; former retired.
- AT-25→JS-STUDENT-12 mixes active ticket with blockage.
- AT-12→JS-HEADMAN-05 associates student personal request history with headman receipt/decision, a different audience.
- AT-11→JS-STUDENT-02 must not add limit to automatic geo requests.
- NT-03→JS-SYSTEM-10 is directly associated, but row says aggregate WS types are absent from story catalogue. Association accepts neither a new type nor mandatory transport by itself.

All62 preserve residual notes; none are promoted solely because IDs occur at cited lines.

## Question candidate and missing historical receipts

Q003 was answered A by owner, recorded in D004. Textual legend/MAP-06 criterion alone is EXCLUDED; both shared map stories remain UNKNOWN and their other criteria required. Original source question:does unfilled common icon legend belong to ready mobile map acceptance? JS-STUDENT-24 (`canonical:815–821`) requires text legend; MAP-06 (`backend-requests.md:328`) defers catalogue; `wireframes/student/105-student-map.md:140` leaves content/owner open; current MapScreen/CampusMapReadService has no legend model. R-05 deferred ROOM catalogue is separate. Choices:retain deferred legend until materials (recommend); include approved common legend now with owner content; confirm future-web-only legend. This source ambiguity is resolved by the owner; no icon set is invented. Main map lifecycle remains required.

Missing `.agent/evidence/admin-permissions-20261008/CONTRACT.md`; current catalogue/authority sources still define behavior. Three explicit dated-decision receipt links are absent, listed in `missing-historical-receipts.json`. Historical BACKEND-STAGE/CLOSURE are selected acceptance provenance; CLOSURE explicitly denies complete147-story/Figma/offline/native-TMA acceptance. Offsite provider/RPO/RTO are deferred by2026-10-02 decision. No current runtime or production-ready claim.

No new confirmed DEFECT/GAP/PASS. Scope complete; no resource lease or pending process. Root can integrate receipts and assign concrete execution packets.

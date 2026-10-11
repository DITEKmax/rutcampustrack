# Next checks — proposals, not executions

Frozen product revision3908fad9624f97aec8891fc79338ceb6b0616bf3. Lease shared synthetic runtime; record actor/data/clock/revision. Do not duplicate unchanged earlier suites.

1. Reproduce MANAGE_HOMEWORK authority mismatch. Open original LessonApi#getNextHomeworkLesson contract before issuing exact API command. Source command:

   `rg -n 'getNextHomeworkLesson|MANAGE_HOMEWORK|RPC_PERMISSIONS' services/schedule-service/schedule-api-contract/src/main/java services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantPermissionAuthority.java services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkManagementAuthorization.java`

   Compare durable headman, helper-only MANAGE_HOMEWORK and revoked/foreign helper. Save actual response/status and exact IDs. An always-true helper mock does not verify catalogue acceptance.

2. R-02/§3 Attendance:manual quota five/per-pair submit/retry; auto geo unlimited/nonexpiring; audited quota increase; blockage denies normal paths while excuse remains; geo overrides manual absence; PRESENT protected from EXCUSED. Fix Europe/Moscow clock at−5/start/end/+5 and separately verify UTC JVM. Preparation3/shared quota/manual EXCUSED must not define expectations.

3. Archive/restore/history:semester barrier denies all schedule/mark/homework/ticket writes including ADMIN/background; restore remains inactive; user restore adds no grants/membership/headman/helper/session. Archive extension contains old range/avoids overlap/preserves IDs and spring-cycle cutoff. Fault/retry/concurrency evidence only for distinct risks.

4. Teacher projection:active new teacher reads whole-group attendance history; inactive/foreign teacher denied; teacher writes and decisions denied; ticket detail trimmed server-side to subject. Use actual API/DB/browser path rather than old own-lessons-only expectations.

5. Limited admin12 grants with prerequisites; same-session revoke; create/edit/reset/archive/finaldelete specific combinations; self elevation and LIMITED active ADMIN target denial; last FULL preserved under concurrency. Helpers5 grants independently; VIEW_STATS journal/stats export allowed and roster/credentials denied. `grants-catalog.json` supplies source definitions; route grants require actual execution.

6. Export/map/notifications:five table/two graph formats/content/filter/header; async job/retry; roster privacy; immutable map versions/partial SVG+PNG/sanitization/shared-content delete; recipient membership/dedup/preferences survive cache eviction and defer on outage. External provider access is separate; queue-only evidence proves no real provider delivery.

7. Current PWA/TMA:user paths with real server; D002 read-only offline/reconnect admission; D003 permits reuse of valid initData within86400 seconds with fresh account/binding/grants authority each admission; no same-session promise. No new web implementation from source desktop layout. Unavailable installation/device/native-TMA remains UNKNOWN/BLOCKED until evidence or explicit owner exclusion.

8. Join story/request provenance without counting request rows twice.105 stories/146 requests lack registered association; preserve them in source inventory and select current operation per accepted criterion. Include helper and limited-admin variants; А–Ж are packages.

Exact Gradle/browser commands come from current build/workflows under root lease; no heavy command or runtime test was executed in this scope.

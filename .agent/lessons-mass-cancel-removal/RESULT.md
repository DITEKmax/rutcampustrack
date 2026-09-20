# Public mass-cancel removal — source map

Baseline ed9b63c9449b23fa3f5dbf2feb52cd8f9e9cfe83. Risk S2; implementation not authorized in this stage. Luna scout RELEASED after six direct files; lead opened owner decision and critical API/controller/service/test originals. No edits to product, tests or heavy execution.

Owner docs/wireframes/headman/118-headman-lesson-management.md:23,179–181 removes POST /schedule/lessons/mass-cancel from backend; individual cancellation remains. LESSONS-L5B-DESIGN-ACCEPTED-V3 addendum explicitly supersedes historical batch feature inventory.

## Exact candidate scope

All paths below relative to accepted checkout.

1. services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/LessonApi.java — imports25–26, public annotation/operation64–72.
2. services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonController.java — massCancelLessons55–57 delegates to service.
3. services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java — batch method160–193, batch request import13.
4. services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/MassCancelRequest.java — obsolete public request15–40.
5. services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/MassCancelResponse.java — obsolete response10–14.
6. services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiIT.java — old success test231–260, class description36.
7. docs/openapi/schedule.json — checked-in route746–800 and schemas2009–2050.
8. frontends/web-panel/src/app/api/generated/schedule.types.ts — generated route/operation/schema exposure.
9. frontends/pwa/src/api/generated/schedule.types.ts — same exposure.
10. frontends/mini-app/src/api/generated/schedule.types.ts — same exposure.
11. frontends/web-panel/src/app/api/schema.ts — aliases71–72.

Files1–6 opened by scout;7–11 located through targeted symbol search, not broadly audited. No concrete frontend invocation found by exact route/symbol search; dynamic clients not proven absent.

## Preservation and proposed acceptance

Lead confirmed API individual cancel/restore49–62, controller41–50, service111–156 retain existing behavior. Remove the dedicated batch method only; preserve LessonCancelledEvent, audit fields and history. Repository findByScheduleItemIdInAndDateBetweenAndStatusIn also has ScheduleGrpcServiceImpl caller106–107 and must remain. No database/schema/event changes or general lifecycle fixes follow.

Replace the obsolete batch success test with a focused authenticated removed-route regression: request cannot invoke batch operation, no lesson state/audit changes or cancellation events; exact expected MVC status selected from actual route mapping. Retain individual cancellation/restore coverage. Check OpenAPI route/schemas and generated types no longer expose operation; selected compile/type checks under later root permission. Do not run now.

## Contract implications / unresolved procedure

Candidate eleven-path cleanup includes generated contracts shared across clients, therefore assign one isolated writer after root freezes scope. Existing generation/update procedure was outside six-file scout and remains a bounded implementation-preflight question; do not invent manual generation policy or refresh unrelated contracts. Root decides exact generation command and check lease in implementation packet. Historical owner/source/review documents remain intact. No further scouting or implementation started.

# Phase 54: Headman Web Cabinet — Group Management + Subjects - Context

**Gathered:** 2026-04-09 (assumptions mode)
**Status:** Ready for planning

<domain>
## Phase Boundary

The backend WPAN-13 blocker is resolved (headman can call assistant endpoints), and the headman
has a working dashboard, can manage their group's student list with assistant CRUD, and perform
subject CRUD with teacher assignments.

Deliverables scoped to this phase:
- Backend: extend `RoleCheckAspect` to permit headman-scoped operations (WPAN-13)
- Angular: `/headman/dashboard` — group stats, today's lesson, pending tickets/late-checkin counts
- Angular: `/headman/group` — student list + assistant CRUD (create/delete with permissions)
- Angular: `/headman/subjects` — subject CRUD with teacher assignment

**Out of scope:**
- Attendance journal, excuse approvals, late check-in approvals, stats (Phase 55)
- PWA headman mode (Phase 56)
- Landing HEADMAN role section (Phase 57)

</domain>

<decisions>
## Implementation Decisions

### WPAN-13 Backend Fix (academic-service)

- **D-01:** Extend `RoleCheckAspect.checkRole()` to allow the method to proceed when:
  `(actual == STUDENT && requestContext.isHeadman() == true)` in addition to the existing
  `Arrays.asList(required).contains(actual)` check. This is the minimal change: headman IS
  a STUDENT (same enum), so checking `isHeadman` as a secondary pass is semantically correct.
  No new `UserRole` enum value is added — consistent with STATE.md architecture decision.
- **D-02:** The `X-Is-Headman` and `X-Group-Id` headers are already injected by the API Gateway
  (`JwtAuthenticationFilter.java`) and populated into `RequestContext` before the AOP aspect
  runs. No Angular interceptor change needed — the gateway is the header source.
- **D-03:** The `AssistantService.requireHeadman()` method (line 28) already checks
  `requestContext.isHeadman()` separately from the `@RequireRole` annotation. After the
  `RoleCheckAspect` fix, the controller's `@RequireRole({UserRole.STUDENT})` will pass for
  headmen, and then `AssistantService.requireHeadman()` provides the second layer ensuring
  only true headmen (not plain students) can write. Both layers stay in place.
- **D-04:** Same pattern for `SubjectController` — `@RequireRole({UserRole.STUDENT})` is on
  `createSubject`, `updateSubject`, `deleteSubject`. The `SubjectService.requireHeadman()`
  check (lines 28-31) already exists. After the `RoleCheckAspect` fix, these methods will
  work correctly for headmen.
- **D-05:** Write a unit test for `RoleCheckAspect` that verifies a `STUDENT + isHeadman=true`
  context passes `@RequireRole({UserRole.STUDENT})`. The existing test infrastructure uses
  Mockito mocks of `RequestContext`.

### Angular: Route Registration

- **D-06:** Replace the placeholder `HeadmanPlaceholderComponent` route at `/headman/dashboard`
  with the new `HeadmanDashboardComponent`. Add two new lazy-loaded routes under the existing
  `/headman` block (all with `canActivate: [headmanGuard]`):
  - `/headman/group` → `HeadmanGroupComponent`
  - `/headman/subjects` → `HeadmanSubjectsComponent`
  All routes get `data: { eyebrow: 'Староста' }` + appropriate `title` per UI-SPEC copywriting contract.

### Angular: HeadmanApiService

- **D-07:** Create `frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts`
  following the `StudentApiService` pattern exactly (Injectable, `inject(HttpClient)`, all calls
  are absolute `/api/...` paths, Bearer token injected by `authInterceptor`). Methods:
  - `getHeadmanDashboard(groupId)` → `GET /api/academic/headman/dashboard?groupId=X`
    (if endpoint exists; otherwise compose from separate calls — verify during planning/research)
  - `getGroupMembers(groupId)` → `GET /api/academic/groups/{groupId}/members` (paginated)
  - `listAssistants(groupId)` → `GET /api/academic/assistants?groupId=X`
  - `assignAssistant(body)` → `POST /api/academic/assistants`
  - `updateAssistantPermissions(id, body)` → `PATCH /api/academic/assistants/{id}/permissions`
  - `revokeAssistant(id)` → `DELETE /api/academic/assistants/{id}`
  - `listSubjects(groupId)` → `GET /api/academic/subjects?groupId=X` (or relevant filter)
  - `createSubject(body)` → `POST /api/academic/subjects`
  - `updateSubject(id, body)` → `PUT /api/academic/subjects/{id}`
  - `deleteSubject(id)` → `DELETE /api/academic/subjects/{id}`
  - `listTeachers()` → `GET /api/academic/users?role=TEACHER` (for subject teacher select)
- **D-08:** Researcher must verify the exact dashboard endpoint — a dedicated
  `GET /api/academic/headman/dashboard` may not exist. If not, the dashboard component
  fetches via `forkJoin`: group members count + today's schedule lesson + excuse ticket count
  + late check-in count from their respective endpoints.

### Angular: Component Architecture

- **D-09:** New components all in `frontends/web-panel/src/app/features/headman/`:
  - `dashboard/headman-dashboard.component.ts` — 4-stat grid + today's lesson card
  - `group/headman-group.component.ts` — student list table + assistants section below
  - `group/assign-assistant-dialog.component.ts` — MatDialog for assigning assistant
  - `group/delete-assistant-dialog.component.ts` — MatDialog confirmation for delete
  - `subjects/headman-subjects.component.ts` — subject list table
  - `subjects/subject-dialog.component.ts` — MatDialog for create/edit subject
  - `subjects/delete-subject-dialog.component.ts` — MatDialog confirmation for delete
  - `shared/headman-api.service.ts` — HTTP layer
- **D-10:** Dashboard uses the existing `StatCardComponent` from Phase 52 (`stat-card.component.ts`)
  for the 4 stat tiles. No new stat card component is created.
- **D-11:** Sidebar addition: add headman nav items to `SidebarComponent.allNavItems` array.
  Items have `roles: ['STUDENT']` (headman IS a student role) plus an additional `isHeadman: true`
  filter. Check how `filteredNavItems` computed signal filters — may need `isHeadman` awareness
  added to `NavItem` interface if not already present (UI-SPEC §0 confirms this change).
  Sidebar section label: "Старостат". Items: "Кабинет старосты" (`/headman/dashboard`),
  "Группа" (`/headman/group`), "Предметы" (`/headman/subjects`).

### Testing

- **D-12:** Backend tests: extend `RoleCheckAspectTest` (or create if missing) with a test case
  for `STUDENT + isHeadman=true` — confirms the aspect passes for headman-scoped operations.
- **D-13:** Angular: add spec file for `HeadmanApiService` with mocked `HttpClient` (mirror of
  `student-api.service.spec.ts`). No guard spec changes needed — guards already correct per
  UI-SPEC §0.
- **D-14:** All existing tests must pass unchanged. Web-panel vitest count (currently 129+)
  must not decrease.

### Claude's Discretion

- Whether to use a dedicated `GET /api/academic/headman/dashboard` endpoint or `forkJoin` for
  dashboard data (verify endpoint existence during research/planning).
- Exact `listSubjects` query parameter name for group filter (check `SubjectApi.java` `listSubjects`
  signature — may need `groupId` param or be absent).
- Whether `listTeachers()` uses a dedicated endpoint or `GET /api/academic/users?role=TEACHER`.
- Exact skeleton loading animation approach (consistent with prior student phases).
- Whether `NavItem` interface already has `isHeadman?: boolean` or needs it added.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §Headman Web Cabinet (HEAD-WEB-01..04) — all 4 requirements for this phase

### UI Design Contract (MANDATORY)
- `.planning/phases/54/54-UI-SPEC.md` — complete visual + interaction contract: spacing, typography,
  colors, component inventory (§0 guard/sidebar, §1 dashboard, §2 group+assistant CRUD, §3 subject CRUD,
  §4 route additions), copywriting, accessibility, responsive breakpoints

### Backend Source Files (WPAN-13)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RoleCheckAspect.java`
  — AOP aspect to extend (currently rejects STUDENT for headman operations)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/RequestContext.java`
  — `isHeadman()` already available, no changes needed here
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantController.java`
  — 4 methods with `@RequireRole({UserRole.STUDENT})` — will work post-fix
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectController.java`
  — 3 write methods with `@RequireRole({UserRole.STUDENT})` — will work post-fix

### Angular Source Files (key integration points)
- `frontends/web-panel/src/app/app.routes.ts` — route tree, headman block to extend (line ~130+)
- `frontends/web-panel/src/app/layout/shell/shell.component.ts` (and `sidebar.component.ts`) — NavItem filtering
- `frontends/web-panel/src/app/features/student/shared/student-api.service.ts` — pattern to follow for HeadmanApiService
- `frontends/web-panel/src/app/core/auth/auth.interceptor.ts` — confirms no Angular header change needed
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java`
  — confirms `X-Is-Headman` + `X-Group-Id` already forwarded by gateway

### Contract APIs
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/AssistantApi.java`
  — assistant CRUD endpoints + HTTP methods
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SubjectApi.java`
  — subject CRUD endpoints

### Architecture Decisions
- `.planning/STATE.md` §Key v9.0 Architecture Decisions — WPAN-13 fix approach (AOP + no new UserRole),
  headmanGuard definition, `is_headman` JWT claim source

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `StatCardComponent` (Phase 52) — reuse directly for 4 dashboard stat tiles; supports `accent` input, sparkline, loading skeleton
- `StudentApiService` pattern — template for `HeadmanApiService` (Injectable, absolute URLs, `inject(HttpClient)`)
- `MatDialog` / `MatSnackBar` patterns — already established in `groups-page.component.ts` (admin) for open/close/error handling
- `AssistantService` + `AssistantController` — full CRUD already implemented server-side
- `SubjectService` + `SubjectController` — full CRUD already implemented server-side

### Established Patterns
- All page components use `.page-header` / `.page-card` / `.page-stack` / `.page-empty` / `.page-error` CSS primitives
- Route fade animation: `trigger('routeFade', [...])` applied to host element in all student/teacher/admin pages
- Dialog pattern: `MatDialog.open(Component, { width: '520px', maxWidth: '95vw', data: {...} })` then subscribe to `afterClosed()`
- Snackbar pattern: `MatSnackBar.open(message, undefined, { duration: 4000 })` (no action label)
- `forkJoin` for parallel data loading on dashboard pages (see `teacher-dashboard.component.ts`)
- Error inline + skeleton: signal-based `loading = signal(false)`, `error = signal<string | null>(null)`

### Integration Points
- `RoleCheckAspect` — single change point for WPAN-13; after fix, all `@RequireRole({STUDENT})` methods
  on `AssistantController` and `SubjectController` will pass for headmen
- `SidebarComponent.allNavItems` — add 3 headman nav items with `isHeadman: true` filter
- `app.routes.ts` — replace placeholder + add 2 new routes under `/headman` block
- `HeadmanAssistantRepository` — `findByGroupIdAndIsActiveTrue(groupId)` already exists
- `GroupController.getMyGroupMembers()` — `@RequireRole({STUDENT})` already, headmen pass after fix

</code_context>

<specifics>
## Specific Ideas

- UI-SPEC §2 specifies the assign-assistant dialog uses `MatCheckbox` rows (min-height 44px) for
  permission selection with "Выберите хотя бы одно право" validation error
- Dashboard stat card accent mapping is fully specified in UI-SPEC (primary for member count,
  warning for lesson + tickets, info for late check-in) — follow exactly
- Today's lesson card: 4px left accent border strip, `min-height: 80px`, displays start/end time
  in `--font-mono` tabular-nums style
- Sidebar section "Старостат" — headman nav section is labeled separately from student nav items
- Subject dialog teacher select shows "Не назначен" when no teacher assigned

</specifics>

<deferred>
## Deferred Ideas

- Attendance journal for headman — Phase 55 (`/headman/journal`)
- Excuse ticket approval — Phase 55 (`/headman/excuses`)
- Late check-in approval — Phase 55 (`/headman/late-checkin`)
- Group statistics with red-zone threshold configuration — Phase 55 (`/headman/stats`)
- PWA headman mode — Phase 56

None — analysis stayed within phase scope.

</deferred>

---

*Phase: 54-headman-web-cabinet-group-management-subjects*
*Context gathered: 2026-04-09*

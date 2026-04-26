# Phase 54: Headman Web Cabinet — Group Management + Subjects - Research

**Researched:** 2026-04-09
**Domain:** Angular 17 web-panel + Spring Boot AOP security fix
**Confidence:** HIGH (all findings verified from source files)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**WPAN-13 Backend Fix (academic-service)**
- D-01: Extend `RoleCheckAspect.checkRole()` to pass when `actual == STUDENT && requestContext.isHeadman() == true` in addition to the existing `Arrays.asList(required).contains(actual)` check. No new `UserRole` enum value.
- D-02: `X-Is-Headman` and `X-Group-Id` headers already injected by API Gateway. No Angular interceptor change needed.
- D-03: `AssistantService.requireHeadman()` stays in place as second layer after RoleCheckAspect fix.
- D-04: Same pattern for SubjectController — `SubjectService.requireHeadman()` stays as second layer.
- D-05: Unit test for RoleCheckAspect: `STUDENT + isHeadman=true` passes `@RequireRole({UserRole.STUDENT})`.

**Angular: Route Registration**
- D-06: Replace `HeadmanPlaceholderComponent` at `/headman/dashboard`. Add `/headman/group` and `/headman/subjects` under existing `/headman` block with `canActivate: [headmanGuard]`. All with `data: { eyebrow: 'Староста' }`.

**Angular: HeadmanApiService**
- D-07: Create `frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts` following `StudentApiService` pattern. Method list as specified in CONTEXT.md D-07.
- D-08: Verify dashboard endpoint existence during research (see Key Findings below).

**Angular: Component Architecture**
- D-09: Components in `frontends/web-panel/src/app/features/headman/` as listed.
- D-10: Reuse `StatCardComponent` from Phase 52 for 4 dashboard stat tiles.
- D-11: Add headman nav items to `SidebarComponent.allNavItems` with `isHeadman: true` filter. Check `NavItem` interface — may need `isHeadman?: boolean` added.

**Testing**
- D-12: Extend/create `RoleCheckAspectTest` with `STUDENT + isHeadman=true` test case.
- D-13: Add `headman-api.service.spec.ts` with mocked `HttpClient`.
- D-14: All existing tests must pass unchanged. Web-panel vitest count (129+) must not decrease.

### Claude's Discretion
- Whether to use dedicated `GET /api/academic/headman/dashboard` endpoint or `forkJoin` (RESOLVED — see Key Finding A below)
- Exact `listSubjects` query parameter name for group filter (RESOLVED — see Key Finding B below)
- Whether `listTeachers()` uses dedicated endpoint or `GET /api/academic/users?role=TEACHER` (RESOLVED — see Key Finding C below)
- Exact skeleton loading animation approach (consistent with prior student phases)
- Whether `NavItem` interface already has `isHeadman?: boolean` (RESOLVED — see Key Finding D below)

### Deferred Ideas (OUT OF SCOPE)
- Attendance journal, excuse approvals, late check-in approvals, group stats — Phase 55
- PWA headman mode — Phase 56
- Landing HEADMAN role section — Phase 57

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HEAD-WEB-01 | WPAN-13 backend fix: headman can call `@RequireRole({STUDENT})` endpoints | RoleCheckAspect.java line 31 is the exact change point; no test file exists yet (Wave 0 gap) |
| HEAD-WEB-02 | Dashboard: group stats, today's lesson, pending ticket/late-checkin counts | No dedicated headman dashboard endpoint exists; must use `forkJoin` across separate endpoints |
| HEAD-WEB-03 | Group management: student list + assistant CRUD | `GET /api/academic/groups/my/members` (paginated PagedModel<UserResponse>); AssistantApi fully implemented |
| HEAD-WEB-04 | Subject CRUD with teacher assignment | `listSubjects` has NO groupId filter; `listTeachers` requires ADMIN role — both need resolution |

</phase_requirements>

---

## Summary

Phase 54 combines a single-line AOP backend fix (RoleCheckAspect) with three new Angular feature pages for headman workflow. The backend fix is minimal and surgical — `RoleCheckAspect.checkRole()` currently rejects any call where `actual` is not in `required[]`, so a STUDENT-role headman is blocked even though `AssistantController` and `SubjectController` declare `@RequireRole({UserRole.STUDENT})`. The fix adds a secondary pass: if `requestContext.isHeadman() == true` and `STUDENT` is in `required[]`, proceed. Both downstream service methods (`AssistantService.requireHeadman()`, `SubjectService.requireHeadman()`) already provide the second security layer.

The Angular work has four critical findings that affect the planner's task design: (1) no dedicated headman dashboard endpoint exists — dashboard must compose data via `forkJoin`; (2) `SubjectApi.listSubjects()` accepts no `groupId` filter parameter — the subject list returns ALL subjects; (3) `GET /api/academic/users?role=TEACHER` (listUsers) is guarded with `@RequireRole({ADMIN})`, making it inaccessible to headmen — a separate teacher-listing approach is needed; (4) `NavItem` interface has no `isHeadman?: boolean` field and `filteredNavItems` computed signal does not filter by it — both must be extended.

**Primary recommendation:** Implement the RoleCheckAspect fix first (Wave 0), add RoleCheckAspectTest, then build Angular components against the verified API contracts.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring AOP (`@Aspect`) | Spring Boot 3.4 (AspectJ) | `RoleCheckAspect` modification | Already in use — existing aspect |
| Angular Material M3 | Already installed | MatDialog, MatTable, MatCheckbox, MatSelect | Phase 51–53 established pattern |
| Angular HttpClient | Already installed | `HeadmanApiService` HTTP calls | Project pattern (`StudentApiService`) |
| RxJS `forkJoin` | Already installed | Dashboard parallel data load | Used in `student-stats.component.ts` |
| Vitest | Already configured | Angular unit tests | Project test framework |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Mockito | Spring Boot test | Mock `RequestContext` in `RoleCheckAspectTest` | Backend unit test for D-05/D-12 |
| Angular signals (`signal`, `computed`) | Already used | Reactive state in page components | All new page components follow this pattern |

---

## Architecture Patterns

### Recommended Project Structure
```
frontends/web-panel/src/app/features/headman/
├── dashboard/
│   └── headman-dashboard.component.ts
├── group/
│   ├── headman-group.component.ts
│   ├── assign-assistant-dialog.component.ts
│   └── delete-assistant-dialog.component.ts
├── subjects/
│   ├── headman-subjects.component.ts
│   ├── subject-dialog.component.ts
│   └── delete-subject-dialog.component.ts
└── shared/
    └── headman-api.service.ts

services/academic-service/academic-app/src/main/java/.../security/
└── RoleCheckAspect.java          ← single line change
src/test/java/.../security/
└── RoleCheckAspectTest.java      ← new file (Wave 0 gap)
```

### Pattern 1: RoleCheckAspect Fix
**What:** Add `isHeadman` secondary pass before throwing `AccessDeniedException`.
**When to use:** Applied once in the aspect; downstream service guards remain untouched.
**Example:**
```java
// Source: services/academic-service/academic-app/src/.../security/RoleCheckAspect.java
@Around("@annotation(requireRole)")
public Object checkRole(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
    UserRole[] required = requireRole.value();
    UserRole actual = requestContext.getRole();
    boolean headmanBypass = requestContext.isHeadman()
            && Arrays.asList(required).contains(UserRole.STUDENT);
    if (actual == null || (!Arrays.asList(required).contains(actual) && !headmanBypass)) {
        throw new AccessDeniedException("Required role: " + Arrays.toString(required));
    }
    return pjp.proceed();
}
```

### Pattern 2: HeadmanApiService (mirror of StudentApiService)
**What:** Injectable service with `inject(HttpClient)`, absolute `/api/...` paths.
**Example structure (verified from `student-api.service.ts`):**
```typescript
// Source: frontends/web-panel/src/app/features/student/shared/student-api.service.ts
@Injectable({ providedIn: 'root' })
export class HeadmanApiService {
  private readonly http = inject(HttpClient);
  // All calls: this.http.get('/api/academic/...', { params })
  // HATEOAS unwrap: resp._embedded?.['userResponseList'] ?? []
}
```

### Pattern 3: Dashboard forkJoin (no dedicated endpoint)
**What:** Parallel load from multiple endpoints; same pattern as `student-stats.component.ts` lines 43-57.
**Dashboard data sources:**
- Member count: `GET /api/academic/groups/my/members?size=1` → `page.totalElements`
- Today's lesson: `GET /api/schedule/groups/{groupId}/lessons?dateFrom=today&dateTo=today`
- Pending excuse tickets: `GET /api/attendance/excuses` (may return 404 gracefully)
- Late check-in count: `GET /api/attendance/late-checkin` (may return 404 gracefully)

### Pattern 4: NavItem with isHeadman filter
**What:** Extend `NavItem` interface and `filteredNavItems` computed signal.
**Current state:**
```typescript
// Source: sidebar.component.ts lines 24-29
interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles: ('TEACHER' | 'ADMIN' | 'STUDENT')[];
  // isHeadman?: boolean  ← MISSING, must be added
}
// filteredNavItems (line 176-179) only checks user.role, not isHeadman
```
**Required addition:**
- Add `isHeadman?: boolean` to `NavItem` interface
- Extend `filteredNavItems` computed: if `item.isHeadman`, also require `user.isHeadman === true`
- AuthService's `currentUser()` must expose `isHeadman` — verify the user model type

### Anti-Patterns to Avoid
- **Calling `GET /api/academic/users?role=TEACHER` from headman:** `listUsers` in `UserController` is `@RequireRole({ADMIN})` (line 53-63). Will return 403 for headman. Do NOT use this endpoint for teacher select without a fix or alternative.
- **Filtering subjects by groupId client-side from full list:** `SubjectApi.listSubjects()` accepts no `groupId` param and returns ALL subjects via `subjectRepository.findAll(pageable)`. The subject list may grow large. Planner must decide: load all and display all, or add backend groupId filter.
- **Assuming `filteredNavItems` supports headman items without change:** Current signal at line 176-179 only checks `user.role` — headman items with `roles: ['STUDENT']` would appear for ALL students, not just headmen.

---

## Key Findings (Open Questions Resolved)

### A. Dashboard endpoint: DOES NOT EXIST — use forkJoin
**Finding:** `DashboardApi.java` (the only DashboardApi contract) maps to `GET /academic/dashboard/stats` and is admin-only (see `DashboardController` implementation pattern). There is NO `GET /api/academic/headman/dashboard` endpoint anywhere in the contract API list.
**Evidence:** Glob of all `*Api.java` files returned: `DashboardApi`, `GroupApi`, `SemesterApi`, `UserApi`, `AssignmentApi`, `AssistantApi`, `HomeworkApi`, `SubjectApi`, `ThresholdApi`. None is a headman dashboard.
**Decision:** D-08 resolves to: use `forkJoin` in `HeadmanDashboardComponent`. No backend endpoint creation needed.

### B. SubjectApi.listSubjects() — NO groupId parameter
**Finding:** `SubjectApi.java` lines 52-54: `listSubjects(Pageable pageable, PagedResourcesAssembler<SubjectResponse> assembler)` — no `@RequestParam groupId`.
**Evidence:** `SubjectService.listSubjects()` line 51: `subjectRepository.findAll(pageable)` — returns ALL subjects, no group filter.
**Evidence:** `SubjectRepository.java`: only has `findByType()` and `findByNameContainingIgnoreCase()` — no `findByGroupId`.
**Impact:** `HeadmanApiService.listSubjects(groupId)` from CONTEXT.md D-07 cannot pass `groupId` to a real filter — it will be silently ignored. The planner MUST choose one:
  - Option A: Load all subjects (no filter) — simplest, subjects are typically shared across groups
  - Option B: Add `groupId` filter to `SubjectApi`, `SubjectService`, and `SubjectRepository` — requires backend work
**Recommendation:** Option A (load all subjects). Subjects in this system appear to be institution-wide (no `groupId` FK on `Subject` entity based on `SubjectRepository`). The headman sees and manages all subjects. [ASSUMED: subjects are institution-wide, not per-group — verify against `Subject` entity if needed]

### C. Teacher listing endpoint — ADMIN-ONLY, NOT accessible to headman
**Finding:** `UserController.java` line 53-63: `listUsers()` is `@RequireRole({ADMIN})`. After the RoleCheckAspect fix, headman is still `STUDENT`, not `ADMIN`. This endpoint will return 403 for headman even after the fix.
**Evidence:** `UserController.java` line 54: `@RequireRole({ADMIN})`.
**Impact:** `HeadmanApiService.listTeachers()` → `GET /api/academic/users?role=TEACHER` WILL FAIL with 403 for headman users. The planner MUST address this. Options:
  - Option A: Add a new `GET /api/academic/teachers` endpoint with `@RequireRole({STUDENT})` returning only teacher name + id (minimal exposure). Requires new backend work.
  - Option B: Add `STUDENT` to `@RequireRole` on `listUsers` in `UserController` (too broad — exposes all user data to students).
  - Option C: Add a dedicated `GET /api/academic/users/teachers` or similar read-only endpoint for non-admin roles.
**Recommendation:** Option A — a minimal read-only teacher-listing endpoint accessible to STUDENT/HEADMAN, returning only `id` + `fullName` + `role`. This is the smallest surface area change. [ASSUMED: no such endpoint exists — confirmed by exhaustive API contract search above]

### D. NavItem interface — isHeadman field MISSING
**Finding:** `sidebar.component.ts` lines 24-29: `NavItem` interface has `label`, `icon`, `route`, `roles` only. NO `isHeadman?: boolean` field.
**Evidence:** `filteredNavItems` computed signal at lines 176-179 filters only by `user.role`. Adding headman items with `roles: ['STUDENT']` without the `isHeadman` guard would show them to ALL students.
**Required change:** Add `isHeadman?: boolean` to `NavItem` interface AND update `filteredNavItems` computed to exclude items where `item.isHeadman === true && !user.isHeadman`.
**Secondary check needed:** Verify that `AuthService.currentUser()` return type includes `isHeadman: boolean`. This is needed by the updated `filteredNavItems`. [ASSUMED: `currentUser()` includes `isHeadman` from JWT claim `is_headman` — verify in `auth.service.ts`]

### E. GroupApi.getMyGroupMembers() — exact shape
**Finding:** `GroupApi.java` lines 82-90:
- Method: `GET /academic/groups/my/members`
- Params: `Pageable pageable`, `PagedResourcesAssembler<UserResponse> assembler`
- Returns: `ResponseEntity<PagedModel<EntityModel<UserResponse>>>`
- Auth: `@RequireRole({STUDENT})` — will pass for headman after fix (STUDENT role)
- NO `groupId` param — the backend derives group from the authenticated user's context (`requestContext.getGroupId()`)
**HATEOAS embedded key:** Will be `userResponseList` (Spring HATEOAS convention for `UserResponse`). Verify or use defensive `Object.values(embedded)[0]` pattern (see `admin-api.service.ts` line 41).

### F. RoleCheckAspectTest — DOES NOT EXIST
**Finding:** Glob of `services/academic-service/academic-app/src/test/**/*RoleCheck*` returned no files.
**Evidence:** Test directory contains only integration tests under `integration/` package.
**Impact:** D-12 requires creating a NEW `RoleCheckAspectTest.java` from scratch. This is a Wave 0 gap.
**Existing test infrastructure:** Integration tests use Spring Boot test context. For a unit test of the aspect, Mockito mocks of `RequestContext` suffice (no Spring context needed — aligns with D-05 specification).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Role-based AOP | Custom filter/interceptor | Extend existing `RoleCheckAspect` | One change point, consistent with all other controllers |
| HATEOAS response unwrapping | Custom deserializer | `Object.values(resp._embedded ?? {})[0] ?? []` | Pattern established in `admin-api.service.ts` line 41 |
| Dialog open/close | Custom modal | `MatDialog.open()` + `afterClosed()` subscribe | Established in admin groups page |
| Loading/error state | Custom state machine | `signal(false)` + `signal<string|null>(null)` | Established in all student components |
| Parallel HTTP | Sequential chained calls | `forkJoin([...])` | Established in `student-stats.component.ts` lines 46-57 |

---

## Common Pitfalls

### Pitfall 1: Headman nav items visible to all students
**What goes wrong:** Adding `{ roles: ['STUDENT'], isHeadman: true, ... }` nav items without updating `filteredNavItems` computed — ALL students see headman nav items.
**Why it happens:** Current computed at sidebar line 176-179 only checks `user.role`.
**How to avoid:** MUST update both `NavItem` interface AND `filteredNavItems` computed in the same commit.
**Warning signs:** In dev, log in as a non-headman student and check sidebar — headman section should NOT appear.

### Pitfall 2: 403 on listTeachers for headman
**What goes wrong:** `HeadmanApiService.listTeachers()` calls `GET /api/academic/users?role=TEACHER` — gets 403 because `UserController.listUsers()` is `@RequireRole({ADMIN})`.
**Why it happens:** The RoleCheckAspect fix only helps for `@RequireRole({STUDENT})` methods. `listUsers` requires ADMIN.
**How to avoid:** Create a new minimal teacher-listing endpoint or make subject dialog's teacher select field load gracefully (catch 403, show empty select with "Нет данных").
**Warning signs:** Subject dialog teacher dropdown fails to populate; browser console shows 403 on `/api/academic/users`.

### Pitfall 3: Subject list shows all institution subjects (no group filter)
**What goes wrong:** `listSubjects` returns all subjects — headman sees subjects they didn't create and may try to modify them, getting 403 from `SubjectService.requireHeadman()` (which checks their headman status but not ownership).
**Why it happens:** `SubjectService.requireHeadman()` only checks `requestContext.isHeadman()`, not group ownership of the subject.
**How to avoid:** Accept the behavior (institution-wide subjects) or scope by adding `groupId` to Subject entity. Document clearly in planner output.

### Pitfall 4: RoleCheckAspect test requires Spring context
**What goes wrong:** Writing the test as `@SpringBootTest` causes slow startup and requires Testcontainers for PostgreSQL.
**Why it happens:** All existing tests in the academic-service test dir are full integration tests.
**How to avoid:** Write `RoleCheckAspectTest` as a plain unit test with `@ExtendWith(MockitoExtension.class)`. Mock `RequestContext` with `@Mock`. Instantiate `RoleCheckAspect` directly. No Spring context needed.

### Pitfall 5: HATEOAS embedded key varies by response type
**What goes wrong:** Hardcoding `resp._embedded?.['assistantResponseList']` — Spring HATEOAS derives the key from the class name and it may be different.
**Why it happens:** Spring HATEOAS pluralizes and camelCases: `AssistantResponse` → key might be `assistantResponseList` but verify.
**How to avoid:** Use the defensive pattern from `admin-api.service.ts` line 41: `Object.values(resp._embedded ?? {})[0] ?? []`. Or check actual response in browser DevTools after first integration.

### Pitfall 6: listSubjects PATCH/DELETE headman-scope enforcement
**What goes wrong:** `SubjectService.requireHeadman()` only checks `isHeadman()`, not that the subject belongs to the headman's group. Any headman can delete any subject.
**Why it happens:** No group-scoped ownership check in `SubjectService`.
**How to avoid:** This is existing backend behavior — document it. Out of scope for Phase 54 to fix. UI should not expose delete for subjects the headman didn't create, but backend has no hard guard.

---

## Environment Availability

Step 2.6: SKIPPED for new files (no new external dependencies — all tooling from Phases 51-53 is already available).

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Mockito (Spring Boot Test) |
| Frontend framework | Vitest (Angular) |
| Backend test dir | `services/academic-service/academic-app/src/test/java/` |
| Frontend quick run | `npx vitest run --reporter=verbose` (from `frontends/web-panel/`) |
| Backend quick run | `./gradlew :services:academic-service:academic-app:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| HEAD-WEB-01 | `STUDENT + isHeadman=true` passes `@RequireRole({STUDENT})` | Unit | `./gradlew :services:academic-service:academic-app:test --tests "*.RoleCheckAspectTest"` | ❌ Wave 0 gap |
| HEAD-WEB-01 | `STUDENT + isHeadman=false` still rejected for non-student roles | Unit | same | ❌ Wave 0 gap |
| HEAD-WEB-03 | `HeadmanApiService` HTTP calls mapped correctly | Unit | `npx vitest run headman-api` | ❌ Wave 0 gap |
| HEAD-WEB-02/03/04 | Page components render without error | Smoke/manual | Angular dev server | ❌ not yet built |

### Wave 0 Gaps
- [ ] `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/RoleCheckAspectTest.java` — covers HEAD-WEB-01
- [ ] `frontends/web-panel/src/app/features/headman/shared/headman-api.service.spec.ts` — covers HEAD-WEB-03 API layer

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Subjects are institution-wide (no `groupId` FK on `Subject` entity) — headman sees all subjects | Key Finding B | Low: SubjectRepository has no `findByGroupId` — no group FK exists |
| A2 | No dedicated teacher-listing endpoint accessible to STUDENT role exists | Key Finding C | Low: exhaustive glob of all `*Api.java` in contract module found nothing |
| A3 | `AuthService.currentUser()` includes `isHeadman: boolean` from JWT | Key Finding D | Medium: if missing, sidebar `filteredNavItems` fix also needs auth service update |

---

## Open Questions

1. **Teacher select in Subject dialog**
   - What we know: `GET /api/academic/users?role=TEACHER` requires ADMIN role; headman will get 403
   - What's unclear: Whether a new minimal endpoint should be created in this phase, or whether the teacher field should be optional/disabled for headman
   - Recommendation: Create `GET /api/academic/users/teachers` with `@RequireRole({STUDENT})` returning `id + fullName` only. Planner should add this as a backend task.

2. **`AuthService.currentUser()` includes `isHeadman`?**
   - What we know: Sidebar needs `user.isHeadman` to filter headman nav items; JWT contains `is_headman` claim
   - What's unclear: Whether the `currentUser()` signal's TypeScript type already exposes `isHeadman: boolean`
   - Recommendation: Implementer must read `auth.service.ts` and the user model type before writing sidebar changes.

3. **Subject ownership scope**
   - What we know: Any headman can delete any subject (no group-scoped guard in SubjectService)
   - What's unclear: Whether this is intentional design (institution-wide subjects) or a future gap
   - Recommendation: Accept for Phase 54 — document in UI that subjects are institution-wide. Phase 55+ can add scoping if needed.

---

## Sources

### Primary (HIGH confidence — verified from source files)
- `services/academic-service/academic-app/src/main/java/.../security/RoleCheckAspect.java` — exact change point confirmed (line 31)
- `services/academic-service/academic-api-contract/src/main/java/.../api/SubjectApi.java` — no groupId param (lines 52-54)
- `services/academic-service/academic-app/src/main/java/.../subject/SubjectService.java` — `findAll(pageable)` confirmed (line 51)
- `services/academic-service/academic-app/src/main/java/.../repository/SubjectRepository.java` — no groupId query methods
- `services/academic-service/academic-api-contract/src/main/java/.../api/UserApi.java` — listUsers signature with `@RequestParam UserRole role` (line 62)
- `services/academic-service/academic-app/src/main/java/.../user/UserController.java` — `@RequireRole({ADMIN})` on listUsers (line 54)
- `services/academic-service/academic-api-contract/src/main/java/.../api/GroupApi.java` — `getMyGroupMembers` signature (lines 82-90)
- `services/academic-service/academic-api-contract/src/main/java/.../api/DashboardApi.java` — admin-only, no headman endpoint
- `services/academic-service/academic-api-contract/src/main/java/.../api/AssistantApi.java` — full CRUD contract confirmed
- `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` — `NavItem` interface (lines 24-29), `filteredNavItems` (lines 176-179)
- `frontends/web-panel/src/app/app.routes.ts` — headman block lines 168-184, only `/headman/dashboard` placeholder exists
- `frontends/web-panel/src/app/features/headman/headman-placeholder/headman-placeholder.component.ts` — confirmed placeholder component
- `frontends/web-panel/src/app/features/student/shared/student-api.service.ts` — service pattern for HeadmanApiService
- `frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts` — HATEOAS unwrap pattern

### Secondary (MEDIUM confidence)
- Glob results for `*Api.java` — exhaustive listing of academic contract APIs confirms no headman dashboard endpoint

---

## Metadata

**Confidence breakdown:**
- RoleCheckAspect fix: HIGH — exact file and line verified, fix approach is minimal and correct
- Dashboard forkJoin approach: HIGH — absence of endpoint confirmed by exhaustive glob
- SubjectApi no groupId: HIGH — contract and service both verified
- listTeachers ADMIN-only blocker: HIGH — UserController annotation verified
- NavItem isHeadman missing: HIGH — interface source verified
- RoleCheckAspectTest absence: HIGH — glob returned no files

**Research date:** 2026-04-09
**Valid until:** 2026-05-09 (stable backend; Angular version stable)

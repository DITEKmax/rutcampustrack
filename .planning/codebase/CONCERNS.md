# Codebase Concerns

**Analysis Date:** 2026-04-08

---

## CRITICAL ISSUES — Production Broken (v9.0 targets these)

### 1. Root URL Routes All Users to PWA (INFRA)

**Problem:** `nginx/conf.d/default.conf:125-128` catch-all `location /` proxies everything to PWA, regardless of user role.

```nginx
# --- PWA at root (NET-02) — MUST be last (catch-all) ---
location / {
    proxy_pass http://rct-pwa-nginx:80;
    proxy_set_header Host $host;
}
```

**Impact:** 
- Admins accessing `https://ruttrack.site/` see student PWA (React mobile UI)
- Teachers see student PWA
- Only way to reach admin/teacher panels is via explicit `/admin/` path
- No role-based entry point; confusing for all users
- Breaking: v8.0 shipped with this config; real users complained post-deploy

**Fix approach (Phase 49):**
- Redirect `https://ruttrack.site/` → `/login` (unified entry point)
- Landing accessible via `/presentation/` (explicit path only)
- After login, Angular auth guard redirects per role: ADMIN → `/admin/dashboard`, TEACHER → `/teacher/dashboard`, STUDENT → `/student/dashboard`, HEADMAN → `/headman/dashboard`
- PWA stays at `/app/` (intentional separate channel)

**Files involved:**
- `nginx/conf.d/default.conf` (lines 4-129)

---

### 2. Web-Panel Login Broken for Non-ADMIN Roles (AUTH)

**Problem:** Hard-coded role type and routing logic prevent STUDENT login; broken loop for TEACHER.

**Files & Code:**

**`frontends/web-panel/src/app/core/auth/auth.service.ts:8`**
```typescript
export interface AuthUser {
  id: number;
  role: 'TEACHER' | 'ADMIN';  // ❌ STUDENT not listed — parsing fails
}
```
When JWT contains `role: STUDENT`, `toUpperCase()` at line 27 produces valid string, but TypeScript type assertion at line 27 only allows `TEACHER | ADMIN`. Frontend silently parses but type is invalid.

**`frontends/web-panel/src/app/features/login/login.component.ts:50`**
```typescript
const role = this.authService.currentUser()?.role;
this.router.navigate([role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard']);
```
After login:
- ADMIN → `/admin/dashboard` ✓ (works)
- TEACHER → `/teacher/dashboard` ✓ (works)
- STUDENT → `/teacher/dashboard` ❌ (wrong dashboard)
- HEADMAN (student with `is_headman=true`) → `/teacher/dashboard` ❌ (wrong)

**`frontends/web-panel/src/app/app.routes.ts:20`**
```typescript
{
  path: 'teacher',
  canActivate: [roleGuard(['TEACHER'])],
  children: [ /* dashboard, journal, stats */ ]
}
```
After STUDENT lands on `/teacher/dashboard`, `roleGuard(['TEACHER'])` blocks access → navigates to `/login` → login form submits again → `/teacher/dashboard` → blocked again → **infinite redirect loop**.

**`frontends/web-panel/src/app/app.routes.ts` (lines 1-89)**
- Routes for TEACHER and ADMIN only
- No STUDENT routes exist
- No HEADMAN routes exist
- No dynamic role-based routing

**Impact:**
- STUDENT cannot login to web panel (breaks production for ~500+ users if they try web access)
- HEADMAN has same problem as STUDENT (is_headman is JWT claim, not separate route)
- TEACHER works but demonstrates fragile single-path assumption
- Workaround: Only explicit path like `/admin/` works for those roles; breaks user experience post-Phase 49 redirect

**Fix approach (Phase 50):**
- Expand `AuthUser` interface: `role: 'ADMIN' | 'TEACHER' | 'STUDENT'` + `isHeadman: boolean`
- Update `currentUser()` to parse `is_headman` from JWT claim (already present in `JwtService.java:96`)
- Rewrite `login.component.ts:50`: Route to `/student/dashboard` for STUDENT, `/headman/dashboard` for HEADMAN+STUDENT
- Add child routes: `{ path: 'student', canActivate: [studentGuard], children: [...] }` and `{ path: 'headman', canActivate: [headmanGuard], children: [...] }`
- Implement `studentGuard` and `headmanGuard` (latter checks `role === STUDENT && is_headman === true`)

**Files involved:**
- `frontends/web-panel/src/app/core/auth/auth.service.ts` (interface + computed)
- `frontends/web-panel/src/app/features/login/login.component.ts` (redirect logic)
- `frontends/web-panel/src/app/app.routes.ts` (route definitions)
- `frontends/web-panel/src/app/core/auth/role.guard.ts` (new guards)

---

### 3. Landing Page Dead Telegram Links

**Problem:** Three links in landing page point to empty `https://t.me/` (no bot username).

**Files & Lines:**
- `frontends/landing/dist/index.html:1029` — header "Открыть в Telegram" button
- `frontends/landing/dist/index.html:1107` — middle section "Открыть в Telegram"
- `frontends/landing/dist/index.html:1306` — footer "Открыть в Telegram"

**Current HTML:**
```html
<a href="https://t.me/" class="btn btn--primary btn--sm" rel="noopener">
```

**Impact:**
- User clicks "Открыть в Telegram" → browser navigates to `https://t.me/` (Telegram home)
- Not the intended Mini App or bot
- Breaks user journey from landing to app
- Production visible since v7.0 landing deployed

**Fix approach (Phase 49):**
- Replace all three `href="https://t.me/"` with either:
  - `href="/login"` (send users to unified login)
  - OR `href="https://t.me/{bot_username}"` (if bot username available — check with stakeholder)
- Landing will move to `/presentation/` in Phase 49, so verify links work in new location

**Files involved:**
- `frontends/landing/dist/index.html` (lines 1029, 1107, 1306)

---

### 4. WPAN-13 Blocked: Headman Assistant Management Backend

**Problem:** Backend `@RequireRole(STUDENT)` guard in academic-service prevents headman-scoped assistant operations.

**Context:**
- Job story `JS-HEADMAN-14` requires: "Assign assistant to group, grant specific permissions"
- Assistant CRUD exists in `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/`
- But `@RequireRole(STUDENT)` on endpoints assumes student-self operation, not headman-delegated-on-group

**Impact:**
- v7.0 deferred WPAN-13 — blocked on backend authorization model
- v9.0 Phase 55 (`/headman/group` management) cannot proceed without backend fix
- Workaround: Headman cannot delegate assistant rights through web UI (only through direct DB or old TMA)

**Fix approach (v9.0 Phases 50+55 coordination):**
- Extend `@RequireRole(STUDENT)` logic: allow headman-scoped operations if user is headman of target group
- New contract: `AssistantApi` with methods like `createAssistantForGroup(groupId, studentId, permissions)`
- Authorization check: `if (isHeadman(user) && isHeadmanOf(user, groupId)) allow operation`
- Alternative: New role `@RequireRole({STUDENT, HEADMAN})` with group-scope validation

**Files involved:**
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/` (controller/service)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/` (permissions/gRPC IsHeadman call)

---

## Architectural Debt

### 1. Design Gap: PWA Built Only for STUDENT Role

**Problem:** `docs/design-decisions.md §3` explicitly states:
> "PWA — аудитория: **все роли** (студенты, старосты, преподаватели). Админы остаются на desktop веб-панели"

**Reality:** `frontends/pwa/src/` implemented only for STUDENT. No UI for TEACHER, HEADMAN.

**Impact:**
- Job story `JS-TEACHER-08` (teacher mobile journal) unimplemented
- Job story `JS-HEADMAN-20` (headman mobile full-featured panel) unimplemented
- v9.0 Brief commits to HEADMAN PWA support (Phase 57), but TEACHER remains deferred

**Files involved:**
- `frontends/pwa/src/` (all feature folders: `schedule`, `checkin`, `stats`, `homework`)
- `frontends/pwa/src/features/auth/AuthProvider.tsx` (role-switching logic exists but incomplete)

**Fix approach (v10.0+):**
- Add TEACHER-specific BottomNav tab in PWA (journal, stats, export)
- Add conditional rendering in PWA shell based on `AuthUser.role`
- Leverage existing `TanStackQuery` + `axios` client for backend calls

---

### 2. Monolithic Web-Panel: STUDENT+HEADMAN Web Cabinet Not Scoped

**Problem:** `frontends/web-panel/angular.json:44` has `baseHref: "/admin/"`. After Phase 50 migration to `/`, single app becomes entry point for 4 roles.

**Risk:**
- Web-panel bundle size grows significantly (admin + teacher + student + headman lazy routes)
- CI Docker build time increases (multi-stage Dockerfile must compile all feature modules)
- Lazy-loaded routes for student/headman must be tree-shaken properly (test coverage)

**Impact:**
- v8.0: `rct-web-panel-nginx` image ~50MB (admin+teacher only)
- v9.0 projection: +student lazy routes (+20MB?), +headman lazy routes (+20MB?) → ~90MB
- Deploy times increase; CI/CD pipeline slower

**Fix approach:**
- Verify Webpack lazy chunking works: `ng build --configuration production --stats-json`
- Test bundle analyzer to confirm unused routes don't ship
- Monitor actual image size after Phase 50 build

**Files involved:**
- `frontends/web-panel/angular.json` (baseHref, outputPath)
- `frontends/web-panel/Dockerfile` (multi-stage build)

---

### 3. HEADMAN as Boolean Flag, Not Enum Role

**Problem:** Backend architectural decision (non-breaking): `UserRole = { ADMIN, TEACHER, STUDENT }`. HEADMAN represented as `is_headman: boolean` on `User.entity`.

**Complexity:**
- Frontend must check TWO fields: `role === STUDENT && is_headman === true` for authorization
- gRPC call `IsHeadman(userId)` exists (proto/academic.proto:23) but PWA already reads `is_headman` from JWT claim
- JWT generation in `JwtService.java:96` already includes `is_headman` — good. But frontend code must handle both claims-based and role-based checks

**Impact:**
- Inconsistency: `role.guard.ts` checks single role, but headman guard must check role + boolean
- Prone to bugs: forgetting `is_headman` check allows regular student to access headman routes
- v9.0 introduces 4 guards: `authGuard`, `roleGuard(['TEACHER'])`, `studentGuard`, `headmanGuard` — complexity

**Fix approach:**
- Document in `CONVENTIONS.md`: "Headman guard pattern: check `role === STUDENT && isHeadman === true`"
- Create `AuthUser.isHeadman` computed property (done in Phase 50 fix)
- Consistent usage across all guards

**Files involved:**
- `frontends/web-panel/src/app/core/auth/` (all guards)
- `frontends/pwa/src/features/auth/AuthProvider.tsx` (role logic)

---

## Known Gaps from Previous Milestones

### v3.0 (Schedule Service) — Unfixed Debt

**1. IllegalArgumentException → HTTP 500**
- Problem: `REST layer missing exception handler for `IllegalArgumentException`
- Files: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/` (controller)
- Impact: Malformed requests return generic 500 instead of RFC 7807 400 Bad Request
- Fix: Add `@ExceptionHandler(IllegalArgumentException.class)` to `ControllerAdvice`

**2. LSSN-03 Idempotency: saveAll Throws 409**
- Problem: `LessonRepository.saveAll()` throws unique constraint violation on retry
- Files: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java`
- Impact: Lesson creation not idempotent — retries fail
- Fix: Use PostgreSQL `ON CONFLICT DO NOTHING` in Flyway migration or batch insert with UpsertRepository pattern

**3. GRPC-03 GetLessonsByGroup Includes Cancelled**
- Problem: gRPC endpoint returns cancelled lessons
- Files: `services/schedule-service/schedule-app/src/main/grpc/` (service impl)
- Impact: Attendance service sees phantom lessons; statistics include cancelled
- Fix: Filter `status != CANCELLED` in query

---

### v5.0 (Notification Service) — Handlers Wired, No Publishers

**Problem:** Event handlers for `excuse.requested` and `late_checkin.requested` exist but no backend publisher sends these events.

**Files:**
- `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java:27-28` — handlers registered
- `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java` — tests wired
- No corresponding `AttendanceEventService` publishing these events

**Impact (marked WS-05, WS-06, NOTIF-08, NOTIF-09):**
- Excuse ticket UI exists in PWA but no notifications reach headman
- Late check-in UI exists but no notifications
- Features appear broken in production
- v9.0 Brief notes: "Excuse ticket creation and late check-in request flows: fully deferred — backend not implemented"

**Fix approach:**
- Implement `ExcuseEventService` in `attendance-service` to publish `excuse.requested` on excuse creation
- Implement late-check-in publisher (same)
- Wire in RabbitMQ template with event envelope
- Test integration with notification-service

**Files blocked:**
- `frontends/pwa/src/features/excuses/` (UI complete, backend missing)
- `frontends/pwa/src/features/late-checkin/` (UI complete, backend missing)

---

### v5.0 — WebSocket Group Isolation (WS-07)

**Problem:** Group-based topic routing wired in `notification-web`, but no live broker-level verification that user cannot see other groups' messages.

**Impact:** Low-severity (auth guard blocks, but no encryption layer)
- Any student theoretically could subscribe to `/topic/group/{other_group_id}` if they knew ID
- Current guards prevent this at HTTP level, but STOMP protocol has no cross-check

**Fix approach:** 
- Add subscription interceptor in notification-web to verify group membership before STOMP delivery
- Log/block unauthorized subscription attempts

---

### v5.0 — Notification Timezone & Timer Testing (NOTIF-02, NOTIF-03)

**Problem:** "TZ fix applied; live timer testing still needed"

**Status:** Timezone fix in code, but live e2e tests never run to confirm reminder timers work correctly.

**Impact:** Low (minor bugs in reminder timing only)

**Fix approach:** Run E2E test with Testcontainers + RabbitMQ to confirm 3 reminders fire at correct UTC times

---

## Test Coverage Gaps

### 1. Web-Panel Route Guards — 34 Tests Insufficient

**Problem:** `frontends/web-panel/` has 34 spec files but angular.json has `skipTests: true` for components (lines 9-27).

**Files:** `frontends/web-panel/src/**/*.spec.ts` (34 total)

**Gaps:**
- STUDENT and HEADMAN routes don't exist yet → no tests for new guards
- `login.component.ts:50` redirect logic never tested with STUDENT role
- Role guard guard conditions partial

**Risk:** Phase 50 route migration can break existing teacher/admin tests if not re-run comprehensively

**Fix approach:**
- Expand test suite after Phase 50 to cover all 4 roles
- Test `headmanGuard` explicitly: `role === STUDENT && isHeadman === true`
- Test redirect logic: `ADMIN → /admin/dashboard`, `TEACHER → /teacher/dashboard`, `STUDENT → /student/dashboard`, `HEADMAN → /headman/dashboard`

---

### 2. PWA Vitest Coverage — 63 Tests Locked to STUDENT

**Problem:** `frontends/pwa/` has 63 vitest tests, all assume STUDENT role.

**Files:** `frontends/pwa/src/**/__tests__/**` (63 total, per v6.0 report)

**Gaps:**
- No HEADMAN role tests
- No TEACHER role tests
- v9.0 Phase 57 (PWA headman role) will add HEADMAN UI without pre-existing tests

**Risk:** Phase 57 can accidentally break STUDENT UI while adding HEADMAN

**Fix approach:**
- Add test parameterization: test all features for both STUDENT and HEADMAN roles
- Verify existing 63 tests pass with HEADMAN auth context
- Add new test file `PWAHeadmanRole.test.tsx` for headman-specific features

---

## Code Duplication & Shared Resources

### Frontend React Code Duplication (PWA vs Mini App)

**Problem:** Both `frontends/pwa/` and `frontends/mini-app/` are React projects. Potential duplication:
- Check-in logic (GPS capture, status validation)
- Schedule view (week navigation, lesson rendering)
- API client (axios + TanStack Query setup)

**Status:** `frontends/shared/` exists but usage unknown (not checked in detail).

**Impact:** Medium
- Maintenance burden: bug fix in PWA might need same fix in Mini App
- Bundle duplication: both ship similar code

**Fix approach:**
- Audit `frontends/shared/` contents
- Extract common types/constants (AttendanceStatus enum, API client setup)
- Create `@rct/shared` npm workspace package for shared logic
- Link both PWA and Mini App to it

**Files involved:**
- `frontends/pwa/src/features/` (check-in, schedule, auth)
- `frontends/mini-app/src/features/` (check-in, schedule, auth)
- `frontends/shared/` (should be expanded)

---

## Deployment & Infrastructure

### 1. CI Docker Image Size Growth Projections

**Problem:** v9.0 will add STUDENT and HEADMAN routes to web-panel, growing the image.

**Current (v8.0):**
- `rct-web-panel-nginx`: ~50MB (admin + teacher routes)
- Multi-stage build in `frontends/web-panel/Dockerfile`

**v9.0 Projection:**
- +STUDENT feature routes (schedule, checkin, homework, stats, excuses, late-checkin, profile)
- +HEADMAN feature routes (group, subjects, journal, excuses, late-checkin, stats)
- Estimated: +40MB → ~90MB total

**Impact:** 
- Longer push to registry (GitHub Container Registry)
- Longer deploy time (VPS image pull)
- Storage cost

**Fix approach:**
- Monitor actual image size after Phase 50
- Use `docker image inspect` and `dive` tool to find optimization opportunities
- Consider aggressive tree-shaking / lazy-chunking validation
- If >100MB, consider splitting into micro-frontends (future)

---

### 2. Service Worker Cache Invalidation on PWA Redeploy

**Problem:** PWA (`frontends/pwa/`) has Service Worker (workbox) that caches static assets and API responses.

**Risk:** 
- Redeploy v9.0 PWA with breaking API changes (new endpoints for HEADMAN)
- Old SW cache serves stale API responses
- Users on old version see broken UI

**Status:** Workbox precache hash validation exists (workbox auto-invalidates precache on hash change), but offline cache strategy unclear.

**Impact:** Low-medium (users can manually refresh or uninstall/reinstall app)

**Fix approach:**
- Document cache invalidation strategy in `frontends/pwa/vite.config.ts` comments
- Consider adding version check: on SW update, prompt user to refresh
- Test in dev: change API response, verify SW updates correctly

**Files involved:**
- `frontends/pwa/vite.config.ts` (workbox config)
- `frontends/pwa/src/service-worker.ts` (if exists) or workbox handlers

---

### 3. Nginx baseHref Redirect Impact

**Problem:** Phase 49 changes nginx routing from `/admin/` catch-all to `/` redirect.

**Risk:**
- Old external links `https://ruttrack.site/admin/...` will 404 after redirect
- Docs/landing/emails with `/admin/` links become broken

**Impact:** Medium (user experience + SEO)

**Fix approach:**
- Phase 49 must include: grep all docs/landing/footer for `/admin/` links and update to `/login`
- Add nginx rewrite rule as fallback: `rewrite ^/admin/(.*) /$1 permanent;` (optional legacy redirect)
- Update any external documentation (README, runbook, etc.)

**Files to audit:**
- `README.md` (if has admin link)
- `frontends/landing/dist/index.html` (footer links)
- `.planning/` docs (cross-references)

---

## CLAUDE.md Status Out of Date

**Problem:** `CLAUDE.md` (project instructions) has stale status section.

**Lines 7-15:**
```
- **v6.0**: В РАБОТЕ (PWA + Web Push) — фазы 27-32, завершены 27-30 (4/6)
```

**Reality:** v8.0 shipped 2026-04-08. v6.0 completed long ago.

**Impact:** Low (informational only, doesn't break functionality)

**Fix approach (Phase 59 Documentation):**
- Update status to show v8.0 complete, v9.0 planned
- Change "В РАБОТЕ" section to reflect v9.0 phases 49-59

**Files involved:**
- `CLAUDE.md` (lines 7-15)

---

## Summary by Severity

| Severity | Count | Items | v9.0 Target? |
|----------|-------|-------|--------------|
| **CRITICAL** (production broken) | 4 | Nginx routing, web-panel auth loop, landing links, WPAN-13 backend | YES — Phases 49-50, 55 |
| **Architectural debt** | 3 | PWA role gap, web-panel size growth, HEADMAN boolean complexity | PARTIAL — v10.0+ mostly |
| **Known gaps** (v3-v5) | 6 | Exception handler, idempotency, late-checkin flow, excuse flow, group isolation, timer testing | DEFERRED — v10.0+ |
| **Test coverage** | 2 | Web-panel guards, PWA HEADMAN role | Phase 50+57 |
| **Code duplication** | 1 | React shared code (PWA+Mini App) | Optional v9.0 |
| **Deployment risks** | 3 | Image size, SW cache, nginx redirects | Phase 49-50 |
| **Docs** | 1 | CLAUDE.md status | Phase 59 |

---

*Concerns audit: 2026-04-08*

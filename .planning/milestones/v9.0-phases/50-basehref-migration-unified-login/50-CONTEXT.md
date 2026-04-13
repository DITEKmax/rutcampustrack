# Phase 50: baseHref Migration + Unified /login - Context

**Gathered:** 2026-04-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Turn the Angular web-panel (currently serves only `/admin/*`) into a single SPA that
handles all 4 roles (ADMIN, TEACHER, STUDENT, HEADMAN) from `baseHref: /`, with `/login`
as the unified public entry point and role-based post-login routing. Deliverables scoped to
this phase:

- Angular `baseHref` migration `/admin/ → /` (`angular.json`)
- Prod reverse proxy reconfig (`nginx/conf.d/default.conf`): `location /` → web-panel,
  remove `location /admin/`, keep `location = /` → `301 /login` from Phase 49
- `AuthService.currentUser` extended to expose `role: 'ADMIN'|'TEACHER'|'STUDENT'` +
  `isHeadman: boolean` from JWT claims (JWT already carries `is_headman`, see JwtService.java:96)
- New guards: `studentGuard`, `headmanGuard`, `guestGuard` (for `/login` when already logged in)
- Role-based redirect helper in `AuthService.resolveDashboardFor(user)`; wired into
  `login.component.ts` post-success and `guestGuard`
- Placeholder routes + components for `/student/dashboard`, `/student/schedule`, `/headman/dashboard`
  (real components arrive in Phases 51–55)
- Replace external references to `ruttrack.site/admin/` in landing + docs with `/login`
- Full unit-test coverage for all new guards, `AuthService.resolveDashboardFor`, and the
  rewritten `login.component.ts` redirect logic; 129 existing vitest tests stay green

**Out of scope** (deferred to later phases, per ROADMAP.md):
- Real `/student/*` content (Phase 51-53)
- Real `/headman/*` content (Phase 54-55)
- WPAN-13 backend AOP change (Phase 54)
- PWA headman mode (Phase 56)
- Landing `LAND-v9-05` multi-role description (Phase 57)

</domain>

<decisions>
## Implementation Decisions

### Nginx Prod Routing
- **D-01:** `nginx/conf.d/default.conf` changes: remove the `location /admin/ { proxy_pass http://rct-web-panel-nginx:80/; }` block (current lines ~77–81). Add a catch-all `location / { proxy_pass http://rct-web-panel-nginx:80/; proxy_set_header Host $host; }` at the end of the HTTPS `server` block so web-panel serves the root. More specific prefix locations (`/api/`, `/app/`, `/presentation/`, `/mini-app/`, `/swagger-ui.html`, `/swagger-ui/`, `/v3/api-docs`, `/openapi/`, `/.well-known/acme-challenge/`) remain ABOVE the fallback and win by nginx prefix-match precedence.
- **D-02:** Keep the Phase 49 `location = / { return 301 /login; }` block intact. Exact-match `= /` beats the new prefix `/`, so root still 301-redirects to `/login` (preserves INFRA-v9-01). Users land on `/login`, which is then served by the web-panel SPA.

### Backwards Compatibility for Old `/admin/*` URLs
- **D-03:** Grep the repo for `ruttrack.site/admin` and `href="/admin` under `frontends/landing/`, `docs/`, `.planning/`. Replace external references (landing HTML, docs .md files — at minimum `frontends/landing/dist/index.html:1330` which contains `https://ruttrack.site/admin/`) with `/login`. Internal Angular `routerLink="/admin/..."` references in web-panel source are left untouched — they still work because the same SPA now serves them from `baseHref: /`.
- **D-04:** NO explicit 301 redirect for `/admin/*` in nginx. The SPA fallback naturally serves `/admin/dashboard`: Angular router matches the existing `AdminDashboardComponent`, `authGuard` forces unauthenticated users to `/login`, `roleGuard(['ADMIN'])` redirects non-admins to their own dashboard via `AuthService.resolveDashboardFor()`. Zero extra routing code, graceful degradation for old bookmarks.

### Placeholder Shells & Layout
- **D-05:** Single `ShellComponent` (existing `layout/shell/shell.component.ts`) hosts ALL 4 roles. `/student/*` and `/headman/*` mount as children of the same shell alongside current `/admin/*` and `/teacher/*` children. Sidebar menu items filter by `authService.currentUser()?.role` and `isHeadman`. Rationale: maximum reuse, visual consistency with ADMIN/TEACHER already shipped in v7.0, and the real student/headman layouts in Phases 51-55 can extend/override as needed.
- **D-06:** Placeholder components are lightweight standalone components that render a single centered message: `"Кабинет [студента|старосты] появится в Фазе 51/54"`. No sidebar entries for them yet, no styles beyond basic typography. Enough to verify ROADMAP success criteria 1, 3, 4 and give the router something concrete to match.
- **D-07:** Routes registered in `app.routes.ts` in this phase (both behind `authGuard` + the right role guard, under `ShellComponent`):
  - `/student/dashboard` → placeholder (`studentGuard`)
  - `/student/schedule` → placeholder (`studentGuard`) — needed so ROADMAP criterion 4 "visit `/student/schedule` as headman succeeds" is actually verifiable in this phase, not deferred
  - `/headman/dashboard` → placeholder (`headmanGuard`)
  - `path: ''` under `/student` and `/headman` redirects to `dashboard`
  - No other `/student/*` or `/headman/*` routes are pre-registered — Phases 51-55 add them with real `loadComponent`.

### `/login` Behavior for Authenticated Users
- **D-08:** New `guestGuard` (CanActivateFn) applied to the `/login` route. If `authService.isAuthenticated()` is true, it calls `authService.resolveDashboardFor(currentUser())` and returns `router.createUrlTree([dashboardPath])`. Unauthenticated users see the form as before.
- **D-09:** `AuthService.resolveDashboardFor(user: AuthUser | null): string` becomes the SINGLE SOURCE OF TRUTH for post-login redirects. Logic:
  - `null` → `/login`
  - `role === 'ADMIN'` → `/admin/dashboard`
  - `role === 'TEACHER'` → `/teacher/dashboard`
  - `role === 'STUDENT' && isHeadman` → `/headman/dashboard`
  - `role === 'STUDENT'` → `/student/dashboard`
  Used by: `login.component.ts` (after successful login, replacing the current `role === 'ADMIN' ? '/admin/dashboard' : '/teacher/dashboard'` switch on line 50), `guestGuard`, and `roleGuard` fallback path (so denied access also routes to the correct per-role dashboard, not a hard-coded `/admin/dashboard`).

### Tests
- **D-10:** Full unit-test coverage for every new piece:
  - `core/auth/student.guard.spec.ts` — STUDENT passes, headman passes, ADMIN/TEACHER rejected, unauthenticated → `/login`
  - `core/auth/headman.guard.spec.ts` — STUDENT+isHeadman passes, plain STUDENT rejected (→ `resolveDashboardFor` = `/student/dashboard`), ADMIN/TEACHER rejected, unauthenticated → `/login`
  - `core/auth/guest.guard.spec.ts` — unauthenticated allows `/login`, ADMIN → `/admin/dashboard`, TEACHER → `/teacher/dashboard`, STUDENT → `/student/dashboard`, headman → `/headman/dashboard`
  - `core/auth/auth.service.spec.ts` — extend existing file: `resolveDashboardFor` table tests for all 4 roles + null; `currentUser()` parses `role`, `is_headman`, `group_id` from a crafted JWT; malformed JWT returns `null`
  - `core/auth/role.guard.spec.ts` — update to cover STUDENT + headman users against existing roleGuard paths (ADMIN/TEACHER)
  - `features/login/login.component.spec.ts` — update: 4 post-success redirect scenarios (one per role), use `AuthService.resolveDashboardFor` instead of inline switch
  - All 129 current vitest tests remain green (AUTH-v9-07). Target after this phase: 129 + new tests passing.

### Claude's Discretion
- Exact file/component names for placeholders (e.g., `StudentDashboardPlaceholderComponent`
  vs `StudentPlaceholderComponent` reused across both routes) — decide during planning.
- Whether `is_headman` claim reads as `boolean` or `string "true"`/`"false"` — check the
  actual JWT payload shape during research (JwtService.java:96 is backend source of truth).
- Placeholder component styling details (Material typography variant, margin, icon).
- Whether `resolveDashboardFor` is a plain method or a `computed` signal — planning call.
- Order of edits between `angular.json` baseHref change, nginx edit, and code changes
  (parallel-safe, but planning should lock the commit sequence).

### Folded Todos
None — no pending todos matched Phase 50 (`todo match-phase 50` returned 0).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone v9.0 Scope & Requirements
- `.planning/REQUIREMENTS.md` §Infrastructure (INFRA-v9-04) — web-panel single-SPA requirement
- `.planning/REQUIREMENTS.md` §Authentication (AUTH-v9-01 through AUTH-v9-07) — all 7 AUTH requirements belong to this phase
- `.planning/ROADMAP.md` §"Phase 50: baseHref Migration + Unified /login" (lines 161–182) — goal statement, 6 success criteria, implementation notes, risks
- `.planning/PROJECT.md` Key Decisions §D-05 (Mini App memory-only tokens) + D-06 (Web Panel Angular signals for token storage) — memory-only tokens rule that constrains logout flow

### Phase 49 Handover (prerequisite state)
- `nginx/conf.d/default.conf` — current prod nginx config (Phase 49 added `location = / → 301 /login` and `/presentation/`, `/app/` proxy blocks; Phase 50 edits this file)
- `.planning/phases/49-nginx-routing-landing-dead-link-fix/49-01-PLAN.md` — Phase 49 execution, context for the existing nginx routing decisions Phase 50 builds on

### Codebase Touchpoints (files that must change)
- `frontends/web-panel/angular.json` — `baseHref` config (search for `"baseHref": "/admin/"` — appears at two build configurations)
- `frontends/web-panel/nginx.conf` — in-container SPA fallback nginx (already `try_files $uri $uri/ /index.html;` at location /, no changes needed unless baseHref `<base href>` injection requires it — verify during research)
- `frontends/web-panel/src/app/app.routes.ts` — add `/student/*` and `/headman/*` trees, apply new guards
- `frontends/web-panel/src/app/core/auth/auth.service.ts` — extend `AuthUser` interface (`role: 'ADMIN'|'TEACHER'|'STUDENT'`, add `isHeadman: boolean`), add `resolveDashboardFor(user)` method, parse `is_headman` from JWT claims
- `frontends/web-panel/src/app/core/auth/auth.guard.ts` — keep as-is (generic auth check)
- `frontends/web-panel/src/app/core/auth/role.guard.ts` — update fallback to use `resolveDashboardFor`, support STUDENT role
- `frontends/web-panel/src/app/core/auth/student.guard.ts` — NEW
- `frontends/web-panel/src/app/core/auth/headman.guard.ts` — NEW
- `frontends/web-panel/src/app/core/auth/guest.guard.ts` — NEW
- `frontends/web-panel/src/app/features/login/login.component.ts` — line 50: replace inline `role === 'ADMIN' ? ...` ternary with `this.router.navigateByUrl(this.authService.resolveDashboardFor(this.authService.currentUser()))`
- `frontends/web-panel/src/app/features/student/` — NEW feature directory with placeholder component(s)
- `frontends/web-panel/src/app/features/headman/` — NEW feature directory with placeholder component(s)
- `frontends/landing/dist/index.html:1330` — external `https://ruttrack.site/admin/` reference → replace with `/login`
- `services/auth-service/auth-app/.../JwtService.java:94-96` — BACKEND REFERENCE ONLY (read to confirm JWT claims shape: `role`, `is_headman`, `group_id` all present in the access token; do NOT modify)

### Testing
- `.planning/codebase/TESTING.md` — test strategy for the monorepo, including web-panel vitest conventions
- `frontends/web-panel/vitest.config.ts` — vitest runner config
- `frontends/web-panel/src/app/core/auth/auth.service.spec.ts` — existing spec to extend (currentUser parsing tests)
- `frontends/web-panel/src/app/core/auth/auth.guard.spec.ts`, `auth.interceptor.spec.ts` — existing specs that must remain green

### Codebase Maps (general context)
- `.planning/codebase/STRUCTURE.md` — repo layout overview
- `.planning/codebase/CONVENTIONS.md` — contract-first, no-Lombok-in-contract, etc. (mostly backend but contains Angular conventions)
- `.planning/codebase/ARCHITECTURE.md` — 5-service system architecture

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `layout/shell/shell.component.ts` — existing sidebar + header shell used by ADMIN and TEACHER routes today. Extends naturally to STUDENT/HEADMAN by adding role-conditional menu items; no new layout component needed.
- `core/auth/auth.service.ts` — already uses Angular signals (`_accessToken`, `_refreshToken`, `currentUser` computed) and already parses JWT via `atob(parts[1])`. Adding `isHeadman` + `resolveDashboardFor` is a straight extension, not a rewrite. Memory-only token storage (D-05/D-06) is already in place.
- `core/auth/auth.api.ts` + `core/auth/auth.interceptor.ts` — login/refresh/logout HTTP flow already implemented and tested; Phase 50 does not touch them.
- `core/auth/role.guard.ts` — existing factory pattern `roleGuard(allowedRoles: string[])` is the template for new `studentGuard` / `headmanGuard` — both can follow the same `CanActivateFn` shape, but as named functions (not factories) because they have no configuration.
- Phase 49 nginx block `location = / { return 301 /login; }` — already live, Phase 50 just doesn't remove it.

### Established Patterns
- **Guards:** All guards use `inject(AuthService)` + `inject(Router)` + return `true | UrlTree`, never imperative `router.navigate`. Must follow same pattern for new guards.
- **JWT parsing:** Manual `atob(payload)` in `auth.service.ts:22-32` — lightweight, no `jwt-decode` dependency. Keep the approach; add defensive parsing for `is_headman` (may be present-or-absent, not just truthy/falsy).
- **Signals everywhere:** `currentUser` is a `computed` signal, not a method. `isAuthenticated` is a `computed`. New dashboard resolver can also be a method or a computed — either fits the codebase.
- **Route data for title/eyebrow:** Existing routes carry `data: { title, eyebrow }` — placeholder routes should follow to integrate with `ShellComponent` header rendering.
- **Role uppercase:** `(payload.role as string).toUpperCase()` at `auth.service.ts:27` — JWT may ship lowercase `"admin"`/`"student"`, code normalizes to `UPPER_CASE`. Must keep this behavior for STUDENT role as well.

### Integration Points
- **Route tree:** `app.routes.ts` currently has `path: 'login'` (public) + `path: ''` (`ShellComponent` + `authGuard`) with admin/teacher children + wildcard `redirectTo: 'login'`. Phase 50 additions fit inside the existing shell children block — no restructuring.
- **nginx:** Two different nginx layers. (1) In-container `frontends/web-panel/nginx.conf` (already does SPA fallback with `try_files $uri $uri/ /index.html;` — should work as-is once `baseHref: /` is in effect; verify no hard-coded `/admin/` anywhere). (2) Prod reverse proxy `nginx/conf.d/default.conf` (edits in D-01, D-02).
- **Docker images:** `rct-web-panel-nginx` image is rebuilt from `frontends/web-panel/Dockerfile` — a baseHref change triggers a rebuild. INFRA-v9-07 requires "all 4 frontend Docker images rebuild successfully" — web-panel is one of them.
- **`<base href>`:** `angular.json` `baseHref` config controls Angular CLI build-time injection of `<base href="...">` in `index.html`. `/admin/ → /` change must be reflected in both `architect.build.options.baseHref` and `architect.build.configurations.production.baseHref` (grep showed 2 occurrences at `angular.json:44,62`).

</code_context>

<specifics>
## Specific Ideas

- Phase 49 already landed `location = / → 301 /login` — Phase 50 deliberately does NOT remove this even though Angular could handle it, because the explicit 301 preserves SEO signal and is already UAT-validated via INFRA-v9-01.
- Placeholder copy should be in Russian to match the rest of the web-panel UI (see `login.component.ts:57` — existing Russian error messages).
- ROADMAP criterion 4 (`/student/schedule` as headman passes studentGuard) cannot be E2E-verified without the route existing, so `/student/schedule` gets a placeholder in this phase specifically to close that criterion. Phase 51 replaces the placeholder with the real schedule view.
- The HEADMAN risk callout from ROADMAP.md:180 ("grep for `ruttrack.site/admin` in all docs and landing before editing to avoid broken links") is acknowledged and handled by D-03.

</specifics>

<deferred>
## Deferred Ideas

- **Dockerfile / in-container nginx deep review** — suggested as a separate discussion area; deferred because `frontends/web-panel/nginx.conf` appears baseHref-agnostic (`try_files $uri $uri/ /index.html`) and the Dockerfile rebuild is already mandated by INFRA-v9-07 regardless. Research agent will verify during Phase 50 planning; escalate to a deviation only if a hidden `/admin/` reference is found.
- **`jwt-decode` library adoption + stricter JWT typing** — nice-to-have; current manual `atob` works and is tested. Belongs in a future tech-debt phase.
- **ShellComponent sidebar registry refactor** (declarative menu items vs imperative switch) — mentioned as a discussion area; not chosen for this phase. Planning should use the simplest role-filter approach and can revisit when Phases 51-55 put real pressure on the sidebar.
- **returnUrl pattern in `authGuard`** (`/login?returnUrl=/admin/users` after successful login brings user back to intended page) — good UX but additional scope; deferred. Current behavior (redirect to role dashboard regardless of where user came from) is acceptable.
- **`/student/*` and `/headman/*` complete route tree** — real components come in Phases 51-55. Phase 50 only registers the 3 placeholder routes needed to prove guards work.
- **Landing `LAND-v9-05` multi-role description** — belongs to Phase 57 (docs & landing polish), not Phase 50.

</deferred>

---

*Phase: 50-basehref-migration-unified-login*
*Context gathered: 2026-04-09*

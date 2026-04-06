# Architecture Research

**Domain:** v7.0 Frontends — Telegram Mini App, Angular Web Panel, Landing page integration with existing RutCampusTrack microservice backend
**Researched:** 2026-04-06
**Confidence:** HIGH (full codebase inspection + official Telegram docs + Angular docs)

---

## Standard Architecture

### System Overview (after v7.0)

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                           DOCKER PRIVATE NETWORK                                │
│                                                                                 │
│  CLIENTS                                                                        │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────┐               │
│  │  Telegram        │  │  Browser / PWA   │  │  Browser        │               │
│  │  (Mini App       │  │  (RutTrack PWA)  │  │  (Web Panel     │               │
│  │   WebView)       │  │                  │  │   Angular)      │               │
│  │                  │  │                  │  │                 │               │
│  │  Auth: initData  │  │  Auth: JWT       │  │  Auth: JWT      │               │
│  │  → /auth/tma     │  │  Bearer token    │  │  Bearer token   │               │
│  │  → own JWT       │  │  httpOnly cookie │  │  localStorage   │               │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬────────┘               │
│           │                     │                      │                        │
│           │ HTTPS               │ HTTPS                │ HTTPS                  │
│           └─────────────────────┴──────────────────────┘                        │
│                                 │                                               │
│                                 ▼                                               │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                      API Gateway  :8080                                  │   │
│  │  CORS: allowed-origins += mini-app origin, web-panel origin              │   │
│  │  JWT filter: validates RSA sig, injects X-User-Id, X-User-Role,         │   │
│  │             X-Group-Id, X-Is-Headman into downstream headers             │   │
│  │  Public: /api/auth/login, /api/auth/refresh, /api/auth/public-key,      │   │
│  │          /api/auth/otp/*, /api/auth/tma (NEW)                           │   │
│  │                                                                          │   │
│  │  /api/auth/**       → auth-service:9090                                  │   │
│  │  /api/academic/**   → academic-service:9091                              │   │
│  │  /api/schedule/**   → schedule-service:9092                              │   │
│  │  /api/attendance/** → attendance-service:9093                            │   │
│  │  /api/ws/**         → notification-web:9094  (WS proxy)                  │   │
│  │  /api/push/**       → notification-web:9094  (push subscriptions)        │   │
│  └──────────────┬─────────────────────────────────────────────────────────┘    │
│                 │                                                               │
│   Auth(9090)  Acad(9091)  Sched(9092)  Att(9093)  Notification-web(9094)       │
│                                                                                 │
│  SERVING (static builds)                                                        │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────┐               │
│  │  pwa-nginx :80   │  │ panel-nginx :81  │  │ landing :82     │               │
│  │  pwa/dist/       │  │ web-panel/dist/  │  │ landing/        │               │
│  └──────────────────┘  └──────────────────┘  └─────────────────┘               │
└────────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | New vs Existing |
|-----------|----------------|-----------------|
| pwa-nginx | Serves React PWA static build, SPA fallback | Existing (port 80) |
| panel-nginx | Serves Angular web panel static build, SPA fallback | NEW (port 81) |
| landing (static) | Serves HTML/CSS landing page (optional nginx or CDN) | NEW (port 82) |
| API Gateway | CORS, JWT validation, routing, header injection | Modified (add origins + TMA route) |
| Auth Service | JWT login, refresh, OTP — plus new TMA initData exchange | Modified (add /auth/tma endpoint) |
| Mini App (React) | Telegram-embedded UI for students: schedule, check-in, stats | NEW |
| Web Panel (Angular) | Desktop admin/teacher CRUD dashboard | NEW |
| Landing (HTML/CSS) | Marketing page, project description | NEW |

---

## Integration Points

### 1. Telegram Mini App — Auth Integration

**The challenge:** Telegram Mini App cannot use the standard login+password flow. The user identity comes from Telegram's `initData` (signed HMAC-SHA256 with bot token). This must be exchanged for a backend JWT.

**Flow:**
```
[Telegram WebView launches Mini App]
    ↓
window.Telegram.WebApp.initData  ← signed string from Telegram native app
    ↓
POST /api/auth/tma
  Authorization: tma <raw initData>
    ↓
Auth Service validates HMAC-SHA256 signature with bot token secret
Auth Service finds user by telegram_id (from initData.user.id)
Auth Service returns { accessToken, expiresIn }
    ↓
Mini App stores token in memory (no httpOnly cookie — no SPA cookie domain)
All subsequent API calls: Authorization: Bearer <token>
```

**Backend changes required:**
- New endpoint `POST /api/auth/tma` in auth-service (public route in Gateway)
- Validate initData: HMAC-SHA256(bot_token with key "WebAppData") → compare hash field
- Look up user by `telegram_id` in academic_db (users table already has telegram_id column per schema)
- Return same `AccessTokenResponse` as login endpoint
- Gateway: add `/api/auth/tma` to PUBLIC_PATHS

**Confidence:** HIGH — Telegram official docs describe this exact flow. The `telegram_id` is already stored in the users table from the Telegram bot linking flow (v5.0).

### 2. Mini App — CORS and Origin

Telegram Mini App runs at `https://t.me` or inside the native Telegram app WebView. The WebView does NOT have a predictable HTTP origin — it may send `null` origin or the tma web app URL.

**Decision:** The Mini App will be deployed as a web app at a known URL (e.g., `https://miniapp.rut.ru` or served from the same host). Gateway CORS must include that origin. During dev, `http://localhost:5174` (different port from PWA's 5173).

**Alternative:** If deployed to Telegram Web Apps hosting or Vercel, add that origin to the Gateway `allowed-origins` list.

### 3. Web Panel — Auth Integration

Angular Web Panel uses the same JWT flow as the PWA:
- `POST /api/auth/login` → access token in memory + refresh token in httpOnly cookie
- Silent refresh via interceptor on 401
- Same `Authorization: Bearer <token>` header pattern

**Key difference from PWA:** Angular stores the access token differently. The recommended pattern:
- Store access token in a service/signal (Angular signals, Angular 17+) — NOT localStorage (XSS risk)
- httpOnly cookie for refresh token works identically since it's browser-native
- Angular HttpClient interceptor (functional interceptor pattern, Angular 17+) handles token injection and 401 refresh

**No backend changes required** for Web Panel auth — same endpoints, same JWT.

### 4. Web Panel — Role-Based Views

The Web Panel serves two roles with different capabilities:

| Role | Access | Key Screens |
|------|--------|-------------|
| ADMIN | Full CRUD | Users, Groups, Semesters, Dashboard stats |
| TEACHER | Read-only | Attendance journal, student stats, own subjects |

Role comes from the JWT claim `role`. Angular route guards read from the decoded token.

The backend already enforces `@RequireRole` — the UI guards are UX-only (not security).

### 5. Landing Page — No Backend Integration

The landing page is purely static HTML/CSS. No API calls. Links to:
- PWA install URL
- Telegram bot link (`t.me/RutTrackBot`)
- (Optional) Web Panel login

**Deployment:** Can be served by a simple nginx container or just bundled into the PWA nginx under a different path (`/landing`). Simplest: separate nginx container or serve from a public CDN/GitHub Pages.

### 6. CORS Gateway Changes (REQUIRED)

Current `application.yml` only allows:
```yaml
allowed-origins:
  - "http://localhost:5173"
  - "http://localhost:80"
```

Must add for v7.0:
```yaml
allowed-origins:
  - "http://localhost:5173"   # PWA dev
  - "http://localhost:5174"   # Mini App dev
  - "http://localhost:4200"   # Angular dev
  - "http://localhost:80"     # PWA prod (nginx)
  - "http://localhost:81"     # Web Panel prod (nginx)
  # production URLs as env-driven values
```

**Note:** Telegram Mini App WebView origin is unpredictable. Options:
1. Add the deployed Mini App URL (e.g., `https://miniapp.rut.ru`) — preferred
2. Use `allowedOriginPatterns` with wildcard — less secure, avoid
3. Accept that Mini App requests go through a known domain

---

## Recommended Project Structures

### Mini App (`frontends/mini-app/`)

```
frontends/mini-app/
├── src/
│   ├── features/
│   │   ├── auth/           # TMA initData exchange → JWT
│   │   ├── schedule/       # Reuse from PWA (copy or shared package)
│   │   ├── checkin/        # Geo check-in (reuse from PWA)
│   │   ├── attendance/     # Stats (reuse from PWA)
│   │   └── homework/       # Homework list (reuse from PWA)
│   ├── shared/
│   │   ├── lib/
│   │   │   ├── axios.ts    # Same pattern as PWA (copy, not share — different auth)
│   │   │   └── tma.ts      # @telegram-apps/sdk init + theme sync
│   │   └── components/     # Telegram-native UI adaptations
│   ├── App.tsx
│   └── main.tsx
├── vite.config.ts          # Port 5174, proxy /api → Gateway
└── package.json
```

**Code sharing strategy:** Copy feature API modules (api.ts, types.ts) from PWA rather than creating a shared package. A shared npm workspace package would require monorepo tooling (Turborepo/Nx) that adds overhead. At this scale, copy-with-intent is acceptable. UI components will differ (Telegram theming vs standalone PWA).

### Web Panel (`frontends/web-panel/`)

```
frontends/web-panel/
├── src/
│   app/
│   ├── core/
│   │   ├── auth/           # AuthService (signals), HTTP interceptor, route guards
│   │   ├── api/            # HttpClient-based API services per domain
│   │   └── models/         # TypeScript interfaces matching backend DTOs
│   ├── features/
│   │   ├── dashboard/      # Admin stats overview
│   │   ├── users/          # ADMIN: CRUD users, groups, semesters
│   │   ├── schedule/       # ADMIN/HEADMAN: schedule templates
│   │   ├── attendance/     # TEACHER/ADMIN: journal, stats, reports
│   │   └── profile/        # Shared: own profile
│   ├── shared/
│   │   ├── components/     # Reusable UI (tables, forms, modals)
│   │   └── pipes/          # Date formatting, status labels
│   ├── app.config.ts       # provideRouter, provideHttpClient, interceptors
│   ├── app.routes.ts       # Lazy-loaded feature routes
│   └── app.component.ts    # Shell with sidebar nav
├── angular.json
└── package.json
```

**Angular version:** 18+ (current stable as of 2025). Use standalone components throughout — no NgModules. Functional interceptors for JWT injection. Signals for state.

### Landing (`frontends/landing/`)

```
frontends/landing/
├── index.html
├── style.css
├── main.js                 # Minimal JS (GSAP + ScrollTrigger for animations)
├── assets/
│   ├── icons/
│   └── screenshots/        # App screenshots for showcasing
└── Dockerfile              # nginx:alpine serving static files
```

---

## Architectural Patterns

### Pattern 1: TMA initData → JWT Exchange (NEW pattern for Mini App)

**What:** The Mini App sends raw `initData` from `window.Telegram.WebApp.initData` to a dedicated backend endpoint. The backend validates the HMAC signature, looks up the user, and returns a standard JWT.

**When to use:** Every time the Mini App starts. initData expires quickly (Telegram re-issues on each open).

**Trade-offs:** Adds one new endpoint and bot-token secret to auth-service. Bot token must be stored as environment variable. initData validation is well-documented and simple to implement.

```typescript
// Mini App: auth/api.ts
import { retrieveLaunchParams } from '@telegram-apps/sdk-react'

export async function tmaLogin(): Promise<AccessTokenResponse> {
  const { initDataRaw } = retrieveLaunchParams()
  const { data } = await apiClient.post<AccessTokenResponse>('/auth/tma', {}, {
    headers: { Authorization: `tma ${initDataRaw}` }
  })
  return data
}
```

```java
// Auth Service: new endpoint
// POST /auth/tma — public route
// Header: Authorization: tma <initDataRaw>
// Validate HMAC-SHA256, extract user.id (telegram_id)
// Find user in academic_db WHERE telegram_id = ?
// Return same AccessTokenResponse as /auth/login
```

### Pattern 2: Angular Functional HTTP Interceptor (JWT injection)

**What:** Angular 17+ supports functional interceptors — no class boilerplate. Inject the token from an AuthService signal.

**When to use:** All authenticated API calls from the Web Panel.

**Trade-offs:** Simpler than class-based, but requires care with dependency injection in functional context.

```typescript
// core/auth/auth.interceptor.ts
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService)
  const token = authService.accessToken()  // signal
  if (token) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))
  }
  return next(req)
}

// app.config.ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
}
```

### Pattern 3: Angular Route Guards with Role Checking

**What:** canActivate/canMatch guards read the user role from decoded JWT and block access to role-specific routes.

**When to use:** All admin-only and teacher-only routes.

```typescript
// core/auth/role.guard.ts
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService)
  return auth.role() === 'ADMIN' ? true : inject(Router).createUrlTree(['/dashboard'])
}
```

### Pattern 4: Separate nginx Containers per Frontend

**What:** Each frontend (PWA, Web Panel, Landing) gets its own nginx:alpine container with separate port mapping. Builds run locally (`npm run build`) and the `dist/` folder is mounted as a read-only volume.

**When to use:** Consistent with the existing `pwa-nginx` pattern already in docker-compose.

**Trade-offs:** Requires running 3 build commands before deployment. No hot-reload in production containers (dev uses `vite dev` / `ng serve`). Simple and predictable.

---

## Data Flow

### Mini App Auth Flow

```
Telegram opens Mini App (WebView)
    ↓
@telegram-apps/sdk init() → mounts on window.Telegram.WebApp
    ↓
retrieveLaunchParams() → { initDataRaw: "..." }
    ↓
POST /api/auth/tma  [Authorization: tma <initDataRaw>]  (public route)
    ↓
Auth Service: HMAC-SHA256 validate → extract telegram_id
Auth Service: SELECT * FROM users WHERE telegram_id = ?
Auth Service: return { accessToken, expiresIn }
    ↓
Mini App stores token in React state / ref
All API calls: Authorization: Bearer <token>
```

### Web Panel Request Flow

```
User opens /admin/users
    ↓
Angular Router → canActivate adminGuard → reads AuthService.role() signal
    ↓
UsersComponent → UsersApiService.getUsers() → HttpClient.get('/api/academic/users')
    ↓
authInterceptor injects: Authorization: Bearer <token>
    ↓
API Gateway: validates JWT, injects X-User-Id / X-User-Role headers
    ↓
Academic Service: @RequireRole(ADMIN) passes → return PagedModel<UserResponse>
    ↓
UsersComponent: renders table with HATEOAS pagination links
```

### Silent Token Refresh (Web Panel)

```
API call returns 401 (token expired)
    ↓
refreshInterceptor detects 401 + !_retry flag
    ↓
POST /api/auth/refresh (withCredentials: true → sends httpOnly cookie)
    ↓
Auth Service: validates refresh token, returns new access token
    ↓
AuthService.accessToken.set(newToken)
    ↓
Retry original request with new token
```

---

## New vs Modified Components Summary

| Component | Status | What Changes |
|-----------|--------|-------------|
| API Gateway `application.yml` | MODIFIED | Add Mini App + Web Panel origins to `allowed-origins`; add `/api/auth/tma` to public paths |
| `JwtAuthenticationFilter.java` | MODIFIED | Add `/api/auth/tma` to `PUBLIC_PATHS` set |
| Auth Service | MODIFIED | New `POST /auth/tma` endpoint: initData validation + telegram_id lookup |
| `docker-compose.yml` | MODIFIED | Add `panel-nginx` and `landing` services; add `BOT_TOKEN` env var to auth-service |
| `frontends/mini-app/` | NEW | React + Vite + @telegram-apps/sdk-react, same API pattern as PWA |
| `frontends/web-panel/` | NEW | Angular 18 standalone, HttpClient, signals, lazy routes |
| `frontends/landing/` | NEW | HTML + CSS + GSAP, no backend dependency |

---

## Build Order

1. **Auth Service extension** — `POST /auth/tma` endpoint and bot token env var. Unblocks Mini App auth. Gateway public path change included.
2. **Gateway CORS update** — Add new origins. Simple config change, can be done in same step as auth.
3. **Landing page** — Zero backend dependencies. Can build in parallel with auth.
4. **Mini App** — Depends on auth/tma endpoint. Shares API patterns with PWA (can reuse api.ts files).
5. **Web Panel** — Depends only on existing REST APIs (already complete). Angular scaffold → feature by feature.

**Critical dependency:** Mini App auth is the only new backend work. Everything else connects to existing APIs.

---

## Anti-Patterns

### Anti-Pattern 1: Shared npm Package for PWA + Mini App

**What people do:** Create `frontends/shared/` workspace package with common components and API clients.

**Why it's wrong:** Requires Turborepo or Nx for build orchestration. Mini App and PWA have different auth mechanisms (initData vs login/password) and different UI patterns (Telegram theming vs standalone). The shared code surface is small (API types, a few utility functions).

**Do this instead:** Copy feature API modules (`api.ts`, `types.ts`) from PWA into Mini App at the start of each feature. Accept the duplication — it's intentional divergence, not accidental.

### Anti-Pattern 2: Using localStorage for Access Token in Angular

**What people do:** Store the JWT access token in `localStorage` for Angular's simpler "persist across tabs" pattern.

**Why it's wrong:** XSS vulnerability. Any injected script can read `localStorage`. The PWA uses in-memory storage for the same reason.

**Do this instead:** Store access token in an Angular signal inside `AuthService`. Token lives in JS heap — lost on page refresh, recovered by silent refresh via httpOnly cookie. This is the same pattern the PWA uses.

### Anti-Pattern 3: Single nginx for All Frontends

**What people do:** Serve PWA, Web Panel, and Landing from one nginx container with path-based routing (`/panel`, `/app`, `/`).

**Why it's wrong:** SPA routing conflicts. Both React and Angular handle their own routing and need `try_files $uri /index.html` — but only for their own path subtree. One nginx config serving multiple SPAs at different paths requires complex location blocks.

**Do this instead:** Separate nginx containers per frontend (consistent with existing `pwa-nginx` pattern). Each container has its own `nginx.conf` with a clean `try_files` setup.

### Anti-Pattern 4: Validating initData on the Frontend

**What people do:** Check if initData looks valid in the Mini App before sending to the backend.

**Why it's wrong:** initData validation requires the bot token secret. Exposing that in client-side code defeats the purpose.

**Do this instead:** Send raw initData to the backend immediately. Backend validates with the secret. Frontend trusts the backend response.

---

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0-5k users | Current single-VPS docker-compose is sufficient. All frontends as static builds. |
| 5k-50k users | Add CDN (Cloudflare) in front of static files. Backend unchanged. |
| 50k+ users | Gateway horizontal scaling with load balancer. Frontend CDN mandatory. |

At the university scale (500-5000 students), no scaling changes are needed for v7.0. Static builds served by nginx containers handle thousands of concurrent connections without backend involvement.

---

## Sources

- [Telegram Mini Apps — Init Data validation](https://docs.telegram-mini-apps.com/platform/init-data) (official)
- [Telegram Mini Apps — Authorizing User](https://docs.telegram-mini-apps.com/platform/authorizing-user) (official)
- [Telegram WebApp API reference](https://core.telegram.org/bots/webapps) (official)
- [@tma.js/sdk-react npm](https://www.npmjs.com/package/@tma.js/sdk-react)
- [Angular Standalone Components — Angular 2025](https://metadesignsolutions.com/standalone-components-in-angular-clean-architecture-in-2025/)
- [Angular Functional Interceptors](https://angular.dev/guide/http/interceptors) (official)
- Existing codebase: `services/api-gateway/src/main/java/.../JwtAuthenticationFilter.java` (source of truth for public routes)
- Existing codebase: `services/api-gateway/src/main/resources/application.yml` (source of truth for CORS config)
- Existing codebase: `frontends/pwa/src/shared/lib/axios.ts` (source of truth for JWT auth pattern)

---
*Architecture research for: v7.0 Frontends (Mini App + Web Panel + Landing)*
*Researched: 2026-04-06*

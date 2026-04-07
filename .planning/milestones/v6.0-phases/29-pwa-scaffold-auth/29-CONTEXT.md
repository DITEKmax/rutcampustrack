# Phase 29: PWA Scaffold + Auth - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Students can install RutTrack on their home screen, log in with username/password, and see a working app shell that loads offline. This phase sets up the React PWA project, implements JWT auth with httpOnly refresh cookie, configures the Service Worker for app shell caching, and wires A2HS/iOS install prompt infrastructure. No data screens (schedule, check-in, stats) — those are Phase 30+.

</domain>

<decisions>
## Implementation Decisions

### Token Storage & Auth Flow
- **D-01:** Refactor auth-service backend to set refresh token as httpOnly + Secure + SameSite=Strict cookie on `/auth/login` and `/auth/refresh` responses. PWA never sees the refresh token in JavaScript.
- **D-02:** Access token stored in React memory (context/closure), attached to API requests via `Authorization: Bearer` header. Gateway already validates JWT from Authorization header — no Gateway changes needed.
- **D-03:** Silent refresh via Axios response interceptor: on 401, interceptor calls `/auth/refresh` (cookie sent automatically), receives new access token, retries original request.
- **D-04:** Logout calls `POST /auth/logout` which invalidates refresh token in Redis + clears httpOnly cookie via `Set-Cookie`. PWA clears in-memory access token and redirects to login.

### React Project Setup
- **D-05:** CSS: shadcn/ui + Tailwind CSS. Pre-built accessible components (Button, Dialog, Input, etc.) with Tailwind utility classes. Phosphor Icons (bold/fill weight per design-decisions.md).
- **D-06:** Data fetching: TanStack Query (React Query) with Axios as HTTP transport. Handles caching, background refetch, stale-while-revalidate.
- **D-07:** Routing: React Router v7 with nested routes and lazy loading.
- **D-08:** Project structure: Hybrid — `src/shared/` for common components/hooks/utils + `src/features/` for domain logic (auth/, schedule/, checkin/, etc.). Feature folders contain components, hooks, and API layer.
- **D-09:** Animation: Motion (framer-motion) per design-decisions.md for page transitions and UI feedback.

### A2HS Install Prompt
- **D-10:** Phase 29 captures `beforeinstallprompt` event and stores the deferred prompt in a React context. Phase 30 will call `prompt()` after first successful check-in. Clean separation: infrastructure now, trigger later.
- **D-11:** iOS Safari onboarding: full-screen overlay on first visit when detecting iOS Safari + not standalone mode. Step-by-step visual instructions (Share -> Add to Home Screen). Dismiss stores `ios_onboarding_shown` flag in localStorage. Shows once.

### Offline Shell Scope
- **D-12:** Offline unauthenticated: login page loads from Service Worker cache with subtle "You are offline" banner. Login button disabled with "Connect to internet to sign in" message.
- **D-13:** Offline authenticated (access token still valid): app shell with navigation loads, but data screens show "No connection" empty states. No offline data caching in Phase 29 — that's Phase 30 (SCHED-03 stale-while-revalidate).
- **D-14:** Service Worker precaches all app chunks via vite-plugin-pwa with `injectManifest` strategy (already decided in STATE.md — needed for custom push handler). All JS/CSS/HTML in build output precached.

### Claude's Discretion
- Exact shadcn/ui component selection and theme configuration
- Tailwind color palette / dark mode setup
- Axios instance configuration (base URL, interceptor details)
- TanStack Query default options (staleTime, gcTime, retry)
- React Router route structure and layout components
- vite-plugin-pwa workbox configuration details
- Auth context/provider implementation pattern
- Login form design and validation approach

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Auth service (backend changes needed)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` — Login/refresh/logout endpoints that need httpOnly cookie refactor
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/AuthService.java` — Token generation logic
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/config/JwtProperties.java` — Access 15min, refresh 7 days
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/TokenResponse.java` — Current response DTO (will change)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/RefreshRequest.java` — Currently takes refreshToken in body (will change)
- `services/auth-service/src/main/resources/application.yml` — JWT expiration config

### API Gateway
- `services/api-gateway/src/main/resources/application.yml` — Routes and CORS config (Phase 28 already configured)

### PWA infrastructure (Phase 28 output)
- `frontends/pwa/nginx.conf` — Nginx config for SPA serving, SW no-cache headers
- `frontends/pwa/dist/index.html` — Placeholder from Phase 28
- `frontends/pwa/dist/sw.js` — Placeholder from Phase 28
- `docker-compose.yml` — pwa-nginx container already configured

### Design system
- `docs/design-decisions.md` — Phosphor Icons (bold/fill), Motion (framer-motion), manifest spec, iOS onboarding requirements

### Requirements
- `.planning/REQUIREMENTS.md` &#167;PWA Foundation — PWA-01..07

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Auth endpoints**: `/auth/login`, `/auth/refresh`, `/auth/logout` already implemented — need httpOnly cookie refactor, not rewrite
- **nginx.conf**: Already configured with SPA fallback, SW no-cache, asset caching headers
- **docker-compose pwa-nginx**: Container already wired in Phase 28

### Established Patterns
- **Gateway auth flow**: JWT in Authorization header -> Gateway validates -> injects X-User-Id/X-User-Role/X-Group-Id headers to downstream services
- **Contract-first**: api-contract (java-library) + app (Spring Boot) — auth-service is the exception (no separate contract module), but DTOs exist
- **RFC 7807 errors**: All services return `ErrorResponse` record for error cases

### Integration Points
- **Auth service refactor**: `AuthController.login()` and `AuthController.refresh()` need to set httpOnly cookies instead of (or alongside) returning refresh token in body
- **Gateway CORS**: Already configured in Phase 28 — must include `Access-Control-Allow-Credentials: true` for cookie flow
- **PWA build output**: Vite build replaces placeholder files in `frontends/pwa/dist/`, served by existing nginx container

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 29-pwa-scaffold-auth*
*Context gathered: 2026-04-06*

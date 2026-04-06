# Phase 36: Mini App Scaffold + Auth - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Vite React scaffold for Telegram Mini App with Telegram SDK integration, initData-based auth flow, in-memory token management, and dev mock environment. Features (schedule, check-in, stats, homework) are Phase 37.

</domain>

<decisions>
## Implementation Decisions

### Code Sharing with PWA
- **D-01:** Independent project — separate `package.json`, own axios client, own types. Copy patterns from PWA but diverge freely. No monorepo workspace.
- **D-02:** Same UI stack as PWA — Tailwind + shadcn + Motion. Telegram theme colors mapped to CSS variables.
- **D-03:** Same data layer as PWA — TanStack Query + Axios with interceptors.

### Auth Flow & Token Storage
- **D-04:** Auth on app mount, blocking — AuthProvider calls `POST /api/auth/tma` with initData immediately. App shows loading state until JWT received.
- **D-05:** Memory-only tokens — both access and refresh tokens in React state only. No localStorage, no sessionStorage. Telegram re-sends initData on each Mini App open.
- **D-06:** On 401, re-authenticate via initData — call `/api/auth/tma` again with cached initData. No `/api/auth/refresh-body` usage in Mini App.

### Dev Mock Environment
- **D-07:** Mock WebApp object + env flag — when `VITE_TMA_DEV=true`, inject fake `window.Telegram.WebApp` with mock initData, theme params, viewport. Real TMA auth bypassed with test JWT.
- **D-08:** Configurable mock user via `VITE_TMA_MOCK_USER` env var — maps to existing test accounts (student00001, teacher00001, etc.). Default: student.

### Telegram SDK Integration
- **D-09:** Use `@telegram-apps/sdk-react` — official React bindings with hooks (useInitData, useViewport, useThemeParams, useBackButton).
- **D-10:** Map Telegram theme colors to Tailwind CSS variables — read themeParams on mount, set as CSS custom properties (`--tg-bg`, `--tg-text`, etc.), reference in Tailwind config.
- **D-11:** Include react-router in scaffold — set up routing with placeholder pages. BackButton integration needs routing context. Phase 37 fills in feature pages.

### Claude's Discretion
- Project file structure within `frontends/mini-app/`
- Exact Tailwind config and shadcn component selection
- Mock WebApp implementation details
- Loading/error states during initial auth

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Auth endpoints
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` — TMA auth and refresh-body endpoints
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/TmaService.java` — initData HMAC validation logic
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/TmaAuthRequest.java` — Request DTO for TMA auth

### PWA auth patterns (reference, not shared code)
- `frontends/pwa/src/features/auth/AuthProvider.tsx` — Auth context pattern to adapt for TMA
- `frontends/pwa/src/shared/lib/axios.ts` — Interceptor pattern with token refresh queue
- `frontends/pwa/package.json` — Reference for dependency versions (React 19, TanStack Query, Tailwind)

### Design decisions
- `docs/design-decisions.md` — Icons (Phosphor bold/fill for mobile), animations (Motion), branding

### Infrastructure
- `frontends/mini-app/nginx.conf` — Existing nginx config from Phase 33
- `.planning/REQUIREMENTS.md` §TMA-01..TMA-05 — Requirements for this phase

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- PWA `AuthProvider.tsx` pattern: context + interceptor wiring — adapt for initData-based auth
- PWA `axios.ts` interceptor queue: token refresh with request queuing — simplify for initData re-auth
- PWA `shared/lib/queryClient.ts`: TanStack Query configuration — copy and adjust

### Established Patterns
- Feature-based folder structure: `features/auth/`, `features/schedule/`, etc.
- Shared utilities in `shared/lib/` and `shared/hooks/`
- Axios client with interceptors for auth token management
- Vitest for testing with Testing Library

### Integration Points
- API Gateway at `/api` prefix — same as PWA
- Gateway CORS configured for Mini App origin (Phase 33)
- nginx container serves built Mini App (Phase 33 infrastructure)

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches based on PWA patterns adapted for Telegram Mini App context.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 36-mini-app-scaffold-auth*
*Context gathered: 2026-04-07*

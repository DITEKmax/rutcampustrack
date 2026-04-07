# Phase 29: PWA Scaffold + Auth - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-06
**Phase:** 29-pwa-scaffold-auth
**Areas discussed:** Token storage & auth flow, React project setup, A2HS install prompt, Offline shell scope

---

## Token Storage & Auth Flow

| Option | Description | Selected |
|--------|-------------|----------|
| Refactor backend to httpOnly cookie | Auth service sets refresh token as httpOnly + Secure + SameSite=Strict cookie. PWA never sees refresh token in JS. Requires backend changes. | ✓ |
| Keep in memory only | Store refresh token in JS memory. Session lost on page reload. No backend changes. | |
| Store in localStorage | Persist in localStorage. Survives reload but vulnerable to XSS. Contradicts PWA-01. | |

**User's choice:** Refactor backend to httpOnly cookie
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Authorization: Bearer header | Access token in React state/context, attached as Bearer header. Gateway already validates from header. | ✓ |
| httpOnly cookie for access token too | Both tokens as cookies. Requires CSRF protection and Gateway changes. | |

**User's choice:** Authorization: Bearer header
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Axios interceptor on 401 | On 401, interceptor calls /auth/refresh, gets new access token, retries request. | ✓ |
| Proactive timer refresh | Timer at ~14 min refreshes before expiry. Prevents 401s but adds complexity. | |
| Both timer + interceptor | Timer refreshes proactively, interceptor catches edge cases. Most robust. | |

**User's choice:** Axios interceptor on 401
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, server-side invalidation | POST /auth/logout deletes refresh token from Redis + clears httpOnly cookie. Already implemented in backend. | ✓ |
| Client-side only | Clear cookie and in-memory token. Refresh token remains valid in Redis until expiry. | |

**User's choice:** Yes, server-side invalidation
**Notes:** None

---

## React Project Setup

| Option | Description | Selected |
|--------|-------------|----------|
| Tailwind CSS | Already in phases-plan.md. Utility classes, mobile-first. | |
| CSS Modules | Scoped styles per component. Lighter but more manual CSS. | |
| shadcn/ui + Tailwind | Tailwind plus pre-built accessible components. Copy-paste, customizable. | ✓ |

**User's choice:** shadcn/ui + Tailwind
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| TanStack Query | Already in phases-plan.md. Caching, background refetch, stale-while-revalidate. Industry standard. | ✓ |
| SWR | Lighter alternative. Fewer features. | |
| Plain Axios + React state | Manual data fetching. Full control but no built-in cache. | |

**User's choice:** TanStack Query
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| React Router v7 | Standard for React SPAs. Nested routes, lazy loading. Most mature. | ✓ |
| TanStack Router | Type-safe routing. Newer, less ecosystem support. | |

**User's choice:** React Router v7
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Feature-based | src/features/auth/, src/features/schedule/ — each with components, hooks, api. | |
| Layer-based | src/components/, src/hooks/, src/api/, src/pages/. Simpler but messy at scale. | |
| Hybrid | src/shared/ for common + src/features/ for domain logic. Best of both. | ✓ |

**User's choice:** Hybrid
**Notes:** None

---

## A2HS Install Prompt

| Option | Description | Selected |
|--------|-------------|----------|
| Capture beforeinstallprompt, defer to Phase 30 | Phase 29 intercepts and stores event. Phase 30 calls prompt() after first check-in. | ✓ |
| Show prompt on first login | Trigger immediately after login. Contradicts success criteria. | |
| Manual install banner only | Custom banner with instructions. Works everywhere but less native. | |

**User's choice:** Capture beforeinstallprompt, defer to Phase 30
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| Full-screen overlay on first visit | Detect iOS Safari + not standalone. Step-by-step instructions. Shows once. | ✓ |
| Subtle bottom banner | Small dismissible banner. Less intrusive but easy to ignore. | |
| Settings page only | Instructions in app settings. Discoverable but low conversion. | |

**User's choice:** Full-screen overlay on first visit
**Notes:** None

---

## Offline Shell Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Login page with offline indicator | App shell + login form from SW cache. Subtle 'You are offline' banner. Login disabled. | ✓ |
| Dedicated offline page | Separate 'No connection' page with retry button. | |

**User's choice:** Login page with offline indicator
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| App shell with empty screens | Navigation loads, data screens show 'No connection' empty states. No data caching in Phase 29. | ✓ |
| Redirect to login | Show login if offline and can't verify token. Confusing. | |
| Full skeleton with cached data | Cache API responses. Too ambitious for Phase 29. | |

**User's choice:** App shell with empty screens
**Notes:** None

| Option | Description | Selected |
|--------|-------------|----------|
| All app chunks | vite-plugin-pwa injectManifest precaches all JS/CSS/HTML. Small app, minimal size. | ✓ |
| Only login + shell routes | Selective precaching. Saves bandwidth but other routes fail offline. | |

**User's choice:** All app chunks
**Notes:** None

---

## Claude's Discretion

- Exact shadcn/ui component selection and theme configuration
- Tailwind color palette / dark mode setup
- Axios instance configuration details
- TanStack Query default options
- React Router route structure and layout components
- vite-plugin-pwa workbox configuration details
- Auth context/provider implementation pattern
- Login form design and validation

## Deferred Ideas

None — discussion stayed within phase scope

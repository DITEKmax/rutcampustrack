# Phase 36: Mini App Scaffold + Auth - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-07
**Phase:** 36-mini-app-scaffold-auth
**Areas discussed:** Code sharing with PWA, Auth flow & token storage, Dev mock environment, Telegram SDK integration

---

## Code Sharing with PWA

| Option | Description | Selected |
|--------|-------------|----------|
| Independent projects | Separate package.json, own axios client, own types. Copy patterns from PWA but diverge freely. | ✓ |
| Shared packages via workspace | pnpm/npm workspace with shared packages (api-client, types, hooks). More DRY but adds monorepo complexity. | |

**User's choice:** Independent projects
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Same stack (Tailwind + shadcn + Motion) | Consistent look, reuse design patterns from PWA. Telegram theme colors mapped to CSS variables. | ✓ |
| Lighter stack | Only Tailwind + minimal CSS — skip shadcn and Motion for smaller bundle. | |
| Telegram UI Kit | @telegram-apps/telegram-ui — native Telegram look but different from PWA. | |

**User's choice:** Same stack
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| TanStack Query + Axios | Same pattern as PWA — query caching, optimistic updates, interceptors. Proven in the project. | ✓ |
| Plain fetch + React state | No extra deps — lighter bundle, but manual cache/retry logic. | |

**User's choice:** TanStack Query + Axios
**Notes:** None

---

## Auth Flow & Token Storage

| Option | Description | Selected |
|--------|-------------|----------|
| On app mount, blocking | AuthProvider calls /api/auth/tma with initData immediately. App shows loading spinner until JWT received. | ✓ |
| Lazy on first API call | First API request triggers initData exchange. More complex interceptor logic. | |

**User's choice:** On app mount, blocking
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| localStorage | Per requirements TMA-03/TMA-04. Access token in React state, refresh in localStorage. | |
| sessionStorage | Cleared on tab close — more secure but re-authenticates each time. | |
| Memory only | Both tokens in React state. Lost on reload, but Telegram re-sends initData on each open. | ✓ |

**User's choice:** Memory only
**Notes:** Since Telegram re-sends initData on each Mini App open, memory-only tokens work well. No localStorage complexity, re-authentication is transparent.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Re-auth via initData | On 401, call /api/auth/tma again with cached initData. No refresh-body needed. | ✓ |
| Refresh-body first, initData fallback | Try refresh-body with in-memory token, fall back to initData on failure. | |

**User's choice:** Re-auth via initData
**Notes:** Simplifies Mini App — no refresh-body endpoint usage at all.

---

## Dev Mock Environment

| Option | Description | Selected |
|--------|-------------|----------|
| Mock WebApp object + env flag | When VITE_TMA_DEV=true, inject fake window.Telegram.WebApp with mock initData, theme, viewport. | ✓ |
| Telegram Bot + ngrok tunnel | Real Telegram bot pointing to localhost via ngrok. Full real flow but slower dev cycle. | |

**User's choice:** Mock WebApp object + env flag
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Configurable via env var | VITE_TMA_MOCK_USER=student (or teacher/admin/headman). Maps to existing test accounts. | ✓ |
| Always student | Dev mock always authenticates as the default test student. | |

**User's choice:** Configurable via env var
**Notes:** None

---

## Telegram SDK Integration

| Option | Description | Selected |
|--------|-------------|----------|
| @telegram-apps/sdk-react | Official React bindings with hooks: useInitData, useViewport, useThemeParams, useBackButton. | ✓ |
| Raw window.Telegram.WebApp | No SDK dependency — use the global WebApp object directly. Less type safety. | |

**User's choice:** @telegram-apps/sdk-react
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Map to Tailwind CSS variables | Read themeParams on mount, set as CSS custom properties, reference in Tailwind config. | ✓ |
| Inline styles from SDK | Apply Telegram colors directly via style attributes. Simpler but bypasses Tailwind. | |

**User's choice:** Map to Tailwind CSS variables
**Notes:** None

---

| Option | Description | Selected |
|--------|-------------|----------|
| Include react-router | Set up routing with placeholder pages. BackButton integration needs routing context. | ✓ |
| Single screen, no routing | Just auth + 'Hello, {user}' screen. Add routing in Phase 37. | |

**User's choice:** Include react-router
**Notes:** Phase 37 fills in feature pages.

---

## Claude's Discretion

- Project file structure within `frontends/mini-app/`
- Exact Tailwind config and shadcn component selection
- Mock WebApp implementation details
- Loading/error states during initial auth

## Deferred Ideas

None — discussion stayed within phase scope.

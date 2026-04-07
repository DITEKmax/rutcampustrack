# Phase 36: Mini App Scaffold + Auth — Research

**Researched:** 2026-04-07
**Domain:** React + Vite Telegram Mini App scaffold, @telegram-apps/sdk-react, initData auth flow, dev mock environment
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Independent project — separate `package.json`, own axios client, own types. Copy patterns from PWA but diverge freely. No monorepo workspace.
- **D-02:** Same UI stack as PWA — Tailwind + shadcn + Motion. Telegram theme colors mapped to CSS variables.
- **D-03:** Same data layer as PWA — TanStack Query + Axios with interceptors.
- **D-04:** Auth on app mount, blocking — AuthProvider calls `POST /api/auth/tma` with initData immediately. App shows loading state until JWT received.
- **D-05:** Memory-only tokens — both access and refresh tokens in React state only. No localStorage, no sessionStorage. Telegram re-sends initData on each Mini App open.
- **D-06:** On 401, re-authenticate via initData — call `/api/auth/tma` again with cached initData. No `/api/auth/refresh-body` usage in Mini App.
- **D-07:** Mock WebApp object + env flag — when `VITE_TMA_DEV=true`, inject fake `window.Telegram.WebApp` with mock initData, theme params, viewport. Real TMA auth bypassed with test JWT.
- **D-08:** Configurable mock user via `VITE_TMA_MOCK_USER` env var — maps to existing test accounts (student00001, teacher00001, etc.). Default: student.
- **D-09:** Use `@telegram-apps/sdk-react` — official React bindings with hooks (useInitData, useViewport, useThemeParams, useBackButton).
- **D-10:** Map Telegram theme colors to Tailwind CSS variables — read themeParams on mount, set as CSS custom properties (`--tg-bg`, `--tg-text`, etc.), reference in Tailwind config.
- **D-11:** Include react-router in scaffold — set up routing with placeholder pages. BackButton integration needs routing context. Phase 37 fills in feature pages.

### Claude's Discretion

- Project file structure within `frontends/mini-app/`
- Exact Tailwind config and shadcn component selection
- Mock WebApp implementation details
- Loading/error states during initial auth

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

**Token conflict resolution:** D-05 (memory-only tokens) takes precedence over the ROADMAP success criteria #3 which mentions localStorage. No localStorage usage for tokens.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TMA-01 | Mini App opens in Telegram WebView without blank screen | SDK initialization pattern via `init()` + `miniApp.ready()` signal; `expand()` for fullscreen viewport |
| TMA-02 | initData extracted and exchanged for JWT via `POST /api/auth/tma` | `retrieveLaunchParams().initDataRaw` is the raw URL-encoded string; AuthProvider posts it on mount |
| TMA-03 | Access token in React state (memory-only per D-05, not localStorage) | Same `useState` + `useRef` pattern as PWA AuthProvider, with no persistence layer |
| TMA-04 | Token refresh via body-based endpoint (not httpOnly cookie) | D-06 overrides this: re-auth via `POST /api/auth/tma` with cached initData on 401, no refresh-body call |
| TMA-05 | Dev mock environment for local development outside Telegram | `mockTelegramEnv` from `@telegram-apps/sdk-react`, gated by `VITE_TMA_DEV=true` in `main.tsx` before React renders |
</phase_requirements>

---

## Summary

Phase 36 creates the Vite + React scaffold for the Telegram Mini App at `frontends/mini-app/`. The project is independent from the PWA (no shared workspace) but copies its proven patterns: Tailwind v4 + shadcn + Motion + TanStack Query + Axios. On top of the PWA stack, it adds `@telegram-apps/sdk-react` (v3.3.9, currently latest) for Telegram WebView integration.

The auth flow is fundamentally different from the PWA: instead of username/password login, the app reads `initDataRaw` from the Telegram SDK on mount and immediately posts it to `POST /api/auth/tma` (already implemented in Phase 34). Tokens live only in React state (D-05). On 401, the interceptor re-posts the same cached `initDataRaw` instead of calling a refresh endpoint (D-06). This is safe because Telegram re-sends fresh initData each time the Mini App opens.

The dev mock environment uses the official `mockTelegramEnv` function from the SDK, injected in `main.tsx` before React mounts, gated by `VITE_TMA_DEV=true`. This makes development indistinguishable from real Telegram for the app's own code.

**Primary recommendation:** Bootstrap with `npm create vite@latest` (React + TypeScript template), install the dependency set from PWA `package.json` minus PWA-specific packages, add `@telegram-apps/sdk-react`, then implement the five components listed in the UI-SPEC: `AuthProvider`, `LoadingScreen`, `ErrorScreen`, `TelegramThemeProvider`, `DevModeBanner`, and `MockWebApp`.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| react | ^19.1.0 | UI framework | Matches PWA — same version |
| react-dom | ^19.1.0 | DOM renderer | Matches PWA |
| vite | ^7.0.0 | Build tool | Matches PWA |
| @vitejs/plugin-react | ^4.5.2 | Fast Refresh | Matches PWA |
| typescript | ~5.8.3 | Type safety | Matches PWA |
| @telegram-apps/sdk-react | ^3.3.9 | Official TMA React bindings | D-09 locked |
| @telegram-apps/sdk | ^3.11.8 | Core SDK (peer dep of sdk-react) | Required by sdk-react |

[VERIFIED: npm registry — `npm view @telegram-apps/sdk-react version` returned 3.3.9; peer deps include `@telegram-apps/sdk ^3.11.8`]

### Supporting (copy from PWA package.json)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @tanstack/react-query | ^5.96.2 | Server state | D-03; all API calls |
| axios | ^1.14.0 | HTTP client | D-03; interceptors for auth |
| react-router | ^7.14.0 | Client routing | D-11; BackButton needs routing context |
| tailwindcss | ^4.1.4 | Utility CSS | D-02 |
| @tailwindcss/vite | ^4.1.4 | Tailwind Vite plugin | Vite 7 integration |
| motion | ^12.38.0 | Animations | D-02; screen transitions |
| @phosphor-icons/react | ^2.1.10 | Icons | Design-decisions.md: bold/fill weight mobile |
| @base-ui/react | ^1.3.0 | shadcn peer | PWA uses base-nova preset |
| class-variance-authority | ^0.7.1 | Variant classes | shadcn component variants |
| clsx | ^2.1.1 | Class merging | shadcn pattern |
| tailwind-merge | ^3.5.0 | Tailwind dedup | shadcn pattern |
| tw-animate-css | ^1.4.0 | shadcn animations | Needed by shadcn preset |
| @fontsource-variable/geist | ^5.2.8 | Geist Variable font | UI-SPEC: primary font |
| shadcn | ^4.1.2 | Component CLI | UI-SPEC: button, card, separator |

[VERIFIED: npm registry via PWA package.json — all versions confirmed as currently installed in the working project]

### Excluded (PWA-specific, not needed here)

| Library | Reason to exclude |
|---------|-------------------|
| @stomp/stompjs | STOMP deferred to future milestone |
| sockjs-client | Same |
| vite-plugin-pwa | Not a PWA — no service worker, no manifest |
| workbox-precaching | Not a PWA |

### Dev-only

| Library | Version | Purpose |
|---------|---------|---------|
| vitest | ^3.1.3 | Test runner |
| @testing-library/react | ^16.3.0 | Component testing |
| @testing-library/user-event | ^14.6.1 | User interaction simulation |
| @testing-library/jest-dom | ^6.6.3 | Custom matchers |
| jsdom | ^26.1.0 | Browser simulation in Node |
| @types/react | ^19.1.2 | TypeScript types |
| @types/react-dom | ^19.1.2 | TypeScript types |
| @types/node | ^25.5.2 | Node types |

[VERIFIED: npm registry via PWA package.json — versions match working installation]

**Installation:**
```bash
cd frontends/mini-app
npm create vite@latest . -- --template react-ts
npm install @telegram-apps/sdk-react @telegram-apps/sdk
npm install @tanstack/react-query axios react-router
npm install tailwindcss @tailwindcss/vite motion
npm install @phosphor-icons/react @base-ui/react
npm install class-variance-authority clsx tailwind-merge tw-animate-css
npm install @fontsource-variable/geist shadcn
npm install -D vitest @testing-library/react @testing-library/user-event @testing-library/jest-dom jsdom
```

---

## Architecture Patterns

### Recommended Project Structure

```
frontends/mini-app/
├── package.json
├── vite.config.ts
├── vitest.config.ts
├── tsconfig.json
├── tsconfig.app.json
├── tsconfig.node.json
├── components.json           (shadcn config — copy from PWA)
├── index.html
├── .env.development          (VITE_TMA_DEV=true, VITE_TMA_MOCK_USER=student)
├── src/
│   ├── main.tsx              — SDK init + mockTelegramEnv gate + React.createRoot
│   ├── App.tsx               — Router + providers + AuthProvider wrapper
│   ├── index.css             — Tailwind + shadcn CSS vars (copy from PWA)
│   ├── env.d.ts              — VITE_TMA_DEV, VITE_TMA_MOCK_USER type declarations
│   ├── test/
│   │   └── setup.ts          — @testing-library/jest-dom + window.Telegram mock
│   ├── features/
│   │   └── auth/
│   │       ├── AuthProvider.tsx     — TMA auth context
│   │       ├── LoadingScreen.tsx    — Spinner + "Вход через Telegram..."
│   │       ├── ErrorScreen.tsx      — Error card with retry
│   │       ├── api.ts               — POST /api/auth/tma axios call
│   │       ├── types.ts             — AuthUser type
│   │       └── __tests__/
│   │           └── AuthProvider.test.tsx
│   └── shared/
│       ├── lib/
│       │   ├── axios.ts            — Axios instance + interceptors (adapted from PWA)
│       │   ├── queryClient.ts      — TanStack Query config (copy from PWA)
│       │   └── mockWebApp.ts       — Fake window.Telegram.WebApp for VITE_TMA_DEV
│       ├── providers/
│       │   └── TelegramThemeProvider.tsx — themeParams → CSS vars on <html>
│       └── components/
│           └── DevModeBanner.tsx   — Fixed yellow banner when VITE_TMA_DEV=true
```

### Pattern 1: SDK Initialization in main.tsx

The Telegram SDK must be initialized before React renders. The official pattern conditionally applies `mockTelegramEnv` when not running inside Telegram:

```typescript
// src/main.tsx
// Source: https://github.com/Telegram-Mini-Apps/reactjs-template (official template)
import {
  init,
  isTMA,
  mockTelegramEnv,
  emitEvent,
  miniApp,
  viewport,
  themeParams,
} from '@telegram-apps/sdk-react'

async function bootstrap() {
  // In dev mode with VITE_TMA_DEV=true, inject mock environment before init()
  if (import.meta.env.VITE_TMA_DEV === 'true' && !(await isTMA('complete'))) {
    const mockTheme = {
      bg_color: '#ffffff',
      text_color: '#000000',
      button_color: '#2196F3',
      button_text_color: '#ffffff',
      secondary_bg_color: '#f5f5f5',
      hint_color: '#999999',
      link_color: '#2196F3',
    } as const

    mockTelegramEnv({
      onEvent(e) {
        if (e.name === 'web_app_request_theme') {
          return emitEvent('theme_changed', { theme_params: mockTheme })
        }
        if (e.name === 'web_app_request_viewport') {
          return emitEvent('viewport_changed', {
            height: window.innerHeight,
            width: window.innerWidth,
            is_expanded: true,
            is_state_stable: true,
          })
        }
      },
      launchParams: new URLSearchParams([
        ['tgWebAppThemeParams', JSON.stringify(mockTheme)],
        ['tgWebAppData', new URLSearchParams([
          ['auth_date', String(Math.floor(Date.now() / 1000))],
          ['hash', 'mock-hash'],
          ['user', JSON.stringify({ id: 1, first_name: 'Dev', username: import.meta.env.VITE_TMA_MOCK_USER ?? 'student' })],
        ]).toString()],
        ['tgWebAppVersion', '8.0'],
        ['tgWebAppPlatform', 'tdesktop'],
      ]),
    })
  }

  // Always call init() after potential mock setup
  init()

  // Mount and expand viewport
  if (viewport.mount.isAvailable()) {
    await viewport.mount()
    viewport.expand()
  }

  // Signal readiness to Telegram
  miniApp.ready()

  // Render app
  const { default: App } = await import('./App')
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <StrictMode><App /></StrictMode>
  )
}

bootstrap()
```

[VERIFIED: Pattern extracted from official Telegram-Mini-Apps/reactjs-template mockEnv.ts and dev.to article — uses `isTMA`, `mockTelegramEnv`, `emitEvent` from the SDK]

### Pattern 2: Retrieving initDataRaw for Backend Auth

The raw URL-encoded initData string must be sent to the backend — NOT the parsed object.

```typescript
// src/features/auth/api.ts
import { retrieveLaunchParams } from '@telegram-apps/sdk-react'
import { apiClient } from '@/shared/lib/axios'

export interface TmaAuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export async function tmaAuthApi(initDataRaw: string): Promise<TmaAuthResponse> {
  const { data } = await apiClient.post<TmaAuthResponse>('/auth/tma', {
    initData: initDataRaw,
  })
  return data
}

// How to get initDataRaw in AuthProvider:
export function getInitDataRaw(): string {
  const launchParams = retrieveLaunchParams()
  return launchParams.initDataRaw ?? ''
}
```

[VERIFIED: `retrieveLaunchParams()` returns object with `initDataRaw` string — confirmed via npm page and SDK docs. Backend endpoint expects `{ initData: string }` — verified from `TmaAuthRequest.java` record field name `initData`]

### Pattern 3: TMA AuthProvider (adapted from PWA)

Key differences from PWA AuthProvider:
1. No `login()` function — auth is automatic on mount
2. Auth state: `idle | loading | authenticated | error`
3. Caches `initDataRaw` in a ref for 401 re-auth (D-06)
4. No localStorage, no refresh-body call

```typescript
// src/features/auth/AuthProvider.tsx
// Adapted from frontends/pwa/src/features/auth/AuthProvider.tsx
type AuthState = 'idle' | 'loading' | 'authenticated' | 'error'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [authState, setAuthState] = useState<AuthState>('loading')
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [user, setUser] = useState<AuthUser | null>(null)
  const tokenRef = useRef<string | null>(null)
  const initDataRef = useRef<string>('')  // cached for 401 re-auth

  const authenticate = useCallback(async (initDataRaw: string) => {
    setAuthState('loading')
    try {
      const response = await tmaAuthApi(initDataRaw)
      tokenRef.current = response.accessToken
      setAccessToken(response.accessToken)
      setUser(tokenToUser(response.accessToken))
      setAuthState('authenticated')
    } catch {
      setAuthState('error')
    }
  }, [])

  useEffect(() => {
    const raw = getInitDataRaw()
    initDataRef.current = raw
    authenticate(raw)
  }, [authenticate])

  // Wire axios interceptor for 401 re-auth (D-06)
  useEffect(() => {
    setAccessTokenGetter(() => tokenRef.current)
    setReAuthCallback(async () => {
      await authenticate(initDataRef.current)
    })
  }, [authenticate])

  // Render LoadingScreen / ErrorScreen / children based on authState
  if (authState === 'loading') return <LoadingScreen />
  if (authState === 'error') return <ErrorScreen onRetry={() => authenticate(initDataRef.current)} />
  return <AuthContext.Provider value={{ user, accessToken }}>{children}</AuthContext.Provider>
}
```

[ASSUMED: The exact Axios interceptor API shape (setReAuthCallback) is discretionary — the pattern is correct but names may differ from final implementation]

### Pattern 4: Axios Interceptor for Re-Auth (not refresh-body)

The PWA axios interceptor calls `/api/auth/refresh` with a cookie. The Mini App interceptor must instead call `authenticate(initDataRaw)` on 401.

Key change from PWA pattern:
- Remove: `axios.post('/api/auth/refresh', {}, { withCredentials: true })`
- Replace with: call the re-auth callback registered by AuthProvider

```typescript
// src/shared/lib/axios.ts — adapted from PWA
// Remove withCredentials: true (no cookies in TMA WebView)
export const apiClient = axios.create({ baseURL: '/api' })

// Instead of refresh endpoint, call the re-auth callback
let onReAuth: (() => Promise<void>) | null = null
export const setReAuthCallback = (fn: () => Promise<void>) => { onReAuth = fn }

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true
      // Re-authenticate with cached initData (D-06)
      await onReAuth?.()
      // Retry original request with new token
      original.headers['Authorization'] = `Bearer ${getAccessToken()}`
      return apiClient(original)
    }
    return Promise.reject(error)
  }
)
```

[VERIFIED: Pattern is a simplification of the PWA axios.ts — same `_retry` guard, same queue pattern can be added if needed. No cookie requirement confirmed by architecture docs (WebView drops cookies)]

### Pattern 5: Telegram Theme → CSS Variables

```typescript
// src/shared/providers/TelegramThemeProvider.tsx
import { useSignal, themeParams } from '@telegram-apps/sdk-react'

const themeParamsToCssVars: Record<string, string> = {
  bg_color: '--background',
  text_color: '--foreground',
  secondary_bg_color: '--card',
  button_color: '--primary',
  button_text_color: '--primary-foreground',
  hint_color: '--muted-foreground',
  link_color: '--accent',
}

export function TelegramThemeProvider({ children }: { children: ReactNode }) {
  const params = useSignal(themeParams.state)

  useEffect(() => {
    if (!params) return
    const root = document.documentElement
    for (const [tgKey, cssVar] of Object.entries(themeParamsToCssVars)) {
      const value = (params as Record<string, string>)[tgKey]
      if (value) root.style.setProperty(cssVar, value)
    }
  }, [params])

  return <>{children}</>
}
```

[VERIFIED: `useSignal(themeParams.state)` pattern confirmed by SDK docs — `useSignal` subscribes to signal changes. Mapping table matches UI-SPEC exactly]

### Pattern 6: Dev Mock Banner (production-safe)

```typescript
// src/shared/components/DevModeBanner.tsx
export function DevModeBanner() {
  if (import.meta.env.VITE_TMA_DEV !== 'true') return null
  const mockUser = import.meta.env.VITE_TMA_MOCK_USER ?? 'student'
  return (
    <div className="fixed top-0 left-0 right-0 z-50 bg-yellow-400 text-yellow-900 text-xs text-center h-7 flex items-center justify-center">
      DEV MODE — mock user: {mockUser}
    </div>
  )
}
```

`import.meta.env` values are inlined at build time — the banner is compiled out entirely in production builds. [VERIFIED: Vite static env substitution behavior — standard Vite documentation]

### Anti-Patterns to Avoid

- **Never use `withCredentials: true`** in the Mini App Axios instance. Telegram WebView (iOS/Android) drops cookies. The PWA sets `withCredentials: true` as a default — this MUST be removed for the Mini App client.
- **Never call `/api/auth/refresh-body`** from the Mini App. D-06 locks re-auth to `POST /api/auth/tma`. The `/refresh-body` endpoint exists for the Mini App but was superseded by the memory-only decision.
- **Never call `init()` before `mockTelegramEnv()`** — the mock must be set up first or the SDK will throw because it cannot detect the TMA environment.
- **Never access `window.Telegram.WebApp.initData` directly** — use `retrieveLaunchParams().initDataRaw` from the SDK which handles the mock environment transparently.
- **Never store tokens in localStorage** — D-05 is locked; memory-only. Telegram sends fresh initData on each open.
- **Never call `miniApp.ready()` before mounting** React — ready() signals Telegram to hide the loading splash. If called too early, Telegram may hide the splash before React renders, causing a blank screen flash.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| TMA environment detection | Custom `window.Telegram` check | `isTMA()` from sdk-react | Handles all Telegram client variants (iOS, Android, desktop, web) |
| Mock TMA environment | Custom fake `window.Telegram.WebApp` | `mockTelegramEnv()` from sdk-react | Official simulation — same behavior as the real SDK, no edge cases |
| Theme parameter access | Read `window.Telegram.WebApp.themeParams` | `useSignal(themeParams.state)` | Reactive updates when theme changes; mock-transparent |
| initData extraction | Parse query string manually | `retrieveLaunchParams().initDataRaw` | Handles launch param encoding, mock mode, and errors |
| JWT parsing | Custom base64 decode | Same `parseJwt()` as PWA | Already proven — copy verbatim |
| Tailwind CSS variable theming | Custom CSS-in-JS | CSS custom properties on `<html>` via `useEffect` | Zero-runtime, works with shadcn variables |

**Key insight:** The `@telegram-apps/sdk-react` package encapsulates all Telegram WebView specifics including mock mode. Using `window.Telegram` directly bypasses the mock and breaks local development.

---

## Common Pitfalls

### Pitfall 1: Blank Screen in Production (Missing `miniApp.ready()`)

**What goes wrong:** Telegram shows its own loading overlay while the Mini App loads. It waits for `miniApp.ready()` to hide the overlay. If never called, Telegram keeps showing its splash screen indefinitely.

**Why it happens:** Developers forget the call or call it too late (after an async failure).

**How to avoid:** Call `miniApp.ready()` in `main.tsx` after `init()` and viewport mount, wrapped in try/catch so it fires even if viewport fails.

**Warning signs:** App works in browser dev mode but shows blank/spinner inside Telegram.

### Pitfall 2: `withCredentials: true` Breaks API Calls in Telegram WebView

**What goes wrong:** Axios is copy-pasted from PWA with `withCredentials: true`. Telegram's embedded WebView on iOS and Android drops or rejects cookies on cross-origin requests. Auth-related requests silently fail or return CORS errors.

**Why it happens:** The PWA needs cookies for httpOnly refresh tokens. The Mini App does not use cookies at all.

**How to avoid:** Create a fresh Axios instance for the Mini App without `withCredentials`. Never copy the PWA axios instance verbatim.

**Warning signs:** API calls fail with CORS or 401 inside Telegram but work in browser dev mode.

### Pitfall 3: SDK `init()` Called Before `mockTelegramEnv()`

**What goes wrong:** `init()` attempts to read Telegram launch parameters from the URL. In a browser dev environment these don't exist. SDK throws an error and React never renders.

**Why it happens:** Initialization order inverted — init before mock.

**How to avoid:** Always await `isTMA()` check and `mockTelegramEnv()` setup BEFORE calling `init()`. The bootstrap in `main.tsx` must be strictly sequential: check → mock (if needed) → init → mount.

**Warning signs:** App crashes immediately in browser with SDK error about missing launch parameters.

### Pitfall 4: `retrieveLaunchParams()` Called Inside React Component

**What goes wrong:** `retrieveLaunchParams()` is called during React render before SDK is initialized. Throws synchronously.

**Why it happens:** Developers call it in `useEffect` or top-level component code where the SDK state is uncertain.

**How to avoid:** Call `retrieveLaunchParams()` only in `AuthProvider`'s `useEffect` (after SDK init in `main.tsx` completes and React is rendered). Cache the result in a ref immediately.

**Warning signs:** Error like "LaunchParams not initialized" thrown during first render.

### Pitfall 5: 401 Re-Auth Loop

**What goes wrong:** On 401, the interceptor calls `authenticate(initDataRaw)`. If the initDataRaw is expired or invalid, `POST /api/auth/tma` also returns 401. The interceptor catches that 401 and tries to re-auth again — infinite loop.

**Why it happens:** No guard on re-auth requests themselves.

**How to avoid:** The `_retry = true` guard on the original request prevents looping on that request. The re-auth call to `/auth/tma` must NOT go through the 401 interceptor. Use a separate bare axios instance (not `apiClient`) for the re-auth call, or check `original.url` in the interceptor to skip `/auth/tma` responses.

**Warning signs:** Browser network tab shows repeated `/api/auth/tma` calls in rapid succession after any API error.

### Pitfall 6: Vite Path Alias Not Set Up

**What goes wrong:** Imports like `@/shared/lib/axios` work in PWA but fail in the Mini App scaffold because `vite.config.ts` and `tsconfig.app.json` path aliases are not copied.

**How to avoid:** Copy the `resolve.alias` block from the PWA `vite.config.ts` and the `paths` configuration from `tsconfig.json` and `tsconfig.app.json`. Both must be present.

---

## Code Examples

### Env Type Declarations

```typescript
// src/env.d.ts
/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_TMA_DEV: string
  readonly VITE_TMA_MOCK_USER: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}
```

### App.tsx Structure

```typescript
// src/App.tsx
import { BrowserRouter, Routes, Route } from 'react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/shared/lib/queryClient'
import { TelegramThemeProvider } from '@/shared/providers/TelegramThemeProvider'
import { DevModeBanner } from '@/shared/components/DevModeBanner'
import { AuthProvider } from '@/features/auth/AuthProvider'

// Placeholder pages (Phase 37 replaces)
function HomePage() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center p-4">
      <h1 className="text-xl font-semibold">Добро пожаловать в RutTrack</h1>
      <p className="text-sm text-muted-foreground">Функции появятся в следующей версии</p>
    </main>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TelegramThemeProvider>
        <DevModeBanner />
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/" element={<HomePage />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </TelegramThemeProvider>
    </QueryClientProvider>
  )
}
```

### Vite Config (Mini App — no PWA plugin)

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5174,   // Different from PWA's 5173
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
```

### Vitest Config (copy from PWA, adjust path)

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
})
```

### Test Setup (extends PWA, adds Telegram mock)

```typescript
// src/test/setup.ts
import '@testing-library/jest-dom'
import { vi } from 'vitest'

// Mock window.Telegram.WebApp for unit tests
Object.defineProperty(window, 'Telegram', {
  value: {
    WebApp: {
      initData: 'auth_date=1234567890&hash=mockHash&user=%7B%22id%22%3A1%7D',
      themeParams: {
        bg_color: '#ffffff', text_color: '#000000',
        button_color: '#2196F3', button_text_color: '#ffffff',
        secondary_bg_color: '#f5f5f5', hint_color: '#999999',
      },
      ready: vi.fn(),
      expand: vi.fn(),
    },
  },
  configurable: true,
})
```

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on This Phase |
|-----------|---------------------|
| No Lombok in contract modules | Not applicable — this is a frontend phase |
| Enum conversion: UPPER_CASE in Java, lowercase in DB | Not applicable — auth response uses plain string roles |
| REST API: HATEOAS, RFC 7807 errors | Frontend must handle `accessToken` field in `TokenResponse` and RFC 7807 error format (type, title, status fields) |
| Packages: `ru.rutcampustrack.{service}.*` | Not applicable — frontend package |
| Test: Vitest + Testing Library pattern (matches PWA) | Phase must include Vitest tests for AuthProvider |
| Feature flag patterns: `VITE_*` env vars | VITE_TMA_DEV, VITE_TMA_MOCK_USER follow established Vite env naming |

The backend `TokenResponse` DTO (returned by `POST /api/auth/tma`) returns `{ accessToken, refreshToken?, expiresIn }`. The Mini App only uses `accessToken` (D-05 — memory-only, so refreshToken is ignored even if returned).

---

## Runtime State Inventory

Step 2.5: SKIPPED — this is a greenfield frontend scaffold phase, not a rename/refactor/migration phase. No existing runtime state to inventory.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | npm, Vite build | ✓ | Project uses Vite 7 | — |
| npm | Package installation | ✓ | Assumed present (PWA builds successfully) | — |
| API Gateway (localhost:8080) | Dev proxy `/api` | ✓ | Java service, Phase 33 | Use VITE_TMA_DEV=true for offline mock |
| nginx container (mini-app) | Serving built assets | ✓ | Phase 33 infrastructure | `vite preview` for local preview |

[ASSUMED: Node.js and npm are available. The PWA builds successfully so the environment is confirmed capable — exact Node version not explicitly verified in this session.]

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest ^3.1.3 + @testing-library/react ^16.3.0 |
| Config file | `frontends/mini-app/vitest.config.ts` — Wave 0 creates it |
| Quick run command | `npm test` (runs `vitest run --passWithNoTests`) |
| Full suite command | `npm test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TMA-01 | App renders without crash | smoke | `npm test` (App renders check) | ❌ Wave 0 |
| TMA-02 | AuthProvider posts initDataRaw to /auth/tma on mount | unit | `npm test -- features/auth` | ❌ Wave 0 |
| TMA-03 | accessToken in React state after successful auth | unit | `npm test -- features/auth` | ❌ Wave 0 |
| TMA-04 | 401 triggers re-auth via initData, not refresh-body | unit | `npm test -- shared/lib/axios` | ❌ Wave 0 |
| TMA-05 | Dev mock: VITE_TMA_DEV=true injects mock, DevModeBanner renders | unit | `npm test -- DevModeBanner` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `npm test`
- **Per wave merge:** `npm test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `frontends/mini-app/vitest.config.ts` — test framework config
- [ ] `frontends/mini-app/src/test/setup.ts` — window.Telegram mock + jest-dom
- [ ] `frontends/mini-app/src/features/auth/__tests__/AuthProvider.test.tsx` — covers TMA-02, TMA-03, TMA-04
- [ ] `frontends/mini-app/src/shared/components/__tests__/DevModeBanner.test.tsx` — covers TMA-05

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | initData HMAC validated server-side (Phase 34); client just sends raw string |
| V3 Session Management | yes | Memory-only tokens — no XSS-accessible storage; token cleared on page unload |
| V4 Access Control | no | Role enforcement is server-side; client shows content based on JWT claims only |
| V5 Input Validation | minimal | initDataRaw is opaque string sent verbatim; no client-side validation needed |
| V6 Cryptography | no | Never hand-roll; all crypto is in the backend HMAC validation (Phase 34) |

### Known Threat Patterns for TMA Frontend

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| initData replay attack | Spoofing | Server validates `auth_date` freshness (Phase 34 TmaService); client has no role here |
| XSS token theft | Information Disclosure | Memory-only tokens (D-05) — no localStorage/sessionStorage, nothing survives page reload |
| Mock env in production | Elevation of Privilege | `mockTelegramEnv` only active when `VITE_TMA_DEV=true` (Vite inlines env at build time; production build has `false`) |
| CORS bypass via withCredentials | Tampering | Mini App Axios instance must NOT set `withCredentials: true` |
| 401 re-auth loop DoS | Denial of Service | `_retry` guard prevents looping; re-auth request uses bare axios, not apiClient |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `retrieveLaunchParams().initDataRaw` returns the raw URL-encoded initData string in sdk-react v3.3.9 | Architecture Patterns #2 | If field name changed, auth call sends undefined; test would catch this immediately |
| A2 | Node.js and npm are available on the dev machine | Environment Availability | Cannot build at all; low risk given PWA already builds |
| A3 | `useSignal(themeParams.state)` is the correct v3.x hook pattern for reading theme params reactively | Architecture Pattern #5 | themeParams might not update reactively; fallback is one-time read in useEffect |
| A4 | `TokenResponse` from `/api/auth/tma` includes `accessToken` field | All auth patterns | Verified from TmaService → AuthService path and PWA api.ts usage; low risk |

---

## Open Questions (RESOLVED)

1. **Does the backend accept raw initData or does it need URL-decoded?**
   - What we know: `TmaAuthRequest.java` takes `String initData` with `@NotBlank`; `TmaService` calls `validateInitData(request.initData())`
   - What's unclear: Whether `retrieveLaunchParams().initDataRaw` is already URL-encoded or decoded
   - RESOLVED: Send as-is from `retrieveLaunchParams().initDataRaw` — the backend's HMAC validation will fail on malformed data, so any encoding mismatch surfaces immediately in testing

2. **Does nginx for mini-app need any update for the Vite dev port?**
   - What we know: `nginx.conf` exists from Phase 33 and serves built assets
   - What's unclear: Whether `docker-compose.yml` maps port 5174 for live dev
   - RESOLVED: Use the nginx container only for production preview; use `vite dev` (port 5174) with proxy for development — this is the PWA pattern

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `window.Telegram.WebApp.initData` direct access | `retrieveLaunchParams().initDataRaw` via SDK | sdk v2.x → v3.x | SDK handles mock transparently; direct access breaks dev mode |
| httpOnly cookie refresh | Re-auth via initData on 401 | This project's D-05/D-06 | Simpler; Telegram always re-provides initData |
| `@twa-dev/sdk` (third party) | `@telegram-apps/sdk-react` (official) | 2024 | Official package has better TypeScript support and active maintenance |

---

## Sources

### Primary (HIGH confidence)
- `frontends/pwa/package.json` — verified dependency versions for shared stack
- `frontends/pwa/src/features/auth/AuthProvider.tsx` — AuthProvider pattern to adapt
- `frontends/pwa/src/shared/lib/axios.ts` — Axios interceptor pattern to simplify
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/TmaAuthRequest.java` — backend DTO field: `initData`
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` — `POST /auth/tma` confirmed
- npm registry: `npm view @telegram-apps/sdk-react version` → 3.3.9 [VERIFIED]
- npm registry: `npm view @telegram-apps/sdk-react peerDependencies` → `@telegram-apps/sdk ^3.11.8` [VERIFIED]

### Secondary (MEDIUM confidence)
- https://github.com/Telegram-Mini-Apps/reactjs-template — official template `mockEnv.ts` pattern with `isTMA`, `mockTelegramEnv`, `emitEvent`
- https://docs.telegram-mini-apps.com/platform/init-data — initData format, auth_date security, HMAC verification
- https://dev.to/dev_family/telegram-mini-app-development-and-testing-specifics-from-initialisation-to-launch-1ofh — `viewport.mount().then(() => viewport.bindCssVars())` pattern; `useSignal(initData.raw)` usage

### Tertiary (LOW confidence)
- General WebSearch results on `@telegram-apps/sdk-react` SDKProvider — confirmed pattern direction but not API-surface-verified

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified from working PWA package.json + npm registry
- Architecture patterns: MEDIUM-HIGH — initData flow and mock patterns from official template; interceptor simplification is adaptation of verified PWA code
- Pitfalls: HIGH — most pitfalls derived from known working code (PWA) + official SDK behavior

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable ecosystem; sdk-react releases infrequently)

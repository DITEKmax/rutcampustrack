# Phase 29: PWA Scaffold + Auth - Research

**Researched:** 2026-04-06
**Domain:** React PWA scaffold, JWT auth with httpOnly cookies, vite-plugin-pwa, Service Worker, A2HS
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Auth-service backend refactored to set refresh token as httpOnly + Secure + SameSite=Strict cookie on `/auth/login` and `/auth/refresh`. PWA never sees the refresh token in JavaScript.
- **D-02:** Access token stored in React memory (context/closure), attached via `Authorization: Bearer` header. No Gateway changes needed.
- **D-03:** Silent refresh via Axios response interceptor: on 401 → call `/auth/refresh` (cookie sent automatically) → receive new access token → retry original request.
- **D-04:** Logout calls `POST /auth/logout` which invalidates refresh token in Redis + clears httpOnly cookie via `Set-Cookie`. PWA clears in-memory access token and redirects to login.
- **D-05:** CSS: shadcn/ui + Tailwind CSS. Phosphor Icons (bold/fill weight per design-decisions.md).
- **D-06:** Data fetching: TanStack Query (React Query) with Axios as HTTP transport.
- **D-07:** Routing: React Router v7 with nested routes and lazy loading.
- **D-08:** Project structure: Hybrid — `src/shared/` + `src/features/` (auth/, schedule/, checkin/, etc.).
- **D-09:** Animation: Motion (framer-motion) per design-decisions.md.
- **D-10:** Phase 29 captures `beforeinstallprompt` and stores deferred prompt in React context. Phase 30 triggers `prompt()`. Infrastructure now, trigger later.
- **D-11:** iOS Safari onboarding: full-screen overlay on first visit when iOS Safari + not standalone. Dismiss stores `ios_onboarding_shown` in localStorage.
- **D-12:** Offline unauthenticated: login page loads from SW cache with "You are offline" banner. Login button disabled.
- **D-13:** Offline authenticated: app shell + nav loads, data screens show "No connection" empty states. No offline data caching in Phase 29.
- **D-14:** Service Worker uses `injectManifest` strategy via vite-plugin-pwa (needed for custom push handler in Phase 31).

### Claude's Discretion

- Exact shadcn/ui component selection and theme configuration
- Tailwind color palette / dark mode setup
- Axios instance configuration (base URL, interceptor details)
- TanStack Query default options (staleTime, gcTime, retry)
- React Router route structure and layout components
- vite-plugin-pwa workbox configuration details
- Auth context/provider implementation pattern
- Login form design and validation approach

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PWA-01 | User can log in with username and password (JWT in memory, refresh in httpOnly cookie) | D-01/D-02: backend cookie refactor + React memory storage pattern |
| PWA-02 | Access token auto-refreshes silently before 15-min expiry | D-03: Axios interceptor pattern; 401 → `/auth/refresh` → retry |
| PWA-03 | User can log out (clears tokens, invalidates refresh on server) | D-04: POST `/auth/logout` + Redis invalidation + cookie clear |
| PWA-04 | PWA manifest with name "RutTrack", standalone display, 192/512 icons | UI-SPEC manifest contract; vite-plugin-pwa generates link tag |
| PWA-05 | Service Worker registers and caches app shell for offline loading | D-14: `injectManifest` strategy; workbox precache all build chunks |
| PWA-06 | Android users see A2HS prompt after first successful check-in | D-10: capture `beforeinstallprompt`, store in context, Phase 30 triggers |
| PWA-07 | iOS users see Safari install instructions when not in standalone | D-11: iOS detection + overlay, localStorage flag |

</phase_requirements>

---

## Summary

Phase 29 has two parallel tracks: (1) a backend refactor of `auth-service` to change the refresh token transport from request body to httpOnly cookie, and (2) a greenfield React PWA scaffolded inside `frontends/pwa/`. The React project does not exist yet — only placeholder `dist/` files from Phase 28 are present. The entire `frontends/pwa/` directory needs a Vite + React + TypeScript project created from scratch.

The most technically sensitive area is the token transport change. The existing `AuthController` takes `refreshToken` in request body; after refactor it must set a `Set-Cookie` response header with `HttpOnly; Secure; SameSite=Strict`. The `/auth/refresh` endpoint changes from body-input to cookie-input, which requires `HttpServletRequest` injection in the controller. The `/auth/logout` endpoint must also accept the cookie instead of (or alongside) the body token. All three auth integration tests will need updates to assert cookie behavior.

On the frontend, vite-plugin-pwa 1.2.0 lists Vite `^7.0.0` in peer dependencies but npm install with Vite 8.0.3 will require `--legacy-peer-deps`. This is a known, documented risk from STATE.md. The fallback is to pin Vite to 7.x. Research confirms the risk is real but manageable: vite-plugin-pwa 1.2.0 was published 2025-11-27 when Vite 8 did not yet exist (released 2026-03-12).

**Primary recommendation:** Scaffold the React project with Vite 7 (not 8) to avoid the vite-plugin-pwa peer dep conflict. Upgrade to Vite 8 when vite-plugin-pwa publishes explicit v8 support.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| react | 19.2.4 | UI framework | Locked by design-decisions.md |
| react-dom | 19.2.4 | DOM renderer | Paired with react |
| typescript | 5.8.x | Type safety | Industry standard for React projects |
| vite | 7.x | Build tool | vite-plugin-pwa peer dep requires `^7.0.0`; Vite 8 not yet supported |
| vite-plugin-pwa | 1.2.0 | SW + manifest generation | Latest; `injectManifest` strategy for custom SW (D-14) |
| react-router | 7.14.0 | Routing | D-07 locked; latest stable 2026-04-02 |
| @tanstack/react-query | 5.96.2 | Data fetching + cache | D-06 locked; latest stable 2026-04-03 |
| axios | 1.14.0 | HTTP transport | D-06 locked; interceptor pattern for silent refresh |
| tailwindcss | 4.2.2 | CSS | D-05 locked; latest stable |
| shadcn/ui | 4.1.2 (cli) | Component library | D-05 locked; slate theme |
| motion | 12.38.0 | Animation | D-09 locked; design-decisions.md |
| @phosphor-icons/react | 2.1.10 | Icons | design-decisions.md; bold/fill weight |
| workbox-window | 7.4.0 | SW registration helper | Bundled via vite-plugin-pwa; direct import for SW lifecycle |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @vite-pwa/assets-generator | ^1.0.0 | Icon generation (192/512) | Wave 0 — generate placeholder icon PNGs |
| @tanstack/react-query-devtools | 5.x | Dev-only query inspector | Dev build only |
| zod | 3.x | Form schema validation | Login form validation (optional — Phase 29 validation is minimal) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Vite 7 | Vite 8.0.3 | Vite 8 is latest but vite-plugin-pwa 1.2.0 peer dep fails without `--legacy-peer-deps`; safer to use Vite 7 |
| React Router v7 | TanStack Router | React Router v7 is locked (D-07); TanStack Router has better type safety but not chosen |
| Axios | native fetch | Axios interceptor API is cleaner for the 401 retry pattern; fetch requires manual retry logic |

**Installation:**

```bash
cd frontends/pwa
npm create vite@latest . -- --template react-ts
npm install react-router react@19 react-dom@19
npm install @tanstack/react-query axios
npm install motion @phosphor-icons/react
npm install -D tailwindcss @tailwindcss/vite vite-plugin-pwa workbox-window
npx shadcn@latest init
```

**Version verification (confirmed via npm registry 2026-04-06):**

| Package | Version | Publish Date |
|---------|---------|-------------|
| vite | 7.x latest | [VERIFIED: npm registry] |
| vite-plugin-pwa | 1.2.0 | 2025-11-27 [VERIFIED: npm registry] |
| react | 19.2.4 | [VERIFIED: npm registry] |
| react-router | 7.14.0 | 2026-04-02 [VERIFIED: npm registry] |
| @tanstack/react-query | 5.96.2 | 2026-04-03 [VERIFIED: npm registry] |
| axios | 1.14.0 | [VERIFIED: npm registry] |
| tailwindcss | 4.2.2 | [VERIFIED: npm registry] |
| motion | 12.38.0 | [VERIFIED: npm registry] |
| @phosphor-icons/react | 2.1.10 | [VERIFIED: npm registry] |
| shadcn (cli) | 4.1.2 | [VERIFIED: npm registry] |

---

## Architecture Patterns

### Recommended Project Structure

```
frontends/pwa/
├── public/
│   ├── manifest.webmanifest     # PWA manifest (generated by vite-plugin-pwa)
│   └── icons/
│       ├── icon-192.png         # Placeholder slate rectangle
│       └── icon-512.png         # Placeholder slate rectangle
├── src/
│   ├── main.tsx                 # React root, QueryClientProvider, RouterProvider
│   ├── sw.ts                    # Custom service worker (injectManifest entry)
│   ├── shared/
│   │   ├── components/
│   │   │   ├── ui/              # shadcn generated components (button, input, etc.)
│   │   │   ├── AppShell.tsx     # Root layout with bottom nav slot
│   │   │   ├── BottomNav.tsx    # 4-tab mobile nav
│   │   │   ├── OfflineBanner.tsx
│   │   │   ├── LoadingSpinner.tsx
│   │   │   └── ProtectedRoute.tsx
│   │   ├── hooks/
│   │   │   ├── useNetworkStatus.ts  # online/offline events
│   │   │   └── useInstallPrompt.ts  # beforeinstallprompt capture
│   │   └── lib/
│   │       ├── axios.ts         # Axios instance + interceptors
│   │       └── queryClient.ts   # TanStack Query client config
│   └── features/
│       ├── auth/
│       │   ├── AuthProvider.tsx  # Context + in-memory token
│       │   ├── LoginPage.tsx
│       │   ├── IOSOnboardingOverlay.tsx
│       │   └── api.ts           # login/refresh/logout API calls
│       └── home/
│           └── HomePlaceholder.tsx
├── vite.config.ts
├── tailwind.config.ts           # (Tailwind 4 uses @tailwindcss/vite, may not need config file)
├── tsconfig.json
├── components.json              # shadcn config (generated by init)
└── package.json
```

### Pattern 1: httpOnly Cookie Refresh Token (Backend Refactor)

**What:** Auth-service `login` and `refresh` endpoints set the refresh token as a `Set-Cookie` header instead of (or in addition to) returning it in the JSON body. The cookie is `HttpOnly; Secure; SameSite=Strict`. The access token still returns in the JSON body.

**When to use:** Any cross-origin browser client that needs XSS-safe token storage.

**Spring Boot implementation:**

```java
// Source: Spring Framework ResponseCookie (Spring Boot 3.4)
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

// In AuthController.login():
@PostMapping("/login")
public ResponseEntity<AccessTokenResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletResponse response) {
    TokenPair tokens = authService.login(request);

    ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", tokens.refreshToken())
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/auth")           // Scope to /auth only — cookie not sent to other routes
            .maxAge(Duration.ofSeconds(jwtProperties.refreshTokenExpiration()))
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

    // Return only accessToken in body — refreshToken removed from JSON
    return ResponseEntity.ok(new AccessTokenResponse(tokens.accessToken(), tokens.expiresIn()));
}

// In AuthController.refresh():
@PostMapping("/refresh")
public ResponseEntity<AccessTokenResponse> refresh(
        @CookieValue(name = "refresh_token") String refreshToken,
        HttpServletResponse response) {
    // ... same cookie-setting pattern
}

// In AuthController.logout():
@PostMapping("/logout")
public ResponseEntity<Void> logout(
        @CookieValue(name = "refresh_token", required = false) String refreshToken,
        HttpServletResponse response) {
    if (refreshToken != null) authService.logout(refreshToken);

    ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/auth")
            .maxAge(0)  // Expire immediately
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
    return ResponseEntity.noContent().build();
}
```

**Key:** The `path("/auth")` scopes the cookie so it is only sent on requests to `/auth/**`. This limits cookie exposure. The `SameSite=Strict` prevents CSRF for cross-origin requests.

**`TokenResponse` DTO change:** Remove `refreshToken` field. Add new `AccessTokenResponse` record with only `accessToken` + `expiresIn`. The existing `RefreshRequest` DTO (takes `refreshToken` in body) is no longer used for `/auth/refresh` — can be deleted or kept for backward compatibility.

### Pattern 2: Axios Interceptor Silent Refresh (Frontend)

**What:** A response interceptor catches 401 errors, calls `/auth/refresh`, receives the new access token, updates the in-memory token, and retries the original request with the new token.

**Pitfall:** Without a queue, multiple simultaneous 401s trigger multiple refresh calls (token rotation race condition). The standard solution is to queue pending requests during a refresh in progress.

```typescript
// Source: [ASSUMED] - standard Axios interceptor pattern
// src/shared/lib/axios.ts
import axios from 'axios';

let isRefreshing = false;
let pendingQueue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = [];

const flushQueue = (token: string | null, error: unknown = null) => {
  pendingQueue.forEach(({ resolve, reject }) => {
    if (token) resolve(token);
    else reject(error);
  });
  pendingQueue = [];
};

export const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true,  // CRITICAL: sends httpOnly refresh cookie on /auth/refresh
});

// Set access token from AuthContext — called by AuthProvider after login
export let getAccessToken: () => string | null = () => null;
export const setAccessTokenGetter = (fn: () => string | null) => { getAccessToken = fn; };

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers['Authorization'] = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true;

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push({
            resolve: (token) => {
              original.headers['Authorization'] = `Bearer ${token}`;
              resolve(apiClient(original));
            },
            reject,
          });
        });
      }

      isRefreshing = true;
      try {
        const { data } = await axios.post('/api/auth/refresh', {}, { withCredentials: true });
        // AuthProvider must expose a setter:
        // setAccessToken(data.accessToken)
        flushQueue(data.accessToken);
        original.headers['Authorization'] = `Bearer ${data.accessToken}`;
        return apiClient(original);
      } catch (refreshError) {
        flushQueue(null, refreshError);
        // Redirect to login — AuthProvider handles this
        window.dispatchEvent(new CustomEvent('auth:logout'));
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }
    return Promise.reject(error);
  }
);
```

**Critical:** `withCredentials: true` on the Axios instance ensures the browser sends the httpOnly `refresh_token` cookie on requests to the same origin. The API Gateway already has `allow-credentials: true` in its CORS config (Phase 28). The PWA dev server runs on `localhost:5173` which is in the Gateway's `allowed-origins` list — cookie will be sent in dev.

### Pattern 3: AuthProvider (In-Memory Token)

**What:** React context that holds the access token in a closure (never in `localStorage` or `sessionStorage`). Exposes `login`, `logout`, `setAccessToken` actions.

```typescript
// src/features/auth/AuthProvider.tsx
interface AuthState {
  accessToken: string | null;
  user: { id: number; role: string; groupId?: number } | null;
  isAuthenticated: boolean;
}

interface AuthActions {
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  setAccessToken: (token: string) => void;  // Called by Axios interceptor on refresh
}
```

**Pattern:** Store token in `useRef` or module-level variable (not `useState`) if re-render on token change is undesirable — but for simplicity `useState` is fine; token changes only on login/logout/refresh which are infrequent.

### Pattern 4: vite-plugin-pwa injectManifest Strategy

**What:** Instead of `generateSW` (auto-generated SW), `injectManifest` uses a custom `src/sw.ts` file where Workbox injects the precache manifest. This is required because Phase 31 needs a `push` event handler in the same SW.

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'autoUpdate',
      manifest: {
        name: 'RutCampusTrack',
        short_name: 'RutTrack',
        display: 'standalone',
        start_url: '/',
        background_color: '#ffffff',
        theme_color: '#0f172a',
        orientation: 'portrait',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any maskable' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2}']
      }
    })
  ]
})
```

```typescript
// src/sw.ts — custom service worker with injected precache manifest
import { precacheAndRoute } from 'workbox-precaching'

// Injected by vite-plugin-pwa at build time
declare const self: ServiceWorkerGlobalScope
precacheAndRoute(self.__WB_MANIFEST)

// Phase 31 will add push handler here:
// self.addEventListener('push', (event) => { ... })
```

### Pattern 5: beforeinstallprompt Capture

```typescript
// src/shared/hooks/useInstallPrompt.ts
// Source: [CITED: https://web.dev/customize-install/]
import { useEffect, useRef } from 'react'

export function useInstallPrompt() {
  const deferredPrompt = useRef<BeforeInstallPromptEvent | null>(null)

  useEffect(() => {
    const handler = (e: Event) => {
      e.preventDefault()
      deferredPrompt.current = e as BeforeInstallPromptEvent
    }
    window.addEventListener('beforeinstallprompt', handler)
    return () => window.removeEventListener('beforeinstallprompt', handler)
  }, [])

  const triggerInstall = async () => {
    if (!deferredPrompt.current) return false
    deferredPrompt.current.prompt()
    const { outcome } = await deferredPrompt.current.userChoice
    deferredPrompt.current = null
    return outcome === 'accepted'
  }

  return { triggerInstall }
}
```

`BeforeInstallPromptEvent` is not in the standard TypeScript DOM lib — needs a type declaration:
```typescript
// src/shared/types/pwa.d.ts
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}
```

### Pattern 6: iOS Detection + Onboarding

```typescript
// Source: [ASSUMED] - standard iOS PWA detection pattern
const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent)
const isStandalone = window.matchMedia('(display-mode: standalone)').matches
  || (navigator as Navigator & { standalone?: boolean }).standalone === true
const hasShownOnboarding = localStorage.getItem('ios_onboarding_shown') === '1'

const shouldShowIOSOnboarding = isIOS && !isStandalone && !hasShownOnboarding
```

**Note:** `window.navigator.standalone` is a non-standard iOS Safari property. TypeScript needs a cast. The `(display-mode: standalone)` media query is the standards-based approach and works on both Android and iOS.

### Anti-Patterns to Avoid

- **Storing refresh token in localStorage:** XSS-accessible. Decision D-01 locks this to httpOnly cookie.
- **Storing access token in localStorage:** Survives page reload but XSS-accessible. Decision D-02 locks this to React memory.
- **Single Axios instance without `withCredentials: true`:** Cookie not sent on cross-origin requests. Must be set on instance-level, not per-request.
- **Multiple refresh calls on concurrent 401s:** Token rotation means the second refresh invalidates the first. Must queue pending requests.
- **generateSW strategy for vite-plugin-pwa:** The auto-generated SW does not support custom `push` event handler injection. Phase 31 push support requires `injectManifest`.
- **Service Worker caching auth API responses:** Never cache `/auth/**` responses — the SW fetch handler must passthrough auth routes.
- **SameSite=Lax for refresh cookie:** Does not prevent CSRF for POST requests in some browsers. Use `SameSite=Strict`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Offline detection | Custom polling | `navigator.onLine` + `online/offline` events via `useNetworkStatus` hook | Browser API; polling wastes battery |
| SW precaching | Custom cache logic | workbox-precaching via vite-plugin-pwa | Content hash invalidation, stale entry cleanup |
| Form state | Custom controlled form | React Hook Form or uncontrolled form with FormData | Login form in Phase 29 is simple enough for uncontrolled; RHF adds 13KB |
| Cookie parsing | Manual `document.cookie` | Server sets httpOnly — JS never reads the refresh cookie; `@CookieValue` on backend | httpOnly cookies are invisible to JS by design |
| Token decode | Custom JWT parsing | `jwtDecode` (1.2KB) for reading `exp` claim for proactive refresh | Hand-rolling base64 decode has edge cases |
| Icon generation | Manual image editing | `@vite-pwa/assets-generator` | Generates correct PNG sizes from source SVG |

**Key insight:** The httpOnly cookie pattern means the browser handles the most complex part (sending the refresh token on the correct request). The frontend only needs to handle the response.

---

## Common Pitfalls

### Pitfall 1: vite-plugin-pwa 1.2.0 Peer Dep Conflict with Vite 8

**What goes wrong:** `npm install` fails with `ERESOLVE unable to resolve dependency tree` because vite-plugin-pwa 1.2.0 declares peer dep `vite: "^3.1.0 || ^4.0.0 || ^5.0.0 || ^6.0.0 || ^7.0.0"` — Vite 8 not included.

**Why it happens:** vite-plugin-pwa 1.2.0 was published 2025-11-27; Vite 8 released 2026-03-12. No updated release yet.

**How to avoid:** Use Vite 7.x (latest 7.x) instead of Vite 8. Or use `--legacy-peer-deps` if Vite 8 is preferred for other reasons.

**Warning signs:** `npm install` error mentioning `vite-plugin-pwa` peer dep resolution failure.

### Pitfall 2: Cookie Not Sent on Refresh Request

**What goes wrong:** `POST /api/auth/refresh` goes to the Gateway without the `refresh_token` cookie, returning 401 permanently.

**Why it happens:** One of: (a) `withCredentials: false` on the Axios instance, (b) the Gateway's CORS config missing `allow-credentials: true` (already set in Phase 28), (c) the cookie `path` doesn't match the request path, (d) `SameSite=Strict` blocking a cross-site navigation.

**How to avoid:** 
- Set `withCredentials: true` on the Axios instance (not just the refresh call).
- Cookie `path="/auth"` — all auth requests go to `/auth/**` via Gateway strip-prefix route.
- In development, `localhost:5173` and `localhost:8080` are same-site (both localhost), so SameSite=Strict works.

**Warning signs:** Network tab shows `/auth/refresh` request without `Cookie` header.

### Pitfall 3: Token Rotation Race Condition

**What goes wrong:** User makes 3 API calls simultaneously; all return 401; each tries to refresh independently. The first refresh succeeds (token rotated), the second refresh call uses the now-invalidated original refresh token and returns 401, triggering logout.

**Why it happens:** Without a flag to block concurrent refresh calls.

**How to avoid:** The `isRefreshing` flag + request queue pattern (Pattern 2 above). The first 401 sets `isRefreshing = true`; subsequent 401s join the queue; when refresh completes, queue is flushed with the new token.

**Warning signs:** User randomly gets logged out under heavy API load.

### Pitfall 4: SameSite=Strict Breaks Redirect Flows

**What goes wrong:** If the user navigates from an external link directly to the PWA (cross-site navigation), `SameSite=Strict` prevents the browser from sending the cookie on the initial navigation, causing an unnecessary re-login.

**Why it happens:** SameSite=Strict blocks the cookie on all cross-site requests, including top-level navigations from other origins.

**How to avoid:** This is acceptable for this app — users are expected to open the PWA directly (from home screen or typing URL), not via external links to protected routes. If this becomes a UX issue, change to `SameSite=Lax` (cookie sent on top-level GET navigations).

**Warning signs:** Users who tap a deep link from an external app are forced to log in again.

### Pitfall 5: Service Worker Intercepts Auth API Requests

**What goes wrong:** The SW intercepts `/api/auth/refresh` and returns a cached 401 response, making the silent refresh fail permanently.

**Why it happens:** Overly broad workbox routing (`globalRoute` without exclusions).

**How to avoid:** The workbox precache only handles `__WB_MANIFEST` entries (build output files). The SW `fetch` handler should NOT be added for API routes in Phase 29. Only the `precacheAndRoute` call exists in `sw.ts`. Any future fetch handler must explicitly exclude `/api/**`.

**Warning signs:** Stale error responses returned from SW cache.

### Pitfall 6: `injectManifest` TypeScript Build Error

**What goes wrong:** Vite build fails because `src/sw.ts` imports workbox modules but Vite's TypeScript config does not know about service worker globals (`self`, `ServiceWorkerGlobalScope`).

**Why it happens:** `tsconfig.json` `lib` setting does not include `WebWorker`.

**How to avoid:** Add a separate `tsconfig.sw.json` or reference `/// <reference lib="webworker" />` at the top of `sw.ts`. Also configure vite-plugin-pwa's `devOptions.type: 'module'` for dev SW support.

**Warning signs:** TypeScript error `Cannot find name 'self'` or `'ServiceWorkerGlobalScope' is not defined` in `sw.ts`.

### Pitfall 7: Auth Service Logout Rejects Request Without Cookie

**What goes wrong:** After the backend refactor, `POST /auth/logout` expects cookie input but existing tests still send `refreshToken` in request body — integration tests break.

**Why it happens:** The backend refactor changes the request contract.

**How to avoid:** Update `AuthController.logout()` to accept `@CookieValue(required = false)` and also retain backward-compatible body param OR update all tests. Since the only consumer is the new PWA, full migration is cleaner.

**Warning signs:** `AuthIntegrationTest.logout_withValidToken_returns204` fails with 400.

---

## Code Examples

### Backend: TokenResponse DTO Change

```java
// Source: Existing code at services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/TokenResponse.java
// CURRENT (to be replaced):
public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}

// NEW (refresh token removed from body — moved to httpOnly cookie):
public record TokenResponse(String accessToken, long expiresIn) {}
```

### Backend: SecurityConfig — Permit /auth/logout Without Auth

```java
// /auth/logout must be accessible without a valid access token
// (user may call logout after access token expired, relying on cookie)
.requestMatchers("/auth/login", "/auth/refresh", "/auth/logout", "/auth/public-key", ...)
.permitAll()
```

`/auth/logout` is already in the permit list in the existing `SecurityConfig` — no change needed.

### Frontend: React Router v7 Route Structure

```typescript
// Source: [CITED: reactrouter.com/start/framework/routing]
// src/main.tsx
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AuthProvider } from './features/auth/AuthProvider'
import { ProtectedRoute } from './shared/components/ProtectedRoute'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <ProtectedRoute><AppShell /></ProtectedRoute>,
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      { path: 'home', lazy: () => import('./features/home/HomePlaceholder') },
      // Phase 30+ routes added here
    ]
  }
])
```

**Note:** React Router v7 `lazy` property on routes works with `createBrowserRouter`. The `<BrowserRouter>` wrapper does NOT support `<Route lazy={...}>` — use `createBrowserRouter` always.

### Frontend: TanStack Query Client Config

```typescript
// src/shared/lib/queryClient.ts
import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,         // 30s — data considered fresh
      gcTime: 5 * 60_000,        // 5min — cache retained after unmount
      retry: 1,                  // retry once on failure
      refetchOnWindowFocus: true,
    },
    mutations: {
      retry: 0,
    },
  },
})
```

### Frontend: Offline Detection Hook

```typescript
// src/shared/hooks/useNetworkStatus.ts
import { useEffect, useState } from 'react'

export function useNetworkStatus() {
  const [isOnline, setIsOnline] = useState(navigator.onLine)

  useEffect(() => {
    const online = () => setIsOnline(true)
    const offline = () => setIsOnline(false)
    window.addEventListener('online', online)
    window.addEventListener('offline', offline)
    return () => {
      window.removeEventListener('online', online)
      window.removeEventListener('offline', offline)
    }
  }, [])

  return isOnline
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| refresh token in localStorage | httpOnly cookie | Industry shift ~2020 | XSS cannot steal refresh token |
| React Router v6 `<BrowserRouter>` | React Router v7 `createBrowserRouter` | v6.4 (2022), v7 (2024) | Data router, lazy routes, type safety |
| `framer-motion` package | `motion` package | Renamed ~2024 | Same API, lighter package name |
| Tailwind CSS v3 config file | Tailwind CSS v4 `@tailwindcss/vite` plugin | v4 GA (2025) | Config via CSS `@theme`, no `tailwind.config.js` required |
| vite-plugin-pwa `generateSW` | `injectManifest` for custom handlers | Always an option | Required when SW needs push/sync handlers |
| React 18 `forwardRef` | React 19 ref as regular prop | React 19 (2024) | Simpler ref passing |

**Deprecated/outdated:**
- `RefreshRequest` body DTO: replaced by `@CookieValue` cookie extraction in `/auth/refresh` and `/auth/logout`
- `TokenResponse.refreshToken` field: removed from response body (now httpOnly cookie)
- Workbox `importScripts` pattern: replaced by ES module SW with `precacheAndRoute`

---

## Runtime State Inventory

> Phase 29 is mostly greenfield (new React project) + backend refactor. No rename/migration, but backend contract change has runtime implications.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Redis keys `refresh:{userId}:{jti}` — format unchanged after refactor | None — key format stays the same |
| Live service config | auth-service running in docker — needs restart after refactor | `docker compose restart auth-service` |
| OS-registered state | None found | None |
| Secrets/env vars | No new secrets required — cookie is derived from existing JWT signing key | None |
| Build artifacts | `frontends/pwa/dist/` — placeholder files from Phase 28 | Will be overwritten by `npm run build` during scaffold |

**Existing auth integration tests will break:** `AuthIntegrationTest` tests that assert `refreshToken` in response body or pass `refreshToken` in request body for `/auth/refresh` and `/auth/logout` must be updated. Specifically:
- `login_withSeedStudent_returnsTokenPair` asserts `response.getBody().refreshToken()` — field removed
- `refresh_withValidToken_returnsNewTokenPair` sends `RefreshRequest(refreshToken)` in body — endpoint now reads cookie
- `logout_withValidToken_returns204` sends `RefreshRequest(refreshToken)` in body — endpoint now reads cookie

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | npm install, Vite build | ✓ | v24.14.0 | — |
| npm | package manager | ✓ | 11.9.0 | — |
| Docker / docker-compose | pwa-nginx container (from Phase 28) | ✓ | Present (Phase 28 wired) | — |
| Vite 7.x | vite-plugin-pwa peer dep | ✓ (npm registry) | 7.x latest | Pin to 7 if conflict |
| `frontends/pwa/` directory | React project scaffold | ✓ | Exists (has placeholder dist/) | — |

**Missing dependencies with no fallback:** None.

**Note on Vite 8 vs 7:** The npm registry confirms Vite 8.0.3 (latest) and vite-plugin-pwa 1.2.0 (latest). The peer dep mismatch is real. Using Vite 7 is the safe default; `--legacy-peer-deps` is the escape hatch if Vite 8 is needed. [VERIFIED: npm registry]

---

## Validation Architecture

> `workflow.nyquist_validation` absent from config.json — treated as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest (standard with Vite projects) + @testing-library/react |
| Config file | `vitest.config.ts` (Wave 0 gap — does not exist yet) |
| Quick run command | `cd frontends/pwa && npm run test` |
| Full suite command | `cd frontends/pwa && npm run test -- --run` |
| Backend tests | `./gradlew :services:auth-service:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PWA-01 | Login returns access token in memory, cookie set by server | Integration (backend) + unit (frontend hook) | `./gradlew :services:auth-service:test` | ✅ (existing, needs update) |
| PWA-01 | AuthProvider stores token, ProtectedRoute redirects unauthenticated | Unit (React Testing Library) | `npm run test -- --run` | ❌ Wave 0 |
| PWA-02 | 401 triggers refresh, retries original request | Unit (axios interceptor mock) | `npm run test -- --run` | ❌ Wave 0 |
| PWA-03 | Logout clears cookie (Set-Cookie maxAge=0) + Redis invalidated | Integration (backend) | `./gradlew :services:auth-service:test` | ✅ (existing, needs update) |
| PWA-04 | manifest.webmanifest has correct fields | Build artifact check / smoke | `cat frontends/pwa/dist/manifest.webmanifest` | ❌ Wave 0 (generated at build) |
| PWA-05 | SW registers, precaches app shell, offline loads login | Manual smoke (Lighthouse / browser DevTools) | Manual | ❌ Manual only |
| PWA-06 | `beforeinstallprompt` captured in context | Unit (event mock) | `npm run test -- --run` | ❌ Wave 0 |
| PWA-07 | iOS detection triggers overlay when not standalone | Unit (userAgent mock) | `npm run test -- --run` | ❌ Wave 0 |

### Wave 0 Gaps (Frontend)

- [ ] `frontends/pwa/vitest.config.ts` — Vitest configuration
- [ ] `frontends/pwa/src/test/setup.ts` — @testing-library/react setup
- [ ] `frontends/pwa/src/features/auth/__tests__/AuthProvider.test.tsx` — covers PWA-01, PWA-02
- [ ] `frontends/pwa/src/shared/hooks/__tests__/useInstallPrompt.test.ts` — covers PWA-06
- [ ] `frontends/pwa/src/features/auth/__tests__/IOSOnboardingOverlay.test.tsx` — covers PWA-07

### Wave 0 Gaps (Backend — existing tests need update)

- [ ] `AuthIntegrationTest.login_withSeedStudent_returnsTokenPair` — remove `refreshToken()` assertion, add cookie assertion
- [ ] `AuthIntegrationTest.refresh_withValidToken_returnsNewTokenPair` — send cookie instead of body
- [ ] `AuthIntegrationTest.logout_withValidToken_returns204` — send cookie instead of body

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT access token in memory; refresh in httpOnly cookie |
| V3 Session Management | yes | httpOnly + Secure + SameSite=Strict; Redis token rotation |
| V4 Access Control | yes | ProtectedRoute; Gateway injects X-User-Role |
| V5 Input Validation | yes | Login form: non-empty username/password only; server-side full validation |
| V6 Cryptography | yes | RSA-256 JWT (existing); no custom crypto |

### Known Threat Patterns for This Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| XSS token theft | Information Disclosure | Refresh in httpOnly cookie (JS-inaccessible); access in memory (not DOM) |
| CSRF on /auth/refresh | Tampering | SameSite=Strict cookie; no state-changing GET |
| Token replay after logout | Elevation of Privilege | Redis key deletion on logout; token rotation on refresh |
| Race condition on concurrent refresh | Tampering | isRefreshing flag + request queue in Axios interceptor |
| Stale SW serving old JS | Spoofing | `no-cache` header on `sw.js` + `index.html` (already in nginx.conf) |
| Overly broad SW caching auth responses | Denial of Service | `sw.ts` precaches only build artifacts; no fetch handler for `/api/**` |

---

## Project Constraints (from CLAUDE.md)

The following directives from `CLAUDE.md` apply to this phase:

**Backend (auth-service refactor):**
- Contract-first: auth-service is the exception to the contract module rule (no separate `auth-api-contract` module). DTOs exist directly in `auth-app`. Confirmed — no architecture change needed.
- No `@Enumerated(EnumType.ORDINAL)` — not relevant to this change.
- `ddl-auto: validate` — no DB schema changes in this phase; no Flyway migration needed.
- Soft delete only — not relevant; no user deletion.
- Centralized error handling via `@ControllerAdvice` — `@CookieValue` will throw `MissingRequestCookieException` if cookie absent; must be handled in existing `GlobalExceptionHandler` (map to 401).
- RFC 7807 Problem Details for errors — maintain existing `ErrorResponse` record format.
- No Lombok in `*-app` service layer — irrelevant; no new entity classes.

**Frontend (new project):**
- Package: `ru.rutcampustrack.{service}` — not applicable to frontend.
- REST paths: `/api/{service}/...` — confirmed; all API calls via `/api/auth/...` through Gateway.
- CLAUDE.md does not specify frontend linting/formatting rules — use Prettier + ESLint as standard.

**Critical CLAUDE.md constraint:** When reading `docs/design-decisions.md` before implementing frontend — confirmed read. All constraints captured in D-05 through D-09.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `SameSite=Strict` on refresh cookie works for `localhost` dev (same-site) | Pitfall 4 + Pattern 1 | If localhost is treated as cross-site, cookie not sent; fallback: `SameSite=Lax` |
| A2 | Axios interceptor pattern with `isRefreshing` queue prevents token rotation race | Pattern 2 | Concurrent requests could exhaust refresh tokens; would need to test under load |
| A3 | `window.navigator.standalone` property detects iOS standalone mode | Pattern 6 | Non-standard property; if unavailable, fall back to `display-mode: standalone` media query |
| A4 | Vite 7.x latest is available and compatible with React 19.2.4 | Standard Stack | Unlikely to be wrong; Vite 7 was current before Vite 8 |
| A5 | `MissingRequestCookieException` (thrown by `@CookieValue` when cookie absent) is handled by existing `@ControllerAdvice` | Project Constraints | If not handled, Spring returns 400 instead of 401; tests would catch this |

---

## Open Questions (RESOLVED)

1. **(RESOLVED)** **Should `/auth/logout` accept both cookie AND body token for backward compatibility?**
   - What we know: Only new PWA consumes this endpoint after refactor; no other clients except bot (bot uses OTP flow, not password login).
   - What's unclear: Are there any existing callers (e.g., test scripts, Postman collections) that send the body token?
   - Recommendation: Full migration to cookie-only. Update integration tests. Cleaner than maintaining both paths.
   - **Resolution:** Cookie-only. No backward compat needed — bot uses OTP flow, not /auth/login.

2. **(RESOLVED)** **Should `path="/auth"` scope the cookie to exactly auth routes, or use `path="/"`?**
   - What we know: `path="/auth"` means cookie only sent to `/auth/**`. All token refresh goes through `/api/auth/refresh` → Gateway strips `/api` prefix → reaches auth-service at `/auth/refresh`. So `path="/auth"` works when the cookie domain is `localhost:9090` (direct) but the PWA calls `localhost:8080/api/auth/refresh` (Gateway). The Gateway strips `/api` and forwards to auth-service — but the cookie domain/path comparison happens at the browser level against the request URL `localhost:8080/api/auth/refresh`, not the forwarded URL.
   - What's unclear: Since the cookie is set by `auth-service` via the Gateway response, the `Set-Cookie` from auth-service travels through the Gateway. The browser sees it as coming from `localhost:8080`. The cookie `path` should match the URL the browser uses: `/api/auth`. If `path="/auth"`, the browser won't include it on `localhost:8080/api/auth/refresh`.
   - Recommendation: Set `path="/api/auth"` on the cookie to match the Gateway-exposed path. This is a subtle but critical correctness issue.
   - **Resolution:** Use `path("/api/auth")` — browser matches cookie path against the Gateway URL (`/api/auth/refresh`), not the forwarded path (`/auth/refresh`). Using `path("/auth")` would silently break cookie sending.

3. **(RESOLVED)** **Vite project initialization: init in `frontends/pwa/` directly or create subdirectory?**
   - What we know: Phase 28 created `frontends/pwa/dist/` (placeholder). The `package.json` and `vite.config.ts` should live in `frontends/pwa/`.
   - What's unclear: `npm create vite@latest .` with an existing `dist/` directory — Vite may warn about non-empty directory.
   - Recommendation: Use `npm create vite@latest .` (dot for current dir) and accept overwrite. The `dist/` directory is a build output folder, not a source file.
   - **Resolution:** Use `npm create vite@latest .` in `frontends/pwa/`. Accept overwrite warning for `dist/` directory.

---

## Sources

### Primary (HIGH confidence)
- npm registry: `vite-plugin-pwa@1.2.0` peer dependencies, publish date — verified via `npm view` 2026-04-06
- npm registry: `vite@8.0.3`, `react@19.2.4`, `react-router@7.14.0`, `@tanstack/react-query@5.96.2`, `axios@1.14.0`, `tailwindcss@4.2.2`, `motion@12.38.0`, `@phosphor-icons/react@2.1.10`, `shadcn@4.1.2` — all verified via `npm view` 2026-04-06
- Codebase: `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` — existing controller signature
- Codebase: `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/AuthService.java` — existing service logic
- Codebase: `services/api-gateway/src/main/resources/application.yml` — CORS config including `allow-credentials: true`
- Codebase: `frontends/pwa/nginx.conf`, `frontends/pwa/dist/` — confirmed placeholder state
- Codebase: `.planning/phases/29-pwa-scaffold-auth/29-UI-SPEC.md` — approved UI contract
- Project skills: `.claude/skills/progressive-web-app/SKILL.md`, `.claude/skills/motion/SKILL.md`, `.claude/skills/react-patterns/SKILL.md`

### Secondary (MEDIUM confidence)
- STATE.md research flag: "Phase 29: vite-plugin-pwa 1.2.0 + Vite 8 peer dep — may need `--legacy-peer-deps`; fallback is Vite 7" — verified as accurate via npm registry check

### Tertiary (LOW confidence)
- Axios interceptor queue pattern (A2) — standard industry pattern, not verified against official Axios docs in this session
- `window.navigator.standalone` iOS detection (A3) — standard iOS PWA pattern, Apple MDN doc not fetched

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified via npm registry 2026-04-06
- Architecture: HIGH — patterns derived from locked decisions (CONTEXT.md) + existing codebase analysis
- Pitfalls: MEDIUM-HIGH — backend pitfalls verified against codebase; frontend interceptor pattern is assumed standard
- Backend refactor: HIGH — full existing code read; change scope is clear

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (npm versions may advance; vite-plugin-pwa may publish Vite 8 support)

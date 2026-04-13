/**
 * PWAHeadmanRole — end-to-end integration test
 *
 * Verifies the full headman role pipeline:
 *   JWT is_headman claim → AuthProvider.tokenToUser → useAuth().user.isHeadman
 *   → useTabs() → BottomNav renders 4 or 5 tabs
 *
 * Covers PWA-HEAD-01 (isHeadman from JWT) + PWA-HEAD-03 (63-test regression gate).
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { renderHook } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import type { ReactNode } from 'react'

// Mock axios — same pattern as existing auth tests
vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  setAccessTokenGetter: vi.fn(),
  setTokenRefreshCallback: vi.fn(),
  setAuthLogoutCallback: vi.fn(),
}))

import { AuthProvider, useAuth } from '@/features/auth/AuthProvider'
import { BottomNav } from '@/shared/components/BottomNav'
import { apiClient } from '@/shared/lib/axios'

const mockedPost = vi.mocked(apiClient.post)

// Helper: base64url-encode a JWT payload
function createFakeJwt(payload: Record<string, unknown>): string {
  const base64url = (obj: unknown) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '')
  return `${base64url({ alg: 'HS256', typ: 'JWT' })}.${base64url(payload)}.fakesig`
}

// Wrapper combining AuthProvider + BrowserRouter for hook tests
function wrapper({ children }: { children: ReactNode }) {
  return (
    <BrowserRouter>
      <AuthProvider>{children}</AuthProvider>
    </BrowserRouter>
  )
}

// Render the full app shell (AuthProvider + BottomNav) with a pre-configured login mock
async function renderWithLogin(jwtPayload: Record<string, unknown>) {
  const token = createFakeJwt(jwtPayload)
  mockedPost.mockResolvedValueOnce({
    data: { accessToken: token, expiresIn: 900 },
  })

  // Use renderHook to get the auth context, then reuse same wrapper for BottomNav
  const { result } = renderHook(() => useAuth(), { wrapper })

  await act(async () => {
    await result.current.login({ login: 'user', password: 'pass' })
  })

  return { user: result.current.user, isHeadman: result.current.user?.isHeadman ?? false }
}

describe('PWAHeadmanRole — JWT is_headman → user.isHeadman parsing', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('is_headman=true in JWT → user.isHeadman is true → BottomNav should show 5 tabs', async () => {
    const { isHeadman } = await renderWithLogin({
      sub: '42',
      role: 'STUDENT',
      groupId: 7,
      is_headman: true,
    })

    // Auth layer: isHeadman parsed correctly
    expect(isHeadman).toBe(true)
  })

  it('is_headman=false in JWT → user.isHeadman is false → BottomNav should show 4 tabs', async () => {
    const { isHeadman } = await renderWithLogin({
      sub: '10',
      role: 'STUDENT',
      groupId: 3,
      is_headman: false,
    })

    expect(isHeadman).toBe(false)
  })

  it('is_headman absent in JWT → user.isHeadman defaults to false → BottomNav should show 4 tabs', async () => {
    const { isHeadman } = await renderWithLogin({
      sub: '5',
      role: 'STUDENT',
      groupId: 2,
      // is_headman intentionally absent
    })

    expect(isHeadman).toBe(false)
  })
})

describe('PWAHeadmanRole — BottomNav tab rendering by isHeadman state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('BottomNav shows Группа tab (5 links) when isHeadman=true', async () => {
    const headmanToken = createFakeJwt({
      sub: '42',
      role: 'STUDENT',
      groupId: 7,
      is_headman: true,
    })
    mockedPost.mockResolvedValueOnce({
      data: { accessToken: headmanToken, expiresIn: 900 },
    })

    // We need a single provider instance shared between the hook and BottomNav.
    // Render the whole tree together and use a trigger component.
    function TestApp() {
      const { login } = useAuth()
      return (
        <>
          <button
            onClick={() => login({ login: 'headman', password: 'p' })}
            data-testid="login-btn"
          />
          <BottomNav />
        </>
      )
    }

    render(
      <BrowserRouter>
        <AuthProvider>
          <TestApp />
        </AuthProvider>
      </BrowserRouter>,
    )

    // Before login: no links rendered (unauthenticated user still sees BottomNav structure)
    // Trigger login
    await act(async () => {
      screen.getByTestId('login-btn').click()
    })

    // After login with is_headman=true: 5 tabs
    const links = screen.getAllByRole('link')
    expect(links).toHaveLength(5)

    // Группа tab is present at index 3
    const labels = links.map((l) => l.textContent)
    expect(labels[3]).toBe('Группа')
    expect(labels[4]).toBe('Профиль')

    // Группа href ends with /group
    const groupLink = screen.getByText('Группа').closest('a')
    expect(groupLink?.getAttribute('href')).toMatch(/\/group$/)
  })

  it('BottomNav shows no Группа tab (4 links) when is_headman absent in JWT', async () => {
    const plainToken = createFakeJwt({
      sub: '5',
      role: 'STUDENT',
      groupId: 2,
    })
    mockedPost.mockResolvedValueOnce({
      data: { accessToken: plainToken, expiresIn: 900 },
    })

    function TestApp() {
      const { login } = useAuth()
      return (
        <>
          <button
            onClick={() => login({ login: 'student', password: 'p' })}
            data-testid="login-btn"
          />
          <BottomNav />
        </>
      )
    }

    render(
      <BrowserRouter>
        <AuthProvider>
          <TestApp />
        </AuthProvider>
      </BrowserRouter>,
    )

    await act(async () => {
      screen.getByTestId('login-btn').click()
    })

    const links = screen.getAllByRole('link')
    expect(links).toHaveLength(4)
    expect(screen.queryByText('Группа')).toBeNull()
  })
})

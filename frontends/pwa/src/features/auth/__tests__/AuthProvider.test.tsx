import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'

// Mock axios module — apiClient.post used for login/logout/refresh/ws-ticket.
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

vi.mock('../clearAllClientState', () => ({
  clearAllClientState: vi.fn().mockResolvedValue(undefined),
}))

function createFakeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  const signature = 'fakesignature'
  return `${header}.${body}.${signature}`
}

import { AuthProvider, useAuth } from '../AuthProvider'
import { apiClient } from '@/shared/lib/axios'
import { clearAllClientState } from '../clearAllClientState'

const mockedPost = vi.mocked(apiClient.post)
const mockedClearAll = vi.mocked(clearAllClientState)

function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>
}

describe('AuthProvider (M03b cookie-based)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    // default: bootstrap refresh fails (no cookie) — returns 401
    mockedPost.mockRejectedValue({ response: { status: 401 } })
  })

  it('renders children; initially isAuthenticated=false and isBootstrapping=true', async () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()

    // Bootstrap attempt resolves in next microtask
    await waitFor(() => expect(result.current.isBootstrapping).toBe(false))
    expect(mockedPost).toHaveBeenCalledWith(
      '/auth/refresh',
      null,
      { withCredentials: true }
    )
  })

  it('bootstraps from valid cookie — restores session on mount', async () => {
    const fakeToken = createFakeJwt({ sub: '9', role: 'TEACHER' })
    mockedPost.mockResolvedValueOnce({
      data: { accessToken: fakeToken, expiresIn: 900 },
    })

    const { result } = renderHook(() => useAuth(), { wrapper })

    await waitFor(() => expect(result.current.isAuthenticated).toBe(true))
    expect(result.current.user?.id).toBe(9)
    expect(result.current.user?.role).toBe('TEACHER')
  })

  it('after login() succeeds, returns isAuthenticated=true and user object', async () => {
    const fakeToken = createFakeJwt({ sub: '1', role: 'STUDENT', groupId: 5 })
    // Bootstrap: 401. Then login: success.
    mockedPost
      .mockRejectedValueOnce({ response: { status: 401 } })
      .mockResolvedValueOnce({
        data: { accessToken: fakeToken, expiresIn: 900 },
      })

    const { result } = renderHook(() => useAuth(), { wrapper })
    await waitFor(() => expect(result.current.isBootstrapping).toBe(false))

    await act(async () => {
      await result.current.login({ login: 'student00001', password: 'pass' })
    })

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual({ id: 1, role: 'STUDENT', groupId: 5, isHeadman: false })
    expect(mockedPost).toHaveBeenCalledWith(
      '/auth/login',
      { login: 'student00001', password: 'pass' },
      { withCredentials: true }
    )
  })

  it('login does NOT write to localStorage (refresh is in HttpOnly cookie)', async () => {
    const fakeToken = createFakeJwt({ sub: '1', role: 'STUDENT', groupId: 5 })
    mockedPost
      .mockRejectedValueOnce({ response: { status: 401 } })
      .mockResolvedValueOnce({
        data: { accessToken: fakeToken, expiresIn: 900 },
      })

    const { result } = renderHook(() => useAuth(), { wrapper })
    await waitFor(() => expect(result.current.isBootstrapping).toBe(false))
    await act(async () => {
      await result.current.login({ login: 'student00001', password: 'pass' })
    })

    expect(localStorage.getItem('rct.auth.v1')).toBeNull()
  })

  it('migration: removes legacy rct.auth.v1 blob on mount', async () => {
    localStorage.setItem(
      'rct.auth.v1',
      JSON.stringify({ accessToken: 'legacy', refreshToken: 'legacy-refresh' })
    )

    const { result } = renderHook(() => useAuth(), { wrapper })
    await waitFor(() => expect(result.current.isBootstrapping).toBe(false))

    expect(localStorage.getItem('rct.auth.v1')).toBeNull()
  })

  it('after logout() calls /auth/logout with credentials and clearAllClientState', async () => {
    const fakeToken = createFakeJwt({ sub: '1', role: 'STUDENT', groupId: 5 })
    mockedPost
      .mockRejectedValueOnce({ response: { status: 401 } }) // bootstrap
      .mockResolvedValueOnce({ data: { accessToken: fakeToken, expiresIn: 900 } }) // login
      .mockResolvedValueOnce({ data: undefined }) // logout

    const { result } = renderHook(() => useAuth(), { wrapper })
    await waitFor(() => expect(result.current.isBootstrapping).toBe(false))
    await act(async () => {
      await result.current.login({ login: 'student00001', password: 'pass' })
    })
    expect(result.current.isAuthenticated).toBe(true)

    await act(async () => {
      await result.current.logout()
    })

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(mockedPost).toHaveBeenCalledWith(
      '/auth/logout',
      null,
      { withCredentials: true }
    )
    expect(mockedClearAll).toHaveBeenCalled()
  })
})

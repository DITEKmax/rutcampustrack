import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import type { ReactNode } from 'react'

// Mock axios module
vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  setAccessTokenGetter: vi.fn(),
  setRefreshTokenGetter: vi.fn(),
  setTokenRefreshCallback: vi.fn(),
  setAuthLogoutCallback: vi.fn(),
}))

// Create a fake JWT with payload { sub: "1", role: "STUDENT", groupId: 5 }
function createFakeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = btoa(JSON.stringify(payload))
  const signature = 'fakesignature'
  return `${header}.${body}.${signature}`
}

import { AuthProvider, useAuth } from '../AuthProvider'
import { apiClient } from '@/shared/lib/axios'

const mockedPost = vi.mocked(apiClient.post)

function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>
}

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('renders children and returns isAuthenticated=false initially', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
  })

  it('after login() succeeds, returns isAuthenticated=true and user object', async () => {
    const fakeToken = createFakeJwt({ sub: '1', role: 'STUDENT', groupId: 5 })
    mockedPost.mockResolvedValueOnce({
      data: { accessToken: fakeToken, refreshToken: 'refresh-xyz', expiresIn: 900 },
    })

    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.login({ login: 'student00001', password: 'pass' })
    })

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual({ id: 1, role: 'STUDENT', groupId: 5, isHeadman: false })
  })

  it('after login() persists tokens to localStorage', async () => {
    const fakeToken = createFakeJwt({ sub: '1', role: 'STUDENT', groupId: 5 })
    mockedPost.mockResolvedValueOnce({
      data: { accessToken: fakeToken, refreshToken: 'refresh-xyz', expiresIn: 900 },
    })

    const { result } = renderHook(() => useAuth(), { wrapper })
    await act(async () => {
      await result.current.login({ login: 'student00001', password: 'pass' })
    })

    const raw = localStorage.getItem('rct.auth.v1')
    expect(raw).not.toBeNull()
    expect(JSON.parse(raw!)).toEqual({ accessToken: fakeToken, refreshToken: 'refresh-xyz' })
  })

  it('restores session from localStorage on mount', () => {
    const fakeToken = createFakeJwt({ sub: '9', role: 'TEACHER' })
    localStorage.setItem(
      'rct.auth.v1',
      JSON.stringify({ accessToken: fakeToken, refreshToken: 'persisted-refresh' }),
    )

    const { result } = renderHook(() => useAuth(), { wrapper })

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user?.id).toBe(9)
    expect(result.current.user?.role).toBe('TEACHER')
  })

  it('ignores malformed localStorage payload', () => {
    localStorage.setItem('rct.auth.v1', 'not-json')
    const { result } = renderHook(() => useAuth(), { wrapper })
    expect(result.current.isAuthenticated).toBe(false)
  })

  it('after logout(), clears localStorage and calls /auth/logout with refreshToken', async () => {
    const fakeToken = createFakeJwt({ sub: '1', role: 'STUDENT', groupId: 5 })
    mockedPost
      .mockResolvedValueOnce({
        data: { accessToken: fakeToken, refreshToken: 'refresh-xyz', expiresIn: 900 },
      })
      .mockResolvedValueOnce({ data: undefined })

    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.login({ login: 'student00001', password: 'pass' })
    })
    expect(result.current.isAuthenticated).toBe(true)

    await act(async () => {
      await result.current.logout()
    })

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(mockedPost).toHaveBeenCalledWith('/auth/logout', { refreshToken: 'refresh-xyz' })
    expect(localStorage.getItem('rct.auth.v1')).toBeNull()
  })
})

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import type { ReactNode } from 'react'

// Mock axios module — same pattern as AuthProvider.test.tsx
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

// Helper: base64url-encode an object (mirrors JWT format)
function createFakeJwt(payload: Record<string, unknown>): string {
  const base64url = (obj: unknown) =>
    btoa(JSON.stringify(obj))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '')
  const header = base64url({ alg: 'HS256', typ: 'JWT' })
  const body = base64url(payload)
  return `${header}.${body}.fakesignature`
}

import { AuthProvider, useAuth } from '../AuthProvider'
import { apiClient } from '@/shared/lib/axios'

const mockedPost = vi.mocked(apiClient.post)

function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>
}

describe('AuthProvider — isHeadman from JWT is_headman claim', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    // bootstrap refresh fails — no live cookie в тестовом окружении
    mockedPost.mockRejectedValue({ response: { status: 401 } })
  })

  it('sets isHeadman=true when JWT payload contains is_headman: true', async () => {
    const fakeToken = createFakeJwt({
      sub: '42',
      role: 'STUDENT',
      groupId: 7,
      is_headman: true,
    })
    mockedPost
      .mockRejectedValueOnce({ response: { status: 401 } }) // bootstrap
      .mockResolvedValueOnce({
        data: { accessToken: fakeToken, expiresIn: 900 },
      })

    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.login({ login: 'headman00001', password: 'pass' })
    })

    expect(result.current.user?.isHeadman).toBe(true)
  })

  it('sets isHeadman=false when JWT payload contains is_headman: false', async () => {
    const fakeToken = createFakeJwt({
      sub: '10',
      role: 'STUDENT',
      groupId: 3,
      is_headman: false,
    })
    mockedPost
      .mockRejectedValueOnce({ response: { status: 401 } })
      .mockResolvedValueOnce({
        data: { accessToken: fakeToken, expiresIn: 900 },
      })

    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.login({ login: 'student00010', password: 'pass' })
    })

    expect(result.current.user?.isHeadman).toBe(false)
  })

  it('sets isHeadman=false when JWT payload has no is_headman claim (default)', async () => {
    const fakeToken = createFakeJwt({
      sub: '5',
      role: 'STUDENT',
      groupId: 2,
      // is_headman intentionally absent
    })
    mockedPost
      .mockRejectedValueOnce({ response: { status: 401 } })
      .mockResolvedValueOnce({
        data: { accessToken: fakeToken, expiresIn: 900 },
      })

    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.login({ login: 'student00005', password: 'pass' })
    })

    expect(result.current.user?.isHeadman).toBe(false)
  })
})

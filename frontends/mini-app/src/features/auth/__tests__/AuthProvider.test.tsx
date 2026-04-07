import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { AuthProvider } from '../AuthProvider'
import { useAuth } from '../AuthProvider'

// Mock axios module to capture setAccessTokenGetter and setReAuthCallback calls
vi.mock('@/shared/lib/axios', () => ({
  setAccessTokenGetter: vi.fn(),
  setReAuthCallback: vi.fn(),
}))

// Mock the auth API module
vi.mock('@/features/auth/api', () => ({
  tmaAuthApi: vi.fn(),
  getInitDataRaw: vi.fn(),
}))

// Helper to create a test JWT with given payload
function makeTestJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '')
  const body = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '')
  return `${header}.${body}.signature`
}

// Simple consumer component to read auth context
function TestConsumer() {
  const auth = useAuth()
  return (
    <div>
      <span data-testid="role">{auth.user?.role}</span>
      <span data-testid="user-id">{auth.user?.id}</span>
      <span data-testid="authenticated">{String(auth.isAuthenticated)}</span>
    </div>
  )
}

describe('AuthProvider', () => {
  const TEST_INIT_DATA = 'auth_date=1234567890&hash=mockHash&user=%7B%22id%22%3A1%7D'
  const TEST_JWT = makeTestJwt({ sub: '1', role: 'STUDENT', groupId: 5 })
  const TEST_RESPONSE = { accessToken: TEST_JWT, refreshToken: 'rt-token', expiresIn: 900 }

  let tmaAuthApi: ReturnType<typeof vi.fn>
  let getInitDataRaw: ReturnType<typeof vi.fn>
  let setAccessTokenGetter: ReturnType<typeof vi.fn>
  let setReAuthCallback: ReturnType<typeof vi.fn>

  beforeEach(async () => {
    vi.clearAllMocks()

    const axiosMock = await import('@/shared/lib/axios')
    setAccessTokenGetter = axiosMock.setAccessTokenGetter as ReturnType<typeof vi.fn>
    setReAuthCallback = axiosMock.setReAuthCallback as ReturnType<typeof vi.fn>

    const apiMock = await import('@/features/auth/api')
    tmaAuthApi = apiMock.tmaAuthApi as ReturnType<typeof vi.fn>
    getInitDataRaw = apiMock.getInitDataRaw as ReturnType<typeof vi.fn>

    getInitDataRaw.mockReturnValue(TEST_INIT_DATA)
    tmaAuthApi.mockResolvedValue(TEST_RESPONSE)
  })

  it('calls tmaAuthApi with initDataRaw on mount', async () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(tmaAuthApi).toHaveBeenCalledWith(TEST_INIT_DATA)
    })
  })

  it('provides user object with id and role from JWT claims after successful auth', async () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('role').textContent).toBe('STUDENT')
      expect(screen.getByTestId('user-id').textContent).toBe('1')
    })
  })

  it('provides accessToken as string in context after successful auth', async () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('true')
    })
  })

  it('renders ErrorScreen with "Не удалось войти" on auth failure', async () => {
    tmaAuthApi.mockRejectedValue(new Error('Network error'))

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByText('Не удалось войти')).toBeInTheDocument()
    })
  })

  it('retry button triggers re-auth with same initDataRaw', async () => {
    tmaAuthApi.mockRejectedValueOnce(new Error('Network error'))
    tmaAuthApi.mockResolvedValueOnce(TEST_RESPONSE)

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByText('Попробовать снова')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('Попробовать снова'))

    await waitFor(() => {
      expect(tmaAuthApi).toHaveBeenCalledTimes(2)
      expect(tmaAuthApi).toHaveBeenNthCalledWith(2, TEST_INIT_DATA)
    })
  })

  it('wires setAccessTokenGetter and setReAuthCallback after successful auth', async () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('role').textContent).toBe('STUDENT')
    })

    expect(setAccessTokenGetter).toHaveBeenCalled()
    expect(setReAuthCallback).toHaveBeenCalled()
  })

  it('does not use localStorage or sessionStorage for token storage', async () => {
    const localStorageSpy = vi.spyOn(Storage.prototype, 'setItem')
    const sessionStorageSpy = vi.spyOn(Storage.prototype, 'setItem')

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    )

    await waitFor(() => {
      expect(screen.getByTestId('role').textContent).toBe('STUDENT')
    })

    // Token should not be stored in localStorage or sessionStorage
    expect(localStorageSpy).not.toHaveBeenCalledWith(expect.stringContaining('token'), expect.anything())
    expect(sessionStorageSpy).not.toHaveBeenCalledWith(expect.stringContaining('token'), expect.anything())
  })
})

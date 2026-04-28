import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router'
import { ProtectedRoute } from '../ProtectedRoute'

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

vi.mock('@/features/auth/AuthProvider', async (importOriginal) => {
  const mod = await importOriginal<typeof import('@/features/auth/AuthProvider')>()
  return {
    ...mod,
    useAuth: vi.fn(),
  }
})

import { useAuth } from '@/features/auth/AuthProvider'

function renderWithRoutes() {
  return render(
    <MemoryRouter initialEntries={['/home']}>
      <Routes>
        <Route
          path="/home"
          element={
            <ProtectedRoute>
              <div data-testid="protected-content">protected</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div data-testid="login-page">login</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('ProtectedRoute', () => {
  it('does NOT redirect to /login while AuthProvider is bootstrapping (cold-start refresh in flight)', () => {
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: false,
      user: null,
      accessToken: null,
      isBootstrapping: true,
      login: vi.fn(),
      logout: vi.fn(),
    })
    renderWithRoutes()

    expect(screen.queryByTestId('login-page')).toBeNull()
    expect(screen.queryByTestId('protected-content')).toBeNull()
  })

  it('renders children when authenticated', () => {
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: true,
      user: { id: 1, role: 'STUDENT', isHeadman: false },
      accessToken: 'token',
      isBootstrapping: false,
      login: vi.fn(),
      logout: vi.fn(),
    })
    renderWithRoutes()

    expect(screen.getByTestId('protected-content')).toBeTruthy()
  })

  it('redirects to /login after bootstrap finishes without auth', () => {
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: false,
      user: null,
      accessToken: null,
      isBootstrapping: false,
      login: vi.fn(),
      logout: vi.fn(),
    })
    renderWithRoutes()

    expect(screen.getByTestId('login-page')).toBeTruthy()
  })
})

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'

// Mock useNetworkStatus
vi.mock('@/shared/hooks/useNetworkStatus', () => ({
  useNetworkStatus: vi.fn(() => ({ isOnline: true })),
}))

// Mock AuthProvider
vi.mock('../AuthProvider', () => ({
  useAuth: vi.fn(() => ({
    isAuthenticated: false,
    user: null,
    login: vi.fn(),
    logout: vi.fn(),
    accessToken: null,
  })),
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

// Mock LoadingSpinner
vi.mock('@/shared/components/LoadingSpinner', () => ({
  LoadingSpinner: () => <div data-testid="spinner" />,
}))

import { LoginPage } from '../LoginPage'
import { useNetworkStatus } from '@/shared/hooks/useNetworkStatus'

const mockedUseNetworkStatus = vi.mocked(useNetworkStatus)

function renderLogin() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedUseNetworkStatus.mockReturnValue({ isOnline: true })
  })

  it('renders username label', () => {
    renderLogin()
    expect(screen.getByText('Имя пользователя')).toBeInTheDocument()
  })

  it('renders password label', () => {
    renderLogin()
    expect(screen.getByText('Пароль')).toBeInTheDocument()
  })

  it('renders submit button with text "Войти в систему"', () => {
    renderLogin()
    expect(screen.getByRole('button', { name: 'Войти в систему' })).toBeInTheDocument()
  })

  it('renders inputs disabled when offline', () => {
    mockedUseNetworkStatus.mockReturnValue({ isOnline: false })
    renderLogin()

    const usernameInput = screen.getByPlaceholderText('student00001')
    const passwordInput = screen.getByLabelText('Пароль')
    expect(usernameInput).toBeDisabled()
    expect(passwordInput).toBeDisabled()
  })

  it('renders button with "Нет подключения" when offline', () => {
    mockedUseNetworkStatus.mockReturnValue({ isOnline: false })
    renderLogin()

    expect(screen.getByRole('button', { name: 'Нет подключения' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Нет подключения' })).toBeDisabled()
  })

  it('renders RutTrack heading', () => {
    renderLogin()
    expect(screen.getByText('RutTrack')).toBeInTheDocument()
  })
})

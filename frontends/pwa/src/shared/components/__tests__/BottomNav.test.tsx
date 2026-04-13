import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'

// Mock axios — same pattern as AuthProvider tests
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

// Mock useAuth to control isHeadman in each test
vi.mock('@/features/auth/AuthProvider', async (importOriginal) => {
  const mod = await importOriginal<typeof import('@/features/auth/AuthProvider')>()
  return {
    ...mod,
    useAuth: vi.fn(),
  }
})

import { BottomNav } from '../BottomNav'
import { useAuth } from '@/features/auth/AuthProvider'

const mockedUseAuth = vi.mocked(useAuth)

function renderBottomNav(): void {
  render(
    <BrowserRouter>
      <BottomNav />
    </BrowserRouter>,
  )
}

describe('BottomNav', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders 4 tabs for a plain student (isHeadman=false)', () => {
    mockedUseAuth.mockReturnValue({
      isAuthenticated: true,
      user: { id: 1, role: 'STUDENT', groupId: 5, isHeadman: false },
      accessToken: 'token',
      login: vi.fn(),
      logout: vi.fn(),
    })

    renderBottomNav()

    const links = screen.getAllByRole('link')
    expect(links).toHaveLength(4)
    expect(screen.queryByText('Группа')).toBeNull()
  })

  it('renders 5 tabs for a headman user (isHeadman=true), Группа before Профиль', () => {
    mockedUseAuth.mockReturnValue({
      isAuthenticated: true,
      user: { id: 42, role: 'STUDENT', groupId: 7, isHeadman: true },
      accessToken: 'token',
      login: vi.fn(),
      logout: vi.fn(),
    })

    renderBottomNav()

    const links = screen.getAllByRole('link')
    expect(links).toHaveLength(5)

    // Verify tab order: Главная, Расписание, Отметка, Группа, Профиль
    const labels = links.map((l) => l.textContent)
    expect(labels).toEqual(['Главная', 'Расписание', 'Отметка', 'Группа', 'Профиль'])

    // Группа link href ends with /group
    const groupLink = screen.getByText('Группа').closest('a')
    expect(groupLink?.getAttribute('href')).toMatch(/\/group$/)
  })
})

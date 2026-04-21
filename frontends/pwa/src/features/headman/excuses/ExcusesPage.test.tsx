import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { ExcusesPage } from './ExcusesPage'
import type { ExcuseTicket } from '@/features/headman/shared/types'

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => ({
    user: { id: 7, role: 'STUDENT', groupId: 1, isHeadman: true },
    isAuthenticated: true,
    accessToken: 'stub',
    login: async () => {},
    logout: async () => {},
  }),
}))

const mockUseGroupExcuses = vi.fn()
const mockMutate = vi.fn()
vi.mock('@/features/headman/shared/headmanApi', async () => {
  const actual =
    await vi.importActual<typeof import('@/features/headman/shared/headmanApi')>(
      '@/features/headman/shared/headmanApi',
    )
  return {
    ...actual,
    useGroupExcuses: () => mockUseGroupExcuses(),
    useDecideExcuse: () => ({ mutateAsync: mockMutate }),
  }
})

function wrap(ui: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  )
}

function pendingTicket(overrides: Partial<ExcuseTicket> = {}): ExcuseTicket {
  return {
    id: 'ex-1',
    studentId: 100,
    groupId: 1,
    studentName: 'Петров П.П.',
    lessonIds: [501, 502],
    excuseType: 'ILLNESS',
    comment: 'Болею',
    status: 'submitted',
    decisionBy: null,
    decisionComment: null,
    decisionAt: null,
    createdAt: '2026-04-16T10:00:00Z',
    updatedAt: '2026-04-16T10:00:00Z',
    ...overrides,
  }
}

describe('ExcusesPage', () => {
  beforeEach(() => {
    mockUseGroupExcuses.mockReset()
    mockMutate.mockReset()
  })

  it('renders back link to /group and page heading', () => {
    mockUseGroupExcuses.mockReturnValue({ data: [], isLoading: false })
    wrap(<ExcusesPage />)
    expect(screen.getByRole('link', { name: /назад/i })).toHaveAttribute('href', '/group')
    expect(screen.getByRole('heading', { level: 1, name: 'Пропуски' })).toBeInTheDocument()
  })

  it('shows empty state when no tickets', () => {
    mockUseGroupExcuses.mockReturnValue({ data: [], isLoading: false })
    wrap(<ExcusesPage />)
    expect(screen.getByText('Нет заявок')).toBeInTheDocument()
  })

  it('renders a pending ticket with approve/reject buttons', () => {
    mockUseGroupExcuses.mockReturnValue({ data: [pendingTicket()], isLoading: false })
    wrap(<ExcusesPage />)
    expect(screen.getByText('Петров П.П.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Одобрить/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Отклонить/ })).toBeInTheDocument()
  })

  it('renders skeletons while loading', () => {
    mockUseGroupExcuses.mockReturnValue({ data: undefined, isLoading: true })
    const { container } = wrap(<ExcusesPage />)
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0)
  })
})

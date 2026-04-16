import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { LateCheckinPage } from './LateCheckinPage'
import type { LateCheckinRequest } from '@/features/headman/shared/types'

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => ({
    user: { id: 7, role: 'STUDENT', groupId: 1, isHeadman: true },
    isAuthenticated: true,
    accessToken: 'stub',
    login: async () => {},
    logout: async () => {},
  }),
}))

const mockUseList = vi.fn()
const mockMutate = vi.fn()
vi.mock('@/features/headman/shared/headmanApi', async () => {
  const actual =
    await vi.importActual<typeof import('@/features/headman/shared/headmanApi')>(
      '@/features/headman/shared/headmanApi',
    )
  return {
    ...actual,
    usePendingLateCheckinRequests: () => mockUseList(),
    useDecideLateCheckin: () => ({ mutateAsync: mockMutate }),
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

function sample(overrides: Partial<LateCheckinRequest> = {}): LateCheckinRequest {
  return {
    id: 'req-1',
    studentId: 10,
    groupId: 1,
    lessonId: 42,
    studentName: 'Сидоров С.С.',
    status: 'pending',
    decisionBy: null,
    decisionAt: null,
    createdAt: '2026-04-16T10:00:00Z',
    updatedAt: '2026-04-16T10:00:00Z',
    ...overrides,
  }
}

describe('LateCheckinPage', () => {
  beforeEach(() => {
    mockUseList.mockReset()
    mockMutate.mockReset()
  })

  it('renders back link and page heading', () => {
    mockUseList.mockReturnValue({ data: [], isLoading: false })
    wrap(<LateCheckinPage />)
    expect(screen.getByRole('link', { name: /назад/i })).toHaveAttribute('href', '/group')
    expect(
      screen.getByRole('heading', { level: 1, name: 'Запросы отметки' }),
    ).toBeInTheDocument()
  })

  it('shows empty state when no requests', () => {
    mockUseList.mockReturnValue({ data: [], isLoading: false })
    wrap(<LateCheckinPage />)
    expect(screen.getByText('Нет активных запросов')).toBeInTheDocument()
  })

  it('renders a request with approve/reject buttons', () => {
    mockUseList.mockReturnValue({ data: [sample()], isLoading: false })
    wrap(<LateCheckinPage />)
    expect(screen.getByText('Сидоров С.С.')).toBeInTheDocument()
    expect(screen.getByText(/Пара #42/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Подтвердить/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Отклонить/ })).toBeInTheDocument()
  })

  it('renders skeletons while loading', () => {
    mockUseList.mockReturnValue({ data: undefined, isLoading: true })
    const { container } = wrap(<LateCheckinPage />)
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0)
  })
})

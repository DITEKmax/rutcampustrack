import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Overview } from './Overview'

// Mock axios to prevent real requests
vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  setAccessTokenGetter: vi.fn(),
  setTokenRefreshCallback: vi.fn(),
  setAuthLogoutCallback: vi.fn(),
}))

vi.mock('@/features/headman/shared/headmanApi', () => ({
  useGroupMembers: vi.fn(),
  useTodayLesson: vi.fn(),
  usePendingExcuses: vi.fn(),
  usePendingLateCheckins: vi.fn(),
}))

vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: vi.fn(() => ({
    user: { id: 1, role: 'STUDENT', groupId: 42, isHeadman: true },
  })),
}))

import {
  useGroupMembers,
  useTodayLesson,
  usePendingExcuses,
  usePendingLateCheckins,
} from '@/features/headman/shared/headmanApi'

function renderOverview() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <Overview />
      </QueryClientProvider>
    </BrowserRouter>,
  )
}

describe('Overview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useGroupMembers).mockReturnValue({
      data: [
        { id: 1, fullName: 'Иванов Иван', login: 'student00001' },
        { id: 2, fullName: 'Петров Пётр', login: 'student00002' },
      ],
      isLoading: false,
    } as any)
    vi.mocked(useTodayLesson).mockReturnValue({
      data: {
        lessonId: 10,
        subjectName: 'Математика',
        startsAt: '2026-04-13T09:00:00Z',
        endsAt: '2026-04-13T10:30:00Z',
        room: '302',
      },
      isLoading: false,
    } as any)
    vi.mocked(usePendingExcuses).mockReturnValue({
      data: [{ id: 1, studentName: 'Иванов', reason: 'болезнь', createdAt: '2026-04-13T08:00:00Z' }],
      isLoading: false,
    } as any)
    vi.mocked(usePendingLateCheckins).mockReturnValue({
      data: [{ id: 1, studentName: 'Петров', lessonId: 10, createdAt: '2026-04-13T08:00:00Z' }],
      isLoading: false,
    } as any)
  })

  it('Test 1: renders "Члены группы" card with numeric member count', () => {
    renderOverview()
    expect(screen.getByText('Члены группы')).toBeInTheDocument()
    // 2 members in fixture
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('Test 2: renders today lesson subject name and time when useTodayLesson returns data', () => {
    renderOverview()
    expect(screen.getByText('Математика')).toBeInTheDocument()
    // Time slice: startsAt 09:00–endsAt 10:30 (UTC)
    expect(screen.getByText(/09:00/)).toBeInTheDocument()
    expect(screen.getByText(/10:30/)).toBeInTheDocument()
  })

  it('Test 3: renders "На сегодня пар больше нет" when useTodayLesson returns null', () => {
    vi.mocked(useTodayLesson).mockReturnValue({ data: null, isLoading: false } as any)
    renderOverview()
    expect(screen.getByText('На сегодня пар больше нет')).toBeInTheDocument()
  })

  it('Test 4: renders pending excuse badge count; shows muted state when data is empty', () => {
    renderOverview()
    // Badge "1" for the pending excuse
    const badges = screen.getAllByText('1')
    expect(badges.length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('Пропусков ждут одобрения')).toBeInTheDocument()

    // Now test empty state
    vi.mocked(usePendingExcuses).mockReturnValue({ data: [], isLoading: false } as any)
    vi.mocked(usePendingLateCheckins).mockReturnValue({ data: [], isLoading: false } as any)
    const { rerender } = renderOverview()
    rerender(
      <BrowserRouter>
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
          <Overview />
        </QueryClientProvider>
      </BrowserRouter>,
    )
    expect(screen.getAllByText('Нет ожидающих запросов').length).toBeGreaterThanOrEqual(1)
  })

  it('Test 5: back button links to /group', () => {
    renderOverview()
    const backLink = screen.getByRole('link', { name: /назад/i })
    expect(backLink).toHaveAttribute('href', '/group')
  })
})

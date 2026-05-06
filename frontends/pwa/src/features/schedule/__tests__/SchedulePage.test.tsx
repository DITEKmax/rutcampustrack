import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import type { ReactNode } from 'react'

// Mock axios
vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
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

// Mock useAuth
vi.mock('@/features/auth/AuthProvider', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { id: 1, role: 'STUDENT', groupId: 5 },
    accessToken: 'fake-token',
    login: vi.fn(),
    logout: vi.fn(),
  }),
}))

// Mock useNetworkStatus
vi.mock('@/shared/hooks/useNetworkStatus', () => ({
  useNetworkStatus: () => ({ isOnline: true }),
}))

// Mock StompProvider
vi.mock('@/features/checkin/StompProvider', () => ({
  useStompEvents: () => ({
    attendanceCounts: {},
    personalStatuses: {},
    markPersonalStatus: vi.fn(),
  }),
}))

import { apiClient } from '@/shared/lib/axios'
import { SchedulePage } from '../SchedulePage'

const mockedGet = vi.mocked(apiClient.get)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          {children}
        </MemoryRouter>
      </QueryClientProvider>
    )
  }
}

/** Get backend dayOfWeek for today: 1=Mon..6=Sat, Sunday maps to 6 (Saturday) */
function getTodayBackendDow(): number {
  const jsDay = new Date().getDay() // 0=Sun, 1=Mon..6=Sat
  if (jsDay === 0) return 6 // Sunday -> treat as Saturday
  return jsDay // Mon=1..Sat=6
}

const makeLessons = (dayOfWeek: number, count: number) =>
  Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    scheduleItemId: i + 100,
    groupId: 5,
    subjectId: 10 + i,
    teacherId: 20 + i,
    date: '2026-04-06',
    status: 'PLANNED' as const,
    dayOfWeek,
    lessonNumber: i + 1,
    startTime: `0${9 + i}:00:00`,
    endTime: `${10 + i}:35:00`,
    weekType: 'BOTH' as const,
    room: `${301 + i}`,
    geoBlocked: false,
    cancelReason: null,
    createdAt: '2026-04-01T00:00:00Z',
  }))

describe('SchedulePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  /** Active semester mock — now required for bounds check (M07 G6/4). */
  const mockSemesters = {
    data: {
      _embedded: {
        semesterResponseList: [
          {
            id: 1,
            name: 'Весна 2026',
            dateFrom: '2026-02-01',
            dateTo: '2026-06-30',
            active: true,
          },
        ],
      },
    },
  }

  it('renders lesson cards when data is returned', async () => {
    const backendDow = getTodayBackendDow()
    const lessons = makeLessons(backendDow, 2)

    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/schedule/groups/')) {
        return { data: { _embedded: { lessonResponseList: lessons } } }
      }
      if (url.includes('/academic/semesters')) {
        return mockSemesters
      }
      if (url.includes('/academic/subjects/')) {
        const id = Number(url.split('/').pop())
        return { data: { id, name: `Subject ${id}` } }
      }
      return { data: {} }
    })

    render(<SchedulePage />, { wrapper: createWrapper() })

    // Wait for room text to appear
    await waitFor(() => {
      expect(screen.getByText(/Ауд\. 301/)).toBeInTheDocument()
    }, { timeout: 3000 })
  })

  it('renders full homework details under a student schedule lesson', async () => {
    const backendDow = getTodayBackendDow()
    const lessons = makeLessons(backendDow, 1)
    const homework = {
      id: 55,
      title: 'Read chapter 4',
      description: 'Solve tasks 1-5',
      link: 'https://example.com/hw.pdf',
      subjectId: lessons[0].subjectId,
      groupId: 5,
      semesterId: 1,
      publishedBy: 1,
      completed: false,
      createdAt: '2026-04-01T00:00:00Z',
      lessonDate: lessons[0].date,
      lessonNumber: lessons[0].lessonNumber,
    }

    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/schedule/groups/')) {
        return { data: { _embedded: { lessonResponseList: lessons } } }
      }
      if (url.includes('/academic/homeworks')) {
        return { data: { _embedded: { homeworkResponseList: [homework] } } }
      }
      if (url.includes('/academic/semesters')) {
        return mockSemesters
      }
      if (url.includes('/academic/subjects/')) {
        return { data: { id: lessons[0].subjectId, name: 'Physics' } }
      }
      return { data: {} }
    })

    render(<SchedulePage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getAllByText('Read chapter 4').length).toBeGreaterThanOrEqual(2)
    })
    expect(await screen.findByText('Solve tasks 1-5')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'https://example.com/hw.pdf' }))
      .toHaveAttribute('href', 'https://example.com/hw.pdf')
  })

  it('shows empty state when no lessons for selected day', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/schedule/groups/')) {
        return { data: {} } // No _embedded means empty
      }
      if (url.includes('/academic/semesters')) {
        return mockSemesters
      }
      return { data: {} }
    })

    render(<SchedulePage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Занятий нет')).toBeInTheDocument()
    }, { timeout: 3000 })
  })

  it('shows fixed today pill when selected day is not today', async () => {
    const backendDow = getTodayBackendDow()
    const lessons = makeLessons(backendDow, 1)

    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/schedule/groups/')) {
        return { data: { _embedded: { lessonResponseList: lessons } } }
      }
      if (url.includes('/academic/semesters')) {
        return mockSemesters
      }
      if (url.includes('/academic/subjects/')) {
        return { data: { id: lessons[0].subjectId, name: 'Physics' } }
      }
      return { data: {} }
    })

    render(<SchedulePage />, { wrapper: createWrapper() })

    expect(screen.queryByTestId('schedule-today-pill')).not.toBeInTheDocument()

    const user = userEvent.setup()
    const tabs = await screen.findAllByRole('tab')
    const todayIndex = backendDow - 1
    const otherIndex = todayIndex === 0 ? 1 : 0
    await user.click(tabs[otherIndex])

    const pill = await screen.findByTestId('schedule-today-pill')
    expect(pill).toHaveClass('fixed', 'left-1/2', '-translate-x-1/2')
    expect(pill.style.bottom).toBe(
      'calc(var(--bottom-nav-height) + 16px + env(safe-area-inset-bottom))',
    )

    await user.click(pill)
    await waitFor(() => {
      expect(screen.queryByTestId('schedule-today-pill')).not.toBeInTheDocument()
    })
  })

  it('day tab click changes displayed lessons', async () => {
    // Put lessons only on Monday (dayOfWeek=1)
    const mondayLessons = makeLessons(1, 1)

    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/schedule/groups/')) {
        return { data: { _embedded: { lessonResponseList: mondayLessons } } }
      }
      if (url.includes('/academic/semesters')) {
        return mockSemesters
      }
      if (url.includes('/academic/subjects/')) {
        return { data: { id: 10, name: 'Monday Subject' } }
      }
      return { data: {} }
    })

    render(<SchedulePage />, { wrapper: createWrapper() })

    const user = userEvent.setup()

    // Click on Monday tab
    const monTab = await screen.findByRole('tab', { name: /Пн/i })
    await user.click(monTab)

    // Should show the Monday lesson room
    await waitFor(() => {
      expect(screen.getByText(/Ауд\. 301/)).toBeInTheDocument()
    }, { timeout: 3000 })
  })
})

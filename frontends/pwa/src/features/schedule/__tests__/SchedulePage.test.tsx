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

  it('renders lesson cards when data is returned', async () => {
    const backendDow = getTodayBackendDow()
    const lessons = makeLessons(backendDow, 2)

    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/schedule/groups/')) {
        return { data: { _embedded: { lessonResponseList: lessons } } }
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

  it('shows empty state when no lessons for selected day', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/schedule/groups/')) {
        return { data: {} } // No _embedded means empty
      }
      return { data: {} }
    })

    render(<SchedulePage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Занятий нет')).toBeInTheDocument()
    }, { timeout: 3000 })
  })

  it('day tab click changes displayed lessons', async () => {
    // Put lessons only on Monday (dayOfWeek=1)
    const mondayLessons = makeLessons(1, 1)

    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/schedule/groups/')) {
        return { data: { _embedded: { lessonResponseList: mondayLessons } } }
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

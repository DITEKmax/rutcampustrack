import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import type { ReactNode } from 'react'

// Mock axios
vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
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
    user: { id: 1, role: 'STUDENT', groupId: 10 },
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
import { AttendanceStatsPage } from '../AttendanceStatsPage'

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
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }
}

const makeStatsResponse = (subjects: object[]) => ({
  data: {
    subjects,
    overall: { total: 20, attended: 15, absent: 3, excused: 2, percentage: 75 },
  },
})

describe('AttendanceStatsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('ATT-01: renders subject stats with percentage and counts', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/attendance/reports/student/stats')) {
        return makeStatsResponse([
          {
            subjectId: 1,
            subjectName: 'Математика',
            total: 20,
            attended: 15,
            absent: 3,
            excused: 2,
            percentage: 75,
          },
        ])
      }
      if (url.includes('/academic/thresholds/resolve')) {
        return { data: { minPercentage: 60, level: 'GROUP', sourceId: 10 } }
      }
      return { data: {} }
    })

    render(<AttendanceStatsPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Математика')).toBeInTheDocument()
    })

    expect(screen.getByText('75%')).toBeInTheDocument()
    expect(screen.getByText(/б: 15/)).toBeInTheDocument()
    expect(screen.getByText(/н: 3/)).toBeInTheDocument()
    expect(screen.getByText(/у: 2/)).toBeInTheDocument()
  })

  it('ATT-02 (red zone shown): renders red zone badge when percentage is below threshold', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/attendance/reports/student/stats')) {
        return makeStatsResponse([
          {
            subjectId: 2,
            subjectName: 'Физика',
            total: 20,
            attended: 10,
            absent: 8,
            excused: 2,
            percentage: 50,
          },
        ])
      }
      if (url.includes('/academic/thresholds/resolve')) {
        return { data: { minPercentage: 60, level: 'GROUP', sourceId: 10 } }
      }
      return { data: {} }
    })

    render(<AttendanceStatsPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Красная зона')).toBeInTheDocument()
    })
  })

  it('ATT-02 (red zone hidden on null threshold): does not render red zone badge when threshold API returns 404', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/attendance/reports/student/stats')) {
        return makeStatsResponse([
          {
            subjectId: 3,
            subjectName: 'История',
            total: 20,
            attended: 10,
            absent: 8,
            excused: 2,
            percentage: 50,
          },
        ])
      }
      if (url.includes('/academic/thresholds/resolve')) {
        throw { response: { status: 404 } }
      }
      return { data: {} }
    })

    render(<AttendanceStatsPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('История')).toBeInTheDocument()
    })

    expect(screen.queryByText('Красная зона')).not.toBeInTheDocument()
  })

  it('Empty state: renders "Нет данных" when subjects array is empty', async () => {
    mockedGet.mockImplementation(async (url: string) => {
      if (url.includes('/attendance/reports/student/stats')) {
        return makeStatsResponse([])
      }
      if (url.includes('/academic/thresholds/resolve')) {
        return { data: { minPercentage: 60, level: 'GROUP', sourceId: 10 } }
      }
      return { data: {} }
    })

    render(<AttendanceStatsPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText('Нет данных')).toBeInTheDocument()
    })

    expect(
      screen.getByText('Посещаемость ещё не была зафиксирована')
    ).toBeInTheDocument()
  })
})

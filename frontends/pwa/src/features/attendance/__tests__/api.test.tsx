import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
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

import { apiClient } from '@/shared/lib/axios'
import { useStudentStats, useThreshold, useAttendanceRecords } from '../api'

const mockedGet = vi.mocked(apiClient.get)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
  }
}

describe('useStudentStats', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns subjects array from API response', async () => {
    const mockData = {
      subjects: [
        {
          subjectId: 1,
          subjectName: 'Математика',
          total: 20,
          attended: 15,
          absent: 3,
          excused: 2,
          percentage: 75,
        },
      ],
      overall: { total: 20, attended: 15, absent: 3, excused: 2, percentage: 75 },
    }

    mockedGet.mockResolvedValue({ data: mockData })

    const { result } = renderHook(() => useStudentStats(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data?.subjects).toHaveLength(1)
    expect(result.current.data?.subjects[0].subjectName).toBe('Математика')
  })
})

describe('useThreshold', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns null when API responds 404', async () => {
    const error = { response: { status: 404 } }
    mockedGet.mockRejectedValue(error)

    const { result } = renderHook(() => useThreshold(10), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toBeNull()
  })

  it('returns minPercentage when threshold is configured', async () => {
    mockedGet.mockResolvedValue({
      data: { minPercentage: 60, level: 'GROUP', sourceId: 10 },
    })

    const { result } = renderHook(() => useThreshold(10), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toBe(60)
  })
})

describe('useAttendanceRecords', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns records array for a given subjectId', async () => {
    const mockRecord = {
      lessonId: 1,
      subjectId: 5,
      lessonDate: '2026-03-15',
      lessonNumber: 2,
      status: 'present',
      symbol: 'б',
      source: 'GEO',
    }

    mockedGet.mockResolvedValue({
      data: { _embedded: { attendanceRecordEntryList: [mockRecord] } },
    })

    const { result } = renderHook(() => useAttendanceRecords(5), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toHaveLength(1)
    expect(result.current.data![0].lessonDate).toBe('2026-03-15')
  })
})

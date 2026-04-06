import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

// Mock axios
vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
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

import { apiClient } from '@/shared/lib/axios'
import { useActiveSemester, useHomework, useToggleHomework } from '../api'

const mockedGet = vi.mocked(apiClient.get)
const mockedPost = vi.mocked(apiClient.post)
const mockedDelete = vi.mocked(apiClient.delete)

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

describe('useActiveSemester', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns the id of the semester with active=true', async () => {
    mockedGet.mockResolvedValue({
      data: {
        _embedded: {
          semesterResponseList: [
            { id: 3, name: 'Осень 2024', startDate: '2024-09-01', endDate: '2024-12-31', active: false },
            { id: 5, name: 'Весна 2025', startDate: '2025-02-01', endDate: '2025-06-30', active: true },
          ],
        },
      },
    })

    const { result } = renderHook(() => useActiveSemester(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toBe(5)
    expect(mockedGet).toHaveBeenCalledWith('/academic/semesters', { params: { size: 50 } })
  })
})

describe('useHomework', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns normalized HomeworkResponse[] from HATEOAS paginated response', async () => {
    mockedGet.mockResolvedValue({
      data: {
        _embedded: {
          homeworkResponseList: [
            {
              id: 1,
              title: 'Задача по матану',
              description: null,
              link: null,
              subjectId: 10,
              groupId: 2,
              semesterId: 5,
              publishedBy: 100,
              completed: false,
              createdAt: '2025-03-01T10:00:00Z',
            },
            {
              id: 2,
              title: 'Лабораторная по физике',
              description: 'Описание',
              link: 'https://example.com',
              subjectId: 11,
              groupId: 2,
              semesterId: 5,
              publishedBy: 101,
              completed: true,
              createdAt: '2025-02-28T08:00:00Z',
            },
          ],
        },
      },
    })

    const { result } = renderHook(() => useHomework(2, 5), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toHaveLength(2)
    expect(result.current.data![0].title).toBe('Задача по матану')
    expect(result.current.data![1].completed).toBe(true)
    expect(mockedGet).toHaveBeenCalledWith('/academic/homeworks', { params: { groupId: 2, semesterId: 5 } })
  })
})

describe('useToggleHomework', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls POST /complete when toggling to done', async () => {
    mockedPost.mockResolvedValue({ status: 204 })

    const { result } = renderHook(() => useToggleHomework(), { wrapper: createWrapper() })

    result.current.mutate({ id: 1, completed: true, groupId: 2, semesterId: 5 })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(mockedPost).toHaveBeenCalledWith('/academic/homeworks/1/complete')
    expect(mockedDelete).not.toHaveBeenCalled()
  })

  it('calls DELETE /complete when toggling to undone', async () => {
    mockedDelete.mockResolvedValue({ status: 204 })

    const { result } = renderHook(() => useToggleHomework(), { wrapper: createWrapper() })

    result.current.mutate({ id: 2, completed: false, groupId: 2, semesterId: 5 })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(mockedDelete).toHaveBeenCalledWith('/academic/homeworks/2/complete')
    expect(mockedPost).not.toHaveBeenCalled()
  })
})

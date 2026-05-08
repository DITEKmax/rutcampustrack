import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

import { apiClient } from '@/shared/lib/axios'
import { useActiveSemester, useHomeworkList, useToggleHomework } from '../api'

function createWrapper(queryClient?: QueryClient) {
  const qc = queryClient ?? new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children)
}

describe('useActiveSemester', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('fetches /academic/semesters and returns the active one', async () => {
    const semesters = [
      { id: 1, name: 'Осень 2024', startDate: '2024-09-01', endDate: '2025-01-31', active: false },
      { id: 2, name: 'Весна 2025', startDate: '2025-02-01', endDate: '2025-06-30', active: true },
    ]
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { _embedded: { semesterResponseList: semesters } },
    })

    const { result } = renderHook(() => useActiveSemester(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.id).toBe(2)
    expect(result.current.data?.active).toBe(true)
  })

  it('returns null when no active semester', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { _embedded: { semesterResponseList: [{ id: 1, active: false }] } },
    })

    const { result } = renderHook(() => useActiveSemester(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toBeNull()
  })
})

describe('useHomeworkList', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('does not fetch when groupId is undefined', () => {
    const { result } = renderHook(() => useHomeworkList(undefined, 1), { wrapper: createWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })

  it('does not fetch when semesterId is undefined', () => {
    const { result } = renderHook(() => useHomeworkList(5, undefined), { wrapper: createWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })

  it('fetches homework when both IDs provided', async () => {
    const homeworks = [{ id: 1, title: 'ДЗ 1', subjectName: 'Физика', completed: false }]
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { _embedded: { homeworkResponseList: homeworks } },
    })

    const { result } = renderHook(() => useHomeworkList(5, 2), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.[0].title).toBe('ДЗ 1')
    expect(apiClient.get).toHaveBeenCalledWith('/academic/homeworks', {
      params: { groupId: 5, semesterId: 2, size: 200 },
    })
  })
})

describe('useToggleHomework', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('calls POST for uncompleted homework (toggle to complete)', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} })

    const { result } = renderHook(() => useToggleHomework(5, 2), { wrapper: createWrapper() })

    await act(async () => {
      result.current.mutate({ id: 1, completed: false })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.post).toHaveBeenCalledWith('/academic/homeworks/1/complete')
  })

  it('calls DELETE for completed homework (toggle to uncomplete)', async () => {
    ;(apiClient.delete as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} })

    const { result } = renderHook(() => useToggleHomework(5, 2), { wrapper: createWrapper() })

    await act(async () => {
      result.current.mutate({ id: 1, completed: true })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.delete).toHaveBeenCalledWith('/academic/homeworks/1/complete')
  })

  it('updates cached homework optimistically and rolls back on error', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(['homeworks', 5, 2], [
      {
        id: 1,
        title: 'ДЗ 1',
        subjectId: 10,
        subjectName: 'Физика',
        groupId: 5,
        semesterId: 2,
        publishedBy: 7,
        completed: false,
        createdAt: '2026-05-08T00:00:00',
        lessonDate: '2026-05-09',
        lessonNumber: 1,
        description: null,
        link: null,
        dueDate: null,
      },
    ])
    let rejectPost: (error: Error) => void = () => {}
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockReturnValue(
      new Promise((_resolve, reject) => {
        rejectPost = reject
      }),
    )

    const { result } = renderHook(() => useToggleHomework(5, 2), {
      wrapper: createWrapper(queryClient),
    })

    act(() => {
      result.current.mutate({ id: 1, completed: false })
    })

    await waitFor(() => {
      expect(queryClient.getQueryData<[{ completed: boolean }]>(['homeworks', 5, 2])?.[0].completed)
        .toBe(true)
    })

    act(() => {
      rejectPost(new Error('network'))
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(queryClient.getQueryData<[{ completed: boolean }]>(['homeworks', 5, 2])?.[0].completed)
      .toBe(false)
  })
})

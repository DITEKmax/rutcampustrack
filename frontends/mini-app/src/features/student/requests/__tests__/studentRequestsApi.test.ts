import { createElement, type ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}))

import { apiClient } from '@/shared/lib/axios'
import {
  useRequestLateCheckin,
  useStudentAttendanceRecords,
  useStudentExcuseTickets,
  useSubmitStudentExcuse,
} from '../studentRequestsApi'

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('studentRequestsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches student records and normalizes status casing', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        _embedded: {
          attendanceRecordEntryList: [
            {
              lessonId: 1,
              subjectId: 2,
              lessonDate: '2026-05-01',
              lessonNumber: 1,
              status: 'ABSENT',
              symbol: 'н',
              source: 'headman',
            },
          ],
        },
      },
    })

    const { result } = renderHook(() => useStudentAttendanceRecords(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(apiClient.get).toHaveBeenCalledWith('/attendance/reports/student/records')
    expect(result.current.data?.[0].status).toBe('absent')
  })

  it('returns empty excuse list on 404 for /me endpoint', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockRejectedValue({
      response: { status: 404 },
    })

    const { result } = renderHook(() => useStudentExcuseTickets(), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([])
  })

  it('posts late-checkin request for a lesson', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} })

    const { result } = renderHook(() => useRequestLateCheckin(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync(123)
    })

    expect(apiClient.post).toHaveBeenCalledWith('/attendance/late-checkin/123', {})
  })

  it('submits excuse without file as JSON request', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} })

    const { result } = renderHook(() => useSubmitStudentExcuse(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync({
        lessonIds: [1, 2],
        excuseType: 'ILLNESS',
        comment: 'болел',
        file: null,
      })
    })

    expect(apiClient.post).toHaveBeenCalledWith('/attendance/excuses', {
      lessonIds: [1, 2],
      excuseType: 'ILLNESS',
      comment: 'болел',
    })
  })

  it('submits excuse with file as multipart request', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} })
    const file = new File(['scan'], 'medical.pdf', { type: 'application/pdf' })

    const { result } = renderHook(() => useSubmitStudentExcuse(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync({
        lessonIds: [7],
        excuseType: 'OTHER',
        comment: null,
        file,
      })
    })

    expect(apiClient.post).toHaveBeenCalledWith(
      '/attendance/excuses/with-file',
      expect.any(FormData),
    )
  })
})

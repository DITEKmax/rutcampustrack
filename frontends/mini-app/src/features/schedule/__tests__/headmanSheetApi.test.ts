import { act, createElement, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

import { apiClient } from '@/shared/lib/axios'
import {
  useCancelLesson,
  useBlockLesson,
  useHeadmanMarkAttendance,
  useHeadmanMarkBatch,
  useLessonAttendance,
  useUnblockLesson,
} from '../headmanSheetApi'

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('headmanSheetApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches and normalizes lesson attendance', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: {
        lessonId: 44,
        groupId: 5,
        subjectId: 10,
        lessonDate: '2026-05-08',
        entries: [
          {
            userId: 7,
            displayName: 'Иван Иванов',
            status: 'PRESENT',
            symbol: '+',
            source: 'student_geo',
            excuseReason: null,
          },
        ],
      },
    })

    const { result } = renderHook(() => useLessonAttendance(44), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.entries[0]).toMatchObject({
      userId: 7,
      displayName: 'Иван Иванов',
      status: 'present',
      symbol: '+',
      source: 'student_geo',
    })
    expect(apiClient.get).toHaveBeenCalledWith('/attendance/reports/lesson/44')
  })

  it('marks one student in backend enum case', async () => {
    ;(apiClient.put as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })

    const { result } = renderHook(() => useHeadmanMarkAttendance(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync({
        lessonId: 44,
        userId: 7,
        status: 'excused',
      })
    })

    expect(apiClient.put).toHaveBeenCalledWith('/attendance/lessons/44/students/7', {
      status: 'EXCUSED',
    })
  })

  it('marks students by batch', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })

    const { result } = renderHook(() => useHeadmanMarkBatch(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync({
        lessonId: 44,
        items: [
          { userId: 7, status: 'present' },
          { userId: 8, status: 'absent' },
        ],
      })
    })

    expect(apiClient.post).toHaveBeenCalledWith('/attendance/marks/batch', {
      items: [
        { lessonId: 44, userId: 7, status: 'PRESENT' },
        { lessonId: 44, userId: 8, status: 'ABSENT' },
      ],
    })
  })

  it('cancels lesson with reason', async () => {
    ;(apiClient.patch as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })

    const { result } = renderHook(() => useCancelLesson(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync({
        lessonId: 44,
        reason: 'Преподаватель заболел',
      })
    })

    expect(apiClient.patch).toHaveBeenCalledWith('/schedule/lessons/44/cancel', {
      reason: 'Преподаватель заболел',
    })
  })

  it('blocks lesson through schedule blockage endpoint', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })

    const { result } = renderHook(() => useBlockLesson(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync(44)
    })

    expect(apiClient.post).toHaveBeenCalledWith('/schedule/lessons/44/blockage')
  })

  it('unblocks lesson through schedule blockage endpoint', async () => {
    ;(apiClient.delete as ReturnType<typeof vi.fn>).mockResolvedValue({ data: undefined })

    const { result } = renderHook(() => useUnblockLesson(), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.mutateAsync(44)
    })

    expect(apiClient.delete).toHaveBeenCalledWith('/schedule/lessons/44/blockage')
  })
})

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: { get: vi.fn() },
}))

import { apiClient } from '@/shared/lib/axios'
import { useSubjectName, useTodaySchedule, useWeekSchedule } from '../api'

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children)
}

describe('useTodaySchedule', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('fetches lessons sorted by lessonNumber', async () => {
    const lessons = [
      { id: 2, lessonNumber: 3, subjectId: 1, startTime: '12:00:00', endTime: '13:30:00', status: 'PLANNED', room: '201', date: '2025-01-01' },
      { id: 1, lessonNumber: 1, subjectId: 2, startTime: '09:00:00', endTime: '10:30:00', status: 'ACTIVE', room: '101', date: '2025-01-01' },
    ]
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { _embedded: { lessonResponseList: lessons } },
    })

    const { result } = renderHook(() => useTodaySchedule(5), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data![0].lessonNumber).toBe(1)
    expect(result.current.data![1].lessonNumber).toBe(3)
  })

  it('uses dateFrom=today&dateTo=today params', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { _embedded: { lessonResponseList: [] } },
    })

    const today = new Date().toISOString().split('T')[0]
    renderHook(() => useTodaySchedule(5), { wrapper: createWrapper() })

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith('/schedule/groups/5/lessons', {
        params: { dateFrom: today, dateTo: today, size: 50 },
      })
    })
  })

  it('does not fetch when groupId is undefined', () => {
    const { result } = renderHook(() => useTodaySchedule(undefined), { wrapper: createWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })

  it('returns empty array when _embedded is missing', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: {} })

    const { result } = renderHook(() => useTodaySchedule(5), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([])
  })
})

describe('useWeekSchedule', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('fetches a week range and sorts by date then lessonNumber', async () => {
    const lessons = [
      { id: 3, lessonNumber: 2, subjectId: 1, startTime: '10:40:00', endTime: '12:10:00', status: 'PLANNED', room: '201', date: '2025-01-14' },
      { id: 1, lessonNumber: 1, subjectId: 2, startTime: '09:00:00', endTime: '10:30:00', status: 'ACTIVE', room: '101', date: '2025-01-13' },
      { id: 2, lessonNumber: 3, subjectId: 3, startTime: '12:20:00', endTime: '13:50:00', status: 'PLANNED', room: '301', date: '2025-01-13' },
    ]
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { _embedded: { lessonResponseList: lessons } },
    })

    const { result } = renderHook(
      () => useWeekSchedule(5, '2025-01-13', '2025-01-18'),
      { wrapper: createWrapper() },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data?.map((lesson) => lesson.id)).toEqual([1, 2, 3])
    expect(apiClient.get).toHaveBeenCalledWith('/schedule/groups/5/lessons', {
      params: { dateFrom: '2025-01-13', dateTo: '2025-01-18', size: 100 },
    })
  })
})

describe('useSubjectName', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('fetches subject by ID', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({
      data: { id: 1, name: 'Математика' },
    })

    const { result } = renderHook(() => useSubjectName(1), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.name).toBe('Математика')
    expect(apiClient.get).toHaveBeenCalledWith('/academic/subjects/1')
  })
})

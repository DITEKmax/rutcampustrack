import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: { get: vi.fn() },
}))

import { apiClient } from '@/shared/lib/axios'
import { useStudentStats, useStudentThresholds, DEFAULT_RED_ZONE_THRESHOLD } from '../api'
import { SubjectStatsCard } from '../SubjectStatsCard'

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children)
}

describe('useStudentStats', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('fetches from /attendance/reports/student/stats', async () => {
    const mockData = {
      overall: { total: 10, attended: 8, absent: 1, excused: 1, percentage: 80 },
      subjects: [],
    }
    ;(apiClient.get as ReturnType<typeof vi.fn>).mockResolvedValue({ data: mockData })

    const { result } = renderHook(() => useStudentStats(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.get).toHaveBeenCalledWith('/attendance/reports/student/stats')
    expect(result.current.data?.overall.percentage).toBe(80)
  })
})

describe('DEFAULT_RED_ZONE_THRESHOLD', () => {
  it('equals 60', () => {
    expect(DEFAULT_RED_ZONE_THRESHOLD).toBe(60)
  })
})

describe('useStudentThresholds', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('resolves backend thresholds per subject', async () => {
    ;(apiClient.get as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ data: { minPercentage: 75, level: 'group', sourceId: 1 } })
      .mockResolvedValueOnce({ data: { minPercentage: 80, level: 'subject', sourceId: 2 } })

    const subjects = [
      { subjectId: 1, subjectName: 'Физика', total: 10, attended: 7, absent: 3, excused: 0, percentage: 70 },
      { subjectId: 2, subjectName: 'Математика', total: 10, attended: 8, absent: 2, excused: 0, percentage: 80 },
    ]

    const { result } = renderHook(() => useStudentThresholds(5, subjects), {
      wrapper: createWrapper(),
    })

    await waitFor(() => expect(result.current[1]).toBe(75))
    expect(result.current[2]).toBe(80)
    expect(apiClient.get).toHaveBeenCalledWith('/academic/thresholds/resolve', {
      params: { groupId: 5, subjectId: 1 },
    })
    expect(apiClient.get).toHaveBeenCalledWith('/academic/thresholds/resolve', {
      params: { groupId: 5, subjectId: 2 },
    })
  })
})

describe('SubjectStatsCard', () => {
  it('renders red zone indicator when percentage < 60', () => {
    const stats = { subjectId: 1, subjectName: 'Физика', total: 10, attended: 4, absent: 5, excused: 1, percentage: 40 }
    const { container } = render(createElement(SubjectStatsCard, { stats, threshold: 60 }))

    const card = container.firstElementChild as HTMLElement
    expect(card.className).toContain('border-l-4')
    expect(card.className).toContain('border-l-destructive')

    const percentage = screen.getByText('40%')
    expect(percentage.className).toContain('text-destructive')
  })

  it('renders normal indicator when percentage >= 60', () => {
    const stats = { subjectId: 2, subjectName: 'Математика', total: 10, attended: 8, absent: 1, excused: 1, percentage: 80 }
    const { container } = render(createElement(SubjectStatsCard, { stats, threshold: 60 }))

    const card = container.firstElementChild as HTMLElement
    expect(card.className).not.toContain('border-l-destructive')

    const percentage = screen.getByText('80%')
    expect(percentage.className).not.toContain('text-destructive')
  })

  it('uses backend threshold when provided', () => {
    const stats = { subjectId: 3, subjectName: 'История', total: 10, attended: 7, absent: 3, excused: 0, percentage: 70 }
    const { container } = render(createElement(SubjectStatsCard, { stats, threshold: 75 }))

    const card = container.firstElementChild as HTMLElement
    expect(card.className).toContain('border-l-destructive')
  })
})

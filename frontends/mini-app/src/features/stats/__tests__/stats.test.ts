import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: { get: vi.fn() },
}))

import { apiClient } from '@/shared/lib/axios'
import { useStudentStats, RED_ZONE_THRESHOLD } from '../api'
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

describe('RED_ZONE_THRESHOLD', () => {
  it('equals 60', () => {
    expect(RED_ZONE_THRESHOLD).toBe(60)
  })
})

describe('SubjectStatsCard', () => {
  it('renders red zone indicator when percentage < 60', () => {
    const stats = { subjectId: 1, subjectName: 'Физика', total: 10, attended: 4, absent: 5, excused: 1, percentage: 40 }
    const { container } = render(createElement(SubjectStatsCard, { stats }))

    const card = container.firstElementChild as HTMLElement
    expect(card.className).toContain('border-l-4')
    expect(card.className).toContain('border-l-destructive')

    const percentage = screen.getByText('40%')
    expect(percentage.className).toContain('text-destructive')
  })

  it('renders normal indicator when percentage >= 60', () => {
    const stats = { subjectId: 2, subjectName: 'Математика', total: 10, attended: 8, absent: 1, excused: 1, percentage: 80 }
    const { container } = render(createElement(SubjectStatsCard, { stats }))

    const card = container.firstElementChild as HTMLElement
    expect(card.className).not.toContain('border-l-destructive')

    const percentage = screen.getByText('80%')
    expect(percentage.className).not.toContain('text-destructive')
  })
})

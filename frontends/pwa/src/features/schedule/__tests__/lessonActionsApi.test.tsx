import { act, renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCancelLesson } from '../lessonActionsApi'
import { apiClient } from '@/shared/lib/axios'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    patch: vi.fn(),
  },
}))

const mockedPatch = vi.mocked(apiClient.patch)

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  const hook = renderHook(() => useCancelLesson(), { wrapper })
  return { ...hook, queryClient }
}

describe('lessonActionsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('cancels a lesson through the schedule-service endpoint with reason', async () => {
    mockedPatch.mockResolvedValueOnce({ data: { status: 'CANCELLED' } })
    const { result, queryClient } = setup()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    await act(async () => {
      await result.current.mutateAsync({
        lessonId: 77,
        reason: 'Болезнь преподавателя',
      })
    })

    expect(mockedPatch).toHaveBeenCalledWith('/schedule/lessons/77/cancel', {
      reason: 'Болезнь преподавателя',
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['schedule'] })
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['lessonAttendance', 77],
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['journal'] })
  })
})

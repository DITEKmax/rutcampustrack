import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: { post: vi.fn() },
}))

import { apiClient } from '@/shared/lib/axios'
import { useCheckin, mapCheckinError } from '../api'

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children)
}

describe('useCheckin', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('calls POST /attendance/checkin with lat/lng', async () => {
    ;(apiClient.post as ReturnType<typeof vi.fn>).mockResolvedValue({ data: { status: 'present' } })

    const { result } = renderHook(() => useCheckin(), { wrapper: createWrapper() })

    result.current.mutate({ lat: 55.75, lng: 37.62 })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.post).toHaveBeenCalledWith('/attendance/checkin', { lat: 55.75, lng: 37.62 })
  })
})

describe('mapCheckinError', () => {
  it('returns correct message for 403', () => {
    expect(mapCheckinError(403)).toBe('Вы находитесь слишком далеко от аудитории')
  })

  it('returns correct message for 404', () => {
    expect(mapCheckinError(404)).toBe('Активное занятие не найдено')
  })

  it('returns correct message for 409', () => {
    expect(mapCheckinError(409)).toBe('Вы уже отметились на этой паре')
  })

  it('returns correct message for 422', () => {
    expect(mapCheckinError(422)).toBe('Окно отметки ещё не открыто или уже закрыто')
  })

  it('returns correct message for 429', () => {
    expect(mapCheckinError(429)).toBe('Слишком много попыток. Подождите немного.')
  })

  it('returns default message for unknown status', () => {
    expect(mapCheckinError(500)).toBe('Ошибка сервера. Попробуйте ещё раз')
  })
})

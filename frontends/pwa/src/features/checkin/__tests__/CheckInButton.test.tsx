import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
  setAccessTokenGetter: vi.fn(),
  setTokenRefreshCallback: vi.fn(),
  setAuthLogoutCallback: vi.fn(),
}))

vi.mock('@/shared/hooks/useNetworkStatus', () => ({
  useNetworkStatus: () => ({ isOnline: true }),
}))

import { apiClient } from '@/shared/lib/axios'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

describe('CheckInButton', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls getCurrentPosition on click (stub)', async () => {
    const mockGetCurrentPosition = vi.fn().mockImplementation((success) => {
      success({ coords: { latitude: 55.75, longitude: 37.61 } })
    })
    Object.defineProperty(global.navigator, 'geolocation', {
      value: { getCurrentPosition: mockGetCurrentPosition, watchPosition: vi.fn(), clearWatch: vi.fn() },
      configurable: true,
    })

    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { status: 'present' } })

    // Stub: CheckInButton is not yet implemented, so we test the checkin API hook
    const { useCheckin } = await import('../../checkin/api')
    const { renderHook, act } = await import('@testing-library/react')

    const { result } = renderHook(() => useCheckin(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({ lat: 55.75, lng: 37.61 })
    })

    expect(apiClient.post).toHaveBeenCalledWith('/attendance/checkin', { lat: 55.75, lng: 37.61 })
  })

  it('mapCheckinError returns correct error messages', async () => {
    const { mapCheckinError } = await import('../../checkin/api')

    expect(mapCheckinError(403)).toBe('Геоотметка заблокирована преподавателем')
    expect(mapCheckinError(404)).toBe('Активное занятие не найдено')
    expect(mapCheckinError(409)).toBe('Вы уже отмечены на этом занятии')
    expect(mapCheckinError(422)).toBe('Вы находитесь вне зоны отметки')
    expect(mapCheckinError(429)).toBe('Слишком много попыток. Подождите минуту')
    expect(mapCheckinError(500)).toBe('Ошибка сервера. Попробуйте ещё раз')
  })
})

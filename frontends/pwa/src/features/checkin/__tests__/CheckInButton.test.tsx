import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'

// Mock axios
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

// Default: online
let mockIsOnline = true
vi.mock('@/shared/hooks/useNetworkStatus', () => ({
  useNetworkStatus: () => ({ isOnline: mockIsOnline }),
}))

// Mock useCheckin and mapCheckinError
let mockMutate: ReturnType<typeof vi.fn>
let mockMutateAsync: ReturnType<typeof vi.fn>
let mockIsPending: boolean

vi.mock('@/features/checkin/api', () => ({
  useCheckin: () => ({
    mutate: mockMutate,
    mutateAsync: mockMutateAsync,
    isPending: mockIsPending,
  }),
  mapCheckinError: (status: number) => {
    switch (status) {
      case 404: return 'Активное занятие не найдено'
      case 409: return 'Вы уже отмечены на этом занятии'
      case 422: return 'Вы находитесь вне зоны отметки'
      default: return 'Ошибка сервера. Попробуйте ещё раз'
    }
  },
  mapGeolocationError: (err: { code?: number }) => {
    switch (err?.code) {
      case 1: return 'Нет доступа к GPS. Разрешите доступ в настройках браузера'
      case 2: return 'Не удалось определить местоположение. Проверь GPS и попробуй ещё раз'
      case 3: return 'Слишком долго ищем GPS. Попробуй ещё раз в открытом пространстве'
      default: return 'Не удалось получить координаты'
    }
  },
}))

import { CheckInButton } from '../CheckInButton'
import { CheckInToast } from '../CheckInToast'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

describe('CheckInButton', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockIsOnline = true
    mockIsPending = false
    mockMutate = vi.fn()
    mockMutateAsync = vi.fn()
  })

  it('calls navigator.geolocation.getCurrentPosition on click', async () => {
    const mockGetCurrentPosition = vi.fn()
    Object.defineProperty(global.navigator, 'geolocation', {
      value: { getCurrentPosition: mockGetCurrentPosition, watchPosition: vi.fn(), clearWatch: vi.fn() },
      configurable: true,
    })

    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <CheckInButton onSuccess={vi.fn()} />
      </Wrapper>
    )

    await userEvent.click(screen.getByRole('button', { name: /отметиться/i }))
    expect(mockGetCurrentPosition).toHaveBeenCalled()
  })

  it('shows spinner during GPS/submit loading (aria-busy="true")', async () => {
    // Make getCurrentPosition not resolve immediately (simulates waiting for GPS)
    const mockGetCurrentPosition = vi.fn()
    Object.defineProperty(global.navigator, 'geolocation', {
      value: { getCurrentPosition: mockGetCurrentPosition, watchPosition: vi.fn(), clearWatch: vi.fn() },
      configurable: true,
    })

    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <CheckInButton onSuccess={vi.fn()} />
      </Wrapper>
    )

    await userEvent.click(screen.getByRole('button', { name: /отметиться/i }))

    // During GPS capture, button should be busy
    const button = screen.getByRole('button')
    expect(button).toHaveAttribute('aria-busy', 'true')
  })

  it('is disabled when offline', () => {
    mockIsOnline = false

    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <CheckInButton onSuccess={vi.fn()} />
      </Wrapper>
    )

    expect(screen.getByRole('button')).toBeDisabled()
  })

  it('on successful check-in, onSuccess callback is called', async () => {
    const onSuccess = vi.fn()
    const mockGetCurrentPosition = vi.fn().mockImplementation((success) => {
      success({ coords: { latitude: 55.7558, longitude: 37.6173 } })
    })
    Object.defineProperty(global.navigator, 'geolocation', {
      value: { getCurrentPosition: mockGetCurrentPosition, watchPosition: vi.fn(), clearWatch: vi.fn() },
      configurable: true,
    })

    mockMutate.mockImplementation((_data: unknown, options: { onSuccess?: () => void }) => {
      options?.onSuccess?.()
    })

    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <CheckInButton onSuccess={onSuccess} />
      </Wrapper>
    )

    await userEvent.click(screen.getByRole('button', { name: /отметиться/i }))

    expect(mockMutate).toHaveBeenCalledWith(
      { lat: 55.7558, lng: 37.6173 },
      expect.objectContaining({ onSuccess: expect.any(Function) })
    )
    expect(onSuccess).toHaveBeenCalled()
  })

  it('on GPS error, shows failure via onError with exact D-09 text', async () => {
    const onError = vi.fn()
    const mockGetCurrentPosition = vi.fn().mockImplementation((_success, error) => {
      error({ code: 1, message: 'User denied' }) // PERMISSION_DENIED
    })
    Object.defineProperty(global.navigator, 'geolocation', {
      value: { getCurrentPosition: mockGetCurrentPosition, watchPosition: vi.fn(), clearWatch: vi.fn() },
      configurable: true,
    })

    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <CheckInButton onSuccess={vi.fn()} onError={onError} />
      </Wrapper>
    )

    await userEvent.click(screen.getByRole('button', { name: /отметиться/i }))

    expect(onError).toHaveBeenCalledWith('Нет доступа к GPS. Разрешите доступ в настройках браузера')
  })

  it('on API 409 error, calls onError with "Вы уже отмечены на этом занятии"', async () => {
    const onError = vi.fn()
    const mockGetCurrentPosition = vi.fn().mockImplementation((success) => {
      success({ coords: { latitude: 55.7558, longitude: 37.6173 } })
    })
    Object.defineProperty(global.navigator, 'geolocation', {
      value: { getCurrentPosition: mockGetCurrentPosition, watchPosition: vi.fn(), clearWatch: vi.fn() },
      configurable: true,
    })

    mockMutate.mockImplementation((_data: unknown, options: { onError?: (err: unknown) => void }) => {
      options?.onError?.({ response: { status: 409 } })
    })

    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <CheckInButton onSuccess={vi.fn()} onError={onError} />
      </Wrapper>
    )

    await userEvent.click(screen.getByRole('button', { name: /отметиться/i }))
    expect(onError).toHaveBeenCalledWith('Вы уже отмечены на этом занятии')
  })

  it('on API 422 error, calls onError with "Вы находитесь вне зоны отметки"', async () => {
    const onError = vi.fn()
    const mockGetCurrentPosition = vi.fn().mockImplementation((success) => {
      success({ coords: { latitude: 55.7558, longitude: 37.6173 } })
    })
    Object.defineProperty(global.navigator, 'geolocation', {
      value: { getCurrentPosition: mockGetCurrentPosition, watchPosition: vi.fn(), clearWatch: vi.fn() },
      configurable: true,
    })

    mockMutate.mockImplementation((_data: unknown, options: { onError?: (err: unknown) => void }) => {
      options?.onError?.({ response: { status: 422 } })
    })

    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <CheckInButton onSuccess={vi.fn()} onError={onError} />
      </Wrapper>
    )

    await userEvent.click(screen.getByRole('button', { name: /отметиться/i }))
    expect(onError).toHaveBeenCalledWith('Вы находитесь вне зоны отметки')
  })

  it('on API 404 error, calls onError with "Активное занятие не найдено"', async () => {
    const onError = vi.fn()
    const mockGetCurrentPosition = vi.fn().mockImplementation((success) => {
      success({ coords: { latitude: 55.7558, longitude: 37.6173 } })
    })
    Object.defineProperty(global.navigator, 'geolocation', {
      value: { getCurrentPosition: mockGetCurrentPosition, watchPosition: vi.fn(), clearWatch: vi.fn() },
      configurable: true,
    })

    mockMutate.mockImplementation((_data: unknown, options: { onError?: (err: unknown) => void }) => {
      options?.onError?.({ response: { status: 404 } })
    })

    const Wrapper = createWrapper()
    render(
      <Wrapper>
        <CheckInButton onSuccess={vi.fn()} onError={onError} />
      </Wrapper>
    )

    await userEvent.click(screen.getByRole('button', { name: /отметиться/i }))
    expect(onError).toHaveBeenCalledWith('Активное занятие не найдено')
  })
})

describe('CheckInToast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('auto-dismisses success after 3000ms', () => {
    const onDismiss = vi.fn()
    render(<CheckInToast type="success" message="Отметка принята" onDismiss={onDismiss} />)

    expect(onDismiss).not.toHaveBeenCalled()

    act(() => {
      vi.advanceTimersByTime(3000)
    })

    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('auto-dismisses failure after 5000ms', () => {
    const onDismiss = vi.fn()
    render(<CheckInToast type="error" message="Ошибка" onDismiss={onDismiss} />)

    act(() => {
      vi.advanceTimersByTime(3000)
    })
    expect(onDismiss).not.toHaveBeenCalled()

    act(() => {
      vi.advanceTimersByTime(2000)
    })
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })
})

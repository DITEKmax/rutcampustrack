import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  CheckinLocationError,
  getCheckinLocation,
  isBrowserLocationFallbackEnabled,
  mapLocationError,
} from '../location'

type TelegramLocationCallback = (location: { latitude?: number; longitude?: number; lat?: number; lng?: number } | null) => void

function setTelegramWebApp(webApp: unknown) {
  Object.defineProperty(window, 'Telegram', {
    configurable: true,
    value: { WebApp: webApp },
  })
}

function setBrowserGeolocation(geolocation: unknown) {
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: geolocation,
  })
}

describe('getCheckinLocation', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_TMA_DEV', 'false')
    Reflect.deleteProperty(window, 'Telegram')
    Reflect.deleteProperty(navigator, 'geolocation')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.useRealTimers()
    Reflect.deleteProperty(window, 'Telegram')
    Reflect.deleteProperty(navigator, 'geolocation')
  })

  it('uses Telegram WebApp.requestLocation when available', async () => {
    const requestLocation = vi.fn((callback: TelegramLocationCallback) => {
      callback({ latitude: 55.7522, longitude: 37.6156 })
    })
    setTelegramWebApp({ requestLocation })

    await expect(getCheckinLocation()).resolves.toEqual({
      lat: 55.7522,
      lng: 37.6156,
      source: 'telegram',
    })
    expect(requestLocation).toHaveBeenCalledTimes(1)
  })

  it('uses Telegram LocationManager after init when requestLocation is absent', async () => {
    const init = vi.fn((callback?: () => void) => callback?.())
    const getLocation = vi.fn((callback: TelegramLocationCallback) => {
      callback({ lat: 55.75, lng: 37.61 })
    })
    setTelegramWebApp({ LocationManager: { init, isInited: false, getLocation } })

    await expect(getCheckinLocation()).resolves.toEqual({
      lat: 55.75,
      lng: 37.61,
      source: 'telegram',
    })
    expect(init).toHaveBeenCalledTimes(1)
    expect(getLocation).toHaveBeenCalledTimes(1)
  })

  it('uses browser geolocation when Telegram location API is absent', async () => {
    const getCurrentPosition = vi.fn((success: PositionCallback) => {
      success({
        coords: {
          latitude: 55.76,
          longitude: 37.64,
          accuracy: 10,
          altitude: null,
          altitudeAccuracy: null,
          heading: null,
          speed: null,
          toJSON: () => ({}),
        },
        timestamp: 1,
        toJSON: () => ({}),
      })
    })
    setBrowserGeolocation({ getCurrentPosition })

    await expect(getCheckinLocation()).resolves.toEqual({
      lat: 55.76,
      lng: 37.64,
      source: 'browser',
    })
    expect(isBrowserLocationFallbackEnabled()).toBe(true)
    expect(getCurrentPosition).toHaveBeenCalledTimes(1)
  })

  it('uses browser geolocation when VITE_TMA_DEV enables fallback', async () => {
    vi.stubEnv('VITE_TMA_DEV', 'true')
    setTelegramWebApp({ requestLocation: (callback: TelegramLocationCallback) => callback(null) })
    const getCurrentPosition = vi.fn(
      (
        success: PositionCallback,
        _error?: PositionErrorCallback | null,
        options?: PositionOptions,
      ) => {
        success({
          coords: {
            latitude: 55.76,
            longitude: 37.64,
            accuracy: 10,
            altitude: null,
            altitudeAccuracy: null,
            heading: null,
            speed: null,
            toJSON: () => ({}),
          },
          timestamp: 1,
          toJSON: () => ({}),
        })
        expect(options).toMatchObject({
          enableHighAccuracy: true,
          timeout: 15_000,
          maximumAge: 15_000,
        })
      },
    )
    setBrowserGeolocation({ getCurrentPosition })

    await expect(getCheckinLocation()).resolves.toEqual({
      lat: 55.76,
      lng: 37.64,
      source: 'browser',
    })
    expect(isBrowserLocationFallbackEnabled()).toBe(true)
    expect(getCurrentPosition).toHaveBeenCalledTimes(1)
  })

  it('keeps Telegram denial in production when Telegram location API exists', async () => {
    setTelegramWebApp({ requestLocation: (callback: TelegramLocationCallback) => callback(null) })
    setBrowserGeolocation({
      getCurrentPosition: vi.fn(),
    })

    await expect(getCheckinLocation()).rejects.toMatchObject({
      code: 'denied',
    })
    expect(isBrowserLocationFallbackEnabled()).toBe(false)
  })

  it('falls back to browser geolocation in dev when Telegram denies location', async () => {
    vi.stubEnv('VITE_TMA_DEV', 'true')
    setTelegramWebApp({ requestLocation: (callback: TelegramLocationCallback) => callback(null) })
    setBrowserGeolocation({
      getCurrentPosition: (success: PositionCallback) => {
        success({
          coords: {
            latitude: 55.77,
            longitude: 37.65,
            accuracy: 10,
            altitude: null,
            altitudeAccuracy: null,
            heading: null,
            speed: null,
            toJSON: () => ({}),
          },
          timestamp: 1,
          toJSON: () => ({}),
        })
      },
    })

    await expect(getCheckinLocation()).resolves.toEqual({
      lat: 55.77,
      lng: 37.65,
      source: 'browser',
    })
  })

  it('maps Telegram denial to a user-facing location error', () => {
    const error = new CheckinLocationError('denied', 'Разрешите доступ к геолокации в Telegram.')

    expect(mapLocationError(error)).toBe('Разрешите доступ к геолокации в Telegram.')
  })

  it('maps browser geolocation error codes', () => {
    expect(mapLocationError({ code: 1 })).toContain('Разрешите доступ')
    expect(mapLocationError({ code: 2 })).toContain('Проверьте GPS')
    expect(mapLocationError({ code: 3 })).toContain('Слишком долго')
  })
})

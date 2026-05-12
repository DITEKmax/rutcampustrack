import type { CheckinRequest } from './types'

type LocationSource = 'telegram' | 'browser'
type LocationErrorCode = 'denied' | 'timeout' | 'unavailable' | 'unsupported'

interface TelegramLocationData {
  latitude?: number
  longitude?: number
  lat?: number
  lng?: number
}

interface TelegramLocationManager {
  init?: (callback?: () => void) => void
  getLocation?: (callback: (location: TelegramLocationData | null) => void) => void
  isInited?: boolean
}

interface TelegramWebApp {
  requestLocation?: (callback: (location: TelegramLocationData | null) => void) => void
  LocationManager?: TelegramLocationManager
}

declare global {
  interface Window {
    Telegram?: {
      WebApp?: TelegramWebApp
    }
  }
}

export class CheckinLocationError extends Error {
  constructor(
    readonly code: LocationErrorCode,
    message: string,
  ) {
    super(message)
    this.name = 'CheckinLocationError'
  }
}

export interface CheckinLocationResult extends CheckinRequest {
  source: LocationSource
}

const LOCATION_TIMEOUT_MS = 15_000

export function isBrowserLocationFallbackEnabled(): boolean {
  return import.meta.env.VITE_TMA_DEV === 'true' || !hasTelegramLocationApi()
}

export async function getCheckinLocation(): Promise<CheckinLocationResult> {
  try {
    const telegramLocation = await getTelegramLocation()
    if (telegramLocation) return telegramLocation
  } catch (error) {
    if (!isBrowserLocationFallbackEnabled()) throw error
  }

  if (isBrowserLocationFallbackEnabled()) {
    return getBrowserLocation()
  }

  throw new CheckinLocationError(
    'unsupported',
    'Устройство не поддерживает геолокацию.',
  )
}

export function mapLocationError(error: unknown): string {
  if (error instanceof CheckinLocationError) return error.message
  const code = (error as { code?: number })?.code
  switch (code) {
    case 1:
      return 'Нет доступа к GPS. Разрешите доступ к геолокации.'
    case 2:
      return 'Не удалось определить местоположение. Проверьте GPS и попробуйте еще раз.'
    case 3:
      return 'Слишком долго ищем GPS. Попробуйте еще раз в открытом пространстве.'
    default:
      return 'Не удалось получить координаты.'
  }
}

async function getTelegramLocation(): Promise<CheckinLocationResult | null> {
  const webApp = window.Telegram?.WebApp
  if (!webApp) return null

  if (typeof webApp.requestLocation === 'function') {
    return requestTelegramLocation(webApp.requestLocation)
  }

  const manager = webApp.LocationManager
  if (manager?.getLocation) {
    if (!manager.isInited && manager.init) {
      await new Promise<void>((resolve) => manager.init?.(resolve))
    }
    return requestTelegramLocation(manager.getLocation.bind(manager))
  }

  return null
}

function hasTelegramLocationApi(): boolean {
  const webApp = window.Telegram?.WebApp
  return (
    typeof webApp?.requestLocation === 'function' ||
    typeof webApp?.LocationManager?.getLocation === 'function'
  )
}

function requestTelegramLocation(
  request: (callback: (location: TelegramLocationData | null) => void) => void,
): Promise<CheckinLocationResult> {
  return new Promise((resolve, reject) => {
    const timeoutId = window.setTimeout(() => {
      reject(new CheckinLocationError('timeout', 'Telegram слишком долго запрашивает геолокацию.'))
    }, LOCATION_TIMEOUT_MS)

    try {
      request((location) => {
        window.clearTimeout(timeoutId)
        const coords = normalizeTelegramLocation(location)
        if (!coords) {
          reject(new CheckinLocationError('denied', 'Разрешите доступ к геолокации в Telegram.'))
          return
        }
        resolve({ ...coords, source: 'telegram' })
      })
    } catch {
      window.clearTimeout(timeoutId)
      reject(new CheckinLocationError('unavailable', 'Telegram location API недоступен.'))
    }
  })
}

function normalizeTelegramLocation(location: TelegramLocationData | null): CheckinRequest | null {
  if (!location) return null
  const lat = location.latitude ?? location.lat
  const lng = location.longitude ?? location.lng
  if (typeof lat !== 'number' || typeof lng !== 'number') return null
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null
  return { lat, lng }
}

function getBrowserLocation(): Promise<CheckinLocationResult> {
  if (!('geolocation' in navigator)) {
    throw new CheckinLocationError('unsupported', 'Устройство не поддерживает геолокацию.')
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          lat: position.coords.latitude,
          lng: position.coords.longitude,
          source: 'browser',
        })
      },
      (error) => reject(error),
      { enableHighAccuracy: true, timeout: LOCATION_TIMEOUT_MS, maximumAge: 15_000 },
    )
  })
}

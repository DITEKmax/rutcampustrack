import type { StudentCheckinCommand } from '@rct/mobile-core'

interface TelegramLocation { latitude: number; longitude: number }
interface TelegramWebApp {
  initData: string
  ready(): void
  expand(): void
  viewportHeight: number
  safeAreaInset?: { top: number; right: number; bottom: number; left: number }
  themeParams?: Record<string, string>
  LocationManager?: { init(callback: () => void): void; getLocation(callback: (location: TelegramLocation | null) => void): void }
}

declare global { interface Window { Telegram?: { WebApp: TelegramWebApp } } }

export function installFixtureTelegramHost(): void {
  if (window.Telegram?.WebApp?.initData) return
  window.Telegram = { WebApp: {
    initData: 'fixture-signed-init-data',
    ready: () => undefined,
    expand: () => undefined,
    viewportHeight: window.innerHeight,
    safeAreaInset: { top: 0, right: 0, bottom: 0, left: 0 },
    LocationManager: {
      init: (callback) => callback(),
      getLocation: (callback) => callback({ latitude: 55.7539, longitude: 37.6208 }),
    },
  } }
}

export class TelegramHost {
  readonly app: TelegramWebApp | null = window.Telegram?.WebApp ?? null

  start(): string | null {
    if (!this.app?.initData) return null
    this.app.ready()
    this.app.expand()
    document.documentElement.style.setProperty('--rct-host-viewport', `${this.app.viewportHeight}px`)
    if (this.app.safeAreaInset) document.documentElement.style.setProperty('--rct-host-safe-bottom', `${this.app.safeAreaInset.bottom}px`)
    return this.app.initData
  }

  location(): Promise<StudentCheckinCommand> {
    return new Promise((resolve) => {
      if (!this.app?.LocationManager) { resolve({ geo: { kind: 'UNAVAILABLE', reason: 'POSITION_UNAVAILABLE' } }); return }
      this.app.LocationManager.init(() => this.app?.LocationManager?.getLocation((location) => {
        resolve(location ? { geo: { kind: 'COORDINATES', latitude: location.latitude, longitude: location.longitude } } : { geo: { kind: 'UNAVAILABLE', reason: 'POSITION_UNAVAILABLE' } })
      }))
    })
  }
}

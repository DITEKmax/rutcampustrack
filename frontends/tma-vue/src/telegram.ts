import type { SystemThemeSource } from '../../shared/foundation'
import type { MobileHostAdapter, MobileHostAction } from '@rct/mobile-core'
import type { TelegramDownloadFileParams } from './report-download-adapter'
import { browserGeoSource, type GeoSource } from '../../mobile-core/src/device-access/geolocation'
import {
  inspectBrowserPermission,
  type DevicePlatform,
  type DevicePermission,
} from '../../mobile-core/src/device-access/device-access'

interface TelegramLocation {
  latitude: number
  longitude: number
  horizontal_accuracy?: number | null
}

interface TelegramButton {
  show?: () => void
  hide?: () => void
  enable?: () => void
  disable?: () => void
  setText?: (text: string) => void
  onClick?: (callback: () => void) => void
  offClick?: (callback: () => void) => void
}

interface TelegramWebApp {
  version?: string
  platform?: string
  isVersionAtLeast?: (version: string) => boolean
  showScanQrPopup?: (params: { text?: string }, callback: (value: string) => boolean) => void
  closeScanQrPopup?: () => void
  initData: string
  close?: () => void
  ready(): void
  expand(): void
  viewportHeight: number
  viewportStableHeight?: number
  safeAreaInset?: { top: number; right: number; bottom: number; left: number }
  colorScheme?: 'light' | 'dark'
  themeParams?: Record<string, string>
  BackButton?: TelegramButton
  MainButton?: TelegramButton
  onEvent?: (event: string, callback: () => void) => void
  offEvent?: (event: string, callback: () => void) => void
  openLink?: (url: string) => void
  downloadFile?: (params: TelegramDownloadFileParams, callback: (accepted: boolean) => void) => void
  LocationManager?: {
    isInited?: boolean
    isLocationAvailable?: boolean
    isAccessRequested?: boolean
    isAccessGranted?: boolean
    openSettings?: () => void
    init(callback: () => void): void
    getLocation(callback: (location: TelegramLocation | null) => void): void
  }
}

declare global {
  interface Window {
    Telegram?: { WebApp: TelegramWebApp }
  }
}

export function installFixtureTelegramHost(): void {
  if (window.Telegram?.WebApp?.initData) return
  const listeners = new Map<string, Set<() => void>>()
  const backButton: TelegramButton = {
    show: () => undefined,
    hide: () => undefined,
    onClick: (callback) => {
      const entries = listeners.get('backButtonClicked') ?? new Set<() => void>()
      entries.add(callback)
      listeners.set('backButtonClicked', entries)
    },
    offClick: (callback) => listeners.get('backButtonClicked')?.delete(callback),
  }
  const mainButton: TelegramButton = {
    show: () => undefined,
    hide: () => undefined,
    enable: () => undefined,
    disable: () => undefined,
    setText: () => undefined,
    onClick: (callback) => {
      const entries = listeners.get('mainButtonClicked') ?? new Set<() => void>()
      entries.add(callback)
      listeners.set('mainButtonClicked', entries)
    },
    offClick: (callback) => listeners.get('mainButtonClicked')?.delete(callback),
  }
  window.Telegram = {
    WebApp: {
      version: '8.0',
      initData: 'fixture-signed-init-data',
      ready: () => undefined,
      expand: () => undefined,
      viewportHeight: window.innerHeight,
      viewportStableHeight: window.innerHeight,
      safeAreaInset: { top: 0, right: 0, bottom: 0, left: 0 },
      BackButton: backButton,
      MainButton: mainButton,
      onEvent: (event, callback) => {
        const entries = listeners.get(event) ?? new Set<() => void>()
        entries.add(callback)
        listeners.set(event, entries)
      },
      offEvent: (event, callback) => listeners.get(event)?.delete(callback),
      LocationManager: {
        isInited: true,
        isLocationAvailable: true,
        isAccessRequested: true,
        isAccessGranted: true,
        init: (callback) => callback(),
        getLocation: (callback) => callback({ latitude: 55.7539, longitude: 37.6208 }),
      },
    },
  }
}

export class TelegramHost implements MobileHostAdapter {
  readonly backOwner = 'host' as const
  readonly primaryActionOwner = 'host' as const
  readonly app: TelegramWebApp | null = window.Telegram?.WebApp ?? null
  private primaryActionHandler: (() => void) | null = null
  private backRequestedVisible = false
  private backSuspensions = 0
  private backClaim: object | null = null

  close(): void {
    this.app?.close?.()
  }

  start(): string | null {
    if (!this.app?.initData) return null
    this.app.ready()
    this.app.expand()
    if (typeof document !== 'undefined') {
      const stableHeight = this.app.viewportStableHeight ?? this.app.viewportHeight
      document.documentElement.style.setProperty('--rct-host-viewport', `${stableHeight}px`)
      if (this.app.safeAreaInset)
        document.documentElement.style.setProperty(
          '--rct-host-safe-bottom',
          `${this.app.safeAreaInset.bottom}px`,
        )
    }
    return this.app.initData
  }

  subscribeBack(listener: () => void): () => void {
    const guardedListener = (): void => {
      if (this.backSuspensions > 0 || this.backClaim) return
      listener()
    }
    return this.attachBack(guardedListener)
  }

  private attachBack(listener: () => void): () => void {
    const button = this.app?.BackButton
    if (button?.onClick) {
      button.onClick(listener)
      return () => button.offClick?.(listener)
    }
    if (this.app?.onEvent) {
      this.app.onEvent('backButtonClicked', listener)
      return () => this.app?.offEvent?.('backButtonClicked', listener)
    }
    return () => undefined
  }

  claimBack(listener: () => void): () => void {
    if (this.backClaim) throw new Error('Владелец кнопки Назад уже назначен')
    const claim = {}
    this.backClaim = claim
    const stop = this.attachBack(() => {
      if (this.backClaim === claim && this.backSuspensions === 0) listener()
    })
    this.applyBackVisibility()
    let released = false
    return () => {
      if (released) return
      released = true
      stop()
      if (this.backClaim === claim) this.backClaim = null
      this.applyBackVisibility()
    }
  }

  setBackVisible(visible: boolean): void {
    this.backRequestedVisible = visible
    this.applyBackVisibility()
  }

  suspendBack(): () => void {
    this.backSuspensions += 1
    this.applyBackVisibility()
    let released = false
    return () => {
      if (released) return
      released = true
      this.backSuspensions = Math.max(0, this.backSuspensions - 1)
      this.applyBackVisibility()
    }
  }

  private applyBackVisibility(): void {
    if (this.backSuspensions > 0 || (!this.backClaim && !this.backRequestedVisible))
      this.app?.BackButton?.hide?.()
    else this.app?.BackButton?.show?.()
  }

  subscribeKeyboard(listener: (visible: boolean) => void): () => void {
    const notify = (): void => {
      const current = this.app?.viewportHeight ?? window.innerHeight
      const stable = this.app?.viewportStableHeight ?? window.innerHeight
      if (typeof document !== 'undefined') {
        document.documentElement.style.setProperty('--rct-host-viewport', `${stable}px`)
      }
      listener(current < stable - 80)
    }
    const event = 'viewportChanged'
    this.app?.onEvent?.(event, notify)
    notify()
    return () => this.app?.offEvent?.(event, notify)
  }

  setPrimaryAction(action: MobileHostAction | null): void {
    const button = this.app?.MainButton
    if (!button) return
    if (this.primaryActionHandler) button.offClick?.(this.primaryActionHandler)
    this.primaryActionHandler = null
    if (!action) {
      button.hide?.()
      return
    }
    button.setText?.(action.label)
    if (action.disabled) button.disable?.()
    else button.enable?.()
    const handler = (): void => {
      void action.onInvoke()
    }
    this.primaryActionHandler = handler
    button.onClick?.(handler)
    button.show?.()
  }

  openLink(url: string): void {
    if (this.app?.openLink) {
      // Telegram's opener must run in the same user-interaction turn.
      this.app.openLink(url)
      return
    }
    window.open(url, '_blank', 'noopener,noreferrer')
  }

  supportsFileDownload(): boolean {
    return typeof this.app?.downloadFile === 'function'
  }

  requestFileDownload(
    params: TelegramDownloadFileParams,
    callback: (accepted: boolean) => void,
  ): boolean {
    const downloadFile = this.app?.downloadFile
    if (typeof downloadFile !== 'function') return false
    downloadFile.call(this.app, { url: params.url, file_name: params.file_name }, callback)
    return true
  }

  devicePlatform(): DevicePlatform {
    const platform = telegramDevicePlatform(this.app)
    // Initialize the capability flags before the first check-in. This never prompts.
    void platform.prepareGeo?.()
    return {
      ...platform,
      openBrowserApp: () => this.openLink(new URL('/app/', window.location.origin).href),
    }
  }
}

function supports(app: TelegramWebApp | null, required: string): boolean {
  if (app?.isVersionAtLeast) return app.isVersionAtLeast(required)
  const actual = (app?.version ?? '0').split('.').map(Number)
  const minimum = required.split('.').map(Number)
  return (
    (actual[0] ?? 0) > (minimum[0] ?? 0) ||
    ((actual[0] ?? 0) === minimum[0] && (actual[1] ?? 0) >= (minimum[1] ?? 0))
  )
}

export function telegramDevicePlatform(app: TelegramWebApp | null): DevicePlatform {
  const candidate = supports(app, '8.0') ? app?.LocationManager : undefined
  const manager =
    typeof candidate?.init === 'function' && typeof candidate.getLocation === 'function'
      ? candidate
      : undefined
  const nativeQr =
    app?.platform !== 'tdesktop' &&
    supports(app, '6.4') &&
    app?.showScanQrPopup &&
    app.closeScanQrPopup
  const browserGeo = browserGeoSource()
  let nativeDenialObserved = false
  let nativeNullObserved = false
  let nativeRequest: object | null = null
  const nativeDenied = () => {
    if (manager?.isAccessGranted === true) {
      nativeDenialObserved = false
      nativeNullObserved = false
    } else if (manager?.isAccessRequested === true) nativeDenialObserved = true
    return nativeDenialObserved
  }
  // Unavailable clients can omit both access flags. Never switch sources after a known denial.
  const browserFallback = () =>
    !nativeDenied() &&
    !nativeNullObserved &&
    !nativeRequest &&
    manager?.isInited === true &&
    manager.isLocationAvailable === false &&
    manager.isAccessGranted !== true
  const prepareGeo = (signal?: AbortSignal, budgetMs = 3_000) => {
    if (!manager || manager.isInited) return
    return new Promise<void>((resolve) => {
      const finish = () => {
        clearTimeout(timer)
        signal?.removeEventListener('abort', finish)
        resolve()
      }
      const timer = setTimeout(finish, budgetMs)
      signal?.addEventListener('abort', finish, { once: true })
      if (signal?.aborted) return finish()
      try {
        manager.init(finish)
      } catch {
        finish()
      }
    })
  }
  const geo: GeoSource = !manager
    ? browserGeo
    : {
        source: 'TMA',
        watch(sample, failed) {
          if (!manager.isInited) {
            failed('TIMEOUT')
            return () => {}
          }
          let cancelled = false
          let next: ReturnType<typeof setTimeout> | undefined
          const request = () => {
            if (cancelled) return
            if (manager.isLocationAvailable === false) {
              failed(nativeDenied() ? 'PERMISSION_DENIED' : 'UNSUPPORTED')
              return
            }
            // The SDK sends one response to all pending callbacks. Keep an unanswered
            // request reserved after local cancellation until the bridge responds.
            if (nativeRequest) {
              failed('POSITION_UNAVAILABLE')
              return
            }
            const requestToken = {}
            nativeRequest = requestToken
            try {
              manager.getLocation((location) => {
                if (nativeRequest !== requestToken) return
                nativeRequest = null
                if (location) {
                  nativeDenialObserved = false
                  nativeNullObserved = false
                } else {
                  nativeDenied()
                  if (manager.isAccessGranted !== true) nativeNullObserved = true
                }
                if (cancelled) return
                if (!location) {
                  if (nativeDenied()) failed('PERMISSION_DENIED')
                  else if (manager.isLocationAvailable === false) failed('UNSUPPORTED')
                  else
                    failed(manager.isAccessGranted ? 'POSITION_UNAVAILABLE' : 'PERMISSION_DENIED')
                  return
                }
                sample({
                  latitude: location.latitude,
                  longitude: location.longitude,
                  accuracy: location.horizontal_accuracy ?? null,
                  timestamp: null,
                  receivedAt: Date.now(),
                })
                // Never overlap native getLocation calls; the shared owner supplies the deadline.
                if (!cancelled) next = setTimeout(request, 1_500)
              })
            } catch {
              if (nativeRequest === requestToken) nativeRequest = null
              failed('POSITION_UNAVAILABLE')
            }
          }
          request()
          return () => {
            cancelled = true
            clearTimeout(next)
          }
        },
      }
  return {
    geo,
    ...(manager ? { selectGeo: () => (browserFallback() ? browserGeo : geo), prepareGeo } : {}),
    async inspect(kind, signal): Promise<DevicePermission> {
      if (kind === 'camera' || !manager) return inspectBrowserPermission(kind, signal)
      const preparation = prepareGeo(signal)
      if (preparation) await preparation
      if (signal?.aborted || !manager.isInited) return 'unknown'
      if (browserFallback()) return inspectBrowserPermission(kind, signal)
      if (nativeDenied()) return 'denied'
      if (manager.isLocationAvailable === false) return 'unsupported'
      if (manager.isAccessGranted) return 'granted'
      return manager.isAccessRequested === false ? 'prompt' : 'unknown'
    },
    ...(manager?.openSettings
      ? {
          openLocationSettings() {
            if (
              !manager.isInited ||
              !manager.isLocationAvailable ||
              manager.isAccessRequested !== true ||
              manager.isAccessGranted === true
            )
              return false
            manager.openSettings?.()
            return true
          },
        }
      : {}),
    onDeviceReturn(listener) {
      app?.onEvent?.('activated', listener)
      app?.onEvent?.('locationManagerUpdated', listener)
      return () => {
        app?.offEvent?.('activated', listener)
        app?.offEvent?.('locationManagerUpdated', listener)
      }
    },
    ...(nativeQr
      ? {
          scanQr(
            scanned: (value: string) => void,
            closed: () => void,
            failed: (error: unknown) => void,
          ) {
            let done = false
            const stop = () => {
              if (done) return
              done = true
              clearTimeout(timer)
              if (supports(app, '7.7')) app?.offEvent?.('scanQrPopupClosed', onClose)
              app?.closeScanQrPopup?.()
            }
            const onClose = () => {
              if (!done) {
                stop()
                closed()
              }
            }
            const timer = setTimeout(() => {
              stop()
              failed(new DOMException('Сканирование не завершено', 'TimeoutError'))
            }, 60_000)
            if (supports(app, '7.7')) app?.onEvent?.('scanQrPopupClosed', onClose)
            try {
              app?.showScanQrPopup?.({ text: 'QR для входа в браузере' }, (value) => {
                if (!done) {
                  stop()
                  scanned(value)
                }
                return true
              })
            } catch (error) {
              stop()
              failed(error)
            }
            return stop
          },
        }
      : {}),
  }
}

/** Telegram's active appearance is the system source inside the mini app. */
export function createTelegramSystemThemeSource(app: TelegramWebApp | null): SystemThemeSource {
  return {
    read: () => (app?.colorScheme === 'dark' ? 'dark' : 'light'),
    subscribe(listener) {
      app?.onEvent?.('themeChanged', listener)
      return () => app?.offEvent?.('themeChanged', listener)
    },
  }
}

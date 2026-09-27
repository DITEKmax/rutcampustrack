import type { MobileHostAdapter, MobileHostAction, StudentCheckinCommand } from '@rct/mobile-core'
import type { TelegramDownloadFileParams } from './report-download-adapter'

interface TelegramLocation { latitude: number; longitude: number }

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
  initData: string
  close?: () => void
  ready(): void
  expand(): void
  viewportHeight: number
  viewportStableHeight?: number
  safeAreaInset?: { top: number; right: number; bottom: number; left: number }
  themeParams?: Record<string, string>
  BackButton?: TelegramButton
  MainButton?: TelegramButton
  onEvent?: (event: string, callback: () => void) => void
  offEvent?: (event: string, callback: () => void) => void
  openLink?: (url: string) => void
  downloadFile?: (params: TelegramDownloadFileParams, callback: (accepted: boolean) => void) => void
  LocationManager?: { init(callback: () => void): void; getLocation(callback: (location: TelegramLocation | null) => void): void }
}

declare global { interface Window { Telegram?: { WebApp: TelegramWebApp } } }

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
  window.Telegram = { WebApp: {
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
      init: (callback) => callback(),
      getLocation: (callback) => callback({ latitude: 55.7539, longitude: 37.6208 }),
    },
  } }
}

export class TelegramHost implements MobileHostAdapter {
  readonly backOwner = 'host' as const
  readonly primaryActionOwner = 'host' as const
  readonly app: TelegramWebApp | null = window.Telegram?.WebApp ?? null
  private primaryActionHandler: (() => void) | null = null
  private backRequestedVisible = false
  private backSuspensions = 0

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
      if (this.app.safeAreaInset) document.documentElement.style.setProperty('--rct-host-safe-bottom', `${this.app.safeAreaInset.bottom}px`)
    }
    return this.app.initData
  }

  subscribeBack(listener: () => void): () => void {
    const button = this.app?.BackButton
    const guardedListener = (): void => {
      if (this.backSuspensions > 0) return
      listener()
    }
    if (button?.onClick) {
      button.onClick(guardedListener)
      return () => button.offClick?.(guardedListener)
    }
    if (this.app?.onEvent) {
      this.app.onEvent('backButtonClicked', guardedListener)
      return () => this.app?.offEvent?.('backButtonClicked', guardedListener)
    }
    return () => undefined
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
    if (this.backSuspensions > 0 || !this.backRequestedVisible) this.app?.BackButton?.hide?.()
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
    const handler = (): void => { void action.onInvoke() }
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

  location(): Promise<StudentCheckinCommand> {
    return new Promise((resolve) => {
      if (!this.app?.LocationManager) {
        resolve({ geo: { kind: 'UNAVAILABLE', reason: 'POSITION_UNAVAILABLE' } })
        return
      }
      this.app.LocationManager.init(() => this.app?.LocationManager?.getLocation((location) => {
        resolve(location
          ? { geo: { kind: 'COORDINATES', latitude: location.latitude, longitude: location.longitude } }
          : { geo: { kind: 'UNAVAILABLE', reason: 'POSITION_UNAVAILABLE' } })
      }))
    })
  }
}

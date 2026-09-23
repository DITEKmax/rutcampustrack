export interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

export type InstallPromptOutcome = 'accepted' | 'dismissed' | 'unavailable'

export interface InstallPromptState {
  readonly canInstall: boolean
  readonly dismissed: boolean
  readonly installed: boolean
  readonly ios: boolean
  readonly standalone: boolean
}

export interface InstallPromptController {
  getState(): InstallPromptState
  subscribe(listener: (state: InstallPromptState) => void): () => void
  install(): Promise<InstallPromptOutcome>
  dismiss(): void
  dispose(): void
}

export const INSTALL_DISMISSED_STORAGE_KEY = 'rct:pwa-install-dismissed:v1'

function isIos(view: Window): boolean {
  const userAgent = view.navigator.userAgent.toLowerCase()
  const iPadDesktopMode = view.navigator.platform === 'MacIntel' && view.navigator.maxTouchPoints > 1
  return /iphone|ipad|ipod/.test(userAgent) || iPadDesktopMode
}

function isStandalone(view: Window): boolean {
  const navigatorWithStandalone = view.navigator as Navigator & { standalone?: boolean }
  return view.matchMedia?.('(display-mode: standalone)').matches === true
    || navigatorWithStandalone.standalone === true
}

function readStorage(view: Window): Storage | null {
  try {
    return view.localStorage
  } catch {
    return null
  }
}

function readDismissed(storage: Storage | null): boolean {
  try {
    return storage?.getItem(INSTALL_DISMISSED_STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function persistDismissed(storage: Storage | null): void {
  try {
    storage?.setItem(INSTALL_DISMISSED_STORAGE_KEY, 'true')
  } catch {
    // The in-memory state still suppresses the banner for this page.
  }
}

export function createInstallPromptController(
  view: Window | null = typeof window === 'undefined' ? null : window,
): InstallPromptController {
  const storage = view ? readStorage(view) : null
  const listeners = new Set<(state: InstallPromptState) => void>()
  const ios = view ? isIos(view) : false
  let deferredPrompt: BeforeInstallPromptEvent | null = null
  let dismissed = readDismissed(storage)
  let installed = view ? isStandalone(view) : false
  let standalone = installed
  let disposed = false

  const displayModeQuery = view?.matchMedia?.('(display-mode: standalone)') ?? null

  function getState(): InstallPromptState {
    return {
      canInstall: deferredPrompt !== null && !dismissed && !installed,
      dismissed,
      installed,
      ios,
      standalone,
    }
  }

  function notify(): void {
    const state = getState()
    for (const listener of [...listeners]) listener(state)
  }

  function syncStandalone(): void {
    if (!view) return
    standalone = isStandalone(view)
    if (standalone) installed = true
    notify()
  }

  const onBeforeInstallPrompt = (event: Event): void => {
    const candidate = event as BeforeInstallPromptEvent
    if (typeof candidate.prompt !== 'function' || !candidate.userChoice) return
    event.preventDefault()
    deferredPrompt = candidate
    notify()
  }

  const onAppInstalled = (): void => {
    installed = true
    standalone = true
    deferredPrompt = null
    notify()
  }

  const onStorage = (event: StorageEvent): void => {
    if (event.key !== INSTALL_DISMISSED_STORAGE_KEY || event.newValue !== 'true') return
    dismissed = true
    notify()
  }

  if (view) {
    view.addEventListener('beforeinstallprompt', onBeforeInstallPrompt as EventListener)
    view.addEventListener('appinstalled', onAppInstalled)
    view.addEventListener('storage', onStorage)
    if (displayModeQuery) {
      if (typeof displayModeQuery.addEventListener === 'function') {
        displayModeQuery.addEventListener('change', syncStandalone)
      } else {
        displayModeQuery.addListener(syncStandalone)
      }
    }
  }

  async function install(): Promise<InstallPromptOutcome> {
    const prompt = deferredPrompt
    if (!prompt || dismissed || installed) return 'unavailable'

    deferredPrompt = null
    notify()
    try {
      await prompt.prompt()
      const result = await prompt.userChoice
      if (result.outcome === 'accepted') installed = true
      return result.outcome
    } catch {
      return 'unavailable'
    } finally {
      notify()
    }
  }

  function subscribe(listener: (state: InstallPromptState) => void): () => void {
    if (disposed) return () => undefined
    listeners.add(listener)
    listener(getState())
    return () => listeners.delete(listener)
  }

  function dismiss(): void {
    if (disposed || dismissed) return
    dismissed = true
    persistDismissed(storage)
    notify()
  }

  function dispose(): void {
    if (disposed) return
    disposed = true
    listeners.clear()
    if (!view) return
    view.removeEventListener('beforeinstallprompt', onBeforeInstallPrompt as EventListener)
    view.removeEventListener('appinstalled', onAppInstalled)
    view.removeEventListener('storage', onStorage)
    if (displayModeQuery) {
      if (typeof displayModeQuery.removeEventListener === 'function') {
        displayModeQuery.removeEventListener('change', syncStandalone)
      } else {
        displayModeQuery.removeListener(syncStandalone)
      }
    }
  }

  return { getState, subscribe, install, dismiss, dispose }
}

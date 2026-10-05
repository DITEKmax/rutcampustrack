/**
 * Caller-owned mobile theme binding.
 *
 * The shared package does not persist a preference or apply anything while it
 * is imported. A caller creates one controller for the element it owns and
 * disposes it when that owner unmounts.
 */
export const MOBILE_THEME_ATTRIBUTE = 'data-theme'
export const MOBILE_THEME_MODES = ['system', 'light', 'dark'] as const

export type MobileThemeMode = (typeof MOBILE_THEME_MODES)[number]
export type MobileThemeResolvedMode = Exclude<MobileThemeMode, 'system'>

export interface MobileThemeSnapshot {
  readonly mode: MobileThemeMode
  readonly resolvedMode: MobileThemeResolvedMode
}

export interface MobileThemeController extends MobileThemeSnapshot {
  setMode(mode: MobileThemeMode): void
  subscribe(listener: (snapshot: MobileThemeSnapshot) => void): () => void
  dispose(): void
}

export interface MobileThemeOptions {
  /** Defaults to document.documentElement when a DOM is available. */
  target?: HTMLElement | null
  mode?: MobileThemeMode
  /** Locks resolved/applied appearance while retaining the caller-selected preference. */
  lockedMode?: MobileThemeResolvedMode
}

export class MobileThemeOwnershipError extends Error {
  constructor() {
    super('A mobile theme controller already owns this target')
    this.name = 'MobileThemeOwnershipError'
  }
}

type ThemeListener = (snapshot: MobileThemeSnapshot) => void

const targetOwners = new WeakMap<HTMLElement, object>()
const MEDIA_QUERY = '(prefers-color-scheme: dark)'

function isThemeMode(value: unknown): value is MobileThemeMode {
  return value === 'system' || value === 'light' || value === 'dark'
}

function normalizeMode(value: MobileThemeMode | undefined): MobileThemeMode {
  return isThemeMode(value) ? value : 'system'
}

function defaultTarget(): HTMLElement | null {
  return typeof document === 'undefined' ? null : document.documentElement
}

function resolveWindow(target: HTMLElement | null): (Window & typeof globalThis) | null {
  if (target?.ownerDocument?.defaultView) return target.ownerDocument.defaultView
  return typeof window === 'undefined' ? null : window
}

function resolveMediaQuery(target: HTMLElement | null): MediaQueryList | null {
  const view = resolveWindow(target)
  return view?.matchMedia ? view.matchMedia(MEDIA_QUERY) : null
}

function resolveMode(mode: MobileThemeMode, mediaQuery: MediaQueryList | null): MobileThemeResolvedMode {
  if (mode !== 'system') return mode
  return mediaQuery?.matches ? 'dark' : 'light'
}

function subscribeToMediaQuery(mediaQuery: MediaQueryList, listener: () => void): () => void {
  if (typeof mediaQuery.addEventListener === 'function') {
    mediaQuery.addEventListener('change', listener)
    return () => mediaQuery.removeEventListener('change', listener)
  }

  mediaQuery.addListener(listener)
  return () => mediaQuery.removeListener(listener)
}

/**
 * Creates a theme binding without taking ownership of persistence or host
 * policy. Only one live controller may own a target; a second owner is an
 * explicit error instead of silently overwriting the first mount.
 */
export function createMobileTheme(options: MobileThemeOptions = {}): MobileThemeController {
  const target = options.target === undefined ? defaultTarget() : options.target
  const owner = target ? {} : null
  if (target && targetOwners.has(target)) throw new MobileThemeOwnershipError()
  if (target && owner) targetOwners.set(target, owner)

  const previousAttribute = target?.getAttribute(MOBILE_THEME_ATTRIBUTE) ?? null
  const previousColorScheme = target?.style.colorScheme ?? ''
  const listeners = new Set<ThemeListener>()
  let mode: MobileThemeMode = normalizeMode(options.mode ?? options.lockedMode)
  let mediaQuery = !options.lockedMode && mode === 'system' ? resolveMediaQuery(target) : null
  let resolvedMode = options.lockedMode ?? resolveMode(mode, mediaQuery)
  let stopMediaSubscription: (() => void) | undefined
  let disposed = false

  const snapshot = (): MobileThemeSnapshot => ({ mode, resolvedMode })

  function notify(): void {
    const current = snapshot()
    for (const listener of [...listeners]) listener(current)
  }

  function apply(): void {
    if (!target) return
    target.setAttribute(MOBILE_THEME_ATTRIBUTE, options.lockedMode ?? mode)
    target.style.colorScheme = resolvedMode
  }

  function stopWatchingMedia(): void {
    stopMediaSubscription?.()
    stopMediaSubscription = undefined
  }

  function watchMedia(): void {
    stopWatchingMedia()
    if (options.lockedMode || mode !== 'system' || !mediaQuery) return
    stopMediaSubscription = subscribeToMediaQuery(mediaQuery, () => {
      if (disposed || mode !== 'system') return
      const nextResolvedMode = resolveMode(mode, mediaQuery)
      if (nextResolvedMode === resolvedMode) return
      resolvedMode = nextResolvedMode
      apply()
      notify()
    })
  }

  function setMode(nextMode: MobileThemeMode): void {
    if (disposed) return
    const normalized = normalizeMode(nextMode)
    if (normalized === mode) return
    mode = normalized
    if (!options.lockedMode && mode === 'system' && !mediaQuery) mediaQuery = resolveMediaQuery(target)
    resolvedMode = options.lockedMode ?? resolveMode(mode, mediaQuery)
    apply()
    watchMedia()
    notify()
  }

  function subscribe(listener: ThemeListener): () => void {
    if (disposed) return () => undefined
    listeners.add(listener)
    return () => listeners.delete(listener)
  }

  function dispose(): void {
    if (disposed) return
    disposed = true
    stopWatchingMedia()
    listeners.clear()
    if (target) {
      if (previousAttribute === null) target.removeAttribute(MOBILE_THEME_ATTRIBUTE)
      else target.setAttribute(MOBILE_THEME_ATTRIBUTE, previousAttribute)
      target.style.colorScheme = previousColorScheme
      if (owner && targetOwners.get(target) === owner) targetOwners.delete(target)
    }
  }

  apply()
  watchMedia()

  return {
    get mode() { return mode },
    get resolvedMode() { return resolvedMode },
    setMode,
    subscribe,
    dispose,
  }
}

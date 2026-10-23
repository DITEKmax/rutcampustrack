/**
 * Caller-owned mobile theme binding.
 *
 * The shared package does not persist a preference or apply anything while it
 * is imported. A caller creates one controller for the element it owns and
 * disposes it when that owner unmounts.
 */
export const THEME_ATTRIBUTE = 'data-theme'
export const THEME_MODES = ['system', 'light', 'dark'] as const

export type ThemeMode = (typeof THEME_MODES)[number]
export type ResolvedTheme = Exclude<ThemeMode, 'system'>

export interface ThemeSnapshot {
  readonly mode: ThemeMode
  readonly resolvedMode: ResolvedTheme
}

export interface ThemeController extends ThemeSnapshot {
  setMode(mode: ThemeMode): void
  subscribe(listener: (snapshot: ThemeSnapshot) => void): () => void
  dispose(): void
}

export interface ThemeOptions {
  /** Defaults to document.documentElement when a DOM is available. */
  target?: HTMLElement | null
  mode?: ThemeMode
  systemSource?: SystemThemeSource
  /** Compatibility only; applications should resolve the selected theme. */
  lockedMode?: ResolvedTheme
}

export class ThemeOwnershipError extends Error {
  constructor() {
    super('A mobile theme controller already owns this target')
    this.name = 'ThemeOwnershipError'
  }
}

type ThemeListener = (snapshot: ThemeSnapshot) => void

const targetOwners = new WeakMap<HTMLElement, object>()
const MEDIA_QUERY = '(prefers-color-scheme: dark)'

function isThemeMode(value: unknown): value is ThemeMode {
  return value === 'system' || value === 'light' || value === 'dark'
}

function normalizeMode(value: ThemeMode | undefined): ThemeMode {
  return isThemeMode(value) ? value : 'system'
}

function defaultTarget(): HTMLElement | null {
  return typeof document === 'undefined' ? null : document.documentElement
}

function resolveWindow(target: HTMLElement | null): (Window & typeof globalThis) | null {
  if (target?.ownerDocument?.defaultView) return target.ownerDocument.defaultView
  return typeof window === 'undefined' ? null : window
}

export interface SystemThemeSource {
  read(): ResolvedTheme
  subscribe(listener: () => void): () => void
}
export function createBrowserSystemThemeSource(
  view: Pick<Window, 'matchMedia'> | null = typeof window === 'undefined' ? null : window,
): SystemThemeSource {
  const query = view?.matchMedia?.(MEDIA_QUERY)
  return {
    read: () => (query?.matches ? 'dark' : 'light'),
    subscribe(listener) {
      if (!query) return () => undefined
      if (typeof query.addEventListener === 'function') {
        query.addEventListener('change', listener)
        return () => query.removeEventListener('change', listener)
      }
      query.addListener(listener)
      return () => query.removeListener(listener)
    },
  }
}

/**
 * Creates a theme binding without taking ownership of persistence or host
 * policy. Only one live controller may own a target; a second owner is an
 * explicit error instead of silently overwriting the first mount.
 */
export function createTheme(options: ThemeOptions = {}): ThemeController {
  const target = options.target === undefined ? defaultTarget() : options.target
  const owner = target ? {} : null
  if (target && targetOwners.has(target)) throw new ThemeOwnershipError()
  if (target && owner) targetOwners.set(target, owner)

  const previousAttribute = target?.getAttribute(THEME_ATTRIBUTE) ?? null
  const previousColorScheme = target?.style.colorScheme ?? ''
  const listeners = new Set<ThemeListener>()
  let mode: ThemeMode = normalizeMode(options.mode ?? options.lockedMode)
  const source = options.systemSource ?? createBrowserSystemThemeSource(resolveWindow(target))
  const resolve = (): ResolvedTheme =>
    options.lockedMode ?? (mode === 'system' ? source.read() : mode)
  let resolvedMode = resolve()
  let stopMediaSubscription: (() => void) | undefined
  let disposed = false

  const snapshot = (): ThemeSnapshot => ({ mode, resolvedMode })

  function notify(): void {
    const current = snapshot()
    for (const listener of [...listeners]) listener(current)
  }

  function apply(): void {
    if (!target) return
    target.setAttribute(THEME_ATTRIBUTE, resolvedMode)
    target.style.colorScheme = resolvedMode
  }

  function stopWatchingMedia(): void {
    stopMediaSubscription?.()
    stopMediaSubscription = undefined
  }

  function watchMedia(): void {
    stopWatchingMedia()
    if (options.lockedMode || mode !== 'system') return
    stopMediaSubscription = source.subscribe(() => {
      if (disposed || mode !== 'system') return
      const nextResolvedMode = resolve()
      if (nextResolvedMode === resolvedMode) return
      resolvedMode = nextResolvedMode
      apply()
      notify()
    })
  }

  function setMode(nextMode: ThemeMode): void {
    if (disposed) return
    const normalized = normalizeMode(nextMode)
    if (normalized === mode) {
      notify()
      return
    }
    mode = normalized
    resolvedMode = resolve()
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
      if (previousAttribute === null) target.removeAttribute(THEME_ATTRIBUTE)
      else target.setAttribute(THEME_ATTRIBUTE, previousAttribute)
      target.style.colorScheme = previousColorScheme
      if (owner && targetOwners.get(target) === owner) targetOwners.delete(target)
    }
  }

  apply()
  watchMedia()

  return {
    get mode() {
      return mode
    },
    get resolvedMode() {
      return resolvedMode
    },
    setMode,
    subscribe,
    dispose,
  }
}

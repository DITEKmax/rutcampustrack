import { THEME_MODES, type ThemeMode } from './theme'
export const THEME_PREFERENCE_KEY = 'rct.theme'
export const LEGACY_THEME_PREFERENCE_KEYS = [
  'rct.mobile.theme',
  'ruttrack.theme',
  'web-panel.theme',
  'rct.web.theme',
] as const
type ThemeStorage = Pick<Storage, 'getItem' | 'setItem'> & Partial<Pick<Storage, 'removeItem'>>
export function isThemeMode(value: unknown): value is ThemeMode {
  return THEME_MODES.some((mode) => mode === value)
}
/** Non-account device choice; never remove on logout. */
export function createThemePreference(
  storage: () => ThemeStorage | null,
  events: Pick<Window, 'addEventListener' | 'removeEventListener'> | null = typeof window ===
  'undefined'
    ? null
    : window,
) {
  const listeners = new Set<(mode: ThemeMode | null) => void>()
  const read = (): ThemeMode | null => {
    try {
      const current = storage()?.getItem(THEME_PREFERENCE_KEY)
      if (isThemeMode(current)) return current
      for (const key of LEGACY_THEME_PREFERENCE_KEYS) {
        const legacy = storage()?.getItem(key)
        if (isThemeMode(legacy)) {
          try {
            const store = storage()
            store?.setItem(THEME_PREFERENCE_KEY, legacy)
            for (const oldKey of LEGACY_THEME_PREFERENCE_KEYS) store?.removeItem?.(oldKey)
          } catch {
            /* Read-only storage can still supply a valid legacy choice. */
          }
          return legacy
        }
      }
    } catch {
      /* Blocked storage must not block appearance. */
    }
    return null
  }
  const changed = (event: Event): void => {
    const change = event as StorageEvent
    if (change.key !== THEME_PREFERENCE_KEY && change.key !== null) return
    let ownStore: ThemeStorage | null
    try {
      ownStore = storage()
    } catch {
      return
    }
    if (change.storageArea && change.storageArea !== ownStore) return
    const mode = change.key === null ? null : isThemeMode(change.newValue) ? change.newValue : null
    for (const listener of [...listeners]) listener(mode)
  }
  events?.addEventListener?.('storage', changed)
  return {
    read,
    write(mode: ThemeMode): void {
      if (!isThemeMode(mode)) return
      try {
        storage()?.setItem(THEME_PREFERENCE_KEY, mode)
      } catch {
        /* Keep in-memory choice. */
      }
    },
    subscribe(listener: (mode: ThemeMode | null) => void): () => void {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    dispose(): void {
      events?.removeEventListener?.('storage', changed)
      listeners.clear()
    },
  }
}

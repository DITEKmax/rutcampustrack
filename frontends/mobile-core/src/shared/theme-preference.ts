import { MOBILE_THEME_MODES, type MobileThemeMode } from './theme'

export const MOBILE_THEME_PREFERENCE_KEY = 'rct.mobile.theme'
type ThemeStorage = Pick<Storage, 'getItem' | 'setItem'>

/** Caller owns when persistence runs. Only a validated non-account theme choice is read/written. */
export function createMobileThemePreference(storage: () => ThemeStorage | null) {
  return {
    read(): MobileThemeMode | null {
      try {
        const value = storage()?.getItem(MOBILE_THEME_PREFERENCE_KEY)
        return MOBILE_THEME_MODES.find((mode) => mode === value) ?? null
      } catch { return null }
    },
    write(mode: MobileThemeMode): void {
      if (!MOBILE_THEME_MODES.includes(mode)) return
      try { storage()?.setItem(MOBILE_THEME_PREFERENCE_KEY, mode) } catch { /* Preference storage may be unavailable. */ }
    },
  }
}

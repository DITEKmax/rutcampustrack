/** Stable mobile aliases to the host-neutral implementation. */
export {
  THEME_ATTRIBUTE as MOBILE_THEME_ATTRIBUTE,
  THEME_MODES as MOBILE_THEME_MODES,
  ThemeOwnershipError as MobileThemeOwnershipError,
  createTheme as createMobileTheme,
  createBrowserSystemThemeSource,
} from '../../../shared/foundation/theme'
export type {
  ThemeMode as MobileThemeMode,
  ResolvedTheme as MobileThemeResolvedMode,
  ThemeSnapshot as MobileThemeSnapshot,
  ThemeController as MobileThemeController,
  ThemeOptions as MobileThemeOptions,
  SystemThemeSource,
} from '../../../shared/foundation/theme'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

/**
 * RutCampusTrack — Transit Grid Theme Provider (React)
 *
 * Manages the active theme (dark | light) via the `[data-theme]` attribute on
 * <html>. Persists user choice in localStorage under `ruttrack.theme`. Falls
 * back to `prefers-color-scheme` when no stored value exists.
 *
 * An `initialTheme` prop lets a parent seed the first render from an external
 * source (e.g. Telegram `colorScheme` in the mini-app) while still allowing
 * the user to manually override afterwards.
 *
 * Mirrored verbatim in frontends/pwa and frontends/mini-app because there is
 * no shared React package in the monorepo.
 */

export type Theme = 'dark' | 'light'

const STORAGE_KEY = 'ruttrack.theme'

interface ThemeContextValue {
  theme: Theme
  setTheme: (theme: Theme) => void
  toggleTheme: () => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function readInitialTheme(seed?: Theme): Theme {
  if (typeof window === 'undefined') return seed ?? 'dark'
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored === 'dark' || stored === 'light') return stored
  } catch {
    // localStorage can throw in private mode — fall through
  }
  if (seed) return seed
  if (window.matchMedia?.('(prefers-color-scheme: light)').matches) return 'light'
  return 'dark'
}

function applyTheme(theme: Theme): void {
  const root = document.documentElement
  root.setAttribute('data-theme', theme)
  // Legacy `.dark` class retained for any libraries that rely on it.
  root.classList.toggle('dark', theme === 'dark')
}

interface ThemeProviderProps {
  children: ReactNode
  /** Optional hint for the initial theme when no user preference is stored. */
  initialTheme?: Theme
}

export function ThemeProvider({ children, initialTheme }: ThemeProviderProps) {
  const [theme, setThemeState] = useState<Theme>(() => readInitialTheme(initialTheme))
  // Track whether the user has explicitly chosen a theme — used to decide if
  // external hints (e.g. Telegram colorScheme) should still update the theme.
  const userOverrodeRef = useRef<boolean>(
    typeof window !== 'undefined' &&
      (() => {
        try {
          return !!window.localStorage.getItem(STORAGE_KEY)
        } catch {
          return false
        }
      })(),
  )

  // Apply on mount + every change.
  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  // React to OS preference changes only while the user hasn't overridden.
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    const mql = window.matchMedia('(prefers-color-scheme: light)')
    const handler = (e: MediaQueryListEvent) => {
      if (userOverrodeRef.current) return
      setThemeState(e.matches ? 'light' : 'dark')
    }
    mql.addEventListener('change', handler)
    return () => mql.removeEventListener('change', handler)
  }, [])

  // If `initialTheme` changes after mount (e.g. Telegram color scheme ready)
  // and the user hasn't overridden, follow the hint.
  useEffect(() => {
    if (!initialTheme) return
    if (userOverrodeRef.current) return
    setThemeState(initialTheme)
  }, [initialTheme])

  const setTheme = useCallback((next: Theme) => {
    // Brief transition class so colors interpolate instead of snapping.
    const root = document.documentElement
    root.classList.add('theme-transitioning')
    window.setTimeout(() => root.classList.remove('theme-transitioning'), 320)

    userOverrodeRef.current = true
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // ignore
    }
    setThemeState(next)
  }, [])

  const toggleTheme = useCallback(() => {
    setTheme(theme === 'dark' ? 'light' : 'dark')
  }, [theme, setTheme])

  const value = useMemo<ThemeContextValue>(
    () => ({ theme, setTheme, toggleTheme }),
    [theme, setTheme, toggleTheme],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) {
    throw new Error('useTheme must be used inside a <ThemeProvider>')
  }
  return ctx
}

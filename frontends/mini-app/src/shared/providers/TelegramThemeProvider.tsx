import { useMemo, type ReactNode } from 'react'
import { useSignal, miniApp } from '@telegram-apps/sdk-react'
import { ThemeProvider, type Theme } from '@/shared/theme/ThemeProvider'

/**
 * RutCampusTrack — Telegram-aware theme bridge.
 *
 * Reads Telegram's `miniApp.isDark` signal on first render and feeds it into
 * the Transit Grid `ThemeProvider` as `initialTheme`. The Transit Grid provider
 * then owns the active theme (dark | light) via the `[data-theme]` attribute
 * and all colors come from `tokens.css` — NOT from Telegram's raw color codes.
 *
 * Why not apply Telegram colors directly?
 * The previous implementation mapped `bg_color`, `text_color`, etc. to
 * `--background` / `--foreground`, which clobbered our brand palette and left
 * the app unbranded inside Telegram. The brandbook requires RutCampusTrack
 * to look like itself everywhere — Telegram's color scheme only hints which
 * of our two themes to start with.
 *
 * The user can still manually toggle via the ThemeToggle button; their choice
 * is persisted in localStorage and takes precedence over Telegram's signal.
 */
export function TelegramThemeProvider({ children }: { children: ReactNode }) {
  // `useSignal(miniApp.isDark)` returns boolean | undefined; undefined when
  // SDK isn't mounted (dev mock) — we default to 'dark' in that case.
  const isDark = useSignal(miniApp.isDark) as boolean | undefined

  const initialTheme = useMemo<Theme>(
    () => (isDark === false ? 'light' : 'dark'),
    [isDark],
  )

  return <ThemeProvider initialTheme={initialTheme}>{children}</ThemeProvider>
}

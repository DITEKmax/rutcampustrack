import { useEffect, type ReactNode } from 'react'
import { useSignal, themeParams } from '@telegram-apps/sdk-react'

const THEME_MAP: Record<string, string> = {
  bg_color: '--background',
  text_color: '--foreground',
  secondary_bg_color: '--card',
  button_color: '--primary',
  button_text_color: '--primary-foreground',
  hint_color: '--muted-foreground',
  link_color: '--accent',
}

export function TelegramThemeProvider({ children }: { children: ReactNode }) {
  // useSignal returns null when themeParams is not mounted (dev mock mode)
  // Fall back to PWA neutral CSS var defaults from index.css in that case
  const params = useSignal(themeParams.state) as Record<string, string> | null

  useEffect(() => {
    if (!params) return
    const root = document.documentElement
    for (const [tgKey, cssVar] of Object.entries(THEME_MAP)) {
      const value = params[tgKey]
      if (value) root.style.setProperty(cssVar, value)
    }
  }, [params])

  return <>{children}</>
}

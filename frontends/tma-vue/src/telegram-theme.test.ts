import { describe, expect, it } from 'vitest'
import { createTheme } from '../../shared/foundation/theme'
import { createTelegramSystemThemeSource } from './telegram'

describe('Telegram system appearance', () => {
  it('follows themeChanged live, keeps a manual override, and unsubscribes on disposal', () => {
    const callbacks = new Set<() => void>()
    const app = {
      initData: '',
      ready() {},
      expand() {},
      viewportHeight: 700,
      colorScheme: 'dark' as 'dark' | 'light',
      onEvent(event: string, callback: () => void) {
        if (event === 'themeChanged') callbacks.add(callback)
      },
      offEvent(event: string, callback: () => void) {
        if (event === 'themeChanged') callbacks.delete(callback)
      },
    }
    const theme = createTheme({ target: null, systemSource: createTelegramSystemThemeSource(app) })
    expect(theme.resolvedMode).toBe('dark')
    app.colorScheme = 'light'
    for (const callback of callbacks) callback()
    expect(theme.resolvedMode).toBe('light')
    theme.setMode('dark')
    app.colorScheme = 'light'
    for (const callback of callbacks) callback()
    expect(theme.resolvedMode).toBe('dark')
    theme.setMode('system')
    expect(theme.resolvedMode).toBe('light')
    theme.dispose()
    expect(callbacks.size).toBe(0)
  })
})

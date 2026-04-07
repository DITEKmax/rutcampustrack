import { isTMA, mockTelegramEnv, emitEvent } from '@telegram-apps/sdk-react'

type HexColor = `#${string}`

const mockTheme: Record<string, HexColor> = {
  bg_color: '#ffffff',
  text_color: '#000000',
  button_color: '#2196F3',
  button_text_color: '#ffffff',
  secondary_bg_color: '#f5f5f5',
  hint_color: '#999999',
  link_color: '#2196F3',
}

export async function setupMockEnv(): Promise<void> {
  if (import.meta.env.VITE_TMA_DEV !== 'true') {
    return
  }

  const isReal = await isTMA('complete')
  if (isReal) {
    return
  }

  const mockUser = import.meta.env.VITE_TMA_MOCK_USER ?? 'student'

  const tgWebAppData = new URLSearchParams({
    auth_date: String(Math.floor(Date.now() / 1000)),
    hash: 'mock-hash',
    user: JSON.stringify({ id: 1, first_name: 'Dev', username: mockUser }),
  }).toString()

  const launchParams = new URLSearchParams({
    tgWebAppThemeParams: JSON.stringify(mockTheme),
    tgWebAppData,
    tgWebAppVersion: '8.0',
    tgWebAppPlatform: 'tdesktop',
  })

  mockTelegramEnv({
    launchParams,
    onEvent([eventType], _next) {
      if (eventType === 'web_app_request_theme') {
        emitEvent('theme_changed', { theme_params: mockTheme })
      } else if (eventType === 'web_app_request_viewport') {
        emitEvent('viewport_changed', {
          height: window.innerHeight,
          width: window.innerWidth,
          is_expanded: true,
          is_state_stable: true,
        })
      }
    },
  })
}

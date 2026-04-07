import '@fontsource-variable/geist'
import './index.css'
import { StrictMode } from 'react'
import ReactDOM from 'react-dom/client'
import { init, miniApp, viewport } from '@telegram-apps/sdk-react'
import { setupMockEnv } from '@/shared/lib/mockWebApp'

async function bootstrap() {
  try {
    await setupMockEnv()
    init()

    if (viewport.mount.isAvailable()) {
      try {
        await viewport.mount()
        viewport.expand()
      } catch {
        // viewport mount failed — continue
      }
    }

    miniApp.ready()

    const { default: App } = await import('./App')
    ReactDOM.createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <App />
      </StrictMode>
    )
  } catch {
    miniApp.ready()
    document.getElementById('root')!.innerHTML = '<div style="padding:20px;color:red;">Failed to initialize app</div>'
  }
}

bootstrap()

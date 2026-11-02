import '../../../mobile-core/src/styles/base.pcss'
import { createApp } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createInteractiveMockTransport } from './interactive-mock-transport'

async function start(): Promise<void> {
  if (!import.meta.env.DEV || location.hostname !== '127.0.0.1' || location.port !== '14222')
    throw new Error('Interactive PWA mock requires the dedicated loopback server on port 14222.')

  // Import the real shell only after interception. There is no backend fallback,
  // telemetry bootstrap, service-worker registration or install prompt here.
  window.fetch = createInteractiveMockTransport()
  const { default: App } = await import('../App.vue')
  const { createInstallPromptController } = await import('../features/install/install-prompt')
  const app = createApp(App, { installPrompt: createInstallPromptController() })
  app.use(VueQueryPlugin, { queryClient: new QueryClient() })
  app.mount('#app')
}

void start()

import '../../mobile-core/src/styles/base.pcss'
import { createApp } from 'vue'
import { installErrorReporting } from '../../mobile-core/src/observability/errors'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'

const app = createApp(App)
installErrorReporting(app, {
  dsn: import.meta.env.VITE_GLITCHTIP_DSN,
  release: import.meta.env.VITE_APP_VERSION,
  environment: import.meta.env.VITE_TELEMETRY_ENVIRONMENT ?? 'production',
})
app.use(VueQueryPlugin, { queryClient: new QueryClient() })
app.mount('#app')

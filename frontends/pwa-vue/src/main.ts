import '@fontsource-variable/onest'
import { createApp } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'

const app = createApp(App)
app.use(VueQueryPlugin, { queryClient: new QueryClient() })
app.mount('#app')

function unregisterDevelopmentServiceWorker(base: string): void {
  const scopeUrl = new URL(base, window.location.href).href
  const scriptUrl = new URL(`${base}sw.js`, window.location.href).href
  void navigator.serviceWorker.getRegistrations().then((registrations) => {
    for (const registration of registrations) {
      const workers = [registration.installing, registration.waiting, registration.active]
      const ownsScope = registration.scope === scopeUrl
      const ownsScript = workers.some((worker) => worker?.scriptURL === scriptUrl)
      if (ownsScope && ownsScript) void registration.unregister()
    }
  })
}

if ('serviceWorker' in navigator) {
  const base = import.meta.env.BASE_URL
  if (__RCT_SERVICE_WORKER_ENABLED__ && window.isSecureContext) {
    void navigator.serviceWorker.register(`${base}sw.js`, { scope: base })
  } else if (import.meta.env.DEV) {
    unregisterDevelopmentServiceWorker(base)
  }
}

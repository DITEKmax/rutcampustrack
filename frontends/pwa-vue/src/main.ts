import '@fontsource-variable/onest'
import { createApp } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'

const app = createApp(App)
app.use(VueQueryPlugin, { queryClient: new QueryClient() })
app.mount('#app')

if ('serviceWorker' in navigator && __RCT_SERVICE_WORKER_ENABLED__ && window.location.hostname === 'localhost') {
  const base = import.meta.env.BASE_URL
  void navigator.serviceWorker.register(`${base}sw.js`, { scope: base })
}

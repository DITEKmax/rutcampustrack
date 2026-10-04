import '../../mobile-core/src/styles/base.pcss'
import { createApp } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'

const app = createApp(App)
app.use(VueQueryPlugin, { queryClient: new QueryClient() })
app.mount('#app')

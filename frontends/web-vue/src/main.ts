import { createApp } from 'vue'
import App from './App.vue'
import { router } from './app/router'
import { webSession } from './auth/session'
import './app/theme'
import '../../shared/foundation/base.pcss'
import './shared/web.pcss'

createApp(App).use(router).mount('#app')
void webSession.bootstrap()

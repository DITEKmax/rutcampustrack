import { createApp } from 'vue'
import App from './App.vue'
import { router } from './app/router'
import { webSession } from './auth/session'
import { installChunkRecovery } from './app/chunk-recovery'
import './app/theme'
import '../../shared/foundation/base.pcss'
import './shared/web.pcss'

installChunkRecovery(router)
createApp(App).use(router).mount('#app')
void webSession.bootstrap()

import { createApp, h, ref } from 'vue'
import { flush } from '@sentry/vue'
import { installErrorReporting } from '../../frontends/mobile-core/src/observability/errors'

declare global {
  interface Window {
    __RCT_OBSERVER_FIXTURE__: { dsn: string; release: string }
    __RCT_OBSERVER_FLUSH__: () => Promise<boolean>
  }
}
const completed = ref(0)
const app = createApp({
  render() {
    return h('main', [
      h('span', { id: 'completed' }, String(completed.value)),
      h(
        'button',
        {
          id: 'operation',
          onClick: () => {
            completed.value++
            throw new TypeError('synthetic-private@example.invalid')
          },
        },
        'Synthetic operation',
      ),
    ])
  },
})
installErrorReporting(app, {
  dsn: window.__RCT_OBSERVER_FIXTURE__.dsn,
  release: window.__RCT_OBSERVER_FIXTURE__.release,
  environment: 'isolated-ci',
})
window.__RCT_OBSERVER_FLUSH__ = () => flush(3000)
app.mount('#app')

import '../../../mobile-core/src/styles/base.pcss'
import { createApp } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { fixtureGeoSource } from '../../../mobile-core/src/dev/device-access-fixture'
import { previewNow } from '../../../web-vue/src/dev/visual-review-fixtures'
import { createInteractiveMockTransport } from '../../../pwa-vue/src/dev/interactive-mock-transport'
import { createPwaTraceObserver, pwaDemoChannel } from './pwa-trace'
import { observeDemoActions } from './pwa-actions'
import { createAdminMapDemo, createCampusDemo } from './pwa-campus'
import { createDemoSnapshots } from './pwa-snapshots'
import { createStudentDemo } from './pwa-student'
import { createAdminDemo } from './pwa-admin'
import { createHeadmanDemo } from './pwa-headman'
import { createReportDemo } from './pwa-reports'
import { createProfileDemo, demoNotificationSeed } from './pwa-profile'

const frameId = new URLSearchParams(location.search).get('instance')

function memoryStorage(): Storage {
  const items = new Map<string, string>()
  return {
    get length() { return items.size },
    clear() { items.clear() },
    getItem: key => items.get(String(key)) ?? null,
    key: index => [...items.keys()][index] ?? null,
    removeItem(key) { items.delete(String(key)) },
    setItem(key, value) { items.set(String(key), String(value)) },
  }
}

function localDocument(url: string | URL | undefined): boolean {
  if (!url) return true
  try {
    const target = new URL(url, location.href)
    return target.protocol === 'blob:' && target.origin === location.origin
  } catch {
    return false
  }
}

async function start(): Promise<void> {
  // Some feature screens store unfinished commands directly in Web Storage.
  // The iframe owns both stores, including those initialized by the PWA modules.
  Object.defineProperties(window, {
    localStorage: { value: memoryStorage() },
    sessionStorage: { value: memoryStorage() },
  })
  const open = window.open.bind(window)
  window.open = (url, target, features) => localDocument(url) ? open(url, target, features) : null
  document.addEventListener('click', event => {
    const link = event.target instanceof Element ? event.target.closest('a[target="_blank"]') : null
    if (!(link instanceof HTMLAnchorElement) || localDocument(link.href)) return
    event.preventDefault()
    // Student materials use the shell's own handler, while HEADMAN renders
    // ordinary links. The latter receive the same synthetic local download.
    if (!link.closest('.student-feature-owner')) downloadMaterial()
  }, true)
  const handlers = [createCampusDemo(), createAdminMapDemo(), createStudentDemo(), createAdminDemo(), createHeadmanDemo(), createReportDemo(), createProfileDemo()]
  const transport = createInteractiveMockTransport({ persist: false, notificationSeed: demoNotificationSeed, handle: async (...args) => {
    for (const handle of handlers) {
      const result = await handle(...args)
      if (result) return result
    }
    return null
  } })
  const browserFetch = window.fetch.bind(window)
  const trace = createPwaTraceObserver((detail) => {
    if (window.parent !== window)
      window.parent.postMessage({ channel: pwaDemoChannel, frameId, type: 'trace', detail }, location.origin)
  })
  const traceNavigation = observeDemoActions(trace)
  const traced = trace.wrap(transport)
  window.fetch = (input, init) => {
    const url = new URL(input instanceof Request ? input.url : String(input), location.origin)
    return url.protocol === 'blob:' && url.origin === location.origin ? browserFetch(input, init) : traced(input, init)
  }

  const { default: App } = await import('../../../pwa-vue/src/App.vue')
  const { createInstallPromptController } = await import('../../../pwa-vue/src/features/install/install-prompt')
  const app = createApp(App, {
    installPrompt: createInstallPromptController(null),
    demo: {
      geo: fixtureGeoSource('BROWSER', 'coordinates'),
      clock: () => new Date(previewNow),
      authChannel: { postMessage() {}, addEventListener() {}, removeEventListener() {}, close() {} },
      snapshots: createDemoSnapshots(),
      openMaterial: downloadMaterial,
    },
  })
  app.use(VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false },
  } }) })
  const pwa = app.mount('#app') as unknown as {
    openDemoStudentRoute(route: 'today' | 'attendance' | 'homework' | 'map'): Promise<void>
  }
  window.addEventListener('message', event => {
    if (event.origin !== location.origin || event.source !== parent ||
        event.data?.channel !== pwaDemoChannel || event.data.frameId !== frameId ||
        event.data.type !== 'navigate') return
    const route = event.data.route
    if (route === 'today' || route === 'attendance' || route === 'homework' || route === 'map') {
      traceNavigation(route)
      void pwa.openDemoStudentRoute(route)
    }
  })
  if (window.parent !== window)
    window.parent.postMessage({ channel: pwaDemoChannel, frameId, type: 'ready' }, location.origin)
}

function downloadMaterial() {
  const link = document.createElement('a')
  const url = URL.createObjectURL(new Blob(['Материалы к занятию\n\nПовтори теорию и реши задачи из домашнего задания.'], { type: 'text/plain;charset=utf-8' }))
  link.href = url
  link.download = 'Материалы к занятию.txt'
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

void start()

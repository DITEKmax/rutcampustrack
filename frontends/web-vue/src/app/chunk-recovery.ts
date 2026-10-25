import { ref } from 'vue'
import type { Router } from 'vue-router'

// Preserve the legacy per-tab limit across the Angular → Vue cutover.
const RELOADED_KEY = 'rct.chunk-reload.v1'
const importFailure =
  /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Unable to preload CSS for/i

interface RecoveryBrowser {
  readonly sessionStorage: Pick<Storage, 'getItem' | 'setItem'>
  readonly location: Pick<Location, 'reload'>
}

export function createChunkRecovery(browser: RecoveryBrowser) {
  const failed = ref(false)
  const canRefresh = ref(false)

  function observe(error: unknown) {
    const message = error instanceof Error ? error.message : typeof error === 'string' ? error : ''
    if (!importFailure.test(message)) return
    failed.value = true
    try {
      canRefresh.value = !browser.sessionStorage.getItem(RELOADED_KEY)
    } catch {
      // Without a durable tab guard, leave recovery to the browser controls.
      canRefresh.value = false
    }
  }

  function refresh() {
    if (!failed.value || !canRefresh.value) return
    canRefresh.value = false
    try {
      // Recheck at the action boundary; another handler may already have used it.
      if (browser.sessionStorage.getItem(RELOADED_KEY)) return
      browser.sessionStorage.setItem(RELOADED_KEY, '1')
    } catch {
      return
    }
    // Deliberately user initiated: never discard drafts or unknown writes on error.
    // Existing beforeunload guards still participate in this navigation.
    browser.location.reload()
  }

  return { failed, canRefresh, observe, refresh }
}

export const chunkRecovery = createChunkRecovery({
  get sessionStorage() {
    return window.sessionStorage
  },
  get location() {
    return window.location
  },
})

export function installChunkRecovery(router: Pick<Router, 'onError'>) {
  window.addEventListener('vite:preloadError', (event) => {
    chunkRecovery.observe((event as Event & { payload: unknown }).payload)
  })
  router.onError(chunkRecovery.observe)
}

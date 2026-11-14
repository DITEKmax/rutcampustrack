import type { App } from 'vue'

function anchorLayout(root: HTMLElement): string | undefined {
  const fragment = location.hash.slice(1)
  if (!root.dataset.topic) {
    if (fragment === 'topics') return 'tiles'
    if (fragment === 'demo' || fragment === 'demo-phone') return 'demo'
    return
  }

  if (root.dataset.topic === 'design') {
    if (fragment === 'design-today') return 'today'
    if (fragment === 'design-type' || fragment === 'design-tokens') return 'scale'
    if (fragment === 'design-palette') return 'palette'
    return
  }

  const anchor = document.getElementById(fragment)
  if (anchor?.closest('section') && root.contains(anchor)) return 'story'
}

export async function startLanding(load: (root: HTMLElement) => Promise<App<Element>>): Promise<void> {
  const root = document.getElementById('app')
  if (!root) return
  const loading = document.getElementById('landing-loading')
  const layout = anchorLayout(root)
  if (loading && layout) loading.dataset.anchor = layout

  const reloadKey = 'rct.landing.chunk-reload:' + location.pathname
  function reloadOnce() {
    try {
      if (sessionStorage.getItem(reloadKey)) return
      sessionStorage.setItem(reloadKey, '1')
    } catch {
      return
    }
    location.reload()
  }

  window.addEventListener('vite:preloadError', reloadOnce)
  const fallback = Array.from(root.childNodes)
  try {
    const app = await load(root)
    // Preserve keyboard focus if the reader is already using the static page.
    if (root.contains(document.activeElement)) return
    app.config.throwUnhandledErrorInProduction = true
    try {
      app.mount(root)
    } finally {
      app.config.throwUnhandledErrorInProduction = false
    }
  } catch (error) {
    if (fallback.some(node => node.parentNode !== root)) root.replaceChildren(...fallback)
    console.error('Could not load the interactive presentation', error)
  } finally {
    loading?.remove()
    window.removeEventListener('vite:preloadError', reloadOnce)
  }
}

importScripts('./sw-assets.js')

const releaseVersion = '__RCT_PWA_RELEASE__'
const cacheName = `rct-student-pwa-v3-${releaseVersion}`
const precacheAssets = self.__RCT_PRECACHE_ASSETS__

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(cacheName).then((cache) => cache.addAll(precacheAssets)))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key.startsWith('rct-student-pwa-') && key !== cacheName).map((key) => caches.delete(key)))))
  self.clients.claim()
})

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting()
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  const url = new URL(request.url)
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.includes('/api/')) return

  // The gateway release marker must always be read from the network. Keeping
  // it in the app-shell cache would hide a forced update while online.
  if (url.pathname.endsWith('/version.json')) {
    event.respondWith(fetch(request, { cache: 'no-store' }))
    return
  }

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).then((response) => {
      const copy = response.clone()
      void caches.open(cacheName).then((cache) => cache.put(request, copy))
      return response
    }).catch(() => caches.match(request).then((cached) => cached ?? caches.match('./'))))
    return
  }

  event.respondWith(caches.match(request).then((cached) => cached ?? fetch(request).then((response) => {
    const copy = response.clone()
    void caches.open(cacheName).then((cache) => cache.put(request, copy))
    return response
  })))
})

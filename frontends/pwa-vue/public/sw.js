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
  if (event.data?.type === 'RCT_PUSH_READ' || event.data?.type === 'RCT_PUSH_BIND' || event.data?.type === 'RCT_PUSH_CLEAR') {
    // Stop an in-flight push as soon as disable reaches this worker, before IDB I/O.
    if (event.data.type === 'RCT_PUSH_BIND' && event.data.binding === null) pushGateClosed = true
    event.waitUntil(pushSerial(async () => {
      try {
        const source = event.source && await self.clients.get(event.source.id)
        if (!source || !source.url.startsWith(self.registration.scope)) throw new Error('Invalid source')
        let cleared = false
        if (event.data.type === 'RCT_PUSH_CLEAR') {
          const previous = await pushBinding()
          const expected = event.data.binding
          const matches = previous === null ? expected === null
            : expected?.userId === previous.userId && expected?.fingerprint === previous.fingerprint
          if (matches) {
            pushGateClosed = true
            await pushBinding(null, true)
            const notifications = await self.registration.getNotifications()
            for (const notification of notifications) {
              if (matchesPush(previous, notification.data)) notification.close()
            }
            cleared = true
          }
        }
        if (event.data.type === 'RCT_PUSH_BIND') {
          const next = event.data.binding
          if (next !== null && (!next || typeof next.userId !== 'string' || !/^\d+$/.test(next.userId)
            || typeof next.fingerprint !== 'string' || !/^[a-f0-9]{64}$/.test(next.fingerprint))) throw new Error('Invalid binding')
          await pushBinding(next, true)
          pushGateClosed = next === null
          const notifications = await self.registration.getNotifications()
          for (const notification of notifications) notification.close()
        }
        event.ports[0]?.postMessage({ ok: true, binding: await pushBinding(), cleared })
      } catch { event.ports[0]?.postMessage({ ok: false }) }
    }))
  }
})

// This database stores only the accepted recipient and an endpoint digest, never auth or payload data.
let pushQueue = Promise.resolve()
let pushGateClosed = false
function pushSerial(operation) {
  const result = pushQueue.then(operation)
  pushQueue = result.catch(() => undefined)
  return result
}
function pushBinding(value, write = false) {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open('rct-pwa-push-v1', 1)
    request.onupgradeneeded = () => request.result.createObjectStore('binding')
    request.onerror = () => reject(request.error)
    request.onsuccess = () => {
      const db = request.result
      const tx = db.transaction('binding', write ? 'readwrite' : 'readonly')
      const store = tx.objectStore('binding')
      const query = write ? (value === null ? store.delete('current') : store.put(value, 'current')) : store.get('current')
      tx.oncomplete = () => { db.close(); resolve(write ? value : query.result ?? null) }
      tx.onerror = tx.onabort = () => { db.close(); reject(tx.error) }
    }
  })
}
function matchesPush(binding, envelope) {
  return binding && envelope && envelope.recipientUserId === binding.userId
    && envelope.subscriptionFingerprint === binding.fingerprint
}
self.addEventListener('push', (event) => {
  event.waitUntil(pushSerial(async () => {
    let payload
    try { payload = event.data?.json() } catch { return }
    const binding = await pushBinding()
    if (pushGateClosed || !matchesPush(binding, payload)) return
    await self.registration.showNotification(typeof payload.title === 'string' ? payload.title.slice(0, 180) : 'RutCampusTrack', {
      body: typeof payload.body === 'string' ? payload.body.slice(0, 1500) : '',
      tag: `rct-${binding.fingerprint}-${typeof payload.event_type === 'string' ? payload.event_type : 'notification'}`,
      data: { recipientUserId: binding.userId, subscriptionFingerprint: binding.fingerprint },
    })
  }).catch(() => undefined))
})
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  event.waitUntil(pushSerial(async () => {
    const binding = await pushBinding()
    if (pushGateClosed || !matchesPush(binding, event.notification.data)) return
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const client of windows) {
      if (client.url.startsWith(self.registration.scope)) {
        await client.focus()
        client.postMessage({ type: 'RCT_PUSH_OPEN', userId: binding.userId })
        return
      }
    }
    // Push payload URLs are intentionally ignored: the existing history owns target authorization.
    const url = new URL(self.registration.scope)
    url.searchParams.set('pushNotifications', '1')
    url.searchParams.set('pushOwner', binding.userId)
    await self.clients.openWindow(url.href)
  }).catch(() => undefined))
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

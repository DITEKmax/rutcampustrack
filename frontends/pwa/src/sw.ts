/// <reference lib="webworker" />
import { precacheAndRoute } from 'workbox-precaching'
import { registerRoute } from 'workbox-routing'
import { StaleWhileRevalidate } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'
import { CacheableResponsePlugin } from 'workbox-cacheable-response'
import { isHeadmanApiRequest } from './sw-runtime-cache'

declare const self: ServiceWorkerGlobalScope

// Activate new SW immediately on update — users get fresh code without
// having to close every tab. Combined with registerType:'autoUpdate' in
// vite.config.ts this makes PWA self-updating on each navigation.
self.skipWaiting()
self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

precacheAndRoute(self.__WB_MANIFEST)

function getUrlForEventType(eventType: string): string {
  switch (eventType) {
    case 'lesson.started': return '/checkin'
    case 'lesson.cancelled': return '/schedule'
    default: return '/'
  }
}

// PUSHUI-01: Handle push events — show notification with deep-link data
self.addEventListener('push', (event) => {
  let payload: Record<string, unknown> = {}
  try {
    payload = event.data?.json() ?? {}
  } catch {
    payload = {}
  }
  const {
    title = 'RutTrack',
    body = '',
    event_type = '',
    data = {},
  } = payload as {
    title?: string
    body?: string
    event_type?: string
    data?: Record<string, unknown>
  }

  const url = getUrlForEventType(event_type)

  // PUSHUI-04: Foreground suppression — skip notification if PWA window is focused
  const promiseChain = self.clients
    .matchAll({ type: 'window', includeUncontrolled: true })
    .then((clients) => {
      const isFocused = clients.some((c) => (c as WindowClient).focused)
      if (isFocused) return
      // `silent: false` + `vibrate` ensure the device plays its default
      // notification sound and haptics — Android respects `vibrate`, iOS
      // respects `silent`. Without these the PWA shows a silent banner which
      // users miss, especially with the phone in a pocket.
      return self.registration.showNotification(title, {
        body,
        icon: '/icons/icon-192.png',
        badge: '/icons/icon-192.png',
        data: { url, event_type, lessonId: (data as Record<string, unknown>).lesson_id },
        tag: `${event_type}-${(data as Record<string, unknown>).lesson_id ?? Date.now()}`,
        silent: false,
        vibrate: [200, 100, 200],
        renotify: true,
      } as NotificationOptions & { vibrate?: number[]; renotify?: boolean })
    })

  event.waitUntil(promiseChain)
})

// PUSHUI-02: Handle notification click — navigate to correct screen
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const urlToOpen = new URL(
    event.notification.data?.url ?? '/',
    self.location.origin
  ).href

  const promiseChain = self.clients
    .matchAll({ type: 'window', includeUncontrolled: true })
    .then((clients) => {
      for (const client of clients) {
        if (client.url === urlToOpen) {
          return (client as WindowClient).focus()
        }
      }
      return self.clients.openWindow(urlToOpen)
    })

  event.waitUntil(promiseChain)
})

// ──────────────────────────────────────────────────────────────
// Phase 56: Runtime caching for headman GET endpoints (PWA-HEAD-04)
// Strategy: Stale-While-Revalidate
// Cache: headman-api-cache-v1 (24h TTL, 100 entries max, GET-only, 200-only)
// ──────────────────────────────────────────────────────────────

registerRoute(
  ({ url, request }) => request.method === 'GET' && isHeadmanApiRequest(url),
  new StaleWhileRevalidate({
    cacheName: 'headman-api-cache-v1',
    plugins: [
      new CacheableResponsePlugin({ statuses: [200] }),
      new ExpirationPlugin({
        maxAgeSeconds: 86400,      // 24 hours
        maxEntries: 100,
      }),
    ],
  })
)

/// <reference lib="webworker" />
import { precacheAndRoute } from 'workbox-precaching'
import { registerRoute } from 'workbox-routing'
import { StaleWhileRevalidate } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'
import { CacheableResponsePlugin } from 'workbox-cacheable-response'
import { isHeadmanApiRequest } from './sw-runtime-cache'
import { getUrlForEventType, PWA_ICON_URL } from './features/push/pushUtils'

declare const self: ServiceWorkerGlobalScope

// Activate new SW immediately on update — users get fresh code without
// having to close every tab. Combined with registerType:'autoUpdate' in
// vite.config.ts this makes PWA self-updating on each navigation.
self.skipWaiting()
self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

precacheAndRoute(self.__WB_MANIFEST)

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

  // Always show the OS notification for Web Push. The user explicitly enabled
  // device notifications, and suppressing foreground push makes installed PWAs
  // look broken when the app is open in the background task switcher.
  const promiseChain = self.registration.showNotification(title, {
    body,
    icon: PWA_ICON_URL,
    badge: PWA_ICON_URL,
    data: { url, event_type, lessonId: (data as Record<string, unknown>).lesson_id },
    tag: `${event_type}-${(data as Record<string, unknown>).lesson_id ?? Date.now()}`,
    silent: false,
    vibrate: [200, 100, 200],
    renotify: true,
  } as NotificationOptions & { vibrate?: number[]; renotify?: boolean })

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
    .then(async (clients) => {
      for (const client of clients) {
        if (client.url === urlToOpen) {
          return (client as WindowClient).focus()
        }
      }
      for (const client of clients) {
        const windowClient = client as WindowClient
        const currentUrl = new URL(windowClient.url)
        if (
          currentUrl.origin === self.location.origin &&
          currentUrl.pathname.startsWith('/app/')
        ) {
          const navigated = await windowClient.navigate(urlToOpen)
          return (navigated ?? windowClient).focus()
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

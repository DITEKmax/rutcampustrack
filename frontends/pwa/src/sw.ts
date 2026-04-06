/// <reference lib="webworker" />
import { precacheAndRoute } from 'workbox-precaching'

declare const self: ServiceWorkerGlobalScope

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
      return self.registration.showNotification(title, {
        body,
        icon: '/icons/icon-192.png',
        badge: '/icons/icon-192.png',
        data: { url, event_type, lessonId: (data as Record<string, unknown>).lesson_id },
        tag: `${event_type}-${(data as Record<string, unknown>).lesson_id ?? Date.now()}`,
      })
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

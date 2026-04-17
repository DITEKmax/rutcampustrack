export function getUrlForEventType(eventType: string): string {
  switch (eventType) {
    case 'lesson.started': return '/checkin'
    case 'lesson.cancelled': return '/schedule'
    case 'homework.published':
    case 'homework.updated':
      return '/homework'
    default: return '/'
  }
}

export function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  return new Uint8Array([...rawData].map((char) => char.charCodeAt(0)))
}

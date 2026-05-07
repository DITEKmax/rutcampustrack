export const APP_BASE_PATH = '/app'
export const PWA_ICON_URL = `${APP_BASE_PATH}/icons/icon-192.png`

export function appPath(path = '/'): string {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${APP_BASE_PATH}${normalized === '/' ? '/' : normalized}`
}

export function getUrlForEventType(eventType: string): string {
  switch (eventType) {
    case 'lesson.started':
    case 'lesson.reminder':
    case 'attendance.marked':
      return appPath('/checkin')
    case 'lesson.cancelled':
    case 'lesson.one_off.created':
    case 'lesson.one_off.cancelled':
      return appPath('/schedule')
    case 'homework.published':
    case 'homework.updated':
    case 'homework.weekly_digest':
    case 'homework.due_reminder':
      return appPath('/homework')
    case 'late_checkin.requested':
      return appPath('/group/late-checkin')
    case 'excuse.requested':
      return appPath('/group/excuses')
    case 'group.renamed':
    case 'group.archived':
      return appPath('/group')
    default:
      return appPath('/notifications')
  }
}

export function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  return new Uint8Array([...rawData].map((char) => char.charCodeAt(0)))
}

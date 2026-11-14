import { initialNotifications, previewNow, profile } from '../../../web-vue/src/dev/visual-review-fixtures'
import { requests } from '../../../web-vue/src/dev/headman-visual-data'
import { parseNotificationPreferencesV2 } from '../../../mobile-core/src/features/notifications/notifications-v2-client'
import type { NotificationPreferencesV2 } from '../../../mobile-core/src/features/notifications/notifications-v2-models'
import type { PreviewHandler, PreviewRole } from '../../../pwa-vue/src/dev/interactive-mock-transport'
import { demoNotificationCatalog, initialDemoNotificationPreferences } from './pwa-notification-settings'

const noContent = () => new Response(null, { status: 204 })
const authPath = '/api/auth/'
const preferencesPath = '/api/notifications/preferences/v2'

export function demoNotificationSeed(role: PreviewRole): typeof initialNotifications {
  const first = initialNotifications[0]!
  const item = { ...first, details: [] }
  if (role === 'HEADMAN') return [{
    ...item, id: '201', type: 'EXCUSE_REQUESTED', heading: 'Новая заявка на уважительную причину',
    summary: requests[0]!.summary.studentName ?? 'Новая заявка в группе',
    action: { kind: 'request', role, requestKind: 'EXCUSE', requestId: requests[0]!.summary.id },
  }]
  if (role === 'TEACHER') return [{
    ...item, id: '301', type: 'LESSON_UPDATED', heading: 'Проверь журнал группы ИВТ-241',
    summary: 'В журнале доступны результаты прошедших занятий.',
    action: { kind: 'lesson', role, lessonId: '401' },
  }]
  if (role === 'ADMIN') return [{
    ...item, id: '401', type: 'SEMESTER_ACTIVATED', heading: 'Активирован учебный семестр',
    summary: 'Осенний семестр доступен в реестре.', action: null,
  }]
  return initialNotifications
}

export function createProfileDemo(): PreviewHandler {
  const settings = new Map<PreviewRole, NotificationPreferencesV2>()
  const removedSessions = new Set<PreviewRole>()
  const history = new Map<PreviewRole, { id: string; type: string; occurredAt: string; clientLabel: string; authMethod: string }[]>()
  const extraSession: Record<PreviewRole, string> = {
    STUDENT: '20000000-0000-4000-8000-000000000001',
    HEADMAN: '20000000-0000-4000-8000-000000000002',
    TEACHER: '20000000-0000-4000-8000-000000000003',
    ADMIN: '20000000-0000-4000-8000-000000000004',
  }
  return async (url, method, body, role) => {
    const path = url.pathname
    if (path === '/api/notifications/catalog' && method === 'GET')
      return Response.json(demoNotificationCatalog)
    if (path === preferencesPath) {
      if (method === 'GET') return Response.json(settings.get(role) ?? initialDemoNotificationPreferences())
      if (method === 'PUT') {
        const update = await body()
        const current = settings.get(role) ?? initialDemoNotificationPreferences()
        if (update.expectedRevision !== current.revision)
          return Response.json({ currentPreferences: current }, { status: 409 })
        try {
          const next = parseNotificationPreferencesV2({
            ...current,
            revision: String(Number(current.revision) + 1),
            splitChannels: update.splitChannels,
            types: update.types,
            mutedUntil: 'mutedUntil' in update ? update.mutedUntil : current.mutedUntil,
          })
          if (Object.keys(next.types).length !== demoNotificationCatalog.types.length)
            return Response.json({ title: 'Настройки не соответствуют каталогу' }, { status: 400 })
          settings.set(role, next)
          return Response.json(next)
        } catch {
          return Response.json({ title: 'Некорректные настройки уведомлений' }, { status: 400 })
        }
      }
    }
    if (!path.startsWith(authPath)) return null
    const route = path.slice(authPath.length)
    const activity = (type: string) => {
      const current = history.get(role) ?? []
      current.unshift({ id: crypto.randomUUID(), type, occurredAt: new Date().toISOString(), clientLabel: 'Демо PWA', authMethod: 'PASSWORD' })
      history.set(role, current)
    }
    if (route === 'sessions' && method === 'GET') {
      const current = { sessionId: profile.sessionId, current: true, authMethod: 'PASSWORD', clientLabel: 'Демо PWA', createdAt: previewNow, lastSeenAt: previewNow }
      const other = { ...current, sessionId: extraSession[role], current: false, clientLabel: 'Демо · другой браузер' }
      return Response.json({ items: removedSessions.has(role) ? [current] : [current, other], nextCursor: null })
    }
    if (route === 'account-history' && method === 'GET') return Response.json({ items: [
      ...history.get(role) ?? [], { id: extraSession[role], type: 'LOGIN', occurredAt: previewNow, authMethod: 'PASSWORD', clientLabel: 'Демо PWA' },
    ], nextCursor: null })
    if (route === `sessions/${extraSession[role]}` && method === 'DELETE') {
      removedSessions.add(role)
      activity('SECURITY_REVOKED')
      return noContent()
    }
    if ((route === 'logout-all' || route === 'change-password') && method === 'POST') {
      removedSessions.add(role)
      activity(route === 'logout-all' ? 'LOGOUT_ALL' : 'PASSWORD_CHANGED')
      return noContent()
    }
    if (route === 'password-reset/request' && method === 'POST')
      return Response.json({ challengeId: crypto.randomUUID(), ttlSeconds: 600 }, { status: 202 })
    if (route === 'password-reset/verify' && method === 'POST')
      return Response.json({ resetTicket: crypto.randomUUID(), expiresInSeconds: 600, attemptsRemaining: 3 })
    if (route === 'password-reset/complete' && method === 'POST') return noContent()
    return null
  }
}

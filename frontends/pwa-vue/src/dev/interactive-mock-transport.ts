import { attendance, homework, initialNotifications, overview, preferences, previewNow, profile, rankingPage, requestDetail, requestOptions, requestPage, schedule, studentSession, subjectDetail, today } from '../../../web-vue/src/dev/visual-review-fixtures'
import { findPreset } from '../../../shared/profile/avatar-presets'
import { headmanPreview } from '../../../web-vue/src/dev/headman-visual-preview'
import { teacherPreview } from '../../../web-vue/src/dev/teacher-visual-preview'
import { adminPreview, campusPreview } from '../../../web-vue/src/dev/admin-visual-preview'
import type { ProfileSnapshot } from '../../../mobile-core/src/features/profile/profile-types'

/** Isolated PWA state; wire fixtures and role handlers are shared with the WEB preview. */
export function createInteractiveMockTransport(): typeof fetch {
  let items = structuredClone(initialNotifications)
  let homeworkItems = structuredClone(homework.items)
  let deviceTheme: string | null = null
  let themeRevision = 1
  const roleKey = 'rct:pwa-interactive:active-role'
  const roles = ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'] as const
  type PreviewRole = typeof roles[number]
  const isRole = (value: unknown): value is PreviewRole => roles.some(role => role === value)
  let activeRole: PreviewRole = 'STUDENT'
  try { const saved = sessionStorage.getItem(roleKey); if (isRole(saved)) activeRole = saved } catch { /* In-memory role still works. */ }
  let sessionVersion = 1
  const session = (): ProfileSnapshot => ({ ...profile, activeRole, sessionVersion: String(sessionVersion),
    roles: [{ grantId: '1', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false, groupId: '17' },
      { grantId: '2', role: 'HEADMAN', status: 'ACTIVE', selectable: true, readOnly: false, groupId: '17' },
      { grantId: '3', role: 'TEACHER', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: '4', role: 'ADMIN', status: 'ACTIVE', selectable: true, readOnly: false }] }) as ProfileSnapshot
  const avatarStorageKey = 'rct:pwa-interactive:avatar:42'
  let avatarId: string | null = null
  try { avatarId = findPreset(localStorage.getItem(avatarStorageKey))?.id ?? null } catch { /* The preview still works without persistence. */ }
  const unread = () => items.filter((item) => item.readAt === null).length
  const json = (value: unknown, status = 200) => Response.json(value, { status })
  // No fallback to native fetch: every unknown request is diagnosed and stays local.
  return async (input, init) => {
    const url = new URL(input instanceof Request ? input.url : String(input), location.origin)
    const method = (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()
    const path = url.pathname
    const body = async (): Promise<Record<string, unknown>> => {
      const value: unknown = init?.body ? JSON.parse(String(init.body)) : input instanceof Request ? await input.clone().json() : {}
      return value && typeof value === 'object' ? value as Record<string, unknown> : {}
    }
    if (url.origin === location.origin) {
      if (path === '/version.json' && method === 'GET') return json({ latest: '0.0.0', minimumSupported: '0.0.0', force: false })
      if (path === '/api/auth/refresh' && method === 'POST') return json({ accessToken: 'local-visual-preview-only' })
      if (path === '/api/auth/session' && method === 'GET') return json(session())
      if (path === '/api/auth/session/active-role' && method === 'PUT') {
        const command = await body()
        if (command.expectedSessionVersion !== String(sessionVersion)) return json({ title: 'Версия preview-сессии изменилась' }, 409)
        if (!isRole(command.role)) return json({ title: 'Роль недоступна в preview' }, 400)
        activeRole = command.role
        sessionVersion++
        try { sessionStorage.setItem(roleKey, activeRole) } catch { /* In-memory role still works. */ }
        return json({ accessToken: 'local-visual-preview-only', expiresIn: 3600, session: session() })
      }
      if (path === '/api/academic/users/me/avatar' && method === 'PATCH') {
        const command = await body()
        if (command.avatarId !== '' && !findPreset(String(command.avatarId)))
          return json({ title: 'Неизвестный аватар' }, 400)
        avatarId = command.avatarId ? String(command.avatarId) : null
        try {
          if (avatarId) localStorage.setItem(avatarStorageKey, avatarId)
          else localStorage.removeItem(avatarStorageKey)
        } catch { /* Current-page synthetic state remains usable. */ }
        return new Response(null, { status: 204 })
      }
      if (path === '/api/auth/device-preferences' && (method === 'GET' || method === 'PUT')) {
        if (method === 'PUT') {
          const command = await body()
          if (['system', 'light', 'dark'].includes(String(command.theme))) deviceTheme = String(command.theme)
          themeRevision++
        }
        return json({ theme: deviceTheme, revision: String(themeRevision) })
      }
      // Intentionally no websocket ticket: realtime cannot reach any backend.
      if (path === '/api/auth/ws-ticket') return json({ title: 'Realtime отключён в визуальном preview' }, 503)
      if (activeRole === 'HEADMAN') {
        const response = await headmanPreview(url, method, body)
        if (response) return response
      }
      if (activeRole === 'TEACHER' || activeRole === 'ADMIN') {
        try {
          const response = activeRole === 'TEACHER' ? await teacherPreview(url, method) : await adminPreview(url, method)
          if (response) return response
        } catch (cause) {
          console.warn(`Preview fixture: ${method} ${path}`, cause)
          return json({ title: 'Не удалось прочитать синтетические данные preview', detail: cause instanceof Error ? cause.message : 'Некорректный запрос' }, 422)
        }
      }
      if (path.startsWith('/api/v1/map/')) {
        const response = await campusPreview(url, method)
        if (response) return response
      }
      if (method === 'GET') {
        if (path === '/api/academic/users/me') return json({ id: 42, firstName: 'Анна', lastName: 'Смирнова', middleName: null, telegramId: null, avatarId })
        if (path === '/api/auth/sessions' || path === '/api/auth/account-history') return json({ items: [], nextCursor: null })
        if (path === '/api/academic/assistants/me/permissions') return json([])
        if (path === '/api/v1/student/session') return json({ ...studentSession, sessionVersion: String(sessionVersion), rolesVersion: profile.rolesVersion })
        if (path === '/api/v1/student/today') return json(today)
        if (path === '/api/v1/student/schedule') return json(schedule)
        if (path === '/api/v1/student/attendance') return json(attendance)
        if (path === '/api/v1/student/statistics') return json(overview)
        if (path === '/api/v1/student/statistics/ranking') {
          const page = url.searchParams.has('page') ? Number(url.searchParams.get('page')) : null
          const size = Number(url.searchParams.get('size') ?? 20)
          if ((page !== null && (!Number.isSafeInteger(page) || page < 0)) || !Number.isSafeInteger(size) || size < 1 || size > 100)
            return json({ title: 'Некорректная страница рейтинга' }, 400)
          return json(rankingPage(page, size))
        }
        if (path.startsWith('/api/v1/student/statistics/subjects/')) return json(subjectDetail(
          decodeURIComponent(path.slice('/api/v1/student/statistics/subjects/'.length)),
          url.searchParams.get('range') === 'days' ? 'days' : 'weeks', url.searchParams.getAll('types'),
        ))
        if (path === '/api/v1/student/requests/options') return json(requestOptions)
        if (path === '/api/v1/student/requests') {
          const bucket = url.searchParams.get('bucket') ?? 'OPEN'
          const page = Number(url.searchParams.get('page') ?? 0)
          const size = Number(url.searchParams.get('size') ?? 10)
          if (!['OPEN', 'ARCHIVE'].includes(bucket) || !Number.isSafeInteger(page) || page < 0 || !Number.isSafeInteger(size) || size < 1 || size > 100)
            return json({ title: 'Некорректная страница заявок' }, 400)
          return json(requestPage(bucket as 'OPEN' | 'ARCHIVE', page, size))
        }
        const request = path.match(/^\/api\/v1\/student\/requests\/([^/]+)$/)
        if (request) {
          const detail = requestDetail(decodeURIComponent(request[1]!))
          return detail ? json(detail) : json({ title: 'Заявка не найдена в визуальном preview' }, 404)
        }
        if (path === '/api/v1/student/homework') return json({ ...homework, items: homeworkItems,
          from: url.searchParams.get('from') ?? homework.from, to: url.searchParams.get('to') ?? homework.to })
        if (path === '/api/notifications/inbox') return json({ role: activeRole, items, nextCursor: null })
        if (path === '/api/notifications/inbox/unread-count') return json({ role: activeRole, count: unread() })
        if (path === '/api/notifications/preferences/v2') return json(preferences)
      }
      const mark = path.match(/^\/api\/notifications\/inbox\/([^/]+)\/read-state$/)
      if (mark && method === 'PUT') {
        const command = await body()
        const item = items.find((item) => item.id === mark[1])
        if (item) {
          item.readAt = command.read ? previewNow : null
          item.revision = String(Number(item.revision) + 1)
          return json({ role: activeRole, item, unreadCount: unread() })
        }
      }
      if (path === '/api/notifications/inbox/mark-all-read' && method === 'POST') {
        items = items.map((item) => ({ ...item, readAt: previewNow, revision: String(Number(item.revision) + 1) }))
        return json({ role: activeRole, unreadCount: 0 })
      }
      const completion = path.match(/^\/api\/v1\/student\/homework\/([^/]+)\/completion$/)
      if (completion && method === 'PUT') {
        const command = await body()
        if (!homeworkItems.some((item) => item.id === completion[1]))
          return json({ title: 'Домашнее задание не найдено в локальном mock' }, 404)
        if (typeof command.completed !== 'boolean')
          return json({ title: 'Укажи состояние выполнения домашнего задания' }, 400)
        const ack = { id: completion[1], completed: command.completed === true, completedAt: command.completed ? previewNow : null }
        homeworkItems = homeworkItems.map((item) => item.id === ack.id ? { ...item, ...ack, id: item.id } : item)
        return json(ack)
      }
    }
    const diagnostic = `Preview: ${method} ${path} не поддерживается. Запрос не отправлен.`
    console.warn(diagnostic)
    return json({ title: 'Операция недоступна в визуальном preview', detail: diagnostic }, 501)
  }

}

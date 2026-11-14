import { attendance, homework, initialNotifications, overview, preferences, previewNow, profile, rankingPage, requestDetail, requestOptions, requestPage, schedule, studentSession, subjectDetail, today } from '../../../web-vue/src/dev/visual-review-fixtures'
import { findPreset } from '../../../shared/profile/avatar-presets'
import { assistants, groupId, members, permissions, semester } from '../../../web-vue/src/dev/headman-visual-data'
import { headmanPreview } from '../../../web-vue/src/dev/headman-visual-preview'
import { teacherPreview } from '../../../web-vue/src/dev/teacher-visual-preview'
import { adminPreview, campusPreview } from '../../../web-vue/src/dev/admin-visual-preview'
import type {
  HeadmanAssistant,
  HeadmanAssistantPermission,
  HeadmanCredentialView,
  HeadmanLateCheckinBudget,
} from '../../../mobile-core/src/features/headman-group/headman-group-client'
import type { ProfileSnapshot } from '../../../mobile-core/src/features/profile/profile-types'

export type PreviewRole = 'STUDENT' | 'HEADMAN' | 'TEACHER' | 'ADMIN'
type BodyReader = () => Promise<Record<string, unknown>>
export type PreviewHandler = (url: URL, method: string, body: BodyReader, role: PreviewRole, source: RequestInit['body'], headers: Headers) => Promise<Response | null>

interface InteractiveMockOptions {
  persist?: boolean
  handle?: PreviewHandler
  notificationSeed?: (role: PreviewRole) => typeof initialNotifications
}

/** Isolated PWA state; wire fixtures and role handlers are shared with the WEB preview. */
export function createInteractiveMockTransport({ persist = true, handle, notificationSeed }: InteractiveMockOptions = {}): typeof fetch {
  const notifications = new Map<PreviewRole, typeof initialNotifications>()
  const notificationState = (role: PreviewRole) => {
    if (!notifications.has(role)) notifications.set(role, structuredClone(notificationSeed?.(role) ?? initialNotifications))
    return notifications.get(role)!
  }
  let homeworkItems = structuredClone(homework.items)
  const headmanGroup = createHeadmanGroupPreview()
  let deviceTheme: string | null = null
  let themeRevision = 1
  const roleKey = 'rct:pwa-interactive:active-role'
  const roles = ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'] as const
  const isRole = (value: unknown): value is PreviewRole => roles.some(role => role === value)
  let activeRole: PreviewRole = 'STUDENT'
  try { const saved = persist && sessionStorage.getItem(roleKey); if (isRole(saved)) activeRole = saved } catch { /* In-memory role still works. */ }
  let sessionVersion = 1
  const session = (): ProfileSnapshot => ({ ...profile, activeRole, sessionVersion: String(sessionVersion),
    roles: [{ grantId: '1', role: 'STUDENT', status: 'ACTIVE', selectable: true, readOnly: false, groupId: '17' },
      { grantId: '2', role: 'HEADMAN', status: 'ACTIVE', selectable: true, readOnly: false, groupId: '17' },
      { grantId: '3', role: 'TEACHER', status: 'ACTIVE', selectable: true, readOnly: false },
      { grantId: '4', role: 'ADMIN', status: 'ACTIVE', selectable: true, readOnly: false }] }) as ProfileSnapshot
  const avatarStorageKey = 'rct:pwa-interactive:avatar:42'
  const avatars = new Map<PreviewRole, string | null>()
  try { if (persist) avatars.set(activeRole, findPreset(localStorage.getItem(avatarStorageKey))?.id ?? null) } catch { /* The preview still works without persistence. */ }
  const json = (value: unknown, status = 200) => Response.json(value, { status })
  // No fallback to native fetch: every unknown request is diagnosed and stays local.
  return async (input, init) => {
    const url = new URL(input instanceof Request ? input.url : String(input), location.origin)
    const method = (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()
    const path = url.pathname
    const items = notificationState(activeRole)
    const unread = () => items.filter((item) => item.readAt === null).length
    const body = async (): Promise<Record<string, unknown>> => {
      const value: unknown = init?.body ? JSON.parse(String(init.body)) : input instanceof Request ? await input.clone().json() : {}
      return value && typeof value === 'object' ? value as Record<string, unknown> : {}
    }
    if (url.origin === location.origin) {
      if ((path === '/version.json' || path === '/presentation/version.json') && method === 'GET') return json({ latest: '0.0.0', minimumSupported: '0.0.0', force: false })
      if (['/api/auth/refresh', '/api/auth/login', '/api/auth/otp/verify-by-code'].includes(path) && method === 'POST') return json({ accessToken: 'local-visual-preview-only' })
      if (path === '/api/auth/session' && method === 'GET') return json(session())
      if (path === '/api/auth/session/active-role' && method === 'PUT') {
        const command = await body()
        if (command.expectedSessionVersion !== String(sessionVersion)) return json({ title: 'Версия preview-сессии изменилась' }, 409)
        if (!isRole(command.role)) return json({ title: 'Роль недоступна в preview' }, 400)
        activeRole = command.role
        sessionVersion++
        try { if (persist) sessionStorage.setItem(roleKey, activeRole) } catch { /* In-memory role still works. */ }
        return json({ accessToken: 'local-visual-preview-only', expiresIn: 3600, session: session() })
      }
      if (path === '/api/academic/users/me/avatar' && method === 'PATCH') {
        const command = await body()
        if (command.avatarId !== '' && !findPreset(String(command.avatarId)))
          return json({ title: 'Неизвестный аватар' }, 400)
        const avatarId = command.avatarId ? String(command.avatarId) : null
        avatars.set(activeRole, avatarId)
        try {
          if (persist && avatarId) localStorage.setItem(avatarStorageKey, avatarId)
          else if (persist) localStorage.removeItem(avatarStorageKey)
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
      if (path === '/api/auth/ws-ticket' || path === '/api/auth/logout') {
        if (path.endsWith('/logout')) return new Response(null, { status: 204 })
        return json({ title: 'Realtime отключён в визуальном preview' }, 503)
      }
      const local = await handle?.(url, method, body, activeRole, init?.body, new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined)))
      if (local) return local
      if (activeRole === 'HEADMAN') {
        const response = (await headmanGroup(url, method, body)) ?? (await headmanPreview(url, method, body))
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
        if (path === '/api/academic/users/me') return json({ id: 42, firstName: 'Анна', lastName: 'Смирнова', middleName: null, telegramId: null, avatarId: avatars.get(activeRole) ?? null })
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
        for (const item of items) {
          item.readAt = previewNow
          item.revision = String(Number(item.revision) + 1)
        }
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

const ASSISTANTS_PATH = '/api/academic/assistants'
const MAX_INT32 = 2147483647

function permissionSet(value: unknown): HeadmanAssistantPermission[] | null {
  if (!Array.isArray(value)) return null
  const requested = new Set(value)
  const selected = permissions.filter(({ code }) => requested.has(code)).map(({ code }) => code)
  return requested.size === selected.length ? selected : null
}

/** Mutable fixtures for the mobile stand. The shared Web preview remains read-only here. */
function createHeadmanGroupPreview() {
  const credentials = new Map<number, HeadmanCredentialView>()
  const budgets = new Map<number, HeadmanLateCheckinBudget>()
  const receipts = new Map<string, { increase: number; budget: HeadmanLateCheckinBudget }>()
  const assistantRows = new Map(assistants.map(row => [row.id, row]))
  let nextAssistantId = Math.max(0, ...assistantRows.keys()) + 1

  const json = (value: unknown, status = 200) => Response.json(value, { status })
  const privateJson = (value: unknown) => Response.json(value, { headers: { 'Cache-Control': 'no-store' } })
  const problem = (status: number, title: string) => json({ title }, status)
  const missing = () => problem(404, 'Синтетическая запись не найдена')
  const findMember = (id: unknown) => members.find(row => row.id === id && row.groupId === groupId && row.status === 'ACTIVE')

  return async (url: URL, method: string, body: () => Promise<Record<string, unknown>>): Promise<Response | null> => {
    const path = url.pathname
    const credential = path.match(/^\/api\/auth\/headman\/students\/(\d+)\/(initial-password|generated-password|password-reset-link)$/)
    if (credential) {
      const studentId = Number(credential[1])
      if (!findMember(studentId)) return missing()
      const current = credentials.get(studentId)
      if (method === 'GET' && credential[2] === 'initial-password')
        return current ? privateJson(current) : null
      if (method === 'POST' && credential[2] === 'generated-password') {
        const command: Record<string, unknown> = await body().catch(() => ({}))
        const expected = command.expectedCredentialsVersion
        if (typeof expected !== 'string' || !/^[1-9][0-9]{0,18}$/.test(expected) || BigInt(expected) > 9223372036854775807n)
          return problem(400, 'Некорректная версия доступа')
        // Read after parsing, so concurrent requests cannot both use the same version.
        const version = credentials.get(studentId)?.credentialsVersion ?? '1'
        if (version !== expected) return problem(409, 'Версия доступа изменилась')
        const credentialsVersion = String(BigInt(version) + 1n)
        const generated: HeadmanCredentialView = {
          studentId: String(studentId),
          credentialsVersion,
          state: 'GENERATED',
          origin: 'headman_reset',
          password: `preview-only-${studentId}-${credentialsVersion}`,
        }
        credentials.set(studentId, generated)
        return privateJson(generated)
      }
      if (method === 'POST' && credential[2] === 'password-reset-link') {
        const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
        const link = new URL('/password-reset', loopback ? url.origin : 'http://localhost')
        const expiresInSeconds = 3600
        const expiresAt = new Date(Date.now() + expiresInSeconds * 1000).toISOString()
        link.hash = new URLSearchParams({ resetTicket: `preview-only-${crypto.randomUUID()}`, expiresAt }).toString()
        return privateJson({ url: link.href, expiresAt, expiresInSeconds })
      }
    }

    const budget = path.match(/^\/api\/attendance\/requests\/students\/(\d+)\/late-checkin-budget$/)
    if (budget && (method === 'GET' || method === 'POST')) {
      const studentId = Number(budget[1])
      if (!findMember(studentId)) return missing()
      if (method === 'GET') {
        const saved = budgets.get(studentId)
        return saved ? json(saved) : null
      }
      const command: Record<string, unknown> = await body().catch(() => ({}))
      const { increase, requestKey, semesterId } = command
      if (typeof increase !== 'number' || !Number.isInteger(increase) || increase < 1 || increase > MAX_INT32 ||
          typeof requestKey !== 'string' || !/^[\x21-\x7e]{16,128}$/.test(requestKey) ||
          typeof semesterId !== 'number' || !Number.isSafeInteger(semesterId) || semesterId < 1)
        return problem(400, 'Нужны семестр, положительное число попыток и ключ запроса')
      if (semesterId !== semester.id) return problem(409, 'Текущий семестр изменился; обнови данные')
      const receipt = receipts.get(requestKey)
      if (receipt) {
        if (receipt.budget.studentId !== studentId || receipt.increase !== increase)
          return problem(409, 'Ключ уже использован для другого изменения лимита')
        return json(receipt.budget)
      }
      const before = budgets.get(studentId)
      const used = before?.used ?? studentId % 3
      const limit = (before?.limit ?? 5) + increase
      if (limit > MAX_INT32) return problem(409, 'Лимит попыток не помещается в допустимый диапазон')
      const after = { studentId, semesterId, limit, used, remaining: limit - used }
      budgets.set(studentId, after)
      receipts.set(requestKey, { increase, budget: after })
      return json(after)
    }

    if (path === ASSISTANTS_PATH && method === 'GET') {
      if (url.searchParams.get('groupId') !== String(groupId)) return problem(403, 'Группа недоступна')
      const number = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 100)
      if (!Number.isSafeInteger(number) || number < 0 || !Number.isSafeInteger(size) || size < 1 || size > 100)
        return problem(400, 'Некорректная страница preview')
      const active = [...assistantRows.values()].filter(row => row.active)
      return json({
        _embedded: { assistantResponseList: active.slice(number * size, (number + 1) * size) },
        page: { number, size, totalElements: active.length, totalPages: Math.ceil(active.length / size) },
      })
    }
    if (path === ASSISTANTS_PATH && method === 'POST') {
      const command: Record<string, unknown> = await body().catch(() => ({}))
      if (command.groupId !== groupId) return problem(403, 'Группа недоступна')
      const student = findMember(command.studentId)
      if (!student) return missing()
      if (!student.roles?.some(role => role.role === 'STUDENT' && role.status === 'ACTIVE' && role.groupId === groupId))
        return problem(403, 'Студент не имеет активного права в этой группе')
      const next = permissionSet(command.permissions)
      if (!next?.length) return problem(400, 'Укажи права помощника')
      if ([...assistantRows.values()].some(row => row.studentId === student.id && row.active))
        return problem(409, 'Студент уже является помощником')
      const row: HeadmanAssistant = {
        id: nextAssistantId++,
        studentId: student.id,
        studentName: student.fullName,
        login: student.login,
        groupId,
        permissions: next,
        active: true,
        assignedAt: new Date().toISOString(),
        revokedAt: null,
      }
      assistantRows.set(row.id, row)
      return json(row, 201)
    }
    const assistant = path.match(/^\/api\/academic\/assistants\/(\d+)\/permissions$/)
    if (assistant && method === 'PATCH') {
      const command: Record<string, unknown> = await body().catch(() => ({}))
      const row = assistantRows.get(Number(assistant[1]))
      if (!row) return missing()
      const next = permissionSet(command.permissions)
      const expected = permissionSet(command.expectedPermissions)
      if (!next || (expected === null && (!next.length || command.expectedPermissions != null)))
        return problem(400, 'Некорректный набор прав или снимок прав помощника')
      if (!row.active) return problem(409, 'Помощник уже отозван')
      if (expected && (expected.length !== row.permissions.length || !expected.every(code => row.permissions.includes(code))))
        return json({
          title: 'Права помощника изменились. Обнови список и подтверди изменения заново.',
          extras: { code: 'ASSISTANT_PERMISSIONS_CONFLICT' },
        }, 409)
      const updated = next.length
        ? { ...row, permissions: next }
        : { ...row, active: false, revokedAt: new Date().toISOString() }
      assistantRows.set(row.id, updated)
      return json(updated)
    }
    return null
  }
}

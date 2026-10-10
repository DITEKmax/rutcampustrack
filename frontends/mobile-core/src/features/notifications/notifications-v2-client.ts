import { StaleSessionGenerationError } from '../../shared/session-owner'
import { isHomeworkDate } from '../../domain/homework'
import { NotificationsApiError, type NotificationsSessionOwner } from './notifications-client'
import {
  notificationTypeIds, notificationPaletteIds, isCompleteNotificationTypeSet,
  type NotificationAction, type NotificationCatalog, type NotificationItem,
  type NotificationSignedRole, type NotificationTypeId, type NotificationPaletteId,
  type NotificationPreferencesV2, type NotificationPreferencesV2Update, type NotificationsV2Api,
} from './notifications-v2-models'

export interface NotificationsV2SessionOwner extends NotificationsSessionOwner {
  currentRole(): NotificationSignedRole | null
  /** Signed profile/session/permission revision, independent from token refresh. */
  currentContext(): string
}
export class NotificationsConflictError extends NotificationsApiError {
  constructor(readonly currentPreferences: NotificationPreferencesV2 | null) {
    super('Настройки изменились. Твой вариант сохранён на экране.', 409)
    this.name = 'NotificationsConflictError'
  }
}
const roles: readonly NotificationSignedRole[] = ['STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN']
const typeSet = new Set<string>(notificationTypeIds)
const paletteSet = new Set<string>(notificationPaletteIds)

export function createGenerationBoundNotificationsV2Api(
  owner: NotificationsV2SessionOwner,
  fetcher: typeof fetch = globalThis.fetch.bind(globalThis),
): NotificationsV2Api {
  const generation = owner.currentGeneration()
  const role = owner.currentRole()
  const context = owner.currentContext()
  if (!role) throw new StaleSessionGenerationError()
  function assertCurrent(): void {
    if (owner.currentGeneration() !== generation || owner.currentRole() !== role || owner.currentContext() !== context) {
      throw new StaleSessionGenerationError()
    }
  }
  function assertRole(value: unknown): void {
    if (value !== role) throw new NotificationsApiError('Роль ответа уведомлений не подтверждена', 403)
  }
  async function request(path: string, init: RequestInit = {}, retried = false): Promise<unknown> {
    assertCurrent()
    const token = owner.accessTokenFor(generation)
    if (!token) throw new NotificationsApiError('Сессия истекла', 401)
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    headers.set('Authorization', `Bearer ${token}`)
    if (init.body !== undefined) headers.set('Content-Type', 'application/json')
    let response: Response
    try { response = await fetcher(`/api/notifications${path}`, { ...init, headers, credentials: 'same-origin', cache: 'no-store' }) }
    catch (error) { assertCurrent(); throw error }
    assertCurrent()
    if (response.status === 401 && !retried) {
      await owner.refreshFor(generation)
      assertCurrent()
      return request(path, init, true)
    }
    let value: unknown
    try { value = response.status === 204 ? undefined : await response.json() }
    catch { assertCurrent(); throw new NotificationsApiError('Сервер вернул некорректный ответ', response.ok ? null : response.status) }
    assertCurrent()
    if (!response.ok) {
      if (response.status === 409 && path === '/preferences/v2') {
        const body = record(value)
        let current: NotificationPreferencesV2 | null = null
        try { current = parseNotificationPreferencesV2(body?.currentPreferences ?? record(body?.metadata)?.currentPreferences ?? body?.current ?? value) } catch { /* Conflict remains actionable without untrusted data. */ }
        throw new NotificationsConflictError(current)
      }
      throw new NotificationsApiError('Не удалось выполнить запрос уведомлений', response.status)
    }
    return value
  }
  return {
    async catalog() { return parseNotificationCatalog(await request('/catalog')) },
    async inbox(options = {}) {
      const params = new URLSearchParams({ limit: '20', unreadOnly: String(options.unreadOnly ?? false) })
      if (options.cursor !== undefined) params.set('cursor', boundedString(options.cursor, 4096))
      const body = requiredRecord(await request(`/inbox?${params}`))
      assertRole(body.role)
      if (!Array.isArray(body.items) || body.items.length > 100) invalid()
      return { role, items: body.items.map(parseNotificationItem), nextCursor: body.nextCursor === null ? null : boundedString(body.nextCursor, 4096) }
    },
    async unreadCount() {
      const body = requiredRecord(await request('/inbox/unread-count'))
      assertRole(body.role)
      return count(body.count)
    },
    async setReadState(item, read) {
      const body = requiredRecord(await request(`/inbox/${encodeURIComponent(safeId(item.id))}/read-state`, {
        method: 'PUT', body: JSON.stringify({ read, expectedRevision: boundedString(item.revision, 256) }),
      }))
      assertRole(body.role)
      const saved = parseNotificationItem(body.item)
      if (saved.id !== item.id || (saved.readAt !== null) !== read) invalid()
      return { role, item: saved, unreadCount: count(body.unreadCount) }
    },
    async markAllRead() {
      const body = requiredRecord(await request('/inbox/mark-all-read', { method: 'POST' }))
      assertRole(body.role)
      return { role, unreadCount: count(body.unreadCount) }
    },
    async getPreferences() { return parseNotificationPreferencesV2(await request('/preferences/v2')) },
    async updatePreferences(value: NotificationPreferencesV2Update) {
      const types = parseTypes(value.types)
      if (typeof value.splitChannels !== 'boolean' || !value.splitChannels && Object.values(types).some(entry => entry.appPush !== entry.telegram)) invalid()
      const body: NotificationPreferencesV2Update = {
        expectedRevision: boundedString(value.expectedRevision, 256), splitChannels: value.splitChannels, types,
      }
      if (Object.prototype.hasOwnProperty.call(value, 'mutedUntil')) body.mutedUntil = nullableInstant(value.mutedUntil)
      return parseNotificationPreferencesV2(await request('/preferences/v2', { method: 'PUT', body: JSON.stringify(body) }))
    },
  }
}
export function parseNotificationItem(value: unknown): NotificationItem {
  const body = requiredRecord(value)
  const type = typeId(body.type)
  if (!Array.isArray(body.details) || body.details.length > 100) invalid()
  const details = body.details.map(raw => {
    const field = requiredRecord(raw)
    return { ...(field.label === undefined ? {} : { label: boundedString(field.label, 160) }), value: boundedString(field.value, 10000) }
  })
  return {
    id: safeId(body.id), type, heading: boundedString(body.heading, 300),
    ...(body.summary === undefined ? {} : { summary: boundedString(body.summary, 2000) }),
    sentAt: instant(body.sentAt), orderAt: instant(body.orderAt), readAt: nullableInstant(body.readAt),
    revision: boundedString(body.revision, 256), details,
    action: parseNotificationAction(body.action),
  }
}
export function parseNotificationAction(value: unknown): NotificationAction | null {
  if (value === null) return null
  const body = record(value)
  if (!body || !roles.includes(body.role as NotificationSignedRole)) return null
  const role = body.role as NotificationSignedRole
  const date = body.lessonDate
  if (date !== undefined && (typeof date !== 'string' || !isHomeworkDate(date))) return null
  const optionalDate = date === undefined ? {} : { lessonDate: date as string }
  switch (body.kind) {
    case 'lesson': return positiveId(body.lessonId) ? { kind: 'lesson', role, lessonId: body.lessonId as string, ...optionalDate } : null
    case 'homework': return positiveId(body.homeworkId) ? { kind: 'homework', role, homeworkId: body.homeworkId as string, ...optionalDate } : null
    case 'group': return positiveId(body.groupId) ? { kind: 'group', role, groupId: body.groupId as string } : null
    case 'request': return typeof body.requestId === 'string' && /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(body.requestId) && (body.requestKind === 'EXCUSE' || body.requestKind === 'LATE_CHECKIN')
      ? { kind: 'request', role, requestId: body.requestId, requestKind: body.requestKind } : null
    case 'homework-week': return typeof body.weekStart === 'string' && isHomeworkDate(body.weekStart) ? { kind: 'homework-week', role, weekStart: body.weekStart } : null
    default: return null
  }
}
export function parseNotificationCatalog(value: unknown): NotificationCatalog {
  const body = requiredRecord(value)
  if (body.schemaVersion !== 2 || !Array.isArray(body.groups) || !Array.isArray(body.types) || !Array.isArray(body.palette)) invalid()
  const groups = body.groups.map(raw => { const group = requiredRecord(raw); return { id: safeId(group.id), label: boundedString(group.label, 160) } })
  const types = body.types.map(raw => {
    const entry = requiredRecord(raw)
    if (!Array.isArray(entry.roles) || entry.roles.some(role => !roles.includes(role as NotificationSignedRole))) invalid()
    const groupId = safeId(entry.groupId)
    if (!groups.some(group => group.id === groupId)) invalid()
    return { id: typeId(entry.id), groupId, label: boundedString(entry.label, 160), roles: entry.roles as NotificationSignedRole[] }
  })
  const palette = body.palette.map(raw => { const entry = requiredRecord(raw); return { id: paletteId(entry.id), label: boundedString(entry.label, 160) } })
  if (!isCompleteNotificationTypeSet(types.map(entry => entry.id))
    || new Set(palette.map(entry => entry.id)).size !== notificationPaletteIds.length || palette.length !== notificationPaletteIds.length) invalid()
  return { schemaVersion: 2, revision: boundedString(body.revision, 256), groups, types, palette }
}
export function parseNotificationPreferencesV2(value: unknown): NotificationPreferencesV2 {
  const body = requiredRecord(value)
  if (typeof body.splitChannels !== 'boolean' || typeof body.telegramLinked !== 'boolean') invalid()
  const types = parseTypes(body.types)
  if (!body.splitChannels && Object.values(types).some(entry => entry.appPush !== entry.telegram)) invalid()
  return { revision: boundedString(body.revision, 256), splitChannels: body.splitChannels, types, mutedUntil: nullableInstant(body.mutedUntil), telegramLinked: body.telegramLinked }
}
function parseTypes(value: unknown): NotificationPreferencesV2['types'] {
  const body = requiredRecord(value)
  if (!isCompleteNotificationTypeSet(Object.keys(body))) invalid()
  return Object.fromEntries(Object.keys(body).map(id => {
    const entry = requiredRecord(body[id])
    if (typeof entry.appPush !== 'boolean' || typeof entry.telegram !== 'boolean') invalid()
    return [id, { appPush: entry.appPush, telegram: entry.telegram, colorId: paletteId(entry.colorId) }]
  })) as NotificationPreferencesV2['types']
}
function record(value: unknown): Record<string, unknown> | null { return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null }
function requiredRecord(value: unknown): Record<string, unknown> { const body = record(value); if (!body) invalid(); return body }
function invalid(): never { throw new NotificationsApiError('Сервер вернул некорректные данные уведомлений') }
function boundedString(value: unknown, maximum: number): string { if (typeof value !== 'string' || !value.trim() || value.length > maximum || [...value].some(character => { const code = character.charCodeAt(0); return code < 32 && code !== 9 && code !== 10 && code !== 13 || code === 127 })) invalid(); return value }
function safeId(value: unknown): string { const id = boundedString(value, 128); if (!/^[A-Za-z0-9_-]+$/.test(id)) invalid(); return id }
function typeId(value: unknown): NotificationTypeId { if (typeof value !== 'string' || !typeSet.has(value)) invalid(); return value as NotificationTypeId }
function paletteId(value: unknown): NotificationPaletteId { if (typeof value !== 'string' || !paletteSet.has(value)) invalid(); return value as NotificationPaletteId }
function count(value: unknown): number { if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) invalid(); return value }
function instant(value: unknown): string { if (typeof value !== 'string' || !/^\d{4}-\d\d-\d\dT/.test(value) || !Number.isFinite(Date.parse(value))) invalid(); return value }
function nullableInstant(value: unknown): string | null { return value === null ? null : instant(value) }
function positiveId(value: unknown): value is string { return typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= 9_223_372_036_854_775_807n }

import { StaleSessionGenerationError } from '../../shared/session-owner'
import { isHomeworkDate } from '../../domain/homework'

export type NotificationCategoryKey =
  | 'lessons'
  | 'reminders'
  | 'homework'
  | 'tickets'
  | 'schedule'
  | 'group'

export interface NotificationHistoryItem {
  id: string
  type: string
  sentAt: string
  readAt: string | null
  context: readonly { label: string; value: string }[]
  target: NotificationTarget | null
}

export interface NotificationHomeworkTarget {
  kind: 'homework'
  homeworkId: string
  lessonDate: string
}

export interface NotificationRequestTarget {
  kind: 'request'
  requestId: string
  requestKind: 'EXCUSE' | 'LATE_CHECKIN'
}

export interface NotificationLessonTarget {
  kind: 'lesson'
  lessonId: string
}

export type NotificationTarget =
  | NotificationHomeworkTarget
  | NotificationRequestTarget
  | NotificationLessonTarget

export interface NotificationTargetIntent {
  requestId: number
  generation: number
  ownerKey: string
  target: NotificationTarget
}

export interface NotificationHistoryPage {
  items: NotificationHistoryItem[]
  pageNumber: number
  totalPages: number
  totalElements: number
}

export interface NotificationPreferences {
  categories: Record<NotificationCategoryKey, boolean>
  mutedUntil: string | null
}

export interface NotificationPreferencesUpdate {
  categories: Record<NotificationCategoryKey, boolean>
  mutedUntil: string | null
}

export interface NotificationsApi {
  listHistory(page: number, unreadOnly?: boolean): Promise<NotificationHistoryPage>
  unreadCount(): Promise<number>
  markRead(id: string): Promise<void>
  markAllRead(): Promise<void>
  getPreferences(): Promise<NotificationPreferences>
  updatePreferences(value: NotificationPreferencesUpdate): Promise<NotificationPreferences>
}

export interface NotificationsSessionOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export class NotificationsApiError extends Error {
  readonly status: number | null

  constructor(message: string, status: number | null = null) {
    super(message)
    this.name = 'NotificationsApiError'
    this.status = status
  }
}

const categoryKeys: readonly NotificationCategoryKey[] = [
  'lessons', 'reminders', 'homework', 'tickets', 'schedule', 'group',
]

const contextLabels: Readonly<Record<string, string>> = {
  subjectName: 'Предмет',
  lessonDate: 'Дата',
  weekStart: 'Неделя с',
  lessonNumber: 'Занятие',
}

export function createGenerationBoundNotificationsApi(
  owner: NotificationsSessionOwner,
  fetcher: typeof fetch = globalThis.fetch.bind(globalThis),
): NotificationsApi {
  const generation = owner.currentGeneration()

  function assertCurrent(): void {
    if (owner.currentGeneration() !== generation) throw new StaleSessionGenerationError()
  }

  async function request(path: string, init: RequestInit = {}, retried = false): Promise<unknown> {
    assertCurrent()
    const token = owner.accessTokenFor(generation)
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/hal+json, application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    if (init.body !== undefined) headers.set('Content-Type', 'application/json')

    let response: Response
    try {
      response = await fetcher(`/api${path}`, {
        ...init,
        headers,
        credentials: 'same-origin',
      })
    } catch (error) {
      assertCurrent()
      throw error
    }
    assertCurrent()

    if (response.status === 401 && !retried) {
      try {
        await owner.refreshFor(generation)
      } catch (error) {
        assertCurrent()
        const status = errorStatus(error)
        throw new NotificationsApiError(
          status === 401 ? 'Сессия истекла. Войди в приложение ещё раз.' : 'Не удалось обновить сессию.',
          status,
        )
      }
      assertCurrent()
      return request(path, init, true)
    }
    if (!response.ok) {
      throw new NotificationsApiError('Не удалось выполнить запрос уведомлений', response.status)
    }
    if (response.status === 204) return undefined

    try {
      const result: unknown = await response.json()
      assertCurrent()
      return result
    } catch (error) {
      assertCurrent()
      if (error instanceof StaleSessionGenerationError) throw error
      throw new NotificationsApiError('Сервер вернул некорректный ответ')
    }
  }

  return {
    async listHistory(page, unreadOnly = false) {
      const params = new URLSearchParams({
        page: String(nonNegativeInteger(page, 'page')),
        size: '20',
        unreadOnly: String(unreadOnly),
        sort: 'sentAt,desc',
      })
      return parseHistoryPage(await request(`/notifications?${params.toString()}`))
    },
    async unreadCount() {
      const value = asRecord(await request('/notifications/unread-count'))
      const count = value?.count
      if (typeof count !== 'number' || !Number.isSafeInteger(count) || count < 0) {
        throw new NotificationsApiError('Сервер вернул некорректный счётчик уведомлений')
      }
      return count
    },
    async markRead(id) {
      const safeId = notificationId(id)
      await request(`/notifications/${safeId}/read`, { method: 'PATCH' })
    },
    async markAllRead() {
      await request('/notifications/mark-all-read', { method: 'POST' })
    },
    async getPreferences() {
      return parsePreferences(await request('/notifications/preferences'))
    },
    async updatePreferences(value) {
      return parsePreferences(await request('/notifications/preferences', {
        method: 'PUT',
        body: JSON.stringify({
          categories: normalizeCategories(value.categories),
          mutedUntil: value.mutedUntil,
        }),
      }))
    },
  }
}

export function notificationsErrorMessage(error: unknown): string {
  if (error instanceof NotificationsApiError) {
    switch (error.status) {
      case 400: return 'Запрос уведомлений не принят. Обнови экран и попробуй снова.'
      case 401: return 'Сессия истекла. Войди в приложение ещё раз.'
      case 403: return 'Доступ к уведомлениям сейчас недоступен.'
      case 409: return 'Данные изменились. Обнови экран и попробуй снова.'
      case 429: return 'Слишком много запросов. Подожди немного и повтори.'
      case 503: return 'Сервис уведомлений временно недоступен. Попробуй позже.'
      default: return error.status === null
        ? error.message
        : 'Не удалось загрузить уведомления. Попробуй ещё раз.'
    }
  }
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return 'Нет подключения к интернету. Проверь соединение и попробуй снова.'
  }
  return 'Не удалось связаться с сервисом уведомлений. Проверь соединение и попробуй ещё раз.'
}

function parseHistoryPage(value: unknown): NotificationHistoryPage {
  const record = asRecord(value)
  const embedded = asRecord(record?._embedded)
  const page = asRecord(record?.page)
  if (!record || !page) throw new NotificationsApiError('Сервер вернул некорректную историю')
  const pageNumber = nonNegativeInteger(page.number, 'page.number')
  const totalPages = nonNegativeInteger(page.totalPages, 'page.totalPages')
  const totalElements = nonNegativeInteger(page.totalElements, 'page.totalElements')
  // Spring HATEOAS omits _embedded when the personal history is empty.
  if (!embedded && !(record._embedded === undefined && totalElements === 0 && totalPages === 0)) {
    throw new NotificationsApiError('Сервер вернул некорректную историю')
  }
  const rawItems = embedded?.notificationHistoryDtoList
  if (rawItems !== undefined && !Array.isArray(rawItems)) {
    throw new NotificationsApiError('Сервер вернул некорректную историю')
  }
  return {
    items: (Array.isArray(rawItems) ? rawItems : []).flatMap((item) => {
      const parsed = parseHistoryItem(item)
      return parsed ? [parsed] : []
    }),
    pageNumber,
    totalPages,
    totalElements,
  }
}

function parseHistoryItem(value: unknown): NotificationHistoryItem | null {
  const record = asRecord(value)
  if (!record || typeof record.id !== 'string' || !isSafeId(record.id)
    || typeof record.type !== 'string' || typeof record.sentAt !== 'string'
    || (record.readAt !== null && typeof record.readAt !== 'string')) return null
  return {
    id: record.id,
    type: /^[A-Z_]{1,80}$/.test(record.type) ? record.type : 'UNKNOWN',
    sentAt: record.sentAt,
    readAt: typeof record.readAt === 'string' ? record.readAt : null,
    context: safeContext(record.payload),
    target: parseTarget(record.type, record.payload),
  }
}

function parseTarget(type: string, value: unknown): NotificationTarget | null {
  const payload = asRecord(value)
  if (!payload) return null
  if (type === 'HOMEWORK_PUBLISHED' || type === 'HOMEWORK_UPDATED') {
    return parseHomeworkTarget(payload)
  }
  if (type === 'HOMEWORK_DUE_REMINDER') {
    const homework = asRecord(payload.homework)
    return homework ? parseHomeworkTarget(homework) : null
  }
  if (type === 'EXCUSE_REQUESTED' || type === 'EXCUSE_APPROVED' || type === 'EXCUSE_REJECTED') {
    const requestId = safeRequestId(payload.ticket_id)
    return requestId ? { kind: 'request', requestId, requestKind: 'EXCUSE' } : null
  }
  if (type === 'LATE_CHECKIN_REQUESTED' || type === 'LATE_CHECKIN_APPROVED' || type === 'LATE_CHECKIN_REJECTED') {
    const requestId = safeRequestId(payload.request_id)
    return requestId ? { kind: 'request', requestId, requestKind: 'LATE_CHECKIN' } : null
  }
  if (type === 'LESSON_STARTED' || type === 'LESSON_CANCELLED' || type === 'ATTENDANCE_MARKED_BY_HEADMAN') {
    const lessonId = positiveSafeId(payload.lesson_id)
    return lessonId ? { kind: 'lesson', lessonId } : null
  }
  return null
}

function parseHomeworkTarget(payload: Record<string, unknown>): NotificationHomeworkTarget | null {
  const homeworkId = positiveSafeId(payload.homework_id)
  const lessonDate = payload.lesson_date
  if (!homeworkId || typeof lessonDate !== 'string' || !isHomeworkDate(lessonDate)) return null
  return { kind: 'homework', homeworkId, lessonDate }
}

function safeRequestId(value: unknown): string | null {
  return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(value)
    ? value
    : null
}

function positiveSafeId(value: unknown): string | null {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value > 0 ? String(value) : null
  }
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? value : null
}

function safeContext(value: unknown): NotificationHistoryItem['context'] {
  const payload = asRecord(value)
  if (!payload) return []
  const result: { label: string; value: string }[] = []
  for (const [key, label] of Object.entries(contextLabels)) {
    const raw = payload[key]
    const text = typeof raw === 'number' && Number.isSafeInteger(raw)
      ? String(raw)
      : typeof raw === 'string' ? raw.trim() : ''
    const hasControlCharacter = [...text].some((character) => {
      const code = character.charCodeAt(0)
      return code < 32 || code === 127
    })
    if (!text || text.length > 80 || /[<>]/.test(text) || hasControlCharacter || /https?:\/\//i.test(text)) continue
    result.push({ label, value: text })
  }
  return result
}

function parsePreferences(value: unknown): NotificationPreferences {
  const record = asRecord(value)
  const categories = asRecord(record?.categories)
  if (!record || !categories) throw new NotificationsApiError('Сервер вернул некорректные настройки')
  const rawMutedUntil = record.mutedUntil
  if (rawMutedUntil !== null && typeof rawMutedUntil !== 'string') {
    throw new NotificationsApiError('Сервер вернул некорректные настройки')
  }
  if (typeof rawMutedUntil === 'string' && !Number.isFinite(Date.parse(rawMutedUntil))) {
    throw new NotificationsApiError('Сервер вернул некорректные настройки')
  }
  return {
    categories: normalizeCategories(categories),
    mutedUntil: typeof rawMutedUntil === 'string' ? rawMutedUntil : null,
  }
}

function normalizeCategories(value: Partial<Record<NotificationCategoryKey, unknown>>): Record<NotificationCategoryKey, boolean> {
  return Object.fromEntries(categoryKeys.map((key) => [key, typeof value[key] === 'boolean' ? value[key] : true])) as Record<NotificationCategoryKey, boolean>
}

function notificationId(value: string): string {
  if (!isSafeId(value)) throw new NotificationsApiError('Идентификатор уведомления некорректен')
  return value
}

function isSafeId(value: string): boolean {
  return /^[A-Za-z0-9_-]{1,128}$/.test(value)
}

function nonNegativeInteger(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new NotificationsApiError(`Некорректное значение ${field}`)
  }
  return value
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function errorStatus(error: unknown): number | null {
  const record = asRecord(error)
  const status = record?.status
  return typeof status === 'number' && Number.isInteger(status) && status >= 400 && status <= 599
    ? status
    : null
}

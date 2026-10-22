import type { HeadmanStatsPersonalMetric } from './headman-stats-client'

export interface HeadmanStatsSemester {
  readonly id: number
  readonly name: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly active: boolean
  readonly archived: boolean
}
export interface HeadmanStatsSemesterCatalog {
  readonly groupId: number
  readonly activeSemesterId: number | null
  readonly semesters: readonly HeadmanStatsSemester[]
}
export interface HeadmanStatsOverview {
  readonly groupId: number
  readonly membership: { readonly active: number; readonly total: number }
  readonly today: {
    readonly date: string
    readonly present: HeadmanStatsPersonalMetric
    readonly presentOrExcused: HeadmanStatsPersonalMetric
  }
}
export type HeadmanStatsLessonStatus = 'PRESENT' | 'EXCUSED' | 'ABSENT'
export interface HeadmanStatsLesson {
  readonly lessonId: number
  readonly lessonDate: string
  readonly lessonNumber: number | null
  readonly subjectId: number | null
  readonly subjectName: string
  readonly lessonType: string
  readonly startsAt: string
  readonly endsAt: string
  readonly room: string | null
  readonly status: HeadmanStatsLessonStatus | null
  readonly source: string | null
  readonly markRevision: string | null
  readonly editable: boolean
  readonly blockedReason: string | null
  readonly approvedTicket: {
    readonly kind: 'EXCUSE' | 'LATE_CHECKIN'
    readonly id: string
    readonly status: 'APPROVED'
  } | null
}
export interface HeadmanStatsLessonPage {
  readonly page: number
  readonly size: number
  readonly totalElements: number
  readonly totalPages: number
  readonly hasPrevious: boolean
  readonly hasNext: boolean
  readonly items: readonly HeadmanStatsLesson[]
}
export interface HeadmanStatsLessonQuery {
  readonly semesterId?: number
  readonly page: number
  readonly size: number
  readonly subjectId?: number
  readonly lessonTypes?: readonly string[]
}

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('Сервер вернул некорректные данные статистики.')
  return value as Record<string, unknown>
}
function payload(value: unknown): Record<string, unknown> {
  const item = record(value)
  return item.content && !Array.isArray(item.content) ? record(item.content) : item
}
function integer(value: unknown, minimum = 0): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum)
    throw new Error('Сервер вернул некорректный счётчик.')
  return value
}
function text(value: unknown): string {
  if (typeof value !== 'string') throw new Error('Сервер вернул некорректный текст.')
  return value
}
function nullableText(value: unknown): string | null {
  return value === null ? null : text(value)
}
function date(value: unknown): string {
  const result = text(value)
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(result) ||
    new Date(result + 'T12:00:00Z').toISOString().slice(0, 10) !== result
  )
    throw new Error('Сервер вернул некорректную дату.')
  return result
}
function instant(value: unknown): string {
  const result = text(value)
  if (!Number.isFinite(Date.parse(result)) || !/T/.test(result))
    throw new Error('Сервер вернул некорректное время.')
  return result
}
function boolean(value: unknown): boolean {
  if (typeof value !== 'boolean') throw new Error('Сервер вернул некорректный признак доступа.')
  return value
}
function list(value: unknown): readonly unknown[] {
  if (!Array.isArray(value)) throw new Error('Сервер вернул некорректный список.')
  return value
}
function personalMetric(value: unknown): HeadmanStatsPersonalMetric {
  const item = record(value)
  const numerator = integer(item.numerator)
  const denominator = integer(item.denominator)
  if (
    numerator > denominator ||
    (denominator === 0 && item.percent !== null) ||
    (denominator > 0 &&
      (typeof item.percent !== 'number' ||
        !Number.isFinite(item.percent) ||
        item.percent < 0 ||
        item.percent > 100))
  )
    throw new Error('Сервер вернул некорректную метрику.')
  return { numerator, denominator, percent: denominator > 0 ? (item.percent as number) : null }
}
export function normalizeHeadmanStatsSemesterCatalog(value: unknown): HeadmanStatsSemesterCatalog {
  const item = payload(value)
  const semesters = list(item.semesters).map((value) => {
    const semester = record(value)
    const dateFrom = date(semester.dateFrom)
    const dateTo = date(semester.dateTo)
    if (dateTo < dateFrom) throw new Error('Сервер вернул некорректный период.')
    return {
      id: integer(semester.id, 1),
      name: text(semester.name),
      dateFrom,
      dateTo,
      active: boolean(semester.active),
      archived: boolean(semester.archived),
    }
  })
  const activeSemesterId = item.activeSemesterId === null ? null : integer(item.activeSemesterId, 1)
  if (
    new Set(semesters.map((semester) => semester.id)).size !== semesters.length ||
    (activeSemesterId !== null && !semesters.some((semester) => semester.id === activeSemesterId))
  )
    throw new Error('Сервер вернул некорректный каталог семестров.')
  return { groupId: integer(item.groupId, 1), activeSemesterId, semesters }
}
export function normalizeHeadmanStatsOverview(value: unknown): HeadmanStatsOverview {
  const item = payload(value)
  const membership = record(item.membership)
  const today = record(item.today)
  const active = integer(membership.active)
  const total = integer(membership.total)
  if (active > total) throw new Error('Сервер вернул некорректный состав группы.')
  return {
    groupId: integer(item.groupId, 1),
    membership: { active, total },
    today: {
      date: date(today.date),
      present: personalMetric(today.present),
      presentOrExcused: personalMetric(today.presentOrExcused),
    },
  }
}
export function normalizeHeadmanStatsLessons(value: unknown): HeadmanStatsLessonPage {
  const item = payload(value)
  const items = list(item.items).map((value): HeadmanStatsLesson => {
    const lesson = record(value)
    const status = lesson.status
    if (status !== null && status !== 'PRESENT' && status !== 'EXCUSED' && status !== 'ABSENT')
      throw new Error('Неизвестный статус отметки. Обнови историю.')
    const markRevision = nullableText(lesson.markRevision)
    if ((status !== null && !markRevision?.trim()) || (status === null && markRevision !== null))
      throw new Error('У отметки нет актуальной ревизии. Обнови историю.')
    let approvedTicket: HeadmanStatsLesson['approvedTicket'] = null
    if (lesson.approvedTicket !== null) {
      const ticket = record(lesson.approvedTicket)
      if (
        (ticket.kind !== 'EXCUSE' && ticket.kind !== 'LATE_CHECKIN') ||
        ticket.status !== 'APPROVED' ||
        !text(ticket.id).trim()
      )
        throw new Error('Сервер вернул некорректную связь заявки.')
      approvedTicket = { kind: ticket.kind, id: text(ticket.id), status: 'APPROVED' }
    }
    const startsAt = instant(lesson.startsAt)
    const endsAt = instant(lesson.endsAt)
    if (Date.parse(endsAt) <= Date.parse(startsAt))
      throw new Error('Сервер вернул некорректное время пары.')
    return {
      lessonId: integer(lesson.lessonId, 1),
      lessonDate: date(lesson.lessonDate),
      lessonNumber: lesson.lessonNumber === null ? null : integer(lesson.lessonNumber, 1),
      subjectId: lesson.subjectId === null ? null : integer(lesson.subjectId, 1),
      subjectName: text(lesson.subjectName),
      lessonType: text(lesson.lessonType),
      startsAt,
      endsAt,
      room: nullableText(lesson.room),
      status,
      source: nullableText(lesson.source),
      markRevision,
      editable: boolean(lesson.editable),
      blockedReason: nullableText(lesson.blockedReason),
      approvedTicket,
    }
  })
  const size = integer(item.size, 1)
  if (
    size > 100 ||
    items.length > size ||
    new Set(items.map((lesson) => lesson.lessonId)).size !== items.length
  )
    throw new Error('Сервер вернул некорректную страницу истории.')
  return {
    page: integer(item.page),
    size,
    totalElements: integer(item.totalElements),
    totalPages: integer(item.totalPages),
    hasPrevious: boolean(item.hasPrevious),
    hasNext: boolean(item.hasNext),
    items,
  }
}

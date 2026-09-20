import type { StudentHomework, StudentHomeworkItem } from '../api/types'

export const HOMEWORK_TIME_ZONE = 'Europe/Moscow'

export interface HomeworkDateRange {
  from: string
  to: string
}

export type HomeworkGroupKind = 'completed-today' | 'lesson-date'

export interface HomeworkGroup {
  key: string
  kind: HomeworkGroupKind
  date: string | null
  monthLabel: string
  label: string
  items: readonly StudentHomeworkItem[]
}

export interface SupportedHomeworkLink {
  supported: true
  original: string
}

export interface UnsupportedHomeworkLink {
  supported: false
  reason: 'missing' | 'relative' | 'protocol' | 'invalid'
}

export type HomeworkLinkValidation = SupportedHomeworkLink | UnsupportedHomeworkLink

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/** Returns a YYYY-MM-DD date from an ISO instant using the server's timezone. */
export function moscowDate(value: string): string | null {
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return null
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: HOMEWORK_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(timestamp)
  const year = parts.find((part) => part.type === 'year')?.value
  const month = parts.find((part) => part.type === 'month')?.value
  const day = parts.find((part) => part.type === 'day')?.value
  return year && month && day ? `${year}-${month}-${day}` : null
}

/** Checks the wire-level date used by the homework API without normalising it. */
export function isHomeworkDate(value: string): boolean {
  if (!DATE_PATTERN.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  if (!year || !month || !day) return false
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

export function compareHomeworkDates(left: string, right: string): number {
  return left < right ? -1 : left > right ? 1 : 0
}

export function addHomeworkDays(value: string, days: number): string {
  if (!isHomeworkDate(value)) throw new Error(`Invalid homework date: ${value}`)
  const date = new Date(`${value}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

export function formatHomeworkMonth(date: string): string {
  if (!isHomeworkDate(date)) return ''
  return new Intl.DateTimeFormat('ru-RU', { timeZone: HOMEWORK_TIME_ZONE, month: 'long' })
    .format(new Date(`${date}T12:00:00+03:00`))
    .toLocaleUpperCase('ru-RU')
}

export function formatHomeworkDate(date: string, serverNow: string): string {
  if (!isHomeworkDate(date)) return date
  const today = moscowDate(serverNow)
  const tomorrow = today && addHomeworkDays(today, 1)
  const dateLabel = new Intl.DateTimeFormat('ru-RU', {
    timeZone: HOMEWORK_TIME_ZONE,
    day: 'numeric',
    month: 'long',
  }).format(new Date(`${date}T12:00:00+03:00`))
  if (date === today) return `Сегодня, ${dateLabel}`
  if (date === tomorrow) return `Завтра, ${dateLabel}`
  return dateLabel
}

function itemOrder(left: StudentHomeworkItem, right: StudentHomeworkItem): number {
  const dateOrder = compareHomeworkDates(left.lessonDate, right.lessonDate)
  if (dateOrder !== 0) return dateOrder
  const completionOrder = Number(left.completed) - Number(right.completed)
  if (completionOrder !== 0) return completionOrder
  if (left.lessonNumber !== right.lessonNumber) return left.lessonNumber - right.lessonNumber
  const subjectOrder = left.subject.name.localeCompare(right.subject.name, 'ru')
  if (subjectOrder !== 0) return subjectOrder
  return left.id.localeCompare(right.id)
}

/**
 * Deduplicates the server union while keeping its first authoritative item.
 * The API may include a completion-date union item and a lesson-date item in
 * the same response; the UI must render that assignment once.
 */
export function dedupeHomeworkItems(items: readonly StudentHomeworkItem[]): StudentHomeworkItem[] {
  const seen = new Set<string>()
  return items.filter((item) => {
    if (seen.has(item.id)) return false
    seen.add(item.id)
    return true
  })
}

export function isCompletedToday(item: StudentHomeworkItem, serverNow: string): boolean {
  return item.completed && item.completedAt !== null && moscowDate(item.completedAt) !== null
    && moscowDate(item.completedAt) === moscowDate(serverNow)
}

/**
 * Builds the final feed groups. Completion date has its own group and is not
 * inferred from lessonDate, device time, or the boolean alone.
 */
export function groupHomework(feed: StudentHomework): HomeworkGroup[] {
  const unique = dedupeHomeworkItems(feed.items)
  const completedToday = unique.filter((item) => isCompletedToday(item, feed.serverNow)).sort(itemOrder)
  const chronological = unique.filter((item) => !isCompletedToday(item, feed.serverNow)).sort(itemOrder)
  const groups: HomeworkGroup[] = []

  if (completedToday.length > 0) {
    const completedDate = moscowDate(feed.serverNow)
    groups.push({
      key: 'completed-today',
      kind: 'completed-today',
      date: completedDate,
      monthLabel: completedDate ? formatHomeworkMonth(completedDate) : '',
      label: 'Выполнено сегодня',
      items: completedToday,
    })
  }

  for (const item of chronological) {
    const previous = groups[groups.length - 1]
    if (previous?.kind === 'lesson-date' && previous.date === item.lessonDate) {
      groups[groups.length - 1] = { ...previous, items: [...previous.items, item] }
      continue
    }
    groups.push({
      key: `lesson-date:${item.lessonDate}`,
      kind: 'lesson-date',
      date: item.lessonDate,
      monthLabel: formatHomeworkMonth(item.lessonDate),
      label: formatHomeworkDate(item.lessonDate, feed.serverNow),
      items: [item],
    })
  }
  return groups
}

/**
 * Validates an external material target. The original string is returned so a
 * shell adapter can open exactly what the server supplied, without rewriting.
 */
export function validateHomeworkLink(link: string | null): HomeworkLinkValidation {
  if (link === null || link.length === 0) return { supported: false, reason: 'missing' }
  if (link.trim() !== link || link.startsWith('//') || link.startsWith('/')) {
    return { supported: false, reason: 'relative' }
  }
  try {
    const parsed = new URL(link)
    const protocol = parsed.protocol.toLowerCase()
    if ((protocol !== 'http:' && protocol !== 'https:') || parsed.hostname.length === 0) {
      return { supported: false, reason: 'protocol' }
    }
    return { supported: true, original: link }
  } catch {
    return { supported: false, reason: 'invalid' }
  }
}

export function homeworkRangeKey(range: HomeworkDateRange | null): string {
  return range ? `${range.from}:${range.to}` : 'current'
}

export function normalizeHomeworkRange(range: HomeworkDateRange | null | undefined): HomeworkDateRange | null {
  if (!range || !isHomeworkDate(range.from) || !isHomeworkDate(range.to)) return null
  if (compareHomeworkDates(range.from, range.to) > 0) return null
  return { from: range.from, to: range.to }
}

/**
 * Returns a bounded seven-day page before the currently loaded page. The
 * semester start is always the lower bound, so repeated navigation cannot
 * escape the server-authorized semester.
 */
export function previousHomeworkRange(feed: StudentHomework | null, pageSize = 7): HomeworkDateRange | null {
  if (!feed || !isHomeworkDate(feed.from) || !isHomeworkDate(feed.semester.dateFrom)) return null
  const end = addHomeworkDays(feed.from, -1)
  if (compareHomeworkDates(end, feed.semester.dateFrom) < 0) return null
  const start = addHomeworkDays(end, -(Math.max(1, pageSize) - 1))
  return {
    from: compareHomeworkDates(start, feed.semester.dateFrom) < 0 ? feed.semester.dateFrom : start,
    to: end,
  }
}

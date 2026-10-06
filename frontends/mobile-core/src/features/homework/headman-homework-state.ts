import type { HeadmanJournalLesson } from '../headman-journal/headman-journal-client'
import type { HeadmanSubject, HeadmanSubjectType } from '../headman-subjects/headman-subjects-client'
import type { HeadmanHomeworkSemester } from './headman-homework-client'

export const homeworkTypeLabels: Record<HeadmanSubjectType, string> = { LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная' }
export function isHomeworkDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T12:00:00Z`)
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
}
export function shiftHomeworkDate(value: string, days: number): string {
  const date = new Date(`${value}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}
export function homeworkWeek(value: string): string[] {
  const day = new Date(`${value}T12:00:00Z`).getUTCDay()
  const monday = shiftHomeworkDate(value, -((day + 6) % 7))
  return Array.from({ length: 7 }, (_, index) => shiftHomeworkDate(monday, index))
}
export function homeworkMonth(value: string): string[] {
  const first = `${value.slice(0, 7)}-01`
  const start = homeworkWeek(first)[0]!
  const last = new Date(`${first}T12:00:00Z`)
  last.setUTCMonth(last.getUTCMonth() + 1, 0)
  const end = homeworkWeek(last.toISOString().slice(0, 10))[6]!
  const days: string[] = []
  for (let date = start; date <= end; date = shiftHomeworkDate(date, 1)) days.push(date)
  return days
}
export function homeworkDateWritable(date: string, semester: HeadmanHomeworkSemester | null, today: string): boolean {
  return isHomeworkDate(date) && semester !== null && semester.active && date >= today
    && (semester.dateFrom === null || date >= semester.dateFrom)
    && (semester.dateTo === null || date <= semester.dateTo)
}
export function homeworkSubjectTypes(subject: HeadmanSubject, groupId: number, semesterId: number, date: string): HeadmanSubjectType[] {
  if (subject.groupId !== groupId) return []
  return [...new Set(subject.assignments.filter((assignment) => assignment.semesterId === semesterId
    && assignment.validFrom <= date && (assignment.validUntilExclusive === null || date < assignment.validUntilExclusive))
    .map((assignment) => assignment.lessonType))]
}
/** The selected physical lesson, never a guessed weekday recurrence, owns placement. */
export function homeworkLessonEligible(lesson: HeadmanJournalLesson, context: {
  groupId: number; subjectId: number; type: HeadmanSubjectType; semester: HeadmanHomeworkSemester; now: Date
}): boolean {
  const today = context.now.toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })
  if (!homeworkDateWritable(lesson.date, context.semester, today) || lesson.groupId !== context.groupId
    || lesson.subjectId !== context.subjectId || lesson.lessonType !== context.type || !lesson.current
    || (lesson.status !== 'PLANNED' && lesson.status !== 'ACTIVE') || lesson.lessonNumber === null || lesson.lessonNumber < 1 || lesson.lessonNumber > 8
    || !lesson.occurrenceId || !/^[1-9][0-9]*$/.test(lesson.occurrenceId) || lesson.occurrenceRevision === null || !/^[1-9][0-9]*$/.test(lesson.occurrenceRevision)
    || !lesson.endTime || !/^\d{2}:\d{2}(:\d{2})?$/.test(lesson.endTime)) return false
  return Date.parse(`${lesson.date}T${lesson.endTime.slice(0, 5)}:00+03:00`) + 5 * 60_000 > context.now.getTime()
}
export function homeworkLessonOrder(left: HeadmanJournalLesson, right: HeadmanJournalLesson): number {
  return left.date.localeCompare(right.date) || (left.lessonNumber ?? 0) - (right.lessonNumber ?? 0) || left.id - right.id
}
export function canReselectHomeworkCreate(problem: unknown, history: { attempts: number; everUncertain: boolean; hasReceipt: boolean }): boolean {
  if (history.attempts !== 1 || history.everUncertain || history.hasReceipt || typeof problem !== 'object' || problem === null) return false
  const extras = (problem as { extras?: unknown }).extras
  return typeof extras === 'object' && extras !== null
    && (extras as { code?: unknown }).code === 'HOMEWORK_SELECTION_STALE'
    && (extras as { admission?: unknown }).admission === 'CURRENT_ATTEMPT_REJECTED_BEFORE_ADMISSION'
}
export function homeworkTextSegments(text: string): readonly { text: string; url: string | null }[] {
  const segments: { text: string; url: string | null }[] = []
  const pattern = /https?:\/\/[^\s<>]+/g
  let offset = 0
  for (const match of text.matchAll(pattern)) {
    const start = match.index
    if (start > offset) segments.push({ text: text.slice(offset, start), url: null })
    const raw = match[0]
    let safe = false
    try { const url = new URL(raw); safe = (url.protocol === 'http:' || url.protocol === 'https:') && !url.username && !url.password } catch { /* Plain text remains readable. */ }
    segments.push({ text: raw, url: safe ? raw : null })
    offset = start + raw.length
  }
  if (offset < text.length) segments.push({ text: text.slice(offset), url: null })
  return segments
}

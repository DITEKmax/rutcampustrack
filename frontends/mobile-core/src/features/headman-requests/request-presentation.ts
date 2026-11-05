import type {
  HeadmanRequestDetail,
  HeadmanRequestLesson,
  HeadmanRequestSummary,
} from './headman-requests-client'

interface RequestGroup {
  readonly key: string
  name: string | null
  readonly items: HeadmanRequestSummary[]
}

const studentCollator = new Intl.Collator('ru-RU', { sensitivity: 'base' })
const dateFormatter = new Intl.DateTimeFormat('ru-RU', {
  day: 'numeric',
  month: 'long',
  timeZone: 'UTC',
})

export function groupHeadmanRequests(items: readonly HeadmanRequestSummary[]): RequestGroup[] {
  const groups = new Map<string, RequestGroup>()
  for (const item of items) {
    const key =
      item.studentId != null && Number.isSafeInteger(item.studentId) && item.studentId > 0
        ? `student:${item.studentId}`
        : `request:${item.id}`
    const group = groups.get(key) ?? { key, name: null, items: [] }
    group.name ??= item.studentName?.trim() || null
    group.items.push(item)
    groups.set(key, group)
  }
  // Academic displayName starts with the surname. Keep server order within each student.
  return [...groups.values()].sort((a, b) =>
    a.name && b.name ? studentCollator.compare(a.name, b.name) : a.name ? -1 : b.name ? 1 : 0,
  )
}

const excuseReasons: Readonly<Record<string, string>> = {
  ILLNESS: 'Болезнь',
  MEDICAL_EXAMINATION: 'Медосмотр',
  COMPETITION_PARTICIPATION: 'Участие в соревновании',
  FAMILY_CIRCUMSTANCES: 'Семейные обстоятельства',
  SUMMONS: 'Повестка',
  UNIVERSITY_ORDER: 'Распоряжение университета',
  EXEMPTION: 'Освобождение',
  OTHER: 'Другая причина',
}

export function requestReason({ kind, reason }: HeadmanRequestSummary): string {
  if (kind === 'LATE_CHECKIN') return 'Не успел отметиться'
  return reason ? (excuseReasons[reason] ?? reason) : 'Уважительная причина'
}

export function requestDate(value: string | null): string {
  if (!value) return 'Дата не указана'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date)
}

export function requestPeriod({ coverageStart, coverageEnd }: HeadmanRequestSummary): string {
  if (!coverageStart || !coverageEnd || coverageStart === coverageEnd)
    return requestDate(coverageStart ?? coverageEnd)
  return `${requestDate(coverageStart)} — ${requestDate(coverageEnd)}`
}

export interface RequestLessonRow {
  readonly lesson: HeadmanRequestLesson
  readonly mark?: string | null
  readonly label?: string
  readonly excluded?: boolean
}

export function originalLessonRows(lessons: readonly HeadmanRequestLesson[]): RequestLessonRow[] {
  // The legacy detail's original rows hydrate attendance from the live journal.
  // Only their schedule metadata is a snapshot.
  return lessons.map((lesson) => ({ lesson }))
}

export function currentLessonRows(
  lessons: readonly HeadmanRequestLesson[],
  excludedIds: readonly string[] = [],
): RequestLessonRow[] {
  return lessons.map((lesson) => {
    const excluded = excludedIds.includes(lesson.occurrenceId ?? '')
    return {
      lesson,
      mark: lesson.attendanceStatus,
      excluded,
      ...(excluded && { label: 'Исключена из одобрения' }),
    }
  })
}

export function previewApproval(
  detail: HeadmanRequestDetail,
  excludedIds: readonly string[],
): { before: RequestLessonRow[]; after: RequestLessonRow[] } {
  const before = currentLessonRows(detail.currentAffectedLessons ?? [])
  const after = before.map(({ lesson, mark }) =>
    excludedIds.includes(lesson.occurrenceId ?? '')
      ? { lesson, mark: lesson.attendanceStatus, label: 'Исключена · без изменений' }
      : {
          lesson,
          mark:
            mark === 'PRESENT' || detail.summary.kind === 'LATE_CHECKIN' ? 'PRESENT' : 'EXCUSED',
        },
  )
  return { before, after }
}

export function storedDecisionRows(detail: HeadmanRequestDetail) {
  const receipt = detail.decisionSelection
  return {
    before: receipt?.beforeLessons
      ? currentLessonRows(receipt.beforeLessons)
      : originalLessonRows(detail.lessons),
    after: [
      ...(receipt?.includedLessons.map((lesson) => ({
        lesson,
        mark: lesson.attendanceStatus,
        label: detail.summary.status === 'APPROVED' ? 'Одобрена' : 'Без изменений',
      })) ?? []),
      ...(receipt?.excludedLessons.map((lesson) => ({
        lesson,
        mark: lesson.attendanceStatus,
        label: 'Исключена · без изменений',
      })) ?? []),
    ],
  }
}

export function hasDifferentOriginalLessons(
  originals: readonly HeadmanRequestLesson[],
  current: readonly HeadmanRequestLesson[],
): boolean {
  if (originals.length !== current.length) return true
  const byId = new Map(current.map((lesson) => [lesson.lessonId, lesson]))
  return originals.some((lesson) => {
    const other = byId.get(lesson.lessonId)
    return (
      !other ||
      lesson.date !== other.date ||
      lesson.subjectId !== other.subjectId ||
      lesson.startsAt !== other.startsAt ||
      lesson.endsAt !== other.endsAt
    )
  })
}

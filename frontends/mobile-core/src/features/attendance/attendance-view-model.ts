export type AttendanceTheme = 'dark' | 'light'

export type AttendanceReadState<T> =
  | { readonly status: 'loading' }
  | { readonly status: 'ready'; readonly data: T }
  | { readonly status: 'empty' }
  | { readonly status: 'error'; readonly code: string; readonly message: string; readonly retryable: boolean }
  | { readonly status: 'forbidden'; readonly reason: string }
  | { readonly status: 'offline' }
  | { readonly status: 'no-semester' }

export type AttendanceMode = 'days' | 'subjects' | 'graph'
export type AttendanceGraphRange = 'days' | 'weeks'
export type LessonType = 'LECTURE' | 'PRACTICE' | 'LAB'
export const LESSON_TYPE_ORDER = ['LECTURE', 'PRACTICE', 'LAB'] as const satisfies readonly LessonType[]

export type AttendanceLessonStatus =
  | 'PRESENT'
  | 'ABSENT'
  | 'EXCUSED'
  | 'ACTIVE'
  | 'FUTURE'
  | 'NO_DATA'
  | 'CANCELLED'

export type AttendanceRequestKind = 'EXCUSE' | 'LATE_CHECKIN'

export interface AttendanceRequestOption {
  readonly id: string
  readonly kind: AttendanceRequestKind
  readonly label: string
  readonly enabled: boolean
  readonly reason?: string | null
}

export interface AttendanceMetricValue {
  readonly count: number
  readonly percent: number | null
}

export interface AttendanceMetricSet {
  readonly present: AttendanceMetricValue
  readonly presentOrExcused: AttendanceMetricValue
  readonly excused: AttendanceMetricValue
  readonly absent: AttendanceMetricValue
  readonly held: number
  readonly planned: number
}

export interface AttendanceLesson {
  readonly id: string
  readonly date: string
  readonly number: string
  readonly subject: { readonly id: string; readonly name: string }
  readonly type: LessonType
  readonly schedule: {
    readonly startsAt: string
    readonly endsAt: string
    readonly room: string | null
  }
  readonly status: AttendanceLessonStatus
  readonly requestOptions: readonly AttendanceRequestOption[]
}

export interface AttendanceDay {
  readonly date: string
  readonly weekday: string
  readonly dayNumber: string
  readonly state: 'PAST' | 'CURRENT' | 'FUTURE'
  readonly lessons: readonly AttendanceLesson[]
}

export type AttendanceHistoryStatus = 'PRESENT' | 'ABSENT' | 'EXCUSED' | 'FUTURE' | 'NO_DATA'

export interface AttendanceHistorySegment {
  readonly id: string
  readonly status: AttendanceHistoryStatus
}

export interface AttendanceSubjectTypeSummary {
  readonly type: LessonType
  readonly metrics: AttendanceMetricSet
  readonly history: readonly AttendanceHistorySegment[]
}

export interface AttendanceSubject {
  readonly id: string
  readonly name: string
  readonly typeCards: readonly AttendanceSubjectTypeSummary[]
}

export interface AttendanceGraphPoint {
  readonly id: string
  readonly label: string
  readonly dateFrom: string
  readonly dateTo: string
  readonly state: 'DATA' | 'NO_DATA' | 'FUTURE'
  readonly metrics: AttendanceMetricSet
}

export interface AttendanceViewModel {
  readonly semester?: { readonly id: string; readonly name: string; readonly dateFrom: string; readonly dateTo: string }
  readonly serverNow?: string
  readonly metrics: AttendanceMetricSet
  readonly days: readonly AttendanceDay[]
  readonly subjects: readonly AttendanceSubject[]
  readonly graph: {
    readonly days: readonly AttendanceGraphPoint[]
    readonly weeks: readonly AttendanceGraphPoint[]
  }
}

export function displayPercent(percent: number | null): string {
  return percent === null || !Number.isFinite(percent) ? '—' : `${Math.round(percent)}%`
}

export function lessonTypeLabel(type: LessonType): string {
  const labels: Record<LessonType, string> = {
    LECTURE: 'Лекция',
    PRACTICE: 'Практика',
    LAB: 'Лабораторная',
  }
  return labels[type]
}

export function lessonStatusLabel(status: AttendanceLessonStatus): string {
  const labels: Record<AttendanceLessonStatus, string> = {
    PRESENT: 'присутствовал',
    ABSENT: 'отсутствовал',
    EXCUSED: 'уважительная причина',
    ACTIVE: 'сейчас',
    FUTURE: 'будет',
    NO_DATA: 'нет данных',
    CANCELLED: 'отменена',
  }
  return labels[status]
}

export function historyStatusLabel(status: AttendanceHistoryStatus): string {
  const labels: Record<AttendanceHistoryStatus, string> = {
    PRESENT: 'присутствовал',
    ABSENT: 'отсутствовал',
    EXCUSED: 'уважительная причина',
    FUTURE: 'будет',
    NO_DATA: 'нет данных',
  }
  return labels[status]
}

export function formatLessonTime(lesson: AttendanceLesson): string {
  return `${lesson.schedule.startsAt.slice(0, 5)}–${lesson.schedule.endsAt.slice(0, 5)}`
}

export function formatDayDate(date: string): string {
  const parsed = new Date(`${date}T12:00:00Z`)
  if (Number.isNaN(parsed.getTime())) return date
  return parsed.toLocaleDateString('ru-RU', {
    timeZone: 'Europe/Moscow',
    day: 'numeric',
    month: 'long',
  })
}

export function lessonsForDate(data: AttendanceViewModel, date: string): readonly AttendanceLesson[] {
  return data.days.find((day) => day.date === date)?.lessons ?? []
}

export function findAttendanceLesson(data: AttendanceViewModel, lessonId: string | null): AttendanceLesson | null {
  if (lessonId === null) return null
  for (const day of data.days) {
    const lesson = day.lessons.find((candidate) => candidate.id === lessonId)
    if (lesson) return lesson
  }
  return null
}

export function graphForRange(data: AttendanceViewModel, range: AttendanceGraphRange): readonly AttendanceGraphPoint[] {
  return data.graph[range]
}

/** Eligibility is a server-owned option; status alone never enables a request. */
export function hasEnabledRequestOption(lesson: AttendanceLesson): boolean {
  return lesson.requestOptions.some((option) => option.enabled)
}

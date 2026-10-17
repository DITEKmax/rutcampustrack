import type {
  TeacherApi,
  TeacherDayLesson,
  TeacherJournalCell,
  TeacherJournalQuery,
  TeacherJournalResponse,
  TeacherJournalStudent,
} from './teacher-client'
import { teacherJournalTypes } from './teacher-client'

export type JournalMark = 'present' | 'excused' | 'absent'
export interface JournalCounts {
  present: number
  excused: number
  absent: number
  denominator: number
}
export function journalContextKey(query: TeacherJournalQuery | null): string {
  return query
    ? [
        query.semesterId,
        query.groupId,
        query.subjectId,
        teacherJournalTypes(query).slice().sort().join(','),
        query.dateFrom ?? '',
        query.dateTo ?? '',
        query.pastOnly === true,
      ].join(':')
    : ''
}
export function journalMark(cell: TeacherJournalCell | null | undefined): JournalMark | null {
  if (!cell) return null // No historical membership cell must never turn into an absence.
  const status = cell.status?.toLowerCase()
  if (status === 'present' || cell.symbol === '+' || cell.symbol === 'б') return 'present'
  if (status === 'excused' || cell.symbol === 'у') return 'excused'
  if (status === 'absent' || cell.symbol === 'н' || !cell.recordPresent) return 'absent'
  return null
}
export function journalCounts(
  cells: readonly TeacherJournalCell[],
  lessonIds: ReadonlySet<number>,
): JournalCounts {
  const counts: JournalCounts = { present: 0, excused: 0, absent: 0, denominator: 0 }
  const seen = new Set<number>()
  for (const cell of cells) {
    if (!lessonIds.has(cell.lessonId) || seen.has(cell.lessonId)) continue
    seen.add(cell.lessonId)
    const mark = journalMark(cell)
    if (mark) {
      counts[mark] += 1
      counts.denominator += 1
    }
  }
  return counts
}
export function mergeJournalPage(
  previous: TeacherJournalResponse | null,
  next: TeacherJournalResponse,
): TeacherJournalResponse {
  const lessons = new Map<number, TeacherDayLesson>()
  const students = new Map<number, TeacherJournalStudent>()
  for (const lesson of [...(previous?.lessons ?? []), ...next.lessons])
    lessons.set(lesson.id, lesson)
  for (const student of [...(previous?.students ?? []), ...next.students]) {
    const old = students.get(student.studentId)
    const cells = new Map<number, TeacherJournalCell>()
    for (const cell of [...(old?.cells ?? []), ...student.cells]) cells.set(cell.lessonId, cell)
    students.set(student.studentId, { ...student, cells: [...cells.values()] })
  }
  return {
    ...next,
    metrics: previous ? (previous.metrics ?? null) : (next.metrics ?? null),
    lessons: [...lessons.values()].sort(
      (a, b) =>
        a.date.localeCompare(b.date) ||
        (a.startsAt ?? '').localeCompare(b.startsAt ?? '') ||
        a.lessonNumber - b.lessonNumber ||
        a.id - b.id,
    ),
    students: [...students.values()].sort((a, b) =>
      a.displayName.localeCompare(b.displayName, 'ru'),
    ),
  }
}
/** Only a completed union is published. Obsolete sessions/contexts stop before another page. */
export async function loadJournalPages(
  api: Pick<TeacherApi, 'journal'>,
  query: TeacherJournalQuery,
  isCurrent: () => boolean,
): Promise<TeacherJournalResponse | null> {
  if (teacherJournalTypes(query).length === 0 || !isCurrent()) return null
  let merged: TeacherJournalResponse | null = null
  for (let page = 0; ; page += 1) {
    const next = await api.journal({
      ...query,
      page,
      pageSize: 100,
      includeMetrics: page === 0,
      metricsStudentId: undefined,
    })
    if (!isCurrent()) return null
    if (
      next.page !== page ||
      (next.hasMore &&
        (next.lessons.length === 0 || (page + 1) * next.pageSize >= next.totalLessons))
    )
      throw new Error('Сервер вернул несогласованные страницы журнала.')
    merged = mergeJournalPage(merged, next)
    if (!next.hasMore) {
      if (
        merged.metrics &&
        (merged.metrics.selectedStudentId !== null ||
          merged.metrics.series.some(
            (point) =>
              !merged?.lessons.some(
                (lesson) =>
                  lesson.id === point.lessonId && lesson.status === 'CLOSED' && !lesson.cancelled,
              ),
          ))
      )
        throw new Error('Сервер вернул несогласованные метрики журнала.')
      return merged
    }
  }
}
/** Formatting only: percentages and their common denominator belong to Attendance. */
export function journalMetricPercent(
  metric: import('./teacher-client').TeacherStatsMetric | null | undefined,
): string {
  return metric && metric.denominator > 0
    ? `${new Intl.NumberFormat('ru', { maximumFractionDigits: 1 }).format(metric.percent)} %`
    : '—'
}
export function journalMetricLabel(
  label: string,
  metric: import('./teacher-client').TeacherStatsMetric | null | undefined,
): string {
  return metric
    ? `${label}: ${metric.numerator} из ${metric.denominator}`
    : `${label}: данные недоступны`
}
export function journalPercent(value: number, denominator: number): string {
  return denominator
    ? `${new Intl.NumberFormat('ru', { maximumFractionDigits: 1 }).format((value / denominator) * 100)} %`
    : '—'
}
export function journalPassedEnd(
  semesterEnd: string | undefined,
  serverNow: string | undefined,
): string | null {
  if (!semesterEnd || !serverNow) return null
  // Use the server clock, in the campus timezone, rather than a drifting device clock.
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(serverNow))
  const part = (name: string) => parts.find((item) => item.type === name)?.value ?? ''
  const today = `${part('year')}-${part('month')}-${part('day')}`
  return today < semesterEnd ? today : semesterEnd
}

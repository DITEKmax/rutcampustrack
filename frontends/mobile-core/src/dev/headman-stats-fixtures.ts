import type { HeadmanStatsContext, HeadmanStatsMetrics, HeadmanStatsResponse, HeadmanStatsStudentDetailResponse, HeadmanStatsTrendPoint, HeadmanStatsTrendResponse, HeadmanStatsQuery, HeadmanStatsTrendQuery } from '../features/headman-stats/headman-stats-client'
import type { HeadmanStatsLesson } from '../features/headman-stats/headman-stats-history-client'

export const HEADMAN_STATS_REVIEW_STATES = ['overview-autumn', 'overview-spring', 'subject-multi', 'subject-single', 'subject-none', 'subject-error', 'person-detail', 'history', 'history-cleared', 'history-confirmation', 'history-error', 'history-empty', 'days-point', 'no-data', 'offline', 'error', 'long-name'] as const
const types = [{ code: 'LECTURE', label: 'Лекция' }, { code: 'PRACTICE', label: 'Практика' }, { code: 'LAB', label: 'Лабораторная' }]
// Independent synthetic lesson cells; DAY and WEEK responses aggregate the same cells.
const cells = [
  [[8, 1, 1], [7, 1, 1], [7, 0, 1]],
  [[7, 1, 2], [7, 1, 1], [6, 1, 1]],
  [[9, 0, 1], [8, 1, 0], [7, 0, 1]],
  [[6, 3, 1], [7, 1, 1], [6, 1, 1]],
]
function countsFor(index: number, subjectId: number | null, lessonTypes: readonly string[]) {
  return cells[index]!.reduce((sum, counts, type) => {
    if (subjectId !== null && lessonTypes.length && !lessonTypes.includes(types[type]!.code)) return sum
    return sum.map((count, status) => count + counts[status]!)
  }, [0, 0, 0]) as [number, number, number]
}
function metric(count: number, total: number) { return { numerator: count, denominator: total, percent: total ? count / total * 100 : 0 } }
function metrics(present: number, excused: number, absent: number): HeadmanStatsMetrics {
  const total = present + excused + absent
  return { present: metric(present, total), presentOrExcused: metric(present + excused, total), excused: metric(excused, total), absent: metric(absent, total) }
}
function context(state: string, subjectId: number | null = null, semesterId?: number): HeadmanStatsContext {
  const spring = semesterId !== undefined ? semesterId === 8 : state === 'overview-spring'
  return { groupId: 2, groupName: 'УВП-211', semesterId: spring ? 8 : 7, semesterName: spring ? 'Весенний семестр 2027' : 'Осенний семестр 2026', semesterFrom: spring ? '2027-02-01' : '2026-09-01', semesterTo: spring ? '2027-06-30' : '2027-01-31', subjectId, subjectName: subjectId ? 'Информационные системы' : '', lessonTypes: [], lessonsCount: state === 'no-data' ? 0 : 27, generatedAt: '2026-10-06T08:30:00Z' }
}
export function headmanStatsFixtureResponse(state: string, query: HeadmanStatsQuery): HeadmanStatsResponse {
  const names = ['Александрова Мария', 'Воронов Алексей', 'Громов Илья', 'Мельникова Дарья']
  let rows = names.map((displayName, index) => {
    const counts = state === 'no-data' ? [0, 0, 0] as [number, number, number] : countsFor(index, query.subjectId, query.lessonTypes)
    return { studentId: index + 11, displayName: state === 'long-name' && index === 0 ? 'Александрова-Константинопольская Мария Александровна' : displayName, metrics: metrics(...counts), lateCheckin: { submitted: index + 1, approved: index, rejected: 0 }, excuse: { submitted: 2, approved: 1, rejected: 0 }, sources: { studentGeo: Math.max(0, counts[0] - 6), manualRequest: Math.min(1, counts[0]), autoAfterGeoFailure: Math.min(2, Math.max(0, counts[0] - 1)), headmanManual: Math.min(3, Math.max(0, counts[0] - 3)) } }
  })
  const totals = rows.reduce((sum, row) => [sum[0]! + row.metrics.present.numerator, sum[1]! + row.metrics.excused.numerator, sum[2]! + row.metrics.absent.numerator], [0, 0, 0]) as [number, number, number]
  for (const filter of query.filters) if (filter.field === 'displayName' && filter.contains) rows = rows.filter((row) => row.displayName.toLocaleLowerCase('ru-RU').includes(filter.contains!.toLocaleLowerCase('ru-RU')))
  for (const sort of [...query.sorts].reverse()) if (sort.field === 'displayName') rows.sort((left, right) => left.displayName.localeCompare(right.displayName, 'ru-RU') * (sort.descending ? -1 : 1))
  return { context: context(state, query.subjectId, query.semesterId), summary: metrics(...totals), filteredStudents: rows.length, rows, subjects: [{ id: 12, label: 'Информационные системы', lessonTypes: types }, { id: 13, label: 'Высшая математика', lessonTypes: types.slice(0, 2) }], columns: [{ field: 'displayName', label: 'Студент', filterKind: 'TEXT' }, { field: 'presentPercent', label: '«+», %', filterKind: 'RANGE' }, { field: 'excusedPercent', label: '«у», %', filterKind: 'RANGE' }, { field: 'absentPercent', label: '«н», %', filterKind: 'RANGE' }], formats: [{ code: 'xlsx', label: 'Excel', contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', extension: 'xlsx' }], page: 0, size: 50, totalPages: rows.length ? 1 : 0, totalElements: rows.length, hasPrevious: false, hasNext: false, emptyState: state === 'no-data' ? 'NO_COMPLETED_LESSONS' : rows.length ? 'NONE' : 'FILTERED_EMPTY' }
}
function points(state: string, personal = false, query: HeadmanStatsTrendQuery = { mode: 'SEMESTER' }): HeadmanStatsTrendPoint[] {
  const period = context(state, query.mode === 'SUBJECT' ? query.subjectId ?? null : null, query.semesterId)
  const first = new Date(period.semesterFrom + 'T12:00:00Z')
  const last = new Date(period.semesterTo + 'T12:00:00Z')
  const observations: { day: number; present: number; combined: number }[] = []
  const offsets = [0, 1, 3, 4, 7, 8, 9, 10, 21, 22, 23, 24, 28, 29, 34, 35]
  if (state !== 'no-data') for (let student = 0; student < (personal ? 1 : cells.length); student += 1) {
    for (let type = 0; type < types.length; type += 1) {
      if (query.mode === 'SUBJECT' && query.lessonTypes?.length && !query.lessonTypes.includes(types[type]!.code)) continue
      const counts = cells[student]![type]!
      let cell = 0
      for (let status = 0; status < counts.length; status += 1) for (let count = 0; count < counts[status]!; count += 1) {
        observations.push({ day: offsets[(cell++ * 3 + type * 5) % offsets.length]!, present: status === 0 ? 1 : 0, combined: status !== 2 ? 1 : 0 })
      }
    }
  }
  const daily = query.bucketUnit === 'DAY'
  const result: HeadmanStatsTrendPoint[] = []
  const cursor = new Date(first)
  if (!daily) cursor.setUTCDate(cursor.getUTCDate() - (cursor.getUTCDay() + 6) % 7)
  while (cursor <= last) {
    const from = new Date(Math.max(cursor.getTime(), first.getTime()))
    const end = new Date(cursor); if (!daily) end.setUTCDate(end.getUTCDate() + 6)
    const to = new Date(Math.min(end.getTime(), last.getTime()))
    const fromDay = Math.round((from.getTime() - first.getTime()) / 86400000)
    const toDay = Math.round((to.getTime() - first.getTime()) / 86400000)
    const bucket = observations.filter((cell) => cell.day >= fromDay && cell.day <= toDay)
    const present = bucket.reduce((sum, cell) => sum + cell.present, 0)
    const combined = bucket.reduce((sum, cell) => sum + cell.combined, 0)
    const denominator = bucket.length
    const date = from.toISOString().slice(0, 10)
    result.push({ key: date, label: date, from: date, to: to.toISOString().slice(0, 10), present: { numerator: present, denominator, percent: denominator ? present / denominator * 100 : null }, presentOrExcused: { numerator: combined, denominator, percent: denominator ? combined / denominator * 100 : null } })
    cursor.setUTCDate(cursor.getUTCDate() + (daily ? 1 : 7))
  }
  return result
}
export function headmanStatsFixtureTrend(state: string, query: HeadmanStatsTrendQuery, personal = false): HeadmanStatsTrendResponse {
  const period = context(state, query.mode === 'SUBJECT' ? query.subjectId : null, query.semesterId)
  return { context: period, mode: query.mode, points: points(state, personal, query), emptyState: state === 'no-data' ? 'NO_COMPLETED_LESSONS' : 'NONE', formats: [{ code: 'png', label: 'PNG', contentType: 'image/png', extension: 'png' }, { code: 'html', label: 'HTML', contentType: 'text/html', extension: 'html' }] }
}
export function headmanStatsFixtureLessons(state: string): HeadmanStatsLesson[] {
  if (state === 'history-empty') return []
  return [
    { lessonId: 101, lessonDate: '2026-10-02', lessonNumber: 7, subjectId: 12, subjectName: 'Информационные системы', lessonType: 'LECTURE', startsAt: '2026-10-02T15:00:00Z', endsAt: '2026-10-02T16:30:00Z', room: 'А-401', status: state === 'history-cleared' ? null : 'PRESENT', source: state === 'history-cleared' ? null : 'HEADMAN_MANUAL', markRevision: state === 'history-cleared' ? null : 'fixture-mark-101-v1', editable: true, blockedReason: null, approvedTicket: null },
    { lessonId: 102, lessonDate: '2026-10-01', lessonNumber: 6, subjectId: 13, subjectName: 'Высшая математика', lessonType: 'PRACTICE', startsAt: '2026-10-01T13:20:00Z', endsAt: '2026-10-01T14:50:00Z', room: 'Б-203', status: 'EXCUSED', source: 'EXCUSE_REQUEST', markRevision: 'fixture-mark-102-v1', editable: true, blockedReason: null, approvedTicket: { kind: 'EXCUSE', id: 'b4b8c43c-0221-4138-8295-61620c64f01d', status: 'APPROVED' } },
    { lessonId: 103, lessonDate: '2026-09-29', lessonNumber: 4, subjectId: 14, subjectName: 'Архитектура ЭВМ', lessonType: 'LAB', startsAt: '2026-09-29T10:00:00Z', endsAt: '2026-09-29T11:30:00Z', room: 'А-312', status: 'ABSENT', source: 'HEADMAN_MANUAL', markRevision: 'fixture-mark-103-v1', editable: true, blockedReason: null, approvedTicket: null },
    { lessonId: 104, lessonDate: '2026-09-28', lessonNumber: 3, subjectId: 15, subjectName: state === 'long-name' ? 'Иностранный язык в международной профессиональной коммуникации' : 'Иностранный язык', lessonType: 'PRACTICE', startsAt: '2026-09-28T08:20:00Z', endsAt: '2026-09-28T09:50:00Z', room: null, status: 'PRESENT', source: 'STUDENT_GEO', markRevision: 'fixture-mark-104-v1', editable: false, blockedReason: 'Пара недоступна для изменения', approvedTicket: null },
  ]
}
export function headmanStatsFixtureDetail(state: string, studentId: number, semesterId?: number): HeadmanStatsStudentDetailResponse {
  const row = headmanStatsFixtureResponse(state, { subjectId: null, lessonTypes: [], page: 0, size: 50, sorts: [], filters: [] }).rows.find((item) => item.studentId === studentId)!
  const personalCounters = { sources: row.sources, lateCheckin: row.lateCheckin, excuse: row.excuse }
  const page = { page: 0, size: 20, totalElements: 0, totalPages: 0, hasPrevious: false, hasNext: false, items: [] }
  return { context: context(state, null, semesterId), student: { id: row.studentId, displayName: row.displayName }, metrics: row.metrics, ...personalCounters, subjects: [], weeks: points(state, true, { mode: 'SEMESTER', ...(semesterId === undefined ? {} : { semesterId }) }).map((point) => { const monday = new Date(point.from + 'T12:00:00Z'); monday.setUTCDate(monday.getUTCDate() - (monday.getUTCDay() + 6) % 7); return { weekStart: monday.toISOString().slice(0, 10), from: point.from, to: point.to, present: point.present, presentOrExcused: point.presentOrExcused } }), lateCheckins: page, excuses: page, emptyState: state === 'no-data' ? 'NO_COMPLETED_LESSONS' : 'NONE' }
}


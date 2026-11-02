import { headmanStatsFixtureResponse } from '../../../mobile-core/src/dev/headman-stats-fixtures'
import type { HeadmanStatsContext, HeadmanStatsMetrics, HeadmanStatsQuery, HeadmanStatsResponse, HeadmanStatsStudentRow, HeadmanStatsTrendQuery, HeadmanStatsTrendResponse, HeadmanStatsStudentDetailResponse } from '../../../mobile-core/src/features/headman-stats/headman-stats-client'
import type { HeadmanStatsLesson, HeadmanStatsOverview, HeadmanStatsSemesterCatalog } from '../../../mobile-core/src/features/headman-stats/headman-stats-history-client'
import { groupId, headmanDate, headmanNow, lessons, members, monday, plus, report, requests, semester, semesters, subjects } from './headman-visual-data'

const metric = (count: number, total: number) => ({ numerator: count, denominator: total, percent: total ? count / total * 100 : 0 })
export const personalMetric = (count: number, total: number) => ({ numerator: count, denominator: total, percent: total ? count / total * 100 : null })
const completed = lessons.filter((lesson) => lesson.status === 'CLOSED')
const typeLabels: Readonly<Record<string, string>> = { LECTURE: 'Лекция', LAB: 'Лабораторная', PRACTICE: 'Практика' }
export const statsCatalog: HeadmanStatsSemesterCatalog = { groupId, activeSemesterId: semester.id, semesters: semesters.map((item) => ({ ...item, archived: !item.active })) }
const activeEntries = lessons.filter((lesson) => lesson.date === headmanDate && lesson.status === 'ACTIVE').flatMap((lesson) => report(lesson).entries)
export const statsOverview: HeadmanStatsOverview = { groupId, membership: { active: members.length, total: members.length }, today: { date: headmanDate, present: personalMetric(activeEntries.filter((entry) => entry.status === 'PRESENT').length, activeEntries.length), presentOrExcused: personalMetric(activeEntries.filter((entry) => entry.status === 'PRESENT' || entry.status === 'EXCUSED').length, activeEntries.length) } }
export function context(subjectId: number | null = null, types: readonly string[] = []): HeadmanStatsContext {
  return { groupId, groupName: 'ИВТ-21', semesterId: semester.id, semesterName: semester.name, semesterFrom: semester.dateFrom, semesterTo: semester.dateTo, subjectId, subjectName: subjects.find((subject) => subject.id === subjectId)?.name ?? '', lessonTypes: types, lessonsCount: scope(subjectId, types).length, generatedAt: headmanNow }
}
function scope(subjectId: number | null, types: readonly string[]) {
  return completed.filter((lesson) => (!subjectId || lesson.subjectId === subjectId) && (!types.length || types.includes(lesson.lessonType ?? '')))
}
function counts(items: typeof completed, studentId?: number): HeadmanStatsMetrics {
  const entries = items.flatMap((lesson) => report(lesson).entries.filter((entry) => !studentId || entry.userId === studentId))
  const present = entries.filter((entry) => entry.status === 'PRESENT').length
  const excused = entries.filter((entry) => entry.status === 'EXCUSED').length
  const absent = entries.filter((entry) => entry.status === 'ABSENT').length
  const total = present + excused + absent
  return { present: metric(present, total), presentOrExcused: metric(present + excused, total), excused: metric(excused, total), absent: metric(absent, total) }
}
function ticketCounts(studentId: number, kind: 'EXCUSE' | 'LATE_CHECKIN') {
  const items = requests.filter((request) => request.summary.studentId === studentId && request.summary.kind === kind)
  return { submitted: items.length, approved: items.filter((item) => item.summary.status === 'APPROVED').length, rejected: items.filter((item) => item.summary.status === 'REJECTED').length }
}
function studentRow(studentId: number, subjectId: number | null, types: readonly string[]): HeadmanStatsStudentRow {
  const metrics = counts(scope(subjectId, types), studentId)
  return { studentId, displayName: members.find((member) => member.id === studentId)!.fullName, metrics, lateCheckin: ticketCounts(studentId, 'LATE_CHECKIN'), excuse: ticketCounts(studentId, 'EXCUSE'), sources: { studentGeo: metrics.present.numerator, manualRequest: 0, autoAfterGeoFailure: 0, headmanManual: 0 } }
}
export function statsResponse(query: HeadmanStatsQuery): HeadmanStatsResponse {
  const shell = headmanStatsFixtureResponse('person-detail', { ...query, page: 0, size: 100, filters: [], sorts: [] })
  let rows = members.map((member) => studentRow(member.id, query.subjectId, query.lessonTypes))
  const value = (row: HeadmanStatsStudentRow, field: string): string | number => field === 'displayName' ? row.displayName : row.metrics[field.replace(/Count$|Percent$/, '') as keyof HeadmanStatsMetrics]?.[field.endsWith('Count') ? 'numerator' : 'percent'] ?? 0
  for (const filter of query.filters) rows = rows.filter((row) => {
    const actual = value(row, filter.field)
    return typeof actual === 'string' ? !filter.contains || actual.toLocaleLowerCase('ru').includes(filter.contains.toLocaleLowerCase('ru')) : (filter.minimum == null || actual >= filter.minimum) && (filter.maximum == null || actual <= filter.maximum)
  })
  for (const sort of [...query.sorts].reverse()) rows.sort((a, b) => {
    const left = value(a, sort.field), right = value(b, sort.field)
    return (typeof left === 'string' ? left.localeCompare(String(right), 'ru') : left - Number(right)) * (sort.descending ? -1 : 1)
  })
  const filtered = rows.map((row) => row.studentId)
  const filteredLessons = scope(query.subjectId, query.lessonTypes)
  const allMetrics = filteredLessons.flatMap((lesson) => report(lesson).entries.filter((entry) => filtered.includes(entry.userId)))
  const p = allMetrics.filter((entry) => entry.status === 'PRESENT').length, e = allMetrics.filter((entry) => entry.status === 'EXCUSED').length, a = allMetrics.filter((entry) => entry.status === 'ABSENT').length
  const total = p + e + a
  const sumTickets = (kind: 'lateCheckin' | 'excuse') => rows.reduce((sum, row) => ({ submitted: sum.submitted + row[kind].submitted, approved: sum.approved + row[kind].approved, rejected: sum.rejected + row[kind].rejected }), { submitted: 0, approved: 0, rejected: 0 })
  const totalElements = rows.length, totalPages = Math.ceil(totalElements / query.size)
  return { ...shell, context: context(query.subjectId, query.lessonTypes), summary: { present: metric(p, total), presentOrExcused: metric(p + e, total), excused: metric(e, total), absent: metric(a, total) }, summaryTickets: { lateCheckin: sumTickets('lateCheckin'), excuse: sumTickets('excuse') }, filteredStudents: totalElements, rows: rows.slice(query.page * query.size, (query.page + 1) * query.size), subjects: subjects.map((subject) => ({ id: subject.id, label: subject.name, lessonTypes: subject.lessonTypes.map((code) => ({ code, label: typeLabels[code]! })) })), reasonCounts: [{ code: 'ILLNESS', label: 'Болезнь', count: e }], page: query.page, size: query.size, totalElements, totalPages, hasPrevious: query.page > 0, hasNext: query.page + 1 < totalPages, emptyState: totalElements ? 'NONE' : 'FILTERED_EMPTY' }
}
export function statsTrend(query: HeadmanStatsTrendQuery, studentId?: number): HeadmanStatsTrendResponse {
  const subjectId = query.mode === 'SUBJECT' ? query.subjectId : null, types = query.mode === 'SUBJECT' ? query.lessonTypes ?? [] : []
  const from = query.mode === 'WEEK' ? query.weekStart : semester.dateFrom, to = query.mode === 'WEEK' ? plus(from, 6) : semester.dateTo
  const daily = query.bucketUnit === 'DAY'
  const points: HeadmanStatsTrendResponse['points'][number][] = []
  for (let cursor = daily ? from : monday(from); cursor <= to; cursor = plus(cursor, daily ? 1 : 7)) {
    const start = cursor < from ? from : cursor, end = (daily ? cursor : plus(cursor, 6)) > to ? to : daily ? cursor : plus(cursor, 6)
    const metrics = counts(scope(subjectId, types).filter((lesson) => lesson.date >= start && lesson.date <= end), studentId)
    points.push({ key: cursor, label: new Date(`${start}T12:00:00Z`).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', timeZone: 'UTC' }), from: start, to: end, present: personalMetric(metrics.present.numerator, metrics.present.denominator), presentOrExcused: personalMetric(metrics.presentOrExcused.numerator, metrics.present.denominator) })
  }
  return { context: context(subjectId, types), mode: query.mode, points, emptyState: points.some((point) => point.present.denominator) ? 'NONE' : 'NO_COMPLETED_LESSONS', formats: [{ code: 'png', label: 'PNG', contentType: 'image/png', extension: 'png' }, { code: 'html', label: 'HTML', contentType: 'text/html', extension: 'html' }] }
}
export function studentLessons(studentId: number): readonly HeadmanStatsLesson[] {
  return [...completed].reverse().map((lesson) => {
    const entry = report(lesson).entries.find((item) => item.userId === studentId)!
    return { lessonId: lesson.id, lessonDate: lesson.date, lessonNumber: lesson.lessonNumber, subjectId: lesson.subjectId, subjectName: subjects.find((subject) => subject.id === lesson.subjectId)!.name, lessonType: lesson.lessonType!, startsAt: `${lesson.date}T${lesson.startTime}+03:00`, endsAt: `${lesson.date}T${lesson.endTime}+03:00`, room: lesson.room, status: entry.status === 'PRESENT' || entry.status === 'ABSENT' || entry.status === 'EXCUSED' ? entry.status : null, source: entry.source, markRevision: entry.markRevision, editable: true, blockedReason: null, approvedTicket: entry.approvedTicket }
  })
}
export function statsDetail(studentId: number): HeadmanStatsStudentDetailResponse {
  const row = studentRow(studentId, null, [])
  const trend = statsTrend({ mode: 'SEMESTER', bucketUnit: 'WEEK' }, studentId)
  const page = { page: 0, size: 10, totalElements: 0, totalPages: 0, hasPrevious: false, hasNext: false, items: [] }
  const late = requests.filter((item) => item.summary.studentId === studentId && item.summary.kind === 'LATE_CHECKIN')
  const excuses = requests.filter((item) => item.summary.studentId === studentId && item.summary.kind === 'EXCUSE')
  return { context: context(), student: { id: studentId, displayName: row.displayName }, metrics: row.metrics, sources: row.sources, lateCheckin: row.lateCheckin, excuse: row.excuse, rank: { available: true, visibility: 'VISIBLE', participantCount: members.length, position: [...members].sort((a, b) => studentRow(b.id, null, []).metrics.present.percent - studentRow(a.id, null, []).metrics.present.percent).findIndex((member) => member.id === studentId) + 1 }, subjects: subjects.map((subject) => ({ subjectId: subject.id, subjectName: subject.name, metrics: counts(scope(subject.id, []), studentId), lessonTypes: subject.lessonTypes.map((code) => ({ code, label: typeLabels[code]!, metrics: counts(scope(subject.id, [code]), studentId) })) })), weeks: trend.points.map((point) => ({ ...point, weekStart: monday(point.from) })), lateCheckins: { ...page, totalElements: late.length, totalPages: late.length ? 1 : 0, items: late.map((item) => ({ id: item.summary.id, lessonDate: item.lessons[0]!.date!, subjectId: item.lessons[0]!.subjectId, subjectName: item.lessons[0]!.subjectName!, lessonType: item.lessons[0]!.subjectType!, lessonNumber: item.lessons[0]!.lessonNumber, submittedAt: item.summary.createdAt!, decidedAt: item.summary.decisionAt, status: item.summary.status, origin: 'MANUAL' })) }, excuses: { ...page, totalElements: excuses.length, totalPages: excuses.length ? 1 : 0, items: excuses.map((item) => ({ id: item.summary.id, submittedAt: item.summary.createdAt!, decidedAt: item.summary.decisionAt, status: item.summary.status === 'PENDING' ? 'SUBMITTED' : item.summary.status, lessons: item.lessons.map((lesson) => ({ lessonId: lesson.lessonId!, lessonDate: lesson.date!, subjectName: lesson.subjectName!, lessonType: lesson.subjectType!, lessonNumber: lesson.lessonNumber })) })) }, emptyState: 'NONE' }
}

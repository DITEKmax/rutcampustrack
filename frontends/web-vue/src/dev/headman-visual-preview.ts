import { headmanPreviewMark } from './headman-visual-marks'
import type { HeadmanStatsQuery, HeadmanStatsTrendQuery } from '../../../mobile-core/src/features/headman-stats/headman-stats-client'
import { actions, assistants, assignments, groupId, headmanDate, headmanNow, homeworks, homeworkHistory, lessonById, lessons, members, monday, permissions, plus, previewPdf, report, requests, scheduleItems, semester, semesters, slots, subjects, teachers } from './headman-visual-data'
import { statsCatalog, statsDetail, statsOverview, statsResponse, statsTrend, studentLessons } from './headman-visual-stats'

const json = (value: unknown, status = 200) => Response.json(value, { status })
const missing = () => json({ title: 'Синтетическая запись не найдена' }, 404)
const formats = [{ code: 'xlsx', label: 'Excel', contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', extension: 'xlsx' }, { code: 'pdf', label: 'PDF', contentType: 'application/pdf', extension: 'pdf' }]
function page(url: URL, key: string, items: readonly unknown[], extra = {}) {
  const number = Number(url.searchParams.get('page') ?? 0), size = Number(url.searchParams.get('size') ?? 100)
  if (!Number.isSafeInteger(number) || number < 0 || !Number.isSafeInteger(size) || size < 1 || size > 100) return json({ title: 'Некорректная страница preview' }, 400)
  return json({ _embedded: { [key]: items.slice(number * size, (number + 1) * size) }, page: { number, size, totalElements: items.length, totalPages: Math.ceil(items.length / size) }, ...extra })
}
export function weekPolicy(date: string) {
  const start = monday(date)
  const index = Math.round((Date.parse(`${start}T12:00:00Z`) - Date.parse(`${monday(semester.dateFrom)}T12:00:00Z`)) / 604800000)
  const thursday = new Date(`${plus(start, 3)}T12:00:00Z`)
  const first = new Date(Date.UTC(thursday.getUTCFullYear(), 0, 1))
  const isoWeek = Math.ceil(((thursday.getTime() - first.getTime()) / 86400000 + 1) / 7)
  return { weekStart: start, weekEnd: plus(start, 6), isoWeek, weekType: index % 2 ? 'EVEN' : 'ODD', parityNumber: index % 2 ? 2 : 1, label: index % 2 ? '2 неделя' : '1 неделя', weekTypes: [{ weekType: 'ALL', parityNumber: null, label: 'Каждую неделю' }, { weekType: 'ODD', parityNumber: 1, label: '1 неделя' }, { weekType: 'EVEN', parityNumber: 2, label: '2 неделя' }] }
}
// Exact read contracts plus one explicitly authorized in-memory synthetic mark/clear operation.
// Password, upload, decision, schedule-write and export operations remain unsupported.
export async function headmanPreview(url: URL, method: string, body: () => Promise<Record<string, unknown>>): Promise<Response | null> {
  const path = url.pathname
  const stats = '/api/attendance/reports/headman/stats'
  if (method === 'POST' && path === `${stats}/query`) return json(statsResponse(await body() as unknown as HeadmanStatsQuery))
  if (method === 'POST' && path === `${stats}/trend`) return json(statsTrend(await body() as unknown as HeadmanStatsTrendQuery))
  const personalTrend = path.match(/^\/api\/attendance\/reports\/headman\/stats\/students\/(\d+)\/trend$/)
  if (method === 'POST' && personalTrend) return members.some((member) => member.id === Number(personalTrend[1])) ? json(statsTrend(await body() as unknown as HeadmanStatsTrendQuery, Number(personalTrend[1]))) : missing()
  const marked = await headmanPreviewMark(url, method, body)
  if (marked) return marked
  if (method !== 'GET') return null
  if (path === '/api/academic/semesters') return page(url, 'semesterResponseList', semesters)
  if (path === '/api/academic/subjects') return page(url, 'subjectResponseList', subjects)
  const deletion = path.match(/^\/api\/academic\/subjects\/(\d+)\/deletion-preview$/)
  if (deletion) {
    const subjectId = Number(deletion[1]), item = subjects.find((row) => row.id === subjectId)
    return item ? json({ subjectId: String(subjectId), semesterId: String(semester.id), groupId: String(groupId), subjectName: item.name, previewDigest: 'b'.repeat(64), academicRows: item.assignments.length + homeworks.filter((row) => row.subjectId === subjectId).length, scheduleRows: lessons.filter((row) => row.subjectId === subjectId).length, attendanceRows: lessons.filter((row) => row.subjectId === subjectId && row.status === 'CLOSED').length * members.length, mixedTickets: requests.filter((row) => row.lessons.some((lesson) => lesson.subjectId === subjectId) && row.lessons.some((lesson) => lesson.subjectId !== subjectId)).length }) : missing()
  }
  const subject = path.match(/^\/api\/academic\/subjects\/(\d+)$/)
  if (subject) return subjects.some((item) => item.id === Number(subject[1])) ? json(subjects.find((item) => item.id === Number(subject[1]))) : missing()
  if (path === '/api/academic/groups/my/members') return page(url, 'userResponseList', members)
  if (path === '/api/academic/assistants/permissions') return json(permissions)
  if (path === '/api/academic/assistants') return page(url, 'assistantResponseList', assistants)
  if (path === '/api/academic/users/by-ids') return page(url, 'userSummaryResponseList', teachers.filter((teacher) => url.searchParams.get('ids')?.split(',').includes(String(teacher.id))))
  if (path === '/api/academic/users/teachers/search') return page(url, 'teacherLookupResponseList', teachers.filter((teacher) => `${teacher.fullName} ${teacher.employeeNumber}`.toLocaleLowerCase('ru').includes((url.searchParams.get('search') ?? '').toLocaleLowerCase('ru'))))
  if (path === '/api/schedule/academic-week-policy') return json(weekPolicy(url.searchParams.get('date') ?? headmanDate))
  if (path === '/api/schedule/lesson-slots') return json({ timezone: 'Europe/Moscow', slots })
  if (path === '/api/academic/assignments') return page(url, 'assignmentResponseList', assignments)
  if (path === '/api/schedule/items') return page(url, 'scheduleItemResponseList', scheduleItems)
  const schedule = path.match(/^\/api\/schedule\/items\/(\d+)$/)
  if (schedule) return scheduleItems.some((item) => item.id === Number(schedule[1])) ? json(scheduleItems.find((item) => item.id === Number(schedule[1]))) : missing()
  if (path === '/api/schedule/one-off-lessons') return page(url, 'oneOffLessonResponseList', [])
  if (path === `/api/schedule/groups/${groupId}/lessons`) {
    const from = url.searchParams.get('dateFrom') ?? semester.dateFrom, to = url.searchParams.get('dateTo') ?? semester.dateTo
    const progress = { semesterId: semester.id, generatedAt: headmanNow, items: subjects.flatMap((item) => item.lessonTypes.map((lessonType) => ({ subjectId: item.id, lessonType, done: lessons.filter((lesson) => lesson.subjectId === item.id && lesson.lessonType === lessonType && lesson.status === 'CLOSED').length, total: lessons.filter((lesson) => lesson.subjectId === item.id && lesson.lessonType === lessonType && lesson.current && lesson.status !== 'CANCELLED' && lesson.status !== 'TRANSFERRED').length }))) }
    return page(url, 'lessonResponseList', lessons.filter((lesson) => lesson.date >= from && lesson.date <= to), { semesterProgress: progress })
  }
  if (path === '/api/schedule/lessons/next') {
    const lesson = lessons.find((item) => item.date >= (url.searchParams.get('fromDate') ?? headmanDate) && item.subjectId === Number(url.searchParams.get('subjectId')) && item.lessonType === url.searchParams.get('lessonType') && item.current && item.status !== 'CANCELLED' && item.status !== 'TRANSFERRED')
    return lesson ? json({ ...lesson, lessonId: lesson.id, semesterId: semester.id }) : new Response(null, { status: 204 })
  }
  const attendance = path.match(/^\/api\/attendance\/reports\/lesson\/(\d+)(\/actions)?$/)
  if (attendance) {
    const lesson = lessonById(Number(attendance[1]))
    if (!lesson) return missing()
    if (attendance[2]) return json(actions(lesson))
    const data = report(lesson)
    // The wire contract uses null for an unmarked entry; EMPTY is a client projection.
    return json({ ...data, entries: data.entries.map((entry) => ({ ...entry, status: entry.status === 'EMPTY' ? null : entry.status })) })
  }
  if (path === '/api/academic/homeworks') return page(url, 'homeworkResponseList', homeworks.filter((item) => item.archived === (url.searchParams.get('archived') === 'true')))
  const homework = path.match(/^\/api\/academic\/homeworks\/(\d+)(\/history)?$/)
  if (homework) {
    const item = homeworks.find((row) => row.id === Number(homework[1]))
    return item ? homework[2] ? json({ content: homeworkHistory(item), page: { number: 0, size: 100, totalElements: homeworkHistory(item).length, totalPages: 1 } }) : json(item) : missing()
  }
  if (path === '/api/attendance/requests') {
    const pageNumber = Number(url.searchParams.get('page') ?? 0), size = Number(url.searchParams.get('size') ?? 20)
    const items = requests.filter(({ summary: item }) => (url.searchParams.get('bucket') === 'ARCHIVE' ? item.status !== 'PENDING' : item.status === 'PENDING') && (!url.searchParams.get('type') || item.kind === url.searchParams.get('type')) && (!url.searchParams.get('studentName') || item.studentName!.toLocaleLowerCase('ru').includes(url.searchParams.get('studentName')!.toLocaleLowerCase('ru'))) && (!url.searchParams.get('coverageDateFrom') || item.coverageEnd! >= url.searchParams.get('coverageDateFrom')!) && (!url.searchParams.get('coverageDateTo') || item.coverageStart! <= url.searchParams.get('coverageDateTo')!)).sort((a, b) => { const field = url.searchParams.get('bucket') === 'ARCHIVE' ? 'decisionAt' : 'createdAt'; return (b.summary[field] ?? '').localeCompare(a.summary[field] ?? '') || a.summary.kind.localeCompare(b.summary.kind) || a.summary.id.localeCompare(b.summary.id) })
    return json({ content: items.slice(pageNumber * size, (pageNumber + 1) * size).map((item) => item.summary), page: pageNumber, size, totalElements: items.length, totalPages: Math.ceil(items.length / size) })
  }
  const attachment = path.match(/^\/api\/attendance\/requests\/([^/]+)\/attachments\/([^/]+)$/)
  if (attachment) {
    const item = requests.find(request => request.summary.id === decodeURIComponent(attachment[1]!))?.attachments.find(file => file.id === decodeURIComponent(attachment[2]!))
    return item ? new Response(previewPdf, { headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="preview-confirmation.pdf"' } }) : missing()
  }
  const request = path.match(/^\/api\/attendance\/requests\/([^/]+)$/)
  if (request) return requests.some((item) => item.summary.id === request[1]) ? json(requests.find((item) => item.summary.id === request[1])) : missing()
  const budget = path.match(/^\/api\/attendance\/requests\/students\/(\d+)\/late-checkin-budget$/)
  if (budget) return members.some((member) => member.id === Number(budget[1])) ? json({ studentId: Number(budget[1]), semesterId: semester.id, limit: 5, used: Number(budget[1]) % 3, remaining: 5 - Number(budget[1]) % 3 }) : missing()
  const credential = path.match(/^\/api\/auth\/headman\/students\/(\d+)\/initial-password$/)
  if (credential) return members.some((member) => member.id === Number(credential[1])) ? json({ studentId: credential[1], credentialsVersion: '1', state: 'STUDENT_CHANGED', origin: null, password: null }) : missing()
  if (path === `${stats}/semesters`) return json(statsCatalog)
  if (path === `${stats}/overview`) return json(statsOverview)
  const personal = path.match(/^\/api\/attendance\/reports\/headman\/stats\/students\/(\d+)\/(detail|lessons)$/)
  if (personal) {
    const id = Number(personal[1])
    if (!members.some((member) => member.id === id)) return missing()
    if (personal[2] === 'detail') return json(statsDetail(id))
    const number = Number(url.searchParams.get('page') ?? 0), size = Number(url.searchParams.get('size') ?? 20)
    const items = studentLessons(id).filter((lesson) => (!url.searchParams.get('subjectId') || lesson.subjectId === Number(url.searchParams.get('subjectId'))) && (!url.searchParams.getAll('lessonTypes').length || url.searchParams.getAll('lessonTypes').includes(lesson.lessonType)))
    return json({ items: items.slice(number * size, (number + 1) * size), page: number, size, totalElements: items.length, totalPages: Math.ceil(items.length / size), hasPrevious: number > 0, hasNext: (number + 1) * size < items.length })
  }
  if (path === '/api/attendance/reports/headman/group-composition/formats') return json({ formats })
  if (path === '/api/attendance/reports/headman/subject-journal/formats') return json([{ code: 'xlsx', label: 'Excel' }, { code: 'pdf', label: 'PDF' }, { code: 'docx', label: 'Word' }, { code: 'html', label: 'HTML' }, { code: 'png', label: 'PNG' }])
  if (path === '/api/attendance/reports/headman-weekly/weeks') {
    const weeks = []
    for (let date = monday(semester.dateFrom), index = 1; date <= semester.dateTo; date = plus(date, 7), index++) weeks.push({ weekOfSemester: index, isoWeek: weekPolicy(date).isoWeek, label: `${index} неделя`, weekStart: date, weekEnd: plus(date, 6), current: date === monday(headmanDate) })
    return json({ semesterId: semester.id, semesterName: semester.name, semesterDateFrom: semester.dateFrom, semesterDateTo: semester.dateTo, weeks, formats })
  }
  return null
}

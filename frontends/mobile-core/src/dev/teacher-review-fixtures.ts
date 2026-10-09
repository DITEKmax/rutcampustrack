import { TeacherApi, type TeacherDayLesson, type TeacherRosterEntry, type TeacherStatsMetric, type TeacherExportFormat } from '../features/teacher/teacher-client'
import { CampusMapClient } from '../api/map-client'
import { DEFAULT_PASSWORD_POLICY, ProfileRequestError, type ProfilePort, type ProfileSnapshot } from '../features/profile/profile-types'
import { syntheticAttachmentResponse } from './attachment-preview-fixture'
import { mapFixture, mapPlanFixture } from './profile-map-fixtures'

/** Dev-only transport seam. All people, groups, sessions and reports are synthetic. */
export const teacherReviewStates = [
  'home', 'home-date-choice', 'home-empty', 'home-loading', 'home-error', 'home-forbidden',
  'group-subjects', 'journal-unselected-types', 'journal-table-wide', 'journal-chart-group', 'journal-chart-student', 'journal-subject-switch', 'journal-manual-reason',
  'journal-loading', 'journal-error', 'journal-empty', 'journal-no-scheduled-subject', 'journal-excuse', 'journal-excuse-return',
  'lesson-export-options', 'lesson-export-loading', 'lesson-export-error', 'map-png-ready', 'map-png-processing', 'map-png-failed',
  'attendance', 'attendance-group-options', 'attendance-subject-options', 'attendance-period',
  'attendance-catalog-loading', 'attendance-catalog-error', 'attendance-no-groups', 'attendance-empty', 'attendance-loading', 'attendance-error', 'attendance-pagination', 'attendance-journal-page-return',
  'lesson', 'lesson-active', 'lesson-planned', 'lesson-manual-reason', 'lesson-empty', 'lesson-loading', 'lesson-error', 'lesson-forbidden',
  'journal', 'journal-pagination', 'journal-export-options', 'journal-export-loading', 'journal-export-error',
  'excuse', 'excuse-pending', 'excuse-image', 'excuse-pdf', 'excuse-expired', 'excuse-file-error',
  'stats-groups', 'stats-students', 'stats-sort', 'stats-sort-students', 'stats-filters', 'stats-filters-students', 'stats-filter-error', 'stats-multisort', 'stats-multisort-options', 'stats-multisort-removed',
  'stats-group-options', 'stats-subject-options', 'stats-export-options', 'stats-export-error',
  'stats-catalog-loading', 'stats-catalog-error', 'stats-no-groups', 'stats-no-groups-students', 'stats-empty', 'stats-zero', 'stats-loading', 'stats-error',
  'profile', 'roles', 'roles-pending', 'roles-error', 'appearance', 'security', 'sessions', 'history', 'map', 'map-floor-missing', 'map-building-options', 'map-floor-options',
] as const
export type TeacherReviewState = typeof teacherReviewStates[number]
export const teacherReviewDate = '2026-10-08'
const serverNow = '2026-10-08T08:10:00Z'
const semester = { id: 7, name: 'Осенний семестр 2026/2027', dateFrom: '2026-09-01', dateTo: '2027-01-31' }
const subjectOptions = [
  { groupId: 101, subjectId: 201, subjectName: 'Программирование', lessonTypes: ['lecture', 'practice', 'lab'] },
  { groupId: 101, subjectId: 202, subjectName: 'Базы данных', lessonTypes: ['lecture', 'practice'] },
  { groupId: 102, subjectId: 201, subjectName: 'Программирование', lessonTypes: ['lecture', 'practice'] },
]
const assignments = subjectOptions.map((item, i) => ({ id: 301 + i, teacherId: 900, ...item, groupName: item.groupId === 101 ? 'ИВТ-241' : 'ИВТ-242', semesterId: 7, lessonType: 'lecture', validFrom: '2026-09-01', validUntilExclusive: null }))
const lessons: TeacherDayLesson[] = [
  { id: 401, assignmentId: 301, groupId: 101, groupName: 'ИВТ-241', subjectId: 201, subjectName: 'Программирование', semesterId: 7, lessonType: 'lecture', date: teacherReviewDate, lessonNumber: 1, startsAt: '09:00:00', endsAt: '10:30:00', room: 'А-402', status: 'CLOSED', oneOff: false, moved: false, cancelled: false },
  { id: 402, assignmentId: 301, groupId: 101, groupName: 'ИВТ-241', subjectId: 201, subjectName: 'Программирование', semesterId: 7, lessonType: 'practice', date: teacherReviewDate, lessonNumber: 2, startsAt: '10:40:00', endsAt: '12:10:00', room: 'А-405', status: 'ACTIVE', oneOff: false, moved: false, cancelled: false },
  { id: 403, assignmentId: 302, groupId: 101, groupName: 'ИВТ-241', subjectId: 202, subjectName: 'Базы данных', semesterId: 7, lessonType: 'lecture', date: teacherReviewDate, lessonNumber: 3, startsAt: '12:30:00', endsAt: '14:00:00', room: 'Б-215', status: 'PLANNED', oneOff: true, moved: false, cancelled: false },
  { id: 404, assignmentId: 303, groupId: 102, groupName: 'ИВТ-242', subjectId: 201, subjectName: 'Программирование', semesterId: 7, lessonType: 'lecture', date: teacherReviewDate, lessonNumber: 4, startsAt: '14:10:00', endsAt: '15:40:00', room: 'А-402', status: 'CANCELLED', oneOff: false, moved: false, cancelled: true },
].map(lesson => ({ ...lesson, assignmentId: lesson.oneOff ? null : lesson.assignmentId, cancellationReason: lesson.cancelled ? 'Преподаватель на конференции. Занятие отменено по согласованию с кафедрой.' : null })) as TeacherDayLesson[]
const names = ['Александрова Мария Игоревна', 'Белов Артём Сергеевич', 'Васильева Софья Андреевна', 'Громов Иван Павлович', 'Данилова Ева Михайловна', 'Егоров Никита Олегович', 'Жукова Анна Дмитриевна']
const roster: TeacherRosterEntry[] = names.map((displayName, i) => ({
  studentId: 501 + i, displayName, status: i === 0 ? 'present' : i === 1 ? 'excused' : i === 2 ? 'excused' : i === 6 ? null : 'absent',
  symbol: i === 0 ? '+' : i === 1 || i === 2 ? 'у' : i === 6 ? null : 'н', source: i === 0 ? 'student_geo' : i === 2 ? 'NO_TICKET' : i === 5 ? 'auto_scheduler' : 'headman',
  recordPresent: i !== 6, pendingTicket: i === 4, autoAbsent: i === 5, ticketId: i === 1 || i === 4 ? 'synthetic-excuse' : null,
  excuseType: i === 1 || i === 2 ? 'ILLNESS' : null, excuseReason: i === 1 || i === 2 ? 'Болезнь' : null, excuseComment: i === 2 ? 'Подтверждено справкой' : null,
  attachmentId: i === 1 ? 'synthetic-attachment' : null, attachmentName: i === 1 ? 'Справка.pdf' : null,
  attachmentContentType: i === 1 ? 'application/pdf' : null, attachmentSize: i === 1 ? 12480 : null,
}))
// Completed history is independent of today's ACTIVE/PLANNED cards. Missing
// attendance for an enrolled student is ABSENT; nonmembers have no cell.
const history: TeacherDayLesson[] = Array.from({ length: 225 }, (_, index) => {
  const base = lessons[0]!
  const date = new Date(Date.UTC(2026, 8, 1 + Math.floor(index / 7))).toISOString().slice(0, 10)
  return { ...base, id: 1000 + index, date, lessonNumber: index % 7 + 1, lessonType: ['lecture', 'practice', 'lab'][index % 3]!, cancellationReason: null }
})
const allLessons = [...history, ...lessons]
function cellsFor(student: TeacherRosterEntry, selected: readonly TeacherDayLesson[]) {
  return selected.filter(lesson => student.studentId !== 507 || lesson.date >= '2026-09-15').map(lesson => ({ ...student, lessonId: lesson.id,
    ...(student.studentId === 507 && lesson.status === 'CLOSED' ? { status: 'absent', symbol: 'н', recordPresent: false, autoAbsent: false } : {}) }))
}
const taughtSubjectOptions = [...subjectOptions, { groupId: 101, subjectId: 203, subjectName: 'Дискретная математика', lessonTypes: ['lecture'] }]
const historicalSubjectOptions = [...subjectOptions, { groupId: 101, subjectId: 204, subjectName: 'История вычислительной техники', lessonTypes: ['lecture'] }]
const slots = [ ['09:00:00', '10:30:00'], ['10:40:00', '12:10:00'], ['12:30:00', '14:00:00'], ['14:10:00', '15:40:00'], ['16:00:00', '17:30:00'], ['17:40:00', '19:10:00'], ['19:20:00', '20:50:00'], ['21:00:00', '22:30:00'] ].map(([startTime, endTime], index) => ({ lessonNumber: index + 1, startTime, endTime }))
const metric = (numerator: number, denominator: number): TeacherStatsMetric => ({ numerator, denominator, percent: denominator ? numerator * 100 / denominator : 0 })
function metrics(selected: readonly TeacherDayLesson[], members: readonly TeacherRosterEntry[]) {
  const cells = members.flatMap(member => cellsFor(member, selected))
  const present = cells.filter(cell => cell.status === 'present').length
  const excused = cells.filter(cell => cell.status === 'excused').length
  return { present: metric(present, cells.length), presentOrExcused: metric(present + excused, cells.length), excused: metric(excused, cells.length), absent: metric(cells.length - present - excused, cells.length) }
}
const formats: TeacherExportFormat[] = [
  { code: 'docx', label: 'Word', extension: 'docx', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' },
  { code: 'pdf', label: 'PDF', extension: 'pdf', contentType: 'application/pdf' },
  { code: 'png', label: 'PNG (ZIP)', extension: 'zip', contentType: 'application/zip' },
  { code: 'html', label: 'HTML', extension: 'html', contentType: 'text/html' },
  { code: 'xlsx', label: 'Excel', extension: 'xlsx', contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
]
export function createTeacherReviewFixtures(state: TeacherReviewState) {
  const requests: string[] = []
  const response = (value: unknown) => new Response(JSON.stringify(value, (key, item: unknown) => typeof item === 'number' && /^(id|.*Id|decisionBy)$/.test(key) ? String(item) : item), { headers: { 'Content-Type': 'application/json' } })
  const error = (status = 503) => Response.json({ code: status === 403 ? 'OUT_OF_SCOPE' : 'DEPENDENCY_UNAVAILABLE', detail: status === 403 ? 'Нет доступа к этой группе.' : 'Сервис временно недоступен. Попробуй ещё раз.' }, { status })
  const fetcher: typeof fetch = async (input, init) => {
    const url = new URL(String(input), window.location.origin)
    requests.push(url.pathname + url.search)
    if ((init?.method ?? 'GET') !== 'GET') throw new Error('Teacher review permits GET only')
    const path = url.pathname.replace('/api/v1/teacher', '')
    if (url.pathname === '/api/schedule/lesson-slots') return response({ timezone: 'Europe/Moscow', slots })
    const target = state.split('-')[0]
    const targetRequest = target === 'home' ? path === '/day' : target === 'attendance' || target === 'journal' ? path === '/journal' : target === 'lesson' ? path.startsWith('/lessons/') : target === 'stats' ? path === '/stats' : false
    if (targetRequest && !state.includes('-export-') && state.endsWith('-loading')) return new Promise<Response>(() => undefined)
    if (targetRequest && !state.includes('-export-') && state.endsWith('-error')) return error()
    if (targetRequest && state.endsWith('-forbidden')) return error(403)
    if (path === '/semester') return response(semester)
    if (path === '/catalog' && state.endsWith('catalog-loading')) return new Promise<Response>(() => undefined)
    if (path === '/catalog' && state.endsWith('catalog-error')) return error()
    if (path === '/catalog' && (state.endsWith('no-groups') || state === 'stats-no-groups-students')) return response({ groups: [], subjectOptions: [], taughtSubjectOptions: [], serverNow })
    if (path === '/catalog') return response({ groups: [ { id: '101', name: 'ИВТ-241' }, { id: '102', name: 'ИВТ-242' } ], subjectOptions: historicalSubjectOptions, taughtSubjectOptions, serverNow })
    if (path === '/assignments') return response(assignments)
    if (path === '/day') return response({ semesterId: 7, date: url.searchParams.get('date') ?? teacherReviewDate, lessons: state === 'home-empty' ? [] : lessons, serverNow })
    if (path.startsWith('/lessons/')) {
      const lesson = allLessons.find(item => String(item.id) === path.split('/').at(-1)) ?? lessons[0]!
      const unrecorded = (item: TeacherRosterEntry): TeacherRosterEntry => ({ ...item, status: null, symbol: null, source: null, recordPresent: false, pendingTicket: false, autoAbsent: false, ticketId: null, excuseType: null, excuseReason: null, excuseComment: null, attachmentId: null, attachmentName: null, attachmentContentType: null, attachmentSize: null })
      const lessonRoster = state === 'lesson-planned' ? roster.map(unrecorded) : state === 'lesson-active' ? roster.map((item, index) => index < 2 ? item : unrecorded(item)) : roster
      return response({ lesson, roster: state === 'lesson-empty' ? [] : lessonRoster, serverNow })
    }
    if (path === '/journal') {
      const page = Number(url.searchParams.get('page') ?? 0)
      const pageSize = Number(url.searchParams.get('pageSize') ?? 100)
      const selected = state === 'attendance-empty' || state === 'journal-empty' ? [] : allLessons.filter(item => item.groupId === Number(url.searchParams.get('groupId')) && item.subjectId === Number(url.searchParams.get('subjectId')) && url.searchParams.getAll('lessonType').includes(item.lessonType)
        && (!url.searchParams.get('dateFrom') || item.date >= url.searchParams.get('dateFrom')!) && (!url.searchParams.get('dateTo') || item.date <= url.searchParams.get('dateTo')!)
        && (url.searchParams.get('pastOnly') !== 'true' || item.status === 'CLOSED' && !item.cancelled)).sort((a, b) => a.date.localeCompare(b.date) || a.lessonNumber - b.lessonNumber || a.id - b.id)
      const pageLessons = selected.slice(page * pageSize, (page + 1) * pageSize)
      return response({ lessons: pageLessons, students: roster.map(item => ({ studentId: item.studentId, displayName: item.displayName, cells: cellsFor(item, pageLessons) })), serverNow, page, pageSize, totalLessons: selected.length, hasMore: (page + 1) * pageSize < selected.length })
    }
    if (path === '/stats') {
      const scope = url.searchParams.get('scope') ?? 'groups'
      const empty = state === 'stats-empty' || state === 'stats-no-groups'
      const selected = empty || state === 'stats-zero' ? [] : allLessons.filter(lesson => lesson.status === 'CLOSED' && !lesson.cancelled
        && (scope !== 'students' || lesson.groupId === Number(url.searchParams.get('groupId')) && lesson.subjectId === Number(url.searchParams.get('subjectId')))
        && (!url.searchParams.getAll('lessonType').length || url.searchParams.getAll('lessonType').includes(lesson.lessonType)))
      return response({ scope, semesterId: 7, periodFrom: '2026-09-01', periodTo: teacherReviewDate, lessonsCount: selected.length,
        students: scope === 'students' && !empty ? roster.map(item => ({ studentId: item.studentId, displayName: item.displayName, ...metrics(selected, [item]) })) : [],
        groups: scope === 'groups' && !empty ? [101, 102].map((groupId, i) => { const groupLessons = selected.filter(lesson => lesson.groupId === groupId); return { groupId, groupName: i ? 'ИВТ-242' : 'ИВТ-241', lessonsCount: groupLessons.length, ...metrics(groupLessons, roster) } }) : [], subjectOptions: historicalSubjectOptions, serverNow })
    }
    if (path.endsWith('/export/formats')) return response({ formats })
    if (path.endsWith('/export')) {
      if (state.endsWith('export-loading')) return new Promise<Response>(() => undefined)
      if (state.endsWith('export-error')) return error()
      const format = formats.find(item => item.code === url.searchParams.get('format')) ?? formats[0]!
      return new Response('Synthetic gallery report', { headers: { 'Content-Type': format.contentType, 'Content-Disposition': `attachment; filename="synthetic-report.${format.extension}"` } })
    }
    if (path.includes('/attachments/')) {
      if (state === 'excuse-expired') return error(404)
      if (state === 'excuse-file-error') return error()
      return syntheticAttachmentResponse(state === 'excuse-image' ? 'image' : 'pdf')
    }
    if (path.startsWith('/excuses/')) return response({ id: 'synthetic-excuse', kind: 'EXCUSE', status: state === 'excuse-pending' ? 'SUBMITTED' : 'APPROVED', studentId: 502, studentName: names[1], groupId: 101, groupName: 'ИВТ-241', excuseType: 'ILLNESS', reason: 'Болезнь', comment: 'Справка о временной нетрудоспособности', createdAt: '2026-10-07T11:15:00Z', decisionBy: state === 'excuse-pending' ? null : 900, decisionByName: state === 'excuse-pending' ? null : 'Кузнецова Ольга Викторовна', decisionAt: state === 'excuse-pending' ? null : '2026-10-08T05:40:00Z', decisionComment: null, lessons: lessons.slice(0, 2), attachments: [{ id: 'synthetic-attachment', fileName: state === 'excuse-image' ? 'Справка.png' : 'Справка.pdf', contentType: state === 'excuse-image' ? 'image/png' : 'application/pdf', size: 12480, uploadedAt: '2026-10-07T11:15:00Z', expiresAt: state === 'excuse-expired' ? '2026-10-07T20:59:59Z' : '2027-02-01T00:00:00Z' }], serverNow })
    throw new Error(`Unexpected synthetic teacher API request ${path}`)
  }
  const snapshot: ProfileSnapshot = { sessionId: 'synthetic-session', userId: 'synthetic-teacher', displayName: 'Кузнецова Ольга Викторовна', sessionVersion: '1', rolesVersion: '1', activeRole: 'TEACHER', roles: [
    { grantId: 'synthetic-teacher-grant', role: 'TEACHER', status: 'ACTIVE', selectable: true, readOnly: false },
    { grantId: 'synthetic-admin-grant', role: 'ADMIN', status: 'ACTIVE', selectable: true, readOnly: false },
  ], readOnly: false, passwordPolicy: DEFAULT_PASSWORD_POLICY }
  const profile: ProfilePort = {
    async getSnapshot() { return snapshot },
    async selectRole(input) {
      if (state === 'roles-pending') return new Promise(() => undefined)
      if (state === 'roles-error') throw new ProfileRequestError('ROLE_NOT_SELECTABLE', 'Роль временно недоступна.')
      snapshot.activeRole = input.role; snapshot.sessionVersion = String(Number(input.expectedSessionVersion) + 1)
      return { session: snapshot, accessToken: 'synthetic-only', expiresIn: 3600 }
    },
    async listSessions() { return { items: [{ sessionId: 'synthetic-session', current: true, clientLabel: 'Chrome · Windows', authMethod: 'PASSWORD', createdAt: serverNow, lastSeenAt: serverNow }], nextCursor: null } },
    async listHistory() { return { items: [{ id: 'synthetic-event', type: 'LOGIN', authMethod: 'PASSWORD', clientLabel: 'Chrome · Windows', occurredAt: serverNow }], nextCursor: null } },
    async changePassword() { /* synthetic acknowledgement */ }, async logoutAll() { /* synthetic acknowledgement */ }, isOnline: () => true,
  }
  const map = new CampusMapClient({ accessToken: () => null, fetcher: async (input, init) => {
    if (!state.startsWith('map-')) return response({ schemaVersion: 1, validationPolicyVersion: 1, revision: 'synthetic-map', buildings: [] })
    const url = new URL(String(input), window.location.origin)
    requests.push(url.pathname + url.search)
    if ((init?.method ?? 'GET') === 'POST' && url.pathname.endsWith('/opens')) return new Response(null, { status: 204 })
    if ((init?.method ?? 'GET') !== 'GET') throw new Error('Synthetic map permits only reads and local opening acknowledgement')
    if (url.pathname === '/api/v1/map/manifest') return response(mapFixture('map'))
    if (/^\/api\/v1\/map\/buildings\/[^/]+\/floors\/[^/]+\/plan$/.test(url.pathname)) {
      const parts = url.pathname.split('/')
      const mapState = state === 'map-png-ready' ? 'map-png' : state === 'map-png-processing' ? 'map-processing' : state === 'map-png-failed' ? 'map-failed' : 'map'
      return response({ plan: mapPlanFixture(mapState, parts[5]!, parts[7]!) })
    }
    if (/\/assets\/png\//.test(url.pathname)) return syntheticAttachmentResponse('image')
    throw new Error(`Unexpected synthetic map request ${url.pathname}`)
  } })
  return { api: new TeacherApi({ accessToken: () => null, fetcher }), profile, map, requests }
}

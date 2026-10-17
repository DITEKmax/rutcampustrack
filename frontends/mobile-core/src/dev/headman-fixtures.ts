import { syntheticAttachmentResponse } from './attachment-preview-fixture'
import { HeadmanScheduleApi } from '../features/schedule/headman-schedule-client'
import { HeadmanRequestsApi } from '../features/headman-requests/headman-requests-client'
import { HeadmanLessonActionsApi } from '../features/headman-journal/headman-journal-actions-client'
import { HeadmanJournalApi } from '../features/headman-journal/headman-journal-client'
import { HeadmanSubjectsApi } from '../features/headman-subjects/headman-subjects-client'
import { HeadmanHomeworkApi } from '../features/homework/headman-homework-client'

export const headmanReviewStates = [
  'journal-tools-dialog',
  'weekly-download',
  'weekly-download-expanded',
  'weekly-download-multiple',
  'weekly-download-none',
  'weekly-download-format-open',
  'ticket-preview-unsupported',
  'ticket-preview-expired',
  'ticket-preview-pdf',
  'ticket-preview-pdf-ready',
  'action-log',
  'action-log-empty',
  'action-log-error',
  'action-log-marks',
  'action-log-homework',
  'action-log-geo-unavailable',
  'today-current',
  'today-homework-expanded',
  'today-past',
  'today-empty',
  'mixed-roster',
  'reason-form',
  'manual-new-empty',
  'manual-file',
  'student-ticket-link',
  'clear-confirm-manual',
  'clear-confirm-student-ticket',
  'clear-error',
  'offline',
  'longnames',
] as const
export function createHeadmanReviewApis(state: string) {
  const names = [
    'Алексей Громов',
    'Мария Васильева',
    'Валерия Ушакова',
    'Анна Иванова',
    'Артём Карпов',
    'Илья Соколов',
    'Вероника Белова',
    'Виктория Павлова',
    'Глеб Фёдоров',
    'Даниил Морозов',
    'Евгения Крылова',
    'Егор Никитин',
  ]
  const entries = names.map((name, index) => ({
    userId: index + 1,
    displayName: state === 'longnames' ? `${name} Константинопольский-Воскресенский` : name,
    status:
      index === 1 || index === 5
        ? 'EXCUSED'
        : index === 2
          ? 'ABSENT'
          : index === 3
            ? null
            : 'PRESENT',
    symbol: null,
    markRevision: index === 3 ? null : `fixture-revision-${index}-1`,
    source:
      index === 1 ? 'headman_excuse' : index === 3 ? null : index === 4 ? 'headman' : 'student_geo',
    excuseReason: index === 1 ? 'Болезнь' : index === 5 ? 'Олимпиада' : null,
    excuseType: index === 1 ? 'ILLNESS' : index === 5 ? 'COMPETITION_PARTICIPATION' : null,
    comment: null as string | null,
    attachmentId: index === 1 ? 'manual-file-1' : null,
    attachmentName: index === 1 ? 'Справка.pdf' : null,
    attachmentContentType: index === 1 ? 'application/pdf' : null,
    attachmentSize: index === 1 ? 12000 : null,
  }))
  const studentTicketState =
    state === 'student-ticket-link' ||
    state.startsWith('ticket-preview') ||
    state === 'clear-confirm-student-ticket'
  const ticketEntry = entries.find((entry) => entry.userId === (studentTicketState ? 2 : 6))!
  const rosterEntries = entries.map((entry) => ({
    ...entry,
    approvedTicket:
      entry === ticketEntry
        ? { kind: 'EXCUSE', id: `fixture-ticket-${entry.userId}`, status: 'APPROVED' }
        : null,
  }))
  if (studentTicketState) {
    rosterEntries[1]!.attachmentId = null
    rosterEntries[1]!.attachmentName = null
    rosterEntries[1]!.attachmentSize = null
  }
  let revisionConflict = state === 'clear-error'
  const lessons = [
    {
      id: 20,
      groupId: 4,
      subjectId: 2,
      date: '2026-10-06',
      status: 'CLOSED',
      lessonNumber: 2,
      startTime: '10:10:00',
      endTime: '11:40:00',
      room: 'Б-204',
      lessonType: 'PRACTICE',
      current: true,
      occurrenceRevision: '1',
      transferOperationId: null,
      transferState: null,
    },
    {
      id: 30,
      groupId: 4,
      subjectId: 1,
      date: '2026-10-06',
      status: 'ACTIVE',
      lessonNumber: 3,
      startTime: '18:00:00',
      endTime: '19:30:00',
      room: 'А-401',
      lessonType: 'LECTURE',
      current: true,
      occurrenceRevision: '1',
      transferOperationId: null,
      transferState: null,
    },
  ]
  const json = (value: unknown, status = 200) =>
    new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
  const page = (name: string, items: unknown[]) =>
    json({
      _embedded: { [name]: items },
      page: { number: 0, size: 100, totalElements: items.length, totalPages: 1 },
    })
  const fetcher: typeof fetch = async (input, init) => {
    const url = new URL(String(input), 'http://fixture.invalid')
    await new Promise((resolve) => setTimeout(resolve, 30))
    if (url.pathname === '/api/schedule/academic-week-policy') {
      const weeks = [
        {
          weekStart: '2026-09-28',
          weekEnd: '2026-10-04',
          isoWeek: 40,
          weekType: 'ODD',
          parityNumber: 1,
          label: '1',
        },
        {
          weekStart: '2026-10-05',
          weekEnd: '2026-10-11',
          isoWeek: 41,
          weekType: 'EVEN',
          parityNumber: 2,
          label: '2',
        },
        {
          weekStart: '2026-10-12',
          weekEnd: '2026-10-18',
          isoWeek: 42,
          weekType: 'ODD',
          parityNumber: 1,
          label: '1',
        },
        {
          weekStart: '2026-10-19',
          weekEnd: '2026-10-25',
          isoWeek: 43,
          weekType: 'EVEN',
          parityNumber: 2,
          label: '2',
        },
      ]
      const date = url.searchParams.get('date') ?? ''
      const week = weeks.find((item) => date >= item.weekStart && date <= item.weekEnd)
      return week
        ? json({
            ...week,
            weekTypes: [
              { weekType: 'ALL', parityNumber: null, label: 'Каждую неделю' },
              { weekType: 'ODD', parityNumber: 1, label: '1' },
              { weekType: 'EVEN', parityNumber: 2, label: '2' },
            ],
          })
        : json({ detail: 'Fixture week not available' }, 400)
    }
    if (url.pathname === '/api/academic/semesters')
      return page('semesterResponseList', [
        {
          id: 1,
          name: 'Осенний семестр 2026',
          dateFrom: '2026-09-01',
          dateTo: '2027-01-31',
          active: true,
        },
      ])
    if (url.pathname.endsWith('/actions')) {
      if (state === 'action-log-error')
        return json({ detail: 'История пары временно недоступна.' }, 503)
      return json({
        items:
          state === 'action-log-empty'
            ? []
            : [
                {
                  id: 'event-1',
                  occurredAt: '2026-10-06T15:12:00Z',
                  action: 'ATTENDANCE_SET',
                  actor: { userId: 10, role: 'HEADMAN', displayName: 'Максим Кузнецов' },
                  student: { userId: 5, displayName: 'Артём Карпов' },
                  before: null,
                  after: { status: 'PRESENT' },
                  geo: null,
                  ticket: null,
                  homework: null,
                },
                {
                  id: 'event-2',
                  occurredAt: '2026-10-06T15:10:00Z',
                  action: 'ATTENDANCE_SET',
                  actor: { userId: 10, role: 'HEADMAN', displayName: 'Максим Кузнецов' },
                  student: { userId: 2, displayName: 'Мария Васильева' },
                  before: null,
                  after: { status: 'EXCUSED' },
                  geo: null,
                  ticket: { kind: 'EXCUSE', id: 'fixture-ticket-2', status: 'APPROVED' },
                  homework: null,
                },
                {
                  id: 'event-4',
                  occurredAt: '2026-10-06T15:13:00Z',
                  action: 'GEO_ATTEMPT',
                  source: 'attendance-service',
                  outcome: 'REJECTED',
                  errorCode: 'CHECKIN_COOLDOWN',
                  actor: { userId: 5, role: 'STUDENT', displayName: 'Артём Карпов' },
                  student: { userId: 5, displayName: 'Артём Карпов' },
                  before: null,
                  after: null,
                  geo:
                    state === 'action-log-geo-unavailable'
                      ? null
                      : {
                          latitude: 55.7901,
                          longitude: 37.6086,
                          distanceMeters: 840,
                          radiusMeters: 100,
                          accuracyMeters: 15,
                          verdict: 'OUTSIDE_RADIUS',
                        },
                  ticket: null,
                  homework: null,
                },
                {
                  id: 'event-5',
                  occurredAt: '2026-10-06T15:14:00Z',
                  action: 'REQUEST_DECIDED',
                  outcome: 'APPLIED',
                  actor: { userId: 10, role: 'ASSISTANT', displayName: 'Максим Кузнецов' },
                  student: { userId: 3, displayName: 'Валерия Ушакова' },
                  before: { status: 'ABSENT' },
                  after: { status: 'EXCUSED' },
                  geo: null,
                  ticket: { kind: 'EXCUSE', id: 'fixture-ticket-3', status: 'APPROVED' },
                  homework: null,
                },
                {
                  id: 'event-6',
                  occurredAt: '2026-10-06T15:15:00Z',
                  action: 'ATTENDANCE_SET',
                  outcome: 'REJECTED',
                  errorCode: 'ATTENDANCE_REVISION_CONFLICT',
                  actor: { userId: 10, role: 'HEADMAN', displayName: 'Максим Кузнецов' },
                  student: { userId: 3, displayName: 'Валерия Ушакова' },
                  before: null,
                  after: null,
                  geo: null,
                  ticket: null,
                  homework: null,
                },
                {
                  id: 'event-3',
                  occurredAt: '2026-10-06T09:24:00Z',
                  action: 'HOMEWORK_CREATED',
                  source: 'academic-service',
                  outcome: 'APPLIED',
                  errorCode: null,
                  actor: { userId: 10, role: 'HEADMAN', displayName: 'Максим Кузнецов' },
                  student: null,
                  before: null,
                  after: null,
                  geo: null,
                  ticket: null,
                  homework: { id: '1', revision: 1 },
                },
              ],
        nextCursor: null,
      })
    }
    if (url.pathname.includes('/reports/lesson/'))
      return json({
        lessonId: Number(url.pathname.split('/').at(-1)),
        groupId: 4,
        subjectId: 1,
        semesterId: 1,
        lessonDate: '2026-10-06',
        lessonStatus: 'ACTIVE',
        editable: true,
        entries: rosterEntries,
      })
    if (url.pathname.match(/\/lessons\/\d+\/students\/\d+$/)) {
      const userId = Number(url.pathname.split('/').at(-1))
      const entry = rosterEntries.find((item) => item.userId === userId)
      if (!entry) return json({ detail: 'Студент не найден' }, 404)
      if (revisionConflict && init?.method === 'DELETE') {
        revisionConflict = false
        entry.markRevision = 'fixture-replaced-excuse'
        entry.excuseReason = 'Причина обновлена другим пользователем'
        return json({ detail: 'Отметка изменилась. Перечитай её перед повторным действием.' }, 409)
      }
      let payload: {
        status?: string
        expectedStatus?: string
        expectedRevision?: string
        excuseType?: string
        comment?: string
      } = {}
      if (typeof init?.body === 'string') payload = JSON.parse(init.body)
      if (init?.body instanceof FormData) {
        const request = init.body.get('request')
        if (request instanceof Blob) payload = JSON.parse(await request.text())
      }
      const expectedStatus =
        init?.method === 'DELETE' ? url.searchParams.get('expectedStatus') : payload.expectedStatus
      const expectedRevision =
        init?.method === 'DELETE'
          ? url.searchParams.get('expectedRevision')
          : payload.expectedRevision
      if (
        expectedStatus !== (entry.status ?? 'EMPTY') ||
        expectedRevision !== (entry.markRevision ?? 'EMPTY')
      )
        return json({ detail: 'Отметка изменилась.' }, 409)
      if (init?.method === 'DELETE') {
        entry.status = null
        entry.markRevision = null
        entry.attachmentId = null
        entry.attachmentName = null
        entry.attachmentSize = null
        entry.excuseReason = null
        entry.excuseType = null
        entry.source = null
        return new Response(null, { status: 204 })
      }
      entry.status = payload.status ?? 'EXCUSED'
      entry.excuseType = payload.excuseType ?? null
      entry.comment = payload.comment ?? null
      if (entry.status === 'EXCUSED')
        entry.excuseReason = payload.excuseType === 'ILLNESS' ? 'Болезнь' : 'Уважительная причина'
      entry.source = entry.status === 'EXCUSED' ? 'headman_excuse' : 'headman'
      entry.markRevision = `fixture-revision-${userId}-${crypto.randomUUID()}`
      return json({ status: entry.status, lessonId: 30, userId, timestamp: '2026-10-06T15:12:00Z' })
    }
    if (url.pathname.includes('/requests/')) {
      if (url.pathname.includes('/attachments/'))
        return syntheticAttachmentResponse(
          state.startsWith('ticket-preview-pdf')
            ? 'pdf'
            : state === 'ticket-preview-unsupported'
              ? 'unsupported'
              : 'image',
        )
      const id = url.pathname.split('/').at(-1)!
      const studentId = Number(id.split('-').at(-1))
      return json({
        summary: {
          id,
          kind: 'EXCUSE',
          status: 'APPROVED',
          studentId,
          studentName: names[studentId - 1],
          reason: 'Болезнь',
          comment: 'Заявка студента, одобренная старостой',
          coverageStart: '2026-10-06',
          coverageEnd: '2026-10-06',
          hasAttachments: true,
          decisionAt: '2026-10-06T09:00:00Z',
        },
        lessons: [
          {
            lessonId: 30,
            groupId: 4,
            subjectId: 1,
            subjectName: 'Информационные системы',
            lessonNumber: 3,
            date: '2026-10-06',
          },
        ],
        attachments: [
          {
            id: 'ticket-file-1',
            name: state.startsWith('ticket-preview-pdf')
              ? 'Учебный образец.pdf'
              : 'Учебный образец.png',
            contentType: state.startsWith('ticket-preview-pdf') ? 'application/pdf' : 'image/png',
            size: 28000,
            state: 'AVAILABLE',
            expiresAt: state === 'ticket-preview-expired' ? '2026-01-01T00:00:00Z' : null,
          },
        ],
      })
    }
    if (url.pathname.endsWith('/attachment')) return syntheticAttachmentResponse('pdf')
    if (url.pathname.includes('/schedule/groups/'))
      return page('lessonResponseList', state === 'today-empty' ? [] : lessons)
    if (url.pathname === '/api/academic/subjects')
      return page('subjectResponseList', [
        {
          id: 1,
          name:
            state === 'longnames'
              ? 'Информационные системы и многокомпонентные распределённые технологии'
              : 'Информационные системы',
          type: 'LECTURE',
          groupId: 4,
          lessonTypes: ['LECTURE'],
          teacherIds: [],
          assignments: [],
        },
        {
          id: 2,
          name: 'Базы данных',
          type: 'PRACTICE',
          groupId: 4,
          lessonTypes: ['PRACTICE'],
          teacherIds: [],
          assignments: [],
        },
      ])
    if (url.pathname === '/api/academic/homeworks')
      return page('homeworkResponseList', [
        {
          id: 1,
          title: 'Пределы функций',
          description:
            'Повтори определение предела функции и непрерывности. Реши задачи № 16–20 из методички. Для № 19 приложи краткое обоснование выбранного способа.',
          link: 'https://example.org/homework',
          subjectId: 1,
          groupId: 4,
          semesterId: 1,
          publishedBy: 10,
          lessonDate: '2026-10-06',
          lessonNumber: 3,
          bindingMode: 'LESSON',
          revision: 1,
          archived: false,
        },
      ])
    if (url.pathname == '/api/attendance/reports/headman-weekly/weeks')
      return json({
        semesterId: 1,
        semesterName: 'Осенний семестр',
        semesterDateFrom: '2026-09-01',
        semesterDateTo: '2026-12-31',
        weeks: Array.from({ length: 6 }, (_, index) => {
          const start = new Date('2026-08-31T12:00:00Z')
          start.setUTCDate(start.getUTCDate() + index * 7)
          const end = new Date(start)
          end.setUTCDate(end.getUTCDate() + 6)
          return {
            weekOfSemester: index + 1,
            isoWeek: 36 + index,
            label: `${index + 1} неделя`,
            weekStart: start.toISOString().slice(0, 10),
            weekEnd: end.toISOString().slice(0, 10),
            current: index === 5,
          }
        }),
        formats: [
          {
            code: 'xlsx',
            label: 'Excel',
            contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            extension: 'xlsx',
          },
        ],
      })
    return json({ detail: `Synthetic API route not defined: ${url.pathname}` }, 404)
  }
  return {
    schedule: new HeadmanScheduleApi({ accessToken: () => null, fetcher }),
    requests: new HeadmanRequestsApi({ accessToken: () => null, fetcher }),
    actions: new HeadmanLessonActionsApi({ accessToken: () => null, fetcher }),
    journal: new HeadmanJournalApi({ accessToken: () => null, fetcher }),
    subjects: new HeadmanSubjectsApi({ accessToken: () => null, fetcher }),
    homework: new HeadmanHomeworkApi({ accessToken: () => null, fetcher }),
  }
}

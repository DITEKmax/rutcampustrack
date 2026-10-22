import {
  HeadmanRequestsApi,
  type HeadmanRequestSummary,
  type HeadmanRequestLesson,
  type HeadmanRequestDetail,
} from '../features/headman-requests/headman-requests-client'
import { syntheticAttachmentResponse } from './attachment-preview-fixture'
export const requestReviewStates = [
  'queue',
  'detail',
  'approve',
  'reject',
  'archive',
  'late',
  'readonly',
  'empty',
  'error',
  'offline',
  'unknown',
  'transferred',
  'paging',
  'forbidden',
  'exclude',
  'excluded',
  'partial-approve',
  'stale-selection',
  'trash-one-step',
  'restorable',
  'restored',
  'final-cards',
] as const
export function createRequestsReviewApi(
  state: string,
  log: (line: string) => void,
): HeadmanRequestsApi {
  function summary(
    id: string,
    kind: 'EXCUSE' | 'LATE_CHECKIN',
    name: string,
  ): HeadmanRequestSummary {
    return {
      id,
      kind,
      status: 'PENDING',
      studentId: 8,
      studentName: name,
      reason: kind === 'EXCUSE' ? 'Болезнь' : 'Не успел отметиться',
      comment:
        kind === 'EXCUSE'
          ? 'Болел со 2 сентября. Прикрепляю справку от врача.'
          : 'Был на паре, но не успел отметиться. Прошу подтвердить присутствие.',
      coverageStart: '2026-09-02',
      coverageEnd: '2026-09-04',
      lessonCount: 3,
      alreadyMarkedCount: 1,
      hasAttachments: kind === 'EXCUSE',
      createdAt: '2026-09-04T12:00:00Z',
      updatedAt: '2026-09-04T12:00:00Z',
      decisionBy: null,
      decisionAt: null,
      decisionComment: null,
    }
  }
  const requests = [
    summary('excuse-maria', 'EXCUSE', 'Мария Васильева'),
    summary('late-ilya', 'LATE_CHECKIN', 'Илья Соколов'),
    summary('excuse-anna', 'EXCUSE', 'Анна Смирнова'),
    summary('late-maxim', 'LATE_CHECKIN', 'Максим Орлов'),
  ]
  const lessons: HeadmanRequestLesson[] = [
    {
      occurrenceId: '201',
      occurrenceRevision: '1',
      lessonId: 101,
      groupId: 4,
      subjectId: 3,
      subjectName: 'Информационные системы',
      subjectType: 'LECTURE',
      semesterId: 1,
      lessonNumber: 5,
      date: '2026-09-02',
      startsAt: '18:00:00',
      endsAt: '19:30:00',
      requestLessonStatus: 'ABSENT',
      attendanceStatus: 'ABSENT',
      attendanceSource: 'HEADMAN',
    },
    {
      occurrenceId: '202',
      occurrenceRevision: '1',
      lessonId: 102,
      groupId: 4,
      subjectId: 4,
      subjectName: 'Высшая математика',
      subjectType: 'PRACTICE',
      semesterId: 1,
      lessonNumber: 4,
      date: '2026-09-03',
      startsAt: '16:20:00',
      endsAt: '17:50:00',
      requestLessonStatus: 'ABSENT',
      attendanceStatus: 'PRESENT',
      attendanceSource: 'QR',
    },
    {
      occurrenceId: '203',
      occurrenceRevision: '1',
      lessonId: 103,
      groupId: 4,
      subjectId: 5,
      subjectName: 'Физика',
      subjectType: 'LECTURE',
      semesterId: 1,
      lessonNumber: 3,
      date: '2026-09-04',
      startsAt: '14:40:00',
      endsAt: '16:10:00',
      requestLessonStatus: 'ABSENT',
      attendanceStatus: null,
      attendanceSource: null,
    },
  ]
  const details = new Map<string, HeadmanRequestDetail>(
    requests.map((item, index) => {
      const affected =
        item.kind === 'LATE_CHECKIN'
          ? [lessons[index === 1 ? 0 : 1]!]
          : index === 0
            ? lessons
            : [lessons[1]!]
      return [
        item.id,
        {
          decisionRevision: item.kind === 'EXCUSE' ? 'a'.repeat(64) : null,
          summary: item,
          lessons: affected,
          currentAffectedLessons:
            state === 'transferred'
              ? affected.map((lesson) => ({
                  ...lesson,
                  lessonId: (lesson.lessonId ?? 0) + 1000,
                  date: '2026-09-10',
                  startsAt: '18:00:00',
                  endsAt: '19:30:00',
                  attendanceStatus: 'ABSENT',
                }))
              : affected,
          attachments:
            item.kind === 'EXCUSE'
              ? [
                  {
                    id: 'medical-note',
                    name: 'Справка от врача.png',
                    contentType: 'image/png',
                    size: 18624,
                    sha256: null,
                    state: 'AVAILABLE',
                    uploadedAt: '2026-09-04T12:00:00Z',
                    expiresAt: '2099-12-31T23:59:59Z',
                    downloadUrl: null,
                  },
                ]
              : [],
        },
      ]
    }),
  )
  let detailReads = 0
  const json = (value: unknown, status = 200) =>
    new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
  return new HeadmanRequestsApi({
    accessToken: () => 'synthetic-requests-review',
    fetcher: async (input, init) => {
      const url = new URL(String(input), window.location.origin),
        method = init?.method ?? 'GET'
      log(`${method} ${url.pathname}${url.search}${init?.body ? ` ${String(init.body)}` : ''}`)
      if (state === 'forbidden') return json({ detail: 'Нет доступа к заявкам этой группы.' }, 403)
      if (state === 'error') return json({ detail: 'Сервис заявок временно недоступен.' }, 503)
      const match = url.pathname.match(/\/requests\/([^/]+)(?:\/(.*))?$/)
      if (match) {
        const detail = details.get(match[1]!)
        if (!detail) return json({ detail: 'Заявка не найдена.' }, 404)
        if (match[2]?.startsWith('attachments/')) return syntheticAttachmentResponse()
        if (method === 'POST') {
          const body = JSON.parse(String(init?.body)) as {
            decision: 'APPROVED' | 'REJECTED'
            reason?: string
            requestKey?: string
            expectedRevision?: string
            excludedOccurrenceIds?: string[]
          }
          if (state === 'unknown')
            throw new TypeError('Synthetic response lost after request was sent')
          const targets = detail.currentAffectedLessons ?? []
          const decisionSelection =
            body.requestKey && body.expectedRevision
              ? {
                  requestKey: body.requestKey,
                  expectedRevision: body.expectedRevision,
                  includedLessons: targets.filter(
                    (lesson) => !body.excludedOccurrenceIds?.includes(lesson.occurrenceId ?? ''),
                  ),
                  excludedLessons: targets.filter((lesson) =>
                    body.excludedOccurrenceIds?.includes(lesson.occurrenceId ?? ''),
                  ),
                }
              : null
          const decided = {
            ...detail,
            decisionRevision: null,
            decisionSelection,
            summary: {
              ...detail.summary,
              status: body.decision,
              decisionComment: body.reason ?? null,
              decisionBy: 1,
              decisionAt: '2026-10-06T12:00:00Z',
            },
          }
          details.set(detail.summary.id, decided)
          return json(decided)
        }
        if (
          state === 'stale-selection' &&
          detail.summary.id === 'excuse-maria' &&
          ++detailReads > 2
        )
          return json({
            ...detail,
            decisionRevision: 'b'.repeat(64),
            currentAffectedLessons: detail.currentAffectedLessons?.map((lesson) => ({
              ...lesson,
              occurrenceRevision: '2',
            })),
          })
        return json(detail)
      }
      let content = [...details.values()].map((detail) => detail.summary)
      if (url.searchParams.get('bucket') === 'ARCHIVE')
        content = content.slice(0, 3).map((item, index) => ({
          ...item,
          status: index === 1 ? 'REJECTED' : 'APPROVED',
          decisionComment: index === 1 ? 'Недостаточно подтверждений' : null,
        }))
      if (state === 'empty') content = []
      const type = url.searchParams.get('type')
      if (type) content = content.filter((item) => item.kind === type)
      const name = url.searchParams.get('studentName')
      if (name)
        content = content.filter((item) =>
          item.studentName?.toLocaleLowerCase().includes(name.toLocaleLowerCase()),
        )
      const count = content.length,
        page = Number(url.searchParams.get('page') ?? 0),
        size = Number(url.searchParams.get('size') ?? 20)
      if (state === 'paging' && size > 1)
        return json({
          content: content.slice(page * 2, page * 2 + 2),
          page,
          size: 2,
          totalElements: count,
          totalPages: 2,
        })
      return json({
        content: content.slice(page * size, page * size + size),
        page,
        size,
        totalElements: count,
        totalPages: Math.ceil(count / size),
      })
    },
  })
}

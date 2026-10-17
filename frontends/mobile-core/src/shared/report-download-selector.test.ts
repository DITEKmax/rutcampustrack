import { describe, expect, it } from 'vitest'
import {
  ReportDownloadClient,
  validateTicketRequest,
  type ReportDownloadTicketRequest,
} from './report-download-client'
import { TeacherApi, toTeacherJournalReportRequest } from '../features/teacher/teacher-client'

describe('teacher real day contract', () => {
  it('keeps a one-off without assignment and the real cancellation reason', async () => {
    const lesson = {
      id: '403',
      assignmentId: null,
      groupId: '101',
      groupName: 'ИВТ-241',
      subjectId: '201',
      subjectName: 'Программирование',
      semesterId: '7',
      lessonType: 'lecture',
      date: '2026-10-08',
      lessonNumber: 3,
      startsAt: '12:30:00',
      endsAt: '14:00:00',
      room: null,
      status: 'CANCELLED',
      oneOff: true,
      moved: false,
      cancelled: true,
      cancellationReason: 'Преподаватель на конференции',
    }
    const api = new TeacherApi({
      accessToken: () => null,
      fetcher: async () =>
        Response.json({
          semesterId: '7',
          date: '2026-10-08',
          lessons: [lesson, { ...lesson, id: '404', cancelled: false, status: 'PLANNED' }],
          serverNow: '2026-10-08T08:10:00Z',
        }),
    })
    const day = await api.day(7, '2026-10-08')
    expect(day.lessons[0]).toMatchObject({
      assignmentId: null,
      oneOff: true,
      cancellationReason: 'Преподаватель на конференции',
    })
    expect(day.lessons[1]?.cancellationReason).toBeNull()
  })
  it('keeps the taught list separate from historical subjects', async () => {
    const option = {
      groupId: '101',
      subjectId: '201',
      subjectName: 'Программирование',
      lessonTypes: ['lecture'],
    }
    const api = new TeacherApi({
      accessToken: () => null,
      fetcher: async () =>
        Response.json({
          groups: [{ id: '101', name: 'ИВТ-241' }],
          subjectOptions: [{ ...option, subjectId: '204' }],
          taughtSubjectOptions: [option],
          serverNow: '2026-10-08T08:10:00Z',
        }),
    })
    const catalog = await api.catalog(7)
    expect(catalog.subjectOptions[0]?.subjectId).toBe(204)
    expect(catalog.taughtSubjectOptions[0]?.subjectId).toBe(201)
  })
})

describe('teacher exact export selector', () => {
  it('preserves exact lesson and completed-only selectors in a host ticket', async () => {
    const request = toTeacherJournalReportRequest(
      {
        semesterId: 7,
        groupId: 101,
        subjectId: 201,
        lessonTypes: ['lecture'],
        pastOnly: false,
        lessonId: '402',
        dateFrom: '2026-10-08',
        dateTo: '2026-10-08',
      },
      'pdf',
    )
    let sent: unknown
    const client = new ReportDownloadClient({
      accessToken: () => null,
      assertCurrent: () => undefined,
      fetcher: async (_input, init) => {
        if (!String(_input).endsWith('/ticket')) {
          sent = (JSON.parse(String(init?.body)) as { report: unknown }).report
          return Response.json({
            jobId: '00000000-0000-4000-8000-000000000001',
            state: 'ready',
            expiresAt: new Date(Date.now() + 60000).toISOString(),
            suggestedFilename: 'journal.pdf',
            errorCode: null,
          })
        }
        return Response.json({
          downloadPath: `/api/report-download/${'a'.repeat(43)}`,
          expiresAt: new Date(Date.now() + 60000).toISOString(),
          suggestedFilename: 'journal.pdf',
        })
      },
    })
    await client.issueTicket(request)
    expect(sent).toEqual({
      kind: 'TEACHER_JOURNAL',
      teacherJournal: {
        semesterId: 7,
        groupId: 101,
        subjectId: 201,
        lessonTypes: ['lecture'],
        pastOnly: false,
        lessonId: '402',
        dateFrom: '2026-10-08',
        dateTo: '2026-10-08',
        format: 'pdf',
      },
    })
    expect(
      toTeacherJournalReportRequest(
        { semesterId: 7, groupId: 101, subjectId: 201, lessonTypes: ['lecture'], pastOnly: true },
        'xlsx',
      ),
    ).toMatchObject({ teacherJournal: { pastOnly: true } })
  })
  it.each(['0', '-1', '1.5', ' 402', 402, ''])('rejects malformed exact lesson %s', (lessonId) => {
    expect(() =>
      validateTicketRequest({
        kind: 'TEACHER_JOURNAL',
        teacherJournal: {
          semesterId: 7,
          groupId: 101,
          subjectId: 201,
          lessonTypes: ['lecture'],
          format: 'pdf',
          lessonId,
        },
      } as ReportDownloadTicketRequest),
    ).toThrow(RangeError)
  })
  it('rejects a nonboolean completed-only selector rather than dropping it', () => {
    expect(() =>
      validateTicketRequest({
        kind: 'TEACHER_JOURNAL',
        teacherJournal: {
          semesterId: 7,
          groupId: 101,
          subjectId: 201,
          lessonTypes: ['lecture'],
          format: 'pdf',
          pastOnly: 'true',
        },
      } as unknown as ReportDownloadTicketRequest),
    ).toThrow(RangeError)
  })
})

describe('headman selected semester export boundary', () => {
  it.each(['HEADMAN_STATS', 'HEADMAN_STATS_TREND'] as const)(
    'sends the exact selected semester for %s',
    async (kind) => {
      const request: ReportDownloadTicketRequest =
        kind === 'HEADMAN_STATS'
          ? { kind, headmanStats: { semesterId: 12, format: 'xlsx' } }
          : {
              kind,
              headmanStatsTrend: {
                semesterId: 12,
                bucketUnit: 'WEEK',
                mode: 'SEMESTER',
                format: 'png',
              },
            }
      let sent: unknown
      const client = new ReportDownloadClient({
        accessToken: () => null,
        assertCurrent: () => undefined,
        fetcher: async (_input, init) => {
          if (!String(_input).endsWith('/ticket')) {
            sent = (JSON.parse(String(init?.body)) as { report: unknown }).report
            return Response.json({
              jobId: '00000000-0000-4000-8000-000000000001',
              state: 'ready',
              expiresAt: new Date(Date.now() + 60000).toISOString(),
              suggestedFilename:
                kind === 'HEADMAN_STATS' ? 'headman-stats.xlsx' : 'headman-stats-trend.png',
              errorCode: null,
            })
          }
          return new Response(
            JSON.stringify({
              downloadPath: `/api/report-download/${'a'.repeat(43)}`,
              expiresAt: new Date(Date.now() + 60000).toISOString(),
              suggestedFilename:
                kind === 'HEADMAN_STATS' ? 'headman-stats.xlsx' : 'headman-stats-trend.png',
            }),
            { headers: { 'Content-Type': 'application/json' } },
          )
        },
      })
      await client.issueTicket(request)
      expect(sent).toEqual(request)
    },
  )
  it.each([0, -1, 1.5, '12', null])(
    'rejects invalid selected semester %s rather than dropping it',
    (semesterId) => {
      for (const request of [
        { kind: 'HEADMAN_STATS', headmanStats: { semesterId, format: 'xlsx' } },
        {
          kind: 'HEADMAN_STATS_TREND',
          headmanStatsTrend: { semesterId, mode: 'SEMESTER', format: 'png' },
        },
      ])
        expect(() => validateTicketRequest(request as ReportDownloadTicketRequest)).toThrow(
          RangeError,
        )
    },
  )
  it('accepts DAY and WEEK, rejects an unsupported bucket and preserves legacy optional defaults', () => {
    for (const bucketUnit of ['DAY', 'WEEK', undefined]) {
      expect(() =>
        validateTicketRequest({
          kind: 'HEADMAN_STATS_TREND',
          headmanStatsTrend: {
            mode: 'SEMESTER',
            format: 'png',
            ...(bucketUnit ? { bucketUnit } : {}),
          },
        } as ReportDownloadTicketRequest),
      ).not.toThrow()
    }
    expect(() =>
      validateTicketRequest({
        kind: 'HEADMAN_STATS_TREND',
        headmanStatsTrend: { mode: 'SEMESTER', format: 'png', bucketUnit: 'MONTH' },
      } as unknown as ReportDownloadTicketRequest),
    ).toThrow(RangeError)
    expect(() =>
      validateTicketRequest({ kind: 'HEADMAN_STATS', headmanStats: { format: 'pdf' } }),
    ).not.toThrow()
  })
})

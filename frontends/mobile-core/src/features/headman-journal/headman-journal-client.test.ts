import { describe, expect, it } from 'vitest'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { moscowDate } from '../../domain/homework'
import {
  HeadmanJournalApi,
  createGenerationBoundHeadmanJournalApi,
} from './headman-journal-client'

function jsonResponse(value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('HeadmanJournalApi', () => {
  it('uses the shared Moscow calendar date at the UTC day boundary', () => {
    expect(moscowDate('2026-09-20T21:30:00.000Z')).toBe('2026-09-21')
    expect(moscowDate('2026-09-21T20:59:59.999Z')).toBe('2026-09-21')
  })

  it('reads paged concrete lessons and preserves server lesson fields', async () => {
    const paths: string[] = []
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async (input) => {
        paths.push(String(input))
        return jsonResponse({
          _embedded: {
            lessonResponseList: [{
              id: 42,
              groupId: 7,
              subjectId: 9,
              date: '2026-09-21',
              status: 'CANCELLED',
              lessonNumber: 2,
              startTime: '10:00:00',
              endTime: '11:30:00',
              room: 'А-101',
            }],
          },
          page: { totalPages: 1 },
        })
      },
    })

    await expect(api.listLessons(7, '2026-09-21', '2026-09-21')).resolves.toEqual([{
      id: 42,
      groupId: 7,
      subjectId: 9,
      date: '2026-09-21',
      status: 'CANCELLED',
      lessonNumber: 2,
      startTime: '10:00:00',
      endTime: '11:30:00',
      room: 'А-101',
      lessonType: null,
    }])
    expect(paths).toEqual([
      '/api/schedule/groups/7/lessons?dateFrom=2026-09-21&dateTo=2026-09-21&page=0&size=100'
        + '&status=PLANNED&status=ACTIVE&status=CLOSED&status=CANCELLED',
    ])
  })

  it('keeps unmarked and cancelled states distinct and refuses legacy FREE_ATTENDANCE normalization', async () => {
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async () => jsonResponse({
        lessonId: 42,
        groupId: 7,
        subjectId: 9,
        lessonDate: '2026-09-21',
        entries: [
          { userId: 3, displayName: 'Яна', status: null, symbol: null, editable: true },
          { userId: 2, displayName: 'Борис', status: 'cancelled', symbol: null, editable: false },
          { userId: 1, displayName: 'Алиса', status: 'FREE_ATTENDANCE', symbol: 'у', editable: true },
        ],
      }),
    })

    const report = await api.getLessonAttendance(42)
    expect(report.entries.map((entry) => [entry.displayName, entry.status])).toEqual([
      ['Алиса', 'UNSUPPORTED'],
      ['Борис', 'CANCELLED'],
      ['Яна', 'EMPTY'],
    ])
  })

  it('rejects a late response after the session generation changes', async () => {
    let generation = 0
    let release: ((response: Response) => void) | undefined
    const api = createGenerationBoundHeadmanJournalApi({
      currentGeneration: () => generation,
      accessTokenFor: () => 'token',
      refreshFor: async () => undefined,
    }, async () => new Promise<Response>((resolve) => { release = resolve }))

    const pending = api.getLessonAttendance(42)
    generation = 1
    release?.(jsonResponse({ lessonId: 42, entries: [] }))

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })

  it('uses the frozen JSON, multipart, and DELETE wire without local success', async () => {
    const requests: Array<{ path: string; init: RequestInit }> = []
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async (input, init = {}) => {
        requests.push({ path: String(input), init })
        if (init.method === 'DELETE') return new Response(null, { status: 204 })
        return jsonResponse({
          content: { status: 'PRESENT', lessonId: 42, userId: 3, timestamp: '2026-09-21T10:00:00Z' },
        })
      },
    })

    await expect(api.mark(42, 3, { status: 'PRESENT' })).resolves.toMatchObject({
      status: 'PRESENT',
      lessonId: 42,
      userId: 3,
    })
    const jsonRequest = requests[0]!
    expect(jsonRequest.path).toBe('/api/attendance/lessons/42/students/3')
    expect(jsonRequest.init.method).toBe('PUT')
    expect(new Headers(jsonRequest.init.headers).get('Content-Type')).toBe('application/json')
    expect(JSON.parse(String(jsonRequest.init.body))).toEqual({ status: 'PRESENT' })

    const file = new File(['proof'], 'proof.pdf', { type: 'application/pdf' })
    await expect(api.mark(42, 3, {
      status: 'EXCUSED',
      excuseType: 'ILLNESS',
      comment: 'Комментарий',
      file,
    })).resolves.toMatchObject({ status: 'PRESENT' })
    const multipartRequest = requests[1]!
    expect(multipartRequest.init.body).toBeInstanceOf(FormData)
    const form = multipartRequest.init.body as FormData
    const requestPart = form.get('request')
    expect(requestPart).toBeInstanceOf(Blob)
    await expect((requestPart as Blob).text()).resolves.toBe(JSON.stringify({
      status: 'EXCUSED',
      excuseType: 'ILLNESS',
      comment: 'Комментарий',
    }))
    expect(form.get('file')).toBeInstanceOf(File)

    await expect(api.clear(42, 3)).resolves.toBeUndefined()
    expect(requests[2]?.init.method).toBe('DELETE')
    expect(() => api.mark(42, 3, {
      status: 'EXCUSED',
      excuseType: 'FREE_ATTENDANCE' as never,
    })).toThrow('excuseType')
  })

  it('downloads an authorized student attachment through the frozen lesson path', async () => {
    const paths: string[] = []
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async (input) => {
        paths.push(String(input))
        return new Response('file-body', { status: 200, headers: { 'Content-Type': 'application/pdf' } })
      },
    })

    const blob = await api.downloadAttachment(42, 3)
    expect(paths).toEqual(['/api/attendance/lessons/42/students/3/attachment'])
    await expect(blob.text()).resolves.toBe('file-body')
  })

  it('reads the server weekly and format catalogue without inventing format options', async () => {
    const paths: string[] = []
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async (input) => {
        paths.push(String(input))
        return jsonResponse({
          content: {
            semesterId: 4,
            semesterName: 'Осень 2026',
            semesterDateFrom: '2026-09-01',
            semesterDateTo: '2027-01-31',
            weeks: [{
              weekOfSemester: 1,
              isoWeek: 36,
              label: 'Н1',
              weekStart: '2026-08-31',
              weekEnd: '2026-09-06',
              current: true,
            }],
            formats: [
              { code: 'docx', label: 'Word (.docx)', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', extension: 'docx' },
              { code: 'pdf', label: 'PDF (.pdf)', contentType: 'application/pdf', extension: 'pdf' },
              { code: 'png', label: 'PNG, архив страниц (.zip)', contentType: 'application/zip', extension: 'zip' },
              { code: 'html', label: 'HTML (.html)', contentType: 'text/html; charset=UTF-8', extension: 'html' },
              { code: 'xlsx', label: 'Excel (.xlsx)', contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', extension: 'xlsx' },
            ],
          },
        })
      },
    })

    const options = await api.getWeeklyExportOptions()

    expect(paths).toEqual(['/api/attendance/reports/headman-weekly/weeks'])
    expect(options.weeks[0]).toMatchObject({ weekStart: '2026-08-31', label: 'Н1', current: true })
    expect(options.formats.map((format) => [format.code, format.extension])).toEqual([
      ['docx', 'docx'], ['pdf', 'pdf'], ['png', 'zip'], ['html', 'html'], ['xlsx', 'xlsx'],
    ])
  })

  it('posts only selected server week starts and preserves binary filename and MIME', async () => {
    const requests: Array<{ path: string; init: RequestInit }> = []
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async (input, init = {}) => {
        requests.push({ path: String(input), init })
        return new Response('png-zip', {
          status: 200,
          headers: {
            'Content-Type': 'application/zip',
            'Content-Disposition': "attachment; filename*=UTF-8''UVPV511_27.04.2026_png.zip",
          },
        })
      },
    })

    const downloaded = await api.downloadWeeklyExport(['2026-04-27'], {
      code: 'png', label: 'PNG, архив страниц (.zip)', contentType: 'application/zip', extension: 'zip',
    })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.path).toBe('/api/attendance/reports/headman-weekly/export')
    expect(requests[0]?.init.method).toBe('POST')
    expect(new Headers(requests[0]?.init.headers).get('Accept')).toBe('*/*')
    expect(JSON.parse(String(requests[0]?.init.body))).toEqual({ weekStarts: ['2026-04-27'], format: 'png' })
    expect(downloaded.filename).toBe('UVPV511_27.04.2026_png.zip')
    expect(downloaded.blob.type).toBe('application/zip')
    await expect(downloaded.blob.text()).resolves.toBe('png-zip')
  })
})

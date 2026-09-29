import { describe, expect, it } from 'vitest'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { moscowDate } from '../../domain/homework'
import {
  HeadmanJournalApi,
  HeadmanJournalApiError,
  createHeadmanLessonTransferRequest,
  createGenerationBoundHeadmanJournalApi,
} from './headman-journal-client'

function jsonResponse(value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function jsonResponseWithStatus(value: unknown, status: number): Response {
  return new Response(JSON.stringify(value), {
    status,
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
              current: true,
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
      occurrenceRevision: null,
      current: true,
      transferOperationId: null,
      transferState: null,
    }])
    expect(paths).toEqual([
      '/api/schedule/groups/7/lessons?dateFrom=2026-09-21&dateTo=2026-09-21&page=0&size=100'
        + '&status=PLANNED&status=ACTIVE&status=CLOSED&status=CANCELLED',
    ])
  })

  it('sends the frozen transfer payload and follows the scoped operation status', async () => {
    const requests: Array<{ path: string; init: RequestInit }> = []
    const pending = {
      operationId: '8f065bb4-06b4-4b55-a6a2-326910090acf',
      state: 'PENDING',
      occurrenceId: '73',
      sourceLessonId: '42',
      targetLessonId: '91',
      targetDate: '2026-10-02',
      revision: '18',
      retryable: true,
      errorCode: null,
    }
    const completed = { ...pending, state: 'COMPLETED', revision: '19', retryable: false }
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async (input, init = {}) => {
        requests.push({ path: String(input), init })
        return jsonResponseWithStatus(requests.length === 1 ? pending : completed, 200)
      },
    })
    const request = createHeadmanLessonTransferRequest(
      '2026-10-02',
      3,
      '17',
      '2507aed5-9905-4b02-a91f-bf98799e18b6',
    )

    await expect(api.transferLesson(42, request)).rejects.toThrow('не соответствующее HTTP-ответу')

    const acceptedApi = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async (input, init = {}) => {
        requests.push({ path: String(input), init })
        return requests.length === 2
          ? jsonResponseWithStatus(pending, 202)
          : jsonResponseWithStatus(completed, 200)
      },
    })
    const accepted = await acceptedApi.transferLesson(42, request)
    expect(accepted).toMatchObject({
      operationId: pending.operationId,
      state: 'PENDING',
      targetDate: '2026-10-02',
      retryable: true,
    })
    expect(requests[1]?.path).toBe('/api/schedule/lessons/42/transfer')
    expect(requests[1]?.init.method).toBe('POST')
    expect(JSON.parse(String(requests[1]?.init.body))).toEqual({
      targetDate: '2026-10-02',
      targetLessonNumber: 3,
      targetStartTime: null,
      targetEndTime: null,
      targetRoom: null,
      expectedRevision: '17',
      requestKey: '2507aed5-9905-4b02-a91f-bf98799e18b6',
    })

    await expect(acceptedApi.getLessonTransfer(accepted.operationId)).resolves.toMatchObject({
      operationId: pending.operationId,
      state: 'COMPLETED',
      retryable: false,
    })
    expect(requests[2]?.path).toBe(`/api/schedule/lesson-transfers/${pending.operationId}`)
    expect(requests[2]?.init.method).toBeUndefined()
  })

  it('retains occurrence revision and a resumable transfer operation from the latest lesson list', async () => {
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async () => jsonResponse({
        _embedded: {
          lessonResponseList: [{
            id: 42,
            groupId: 7,
            date: '2026-09-30',
            status: 'PLANNED',
            lessonNumber: 2,
            occurrenceRevision: 17,
            current: true,
            transferOperationId: '8f065bb4-06b4-4b55-a6a2-326910090acf',
            transferState: 'PENDING',
          }],
        },
        page: { totalPages: 1 },
      }),
    })

    await expect(api.listLessons(7, '2026-09-30', '2026-09-30')).resolves.toMatchObject([{
      id: 42,
      occurrenceRevision: '17',
      transferOperationId: '8f065bb4-06b4-4b55-a6a2-326910090acf',
      transferState: 'PENDING',
    }])
  })

  it('preserves the same request key and payload when an uncertain transfer is retried', async () => {
    const bodies: string[] = []
    let attempt = 0
    const response = {
      operationId: '8f065bb4-06b4-4b55-a6a2-326910090acf',
      state: 'PENDING',
      occurrenceId: '73',
      sourceLessonId: '42',
      targetLessonId: '91',
      targetDate: '2026-10-02',
      revision: '18',
      retryable: true,
      errorCode: null,
    }
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async (_input, init = {}) => {
        bodies.push(String(init.body))
        attempt += 1
        if (attempt === 1) throw new TypeError('network response was lost')
        return jsonResponseWithStatus(response, 202)
      },
    })
    const request = createHeadmanLessonTransferRequest(
      '2026-10-02',
      3,
      '17',
      '2507aed5-9905-4b02-a91f-bf98799e18b6',
    )

    await expect(api.transferLesson(42, request)).rejects.toThrow('network response was lost')
    await expect(api.transferLesson(42, request)).resolves.toMatchObject({ state: 'PENDING' })

    expect(Object.isFrozen(request)).toBe(true)
    expect(bodies).toHaveLength(2)
    expect(bodies[0]).toBe(bodies[1])
    expect(JSON.parse(bodies[0] ?? '{}')).toMatchObject({
      expectedRevision: '17',
      requestKey: '2507aed5-9905-4b02-a91f-bf98799e18b6',
    })
  })

  it('refreshes an expired session once and retries the exact transfer payload', async () => {
    const requests: Array<{ authorization: string | null; body: string }> = []
    let token = 'old-token'
    let refreshed = false
    const api = new HeadmanJournalApi({
      accessToken: () => token,
      onUnauthorized: async () => {
        refreshed = true
        token = 'new-token'
      },
      fetcher: async (_input, init = {}) => {
        requests.push({
          authorization: new Headers(init.headers).get('Authorization'),
          body: String(init.body),
        })
        if (requests.length === 1) return new Response(null, { status: 401 })
        return jsonResponseWithStatus({
          operationId: '8f065bb4-06b4-4b55-a6a2-326910090acf',
          state: 'PENDING',
          occurrenceId: '73',
          sourceLessonId: '42',
          targetLessonId: '91',
          targetDate: '2026-10-02',
          revision: '18',
          retryable: true,
          errorCode: null,
        }, 202)
      },
    })
    const request = createHeadmanLessonTransferRequest(
      '2026-10-02',
      3,
      '17',
      '2507aed5-9905-4b02-a91f-bf98799e18b6',
    )

    await expect(api.transferLesson(42, request)).resolves.toMatchObject({ state: 'PENDING' })

    expect(refreshed).toBe(true)
    expect(requests.map((item) => item.authorization)).toEqual(['Bearer old-token', 'Bearer new-token'])
    expect(requests[0]?.body).toBe(requests[1]?.body)
  })

  it('surfaces a transfer conflict body without treating it as accepted', async () => {
    const api = new HeadmanJournalApi({
      accessToken: () => 'token',
      fetcher: async () => jsonResponseWithStatus({
        operationId: '8f065bb4-06b4-4b55-a6a2-326910090acf',
        state: 'ERROR',
        occurrenceId: '73',
        sourceLessonId: '42',
        targetLessonId: '91',
        targetDate: '2026-10-02',
        revision: '17',
        retryable: false,
        errorCode: 'SOURCE_STATE_CONFLICT',
      }, 409),
    })
    const request = createHeadmanLessonTransferRequest(
      '2026-10-02',
      3,
      '17',
      '2507aed5-9905-4b02-a91f-bf98799e18b6',
    )

    await expect(api.transferLesson(42, request)).rejects.toMatchObject({
      response: { status: 409 },
      problem: { errorCode: 'SOURCE_STATE_CONFLICT' },
    } satisfies Partial<HeadmanJournalApiError>)
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

  it('rejects a late transfer response after the session owner changes', async () => {
    let generation = 0
    let release: ((response: Response) => void) | undefined
    const api = createGenerationBoundHeadmanJournalApi({
      currentGeneration: () => generation,
      accessTokenFor: () => 'token',
      refreshFor: async () => undefined,
    }, async () => new Promise<Response>((resolve) => { release = resolve }))

    const pending = api.transferLesson(42, createHeadmanLessonTransferRequest(
      '2026-10-02',
      3,
      '17',
      '2507aed5-9905-4b02-a91f-bf98799e18b6',
    ))
    generation = 1
    release?.(jsonResponseWithStatus({
      operationId: '8f065bb4-06b4-4b55-a6a2-326910090acf',
      state: 'COMPLETED',
      occurrenceId: '73',
      sourceLessonId: '42',
      targetLessonId: '91',
      targetDate: '2026-10-02',
      revision: '18',
      retryable: false,
      errorCode: null,
    }, 200))

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

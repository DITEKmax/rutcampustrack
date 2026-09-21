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
    expect(paths).toEqual(['/api/schedule/groups/7/lessons?dateFrom=2026-09-21&dateTo=2026-09-21&page=0&size=100'])
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
})

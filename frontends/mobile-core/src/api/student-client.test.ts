import { afterEach, describe, expect, it, vi } from 'vitest'
import { decodeStatisticsRanking, StudentApi } from './student-client'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('StudentApi.getSemesterSchedule', () => {
  it('returns null schedule and ETag for 304', async () => {
    const response = new Response(null, {
      status: 304,
      headers: { ETag: '"semester-v1"' },
    })
    const api = new StudentApi({
      accessToken: () => 'token',
      fetcher: async () => response,
    })

    await expect(api.getSemesterSchedule('semester-1', '"semester-v0"')).resolves.toEqual({
      data: null,
      etag: '"semester-v1"',
    })
  })

  it('returns decoded schedule and ETag for 200', async () => {
    const response = new Response(JSON.stringify({ lessons: [] }), {
      status: 200,
      headers: { ETag: '"semester-v2"' },
    })
    const api = new StudentApi({
      accessToken: () => 'token',
      fetcher: async () => response,
    })

    await expect(api.getSemesterSchedule('semester-1')).resolves.toEqual({
      data: { lessons: [] },
      etag: '"semester-v2"',
    })
  })

})

describe('StudentApi Requests transport', () => {
  it('sends the request JSON Blob and repeated files without setting multipart boundary', async () => {
    const captured: Array<{ path: string; init: RequestInit }> = []
    const api = new StudentApi({
      accessToken: () => 'token',
      fetcher: async (input, init) => {
        captured.push({ path: String(input), init: init ?? {} })
        return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } })
      },
    })
    const file = new File(['proof'], 'proof.txt', { type: 'text/plain' })

    await api.submitExcuse(
      { lessonIds: ['lesson-1'], reason: 'OTHER', comment: 'Пояснение' },
      [file],
      'request-key-0001',
    )

    const snapshot = captured[0]
    expect(snapshot).toBeDefined()
    if (!snapshot) throw new Error('transport was not called')
    expect(snapshot.path).toBe('/api/v1/student/requests/excuse')
    const headers = new Headers(snapshot.init.headers)
    expect(headers.get('Authorization')).toBe('Bearer token')
    expect(headers.get('Content-Type')).toBeNull()
    const body = snapshot.init.body
    expect(body).toBeInstanceOf(FormData)
    const form = body as FormData
    expect(form.get('request')).toBeInstanceOf(Blob)
    expect((form.get('request') as Blob).type).toBe('application/json')
    expect(form.getAll('files')).toHaveLength(1)
    expect((form.getAll('files')[0] as File).name).toBe('proof.txt')
  })

  it('keeps binary downloads authenticated and validates visible idempotency keys before fetch', async () => {
    let calls = 0
    let attachmentRequest: { url: string; init: RequestInit } | undefined
    const api = new StudentApi({
      accessToken: () => 'token',
      fetcher: async (input, init) => {
        calls += 1
        attachmentRequest = { url: String(input), init: init ?? {} }
        return new Response('bytes', {
          status: 200,
          headers: { 'Content-Type': 'application/octet-stream' },
        })
      },
    })

    await expect(
      Promise.resolve().then(() => api.submitLateCheckin({ lessonId: 'lesson-1' }, 'short')),
    ).rejects.toThrow('16–128')
    expect(calls).toBe(0)
    await expect(api.downloadRequestAttachment('request-1', 'file-1')).resolves.toBeInstanceOf(Blob)
    expect(attachmentRequest?.url).toBe('/api/v1/student/requests/request-1/attachments/file-1');
    expect(attachmentRequest?.init.method ?? 'GET').toBe('GET')
    expect(attachmentRequest?.init.credentials).toBe('include')
    const attachmentHeaders = new Headers(attachmentRequest?.init.headers)
    expect(attachmentHeaders.get('Authorization')).toBe('Bearer token')
    expect(attachmentHeaders.get('Accept')).toBe('application/octet-stream')
  })
})

describe('StudentApi current own-group ranking boundary', () => {
  const page = {
    available: true,
    page: 1,
    size: 20,
    total: 42,
    ownPosition: 22,
    rows: [{ id: '1', name: '<Своё имя>', position: 22, percentage: 0, isSelf: true }],
  }
  it('requests the own anchored page by omission and preserves true zero/null without coercion', async () => {
    const requests: Array<{ url: URL; signal: AbortSignal | null | undefined }> = []
    const api = new StudentApi({
      accessToken: () => 'synthetic',
      fetcher: async (input, init) => {
        requests.push({ url: new URL(String(input), 'https://fixture'), signal: init?.signal })
        return Response.json(page)
      },
    })
    const controller = new AbortController()
    expect(await api.getStatisticsRanking('7', null, 20, controller.signal)).toEqual(page)
    expect(requests[0]?.url.pathname).toBe('/api/v1/student/statistics/ranking')
    expect(requests[0]?.url.searchParams.has('page')).toBe(false)
    expect(requests[0]?.signal).toBe(controller.signal)
    await api.getStatisticsRanking('7', 0, 100)
    expect(requests[1]?.url.searchParams.get('page')).toBe('0')
    expect(requests[1]?.url.searchParams.get('size')).toBe('100')
    expect(
      decodeStatisticsRanking({
        ...page,
        rows: [{ ...page.rows[0], percentage: null, position: null }],
      }).rows[0]?.percentage,
    ).toBeNull()
  })
  it.each([
    { ...page, page: -1 },
    { ...page, size: 101 },
    { ...page, total: '42' },
    { ...page, rows: [{ ...page.rows[0], percentage: '0' }] },
    { ...page, rows: [{ ...page.rows[0], percentage: Number.NaN }] },
    { ...page, rows: [{ ...page.rows[0], isSelf: 'true' }] },
    { ...page, rows: [page.rows[0], page.rows[0]] },
  ])('rejects malformed ranking instead of constructing a usable peer list', (value) => {
    expect(() => decodeStatisticsRanking(value)).toThrow(TypeError)
  })
  it('rejects invalid page/size before a request', async () => {
    const fetcher = vi.fn()
    const api = new StudentApi({ accessToken: () => null, fetcher })
    await expect(api.getStatisticsRanking('7', -1)).rejects.toThrow(RangeError)
    await expect(api.getStatisticsRanking('7', 0, 0)).rejects.toThrow(RangeError)
    expect(fetcher).not.toHaveBeenCalled()
  })
})

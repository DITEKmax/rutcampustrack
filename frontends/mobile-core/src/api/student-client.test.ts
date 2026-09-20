import { afterEach, describe, expect, it, vi } from 'vitest'
import { StudentApi } from './student-client'

afterEach(() => { vi.restoreAllMocks() })

describe('StudentApi.getSemesterSchedule', () => {
  it('consumes a 304 body before returning the null schedule and ETag', async () => {
    const response = new Response(null, {
      status: 304,
      headers: { ETag: '"semester-v1"' },
    })
    const arrayBuffer = vi.spyOn(response, 'arrayBuffer')
    const json = vi.spyOn(response, 'json')
    const api = new StudentApi({
      accessToken: () => 'token',
      fetcher: async () => response,
    })

    await expect(api.getSemesterSchedule('semester-1', '"semester-v0"')).resolves.toEqual({
      data: null,
      etag: '"semester-v1"',
    })
    expect(arrayBuffer).toHaveBeenCalledOnce()
    expect(json).not.toHaveBeenCalled()
  })

  it('JSON-parses a 200 schedule exactly once', async () => {
    const response = new Response(JSON.stringify({ lessons: [] }), {
      status: 200,
      headers: { ETag: '"semester-v2"' },
    })
    const json = vi.spyOn(response, 'json')
    const api = new StudentApi({
      accessToken: () => 'token',
      fetcher: async () => response,
    })

    await expect(api.getSemesterSchedule('semester-1')).resolves.toEqual({
      data: { lessons: [] },
      etag: '"semester-v2"',
    })
    expect(json).toHaveBeenCalledOnce()
  })

  it('propagates a 304 body-read rejection', async () => {
    const response = new Response(null, { status: 304 })
    const bodyReadError = new Error('304 body read failed')
    vi.spyOn(response, 'arrayBuffer').mockRejectedValue(bodyReadError)
    const api = new StudentApi({
      accessToken: () => 'token',
      fetcher: async () => response,
    })

    await expect(api.getSemesterSchedule('semester-1')).rejects.toBe(bodyReadError)
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

    await api.submitExcuse({ lessonIds: ['lesson-1'], reason: 'OTHER', comment: 'Пояснение' }, [file], 'request-key-0001')

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
    let accept: string | null = null
    const api = new StudentApi({
      accessToken: () => 'token',
      fetcher: async (_input, init) => {
        calls += 1
        accept = new Headers(init?.headers).get('Accept')
        return new Response('bytes', { status: 200, headers: { 'Content-Type': 'application/octet-stream' } })
      },
    })

    await expect(Promise.resolve().then(() => api.submitLateCheckin({ lessonId: 'lesson-1' }, 'short'))).rejects.toThrow('16–128')
    expect(calls).toBe(0)
    await expect(api.downloadRequestAttachment('request-1', 'file-1')).resolves.toBeInstanceOf(Blob)
    expect(accept).toBe('application/octet-stream')
  })
})

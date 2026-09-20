import { describe, expect, it, vi } from 'vitest'
import { StudentApi } from './student-client'

const detail = JSON.stringify({
  summary: {
    id: '507f1f77bcf86cd799439011',
    kind: 'LATE_CHECKIN',
    status: 'PENDING',
    origin: 'MANUAL',
    lessons: [],
    createdAt: '2026-09-07T10:00:00Z',
    updatedAt: '2026-09-07T10:00:00Z',
  },
  reason: null,
  comment: null,
  decision: null,
  attachments: [],
})

describe('StudentApi Requests transport', () => {
  it('keeps JSON requests typed and sends the idempotency key through the shared transport', async () => {
    const fetcher = vi.fn<typeof fetch>(async (_input, init) => {
      expect(init?.headers).toBeInstanceOf(Headers)
      const headers = new Headers(init?.headers)
      expect(headers.get('Accept')).toBe('application/json')
      expect(headers.get('Content-Type')).toBe('application/json')
      expect(headers.get('Idempotency-Key')).toBe('late-key-00000001')
      expect(headers.get('Authorization')).toBe('Bearer jwt-a')
      expect(headers.get('X-Internal-Token')).toBe('jwt-a')
      expect(init?.body).toBe(JSON.stringify({ lessonId: '77' }))
      return new Response(detail, {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })

    const api = new StudentApi({ accessToken: () => 'jwt-a', fetcher })
    await expect(api.submitLateCheckin({ lessonId: '77' }, 'late-key-00000001'))
      .resolves.toMatchObject({ summary: { id: '507f1f77bcf86cd799439011' } })
    expect(fetcher).toHaveBeenCalledOnce()
  })

  it('lets the browser set multipart boundaries and preserves repeated files', async () => {
    const fetcher = vi.fn<typeof fetch>(async (_input, init) => {
      const headers = new Headers(init?.headers)
      expect(headers.get('Accept')).toBe('application/json')
      expect(headers.has('Content-Type')).toBe(false)
      expect(headers.get('Idempotency-Key')).toBe('excuse-key-00000001')
      expect(init?.body).toBeInstanceOf(FormData)

      const form = init?.body as FormData
      expect(form.get('request')).toBeInstanceOf(Blob)
      expect(await (form.get('request') as Blob).text()).toBe(JSON.stringify({
        lessonIds: ['77'],
        reason: 'ILLNESS',
        comment: 'Температура',
      }))
      expect(form.getAll('files')).toHaveLength(2)
      expect((form.getAll('files')[0] as File).name).toBe('medical-note.pdf')
      expect((form.getAll('files')[1] as File).name).toBe('doctor-note.png')
      return new Response(detail, {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })

    const api = new StudentApi({ accessToken: () => 'jwt-a', fetcher })
    const files = [
      new File(['pdf'], 'medical-note.pdf', { type: 'application/pdf' }),
      new File(['png'], 'doctor-note.png', { type: 'image/png' }),
    ]
    await expect(api.submitExcuse(
      { lessonIds: ['77'], reason: 'ILLNESS', comment: 'Температура' },
      files,
      'excuse-key-00000001',
    )).resolves.toMatchObject({ summary: { status: 'PENDING' } })
  })

  it('returns binary attachments without attempting JSON parsing', async () => {
    const fetcher = vi.fn<typeof fetch>(async (_input, init) => {
      const headers = new Headers(init?.headers)
      expect(headers.get('Accept')).toBe('application/octet-stream')
      return new Response(new Uint8Array([0x25, 0x50, 0x44, 0x46]), {
        status: 200,
        headers: {
          'Content-Type': 'application/pdf',
          'Content-Disposition': "attachment; filename*=UTF-8''medical-note.pdf",
        },
      })
    })

    const api = new StudentApi({ accessToken: () => 'jwt-a', fetcher })
    const result = await api.downloadRequestAttachment(
      '507f1f77bcf86cd799439011',
      '507f1f77bcf86cd799439012',
    )
    expect(result.contentType).toBe('application/pdf')
    expect(result.filename).toBe('medical-note.pdf')
    expect([...new Uint8Array(await result.blob.arrayBuffer())])
      .toEqual([0x25, 0x50, 0x44, 0x46])
  })

  it('reuses the original JSON body and idempotency key for one same-context 401 retry', async () => {
    const calls: RequestInit[] = []
    const fetcher = vi.fn<typeof fetch>(async (_input, init) => {
      calls.push(init ?? {})
      if (calls.length === 1) return new Response(null, { status: 401 })
      return new Response(detail, {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
    const onUnauthorized = vi.fn(async () => undefined)
    const api = new StudentApi({ accessToken: () => 'jwt-a', onUnauthorized, fetcher })

    await expect(api.submitLateCheckin({ lessonId: '77' }, 'retry-key-00000001'))
      .resolves.toMatchObject({ summary: { id: '507f1f77bcf86cd799439011' } })
    expect(onUnauthorized).toHaveBeenCalledOnce()
    expect(calls).toHaveLength(2)
    const first = calls.at(0)
    const second = calls.at(1)
    expect(first).toBeDefined()
    expect(second).toBeDefined()
    expect(second?.body).toBe(first?.body)
    expect(new Headers(second?.headers).get('Idempotency-Key'))
      .toBe(new Headers(first?.headers).get('Idempotency-Key'))
  })
})

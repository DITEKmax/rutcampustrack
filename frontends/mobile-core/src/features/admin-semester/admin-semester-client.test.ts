import { describe, expect, it } from 'vitest'
import {
  createGenerationBoundAdminSemesterClient,
  StaleSessionGenerationError,
} from '../../shared/session-owner'
import { AdminSemesterClient } from './admin-semester-client'

const semester = {
  id: 7,
  name: 'Осень 2026',
  dateFrom: '2026-09-01',
  dateTo: '2027-01-31',
  active: false,
  createdAt: '2026-09-22T10:00:00Z',
}
const secondSemester = {
  ...semester,
  id: 8,
  name: 'Весна 2027',
  dateFrom: '2027-02-01',
  dateTo: '2027-06-30',
  active: true,
}

function jsonResponse(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('AdminSemesterClient', () => {
  it('reads the HATEOAS semester page from the academic gateway', async () => {
    let path = ''
    const client = new AdminSemesterClient({
      accessToken: () => 'token',
      fetcher: async (input, init) => {
        path = String(input)
        expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token')
        return jsonResponse({ _embedded: { semesterResponseList: [semester] } })
      },
    })

    await expect(client.listSemesters()).resolves.toEqual([semester])
    expect(path).toBe('/api/academic/semesters?size=200')
  })

  it('follows every server-reported page so an active semester cannot be hidden', async () => {
    const paths: string[] = []
    const signals: Array<AbortSignal | null> = []
    const client = new AdminSemesterClient({
      accessToken: () => 'token',
      fetcher: async (input, init) => {
        paths.push(String(input))
        signals.push(init?.signal instanceof AbortSignal ? init.signal : null)
        const page = new URL(String(input), 'https://example.test').searchParams.get('page') ?? '0'
        return page === '0'
          ? jsonResponse({
            _embedded: { semesterResponseList: [semester] },
            page: { number: 0, totalPages: 2 },
          })
          : jsonResponse({
            _embedded: { semesterResponseList: [secondSemester] },
            page: { number: 1, totalPages: 2 },
          })
      },
    })

    const controller = new AbortController()
    await expect(client.listSemesters(controller.signal)).resolves.toEqual([semester, secondSemester])
    expect(paths).toEqual([
      '/api/academic/semesters?size=200',
      '/api/academic/semesters?page=1&size=200',
    ])
    expect(signals).toEqual([controller.signal, controller.signal])
  })

  it('posts a current period with a past start date and preserves server validation', async () => {
    let capturedInput: RequestInfo | URL | undefined
    let capturedInit: RequestInit | undefined
    const client = new AdminSemesterClient({
      accessToken: () => 'token',
      fetcher: async (input, init) => {
        capturedInput = input
        capturedInit = init
        return jsonResponse({ ...semester, active: false })
      },
    })

    await client.createSemester({
      name: 'Осень 2026',
      dateFrom: '2026-09-01',
      dateTo: '2027-01-31',
    })
    expect(capturedInput).toBe('/api/academic/semesters')
    expect(capturedInit?.method).toBe('POST')
    expect(JSON.parse(String(capturedInit?.body))).toEqual({
      name: 'Осень 2026',
      dateFrom: '2026-09-01',
      dateTo: '2027-01-31',
    })
  })

  it('activates with one PATCH and no client-side deactivation request', async () => {
    const calls: Array<{ path: string; method: string | undefined; body: BodyInit | null | undefined }> = []
    const client = new AdminSemesterClient({
      accessToken: () => 'token',
      fetcher: async (input, init) => {
        calls.push({ path: String(input), method: init?.method, body: init?.body })
        return jsonResponse({ ...semester, active: true })
      },
    })

    await expect(client.activateSemester(7)).resolves.toMatchObject({ id: 7, active: true })
    expect(calls).toEqual([{
      path: '/api/academic/semesters/7/activate',
      method: 'PATCH',
      body: undefined,
    }])
  })

  it('rejects a response that crosses the captured session generation', async () => {
    let generation = 4
    let resolveResponse!: (response: Response) => void
    const client = createGenerationBoundAdminSemesterClient({
      currentGeneration: () => generation,
      accessTokenFor: () => 'token',
      refreshFor: async () => undefined,
    }, () => new Promise<Response>((resolve) => { resolveResponse = resolve }))

    const pending = client.listSemesters()
    generation = 5
    resolveResponse(jsonResponse({ _embedded: { semesterResponseList: [semester] } }))

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })
})

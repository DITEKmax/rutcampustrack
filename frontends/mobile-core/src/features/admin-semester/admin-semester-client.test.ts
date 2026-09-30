import { describe, expect, it } from 'vitest'
import {
  createGenerationBoundAdminSemesterClient,
  StaleSessionGenerationError,
} from '../../shared/session-owner'
import { AdminSemesterClient } from './admin-semester-client'

const semesterResponse = {
  id: 7,
  name: 'Осень 2026',
  dateFrom: '2026-09-01',
  dateTo: '2027-01-31',
  active: false,
  createdAt: '2026-09-22T10:00:00Z',
  archived: false,
  transition: 'NONE',
  stateVersion: 0,
  releasePending: false,
  writeBlocked: false,
  semesterType: null,
  academicYear: null,
}
const { writeBlocked, ...semesterFields } = semesterResponse
const semester = { ...semesterFields, isWriteBlocked: writeBlocked }
const secondSemesterResponse = {
  ...semesterResponse,
  id: 8,
  name: 'Весна 2027',
  dateFrom: '2027-02-01',
  dateTo: '2027-06-30',
  active: true,
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

const archiveOperation = {
  operationId: '8f0504b6-ff3c-47a7-b762-0d008cf509a0',
  semesterId: 7,
  action: 'ARCHIVE',
  operationState: 'PENDING',
  retryable: false,
  stateVersion: 2,
  transition: 'ARCHIVING',
  active: true,
  archived: false,
  releasePending: false,
  academic: 'READY',
  schedule: 'PENDING',
  attendance: 'NOT_STARTED',
  blockingReason: null,
}

describe('AdminSemesterClient', () => {
  it('reads the HATEOAS semester page from the academic gateway', async () => {
    let path = ''
    const client = new AdminSemesterClient({
      accessToken: () => 'token',
      fetcher: async (input, init) => {
        path = String(input)
        expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token')
        return jsonResponse({ _embedded: { semesterResponseList: [semesterResponse] } })
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
            _embedded: { semesterResponseList: [semesterResponse] },
            page: { number: 0, totalPages: 2 },
          })
          : jsonResponse({
            _embedded: { semesterResponseList: [secondSemesterResponse] },
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
        return jsonResponse({
          ...semesterResponse,
          name: 'Осенний 2026/2027',
          semesterType: 'AUTUMN',
          academicYear: 2026,
          active: false,
        })
      },
    })

    await client.createSemester({
      name: 'Осенний 2026/2027',
      dateFrom: '2026-09-01',
      dateTo: '2027-01-31',
      semesterType: 'AUTUMN',
      academicYear: 2026,
    })
    expect(capturedInput).toBe('/api/academic/semesters')
    expect(capturedInit?.method).toBe('POST')
    expect(JSON.parse(String(capturedInit?.body))).toEqual({
      name: 'Осенний 2026/2027',
      dateFrom: '2026-09-01',
      dateTo: '2027-01-31',
      semesterType: 'AUTUMN',
      academicYear: 2026,
    })
  })

  it('activates with one PATCH and no client-side deactivation request', async () => {
    const calls: Array<{ path: string; method: string | undefined; body: BodyInit | null | undefined }> = []
    const client = new AdminSemesterClient({
      accessToken: () => 'token',
      fetcher: async (input, init) => {
        calls.push({ path: String(input), method: init?.method, body: init?.body })
        return jsonResponse({ ...semesterResponse, active: true })
      },
    })

    await expect(client.activateSemester(7)).resolves.toMatchObject({ id: 7, active: true })
    expect(calls).toEqual([{
      path: '/api/academic/semesters/7/activate',
      method: 'PATCH',
      body: undefined,
    }])
  })

  it('starts archive and restore with an empty body and a caller-owned idempotency key', async () => {
    const calls: Array<{ path: string; method: string | undefined; body: BodyInit | null | undefined; key: string | null }> = []
    const client = new AdminSemesterClient({
      accessToken: () => 'token',
      fetcher: async (input, init) => {
        calls.push({
          path: String(input),
          method: init?.method,
          body: init?.body,
          key: new Headers(init?.headers).get('Idempotency-Key'),
        })
        const action = String(input).endsWith('/restore') ? 'RESTORE' : 'ARCHIVE'
        return jsonResponse({
          ...archiveOperation,
          action,
          transition: action === 'ARCHIVE' ? 'ARCHIVING' : 'RESTORING',
          active: action === 'ARCHIVE',
          archived: action === 'RESTORE',
        }, 202)
      },
    })

    await expect(client.archiveSemester(7, 'b6160ec9-7450-4d41-b19c-0cb166b9115d')).resolves.toMatchObject({
      operationId: archiveOperation.operationId,
      operationState: 'PENDING',
    })
    await client.restoreSemester(7, 'cb087e19-b5ae-44cb-9444-c685c7f9a3d2')

    expect(calls).toEqual([
      {
        path: '/api/academic/semesters/7/archive',
        method: 'POST',
        body: null,
        key: 'b6160ec9-7450-4d41-b19c-0cb166b9115d',
      },
      {
        path: '/api/academic/semesters/7/restore',
        method: 'POST',
        body: null,
        key: 'cb087e19-b5ae-44cb-9444-c685c7f9a3d2',
      },
    ])
  })

  it('reads current archive authority separately from the historical operation snapshot', async () => {
    const paths: string[] = []
    const client = new AdminSemesterClient({
      accessToken: () => 'token',
      fetcher: async (input) => {
        paths.push(String(input))
        if (String(input).startsWith('/api/academic/semester-archive-operations/')) {
          return jsonResponse(archiveOperation)
        }
        return jsonResponse({
          semesterId: 7,
          active: false,
          archived: false,
          transition: 'NONE',
          stateVersion: 4,
          releasePending: true,
          operation: { ...archiveOperation, action: 'RESTORE', operationState: 'COMPLETED', stateVersion: 3 },
        })
      },
    })

    await expect(client.getArchiveStatus(7)).resolves.toMatchObject({
      semesterId: 7,
      archived: false,
      stateVersion: 4,
      releasePending: true,
      operation: { operationState: 'COMPLETED', stateVersion: 3 },
    })
    await expect(client.getArchiveOperation(archiveOperation.operationId)).resolves.toMatchObject({
      releasePending: false,
    })
    expect(paths).toEqual([
      '/api/academic/semesters/7/archive/status',
      `/api/academic/semester-archive-operations/${archiveOperation.operationId}`,
    ])
  })

  it('preserves a persisted operation carried by an uncertain 503 response', async () => {
    const client = new AdminSemesterClient({
      accessToken: () => 'token',
      fetcher: async () => jsonResponse({ title: 'Операция ещё выполняется', operation: archiveOperation }, 503),
    })

    await expect(client.archiveSemester(7, 'b6160ec9-7450-4d41-b19c-0cb166b9115d'))
      .rejects.toMatchObject({ response: { status: 503 }, archiveOperation: { operationId: archiveOperation.operationId } })
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
    resolveResponse(jsonResponse({ _embedded: { semesterResponseList: [semesterResponse] } }))

    await expect(pending).rejects.toBeInstanceOf(StaleSessionGenerationError)
  })
})

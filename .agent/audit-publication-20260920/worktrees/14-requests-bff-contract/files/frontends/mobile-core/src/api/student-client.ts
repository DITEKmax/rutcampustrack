import type {
  MobileProblemDetails,
  StudentCheckinAck,
  StudentCheckinCommand,
  StudentHomework,
  StudentHomeworkCompletion,
  StudentHomeworkCompletionCommand,
  StudentSemesterSchedule,
  StudentSession,
  StudentToday,
  StudentRequestBucket,
  StudentRequestDetail,
  StudentRequestOptions,
  StudentRequestPage,
  StudentExcuseRequest,
  StudentLateCheckinRequest,
} from './types'

export class StudentApiError extends Error {
  readonly problem: MobileProblemDetails | null

  constructor(readonly response: Response, problem: MobileProblemDetails | null) {
    super(problem?.detail || problem?.title || `HTTP ${response.status}`)
    this.name = 'StudentApiError'
    this.problem = problem
  }
}

export interface StudentApiOptions {
  accessToken: () => string | null
  onUnauthorized?: () => Promise<void>
  fetcher?: typeof fetch
}

export class StudentApi {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: StudentApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  getSession(): Promise<StudentSession> {
    return this.request('/api/v1/student/session')
  }

  getToday(): Promise<StudentToday> {
    return this.request('/api/v1/student/today')
  }

  getSemesterSchedule(semesterId: string, etag?: string): Promise<{ data: StudentSemesterSchedule | null; etag: string | null }> {
    const init: RequestInit = etag ? { headers: { 'If-None-Match': etag } } : {}
    return this.requestResponse(`/api/v1/student/schedule?semesterId=${encodeURIComponent(semesterId)}`, init)
      .then(async (response) => ({
        data: response.status === 304 ? null : await this.json<StudentSemesterSchedule>(response),
        etag: response.headers.get('ETag'),
      }))
  }

  getHomework(from?: string, to?: string): Promise<StudentHomework> {
    const params = new URLSearchParams()
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    const query = params.toString()
    return this.request(`/api/v1/student/homework${query ? `?${query}` : ''}`)
  }

  setHomeworkCompletion(id: string, command: StudentHomeworkCompletionCommand): Promise<StudentHomeworkCompletion> {
    return this.request(`/api/v1/student/homework/${encodeURIComponent(id)}/completion`, {
      method: 'PUT',
      body: JSON.stringify(command),
    })
  }

  checkin(lessonId: string, command: StudentCheckinCommand, idempotencyKey: string): Promise<StudentCheckinAck> {
    return this.request(`/api/v1/student/lessons/${encodeURIComponent(lessonId)}/checkin`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(command),
    })
  }

  listRequests(
    bucket: StudentRequestBucket = 'OPEN',
    page?: number,
    size?: number,
  ): Promise<StudentRequestPage> {
    const params = new URLSearchParams({ bucket })
    if (page !== undefined) params.set('page', String(page))
    if (size !== undefined) params.set('size', String(size))
    return this.request(`/api/v1/student/requests?${params.toString()}`)
  }

  /** Alias retained for callers that use the resource name as the method name. */
  getRequests(
    bucket: StudentRequestBucket = 'OPEN',
    page?: number,
    size?: number,
  ): Promise<StudentRequestPage> {
    return this.listRequests(bucket, page, size)
  }

  getRequestOptions(): Promise<StudentRequestOptions> {
    return this.request('/api/v1/student/requests/options')
  }

  getOptions(): Promise<StudentRequestOptions> {
    return this.getRequestOptions()
  }

  getRequest(id: string): Promise<StudentRequestDetail> {
    return this.request(`/api/v1/student/requests/${encodeURIComponent(id)}`)
  }

  getStudentRequest(id: string): Promise<StudentRequestDetail> {
    return this.getRequest(id)
  }

  submitExcuse(
    request: StudentExcuseRequest,
    files: Blob[],
    idempotencyKey: string,
  ): Promise<StudentRequestDetail>
  submitExcuse(
    request: StudentExcuseRequest,
    idempotencyKey: string,
    files?: Blob[],
  ): Promise<StudentRequestDetail>
  submitExcuse(
    request: StudentExcuseRequest,
    filesOrKey: Blob[] | string,
    keyOrFiles?: string | Blob[],
  ): Promise<StudentRequestDetail> {
    const files = typeof filesOrKey === 'string' ? keyOrFiles as Blob[] ?? [] : filesOrKey
    const idempotencyKey = typeof filesOrKey === 'string' ? filesOrKey : keyOrFiles as string
    const form = new FormData()
    form.append(
      'request',
      new Blob([JSON.stringify(request)], { type: 'application/json' }),
      'request.json',
    )
    files.forEach((file, index) => {
      const filename = typeof File !== 'undefined' && file instanceof File
        ? file.name
        : `attachment-${index + 1}`
      form.append('files', file, filename)
    })
    return this.request('/api/v1/student/requests/excuse', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: form,
    })
  }

  submitLateCheckin(
    request: StudentLateCheckinRequest,
    idempotencyKey: string,
  ): Promise<StudentRequestDetail> {
    return this.request('/api/v1/student/requests/late-checkin', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(request),
    })
  }

  cancelRequest(id: string): Promise<StudentRequestDetail> {
    return this.request(`/api/v1/student/requests/${encodeURIComponent(id)}/cancel`, {
      method: 'POST',
    })
  }

  cancelStudentRequest(id: string): Promise<StudentRequestDetail> {
    return this.cancelRequest(id)
  }

  async downloadRequestAttachment(
    id: string,
    attachmentId: string,
  ): Promise<{ blob: Blob; contentType: string; filename: string; headers: Headers }> {
    const response = await this.requestResponse(
      `/api/v1/student/requests/${encodeURIComponent(id)}/attachments/${encodeURIComponent(attachmentId)}`,
      { headers: { Accept: 'application/octet-stream' } },
    )
    return {
      blob: await response.blob(),
      contentType: response.headers.get('Content-Type') ?? 'application/octet-stream',
      filename: filenameFromContentDisposition(response.headers.get('Content-Disposition')),
      headers: response.headers,
    }
  }

  downloadAttachment(id: string, attachmentId: string) {
    return this.downloadRequestAttachment(id, attachmentId)
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await this.requestResponse(path, init)
    return this.json<T>(response)
  }

  private async requestResponse(path: string, init?: RequestInit, retried = false): Promise<Response> {
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    const body = init?.body
    const isFormData = typeof FormData !== 'undefined' && body instanceof FormData
    if (body !== undefined && body !== null && !isFormData && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
      headers.set('X-Internal-Token', token)
    }
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    if (response.ok || response.status === 304) return response
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      return this.requestResponse(path, init, true)
    }

    let problem: MobileProblemDetails | null = null
    try {
      problem = await response.clone().json() as MobileProblemDetails
    } catch {
      // A gateway/network response without Problem Details keeps its HTTP context.
    }
    throw new StudentApiError(response, problem)
  }

  private async json<T>(response: Response): Promise<T> {
    return response.json() as Promise<T>
  }
}

function filenameFromContentDisposition(value: string | null): string {
  if (!value) return 'attachment'
  const encoded = value.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    try { return decodeURIComponent(encoded.replace(/^"|"$/g, '')) } catch { /* fallback below */ }
  }
  return value.match(/filename="([^"]+)"/i)?.[1]
    ?? value.match(/filename=([^;]+)/i)?.[1]?.trim()
    ?? 'attachment'
}

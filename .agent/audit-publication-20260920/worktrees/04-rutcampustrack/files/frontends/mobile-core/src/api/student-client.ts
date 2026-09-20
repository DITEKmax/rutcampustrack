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

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await this.requestResponse(path, init)
    return this.json<T>(response)
  }

  private async requestResponse(path: string, init?: RequestInit, retried = false): Promise<Response> {
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    headers.set('Accept', 'application/json')
    if (init?.body) headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    if (response.ok || response.status === 304) return response
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      return this.requestResponse(path, init, true)
    }

    let problem: MobileProblemDetails | null = null
    try {
      problem = await response.json() as MobileProblemDetails
    } catch {
      // A gateway/network response without Problem Details keeps its HTTP context.
    }
    throw new StudentApiError(response, problem)
  }

  private async json<T>(response: Response): Promise<T> {
    return response.json() as Promise<T>
  }
}

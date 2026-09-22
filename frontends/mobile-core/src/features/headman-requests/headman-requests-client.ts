import { StaleSessionGenerationError } from '../../shared/session-owner'

export type HeadmanRequestBucket = 'OPEN' | 'ARCHIVE'
export type HeadmanRequestKind = 'EXCUSE' | 'LATE_CHECKIN'
export type HeadmanRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface HeadmanRequestSummary {
  readonly id: string
  readonly kind: HeadmanRequestKind
  readonly status: HeadmanRequestStatus
  readonly studentId: number | null
  readonly studentName: string | null
  readonly reason: string | null
  readonly comment: string | null
  readonly coverageStart: string | null
  readonly coverageEnd: string | null
  readonly lessonCount: number
  readonly alreadyMarkedCount: number
  readonly hasAttachments: boolean
  readonly createdAt: string | null
  readonly updatedAt: string | null
  readonly decisionBy: number | null
  readonly decisionAt: string | null
  readonly decisionComment: string | null
}

export interface HeadmanRequestLesson {
  readonly lessonId: number | null
  readonly groupId: number | null
  readonly subjectId: number | null
  readonly subjectName: string | null
  readonly subjectType: string | null
  readonly semesterId: number | null
  readonly lessonNumber: number | null
  readonly date: string | null
  readonly startsAt: string | null
  readonly endsAt: string | null
  readonly requestLessonStatus: string | null
  readonly attendanceStatus: string | null
  readonly attendanceSource: string | null
}

export interface HeadmanRequestAttachment {
  readonly id: string
  readonly name: string | null
  readonly contentType: string | null
  readonly size: number
  readonly sha256: string | null
  readonly state: string | null
  readonly uploadedAt: string | null
  readonly expiresAt: string | null
  readonly downloadUrl: string | null
}

export interface HeadmanRequestDetail {
  readonly summary: HeadmanRequestSummary
  readonly lessons: readonly HeadmanRequestLesson[]
  readonly attachments: readonly HeadmanRequestAttachment[]
}

export interface HeadmanRequestPage {
  readonly content: readonly HeadmanRequestSummary[]
  readonly page: number
  readonly size: number
  readonly totalElements: number
  readonly totalPages: number
}

export interface HeadmanRequestFilters {
  readonly bucket?: HeadmanRequestBucket
  readonly page?: number
  readonly size?: number
  readonly type?: HeadmanRequestKind | null
  readonly studentName?: string | null
  readonly coverageDateFrom?: string | null
  readonly coverageDateTo?: string | null
}

export interface HeadmanRequestsApiOptions {
  accessToken: () => string | null
  onUnauthorized?: () => Promise<void>
  assertCurrent?: () => void
  fetcher?: typeof fetch
}

export class HeadmanRequestsApiError extends Error {
  constructor(readonly response: Response, readonly problem: unknown) {
    super(problemDetail(problem) ?? `HTTP ${response.status}`)
    this.name = 'HeadmanRequestsApiError'
  }
}

export class HeadmanRequestsApi {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: HeadmanRequestsApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  list(filters: HeadmanRequestFilters = {}): Promise<HeadmanRequestPage> {
    const bucket = filters.bucket ?? 'OPEN'
    const page = filters.page ?? 0
    const size = filters.size ?? 20
    if (page < 0 || !Number.isSafeInteger(page)) throw new RangeError('page must be a non-negative integer')
    if (size < 1 || size > 100 || !Number.isSafeInteger(size)) throw new RangeError('size must be between 1 and 100')
    const query = new URLSearchParams({ bucket, page: String(page), size: String(size) })
    if (filters.type) query.set('type', filters.type)
    if (filters.studentName?.trim()) query.set('studentName', filters.studentName.trim())
    if (filters.coverageDateFrom) {
      assertDate(filters.coverageDateFrom, 'coverageDateFrom')
      query.set('coverageDateFrom', filters.coverageDateFrom)
    }
    if (filters.coverageDateTo) {
      assertDate(filters.coverageDateTo, 'coverageDateTo')
      query.set('coverageDateTo', filters.coverageDateTo)
    }
    if (filters.coverageDateFrom && filters.coverageDateTo && filters.coverageDateFrom > filters.coverageDateTo) {
      throw new RangeError('coverageDateFrom must be before or equal to coverageDateTo')
    }
    return this.request<unknown>(`/api/attendance/requests?${query.toString()}`).then(normalizePage)
  }

  get(requestId: string): Promise<HeadmanRequestDetail> {
    assertId(requestId)
    return this.request<unknown>(`/api/attendance/requests/${encodeURIComponent(requestId)}`).then(normalizeDetail)
  }

  decide(requestId: string, decision: 'APPROVED' | 'REJECTED', reason?: string | null): Promise<HeadmanRequestDetail> {
    assertId(requestId)
    if (decision !== 'APPROVED' && decision !== 'REJECTED') throw new TypeError('Unsupported request decision')
    const normalizedReason = reason?.trim() || null
    if (decision === 'REJECTED' && !normalizedReason) throw new TypeError('A rejection reason is required')
    return this.request<unknown>(`/api/attendance/requests/${encodeURIComponent(requestId)}/decision`, {
      method: 'POST',
      body: JSON.stringify({ decision, ...(normalizedReason ? { reason: normalizedReason } : {}) }),
    }).then(normalizeDetail)
  }

  async downloadAttachment(requestId: string, attachmentId: string): Promise<Blob> {
    assertId(requestId)
    assertId(attachmentId)
    const response = await this.response(`/api/attendance/requests/${encodeURIComponent(requestId)}/attachments/${encodeURIComponent(attachmentId)}`)
    if (!response.ok) throw await this.apiError(response)
    const blob = await response.blob()
    this.options.assertCurrent?.()
    return blob
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await this.response(path, init, retried)
    if (!response.ok) throw await this.apiError(response)
    const value = await response.json() as T
    this.options.assertCurrent?.()
    return value
  }

  private async response(path: string, init?: RequestInit, retried = false): Promise<Response> {
    this.options.assertCurrent?.()
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (typeof init?.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      return this.response(path, init, true)
    }
    return response
  }

  private async apiError(response: Response): Promise<HeadmanRequestsApiError> {
    let problem: unknown = null
    try { problem = await response.json() } catch { /* Preserve HTTP status. */ }
    this.options.assertCurrent?.()
    return new HeadmanRequestsApiError(response, problem)
  }
}

export interface HeadmanRequestsApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export function createGenerationBoundHeadmanRequestsApi(
  owner: HeadmanRequestsApiGenerationOwner,
  fetcher?: typeof fetch,
): HeadmanRequestsApi {
  const generation = owner.currentGeneration()
  const assertCurrent = (): void => {
    if (generation !== owner.currentGeneration()) throw new StaleSessionGenerationError()
  }
  const options: HeadmanRequestsApiOptions = {
    assertCurrent,
    accessToken: () => {
      assertCurrent()
      return owner.accessTokenFor(generation)
    },
    onUnauthorized: async () => {
      assertCurrent()
      await owner.refreshFor(generation)
      assertCurrent()
    },
  }
  const transport = fetcher ?? ((input: RequestInfo | URL, init?: RequestInit) => globalThis.fetch(input, init))
  options.fetcher = async (input, init) => {
    assertCurrent()
    const response = await transport(input, init)
    assertCurrent()
    return response
  }
  return new HeadmanRequestsApi(options)
}

function normalizePage(value: unknown): HeadmanRequestPage {
  const record = asRecord(value)
  const content = Array.isArray(record?.content) ? record.content.map(normalizeSummary).filter(isValue) : []
  return {
    content,
    page: integerValue(record?.page) ?? 0,
    size: integerValue(record?.size) ?? content.length,
    totalElements: integerValue(record?.totalElements) ?? content.length,
    totalPages: integerValue(record?.totalPages) ?? (content.length ? 1 : 0),
  }
}

function normalizeDetail(value: unknown): HeadmanRequestDetail {
  const record = asRecord(value)
  const summary = normalizeSummary(record?.summary)
  if (!summary) throw new Error('Сервер вернул неполную заявку')
  const lessons = Array.isArray(record?.lessons) ? record.lessons.map(normalizeLesson).filter(isValue) : []
  const attachments = Array.isArray(record?.attachments) ? record.attachments.map(normalizeAttachment).filter(isValue) : []
  return { summary, lessons, attachments }
}

function normalizeSummary(value: unknown): HeadmanRequestSummary | null {
  const record = asRecord(value)
  const id = stringValue(record?.id)
  const kind = record?.kind === 'EXCUSE' || record?.kind === 'LATE_CHECKIN' ? record.kind : null
  const status = record?.status === 'PENDING' || record?.status === 'APPROVED'
    || record?.status === 'REJECTED' || record?.status === 'CANCELLED' ? record.status : null
  if (!id || !kind || !status) return null
  return {
    id,
    kind,
    status,
    studentId: positiveInteger(record?.studentId),
    studentName: stringValue(record?.studentName),
    reason: stringValue(record?.reason),
    comment: stringValue(record?.comment),
    coverageStart: stringValue(record?.coverageStart),
    coverageEnd: stringValue(record?.coverageEnd),
    lessonCount: integerValue(record?.lessonCount) ?? 0,
    alreadyMarkedCount: integerValue(record?.alreadyMarkedCount) ?? 0,
    hasAttachments: record?.hasAttachments === true,
    createdAt: stringValue(record?.createdAt),
    updatedAt: stringValue(record?.updatedAt),
    decisionBy: positiveInteger(record?.decisionBy),
    decisionAt: stringValue(record?.decisionAt),
    decisionComment: stringValue(record?.decisionComment),
  }
}

function normalizeLesson(value: unknown): HeadmanRequestLesson | null {
  const record = asRecord(value)
  if (!record) return null
  return {
    lessonId: positiveInteger(record.lessonId),
    groupId: positiveInteger(record.groupId),
    subjectId: positiveInteger(record.subjectId),
    subjectName: stringValue(record.subjectName),
    subjectType: stringValue(record.subjectType),
    semesterId: positiveInteger(record.semesterId),
    lessonNumber: integerValue(record.lessonNumber),
    date: stringValue(record.date),
    startsAt: stringValue(record.startsAt),
    endsAt: stringValue(record.endsAt),
    requestLessonStatus: stringValue(record.requestLessonStatus),
    attendanceStatus: stringValue(record.attendanceStatus),
    attendanceSource: stringValue(record.attendanceSource),
  }
}

function normalizeAttachment(value: unknown): HeadmanRequestAttachment | null {
  const record = asRecord(value)
  const id = stringValue(record?.id)
  if (!record || !id) return null
  return {
    id,
    name: stringValue(record.name),
    contentType: stringValue(record.contentType),
    size: integerValue(record.size) ?? 0,
    sha256: stringValue(record.sha256),
    state: stringValue(record.state),
    uploadedAt: stringValue(record.uploadedAt),
    expiresAt: stringValue(record.expiresAt),
    downloadUrl: stringValue(record.downloadUrl),
  }
}

function assertId(value: string): void {
  if (!value || value.length > 128) throw new TypeError('request id must be a non-empty string')
}

function assertDate(value: string, name: string): void {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(Date.parse(`${value}T00:00:00Z`))) {
    throw new RangeError(`${name} must be YYYY-MM-DD`)
  }
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null ? value as Record<string, unknown> : null
}

function stringValue(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null
}

function integerValue(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) ? value : null
}

function positiveInteger(value: unknown): number | null {
  const number = integerValue(value)
  return number !== null && number > 0 ? number : null
}

function isValue<T>(value: T | null): value is T {
  return value !== null
}

function problemDetail(value: unknown): string | null {
  const record = asRecord(value)
  return typeof record?.detail === 'string'
    ? record.detail
    : typeof record?.title === 'string' ? record.title : null
}

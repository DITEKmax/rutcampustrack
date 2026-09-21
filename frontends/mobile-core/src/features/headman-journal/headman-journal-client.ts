import { StaleSessionGenerationError } from '../../shared/session-owner'

export type HeadmanJournalLessonStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED' | 'UNSUPPORTED'
export type HeadmanJournalAttendanceStatus = 'PRESENT' | 'ABSENT' | 'EXCUSED' | 'EMPTY' | 'CANCELLED' | 'UNSUPPORTED'
export type HeadmanJournalExcuseType =
  | 'ILLNESS'
  | 'MEDICAL_EXAMINATION'
  | 'COMPETITION_PARTICIPATION'
  | 'FAMILY_CIRCUMSTANCES'
  | 'SUMMONS'
  | 'UNIVERSITY_ORDER'
  | 'EXEMPTION'
  | 'OTHER'

export interface HeadmanJournalLesson {
  readonly id: number
  readonly groupId: number | null
  readonly subjectId: number | null
  readonly date: string
  readonly status: HeadmanJournalLessonStatus
  readonly lessonNumber: number | null
  readonly startTime: string | null
  readonly endTime: string | null
  readonly room: string | null
  readonly lessonType: string | null
}

export interface HeadmanJournalRosterEntry {
  readonly userId: number
  readonly displayName: string
  readonly status: HeadmanJournalAttendanceStatus
  readonly symbol: string | null
  readonly source: string | null
  readonly excuseReason: string | null
  readonly excuseType: string | null
  readonly comment: string | null
  readonly attachmentId: string | null
  readonly attachmentName: string | null
  readonly attachmentContentType: string | null
  readonly attachmentSize: number | null
  readonly editable: boolean
}

export interface HeadmanJournalReport {
  readonly lessonId: number
  readonly groupId: number | null
  readonly subjectId: number | null
  readonly lessonDate: string | null
  readonly semesterId: number | null
  readonly lessonStatus: HeadmanJournalLessonStatus
  readonly editable: boolean
  readonly entries: readonly HeadmanJournalRosterEntry[]
}

export type HeadmanJournalMarkCommand =
  | { readonly status: 'PRESENT' | 'ABSENT' }
  | {
    readonly status: 'EXCUSED'
    readonly excuseType: HeadmanJournalExcuseType
    readonly comment?: string | null
    readonly file?: File | null
  }

export interface HeadmanJournalMarkAck {
  readonly status: 'PRESENT' | 'ABSENT' | 'EXCUSED'
  readonly lessonId: number
  readonly userId: number
  readonly timestamp: string | null
}

export interface HeadmanJournalWritePort {
  mark(lessonId: number, userId: number, command: HeadmanJournalMarkCommand): Promise<HeadmanJournalMarkAck>
  clear(lessonId: number, userId: number): Promise<void>
}

export interface HeadmanJournalApiOptions {
  accessToken: () => string | null
  onUnauthorized?: () => Promise<void>
  assertCurrent?: () => void
  fetcher?: typeof fetch
}

export class HeadmanJournalApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: unknown,
  ) {
    super(problemDetail(problem) ?? `HTTP ${response.status}`)
    this.name = 'HeadmanJournalApiError'
  }
}

export class HeadmanJournalApi implements HeadmanJournalWritePort {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: HeadmanJournalApiOptions, private readonly writePort: HeadmanJournalWritePort | null = null) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  get writable(): boolean {
    return true
  }

  listLessons(groupId: number, dateFrom: string, dateTo: string): Promise<readonly HeadmanJournalLesson[]> {
    assertPositiveInteger(groupId, 'groupId')
    assertDate(dateFrom, 'dateFrom')
    assertDate(dateTo, 'dateTo')
    if (dateFrom > dateTo) throw new RangeError('dateFrom must be before or equal to dateTo')
    return this.requestPaged((page) => {
      const query = new URLSearchParams({
        dateFrom,
        dateTo,
        page: String(page),
        size: '100',
      })
      return `/api/schedule/groups/${groupId}/lessons?${query.toString()}`
    }, 'lessonResponseList', normalizeLesson)
  }

  async getLessonAttendance(lessonId: number): Promise<HeadmanJournalReport> {
    assertPositiveInteger(lessonId, 'lessonId')
    const value = await this.request<unknown>(`/api/attendance/reports/lesson/${lessonId}`)
    return normalizeReport(value, lessonId)
  }

  async downloadAttachment(lessonId: number, userId: number): Promise<Blob> {
    assertPositiveInteger(lessonId, 'lessonId')
    assertPositiveInteger(userId, 'userId')
    const response = await this.response(`/api/attendance/lessons/${lessonId}/students/${userId}/attachment`)
    if (!response.ok) throw await this.apiError(response)
    const blob = await response.blob()
    this.options.assertCurrent?.()
    return blob
  }

  mark(lessonId: number, userId: number, command: HeadmanJournalMarkCommand): Promise<HeadmanJournalMarkAck> {
    if (this.writePort) return this.writePort.mark(lessonId, userId, command)
    assertPositiveInteger(lessonId, 'lessonId')
    assertPositiveInteger(userId, 'userId')
    const payload = markPayload(command)
    const headers = new Headers()
    let body: BodyInit
    if (command.status === 'EXCUSED' && command.file) {
      const form = new FormData()
      form.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
      form.append('file', command.file, command.file.name)
      body = form
    } else {
      headers.set('Content-Type', 'application/json')
      body = JSON.stringify(payload)
    }
    return this.request<unknown>(`/api/attendance/lessons/${lessonId}/students/${userId}`, {
      method: 'PUT',
      headers,
      body,
    }).then((value) => normalizeMarkAck(value, lessonId, userId))
  }

  clear(lessonId: number, userId: number): Promise<void> {
    if (this.writePort) return this.writePort.clear(lessonId, userId)
    assertPositiveInteger(lessonId, 'lessonId')
    assertPositiveInteger(userId, 'userId')
    return this.request<void>(`/api/attendance/lessons/${lessonId}/students/${userId}`, { method: 'DELETE' })
  }

  private async requestPaged<T>(
    path: (page: number) => string,
    embeddedKey: string,
    normalize: (value: unknown) => T | null,
  ): Promise<readonly T[]> {
    const items: T[] = []
    let nextPath = path(0)
    for (let page = 0; page < 100; page += 1) {
      const value = await this.request<unknown>(nextPath)
      items.push(...embeddedItems(value, embeddedKey).map(normalize).filter((item): item is T => item !== null))
      const metadata = pageMetadata(value)
      if (metadata !== null && metadata.totalPages > page + 1) {
        nextPath = path(page + 1)
        continue
      }
      const nextHref = nextPageHref(value)
      if (nextHref !== null) {
        nextPath = nextHref
        continue
      }
      return items
    }
    throw new Error('Сервер вернул слишком много страниц пар')
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await this.response(path, init, retried)
    if (response.ok) {
      if (response.status === 204) {
        this.options.assertCurrent?.()
        return undefined as T
      }
      const value = await response.json() as T
      this.options.assertCurrent?.()
      return value
    }
    throw await this.apiError(response)
  }

  private async response(path: string, init?: RequestInit, retried = false): Promise<Response> {
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

  private async apiError(response: Response): Promise<HeadmanJournalApiError> {
    let problem: unknown = null
    try {
      problem = await response.json()
    } catch {
      // Preserve the HTTP status when the gateway did not return JSON.
    }
    this.options.assertCurrent?.()
    return new HeadmanJournalApiError(response, problem)
  }
}

export interface HeadmanJournalApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export function createGenerationBoundHeadmanJournalApi(
  owner: HeadmanJournalApiGenerationOwner,
  fetcher?: typeof fetch,
  writePort: HeadmanJournalWritePort | null = null,
): HeadmanJournalApi {
  const generation = owner.currentGeneration()
  const assertCurrent = (): void => {
    if (generation !== owner.currentGeneration()) throw new StaleSessionGenerationError()
  }
  const options: HeadmanJournalApiOptions = {
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
  return new HeadmanJournalApi(options, writePort)
}

function normalizeLesson(value: unknown): HeadmanJournalLesson | null {
  const record = unwrapContent(value)
  if (!record) return null
  const id = positiveInteger(record.id)
  const date = stringValue(record.date)
  if (id === null || date === null) return null
  const status = normalizeLessonStatus(record.status)
  return {
    id,
    groupId: positiveInteger(record.groupId),
    subjectId: positiveInteger(record.subjectId),
    date,
    status,
    lessonNumber: integerValue(record.lessonNumber),
    startTime: stringValue(record.startTime),
    endTime: stringValue(record.endTime),
    room: stringValue(record.room),
    lessonType: stringValue(record.lessonType),
  }
}

function normalizeReport(value: unknown, fallbackLessonId: number): HeadmanJournalReport {
  const record = unwrapContent(value)
  const lessonId = positiveInteger(record?.lessonId) ?? fallbackLessonId
  const rawEntries = Array.isArray(record?.entries) ? record.entries : []
  const reportEditable = booleanValue(record?.editable) ?? false
  const entries = rawEntries
    .map((entry) => normalizeEntry(entry, reportEditable))
    .filter((entry): entry is HeadmanJournalRosterEntry => entry !== null)
    .sort((left, right) => left.displayName.localeCompare(right.displayName, 'ru'))
  return {
    lessonId,
    groupId: positiveInteger(record?.groupId),
    subjectId: positiveInteger(record?.subjectId),
    lessonDate: stringValue(record?.lessonDate),
    semesterId: positiveInteger(record?.semesterId),
    lessonStatus: normalizeLessonStatus(record?.lessonStatus),
    editable: reportEditable,
    entries,
  }
}

function normalizeEntry(value: unknown, reportEditable: boolean): HeadmanJournalRosterEntry | null {
  if (!isRecord(value)) return null
  const userId = positiveInteger(value.userId)
  if (userId === null) return null
  const rawStatus = typeof value.status === 'string'
    ? value.status.trim().toUpperCase()
    : value.status === null
      ? ''
      : 'UNSUPPORTED'
  const status: HeadmanJournalAttendanceStatus = rawStatus === 'PRESENT'
    ? 'PRESENT'
    : rawStatus === 'ABSENT'
      ? 'ABSENT'
      : rawStatus === 'EXCUSED'
        ? 'EXCUSED'
      : rawStatus === 'CANCELLED'
          ? 'CANCELLED'
          : rawStatus === ''
            ? 'EMPTY'
            : 'UNSUPPORTED'
  return {
    userId,
    displayName: stringValue(value.displayName) ?? `Студент #${userId}`,
    status,
    symbol: stringValue(value.symbol),
    source: stringValue(value.source),
    excuseReason: stringValue(value.excuseReason),
    excuseType: stringValue(value.excuseType),
    comment: stringValue(value.comment),
    attachmentId: stringValue(value.attachmentId),
    attachmentName: stringValue(value.attachmentName),
    attachmentContentType: stringValue(value.attachmentContentType),
    attachmentSize: nonNegativeInteger(value.attachmentSize),
    editable: reportEditable,
  }
}

function normalizeLessonStatus(value: unknown): HeadmanJournalLessonStatus {
  const status = typeof value === 'string' ? value.trim().toUpperCase() : ''
  return status === 'PLANNED' || status === 'ACTIVE' || status === 'CLOSED' || status === 'CANCELLED' ? status : 'UNSUPPORTED'
}

function unwrapContent(value: unknown): Record<string, unknown> | null {
  if (!isRecord(value)) return null
  return isRecord(value.content) ? value.content : value
}

function embeddedItems(value: unknown, key: string): readonly unknown[] {
  if (!isRecord(value) || !isRecord(value._embedded)) return []
  const embedded = value._embedded
  if (Array.isArray(embedded[key])) return embedded[key]
  const first = Object.values(embedded).find(Array.isArray)
  return Array.isArray(first) ? first : []
}

function pageMetadata(value: unknown): { totalPages: number } | null {
  if (!isRecord(value) || !isRecord(value.page)) return null
  const totalPages = value.page.totalPages
  return typeof totalPages === 'number' && Number.isSafeInteger(totalPages) && totalPages >= 0
    ? { totalPages }
    : null
}

function nextPageHref(value: unknown): string | null {
  if (!isRecord(value) || !isRecord(value._links) || !isRecord(value._links.next)) return null
  const href = value._links.next.href
  return typeof href === 'string' && href.startsWith('/api/') ? href : null
}

function assertPositiveInteger(value: number, name: string): void {
  if (!Number.isSafeInteger(value) || value <= 0) throw new RangeError(`${name} must be a positive integer`)
}

function assertDate(value: string, name: string): void {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new RangeError(`${name} must be an ISO date`)
}

function positiveInteger(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? value : null
}

function integerValue(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) ? value : null
}

function nonNegativeInteger(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : null
}

function stringValue(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null
}

function booleanValue(value: unknown): boolean | null {
  return typeof value === 'boolean' ? value : null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function problemDetail(value: unknown): string | null {
  if (!isRecord(value)) return null
  return typeof value.detail === 'string' ? value.detail : typeof value.title === 'string' ? value.title : null
}

function markPayload(command: HeadmanJournalMarkCommand): Record<string, string> {
  if (command.status === 'PRESENT' || command.status === 'ABSENT') return { status: command.status }
  if (command.status !== 'EXCUSED') throw new RangeError('status недоступен для ручной отметки')
  if (!isHeadmanJournalExcuseType(command.excuseType)) {
    throw new RangeError('excuseType должен быть значением серверного enum')
  }
  const comment = optionalComment(command.comment)
  return {
    status: command.status,
    excuseType: command.excuseType,
    ...(comment === undefined ? {} : { comment }),
  }
}

function optionalComment(value: string | null | undefined): string | undefined {
  if (value === null || value === undefined || value.trim() === '') return undefined
  if (Array.from(value).length > 1000) throw new RangeError('comment не может быть длиннее 1000 символов')
  return value
}

function isHeadmanJournalExcuseType(value: string): value is HeadmanJournalExcuseType {
  return value === 'ILLNESS'
    || value === 'MEDICAL_EXAMINATION'
    || value === 'COMPETITION_PARTICIPATION'
    || value === 'FAMILY_CIRCUMSTANCES'
    || value === 'SUMMONS'
    || value === 'UNIVERSITY_ORDER'
    || value === 'EXEMPTION'
    || value === 'OTHER'
}

function normalizeMarkAck(value: unknown, fallbackLessonId: number, fallbackUserId: number): HeadmanJournalMarkAck {
  const record = unwrapContent(value)
  const status = typeof record?.status === 'string' ? record.status.toUpperCase() : ''
  if (status !== 'PRESENT' && status !== 'ABSENT' && status !== 'EXCUSED') {
    throw new Error('Сервер вернул неподдерживаемый ACK посещаемости')
  }
  return {
    status,
    lessonId: positiveInteger(record?.lessonId) ?? fallbackLessonId,
    userId: positiveInteger(record?.userId) ?? fallbackUserId,
    timestamp: stringValue(record?.timestamp),
  }
}

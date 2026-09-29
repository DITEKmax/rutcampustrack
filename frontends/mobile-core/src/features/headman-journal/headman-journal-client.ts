import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  isReportDownloadFormat,
  type ReportDownloadTicketRequest,
} from '../../shared/report-download-client'

export type HeadmanJournalLessonStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED' | 'UNSUPPORTED'
export type HeadmanLessonTransferState = 'PENDING' | 'COMPLETED' | 'ERROR'
export type HeadmanLessonTransferErrorCode =
  | 'TARGET_DATA_CONFLICT'
  | 'SOURCE_STATE_CONFLICT'
  | 'SCOPE_MISMATCH'
  | 'INVALID_SNAPSHOT'
  | 'DEPENDENCY_UNAVAILABLE'
  | 'ARCHIVED_SEMESTER'
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
  readonly occurrenceRevision: string | null
  readonly current: boolean
  readonly transferOperationId: string | null
  readonly transferState: HeadmanLessonTransferState | null
}

export interface HeadmanLessonTransferRequest {
  readonly targetDate: string
  readonly targetLessonNumber: number
  readonly targetStartTime: string | null
  readonly targetEndTime: string | null
  readonly targetRoom: string | null
  readonly expectedRevision: string
  readonly requestKey: string
}

export interface HeadmanLessonTransferResponse {
  readonly operationId: string
  readonly state: HeadmanLessonTransferState
  readonly occurrenceId: string
  readonly sourceLessonId: string
  readonly targetLessonId: string
  readonly targetDate: string
  readonly revision: string
  readonly retryable: boolean
  readonly errorCode: HeadmanLessonTransferErrorCode | null
}

export function createHeadmanLessonTransferRequest(
  targetDate: string,
  targetLessonNumber: number,
  expectedRevision: string,
  requestKey: string,
): HeadmanLessonTransferRequest {
  assertDate(targetDate, 'targetDate')
  if (!Number.isInteger(targetLessonNumber) || targetLessonNumber < 1 || targetLessonNumber > 8) {
    throw new RangeError('targetLessonNumber должен быть от 1 до 8')
  }
  assertPositiveDecimalString(expectedRevision, 'expectedRevision')
  assertUuid(requestKey, 'requestKey')
  return Object.freeze({
    targetDate,
    targetLessonNumber,
    targetStartTime: null,
    targetEndTime: null,
    targetRoom: null,
    expectedRevision,
    requestKey,
  })
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

export interface HeadmanWeeklyWeekOption {
  readonly weekOfSemester: number
  readonly isoWeek: number
  readonly label: string
  readonly weekStart: string
  readonly weekEnd: string
  readonly current: boolean
}

export interface HeadmanWeeklyExportFormat {
  readonly code: string
  readonly label: string
  readonly contentType: string
  readonly extension: string
}

export interface HeadmanWeeklyExportOptions {
  readonly semesterId: number
  readonly semesterName: string
  readonly semesterDateFrom: string
  readonly semesterDateTo: string
  readonly weeks: readonly HeadmanWeeklyWeekOption[]
  readonly formats: readonly HeadmanWeeklyExportFormat[]
}

export interface HeadmanWeeklyDownload {
  readonly blob: Blob
  readonly filename: string
}

export function toHeadmanWeeklyReportRequest(
  weeks: readonly HeadmanWeeklyWeekOption[],
  selectedWeekStarts: readonly string[],
  format: HeadmanWeeklyExportFormat,
): ReportDownloadTicketRequest {
  if (!isReportDownloadFormat(format.code)) {
    throw new RangeError('Сервер вернул неподдерживаемый формат недельного отчёта.')
  }
  const selected = new Set(selectedWeekStarts)
  const orderedWeeks = weeks.filter((week) => selected.has(week.weekStart))
  const firstWeek = orderedWeeks[0]
  if (!firstWeek) throw new RangeError('Выбери хотя бы одну неделю из доступных.')
  if (orderedWeeks.length === 1 && firstWeek.current) {
    return {
      kind: 'HEADMAN_WEEKLY_CURRENT',
      headmanWeeklyCurrent: {
        weekStart: firstWeek.weekStart,
        format: format.code,
      },
    }
  }
  return {
    kind: 'HEADMAN_WEEKLY_SELECTED',
    headmanWeeklySelected: {
      weekStarts: orderedWeeks.map((week) => week.weekStart),
      format: format.code,
    },
  }
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
      for (const status of ['PLANNED', 'ACTIVE', 'CLOSED', 'CANCELLED'] as const) {
        query.append('status', status)
      }
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

  async getWeeklyExportOptions(): Promise<HeadmanWeeklyExportOptions> {
    const value = await this.request<unknown>('/api/attendance/reports/headman-weekly/weeks')
    return normalizeWeeklyExportOptions(value)
  }

  async downloadWeeklyExport(
    weekStarts: readonly string[],
    format: HeadmanWeeklyExportFormat,
    signal?: AbortSignal,
  ): Promise<HeadmanWeeklyDownload> {
    if (weekStarts.length === 0) throw new RangeError('Выбери хотя бы одну неделю')
    if (!format.code.trim()) throw new RangeError('Выбери формат выгрузки')
    for (const weekStart of weekStarts) assertDate(weekStart, 'weekStart')
    const response = await this.response('/api/attendance/reports/headman-weekly/export', {
      method: 'POST',
      headers: { Accept: '*/*' },
      body: JSON.stringify({ weekStarts: [...weekStarts], format: format.code }),
      ...(signal ? { signal } : {}),
    })
    if (!response.ok) throw await this.apiError(response)
    const blob = await response.blob()
    this.options.assertCurrent?.()
    return {
      blob,
      filename: filenameFromContentDisposition(
        response.headers.get('Content-Disposition'),
        `zhurnal.${format.extension}`,
      ),
    }
  }

  async cancelLesson(lessonId: number, reason: string): Promise<HeadmanJournalLesson> {
    assertPositiveInteger(lessonId, 'lessonId')
    const normalizedReason = reason.trim()
    if (!normalizedReason) throw new RangeError('Причина отмены обязательна')
    if (Array.from(normalizedReason).length > 512) throw new RangeError('Причина отмены не может быть длиннее 512 символов')
    const value = await this.request<unknown>(`/api/schedule/lessons/${lessonId}/cancel`, {
      method: 'PATCH',
      body: JSON.stringify({ reason: normalizedReason }),
    })
    const lesson = normalizeLesson(value)
    if (!lesson) throw new Error('Сервер вернул неполную пару после отмены')
    return lesson
  }

  async restoreLesson(lessonId: number): Promise<HeadmanJournalLesson> {
    assertPositiveInteger(lessonId, 'lessonId')
    const value = await this.request<unknown>(`/api/schedule/lessons/${lessonId}/restore`, {
      method: 'PATCH',
    })
    const lesson = normalizeLesson(value)
    if (!lesson) throw new Error('Сервер вернул неполную пару после восстановления')
    return lesson
  }

  async transferLesson(lessonId: number, request: HeadmanLessonTransferRequest): Promise<HeadmanLessonTransferResponse> {
    assertPositiveInteger(lessonId, 'lessonId')
    const payload = snapshotHeadmanLessonTransferRequest(request)
    const response = await this.response(`/api/schedule/lessons/${lessonId}/transfer`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    if (response.status !== 200 && response.status !== 202) throw await this.apiError(response)
    const result = normalizeHeadmanLessonTransferResponse(await response.json())
    this.options.assertCurrent?.()
    if (result.sourceLessonId !== String(lessonId)) {
      throw new Error('Сервер вернул операцию переноса для другой пары.')
    }
    if (result.targetDate !== payload.targetDate) {
      throw new Error('Сервер вернул другую дату целевого переноса.')
    }
    if ((response.status === 200 && result.state !== 'COMPLETED')
      || (response.status === 202 && result.state !== 'PENDING')) {
      throw new Error('Сервер вернул состояние переноса, не соответствующее HTTP-ответу.')
    }
    return result
  }

  async getLessonTransfer(operationId: string): Promise<HeadmanLessonTransferResponse> {
    assertUuid(operationId, 'operationId')
    const result = normalizeHeadmanLessonTransferResponse(
      await this.request<unknown>(`/api/schedule/lesson-transfers/${encodeURIComponent(operationId)}`),
    )
    if (result.operationId !== operationId) {
      throw new Error('Сервер вернул состояние другой операции переноса.')
    }
    return result
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
  const occurrenceRevision = positiveDecimalValue(record.occurrenceRevision)
  if (record.occurrenceRevision !== null && record.occurrenceRevision !== undefined && occurrenceRevision === null) {
    throw new Error('Сервер вернул некорректную revision пары в расписании.')
  }
  const transferOperationId = nullableUuid(record.transferOperationId, 'transferOperationId')
  const transferState = nullableTransferState(record.transferState)
  if ((transferOperationId === null) !== (transferState === null)) {
    throw new Error('Сервер вернул неполный статус операции переноса пары.')
  }
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
    occurrenceRevision,
    current: booleanValue(record.current) ?? false,
    transferOperationId,
    transferState,
  }
}

function normalizeHeadmanLessonTransferResponse(value: unknown): HeadmanLessonTransferResponse {
  const record = unwrapContent(value)
  if (!record) throw new Error('Сервер вернул пустой ответ о переносе пары.')
  const operationId = nullableUuid(record.operationId, 'operationId')
  const state = nullableTransferState(record.state)
  const occurrenceId = positiveDecimalValue(record.occurrenceId)
  const sourceLessonId = positiveDecimalValue(record.sourceLessonId)
  const targetLessonId = positiveDecimalValue(record.targetLessonId)
  const targetDate = isoDateValue(record.targetDate)
  const revision = positiveDecimalValue(record.revision)
  const retryable = booleanValue(record.retryable)
  const errorCode = nullableTransferErrorCode(record.errorCode)
  if (operationId === null || state === null || occurrenceId === null || sourceLessonId === null
    || targetLessonId === null || targetDate === null || revision === null || retryable === null || errorCode === undefined) {
    throw new Error('Сервер вернул неполный ответ о переносе пары.')
  }
  if ((state === 'ERROR') !== (errorCode !== null) || retryable !== (state === 'PENDING')) {
    throw new Error('Сервер вернул противоречивое состояние операции переноса.')
  }
  return { operationId, state, occurrenceId, sourceLessonId, targetLessonId, targetDate, revision, retryable, errorCode }
}

function snapshotHeadmanLessonTransferRequest(value: HeadmanLessonTransferRequest): HeadmanLessonTransferRequest {
  if (!value || typeof value !== 'object') throw new TypeError('request обязателен')
  assertDate(value.targetDate, 'targetDate')
  if (!Number.isInteger(value.targetLessonNumber) || value.targetLessonNumber < 1 || value.targetLessonNumber > 8) {
    throw new RangeError('targetLessonNumber должен быть от 1 до 8')
  }
  assertPositiveDecimalString(value.expectedRevision, 'expectedRevision')
  assertUuid(value.requestKey, 'requestKey')
  const hasStart = value.targetStartTime !== null
  const hasEnd = value.targetEndTime !== null
  if (hasStart !== hasEnd || (hasStart && (!isTimeValue(value.targetStartTime) || !isTimeValue(value.targetEndTime)))) {
    throw new RangeError('targetStartTime и targetEndTime должны задаваться вместе корректным временем')
  }
  if (value.targetRoom !== null && (typeof value.targetRoom !== 'string' || Array.from(value.targetRoom).length > 64)) {
    throw new RangeError('targetRoom не может быть длиннее 64 символов')
  }
  return Object.freeze({
    targetDate: value.targetDate,
    targetLessonNumber: value.targetLessonNumber,
    targetStartTime: value.targetStartTime,
    targetEndTime: value.targetEndTime,
    targetRoom: value.targetRoom,
    expectedRevision: value.expectedRevision,
    requestKey: value.requestKey,
  })
}

function normalizeWeeklyExportOptions(value: unknown): HeadmanWeeklyExportOptions {
  const record = unwrapContent(value)
  if (!record) throw new Error('Сервер вернул неполный список недель для выгрузки.')
  const semesterId = positiveInteger(record.semesterId)
  const semesterName = stringValue(record.semesterName)
  const semesterDateFrom = isoDateValue(record.semesterDateFrom)
  const semesterDateTo = isoDateValue(record.semesterDateTo)
  if (semesterId === null || semesterName === null || semesterDateFrom === null || semesterDateTo === null) {
    throw new Error('Сервер вернул неполный контекст семестра.')
  }
  if (!Array.isArray(record.weeks) || !Array.isArray(record.formats)) {
    throw new Error('Сервер не вернул список недель и форматов.')
  }
  const weeks = record.weeks.map(normalizeWeeklyWeek).filter((item): item is HeadmanWeeklyWeekOption => item !== null)
  const formats = record.formats.map(normalizeWeeklyFormat).filter((item): item is HeadmanWeeklyExportFormat => item !== null)
  if (weeks.length !== record.weeks.length || formats.length !== record.formats.length || formats.length === 0) {
    throw new Error('Сервер вернул некорректный список недель или форматов.')
  }
  if (new Set(weeks.map((week) => week.weekStart)).size !== weeks.length
    || new Set(formats.map((format) => format.code)).size !== formats.length) {
    throw new Error('Сервер вернул повторяющиеся недели или форматы.')
  }
  return {
    semesterId,
    semesterName,
    semesterDateFrom,
    semesterDateTo,
    weeks,
    formats,
  }
}

function normalizeWeeklyWeek(value: unknown): HeadmanWeeklyWeekOption | null {
  if (!isRecord(value)) return null
  const weekOfSemester = integerValue(value.weekOfSemester)
  const isoWeek = integerValue(value.isoWeek)
  const label = stringValue(value.label)
  const weekStart = isoDateValue(value.weekStart)
  const weekEnd = isoDateValue(value.weekEnd)
  const current = booleanValue(value.current)
  if (weekOfSemester === null || weekOfSemester <= 0 || isoWeek === null || isoWeek <= 0
    || label === null || weekStart === null || weekEnd === null || current === null || weekStart > weekEnd) return null
  return { weekOfSemester, isoWeek, label, weekStart, weekEnd, current }
}

function normalizeWeeklyFormat(value: unknown): HeadmanWeeklyExportFormat | null {
  if (!isRecord(value)) return null
  const code = stringValue(value.code)
  const label = stringValue(value.label)
  const contentType = stringValue(value.contentType)
  const extension = stringValue(value.extension)
  if (code === null || label === null || contentType === null || extension === null
    || !/^[a-z0-9-]+$/.test(code) || !/^[a-z0-9]+$/.test(extension)) return null
  return { code, label, contentType, extension }
}

function isoDateValue(value: unknown): string | null {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null
}

function filenameFromContentDisposition(value: string | null, fallback: string): string {
  if (!value) return fallback
  const utf8 = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(value)?.[1]
  if (utf8) {
    try {
      return decodeURIComponent(utf8.trim().replace(/^"|"$/g, ''))
    } catch {
      // Fall back to the quoted filename if a gateway malformed RFC 5987 encoding.
    }
  }
  const quoted = /filename\s*=\s*"([^"]+)"/i.exec(value)?.[1]
  const unquoted = /filename\s*=\s*([^;]+)/i.exec(value)?.[1]
  return (quoted ?? unquoted)?.trim() || fallback
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

function assertPositiveDecimalString(value: string, name: string): void {
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) {
    throw new RangeError(`${name} must be a positive decimal string`)
  }
}

function assertUuid(value: string, name: string): void {
  if (typeof value !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
    throw new RangeError(`${name} must be a UUID`)
  }
}

function positiveDecimalValue(value: unknown): string | null {
  if (typeof value === 'string' && /^[1-9][0-9]*$/.test(value)) return value
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? String(value) : null
}

function nullableUuid(value: unknown, name: string): string | null {
  if (value === null || value === undefined) return null
  if (typeof value !== 'string') throw new Error(`Сервер вернул некорректный ${name}.`)
  try {
    assertUuid(value, name)
  } catch {
    throw new Error(`Сервер вернул некорректный ${name}.`)
  }
  return value.toLowerCase()
}

function nullableTransferState(value: unknown): HeadmanLessonTransferState | null {
  if (value === null || value === undefined || value === '') return null
  if (value === 'PENDING' || value === 'COMPLETED' || value === 'ERROR') return value
  throw new Error('Сервер вернул неподдерживаемое состояние переноса пары.')
}

function nullableTransferErrorCode(value: unknown): HeadmanLessonTransferErrorCode | null | undefined {
  if (value === null || value === undefined || value === '') return null
  if (value === 'TARGET_DATA_CONFLICT' || value === 'SOURCE_STATE_CONFLICT' || value === 'SCOPE_MISMATCH'
    || value === 'INVALID_SNAPSHOT' || value === 'DEPENDENCY_UNAVAILABLE' || value === 'ARCHIVED_SEMESTER') {
    return value
  }
  return undefined
}

function isTimeValue(value: unknown): value is string {
  return typeof value === 'string' && /^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,9})?)?$/.test(value)
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

import { StaleSessionGenerationError } from '../../shared/session-owner'
import type {
  ReportDownloadFormat,
  ReportDownloadTicketRequest,
  TeacherStatsReportSelector,
} from '../../shared/report-download-client'

export interface TeacherAssignment {
  readonly id: number
  readonly teacherId: number
  readonly groupId: number
  readonly groupName: string
  readonly subjectId: number
  readonly subjectName: string
  readonly semesterId: number
  readonly lessonType: string
  readonly validFrom: string
  readonly validUntilExclusive: string | null
}

export interface TeacherDayLesson {
  readonly id: number
  readonly assignmentId: number
  readonly groupId: number
  readonly groupName: string
  readonly subjectId: number
  readonly subjectName: string
  readonly semesterId: number
  readonly lessonType: string
  readonly date: string
  readonly lessonNumber: number
  readonly startsAt: string | null
  readonly endsAt: string | null
  readonly room: string | null
  readonly status: 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED' | 'UNKNOWN'
  readonly oneOff: boolean
  readonly moved: boolean
  readonly cancelled: boolean
}

export interface TeacherDayResponse {
  readonly semesterId: number
  readonly date: string
  readonly lessons: readonly TeacherDayLesson[]
  readonly serverNow: string
}

export interface TeacherSemester {
  readonly id: number
  readonly name: string
  readonly dateFrom: string
  readonly dateTo: string
}

export interface TeacherRosterEntry {
  readonly studentId: number
  readonly displayName: string
  readonly status: string | null
  readonly symbol: string | null
  readonly source: string | null
  readonly recordPresent: boolean
  readonly pendingTicket: boolean
  readonly autoAbsent: boolean
  readonly ticketId: string | null
  readonly excuseType: string | null
  readonly excuseReason: string | null
  readonly excuseComment: string | null
  readonly attachmentId: string | null
  readonly attachmentName: string | null
  readonly attachmentContentType: string | null
  readonly attachmentSize: number | null
}

export interface TeacherLessonResponse {
  readonly lesson: TeacherDayLesson
  readonly roster: readonly TeacherRosterEntry[]
  readonly serverNow: string
}

export interface TeacherJournalCell {
  readonly lessonId: number
  readonly status: string | null
  readonly symbol: string | null
  readonly source: string | null
  readonly recordPresent: boolean
  readonly pendingTicket: boolean
  readonly autoAbsent: boolean
  readonly ticketId: string | null
  readonly excuseType: string | null
  readonly excuseReason: string | null
}

export interface TeacherJournalStudent {
  readonly studentId: number
  readonly displayName: string
  readonly cells: readonly TeacherJournalCell[]
}

export interface TeacherJournalQuery {
  readonly semesterId: number
  readonly groupId: number
  readonly subjectId: number
  readonly lessonType: string
  readonly page?: number
  readonly pageSize?: number
}

export function toTeacherJournalReportRequest(
  query: TeacherJournalQuery,
  format: TeacherExportFormatCode,
): ReportDownloadTicketRequest {
  return {
    kind: 'TEACHER_JOURNAL',
    teacherJournal: {
      semesterId: query.semesterId,
      groupId: query.groupId,
      subjectId: query.subjectId,
      lessonTypes: [query.lessonType],
      format,
    },
  }
}

export interface TeacherJournalResponse {
  readonly lessons: readonly TeacherDayLesson[]
  readonly students: readonly TeacherJournalStudent[]
  readonly serverNow: string
  readonly page: number
  readonly pageSize: number
  readonly totalLessons: number
  readonly hasMore: boolean
}

export type TeacherExportFormatCode = ReportDownloadFormat

const TEACHER_EXPORT_CONTENT_TYPES: Record<TeacherExportFormatCode, string> = {
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  pdf: 'application/pdf',
  png: 'application/zip',
  html: 'text/html',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
}

export interface TeacherExportFormat {
  readonly code: TeacherExportFormatCode
  readonly label: string
  readonly contentType: string
  readonly extension: string
}

export interface TeacherJournalExportQuery {
  readonly semesterId: number
  readonly groupId: number
  readonly subjectId: number
  readonly lessonTypes: readonly string[]
  readonly format: TeacherExportFormatCode
}

export interface TeacherJournalExportFile {
  readonly blob: Blob
  readonly filename: string
  readonly contentType: string
}

export interface TeacherStatsExportFile {
  readonly blob: Blob
  readonly filename: string
  readonly contentType: string
}

export type TeacherStatsScope = 'students' | 'groups'

export interface TeacherStatsSort {
  readonly column: string
  readonly descending?: boolean
}

export interface TeacherStatsFilter {
  readonly column: string
  readonly contains?: string
  readonly minPercent?: number
  readonly maxPercent?: number
  readonly minValue?: number
  readonly maxValue?: number
}

export interface TeacherStatsQuery {
  readonly semesterId: number
  readonly scope: TeacherStatsScope
  readonly groupId?: number | null
  readonly subjectId?: number | null
  readonly lessonTypes?: readonly string[]
  readonly sorts?: readonly TeacherStatsSort[]
  readonly filters?: readonly TeacherStatsFilter[]
}

export function toTeacherStatsReportSelector(
  query: TeacherStatsQuery,
  format: TeacherExportFormatCode,
): TeacherStatsReportSelector {
  const params = createTeacherStatsQueryParams(query)
  const lessonTypes = params.getAll('lessonType')
  const sorts = params.getAll('sort')
  const filters = params.getAll('filter')
  return {
    semesterId: query.semesterId,
    scope: query.scope,
    ...(query.groupId != null ? { groupId: query.groupId } : {}),
    ...(query.subjectId != null ? { subjectId: query.subjectId } : {}),
    ...(lessonTypes.length > 0 ? { lessonTypes } : {}),
    ...(sorts.length > 0 ? { sorts } : {}),
    ...(filters.length > 0 ? { filters } : {}),
    format,
  }
}

export interface TeacherStatsMetric {
  readonly numerator: number
  readonly denominator: number
  readonly percent: number
}

export interface TeacherStatsSubjectOption {
  readonly groupId: number
  readonly subjectId: number
  readonly subjectName: string
  readonly lessonTypes: readonly string[]
}

export interface TeacherStatsStudent {
  readonly studentId: number
  readonly displayName: string
  readonly present: TeacherStatsMetric
  readonly presentOrExcused: TeacherStatsMetric
  readonly excused: TeacherStatsMetric
  readonly absent: TeacherStatsMetric
}

export interface TeacherStatsGroup {
  readonly groupId: number
  readonly groupName: string
  readonly lessonsCount: number
  readonly present: TeacherStatsMetric
  readonly presentOrExcused: TeacherStatsMetric
  readonly excused: TeacherStatsMetric
  readonly absent: TeacherStatsMetric
}

export interface TeacherStatsResponse {
  readonly scope: TeacherStatsScope
  readonly semesterId: number
  readonly periodFrom: string | null
  readonly periodTo: string | null
  readonly lessonsCount: number
  readonly students: readonly TeacherStatsStudent[]
  readonly groups: readonly TeacherStatsGroup[]
  readonly subjectOptions: readonly TeacherStatsSubjectOption[]
  readonly serverNow: string
}

export interface TeacherExcuseAttachment {
  readonly id: string
  readonly fileName: string
  readonly contentType: string
  readonly size: number
  readonly uploadedAt: string | null
  readonly expiresAt: string | null
}

export interface TeacherExcuseResponse {
  readonly id: string
  readonly kind: string
  readonly status: string
  readonly studentId: number
  readonly studentName: string
  readonly groupId: number
  readonly groupName: string | null
  readonly excuseType: string | null
  readonly reason: string | null
  readonly comment: string | null
  readonly createdAt: string | null
  readonly decisionBy: number | null
  readonly decisionAt: string | null
  readonly decisionComment: string | null
  readonly lessons: readonly TeacherDayLesson[]
  readonly attachments: readonly TeacherExcuseAttachment[]
  readonly serverNow: string
}

export interface TeacherApiOptions {
  readonly accessToken: () => string | null
  readonly onUnauthorized?: () => Promise<void>
  readonly assertCurrent?: () => void
  readonly fetcher?: typeof fetch
}

export class TeacherApiError extends Error {
  constructor(readonly response: Response, readonly problem: unknown) {
    super(problemDetail(problem) ?? `HTTP ${response.status}`)
    this.name = 'TeacherApiError'
  }
}

export class TeacherApi {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: TeacherApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  semester(): Promise<TeacherSemester> {
    return this.request<unknown>('/api/v1/teacher/semester').then(normalizeSemester)
  }

  assignments(semesterId: number, dateFrom: string, dateTo: string): Promise<readonly TeacherAssignment[]> {
    assertPositiveInteger(semesterId, 'semesterId')
    assertDate(dateFrom, 'dateFrom')
    assertDate(dateTo, 'dateTo')
    if (dateFrom > dateTo) throw new RangeError('dateFrom must be before or equal to dateTo')
    const query = new URLSearchParams({ semesterId: String(semesterId), dateFrom, dateTo })
    return this.request<unknown>(`/api/v1/teacher/assignments?${query.toString()}`)
      .then((value) => arrayValue(value).map(normalizeAssignment))
  }

  day(semesterId: number, date: string): Promise<TeacherDayResponse> {
    assertPositiveInteger(semesterId, 'semesterId')
    assertDate(date, 'date')
    const query = new URLSearchParams({ semesterId: String(semesterId), date })
    return this.request<unknown>(`/api/v1/teacher/day?${query.toString()}`).then(normalizeDay)
  }

  lesson(lessonId: number): Promise<TeacherLessonResponse> {
    assertPositiveInteger(lessonId, 'lessonId')
    return this.request<unknown>(`/api/v1/teacher/lessons/${lessonId}`).then((value) => normalizeLesson(value))
  }

  journal(query: TeacherJournalQuery): Promise<TeacherJournalResponse> {
    assertPositiveInteger(query.semesterId, 'semesterId')
    assertPositiveInteger(query.groupId, 'groupId')
    assertPositiveInteger(query.subjectId, 'subjectId')
    assertText(query.lessonType, 'lessonType')
    const page = query.page ?? 0
    const pageSize = query.pageSize ?? 100
    assertNonNegativeInteger(page, 'page')
    assertPageSize(pageSize)
    const params = new URLSearchParams({
      semesterId: String(query.semesterId),
      groupId: String(query.groupId),
      subjectId: String(query.subjectId),
      lessonType: query.lessonType,
      page: String(page),
      pageSize: String(pageSize),
    })
    return this.request<unknown>(`/api/v1/teacher/journal?${params.toString()}`)
      .then(normalizeJournal)
  }

  journalExportFormats(): Promise<readonly TeacherExportFormat[]> {
    return this.request<unknown>('/api/v1/teacher/journal/export/formats')
      .then((value) => {
        const record = requiredRecord(value)
        const formats = arrayValue(record.formats).map(normalizeExportFormat)
        if (formats.length !== 5
          || new Set(formats.map((format) => format.code)).size !== 5
          || !(['docx', 'pdf', 'png', 'html', 'xlsx'] as const).every((code) => formats.some((format) => format.code === code))) {
          throw new Error('Сервер вернул неполный список форматов журнала.')
        }
        return formats
      })
  }

  async exportJournal(query: TeacherJournalExportQuery): Promise<TeacherJournalExportFile> {
    assertPositiveInteger(query.semesterId, 'semesterId')
    assertPositiveInteger(query.groupId, 'groupId')
    assertPositiveInteger(query.subjectId, 'subjectId')
    if (query.lessonTypes.length < 1 || query.lessonTypes.length > 3) {
      throw new RangeError('lessonTypes must contain one to three values')
    }
    const params = new URLSearchParams({
      semesterId: String(query.semesterId),
      groupId: String(query.groupId),
      subjectId: String(query.subjectId),
      format: query.format,
    })
    for (const lessonType of query.lessonTypes) {
      assertText(lessonType, 'lessonType')
      params.append('lessonType', lessonType)
    }
    const response = await this.response(
      `/api/v1/teacher/journal/export?${params.toString()}`,
      { headers: { Accept: 'application/octet-stream' } },
    )
    if (!response.ok) throw await this.apiError(response)
    const blob = await response.blob()
    this.options.assertCurrent?.()
    if (blob.size === 0) throw new Error('Сервер вернул пустой файл журнала.')
    const contentType = response.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase() ?? ''
    const filename = downloadFilename(response.headers.get('Content-Disposition'))
    const expectedExtension = query.format === 'png' ? 'zip' : query.format
    if (!filename || !filename.toLowerCase().endsWith(`.${expectedExtension}`)
      || contentType !== TEACHER_EXPORT_CONTENT_TYPES[query.format]) {
      throw new Error('Сервер вернул некорректные метаданные файла журнала.')
    }
    return { blob, filename, contentType }
  }

  stats(query: TeacherStatsQuery): Promise<TeacherStatsResponse> {
    const params = this.statsQueryParams(query)
    return this.request<unknown>(`/api/v1/teacher/stats?${params.toString()}`).then(normalizeStats)
  }

  statsExportFormats(): Promise<readonly TeacherExportFormat[]> {
    return this.request<unknown>('/api/v1/teacher/stats/export/formats')
      .then((value) => {
        const record = requiredRecord(value)
        const formats = arrayValue(record.formats).map((item) => normalizeStatsExportFormat(item))
        if (formats.length !== 5
          || new Set(formats.map((format) => format.code)).size !== 5
          || !(['docx', 'pdf', 'png', 'html', 'xlsx'] as const).every((code) => formats.some((format) => format.code === code))) {
          throw new Error('Сервер вернул неполный список форматов статистики.')
        }
        return formats
      })
  }

  async exportStats(query: TeacherStatsQuery, format: TeacherExportFormatCode): Promise<TeacherStatsExportFile> {
    const params = this.statsQueryParams(query)
    if (!(['docx', 'pdf', 'png', 'html', 'xlsx'] as const).includes(format)) {
      throw new RangeError('format is invalid')
    }
    params.set('format', format)
    const response = await this.response(
      `/api/v1/teacher/stats/export?${params.toString()}`,
      { headers: { Accept: 'application/octet-stream' } },
    )
    if (!response.ok) throw await this.apiError(response)
    const blob = await response.blob()
    this.options.assertCurrent?.()
    if (blob.size === 0) throw new Error('Сервер вернул пустой файл статистики.')
    const contentType = response.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase() ?? ''
    const filename = downloadFilename(response.headers.get('Content-Disposition'))
    const expectedExtension = format === 'png' ? 'zip' : format
    if (!filename || !filename.toLowerCase().endsWith(`.${expectedExtension}`)
      || contentType !== TEACHER_EXPORT_CONTENT_TYPES[format]) {
      throw new Error('Сервер вернул некорректные метаданные файла статистики.')
    }
    return { blob, filename, contentType }
  }

  private statsQueryParams(query: TeacherStatsQuery): URLSearchParams {
    return createTeacherStatsQueryParams(query)
  }

  excuse(requestId: string): Promise<TeacherExcuseResponse> {
    assertText(requestId, 'requestId')
    return this.request<unknown>(`/api/v1/teacher/excuses/${encodeURIComponent(requestId)}`)
      .then(normalizeExcuse)
  }

  async downloadAttachment(requestId: string, attachmentId: string): Promise<Blob> {
    assertText(requestId, 'requestId')
    assertText(attachmentId, 'attachmentId')
    const response = await this.response(
      `/api/v1/teacher/excuses/${encodeURIComponent(requestId)}/attachments/${encodeURIComponent(attachmentId)}`,
      { headers: { Accept: 'application/octet-stream' } },
    )
    if (!response.ok) throw await this.apiError(response)
    const blob = await response.blob()
    this.options.assertCurrent?.()
    return blob
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await this.response(path, init, retried)
    if (!response.ok) throw await this.apiError(response)
    const value = response.status === 204 ? undefined as T : await response.json() as T
    this.options.assertCurrent?.()
    return value
  }

  private async response(path: string, init?: RequestInit, retried = false): Promise<Response> {
    this.options.assertCurrent?.()
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    this.options.assertCurrent?.()
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      return this.response(path, init, true)
    }
    return response
  }

  private async apiError(response: Response): Promise<TeacherApiError> {
    let problem: unknown = null
    try { problem = await response.json() } catch { /* Preserve status. */ }
    this.options.assertCurrent?.()
    return new TeacherApiError(response, problem)
  }
}

function createTeacherStatsQueryParams(query: TeacherStatsQuery): URLSearchParams {
  assertPositiveInteger(query.semesterId, 'semesterId')
  if (query.scope !== 'students' && query.scope !== 'groups') throw new RangeError('scope is invalid')
  if (query.scope === 'students') {
    assertPositiveInteger(query.groupId ?? 0, 'groupId')
    assertPositiveInteger(query.subjectId ?? 0, 'subjectId')
  } else if (query.groupId != null || query.subjectId != null) {
    throw new RangeError('group scope cannot select group or subject')
  }
  const params = new URLSearchParams({
    semesterId: String(query.semesterId),
    scope: query.scope,
  })
  if (query.groupId != null) params.set('groupId', String(query.groupId))
  if (query.subjectId != null) params.set('subjectId', String(query.subjectId))
  for (const lessonType of query.lessonTypes ?? []) {
    assertText(lessonType, 'lessonType')
    params.append('lessonType', lessonType)
  }
  for (const sort of query.sorts ?? []) {
    assertText(sort.column, 'sort.column')
    params.append('sort', `${sort.descending ? '-' : ''}${sort.column}`)
  }
  for (const filter of query.filters ?? []) {
    assertText(filter.column, 'filter.column')
    if (filter.contains != null) params.append('filter', `${filter.column}~${filter.contains}`)
    if (filter.minPercent != null) params.append('filter', `${filter.column}>=${filter.minPercent}`)
    if (filter.maxPercent != null) params.append('filter', `${filter.column}<=${filter.maxPercent}`)
    if (filter.minValue != null) params.append('filter', `${filter.column}>=${filter.minValue}`)
    if (filter.maxValue != null) params.append('filter', `${filter.column}<=${filter.maxValue}`)
  }
  return params
}

export interface TeacherApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export function createGenerationBoundTeacherApi(
  owner: TeacherApiGenerationOwner,
  fetcher?: typeof fetch,
): TeacherApi {
  const generation = owner.currentGeneration()
  const assertCurrent = (): void => {
    if (generation !== owner.currentGeneration()) throw new StaleSessionGenerationError()
  }
  return new TeacherApi({
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
    fetcher: fetcher ?? ((input: RequestInfo | URL, init?: RequestInit) => globalThis.fetch(input, init)),
  })
}

function normalizeAssignment(value: unknown): TeacherAssignment {
  const record = requiredRecord(value)
  return {
    id: positiveInteger(record.id, 'assignment.id'),
    teacherId: positiveInteger(record.teacherId, 'assignment.teacherId'),
    groupId: positiveInteger(record.groupId, 'assignment.groupId'),
    groupName: requiredString(record.groupName, 'assignment.groupName'),
    subjectId: positiveInteger(record.subjectId, 'assignment.subjectId'),
    subjectName: requiredString(record.subjectName, 'assignment.subjectName'),
    semesterId: positiveInteger(record.semesterId, 'assignment.semesterId'),
    lessonType: requiredString(record.lessonType, 'assignment.lessonType'),
    validFrom: requiredDate(record.validFrom, 'assignment.validFrom'),
    validUntilExclusive: nullableDate(record.validUntilExclusive, 'assignment.validUntilExclusive'),
  }
}

function normalizeExportFormat(value: unknown): TeacherExportFormat {
  const record = requiredRecord(value)
  const code = requiredString(record.code, 'exportFormat.code')
  if (!(['docx', 'pdf', 'png', 'html', 'xlsx'] as string[]).includes(code)) {
    throw new Error('Сервер вернул неизвестный формат журнала.')
  }
  const extension = requiredString(record.extension, 'exportFormat.extension')
  const contentType = requiredString(record.contentType, 'exportFormat.contentType')
  const expectedExtension = code === 'png' ? 'zip' : code
  if (extension !== expectedExtension
    || contentType.split(';', 1)[0]?.trim().toLowerCase() !== TEACHER_EXPORT_CONTENT_TYPES[code as TeacherExportFormatCode]) {
    throw new Error('Сервер вернул несовместимые метаданные формата журнала.')
  }
  return {
    code: code as TeacherExportFormatCode,
    label: requiredString(record.label, 'exportFormat.label'),
    contentType,
    extension,
  }
}

function normalizeStatsExportFormat(value: unknown): TeacherExportFormat {
  const record = requiredRecord(value)
  const code = requiredString(record.code, 'statsExportFormat.code')
  if (!(['docx', 'pdf', 'png', 'html', 'xlsx'] as string[]).includes(code)) {
    throw new Error('Сервер вернул неизвестный формат статистики.')
  }
  const extension = requiredString(record.extension, 'statsExportFormat.extension')
  const contentType = requiredString(record.contentType, 'statsExportFormat.contentType')
  const expectedExtension = code === 'png' ? 'zip' : code
  if (extension !== expectedExtension
    || contentType.split(';', 1)[0]?.trim().toLowerCase() !== TEACHER_EXPORT_CONTENT_TYPES[code as TeacherExportFormatCode]) {
    throw new Error('Сервер вернул несовместимые метаданные формата статистики.')
  }
  return {
    code: code as TeacherExportFormatCode,
    label: requiredString(record.label, 'statsExportFormat.label'),
    contentType,
    extension,
  }
}

function downloadFilename(contentDisposition: string | null): string | null {
  if (!contentDisposition) return null
  const encoded = contentDisposition.match(/filename\*\s*=\s*UTF-8''([^;]+)/i)?.[1]
  const plain = contentDisposition.match(/filename\s*=\s*"?([^";]+)"?/i)?.[1]
  let filename: string
  try {
    filename = encoded ? decodeURIComponent(encoded.trim()) : (plain?.trim() ?? '')
  } catch {
    return null
  }
  return /^[A-Za-z0-9._-]+$/.test(filename) ? filename : null
}

function normalizeSemester(value: unknown): TeacherSemester {
  const record = requiredRecord(value)
  return {
    id: positiveInteger(record.id, 'semester.id'),
    name: requiredString(record.name, 'semester.name'),
    dateFrom: requiredDate(record.dateFrom, 'semester.dateFrom'),
    dateTo: requiredDate(record.dateTo, 'semester.dateTo'),
  }
}

function normalizeDay(value: unknown): TeacherDayResponse {
  const record = requiredRecord(value)
  const lessons = arrayValue(record.lessons).map(normalizeDayLesson)
  return {
    semesterId: positiveInteger(record.semesterId, 'day.semesterId'),
    date: requiredDate(record.date, 'day.date'),
    lessons: lessons.sort(compareLessons),
    serverNow: requiredString(record.serverNow, 'day.serverNow'),
  }
}

function normalizeLesson(value: unknown): TeacherLessonResponse {
  const record = requiredRecord(value)
  return {
    lesson: normalizeDayLesson(record.lesson),
    roster: arrayValue(record.roster).map(normalizeRoster).sort((left, right) => left.displayName.localeCompare(right.displayName, 'ru')),
    serverNow: requiredString(record.serverNow, 'lesson.serverNow'),
  }
}

function normalizeJournal(value: unknown): TeacherJournalResponse {
  const record = requiredRecord(value)
  return {
    lessons: arrayValue(record.lessons).map(normalizeDayLesson).sort(compareLessons),
    students: arrayValue(record.students).map(normalizeStudent).sort((left, right) => left.displayName.localeCompare(right.displayName, 'ru')),
    serverNow: requiredString(record.serverNow, 'journal.serverNow'),
    page: nonNegativeInteger(record.page, 'journal.page'),
    pageSize: pageSizeValue(record.pageSize, 'journal.pageSize'),
    totalLessons: nonNegativeInteger(record.totalLessons, 'journal.totalLessons'),
    hasMore: requiredBoolean(record.hasMore, 'journal.hasMore'),
  }
}

function normalizeStats(value: unknown): TeacherStatsResponse {
  const record = requiredRecord(value)
  const scope = requiredString(record.scope, 'stats.scope')
  if (scope !== 'students' && scope !== 'groups') throw new Error('Сервер вернул неизвестный разрез статистики.')
  return {
    scope,
    semesterId: positiveInteger(record.semesterId, 'stats.semesterId'),
    periodFrom: nullableDate(record.periodFrom, 'stats.periodFrom'),
    periodTo: nullableDate(record.periodTo, 'stats.periodTo'),
    lessonsCount: nonNegativeInteger(record.lessonsCount, 'stats.lessonsCount'),
    students: arrayValue(record.students).map(normalizeStatsStudent),
    groups: arrayValue(record.groups).map(normalizeStatsGroup),
    subjectOptions: arrayValue(record.subjectOptions).map(normalizeStatsSubjectOption),
    serverNow: requiredString(record.serverNow, 'stats.serverNow'),
  }
}

function normalizeStatsSubjectOption(value: unknown): TeacherStatsSubjectOption {
  const record = requiredRecord(value)
  return {
    groupId: positiveInteger(record.groupId, 'stats.subjectOption.groupId'),
    subjectId: positiveInteger(record.subjectId, 'stats.subjectOption.subjectId'),
    subjectName: requiredString(record.subjectName, 'stats.subjectOption.subjectName'),
    lessonTypes: arrayValue(record.lessonTypes)
      .map((lessonType) => requiredString(lessonType, 'stats.subjectOption.lessonType')),
  }
}

function normalizeStatsStudent(value: unknown): TeacherStatsStudent {
  const record = requiredRecord(value)
  return {
    studentId: positiveInteger(record.studentId, 'stats.studentId'),
    displayName: requiredString(record.displayName, 'stats.displayName'),
    present: normalizeStatsMetric(record.present),
    presentOrExcused: normalizeStatsMetric(record.presentOrExcused),
    excused: normalizeStatsMetric(record.excused),
    absent: normalizeStatsMetric(record.absent),
  }
}

function normalizeStatsGroup(value: unknown): TeacherStatsGroup {
  const record = requiredRecord(value)
  return {
    groupId: positiveInteger(record.groupId, 'stats.groupId'),
    groupName: requiredString(record.groupName, 'stats.groupName'),
    lessonsCount: nonNegativeInteger(record.lessonsCount, 'stats.lessonsCount'),
    present: normalizeStatsMetric(record.present),
    presentOrExcused: normalizeStatsMetric(record.presentOrExcused),
    excused: normalizeStatsMetric(record.excused),
    absent: normalizeStatsMetric(record.absent),
  }
}

function normalizeStatsMetric(value: unknown): TeacherStatsMetric {
  const record = requiredRecord(value)
  const numerator = nonNegativeInteger(record.numerator, 'stats.metric.numerator')
  const denominator = nonNegativeInteger(record.denominator, 'stats.metric.denominator')
  const percent = typeof record.percent === 'number' && Number.isFinite(record.percent)
    ? record.percent : Number.NaN
  if (!Number.isFinite(percent) || percent < 0 || percent > 100 || numerator > denominator) {
    throw new Error('Сервер вернул некорректную метрику статистики.')
  }
  return { numerator, denominator, percent }
}

function normalizeExcuse(value: unknown): TeacherExcuseResponse {
  const record = requiredRecord(value)
  return {
    id: requiredString(record.id, 'excuse.id'),
    kind: requiredString(record.kind, 'excuse.kind'),
    status: requiredString(record.status, 'excuse.status'),
    studentId: positiveInteger(record.studentId, 'excuse.studentId'),
    studentName: requiredString(record.studentName, 'excuse.studentName'),
    groupId: positiveInteger(record.groupId, 'excuse.groupId'),
    groupName: nullableString(record.groupName),
    excuseType: nullableString(record.excuseType),
    reason: nullableString(record.reason),
    comment: nullableString(record.comment),
    createdAt: nullableString(record.createdAt),
    decisionBy: nullablePositiveInteger(record.decisionBy, 'excuse.decisionBy'),
    decisionAt: nullableString(record.decisionAt),
    decisionComment: nullableString(record.decisionComment),
    lessons: arrayValue(record.lessons).map(normalizeDayLesson).sort(compareLessons),
    attachments: arrayValue(record.attachments).map(normalizeAttachment),
    serverNow: requiredString(record.serverNow, 'excuse.serverNow'),
  }
}

function normalizeDayLesson(value: unknown): TeacherDayLesson {
  const record = requiredRecord(value)
  const rawStatus = requiredString(record.status, 'lesson.status').toUpperCase()
  const status: TeacherDayLesson['status'] = rawStatus === 'PLANNED' || rawStatus === 'ACTIVE'
    || rawStatus === 'CLOSED' || rawStatus === 'CANCELLED' ? rawStatus : 'UNKNOWN'
  return {
    id: positiveInteger(record.id, 'lesson.id'),
    assignmentId: nonNegativeInteger(record.assignmentId, 'lesson.assignmentId'),
    groupId: positiveInteger(record.groupId, 'lesson.groupId'),
    groupName: requiredString(record.groupName, 'lesson.groupName'),
    subjectId: positiveInteger(record.subjectId, 'lesson.subjectId'),
    subjectName: requiredString(record.subjectName, 'lesson.subjectName'),
    semesterId: positiveInteger(record.semesterId, 'lesson.semesterId'),
    lessonType: requiredString(record.lessonType, 'lesson.lessonType'),
    date: requiredDate(record.date, 'lesson.date'),
    lessonNumber: nonNegativeInteger(record.lessonNumber, 'lesson.lessonNumber'),
    startsAt: nullableString(record.startsAt),
    endsAt: nullableString(record.endsAt),
    room: nullableString(record.room),
    status,
    oneOff: requiredBoolean(record.oneOff, 'lesson.oneOff'),
    moved: requiredBoolean(record.moved, 'lesson.moved'),
    cancelled: requiredBoolean(record.cancelled, 'lesson.cancelled'),
  }
}

function normalizeRoster(value: unknown): TeacherRosterEntry {
  const record = requiredRecord(value)
  return {
    studentId: positiveInteger(record.studentId, 'roster.studentId'),
    displayName: requiredString(record.displayName, 'roster.displayName'),
    status: nullableString(record.status),
    symbol: nullableString(record.symbol),
    source: nullableString(record.source),
    recordPresent: requiredBoolean(record.recordPresent, 'roster.recordPresent'),
    pendingTicket: requiredBoolean(record.pendingTicket, 'roster.pendingTicket'),
    autoAbsent: requiredBoolean(record.autoAbsent, 'roster.autoAbsent'),
    ticketId: nullableString(record.ticketId),
    excuseType: nullableString(record.excuseType),
    excuseReason: nullableString(record.excuseReason),
    excuseComment: nullableString(record.excuseComment),
    attachmentId: nullableString(record.attachmentId),
    attachmentName: nullableString(record.attachmentName),
    attachmentContentType: nullableString(record.attachmentContentType),
    attachmentSize: nullableNonNegativeInteger(record.attachmentSize, 'roster.attachmentSize'),
  }
}

function normalizeStudent(value: unknown): TeacherJournalStudent {
  const record = requiredRecord(value)
  return {
    studentId: positiveInteger(record.studentId, 'journal.studentId'),
    displayName: requiredString(record.displayName, 'journal.displayName'),
    cells: arrayValue(record.cells).map(normalizeCell).sort((left, right) => left.lessonId - right.lessonId),
  }
}

function normalizeCell(value: unknown): TeacherJournalCell {
  const record = requiredRecord(value)
  return {
    lessonId: positiveInteger(record.lessonId, 'cell.lessonId'),
    status: nullableString(record.status),
    symbol: nullableString(record.symbol),
    source: nullableString(record.source),
    recordPresent: requiredBoolean(record.recordPresent, 'cell.recordPresent'),
    pendingTicket: requiredBoolean(record.pendingTicket, 'cell.pendingTicket'),
    autoAbsent: requiredBoolean(record.autoAbsent, 'cell.autoAbsent'),
    ticketId: nullableString(record.ticketId),
    excuseType: nullableString(record.excuseType),
    excuseReason: nullableString(record.excuseReason),
  }
}

function normalizeAttachment(value: unknown): TeacherExcuseAttachment {
  const record = requiredRecord(value)
  return {
    id: requiredString(record.id, 'attachment.id'),
    fileName: requiredString(record.fileName, 'attachment.fileName'),
    contentType: requiredString(record.contentType, 'attachment.contentType'),
    size: nonNegativeInteger(record.size, 'attachment.size'),
    uploadedAt: nullableString(record.uploadedAt),
    expiresAt: nullableString(record.expiresAt),
  }
}

function compareLessons(left: TeacherDayLesson, right: TeacherDayLesson): number {
  return left.date.localeCompare(right.date)
    || (left.startsAt ?? '').localeCompare(right.startsAt ?? '')
    || left.lessonNumber - right.lessonNumber
    || left.groupName.localeCompare(right.groupName, 'ru')
    || left.id - right.id
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function requiredRecord(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new Error('Сервер вернул некорректные данные преподавателя.')
  return value as Record<string, unknown>
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== 'string' || value.trim() === '') throw new Error(`Сервер вернул пустое поле ${field}.`)
  return value
}

function nullableString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null
}

function requiredDate(value: unknown, field: string): string {
  const string = requiredString(value, field)
  assertDate(string, field)
  return string
}

function nullableDate(value: unknown, field: string): string | null {
  if (value === null || value === undefined || value === '') return null
  return requiredDate(value, field)
}

function requiredBoolean(value: unknown, field: string): boolean {
  if (typeof value !== 'boolean') throw new Error(`Сервер вернул некорректное поле ${field}.`)
  return value
}

function positiveInteger(value: unknown, field: string): number {
  const parsed = integerValue(value)
  if (parsed === null || parsed <= 0) throw new Error(`Сервер вернул некорректный идентификатор ${field}.`)
  return parsed
}

function nullablePositiveInteger(value: unknown, field: string): number | null {
  if (value === null || value === undefined || value === '') return null
  return positiveInteger(value, field)
}

function nonNegativeInteger(value: unknown, field: string): number {
  const parsed = integerValue(value)
  if (parsed === null || parsed < 0) throw new Error(`Сервер вернул некорректное поле ${field}.`)
  return parsed
}

function nullableNonNegativeInteger(value: unknown, field: string): number | null {
  if (value === null || value === undefined || value === '') return null
  return nonNegativeInteger(value, field)
}

function assertPositiveInteger(value: number, field: string): void {
  if (!Number.isSafeInteger(value) || value <= 0) throw new RangeError(`${field} must be a positive integer`)
}

function assertDate(value: string, field: string): void {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new RangeError(`${field} must be an ISO date`)
}

function assertNonNegativeInteger(value: number, field: string): void {
  if (!Number.isSafeInteger(value) || value < 0) throw new RangeError(`${field} must be a non-negative integer`)
}

function assertPageSize(value: number): void {
  if (!Number.isSafeInteger(value) || value < 1 || value > 100) {
    throw new RangeError('pageSize must be an integer from 1 to 100')
  }
}

function pageSizeValue(value: unknown, field: string): number {
  const parsed = positiveInteger(value, field)
  if (parsed > 100) throw new Error(`Сервер вернул слишком большой размер страницы ${field}.`)
  return parsed
}

function assertText(value: string, field: string): void {
  if (value.trim() === '') throw new RangeError(`${field} must not be blank`)
}

function integerValue(value: unknown): number | null {
  if (typeof value === 'number') return Number.isSafeInteger(value) ? value : null
  if (typeof value === 'string' && /^(0|[1-9][0-9]*)$/.test(value)) {
    const parsed = Number(value)
    return Number.isSafeInteger(parsed) ? parsed : null
  }
  return null
}

function problemDetail(value: unknown): string | null {
  if (typeof value !== 'object' || value === null) return null
  const record = value as Record<string, unknown>
  return typeof record.detail === 'string' ? record.detail : typeof record.title === 'string' ? record.title : null
}

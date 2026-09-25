import { StaleSessionGenerationError } from '../../shared/session-owner'
import type {
  ReportDownloadFormat,
  ReportDownloadTicketRequest,
} from '../../shared/report-download-client'

export type HeadmanStatsEmptyState = 'NONE' | 'NO_ACTIVE_SEMESTER' | 'NO_COMPLETED_LESSONS' | 'NO_MEMBERS' | 'FILTERED_EMPTY'
export type HeadmanStatsFilterKind = 'TEXT' | 'RANGE'

export interface HeadmanStatsSort {
  readonly field: string
  readonly descending: boolean
}

export interface HeadmanStatsFilter {
  readonly field: string
  readonly contains?: string | null
  readonly minimum?: number | null
  readonly maximum?: number | null
}

export interface HeadmanStatsQuery {
  readonly subjectId: number | null
  readonly lessonTypes: readonly string[]
  readonly page: number
  readonly size: number
  readonly sorts: readonly HeadmanStatsSort[]
  readonly filters: readonly HeadmanStatsFilter[]
}

export interface HeadmanStatsExportQuery {
  readonly subjectId: number | null
  readonly lessonTypes: readonly string[]
  readonly sorts: readonly HeadmanStatsSort[]
  readonly filters: readonly HeadmanStatsFilter[]
}

export interface HeadmanStatsMetric {
  readonly numerator: number
  readonly denominator: number
  readonly percent: number
}

export interface HeadmanStatsMetrics {
  readonly present: HeadmanStatsMetric
  readonly presentOrExcused: HeadmanStatsMetric
  readonly excused: HeadmanStatsMetric
  readonly absent: HeadmanStatsMetric
}

export interface HeadmanStatsContext {
  readonly groupId: number
  readonly groupName: string
  readonly semesterId: number | null
  readonly semesterName: string
  readonly semesterFrom: string | null
  readonly semesterTo: string | null
  readonly subjectId: number | null
  readonly subjectName: string
  readonly lessonTypes: readonly string[]
  readonly lessonsCount: number
  readonly generatedAt: string
}

export interface HeadmanStatsStudentRow {
  readonly studentId: number
  readonly displayName: string
  readonly metrics: HeadmanStatsMetrics
  readonly lateCheckin: { readonly submitted: number; readonly approved: number; readonly rejected: number }
  readonly excuse: { readonly submitted: number; readonly approved: number; readonly rejected: number }
  readonly sources: {
    readonly studentGeo: number
    readonly manualRequest: number
    readonly autoAfterGeoFailure: number
    readonly headmanManual: number
  }
}

export interface HeadmanStatsSubjectOption {
  readonly id: number
  readonly label: string
  readonly lessonTypes: readonly { readonly code: string; readonly label: string }[]
}

export interface HeadmanStatsColumn {
  readonly field: string
  readonly label: string
  readonly filterKind: HeadmanStatsFilterKind
}

export interface HeadmanStatsFormat {
  readonly code: ReportDownloadFormat
  readonly label: string
  readonly contentType: string
  readonly extension: string
}

export interface HeadmanStatsResponse {
  readonly context: HeadmanStatsContext
  readonly summary: HeadmanStatsMetrics
  readonly filteredStudents: number
  readonly rows: readonly HeadmanStatsStudentRow[]
  readonly subjects: readonly HeadmanStatsSubjectOption[]
  readonly columns: readonly HeadmanStatsColumn[]
  readonly formats: readonly HeadmanStatsFormat[]
  readonly page: number
  readonly size: number
  readonly totalPages: number
  readonly totalElements: number
  readonly hasPrevious: boolean
  readonly hasNext: boolean
  readonly emptyState: HeadmanStatsEmptyState
}

export interface HeadmanStatsDownload {
  readonly blob: Blob
  readonly filename: string
  readonly contentType: string
}

export interface HeadmanStatsApiOptions {
  readonly accessToken: () => string | null
  readonly onUnauthorized?: () => Promise<void>
  readonly assertCurrent?: () => void
  readonly fetcher?: typeof fetch
}

export class HeadmanStatsApiError extends Error {
  constructor(readonly response: Response, readonly problem: unknown) {
    super(problemDetail(problem) ?? `HTTP ${response.status}`)
    this.name = 'HeadmanStatsApiError'
  }
}

export class HeadmanStatsApi {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: HeadmanStatsApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  async query(query: HeadmanStatsQuery, signal?: AbortSignal): Promise<HeadmanStatsResponse> {
    validateQuery(query)
    const value = await this.request<unknown>('/api/attendance/reports/headman/stats/query', {
      method: 'POST',
      body: JSON.stringify(query),
      ...(signal ? { signal } : {}),
    })
    return normalizeResponse(value)
  }

  async downloadExport(
    query: HeadmanStatsExportQuery,
    format: HeadmanStatsFormat,
    signal?: AbortSignal,
  ): Promise<HeadmanStatsDownload> {
    validateExportQuery(query)
    if (!format.code.trim()) throw new RangeError('Выбери формат выгрузки')
    const response = await this.response('/api/attendance/reports/headman/stats/export', {
      method: 'POST',
      headers: { Accept: '*/*' },
      body: JSON.stringify({ ...query, format: format.code }),
      ...(signal ? { signal } : {}),
    })
    if (!response.ok) throw await this.apiError(response)
    const blob = await response.blob()
    this.options.assertCurrent?.()
    const fallbackName = `statistika.${format.extension}`
    return {
      blob,
      filename: filenameFromContentDisposition(response.headers.get('Content-Disposition'), fallbackName),
      contentType: response.headers.get('Content-Type') ?? format.contentType,
    }
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await this.response(path, init, retried)
    if (!response.ok) throw await this.apiError(response)
    const value = await response.json() as T
    this.options.assertCurrent?.()
    return value
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

  private async apiError(response: Response): Promise<HeadmanStatsApiError> {
    let problem: unknown = null
    try {
      problem = await response.json()
    } catch {
      // Keep the HTTP status when the gateway did not return JSON.
    }
    this.options.assertCurrent?.()
    return new HeadmanStatsApiError(response, problem)
  }
}

export interface HeadmanStatsApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export type HeadmanStatsBlock = 'group' | 'subject'

export function toHeadmanStatsReportRequest(
  query: HeadmanStatsExportQuery,
  format: ReportDownloadFormat,
): Extract<ReportDownloadTicketRequest, { readonly kind: 'HEADMAN_STATS' }> {
  validateExportQuery(query)
  return {
    kind: 'HEADMAN_STATS',
    headmanStats: {
      ...(query.subjectId === null ? {} : { subjectId: query.subjectId }),
      ...(query.lessonTypes.length === 0 ? {} : { lessonTypes: [...query.lessonTypes] }),
      ...(query.sorts.length === 0 ? {} : {
        sorts: query.sorts.map((sort) => ({ field: sort.field, descending: sort.descending })),
      }),
      ...(query.filters.length === 0 ? {} : {
        filters: query.filters.map((filter) => ({
          field: filter.field,
          ...(filter.contains == null ? {} : { contains: filter.contains }),
          ...(filter.minimum == null ? {} : { minimum: filter.minimum }),
          ...(filter.maximum == null ? {} : { maximum: filter.maximum }),
        })),
      }),
      format,
    },
  }
}

export function headmanStatsQueryKey(query: HeadmanStatsQuery): string {
  return JSON.stringify({
    subjectId: query.subjectId,
    lessonTypes: query.lessonTypes,
    sorts: query.sorts,
    filters: query.filters,
    page: query.page,
    size: query.size,
  })
}

export function headmanStatsQueryScopeKey(
  block: HeadmanStatsBlock,
  query: Pick<HeadmanStatsQuery, 'subjectId'>,
): string {
  return JSON.stringify({ block, subjectId: query.subjectId })
}

export function isHeadmanStatsResponseCurrent(
  loadedQueryKey: string | null,
  currentQueryKey: string,
): boolean {
  return loadedQueryKey !== null && loadedQueryKey === currentQueryKey
}

export function headmanStatsResponseForCurrentQuery<T>(
  response: T | null,
  loadedQueryKey: string | null,
  currentQueryKey: string,
): T | null {
  return isHeadmanStatsResponseCurrent(loadedQueryKey, currentQueryKey) ? response : null
}

export function hasHeadmanStatsResettableCriteria(
  sorts: readonly HeadmanStatsSort[],
  hasFilterDraft: boolean,
): boolean {
  return sorts.length > 0 || hasFilterDraft
}

export function resetHeadmanStatsCriteria(): Pick<HeadmanStatsExportQuery, 'sorts' | 'filters'> {
  return { sorts: [], filters: [] }
}

export function createGenerationBoundHeadmanStatsApi(
  owner: HeadmanStatsApiGenerationOwner,
  fetcher?: typeof fetch,
): HeadmanStatsApi {
  const generation = owner.currentGeneration()
  const assertCurrent = (): void => {
    if (generation !== owner.currentGeneration()) throw new StaleSessionGenerationError()
  }
  const transport = fetcher ?? ((input: RequestInfo | URL, init?: RequestInit) => globalThis.fetch(input, init))
  return new HeadmanStatsApi({
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
    fetcher: async (input, init) => {
      assertCurrent()
      const response = await transport(input, init)
      assertCurrent()
      return response
    },
  })
}

function validateQuery(query: HeadmanStatsQuery): void {
  validateExportQuery(query)
  if (!Number.isInteger(query.page) || query.page < 0) throw new RangeError('page должен быть неотрицательным')
  if (!Number.isInteger(query.size) || query.size < 1 || query.size > 100) throw new RangeError('size должен быть от 1 до 100')
}

function validateExportQuery(query: HeadmanStatsExportQuery): void {
  if (query.subjectId !== null && (!Number.isSafeInteger(query.subjectId) || query.subjectId <= 0)) {
    throw new RangeError('subjectId должен быть положительным целым числом или null')
  }
  if (!Array.isArray(query.lessonTypes) || query.lessonTypes.some((type) => !type.trim())
    || new Set(query.lessonTypes).size !== query.lessonTypes.length) {
    throw new RangeError('lessonTypes содержит пустое или повторное значение')
  }
  if (!Array.isArray(query.sorts) || !Array.isArray(query.filters)) throw new RangeError('Сортировки и фильтры должны быть списками')
  if (query.lessonTypes.length > 0 && query.subjectId === null) throw new RangeError('Типы занятий можно выбрать только для предмета')
}

function normalizeResponse(value: unknown): HeadmanStatsResponse {
  const record = unwrapContent(value)
  if (!record) throw new Error('Сервер вернул неполную статистику группы.')
  const context = normalizeContext(record.context)
  const summary = normalizeMetrics(record.summary)
  const rows = array(record.rows).map(normalizeRow)
  const subjects = array(record.subjects).map(normalizeSubject)
  const columns = array(record.columns).map(normalizeColumn)
  const formats = array(record.formats).map(normalizeFormat)
  const emptyState = stringValue(record.emptyState)
  const page = nonnegativeInteger(record.page)
  const size = positiveInteger(record.size)
  const totalPages = nonnegativeInteger(record.totalPages)
  const totalElements = nonnegativeInteger(record.totalElements)
  const filteredStudents = nonnegativeInteger(record.filteredStudents)
  const hasPrevious = booleanValue(record.hasPrevious)
  const hasNext = booleanValue(record.hasNext)
  if (!context || !summary || rows.some((row) => !row) || subjects.some((subject) => !subject)
    || columns.some((column) => !column) || formats.some((format) => !format)
    || !isEmptyState(emptyState) || page === null || size === null || totalPages === null
    || totalElements === null || filteredStudents === null || hasPrevious === null || hasNext === null) {
    throw new Error('Сервер вернул некорректную статистику группы.')
  }
  return {
    context,
    summary,
    filteredStudents,
    rows: rows as HeadmanStatsStudentRow[],
    subjects: subjects as HeadmanStatsSubjectOption[],
    columns: columns as HeadmanStatsColumn[],
    formats: formats as HeadmanStatsFormat[],
    page,
    size,
    totalPages,
    totalElements,
    hasPrevious,
    hasNext,
    emptyState,
  }
}

function normalizeContext(value: unknown): HeadmanStatsContext | null {
  const record = asRecord(value)
  if (!record) return null
  const groupId = positiveInteger(record.groupId)
  const semesterId = nullablePositiveInteger(record.semesterId)
  const subjectId = nullablePositiveInteger(record.subjectId)
  const lessonsCount = nonnegativeInteger(record.lessonsCount)
  const generatedAt = stringValue(record.generatedAt)
  const lessonTypes = array(record.lessonTypes).map(stringValue)
  const semesterFrom = nullableString(record.semesterFrom)
  const semesterTo = nullableString(record.semesterTo)
  if (groupId === null || semesterId === undefined || subjectId === undefined || lessonsCount === null
    || generatedAt === null || !Number.isFinite(Date.parse(generatedAt)) || lessonTypes.some((type) => type === null)
    || semesterFrom === undefined || semesterTo === undefined) return null
  return {
    groupId,
    groupName: stringValue(record.groupName) ?? '',
    semesterId,
    semesterName: stringValue(record.semesterName) ?? '',
    semesterFrom,
    semesterTo,
    subjectId,
    subjectName: stringValue(record.subjectName) ?? '',
    lessonTypes: lessonTypes as string[],
    lessonsCount,
    generatedAt,
  }
}

function normalizeMetrics(value: unknown): HeadmanStatsMetrics | null {
  const record = asRecord(value)
  if (!record) return null
  const present = normalizeMetric(record.present)
  const presentOrExcused = normalizeMetric(record.presentOrExcused)
  const excused = normalizeMetric(record.excused)
  const absent = normalizeMetric(record.absent)
  return present && presentOrExcused && excused && absent ? { present, presentOrExcused, excused, absent } : null
}

function normalizeMetric(value: unknown): HeadmanStatsMetric | null {
  const record = asRecord(value)
  if (!record) return null
  const numerator = nonnegativeInteger(record.numerator)
  const denominator = nonnegativeInteger(record.denominator)
  const percent = numberValue(record.percent)
  if (numerator === null || denominator === null || numerator > denominator || percent === null
    || percent < 0 || percent > 100 || denominator === 0 && percent !== 0) return null
  return { numerator, denominator, percent }
}

function normalizeRow(value: unknown): HeadmanStatsStudentRow | null {
  const record = asRecord(value)
  if (!record) return null
  const studentId = positiveInteger(record.studentId)
  const metrics = normalizeMetrics(record.metrics)
  const lateCheckin = normalizeTicketCounts(record.lateCheckin)
  const excuse = normalizeTicketCounts(record.excuse)
  const sourcesRecord = asRecord(record.sources)
  if (studentId === null || metrics === null || lateCheckin === null || excuse === null || !sourcesRecord) return null
  const studentGeo = nonnegativeInteger(sourcesRecord.studentGeo)
  const manualRequest = nonnegativeInteger(sourcesRecord.manualRequest)
  const autoAfterGeoFailure = nonnegativeInteger(sourcesRecord.autoAfterGeoFailure)
  const headmanManual = nonnegativeInteger(sourcesRecord.headmanManual)
  if (studentGeo === null || manualRequest === null || autoAfterGeoFailure === null || headmanManual === null) return null
  return {
    studentId,
    displayName: stringValue(record.displayName) ?? '',
    metrics,
    lateCheckin,
    excuse,
    sources: { studentGeo, manualRequest, autoAfterGeoFailure, headmanManual },
  }
}

function normalizeTicketCounts(value: unknown): HeadmanStatsStudentRow['lateCheckin'] | null {
  const record = asRecord(value)
  if (!record) return null
  const submitted = nonnegativeInteger(record.submitted)
  const approved = nonnegativeInteger(record.approved)
  const rejected = nonnegativeInteger(record.rejected)
  return submitted === null || approved === null || rejected === null ? null : { submitted, approved, rejected }
}

function normalizeSubject(value: unknown): HeadmanStatsSubjectOption | null {
  const record = asRecord(value)
  if (!record) return null
  const id = positiveInteger(record.id)
  const lessonTypes = array(record.lessonTypes).map((item) => {
    const type = asRecord(item)
    const code = type ? stringValue(type.code) : null
    return type && code ? { code, label: stringValue(type.label) ?? code } : null
  })
  if (id === null || lessonTypes.some((item) => !item)) return null
  return { id, label: stringValue(record.label) ?? '', lessonTypes: lessonTypes as { code: string; label: string }[] }
}

function normalizeColumn(value: unknown): HeadmanStatsColumn | null {
  const record = asRecord(value)
  if (!record) return null
  const field = stringValue(record.field)
  const filterKind = stringValue(record.filterKind)
  if (!field || (filterKind !== 'TEXT' && filterKind !== 'RANGE')) return null
  return { field, label: stringValue(record.label) ?? field, filterKind }
}

function normalizeFormat(value: unknown): HeadmanStatsFormat | null {
  const record = asRecord(value)
  if (!record) return null
  const code = stringValue(record.code)
  const contentType = stringValue(record.contentType)
  const extension = stringValue(record.extension)
  if (!code || !contentType || !extension || !['docx', 'pdf', 'png', 'html', 'xlsx'].includes(code)) return null
  return { code: code as HeadmanStatsFormat['code'], label: stringValue(record.label) ?? code, contentType, extension }
}

function unwrapContent(value: unknown): Record<string, unknown> | null {
  const record = asRecord(value)
  if (!record) return null
  const content = asRecord(record.content)
  return content ?? record
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : null
}

function positiveInteger(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? value : null
}

function nullablePositiveInteger(value: unknown): number | null | undefined {
  return value === null ? null : positiveInteger(value) ?? undefined
}

function nonnegativeInteger(value: unknown): number | null {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : null
}

function numberValue(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function stringValue(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}

function nullableString(value: unknown): string | null | undefined {
  return value === null ? null : stringValue(value) ?? undefined
}

function booleanValue(value: unknown): boolean | null {
  return typeof value === 'boolean' ? value : null
}

function isEmptyState(value: string | null): value is HeadmanStatsEmptyState {
  return value !== null && ['NONE', 'NO_ACTIVE_SEMESTER', 'NO_COMPLETED_LESSONS', 'NO_MEMBERS', 'FILTERED_EMPTY'].includes(value)
}

function problemDetail(value: unknown): string | null {
  const record = asRecord(value)
  return record ? stringValue(record.detail) ?? stringValue(record.title) : null
}

function filenameFromContentDisposition(value: string | null, fallback: string): string {
  if (!value) return fallback
  const extended = /filename\*=UTF-8''([^;]+)/i.exec(value)?.[1]
  const quoted = /filename="([^"]+)"/i.exec(value)?.[1]
  const plain = /filename=([^;]+)/i.exec(value)?.[1]?.trim()
  const raw = extended ? decodeURIComponent(extended) : quoted ?? plain
  if (!raw) return fallback
  const cleaned = [...raw].map((character) => {
    const codePoint = character.codePointAt(0) ?? 0
    return character === '/' || character === '\\' || codePoint <= 0x1f || codePoint === 0x7f
      ? '_'
      : character
  }).join('').trim()
  return cleaned && cleaned !== '.' && cleaned !== '..' ? cleaned : fallback
}

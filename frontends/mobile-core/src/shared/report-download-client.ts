import { StaleSessionGenerationError } from './session-owner'

export type ReportDownloadFormat = 'docx' | 'pdf' | 'png' | 'html' | 'xlsx'
export type TeacherReportStatsScope = 'students' | 'groups'

export interface TeacherJournalReportSelector {
  readonly semesterId: number
  readonly groupId: number
  readonly subjectId: number
  readonly lessonTypes: readonly string[]
  readonly dateFrom?: string
  readonly dateTo?: string
  readonly format: ReportDownloadFormat
}

export interface TeacherStatsReportSelector {
  readonly semesterId: number
  readonly scope: TeacherReportStatsScope
  readonly groupId?: number
  readonly subjectId?: number
  readonly lessonTypes?: readonly string[]
  readonly sorts?: readonly string[]
  readonly filters?: readonly string[]
  readonly format: ReportDownloadFormat
}

export interface HeadmanWeeklyCurrentReportSelector {
  readonly weekStart: string
  readonly format: ReportDownloadFormat
}

export interface HeadmanWeeklySelectedReportSelector {
  readonly weekStarts: readonly string[]
  readonly format: ReportDownloadFormat
}

export interface HeadmanStatsReportSelector {
  readonly subjectId?: number
  readonly lessonTypes?: readonly string[]
  readonly sorts?: readonly {
    readonly field: string
    readonly descending: boolean
  }[]
  readonly filters?: readonly {
    readonly field: string
    readonly contains?: string
    readonly minimum?: number
    readonly maximum?: number
  }[]
  readonly format: ReportDownloadFormat
}

export interface HeadmanStatsTrendReportSelector {
  readonly mode: 'SEMESTER' | 'WEEK' | 'SUBJECT'
  readonly weekStart?: string
  readonly subjectId?: number
  readonly lessonTypes?: readonly string[]
  readonly format: 'png' | 'html'
}

export type ReportDownloadTicketRequest =
  | {
    readonly kind: 'TEACHER_JOURNAL'
    readonly teacherJournal: TeacherJournalReportSelector
    readonly teacherStats?: never
    readonly headmanWeeklyCurrent?: never
    readonly headmanWeeklySelected?: never
    readonly headmanStats?: never
  }
  | {
    readonly kind: 'TEACHER_STATS'
    readonly teacherJournal?: never
    readonly teacherStats: TeacherStatsReportSelector
    readonly headmanWeeklyCurrent?: never
    readonly headmanWeeklySelected?: never
    readonly headmanStats?: never
  }
  | {
    readonly kind: 'HEADMAN_WEEKLY_CURRENT'
    readonly teacherJournal?: never
    readonly teacherStats?: never
    readonly headmanWeeklyCurrent: HeadmanWeeklyCurrentReportSelector
    readonly headmanWeeklySelected?: never
    readonly headmanStats?: never
  }
  | {
    readonly kind: 'HEADMAN_WEEKLY_SELECTED'
    readonly teacherJournal?: never
    readonly teacherStats?: never
    readonly headmanWeeklyCurrent?: never
    readonly headmanWeeklySelected: HeadmanWeeklySelectedReportSelector
    readonly headmanStats?: never
  }
  | {
    readonly kind: 'HEADMAN_STATS'
    readonly teacherJournal?: never
    readonly teacherStats?: never
    readonly headmanWeeklyCurrent?: never
    readonly headmanWeeklySelected?: never
    readonly headmanStats: HeadmanStatsReportSelector
    readonly headmanStatsTrend?: never
  }
  | {
    readonly kind: 'HEADMAN_STATS_TREND'
    readonly teacherJournal?: never
    readonly teacherStats?: never
    readonly headmanWeeklyCurrent?: never
    readonly headmanWeeklySelected?: never
    readonly headmanStats?: never
    readonly headmanStatsTrend: HeadmanStatsTrendReportSelector
  }

export interface ReportDownloadTicket {
  readonly downloadPath: string
  readonly expiresAt: string
  readonly suggestedFilename: string
}

export type ReportDownloadResult = 'accepted' | 'cancelled' | 'unsupported' | 'stale'
export type ReportDownloadRequestIsCurrent = () => boolean

export interface ReportDownloadPort {
  download(
    request: ReportDownloadTicketRequest,
    isCurrent: ReportDownloadRequestIsCurrent,
  ): Promise<ReportDownloadResult>
}

export interface ReportDownloadTicketIssuer {
  assertCurrent(): void
  issueTicket(request: ReportDownloadTicketRequest): Promise<ReportDownloadTicket>
}

export interface ReportDownloadClientOptions {
  readonly accessToken: () => string | null
  readonly assertCurrent: () => void
  readonly onUnauthorized?: () => Promise<void>
  readonly fetcher?: typeof fetch
}

export interface ReportDownloadGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export class ReportDownloadTicketError extends Error {
  constructor(readonly status: number) {
    super(ticketErrorMessage(status))
    this.name = 'ReportDownloadTicketError'
  }
}

export class ReportDownloadResponseError extends Error {
  constructor() {
    super('Сервер вернул некорректные данные для скачивания отчёта.')
    this.name = 'ReportDownloadResponseError'
  }
}

export class ReportDownloadClient implements ReportDownloadTicketIssuer {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: ReportDownloadClientOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  assertCurrent(): void {
    this.options.assertCurrent()
  }

  async issueTicket(request: ReportDownloadTicketRequest): Promise<ReportDownloadTicket> {
    this.assertCurrent()
    validateTicketRequest(request)
    let response = await this.requestTicket(request)
    this.assertCurrent()
    if (response.status === 401 && this.options.onUnauthorized) {
      this.assertCurrent()
      await this.options.onUnauthorized()
      this.assertCurrent()
      response = await this.requestTicket(request)
      this.assertCurrent()
    }
    if (!response.ok) throw new ReportDownloadTicketError(response.status)

    let value: unknown
    try {
      value = await response.json()
    } catch {
      this.assertCurrent()
      throw new ReportDownloadResponseError()
    }
    this.assertCurrent()
    return parseTicketResponse(value, request)
  }

  private async requestTicket(request: ReportDownloadTicketRequest): Promise<Response> {
    this.assertCurrent()
    const token = this.options.accessToken()
    this.assertCurrent()
    const headers = new Headers({
      Accept: 'application/json',
      'Content-Type': 'application/json',
    })
    if (token) headers.set('Authorization', `Bearer ${token}`)

    const response = await this.fetcher('/api/auth/report-download-tickets', {
      method: 'POST',
      headers,
      credentials: 'include',
      body: JSON.stringify(request),
    })
    this.assertCurrent()
    return response
  }
}

export function createGenerationBoundReportDownloadClient(
  owner: ReportDownloadGenerationOwner,
  fetcher?: typeof fetch,
): ReportDownloadClient {
  const generation = owner.currentGeneration()
  const assertCurrent = (): void => {
    if (generation !== owner.currentGeneration()) throw new StaleSessionGenerationError()
  }
  return new ReportDownloadClient({
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
    ...(fetcher ? { fetcher } : {}),
  })
}

export function parseTicketResponse(
  value: unknown,
  request: ReportDownloadTicketRequest,
  now = Date.now(),
): ReportDownloadTicket {
  if (!isRecord(value)) throw new ReportDownloadResponseError()
  const downloadPath = value.downloadPath
  const expiresAt = value.expiresAt
  const suggestedFilename = value.suggestedFilename
  if (typeof downloadPath !== 'string' || typeof expiresAt !== 'string' || typeof suggestedFilename !== 'string') {
    throw new ReportDownloadResponseError()
  }
  const expiryTime = Date.parse(expiresAt)
  const format = reportFormat(request)
  const expectedExtension = request.kind === 'HEADMAN_STATS_TREND'
    ? format
    : format === 'png' ? 'zip' : format
  if (!/^\/api\/report-download\/[A-Za-z0-9_-]{43}$/.test(downloadPath)
    || !Number.isFinite(expiryTime) || expiryTime <= now
    || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$/.test(suggestedFilename)
    || !suggestedFilename.endsWith(`.${expectedExtension}`)) {
    throw new ReportDownloadResponseError()
  }
  return { downloadPath, expiresAt, suggestedFilename }
}

export function validateTicketRequest(request: ReportDownloadTicketRequest): void {
  const record = request as unknown as Record<string, unknown>
  const selectorByKind: Readonly<Record<ReportDownloadTicketRequest['kind'], string>> = {
    TEACHER_JOURNAL: 'teacherJournal',
    TEACHER_STATS: 'teacherStats',
    HEADMAN_WEEKLY_CURRENT: 'headmanWeeklyCurrent',
    HEADMAN_WEEKLY_SELECTED: 'headmanWeeklySelected',
    HEADMAN_STATS: 'headmanStats',
    HEADMAN_STATS_TREND: 'headmanStatsTrend',
  }
  const expectedSelector = selectorByKind[request.kind]
  const actualSelectors = Object.keys(record).filter((key) => selectorByKind[request.kind] === key
    || Object.values(selectorByKind).includes(key))
  if (!expectedSelector || actualSelectors.length !== 1 || actualSelectors[0] !== expectedSelector
    || Object.keys(record).some((key) => key !== 'kind' && key !== expectedSelector)) {
    throw new RangeError('A report request must contain exactly the selector for its kind.')
  }
  const selector = record[expectedSelector]
  if (!isRecord(selector) || !isReportDownloadFormat(selector.format)) {
    throw new RangeError('A report request must contain a supported format.')
  }

  const allowedSelectorFields: Readonly<Record<ReportDownloadTicketRequest['kind'], readonly string[]>> = {
    TEACHER_JOURNAL: ['semesterId', 'groupId', 'subjectId', 'lessonTypes', 'dateFrom', 'dateTo', 'format'],
    TEACHER_STATS: ['semesterId', 'scope', 'groupId', 'subjectId', 'lessonTypes', 'sorts', 'filters', 'format'],
    HEADMAN_WEEKLY_CURRENT: ['weekStart', 'format'],
    HEADMAN_WEEKLY_SELECTED: ['weekStarts', 'format'],
    HEADMAN_STATS: ['subjectId', 'lessonTypes', 'sorts', 'filters', 'format'],
    HEADMAN_STATS_TREND: ['mode', 'weekStart', 'subjectId', 'lessonTypes', 'format'],
  }
  if (Object.keys(selector).some((key) => !allowedSelectorFields[request.kind].includes(key))) {
    throw new RangeError('У отчёта есть неподдерживаемые параметры.')
  }
  const requiredSelectorFields: Readonly<Record<ReportDownloadTicketRequest['kind'], readonly string[]>> = {
    TEACHER_JOURNAL: ['semesterId', 'groupId', 'subjectId', 'lessonTypes', 'format'],
    TEACHER_STATS: ['semesterId', 'scope', 'format'],
    HEADMAN_WEEKLY_CURRENT: ['weekStart', 'format'],
    HEADMAN_WEEKLY_SELECTED: ['weekStarts', 'format'],
    HEADMAN_STATS: ['format'],
    HEADMAN_STATS_TREND: ['mode', 'format'],
  }
  if (requiredSelectorFields[request.kind].some((key) => !Object.prototype.hasOwnProperty.call(selector, key))) {
    throw new RangeError('У отчёта не хватает обязательных параметров.')
  }

  switch (request.kind) {
    case 'TEACHER_JOURNAL':
      if (![selector.semesterId, selector.groupId, selector.subjectId].every(isPositiveInteger)
        || !isStringList(selector.lessonTypes, 1, 3, 64)
        || (selector.dateFrom === undefined) !== (selector.dateTo === undefined)
        || (selector.dateFrom !== undefined
          && (!isIsoDate(selector.dateFrom) || !isIsoDate(selector.dateTo)
            || selector.dateTo < selector.dateFrom))) {
        throw new RangeError('Проверь параметры журнала.')
      }
      break
    case 'TEACHER_STATS':
      if (!isPositiveInteger(selector.semesterId) || (selector.scope !== 'students' && selector.scope !== 'groups')
        || (selector.scope === 'students'
          ? !isPositiveInteger(selector.groupId) || !isPositiveInteger(selector.subjectId)
          : selector.groupId !== undefined || selector.subjectId !== undefined)
        || (selector.lessonTypes !== undefined && !isStringList(selector.lessonTypes, 0, 20, 64))
        || (selector.sorts !== undefined && !isStringList(selector.sorts, 0, 20, 128))
        || (selector.filters !== undefined && !isStringList(selector.filters, 0, 20, 128))) {
        throw new RangeError('Проверь параметры статистики.')
      }
      break
    case 'HEADMAN_WEEKLY_CURRENT':
      if (!isIsoDate(selector.weekStart)) throw new RangeError('Укажи начало текущей недели в формате ГГГГ-ММ-ДД.')
      break
    case 'HEADMAN_WEEKLY_SELECTED':
      if (!isStringList(selector.weekStarts, 1, 64, 10) || !selector.weekStarts.every(isIsoDate)
        || new Set(selector.weekStarts).size !== selector.weekStarts.length) {
        throw new RangeError('Проверь список выбранных недель.')
      }
      break
    case 'HEADMAN_STATS':
      if ((selector.subjectId !== undefined && !isPositiveInteger(selector.subjectId))
        || (selector.lessonTypes !== undefined && !isStringList(selector.lessonTypes, 0, 20, 64))
        || (selector.sorts !== undefined && !isHeadmanStatsSortList(selector.sorts))
        || (selector.filters !== undefined && !isHeadmanStatsFilterList(selector.filters))) {
        throw new RangeError('Проверь параметры статистики старосты.')
      }
      break
    case 'HEADMAN_STATS_TREND': {
      const noSubjectSelection = selector.subjectId === undefined
        && (selector.lessonTypes === undefined
          || Array.isArray(selector.lessonTypes) && selector.lessonTypes.length === 0)
      const noWeekSelection = selector.weekStart === undefined
      const validMode = selector.mode === 'SEMESTER'
        ? noSubjectSelection && noWeekSelection
        : selector.mode === 'WEEK'
          ? noSubjectSelection && isIsoDate(selector.weekStart)
            && new Date(`${String(selector.weekStart)}T00:00:00.000Z`).getUTCDay() === 1
          : selector.mode === 'SUBJECT'
            ? noWeekSelection && isPositiveInteger(selector.subjectId)
              && (selector.lessonTypes === undefined || isStringList(selector.lessonTypes, 0, 20, 64))
            : false
      if (!validMode || (selector.format !== 'png' && selector.format !== 'html')) {
        throw new RangeError('Проверь период и формат графика старосты.')
      }
      break
    }
  }
}

export function isReportDownloadFormat(value: unknown): value is ReportDownloadFormat {
  return value === 'docx' || value === 'pdf' || value === 'png' || value === 'html' || value === 'xlsx'
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function isStringList(value: unknown, min: number, max: number, maxLength: number): value is readonly string[] {
  return Array.isArray(value) && value.length >= min && value.length <= max
    && value.every((item) => typeof item === 'string' && item.trim().length > 0 && item.length <= maxLength)
}

function isIsoDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00.000Z`)
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

function isHeadmanStatsSortList(value: unknown): boolean {
  return Array.isArray(value) && value.length <= 20 && value.every((item) => isRecord(item)
    && typeof item.field === 'string' && item.field.trim().length > 0 && item.field.length <= 128
    && typeof item.descending === 'boolean'
    && Object.keys(item).every((key) => key === 'field' || key === 'descending'))
}

function isHeadmanStatsFilterList(value: unknown): boolean {
  return Array.isArray(value) && value.length <= 20 && value.every((item) => isRecord(item)
    && typeof item.field === 'string' && item.field.trim().length > 0 && item.field.length <= 128
    && (item.contains === undefined || typeof item.contains === 'string' && item.contains.length <= 128)
    && (item.minimum === undefined || typeof item.minimum === 'number' && Number.isFinite(item.minimum))
    && (item.maximum === undefined || typeof item.maximum === 'number' && Number.isFinite(item.maximum))
    && Object.keys(item).every((key) => ['field', 'contains', 'minimum', 'maximum'].includes(key)))
}

function reportFormat(request: ReportDownloadTicketRequest): ReportDownloadFormat {
  switch (request.kind) {
    case 'TEACHER_JOURNAL': return request.teacherJournal.format
    case 'TEACHER_STATS': return request.teacherStats.format
    case 'HEADMAN_WEEKLY_CURRENT': return request.headmanWeeklyCurrent.format
    case 'HEADMAN_WEEKLY_SELECTED': return request.headmanWeeklySelected.format
    case 'HEADMAN_STATS': return request.headmanStats.format
    case 'HEADMAN_STATS_TREND': return request.headmanStatsTrend.format
  }
}

function ticketErrorMessage(status: number): string {
  switch (status) {
    case 400: return 'Не удалось подготовить отчёт: проверь выбранные параметры и формат.'
    case 401: return 'Сессия истекла. Войди снова, чтобы скачать отчёт.'
    case 403: return 'У тебя нет права скачать этот отчёт.'
    case 409: return 'Отчёт стал недоступен. Обнови данные и выбери его снова.'
    case 429: return 'Слишком много запросов на скачивание. Попробуй позже.'
    case 503: return 'Сервис отчётов временно недоступен. Попробуй позже.'
    default: return `Не удалось подготовить скачивание отчёта (HTTP ${status}).`
  }
}

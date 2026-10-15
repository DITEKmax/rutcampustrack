import { StaleSessionGenerationError } from './session-owner'
import type { components as AuthContract } from '../api/generated/auth'
import type {
  AdminManagedRole,
  AdminRoleStatus,
  AdminUser,
} from '../features/admin-users/admin-users-client'
import type {
  AdminGroupStatus,
  AdminGroupStudyMode,
  AdminGroupEducationLevel,
} from '../features/admin-groups/admin-groups-client'

export type ReportDownloadFormat = 'docx' | 'pdf' | 'png' | 'html' | 'xlsx'
export type TeacherReportStatsScope = 'students' | 'groups'

export interface TeacherJournalReportSelector {
  readonly semesterId: number
  readonly groupId: number
  readonly subjectId: number
  readonly lessonTypes: readonly string[]
  readonly dateFrom?: string
  readonly dateTo?: string
  readonly pastOnly?: boolean
  readonly lessonId?: string | null
  readonly format: ReportDownloadFormat
}

export function validatedTeacherJournalReportSelector(
  selector: TeacherJournalReportSelector,
): TeacherJournalReportSelector {
  validateTicketRequest({ kind: 'TEACHER_JOURNAL', teacherJournal: selector })
  return {
    ...selector,
    lessonTypes: [...selector.lessonTypes],
    ...(selector.lessonId == null ? {} : { lessonId: selector.lessonId }),
  }
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
  readonly semesterId?: number
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
  readonly semesterId?: number
  readonly bucketUnit?: 'DAY' | 'WEEK'
  readonly mode: 'SEMESTER' | 'WEEK' | 'SUBJECT'
  readonly weekStart?: string
  readonly subjectId?: number
  readonly lessonTypes?: readonly string[]
  readonly format: 'png' | 'html'
}

export interface HeadmanGroupCompositionReportSelector {
  readonly format: ReportDownloadFormat
}

export interface HeadmanSubjectJournalReportSelector {
  readonly subjectId: number
  readonly lessonTypes: readonly string[]
  readonly weekStarts?: readonly string[]
  readonly format: ReportDownloadFormat
}
export interface RegistryReportSort {
  readonly field: string
  readonly descending: boolean
}
export interface AdminUsersReportSelector {
  readonly search?: string
  readonly role?: AdminManagedRole
  readonly status?: AdminUser['status']
  readonly roleStatus?: AdminRoleStatus
  readonly sorts?: readonly RegistryReportSort[]
  readonly format: ReportDownloadFormat
}
export interface AdminGroupsReportSelector {
  readonly status?: AdminGroupStatus
  readonly search?: string
  readonly course?: number
  readonly studyMode?: AdminGroupStudyMode
  readonly educationLevel?: AdminGroupEducationLevel
  readonly sorts?: readonly RegistryReportSort[]
  readonly format: ReportDownloadFormat
}
export type AdminGroupAttendanceSort =
  | 'GROUP_NAME'
  | 'PRESENT_PERCENT'
  | 'PRESENT_OR_EXCUSED_PERCENT'
  | 'COUNTED_LESSONS'
  | 'PRESENT'
  | 'ABSENT'
  | 'EXCUSED'
export interface AdminGroupAttendanceReportSelector {
  readonly semesterId: string
  readonly search: string
  readonly sort: AdminGroupAttendanceSort
  readonly direction: 'ASC' | 'DESC'
  readonly format: ReportDownloadFormat
}

export type ExportJobState = NonNullable<AuthContract['schemas']['Status']['state']>
export type ExportStateListener = (state: ExportJobState) => void
export function exportStateLabel(state: ExportJobState): string {
  return {
    queued: 'Выгрузка в очереди…',
    preparing: 'Файл готовится…',
    ready: 'Файл готов.',
    error: 'Не удалось подготовить файл.',
  }[state]
}

type LegacySelectorExclusions = {
  readonly teacherJournal?: never
  readonly teacherStats?: never
  readonly headmanWeeklyCurrent?: never
  readonly headmanWeeklySelected?: never
  readonly headmanStats?: never
  readonly headmanStatsTrend?: never
  readonly headmanGroupComposition?: never
}

export type ReportDownloadTicketRequest =
  | {
      readonly kind: 'TEACHER_JOURNAL'
      readonly headmanGroupComposition?: never
      readonly teacherJournal: TeacherJournalReportSelector
      readonly teacherStats?: never
      readonly headmanWeeklyCurrent?: never
      readonly headmanWeeklySelected?: never
      readonly headmanStats?: never
    }
  | {
      readonly kind: 'TEACHER_STATS'
      readonly headmanGroupComposition?: never
      readonly teacherJournal?: never
      readonly teacherStats: TeacherStatsReportSelector
      readonly headmanWeeklyCurrent?: never
      readonly headmanWeeklySelected?: never
      readonly headmanStats?: never
    }
  | {
      readonly kind: 'HEADMAN_WEEKLY_CURRENT'
      readonly headmanGroupComposition?: never
      readonly teacherJournal?: never
      readonly teacherStats?: never
      readonly headmanWeeklyCurrent: HeadmanWeeklyCurrentReportSelector
      readonly headmanWeeklySelected?: never
      readonly headmanStats?: never
    }
  | {
      readonly kind: 'HEADMAN_WEEKLY_SELECTED'
      readonly headmanGroupComposition?: never
      readonly teacherJournal?: never
      readonly teacherStats?: never
      readonly headmanWeeklyCurrent?: never
      readonly headmanWeeklySelected: HeadmanWeeklySelectedReportSelector
      readonly headmanStats?: never
    }
  | {
      readonly kind: 'HEADMAN_STATS'
      readonly headmanGroupComposition?: never
      readonly teacherJournal?: never
      readonly teacherStats?: never
      readonly headmanWeeklyCurrent?: never
      readonly headmanWeeklySelected?: never
      readonly headmanStats: HeadmanStatsReportSelector
      readonly headmanStatsTrend?: never
    }
  | {
      readonly kind: 'HEADMAN_STATS_TREND'
      readonly headmanGroupComposition?: never
      readonly teacherJournal?: never
      readonly teacherStats?: never
      readonly headmanWeeklyCurrent?: never
      readonly headmanWeeklySelected?: never
      readonly headmanStats?: never
      readonly headmanStatsTrend: HeadmanStatsTrendReportSelector
    }
  | {
      readonly kind: 'HEADMAN_GROUP_COMPOSITION'
      readonly headmanGroupComposition: HeadmanGroupCompositionReportSelector
      readonly teacherJournal?: never
      readonly teacherStats?: never
      readonly headmanWeeklyCurrent?: never
      readonly headmanWeeklySelected?: never
      readonly headmanStats?: never
      readonly headmanStatsTrend?: never
    }
  | (LegacySelectorExclusions & {
      readonly kind: 'HEADMAN_SUBJECT_JOURNAL'
      readonly headmanSubjectJournal: HeadmanSubjectJournalReportSelector
      readonly adminUsers?: never
      readonly adminGroups?: never
    })
  | (LegacySelectorExclusions & {
      readonly kind: 'ADMIN_USERS'
      readonly adminUsers: AdminUsersReportSelector
      readonly headmanSubjectJournal?: never
      readonly adminGroups?: never
    })
  | (LegacySelectorExclusions & {
      readonly kind: 'ADMIN_GROUPS'
      readonly adminGroups: AdminGroupsReportSelector
      readonly headmanSubjectJournal?: never
      readonly adminUsers?: never
    })
  | (LegacySelectorExclusions & {
      readonly kind: 'ADMIN_GROUP_ATTENDANCE'
      readonly adminGroupAttendance: AdminGroupAttendanceReportSelector
      readonly headmanSubjectJournal?: never
      readonly adminUsers?: never
      readonly adminGroups?: never
    })

export interface ReportDownloadTicket {
  readonly downloadPath: string
  readonly expiresAt: string
  readonly suggestedFilename: string
}

export type ReportDownloadResult = 'accepted' | 'cancelled' | 'unsupported' | 'stale'
export type ReportDownloadRequestIsCurrent = () => boolean

export interface ReportDownloadPort {
  readonly carrier?: 'browser' | 'telegram'
  download(
    request: ReportDownloadTicketRequest,
    isCurrent: ReportDownloadRequestIsCurrent,
    onState?: ExportStateListener,
  ): Promise<ReportDownloadResult>
}

export interface ReportDownloadTicketIssuer {
  assertCurrent(): void
  issueTicket(
    request: ReportDownloadTicketRequest,
    onState?: ExportStateListener,
    isCurrent?: ReportDownloadRequestIsCurrent,
  ): Promise<ReportDownloadTicket>
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

  async issueTicket(
    request: ReportDownloadTicketRequest,
    onState?: ExportStateListener,
    isCurrent: ReportDownloadRequestIsCurrent = () => true,
  ): Promise<ReportDownloadTicket> {
    const current = (): void => {
      this.assertCurrent()
      if (!isCurrent()) throw new StaleSessionGenerationError()
    }
    current()
    validateTicketRequest(request)
    const requestId = globalThis.crypto.randomUUID()
    const created = await this.authenticated(
      '/api/auth/export-jobs',
      'POST',
      { requestId, report: request },
      current,
    )
    let job = parseExportJob(await this.json(created, current), request)
    current()
    const jobId = job.jobId
    let lastState: ExportJobState | undefined
    let wake: (() => void) | undefined
    const unsubscribe = listenExportJob(jobId, () => wake?.())
    try {
      while (true) {
        current()
        if (lastState !== job.state) {
          onState?.(job.state)
          lastState = job.state
        }
        if (job.state === 'error')
          throw new Error(
            job.errorCode === 'AUTHORITY_DENIED'
              ? 'Доступ к выгрузке изменился.'
              : 'Не удалось подготовить файл. Попробуй ещё раз.',
          )
        if (job.state === 'ready') break
        if (Date.parse(job.expiresAt) <= Date.now()) throw new ReportDownloadTicketError(409)
        await new Promise<void>((resolve) => {
          const timer = setTimeout(() => {
            wake = undefined
            resolve()
          }, 1500)
          wake = () => {
            clearTimeout(timer)
            wake = undefined
            resolve()
          }
        })
        current()
        const response = await this.authenticated(
          `/api/auth/export-jobs/${jobId}`,
          'GET',
          undefined,
          current,
        )
        const next = parseExportJob(await this.json(response, current), request)
        if (next.jobId !== jobId) throw new ReportDownloadResponseError()
        job = next
      }
      const response = await this.authenticated(
        `/api/auth/export-jobs/${jobId}/ticket`,
        'POST',
        undefined,
        current,
      )
      const ticket = parseTicketResponse(await this.json(response, current), request)
      current()
      return ticket
    } finally {
      unsubscribe()
      wake = undefined
    }
  }

  private async authenticated(
    path: string,
    method: 'GET' | 'POST',
    body: unknown,
    current: () => void,
    retried = false,
  ): Promise<Response> {
    current()
    const headers = new Headers({ Accept: 'application/json' })
    if (body !== undefined) headers.set('Content-Type', 'application/json')
    const token = this.options.accessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, {
      method,
      headers,
      credentials: 'include',
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    })
    current()
    if (response.status === 401 && this.options.onUnauthorized && !retried) {
      await this.options.onUnauthorized()
      current()
      return this.authenticated(path, method, body, current, true)
    }
    if (!response.ok) throw new ReportDownloadTicketError(response.status)
    return response
  }

  private async json(response: Response, current: () => void): Promise<unknown> {
    try {
      const value: unknown = await response.json()
      current()
      return value
    } catch (failure) {
      current()
      throw failure instanceof StaleSessionGenerationError
        ? failure
        : new ReportDownloadResponseError()
    }
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
  if (
    typeof downloadPath !== 'string' ||
    typeof expiresAt !== 'string' ||
    typeof suggestedFilename !== 'string'
  ) {
    throw new ReportDownloadResponseError()
  }
  const expiryTime = Date.parse(expiresAt)
  const format = reportFormat(request)
  const expectedExtension =
    request.kind === 'HEADMAN_STATS_TREND' ? format : format === 'png' ? 'zip' : format
  if (
    !/^\/api\/report-download\/[A-Za-z0-9_-]{43}$/.test(downloadPath) ||
    !Number.isFinite(expiryTime) ||
    expiryTime <= now ||
    !/^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$/.test(suggestedFilename) ||
    !suggestedFilename.endsWith(`.${expectedExtension}`)
  ) {
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
    HEADMAN_GROUP_COMPOSITION: 'headmanGroupComposition',
    HEADMAN_SUBJECT_JOURNAL: 'headmanSubjectJournal',
    ADMIN_USERS: 'adminUsers',
    ADMIN_GROUPS: 'adminGroups',
    ADMIN_GROUP_ATTENDANCE: 'adminGroupAttendance',
  }
  const expectedSelector = selectorByKind[request.kind]
  const actualSelectors = Object.keys(record).filter(
    (key) => selectorByKind[request.kind] === key || Object.values(selectorByKind).includes(key),
  )
  if (
    !expectedSelector ||
    actualSelectors.length !== 1 ||
    actualSelectors[0] !== expectedSelector ||
    Object.keys(record).some((key) => key !== 'kind' && key !== expectedSelector)
  ) {
    throw new RangeError('A report request must contain exactly the selector for its kind.')
  }
  const selector = record[expectedSelector]
  if (!isRecord(selector) || !isReportDownloadFormat(selector.format)) {
    throw new RangeError('A report request must contain a supported format.')
  }

  const allowedSelectorFields: Readonly<
    Record<ReportDownloadTicketRequest['kind'], readonly string[]>
  > = {
    TEACHER_JOURNAL: [
      'semesterId',
      'groupId',
      'subjectId',
      'lessonTypes',
      'dateFrom',
      'dateTo',
      'pastOnly',
      'lessonId',
      'format',
    ],
    TEACHER_STATS: [
      'semesterId',
      'scope',
      'groupId',
      'subjectId',
      'lessonTypes',
      'sorts',
      'filters',
      'format',
    ],
    HEADMAN_WEEKLY_CURRENT: ['weekStart', 'format'],
    HEADMAN_WEEKLY_SELECTED: ['weekStarts', 'format'],
    HEADMAN_STATS: ['semesterId', 'subjectId', 'lessonTypes', 'sorts', 'filters', 'format'],
    HEADMAN_STATS_TREND: [
      'semesterId',
      'bucketUnit',
      'mode',
      'weekStart',
      'subjectId',
      'lessonTypes',
      'format',
    ],
    HEADMAN_GROUP_COMPOSITION: ['format'],
    HEADMAN_SUBJECT_JOURNAL: ['subjectId', 'lessonTypes', 'weekStarts', 'format'],
    ADMIN_USERS: ['search', 'role', 'status', 'roleStatus', 'sorts', 'format'],
    ADMIN_GROUPS: ['status', 'search', 'course', 'studyMode', 'educationLevel', 'sorts', 'format'],
    ADMIN_GROUP_ATTENDANCE: ['semesterId', 'search', 'sort', 'direction', 'format'],
  }
  if (Object.keys(selector).some((key) => !allowedSelectorFields[request.kind].includes(key))) {
    throw new RangeError('У отчёта есть неподдерживаемые параметры.')
  }
  const requiredSelectorFields: Readonly<
    Record<ReportDownloadTicketRequest['kind'], readonly string[]>
  > = {
    TEACHER_JOURNAL: ['semesterId', 'groupId', 'subjectId', 'lessonTypes', 'format'],
    TEACHER_STATS: ['semesterId', 'scope', 'format'],
    HEADMAN_WEEKLY_CURRENT: ['weekStart', 'format'],
    HEADMAN_WEEKLY_SELECTED: ['weekStarts', 'format'],
    HEADMAN_STATS: ['format'],
    HEADMAN_STATS_TREND: ['mode', 'format'],
    HEADMAN_GROUP_COMPOSITION: ['format'],
    HEADMAN_SUBJECT_JOURNAL: ['subjectId', 'lessonTypes', 'format'],
    ADMIN_USERS: ['format'],
    ADMIN_GROUPS: ['format'],
    ADMIN_GROUP_ATTENDANCE: ['semesterId', 'search', 'sort', 'direction', 'format'],
  }
  if (
    requiredSelectorFields[request.kind].some(
      (key) => !Object.prototype.hasOwnProperty.call(selector, key),
    )
  ) {
    throw new RangeError('У отчёта не хватает обязательных параметров.')
  }

  switch (request.kind) {
    case 'TEACHER_JOURNAL':
      if (
        ![selector.semesterId, selector.groupId, selector.subjectId].every(isPositiveInteger) ||
        !isStringList(selector.lessonTypes, 1, 3, 64) ||
        (selector.pastOnly !== undefined && typeof selector.pastOnly !== 'boolean') ||
        (selector.lessonId != null &&
          (typeof selector.lessonId !== 'string' || !/^[1-9]\d*$/.test(selector.lessonId))) ||
        (selector.dateFrom === undefined) !== (selector.dateTo === undefined) ||
        (selector.dateFrom !== undefined &&
          (!isIsoDate(selector.dateFrom) ||
            !isIsoDate(selector.dateTo) ||
            selector.dateTo < selector.dateFrom))
      ) {
        throw new RangeError('Проверь параметры журнала.')
      }
      break
    case 'TEACHER_STATS':
      if (
        !isPositiveInteger(selector.semesterId) ||
        (selector.scope !== 'students' && selector.scope !== 'groups') ||
        (selector.scope === 'students'
          ? !isPositiveInteger(selector.groupId) || !isPositiveInteger(selector.subjectId)
          : selector.groupId !== undefined || selector.subjectId !== undefined) ||
        (selector.lessonTypes !== undefined && !isStringList(selector.lessonTypes, 0, 20, 64)) ||
        (selector.sorts !== undefined && !isStringList(selector.sorts, 0, 20, 128)) ||
        (selector.filters !== undefined && !isStringList(selector.filters, 0, 20, 128))
      ) {
        throw new RangeError('Проверь параметры статистики.')
      }
      break
    case 'HEADMAN_SUBJECT_JOURNAL':
      if (
        !isPositiveInteger(selector.subjectId) ||
        !isStringList(selector.lessonTypes, 1, 3, 64) ||
        (selector.weekStarts !== undefined &&
          (!isStringList(selector.weekStarts, 0, 64, 10) || !selector.weekStarts.every(isIsoDate)))
      )
        throw new RangeError('Проверь предмет, типы пар и недели.')
      break
    case 'ADMIN_GROUP_ATTENDANCE':
      if (
        typeof selector.semesterId !== 'string' ||
        !/^[1-9][0-9]{0,18}$/.test(selector.semesterId) ||
        (selector.semesterId.length === 19 && selector.semesterId > '9223372036854775807') ||
        typeof selector.search !== 'string' ||
        selector.search.length > 200 ||
        typeof selector.sort !== 'string' ||
        ![
          'GROUP_NAME',
          'PRESENT_PERCENT',
          'PRESENT_OR_EXCUSED_PERCENT',
          'COUNTED_LESSONS',
          'PRESENT',
          'ABSENT',
          'EXCUSED',
        ].includes(selector.sort) ||
        (selector.direction !== 'ASC' && selector.direction !== 'DESC')
      )
        throw new RangeError('Проверь семестр, поиск и порядок отчёта.')
      break
    case 'ADMIN_USERS':
    case 'ADMIN_GROUPS':
      if (
        (selector.search !== undefined &&
          (typeof selector.search !== 'string' || selector.search.length > 255)) ||
        (selector.sorts !== undefined &&
          (!Array.isArray(selector.sorts) ||
            selector.sorts.length > 8 ||
            !isHeadmanStatsSortList(selector.sorts))) ||
        (selector.course !== undefined &&
          (!isPositiveInteger(selector.course) || selector.course > 5))
      )
        throw new RangeError('Проверь фильтры реестра.')
      break
    case 'HEADMAN_WEEKLY_CURRENT':
      if (!isIsoDate(selector.weekStart))
        throw new RangeError('Укажи начало текущей недели в формате ГГГГ-ММ-ДД.')
      break
    case 'HEADMAN_WEEKLY_SELECTED':
      if (
        !isStringList(selector.weekStarts, 1, 64, 10) ||
        !selector.weekStarts.every(isIsoDate) ||
        new Set(selector.weekStarts).size !== selector.weekStarts.length
      ) {
        throw new RangeError('Проверь список выбранных недель.')
      }
      break
    case 'HEADMAN_STATS':
      if (
        (selector.semesterId !== undefined && !isPositiveInteger(selector.semesterId)) ||
        (selector.subjectId !== undefined && !isPositiveInteger(selector.subjectId)) ||
        (selector.lessonTypes !== undefined && !isStringList(selector.lessonTypes, 0, 20, 64)) ||
        (selector.sorts !== undefined && !isHeadmanStatsSortList(selector.sorts)) ||
        (selector.filters !== undefined && !isHeadmanStatsFilterList(selector.filters))
      ) {
        throw new RangeError('Проверь параметры статистики старосты.')
      }
      break
    case 'HEADMAN_STATS_TREND': {
      const noSubjectSelection =
        selector.subjectId === undefined &&
        (selector.lessonTypes === undefined ||
          (Array.isArray(selector.lessonTypes) && selector.lessonTypes.length === 0))
      const noWeekSelection = selector.weekStart === undefined
      const validMode =
        selector.mode === 'SEMESTER'
          ? noSubjectSelection && noWeekSelection
          : selector.mode === 'WEEK'
            ? noSubjectSelection &&
              isIsoDate(selector.weekStart) &&
              new Date(`${String(selector.weekStart)}T00:00:00.000Z`).getUTCDay() === 1
            : selector.mode === 'SUBJECT'
              ? noWeekSelection &&
                isPositiveInteger(selector.subjectId) &&
                (selector.lessonTypes === undefined ||
                  isStringList(selector.lessonTypes, 0, 20, 64))
              : false
      if (
        !validMode ||
        (selector.semesterId !== undefined && !isPositiveInteger(selector.semesterId)) ||
        (selector.bucketUnit !== undefined &&
          selector.bucketUnit !== 'DAY' &&
          selector.bucketUnit !== 'WEEK') ||
        (selector.format !== 'png' && selector.format !== 'html')
      ) {
        throw new RangeError('Проверь период и формат графика старосты.')
      }
      break
    }
  }
}

export function isReportDownloadFormat(value: unknown): value is ReportDownloadFormat {
  return (
    value === 'docx' || value === 'pdf' || value === 'png' || value === 'html' || value === 'xlsx'
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function isStringList(
  value: unknown,
  min: number,
  max: number,
  maxLength: number,
): value is readonly string[] {
  return (
    Array.isArray(value) &&
    value.length >= min &&
    value.length <= max &&
    value.every(
      (item) => typeof item === 'string' && item.trim().length > 0 && item.length <= maxLength,
    )
  )
}

function isIsoDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00.000Z`)
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

function isHeadmanStatsSortList(value: unknown): boolean {
  return (
    Array.isArray(value) &&
    value.length <= 20 &&
    value.every(
      (item) =>
        isRecord(item) &&
        typeof item.field === 'string' &&
        item.field.trim().length > 0 &&
        item.field.length <= 128 &&
        typeof item.descending === 'boolean' &&
        Object.keys(item).every((key) => key === 'field' || key === 'descending'),
    )
  )
}

function isHeadmanStatsFilterList(value: unknown): boolean {
  return (
    Array.isArray(value) &&
    value.length <= 20 &&
    value.every(
      (item) =>
        isRecord(item) &&
        typeof item.field === 'string' &&
        item.field.trim().length > 0 &&
        item.field.length <= 128 &&
        (item.contains === undefined ||
          (typeof item.contains === 'string' && item.contains.length <= 128)) &&
        (item.minimum === undefined ||
          (typeof item.minimum === 'number' && Number.isFinite(item.minimum))) &&
        (item.maximum === undefined ||
          (typeof item.maximum === 'number' && Number.isFinite(item.maximum))) &&
        Object.keys(item).every((key) => ['field', 'contains', 'minimum', 'maximum'].includes(key)),
    )
  )
}

export function reportFormat(request: ReportDownloadTicketRequest): ReportDownloadFormat {
  switch (request.kind) {
    case 'TEACHER_JOURNAL':
      return request.teacherJournal.format
    case 'TEACHER_STATS':
      return request.teacherStats.format
    case 'HEADMAN_WEEKLY_CURRENT':
      return request.headmanWeeklyCurrent.format
    case 'HEADMAN_WEEKLY_SELECTED':
      return request.headmanWeeklySelected.format
    case 'HEADMAN_STATS':
      return request.headmanStats.format
    case 'HEADMAN_STATS_TREND':
      return request.headmanStatsTrend.format
    case 'HEADMAN_GROUP_COMPOSITION':
      return request.headmanGroupComposition.format
    case 'HEADMAN_SUBJECT_JOURNAL':
      return request.headmanSubjectJournal.format
    case 'ADMIN_USERS':
      return request.adminUsers.format
    case 'ADMIN_GROUPS':
      return request.adminGroups.format
    case 'ADMIN_GROUP_ATTENDANCE':
      return request.adminGroupAttendance.format
  }
}

function ticketErrorMessage(status: number): string {
  switch (status) {
    case 400:
      return 'Не удалось подготовить отчёт: проверь выбранные параметры и формат.'
    case 401:
      return 'Сессия истекла. Войди снова, чтобы скачать отчёт.'
    case 403:
      return 'У тебя нет права скачать этот отчёт.'
    case 409:
      return 'Отчёт стал недоступен. Обнови данные и выбери его снова.'
    case 429:
      return 'Слишком много запросов на скачивание. Попробуй позже.'
    case 503:
      return 'Сервис отчётов временно недоступен. Попробуй позже.'
    default:
      return `Не удалось подготовить скачивание отчёта (HTTP ${status}).`
  }
}

interface ExportJobStatus {
  jobId: string
  state: ExportJobState
  suggestedFilename: string
  expiresAt: string
  errorCode: string | null
}
export function parseExportJob(
  value: unknown,
  request: ReportDownloadTicketRequest,
): ExportJobStatus {
  if (
    !isRecord(value) ||
    typeof value.jobId !== 'string' ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value.jobId) ||
    !isExportJobState(value.state) ||
    typeof value.expiresAt !== 'string' ||
    !Number.isFinite(Date.parse(value.expiresAt)) ||
    typeof value.suggestedFilename !== 'string' ||
    !/^[a-z0-9-]+\.(docx|pdf|zip|html|xlsx|png)$/.test(value.suggestedFilename) ||
    !(
      value.errorCode == null ||
      ['AUTHORITY_DENIED', 'GENERATION_FAILED', 'EXPORT_TOO_LARGE'].includes(
        String(value.errorCode),
      )
    )
  )
    throw new ReportDownloadResponseError()
  const extension =
    request.kind === 'HEADMAN_STATS_TREND'
      ? reportFormat(request)
      : reportFormat(request) === 'png'
        ? 'zip'
        : reportFormat(request)
  if (!value.suggestedFilename.endsWith(`.${extension}`)) throw new ReportDownloadResponseError()
  return {
    jobId: value.jobId,
    state: value.state,
    suggestedFilename: value.suggestedFilename,
    expiresAt: value.expiresAt,
    errorCode: (value.errorCode as string | null) ?? null,
  }
}
export function isExportJobState(value: unknown): value is ExportJobState {
  return value === 'queued' || value === 'preparing' || value === 'ready' || value === 'error'
}
const exportListeners = new Map<string, Set<() => void>>()
function listenExportJob(id: string, callback: () => void): () => void {
  const listeners = exportListeners.get(id) ?? new Set<() => void>()
  listeners.add(callback)
  exportListeners.set(id, listeners)
  return () => {
    listeners.delete(callback)
    if (listeners.size === 0) exportListeners.delete(id)
  }
}
/** A socket hint only schedules a fresh authenticated read; its state is never trusted. */
export function notifyExportJobHint(id: string): void {
  for (const callback of exportListeners.get(id) ?? []) callback()
}

export interface ReportFormatOption {
  code: ReportDownloadFormat
  label: string
}
export function parseReportFormats(value: unknown): readonly ReportFormatOption[] {
  if (!Array.isArray(value)) throw new ReportDownloadResponseError()
  const formats = value.map((item) => {
    if (
      !isRecord(item) ||
      !isReportDownloadFormat(item.code) ||
      typeof item.label !== 'string' ||
      item.label.trim().length === 0
    )
      throw new ReportDownloadResponseError()
    return { code: item.code, label: item.label }
  })
  if (formats.length !== 5 || new Set(formats.map((item) => item.code)).size !== 5)
    throw new ReportDownloadResponseError()
  return formats
}

export function reportDownloadAcceptedMessage(port: ReportDownloadPort): string {
  return port.carrier === 'browser'
    ? 'Файл готов. Скачивание передано браузеру.'
    : 'Telegram принял запрос на скачивание; проверь завершение в Telegram.'
}

import type { MobileProblemDetails } from '../../api/types'

export type AdminGroupStatus = 'ACTIVE' | 'DRAFT' | 'ARCHIVED'
export type AdminGroupDurationStatus = 'KNOWN' | 'LEGACY_UNKNOWN'

export interface AdminGroup {
  readonly id: number
  readonly name: string
  readonly alphabeticCode: string | null
  readonly numericCode: string | null
  readonly currentCourse: number | null
  readonly trainingDurationYears: number | null
  readonly durationStatus: AdminGroupDurationStatus
  readonly status: AdminGroupStatus
  readonly draftReason: string | null
  readonly studentCount: number
  readonly headmanFio: string | null
  readonly createdAt: string | null
}

export interface AdminGroupsPage {
  readonly items: readonly AdminGroup[]
  readonly number: number
  readonly size: number
  readonly totalElements: number
  readonly totalPages: number
  readonly activeCount: number
  readonly draftCount: number
  readonly archivedCount: number
}

export interface AdminGroupsListInput {
  readonly status?: AdminGroupStatus
  readonly search?: string
  readonly page?: number
  readonly size?: number
}

export interface CreateAdminGroupInput {
  readonly alphabeticCode: string
  readonly numericCode: string
  readonly trainingDurationYears: number
}

export interface HeadmanCandidate {
  readonly id: number
  readonly fio: string
  readonly current: boolean
}

export interface HeadmanRoster {
  readonly groupId: number
  readonly currentHeadmanId: number | null
  readonly currentHeadmanFio: string | null
  readonly candidates: readonly HeadmanCandidate[]
  readonly activeAssistantCount: number
}

export interface HeadmanAssignmentPreview {
  readonly groupId: number
  readonly currentHeadmanId: number | null
  readonly currentHeadmanFio: string | null
  readonly candidateId: number
  readonly candidateFio: string
  readonly sameHeadman: boolean
  readonly activatesDraft: boolean
  readonly assistantsToRevoke: number
}

export interface AssignHeadmanInput {
  readonly studentId: number
  readonly expectedHeadmanId: number | null
}

export interface HeadmanAssignment {
  readonly groupId: number
  readonly headmanId: number
  readonly headmanFio: string
  readonly changed: boolean
  readonly activatesDraft: boolean
  readonly assistantsRevoked: number
}

export type PromotionAction = 'PROMOTE' | 'ARCHIVE'
export type PromotionSkipReason = 'ALREADY_PROCESSED' | 'CREATED_AFTER_CYCLE_END'

export interface PromotionPreviewItem {
  readonly id: number
  readonly from: string
  readonly to: string | null
  readonly action: PromotionAction
  readonly studentCount: number
}

export interface PromotionSkippedItem {
  readonly id: number
  readonly name: string
  readonly studentCount: number
  readonly reason: PromotionSkipReason
  readonly previousAction: PromotionAction | null
  readonly previousFrom: string | null
  readonly previousTo: string | null
}

export interface PromotionConflict {
  readonly prefix: string
  readonly reason: string
  readonly message: string
  readonly groupIds: readonly number[]
}

export interface PromotionSummary {
  readonly toPromote: readonly PromotionPreviewItem[]
  readonly toArchive: readonly PromotionPreviewItem[]
  readonly skipped: readonly PromotionSkippedItem[]
  readonly conflicts: readonly PromotionConflict[]
  readonly cycleSemesterId: number
  readonly cycleDateTo: string
  readonly groupId: number | null
  readonly previewVersion: string
  readonly promoteCount: number
  readonly archiveCount: number
  readonly promotedStudentCount: number
  readonly archivedStudentCount: number
  readonly skippedGroupCount: number
  readonly skippedStudentCount: number
  readonly conflictCount: number
  readonly dryRun: boolean
  readonly executed: boolean
}

export interface PromotionExecuteInput {
  readonly cycleSemesterId: number
  readonly previewVersion: string
  readonly groupId: number | null
}

export interface AdminGroupsApiOptions {
  readonly accessToken: () => string | null
  readonly onUnauthorized?: () => Promise<void>
  readonly assertCurrent?: () => void
  readonly fetcher?: typeof fetch
}

export class AdminGroupsApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: MobileProblemDetails | null,
  ) {
    super(problem?.detail || problem?.title || `HTTP ${response.status}`)
    this.name = 'AdminGroupsApiError'
  }
}

/** Real Academic ADMIN group registry client for JS-ADMIN-05. */
export class AdminGroupsClient {
  private static readonly basePath = '/api/academic/groups/registry'
  private readonly fetcher: typeof fetch

  constructor(private readonly options: AdminGroupsApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  async listGroups(input: AdminGroupsListInput = {}, signal?: AbortSignal): Promise<AdminGroupsPage> {
    const params = new URLSearchParams()
    params.set('status', input.status ?? 'ACTIVE')
    if (input.search?.trim()) params.set('search', input.search.trim())
    params.set('page', String(nonNegativeInteger(input.page ?? 0, 'page')))
    params.set('size', String(positiveInteger(input.size ?? 20, 'size')))
    const value = await this.request<unknown>(
      `${AdminGroupsClient.basePath}?${params.toString()}`,
      signal ? { signal } : undefined,
    )
    return normalizePage(value)
  }

  createGroup(input: CreateAdminGroupInput, signal?: AbortSignal): Promise<AdminGroup> {
    const payload = normalizeCreateInput(input)
    return this.request<unknown>(AdminGroupsClient.basePath, {
      method: 'POST',
      body: JSON.stringify(payload),
      ...(signal ? { signal } : {}),
    }).then(normalizeGroup)
  }

  getHeadmanRoster(groupId: number, signal?: AbortSignal): Promise<HeadmanRoster> {
    positiveInteger(groupId, 'groupId')
    return this.request<unknown>(`/api/academic/groups/${groupId}/headman/roster`, signal ? { signal } : undefined)
      .then(normalizeHeadmanRoster)
  }

  previewHeadman(groupId: number, studentId: number, signal?: AbortSignal): Promise<HeadmanAssignmentPreview> {
    positiveInteger(groupId, 'groupId')
    positiveInteger(studentId, 'studentId')
    const params = new URLSearchParams({ studentId: String(studentId) })
    return this.request<unknown>(`/api/academic/groups/${groupId}/headman/preview?${params}`, signal ? { signal } : undefined)
      .then(normalizeHeadmanPreview)
  }

  assignHeadman(groupId: number, input: AssignHeadmanInput, signal?: AbortSignal): Promise<HeadmanAssignment> {
    positiveInteger(groupId, 'groupId')
    const studentId = positiveInteger(input.studentId, 'studentId')
    if (input.expectedHeadmanId !== null) positiveInteger(input.expectedHeadmanId, 'expectedHeadmanId')
    return this.request<unknown>(`/api/academic/groups/${groupId}/headman`, {
      method: 'PUT',
      body: JSON.stringify({ studentId, expectedHeadmanId: input.expectedHeadmanId }),
      ...(signal ? { signal } : {}),
    }).then(normalizeHeadmanAssignment)
  }

  previewPromotion(groupId?: number, signal?: AbortSignal): Promise<PromotionSummary> {
    const payload = groupId === undefined
      ? {}
      : { groupId: positiveInteger(groupId, 'groupId') }
    return this.request<unknown>('/api/academic/groups/promote/preview', {
      method: 'POST',
      body: JSON.stringify(payload),
      ...(signal ? { signal } : {}),
    }).then(normalizePromotionSummary)
  }

  executePromotion(input: PromotionExecuteInput, signal?: AbortSignal): Promise<PromotionSummary> {
    const cycleSemesterId = positiveInteger(input.cycleSemesterId, 'cycleSemesterId')
    const previewVersion = requiredText(input.previewVersion, 'previewVersion')
    const groupId = input.groupId === null ? null : positiveInteger(input.groupId, 'groupId')
    return this.request<unknown>('/api/academic/groups/promote', {
      method: 'POST',
      body: JSON.stringify({ cycleSemesterId, previewVersion, groupId }),
      ...(signal ? { signal } : {}),
    }).then(normalizePromotionSummary)
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await this.requestResponse(path, init, retried)
    const value = await response.json() as T
    this.options.assertCurrent?.()
    return value
  }

  private async requestResponse(path: string, init?: RequestInit, retried = false): Promise<Response> {
    this.options.assertCurrent?.()
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    headers.set('Accept', 'application/json')
    if (typeof init?.body === 'string') headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    this.options.assertCurrent?.()
    if (response.ok) return response
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      return this.requestResponse(path, init, true)
    }
    let problem: MobileProblemDetails | null = null
    try {
      problem = await response.json() as MobileProblemDetails
    } catch {
      // Keep the HTTP status when the gateway cannot return Problem Details.
    }
    this.options.assertCurrent?.()
    throw new AdminGroupsApiError(response, problem)
  }
}

function normalizePage(value: unknown): AdminGroupsPage {
  const record = requiredRecord(value, 'groups page')
  const items = Array.isArray(record.items) ? record.items.map(normalizeGroup) : []
  const number = nonNegativeInteger(record.number, 'groups page.number')
  const size = positiveInteger(record.size, 'groups page.size')
  const totalElements = nonNegativeInteger(record.totalElements, 'groups page.totalElements')
  const totalPages = nonNegativeInteger(record.totalPages, 'groups page.totalPages')
  if (totalPages === 0 && number !== 0) throw new Error('Сервер вернул некорректную страницу групп.')
  if (totalPages > 0 && number >= totalPages) throw new Error('Сервер вернул некорректную страницу групп.')
  return {
    items,
    number,
    size,
    totalElements,
    totalPages,
    activeCount: nonNegativeInteger(record.activeCount, 'groups page.activeCount'),
    draftCount: nonNegativeInteger(record.draftCount, 'groups page.draftCount'),
    archivedCount: nonNegativeInteger(record.archivedCount, 'groups page.archivedCount'),
  }
}

function normalizeGroup(value: unknown): AdminGroup {
  const record = requiredRecord(value, 'group')
  return {
    id: positiveInteger(record.id, 'group.id'),
    name: requiredText(record.name, 'group.name'),
    alphabeticCode: nullableText(record.alphabeticCode),
    numericCode: nullableNumericCode(record.numericCode),
    currentCourse: nullablePositiveInteger(record.currentCourse, 'group.currentCourse'),
    trainingDurationYears: nullablePositiveInteger(record.trainingDurationYears, 'group.trainingDurationYears'),
    durationStatus: durationStatus(record.durationStatus),
    status: groupStatus(record.status),
    draftReason: nullableText(record.draftReason),
    studentCount: nonNegativeInteger(record.studentCount, 'group.studentCount'),
    headmanFio: nullableText(record.headmanFio),
    createdAt: nullableText(record.createdAt),
  }
}

function normalizeHeadmanRoster(value: unknown): HeadmanRoster {
  const record = requiredRecord(value, 'headman roster')
  const candidates = Array.isArray(record.candidates)
    ? record.candidates.map(normalizeHeadmanCandidate)
    : []
  return {
    groupId: positiveInteger(record.groupId, 'headman roster.groupId'),
    currentHeadmanId: nullablePositiveInteger(record.currentHeadmanId, 'headman roster.currentHeadmanId'),
    currentHeadmanFio: nullableText(record.currentHeadmanFio),
    candidates,
    activeAssistantCount: nonNegativeInteger(record.activeAssistantCount, 'headman roster.activeAssistantCount'),
  }
}

function normalizeHeadmanCandidate(value: unknown): HeadmanCandidate {
  const record = requiredRecord(value, 'headman candidate')
  return {
    id: positiveInteger(record.id, 'headman candidate.id'),
    fio: requiredText(record.fio, 'headman candidate.fio'),
    current: requiredBoolean(record.current, 'headman candidate.current'),
  }
}

function normalizeHeadmanPreview(value: unknown): HeadmanAssignmentPreview {
  const record = requiredRecord(value, 'headman preview')
  return {
    groupId: positiveInteger(record.groupId, 'headman preview.groupId'),
    currentHeadmanId: nullablePositiveInteger(record.currentHeadmanId, 'headman preview.currentHeadmanId'),
    currentHeadmanFio: nullableText(record.currentHeadmanFio),
    candidateId: positiveInteger(record.candidateId, 'headman preview.candidateId'),
    candidateFio: requiredText(record.candidateFio, 'headman preview.candidateFio'),
    sameHeadman: requiredBoolean(record.sameHeadman, 'headman preview.sameHeadman'),
    activatesDraft: requiredBoolean(record.activatesDraft, 'headman preview.activatesDraft'),
    assistantsToRevoke: nonNegativeInteger(record.assistantsToRevoke, 'headman preview.assistantsToRevoke'),
  }
}

function normalizeHeadmanAssignment(value: unknown): HeadmanAssignment {
  const record = requiredRecord(value, 'headman assignment')
  return {
    groupId: positiveInteger(record.groupId, 'headman assignment.groupId'),
    headmanId: positiveInteger(record.headmanId, 'headman assignment.headmanId'),
    headmanFio: requiredText(record.headmanFio, 'headman assignment.headmanFio'),
    changed: requiredBoolean(record.changed, 'headman assignment.changed'),
    activatesDraft: requiredBoolean(record.activatesDraft, 'headman assignment.activatesDraft'),
    assistantsRevoked: nonNegativeInteger(record.assistantsRevoked, 'headman assignment.assistantsRevoked'),
  }
}

function normalizePromotionSummary(value: unknown): PromotionSummary {
  const record = requiredRecord(value, 'promotion summary')
  const toPromote = Array.isArray(record.toPromote) ? record.toPromote.map(normalizePromotionItem) : []
  const toArchive = Array.isArray(record.toArchive) ? record.toArchive.map(normalizePromotionItem) : []
  const skipped = Array.isArray(record.skipped) ? record.skipped.map(normalizePromotionSkippedItem) : []
  const conflicts = Array.isArray(record.conflicts) ? record.conflicts.map(normalizePromotionConflict) : []
  const dryRun = requiredBoolean(record.dryRun, 'promotion summary.dryRun')
  const executed = requiredBoolean(record.executed, 'promotion summary.executed')
  if (dryRun === executed) throw new Error('Сервер вернул некорректный статус перевода групп.')

  const result: PromotionSummary = {
    toPromote,
    toArchive,
    skipped,
    conflicts,
    cycleSemesterId: positiveInteger(record.cycleSemesterId, 'promotion summary.cycleSemesterId'),
    cycleDateTo: requiredDate(record.cycleDateTo, 'promotion summary.cycleDateTo'),
    groupId: nullablePositiveInteger(record.groupId, 'promotion summary.groupId'),
    previewVersion: requiredText(record.previewVersion, 'promotion summary.previewVersion'),
    promoteCount: nonNegativeInteger(record.promoteCount, 'promotion summary.promoteCount'),
    archiveCount: nonNegativeInteger(record.archiveCount, 'promotion summary.archiveCount'),
    promotedStudentCount: nonNegativeInteger(record.promotedStudentCount, 'promotion summary.promotedStudentCount'),
    archivedStudentCount: nonNegativeInteger(record.archivedStudentCount, 'promotion summary.archivedStudentCount'),
    skippedGroupCount: nonNegativeInteger(record.skippedGroupCount, 'promotion summary.skippedGroupCount'),
    skippedStudentCount: nonNegativeInteger(record.skippedStudentCount, 'promotion summary.skippedStudentCount'),
    conflictCount: nonNegativeInteger(record.conflictCount, 'promotion summary.conflictCount'),
    dryRun,
    executed,
  }
  const promotedStudents = toPromote.reduce((total, item) => total + item.studentCount, 0)
  const archivedStudents = toArchive.reduce((total, item) => total + item.studentCount, 0)
  const skippedStudents = skipped.reduce((total, item) => total + item.studentCount, 0)
  if (result.promoteCount !== toPromote.length
    || result.archiveCount !== toArchive.length
    || result.promotedStudentCount !== promotedStudents
    || result.archivedStudentCount !== archivedStudents
    || result.skippedGroupCount !== skipped.length
    || result.skippedStudentCount !== skippedStudents
    || result.conflictCount !== conflicts.length) {
    throw new Error('Числа предпросмотра не совпадают со списком групп.')
  }
  return result
}

function normalizePromotionItem(value: unknown): PromotionPreviewItem {
  const record = requiredRecord(value, 'promotion item')
  const action = promotionAction(record.action, 'promotion item.action')
  const to = nullableText(record.to)
  if ((action === 'PROMOTE' && to === null) || (action === 'ARCHIVE' && to !== null)) {
    throw new Error('Сервер вернул некорректное действие для группы.')
  }
  return {
    id: positiveInteger(record.id, 'promotion item.id'),
    from: requiredText(record.from, 'promotion item.from'),
    to,
    action,
    studentCount: nonNegativeInteger(record.studentCount, 'promotion item.studentCount'),
  }
}

function normalizePromotionSkippedItem(value: unknown): PromotionSkippedItem {
  const record = requiredRecord(value, 'promotion skipped item')
  const reason = record.reason
  if (reason !== 'ALREADY_PROCESSED' && reason !== 'CREATED_AFTER_CYCLE_END') {
    throw new Error('Сервер вернул неизвестную причину пропуска группы.')
  }
  const previousAction = record.previousAction === null || record.previousAction === undefined
    ? null
    : promotionAction(record.previousAction, 'promotion skipped item.previousAction')
  return {
    id: positiveInteger(record.id, 'promotion skipped item.id'),
    name: requiredText(record.name, 'promotion skipped item.name'),
    studentCount: nonNegativeInteger(record.studentCount, 'promotion skipped item.studentCount'),
    reason,
    previousAction,
    previousFrom: nullableText(record.previousFrom),
    previousTo: nullableText(record.previousTo),
  }
}

function normalizePromotionConflict(value: unknown): PromotionConflict {
  const record = requiredRecord(value, 'promotion conflict')
  const groupIds = Array.isArray(record.groupIds)
    ? record.groupIds.map((id, index) => positiveInteger(id, `promotion conflict.groupIds[${index}]`))
    : []
  return {
    prefix: typeof record.prefix === 'string' ? record.prefix : '',
    reason: requiredText(record.reason, 'promotion conflict.reason'),
    message: requiredText(record.message, 'promotion conflict.message'),
    groupIds,
  }
}

function promotionAction(value: unknown, field: string): PromotionAction {
  if (value === 'PROMOTE' || value === 'ARCHIVE') return value
  throw new Error(`Неизвестное действие ${field}.`)
}

function requiredDate(value: unknown, field: string): string {
  const date = requiredText(value, field)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(Date.parse(date))) {
    throw new Error(`Некорректная дата ${field}.`)
  }
  return date
}

function normalizeCreateInput(input: CreateAdminGroupInput): CreateAdminGroupInput {
  const alphabeticCode = requiredText(input.alphabeticCode, 'alphabeticCode').toUpperCase()
  if (!/^[А-ЯЁ][А-ЯЁа-яё]{1,3}$/.test(alphabeticCode)) {
    throw new RangeError('Буквенный код должен содержать 2–4 кириллических символа')
  }
  const numericCode = requiredText(input.numericCode, 'numericCode')
  if (!/^\d{3}$/.test(numericCode)) throw new RangeError('Цифровой код должен содержать 3 цифры')
  const trainingDurationYears = positiveInteger(input.trainingDurationYears, 'trainingDurationYears')
  const course = Number(numericCode[0])
  if (course < 1 || course > trainingDurationYears) {
    throw new RangeError('Первая цифра цифрового кода должна быть не больше срока обучения')
  }
  return { alphabeticCode, numericCode, trainingDurationYears }
}

function requiredRecord(value: unknown, field: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new Error(`Сервер вернул некорректные данные ${field}.`)
  return value as Record<string, unknown>
}

function requiredText(value: unknown, field: string): string {
  if (typeof value !== 'string' || value.trim() === '') throw new Error(`Пустое поле ${field}.`)
  return value.trim()
}

function nullableText(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null
}

function nullableNumericCode(value: unknown): string | null {
  const text = nullableText(value)
  if (text === null) return null
  if (!/^\d{3}$/.test(text)) throw new Error('Сервер вернул некорректный цифровой код группы.')
  return text
}

function positiveInteger(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0) throw new RangeError(`${field} должен быть положительным числом`)
  return value
}

function nullablePositiveInteger(value: unknown, field: string): number | null {
  return value === null || value === undefined ? null : positiveInteger(value, field)
}

function nonNegativeInteger(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error(`Некорректное поле ${field}.`)
  return value
}

function requiredBoolean(value: unknown, field: string): boolean {
  if (typeof value !== 'boolean') throw new Error(`Некорректное поле ${field}.`)
  return value
}

function groupStatus(value: unknown): AdminGroupStatus {
  if (value === 'ACTIVE' || value === 'DRAFT' || value === 'ARCHIVED') return value
  throw new Error('Сервер вернул неизвестный статус группы.')
}

function durationStatus(value: unknown): AdminGroupDurationStatus {
  if (value === 'KNOWN' || value === 'LEGACY_UNKNOWN') return value
  throw new Error('Сервер вернул неизвестный статус срока обучения.')
}

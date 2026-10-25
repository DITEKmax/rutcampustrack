import { IdentityOperationError, IdentityOperations } from '../../shared/identity-operation'
import { parseReportFormats, type ReportFormatOption } from '../../shared/report-download-client'
import type { MobileProblemDetails } from '../../api/types'

export type AdminGroupStatus = 'ACTIVE' | 'DRAFT' | 'ARCHIVED'
export type AdminGroupDurationStatus = 'KNOWN' | 'LEGACY_UNKNOWN'
export type AdminGroupStudyMode = 'FULL_TIME' | 'PART_TIME' | 'EXTRAMURAL'
export type AdminGroupEducationLevel = 'BACHELOR' | 'SPECIALIST' | 'MASTER'
export type AdminGroupSort = `${'name' | 'currentCourse' | 'programType'},${'asc' | 'desc'}`
export const adminGroupProgramTypes: readonly {
  digit: number
  studyMode: AdminGroupStudyMode
  educationLevel: AdminGroupEducationLevel
  label: string
}[] = [
  { digit: 1, studyMode: 'FULL_TIME', educationLevel: 'BACHELOR', label: 'Очный бакалавриат' },
  {
    digit: 2,
    studyMode: 'PART_TIME',
    educationLevel: 'BACHELOR',
    label: 'Очно-заочный бакалавриат',
  },
  { digit: 3, studyMode: 'EXTRAMURAL', educationLevel: 'BACHELOR', label: 'Заочный бакалавриат' },
  { digit: 4, studyMode: 'FULL_TIME', educationLevel: 'SPECIALIST', label: 'Очный специалитет' },
  {
    digit: 5,
    studyMode: 'PART_TIME',
    educationLevel: 'SPECIALIST',
    label: 'Очно-заочный специалитет',
  },
  { digit: 6, studyMode: 'EXTRAMURAL', educationLevel: 'SPECIALIST', label: 'Заочный специалитет' },
  { digit: 7, studyMode: 'FULL_TIME', educationLevel: 'MASTER', label: 'Очная магистратура' },
  {
    digit: 8,
    studyMode: 'PART_TIME',
    educationLevel: 'MASTER',
    label: 'Очно-заочная магистратура',
  },
  { digit: 9, studyMode: 'EXTRAMURAL', educationLevel: 'MASTER', label: 'Заочная магистратура' },
]

export interface AdminGroup {
  readonly id: number
  readonly name: string
  readonly alphabeticCode: string | null
  readonly numericCode: string | null
  readonly currentCourse: number | null
  readonly programTypeDigit: number | null
  readonly studyMode: AdminGroupStudyMode | null
  readonly educationLevel: AdminGroupEducationLevel | null
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
  readonly course?: number
  readonly studyMode?: AdminGroupStudyMode
  readonly educationLevel?: AdminGroupEducationLevel
  readonly sort?: readonly AdminGroupSort[]
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

export type GroupIdentityResult =
  | { readonly kind: 'HEADMAN'; readonly assignment: HeadmanAssignment }
  | { readonly kind: 'ARCHIVE' | 'RESTORE'; readonly groupId: number }
  | { readonly kind: 'PROMOTION'; readonly summary: PromotionSummary }
  | { readonly kind: 'RECOVERY' }

export interface AdminGroupDeletionCounts {
  readonly accountsAffected: number
  readonly memberships: number
  readonly headmanAssignments: number
  readonly assistantAssignments: number
  readonly academicRows: number
  readonly scheduleRows: number
  readonly attendanceRows: number
  readonly authGrants: number
  readonly [resource: string]: number
}

export interface AdminGroupDeletionPreview {
  readonly groupId: string
  readonly groupName: string
  readonly previewDigest: string
  readonly counts: AdminGroupDeletionCounts
}

export type AdminGroupDeletionPhase =
  | 'PREPARING'
  | 'COMMITTING'
  | 'COMPLETED'
  | 'RELEASING'
  | 'CANCELLED'

export interface AdminGroupDeletionOperation {
  readonly operationId: string
  readonly groupId: string
  readonly phase: AdminGroupDeletionPhase
  readonly retryable: boolean
  readonly counts: AdminGroupDeletionCounts
  readonly failure: string | null
}

export interface ConfirmAdminGroupDeletionInput {
  /** Caller-owned intent key; sent only as Idempotency-Key, never in the body. */
  readonly operationId: string
  readonly password: string
  readonly previewDigest: string
}

/** Recover with getGroupDeletion and this original key; do not repeat the POST blindly. */
export class AdminGroupDeletionUnknownError extends Error {
  readonly state = 'UNKNOWN'

  constructor(
    readonly operationId: string,
    readonly groupId: string,
    readonly status: number | null,
  ) {
    super('Результат удаления неизвестен. Проверь состояние исходной операции.')
    this.name = 'AdminGroupDeletionUnknownError'
  }
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
  private readonly identity: IdentityOperations
  private failedOperationId: string | null = null
  private confirmedFailedOperationId: string | null = null
  private groupIntent = false

  constructor(private readonly options: AdminGroupsApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
    this.identity = new IdentityOperations(
      (path, init) => this.requestResponse(path, init),
      () => this.options.assertCurrent?.(),
    )
  }

  /** Existing protected group read supplies factual name for the registry consumer. */
  async resolveNotificationGroup(groupId: number): Promise<AdminGroup | null> {
    positiveInteger(groupId, 'groupId')
    const raw = requiredRecord(
      await this.request<unknown>(`/api/academic/groups/${groupId}`),
      'group',
    )
    if (positiveInteger(raw.id, 'group.id') !== groupId || typeof raw.active !== 'boolean')
      throw new Error('Сервер вернул другую группу.')
    const name = requiredText(raw.name, 'group.name')
    const statuses: readonly AdminGroupStatus[] = raw.active ? ['ACTIVE'] : ['DRAFT', 'ARCHIVED']
    for (const status of statuses) {
      const page = await this.listGroups({ status, search: name, page: 0, size: 100 })
      const found = page.items.find((group) => group.id === groupId)
      if (found) return found
    }
    return null
  }
  async listGroups(
    input: AdminGroupsListInput = {},
    signal?: AbortSignal,
  ): Promise<AdminGroupsPage> {
    const params = new URLSearchParams()
    params.set('status', input.status ?? 'ACTIVE')
    if (input.search?.trim()) params.set('search', input.search.trim())
    if (input.course !== undefined) {
      if (!Number.isInteger(input.course) || input.course < 1 || input.course > 5)
        throw new RangeError('Курс должен быть от 1 до 5.')
      params.set('course', String(input.course))
    }
    if (input.studyMode !== undefined) params.set('studyMode', input.studyMode)
    if (input.educationLevel !== undefined) params.set('educationLevel', input.educationLevel)
    for (const sort of input.sort ?? []) {
      if (!/^(name|currentCourse|programType),(asc|desc)$/.test(sort))
        throw new RangeError('Неизвестная сортировка групп.')
      params.append('sort', sort)
    }
    const requestedPage = nonNegativeInteger(input.page ?? 0, 'page')
    params.set('page', String(requestedPage))
    params.set('size', String(positiveInteger(input.size ?? 20, 'size')))
    const value = await this.request<unknown>(
      `${AdminGroupsClient.basePath}?${params.toString()}`,
      signal ? { signal } : undefined,
    )
    return normalizePage(value, requestedPage)
  }

  async previewGroupDeletion(
    groupId: string,
    signal?: AbortSignal,
  ): Promise<AdminGroupDeletionPreview> {
    const id = deletionGroupId(groupId)
    const response = await this.requestResponse(
      `/api/academic/groups/${encodeURIComponent(id)}/deletion-preview`,
      { cache: 'no-store', ...(signal ? { signal } : {}) },
    )
    if (response.status !== 200) throw new Error('Сервер не подтвердил предварительную проверку.')
    const value: unknown = await response.json()
    this.options.assertCurrent?.()
    const record = requiredRecord(value, 'group deletion preview')
    if (deletionGroupId(record.groupId) !== id) throw new Error('Сервер вернул другую группу.')
    return {
      groupId: id,
      groupName: requiredText(record.groupName, 'group deletion preview.groupName'),
      previewDigest: deletionDigest(record.previewDigest),
      counts: deletionCounts(record.counts),
    }
  }

  /** A single password-bearing attempt. Recovery is an explicit original-operation GET. */
  async confirmGroupDeletion(
    groupId: string,
    input: ConfirmAdminGroupDeletionInput,
    signal?: AbortSignal,
  ): Promise<AdminGroupDeletionOperation> {
    const id = deletionGroupId(groupId)
    const operationId = deletionOperationId(input.operationId)
    const previewDigest = deletionDigest(input.previewDigest)
    if (typeof input.password !== 'string' || !input.password.trim() || input.password.length > 256)
      throw new RangeError('Укажи пароль длиной до 256 символов.')
    this.options.assertCurrent?.()
    const headers = new Headers({
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'Idempotency-Key': operationId,
    })
    const token = this.options.accessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    let status: number | null = null
    try {
      // Do not use the legacy identity-operation replay or automatic 401 POST retry.
      const response = await this.fetcher(
        `/api/academic/groups/${encodeURIComponent(id)}/deletions`,
        {
          method: 'POST',
          headers,
          credentials: 'include',
          cache: 'no-store',
          body: JSON.stringify({ password: input.password, previewDigest }),
          ...(signal ? { signal } : {}),
        },
      )
      status = response.status
      this.options.assertCurrent?.()
      if (!response.ok) throw new Error('Сервер не подтвердил запрос удаления.')
      const value: unknown = await response.json()
      this.options.assertCurrent?.()
      const result = normalizeDeletionOperation(value, operationId, id)
      if (
        (status !== 200 && status !== 202) ||
        (status === 200 && result.phase !== 'COMPLETED') ||
        (status === 202 && result.phase === 'COMPLETED')
      )
        throw new Error('Сервер не подтвердил результат операции удаления.')
      return result
    } catch {
      // No HTTP status proves non-admission after sending, including a late authority denial.
      // Keep the original recovery key without exposing a password-bearing cause/body.
      throw new AdminGroupDeletionUnknownError(operationId, id, status)
    }
  }

  async getGroupDeletion(
    operationId: string,
    expectedGroupId: string,
    signal?: AbortSignal,
  ): Promise<AdminGroupDeletionOperation> {
    const key = deletionOperationId(operationId)
    const groupId = deletionGroupId(expectedGroupId)
    const response = await this.requestResponse(`/api/academic/group-deletions/${key}`, {
      cache: 'no-store',
      ...(signal ? { signal } : {}),
    })
    if (response.status !== 200) throw new Error('Сервер не подтвердил состояние удаления.')
    const value: unknown = await response.json()
    this.options.assertCurrent?.()
    return normalizeDeletionOperation(value, key, groupId)
  }

  /** Explicitly resolve the original intent; never resend its password-bearing command. */
  async resolveGroupDeletion(
    operationId: string,
    expectedGroupId: string,
    signal?: AbortSignal,
  ): Promise<AdminGroupDeletionOperation> {
    const key = deletionOperationId(operationId)
    const groupId = deletionGroupId(expectedGroupId)
    this.options.assertCurrent?.()
    const headers = new Headers({ Accept: 'application/json' })
    const token = this.options.accessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    let status: number | null = null
    try {
      const response = await this.fetcher(
        `/api/academic/groups/${encodeURIComponent(groupId)}/deletions/${key}/resolve`,
        {
          method: 'POST',
          headers,
          credentials: 'include',
          cache: 'no-store',
          ...(signal ? { signal } : {}),
        },
      )
      status = response.status
      this.options.assertCurrent?.()
      if (status !== 200) throw new Error('Сервер не подтвердил разрешение исходной операции.')
      const value: unknown = await response.json()
      this.options.assertCurrent?.()
      return normalizeDeletionOperation(value, key, groupId)
    } catch {
      // Even a denied or lost resolve reply cannot release the original reference.
      throw new AdminGroupDeletionUnknownError(key, groupId, status)
    }
  }

  createGroup(input: CreateAdminGroupInput, signal?: AbortSignal): Promise<AdminGroup> {
    const payload = normalizeCreateInput(input)
    return this.request<unknown>(AdminGroupsClient.basePath, {
      method: 'POST',
      body: JSON.stringify(payload),
      ...(signal ? { signal } : {}),
    }).then(normalizeGroup)
  }

  async archiveGroup(groupId: number, name: string, signal?: AbortSignal): Promise<void> {
    positiveInteger(groupId, 'groupId')
    const groupName = requiredText(name, 'name')
    await this.groupMutation(`/api/academic/groups/${groupId}`, {
      method: 'PUT',
      body: JSON.stringify({ name: groupName, active: false }),
      ...(signal ? { signal } : {}),
    })
  }

  async renameGroup(
    group: AdminGroup,
    name: string,
    trainingDurationYears?: number,
    signal?: AbortSignal,
  ): Promise<void> {
    positiveInteger(group.id, 'groupId')
    if (group.status !== 'ACTIVE')
      throw new RangeError('Название можно исправить только у активной группы.')
    if (
      trainingDurationYears !== undefined &&
      (!Number.isInteger(trainingDurationYears) ||
        trainingDurationYears < 1 ||
        trainingDurationYears > 5)
    )
      throw new RangeError('Укажи срок обучения от 1 до 5 лет.')
    const duration = trainingDurationYears ?? group.trainingDurationYears
    if (duration === null) throw new RangeError('Укажи срок обучения от 1 до 5 лет.')
    const normalizedName = name.trim().toUpperCase()
    const code = normalizedName.match(/^([А-ЯЁ]{2,4})-([1-5][1-9][0-9])$/)
    if (!code)
      throw new RangeError('Укажи название в формате УИТ-311: курс 1–5, тип программы 1–9.')
    if (Number(code[2]![0]) > duration)
      throw new RangeError('Курс не может превышать срок обучения группы.')
    await this.requestResponse(`/api/academic/groups/${group.id}`, {
      method: 'PUT',
      body: JSON.stringify({
        name: normalizedName,
        active: true,
        ...(trainingDurationYears === undefined ? {} : { trainingDurationYears }),
      }),
      ...(signal ? { signal } : {}),
    })
  }

  async restoreGroup(groupId: number, signal?: AbortSignal): Promise<void> {
    positiveInteger(groupId, 'groupId')
    await this.groupMutation(`/api/academic/groups/${groupId}/restore`, {
      method: 'POST',
      ...(signal ? { signal } : {}),
    })
  }

  getHeadmanRoster(groupId: number, signal?: AbortSignal): Promise<HeadmanRoster> {
    positiveInteger(groupId, 'groupId')
    return this.request<unknown>(
      `/api/academic/groups/${groupId}/headman/roster`,
      signal ? { signal } : undefined,
    ).then(normalizeHeadmanRoster)
  }

  previewHeadman(
    groupId: number,
    studentId: number,
    signal?: AbortSignal,
  ): Promise<HeadmanAssignmentPreview> {
    positiveInteger(groupId, 'groupId')
    positiveInteger(studentId, 'studentId')
    const params = new URLSearchParams({ studentId: String(studentId) })
    return this.request<unknown>(
      `/api/academic/groups/${groupId}/headman/preview?${params}`,
      signal ? { signal } : undefined,
    ).then(normalizeHeadmanPreview)
  }

  assignHeadman(
    groupId: number,
    input: AssignHeadmanInput,
    signal?: AbortSignal,
  ): Promise<HeadmanAssignment> {
    positiveInteger(groupId, 'groupId')
    const studentId = positiveInteger(input.studentId, 'studentId')
    if (input.expectedHeadmanId !== null)
      positiveInteger(input.expectedHeadmanId, 'expectedHeadmanId')
    if (!this.identity.pending) {
      this.groupIntent = false
      this.failedOperationId = null
      this.confirmedFailedOperationId = null
    }
    return this.identityRequest(`/api/academic/groups/${groupId}/headman`, {
      method: 'PUT',
      body: JSON.stringify({ studentId, expectedHeadmanId: input.expectedHeadmanId }),
      ...(signal ? { signal } : {}),
    }).then(normalizeHeadmanAssignment)
  }

  previewPromotion(groupId?: number, signal?: AbortSignal): Promise<PromotionSummary> {
    const payload = groupId === undefined ? {} : { groupId: positiveInteger(groupId, 'groupId') }
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
    return this.identityRequest('/api/academic/groups/promote', {
      method: 'POST',
      body: JSON.stringify({ cycleSemesterId, previewVersion, groupId }),
      ...(signal ? { signal } : {}),
    }).then(normalizePromotionSummary)
  }

  async exportFormats(): Promise<readonly ReportFormatOption[]> {
    return parseReportFormats(
      await this.request<unknown>('/api/academic/registry-exports/groups/formats'),
    )
  }

  acknowledgeIdentityOperation(): void {
    this.identity.acknowledge()
    this.failedOperationId = null
    this.confirmedFailedOperationId = null
  }

  get failedGroupOperationId(): string | null {
    this.options.assertCurrent?.()
    return this.failedOperationId
  }

  async confirmGroupRecovery(): Promise<string | null> {
    this.options.assertCurrent?.()
    const id = this.failedOperationId
    if (!id) return null
    const status = await this.identity.inspect(id)
    this.options.assertCurrent?.()
    if (this.failedOperationId !== id) return null
    if (status !== 'FAILED_SAFE') {
      this.confirmedFailedOperationId = null
      return null
    }
    this.confirmedFailedOperationId = id
    return id
  }

  async reconcileGroupOperation(failedOperationId: string, signal?: AbortSignal): Promise<void> {
    this.options.assertCurrent?.()
    if (failedOperationId !== this.confirmedFailedOperationId)
      throw new Error('Сначала проверь состояние остановленной операции.')
    const response = await this.groupMutation(
      `/api/academic/identity-operations/${failedOperationId}/reconcile`,
      {
        method: 'POST',
        ...(signal ? { signal } : {}),
      },
    )
    await this.confirmRecoverySuccess(response)
  }

  get hasPendingIdentityOperation(): boolean {
    return this.identity.pending
  }

  async resumeIdentityOperation(): Promise<GroupIdentityResult> {
    let resumed: Awaited<ReturnType<IdentityOperations['resume']>>
    try {
      resumed = await this.identity.resume()
    } catch (cause) {
      if (this.groupIntent) this.captureFailedOperation(cause)
      throw cause
    }
    const { path, response } = resumed
    if (/^\/api\/academic\/identity-operations\/[0-9a-f-]{36}\/reconcile$/.test(path)) {
      await this.confirmRecoverySuccess(response)
      return { kind: 'RECOVERY' }
    }
    if (path === '/api/academic/groups/promote') {
      const value = (await response.json()) as unknown
      this.options.assertCurrent?.()
      const summary = normalizePromotionSummary(value)
      if (!summary.executed || summary.dryRun)
        throw new Error('Сервер не подтвердил выполнение перевода групп.')
      return { kind: 'PROMOTION', summary }
    }
    const match = /^\/api\/academic\/groups\/([1-9][0-9]*)(\/restore|\/headman)?$/.exec(path)
    if (!match) throw new Error('Не удалось определить исходное действие группы.')
    const groupId = positiveInteger(Number(match[1]), 'groupId')
    if (match[2] !== '/headman')
      return { kind: match[2] === '/restore' ? 'RESTORE' : 'ARCHIVE', groupId }
    const value = (await response.json()) as unknown
    this.options.assertCurrent?.()
    return { kind: 'HEADMAN', assignment: normalizeHeadmanAssignment(value) }
  }

  private captureFailedOperation(cause: unknown): void {
    if (
      cause instanceof IdentityOperationError &&
      cause.state === 'FAILED_SAFE' &&
      cause.operationId
    ) {
      this.failedOperationId = cause.operationId
      this.confirmedFailedOperationId = null
    }
  }

  private async groupMutation(path: string, init: RequestInit): Promise<Response> {
    if (!this.identity.pending) {
      this.groupIntent = true
      if (!path.endsWith('/reconcile')) {
        this.failedOperationId = null
        this.confirmedFailedOperationId = null
      }
    }
    try {
      return await this.identity.run(path, init)
    } catch (cause) {
      if (this.groupIntent) this.captureFailedOperation(cause)
      throw cause
    }
  }

  private async confirmRecoverySuccess(response: Response): Promise<void> {
    const value = (await response.json()) as unknown
    this.options.assertCurrent?.()
    const record = requiredRecord(value, 'operation recovery')
    if (
      record.status !== 'SUCCEEDED' ||
      record.retryAfterSeconds !== 0 ||
      typeof record.operationId !== 'string' ||
      record.operationId !== response.headers.get('X-Operation-Id')
    )
      throw new Error('Сервер не подтвердил восстановление операции.')
  }

  private async identityRequest(path: string, init: RequestInit): Promise<unknown> {
    const response = path.endsWith('/headman')
      ? await this.identity.run(path, init)
      : await this.groupMutation(path, init)
    const value = (await response.json()) as unknown
    this.options.assertCurrent?.()
    return value
  }

  private async request<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await this.requestResponse(path, init, retried)
    const value = (await response.json()) as T
    this.options.assertCurrent?.()
    return value
  }

  private async requestResponse(
    path: string,
    init?: RequestInit,
    retried = false,
  ): Promise<Response> {
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
      problem = (await response.json()) as MobileProblemDetails
    } catch {
      // Keep the HTTP status when the gateway cannot return Problem Details.
    }
    this.options.assertCurrent?.()
    if (
      this.groupIntent &&
      init?.method &&
      init.method !== 'GET' &&
      !path.endsWith('/headman') &&
      problem &&
      typeof problem === 'object'
    ) {
      const extras = (problem as Record<string, unknown>).extras
      if (extras && typeof extras === 'object') {
        const id = (extras as Record<string, unknown>).operationId
        if (
          typeof id === 'string' &&
          /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)
        ) {
          this.failedOperationId = id
          this.confirmedFailedOperationId = null
        }
      }
    }
    throw new AdminGroupsApiError(response, problem)
  }
}

function deletionGroupId(value: unknown): string {
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value))
    throw new TypeError('Некорректный идентификатор группы.')
  return value
}

function deletionOperationId(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value)
  )
    throw new TypeError('Некорректный идентификатор операции удаления.')
  return value
}

function deletionDigest(value: unknown): string {
  if (typeof value !== 'string' || !/^[0-9a-f]{64}$/.test(value))
    throw new TypeError('Некорректная версия предварительной проверки удаления.')
  return value
}

function deletionCounts(value: unknown): AdminGroupDeletionCounts {
  const record = requiredRecord(value, 'group deletion counts')
  const required = [
    'accountsAffected',
    'memberships',
    'headmanAssignments',
    'assistantAssignments',
    'academicRows',
    'scheduleRows',
    'attendanceRows',
    'authGrants',
  ] as const
  if (required.some((key) => !Object.hasOwn(record, key)))
    throw new Error('Сервер не вернул все последствия удаления группы.')
  return Object.fromEntries(
    Object.entries(record).map(([key, count]) => [
      key,
      nonNegativeInteger(count, 'group deletion count'),
    ]),
  ) as unknown as AdminGroupDeletionCounts
}

function normalizeDeletionOperation(
  value: unknown,
  operationId: string,
  groupId: string,
): AdminGroupDeletionOperation {
  const record = requiredRecord(value, 'group deletion operation')
  if (
    deletionOperationId(record.operationId) !== operationId ||
    deletionGroupId(record.groupId) !== groupId
  )
    throw new Error('Сервер вернул другую операцию удаления группы.')
  const phase = record.phase
  if (
    phase !== 'PREPARING' &&
    phase !== 'COMMITTING' &&
    phase !== 'COMPLETED' &&
    phase !== 'RELEASING' &&
    phase !== 'CANCELLED'
  )
    throw new Error('Сервер вернул неизвестное состояние удаления группы.')
  const retryable = requiredBoolean(record.retryable, 'group deletion retryable')
  if (retryable !== (phase !== 'COMPLETED' && phase !== 'CANCELLED'))
    throw new Error('Сервер вернул противоречивое состояние удаления группы.')
  if (record.failure !== null && typeof record.failure !== 'string')
    throw new Error('Сервер вернул некорректный результат удаления группы.')
  return {
    operationId,
    groupId,
    phase,
    retryable,
    counts: deletionCounts(record.counts),
    failure: record.failure,
  }
}

function normalizePage(value: unknown, requestedPage: number): AdminGroupsPage {
  const record = requiredRecord(value, 'groups page')
  const items = Array.isArray(record.items) ? record.items.map(normalizeGroup) : []
  const number = nonNegativeInteger(record.number, 'groups page.number')
  const size = positiveInteger(record.size, 'groups page.size')
  const totalElements = nonNegativeInteger(record.totalElements, 'groups page.totalElements')
  const totalPages = nonNegativeInteger(record.totalPages, 'groups page.totalPages')
  if (number !== requestedPage) throw new Error('Сервер вернул некорректную страницу групп.')
  if (totalPages === 0 && (totalElements !== 0 || items.length !== 0)) {
    throw new Error('Сервер вернул некорректную страницу групп.')
  }
  if (
    totalPages > 0 &&
    number >= totalPages &&
    (items.length !== 0 || totalElements > number * size)
  ) {
    throw new Error('Сервер вернул некорректную страницу групп.')
  }
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
    programTypeDigit: nullableProgramTypeDigit(record.programTypeDigit),
    studyMode: nullableGroupEnum(record.studyMode, ['FULL_TIME', 'PART_TIME', 'EXTRAMURAL']),
    educationLevel: nullableGroupEnum(record.educationLevel, ['BACHELOR', 'SPECIALIST', 'MASTER']),
    trainingDurationYears: nullablePositiveInteger(
      record.trainingDurationYears,
      'group.trainingDurationYears',
    ),
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
    currentHeadmanId: nullablePositiveInteger(
      record.currentHeadmanId,
      'headman roster.currentHeadmanId',
    ),
    currentHeadmanFio: nullableText(record.currentHeadmanFio),
    candidates,
    activeAssistantCount: nonNegativeInteger(
      record.activeAssistantCount,
      'headman roster.activeAssistantCount',
    ),
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
    currentHeadmanId: nullablePositiveInteger(
      record.currentHeadmanId,
      'headman preview.currentHeadmanId',
    ),
    currentHeadmanFio: nullableText(record.currentHeadmanFio),
    candidateId: positiveInteger(record.candidateId, 'headman preview.candidateId'),
    candidateFio: requiredText(record.candidateFio, 'headman preview.candidateFio'),
    sameHeadman: requiredBoolean(record.sameHeadman, 'headman preview.sameHeadman'),
    activatesDraft: requiredBoolean(record.activatesDraft, 'headman preview.activatesDraft'),
    assistantsToRevoke: nonNegativeInteger(
      record.assistantsToRevoke,
      'headman preview.assistantsToRevoke',
    ),
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
    assistantsRevoked: nonNegativeInteger(
      record.assistantsRevoked,
      'headman assignment.assistantsRevoked',
    ),
  }
}

function normalizePromotionSummary(value: unknown): PromotionSummary {
  const record = requiredRecord(value, 'promotion summary')
  const toPromote = Array.isArray(record.toPromote)
    ? record.toPromote.map(normalizePromotionItem)
    : []
  const toArchive = Array.isArray(record.toArchive)
    ? record.toArchive.map(normalizePromotionItem)
    : []
  const skipped = Array.isArray(record.skipped)
    ? record.skipped.map(normalizePromotionSkippedItem)
    : []
  const conflicts = Array.isArray(record.conflicts)
    ? record.conflicts.map(normalizePromotionConflict)
    : []
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
    promotedStudentCount: nonNegativeInteger(
      record.promotedStudentCount,
      'promotion summary.promotedStudentCount',
    ),
    archivedStudentCount: nonNegativeInteger(
      record.archivedStudentCount,
      'promotion summary.archivedStudentCount',
    ),
    skippedGroupCount: nonNegativeInteger(
      record.skippedGroupCount,
      'promotion summary.skippedGroupCount',
    ),
    skippedStudentCount: nonNegativeInteger(
      record.skippedStudentCount,
      'promotion summary.skippedStudentCount',
    ),
    conflictCount: nonNegativeInteger(record.conflictCount, 'promotion summary.conflictCount'),
    dryRun,
    executed,
  }
  const promotedStudents = toPromote.reduce((total, item) => total + item.studentCount, 0)
  const archivedStudents = toArchive.reduce((total, item) => total + item.studentCount, 0)
  const skippedStudents = skipped.reduce((total, item) => total + item.studentCount, 0)
  if (
    result.promoteCount !== toPromote.length ||
    result.archiveCount !== toArchive.length ||
    result.promotedStudentCount !== promotedStudents ||
    result.archivedStudentCount !== archivedStudents ||
    result.skippedGroupCount !== skipped.length ||
    result.skippedStudentCount !== skippedStudents ||
    result.conflictCount !== conflicts.length
  ) {
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
  const previousAction =
    record.previousAction === null || record.previousAction === undefined
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
    ? record.groupIds.map((id, index) =>
        positiveInteger(id, `promotion conflict.groupIds[${index}]`),
      )
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
  const trainingDurationYears = positiveInteger(
    input.trainingDurationYears,
    'trainingDurationYears',
  )
  const course = Number(numericCode[0])
  if (course < 1 || course > 5) throw new RangeError('Курс должен быть от 1 до 5.')
  if (course > trainingDurationYears) {
    throw new RangeError('Первая цифра цифрового кода должна быть не больше срока обучения')
  }
  return { alphabeticCode, numericCode, trainingDurationYears }
}

function requiredRecord(value: unknown, field: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value))
    throw new Error(`Сервер вернул некорректные данные ${field}.`)
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
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0)
    throw new RangeError(`${field} должен быть положительным числом`)
  return value
}

function nullablePositiveInteger(value: unknown, field: string): number | null {
  return value === null || value === undefined ? null : positiveInteger(value, field)
}

function nonNegativeInteger(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0)
    throw new Error(`Некорректное поле ${field}.`)
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

function nullableProgramTypeDigit(value: unknown): number | null {
  if (value === undefined || value === null) return null
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > 9)
    throw new Error('Неизвестный тип программы группы.')
  return value
}
function nullableGroupEnum<T extends string>(value: unknown, allowed: readonly T[]): T | null {
  if (value === undefined || value === null) return null
  if (typeof value !== 'string' || !allowed.includes(value as T))
    throw new Error('Неизвестные параметры программы группы.')
  return value as T
}

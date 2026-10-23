import { IdentityOperationError, IdentityOperations } from '../../shared/identity-operation'
import { parseReportFormats, type ReportFormatOption } from '../../shared/report-download-client'
import type { AdminAccessSelection } from '../admin-access/admin-access-client'
import type { MobileProblemDetails } from '../../api/types'

export type AdminManagedRole = 'STUDENT' | 'TEACHER' | 'ADMIN' | 'HEADMAN'
export type AdminRoleStatus =
  | 'ACTIVE'
  | 'EXPELLED'
  | 'GRADUATED'
  | 'SUSPENDED'
  | 'DISMISSED'
  | 'ARCHIVED'

export interface AdminRoleGrant {
  readonly role: AdminManagedRole
  readonly status: AdminRoleStatus
  readonly groupId: number | null
  readonly groupName: string | null
  readonly selectable: boolean
  readonly readOnly: boolean
  readonly applicableStatuses: readonly AdminRoleStatus[]
  readonly canUpdate: boolean
  readonly blockedReason: string | null
}

export interface AdminUser {
  readonly id: number
  readonly login: string
  readonly initialPassword?: string | null
  readonly lastName: string
  readonly firstName: string
  readonly middleName: string | null
  readonly fullName: string
  readonly role: 'STUDENT' | 'TEACHER' | 'ADMIN'
  readonly status: 'ACTIVE' | 'EXPELLED' | 'SUSPENDED' | 'ARCHIVED'
  readonly groupId: number | null
  readonly employeeNumber: string | null
  readonly telegramId: number | null
  readonly createdAt: string | null
  readonly roles: readonly AdminRoleGrant[]
}

export interface AdminUserGroupOption {
  readonly id: number
  readonly name: string
}
export interface AdminUserGroupOptionsPage {
  readonly items: readonly AdminUserGroupOption[]
  readonly number: number
  readonly size: number
  readonly totalElements: number
  readonly totalPages: number
}

export interface AdminUsersPage {
  readonly items: readonly AdminUser[]
  readonly number: number
  readonly size: number
  readonly totalElements: number
  readonly totalPages: number
}

export interface AdminUsersListInput {
  readonly groupId?: number
  readonly search?: string
  readonly role?: AdminManagedRole | ''
  readonly roleStatus?: AdminRoleStatus | ''
  readonly sortDirection?: 'asc' | 'desc'
  readonly page?: number
  readonly size?: number
}

export interface CreateAdminUserInput {
  readonly lastName: string
  readonly firstName: string
  readonly middleName?: string
  readonly adminAccess?: AdminAccessSelection
  readonly role: Exclude<AdminManagedRole, 'HEADMAN'>
  readonly groupId?: number
  readonly employeeNumber?: string
  readonly telegramId?: number
}

export interface UpdateAdminRoleInput {
  readonly status: AdminRoleStatus
  readonly groupId?: number
  readonly employeeNumber?: string
  readonly telegramId?: number
}

export interface UpdateAdminUserProfileInput {
  /** Positive Telegram ID; omission preserves the existing binding. */
  readonly telegramId?: number
  readonly lastName?: string
  readonly firstName?: string
  /** An empty string clears the patronymic. */
  readonly middleName?: string
  /** An empty string clears the value when the user has no TEACHER grant. */
  readonly employeeNumber?: string
}

export interface TransferAdminStudentInput {
  readonly newGroupId: number
  readonly reason: string
}

export interface AdminUserArchivePreview {
  readonly userId: number
  readonly linkedGroupCount: number
  readonly attendanceMarksCount: number
  readonly activeHeadmanGroupCount: number
  readonly soleTeacherAssignmentCount: number
  readonly requiresPassword: boolean
  readonly previewDigest: string
  readonly expiresAt: string
}
export interface ArchiveAdminUserInput {
  readonly operationId: string
  readonly previewDigest: string
  readonly password?: string
}

export interface AdminRecoveryLink {
  readonly url: string
  readonly expiresAt: string
  readonly expiresInSeconds: number
}

export interface AdminUsersApiOptions {
  readonly accessToken: () => string | null
  readonly sessionScopeFor?: () => string | null
  readonly onUnauthorized?: () => Promise<void>
  readonly assertCurrent?: () => void
  readonly fetcher?: typeof fetch
}

export interface IdentityRecoveryReference {
  readonly operationId: string
  readonly kind: 'USER' | 'ARCHIVE' | 'ADMIN_ACCESS'
  readonly userId: number | null
  readonly reconcileId: string | null
  readonly archivePreviewDigest: string | null
}
export function readIdentityRecovery(
  value: unknown,
  owner: 'USER' | 'ADMIN_ACCESS',
): IdentityRecoveryReference | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const v = value as Record<string, unknown>
  const uuid = (id: unknown) =>
    typeof id === 'string' &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)
  if (
    Object.keys(v).length !== 5 ||
    !uuid(v.operationId) ||
    !(owner === 'USER'
      ? ['USER', 'ARCHIVE'].includes(String(v.kind))
      : v.kind === 'ADMIN_ACCESS') ||
    !(
      (v.userId === null && v.kind === 'USER') ||
      (Number.isSafeInteger(v.userId) && Number(v.userId) > 0)
    ) ||
    !(v.reconcileId === null || uuid(v.reconcileId)) ||
    !(
      v.archivePreviewDigest === null ||
      (v.kind === 'ARCHIVE' &&
        typeof v.archivePreviewDigest === 'string' &&
        /^[0-9a-f]{64}$/.test(v.archivePreviewDigest))
    )
  )
    return null
  return {
    operationId: String(v.operationId),
    kind: v.kind as IdentityRecoveryReference['kind'],
    userId: v.userId as number | null,
    reconcileId: v.reconcileId as string | null,
    archivePreviewDigest: v.archivePreviewDigest as string | null,
  }
}
export async function confirmIdentityRecoverySuccess(response: Response): Promise<void> {
  const value = (await response.json()) as Record<string, unknown>
  if (
    Object.keys(value).length !== 3 ||
    value.status !== 'SUCCEEDED' ||
    value.retryAfterSeconds !== 0 ||
    typeof value.operationId !== 'string' ||
    value.operationId !== response.headers.get('X-Operation-Id')
  )
    throw new Error('Сервер не подтвердил восстановление операции.')
}

export class AdminUsersApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: MobileProblemDetails | null,
  ) {
    super(problem?.detail || problem?.title || `HTTP ${response.status}`)
    this.name = 'AdminUsersApiError'
  }
}

/** Real Academic user management client for JS-ADMIN-01/02/03. */
export class AdminUsersClient {
  private static readonly basePath = '/api/academic/users'
  private readonly fetcher: typeof fetch
  private identity: IdentityOperations
  private recovery: IdentityRecoveryReference | null = null
  private confirmedRecovery: string | null = null
  private intentContext: Pick<IdentityRecoveryReference, 'kind' | 'userId'> | null = null
  private recoveryScope: string | null = null
  private recoveryRevision = 0
  private persistRecovery: (value: IdentityRecoveryReference | null) => void = () => {}

  constructor(private readonly options: AdminUsersApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
    this.identity = this.createIdentityOperations()
  }
  private createIdentityOperations(): IdentityOperations {
    const revision = this.recoveryRevision
    return new IdentityOperations(
      (path, init) => this.requestResponse(path, init),
      () => {
        this.options.assertCurrent?.()
        if (revision !== this.recoveryRevision) throw new Error('Владелец операции изменился.')
      },
    )
  }
  getIdentityRecoverySessionScope(): string | null {
    this.options.assertCurrent?.()
    return this.options.sessionScopeFor?.() ?? null
  }
  bindIdentityRecovery(
    scope: string | null,
    saved: unknown,
    persist: (value: IdentityRecoveryReference | null) => void,
  ): void {
    if (scope !== this.recoveryScope) {
      ++this.recoveryRevision
      this.identity = this.createIdentityOperations()
      this.intentContext = null
      this.confirmedRecovery = null
      this.recovery = readIdentityRecovery(saved, 'USER')
      this.recoveryScope = scope
    }
    this.persistRecovery = persist
  }
  get failedIdentityOperation(): IdentityRecoveryReference | null {
    this.options.assertCurrent?.()
    return this.recovery ? { ...this.recovery } : null
  }
  private saveRecovery(): void {
    this.persistRecovery(this.recovery ? { ...this.recovery } : null)
  }
  async confirmIdentityRecovery(): Promise<IdentityRecoveryReference | null> {
    const saved = this.recovery
    if (!saved || saved.reconcileId) return null
    const status = await this.identity.inspect(saved.operationId)
    this.options.assertCurrent?.()
    if (this.recovery !== saved || status !== 'FAILED_SAFE') return null
    this.confirmedRecovery = saved.operationId
    return { ...saved }
  }
  async previewIdentityRecovery(signal?: AbortSignal): Promise<AdminUserArchivePreview> {
    const saved = this.recovery
    if (
      !saved ||
      saved.kind !== 'ARCHIVE' ||
      this.confirmedRecovery !== saved.operationId ||
      saved.userId === null
    )
      throw new Error('Сначала проверь состояние остановленной операции.')
    return this.readArchivePreview(
      `/api/academic/identity-operations/${saved.operationId}/reconcile-preview`,
      saved.userId,
      signal,
    )
  }
  async reconcileIdentityOperation(
    proof?: { archivePreviewDigest: string; password?: string },
    signal?: AbortSignal,
  ): Promise<void> {
    const revision = this.recoveryRevision
    const saved = this.recovery
    if (
      !saved ||
      saved.reconcileId ||
      this.confirmedRecovery !== saved.operationId ||
      this.identity.pending
    )
      throw new Error('Сначала проверь состояние остановленной операции.')
    if (saved.kind === 'ARCHIVE' && (!proof || !/^[0-9a-f]{64}$/.test(proof.archivePreviewDigest)))
      throw new Error('Обнови предпросмотр восстановления операции.')
    if (saved.kind !== 'ARCHIVE' && proof)
      throw new Error('Для этого действия подтверждение архива не требуется.')
    this.recovery = {
      ...saved,
      reconcileId: crypto.randomUUID(),
      archivePreviewDigest: proof?.archivePreviewDigest ?? null,
    }
    this.intentContext = saved
    this.saveRecovery() // Durable nonsecret reference precedes any HTTP send; proof is memory-only.
    try {
      const response = await this.identity.run(
        `/api/academic/identity-operations/${saved.operationId}/reconcile`,
        {
          method: 'POST',
          ...(proof ? { body: JSON.stringify(proof) } : {}),
          signal: signal ?? new AbortController().signal,
        },
        this.recovery.reconcileId!,
      )
      await confirmIdentityRecoverySuccess(response)
      this.options.assertCurrent?.()
    } catch (cause) {
      if (revision === this.recoveryRevision) this.captureFailedOperation(cause)
      throw cause
    }
  }
  private captureFailedOperation(cause: unknown): void {
    if (!(cause instanceof IdentityOperationError)) return
    if (cause.state === 'FAILED_SAFE' && cause.operationId && this.intentContext) {
      this.recovery = {
        ...this.intentContext,
        operationId: cause.operationId,
        reconcileId: null,
        archivePreviewDigest: null,
      }
      this.confirmedRecovery = null
      this.saveRecovery()
    } else if (cause.state === 'REJECTED' && this.recovery?.reconcileId) {
      this.recovery = { ...this.recovery, reconcileId: null, archivePreviewDigest: null }
      this.confirmedRecovery = null
      this.saveRecovery()
    }
  }
  private async runIdentity(path: string, init: RequestInit, id?: string): Promise<Response> {
    const revision = this.recoveryRevision
    if (!this.identity.pending) {
      if (this.recovery) throw new Error('Сначала восстанови остановленную операцию.')
      this.intentContext = {
        kind: path.endsWith('/archive') ? 'ARCHIVE' : 'USER',
        userId: Number(path.match(/users\/(\d+)/)?.[1]) || null,
      }
    }
    try {
      return await this.identity.run(path, init, id)
    } catch (cause) {
      if (revision === this.recoveryRevision) this.captureFailedOperation(cause)
      throw cause
    }
  }

  async listUsers(input: AdminUsersListInput = {}, signal?: AbortSignal): Promise<AdminUsersPage> {
    const params = new URLSearchParams()
    if (input.search?.trim()) params.set('search', input.search.trim())
    if (input.role) params.set('role', input.role)
    if (input.roleStatus) params.set('roleStatus', input.roleStatus)
    if (input.groupId !== undefined)
      params.set('groupId', String(positiveInteger(input.groupId, 'groupId')))
    if (input.sortDirection) {
      for (const field of ['lastName', 'firstName', 'middleName', 'id'])
        params.append('sort', `${field},${input.sortDirection}`)
    }
    params.set('page', String(input.page ?? 0))
    params.set('size', String(input.size ?? 20))
    const value = await this.request<unknown>(
      `${AdminUsersClient.basePath}?${params}`,
      signal ? { signal } : undefined,
    )
    return normalizePage(value)
  }

  async listGroups(
    search = '',
    page = 0,
    signal?: AbortSignal,
  ): Promise<AdminUserGroupOptionsPage> {
    const params = new URLSearchParams({
      page: String(nonNegativeInteger(page, 'page')),
      size: '20',
    })
    if (search.trim()) params.set('search', search.trim())
    const value = requiredRecord(
      await this.request<unknown>(
        `/api/academic/groups/options?${params}`,
        signal ? { signal } : undefined,
      ),
      'groups',
    )
    if (!Array.isArray(value.items)) throw new Error('Не удалось загрузить группы.')
    const number = nonNegativeInteger(value.number, 'groups.page')
    const totalPages = nonNegativeInteger(value.totalPages, 'groups.totalPages')
    if (
      number !== page ||
      (totalPages > 0 && number >= totalPages) ||
      (totalPages === 0 && number !== 0)
    )
      throw new Error('Не удалось подтвердить страницу групп.')
    return {
      items: value.items.map((item) => {
        const group = requiredRecord(item, 'group')
        return {
          id: positiveInteger(group.id, 'group.id'),
          name: requiredText(group.name, 'group.name'),
        }
      }),
      number,
      size: positiveInteger(value.size, 'groups.size'),
      totalElements: nonNegativeInteger(value.totalElements, 'groups.totalElements'),
      totalPages,
    }
  }

  getUser(id: number, signal?: AbortSignal): Promise<AdminUser> {
    positiveInteger(id, 'userId')
    return this.request<unknown>(
      `${AdminUsersClient.basePath}/${id}`,
      signal ? { signal } : undefined,
    ).then((value) => normalizeUser(value, true))
  }

  createUser(input: CreateAdminUserInput, signal?: AbortSignal): Promise<AdminUser> {
    const payload = normalizeCreateInput(input)
    return this.identityRequest(AdminUsersClient.basePath, {
      method: 'POST',
      body: JSON.stringify(payload),
      ...(signal ? { signal } : {}),
    }).then((value) => normalizeUser(value, true))
  }

  updateProfile(
    id: number,
    input: UpdateAdminUserProfileInput,
    signal?: AbortSignal,
  ): Promise<AdminUser> {
    positiveInteger(id, 'userId')
    const payload = normalizeProfileInput(input)
    return this.identityRequest(`${AdminUsersClient.basePath}/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
      ...(signal ? { signal } : {}),
    }).then(normalizeUser)
  }

  updateRole(
    id: number,
    role: Exclude<AdminManagedRole, 'HEADMAN'>,
    input: UpdateAdminRoleInput,
    signal?: AbortSignal,
  ): Promise<AdminUser> {
    positiveInteger(id, 'userId')
    const payload = normalizeRoleInput(input)
    return this.identityRequest(`${AdminUsersClient.basePath}/${id}/roles/${role}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
      ...(signal ? { signal } : {}),
    }).then(normalizeUser)
  }

  transferStudent(
    id: number,
    input: TransferAdminStudentInput,
    signal?: AbortSignal,
  ): Promise<AdminUser> {
    positiveInteger(id, 'userId')
    const newGroupId = positiveInteger(input.newGroupId, 'newGroupId')
    const reason = requiredText(input.reason, 'reason')
    return this.identityRequest(`${AdminUsersClient.basePath}/${id}/transfer`, {
      method: 'POST',
      body: JSON.stringify({ newGroupId, reason }),
      ...(signal ? { signal } : {}),
    }).then(normalizeUser)
  }

  async previewArchive(id: number, signal?: AbortSignal): Promise<AdminUserArchivePreview> {
    positiveInteger(id, 'userId')
    return this.readArchivePreview(`${AdminUsersClient.basePath}/${id}/archive-preview`, id, signal)
  }
  private async readArchivePreview(
    path: string,
    id: number,
    signal?: AbortSignal,
  ): Promise<AdminUserArchivePreview> {
    const value = requiredRecord(
      await this.request<unknown>(path, {
        cache: 'no-store',
        ...(signal ? { signal } : {}),
      }),
      'archive preview',
    )
    const userId = positiveInteger(value.userId, 'preview.userId')
    if (userId !== id) throw new Error('Не удалось подтвердить данные пользователя.')
    const previewDigest = requiredText(value.previewDigest, 'preview.digest')
    const expiresAt = requiredText(value.expiresAt, 'preview.expiresAt')
    if (!/^[0-9a-f]{64}$/.test(previewDigest) || !Number.isFinite(Date.parse(expiresAt)))
      throw new Error('Не удалось подтвердить предпросмотр архива.')
    return {
      userId,
      previewDigest,
      expiresAt,
      linkedGroupCount: nonNegativeInteger(value.linkedGroupCount, 'preview.groups'),
      attendanceMarksCount: nonNegativeInteger(value.attendanceMarksCount, 'preview.marks'),
      activeHeadmanGroupCount: nonNegativeInteger(value.activeHeadmanGroupCount, 'preview.headman'),
      soleTeacherAssignmentCount: nonNegativeInteger(
        value.soleTeacherAssignmentCount,
        'preview.teacher',
      ),
      requiresPassword: requiredBoolean(value.requiresPassword, 'preview.requiresPassword'),
    }
  }

  async archiveUser(id: number, input: ArchiveAdminUserInput, signal?: AbortSignal): Promise<void> {
    positiveInteger(id, 'userId')
    operationId(input.operationId)
    if (!/^[0-9a-f]{64}$/.test(input.previewDigest)) throw new Error('Обнови предпросмотр архива.')
    await this.runIdentity(
      `${AdminUsersClient.basePath}/${id}/archive`,
      {
        method: 'POST',
        cache: 'no-store',
        body: JSON.stringify(input),
        ...(signal ? { signal } : {}),
      },
      input.operationId,
    )
  }

  async restoreUser(id: number, idempotencyId: string, signal?: AbortSignal): Promise<void> {
    positiveInteger(id, 'userId')
    operationId(idempotencyId)
    await this.runIdentity(
      `${AdminUsersClient.basePath}/${id}/restore`,
      {
        method: 'POST',
        cache: 'no-store',
        body: JSON.stringify({ operationId: idempotencyId }),
        ...(signal ? { signal } : {}),
      },
      idempotencyId,
    )
  }

  async issueRecoveryLink(id: number, signal?: AbortSignal): Promise<AdminRecoveryLink> {
    positiveInteger(id, 'userId')
    const value = requiredRecord(
      await this.request<unknown>(`/api/auth/admin/users/${id}/password-reset-link`, {
        method: 'POST',
        cache: 'no-store',
        ...(signal ? { signal } : {}),
      }),
      'recovery link',
    )
    const url = requiredText(value.url, 'recovery link.url')
    const expiresAt = requiredText(value.expiresAt, 'recovery link.expiresAt')
    const parsed = new URL(url)
    const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname)
    if (
      (parsed.protocol !== 'https:' && !(loopback && parsed.protocol === 'http:')) ||
      parsed.username ||
      parsed.password ||
      parsed.search ||
      !parsed.hash ||
      !/\/password-reset\/?$/.test(parsed.pathname) ||
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(expiresAt) ||
      !Number.isFinite(Date.parse(expiresAt))
    )
      throw new Error('Сервер вернул некорректную ссылку восстановления.')
    return {
      url,
      expiresAt,
      expiresInSeconds: positiveInteger(value.expiresInSeconds, 'recovery link.expiresInSeconds'),
    }
  }

  async exportFormats(): Promise<readonly ReportFormatOption[]> {
    return parseReportFormats(
      await this.request<unknown>('/api/academic/registry-exports/users/formats'),
    )
  }

  acknowledgeIdentityOperation(): void {
    this.identity.acknowledge()
    if (!this.identity.pending) {
      this.recovery = null
      this.confirmedRecovery = null
      this.saveRecovery()
    }
  }

  get hasPendingIdentityOperation(): boolean {
    return this.identity.pending || Boolean(this.recovery?.reconcileId)
  }

  async resumeIdentityOperation(): Promise<{
    user: AdminUser | null
    created: boolean
    recovered?: boolean
  }> {
    const revision = this.recoveryRevision
    let result: Awaited<ReturnType<IdentityOperations['resume']>>
    try {
      const saved = this.recovery
      if (!this.identity.pending && saved?.reconcileId) {
        const status = await this.identity.inspect(saved.reconcileId)
        if (this.recovery !== saved) throw new Error('Владелец операции изменился.')
        this.intentContext = saved
        if (status === 'REJECTED' || status === 'FAILED_SAFE')
          throw new IdentityOperationError(status, saved.reconcileId)
        const path = `/api/academic/identity-operations/${saved.operationId}/reconcile`
        const response = await this.identity.run(
          path,
          {
            method: 'POST',
            signal: new AbortController().signal,
            ...(saved.archivePreviewDigest
              ? { body: JSON.stringify({ archivePreviewDigest: saved.archivePreviewDigest }) }
              : {}),
          },
          saved.reconcileId,
        )
        result = { path, response }
      } else result = await this.identity.resume()
    } catch (cause) {
      if (revision === this.recoveryRevision) this.captureFailedOperation(cause)
      throw cause
    }
    const { path, response } = result
    if (path.endsWith('/reconcile')) {
      await confirmIdentityRecoverySuccess(response)
      this.options.assertCurrent?.()
      return { user: null, created: false, recovered: true }
    }
    if (path.endsWith('/archive') || path.endsWith('/restore'))
      return { user: null, created: false }
    const value = (await response.json()) as unknown
    this.options.assertCurrent?.()
    return {
      user: normalizeUser(value, path === AdminUsersClient.basePath),
      created: path === AdminUsersClient.basePath,
    }
  }

  private async identityRequest(path: string, init: RequestInit): Promise<unknown> {
    const response = await this.runIdentity(path, init)
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
    throw new AdminUsersApiError(response, problem)
  }
}

function normalizePage(value: unknown): AdminUsersPage {
  if (Array.isArray(value)) {
    return {
      items: value.map((item) => normalizeUser(item)),
      number: 0,
      size: value.length,
      totalElements: value.length,
      totalPages: 1,
    }
  }
  const record = requiredRecord(value, 'users page')
  const embedded = record._embedded
  const items = embedded === undefined || embedded === null ? [] : readEmbeddedUsers(embedded)
  const page = requiredRecord(record.page, 'users page.page')
  const number = nonNegativeInteger(page.number, 'users page.number')
  const size = positiveInteger(page.size, 'users page.size')
  const totalElements = nonNegativeInteger(page.totalElements, 'users page.totalElements')
  const totalPages = nonNegativeInteger(page.totalPages, 'users page.totalPages')
  if (totalPages === 0 && number !== 0)
    throw new Error('Сервер вернул некорректную страницу пользователей.')
  if (totalPages > 0 && number >= totalPages)
    throw new Error('Сервер вернул некорректную страницу пользователей.')
  return { items, number, size, totalElements, totalPages }
}

function readEmbeddedUsers(value: unknown): readonly AdminUser[] {
  const embedded = requiredRecord(value, 'users page._embedded')
  const collection = Object.values(embedded).find(Array.isArray)
  if (collection === undefined) return []
  return (collection as unknown[]).map((item) => normalizeUser(item))
}

function normalizeUser(value: unknown, credentials = false): AdminUser {
  const record = requiredRecord(value, 'user')
  const lastName = requiredText(record.lastName, 'user.lastName')
  const firstName = requiredText(record.firstName, 'user.firstName')
  const middleName = nullableText(record.middleName)
  const rolesValue = record.roles
  const roles = Array.isArray(rolesValue) ? rolesValue.map(normalizeRoleGrant) : []
  return {
    id: positiveInteger(record.id, 'user.id'),
    login: requiredText(record.login, 'user.login'),
    initialPassword: credentials ? nullableText(record.initialPassword) : null,
    lastName,
    firstName,
    middleName,
    fullName: requiredText(
      record.fullName ?? [lastName, firstName, middleName].filter(Boolean).join(' '),
      'user.fullName',
    ),
    role: managedLegacyRole(record.role),
    status: accountStatus(record.status),
    groupId: nullablePositiveInteger(record.groupId, 'user.groupId'),
    employeeNumber: nullableText(record.employeeNumber),
    telegramId: nullablePositiveInteger(record.telegramId, 'user.telegramId'),
    createdAt: nullableText(record.createdAt),
    roles,
  }
}

function normalizeRoleGrant(value: unknown): AdminRoleGrant {
  const record = requiredRecord(value, 'user role')
  const role = roleValue(record.role)
  const status = roleStatus(record.status)
  const applicable = Array.isArray(record.applicableStatuses)
    ? record.applicableStatuses.map((item) => roleStatus(item))
    : []
  return {
    role,
    status,
    groupId: nullablePositiveInteger(record.groupId, 'user role.groupId'),
    groupName: nullableText(record.groupName),
    selectable: requiredBoolean(record.selectable, 'user role.selectable'),
    readOnly: requiredBoolean(record.readOnly, 'user role.readOnly'),
    applicableStatuses: applicable,
    canUpdate: requiredBoolean(record.canUpdate, 'user role.canUpdate'),
    blockedReason: nullableText(record.blockedReason),
  }
}

function normalizeCreateInput(input: CreateAdminUserInput): CreateAdminUserInput {
  const role = input.role
  const normalized: CreateAdminUserInput = {
    lastName: requiredText(input.lastName, 'lastName'),
    firstName: requiredText(input.firstName, 'firstName'),
    role,
    ...(role === 'ADMIN' && input.adminAccess ? { adminAccess: input.adminAccess } : {}),
    ...(nullableText(input.middleName) ? { middleName: nullableText(input.middleName)! } : {}),
    ...(input.groupId === undefined ? {} : { groupId: positiveInteger(input.groupId, 'groupId') }),
    ...(input.employeeNumber === undefined
      ? {}
      : { employeeNumber: requiredText(input.employeeNumber, 'employeeNumber') }),
    ...(input.telegramId === undefined
      ? {}
      : { telegramId: positiveInteger(input.telegramId, 'telegramId') }),
  }
  if (
    role === 'ADMIN' &&
    (!normalized.adminAccess ||
      (normalized.adminAccess.mode === 'LIMITED' && !normalized.adminAccess.permissions.length) ||
      (normalized.adminAccess.mode === 'FULL' && normalized.adminAccess.permissions.length))
  )
    throw new RangeError('Выбери права администратора.')
  if (
    role === 'STUDENT' &&
    (normalized.groupId === undefined || normalized.telegramId === undefined)
  ) {
    throw new RangeError('Для STUDENT нужны группа и Telegram ID')
  }
  if (role === 'TEACHER' && normalized.employeeNumber === undefined) {
    throw new RangeError('Для TEACHER нужен табельный номер')
  }
  return normalized
}

function normalizeRoleInput(input: UpdateAdminRoleInput): UpdateAdminRoleInput {
  return {
    status: roleStatus(input.status),
    ...(input.groupId === undefined ? {} : { groupId: positiveInteger(input.groupId, 'groupId') }),
    ...(input.employeeNumber === undefined
      ? {}
      : { employeeNumber: requiredText(input.employeeNumber, 'employeeNumber') }),
    ...(input.telegramId === undefined
      ? {}
      : { telegramId: positiveInteger(input.telegramId, 'telegramId') }),
  }
}

function normalizeProfileInput(input: UpdateAdminUserProfileInput): UpdateAdminUserProfileInput {
  const payload: {
    telegramId?: number
    lastName?: string
    firstName?: string
    middleName?: string
    employeeNumber?: string
  } = {}
  if (input.lastName !== undefined) payload.lastName = profileName(input.lastName, 'lastName')
  if (input.firstName !== undefined) payload.firstName = profileName(input.firstName, 'firstName')
  if (input.middleName !== undefined)
    payload.middleName = profileOptionalText(input.middleName, 128, 'middleName')
  if (input.employeeNumber !== undefined)
    payload.employeeNumber = profileOptionalText(input.employeeNumber, 32, 'employeeNumber')
  if (input.telegramId !== undefined)
    payload.telegramId = positiveInteger(input.telegramId, 'telegramId')
  if (Object.keys(payload).length === 0) throw new RangeError('Изменения профиля не заданы')
  return payload
}

function profileName(value: string, field: string): string {
  const normalized = value.trim()
  if (!normalized) throw new RangeError(`${field} не может быть пустым`)
  if (normalized.length > 128) throw new RangeError(`${field} не может быть длиннее 128 символов`)
  return normalized
}

function profileOptionalText(value: string, maxLength: number, field: string): string {
  const normalized = value.trim()
  if (normalized.length > maxLength)
    throw new RangeError(`${field} не может быть длиннее ${maxLength} символов`)
  return normalized
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

function requiredBoolean(value: unknown, field: string): boolean {
  if (typeof value !== 'boolean') throw new Error(`Некорректное поле ${field}.`)
  return value
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

function roleValue(value: unknown): AdminManagedRole {
  if (value === 'STUDENT' || value === 'TEACHER' || value === 'ADMIN' || value === 'HEADMAN')
    return value
  throw new Error('Сервер вернул неизвестную роль пользователя.')
}

function managedLegacyRole(value: unknown): 'STUDENT' | 'TEACHER' | 'ADMIN' {
  if (value === 'STUDENT' || value === 'TEACHER' || value === 'ADMIN') return value
  throw new Error('Сервер вернул неизвестную базовую роль пользователя.')
}

function roleStatus(value: unknown): AdminRoleStatus {
  if (
    value === 'ACTIVE' ||
    value === 'EXPELLED' ||
    value === 'GRADUATED' ||
    value === 'SUSPENDED' ||
    value === 'DISMISSED' ||
    value === 'ARCHIVED'
  )
    return value
  throw new Error('Сервер вернул неизвестный статус роли.')
}

function accountStatus(value: unknown): AdminUser['status'] {
  if (value === 'ACTIVE' || value === 'EXPELLED' || value === 'SUSPENDED' || value === 'ARCHIVED')
    return value
  throw new Error('Сервер вернул неизвестный статус пользователя.')
}

function operationId(value: string): void {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value))
    throw new RangeError('Некорректное подтверждение операции.')
}

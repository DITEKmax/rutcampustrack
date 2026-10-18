import {
  readIdentityRecovery,
  confirmIdentityRecoverySuccess,
  type IdentityRecoveryReference,
} from '../admin-users/admin-users-client'
import { IdentityOperationError, IdentityOperations } from '../../shared/identity-operation'
import type { MobileProblemDetails } from '../../api/types'
export const adminPermissionCodes = [
  'DASHBOARD_VIEW',
  'USERS_VIEW',
  'USERS_MANAGE',
  'USER_CREDENTIALS_MANAGE',
  'USERS_ARCHIVE',
  'GROUPS_VIEW',
  'GROUPS_MANAGE',
  'SEMESTERS_VIEW',
  'SEMESTERS_MANAGE',
  'CAMPUS_MAP_MANAGE',
  'DATA_DELETE',
  'ADMIN_ACCESS_MANAGE',
] as const
const knownPermission = (code: string) => (adminPermissionCodes as readonly string[]).includes(code)
export type AdminAccessMode = 'FULL' | 'LIMITED'
export interface AdminPermission {
  readonly code: string
  readonly label: string
  readonly group: string
  readonly requires: readonly string[]
  readonly fullOnly: boolean
  readonly description: string
}
export interface AdminAccessCatalog {
  readonly permissions: readonly AdminPermission[]
}
export interface AdminAccessProfile {
  readonly userId: number
  readonly assigned: boolean
  readonly mode: AdminAccessMode | null
  readonly permissions: readonly string[]
  readonly version: number
}
export interface AdminAccessSelection {
  readonly mode: AdminAccessMode
  readonly permissions: readonly string[]
}
export interface AdminAccessUpdate extends AdminAccessSelection {
  readonly expectedVersion: number
}
export interface AdminAccessOptions {
  readonly accessToken: () => string | null
  readonly sessionScopeFor?: () => string | null
  readonly onUnauthorized?: () => Promise<void>
  readonly assertCurrent?: () => void
  readonly fetcher?: typeof fetch
}
export function adminHasPermission(
  profile: AdminAccessProfile | null | undefined,
  code: string,
): boolean {
  return (
    knownPermission(code) &&
    profile?.assigned === true &&
    (profile.mode === 'FULL' || (profile.mode === 'LIMITED' && profile.permissions.includes(code)))
  )
}
export class AdminAccessApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: MobileProblemDetails | null,
    readonly currentProfile: AdminAccessProfile | null = null,
  ) {
    super(problem?.detail ?? 'Не удалось обновить права доступа.')
    this.name = 'AdminAccessApiError'
  }
}
export interface AdminAccessOperationResult {
  readonly userId: number
  readonly revoked: boolean
  readonly profile: AdminAccessProfile | null
  readonly recovered?: boolean
}
export class AdminAccessClient {
  private readonly fetcher: typeof fetch
  private identity: IdentityOperations
  private recovery: IdentityRecoveryReference | null = null
  private confirmedRecovery: string | null = null
  private recoveryScope: string | null = null
  private recoveryRevision = 0
  private persistRecovery: (value: IdentityRecoveryReference | null) => void = () => {}
  private operation: { userId: number; revoked: boolean } | null = null
  private mutationError: AdminAccessApiError | null = null
  constructor(private readonly options: AdminAccessOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
    this.identity = this.createIdentityOperations()
  }
  private createIdentityOperations(): IdentityOperations {
    const revision = this.recoveryRevision
    return new IdentityOperations(
      (path, init) => this.requestResponse(path, init),
      () => {
        try {
          this.options.assertCurrent?.()
          if (revision !== this.recoveryRevision) throw new Error('Владелец операции изменился.')
        } catch (cause) {
          if (revision === this.recoveryRevision) {
            this.operation = null
            this.mutationError = null
          }
          throw cause
        }
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
      this.operation = null
      this.mutationError = null
      this.confirmedRecovery = null
      this.recovery = readIdentityRecovery(saved, 'ADMIN_ACCESS')
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
  async reconcileIdentityOperation(signal?: AbortSignal): Promise<void> {
    const revision = this.recoveryRevision
    const saved = this.recovery
    if (
      !saved ||
      saved.reconcileId ||
      this.confirmedRecovery !== saved.operationId ||
      this.identity.pending
    )
      throw new Error('Сначала проверь состояние остановленной операции.')
    this.recovery = { ...saved, reconcileId: crypto.randomUUID() }
    this.operation = { userId: saved.userId!, revoked: false }
    this.mutationError = null
    this.saveRecovery()
    try {
      const response = await this.identity.run(
        `/api/academic/identity-operations/${saved.operationId}/reconcile`,
        {
          method: 'POST',
          signal: signal ?? new AbortController().signal,
        },
        this.recovery.reconcileId!,
      )
      await confirmIdentityRecoverySuccess(response)
      this.options.assertCurrent?.()
    } catch (cause) {
      if (revision !== this.recoveryRevision) throw cause
      this.rethrowOperationError(cause)
    }
  }

  async getCatalog(signal?: AbortSignal): Promise<AdminAccessCatalog> {
    const value = record(
      await this.request('/api/academic/admin-access/catalog', signal ? { signal } : undefined),
    )
    if (!Array.isArray(value.permissions)) throw new Error('Не удалось загрузить доступные права.')
    const permissions = value.permissions.map((item) => {
      const permission = record(item)
      if (typeof permission.fullOnly !== 'boolean' || !Array.isArray(permission.requires))
        throw new Error('Не удалось загрузить доступные права.')
      return {
        code: textValue(permission.code),
        label: textValue(permission.label),
        group: textValue(permission.group),
        requires: permission.requires.map(textValue),
        fullOnly: permission.fullOnly,
        description: textValue(permission.description),
      }
    })
    if (
      new Set(permissions.map((item) => item.code)).size !== permissions.length ||
      permissions.some((item) =>
        item.requires.some((code) => !permissions.some((candidate) => candidate.code === code)),
      )
    )
      throw new Error('Не удалось подтвердить состав прав.')
    return { permissions }
  }
  async getMe(signal?: AbortSignal): Promise<AdminAccessProfile> {
    return normalizeAdminAccessProfile(
      await this.request('/api/academic/admin-access/me', signal ? { signal } : undefined),
    )
  }
  async getUserAccess(userId: number, signal?: AbortSignal): Promise<AdminAccessProfile> {
    const profile = normalizeAdminAccessProfile(
      await this.request(this.path(userId), signal ? { signal } : undefined),
    )
    this.options.assertCurrent?.()
    if (profile.userId !== userId) throw new Error('Не удалось подтвердить пользователя.')
    return profile
  }
  async updateUserAccess(
    userId: number,
    input: AdminAccessUpdate,
    signal?: AbortSignal,
  ): Promise<AdminAccessProfile> {
    if (
      !Number.isSafeInteger(input.expectedVersion) ||
      input.expectedVersion < 0 ||
      !['FULL', 'LIMITED'].includes(input.mode) ||
      (input.mode === 'FULL' && input.permissions.length) ||
      (input.mode === 'LIMITED' && !input.permissions.length)
    )
      throw new Error('Проверь выбранные права доступа.')
    const profile = normalizeAdminAccessProfile(
      await this.mutate(userId, {
        method: 'PUT',
        body: JSON.stringify(input),
        ...(signal ? { signal } : {}),
      }),
    )
    this.options.assertCurrent?.()
    if (profile.userId !== userId) throw new Error('Не удалось подтвердить пользователя.')
    return profile
  }
  async revokeUserAccess(
    userId: number,
    expectedVersion: number,
    signal?: AbortSignal,
  ): Promise<void> {
    if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0)
      throw new Error('Обнови текущие права доступа.')
    await this.mutate(userId, {
      method: 'DELETE',
      body: JSON.stringify({ expectedVersion }),
      ...(signal ? { signal } : {}),
    })
  }
  get hasPendingIdentityOperation(): boolean {
    return this.identity.pending || Boolean(this.recovery?.reconcileId)
  }
  get identityOperationUserId(): number | null {
    void this.identity.pending // Apply the same session guard before exposing transient context.
    return this.operation?.userId ?? this.recovery?.userId ?? null
  }
  acknowledgeIdentityOperation(): void {
    this.identity.acknowledge()
    if (!this.identity.pending) {
      this.operation = null
      this.mutationError = null
      this.recovery = null
      this.confirmedRecovery = null
      this.saveRecovery()
    }
  }
  async resumeIdentityOperation(): Promise<AdminAccessOperationResult> {
    const revision = this.recoveryRevision
    try {
      const saved = this.recovery
      let result: Awaited<ReturnType<IdentityOperations['resume']>>
      if (!this.identity.pending && saved?.reconcileId) {
        const status = await this.identity.inspect(saved.reconcileId)
        if (this.recovery !== saved) throw new Error('Владелец операции изменился.')
        if (status === 'REJECTED' || status === 'FAILED_SAFE')
          throw new IdentityOperationError(status, saved.reconcileId)
        const path = `/api/academic/identity-operations/${saved.operationId}/reconcile`
        result = {
          path,
          response: await this.identity.run(
            path,
            { method: 'POST', signal: new AbortController().signal },
            saved.reconcileId,
          ),
        }
      } else result = await this.identity.resume()
      const { path, response } = result
      if (path.endsWith('/reconcile')) {
        await confirmIdentityRecoverySuccess(response)
        this.options.assertCurrent?.()
        return {
          userId: saved?.userId ?? this.operation!.userId,
          revoked: false,
          profile: null,
          recovered: true,
        }
      }
      const operation = this.operation
      if (!operation) throw new Error('Нет незавершённого действия.')
      const profile = operation.revoked ? null : normalizeAdminAccessProfile(await response.json())
      this.options.assertCurrent?.()
      if (profile && profile.userId !== operation.userId)
        throw new Error('Не удалось подтвердить пользователя.')
      return { ...operation, profile }
    } catch (cause) {
      if (revision !== this.recoveryRevision) throw cause
      this.rethrowOperationError(cause)
    }
  }
  private async mutate(userId: number, init: RequestInit): Promise<unknown> {
    const revision = this.recoveryRevision
    const path = this.path(userId)
    if (!this.identity.pending) {
      if (this.recovery) throw new Error('Сначала восстанови остановленную операцию.')
      this.operation = { userId, revoked: init.method === 'DELETE' }
      this.mutationError = null
    }
    try {
      const response = await this.identity.run(path, init)
      const value: unknown = response.status === 204 ? null : await response.json()
      this.options.assertCurrent?.()
      return value
    } catch (cause) {
      if (revision !== this.recoveryRevision) throw cause
      this.rethrowOperationError(cause)
    }
  }
  private rethrowOperationError(cause: unknown): never {
    // Preserve the original denial/CAS body, without treating its HTTP status as
    // proof that an earlier same-key attempt had no effect.
    if (cause instanceof IdentityOperationError) {
      if (cause.state === 'FAILED_SAFE' && cause.operationId && (this.recovery || this.operation)) {
        this.recovery = {
          operationId: cause.operationId,
          kind: 'ADMIN_ACCESS',
          userId: this.recovery?.userId ?? this.operation!.userId,
          reconcileId: null,
          archivePreviewDigest: null,
        }
        this.confirmedRecovery = null
        this.saveRecovery()
        throw cause // Never erase a recoverable terminal UUID behind an earlier HTTP denial.
      }
      if (cause.state === 'REJECTED' && this.recovery?.reconcileId) {
        this.recovery = { ...this.recovery, reconcileId: null }
        this.confirmedRecovery = null
        this.saveRecovery()
      }
      if (this.mutationError) throw this.mutationError
    }
    throw cause
  }
  private path(userId: number): string {
    if (!Number.isSafeInteger(userId) || userId <= 0) throw new Error('Пользователь недоступен.')
    return `/api/academic/users/${userId}/admin-access`
  }
  private async request(path: string, init?: RequestInit): Promise<unknown> {
    const response = await this.requestResponse(path, init)
    const value: unknown = response.status === 204 ? null : await response.json()
    this.options.assertCurrent?.()
    return value
  }
  private async requestResponse(
    path: string,
    init?: RequestInit,
    retried = false,
  ): Promise<Response> {
    this.options.assertCurrent?.()
    const headers = new Headers(init?.headers)
    headers.set('Accept', 'application/json')
    if (init?.body) headers.set('Content-Type', 'application/json')
    const token = this.options.accessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, {
      ...init,
      headers,
      credentials: 'include',
      cache: 'no-store',
    })
    this.options.assertCurrent?.()
    if (response.ok) return response
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      this.options.assertCurrent?.()
      return this.requestResponse(path, init, true)
    }
    let problem: MobileProblemDetails | null = null,
      currentProfile: AdminAccessProfile | null = null
    try {
      const value = record(await response.json())
      problem = value as MobileProblemDetails
      if (value.currentProfile) {
        const profile = normalizeAdminAccessProfile(value.currentProfile)
        const target = path.match(/users\/(\d+)\/admin-access$/)?.[1]
        if (!target || profile.userId === Number(target)) currentProfile = profile
      }
    } catch {
      /* HTTP status remains authoritative. */
    }
    this.options.assertCurrent?.()
    const cause = new AdminAccessApiError(response, problem, currentProfile)
    if (init?.method === 'PUT' || init?.method === 'DELETE') this.mutationError = cause
    throw cause
  }
}
export function normalizeAdminAccessProfile(value: unknown): AdminAccessProfile {
  const profile = record(value)
  if (
    !Number.isSafeInteger(profile.userId) ||
    Number(profile.userId) <= 0 ||
    typeof profile.assigned !== 'boolean' ||
    !Number.isSafeInteger(profile.version) ||
    Number(profile.version) < 0 ||
    !Array.isArray(profile.permissions) ||
    ![null, 'FULL', 'LIMITED'].includes(profile.mode as null | string)
  )
    throw new Error('Не удалось подтвердить права доступа.')
  const permissions = profile.permissions.map(textValue)
  if (
    new Set(permissions).size !== permissions.length ||
    permissions.some((code) => !knownPermission(code)) ||
    (profile.mode === 'LIMITED' &&
      (!permissions.length || permissions.includes('ADMIN_ACCESS_MANAGE'))) ||
    (profile.mode === 'FULL' && permissions.length !== adminPermissionCodes.length)
  )
    throw new Error('Не удалось подтвердить состав прав доступа.')
  if (profile.assigned && Number(profile.version) < 1)
    throw new Error('Не удалось подтвердить версию прав доступа.')
  if (
    (profile.assigned && profile.mode === null) ||
    (!profile.assigned && (profile.mode !== null || permissions.length))
  )
    throw new Error('Не удалось подтвердить права доступа.')
  return {
    userId: Number(profile.userId),
    assigned: profile.assigned,
    mode: profile.mode as AdminAccessMode | null,
    permissions,
    version: Number(profile.version),
  }
}
function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('Не удалось прочитать права доступа.')
  return value as Record<string, unknown>
}
function textValue(value: unknown): string {
  if (typeof value !== 'string' || !value.trim())
    throw new Error('Не удалось прочитать права доступа.')
  return value
}

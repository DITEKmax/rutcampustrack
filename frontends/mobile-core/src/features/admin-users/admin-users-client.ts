import type { MobileProblemDetails } from '../../api/types'

export type AdminManagedRole = 'STUDENT' | 'TEACHER' | 'ADMIN' | 'HEADMAN'
export type AdminRoleStatus = 'ACTIVE' | 'EXPELLED' | 'GRADUATED' | 'SUSPENDED' | 'DISMISSED' | 'ARCHIVED'

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

export interface AdminUsersPage {
  readonly items: readonly AdminUser[]
  readonly number: number
  readonly size: number
  readonly totalElements: number
  readonly totalPages: number
}

export interface AdminUsersListInput {
  readonly search?: string
  readonly role?: AdminManagedRole | ''
  readonly roleStatus?: AdminRoleStatus | ''
  readonly page?: number
  readonly size?: number
}

export interface CreateAdminUserInput {
  readonly lastName: string
  readonly firstName: string
  readonly middleName?: string
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

export interface AdminRecoveryLink {
  readonly url: string
  readonly expiresAt: string
  readonly expiresInSeconds: number
}

export interface AdminUsersApiOptions {
  readonly accessToken: () => string | null
  readonly onUnauthorized?: () => Promise<void>
  readonly assertCurrent?: () => void
  readonly fetcher?: typeof fetch
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

  constructor(private readonly options: AdminUsersApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  async listUsers(input: AdminUsersListInput = {}, signal?: AbortSignal): Promise<AdminUsersPage> {
    const params = new URLSearchParams()
    if (input.search?.trim()) params.set('search', input.search.trim())
    if (input.role) params.set('role', input.role)
    if (input.roleStatus) params.set('roleStatus', input.roleStatus)
    params.set('page', String(input.page ?? 0))
    params.set('size', String(input.size ?? 20))
    const value = await this.request<unknown>(`${AdminUsersClient.basePath}?${params}`, signal ? { signal } : undefined)
    return normalizePage(value)
  }

  getUser(id: number, signal?: AbortSignal): Promise<AdminUser> {
    positiveInteger(id, 'userId')
    return this.request<unknown>(`${AdminUsersClient.basePath}/${id}`, signal ? { signal } : undefined)
      .then(normalizeUser)
  }

  createUser(input: CreateAdminUserInput, signal?: AbortSignal): Promise<AdminUser> {
    const payload = normalizeCreateInput(input)
    return this.request<unknown>(AdminUsersClient.basePath, {
      method: 'POST',
      body: JSON.stringify(payload),
      ...(signal ? { signal } : {}),
    }).then(normalizeUser)
  }

  updateProfile(id: number, input: UpdateAdminUserProfileInput, signal?: AbortSignal): Promise<AdminUser> {
    positiveInteger(id, 'userId')
    const payload = normalizeProfileInput(input)
    return this.request<unknown>(`${AdminUsersClient.basePath}/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(payload),
      ...(signal ? { signal } : {}),
    }).then(normalizeUser)
  }

  updateRole(id: number, role: Exclude<AdminManagedRole, 'HEADMAN'>, input: UpdateAdminRoleInput, signal?: AbortSignal): Promise<AdminUser> {
    positiveInteger(id, 'userId')
    const payload = normalizeRoleInput(input)
    return this.request<unknown>(`${AdminUsersClient.basePath}/${id}/roles/${role}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
      ...(signal ? { signal } : {}),
    }).then(normalizeUser)
  }

  transferStudent(id: number, input: TransferAdminStudentInput, signal?: AbortSignal): Promise<AdminUser> {
    positiveInteger(id, 'userId')
    const newGroupId = positiveInteger(input.newGroupId, 'newGroupId')
    const reason = requiredText(input.reason, 'reason')
    return this.request<unknown>(`${AdminUsersClient.basePath}/${id}/transfer`, {
      method: 'POST',
      body: JSON.stringify({ newGroupId, reason }),
      ...(signal ? { signal } : {}),
    }).then(normalizeUser)
  }

  async issueRecoveryLink(id: number, signal?: AbortSignal): Promise<AdminRecoveryLink> {
    positiveInteger(id, 'userId')
    const value = requiredRecord(await this.request<unknown>(`/api/auth/admin/users/${id}/password-reset-link`, {
      method: 'POST',
      cache: 'no-store',
      ...(signal ? { signal } : {}),
    }), 'recovery link')
    const url = requiredText(value.url, 'recovery link.url')
    const expiresAt = requiredText(value.expiresAt, 'recovery link.expiresAt')
    const parsed = new URL(url)
    const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname)
    if ((parsed.protocol !== 'https:' && !(loopback && parsed.protocol === 'http:'))
      || parsed.username || parsed.password || parsed.search || !parsed.hash
      || !/\/password-reset\/?$/.test(parsed.pathname)
      || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(expiresAt)
      || !Number.isFinite(Date.parse(expiresAt))) throw new Error('Сервер вернул некорректную ссылку восстановления.')
    return { url, expiresAt, expiresInSeconds: positiveInteger(value.expiresInSeconds, 'recovery link.expiresInSeconds') }
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
    throw new AdminUsersApiError(response, problem)
  }
}

function normalizePage(value: unknown): AdminUsersPage {
  if (Array.isArray(value)) {
    return { items: value.map(normalizeUser), number: 0, size: value.length, totalElements: value.length, totalPages: 1 }
  }
  const record = requiredRecord(value, 'users page')
  const embedded = record._embedded
  const items = embedded === undefined || embedded === null ? [] : readEmbeddedUsers(embedded)
  const page = requiredRecord(record.page, 'users page.page')
  const number = nonNegativeInteger(page.number, 'users page.number')
  const size = positiveInteger(page.size, 'users page.size')
  const totalElements = nonNegativeInteger(page.totalElements, 'users page.totalElements')
  const totalPages = nonNegativeInteger(page.totalPages, 'users page.totalPages')
  if (totalPages === 0 && number !== 0) throw new Error('Сервер вернул некорректную страницу пользователей.')
  if (totalPages > 0 && number >= totalPages) throw new Error('Сервер вернул некорректную страницу пользователей.')
  return { items, number, size, totalElements, totalPages }
}

function readEmbeddedUsers(value: unknown): readonly AdminUser[] {
  const embedded = requiredRecord(value, 'users page._embedded')
  const collection = Object.values(embedded).find(Array.isArray)
  if (collection === undefined) return []
  return (collection as unknown[]).map(normalizeUser)
}

function normalizeUser(value: unknown): AdminUser {
  const record = requiredRecord(value, 'user')
  const lastName = requiredText(record.lastName, 'user.lastName')
  const firstName = requiredText(record.firstName, 'user.firstName')
  const middleName = nullableText(record.middleName)
  const rolesValue = record.roles
  const roles = Array.isArray(rolesValue) ? rolesValue.map(normalizeRoleGrant) : []
  return {
    id: positiveInteger(record.id, 'user.id'),
    login: requiredText(record.login, 'user.login'),
    lastName,
    firstName,
    middleName,
    fullName: requiredText(record.fullName ?? [lastName, firstName, middleName].filter(Boolean).join(' '), 'user.fullName'),
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
    ...(nullableText(input.middleName) ? { middleName: nullableText(input.middleName)! } : {}),
    ...(input.groupId === undefined ? {} : { groupId: positiveInteger(input.groupId, 'groupId') }),
    ...(input.employeeNumber === undefined ? {} : { employeeNumber: requiredText(input.employeeNumber, 'employeeNumber') }),
    ...(input.telegramId === undefined ? {} : { telegramId: positiveInteger(input.telegramId, 'telegramId') }),
  }
  if (role === 'STUDENT' && (normalized.groupId === undefined || normalized.telegramId === undefined)) {
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
    ...(input.employeeNumber === undefined ? {} : { employeeNumber: requiredText(input.employeeNumber, 'employeeNumber') }),
    ...(input.telegramId === undefined ? {} : { telegramId: positiveInteger(input.telegramId, 'telegramId') }),
  }
}

function normalizeProfileInput(input: UpdateAdminUserProfileInput): UpdateAdminUserProfileInput {
  const payload: {
    lastName?: string
    firstName?: string
    middleName?: string
    employeeNumber?: string
  } = {}
  if (input.lastName !== undefined) payload.lastName = profileName(input.lastName, 'lastName')
  if (input.firstName !== undefined) payload.firstName = profileName(input.firstName, 'firstName')
  if (input.middleName !== undefined) payload.middleName = profileOptionalText(input.middleName, 128, 'middleName')
  if (input.employeeNumber !== undefined) payload.employeeNumber = profileOptionalText(input.employeeNumber, 32, 'employeeNumber')
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
  if (normalized.length > maxLength) throw new RangeError(`${field} не может быть длиннее ${maxLength} символов`)
  return normalized
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

function requiredBoolean(value: unknown, field: string): boolean {
  if (typeof value !== 'boolean') throw new Error(`Некорректное поле ${field}.`)
  return value
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

function roleValue(value: unknown): AdminManagedRole {
  if (value === 'STUDENT' || value === 'TEACHER' || value === 'ADMIN' || value === 'HEADMAN') return value
  throw new Error('Сервер вернул неизвестную роль пользователя.')
}

function managedLegacyRole(value: unknown): 'STUDENT' | 'TEACHER' | 'ADMIN' {
  if (value === 'STUDENT' || value === 'TEACHER' || value === 'ADMIN') return value
  throw new Error('Сервер вернул неизвестную базовую роль пользователя.')
}

function roleStatus(value: unknown): AdminRoleStatus {
  if (value === 'ACTIVE' || value === 'EXPELLED' || value === 'GRADUATED'
    || value === 'SUSPENDED' || value === 'DISMISSED' || value === 'ARCHIVED') return value
  throw new Error('Сервер вернул неизвестный статус роли.')
}

function accountStatus(value: unknown): AdminUser['status'] {
  if (value === 'ACTIVE' || value === 'EXPELLED' || value === 'SUSPENDED' || value === 'ARCHIVED') return value
  throw new Error('Сервер вернул неизвестный статус пользователя.')
}

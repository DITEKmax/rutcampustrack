import { StaleSessionGenerationError } from '../../shared/session-owner'

export type HeadmanAssistantPermission =
  | 'MARK_ATTENDANCE'
  | 'MANAGE_EXCUSES'
  | 'MANAGE_HOMEWORK'
  | 'CANCEL_LESSONS'
  | 'VIEW_STATS'

export interface HeadmanGroupMember {
  readonly id: number
  readonly fullName: string
  readonly login: string
  readonly status: string
  readonly groupId: number | null
}

export interface HeadmanAssistant {
  readonly id: number
  readonly studentId: number
  readonly studentName: string
  readonly login: string
  readonly groupId: number
  readonly permissions: readonly HeadmanAssistantPermission[]
  readonly active: boolean
  readonly assignedAt: string | null
  readonly revokedAt: string | null
}

export interface HeadmanPermissionOption {
  readonly code: HeadmanAssistantPermission
  readonly label: string
}

export interface HeadmanGroupApiOptions {
  accessToken: () => string | null
  onUnauthorized?: () => Promise<void>
  assertCurrent?: () => void
  fetcher?: typeof fetch
}

export class HeadmanGroupApiError extends Error {
  constructor(
    readonly response: Response,
    readonly problem: unknown,
  ) {
    super(problemDetail(problem) ?? `HTTP ${response.status}`)
    this.name = 'HeadmanGroupApiError'
  }
}

export class HeadmanGroupApi {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: HeadmanGroupApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  listMembers(groupId: number): Promise<readonly HeadmanGroupMember[]> {
    assertPositiveInteger(groupId, 'groupId')
    return this.requestPaged(
      (page) => `/api/academic/groups/my/members?page=${page}&size=100`,
      (value) => embeddedItems(value, ['userResponseList']).map(normalizeMember),
    )
  }

  listAssistants(groupId: number): Promise<readonly HeadmanAssistant[]> {
    assertPositiveInteger(groupId, 'groupId')
    return this.requestPaged(
      (page) => `/api/academic/assistants?groupId=${groupId}&page=${page}&size=100`,
      (value) => embeddedItems(value, ['assistantResponseList']).map(normalizeAssistant),
    )
  }

  async listPermissionCatalog(): Promise<readonly HeadmanPermissionOption[]> {
    const value = await this.request<unknown>('/api/academic/assistants/permissions')
    return normalizePermissionOptions(value)
  }

  async listMyPermissions(): Promise<readonly HeadmanPermissionOption[]> {
    const value = await this.request<unknown>('/api/academic/assistants/me/permissions')
    return normalizePermissionOptions(value)
  }

  async assignAssistant(
    groupId: number,
    studentId: number,
    permissions: readonly HeadmanAssistantPermission[],
  ): Promise<HeadmanAssistant> {
    assertPositiveInteger(groupId, 'groupId')
    assertPositiveInteger(studentId, 'studentId')
    const normalized = normalizePermissions(permissions)
    if (normalized.length === 0) throw new RangeError('permissions must not be empty')
    return normalizeAssistant(await this.request<unknown>('/api/academic/assistants', {
      method: 'POST',
      body: JSON.stringify({ groupId, studentId, permissions: normalized }),
    }))
  }

  async updatePermissions(
    assistantId: number,
    permissions: readonly HeadmanAssistantPermission[],
  ): Promise<HeadmanAssistant> {
    assertPositiveInteger(assistantId, 'assistantId')
    const normalized = normalizePermissions(permissions)
    if (normalized.length === 0) throw new RangeError('permissions must not be empty')
    return normalizeAssistant(await this.request<unknown>(`/api/academic/assistants/${assistantId}/permissions`, {
      method: 'PATCH',
      body: JSON.stringify({ permissions: normalized }),
    }))
  }

  async revokeAssistant(assistantId: number): Promise<void> {
    assertPositiveInteger(assistantId, 'assistantId')
    await this.request<void>(`/api/academic/assistants/${assistantId}`, { method: 'DELETE' })
  }

  private async requestPaged<T>(
    path: (page: number) => string,
    read: (value: unknown) => readonly T[],
  ): Promise<readonly T[]> {
    const items: T[] = []
    let nextPath = path(0)
    const visitedPaths = new Set<string>()
    let page = 0
    for (;;) {
      if (!visitedPaths.add(nextPath)) throw new Error('Сервер вернул зацикленную пагинацию группы')
      const value = await this.request<unknown>(nextPath)
      items.push(...read(value))
      const metadata = pageMetadata(value)
      if (metadata !== null && metadata.totalPages > page + 1) {
        nextPath = path(page + 1)
        page += 1
        continue
      }
      const nextHref = nextPageHref(value)
      if (nextHref !== null) {
        nextPath = nextHref
        page += 1
        continue
      }
      return items
    }
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
    this.options.assertCurrent?.()
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (typeof init?.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    this.options.assertCurrent?.()
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      this.options.assertCurrent?.()
      return this.response(path, init, true)
    }
    return response
  }

  private async apiError(response: Response): Promise<HeadmanGroupApiError> {
    let problem: unknown = null
    try {
      problem = await response.json()
    } catch {
      // Preserve the HTTP status when the gateway did not return JSON.
    }
    this.options.assertCurrent?.()
    return new HeadmanGroupApiError(response, problem)
  }
}

export interface HeadmanGroupApiGenerationOwner {
  currentGeneration(): number
  accessTokenFor(generation: number): string | null
  refreshFor(generation: number): Promise<void>
}

export function createGenerationBoundHeadmanGroupApi(
  owner: HeadmanGroupApiGenerationOwner,
  fetcher?: typeof fetch,
): HeadmanGroupApi {
  const generation = owner.currentGeneration()
  const assertCurrent = (): void => {
    if (generation !== owner.currentGeneration()) throw new StaleSessionGenerationError()
  }
  return new HeadmanGroupApi({
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

const PERMISSION_CODES: readonly HeadmanAssistantPermission[] = [
  'MARK_ATTENDANCE',
  'MANAGE_EXCUSES',
  'MANAGE_HOMEWORK',
  'CANCEL_LESSONS',
  'VIEW_STATS',
]

function normalizePermissions(value: readonly HeadmanAssistantPermission[]): HeadmanAssistantPermission[] {
  return [...new Set(value)].filter((permission): permission is HeadmanAssistantPermission => PERMISSION_CODES.includes(permission))
}

function normalizePermissionOptions(value: unknown): readonly HeadmanPermissionOption[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!isRecord(item) || typeof item.code !== 'string' || !PERMISSION_CODES.includes(item.code as HeadmanAssistantPermission)
      || typeof item.label !== 'string' || item.label.trim() === '') return []
    return [{ code: item.code as HeadmanAssistantPermission, label: item.label.trim() }]
  })
}

function normalizeMember(value: Record<string, unknown>): HeadmanGroupMember {
  const id = positiveNumber(value.id)
  if (id === null) throw new Error('Сервер вернул студента без корректного id')
  const fullName = firstText(value.fullName, value.displayName)
    ?? ([value.lastName, value.firstName, value.middleName].filter(isText).join(' ').trim()
      || firstText(value.login)
      || `Студент #${id}`)
  return {
    id,
    fullName,
    login: firstText(value.login) ?? '',
    status: firstText(value.status)?.toUpperCase() ?? 'ACTIVE',
    groupId: positiveNumber(value.groupId),
  }
}

function normalizeAssistant(value: unknown): HeadmanAssistant {
  const source = unwrapEntity(value)
  const id = positiveNumber(source.id)
  const studentId = positiveNumber(source.studentId)
  const groupId = positiveNumber(source.groupId)
  if (id === null || studentId === null || groupId === null) {
    throw new Error('Сервер вернул неполную запись помощника')
  }
  const permissions = Array.isArray(source.permissions)
    ? normalizePermissions(source.permissions.filter((item): item is HeadmanAssistantPermission => typeof item === 'string') as HeadmanAssistantPermission[])
    : []
  return {
    id,
    studentId,
    studentName: firstText(source.studentName, source.fullName, source.login) ?? `Студент #${studentId}`,
    login: firstText(source.login) ?? '',
    groupId,
    permissions,
    active: source.active !== false,
    assignedAt: firstText(source.assignedAt),
    revokedAt: firstText(source.revokedAt),
  }
}

function embeddedItems(value: unknown, keys: readonly string[]): Record<string, unknown>[] {
  if (Array.isArray(value)) return value.filter(isRecord)
  if (!isRecord(value) || !isRecord(value._embedded)) return []
  for (const key of keys) {
    const items = value._embedded[key]
    if (Array.isArray(items)) return items.filter(isRecord)
  }
  const first = Object.values(value._embedded).find(Array.isArray)
  return Array.isArray(first) ? first.filter(isRecord) : []
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

function unwrapEntity(value: unknown): Record<string, unknown> {
  if (!isRecord(value)) return {}
  return isRecord(value.content) ? value.content : value
}

function firstText(...values: unknown[]): string | null {
  const value = values.find(isText)
  return value === undefined ? null : value.trim() || null
}

function positiveNumber(value: unknown): number | null {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0) return null
  return value
}

function isText(value: unknown): value is string {
  return typeof value === 'string' && value.trim() !== ''
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function assertPositiveInteger(value: number, name: string): void {
  if (!Number.isSafeInteger(value) || value <= 0) throw new RangeError(`${name} must be a positive integer`)
}

function problemDetail(value: unknown): string | null {
  if (!isRecord(value)) return null
  return isText(value.detail) ? value.detail : isText(value.title) ? value.title : null
}

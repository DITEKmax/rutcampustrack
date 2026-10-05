import { ProfileRequestError } from '../../mobile-core/src/features/profile/profile-types'
import type {
  ProfilePasswordPolicy,
  ProfileRole,
  ProfileRoleGrant,
  ProfileRoleStatus,
  ProfileSnapshot,
  ProfileRoleSelection,
  ProfileHistoryPage,
  ProfileHistoryEvent,
  ProfilePageRequest,
  ProfileSessionSummary,
  ProfileSessionsPage,
} from '../../mobile-core/src/features/profile/profile-types'
import type { components as authComponents, operations as authOperations } from '../../mobile-core/src/api/generated/auth'

type AuthSchemas = authComponents['schemas']
type GeneratedTokenResponse = AuthSchemas['TokenResponse']
type GeneratedCurrentSessionResponse = AuthSchemas['CurrentSessionResponse']
type GeneratedSelectActiveRoleResponse = AuthSchemas['SelectActiveRoleResponse']
type GeneratedAuthSessionsPage = AuthSchemas['AuthSessionsPage']
type GeneratedAccountHistoryPage = AuthSchemas['AccountHistoryPage']
type GeneratedAuthSessionSummary = AuthSchemas['AuthSessionSummary']
type GeneratedAccountHistoryEvent = AuthSchemas['AccountHistoryEvent']
type GeneratedChangePasswordRequest = AuthSchemas['ChangePasswordRequest']
type AuthPageQuery = NonNullable<authOperations['sessions']['parameters']['query']>

export type AuthToken = {
  accessToken: string
  expiresIn?: number
}

export interface AuthClientOptions {
  fetcher?: typeof fetch
  accessToken?: () => string | null
}

export interface AuthLoginInput {
  login: string
  password: string
}

export interface AuthSelectRoleInput {
  role: ProfileRole
  expectedSessionVersion: string
}

export type AuthProblem = {
  status?: number
  type?: string
  title?: string
  detail?: string
  instance?: string
  timestamp?: string
  traceId?: string
  field?: string
  code?: string
  fieldErrors?: readonly { field?: string; message?: string }[]
}

export class AuthRequestError extends ProfileRequestError {
  readonly operation: string
  readonly problem: AuthProblem | null
  readonly serverCode: string | null
  readonly causeValue: unknown

  constructor(
    operation: string,
    status: number | undefined,
    problem: AuthProblem | null,
    causeValue?: unknown,
  ) {
    const serverCode = problem?.code ?? null
    super(authErrorCode(serverCode, status), authErrorMessage(operation, status, problem, causeValue), status, problem)
    this.name = 'AuthRequestError'
    this.operation = operation
    this.problem = problem
    this.serverCode = serverCode
    this.causeValue = causeValue
  }
}

export class AuthPayloadError extends AuthRequestError {
  constructor(operation: string, message: string, value?: unknown) {
    super(operation, 502, { title: 'Некорректный ответ сервера', detail: message }, value)
    this.name = 'AuthPayloadError'
  }
}

export class AuthProtocolError extends AuthRequestError {
  constructor(operation: string, responseStatus: number, message: string, value?: unknown) {
    super(operation, responseStatus, { title: 'Некорректный ответ сервера', detail: message }, value)
    this.name = 'AuthProtocolError'
  }
}

export interface AuthClient {
  login(input: AuthLoginInput): Promise<AuthToken>
  refresh(): Promise<AuthToken>
  getSnapshot(): Promise<ProfileSnapshot>
  selectRole(input: AuthSelectRoleInput): Promise<ProfileRoleSelection>
  listSessions(input?: ProfilePageRequest): Promise<ProfileSessionsPage>
  listHistory(input?: ProfilePageRequest): Promise<ProfileHistoryPage>
  changePassword(input: { currentPassword: string; newPassword: string }): Promise<void>
  logoutAll(): Promise<void>
  terminateSession(sessionId: string): Promise<void>
  logout(): Promise<void>
}

/**
 * Small generated-contract adapter. The generated file supplies the DTO
 * shapes, while this boundary checks the fields that the public OpenAPI marks
 * optional but the authenticated runtime must provide.
 */
export function createAuthClient(options: AuthClientOptions = {}): AuthClient {
  const fetcher = options.fetcher ?? ((input: RequestInfo | URL, init?: RequestInit) => globalThis.fetch(input, init))
  const accessToken = options.accessToken ?? (() => null)

  return {
    login: (input) => {
      if (!isNonEmptyString(input.login) || !isNonEmptyString(input.password)) {
        throw new AuthPayloadError('login', 'Логин и пароль не могут быть пустыми', input)
      }
      return requestToken('login', '/api/auth/login', input, fetcher, accessToken)
    },
    refresh: () => requestToken('refresh', '/api/auth/refresh', undefined, fetcher, accessToken),
    getSnapshot: async () => {
      const value = await requestJson<GeneratedCurrentSessionResponse>('current-session', '/api/auth/session', 'GET', undefined, fetcher, accessToken)
      return adaptSession(value, 'current-session')
    },
    selectRole: async (input) => {
      if (!isProfileRole(input.role) || !isDecimalVersion(input.expectedSessionVersion)) {
        throw new AuthPayloadError('select-role', 'Параметры смены роли некорректны', input)
      }
      const value = await requestJson<GeneratedSelectActiveRoleResponse>('select-role', '/api/auth/session/active-role', 'PUT', {
        role: input.role,
        expectedSessionVersion: input.expectedSessionVersion,
      }, fetcher, accessToken)
      return adaptRoleSelection(value, input, 'select-role')
    },
    listSessions: async (input) => {
      const value = await requestJson<GeneratedAuthSessionsPage>(
        'sessions',
        pagePath('/api/auth/sessions', input),
        'GET',
        undefined,
        fetcher,
        accessToken,
      )
      return adaptSessionsPage(value, 'sessions')
    },
    listHistory: async (input) => {
      const value = await requestJson<GeneratedAccountHistoryPage>(
        'account-history',
        pagePath('/api/auth/account-history', input),
        'GET',
        undefined,
        fetcher,
        accessToken,
      )
      return adaptHistoryPage(value, 'account-history')
    },
    changePassword: async (input) => {
      const body = {
        currentPassword: input.currentPassword,
        newPassword: input.newPassword,
      } satisfies GeneratedChangePasswordRequest
      await requestEmptyMutation(
        'change-password',
        '/api/auth/change-password',
        'POST',
        body,
        fetcher,
        accessToken,
      )
    },
    terminateSession: async (sessionId) => {
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u.test(sessionId)) throw new AuthPayloadError('terminate-session', 'Идентификатор сессии некорректен')
      await requestEmptyMutation('terminate-session', `/api/auth/sessions/${sessionId}`, 'DELETE', undefined, fetcher, accessToken)
    },
    logoutAll: async () => {
      await requestEmptyMutation('logout-all', '/api/auth/logout-all', 'POST', undefined, fetcher, accessToken)
    },
    logout: async () => {
      await requestJson<null>('logout', '/api/auth/logout', 'POST', undefined, fetcher, accessToken, { allowEmpty: true })
    },
  }
}

function pagePath(path: string, input?: ProfilePageRequest): string {
  const query: AuthPageQuery = {
    ...(input?.cursor === undefined ? {} : { cursor: input.cursor }),
    ...(input?.limit === undefined ? {} : { limit: input.limit }),
  }
  const params = new URLSearchParams()
  if (query.cursor !== undefined) params.set('cursor', query.cursor)
  if (query.limit !== undefined) params.set('limit', String(query.limit))
  const suffix = params.toString()
  return suffix ? `${path}?${suffix}` : path
}

async function requestToken(
  operation: string,
  path: string,
  body: AuthLoginInput | undefined,
  fetcher: typeof fetch,
  accessToken: () => string | null,
): Promise<AuthToken> {
  const value = await requestJson<GeneratedTokenResponse>(operation, path, 'POST', body, fetcher, accessToken)
  return adaptToken(value, operation, true)
}

async function requestEmptyMutation(
  operation: string,
  path: string,
  method: 'POST' | 'PUT' | 'DELETE',
  body: unknown,
  fetcher: typeof fetch,
  accessToken: () => string | null,
): Promise<void> {
  const response = await sendRequest(operation, path, method, body, fetcher, accessToken)
  if (response.status !== 204) {
    throw new AuthProtocolError(operation, response.status, 'Сервер должен вернуть ровно HTTP 204', response.status)
  }

  // Fetch may normalize illegal wire framing before exposing a Response. We
  // enforce every empty-response signal that remains observable here; framing
  // that Fetch has already normalized must be enforced by the server/gateway.
  let contentLength: string | null
  try {
    contentLength = response.headers.get('Content-Length')
  } catch (error) {
    throw new AuthProtocolError(operation, response.status, 'Не удалось проверить Content-Length пустого ответа', error)
  }
  if (contentLength !== null && (typeof contentLength !== 'string' || !/^0+$/.test(contentLength.trim()))) {
    throw new AuthProtocolError(operation, response.status, 'Сервер вернул некорректный Content-Length для HTTP 204', contentLength)
  }

  let responseBody: string
  try {
    responseBody = await response.text()
  } catch (error) {
    throw new AuthProtocolError(operation, response.status, 'Не удалось проверить пустой ответ сервера', error)
  }
  if (typeof responseBody !== 'string' || responseBody.length !== 0) {
    throw new AuthProtocolError(operation, response.status, 'Сервер вернул тело ответа для HTTP 204', responseBody)
  }
}

async function requestJson<T>(
  operation: string,
  path: string,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  body: unknown,
  fetcher: typeof fetch,
  accessToken: () => string | null,
  options: { allowEmpty?: boolean } = {},
): Promise<T> {
  const response = await sendRequest(operation, path, method, body, fetcher, accessToken)
  if (options.allowEmpty && (response.status === 204 || response.status === 205)) return null as T
  try {
    return await response.json() as T
  } catch (error) {
    throw new AuthPayloadError(operation, 'Сервер вернул пустой или нечитаемый ответ', error)
  }
}

async function sendRequest(
  operation: string,
  path: string,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  body: unknown,
  fetcher: typeof fetch,
  accessToken: () => string | null,
): Promise<Response> {
  const headers = new Headers({ Accept: 'application/json' })
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  const token = accessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetcher(path, {
      method,
      headers,
      credentials: 'include',
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    })
  } catch (error) {
    throw new AuthRequestError(operation, undefined, { code: 'NETWORK', title: 'Нет соединения с сервером' }, error)
  }

  if (!response.ok) {
    throw new AuthRequestError(operation, response.status, await readProblem(response), undefined)
  }
  return response
}

async function readProblem(response: Response): Promise<AuthProblem | null> {
  try {
    const value = await response.json() as unknown
    if (!isRecord(value)) return null
    const extras = isRecord(value.extras) ? value.extras : null
    const code = extras && typeof extras.code === 'string' ? extras.code : undefined
    return {
      ...(typeof value.status === 'number' ? { status: value.status } : {}),
      ...(typeof value.type === 'string' ? { type: value.type } : {}),
      ...(typeof value.title === 'string' ? { title: value.title } : {}),
      ...(typeof value.detail === 'string' ? { detail: value.detail } : {}),
      ...(typeof value.instance === 'string' ? { instance: value.instance } : {}),
      ...(typeof value.timestamp === 'string' ? { timestamp: value.timestamp } : {}),
      ...(typeof value.traceId === 'string' ? { traceId: value.traceId } : {}),
      ...(typeof value.field === 'string' ? { field: value.field } : {}),
      ...(code ? { code } : {}),
      ...(Array.isArray(value.fieldErrors) ? { fieldErrors: value.fieldErrors.filter(isFieldError) } : {}),
    }
  } catch {
    return null
  }
}

function adaptToken(value: unknown, operation: string, requireToken: boolean): AuthToken {
  if (!isRecord(value) || (requireToken && !isNonEmptyString(value.accessToken))) {
    throw new AuthPayloadError(operation, 'Сервер не вернул access token', value)
  }
  const accessToken = value.accessToken
  if (!isNonEmptyString(accessToken)) throw new AuthPayloadError(operation, 'Сервер не вернул access token', value)
  const expiresIn = value.expiresIn
  if (expiresIn !== undefined && !isPositiveInteger(expiresIn)) {
    throw new AuthPayloadError(operation, 'Сервер вернул некорректный срок действия токена', value)
  }
  return expiresIn === undefined ? { accessToken } : { accessToken, expiresIn }
}

function adaptSessionsPage(value: unknown, operation: string): ProfileSessionsPage {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    throw new AuthPayloadError(operation, 'Сервер вернул некорректную страницу сеансов', value)
  }
  return {
    items: value.items.map((item) => adaptSessionSummary(item, operation)),
    nextCursor: adaptNextCursor(value.nextCursor, operation),
  }
}

function adaptHistoryPage(value: unknown, operation: string): ProfileHistoryPage {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    throw new AuthPayloadError(operation, 'Сервер вернул некорректную страницу истории', value)
  }
  return {
    items: value.items.map((item) => adaptHistoryEvent(item, operation)),
    nextCursor: adaptNextCursor(value.nextCursor, operation),
  }
}

function adaptNextCursor(value: unknown, operation: string): string | null {
  if (value === undefined || value === null) return null
  if (!isNonEmptyString(value)) throw new AuthPayloadError(operation, 'Сервер вернул некорректный курсор', value)
  return value
}

function adaptSessionSummary(value: unknown, operation: string): ProfileSessionSummary {
  if (!isRecord(value)) throw new AuthPayloadError(operation, 'Сервер вернул некорректный сеанс', value)
  const session = value as GeneratedAuthSessionSummary
  if (!isNonEmptyString(session.sessionId)
    || !isNonEmptyString(session.authMethod)
    || !isNonEmptyString(session.createdAt)
    || !isNonEmptyString(session.lastSeenAt)
    || typeof session.current !== 'boolean') {
    throw new AuthPayloadError(operation, 'Сервер вернул неполные данные сеанса', value)
  }
  const authMethod = adaptAuthMethod(session.authMethod, operation)
  const clientLabel = optionalNullableString(session.clientLabel, operation, 'clientLabel')
  const locationLabel = optionalNullableString(session.locationLabel, operation, 'locationLabel')
  return {
    sessionId: session.sessionId,
    authMethod,
    ...(clientLabel === undefined ? {} : { clientLabel }),
    ...(locationLabel === undefined ? {} : { locationLabel }),
    createdAt: session.createdAt,
    lastSeenAt: session.lastSeenAt,
    current: session.current,
  }
}

function adaptHistoryEvent(value: unknown, operation: string): ProfileHistoryEvent {
  if (!isRecord(value)) throw new AuthPayloadError(operation, 'Сервер вернул некорректное событие истории', value)
  const event = value as GeneratedAccountHistoryEvent
  if (!isNonEmptyString(event.id)
    || !isNonEmptyString(event.type)
    || !isNonEmptyString(event.occurredAt)) {
    throw new AuthPayloadError(operation, 'Сервер вернул неполное событие истории', value)
  }
  const authMethod = optionalAuthMethod(event.authMethod, operation)
  const clientLabel = optionalNullableString(event.clientLabel, operation, 'clientLabel')
  const locationLabel = optionalNullableString(event.locationLabel, operation, 'locationLabel')
  return {
    id: event.id,
    type: adaptHistoryType(event.type, operation),
    occurredAt: event.occurredAt,
    ...(authMethod === undefined ? {} : { authMethod }),
    ...(clientLabel === undefined ? {} : { clientLabel }),
    ...(locationLabel === undefined ? {} : { locationLabel }),
  }
}

function adaptAuthMethod(value: unknown, operation: string): NonNullable<ProfileSessionSummary['authMethod']> {
  if (value === 'PASSWORD' || value === 'OTP' || value === 'TMA') return value
  throw new AuthPayloadError(operation, 'Сервер вернул неизвестный способ входа', value)
}

function optionalAuthMethod(value: unknown, operation: string): ProfileHistoryEvent['authMethod'] | undefined {
  if (value === undefined || value === null) return value === null ? null : undefined
  return adaptAuthMethod(value, operation)
}

function adaptHistoryType(value: unknown, operation: string): ProfileHistoryEvent['type'] {
  if (value === 'LOGIN'
    || value === 'ROLE_CHANGED'
    || value === 'CURRENT_LOGOUT'
    || value === 'LOGOUT_ALL'
    || value === 'PASSWORD_CHANGED'
    || value === 'SECURITY_REVOKED') return value
  throw new AuthPayloadError(operation, 'Сервер вернул неизвестный тип события истории', value)
}

function optionalNullableString(value: unknown, operation: string, field: string): string | null | undefined {
  if (value === undefined) return undefined
  return expectNullableString(value, operation, field)
}

function adaptSession(value: unknown, operation: string): ProfileSnapshot {
  if (!isRecord(value)) throw new AuthPayloadError(operation, 'Сервер вернул некорректную сессию', value)
  const session = value as GeneratedCurrentSessionResponse
  if (!isCanonicalUuid(session.sessionId)
    || !isDecimalId(session.userId)
    || !isNonEmptyString(session.displayName)
    || !isDecimalVersion(session.sessionVersion)
    || !isDecimalVersion(session.rolesVersion)
    || typeof session.readOnly !== 'boolean'
    || !Array.isArray(session.roles)) {
    throw new AuthPayloadError(operation, 'Сервер вернул неполные данные сессии', value)
  }
  const roles = session.roles.map((grant) => adaptRoleGrant(grant, operation))
  const grantIds = new Set(roles.map((grant) => grant.grantId))
  if (grantIds.size !== roles.length) {
    throw new AuthPayloadError(operation, 'Сервер вернул повторяющееся право роли', value)
  }
  const roleKeys = new Set(roles.map((grant) => grant.role))
  if (roleKeys.size !== roles.length) {
    throw new AuthPayloadError(operation, 'Сервер вернул повторяющуюся роль', value)
  }
  const activeRole = session.activeRole === undefined || session.activeRole === null
    ? null
    : adaptRole(session.activeRole, operation)
  if (activeRole !== null && !roles.some((grant) => grant.role === activeRole)) {
    throw new AuthPayloadError(operation, 'Активная роль отсутствует в списке доступных ролей', value)
  }
  return {
    sessionId: session.sessionId,
    userId: session.userId,
    displayName: session.displayName,
    ...(session.groupLabel === undefined ? {} : { groupLabel: expectNullableString(session.groupLabel, operation, 'groupLabel') }),
    sessionVersion: session.sessionVersion,
    rolesVersion: session.rolesVersion,
    activeRole,
    roles,
    readOnly: session.readOnly,
    passwordPolicy: adaptPasswordPolicy(session.passwordPolicy, operation),
  }
}

function adaptRoleSelection(value: unknown, input: AuthSelectRoleInput, operation: string): ProfileRoleSelection {
  if (!isRecord(value) || !isNonEmptyString(value.accessToken) || !isPositiveInteger(value.expiresIn) || !isRecord(value.session)) {
    throw new AuthPayloadError(operation, 'Сервер вернул неполный ответ смены роли', value)
  }
  const session = adaptSession(value.session, operation)
  if (session.activeRole !== input.role) {
    throw new AuthPayloadError(operation, 'Сервер не подтвердил выбранную роль', value)
  }
  return {
    accessToken: value.accessToken,
    expiresIn: value.expiresIn,
    session,
  }
}

function adaptRoleGrant(value: unknown, operation: string): ProfileRoleGrant {
  if (!isRecord(value)
    || !isDecimalId(value.grantId)
    || !isProfileRole(value.role)
    || !isRoleStatus(value.status)
    || typeof value.selectable !== 'boolean'
    || typeof value.readOnly !== 'boolean') {
    throw new AuthPayloadError(operation, 'Сервер вернул некорректное право роли', value)
  }
  const groupId = value.groupId === undefined || value.groupId === null ? null : value.groupId
  if (groupId !== null && !isDecimalId(groupId)) throw new AuthPayloadError(operation, 'Сервер вернул некорректную группу роли', value)
  return {
    grantId: value.grantId,
    role: value.role,
    status: value.status,
    ...(groupId === null ? {} : { groupId }),
    ...(value.contextLabel === undefined ? {} : { contextLabel: expectNullableString(value.contextLabel, operation, 'contextLabel') }),
    selectable: value.selectable,
    readOnly: value.readOnly,
  }
}

function adaptPasswordPolicy(value: unknown, operation: string): ProfilePasswordPolicy {
  if (!isRecord(value)
    || !isPositiveInteger(value.minCodePoints)
    || !isPositiveInteger(value.maxUtf8Bytes)
    || typeof value.requiresDecimalDigit !== 'boolean'
    || value.normalization !== 'NONE'
    || !Array.isArray(value.specialCategories)
    || value.specialCategories.some((category) => category !== 'P' && category !== 'S')
    || new Set(value.specialCategories).size !== value.specialCategories.length) {
    throw new AuthPayloadError(operation, 'Сервер вернул некорректную политику пароля', value)
  }
  const categories = [...value.specialCategories] as ('P' | 'S')[]
  return {
    minCodePoints: value.minCodePoints,
    maxUtf8Bytes: value.maxUtf8Bytes,
    requiresDecimalDigit: value.requiresDecimalDigit,
    specialCategories: categories,
    normalization: 'NONE',
  }
}

function expectNullableString(value: unknown, operation: string, field: string): string | null {
  if (value !== null && typeof value !== 'string') throw new AuthPayloadError(operation, `Сервер вернул некорректное поле ${field}`, value)
  return value
}

function authErrorCode(serverCode: string | null, status: number | undefined): ProfileRequestError['code'] {
  const known: ProfileRequestError['code'][] = [
    'SESSION_STATE_STALE', 'SESSION_VERSION_CONFLICT', 'REFRESH_ALREADY_ROTATED', 'ROLE_NOT_GRANTED',
    'ROLE_NOT_SELECTABLE', 'ROLE_READ_ONLY', 'BOOTSTRAP_SCOPE_DENIED', 'CURRENT_PASSWORD_INVALID',
    'PASSWORD_POLICY_VIOLATION', 'INVALID_CURSOR', 'AUTHORITY_UNAVAILABLE', 'INVALID_SESSION',
    'SESSION_REVOKED', 'SESSION_NOT_FOUND', 'REFRESH_REJECTED', 'OFFLINE_MUTATION_DISABLED', 'ACCOUNT_INVALIDATED', 'NETWORK', 'UNKNOWN',
  ]
  if (serverCode && known.includes(serverCode as ProfileRequestError['code'])) return serverCode as ProfileRequestError['code']
  if (status === 409) return 'SESSION_STATE_STALE'
  if (status === 503 || status === 504) return 'AUTHORITY_UNAVAILABLE'
  if (status === 401) return 'INVALID_SESSION'
  return serverCode === 'NETWORK' ? 'NETWORK' : 'UNKNOWN'
}

function authErrorMessage(operation: string, status: number | undefined, problem: AuthProblem | null, causeValue: unknown): string {
  if (problem?.detail || problem?.title) return problem.detail ?? problem.title ?? 'Ошибка авторизации'
  if (problem?.code === 'NETWORK' || causeValue instanceof TypeError) return 'Не удалось связаться с сервером'
  if (status === 401) return operation === 'login' ? 'Неверный логин или пароль' : 'Сессия больше недействительна'
  if (status === 403) return 'Доступ запрещён для текущей сессии'
  return 'Не удалось выполнить запрос авторизации'
}

function isProfileRole(value: unknown): value is ProfileRole {
  return value === 'STUDENT' || value === 'HEADMAN' || value === 'TEACHER' || value === 'ADMIN'
}

function adaptRole(value: unknown, operation: string): ProfileRole {
  if (!isProfileRole(value)) throw new AuthPayloadError(operation, 'Сервер вернул неизвестную роль', value)
  return value
}

function isRoleStatus(value: unknown): value is ProfileRoleStatus {
  return value === 'ACTIVE' || value === 'SUSPENDED' || value === 'EXPELLED' || value === 'GRADUATED' || value === 'DISMISSED' || value === 'ARCHIVED'
}

function isDecimalId(value: unknown): value is string {
  return typeof value === 'string' && /^[1-9][0-9]*$/u.test(value)
}

function isCanonicalUuid(value: unknown): value is string {
  return typeof value === 'string'
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u.test(value)
}

function isDecimalVersion(value: unknown): value is string {
  return isDecimalId(value)
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isFieldError(value: unknown): value is { field?: string; message?: string } {
  return isRecord(value)
    && (value.field === undefined || typeof value.field === 'string')
    && (value.message === undefined || typeof value.message === 'string')
}

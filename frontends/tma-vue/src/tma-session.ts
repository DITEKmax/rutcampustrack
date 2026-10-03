import { ref } from 'vue'
import {
  createGenerationBoundHeadmanScheduleApi,
  createGenerationBoundHeadmanJournalApi,
  createGenerationBoundHeadmanRequestsApi,
  createGenerationBoundHeadmanGroupApi,
  createGenerationBoundHeadmanHomeworkApi,
  createGenerationBoundHeadmanSubjectsApi,
  createGenerationBoundHeadmanStatsApi,
  createGenerationBoundStudentApi,
  createGenerationBoundTeacherApi,
  createGenerationBoundReportDownloadClient,
  createGenerationBoundAdminDashboardClient,
  createGenerationBoundAdminSemesterClient,
  createGenerationBoundAdminGroupsClient,
  createGenerationBoundAdminUsersClient,
  ProfileRequestError,
  StaleSessionGenerationError,
} from '@rct/mobile-core'
import type {
  ProfileAuthMethod,
  ProfileHistoryEvent,
  ProfileHistoryType,
  ProfileHistoryPage,
  ProfilePageRequest,
  ProfilePort,
  HeadmanScheduleApi,
  HeadmanJournalApi,
  HeadmanRequestsApi,
  HeadmanGroupApi,
  HeadmanHomeworkApi,
  HeadmanSubjectsApi,
  HeadmanStatsApi,
  ProfileSessionSummary,
  ProfileSessionsPage,
  ProfileRole,
  ProfileRoleGrant,
  ProfileRoleSelection,
  ProfileSnapshot,
  StudentApi,
  TeacherApi,
  ReportDownloadClient,
  AdminDashboardClient,
  AdminSemesterClient,
  AdminGroupsClient,
  AdminUsersClient,
  authComponents,
} from '@rct/mobile-core'
import { authenticateTma, TmaAuthError } from './tma-auth'

type TmaCurrentSession = authComponents['schemas']['CurrentSessionResponse']
type TmaSelectActiveRoleResponse = authComponents['schemas']['SelectActiveRoleResponse']
type TmaAuthSessionSummary = authComponents['schemas']['AuthSessionSummary']
type TmaAccountHistoryEvent = authComponents['schemas']['AccountHistoryEvent']

export interface TmaSessionOptions {
  fetcher: typeof fetch
  getInitData: () => string | null
}

export interface TmaProfilePortOptions {
  onInvalidated?: ProfilePort['onInvalidated']
}

/** In-memory Telegram session owner; no initData or bearer token is persisted. */
export function useTmaSession(options: TmaSessionOptions) {
  const accessToken = ref<string | null>(null)
  const resetGeneration = ref(0)
  let knownProfile: ProfileSnapshot | null = null
  let knownProfileGeneration: number | null = null
  let authenticateInFlight: { generation: number; promise: Promise<void> } | null = null
  const invalidationListeners = new Set<() => void>()

  function rememberProfile(profile: ProfileSnapshot, generation: number): void {
    assertCurrent(generation)
    if (knownProfileGeneration === generation && knownProfile
      && authAuthorityIdentity(knownProfile) !== authAuthorityIdentity(profile)) {
      const token = accessToken.value
      const nextGeneration = clear()
      accessToken.value = token
      knownProfile = profile
      knownProfileGeneration = nextGeneration
      invalidationListeners.forEach((listener) => listener())
      throw new StaleSessionGenerationError()
    }
    knownProfile = profile
    knownProfileGeneration = generation
  }

  function currentGeneration(): number {
    return resetGeneration.value
  }

  function assertCurrent(generation: number): void {
    if (generation !== currentGeneration()) throw new StaleSessionGenerationError()
  }

  function accessTokenFor(generation: number): string | null {
    assertCurrent(generation)
    return accessToken.value
  }

  function sessionScopeFor(generation: number): string | null {
    assertCurrent(generation)
    const profile = knownProfileGeneration === generation ? knownProfile : null
    if (!profile || profile.activeRole !== 'ADMIN'
      || profile.userId.trim() === '' || profile.sessionId.trim() === '') return null
    return JSON.stringify([profile.userId, profile.sessionId, profile.activeRole])
  }

  async function authenticateFor(generation: number): Promise<void> {
    assertCurrent(generation)
    if (authenticateInFlight?.generation === generation) return authenticateInFlight.promise
    const initData = options.getInitData()
    if (!initData) throw new Error('Открой приложение из Telegram')

    const promise = authenticateTma(options.fetcher, initData)
      .then(async (token) => {
        assertCurrent(generation)
        const expectedProfile = knownProfileGeneration === generation ? knownProfile : null
        if (expectedProfile) {
          // A mounted owner may only receive a refreshed bearer after Auth
          // confirms the same session, account and role authority.
          const response = await options.fetcher('/api/auth/session', {
            headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
            credentials: 'include',
          })
          if (!response.ok) throw new TmaAuthError(response.status, 'Не удалось подтвердить обновлённую Telegram-сессию')
          const refreshedProfile = adaptProfile(await response.json() as TmaCurrentSession)
          assertCurrent(generation)
          if (authAuthorityIdentity(expectedProfile) !== authAuthorityIdentity(refreshedProfile)) {
            const nextGeneration = clear()
            accessToken.value = token
            knownProfile = refreshedProfile
            knownProfileGeneration = nextGeneration
            invalidationListeners.forEach((listener) => listener())
            throw new StaleSessionGenerationError()
          }
          rememberProfile(refreshedProfile, generation)
        }
        accessToken.value = token
      })
      .finally(() => {
        if (authenticateInFlight?.promise === promise) authenticateInFlight = null
      })
    authenticateInFlight = { generation, promise }
    return promise
  }

  function authenticate(): Promise<void> {
    return authenticateFor(currentGeneration())
  }

  function createApi(fetcher?: typeof fetch): StudentApi {
    return createGenerationBoundStudentApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createHeadmanApi(fetcher?: typeof fetch): HeadmanScheduleApi {
    return createGenerationBoundHeadmanScheduleApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createHeadmanJournalApi(fetcher?: typeof fetch): HeadmanJournalApi {
    return createGenerationBoundHeadmanJournalApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createHeadmanStatsApi(fetcher?: typeof fetch): HeadmanStatsApi {
    return createGenerationBoundHeadmanStatsApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createHeadmanRequestsApi(fetcher?: typeof fetch): HeadmanRequestsApi {
    return createGenerationBoundHeadmanRequestsApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createHeadmanGroupApi(fetcher?: typeof fetch): HeadmanGroupApi {
    return createGenerationBoundHeadmanGroupApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createHeadmanHomeworkApi(fetcher?: typeof fetch): HeadmanHomeworkApi {
    return createGenerationBoundHeadmanHomeworkApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createHeadmanSubjectsApi(fetcher?: typeof fetch): HeadmanSubjectsApi {
    return createGenerationBoundHeadmanSubjectsApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createTeacherApi(fetcher?: typeof fetch): TeacherApi {
    return createGenerationBoundTeacherApi({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createReportDownloadClient(fetcher?: typeof fetch): ReportDownloadClient {
    return createGenerationBoundReportDownloadClient({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createAdminSemesterApi(fetcher?: typeof fetch): AdminSemesterClient {
    return createGenerationBoundAdminSemesterClient({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
      sessionScopeFor,
    }, fetcher)
  }

  function createAdminDashboardApi(fetcher?: typeof fetch): AdminDashboardClient {
    return createGenerationBoundAdminDashboardClient({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createAdminUsersApi(fetcher?: typeof fetch): AdminUsersClient {
    return createGenerationBoundAdminUsersClient({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  function createAdminGroupsApi(fetcher?: typeof fetch): AdminGroupsClient {
    return createGenerationBoundAdminGroupsClient({
      currentGeneration,
      accessTokenFor,
      refreshFor: authenticateFor,
    }, fetcher)
  }

  async function getProfileFor(generation: number): Promise<ProfileSnapshot> {
    assertCurrent(generation)
    const response = await authenticatedRequest('/api/auth/session', generation)
    if (!response.ok) throw new TmaAuthError(response.status, 'Не удалось получить профиль Telegram-сессии')
    const value = await response.json() as TmaCurrentSession
    assertCurrent(generation)
    const profile = adaptProfile(value)
    rememberProfile(profile, generation)
    return profile
  }

  async function selectRoleFor(
    generation: number,
    input: { role: ProfileRole; expectedSessionVersion: string },
  ): Promise<ProfileRoleSelection & { generation: number }> {
    assertCurrent(generation)
    const response = await authenticatedRequest('/api/auth/session/active-role', generation, {
      method: 'PUT',
      body: JSON.stringify(input),
    })
    if (!response.ok) throw new TmaAuthError(response.status, 'Не удалось сменить роль')
    const value = await response.json() as TmaSelectActiveRoleResponse
    if (!value.accessToken) throw new TmaAuthError(response.status, 'Сервер не вернул access token для выбранной роли')
    const session = adaptProfile(value.session)
    if (session.activeRole !== input.role) throw new TmaAuthError(response.status, 'Сервер не подтвердил выбранную роль')
    assertCurrent(generation)
    const nextGeneration = clear()
    accessToken.value = value.accessToken
    knownProfile = session
    knownProfileGeneration = nextGeneration
    return { accessToken: value.accessToken, expiresIn: value.expiresIn ?? 0, session, generation: nextGeneration }
  }

  async function logoutCurrent(): Promise<void> {
    const generation = currentGeneration()
    const token = accessTokenFor(generation)
    clear()
    if (!token) return
    try {
      const response = await options.fetcher('/api/auth/logout', {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + token },
        credentials: 'include',
      })
      if (!response.ok) {
        throw new TmaAuthError(response.status, 'Не удалось подтвердить отзыв текущей сессии')
      }
    } catch (cause) {
      if (cause instanceof TmaAuthError) throw cause
      throw new TmaAuthError(0, 'Не удалось связаться с Auth и подтвердить отзыв текущей сессии')
    }
  }

  function createProfilePort(generation = currentGeneration(), profileOptions: TmaProfilePortOptions = {}): ProfilePort {
    return {
      getSnapshot: async () => {
        const response = await profileResponse('/api/auth/session', generation, {}, 'profile')
        const value = await profileJson<TmaCurrentSession>(response, 'profile')
        assertCurrent(generation)
        const profile = adaptProfile(value)
        rememberProfile(profile, generation)
        return profile
      },
      selectRole: async (input) => {
        const result = await selectRoleFor(generation, input)
        return { accessToken: result.accessToken, expiresIn: result.expiresIn, session: result.session }
      },
      listSessions: async (input?: ProfilePageRequest): Promise<ProfileSessionsPage> => {
        const response = await profileResponse(profilePagePath('/api/auth/sessions', input), generation, {}, 'sessions')
        const value = await profileJson<unknown>(response, 'sessions')
        assertCurrent(generation)
        return adaptSessionsPage(value)
      },
      listHistory: async (input?: ProfilePageRequest): Promise<ProfileHistoryPage> => {
        const response = await profileResponse(profilePagePath('/api/auth/account-history', input), generation, {}, 'account history')
        const value = await profileJson<unknown>(response, 'account history')
        assertCurrent(generation)
        return adaptHistoryPage(value)
      },
      changePassword: async (input) => {
        const response = await profileResponse('/api/auth/change-password', generation, {
          method: 'POST',
          body: JSON.stringify(input),
        }, 'password')
        await assertEmptyProfileResponse(response, 'password')
        assertCurrent(generation)
      },
      logoutAll: async () => {
        const response = await profileResponse('/api/auth/logout-all', generation, { method: 'POST' }, 'sessions')
        await assertEmptyProfileResponse(response, 'sessions')
        assertCurrent(generation)
      },
      ...(profileOptions.onInvalidated ? { onInvalidated: profileOptions.onInvalidated } : {}),
      isOnline: () => typeof navigator === 'undefined' || navigator.onLine !== false,
    }
  }

  async function profileResponse(
    path: string,
    generation: number,
    init: RequestInit,
    operation: string,
  ): Promise<Response> {
    assertCurrent(generation)
    let response: Response
    try {
      response = await authenticatedRequest(path, generation, init)
      if (response.status === 401) {
        await authenticateFor(generation)
        response = await authenticatedRequest(path, generation, init)
      }
    } catch (cause) {
      if (cause instanceof StaleSessionGenerationError) throw cause
      assertCurrent(generation)
      const status = isRecord(cause) && typeof cause.status === 'number' ? cause.status : undefined
      if (status === 401) throw new ProfileRequestError('INVALID_SESSION', 'Сессия больше недействительна', status, cause)
      throw new ProfileRequestError('NETWORK', `Не удалось загрузить данные профиля: ${operation}`, status, cause)
    }
    assertCurrent(generation)
    if (!response.ok) {
      const error = await profileResponseError(response, operation)
      assertCurrent(generation)
      throw error
    }
    return response
  }

  async function authenticatedRequest(path: string, generation: number, init: RequestInit = {}): Promise<Response> {
    const token = accessTokenFor(generation)
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    if (typeof init.body === 'string') headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    return options.fetcher(path, { ...init, headers, credentials: 'include' })
  }

  function clear(): number {
    resetGeneration.value += 1
    accessToken.value = null
    knownProfile = null
    knownProfileGeneration = null
    return currentGeneration()
  }

  return {
    accessToken,
    resetGeneration,
    generation: resetGeneration,
    currentGeneration,
    subscribeInvalidation: (listener: () => void) => {
      invalidationListeners.add(listener)
      return () => { invalidationListeners.delete(listener) }
    },
    isCurrent: (generation: number) => generation === currentGeneration(),
    authenticate,
    authenticateFor,
    createApi,
    createHeadmanApi,
    createHeadmanJournalApi,
    createHeadmanStatsApi,
    createHeadmanRequestsApi,
    createHeadmanGroupApi,
    createHeadmanHomeworkApi,
    createHeadmanSubjectsApi,
    createTeacherApi,
    createReportDownloadClient,
    createAdminSemesterApi,
    createAdminDashboardApi,
    createAdminUsersApi,
    createAdminGroupsApi,
    getProfileFor,
    selectRoleFor,
    createProfilePort,
    logoutCurrent,
    clear,
  }
}

// Keep the authority comparison equivalent to the PWA owner's semantics.
function authAuthorityIdentity(profile: ProfileSnapshot): string {
  const grants = profile.roles.map((grant) => [
    grant.grantId, grant.role, grant.status, grant.groupId ?? null,
    grant.contextLabel ?? null, grant.selectable, grant.readOnly,
  ] as const).sort((left, right) => left[0] < right[0] ? -1 : left[0] > right[0] ? 1 : 0)
  return JSON.stringify([
    profile.sessionId, profile.userId, profile.activeRole, profile.sessionVersion,
    profile.rolesVersion, profile.readOnly, grants,
  ])
}

function adaptProfile(value: TmaCurrentSession): ProfileSnapshot {
  if (!value || typeof value !== 'object' || !value.sessionId || !value.userId || !value.displayName) {
    throw new TmaAuthError(502, 'Сервер вернул неполный профиль')
  }
  const roles = value.roles.map((grant): ProfileRoleGrant => {
    if (!grant.grantId || !isProfileRole(grant.role) || !isRoleStatus(grant.status)
      || typeof grant.selectable !== 'boolean' || typeof grant.readOnly !== 'boolean') {
      throw new TmaAuthError(502, 'Сервер вернул некорректное право роли')
    }
    return {
      grantId: grant.grantId,
      role: grant.role,
      status: grant.status,
      ...(grant.groupId === undefined ? {} : { groupId: grant.groupId }),
      ...(grant.contextLabel === undefined ? {} : { contextLabel: grant.contextLabel }),
      selectable: grant.selectable,
      readOnly: grant.readOnly,
    }
  })
  const activeRole = value.activeRole === undefined ? null : value.activeRole
  if (activeRole !== null && !isProfileRole(activeRole)) throw new TmaAuthError(502, 'Сервер вернул неизвестную активную роль')
  const policy = value.passwordPolicy
  if (policy.normalization !== 'NONE'
    || policy.minCodePoints === undefined
    || policy.maxUtf8Bytes === undefined
    || policy.requiresDecimalDigit === undefined) {
    throw new TmaAuthError(502, 'Сервер вернул неполную политику пароля')
  }
  return {
    sessionId: value.sessionId,
    userId: value.userId,
    displayName: value.displayName,
    ...(value.groupLabel === undefined ? {} : { groupLabel: value.groupLabel }),
    sessionVersion: value.sessionVersion,
    rolesVersion: value.rolesVersion,
    activeRole,
    roles,
    readOnly: value.readOnly,
    passwordPolicy: {
      minCodePoints: policy.minCodePoints,
      maxUtf8Bytes: policy.maxUtf8Bytes,
      requiresDecimalDigit: policy.requiresDecimalDigit,
      specialCategories: policy.specialCategories.filter((item): item is 'P' | 'S' => item === 'P' || item === 'S'),
      normalization: 'NONE',
    },
  }
}

function isProfileRole(value: unknown): value is ProfileRole {
  return value === 'STUDENT' || value === 'HEADMAN' || value === 'TEACHER' || value === 'ADMIN'
}

function isRoleStatus(value: unknown): value is ProfileRoleGrant['status'] {
  return value === 'ACTIVE' || value === 'SUSPENDED' || value === 'EXPELLED' || value === 'GRADUATED' || value === 'DISMISSED' || value === 'ARCHIVED'
}

async function profileJson<T>(response: Response, operation: string): Promise<T> {
  try {
    return await response.json() as T
  } catch (cause) {
    throw new ProfileRequestError('UNKNOWN', `Сервер вернул пустой или некорректный ответ (${operation})`, response.status, cause)
  }
}

function profilePagePath(path: string, input?: ProfilePageRequest): string {
  const query = new URLSearchParams()
  if (input?.cursor !== undefined) query.set('cursor', input.cursor)
  if (input?.limit !== undefined) query.set('limit', String(input.limit))
  const suffix = query.toString()
  return suffix ? `${path}?${suffix}` : path
}

function adaptSessionsPage(value: unknown): ProfileSessionsPage {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    throw new ProfileRequestError('UNKNOWN', 'Сервер вернул некорректную страницу сеансов')
  }
  return {
    items: value.items.map(adaptSessionSummary),
    nextCursor: adaptNextCursor(value.nextCursor, 'сеансов'),
  }
}

function adaptSessionSummary(value: unknown): ProfileSessionSummary {
  if (!isRecord(value)) throw new ProfileRequestError('UNKNOWN', 'Сервер вернул некорректный сеанс')
  const session = value as unknown as TmaAuthSessionSummary
  if (!nonEmptyString(session.sessionId)
    || !nonEmptyString(session.authMethod)
    || !nonEmptyString(session.createdAt)
    || !nonEmptyString(session.lastSeenAt)
    || typeof session.current !== 'boolean') {
    throw new ProfileRequestError('UNKNOWN', 'Сервер вернул неполные данные сеанса')
  }
  const clientLabel = optionalNullableString(session.clientLabel, 'clientLabel')
  const locationLabel = optionalNullableString(session.locationLabel, 'locationLabel')
  return {
    sessionId: session.sessionId,
    authMethod: profileAuthMethod(session.authMethod),
    ...(clientLabel === undefined ? {} : { clientLabel }),
    ...(locationLabel === undefined ? {} : { locationLabel }),
    createdAt: session.createdAt,
    lastSeenAt: session.lastSeenAt,
    current: session.current,
  }
}

function adaptHistoryPage(value: unknown): ProfileHistoryPage {
  if (!isRecord(value) || !Array.isArray(value.items)) {
    throw new ProfileRequestError('UNKNOWN', 'Сервер вернул некорректную страницу истории')
  }
  return {
    items: value.items.map(adaptHistoryEvent),
    nextCursor: adaptNextCursor(value.nextCursor, 'истории'),
  }
}

function adaptHistoryEvent(value: unknown): ProfileHistoryEvent {
  if (!isRecord(value)) throw new ProfileRequestError('UNKNOWN', 'Сервер вернул некорректное событие истории')
  const event = value as unknown as TmaAccountHistoryEvent
  if (!nonEmptyString(event.id) || !nonEmptyString(event.type) || !nonEmptyString(event.occurredAt)) {
    throw new ProfileRequestError('UNKNOWN', 'Сервер вернул неполное событие истории')
  }
  const clientLabel = optionalNullableString(event.clientLabel, 'clientLabel')
  const locationLabel = optionalNullableString(event.locationLabel, 'locationLabel')
  return {
    id: event.id,
    type: profileHistoryType(event.type),
    occurredAt: event.occurredAt,
    ...(event.authMethod === undefined ? {} : { authMethod: event.authMethod === null ? null : profileAuthMethod(event.authMethod) }),
    ...(clientLabel === undefined ? {} : { clientLabel }),
    ...(locationLabel === undefined ? {} : { locationLabel }),
  }
}

function adaptNextCursor(value: unknown, operation: string): string | null {
  if (value === undefined || value === null) return null
  if (!nonEmptyString(value)) throw new ProfileRequestError('UNKNOWN', `Сервер вернул некорректный курсор ${operation}`)
  return value
}

function profileAuthMethod(value: unknown): ProfileAuthMethod {
  if (value === 'PASSWORD' || value === 'OTP' || value === 'TMA') return value
  throw new ProfileRequestError('UNKNOWN', 'Сервер вернул неизвестный способ входа')
}

function profileHistoryType(value: unknown): ProfileHistoryType {
  if (value === 'LOGIN'
    || value === 'ROLE_CHANGED'
    || value === 'CURRENT_LOGOUT'
    || value === 'LOGOUT_ALL'
    || value === 'PASSWORD_CHANGED'
    || value === 'SECURITY_REVOKED') return value
  throw new ProfileRequestError('UNKNOWN', 'Сервер вернул неизвестный тип события истории')
}

function optionalNullableString(value: unknown, field: string): string | null | undefined {
  if (value === undefined || value === null || typeof value === 'string') return value
  throw new ProfileRequestError('UNKNOWN', `Сервер вернул некорректное поле ${field}`)
}

async function assertEmptyProfileResponse(response: Response, operation: string): Promise<void> {
  if (response.status !== 204) {
    throw new ProfileRequestError('UNKNOWN', `Сервер должен вернуть HTTP 204 (${operation})`, response.status)
  }
  let contentLength: string | null
  let body: string
  try {
    contentLength = response.headers.get('Content-Length')
    body = await response.text()
  } catch (cause) {
    throw new ProfileRequestError('UNKNOWN', `Не удалось проверить пустой ответ (${operation})`, response.status, cause)
  }
  if ((contentLength !== null && !/^0+$/.test(contentLength.trim())) || body.length !== 0) {
    throw new ProfileRequestError('UNKNOWN', `Сервер вернул некорректный ответ (${operation})`, response.status)
  }
}

async function profileResponseError(response: Response, operation: string): Promise<ProfileRequestError> {
  let payload: unknown = null
  try {
    payload = await response.json() as unknown
  } catch {
    // The status remains authoritative when the server omits a problem body.
  }
  const extras = isRecord(payload) && isRecord(payload.extras) ? payload.extras : null
  const rawCode = extras && typeof extras.code === 'string'
    ? extras.code
    : isRecord(payload) && typeof payload.code === 'string' ? payload.code : null
  const code = profileErrorCode(rawCode, response.status)
  const message = isRecord(payload) && (typeof payload.detail === 'string' || typeof payload.title === 'string')
    ? (typeof payload.detail === 'string' ? payload.detail : payload.title as string)
    : response.status === 401 ? 'Сессия больше недействительна'
      : response.status === 403 ? 'Доступ к данным профиля запрещён'
        : response.status === 503 || response.status === 504 ? 'Сервис авторизации временно недоступен'
          : `Не удалось выполнить запрос (${operation})`
  return new ProfileRequestError(code, message, response.status, payload)
}

function profileErrorCode(serverCode: string | null, status: number): ProfileRequestError['code'] {
  const known: ProfileRequestError['code'][] = [
    'SESSION_STATE_STALE', 'SESSION_VERSION_CONFLICT', 'REFRESH_ALREADY_ROTATED', 'ROLE_NOT_GRANTED',
    'ROLE_NOT_SELECTABLE', 'ROLE_READ_ONLY', 'BOOTSTRAP_SCOPE_DENIED', 'CURRENT_PASSWORD_INVALID',
    'PASSWORD_POLICY_VIOLATION', 'INVALID_CURSOR', 'AUTHORITY_UNAVAILABLE', 'INVALID_SESSION',
    'SESSION_REVOKED', 'REFRESH_REJECTED', 'OFFLINE_MUTATION_DISABLED', 'ACCOUNT_INVALIDATED', 'NETWORK', 'UNKNOWN',
  ]
  if (serverCode && known.includes(serverCode as ProfileRequestError['code'])) return serverCode as ProfileRequestError['code']
  if (status === 401) return 'INVALID_SESSION'
  if (status === 409) return 'SESSION_STATE_STALE'
  if (status === 503 || status === 504) return 'AUTHORITY_UNAVAILABLE'
  return 'UNKNOWN'
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function nonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

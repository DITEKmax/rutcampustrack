import { ref } from 'vue'
import {
  createGenerationBoundHeadmanScheduleApi,
  createGenerationBoundHeadmanJournalApi,
  createGenerationBoundHeadmanRequestsApi,
  createGenerationBoundHeadmanGroupApi,
  createGenerationBoundHeadmanHomeworkApi,
  createGenerationBoundHeadmanSubjectsApi,
  createGenerationBoundStudentApi,
  createGenerationBoundTeacherApi,
  createGenerationBoundAdminSemesterClient,
  createGenerationBoundAdminGroupsClient,
  createGenerationBoundAdminUsersClient,
  StaleSessionGenerationError,
} from '@rct/mobile-core'
import type {
  HeadmanScheduleApi,
  HeadmanJournalApi,
  HeadmanRequestsApi,
  HeadmanGroupApi,
  HeadmanHomeworkApi,
  HeadmanSubjectsApi,
  ProfileRole,
  ProfileRoleGrant,
  ProfileRoleSelection,
  ProfileSnapshot,
  StudentApi,
  TeacherApi,
  AdminSemesterClient,
  AdminGroupsClient,
  AdminUsersClient,
  authComponents,
} from '@rct/mobile-core'
import { authenticateTma, TmaAuthError } from './tma-auth'

type TmaCurrentSession = authComponents['schemas']['CurrentSessionResponse']
type TmaSelectActiveRoleResponse = authComponents['schemas']['SelectActiveRoleResponse']

export interface TmaSessionOptions {
  fetcher: typeof fetch
  getInitData: () => string | null
}

/** In-memory Telegram session owner; no initData or bearer token is persisted. */
export function useTmaSession(options: TmaSessionOptions) {
  const accessToken = ref<string | null>(null)
  const resetGeneration = ref(0)
  let authenticateInFlight: { generation: number; promise: Promise<void> } | null = null

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

  async function authenticateFor(generation: number): Promise<void> {
    assertCurrent(generation)
    if (authenticateInFlight?.generation === generation) return authenticateInFlight.promise
    const initData = options.getInitData()
    if (!initData) throw new Error('Открой приложение из Telegram')

    const promise = authenticateTma(options.fetcher, initData)
      .then((token) => {
        assertCurrent(generation)
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

  function createAdminSemesterApi(fetcher?: typeof fetch): AdminSemesterClient {
    return createGenerationBoundAdminSemesterClient({
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
    return adaptProfile(value)
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
    return { accessToken: value.accessToken, expiresIn: value.expiresIn ?? 0, session, generation: nextGeneration }
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
    return currentGeneration()
  }

  return {
    accessToken,
    resetGeneration,
    generation: resetGeneration,
    currentGeneration,
    isCurrent: (generation: number) => generation === currentGeneration(),
    authenticate,
    authenticateFor,
    createApi,
    createHeadmanApi,
    createHeadmanJournalApi,
    createHeadmanRequestsApi,
    createHeadmanGroupApi,
    createHeadmanHomeworkApi,
    createHeadmanSubjectsApi,
    createTeacherApi,
    createAdminSemesterApi,
    createAdminUsersApi,
    createAdminGroupsApi,
    getProfileFor,
    selectRoleFor,
    clear,
  }
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

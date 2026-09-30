import { ref } from 'vue'
import { createGenerationBoundHeadmanScheduleApi } from '../../mobile-core/src/features/schedule/headman-schedule-client'
import { createGenerationBoundHeadmanJournalApi } from '../../mobile-core/src/features/headman-journal/headman-journal-client'
import { createGenerationBoundHeadmanRequestsApi } from '../../mobile-core/src/features/headman-requests/headman-requests-client'
import { createGenerationBoundHeadmanGroupApi } from '../../mobile-core/src/features/headman-group/headman-group-client'
import { createGenerationBoundHeadmanHomeworkApi } from '../../mobile-core/src/features/homework/headman-homework-client'
import { createGenerationBoundHeadmanSubjectsApi } from '../../mobile-core/src/features/headman-subjects/headman-subjects-client'
import { createGenerationBoundHeadmanStatsApi } from '../../mobile-core/src/features/headman-stats/headman-stats-client'
import {
  createGenerationBoundAdminDashboardClient,
  createGenerationBoundAdminSemesterClient,
  createGenerationBoundAdminGroupsClient,
  createGenerationBoundAdminUsersClient,
  createGenerationBoundStudentApi,
  StaleSessionGenerationError,
} from '../../mobile-core/src/shared/session-owner'
import { createGenerationBoundTeacherApi } from '../../mobile-core/src/features/teacher/teacher-client'
import type { AdminSemesterClient } from '../../mobile-core/src/features/admin-semester/admin-semester-client'
import type { AdminDashboardClient } from '../../mobile-core/src/features/admin-dashboard/admin-dashboard-client'
import type { AdminUsersClient } from '../../mobile-core/src/features/admin-users/admin-users-client'
import type { AdminGroupsClient } from '../../mobile-core/src/features/admin-groups/admin-groups-client'
import type { HeadmanScheduleApi } from '../../mobile-core/src/features/schedule/headman-schedule-client'
import type { HeadmanJournalApi } from '../../mobile-core/src/features/headman-journal/headman-journal-client'
import type { HeadmanRequestsApi } from '../../mobile-core/src/features/headman-requests/headman-requests-client'
import type { HeadmanGroupApi } from '../../mobile-core/src/features/headman-group/headman-group-client'
import type { HeadmanHomeworkApi } from '../../mobile-core/src/features/homework/headman-homework-client'
import type { HeadmanSubjectsApi } from '../../mobile-core/src/features/headman-subjects/headman-subjects-client'
import type { HeadmanStatsApi } from '../../mobile-core/src/features/headman-stats/headman-stats-client'
import type { StudentApi } from '../../mobile-core/src/api/student-client'
import type { TeacherApi } from '../../mobile-core/src/features/teacher/teacher-client'
import {
  AuthRequestError,
  createAuthClient,
  type AuthClient,
  type AuthLoginInput,
  type AuthSelectRoleInput,
  type AuthToken,
} from './auth-client'
import type {
  ProfileHistoryPage,
  ProfilePageRequest,
  ProfilePort,
  ProfileRoleGrant,
  ProfileRoleSelection,
  ProfileSnapshot,
  ProfileSessionsPage,
} from '../../mobile-core/src/features/profile/profile-types'

export interface PwaAuthInvalidationChannel {
  postMessage(message: unknown): void
  addEventListener(type: 'message', listener: (event: MessageEvent<unknown>) => void): void
  removeEventListener(type: 'message', listener: (event: MessageEvent<unknown>) => void): void
  close(): void
}

export interface PwaAuthOptions {
  fetcher?: typeof fetch
  /** Injected in tests; production uses a browser BroadcastChannel when available. */
  channel?: PwaAuthInvalidationChannel
  /** Injected in tests; production uses localStorage to survive reloads and coordinate tabs. */
  logoutMarkerStorage?: PwaAuthLogoutMarkerStorage | null
}

export interface PwaAuthLogoutMarkerStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

export type PwaAuthLogoutState = 'pending' | 'confirmed' | 'unconfirmed'

export interface PwaProfilePortOptions {
  onInvalidated?: ProfilePort['onInvalidated']
  onRefreshAlreadyRotated?: ProfilePort['onRefreshAlreadyRotated']
}

export type PwaAuthInvalidationReason = 'external' | 'authority-changed'

export interface PwaAuthLoginResult extends AuthToken {
  generation: number
}

export interface PwaAuthRoleResult extends ProfileRoleSelection {
  generation: number
}

export class PwaAuthError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'PwaAuthError'
  }
}

const INVALIDATION_MESSAGE = 'rct-auth-invalidate-v1'
const EXPLICIT_LOGOUT_STORAGE_KEY = 'rct-pwa-explicit-logout-v1'
const LOGOUT_TIMEOUT_MS = 10_000

export function usePwaAuth(options: PwaAuthOptions = {}) {
  const accessToken = ref<string | null>(null)
  const resetGeneration = ref(0)
  const request = options.fetcher ?? ((input: RequestInfo | URL, init?: RequestInit) => globalThis.fetch(input, init))
  const client: AuthClient = createAuthClient({
    fetcher: request,
    accessToken: () => accessToken.value,
  })
  let refreshInFlight: { generation: number; promise: Promise<void> } | null = null
  const invalidationListeners = new Set<(reason: PwaAuthInvalidationReason) => void>()
  const channel = options.channel ?? createBrowserChannel()
  const logoutMarkerStorage = options.logoutMarkerStorage === undefined
    ? createBrowserLogoutMarkerStorage()
    : options.logoutMarkerStorage
  let logoutState: PwaAuthLogoutState | null = null
  let knownProfile: ProfileSnapshot | null = null
  let knownProfileGeneration: number | null = null

  function explicitLogoutState(): PwaAuthLogoutState | null {
    try {
      const stored = logoutMarkerStorage?.getItem(EXPLICIT_LOGOUT_STORAGE_KEY)
      if (stored === 'pending' || stored === 'confirmed' || stored === 'unconfirmed') logoutState = stored
      // A missing persistent marker cannot clear this instance's latch: the
      // preceding storage write may have failed, but logout still happened.
    } catch {
      // A storage restriction cannot clear this tab's in-memory sign-out latch.
    }
    return logoutState
  }

  function setExplicitLogoutState(state: PwaAuthLogoutState | null): void {
    logoutState = state
    try {
      if (!state) logoutMarkerStorage?.removeItem(EXPLICIT_LOGOUT_STORAGE_KEY)
      else logoutMarkerStorage?.setItem(EXPLICIT_LOGOUT_STORAGE_KEY, state)
    } catch {
      // The in-memory latch still prevents automatic recovery in this tab.
    }
  }

  function rememberProfile(profile: ProfileSnapshot, generation: number): void {
    knownProfile = profile
    knownProfileGeneration = generation
  }

  function notifyInvalidation(reason: PwaAuthInvalidationReason): void {
    for (const listener of invalidationListeners) {
      try {
        listener(reason)
      } catch {
        // A UI listener cannot make the generation-bound invalidation unsafe.
      }
    }
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

  async function refreshFor(generation: number): Promise<void> {
    assertCurrent(generation)
    if (refreshInFlight?.generation === generation) return refreshInFlight.promise

    const promise = client.refresh()
      .then(async (token) => {
        assertCurrent(generation)
        const expectedProfile = knownProfileGeneration === generation ? knownProfile : null
        if (expectedProfile) {
          // Validate the candidate bearer before exposing it to the
          // generation-bound StudentApi. Refresh may succeed while the
          // server has moved this cookie to another role/session/context.
          const candidateClient = createAuthClient({
            fetcher: request,
            accessToken: () => token.accessToken,
          })
          const refreshedProfile = await candidateClient.getSnapshot()
          assertCurrent(generation)
          if (authAuthorityIdentity(expectedProfile) !== authAuthorityIdentity(refreshedProfile)) {
            // Advance first: every API created for the old generation now
            // fails closed. The candidate is only available to a new shell
            // generation after its profile has passed validation.
            const nextGeneration = clear()
            accessToken.value = token.accessToken
            rememberProfile(refreshedProfile, nextGeneration)
            notifyInvalidation('authority-changed')
            throw new StaleSessionGenerationError()
          }
          accessToken.value = token.accessToken
          rememberProfile(refreshedProfile, generation)
          return
        }
        accessToken.value = token.accessToken
      })
      .finally(() => {
        if (refreshInFlight?.promise === promise) refreshInFlight = null
      })
    refreshInFlight = { generation, promise }
    return promise
  }

  function refresh(): Promise<void> {
    return refreshFor(currentGeneration())
  }

  async function login(input: AuthLoginInput): Promise<PwaAuthLoginResult> {
    const generation = currentGeneration()
    const token = await client.login(input)
    assertCurrent(generation)
    // A successful login replaces any old in-memory authority. The token is
    // installed only in the new generation after the old one is invalidated.
    const nextGeneration = clear()
    setExplicitLogoutState(null)
    accessToken.value = token.accessToken
    return { ...token, generation: nextGeneration }
  }

  async function getSessionFor(generation: number): Promise<ProfileSnapshot> {
    assertCurrent(generation)
    const snapshot = await client.getSnapshot()
    assertCurrent(generation)
    rememberProfile(snapshot, generation)
    return snapshot
  }

  async function selectRoleFor(generation: number, input: AuthSelectRoleInput): Promise<PwaAuthRoleResult> {
    assertCurrent(generation)
    const selection = await client.selectRole(input)
    assertCurrent(generation)
    // The old role's token must be unusable before the fresh role token is
    // visible to any BFF client.
    const nextGeneration = clear()
    accessToken.value = selection.accessToken
    rememberProfile(selection.session, nextGeneration)
    return { ...selection, generation: nextGeneration }
  }

  function createApi(fetcher?: typeof fetch): StudentApi {
    return createGenerationBoundStudentApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createHeadmanApi(fetcher?: typeof fetch): HeadmanScheduleApi {
    return createGenerationBoundHeadmanScheduleApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createHeadmanJournalApi(fetcher?: typeof fetch): HeadmanJournalApi {
    return createGenerationBoundHeadmanJournalApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createHeadmanStatsApi(fetcher?: typeof fetch): HeadmanStatsApi {
    return createGenerationBoundHeadmanStatsApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createHeadmanRequestsApi(fetcher?: typeof fetch): HeadmanRequestsApi {
    return createGenerationBoundHeadmanRequestsApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createHeadmanGroupApi(fetcher?: typeof fetch): HeadmanGroupApi {
    return createGenerationBoundHeadmanGroupApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createHeadmanHomeworkApi(fetcher?: typeof fetch): HeadmanHomeworkApi {
    return createGenerationBoundHeadmanHomeworkApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createHeadmanSubjectsApi(fetcher?: typeof fetch): HeadmanSubjectsApi {
    return createGenerationBoundHeadmanSubjectsApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createTeacherApi(fetcher?: typeof fetch): TeacherApi {
    return createGenerationBoundTeacherApi({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createAdminSemesterApi(fetcher?: typeof fetch): AdminSemesterClient {
    return createGenerationBoundAdminSemesterClient({
      currentGeneration,
      accessTokenFor,
      refreshFor,
      sessionScopeFor,
    }, fetcher)
  }

  function createAdminDashboardApi(fetcher?: typeof fetch): AdminDashboardClient {
    return createGenerationBoundAdminDashboardClient({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createAdminUsersApi(fetcher?: typeof fetch): AdminUsersClient {
    return createGenerationBoundAdminUsersClient({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createAdminGroupsApi(fetcher?: typeof fetch): AdminGroupsClient {
    return createGenerationBoundAdminGroupsClient({
      currentGeneration,
      accessTokenFor,
      refreshFor,
    }, fetcher)
  }

  function createProfilePort(generation = currentGeneration(), options: PwaProfilePortOptions = {}): ProfilePort {
    function assertPortCurrent(): void {
      assertCurrent(generation)
    }

    async function guarded<T>(requestProfile: () => Promise<T>): Promise<T> {
      assertPortCurrent()
      let value: T
      try {
        value = await requestProfile()
      } catch (cause) {
        assertPortCurrent()
        throw cause
      }
      assertPortCurrent()
      return value
    }

    return {
      getSnapshot: async () => {
        const profile = await guarded(() => client.getSnapshot())
        rememberProfile(profile, generation)
        return profile
      },
      selectRole: (input) => guarded(() => client.selectRole(input)),
      listSessions: (input?: ProfilePageRequest): Promise<ProfileSessionsPage> => guarded(() => client.listSessions(input)),
      listHistory: (input?: ProfilePageRequest): Promise<ProfileHistoryPage> => guarded(() => client.listHistory(input)),
      changePassword: (input) => guarded(() => client.changePassword(input)),
      logoutAll: () => guarded(() => client.logoutAll()),
      ...(options.onInvalidated ? { onInvalidated: options.onInvalidated } : {}),
      ...(options.onRefreshAlreadyRotated ? { onRefreshAlreadyRotated: options.onRefreshAlreadyRotated } : {}),
      isOnline: () => typeof navigator === 'undefined' || navigator.onLine !== false,
    }
  }

  function setToken(token: string, generation = currentGeneration()): void {
    assertCurrent(generation)
    accessToken.value = token
  }

  /** Invalidates every API client captured before this call. */
  function clear(options: { broadcast?: boolean } = {}): number {
    resetGeneration.value += 1
    accessToken.value = null
    knownProfile = null
    knownProfileGeneration = null
    if (options.broadcast !== false) {
      try {
        channel?.postMessage({ type: INVALIDATION_MESSAGE })
      } catch {
        // A closed or unavailable channel cannot weaken local invalidation.
      }
    }
    return currentGeneration()
  }

  function subscribeInvalidation(listener: (reason: PwaAuthInvalidationReason) => void): () => void {
    invalidationListeners.add(listener)
    return () => invalidationListeners.delete(listener)
  }

  async function logout(clearSnapshot: () => Promise<void>): Promise<void> {
    setExplicitLogoutState('pending')
    // Invalidate before the remote request so an in-flight 401 cannot retry
    // with a replacement token while logout is still waiting on the network.
    const logoutGeneration = clear()
    // Start local invalidation before the remote request completes. The
    // storage owner has its own generation gate, so a queued old write cannot
    // recreate the pointer while the logout request is in flight.
    const snapshotCleared = clearSnapshot()
    // Observe a fast storage rejection immediately while the remote request
    // is still pending; the original promise is awaited in finally so its
    // failure remains visible to the caller.
    void snapshotCleared.catch(() => undefined)
    let logoutError: PwaAuthError | null = null
    try {
      // Keep this request shape stable for the cookie-only logout endpoint.
      const response = await request('/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
        signal: AbortSignal.timeout(LOGOUT_TIMEOUT_MS),
      })
      if (!response.ok) throw new PwaAuthError(response.status, 'Не удалось подтвердить отзыв текущей сессии')
      if (currentGeneration() === logoutGeneration) setExplicitLogoutState('confirmed')
    } catch (cause) {
      if (currentGeneration() === logoutGeneration) setExplicitLogoutState('unconfirmed')
      logoutError = cause instanceof PwaAuthError
        ? cause
        : new PwaAuthError(0, 'Не удалось связаться с Auth и подтвердить отзыв текущей сессии')
    }
    await snapshotCleared
    if (logoutError) throw logoutError
  }

  function dispose(): void {
    if (!channel) return
    channel.removeEventListener('message', onChannelMessage)
    try { channel.close() } catch { /* already closed */ }
  }

  function onChannelMessage(event: MessageEvent<unknown>): void {
    if (!isInvalidationMessage(event.data)) return
    clear({ broadcast: false })
    notifyInvalidation('external')
  }

  channel?.addEventListener('message', onChannelMessage)

  return {
    accessToken,
    resetGeneration,
    generation: resetGeneration,
    currentGeneration,
    isCurrent: (generation: number) => generation === currentGeneration(),
    explicitLogoutState,
    canAutoBootstrap: () => explicitLogoutState() === null,
    refresh,
    refreshFor,
    login,
    getSessionFor,
    selectRoleFor,
    createApi,
    createHeadmanApi,
    createHeadmanJournalApi,
    createHeadmanStatsApi,
    createHeadmanRequestsApi,
    createHeadmanGroupApi,
    createHeadmanHomeworkApi,
    createHeadmanSubjectsApi,
    createTeacherApi,
    createAdminSemesterApi,
    createAdminDashboardApi,
    createAdminUsersApi,
    createAdminGroupsApi,
    createProfilePort,
    setToken,
    clear,
    logout,
    subscribeInvalidation,
    dispose,
    client,
  }
}

/**
 * Identity used to decide whether a refreshed bearer still belongs to the
 * mounted authority. Optional grant fields are normalized so omission and an
 * explicit null carry the same wire meaning, while every grant's group
 * context remains part of the comparison.
 */
export function authAuthorityIdentity(profile: ProfileSnapshot): string {
  const grants = [...profile.roles]
    .map(normalizeRoleGrant)
    .sort((left, right) => left[0] < right[0] ? -1 : left[0] > right[0] ? 1 : 0)
  return JSON.stringify([
    profile.sessionId,
    profile.userId,
    profile.activeRole,
    profile.sessionVersion,
    profile.rolesVersion,
    profile.readOnly,
    grants,
  ])
}

function normalizeRoleGrant(grant: ProfileRoleGrant): readonly [
  string,
  string,
  string,
  string | null,
  string | null,
  boolean,
  boolean,
] {
  return [
    grant.grantId,
    grant.role,
    grant.status,
    grant.groupId ?? null,
    grant.contextLabel ?? null,
    grant.selectable,
    grant.readOnly,
  ]
}

function createBrowserChannel(): PwaAuthInvalidationChannel | undefined {
  if (typeof window === 'undefined' || typeof window.BroadcastChannel !== 'function') return undefined
  return new window.BroadcastChannel('rct-auth-invalidation') as unknown as PwaAuthInvalidationChannel
}

function createBrowserLogoutMarkerStorage(): PwaAuthLogoutMarkerStorage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null
  }
}

function isInvalidationMessage(value: unknown): boolean {
  return typeof value === 'object'
    && value !== null
    && 'type' in value
    && value.type === INVALIDATION_MESSAGE
}

export { AuthRequestError }

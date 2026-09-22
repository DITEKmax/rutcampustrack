<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, toRaw } from 'vue'
import {
  AdminMapClient,
  AdminMapScreen,
  AdminSemesterApiError,
  AdminSemesterScreen,
  AdminGroupsApiError,
  AdminGroupsScreen,
  AdminUsersApiError,
  AdminUsersScreen,
  AdminRoleNavigation,
  CampusMapClient,
  SemesterSnapshotStore,
  HeadmanScheduleApiError,
  HeadmanJournalApiError,
  HeadmanRequestsApiError,
  HeadmanGroupApiError,
  HeadmanScheduleScreen,
  MapScreen,
  StudentApi,
  StudentApiError,
  StudentFeatureOwner,
  TeacherApi,
  TeacherApiError,
  TeacherFeatureOwner,
  ProfileRequestError,
  RoleSwitchScreen,
  StaleSessionGenerationError,
  commandFromCoordinates,
  createFixtureTransport,
  createMobileTheme,
  offlineToday,
  studentFeatureScope,
  studentFeatureScopeIdentity,
  studentOfflineScopeKey,
  unavailableCommand,
  unavailableReason,
  type SemesterSnapshot,
  type SnapshotCleanupResult,
  type StudentCheckinCommand,
  type StudentFeatureScope,
  type StudentHomework,
  type StudentSemesterSchedule,
  type StudentSession,
  type HeadmanScheduleApi,
  type HeadmanJournalApi,
  type HeadmanRequestsApi,
  type HeadmanGroupApi,
  type HeadmanHomeworkApi,
  type HeadmanAssistantPermission,
  type AdminSemesterClient,
  type AdminGroupsClient,
  type AdminUsersClient,
} from '@rct/mobile-core'
import type { ProfilePort, ProfileRole, ProfileSnapshot } from '@rct/mobile-core'
import { AuthRequestError } from './auth-client'
import { PwaAuthError, usePwaAuth, type PwaAuthInvalidationReason } from './auth'
import LoginScreen from './LoginScreen.vue'
import { PwaHostAdapter } from './pwa-host'
import { isOfflineBootstrapRecoveryError } from './bootstrap-policy'
import { createPwaRoleSelection } from './role-flow'
import { assertAuthBffCoherence } from './session-coherence'
import PwaUpdateGate from './PwaUpdateGate.vue'
import { createPwaFetcher } from './pwa-version'

const fixtureMode = import.meta.env.VITE_MOBILE_FIXTURE_MODE === 'true'
const fixtureDiagnosticsMode = fixtureMode && new URLSearchParams(window.location.search).get('fixtureDiagnostics') === 'true'
const fixtureServiceWorkerBuildEnabled = import.meta.env.PROD
const fixtureTransport = fixtureMode ? createFixtureTransport() : undefined
const pwaFetcher = createPwaFetcher()
const requestFetcher: typeof fetch = fixtureTransport ?? pwaFetcher
const auth = usePwaAuth({ fetcher: requestFetcher })
const mapClient = new CampusMapClient({
  accessToken: () => auth.accessToken.value,
  currentGeneration: () => auth.currentGeneration(),
  onUnauthorized: () => auth.refreshFor(auth.currentGeneration()),
  fetcher: requestFetcher,
})
const adminMapClient = new AdminMapClient({
  accessToken: () => auth.accessToken.value,
  onUnauthorized: () => auth.refreshFor(auth.currentGeneration()),
  fetcher: requestFetcher,
})
const host = new PwaHostAdapter()
const theme = typeof document === 'undefined' ? null : createMobileTheme()
const snapshotStore = new SemesterSnapshotStore()

const api = shallowRef<StudentApi | null>(null)
const teacherApi = shallowRef<TeacherApi | null>(null)
const teacherSemesterId = ref<number | null>(null)
const headmanApi = shallowRef<HeadmanScheduleApi | null>(null)
const headmanJournalApi = shallowRef<HeadmanJournalApi | null>(null)
const headmanRequestsApi = shallowRef<HeadmanRequestsApi | null>(null)
const headmanGroupApi = shallowRef<HeadmanGroupApi | null>(null)
const assistantJournalApi = shallowRef<HeadmanJournalApi | null>(null)
const assistantRequestsApi = shallowRef<HeadmanRequestsApi | null>(null)
const assistantHomeworkApi = shallowRef<HeadmanHomeworkApi | null>(null)
const assistantPermissions = shallowRef<readonly HeadmanAssistantPermission[]>([])
const adminSemesterApi = shallowRef<AdminSemesterClient | null>(null)
const adminUsersApi = shallowRef<AdminUsersClient | null>(null)
const adminGroupsApi = shallowRef<AdminGroupsClient | null>(null)
const session = shallowRef<StudentSession | null>(null)
const scope = shallowRef<StudentFeatureScope | null>(null)
const profilePort = shallowRef<ProfilePort | null>(null)
const snapshot = shallowRef<SemesterSnapshot | null>(null)
const semesterSchedule = shallowRef<StudentSemesterSchedule | null>(null)
const onlineSemesterSchedule = shallowRef<StudentSemesterSchedule | null>(null)
const offline = ref(typeof navigator !== 'undefined' ? !navigator.onLine : false)
const readOnly = ref(false)
const sessionReady = ref(false)
const bootstrapping = ref(false)
const bootstrapError = ref<string | null>(null)
const authView = ref<'login' | 'role' | 'student' | 'teacher' | 'headman' | 'map' | 'admin-map' | 'admin-semesters' | 'admin-users' | 'admin-groups'>('login')
const authSnapshot = shallowRef<ProfileSnapshot | null>(null)
const authError = shallowRef<ProfileRequestError | null>(null)
const authLoading = ref(false)
const pendingRole = ref<ProfileRole | null>(null)
const fixtureDiagnosticLines = ref<string[]>([])
const ownerRevision = ref(0)
const cleanupResult = ref<SnapshotCleanupResult | null>(null)
const cleanupRetrying = ref(false)
let cleanupUiGeneration = 0
let stopAuthInvalidation = (): void => undefined
let queuedBootstrapAfterInvalidation = false

const cachedToday = computed(() => {
  const value = snapshot.value
  if (!value) return null
  const asOf = value.cachedAt ? new Date(value.cachedAt) : undefined
  return offlineToday(value.schedule, asOf && Number.isFinite(asOf.getTime()) ? asOf : undefined)
})
const cachedHomework = computed(() => snapshot.value?.homework ?? null)
const displayedSemesterSchedule = computed(() => offline.value ? semesterSchedule.value : onlineSemesterSchedule.value)
const mapViewVisible = computed(() => authView.value === 'map')
const adminMapViewVisible = computed(() => authView.value === 'admin-map')
const adminSemesterViewVisible = computed(() => authView.value === 'admin-semesters')
const adminUsersViewVisible = computed(() => authView.value === 'admin-users')
const adminGroupsViewVisible = computed(() => authView.value === 'admin-groups')
const featureVisible = computed(() => api.value !== null || headmanApi.value !== null || snapshot.value !== null
  || teacherApi.value !== null || mapViewVisible.value || adminMapViewVisible.value || adminSemesterViewVisible.value || adminUsersViewVisible.value || adminGroupsViewVisible.value)
const studentViewVisible = computed(() => authView.value === 'student' && featureVisible.value)
const teacherViewVisible = computed(() => authView.value === 'teacher' && teacherApi.value !== null)
const headmanViewVisible = computed(() => authView.value === 'headman' && headmanApi.value !== null)
const headmanGroupId = ref<number | null>(null)
const ownerKey = computed(() => scope.value
  ? `${studentFeatureScopeIdentity(scope.value)}|${ownerRevision.value}`
  : `${snapshot.value?.scopeKey ?? 'offline-read-model'}|${ownerRevision.value}`)

function currentFetcher(): typeof fetch {
  return requestFetcher
}

function authDenialStatus(error: unknown): number | null {
  if (error instanceof PwaAuthError) return error.status
  if (error instanceof AuthRequestError) return error.status ?? null
  if (error instanceof StudentApiError) return error.response.status
  if (error instanceof TeacherApiError) return error.response.status
  if (error instanceof HeadmanScheduleApiError) return error.response.status
  if (error instanceof HeadmanJournalApiError) return error.response.status
  if (error instanceof HeadmanRequestsApiError) return error.response.status
  if (error instanceof HeadmanGroupApiError) return error.response.status
  if (error instanceof AdminSemesterApiError) return error.response.status
  if (error instanceof AdminGroupsApiError) return error.response.status
  if (error instanceof AdminUsersApiError) return error.response.status
  return null
}

function isConfirmedOnlineAuthDenial(error: unknown): boolean {
  if (authDenialStatus(error) === 401) return true
  if (error instanceof AuthRequestError) {
    return error.code === 'INVALID_SESSION'
      || error.code === 'SESSION_REVOKED'
      || error.code === 'ACCOUNT_INVALIDATED'
      || error.code === 'REFRESH_REJECTED'
  }
  if (error instanceof StudentApiError) {
    const code = error.problem?.extras?.code
    return code === 'INVALID_SESSION' || code === 'SESSION_REVOKED' || code === 'ACCOUNT_INVALIDATED'
  }
  return false
}

function persistedScope(value: StudentFeatureScope) {
  return {
    userId: value.userId!,
    activeRole: value.activeRole!,
    groupId: value.groupId,
    semesterId: value.semesterId!,
  }
}

function makeSnapshot(
  value: StudentFeatureScope,
  schedule: StudentSemesterSchedule,
  previous: SemesterSnapshot | null,
  homework: StudentHomework | null | undefined,
  etag: string | null,
): SemesterSnapshot {
  const nextHomework = homework === undefined ? previous?.homework ?? null : homework
  const cachedAt = nextHomework?.serverNow ?? schedule.updatedAt ?? previous?.cachedAt ?? null
  return {
    ownerId: value.userId!,
    scopeKey: studentOfflineScopeKey(value),
    scope: persistedScope(value),
    schedule,
    etag,
    homework: nextHomework ? structuredClone(toRaw(nextHomework)) : null,
    ...(cachedAt ? { cachedAt } : {}),
  }
}

function isPersistedSnapshot(value: SemesterSnapshot | null): value is SemesterSnapshot {
  if (!value || typeof value.ownerId !== 'string' || !value.schedule || typeof value.schedule !== 'object') return false
  const persisted = value.scope
  if (!persisted || typeof value.scopeKey !== 'string') return false
  return typeof persisted.userId === 'string'
    && persisted.userId === value.ownerId
    && persisted.activeRole === 'STUDENT'
    && typeof persisted.semesterId === 'string'
    && persisted.semesterId === value.schedule.semester.id
    && (persisted.groupId === null || typeof persisted.groupId === 'string')
}

async function loadOfflineSnapshot(): Promise<boolean> {
  if (authView.value === 'headman' || authView.value === 'teacher') return false
  const generation = auth.currentGeneration()
  // Do not keep a previous online query model alive while deciding whether a
  // committed snapshot can be used. This also prevents a late query result
  // from becoming the offline view after a reconnect failure.
  scope.value = null
  api.value = null
  teacherApi.value = null
  teacherSemesterId.value = null
  headmanApi.value = null
  headmanJournalApi.value = null
  headmanRequestsApi.value = null
  headmanGroupApi.value = null
  assistantJournalApi.value = null
  assistantRequestsApi.value = null
  assistantHomeworkApi.value = null
  assistantPermissions.value = []
  adminSemesterApi.value = null
  adminUsersApi.value = null
  adminGroupsApi.value = null
  headmanGroupId.value = null
  session.value = null
  authSnapshot.value = null
  authError.value = null
  semesterSchedule.value = null
  onlineSemesterSchedule.value = null
  snapshot.value = null
  ownerRevision.value += 1
  // A known unresolved cleanup keeps every current-realm offline hydration
  // blocked until the user completes the local retry.
  if (snapshotStore.hasUnresolvedCleanup()) return false
  try {
    const value = await snapshotStore.readCurrent()
    if (!auth.isCurrent(generation)) return false
    if (!isPersistedSnapshot(value)) return false
    const persisted = value.scope
    if (!persisted) return false
    snapshot.value = value
    semesterSchedule.value = value.schedule
    scope.value = {
      userId: persisted.userId,
      activeRole: persisted.activeRole,
      groupId: persisted.groupId,
      semesterId: persisted.semesterId,
      sessionId: null,
      sessionVersion: null,
      rolesVersion: null,
      readOnly: true,
      resetGeneration: auth.currentGeneration(),
    }
    api.value = null
    teacherApi.value = null
    teacherSemesterId.value = null
    session.value = null
    offline.value = true
    readOnly.value = true
    authSnapshot.value = null
    authError.value = null
    authView.value = 'student'
    ownerRevision.value += 1
    return true
  } catch {
    return false
  }
}

/** Invalidate reactive owner references before Vue unmounts its feature view. */
function invalidateOwnerSynchronously(options: { clearAuth?: boolean } = {}): StudentSession | null {
  const previous = session.value
  cleanupUiGeneration += 1
  cleanupRetrying.value = false
  snapshotStore.invalidatePendingWrites()
  if (options.clearAuth !== false) auth.clear()
  profilePort.value = null
  headmanApi.value = null
  headmanJournalApi.value = null
  headmanRequestsApi.value = null
  headmanGroupApi.value = null
  assistantJournalApi.value = null
  assistantRequestsApi.value = null
  assistantHomeworkApi.value = null
  assistantPermissions.value = []
  adminSemesterApi.value = null
  adminUsersApi.value = null
  adminGroupsApi.value = null
  headmanGroupId.value = null
  teacherApi.value = null
  teacherSemesterId.value = null
  scope.value = null
  api.value = null
  session.value = null
  semesterSchedule.value = null
  onlineSemesterSchedule.value = null
  snapshot.value = null
  return previous
}

function observeCleanupResult(result: SnapshotCleanupResult): void {
  if (result.retryRequired) {
    cleanupResult.value = result
    return
  }
  if (!snapshotStore.hasUnresolvedCleanup()) cleanupResult.value = null
}

async function clearOwnerSnapshot(previous: StudentSession | null): Promise<SnapshotCleanupResult> {
  const uiGeneration = cleanupUiGeneration
  const result = await snapshotStore.clearDetailed(previous?.user.id)
  if (uiGeneration === cleanupUiGeneration) observeCleanupResult(result)
  return result
}

async function handleProfileInvalidated(reason: 'logout-all' | 'password-changed' | 'account-invalidated'): Promise<void> {
  const previous = invalidateOwnerSynchronously()
  await clearOwnerSnapshot(previous)
  authSnapshot.value = null
  authError.value = null
  authView.value = 'login'
  sessionReady.value = true
  offline.value = false
  readOnly.value = true
  bootstrapError.value = reason === 'password-changed'
    ? 'Пароль изменён. Войди снова.'
    : reason === 'logout-all'
      ? 'Сеанс завершён на всех устройствах.'
      : 'Сессия больше недоступна.'
}

async function retryPendingCleanup(): Promise<void> {
  const pending = cleanupResult.value
  if (!pending || cleanupRetrying.value) return
  const uiGeneration = ++cleanupUiGeneration
  cleanupRetrying.value = true
  try {
    const result = await snapshotStore.retryCleanup(pending)
    if (uiGeneration === cleanupUiGeneration) observeCleanupResult(result)
  } finally {
    if (uiGeneration === cleanupUiGeneration) cleanupRetrying.value = false
  }
}

async function handleOnlineAuthDenial(error: unknown): Promise<void> {
  const previous = invalidateOwnerSynchronously()
  await clearOwnerSnapshot(previous)
  offline.value = false
  readOnly.value = true
  authSnapshot.value = null
  authView.value = 'login'
  authError.value = null
  bootstrapError.value = error instanceof Error ? error.message : 'Сессия больше недоступна'
}

async function fetchAuthForCurrentGeneration(options: { refresh?: boolean } = {}): Promise<{
  generation: number
  profile: ProfileSnapshot
}> {
  const generation = auth.currentGeneration()
  if (options.refresh !== false || auth.accessToken.value === null) await auth.refreshFor(generation)
  const profile = await auth.getSessionFor(generation)
  if (!auth.isCurrent(generation)) throw new StaleSessionGenerationError()
  return { generation, profile }
}

async function fetchStudentCandidate(
  generation: number,
  profile: ProfileSnapshot,
): Promise<{
  generation: number
  api: StudentApi
  session: StudentSession
  profile: ProfileSnapshot
  assistantJournalApi: HeadmanJournalApi
  assistantRequestsApi: HeadmanRequestsApi
  assistantHomeworkApi: HeadmanHomeworkApi
  assistantPermissions: readonly HeadmanAssistantPermission[]
} | null> {
  if (profile.activeRole !== 'STUDENT') return null
  const candidateApi = auth.createApi(currentFetcher())
  const candidateSession = await candidateApi.getSession()
  const candidateGroupApi = auth.createHeadmanGroupApi(currentFetcher())
  const candidateJournalApi = auth.createHeadmanJournalApi(currentFetcher())
  const candidateRequestsApi = auth.createHeadmanRequestsApi(currentFetcher())
  const candidateHomeworkApi = auth.createHeadmanHomeworkApi(currentFetcher())
  const groupId = candidateSession.group?.id ? Number(candidateSession.group.id) : null
  const permissions = groupId !== null && Number.isSafeInteger(groupId) && groupId > 0
    ? (await candidateGroupApi.listMyPermissions()).map((option) => option.code)
    : []
  assertCandidateCurrent(generation)
  assertAuthBffCoherence(profile, candidateSession)
  return {
    generation,
    api: candidateApi,
    session: candidateSession,
    profile,
    assistantJournalApi: candidateJournalApi,
    assistantRequestsApi: candidateRequestsApi,
    assistantHomeworkApi: candidateHomeworkApi,
    assistantPermissions: permissions,
  }
}

type HeadmanCandidate = {
  generation: number
  api: HeadmanScheduleApi
  journalApi: HeadmanJournalApi
  requestsApi: HeadmanRequestsApi
  groupApi: HeadmanGroupApi
  profile: ProfileSnapshot
  groupId: number
}

function authorizedHeadmanGroupId(profile: ProfileSnapshot): number | null {
  if (profile.activeRole !== 'HEADMAN') return null
  const grant = profile.roles.find((candidate) => candidate.role === 'HEADMAN' && candidate.status === 'ACTIVE' && candidate.groupId)
  const raw = grant?.groupId
  if (!raw || !/^\d+$/.test(raw)) return null
  const value = Number(raw)
  return Number.isSafeInteger(value) && value > 0 ? value : null
}

async function fetchHeadmanCandidate(
  generation: number,
  profile: ProfileSnapshot,
): Promise<HeadmanCandidate | null> {
  const groupId = authorizedHeadmanGroupId(profile)
  if (profile.activeRole !== 'HEADMAN' || groupId === null) {
    throw new ProfileRequestError('BOOTSTRAP_SCOPE_DENIED', 'Для роли старосты не определена учебная группа')
  }
  const candidateApi = auth.createHeadmanApi(currentFetcher())
  const candidateJournalApi = auth.createHeadmanJournalApi(currentFetcher())
  const candidateRequestsApi = auth.createHeadmanRequestsApi(currentFetcher())
  const candidateGroupApi = auth.createHeadmanGroupApi(currentFetcher())
  assertCandidateCurrent(generation)
  return {
    generation,
    api: candidateApi,
    journalApi: candidateJournalApi,
    requestsApi: candidateRequestsApi,
    groupApi: candidateGroupApi,
    profile,
    groupId,
  }
}

type TeacherCandidate = {
  generation: number
  api: TeacherApi
  semesterId: number
  profile: ProfileSnapshot
}

async function fetchTeacherCandidate(
  generation: number,
  profile: ProfileSnapshot,
): Promise<TeacherCandidate | null> {
  if (profile.activeRole !== 'TEACHER') return null
  const candidateApi = auth.createTeacherApi(currentFetcher())
  const semester = await candidateApi.semester()
  assertCandidateCurrent(generation)
  return { generation, api: candidateApi, semesterId: semester.id, profile }
}

type StudentCandidate = {
  generation: number
  api: StudentApi
  session: StudentSession
  profile: ProfileSnapshot
  assistantJournalApi: HeadmanJournalApi
  assistantRequestsApi: HeadmanRequestsApi
  assistantHomeworkApi: HeadmanHomeworkApi
  assistantPermissions: readonly HeadmanAssistantPermission[]
}

async function activateCandidate(candidate: StudentCandidate): Promise<void> {
  const nextScope = studentFeatureScope(candidate.session, candidate.generation)
  const sameOwner = scope.value !== null
    && studentFeatureScopeIdentity(scope.value) === studentFeatureScopeIdentity(nextScope)
  // A snapshot-only shell was mounted with the inert API. It needs one fresh
  // feature owner when authority returns; a live same-generation owner must
  // stay mounted so an ambiguous check-in keeps its command and idempotency
  // key across reconnect/auth refresh.
  const needsFreshOwner = !sameOwner || api.value === null
  let existing: SemesterSnapshot | null = null
  try {
    existing = await snapshotStore.read(candidate.session.user.id, studentOfflineScopeKey(nextScope))
  } catch {
    // Storage is an offline enhancement. An unavailable IDB/localStorage must
    // not prevent an otherwise authenticated online shell from mounting.
  }
  assertCandidateCurrent(candidate.generation)
  const committedSnapshot = isPersistedSnapshot(existing) ? existing : null
  if (!sameOwner) {
    snapshot.value = committedSnapshot
    semesterSchedule.value = committedSnapshot?.schedule ?? null
  } else if (committedSnapshot) {
    snapshot.value = committedSnapshot
    semesterSchedule.value = committedSnapshot.schedule
  }
  let nextSchedule = committedSnapshot?.schedule ?? null
  let nextEtag = committedSnapshot?.etag ?? null

  if (candidate.session.semester) {
    const response = await candidate.api.getSemesterSchedule(candidate.session.semester.id, committedSnapshot?.etag ?? undefined)
    assertCandidateCurrent(candidate.generation)
    if (response.data) {
      nextSchedule = response.data
      nextEtag = response.etag
    }
  }

  // The session and schedule have both passed the generation gate before the
  // feature owner is mounted. A 304 keeps the matching owner/scope snapshot.
  if (nextSchedule) {
    const nextSnapshot = makeSnapshot(nextScope, nextSchedule, committedSnapshot, undefined, nextEtag)
    onlineSemesterSchedule.value = nextSchedule
    if (committedSnapshot) {
      snapshot.value = committedSnapshot
      semesterSchedule.value = committedSnapshot.schedule
    }
    try {
      const switchResult = await snapshotStore.switchToDetailed(candidate.session.user.id)
      assertCandidateCurrent(candidate.generation)
      observeCleanupResult(switchResult)
      await snapshotStore.write(nextSnapshot)
      assertCandidateCurrent(candidate.generation)
      snapshot.value = nextSnapshot
      semesterSchedule.value = nextSchedule
    } catch {
      // Continue online when persistence is unavailable. The committed
      // snapshot remains authoritative for the next offline transition.
    }
  } else {
    snapshot.value = null
    semesterSchedule.value = null
    onlineSemesterSchedule.value = null
    try {
      const switchResult = await snapshotStore.switchToDetailed(candidate.session.user.id)
      assertCandidateCurrent(candidate.generation)
      observeCleanupResult(switchResult)
    } catch { /* online auth remains usable */ }
  }

  assertCandidateCurrent(candidate.generation)
  api.value = needsFreshOwner ? candidate.api : api.value
  assistantJournalApi.value = candidate.assistantJournalApi
  assistantRequestsApi.value = candidate.assistantRequestsApi
  assistantHomeworkApi.value = candidate.assistantHomeworkApi
  assistantPermissions.value = candidate.assistantPermissions
  session.value = candidate.session
  scope.value = nextScope
  if (!sameOwner || profilePort.value === null) {
    profilePort.value = auth.createProfilePort(candidate.generation, {
      onInvalidated: handleProfileInvalidated,
      onRefreshAlreadyRotated: () => handleProfileInvalidated('account-invalidated'),
    })
  }
  if (needsFreshOwner) ownerRevision.value += 1
  offline.value = false
  readOnly.value = candidate.session.readOnly
  bootstrapError.value = null
}

async function activateHeadmanCandidate(candidate: HeadmanCandidate): Promise<void> {
  const previous = invalidateOwnerSynchronously({ clearAuth: false })
  await clearOwnerSnapshot(previous)
  assertCandidateCurrent(candidate.generation)
  headmanApi.value = candidate.api
  headmanJournalApi.value = candidate.journalApi
  headmanRequestsApi.value = candidate.requestsApi
  headmanGroupApi.value = candidate.groupApi
  headmanGroupId.value = candidate.groupId
  authSnapshot.value = candidate.profile
  offline.value = false
  readOnly.value = candidate.profile.readOnly
  bootstrapError.value = null
  ownerRevision.value += 1
}

async function activateTeacherCandidate(candidate: TeacherCandidate): Promise<void> {
  invalidateOwnerSynchronously({ clearAuth: false })
  assertCandidateCurrent(candidate.generation)
  teacherApi.value = candidate.api
  teacherSemesterId.value = candidate.semesterId
  authSnapshot.value = candidate.profile
  offline.value = false
  readOnly.value = candidate.profile.readOnly
  bootstrapError.value = null
  ownerRevision.value += 1
}

async function activateMapRole(profile: ProfileSnapshot, generation: number): Promise<void> {
  assertCandidateCurrent(generation)
  const previous = invalidateOwnerSynchronously({ clearAuth: false })
  await clearOwnerSnapshot(previous)
  assertCandidateCurrent(generation)
  authSnapshot.value = profile
  adminSemesterApi.value = profile.activeRole === 'ADMIN'
    ? auth.createAdminSemesterApi(currentFetcher())
    : null
  adminUsersApi.value = profile.activeRole === 'ADMIN'
    ? auth.createAdminUsersApi(currentFetcher())
    : null
  adminGroupsApi.value = profile.activeRole === 'ADMIN'
    ? auth.createAdminGroupsApi(currentFetcher())
    : null
  offline.value = false
  readOnly.value = profile.readOnly
  bootstrapError.value = null
  ownerRevision.value += 1
  authView.value = profile.activeRole === 'ADMIN' ? 'admin-map' : 'map'
}

function navigateAdmin(route: 'map' | 'semesters' | 'users' | 'groups'): void {
  if (authSnapshot.value?.activeRole !== 'ADMIN'
    || !adminSemesterApi.value || !adminUsersApi.value || !adminGroupsApi.value) return
  authView.value = route === 'map' ? 'admin-map'
    : route === 'semesters' ? 'admin-semesters'
      : route === 'users' ? 'admin-users' : 'admin-groups'
}

function assertCandidateCurrent(generation: number): void {
  if (!auth.isCurrent(generation)) throw new StaleSessionGenerationError()
}

async function bootstrap(options: { refresh?: boolean } = {}): Promise<void> {
  if (bootstrapping.value) return
  bootstrapping.value = true
  try {
    const authCandidate = await fetchAuthForCurrentGeneration(options)
    authSnapshot.value = authCandidate.profile
    authError.value = null
    if (authCandidate.profile.activeRole === 'HEADMAN') {
      const candidate = await fetchHeadmanCandidate(authCandidate.generation, authCandidate.profile)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль старосты недоступна')
      await activateHeadmanCandidate(candidate)
      authView.value = 'headman'
      sessionReady.value = true
    } else if (authCandidate.profile.activeRole !== 'STUDENT') {
      if (authCandidate.profile.activeRole === 'TEACHER') {
        const candidate = await fetchTeacherCandidate(authCandidate.generation, authCandidate.profile)
        if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна')
        await activateTeacherCandidate(candidate)
        authView.value = 'teacher'
      } else if (authCandidate.profile.activeRole === 'ADMIN') {
        await activateMapRole(authCandidate.profile, authCandidate.generation)
      } else {
        const previous = invalidateOwnerSynchronously({ clearAuth: false })
        await clearOwnerSnapshot(previous)
        authView.value = 'role'
        offline.value = false
        readOnly.value = true
        bootstrapError.value = null
      }
    } else {
      const candidate = await fetchStudentCandidate(authCandidate.generation, authCandidate.profile)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль студента недоступна')
      await activateCandidate(candidate)
      authView.value = 'student'
    }
    sessionReady.value = true
  } catch (error) {
    if (error instanceof StaleSessionGenerationError) {
      return
    }
    if (isConfirmedOnlineAuthDenial(error)) {
      await handleOnlineAuthDenial(error)
    } else if (error instanceof AuthRequestError && authSnapshot.value && authView.value === 'role') {
      authError.value = error
      bootstrapError.value = error.message
    } else if (error instanceof ProfileRequestError && error.code === 'SESSION_STATE_STALE') {
      const previous = invalidateOwnerSynchronously()
      await clearOwnerSnapshot(previous)
      authSnapshot.value = null
      authError.value = error
      authView.value = 'login'
      offline.value = false
      readOnly.value = true
      bootstrapError.value = error.message
    } else if (!featureVisible.value && authView.value !== 'headman' && isOfflineBootstrapRecoveryError(error)) {
      const recovered = await loadOfflineSnapshot()
      if (!recovered) {
        authView.value = 'login'
        bootstrapError.value = error instanceof Error ? error.message : 'Не удалось открыть Today'
        readOnly.value = true
      }
    } else if (error instanceof ProfileRequestError && !featureVisible.value) {
      authError.value = error
      bootstrapError.value = error.message
    } else if (api.value !== null || snapshot.value !== null) {
      // A transient reconnect failure cannot establish remote logout. Keep the
      // currently committed owner mounted so an ambiguous action retains its
      // command and idempotency key for the next successful recheck.
      offline.value = true
      readOnly.value = true
      bootstrapError.value = error instanceof Error ? error.message : 'Не удалось проверить подключение'
    } else {
      authView.value = 'login'
      bootstrapError.value = error instanceof Error ? error.message : 'Не удалось получить данные'
      readOnly.value = true
    }
    sessionReady.value = true
  } finally {
    bootstrapping.value = false
    if (queuedBootstrapAfterInvalidation) {
      queuedBootstrapAfterInvalidation = false
      void bootstrap({ refresh: false })
    }
  }
}

function retryBootstrap(): void {
  void bootstrap()
}

function asProfileError(error: unknown): ProfileRequestError {
  if (error instanceof ProfileRequestError) return error
  if (error instanceof StudentApiError) {
    return new ProfileRequestError('UNKNOWN', error.problem?.detail ?? error.message, error.response.status, error.problem)
  }
  return new ProfileRequestError('UNKNOWN', error instanceof Error ? error.message : 'Не удалось выполнить запрос')
}

async function submitLogin(input: { login: string; password: string }): Promise<void> {
  if (authLoading.value) return
  authLoading.value = true
  authError.value = null
  bootstrapError.value = null
  try {
    const result = await auth.login(input)
    const profile = await auth.getSessionFor(result.generation)
    authSnapshot.value = profile
    if (profile.activeRole !== 'STUDENT' && profile.activeRole !== 'HEADMAN'
      && profile.activeRole !== 'TEACHER' && profile.activeRole !== 'ADMIN') {
      authView.value = 'role'
      readOnly.value = true
      sessionReady.value = true
      return
    }
    if (profile.activeRole === 'HEADMAN') {
      const candidate = await fetchHeadmanCandidate(result.generation, profile)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль старосты недоступна')
      await activateHeadmanCandidate(candidate)
      authView.value = 'headman'
    } else if (profile.activeRole === 'TEACHER') {
      const candidate = await fetchTeacherCandidate(result.generation, profile)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна')
      await activateTeacherCandidate(candidate)
      authView.value = 'teacher'
    } else if (profile.activeRole === 'ADMIN') {
      await activateMapRole(profile, result.generation)
    } else {
      const candidate = await fetchStudentCandidate(result.generation, profile)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль студента недоступна')
      await activateCandidate(candidate)
      authView.value = 'student'
    }
    sessionReady.value = true
  } catch (error) {
    if (error instanceof StaleSessionGenerationError) return
    authError.value = asProfileError(error)
    bootstrapError.value = authError.value.message
    authView.value = 'login'
  } finally {
    authLoading.value = false
  }
}

async function selectRole(role: ProfileRole, expectedSessionVersion: string): Promise<void> {
  const request = createPwaRoleSelection(role, expectedSessionVersion)
  if (!request || authLoading.value || !authSnapshot.value) return
  authLoading.value = true
  pendingRole.value = role
  authError.value = null
  bootstrapError.value = null
  // Keep the old token available for the role PUT, while hiding the old
  // feature owner until the response has passed the generation boundary.
  authView.value = 'role'
  const generation = auth.currentGeneration()
  try {
    const selection = await auth.selectRoleFor(generation, request)
    authSnapshot.value = selection.session
    if (selection.session.activeRole === 'HEADMAN') {
      const candidate = await fetchHeadmanCandidate(selection.generation, selection.session)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль старосты недоступна')
      await activateHeadmanCandidate(candidate)
      authView.value = 'headman'
    } else if (selection.session.activeRole === 'STUDENT') {
      const candidate = await fetchStudentCandidate(selection.generation, selection.session)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль студента недоступна')
      await activateCandidate(candidate)
      authView.value = 'student'
    } else if (selection.session.activeRole === 'TEACHER') {
      const candidate = await fetchTeacherCandidate(selection.generation, selection.session)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна')
      await activateTeacherCandidate(candidate)
      authView.value = 'teacher'
    } else if (selection.session.activeRole === 'ADMIN') {
      await activateMapRole(selection.session, selection.generation)
    } else {
      throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Выбранная роль недоступна в этом приложении')
    }
    sessionReady.value = true
  } catch (error) {
    if (error instanceof StaleSessionGenerationError) return
    authError.value = asProfileError(error)
    bootstrapError.value = authError.value.message
    // A version conflict belongs to the old generation. Read the new
    // authoritative snapshot before rendering another selection attempt.
    if (authError.value.code === 'SESSION_VERSION_CONFLICT' || authError.value.code === 'SESSION_STATE_STALE') {
      try {
        const current = await auth.getSessionFor(generation)
        authSnapshot.value = current
      } catch (refreshError) {
        if (isConfirmedOnlineAuthDenial(refreshError)) await handleOnlineAuthDenial(refreshError)
      }
    }
    authView.value = 'role'
  } finally {
    pendingRole.value = null
    authLoading.value = false
  }
}

async function logout(): Promise<void> {
  const previous = invalidateOwnerSynchronously({ clearAuth: false })
  await auth.logout(async () => { await clearOwnerSnapshot(previous) })
  authSnapshot.value = null
  authError.value = null
  bootstrapError.value = null
  authView.value = 'login'
  sessionReady.value = true
  offline.value = false
  readOnly.value = true
}

function handleExternalInvalidation(reason: PwaAuthInvalidationReason = 'external'): void {
  if (reason === 'authority-changed') {
    // The auth layer has already advanced the generation and installed the
    // validated candidate token. Detach the old owner synchronously, then
    // reconcile that token/session without asking refresh to rotate again.
    const wasHeadman = authView.value === 'headman'
      || headmanApi.value !== null
      || authSnapshot.value?.activeRole === 'HEADMAN'
    const wasTeacher = authView.value === 'teacher'
      || teacherApi.value !== null
      || authSnapshot.value?.activeRole === 'TEACHER'
    invalidateOwnerSynchronously({ clearAuth: false })
    authSnapshot.value = null
    authError.value = null
    bootstrapError.value = null
    authView.value = wasHeadman ? 'headman' : wasTeacher ? 'teacher' : 'student'
    sessionReady.value = false
    offline.value = true
    readOnly.value = true
    if (bootstrapping.value) {
      queuedBootstrapAfterInvalidation = true
      return
    }
    void bootstrap({ refresh: false })
    return
  }
  const previous = invalidateOwnerSynchronously({ clearAuth: false })
  authSnapshot.value = null
  authError.value = null
  bootstrapError.value = 'Сессия завершена в другой вкладке'
  authView.value = 'login'
  sessionReady.value = true
  offline.value = false
  readOnly.value = true
  void clearOwnerSnapshot(previous)
}

async function persistHomework(homework: StudentHomework): Promise<void> {
  const currentScope = scope.value
  const currentSession = session.value
  const currentSchedule = semesterSchedule.value
  const currentSnapshot = snapshot.value
  if (!currentScope || !currentSession || !currentSchedule || !currentSnapshot || offline.value || readOnly.value) return
  if (auth.currentGeneration() !== currentScope.resetGeneration) return
  try {
    const nextSnapshot = makeSnapshot(currentScope, currentSchedule, currentSnapshot, homework, currentSnapshot.etag)
    await snapshotStore.write(nextSnapshot)
    assertCandidateCurrent(currentScope.resetGeneration)
    if (offline.value || readOnly.value || scope.value !== currentScope || session.value !== currentSession || semesterSchedule.value !== currentSchedule || snapshot.value !== currentSnapshot) return
    snapshot.value = nextSnapshot
  } catch {
    // The online feature remains authoritative in memory; keep the previous
    // committed snapshot when storage rejects this candidate.
  }
}

async function readPosition(): Promise<GeolocationPosition | GeolocationPositionError | null> {
  if (!navigator.geolocation) return null
  return new Promise((resolve) => navigator.geolocation.getCurrentPosition(resolve, resolve, {
    enableHighAccuracy: true,
    timeout: 10_000,
    maximumAge: 0,
  }))
}

function acquireCheckinCommand(): Promise<StudentCheckinCommand> {
  if (fixtureMode) return Promise.resolve(fixtureCheckinCommand())
  return readPosition().then((result) => result && 'coords' in result
    ? commandFromCoordinates(result)
    : unavailableCommand(unavailableReason(result)))
}

/** Fixture-only browser control; production always uses the real GPS API. */
function fixtureCheckinCommand(): StudentCheckinCommand {
  const scenario = new URLSearchParams(window.location.search).get('fixtureGeo')
  return scenario === 'coordinates'
    ? { geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 } }
    : unavailableCommand('POSITION_UNAVAILABLE')
}

function openMaterial(url: string): void {
  // HomeworkScreen already validates this boundary. Revalidate at the shell
  // edge so an adapter callback can never open a relative or non-HTTP target.
  try {
    const parsed = new URL(url)
    if ((parsed.protocol !== 'http:' && parsed.protocol !== 'https:') || !parsed.hostname) return
    window.open(url, '_blank', 'noopener,noreferrer')
  } catch {
    // Unsupported server data remains inert.
  }
}

function onOwnerError(error: unknown): void {
  if (isConfirmedOnlineAuthDenial(error)) {
    void handleOnlineAuthDenial(error)
    return
  }
  if (error instanceof StudentApiError) bootstrapError.value = error.problem?.detail ?? error.message
  else if (error instanceof Error) bootstrapError.value = error.message
}

function goOffline(): void {
  offline.value = true
  readOnly.value = true
}

function goOnline(): void {
  // Reconnect must verify the current session before restoring mutation
  // authority. Bootstrap mounts a fresh bound owner only after getSession and
  // schedule have passed the generation gate.
  offline.value = true
  readOnly.value = true
  void bootstrap()
}

function openRoleSwitch(): void {
  authError.value = null
  bootstrapError.value = null
  authView.value = 'role'
}

const onServiceWorkerControllerChange = (): void => { void refreshFixtureDiagnostics() }

async function refreshFixtureDiagnostics(): Promise<void> {
  if (!fixtureDiagnosticsMode) return
  const lines = [
    `SW build flag: ${fixtureServiceWorkerBuildEnabled}`,
    `Origin: ${window.location.origin}`,
  ]
  if (!('serviceWorker' in navigator)) {
    fixtureDiagnosticLines.value = [...lines, 'Service worker API: unavailable']
    return
  }
  try {
    const registration = await navigator.serviceWorker.getRegistration()
    if (!registration) {
      fixtureDiagnosticLines.value = [...lines, 'Registration: none', `Controller: ${navigator.serviceWorker.controller ? 'present' : 'none'}`]
      return
    }
    const worker = registration.installing ?? registration.waiting ?? registration.active
    lines.push(`Registration: ${worker?.state ?? 'none'} (${registration.scope})`)
    lines.push(`Controller: ${navigator.serviceWorker.controller?.state ?? 'none'}`)
    const response = await fetch(`${import.meta.env.BASE_URL}sw-assets.js`, { cache: 'no-store' })
    const source = await response.text()
    const match = source.match(/=\s*(\[[\s\S]*\]);?\s*$/)
    const serializedAssets = match?.[1]
    if (!response.ok || !serializedAssets) throw new Error('Generated precache list unavailable')
    const assets = JSON.parse(serializedAssets) as unknown
    if (!Array.isArray(assets) || assets.some((asset) => typeof asset !== 'string')) throw new Error('Generated precache list is invalid')
    const cacheNames = await caches.keys()
    const cacheName = cacheNames.find((name) => name === 'rct-student-pwa-v3')
    if (!cacheName) {
      fixtureDiagnosticLines.value = [...lines, 'Precache: rct-student-pwa-v3 missing']
      return
    }
    const cache = await caches.open(cacheName)
    const matches = await Promise.all(assets.map((asset) => cache.match(asset)))
    const missing = assets.filter((asset, index) => !matches[index])
    lines.push(`Precache assets: ${assets.length - missing.length}/${assets.length}`)
    if (missing.length > 0) lines.push(`Missing: ${missing.join(', ')}`)
    fixtureDiagnosticLines.value = lines
  } catch (error) {
    fixtureDiagnosticLines.value = [...lines, `Diagnostics error: ${error instanceof Error ? error.message : 'unknown'}`]
  }
}

onMounted(() => {
  stopAuthInvalidation = auth.subscribeInvalidation(handleExternalInvalidation)
  window.addEventListener('offline', goOffline)
  window.addEventListener('online', goOnline)
  if (fixtureDiagnosticsMode && 'serviceWorker' in navigator) navigator.serviceWorker.addEventListener('controllerchange', onServiceWorkerControllerChange)
  void bootstrap()
  void refreshFixtureDiagnostics()
})

onBeforeUnmount(() => {
  stopAuthInvalidation()
  auth.dispose()
  invalidateOwnerSynchronously()
  window.removeEventListener('offline', goOffline)
  window.removeEventListener('online', goOnline)
  if (fixtureDiagnosticsMode && 'serviceWorker' in navigator) navigator.serviceWorker.removeEventListener('controllerchange', onServiceWorkerControllerChange)
  theme?.dispose()
})
</script>

<template>
  <PwaUpdateGate :enabled="!fixtureMode" />
  <section
    v-if="!featureVisible && !sessionReady && (authView === 'student' || authView === 'teacher')"
    class="today-state"
    aria-live="polite"
  >
    <span
      class="today-state__spinner"
      aria-hidden="true"
    />
    Подключаемся к сессии…
  </section>
  <LoginScreen
    v-if="authView === 'login'"
    :loading="authLoading || bootstrapping"
    :error="authError"
    @submit="submitLogin"
  />
  <RoleSwitchScreen
    v-else-if="authView === 'role'"
    :snapshot="authSnapshot"
    :pending-role="pendingRole"
    :error="authError"
    :loading="authLoading || bootstrapping"
    :on-select-role="selectRole"
  />
  <HeadmanScheduleScreen
    v-else-if="headmanViewVisible"
    :key="`headman-${ownerRevision}`"
    :api="headmanApi"
    :journal-api="headmanJournalApi"
    :requests-api="headmanRequestsApi"
    :group-api="headmanGroupApi"
    :profile="authSnapshot"
    :group-id="headmanGroupId"
    :offline="offline"
    :read-only="readOnly"
    :host="host"
    :map-client="mapClient"
    :on-role-switch="openRoleSwitch"
    @error="onOwnerError"
  />
  <TeacherFeatureOwner
    v-else-if="teacherViewVisible"
    :key="`teacher-${ownerRevision}`"
    :api="teacherApi"
    :semester-id="teacherSemesterId"
    @owner-error="onOwnerError"
  />
  <MapScreen
    v-else-if="mapViewVisible"
    :client="mapClient"
    theme="dark"
  />
  <template v-else-if="adminMapViewVisible">
    <AdminRoleNavigation
      active="map"
      @navigate="navigateAdmin"
    />
    <AdminMapScreen
      :client="adminMapClient"
      theme="dark"
    />
  </template>
  <template v-else-if="adminSemesterViewVisible && adminSemesterApi">
    <AdminRoleNavigation
      active="semesters"
      @navigate="navigateAdmin"
    />
    <AdminSemesterScreen
      :client="adminSemesterApi"
      theme="dark"
      @owner-error="onOwnerError"
    />
  </template>
  <template v-else-if="adminUsersViewVisible && adminUsersApi">
    <AdminRoleNavigation
      active="users"
      @navigate="navigateAdmin"
    />
    <AdminUsersScreen
      :client="adminUsersApi"
      theme="dark"
      @owner-error="onOwnerError"
    />
  </template>
  <template v-else-if="adminGroupsViewVisible && adminGroupsApi">
    <AdminRoleNavigation
      active="groups"
      @navigate="navigateAdmin"
    />
    <AdminGroupsScreen
      :client="adminGroupsApi"
      theme="dark"
      @owner-error="onOwnerError"
    />
  </template>
  <StudentFeatureOwner
    v-else-if="studentViewVisible"
    :key="ownerKey"
    :api="api"
    :scope="scope"
    :offline="offline"
    :read-only="readOnly"
    :today-fallback="cachedToday"
    :homework-fallback="cachedHomework"
    :semester-schedule="displayedSemesterSchedule"
    :updated-at="snapshot?.cachedAt ?? null"
    :host="host"
    :profile-port="profilePort"
    :profile-role-select="selectRole"
    :assistant-permissions="assistantPermissions"
    :assistant-journal-api="assistantJournalApi"
    :assistant-requests-api="assistantRequestsApi"
    :assistant-homework-api="assistantHomeworkApi"
    :map-client="mapClient"
    :theme-controller="theme"
    :acquire-checkin-command="acquireCheckinCommand"
    :open-material="openMaterial"
    @homework-loaded="persistHomework"
    @owner-error="onOwnerError"
  />
  <button
    v-if="(authView === 'student' && session) || authView === 'teacher' || authView === 'headman' || mapViewVisible || adminMapViewVisible || adminSemesterViewVisible || adminUsersViewVisible"
    class="pwa-logout"
    type="button"
    :disabled="authLoading"
    @click="logout"
  >
    Выйти
  </button>
  <section
    v-if="cleanupResult?.retryRequired"
    class="today-state today-state--error"
    role="alert"
    aria-live="polite"
    data-cleanup-state="pending"
  >
    <p>Локальная очистка ещё не завершена. Офлайн-доступ временно заблокирован.</p>
    <button
      type="button"
      :disabled="cleanupRetrying"
      :aria-busy="cleanupRetrying"
      @click="retryPendingCleanup"
    >
      {{ cleanupRetrying ? 'Повторяем очистку…' : 'Повторить очистку' }}
    </button>
  </section>
  <section
    v-if="(studentViewVisible || teacherViewVisible || headmanViewVisible) && bootstrapError"
    class="today-state today-state--error"
    role="alert"
    aria-live="polite"
  >
    <h2>Не удалось выполнить действие</h2>
    <p>{{ bootstrapError }}</p>
  </section>
  <section
    v-if="authView === 'login' && sessionReady && bootstrapError && !authError"
    class="today-state today-state--error"
    role="alert"
  >
    <h2>Не удалось получить данные</h2>
    <p>{{ bootstrapError }}</p>
    <button
      type="button"
      @click="retryBootstrap"
    >
      Повторить
    </button>
  </section>
  <section
    v-if="fixtureDiagnosticsMode"
    aria-label="Fixture service-worker diagnostics"
  >
    <h2>Fixture service-worker diagnostics</h2>
    <button
      type="button"
      @click="refreshFixtureDiagnostics"
    >
      Refresh diagnostics
    </button>
    <ul>
      <li
        v-for="line in fixtureDiagnosticLines"
        :key="line"
      >
        {{ line }}
      </li>
    </ul>
  </section>
</template>

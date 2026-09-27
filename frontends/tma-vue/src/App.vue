<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import {
  AdminMapClient,
  AdminMapScreen,
  AdminDashboardScreen,
  AdminSemesterApiError,
  AdminSemesterScreen,
  AdminGroupsApiError,
  AdminGroupsScreen,
  AdminUsersApiError,
  AdminUsersScreen,
  AdminRoleNavigation,
  CampusMapClient,
  HeadmanScheduleApiError,
  HeadmanJournalApiError,
  HeadmanRequestsApiError,
  HeadmanGroupApiError,
  HeadmanSubjectsApiError,
  HeadmanScheduleScreen,
  MapScreen,
  NotificationsApiError,
  NotificationsEntryButton,
  NotificationsScreen,
  ProfileRequestError,
  RoleSwitchScreen,
  StaleSessionGenerationError,
  StudentApi,
  StudentApiError,
  StudentFeatureOwner,
  TeacherApi,
  TeacherApiError,
  TeacherFeatureOwner,
  createFixtureTransport,
  createGenerationBoundNotificationsApi,
  createMobileTheme,
  studentFeatureScope,
  studentFeatureScopeIdentity,
  type StudentCheckinCommand,
  type StudentFeatureScope,
  type StudentSession,
  type HeadmanScheduleApi,
  type HeadmanJournalApi,
  type HeadmanRequestsApi,
  type HeadmanGroupApi,
  type HeadmanHomeworkApi,
  type HeadmanSubjectsApi,
  type HeadmanStatsApi,
  type HeadmanAssistantPermission,
  type ProfilePort,
  type ProfileRole,
  type ProfileSnapshot,
  type AdminDashboardClient,
  type AdminSemesterClient,
  type AdminGroupsClient,
  type AdminUsersClient,
  type NotificationsApi,
  type NotificationTarget,
  type NotificationTargetIntent,
} from '@rct/mobile-core'
import { installFixtureTelegramHost, TelegramHost } from './telegram'
import { useTmaSession } from './tma-session'
import { createTelegramReportDownloadPort } from './report-download-adapter'
import { TmaAuthError } from './tma-auth'

const fixtureMode = import.meta.env.VITE_MOBILE_FIXTURE_MODE === 'true'
if (fixtureMode) installFixtureTelegramHost()
const fixtureTransport = fixtureMode ? createFixtureTransport() : undefined
const nativeFetcher: typeof fetch = (input, init) => globalThis.fetch(input, init)
const host = new TelegramHost()
const sessionOwner = useTmaSession({
  fetcher: fixtureTransport ?? nativeFetcher,
  getInitData: () => host.start(),
})
const reportDownload = computed(() => createTelegramReportDownloadPort(
  sessionOwner.createReportDownloadClient(currentFetcher()),
  host,
))
const theme = typeof document === 'undefined' ? null : createMobileTheme()
const mapClient = new CampusMapClient({
  accessToken: () => sessionOwner.accessToken.value,
  currentGeneration: () => sessionOwner.currentGeneration(),
  onUnauthorized: () => sessionOwner.authenticateFor(sessionOwner.currentGeneration()),
  fetcher: fixtureTransport ?? nativeFetcher,
})
const adminMapClient = new AdminMapClient({
  accessToken: () => sessionOwner.accessToken.value,
  onUnauthorized: () => sessionOwner.authenticateFor(sessionOwner.currentGeneration()),
  fetcher: fixtureTransport ?? nativeFetcher,
})

const api = shallowRef<StudentApi | null>(null)
const teacherApi = shallowRef<TeacherApi | null>(null)
const teacherSemesterId = ref<number | null>(null)
const headmanApi = shallowRef<HeadmanScheduleApi | null>(null)
const headmanJournalApi = shallowRef<HeadmanJournalApi | null>(null)
const headmanRequestsApi = shallowRef<HeadmanRequestsApi | null>(null)
const headmanGroupApi = shallowRef<HeadmanGroupApi | null>(null)
const headmanSubjectsApi = shallowRef<HeadmanSubjectsApi | null>(null)
const headmanStatsApi = shallowRef<HeadmanStatsApi | null>(null)
const headmanHomeworkApi = shallowRef<HeadmanHomeworkApi | null>(null)
const headmanHomeworkActorUserId = ref<number | null>(null)
const assistantJournalApi = shallowRef<HeadmanJournalApi | null>(null)
const assistantStatsApi = shallowRef<HeadmanStatsApi | null>(null)
const assistantRequestsApi = shallowRef<HeadmanRequestsApi | null>(null)
const assistantHomeworkApi = shallowRef<HeadmanHomeworkApi | null>(null)
const assistantPermissions = shallowRef<readonly HeadmanAssistantPermission[]>([])
const adminSemesterApi = shallowRef<AdminSemesterClient | null>(null)
const adminDashboardApi = shallowRef<AdminDashboardClient | null>(null)
const adminUsersApi = shallowRef<AdminUsersClient | null>(null)
const adminGroupsApi = shallowRef<AdminGroupsClient | null>(null)
const notificationsApi = shallowRef<NotificationsApi | null>(null)
const notificationsOpen = ref(false)
const notificationsGeneration = ref<number | null>(null)
const notificationTargetIntent = shallowRef<NotificationTargetIntent | null>(null)
let notificationTargetIntentId = 0
const session = shallowRef<StudentSession | null>(null)
const scope = shallowRef<StudentFeatureScope | null>(null)
const profile = shallowRef<ProfileSnapshot | null>(null)
const profilePort = shallowRef<ProfilePort | null>(null)
const headmanGroupId = ref<number | null>(null)
const ready = ref(false)
const offline = ref(typeof navigator !== 'undefined' ? !navigator.onLine : false)
const error = ref<string | null>(null)
const bootstrapping = ref(false)
const ownerRevision = ref(0)
const authView = ref<'role' | 'student' | 'teacher' | 'headman' | 'map' | 'admin-home' | 'admin-map' | 'admin-semesters' | 'admin-users' | 'admin-groups'>('role')
const pendingRole = ref<ProfileRole | null>(null)
const roleError = shallowRef<ProfileRequestError | null>(null)
const roleLoading = ref(false)

const mapViewVisible = computed(() => authView.value === 'map')
const adminHomeViewVisible = computed(() => authView.value === 'admin-home')
const adminMapViewVisible = computed(() => authView.value === 'admin-map')
const adminSemesterViewVisible = computed(() => authView.value === 'admin-semesters')
const adminUsersViewVisible = computed(() => authView.value === 'admin-users')
const adminGroupsViewVisible = computed(() => authView.value === 'admin-groups')
const featureVisible = computed(() => api.value !== null || headmanApi.value !== null
  || teacherApi.value !== null || mapViewVisible.value || adminHomeViewVisible.value || adminMapViewVisible.value || adminSemesterViewVisible.value || adminUsersViewVisible.value || adminGroupsViewVisible.value)
const notificationsEntryVisible = computed(() => featureVisible.value
  && profile.value !== null
  && profile.value.activeRole !== null
  && authView.value !== 'role')
const studentViewVisible = computed(() => authView.value === 'student' && api.value !== null)
const canOpenNotificationTarget = computed(() => studentViewVisible.value
  && profile.value?.activeRole === 'STUDENT'
  && scope.value?.activeRole === 'STUDENT'
  && scope.value.resetGeneration === sessionOwner.currentGeneration()
  && !offline.value)
const teacherViewVisible = computed(() => authView.value === 'teacher' && teacherApi.value !== null)
const headmanViewVisible = computed(() => authView.value === 'headman' && headmanApi.value !== null)
const ownerKey = computed(() => `${scope.value ? studentFeatureScopeIdentity(scope.value) : 'tma-unavailable'}|${ownerRevision.value}`)

function sessionIdentity(value: StudentSession): string {
  return JSON.stringify([
    value.user.id,
    value.activeRole,
    value.group?.id ?? null,
    value.semester?.id ?? null,
  ])
}

function currentFetcher(): typeof fetch | undefined {
  return fixtureTransport
}

function openNotifications(): void {
  if (!notificationsEntryVisible.value) return
  notificationTargetIntent.value = null
  const generation = sessionOwner.currentGeneration()
  notificationsGeneration.value = generation
  notificationsApi.value = createGenerationBoundNotificationsApi({
    currentGeneration: () => sessionOwner.currentGeneration(),
    accessTokenFor: (capturedGeneration) => {
      if (!sessionOwner.isCurrent(capturedGeneration)) throw new StaleSessionGenerationError()
      return sessionOwner.accessToken.value
    },
    refreshFor: (capturedGeneration) => sessionOwner.authenticateFor(capturedGeneration),
  }, currentFetcher())
  notificationsOpen.value = true
}

function closeNotifications(): void {
  notificationsOpen.value = false
  notificationsApi.value = null
  notificationsGeneration.value = null
}

function openNotificationTarget(target: NotificationTarget): void {
  const generation = notificationsGeneration.value
  const currentScope = scope.value
  if (!notificationsOpen.value || !notificationsApi.value || generation === null
    || generation !== sessionOwner.currentGeneration()
    || profile.value?.activeRole !== 'STUDENT'
    || !studentViewVisible.value || !currentScope || currentScope.activeRole !== 'STUDENT'
    || currentScope.resetGeneration !== generation || offline.value) return
  notificationTargetIntent.value = {
    requestId: ++notificationTargetIntentId,
    generation,
    ownerKey: ownerKey.value,
    target,
  }
  closeNotifications()
}

function clearNotificationTargetIntent(requestId: number): void {
  if (notificationTargetIntent.value?.requestId === requestId) notificationTargetIntent.value = null
}

watch(
  () => [sessionOwner.resetGeneration.value, profile.value?.userId, profile.value?.activeRole, ownerKey.value] as const,
  (current, previous) => {
    if (!previous || (current[0] === previous[0] && current[1] === previous[1]
      && current[2] === previous[2] && current[3] === previous[3])) return
    notificationTargetIntent.value = null
    if (notificationsOpen.value) closeNotifications()
  },
)

watch(offline, (isOffline) => {
  if (isOffline) notificationTargetIntent.value = null
})

function authDenialStatus(cause: unknown): number | null {
  if (cause instanceof TmaAuthError) return cause.status
  if (cause instanceof NotificationsApiError) return cause.status
  if (cause instanceof StudentApiError) return cause.response.status
  if (cause instanceof TeacherApiError) return cause.response.status
  if (cause instanceof HeadmanSubjectsApiError) return cause.response.status
  if (cause instanceof HeadmanScheduleApiError) return cause.response.status
  if (cause instanceof HeadmanJournalApiError) return cause.response.status
  if (cause instanceof HeadmanRequestsApiError) return cause.response.status
  if (cause instanceof HeadmanGroupApiError) return cause.response.status
  if (cause instanceof AdminSemesterApiError) return cause.response.status
  if (cause instanceof AdminGroupsApiError) return cause.response.status
  if (cause instanceof AdminUsersApiError) return cause.response.status
  return null
}

function isAuthDenied(cause: unknown): boolean {
  if (cause instanceof NotificationsApiError && cause.status === 403) return false
  if ((cause instanceof HeadmanScheduleApiError || cause instanceof HeadmanJournalApiError)
    && cause.response.status === 403) return false
  const status = authDenialStatus(cause)
  return status === 401 || status === 403
}

function browserIsOffline(): boolean {
  return typeof navigator !== 'undefined' && !navigator.onLine
}

async function fetchSessionForCurrentGeneration(): Promise<{
  generation: number
  api: StudentApi
  session: StudentSession
}> {
  const generation = sessionOwner.currentGeneration()
  await sessionOwner.authenticateFor(generation)
  const candidateApi = sessionOwner.createApi(currentFetcher())
  const candidateSession = await candidateApi.getSession()
  if (!sessionOwner.isCurrent(generation)) throw new Error('Сессия сменилась во время входа')
  return { generation, api: candidateApi, session: candidateSession }
}

async function fetchAssistantCapabilities(
  generation: number,
  studentSession: StudentSession,
): Promise<{
  journalApi: HeadmanJournalApi
  statsApi: HeadmanStatsApi
  requestsApi: HeadmanRequestsApi
  homeworkApi: HeadmanHomeworkApi
  permissions: readonly HeadmanAssistantPermission[]
}> {
  const groupApi = sessionOwner.createHeadmanGroupApi(currentFetcher())
  const journalApi = sessionOwner.createHeadmanJournalApi(currentFetcher())
  const statsApi = sessionOwner.createHeadmanStatsApi(currentFetcher())
  const requestsApi = sessionOwner.createHeadmanRequestsApi(currentFetcher())
  const homeworkApi = sessionOwner.createHeadmanHomeworkApi(currentFetcher())
  const groupId = studentSession.group?.id ? Number(studentSession.group.id) : null
  const permissions = groupId !== null && Number.isSafeInteger(groupId) && groupId > 0
    ? (await groupApi.listMyPermissions()).map((option) => option.code)
    : []
  if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError()
  return { journalApi, statsApi, requestsApi, homeworkApi, permissions }
}

async function fetchProfileForCurrentGeneration(): Promise<{ generation: number; profile: ProfileSnapshot }> {
  const generation = sessionOwner.currentGeneration()
  await sessionOwner.authenticateFor(generation)
  const candidateProfile = await sessionOwner.getProfileFor(generation)
  if (!sessionOwner.isCurrent(generation)) throw new Error('Сессия сменилась во время входа')
  return { generation, profile: candidateProfile }
}

function authorizedHeadmanGroupId(value: ProfileSnapshot): number | null {
  if (value.activeRole !== 'HEADMAN') return null
  const grant = value.roles.find((candidate) => candidate.role === 'HEADMAN' && candidate.status === 'ACTIVE' && candidate.groupId)
  const raw = grant?.groupId
  if (!raw || !/^\d+$/.test(raw)) return null
  const groupId = Number(raw)
  return Number.isSafeInteger(groupId) && groupId > 0 ? groupId : null
}

function positiveSafeHomeworkActorUserId(value: ProfileSnapshot): number | null {
  if (!/^[1-9]\d*$/.test(value.userId)) return null
  const userId = Number(value.userId)
  return Number.isSafeInteger(userId) && userId > 0 ? userId : null
}

type TeacherCandidate = {
  generation: number
  api: TeacherApi
  semesterId: number
  profile: ProfileSnapshot
}

async function fetchTeacherCandidate(
  generation: number,
  value: ProfileSnapshot,
): Promise<TeacherCandidate | null> {
  if (value.activeRole !== 'TEACHER') return null
  const candidateApi = sessionOwner.createTeacherApi(currentFetcher())
  const semester = await candidateApi.semester()
  if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError()
  return { generation, api: candidateApi, semesterId: semester.id, profile: value }
}

function asProfileError(cause: unknown): ProfileRequestError {
  if (cause instanceof ProfileRequestError) return cause
  return new ProfileRequestError('UNKNOWN', cause instanceof Error ? cause.message : 'Не удалось сменить роль')
}

function invalidateOwnerSynchronously(options: { clearAuth?: boolean } = {}): void {
  if (options.clearAuth !== false) sessionOwner.clear()
  teacherApi.value = null
  teacherSemesterId.value = null
  headmanApi.value = null
  headmanJournalApi.value = null
  headmanRequestsApi.value = null
  headmanGroupApi.value = null
  headmanSubjectsApi.value = null
  headmanStatsApi.value = null
  headmanHomeworkApi.value = null
  headmanHomeworkActorUserId.value = null
  assistantJournalApi.value = null
  assistantStatsApi.value = null
  assistantRequestsApi.value = null
  assistantHomeworkApi.value = null
  assistantPermissions.value = []
  adminSemesterApi.value = null
  adminDashboardApi.value = null
  adminUsersApi.value = null
  adminGroupsApi.value = null
  headmanGroupId.value = null
  scope.value = null
  api.value = null
  session.value = null
  profile.value = null
  profilePort.value = null
  ownerRevision.value += 1
}

function activateMapRole(value: ProfileSnapshot): void {
  invalidateOwnerSynchronously({ clearAuth: false })
  profile.value = value
  adminSemesterApi.value = value.activeRole === 'ADMIN'
    ? sessionOwner.createAdminSemesterApi(currentFetcher())
    : null
  adminDashboardApi.value = value.activeRole === 'ADMIN'
    ? sessionOwner.createAdminDashboardApi(currentFetcher())
    : null
  adminUsersApi.value = value.activeRole === 'ADMIN'
    ? sessionOwner.createAdminUsersApi(currentFetcher())
    : null
  adminGroupsApi.value = value.activeRole === 'ADMIN'
    ? sessionOwner.createAdminGroupsApi(currentFetcher())
    : null
  offline.value = false
  error.value = null
  ownerRevision.value += 1
  authView.value = value.activeRole === 'ADMIN' ? 'admin-home' : 'map'
}

function navigateAdmin(route: 'home' | 'map' | 'semesters' | 'users' | 'groups'): void {
  if (profile.value?.activeRole !== 'ADMIN'
    || !adminDashboardApi.value || !adminSemesterApi.value || !adminUsersApi.value || !adminGroupsApi.value) return
  authView.value = route === 'home' ? 'admin-home'
    : route === 'map' ? 'admin-map'
      : route === 'semesters' ? 'admin-semesters'
        : route === 'users' ? 'admin-users' : 'admin-groups'
}

async function activateTeacherCandidate(candidate: TeacherCandidate): Promise<void> {
  invalidateOwnerSynchronously({ clearAuth: false })
  if (!sessionOwner.isCurrent(candidate.generation)) throw new StaleSessionGenerationError()
  teacherApi.value = candidate.api
  teacherSemesterId.value = candidate.semesterId
  profile.value = candidate.profile
  profilePort.value = sessionOwner.createProfilePort(candidate.generation, {
    onInvalidated: () => handleProfileInvalidated(candidate.generation),
  })
  offline.value = false
  error.value = null
  ownerRevision.value += 1
  authView.value = 'teacher'
}

function activateHeadmanOwner(value: ProfileSnapshot, generation: number): void {
  invalidateOwnerSynchronously({ clearAuth: false })
  authView.value = 'role'
  if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError()
  profile.value = value
  const groupId = authorizedHeadmanGroupId(value)
  if (groupId === null) {
    throw new ProfileRequestError('BOOTSTRAP_SCOPE_DENIED', 'Для роли старосты не определена учебная группа')
  }
  profile.value = value
  headmanApi.value = sessionOwner.createHeadmanApi(currentFetcher())
  headmanJournalApi.value = sessionOwner.createHeadmanJournalApi(currentFetcher())
  headmanStatsApi.value = sessionOwner.createHeadmanStatsApi(currentFetcher())
  headmanRequestsApi.value = sessionOwner.createHeadmanRequestsApi(currentFetcher())
  headmanGroupApi.value = sessionOwner.createHeadmanGroupApi(currentFetcher())
  headmanSubjectsApi.value = sessionOwner.createHeadmanSubjectsApi(currentFetcher())
  headmanHomeworkApi.value = sessionOwner.createHeadmanHomeworkApi(currentFetcher())
  headmanHomeworkActorUserId.value = positiveSafeHomeworkActorUserId(value)
  headmanGroupId.value = groupId
  profilePort.value = sessionOwner.createProfilePort(generation, {
    onInvalidated: () => handleProfileInvalidated(generation),
  })
  offline.value = false
  error.value = null
  ownerRevision.value += 1
  authView.value = 'headman'
}

async function bootstrap(): Promise<void> {
  if (bootstrapping.value) return
  if (browserIsOffline()) {
    offline.value = true
    ready.value = true
    error.value = 'TMA работает только при подключении к интернету'
    return
  }
  bootstrapping.value = true
  try {
    const candidateProfile = await fetchProfileForCurrentGeneration()
    profile.value = candidateProfile.profile
    roleError.value = null
    if (candidateProfile.profile.activeRole === 'HEADMAN') {
      activateHeadmanOwner(candidateProfile.profile, candidateProfile.generation)
    } else if (candidateProfile.profile.activeRole === 'STUDENT') {
      const candidate = await fetchSessionForCurrentGeneration()
      const assistant = await fetchAssistantCapabilities(candidate.generation, candidate.session)
      if (session.value && sessionIdentity(session.value) !== sessionIdentity(candidate.session)) {
        invalidateOwnerSynchronously()
        profile.value = candidateProfile.profile
      }
      const nextScope = studentFeatureScope(candidate.session, candidate.generation)
      const sameOwner = scope.value !== null
        && studentFeatureScopeIdentity(scope.value) === studentFeatureScopeIdentity(nextScope)
      // Keep a live owner mounted for same-generation refreshes so an ambiguous
      // check-in retains its command and idempotency key across reconnect.
      const needsFreshOwner = !sameOwner || api.value === null
      headmanApi.value = null
      headmanJournalApi.value = null
      headmanStatsApi.value = null
      headmanRequestsApi.value = null
      headmanGroupApi.value = null
      headmanSubjectsApi.value = null
      headmanHomeworkApi.value = null
      headmanHomeworkActorUserId.value = null
      headmanGroupId.value = null
      assistantJournalApi.value = assistant.journalApi
      assistantStatsApi.value = assistant.statsApi
      assistantRequestsApi.value = assistant.requestsApi
      assistantHomeworkApi.value = assistant.homeworkApi
      assistantPermissions.value = assistant.permissions
      api.value = needsFreshOwner ? candidate.api : api.value
      session.value = candidate.session
      scope.value = nextScope
      authView.value = 'student'
      if (needsFreshOwner) ownerRevision.value += 1
    } else if (candidateProfile.profile.activeRole === 'TEACHER') {
      const candidate = await fetchTeacherCandidate(candidateProfile.generation, candidateProfile.profile)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна')
      await activateTeacherCandidate(candidate)
    } else if (candidateProfile.profile.activeRole === 'ADMIN') {
      activateMapRole(candidateProfile.profile)
    } else {
      invalidateOwnerSynchronously({ clearAuth: false })
      profile.value = candidateProfile.profile
      authView.value = 'role'
      roleError.value = null
    }
    offline.value = false
    error.value = null
  } catch (cause) {
    if (isAuthDenied(cause)) {
      invalidateOwnerSynchronously()
      offline.value = false
    } else {
      // Keep an existing owner mounted but read-only when the authority check
      // cannot complete; a later retry can reuse its recovery intent.
      offline.value = true
    }
    error.value = cause instanceof Error ? cause.message : 'Не удалось открыть приложение'
  } finally {
    ready.value = true
    bootstrapping.value = false
  }
}

async function selectRole(
  role: ProfileRole,
  expectedSessionVersion: string,
  options: { preserveProfileOwner?: boolean } = {},
): Promise<void> {
  if (roleLoading.value || offline.value || !profile.value) return
  if (role !== 'STUDENT' && role !== 'HEADMAN' && role !== 'TEACHER' && role !== 'ADMIN') return
  const grant = profile.value.roles.find((candidate) => candidate.role === role)
  if (!grant?.selectable) return
  roleLoading.value = true
  pendingRole.value = role
  roleError.value = null
  error.value = null
  const generation = sessionOwner.currentGeneration()
  try {
    const selection = await sessionOwner.selectRoleFor(generation, { role, expectedSessionVersion })
    if (options.preserveProfileOwner) {
      invalidateOwnerSynchronously({ clearAuth: false })
      authView.value = 'role'
    }
    profile.value = selection.session
    if (role === 'HEADMAN') {
      activateHeadmanOwner(selection.session, selection.generation)
    } else if (role === 'TEACHER') {
      const candidate = await fetchTeacherCandidate(selection.generation, selection.session)
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна')
      await activateTeacherCandidate(candidate)
    } else if (role === 'ADMIN') {
      activateMapRole(selection.session)
    } else {
      const candidateApi = sessionOwner.createApi(currentFetcher())
      const candidateSession = await candidateApi.getSession()
      if (!sessionOwner.isCurrent(selection.generation)) throw new Error('Сессия сменилась во время входа')
      const assistant = await fetchAssistantCapabilities(selection.generation, candidateSession)
      headmanApi.value = null
      headmanJournalApi.value = null
      headmanStatsApi.value = null
      headmanRequestsApi.value = null
      headmanGroupApi.value = null
      headmanSubjectsApi.value = null
      headmanHomeworkApi.value = null
      headmanHomeworkActorUserId.value = null
      headmanGroupId.value = null
      assistantJournalApi.value = assistant.journalApi
      assistantStatsApi.value = assistant.statsApi
      assistantRequestsApi.value = assistant.requestsApi
      assistantHomeworkApi.value = assistant.homeworkApi
      assistantPermissions.value = assistant.permissions
      api.value = candidateApi
      session.value = candidateSession
      scope.value = studentFeatureScope(candidateSession, selection.generation)
      authView.value = 'student'
      ownerRevision.value += 1
    }
    offline.value = false
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) return
    roleError.value = asProfileError(cause)
    error.value = roleError.value.message
    if (!options.preserveProfileOwner) authView.value = 'role'
    try {
      const current = await sessionOwner.getProfileFor(generation)
      profile.value = current
    } catch {
      // Keep the current role snapshot visible until the next authenticated retry.
    }
    if (options.preserveProfileOwner) throw cause
  } finally {
    pendingRole.value = null
    roleLoading.value = false
  }
}

function selectProfileRole(role: ProfileRole, expectedSessionVersion: string): Promise<void> {
  return selectRole(role, expectedSessionVersion, { preserveProfileOwner: true })
}

function handleProfileInvalidated(generation: number): void {
  if (!sessionOwner.isCurrent(generation)) return
  invalidateOwnerSynchronously()
  authView.value = 'role'
  offline.value = false
  error.value = null
  void bootstrap()
}

function acquireCheckinCommand(): Promise<StudentCheckinCommand> {
  return host.location()
}

function openMaterial(url: string): void {
  try {
    const parsed = new URL(url)
    if ((parsed.protocol !== 'http:' && parsed.protocol !== 'https:') || !parsed.hostname) return
    host.openLink(url)
  } catch {
    // Unsupported server data remains inert.
  }
}

function onOwnerError(cause: unknown): void {
  if (isAuthDenied(cause)) {
    invalidateOwnerSynchronously()
    offline.value = false
  }
  error.value = cause instanceof StudentApiError
    ? cause.problem?.detail ?? cause.message
    : cause instanceof Error ? cause.message : 'Не удалось выполнить действие'
}

function offlineNow(): void {
  offline.value = true
  error.value = 'TMA работает только при подключении к интернету'
}

function onlineNow(): void {
  // Browser online is only a transport hint. Keep the owner read-only until
  // Telegram authentication and the session identity have been checked.
  offline.value = true
  error.value = 'Проверяем подключение к Telegram…'
  void bootstrap()
}

onMounted(() => {
  window.addEventListener('offline', offlineNow)
  window.addEventListener('online', onlineNow)
  void bootstrap()
})

onBeforeUnmount(() => {
  invalidateOwnerSynchronously()
  window.removeEventListener('offline', offlineNow)
  window.removeEventListener('online', onlineNow)
  theme?.dispose()
})
</script>

<template>
  <NotificationsEntryButton
    v-if="notificationsEntryVisible"
    @click="openNotifications"
  />
  <Teleport to="body">
    <NotificationsScreen
      v-if="notificationsOpen && notificationsApi"
      :key="`notifications-${notificationsGeneration}`"
      :api="notificationsApi"
      :host="host"
      :offline="offline"
      :can-open-target="canOpenNotificationTarget"
      @close="closeNotifications"
      @open-target="openNotificationTarget"
      @owner-error="onOwnerError"
    />
  </Teleport>
  <RoleSwitchScreen
    v-if="authView === 'role' && profile"
    :snapshot="profile"
    :pending-role="pendingRole"
    :error="roleError"
    :offline="offline"
    :loading="roleLoading || bootstrapping"
    :on-select-role="selectRole"
  />
  <HeadmanScheduleScreen
    v-else-if="headmanViewVisible"
    :key="`headman-${ownerRevision}`"
    :api="headmanApi"
    :journal-api="headmanJournalApi"
    :stats-api="headmanStatsApi"
    :requests-api="headmanRequestsApi"
    :group-api="headmanGroupApi"
    :subjects-api="headmanSubjectsApi"
    :homework-api="headmanHomeworkApi"
    :homework-actor-user-id="headmanHomeworkActorUserId"
    :profile="profile"
    :profile-port="profilePort"
    :profile-role-select="selectProfileRole"
    :group-id="headmanGroupId"
    :offline="offline"
    :read-only="profile?.readOnly ?? true"
    :host="host"
    :report-download="reportDownload"
    :map-client="mapClient"
    :theme-controller="theme"
    @error="onOwnerError"
  />
  <TeacherFeatureOwner
    v-else-if="teacherViewVisible"
    :key="`teacher-${ownerRevision}`"
    :api="teacherApi"
    :semester-id="teacherSemesterId"
    :map-client="mapClient"
    :report-download="reportDownload"
    :profile-port="profilePort"
    :profile-role-select="selectProfileRole"
    :host="host"
    :theme-controller="theme"
    :offline="offline"
    @owner-error="onOwnerError"
  />
  <MapScreen
    v-else-if="mapViewVisible"
    :client="mapClient"
    theme="dark"
  />
  <template v-else-if="adminHomeViewVisible && adminDashboardApi">
    <AdminRoleNavigation
      active="home"
      @navigate="navigateAdmin"
    />
    <AdminDashboardScreen
      :key="`admin-home-${ownerRevision}`"
      :client="adminDashboardApi"
      theme="dark"
      @owner-error="onOwnerError"
    />
  </template>
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
    :owner-key="ownerKey"
    :api="api"
    :scope="scope"
    :offline="offline"
    :read-only="false"
    :today-fallback="null"
    :homework-fallback="null"
    :semester-schedule="null"
    :updated-at="null"
    :host="host"
    :report-download="reportDownload"
    :map-client="mapClient"
    :acquire-checkin-command="acquireCheckinCommand"
    :open-material="openMaterial"
    :notification-target-intent="notificationTargetIntent"
    :assistant-permissions="assistantPermissions"
    :assistant-journal-api="assistantJournalApi"
    :assistant-stats-api="assistantStatsApi"
    :assistant-requests-api="assistantRequestsApi"
    :assistant-homework-api="assistantHomeworkApi"
    @owner-error="onOwnerError"
    @clear-notification-target="clearNotificationTargetIntent"
  />
  <section
    v-if="offline && featureVisible"
    class="today-state today-state--error"
    role="alert"
    aria-live="polite"
  >
    <h2>Подключись к интернету</h2>
    <p>{{ error ?? 'TMA работает только при подключении к интернету' }}</p>
  </section>
  <section
    v-if="!featureVisible && !profile && (!ready || error)"
    class="today-state"
    :class="{ 'today-state--error': Boolean(error) }"
    :role="error ? 'alert' : undefined"
    aria-live="polite"
  >
    <span
      v-if="!error"
      class="today-state__spinner"
      aria-hidden="true"
    />
    <h2 v-if="error">
      Не удалось открыть приложение
    </h2>
    <p>{{ error ?? 'Подключаемся к Telegram…' }}</p>
    <button
      v-if="error && !offline"
      type="button"
      @click="bootstrap"
    >
      Повторить
    </button>
  </section>
</template>

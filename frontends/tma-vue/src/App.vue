<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import {
  AdminMapClient,
  AdminMapScreen,
  CampusMapClient,
  HeadmanScheduleApiError,
  HeadmanJournalApiError,
  HeadmanScheduleScreen,
  MapScreen,
  ProfileRequestError,
  RoleSwitchScreen,
  StaleSessionGenerationError,
  StudentApi,
  StudentApiError,
  StudentFeatureOwner,
  createFixtureTransport,
  createMobileTheme,
  studentFeatureScope,
  studentFeatureScopeIdentity,
  type StudentCheckinCommand,
  type StudentFeatureScope,
  type StudentSession,
  type HeadmanScheduleApi,
  type HeadmanJournalApi,
  type ProfileRole,
  type ProfileSnapshot,
} from '@rct/mobile-core'
import { installFixtureTelegramHost, TelegramHost } from './telegram'
import { useTmaSession } from './tma-session'
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
const theme = typeof document === 'undefined' ? null : createMobileTheme()
const mapClient = new CampusMapClient({
  accessToken: () => sessionOwner.accessToken.value,
  onUnauthorized: () => sessionOwner.authenticateFor(sessionOwner.currentGeneration()),
  fetcher: fixtureTransport ?? nativeFetcher,
})
const adminMapClient = new AdminMapClient({
  accessToken: () => sessionOwner.accessToken.value,
  onUnauthorized: () => sessionOwner.authenticateFor(sessionOwner.currentGeneration()),
  fetcher: fixtureTransport ?? nativeFetcher,
})

const api = shallowRef<StudentApi | null>(null)
const headmanApi = shallowRef<HeadmanScheduleApi | null>(null)
const headmanJournalApi = shallowRef<HeadmanJournalApi | null>(null)
const session = shallowRef<StudentSession | null>(null)
const scope = shallowRef<StudentFeatureScope | null>(null)
const profile = shallowRef<ProfileSnapshot | null>(null)
const headmanGroupId = ref<number | null>(null)
const ready = ref(false)
const offline = ref(typeof navigator !== 'undefined' ? !navigator.onLine : false)
const error = ref<string | null>(null)
const bootstrapping = ref(false)
const ownerRevision = ref(0)
const authView = ref<'role' | 'student' | 'headman' | 'map' | 'admin-map'>('role')
const pendingRole = ref<ProfileRole | null>(null)
const roleError = shallowRef<ProfileRequestError | null>(null)
const roleLoading = ref(false)

const mapViewVisible = computed(() => authView.value === 'map')
const adminMapViewVisible = computed(() => authView.value === 'admin-map')
const featureVisible = computed(() => api.value !== null || headmanApi.value !== null
  || mapViewVisible.value || adminMapViewVisible.value)
const studentViewVisible = computed(() => authView.value === 'student' && api.value !== null)
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

function authDenialStatus(cause: unknown): number | null {
  if (cause instanceof TmaAuthError) return cause.status
  if (cause instanceof StudentApiError) return cause.response.status
  if (cause instanceof HeadmanScheduleApiError) return cause.response.status
  if (cause instanceof HeadmanJournalApiError) return cause.response.status
  return null
}

function isAuthDenied(cause: unknown): boolean {
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

function asProfileError(cause: unknown): ProfileRequestError {
  if (cause instanceof ProfileRequestError) return cause
  return new ProfileRequestError('UNKNOWN', cause instanceof Error ? cause.message : 'Не удалось сменить роль')
}

function invalidateOwnerSynchronously(options: { clearAuth?: boolean } = {}): void {
  if (options.clearAuth !== false) sessionOwner.clear()
  headmanApi.value = null
  headmanJournalApi.value = null
  headmanGroupId.value = null
  scope.value = null
  api.value = null
  session.value = null
  profile.value = null
  ownerRevision.value += 1
}

function activateMapRole(value: ProfileSnapshot): void {
  invalidateOwnerSynchronously({ clearAuth: false })
  profile.value = value
  offline.value = false
  error.value = null
  ownerRevision.value += 1
  authView.value = value.activeRole === 'ADMIN' ? 'admin-map' : 'map'
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
      const groupId = authorizedHeadmanGroupId(candidateProfile.profile)
      if (groupId === null) throw new ProfileRequestError('BOOTSTRAP_SCOPE_DENIED', 'Для роли старосты не определена учебная группа')
      api.value = null
      session.value = null
      scope.value = null
      headmanApi.value = sessionOwner.createHeadmanApi(currentFetcher())
      headmanJournalApi.value = sessionOwner.createHeadmanJournalApi(currentFetcher())
      headmanGroupId.value = groupId
      authView.value = 'headman'
      ownerRevision.value += 1
    } else if (candidateProfile.profile.activeRole === 'STUDENT') {
      const candidate = await fetchSessionForCurrentGeneration()
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
      headmanGroupId.value = null
      api.value = needsFreshOwner ? candidate.api : api.value
      session.value = candidate.session
      scope.value = nextScope
      authView.value = 'student'
      if (needsFreshOwner) ownerRevision.value += 1
    } else if (candidateProfile.profile.activeRole === 'TEACHER' || candidateProfile.profile.activeRole === 'ADMIN') {
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

async function selectRole(role: ProfileRole, expectedSessionVersion: string): Promise<void> {
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
    profile.value = selection.session
    if (role === 'HEADMAN') {
      const groupId = authorizedHeadmanGroupId(selection.session)
      if (groupId === null) throw new ProfileRequestError('BOOTSTRAP_SCOPE_DENIED', 'Для роли старосты не определена учебная группа')
      api.value = null
      session.value = null
      scope.value = null
      headmanApi.value = sessionOwner.createHeadmanApi(currentFetcher())
      headmanJournalApi.value = sessionOwner.createHeadmanJournalApi(currentFetcher())
      headmanGroupId.value = groupId
      authView.value = 'headman'
      ownerRevision.value += 1
    } else if (role === 'TEACHER' || role === 'ADMIN') {
      activateMapRole(selection.session)
    } else {
      const candidateApi = sessionOwner.createApi(currentFetcher())
      const candidateSession = await candidateApi.getSession()
      if (!sessionOwner.isCurrent(selection.generation)) throw new Error('Сессия сменилась во время входа')
      headmanApi.value = null
      headmanJournalApi.value = null
      headmanGroupId.value = null
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
    authView.value = 'role'
    try {
      const current = await sessionOwner.getProfileFor(generation)
      profile.value = current
    } catch {
      // Keep the current role snapshot visible until the next authenticated retry.
    }
  } finally {
    pendingRole.value = null
    roleLoading.value = false
  }
}

function openRoleSwitch(): void {
  roleError.value = null
  authView.value = 'role'
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
    :profile="profile"
    :group-id="headmanGroupId"
    :offline="offline"
    :read-only="profile?.readOnly ?? true"
    :host="host"
    :map-client="mapClient"
    :on-role-switch="openRoleSwitch"
    @error="onOwnerError"
  />
  <MapScreen
    v-else-if="mapViewVisible"
    :client="mapClient"
    theme="dark"
  />
  <AdminMapScreen
    v-else-if="adminMapViewVisible"
    :client="adminMapClient"
    theme="dark"
  />
  <StudentFeatureOwner
    v-else-if="studentViewVisible"
    :key="ownerKey"
    :api="api"
    :scope="scope"
    :offline="offline"
    :read-only="false"
    :today-fallback="null"
    :homework-fallback="null"
    :semester-schedule="null"
    :updated-at="null"
    :host="host"
    :map-client="mapClient"
    :acquire-checkin-command="acquireCheckinCommand"
    :open-material="openMaterial"
    @owner-error="onOwnerError"
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

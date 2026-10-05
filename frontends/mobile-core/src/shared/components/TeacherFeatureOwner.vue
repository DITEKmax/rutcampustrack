<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { CampusMapClient } from '../../api/map-client'
import MapScreen from '../../features/map/MapScreen.vue'
import AccountHistoryScreen from '../../features/profile/AccountHistoryScreen.vue'
import AppearanceScreen from '../../features/profile/AppearanceScreen.vue'
import ProfileScreen from '../../features/profile/ProfileScreen.vue'
import { ProfileState } from '../../features/profile/profile-state'
import RoleSwitchScreen from '../../features/profile/RoleSwitchScreen.vue'
import SecurityScreen from '../../features/profile/SecurityScreen.vue'
import SessionsScreen from '../../features/profile/SessionsScreen.vue'
import {
  DEFAULT_PASSWORD_POLICY,
  ProfileRequestError,
} from '../../features/profile/profile-types'
import type {
  ProfilePort,
  ProfileRole,
  ProfileRoute,
  ProfileTheme,
} from '../../features/profile/profile-types'
import type { TeacherApi, TeacherJournalQuery } from '../../features/teacher/teacher-client'
import TeacherExcuseScreen from '../../features/teacher/TeacherExcuseScreen.vue'
import TeacherAttendanceScreen from '../../features/teacher/TeacherAttendanceScreen.vue'
import TeacherHomeScreen from '../../features/teacher/TeacherHomeScreen.vue'
import TeacherJournalScreen from '../../features/teacher/TeacherJournalScreen.vue'
import TeacherLessonScreen from '../../features/teacher/TeacherLessonScreen.vue'
import TeacherStatsScreen from '../../features/teacher/TeacherStatsScreen.vue'
import {
  activateTeacherStatsRoute,
  clearTeacherStatsRoute,
  hasTeacherStatsRoute,
} from '../../features/teacher/teacher-stats-route'
import MobileShell from './MobileShell.vue'
import { profileOwnerStaleMessage } from '../profile-owner-status'
import {
  createMobileNavigationStack,
  nestedRoute,
  rootRoute,
} from '../navigation'
import type { MobileNavigationStack, MobileRootRouteId, MobileRoute } from '../navigation'
import { createTeacherNavigationItems } from '../mobile-navigation-items'
import type { MobileHostAdapter } from '../host'
import type { MobileThemeController, MobileThemeResolvedMode } from '../theme'
import type { ReportDownloadPort } from '../report-download-client'
import { createProfileViewPublication } from './profile-view-publication'
import './teacher-feature-owner.pcss'

const props = withDefaults(defineProps<{
  api: TeacherApi | null
  semesterId: number | null
  mapClient?: CampusMapClient | null
  selectedDate?: string
  reportDownload?: ReportDownloadPort | null
  profilePort?: ProfilePort | null
  profileRoleSelect?: ((role: ProfileRole, expectedSessionVersion: string) => void | Promise<void>) | undefined
  host?: MobileHostAdapter | null
  themeController?: MobileThemeController | null
  offline?: boolean
}>(), {
  selectedDate: '',
  mapClient: null,
  reportDownload: null,
  profilePort: null,
  profileRoleSelect: undefined,
  host: null,
  themeController: null,
  offline: false,
})

const emit = defineEmits<{
  ownerError: [cause: unknown]
}>()

type ProfileArea = 'sessions' | 'history'
type ProfileAccountRoute = Extract<ProfileRoute, 'role-switch' | 'appearance' | 'security' | 'sessions' | 'history'>

const navigation: MobileNavigationStack = createMobileNavigationStack(
  rootRoute(hasTeacherStatsRoute() ? 'teacher-stats' : 'teacher-home'),
)
const route = ref<MobileRoute>(navigation.current)
const selectedDate = ref(props.selectedDate || moscowToday())
const lessonId = ref<number | null>(null)
const journalQuery = ref<TeacherJournalQuery | null>(null)
const attendanceQuery = ref<TeacherJournalQuery | null>(null)
const requestId = ref<string | null>(null)
const profileState = shallowRef(props.profilePort ? new ProfileState(props.profilePort) : null)
const profilePendingRole = ref<ProfileRole | null>(null)
const profileRoleError = shallowRef<ProfileRequestError | null>(null)
const themeMode = ref<ProfileTheme>(props.themeController?.mode ?? 'system')
const resolvedTheme = ref<MobileThemeResolvedMode>(props.themeController?.resolvedMode ?? 'dark')
const navItems = computed(() => createTeacherNavigationItems(props.profilePort !== null))
const profilePublication = createProfileViewPublication(
  () => profileState.value?.view ?? null,
  () => disposed,
)
const profileView = profilePublication.view
const publishProfileView = profilePublication.publish
const profileOwnerStatus = computed(() => profileOwnerStaleMessage(route.value, props.offline))
let disposed = false
let stopTheme = (): void => undefined

const stopNavigation = navigation.subscribe(() => {
  route.value = navigation.current
  ensureProfileRoute(route.value)
})

watch(
  () => [props.api, props.semesterId, props.selectedDate, props.profilePort] as const,
  ([, , nextDate, profilePort]) => {
    lessonId.value = null
    journalQuery.value = null
    attendanceQuery.value = null
    requestId.value = null
    if (nextDate) selectedDate.value = nextDate
    profileState.value = profilePort ? new ProfileState(profilePort) : null
    profileRoleError.value = null
    profilePendingRole.value = null
    publishProfileView()
    navigation.goRoot(hasTeacherStatsRoute() ? 'teacher-stats' : 'teacher-home')
  },
)

function moscowToday(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })
}

function bindTheme(controller: MobileThemeController | null): void {
  stopTheme()
  stopTheme = (): void => undefined
  if (!controller) {
    themeMode.value = 'system'
    resolvedTheme.value = 'dark'
    return
  }
  themeMode.value = controller.mode
  resolvedTheme.value = controller.resolvedMode
  stopTheme = controller.subscribe((snapshot) => {
    themeMode.value = snapshot.mode
    resolvedTheme.value = snapshot.resolvedMode
  })
}

watch(() => props.themeController, bindTheme, { immediate: true })

async function runProfile<T>(
  request: (state: ProfileState) => Promise<T>,
  options: { rethrow?: boolean } = {},
): Promise<T | undefined> {
  const state = profileState.value
  if (!state || disposed) return undefined
  let pending: Promise<T>
  try {
    pending = request(state)
    publishProfileView()
  } catch {
    publishProfileView()
    return undefined
  }
  try {
    return await pending
  } catch (error) {
    if (options.rethrow) throw error
    return undefined
  } finally {
    publishProfileView()
    await state.waitForAutomaticStaleReload()
    publishProfileView()
  }
}

async function loadProfileSnapshot(): Promise<void> {
  const loaded = await runProfile((state) => state.loadSnapshot())
  if (loaded && !disposed) ensureProfileRoute(route.value)
}

async function openProfileArea(area: ProfileArea): Promise<void> {
  const state = profileState.value
  if (!state || disposed) return
  if (!state.view.snapshot) {
    await runProfile((current) => current.loadSnapshot())
    if (disposed || !state.view.snapshot) return
  }
  if (area === 'sessions') await runProfile((current) => current.loadAllSessions(publishProfileView))
  else await runProfile((current) => current.loadHistory())
}

function retryProfileSnapshot(): void {
  void loadProfileSnapshot()
}

function retryProfileArea(area: ProfileArea): void {
  void openProfileArea(area)
}

function loadMoreProfile(area: ProfileArea, cursor: string): void {
  const state = profileState.value
  if (!state || disposed) return
  if (area === 'sessions') void runProfile((current) => current.loadSessions({ cursor }))
  else void runProfile((current) => current.loadHistory({ cursor }))
}

function profileRoute(routeName: ProfileAccountRoute) {
  if (routeName === 'role-switch') return nestedRoute('profile', 'profile/role-switch', 'detail')
  if (routeName === 'appearance') return nestedRoute('profile', 'profile/appearance', 'detail')
  if (routeName === 'security') return nestedRoute('profile', 'profile/security', 'detail')
  if (routeName === 'sessions') return nestedRoute('profile', 'profile/sessions', 'detail')
  return nestedRoute('profile', 'profile/history', 'detail')
}

function navigateProfile(routeName: ProfileRoute): void {
  if (routeName === 'profile') {
    navigation.goRoot('profile')
    return
  }
  if (routeName === 'role-switch' || routeName === 'appearance' || routeName === 'security'
    || routeName === 'sessions' || routeName === 'history') {
    profileRoleError.value = null
    navigation.push(profileRoute(routeName))
  }
}

function navigateRoot(routeName: MobileRootRouteId): void {
  if (routeName === 'teacher-home') clearTeacherStatsRoute()
  else if (routeName === 'teacher-stats') activateTeacherStatsRoute()
  else if (routeName !== 'teacher-attendance' && routeName !== 'teacher-map' && routeName !== 'profile') return
  lessonId.value = null
  navigation.goRoot(routeName)
}

function backRoute(): void {
  navigation.back()
}

function openLesson(nextLessonId: number): void {
  lessonId.value = nextLessonId
  navigation.push(nestedRoute('teacher-home', 'teacher-home/lesson', 'detail'))
}

function openJournal(query: TeacherJournalQuery, returnSurface: 'home' | 'stats' = 'home'): void {
  journalQuery.value = query
  lessonId.value = null
  const parent = returnSurface === 'stats' ? 'teacher-stats' : 'teacher-home'
  navigation.push(nestedRoute(parent, `${parent}/journal`, 'detail'))
}

function openAttendanceJournal(query: TeacherJournalQuery): void {
  attendanceQuery.value = query
  journalQuery.value = query
  lessonId.value = null
  navigation.push(nestedRoute('teacher-attendance', 'teacher-attendance/journal', 'detail'))
}

function openJournalLesson(nextLessonId: number, page: number): void {
  if (!route.value.id.endsWith('/journal')) return
  if (route.value.root === 'teacher-attendance' && attendanceQuery.value) {
    attendanceQuery.value = { ...attendanceQuery.value, page }
  } else if (journalQuery.value) {
    journalQuery.value = { ...journalQuery.value, page }
  }
  lessonId.value = nextLessonId
  const parent = route.value.root
  navigation.push(nestedRoute(parent, `${parent}/journal/lesson`, 'detail'))
}

function openStatsJournal(query: TeacherJournalQuery): void {
  openJournal(query, 'stats')
}

function openExcuse(nextRequestId: string): void {
  const parent = route.value.root
  if (parent !== 'teacher-home' && parent !== 'teacher-stats' && parent !== 'teacher-attendance') return
  requestId.value = nextRequestId
  navigation.push(nestedRoute(parent, `${parent}/excuse`, 'task'))
}

function asProfileError(cause: unknown): ProfileRequestError {
  if (cause instanceof ProfileRequestError) return cause
  const status = typeof cause === 'object' && cause !== null && 'status' in cause && typeof cause.status === 'number'
    ? cause.status
    : undefined
  const code = status === 401 ? 'INVALID_SESSION' : status === 403 ? 'ROLE_NOT_GRANTED' : 'NETWORK'
  return new ProfileRequestError(code, cause instanceof Error ? cause.message : 'Не удалось сменить роль', status, cause)
}

async function selectProfileRole(role: ProfileRole, expectedSessionVersion: string): Promise<void> {
  const state = profileState.value
  if (!state && !props.profileRoleSelect) return
  profilePendingRole.value = role
  profileRoleError.value = null
  try {
    if (props.profileRoleSelect) await props.profileRoleSelect(role, expectedSessionVersion)
    else await runProfile((current) => current.selectRole(role), { rethrow: true })
  } catch (cause) {
    if (!disposed) {
      const typed = asProfileError(cause)
      profileRoleError.value = typed
      if (typed.code === 'SESSION_VERSION_CONFLICT' || typed.code === 'SESSION_STATE_STALE') {
        await runProfile((current) => current.loadSnapshot())
      }
    }
  } finally {
    if (!disposed) {
      profilePendingRole.value = null
      publishProfileView()
    }
  }
}

function changeProfileTheme(mode: ProfileTheme): void {
  props.themeController?.setMode(mode)
}

async function changeProfilePassword(input: { currentPassword: string; newPassword: string }): Promise<void> {
  if (!profileState.value || disposed) return
  await runProfile((state) => state.changePassword(input), { rethrow: true })
}

async function terminateProfileSession(sessionId: string): Promise<void> {
  await runProfile(state => state.terminateSession(sessionId), { rethrow: true })
}

async function logoutProfileAll(): Promise<void> {
  if (!profileState.value || disposed) return
  await runProfile((state) => state.logoutAll())
}

function ensureProfileRoute(routeValue: MobileRoute): void {
  const state = profileState.value
  if (routeValue.root !== 'profile' || !state || disposed) return
  if (!state.view.snapshot) {
    if (state.view.snapshotStatus === 'loading') return
    if (routeValue.id === 'profile/sessions' || routeValue.id === 'profile/history') {
      void openProfileArea(routeValue.id === 'profile/sessions' ? 'sessions' : 'history')
    } else {
      void loadProfileSnapshot()
    }
    return
  }
  if (routeValue.id === 'profile/sessions' && state.view.sessionsStatus === 'idle') void openProfileArea('sessions')
  if (routeValue.id === 'profile/history' && state.view.historyStatus === 'idle') void openProfileArea('history')
}

function forwardError(cause: unknown): void {
  emit('ownerError', cause)
}

onBeforeUnmount(() => {
  disposed = true
  lessonId.value = null
  journalQuery.value = null
  requestId.value = null
  stopNavigation()
  stopTheme()
})
</script>

<template>
  <MobileShell
    :route="route"
    :navigation="navigation"
    :nav-items="navItems"
    :active-id="route.root"
    :host="host"
  >
    <template #back />
    <div
      class="teacher-feature-owner"
      :data-host-back="host?.backOwner === 'host'"
    >
      <TeacherHomeScreen
        v-if="route.id === 'teacher-home'"
        :api="api"
        :semester-id="semesterId"
        :selected-date="selectedDate"
        :profile-enabled="profilePort !== null"
        @select-date="selectedDate = $event"
        @open-lesson="openLesson"
        @open-journal="openJournal"
        @open-stats="navigateRoot('teacher-stats')"
        @open-profile="navigateRoot('profile')"
        @error="forwardError"
      />
      <TeacherLessonScreen
        v-else-if="route.id === 'teacher-home/lesson'"
        :api="api"
        :lesson-id="lessonId"
        @back="backRoute"
        @open-excuse="openExcuse"
        @error="forwardError"
      />
      <TeacherJournalScreen
        v-else-if="route.id.endsWith('/journal') || route.id.endsWith('/journal/lesson')"
        :api="api"
        :query="route.root === 'teacher-attendance' ? attendanceQuery : journalQuery"
        :selected-lesson-id="route.id.endsWith('/journal/lesson') ? lessonId : null"
        :report-download="reportDownload"
        @back="backRoute"
        @open-lesson="openJournalLesson"
        @open-excuse="openExcuse"
        @error="forwardError"
      />
      <TeacherExcuseScreen
        v-else-if="route.id.endsWith('/excuse')"
        :api="api"
        :request-id="requestId"
        @back="backRoute"
        @error="forwardError"
      />
      <TeacherStatsScreen
        v-else-if="route.id === 'teacher-stats'"
        :api="api"
        :semester-id="semesterId"
        :report-download="reportDownload"
        @back="navigateRoot('teacher-home')"
        @open-journal="openStatsJournal"
        @error="forwardError"
      />
      <MapScreen
        v-else-if="route.id === 'teacher-map' && mapClient"
        :client="mapClient"
        :theme="resolvedTheme"
      />
      <p
        v-else-if="route.id === 'teacher-map'"
        class="teacher-screen__state"
        role="status"
      >
        Карта сейчас недоступна.
      </p>
      <template v-else-if="route.root === 'profile'">
        <p
          v-if="profileOwnerStatus"
          class="profile-inline-error profile-owner-status"
          data-profile-stale="true"
          role="status"
          aria-live="polite"
        >
          {{ profileOwnerStatus }}
        </p>
        <ProfileScreen
          v-if="route.id === 'profile'"
          :snapshot="profileView.snapshot"
          :loading="profileView.snapshotStatus === 'loading'"
          :error="profileView.snapshotError"
          :theme="resolvedTheme"
          :show-active-role="false"
          :on-retry="retryProfileSnapshot"
          :on-navigate="navigateProfile"
        />
        <RoleSwitchScreen
          v-else-if="route.id === 'profile/role-switch'"
          :snapshot="profileView.snapshot"
          :pending-role="profilePendingRole"
          :error="profileRoleError ?? profileView.snapshotError"
          :loading="profileView.snapshotStatus === 'loading'"
          :offline="offline || profilePort === null"
          :theme="resolvedTheme"
          :on-back="backRoute"
          :on-select-role="selectProfileRole"
        />
        <AppearanceScreen
          v-else-if="route.id === 'profile/appearance'"
          :theme="themeMode"
          :resolved-theme="resolvedTheme"
          :on-back="backRoute"
          :on-theme-change="changeProfileTheme"
        />
        <SecurityScreen
          v-else-if="route.id === 'profile/security'"
          :policy="profileView.snapshot?.passwordPolicy ?? DEFAULT_PASSWORD_POLICY"
          :error="profileView.error"
          :busy="profileView.mutationBusy === 'password'"
          :offline="offline || profilePort === null"
          :theme="resolvedTheme"
          :on-back="backRoute"
          :on-change-password="changeProfilePassword"
        />
        <SessionsScreen
          v-else-if="route.id === 'profile/sessions'"
          :sessions="profileView.sessions"
          :loading="profileView.sessionsStatus === 'loading'"
          :error="profileView.sessionsError"
          :busy="profileView.mutationBusy !== null"
          :terminating-session-id="profileView.terminatingSessionId"
          :termination-error="profileView.terminationError"
          :on-terminate-session="profilePort?.terminateSession ? terminateProfileSession : undefined"
          :offline="offline || profilePort === null"
          :theme="resolvedTheme"
          :on-back="backRoute"
          :on-retry="() => retryProfileArea('sessions')"
          :on-logout-all="logoutProfileAll"
        />
        <AccountHistoryScreen
          v-else-if="route.id === 'profile/history'"
          :events="profileView.history"
          :loading="profileView.historyStatus === 'loading'"
          :error="profileView.historyError"
          :next-cursor="profileView.historyNextCursor"
          :theme="resolvedTheme"
          :on-back="backRoute"
          :on-retry="() => retryProfileArea('history')"
          :on-load-more="(cursor) => loadMoreProfile('history', cursor)"
        />
      </template>
      <TeacherAttendanceScreen
        v-show="route.id === 'teacher-attendance'"
        :api="api"
        :semester-id="semesterId"
        @apply="openAttendanceJournal"
        @error="forwardError"
      />
    </div>
  </MobileShell>
</template>

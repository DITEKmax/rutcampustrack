<script setup lang="ts">
import type { NotificationAction } from '../../features/notifications/notifications-v2-models'
import { notificationNumericId } from '../../features/notifications/notifications-actions'
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
import type { TeacherApi, TeacherJournalQuery, TeacherCatalogResponse, TeacherSemester, TeacherStatsSubjectOption } from '../../features/teacher/teacher-client'
import type { HeadmanLessonSlot } from '../../features/schedule/headman-schedule-client'
import TeacherManualExcuseScreen from '../../features/teacher/TeacherManualExcuseScreen.vue'
import TeacherGroupSubjectsScreen from '../../features/teacher/TeacherGroupSubjectsScreen.vue'
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
import MobileIcon from './MobileIcon.vue'
import StudentWarningBlock from './StudentWarningBlock.vue'
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
  unreadCount?: number | null | undefined
  onNotifications?: (() => void) | undefined
  api: TeacherApi | null
  semesterId: number | null
  mapClient?: CampusMapClient | null
  selectedDate?: string
  reportDownload?: ReportDownloadPort | null
  profilePort?: ProfilePort | null
  onRecover?: (() => void | Promise<void>) | undefined
  profileRoleSelect?: ((role: ProfileRole, expectedSessionVersion: string) => void | Promise<void>) | undefined
  host?: MobileHostAdapter | null
  themeController?: MobileThemeController | null
  offline?: boolean
}>(), { unreadCount: null, onNotifications: undefined,
  selectedDate: '',
  mapClient: null,
  reportDownload: null,
  profilePort: null,
  onRecover: undefined,
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
const journalVisited = ref(false)
const journalEntry = ref<'taught' | 'history'>('history')
const attendanceQuery = ref<TeacherJournalQuery | null>(null)
const attendanceVisited = ref(false)
const requestId = ref<string | null>(null)
const manualStudentId = ref<number | null>(null)
const manualLessonId = ref<number | null>(null)
const selectedGroup = ref<{ id: number; name: string } | null>(null)
const catalog = shallowRef<TeacherCatalogResponse | null>(null)
const semester = shallowRef<TeacherSemester | null>(null)
const lessonSlots = shallowRef<readonly HeadmanLessonSlot[]>([])
const teacherContextError = ref<string | null>(null)
const teacherContextLoading = ref(false)
const ownerGeneration = ref(0)
const calendarOpen = ref(false)
const calendarCloseSignal = ref(0)
const exportOpen = ref(false)
const exportOpenSignal = ref(0)
const exportCloseSignal = ref(0)
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
let teacherContextAttempt = 0
let contextInFlight: { readonly api: TeacherApi; readonly semesterId: number; readonly generation: number; readonly promise: Promise<void> } | null = null
let stopTheme = (): void => undefined
const stopOverlayBack = navigation.beforeBack(() => {
  if (calendarOpen.value) { calendarCloseSignal.value += 1; return false }
  if (exportOpen.value) { exportCloseSignal.value += 1; return false }
  return true
})
const journalSubjectOptions = computed(() => {
  const options = new Map<string, TeacherStatsSubjectOption>()
  for (const option of [...(catalog.value?.subjectOptions ?? []), ...(catalog.value?.taughtSubjectOptions ?? [])]) {
    const key = `${option.groupId}:${option.subjectId}`
    const previous = options.get(key)
    options.set(key, previous ? { ...option, lessonTypes: [...new Set([...previous.lessonTypes, ...option.lessonTypes])] } : option)
  }
  if (journalEntry.value === 'history') return [...options.values()]
  const taughtIds = new Set(catalog.value?.taughtSubjectOptions.map(option => `${option.groupId}:${option.subjectId}`) ?? [])
  return [...options.values()].filter(option => taughtIds.has(`${option.groupId}:${option.subjectId}`))
})

const stopNavigation = navigation.subscribe(() => {
  route.value = navigation.current
  if (route.value.root === 'teacher-attendance') attendanceVisited.value = true
  if (route.value.id.endsWith('/journal')) journalVisited.value = true
  ensureProfileRoute(route.value)
})

watch(
  () => [props.api, props.semesterId, props.selectedDate, props.profilePort] as const,
  ([, , nextDate, profilePort]) => {
    lessonId.value = null
    journalQuery.value = null
    journalVisited.value = false
    attendanceQuery.value = null
    attendanceVisited.value = false
    requestId.value = null
    manualStudentId.value = null
    manualLessonId.value = null
    selectedGroup.value = null
    catalog.value = null
    semester.value = null
    lessonSlots.value = []
    ownerGeneration.value += 1
    calendarOpen.value = false
    exportOpen.value = false
    teacherContextError.value = null
    void loadTeacherContext()
    if (nextDate) selectedDate.value = nextDate
    profileState.value = profilePort ? new ProfileState(profilePort) : null
    profileRoleError.value = null
    profilePendingRole.value = null
    publishProfileView()
    navigation.goRoot(hasTeacherStatsRoute() ? 'teacher-stats' : 'teacher-home')
  },
)

function loadTeacherContext(): Promise<void> {
  const api = props.api
  const semesterId = props.semesterId
  const generation = ownerGeneration.value
  if (!api || semesterId == null) { teacherContextLoading.value = false; return Promise.resolve() }
  if (contextInFlight?.api === api && contextInFlight.semesterId === semesterId && contextInFlight.generation === generation) {
    return contextInFlight.promise
  }
  const attempt = ++teacherContextAttempt
  const current = () => !disposed && attempt === teacherContextAttempt && generation === ownerGeneration.value && api === props.api && semesterId === props.semesterId
  teacherContextLoading.value = true
  teacherContextError.value = null
  const promise = (async () => {
    try {
      const requests = await Promise.allSettled([
        semester.value ? Promise.resolve(semester.value) : api.semester(),
        catalog.value ? Promise.resolve(catalog.value) : api.catalog(semesterId),
        lessonSlots.value.length ? Promise.resolve(lessonSlots.value) : api.lessonSlots().then(result => result.slots),
      ])
      if (!current()) return
      const [semesterResult, catalogResult, slotsResult] = requests
      if (semesterResult.status === 'fulfilled') semester.value = semesterResult.value
      if (catalogResult.status === 'fulfilled') catalog.value = catalogResult.value
      if (slotsResult.status === 'fulfilled') lessonSlots.value = slotsResult.value
      const rejected = requests.find(result => result.status === 'rejected')
      if (rejected?.status === 'rejected') {
        teacherContextError.value = 'Не удалось загрузить учебный контекст. Попробуй ещё раз.'
        forwardError(rejected.reason)
      }
    } finally {
      if (current()) { teacherContextLoading.value = false; contextInFlight = null }
    }
  })()
  contextInFlight = { api, semesterId, generation, promise }
  return promise
}
void loadTeacherContext()

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
watch(() => [props.host, route.value.kind, calendarOpen.value, exportOpen.value] as const, ([host, kind, calendar, exporting]) => {
  if (host?.backOwner === 'host') host.setBackVisible?.(kind === 'nested' || calendar || exporting)
}, { immediate: true, flush: 'post' })

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
  calendarCloseSignal.value += 1
  exportCloseSignal.value += 1
  calendarOpen.value = false
  exportOpen.value = false
  navigation.goRoot(routeName)
}

function backRoute(): void {
  navigation.back()
}

function openLesson(nextLessonId: number): void {
  lessonId.value = nextLessonId
  navigation.push(nestedRoute('teacher-home', 'teacher-home/lesson', 'detail'))
}

function openJournal(query: TeacherJournalQuery, returnSurface: 'home' | 'stats' = 'home', entry: 'taught' | 'history' = 'history'): void {
  journalEntry.value = entry
  journalQuery.value = { semesterId: query.semesterId, groupId: query.groupId, subjectId: query.subjectId, lessonTypes: [], pastOnly: true }
  lessonId.value = null
  const parent = returnSurface === 'stats' ? 'teacher-stats' : 'teacher-home'
  navigation.push(nestedRoute(parent, `${parent}/journal`, 'detail'))
}

function rememberAttendanceContext(query: TeacherJournalQuery): void {
  attendanceQuery.value = query
}

function openAttendanceLesson(nextLessonId: number, query: TeacherJournalQuery): void {
  attendanceQuery.value = query
  lessonId.value = nextLessonId
  navigation.push(nestedRoute('teacher-attendance', 'teacher-attendance/lesson', 'detail'))
}

function openAttendanceJournal(query: TeacherJournalQuery): void {
  attendanceQuery.value = query
  journalEntry.value = 'history'
  journalQuery.value = { semesterId: query.semesterId, groupId: query.groupId, subjectId: query.subjectId, lessonTypes: [], pastOnly: true }
  lessonId.value = null
  navigation.push(nestedRoute('teacher-attendance', 'teacher-attendance/journal', 'detail'))
}

function rememberJournalPage(page: number): void {
  if (!Number.isSafeInteger(page) || page < 0) return
  const activeQuery = journalQuery
  if (activeQuery.value && (activeQuery.value.page ?? 0) !== page) {
    activeQuery.value = { ...activeQuery.value, page }
  }
}
function openJournalLesson(nextLessonId: number, page: number): void {
  if (!route.value.id.endsWith('/journal')) return
  if (journalQuery.value) {
    journalQuery.value = { ...journalQuery.value, page }
  }
  lessonId.value = nextLessonId
  const parent = route.value.root
  navigation.push(nestedRoute(parent, `${parent}/journal/lesson`, 'detail'))
}

function openGroup(groupId: number, groupName: string): void {
  selectedGroup.value = { id: groupId, name: groupName }
  journalQuery.value = null
  navigation.push(nestedRoute('teacher-stats', 'teacher-stats/subjects', 'detail'))
  if (!catalog.value) void loadTeacherContext()
}

function openSubject(option: TeacherStatsSubjectOption): void {
  if (!props.semesterId || !selectedGroup.value || option.groupId !== selectedGroup.value.id) return
  openJournal({ semesterId: props.semesterId, groupId: option.groupId, subjectId: option.subjectId, lessonTypes: [], pastOnly: true }, 'stats', 'taught')
}

function updateJournalQuery(query: TeacherJournalQuery): void { journalQuery.value = { ...query, pastOnly: true } }

function openManualExcuse(nextLessonId: number, studentId: number): void {
  const parent = route.value.root
  if (parent !== 'teacher-home' && parent !== 'teacher-stats' && parent !== 'teacher-attendance') return
  manualLessonId.value = nextLessonId
  manualStudentId.value = studentId
  navigation.push(nestedRoute(parent, `${parent}/manual-excuse`, 'task'))
}

function openLessonExport(nextLessonId: number): void {
  if (lessonId.value === nextLessonId) exportOpenSignal.value += 1
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
  stopOverlayBack()
  stopTheme()
})
async function openNotificationAction(action: NotificationAction, isCurrent: () => boolean): Promise<boolean> {
  const api = props.api, generation = ownerGeneration.value
  if (!api || props.offline || disposed || action.role !== 'TEACHER' || action.kind !== 'lesson' || !isCurrent()) return false
  const id = notificationNumericId(action.lessonId)
  if (id === null) return false
  await api.lesson(id)
  if (!isCurrent() || disposed || api !== props.api || generation !== ownerGeneration.value || props.offline) return false
  openLesson(id)
  return true
}
defineExpose({ openNotificationAction })
</script>

<template>
  <MobileShell
    :route="route"
    :navigation="navigation"
    :nav-items="navItems"
    :active-id="route.root"
    :host="host"
    :custom-back="true"
    @navigate="navigateRoot"
  >
    <template #back />
    <div
      class="teacher-feature-owner"
      :data-host-back="host?.backOwner === 'host'"
    >
      <KeepAlive :key="`home-${ownerGeneration}`">
        <TeacherHomeScreen
          v-if="route.id === 'teacher-home'"
          :on-notifications="onNotifications"
          :unread-count="unreadCount"
          :api="api"
          :semester-id="semesterId"
          :selected-date="selectedDate"
          :lesson-slots="lessonSlots"
          :calendar-close-signal="calendarCloseSignal"
          :role-switch-enabled="profilePort !== null"
          @select-date="selectedDate = $event"
          @open-lesson="openLesson"
          @open-journal="openJournal"
          @open-stats="navigateRoot('teacher-stats')"
          @open-role-switch="navigateProfile('role-switch')"
          @error="forwardError"
          @calendar-open="calendarOpen = $event"
        />
      </KeepAlive>
      <TeacherLessonScreen
        v-if="route.id === 'teacher-home/lesson' || route.id === 'teacher-attendance/lesson' || route.id.endsWith('/journal/lesson')"
        :api="api"
        :lesson-id="lessonId"
        :show-back="true"
        :report-download="reportDownload"
        :export-open-signal="exportOpenSignal"
        :export-close-signal="exportCloseSignal"
        @back="backRoute"
        @open-excuse="openExcuse"
        @open-manual-excuse="openManualExcuse"
        @open-export="openLessonExport"
        @export-open="exportOpen = $event"
        @error="forwardError"
      />
      <KeepAlive :key="`journal-${ownerGeneration}`">
        <TeacherJournalScreen
          v-if="journalVisited"
          v-show="route.id.endsWith('/journal')"
          :api="api"
          :query="journalQuery"
          :active="route.id.endsWith('/journal')"
          :subject-options="journalSubjectOptions"
          :group-name="catalog?.groups.find(group => group.id === journalQuery?.groupId)?.name ?? ''"
          :semester="semester"
          :show-back="true"
          :report-download="reportDownload"
          @back="backRoute"
          @open-lesson="openJournalLesson"
          @change-page="rememberJournalPage"
          @update-query="updateJournalQuery"
          @open-manual-excuse="openManualExcuse"
          @open-excuse="openExcuse"
          @error="forwardError"
        />
      </KeepAlive>
      <TeacherManualExcuseScreen
        v-if="route.id.endsWith('/manual-excuse')"
        :api="api"
        :lesson-id="manualLessonId"
        :student-id="manualStudentId"
        :show-back="true"
        @back="backRoute"
        @error="forwardError"
      />
      <TeacherGroupSubjectsScreen
        v-if="route.id === 'teacher-stats/subjects' && selectedGroup && catalog"
        :group-id="selectedGroup.id"
        :group-name="selectedGroup.name"
        :subject-options="catalog.taughtSubjectOptions"
        :show-back="true"
        @back="backRoute"
        @open-subject="openSubject"
      />
      <main
        v-if="route.id === 'teacher-stats/subjects' && !catalog"
        class="teacher-screen"
        aria-labelledby="teacher-subject-context-title"
        :aria-busy="teacherContextLoading"
      >
        <header class="teacher-screen__detail-header">
          <button
            class="teacher-screen__icon-button"
            type="button"
            aria-label="Назад"
            @click="backRoute"
          >
            <MobileIcon name="back" />
          </button>
          <h1 id="teacher-subject-context-title">
            {{ selectedGroup?.name ?? 'Предметы группы' }}
          </h1>
        </header>
        <StudentWarningBlock
          v-if="teacherContextError"
          title="Предметы недоступны"
          :message="teacherContextError"
          severity="error"
          action-label="Повторить"
          :action-disabled="teacherContextLoading"
          @action="loadTeacherContext"
        />
        <p
          v-else
          class="teacher-screen__state"
          role="status"
        >
          Загружаем предметы…
        </p>
      </main>
      <TeacherExcuseScreen
        v-if="route.id.endsWith('/excuse')"
        :api="api"
        :request-id="requestId"
        :show-back="true"
        @back="backRoute"
        @error="forwardError"
      />
      <KeepAlive :key="`stats-${ownerGeneration}`">
        <TeacherStatsScreen
          v-if="route.id === 'teacher-stats'"
          :api="api"
          :semester-id="semesterId"
          :report-download="reportDownload"
          @back="navigateRoot('teacher-home')"
          @open-journal="openStatsJournal"
          @open-group="openGroup"
          @error="forwardError"
        />
      </KeepAlive>
      <MapScreen
        v-if="route.id === 'teacher-map' && mapClient"
        :client="mapClient"
        :theme="resolvedTheme"
        compact-presentation
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
          :on-recover="onRecover"
          recovery-label="Забыли пароль?"
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
      <KeepAlive :key="`attendance-${ownerGeneration}`">
        <TeacherAttendanceScreen
          v-if="attendanceVisited"
          v-show="route.id === 'teacher-attendance'"
          :api="api"
          :semester-id="semesterId"
          :initial-query="attendanceQuery"
          :active="route.id === 'teacher-attendance'"
          @change-context="rememberAttendanceContext"
          @open-lesson="openAttendanceLesson"
          @open-journal="openAttendanceJournal"
          @open-excuse="openExcuse"
          @error="forwardError"
        />
      </KeepAlive>
    </div>
  </MobileShell>
</template>

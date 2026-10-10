<script setup lang="ts">
import type { NotificationAction } from '../notifications/notifications-v2-models'
import { notificationNumericId } from '../notifications/notifications-actions'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { AcademicWeekPolicy } from '../../domain/academic-week'
import HeadmanLessonManagementScreen from '../headman-lesson-management/HeadmanLessonManagementScreen.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import { freezeRecurringCreate, readRecurringCreate, recurringCreateScope, submitRecurringCreate, type RecurringCreateIntent } from './headman-recurring-create'
import type { HeadmanLessonSlotCatalog } from './headman-schedule-client'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import MobileShell from '../../shared/components/MobileShell.vue'
import RoleSwitchDialog from '../../shared/components/RoleSwitchDialog.vue'
import type { CampusMapClient } from '../../api/map-client'
import MapScreen from '../map/MapScreen.vue'
import { createHeadmanNavigationItems } from '../../shared/mobile-navigation-items'
import {
  createMobileNavigationStack,
  nestedRoute,
  rootRoute,
  type MobileBottomNavItems,
  type MobileNavigationStack,
  type MobileRootRouteId,
  type MobileRoute,
} from '../../shared/navigation'
import type { MobileHostAdapter } from '../../shared/host'
import type { MobileThemeController, MobileThemeResolvedMode } from '../../shared/theme'
import type { ProfilePort, ProfileRole, ProfileRoute, ProfileSnapshot, ProfileTheme } from '../profile/profile-types'
import { DEFAULT_PASSWORD_POLICY, ProfileRequestError } from '../profile/profile-types'
import { ProfileState } from '../profile/profile-state'
import ProfileScreen from '../profile/ProfileScreen.vue'
import RoleSwitchScreen from '../profile/RoleSwitchScreen.vue'
import AppearanceScreen from '../profile/AppearanceScreen.vue'
import SecurityScreen from '../profile/SecurityScreen.vue'
import SessionsScreen from '../profile/SessionsScreen.vue'
import AccountHistoryScreen from '../profile/AccountHistoryScreen.vue'
import HeadmanJournalScreen from '../headman-journal/HeadmanJournalScreen.vue'
import HeadmanLessonActionsScreen from '../headman-journal/HeadmanLessonActionsScreen.vue'
import type { HeadmanLessonActionsApi } from '../headman-journal/headman-journal-actions-client'
import type { HeadmanJournalApi, HeadmanJournalLesson } from '../headman-journal/headman-journal-client'
import HeadmanRequestsScreen from '../headman-requests/HeadmanRequestsScreen.vue'
import type { HeadmanRequestsApi } from '../headman-requests/headman-requests-client'
import HeadmanGroupScreen from '../headman-group/HeadmanGroupScreen.vue'
import type { HeadmanGroupApi } from '../headman-group/headman-group-client'
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client'
import HeadmanSubjectsScreen from '../headman-subjects/HeadmanSubjectsScreen.vue'
import type { HeadmanSubjectsApi } from '../headman-subjects/headman-subjects-client'
import HeadmanStatsScreen from '../headman-stats/HeadmanStatsScreen.vue'
import type { HeadmanStatsApi } from '../headman-stats/headman-stats-client'
import HeadmanHomeScreen from '../headman-home/HeadmanHomeScreen.vue'
import HeadmanMoreScreen from '../headman-home/HeadmanMoreScreen.vue'
import HeadmanHomeworkScreen from '../homework/HeadmanHomeworkScreen.vue'
import homeworkBackIcon from '../../assets/headman-homework-previous.svg'
import type { HeadmanHomeworkApi } from '../homework/headman-homework-client'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import { profileOwnerStaleMessage } from '../../shared/profile-owner-status'
import { createProfileViewPublication } from '../../shared/components/profile-view-publication'
import {
  HeadmanScheduleApiError,
  canCorrectRejectedOneOff,
  isOneOffDateWithinSemester,
  persistOneOffIntent,
  readOneOffIntent,
  type HeadmanOneOffCreateInput,
  type HeadmanOneOffIntent,
  type HeadmanOneOffLesson,
  type HeadmanScheduleApi,
  type HeadmanScheduleAssignment,
  type HeadmanScheduleCreateInput,
  type HeadmanScheduleItem,
  type HeadmanScheduleSemester,
} from './headman-schedule-client'
import './headman-schedule-screen.pcss'
import { hideDeniedHeadmanSchedule, persistRecurringIntent, readRecurringIntent, recurringScope, recurringUpdate, retainRecurringBack, type RecurringIntent, type RecurringOwner } from './headman-recurring-intent'
import type { HeadmanLifecyclePreview, HeadmanScheduleUpdateInput } from './headman-schedule-client'

const props = withDefaults(defineProps<{
  unreadCount?: number | null | undefined
  onNotifications?: (() => void) | undefined
  api: HeadmanScheduleApi | null
  journalApi?: HeadmanJournalApi | null
  lessonActionsApi?: HeadmanLessonActionsApi | null
  requestsApi?: HeadmanRequestsApi | null
  groupApi?: HeadmanGroupApi | null
  subjectsApi?: HeadmanSubjectsApi | null
  statsApi?: HeadmanStatsApi | null
  assistantPermissions?: readonly HeadmanAssistantPermission[] | null
  profile: ProfileSnapshot | null
  groupId: number | null
  mapClient?: CampusMapClient | null
  offline?: boolean
  readOnly?: boolean
  host?: MobileHostAdapter | null
  navItems?: MobileBottomNavItems
  onRoleSwitch?: (() => void | Promise<void>) | undefined
  reportDownload?: ReportDownloadPort | null
  homeworkApi?: HeadmanHomeworkApi | null
  homeworkActorUserId?: number | null
  openMaterial?: ((url: string) => void) | undefined
  profilePort?: ProfilePort | null
  profileRoleSelect?: ((role: ProfileRole, expectedSessionVersion: string) => void | Promise<void>) | undefined
  themeController?: MobileThemeController | null
  onLogout?: (() => void | Promise<void>) | undefined
  onRecover?: (() => void | Promise<void>) | undefined
  selectedDate?: string
}>(), { unreadCount: null, onNotifications: undefined,
  journalApi: null,
  lessonActionsApi: null,
  requestsApi: null,
  groupApi: null,
  subjectsApi: null,
  statsApi: null,
  assistantPermissions: null,
  mapClient: null,
  offline: false,
  readOnly: false,
  host: null,
  navItems: undefined as never,
  onRoleSwitch: undefined,
  reportDownload: null,
  homeworkApi: null,
  homeworkActorUserId: null,
  openMaterial: undefined,
  profilePort: null,
  onLogout: undefined,
  onRecover: undefined,
  profileRoleSelect: undefined,
  themeController: null,
  selectedDate: '',
})

const emit = defineEmits<{
  error: [cause: unknown]
}>()

const navigation: MobileNavigationStack = createMobileNavigationStack(rootRoute('headman-today'))
const route = shallowRef<MobileRoute>(navigation.current)
const navigationItems = computed(() => props.navItems ?? createHeadmanNavigationItems(
  props.profilePort !== null || props.profile !== null || props.onRoleSwitch !== undefined,
))
const loading = ref(true)
const error = ref<string | null>(null)
const denied = ref(false)
const assignments = shallowRef<readonly HeadmanScheduleAssignment[]>([])
const scheduleItems = shallowRef<readonly HeadmanScheduleItem[]>([])
const oneOffLessons = shallowRef<readonly HeadmanOneOffLesson[]>([])
const createMode = ref<'RECURRING' | 'ONE_OFF'>('RECURRING')
const oneOffDate = ref(moscowToday())
const oneOffIntent = shallowRef<HeadmanOneOffIntent | null>(null)
const intentRecoveryBlocked = ref(false)
const editTarget = shallowRef<HeadmanScheduleItem | null>(null)
const recurringCreateIntent = shallowRef<RecurringCreateIntent | null>(null)
const slotCatalog = shallowRef<HeadmanLessonSlotCatalog | null>(null)
const slotPickerOpen = ref(false)
const constructorSubjectId = ref<number | null>(null)
const lessonManagementScreen = ref<InstanceType<typeof HeadmanLessonManagementScreen> | null>(null)
const managementView = ref('agenda')
const requestsScreen = ref<InstanceType<typeof HeadmanRequestsScreen> | null>(null)
const requestsView = ref<'queue'|'archive'|'detail'|'approve'|'reject'|'exclude'>('queue')
const recurringIntent = shallowRef<RecurringIntent | null>(null)
const lifecyclePreview = shallowRef<{ action: 'UPDATE' | 'DELETE'; input: Omit<HeadmanScheduleUpdateInput, 'expectedRevision'> | null; result: HeadmanLifecyclePreview } | null>(null)
const recurringLocked = computed(() => formBusy.value || (recurringIntent.value !== null && !recurringIntent.value.rejected))
const headmanOwner = computed(() => props.profile?.activeRole === 'HEADMAN' && props.assistantPermissions===null && !props.readOnly && !denied.value)
const formLocked = computed(() => formBusy.value || recurringCreateIntent.value !== null || (createMode.value === 'ONE_OFF' && oneOffIntent.value !== null))
const semester = shallowRef<HeadmanScheduleSemester | null>(null)
const selectedDay = ref(1)
const formOpen = ref(false)
const journalOpen = ref(false)
const requestsOpen = ref(false)
const groupScreen = ref<InstanceType<typeof HeadmanGroupScreen> | null>(null)
const groupOpen = ref(false)
const subjectsScreen = ref<InstanceType<typeof HeadmanSubjectsScreen> | null>(null)
const subjectsOpen = ref(false)
const statsOpen = ref(false)
const statsView = ref<'overview' | 'detail' | 'history' | 'trend' | 'tickets' | 'subject'>('overview')
const statsScreen = ref<{ back(): boolean } | null>(null)
const scheduleOpen = ref(false)
const formBusy = ref(false)
const formError = ref<string | null>(null)
const notice = ref<string | null>(null)
const commandKey = ref<string | null>(null)
const assignmentId = ref<number | null>(null)
const weekPolicy = shallowRef<AcademicWeekPolicy | null>(null)
const weekPolicyError = ref<string | null>(null)
let weekPolicyRevision = 0
type ConstructorWeek = 'ALL' | 'ODD' | 'EVEN'
const constructorWeek = ref<ConstructorWeek>('ODD')
const selectedWeekTypes = ref<readonly ConstructorWeek[]>([])
const parityOptions = computed(() => weekPolicy.value?.weekTypes.filter((option) => option.parityNumber !== null) ?? [])
const weekType = computed<ConstructorWeek>({
  get: () => selectedWeekTypes.value.length === 1 ? selectedWeekTypes.value[0]! : 'ALL',
  set: (value) => { selectedWeekTypes.value = value === 'ALL' ? ['ODD', 'EVEN'] : [value] },
})
function toggleConstructorWeek(value: ConstructorWeek): void {
  if (formLocked.value || recurringLocked.value || props.offline || props.readOnly || !headmanOwner.value) return
  selectedWeekTypes.value = selectedWeekTypes.value.includes(value) ? selectedWeekTypes.value.filter((week) => week !== value) : [...selectedWeekTypes.value, value]
}
watch(parityOptions, (options) => { if (!options.some((option) => option.weekType === constructorWeek.value)) constructorWeek.value = options[0]?.weekType ?? 'ODD' })
const lessonNumber = ref<number | null>(null)
const startTime = ref('')
const endTime = ref('')
const room = ref('')
const commandFingerprint = ref<string | null>(null)
const actionLogLesson = shallowRef<HeadmanJournalLesson | null>(null)
const actionLogSubject = ref('Занятие группы')
const actionLogRouteId = 'headman-today/actions' as const
const homeworkStartCreate = ref(false)
const homeworkView = ref<'calendar' | 'archive' | 'detail' | 'wizard' | 'history'>('calendar')
const homeworkScreen = ref<InstanceType<typeof HeadmanHomeworkScreen> | null>(null)
const journalTaskOpen = ref(false)
const journalScreen = ref<InstanceType<typeof HeadmanJournalScreen> | null>(null)
const journalRouteId = 'headman-attendance/lesson' as const
const requestsRouteId = 'headman-requests' as const
const groupRouteId = 'headman-more/group' as const
const subjectsRouteId = 'headman-more/subjects' as const
const statsRouteId = 'headman-more/stats' as const
const homeworkRouteId = 'headman-more/homework' as const
const lessonManagementRouteId = 'headman-more/lessons' as const
const scheduleRouteId = 'headman-more/schedule/list' as const
const scheduleFormRouteId = 'headman-more/schedule/form' as const
const selectedDate = ref(props.selectedDate || moscowToday())
const selectedLessonId = ref<number | null>(null)
const profileState = shallowRef(props.profilePort ? new ProfileState(props.profilePort) : null)
const homeRoleDialogOpen = ref(false)
const profilePendingRole = ref<ProfileRole | null>(null)
const profileRoleError = shallowRef<ProfileRequestError | null>(null)
const themeMode = ref<ProfileTheme>(props.themeController?.mode ?? 'system')
const resolvedTheme = ref<MobileThemeResolvedMode>(props.themeController?.resolvedMode ?? 'dark')
const profilePublication = createProfileViewPublication(
  () => profileState.value?.view ?? null,
  () => disposed,
)
const profileView = profilePublication.view
const publishProfileView = profilePublication.publish
const profileOwnerStatus = computed(() => profileOwnerStaleMessage(route.value, props.offline))
const homeRoleSwitchDisabled = computed(() => props.offline || props.profile === null
  || (props.profilePort === null && props.profileRoleSelect === undefined && props.onRoleSwitch === undefined))
const moreAvailability = computed(() => ({
  stats: props.statsApi !== null && props.groupId !== null,
  homework: props.homeworkApi !== null && props.journalApi !== null && props.groupId !== null
    && Number.isSafeInteger(props.homeworkActorUserId) && (props.homeworkActorUserId ?? 0) > 0,
  map: props.mapClient !== null,
  group: props.groupApi !== null && props.groupId !== null,
  subjects: props.subjectsApi !== null && props.groupId !== null,
  schedule: props.api !== null && props.groupId !== null,
  lessons: props.journalApi !== null && props.groupId !== null,
}))
let loadRevision = 0
let commandRevision = 0
let disposed = false
let stopTheme = (): void => undefined
const stopRecurringBackGuard = navigation.beforeBack((current) => {
  if (current.id !== scheduleFormRouteId || !retainRecurringBack(denied.value, formBusy.value || recurringCreateIntent.value !== null, recurringIntent.value)) return true
  formError.value = formBusy.value ? 'Дождись ответа сервера перед возвращением назад.'
    : 'Результат изменения ещё не подтверждён. Повтори исходный запрос.'
  return false
})
const stopJournalBackGuard = navigation.beforeBack(() => !journalOpen.value || !journalScreen.value?.back())
const stopManagementBackGuard = navigation.beforeBack((current) => current.id !== lessonManagementRouteId || !lessonManagementScreen.value?.back())
const stopRequestsBackGuard = navigation.beforeBack((current) => current.id !== requestsRouteId || !requestsScreen.value?.back())
const stopStatsBackGuard = navigation.beforeBack((current) => {
  if (current.id !== statsRouteId) return true
  return !statsScreen.value?.back()
})
const stopSubjectsBackGuard = navigation.beforeBack((current) => current.id !== subjectsRouteId || !subjectsScreen.value?.back())
const stopGroupBackGuard = navigation.beforeBack((current) => current.id !== groupRouteId || !groupScreen.value?.back())
const stopHomeworkBackGuard = navigation.beforeBack((current) => current.id !== homeworkRouteId || !homeworkScreen.value?.back())
function handleShellBack(): void {
  // Requests owns child views inside a root stack entry; root back guards do not run there.
  if (requestsOpen.value) { requestsScreen.value?.back(); return }
  if (journalTaskOpen.value) journalScreen.value?.back()
}
const shellRoute = computed(() => route.value.id === scheduleRouteId
  ? nestedRoute('headman-more',scheduleRouteId,'overview')
  : route.value.id === lessonManagementRouteId
  ? nestedRoute('headman-more', lessonManagementRouteId, managementView.value === 'agenda' ? 'overview' : 'editor')
  : requestsOpen.value && !['queue','archive'].includes(requestsView.value)
    ? nestedRoute('headman-requests', 'headman-requests/detail', 'editor')
  : route.value.id === homeworkRouteId
  ? nestedRoute('headman-more', homeworkRouteId, homeworkView.value === 'calendar' ? 'overview' : 'editor')
  : journalTaskOpen.value
  ? nestedRoute('headman-attendance', 'headman-attendance/reason', 'editor')
  : statsOpen.value && statsView.value !== 'overview'
    ? nestedRoute('headman-more', statsRouteId, 'detail')
    : route.value)
watch(statsOpen, (open) => { if (!open) statsView.value = 'overview' })
let stopNavigation = navigation.subscribe(() => {
  const next = navigation.current
  route.value = next
  journalOpen.value = next.root === 'headman-attendance'
  requestsOpen.value = next.id === requestsRouteId
  groupOpen.value = next.id === groupRouteId
  subjectsOpen.value = next.id === subjectsRouteId
  statsOpen.value = next.id === statsRouteId
  scheduleOpen.value = next.id === scheduleRouteId || next.id === scheduleFormRouteId
  formOpen.value = next.id === scheduleFormRouteId
  ensureProfileRoute(next)
})

const days = [
  { value: 1, label: 'Пн', name:'Понедельник', accusative:'понедельник' },
  { value: 2, label: 'Вт', name:'Вторник', accusative:'вторник' },
  { value: 3, label: 'Ср', name:'Среда', accusative:'среду' },
  { value: 4, label: 'Чт', name:'Четверг', accusative:'четверг' },
  { value: 5, label: 'Пт', name:'Пятница', accusative:'пятницу' },
  { value: 6, label: 'Сб', name:'Суббота', accusative:'субботу' },
  { value: 7, label: 'Вс', name:'Воскресенье', accusative:'воскресенье' },
] as const

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
  } catch (cause) {
    if (options.rethrow) throw cause
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

async function openProfileArea(area: 'sessions' | 'history'): Promise<void> {
  const state = profileState.value
  if (!state || disposed) return
  if (!state.view.snapshot) {
    await runProfile((current) => current.loadSnapshot())
    if (disposed || !state.view.snapshot) return
  }
  if (area === 'sessions') await runProfile((current) => current.loadAllSessions(publishProfileView))
  else await runProfile((current) => current.loadHistory())
}

function profileRoute(routeName: Extract<ProfileRoute, 'role-switch' | 'appearance' | 'security' | 'sessions' | 'history'>) {
  if (routeName === 'role-switch') return nestedRoute('profile', 'profile/role-switch', 'detail')
  if (routeName === 'appearance') return nestedRoute('profile', 'profile/appearance', 'detail')
  if (routeName === 'security') return nestedRoute('profile', 'profile/security', 'detail')
  if (routeName === 'sessions') return nestedRoute('profile', 'profile/sessions', 'detail')
  return nestedRoute('profile', 'profile/history', 'detail')
}

function navigateProfile(routeName: ProfileRoute, options: { preserveHistory?: boolean } = {}): void {
  if (routeName === 'profile') {
    navigation.goRoot('profile')
    return
  }
  if (routeName === 'role-switch' || routeName === 'appearance' || routeName === 'security'
    || routeName === 'sessions' || routeName === 'history') {
    profileRoleError.value = null
    navigation.push(profileRoute(routeName), options)
  }
}

function openHomeRoleSwitch(): void {
  if (disposed || homeRoleSwitchDisabled.value) return
  profileRoleError.value = null
  homeRoleDialogOpen.value = true
  if (!profileView.value.snapshot) void loadProfileSnapshot()
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
  const snapshot = profileView.value.snapshot ?? props.profile
  if (disposed || props.offline || profilePendingRole.value !== null || !snapshot || snapshot.sessionVersion !== expectedSessionVersion
    || !snapshot.roles.some((grant) => grant.role === role && grant.selectable)) return
  const state = profileState.value
  if (!state && !props.profileRoleSelect) {
    await props.onRoleSwitch?.()
    return
  }
  profilePendingRole.value = role
  profileRoleError.value = null
  try {
    if (props.profileRoleSelect) await props.profileRoleSelect(role, expectedSessionVersion)
    else await runProfile((current) => current.selectRole(role), { rethrow: true })
    if (!disposed) homeRoleDialogOpen.value = false
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

function loadMoreProfile(area: 'sessions' | 'history', cursor: string): void {
  const state = profileState.value
  if (!state || disposed) return
  if (area === 'sessions') void runProfile((current) => current.loadSessions({ cursor }))
  else void runProfile((current) => current.loadHistory({ cursor }))
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
  emit('error', cause)
}

function handleTaskBack(): void {
  navigation.back()
}

function navigateRoot(routeName: MobileRootRouteId): void {
  if (routeName !== 'headman-today' && routeName !== 'headman-attendance' && routeName !== 'headman-requests'
    && routeName !== 'headman-more' && routeName !== 'headman-map' && routeName !== 'profile') return
  if (routeName !== 'headman-attendance') selectedLessonId.value = null
  navigation.goRoot(routeName)
}

async function openHomeLog(lesson: HeadmanJournalLesson): Promise<void> {
  if (!props.lessonActionsApi || props.offline) return
  actionLogLesson.value = lesson
  actionLogSubject.value = 'Занятие группы'
  navigation.push(nestedRoute('headman-today', actionLogRouteId, 'detail'))
  const api = props.subjectsApi
  const groupId = props.groupId
  if (!api || groupId === null) return
  try {
    const subjects = await api.listSubjects(groupId)
    if (!disposed && api === props.subjectsApi && groupId === props.groupId && actionLogLesson.value === lesson) actionLogSubject.value = subjects.find((subject) => subject.id === lesson.subjectId && subject.groupId === groupId)?.name ?? 'Занятие группы'
  } catch (cause) { if (!(cause instanceof StaleSessionGenerationError)) forwardError(cause) }
}

function openHomeLesson(lesson: HeadmanJournalLesson): void {
  selectedDate.value = lesson.date
  selectedLessonId.value = lesson.id
  navigation.goRoot('headman-attendance')
  navigation.push(nestedRoute('headman-attendance', journalRouteId, 'detail'))
}


function openGroup(): void {
  if (!props.groupApi || props.groupId === null) return
  navigation.push(nestedRoute('headman-more', groupRouteId, 'detail'))
}

function openSubjects(): void {
  if (!props.subjectsApi || props.groupId === null) return
  navigation.push(nestedRoute('headman-more', subjectsRouteId, 'detail'))
}

function openStats(): void {
  if (!props.statsApi || props.groupId === null) return
  navigation.push(nestedRoute('headman-more', statsRouteId, 'detail'))
}

function openHomework(startCreate = false): void {
  homeworkStartCreate.value = startCreate
  if (!props.homeworkApi || !props.journalApi || props.groupId === null
    || !Number.isSafeInteger(props.homeworkActorUserId) || (props.homeworkActorUserId ?? 0) <= 0) return
  navigation.push(nestedRoute('headman-more', homeworkRouteId, 'task'))
}

function openSchedule(): void {
  if (!props.api || props.groupId === null) return
  navigation.push(nestedRoute('headman-more', scheduleRouteId, 'overview'))
}

function openLessonManagement(): void {
  if (!props.journalApi || props.groupId === null) return
  selectedDate.value = moscowToday()
  selectedLessonId.value = null
  navigation.push(nestedRoute('headman-more', lessonManagementRouteId, 'task'))
}

function selectMore(destination: 'stats' | 'homework' | 'map' | 'group' | 'subjects' | 'schedule' | 'lessons'): void {
  if (!moreAvailability.value[destination]) return
  if (destination === 'map') {
    openMap()
  } else if (destination === 'stats') {
    openStats()
  } else if (destination === 'homework') {
    openHomework()
  } else if (destination === 'group') {
    openGroup()
  } else if (destination === 'subjects') {
    openSubjects()
  } else if (destination === 'schedule') {
    openSchedule()
  } else {
    openLessonManagement()
  }
}

function openMap(): void {
  if (!props.mapClient) return
  navigateRoot('headman-map')
}

const activeAssignments = computed(() => assignments.value.filter((item) => item.id !== undefined && item.subjectId !== undefined))
const constructorSubjects = computed(() => [...new Map(activeAssignments.value.map((a) => [a.subjectId,a])).values()])
const constructorAssignments = computed(() => activeAssignments.value.filter((a) => a.subjectId === constructorSubjectId.value && !!a.lessonType && !!a.teacherId))
function constructorSlotAvailable(number: number): boolean { return !dayItems.value.some((item) => item.id !== editTarget.value?.id && item.lessonNumber === number && (item.weekType === weekType.value || item.weekType === 'ALL' || weekType.value === 'ALL')) }
function selectConstructorSlot(number: number): void { const slot=slotCatalog.value?.slots.find((s)=>s.lessonNumber===number); if (!slot || formLocked.value || !constructorSlotAvailable(number)) return; lessonNumber.value=slot.lessonNumber;startTime.value=slot.startTime.slice(0,5);endTime.value=slot.endTime.slice(0,5);slotPickerOpen.value=false }
function scheduleTypeName(type: string | undefined): string { return type==='LECTURE'?'Лекция':type==='PRACTICE'?'Практика':type==='LAB'?'Лабораторная':'Тип не указан' }
watch(constructorAssignments,(options)=>{ if (!formLocked.value && !options.some((a)=>a.id===assignmentId.value)) assignmentId.value=options[0]?.id ?? null })
const selectedAssignment = computed(() => activeAssignments.value.find((item) => item.id === assignmentId.value) ?? null)
const dayItems = computed(() => scheduleItems.value
  .filter((item) => item.dayOfWeek === selectedDay.value)
  .slice()
  .sort((left, right) => (left.lessonNumber ?? 0) - (right.lessonNumber ?? 0)))
const visibleDayItems = computed(() => dayItems.value.filter((item) => item.weekType === 'ALL' || item.weekType === constructorWeek.value))
const stateMessage = computed(() => {
  if (props.offline) return 'Изменения доступны только при подключении к интернету.'
  if (denied.value) return 'Нет доступа к расписанию этой группы.'
  if (semester.value === null && !loading.value) return 'Нет активного семестра для заполнения расписания.'
  if (activeAssignments.value.length === 0 && scheduleItems.value.length===0 && !loading.value) return 'Для группы пока нет назначений преподавателей.'
  return null
})

async function loadWeekPolicy(): Promise<void> {
  const run = ++weekPolicyRevision; const api = props.api; const group = props.groupId
  weekPolicy.value = null; weekPolicyError.value = null
  if (!api || props.offline || group === null) return
  const current = () => !disposed && run === weekPolicyRevision && api === props.api && group === props.groupId && !props.offline
  try { const policy = await api.academicWeekPolicy(moscowToday()); if (current()) weekPolicy.value = policy }
  catch (cause) { if (!current() || cause instanceof StaleSessionGenerationError) return; weekPolicyError.value = 'Не удалось загрузить чётность недель.'; forwardError(cause) }
}

function newCommandKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  throw new Error('Для изменения нужен UUID. Обнови браузер.')
}

function openForm(): void {
  if (!headmanOwner.value || denied.value || props.offline || props.readOnly || loading.value || semester.value === null || activeAssignments.value.length === 0) return
  if (recurringIntent.value) { restoreRecurringEditor(recurringIntent.value); return }
  if (recurringCreateIntent.value) { restoreRecurringCreateEditor(recurringCreateIntent.value); return }
  editTarget.value = null
  lifecyclePreview.value = null
  formError.value = null
  notice.value = null
  commandKey.value = newCommandKey()
  commandFingerprint.value = null
  constructorSubjectId.value = activeAssignments.value[0]?.subjectId ?? null
  assignmentId.value = constructorAssignments.value[0]?.id ?? null
  weekType.value = constructorWeek.value
  lessonNumber.value = null
  startTime.value = ''
  endTime.value = ''
  room.value = ''
  createMode.value = 'RECURRING'
  oneOffDate.value = selectedDate.value
  oneOffIntent.value = null
  intentRecoveryBlocked.value = false
  try {
    const scope = oneOffScope()
    if (scope) {
      const saved = readOneOffIntent(sessionStorage, scope)
      if (saved) {
        if (saved.input.groupId !== props.groupId) throw new Error('Сохранённый запрос относится к другой группе.')
        oneOffIntent.value = saved
        createMode.value = 'ONE_OFF'
        assignmentId.value = saved.input.assignmentId
        oneOffDate.value = saved.input.date
        lessonNumber.value = saved.input.lessonNumber
        startTime.value = saved.input.startTime.slice(0, 5)
        endTime.value = saved.input.endTime.slice(0, 5)
        room.value = saved.input.classroom ?? ''
      }
    }
  } catch {
    intentRecoveryBlocked.value = true
    formError.value = 'Не удалось прочитать сохранённый запрос. Проверь доступ к хранилищу браузера.'
  }
  formOpen.value = true
  navigation.push(nestedRoute('headman-more', scheduleFormRouteId, 'editor'))
}

function closeForm(): void {
  if (formBusy.value) return
  navigation.back()
}

function oneOffScope(): string | null {
  const profile = props.profile
  return profile && props.groupId !== null
    ? `rct:oneoff-create:${profile.userId}:${profile.sessionId}:${profile.activeRole}:${props.groupId}` : null
}

function buildInput(): HeadmanScheduleCreateInput | HeadmanOneOffCreateInput | null {
  const selected = selectedAssignment.value
  const currentSemester = semester.value
  if (!props.groupId || !currentSemester?.id || !selected?.id || !selected.subjectId) {
    formError.value = 'Не удалось определить группу, семестр или предмет.'
    return null
  }
  const slot=slotCatalog.value?.slots.find((slot)=>slot.lessonNumber===lessonNumber.value)
  if (!slot || !constructorSlotAvailable(slot.lessonNumber)) { formError.value='Выбери свободную пару из каталога сервера.'; return null }
  startTime.value=slot.startTime.slice(0,5);endTime.value=slot.endTime.slice(0,5)
  if (room.value.trim().length>64) { formError.value='Аудитория — до 64 символов.';return null }
  if (createMode.value === 'ONE_OFF') {
    if (!isOneOffDateWithinSemester(oneOffDate.value, moscowToday(), currentSemester.dateFrom, currentSemester.dateTo)) {
      formError.value = 'Выбери дату в текущем семестре, не раньше сегодня.'
      return null
    }
    if (slot.lessonNumber > 8 || room.value.trim().length > 64) {
      formError.value = 'Номер разовой пары — от 1 до 8, аудитория — до 64 символов.'
      return null
    }
    return {
      assignmentId: selected.id, groupId: props.groupId, subjectId: selected.subjectId,
      date: oneOffDate.value, lessonNumber: slot.lessonNumber,
      startTime: `${startTime.value}:00`, endTime: `${endTime.value}:00`,
      ...(room.value.trim() ? { classroom: room.value.trim() } : {}),
    }
  }
  if (!weekPolicy.value) { formError.value = 'Дождись загрузки чётности недель.'; return null }
  return {
    assignmentId: selected.id,
    groupId: props.groupId,
    subjectId: selected.subjectId,
    semesterId: currentSemester.id,
    dayOfWeek: selectedDay.value,
    lessonNumber: slot.lessonNumber,
    startTime: `${startTime.value}:00`,
    endTime: `${endTime.value}:00`,
    weekType: weekType.value,
    ...(room.value.trim() ? { room: room.value.trim() } : {}),
  }
}

async function save(): Promise<void> {
  if (!headmanOwner.value || denied.value) return
  if (editTarget.value) {
    if (recurringIntent.value && !recurringIntent.value.rejected) await applyRecurring()
    else if (lifecyclePreview.value) await applyRecurring()
    else { await previewRecurring('UPDATE'); if (lifecyclePreview.value) await applyRecurring() }
    return
  }
  if (!selectedWeekTypes.value.length) { formError.value = 'Выбери хотя бы одну неделю.'; return }
  if (formBusy.value || intentRecoveryBlocked.value || props.offline || props.readOnly || !props.api) return
  formError.value = null
  const api = props.api
  const revision = commandRevision
  const mode = createMode.value
  const firstOneOffAttempt = mode === 'ONE_OFF' && oneOffIntent.value === null
  const scope = oneOffScope()
  const isCurrent = (): boolean => !disposed && !props.offline && headmanOwner.value && revision === commandRevision && api === props.api
  const firstRecurringAttempt = mode==='RECURRING' && recurringCreateIntent.value===null
  const input = mode === 'ONE_OFF' && oneOffIntent.value ? oneOffIntent.value.input : recurringCreateIntent.value?.input ?? buildInput()
  if (!input) return
  const nextFingerprint = JSON.stringify(input)
  if (commandFingerprint.value !== null && commandFingerprint.value !== nextFingerprint) commandKey.value = newCommandKey()
  commandFingerprint.value = nextFingerprint
  const key = commandKey.value ?? newCommandKey()
  commandKey.value = key
  formBusy.value = true
  try {
    if (mode === 'ONE_OFF') {
      if (!scope) throw new Error('Для сохранения разовой пары нужна текущая сессия.')
      const saved = oneOffIntent.value ?? { key, input: Object.freeze({ ...input }) as HeadmanOneOffCreateInput }
      persistOneOffIntent(sessionStorage, scope, saved)
      oneOffIntent.value = saved
      const created = await api.createOneOffLesson(saved.input, saved.key)
      if (!isCurrent()) return
      sessionStorage.removeItem(scope)
      oneOffIntent.value = null
      selectedDate.value = created.date
      selectedLessonId.value = created.physicalLessonId
      notice.value = 'Разовая пара создана.'
    } else {
      const owner=currentRecurringOwner(); if (!owner) throw new Error('Для сохранения нужна текущая сессия старосты.')
      const intent=recurringCreateIntent.value ?? freezeRecurringCreate(owner,key,input as HeadmanScheduleCreateInput)
      // Freeze memory too before the durable write; storage refusal cannot reach POST.
      recurringCreateIntent.value=intent
      await submitRecurringCreate(sessionStorage,intent,(payload,key)=>api.createScheduleItem(payload,key))
      if (!isCurrent()) return
      sessionStorage.removeItem(recurringCreateScope(owner));recurringCreateIntent.value=null
      notice.value = 'Слот сохранён. Расписание обновлено с сервера.'
    }
    commandKey.value = null
    commandFingerprint.value = null
    formBusy.value = false
    closeForm()
    await load()
  } catch (cause) {
    if (!isCurrent() || cause instanceof StaleSessionGenerationError) return
    if (handleScheduleDenied(cause)) return
    if (mode==='RECURRING' && canCorrectRejectedOneOff(cause,firstRecurringAttempt)) {
      const owner=currentRecurringOwner(); if (owner) { try { sessionStorage.removeItem(recurringCreateScope(owner)); recurringCreateIntent.value=null; commandKey.value=newCommandKey() } catch { intentRecoveryBlocked.value=true } }
    }
    if (scope && canCorrectRejectedOneOff(cause, firstOneOffAttempt)) {
      try {
        // Only this completed first response proves no acceptance. A retry/reloaded
        // intent may still have a delayed original request, even with the same refusal.
        sessionStorage.removeItem(scope)
        oneOffIntent.value = null
        commandKey.value = newCommandKey()
        commandFingerprint.value = null
        formError.value = `${cause instanceof Error ? cause.message : 'Пара не создана.'} Исправь данные и сохрани ещё раз.`
      } catch {
        formError.value = 'Пара не создана, но не удалось обновить сохранённый запрос. Восстанови доступ к хранилищу и повтори.'
      }
      emit('error', cause)
      return
    }
    formError.value = cause instanceof Error ? cause.message : 'Не удалось сохранить слот.'
    emit('error', cause)
  } finally {
    if (isCurrent()) formBusy.value = false
  }
}

function restoreRecurringCreateEditor(intent: RecurringCreateIntent): void {
  recurringCreateIntent.value=intent; editTarget.value=null; createMode.value='RECURRING';assignmentId.value=intent.input.assignmentId;constructorSubjectId.value=intent.input.subjectId
  selectedDay.value=intent.input.dayOfWeek;weekType.value=intent.input.weekType;lessonNumber.value=intent.input.lessonNumber;startTime.value=intent.input.startTime.slice(0,5);endTime.value=intent.input.endTime.slice(0,5);room.value=intent.input.room ?? ''
  formError.value='Сохранён исходный запрос создания. Повтори его для проверки результата.';navigation.push(nestedRoute('headman-more',scheduleFormRouteId,'editor'))
}

function currentRecurringOwner(): RecurringOwner | null {
  const profile = props.profile
  return headmanOwner.value && profile && props.groupId !== null && semester.value?.id
    ? { userId: profile.userId, sessionId: profile.sessionId, groupId: props.groupId, semesterId: semester.value.id } : null
}

function handleScheduleDenied(cause: unknown): boolean {
  const hidden = hideDeniedHeadmanSchedule(cause, {
    denied, busy: formBusy,
    rows: [assignments, scheduleItems, oneOffLessons],
    editor: [recurringCreateIntent, editTarget, recurringIntent, lifecyclePreview, oneOffIntent, semester, formError, error, notice, commandKey, commandFingerprint],
  })
  if (!hidden) return false
  commandRevision += 1
  loadRevision += 1
  loading.value = false
  room.value = ''
  startTime.value = ''
  endTime.value = ''
  assignmentId.value = null
  lessonNumber.value = null
  emit('error', cause)
  return true
}

function restoreRecurringEditor(intent: RecurringIntent): void {
  recurringIntent.value = intent
  editTarget.value = intent.target
  lifecyclePreview.value = null
  constructorSubjectId.value = intent.target.subjectId ?? null
  assignmentId.value = intent.target.assignmentId ?? null
  lessonNumber.value = intent.target.lessonNumber ?? null
  startTime.value = intent.target.startTime?.slice(0,5) ?? ''
  endTime.value = intent.target.endTime?.slice(0,5) ?? ''
  weekType.value = intent.input?.weekType ?? intent.target.weekType ?? 'ALL'
  room.value = intent.input?.room ?? intent.target.room ?? ''
  selectedDay.value = intent.target.dayOfWeek ?? 1
  formError.value = intent.rejected ? 'Запрос отклонён. Обнови расчёт и подтверди изменения заново.'
    : 'Сохранён исходный запрос. Повтори его, чтобы проверить результат.'
  navigation.push(nestedRoute('headman-more', scheduleFormRouteId, 'editor'))
}

function editRecurring(item: HeadmanScheduleItem): void {
  if (!headmanOwner.value || props.offline || loading.value || formBusy.value || !item.id) return
  if (recurringIntent.value) { restoreRecurringEditor(recurringIntent.value); return }
  if (item.groupId !== props.groupId || item.semesterId !== semester.value?.id) return
  editTarget.value = Object.freeze({ ...item })
  createMode.value = 'RECURRING'
  lifecyclePreview.value = null
  formError.value = null
  notice.value = null
  constructorSubjectId.value = item.subjectId ?? null
  assignmentId.value = item.assignmentId ?? null
  lessonNumber.value = item.lessonNumber ?? null
  startTime.value = item.startTime?.slice(0,5) ?? ''
  endTime.value = item.endTime?.slice(0,5) ?? ''
  weekType.value = item.weekType ?? 'ALL'
  room.value = item.room ?? ''
  navigation.push(nestedRoute('headman-more', scheduleFormRouteId, 'editor'))
}

watch(() => [weekType.value, room.value] as const, () => { lifecyclePreview.value = null }, { flush: 'sync' })

async function previewRecurring(action: 'UPDATE' | 'DELETE'): Promise<void> {
  const api = props.api
  const target = editTarget.value
  const owner = currentRecurringOwner()
  if (!selectedWeekTypes.value.length) { formError.value = 'Выбери хотя бы одну неделю.'; return }
  if (!api || !target?.id || !owner || props.offline || recurringLocked.value || intentRecoveryBlocked.value) return
  const revision = commandRevision
  const isCurrent = (): boolean => !disposed && !props.offline && headmanOwner.value && commandRevision === revision && api === props.api && editTarget.value === target
  formError.value = null
  lifecyclePreview.value = null
  formBusy.value = true
  try {
    const input = action === 'DELETE' ? null : recurringUpdate(target, weekType.value, room.value)
    const result = await api.previewScheduleItem(target.id, input)
    if (isCurrent()) lifecyclePreview.value = { action, input, result }
  } catch (cause) {
    if (!isCurrent() || handleScheduleDenied(cause)) return
    formError.value = cause instanceof Error ? cause.message : 'Не удалось рассчитать последствия.'
    emit('error', cause)
  } finally {
    if (isCurrent()) formBusy.value = false
  }
}

async function applyRecurring(): Promise<void> {
  const api = props.api
  const owner = currentRecurringOwner()
  const target = editTarget.value
  if (!api || !owner || !target?.id || formBusy.value || props.offline || intentRecoveryBlocked.value) return
  const existing = recurringIntent.value
  const preview = lifecyclePreview.value
  if ((!existing || existing.rejected) && !preview) return
  const revision = commandRevision
  const isCurrent = (): boolean => !disposed && !props.offline && headmanOwner.value && revision === commandRevision && api === props.api
    && recurringScope(owner) === (currentRecurringOwner() ? recurringScope(currentRecurringOwner()!) : null)
  const firstAttempt = !existing || existing.rejected
  let intent = existing && !existing.rejected ? existing : null
  formBusy.value = true
  formError.value = null
  let acknowledged = false
  try {
    if (!intent) {
      if (!preview || typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function') throw new Error('Для сохранения нужен UUID запроса. Обнови браузер.')
      intent = { owner, target: Object.freeze({ ...target }), key: crypto.randomUUID(), action: preview.action,
        input: preview.input ? Object.freeze({ ...preview.input, expectedRevision: preview.result.revision }) : null,
        preview: Object.freeze({ ...preview.result }), rejected: false, transferIds: [] }
    }
    persistRecurringIntent(sessionStorage, intent)
    recurringIntent.value = intent
    if (intent.action === 'DELETE') await api.deleteScheduleItem(target.id, intent.preview.revision, intent.key)
    else {
      const accepted = await api.updateScheduleItem(target.id, intent.input!, intent.key)
      if (!isCurrent()) return
      intent = { ...intent, transferIds: (accepted.transfers ?? []).map((transfer) => transfer.operationId) }
      persistRecurringIntent(sessionStorage, intent)
      recurringIntent.value = intent
    }
    acknowledged = true
    if (!isCurrent()) return
    const readback = await api.getScheduleItem(target.id)
    if (!isCurrent()) return
    if (readback.id !== target.id || readback.groupId !== owner.groupId || readback.semesterId !== owner.semesterId
      || (intent.action === 'DELETE' ? readback.active !== false : readback.active !== true
        || readback.weekType !== intent.input!.weekType || (readback.room ?? null) !== intent.input!.room)) {
      throw new Error('Изменение ещё не подтверждено текущим расписанием. Повтори исходный запрос.')
    }
    const transfers = await Promise.all(intent.transferIds.map((id) => api.getRecurringTransfer(id)))
    if (!isCurrent()) return
    if (transfers.some((transfer) => transfer.state !== 'COMPLETED')) {
      formError.value = transfers.some((transfer) => transfer.state === 'ERROR')
        ? 'Слот сохранён, перенос связанных данных завершился с ошибкой. Повтори проверку исходного запроса.'
        : 'Слот сохранён, перенос связанных данных ещё выполняется (PENDING). Повтори проверку исходного запроса.'
      return
    }
    sessionStorage.removeItem(recurringScope(owner))
    recurringIntent.value = null
    lifecyclePreview.value = null
    editTarget.value = null
    formBusy.value = false
    notice.value = intent.action === 'DELETE' ? 'Слот деактивирован. Прошедшие пары и их данные сохранены.'
      : 'Изменение будущих пар подтверждено сервером. Прошедшие пары сохранены.'
    closeForm()
    await load()
  } catch (cause) {
    if (!isCurrent()) return
    if (handleScheduleDenied(cause)) return
    if (intent && firstAttempt && !acknowledged && cause instanceof HeadmanScheduleApiError && cause.response.status === 409) {
      intent = { ...intent, rejected: true }
      try {
        persistRecurringIntent(sessionStorage, intent)
        recurringIntent.value = intent
      } catch {
        formError.value = 'Запрос отклонён, но не удалось сохранить отказ. Восстанови доступ к хранилищу и повтори исходный запрос.'
        return
      }
      lifecyclePreview.value = null
      formError.value = `${cause.message} Обнови расчёт и подтверди изменения заново.`
    } else {
      formError.value = cause instanceof Error ? cause.message : 'Результат не подтверждён. Повтори исходный запрос.'
    }
    emit('error', cause)
  } finally {
    if (isCurrent()) formBusy.value = false
  }
}

async function load(): Promise<void> {
  const revision = ++loadRevision
  loading.value = true
  error.value = null
  denied.value = false
  if (props.offline) {
    loading.value = false
    return
  }
  if (!props.api || props.groupId === null) {
    denied.value = true
    loading.value = false
    return
  }
  try {
    const semesters = await props.api.listSemesters()
    if (revision !== loadRevision) return
    const current = semesters.find((candidate) => candidate.active === true) ?? null
    semester.value = current
    if (!current?.id) {
      assignments.value = []
      scheduleItems.value = []
      oneOffLessons.value = []
      return
    }
    const [nextAssignments, nextItems, nextOneOff, catalog] = await Promise.all([
      props.api.listAssignments(props.groupId, current.id),
      props.api.listScheduleItems(props.groupId, current.id),
      current.dateFrom && current.dateTo
        ? props.api.listOneOffLessons(props.groupId, current.dateFrom, current.dateTo)
        : Promise.resolve([]),
      props.api.lessonSlots(),
    ])
    if (revision !== loadRevision) return
    slotCatalog.value=catalog
    assignments.value = nextAssignments
    scheduleItems.value = nextItems
    oneOffLessons.value = nextOneOff.filter((lesson) => lesson.semesterId === current.id)
    const owner = currentRecurringOwner()
    if (owner && !editTarget.value) {
      try {
        const savedCreate=readRecurringCreate(sessionStorage,owner)
        if (savedCreate) { restoreRecurringCreateEditor(savedCreate); return }
        const saved = readRecurringIntent(sessionStorage, owner)
        intentRecoveryBlocked.value = false
        if (saved) restoreRecurringEditor(saved)
      } catch (cause) {
        intentRecoveryBlocked.value = true
        error.value = cause instanceof Error ? cause.message : 'Не удалось восстановить запрос серии.'
      }
    }
  } catch (cause) {
    if (revision !== loadRevision) return
    if (handleScheduleDenied(cause)) return
    if (cause instanceof HeadmanScheduleApiError && (cause.response.status === 401 || cause.response.status === 403)) {
      denied.value = true
    } else {
      error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить расписание.'
      emit('error', cause)
    }
  } finally {
    if (revision === loadRevision) loading.value = false
  }
}

watch(
  () => [props.api, props.groupId, props.offline, scheduleOpen.value] as const,
  ([, , , isOpen]) => {
    if (isOpen) void load()
    else {
      loadRevision += 1
      loading.value = false
    }
  },
  { immediate: true },
)

watch(() => props.profilePort, (port) => {
  profileState.value = port ? new ProfileState(port) : null
  profilePendingRole.value = null
  profileRoleError.value = null
  publishProfileView()
  ensureProfileRoute(route.value)
})

watch(
  () => [props.api, props.groupId, props.profile?.userId, props.profile?.sessionId, props.profile?.activeRole,
    props.profile?.sessionVersion, props.profile?.rolesVersion, props.readOnly] as const,
  (current, previous) => {
    if (previous && current.every((value, index) => previous[index] === value)) return
    selectedLessonId.value = null
    commandRevision += 1
    loadRevision += 1
    formBusy.value = false
    oneOffIntent.value = null
    recurringCreateIntent.value=null
    recurringIntent.value = null
    editTarget.value = null
    lifecyclePreview.value = null
    intentRecoveryBlocked.value = false
    commandKey.value = null
    commandFingerprint.value = null
    formError.value = null
    notice.value = null
    oneOffLessons.value = []
    assignments.value = []
    scheduleItems.value = []
    semester.value = null
    selectedDate.value = props.selectedDate || moscowToday()
    profileRoleError.value = null
    profilePendingRole.value = null
    navigation.goRoot('headman-today')
  },
  { flush: 'sync' },
)

watch(() => [props.api, props.groupId, props.offline] as const, () => { void loadWeekPolicy() }, { immediate: true, flush: 'sync' })
watch(() => [props.offline, props.assistantPermissions] as const, () => {
  // Invalidate pending constructor publication; keep each durable owner intent for exact recovery.
  commandRevision += 1; formBusy.value=false; lifecyclePreview.value=null
}, { flush:'sync' })
watch(() => semester.value?.id, (current, previous) => {
  if (current === previous || previous === undefined) return
  const wasEditing = editTarget.value !== null
  commandRevision += 1
  recurringIntent.value = null
  editTarget.value = null
  lifecyclePreview.value = null
  formBusy.value = false
  if (wasEditing && route.value.id === scheduleFormRouteId) navigation.replace(nestedRoute('headman-more', scheduleRouteId, 'overview'))
}, { flush: 'sync' })

watch(
  () => [props.host, formOpen.value, formBusy.value, props.offline, props.readOnly,
    createMode.value, oneOffIntent.value, intentRecoveryBlocked.value, editTarget.value, recurringIntent.value, lifecyclePreview.value, denied.value, selectedWeekTypes.value] as const,
  ([host, open, busy, offline, readOnly]) => {
    if (!host || host.primaryActionOwner !== 'host') return
    host.setPrimaryAction?.(open && !denied.value
      ? { label: busy ? 'Сохраняем…' : editTarget.value ? recurringIntent.value && !recurringIntent.value.rejected ? 'Повторить исходный запрос'
        : 'Сохранить пару' : oneOffIntent.value ? 'Повторить сохранение'
        : createMode.value === 'ONE_OFF' ? 'Создать разовую пару' : 'Сохранить слот',
      disabled: busy || offline || readOnly || intentRecoveryBlocked.value || !selectedWeekTypes.value.length, onInvoke: save }
      : null)
  },
  { immediate: true },
)

onBeforeUnmount(() => { weekPolicyRevision += 1;
  disposed = true
  commandRevision += 1
  loadRevision += 1
  stopNavigation()
  stopRecurringBackGuard()
  stopManagementBackGuard()
  stopRequestsBackGuard()
  stopStatsBackGuard(); stopJournalBackGuard()
  stopHomeworkBackGuard(); stopGroupBackGuard(); stopSubjectsBackGuard()
  stopTheme()
  props.host?.setPrimaryAction?.(null)
})
async function openNotificationAction(action: NotificationAction, isCurrent: () => boolean): Promise<boolean> {
  if (!isCurrent() || disposed || props.offline || action.role !== 'HEADMAN' || props.profile?.activeRole !== 'HEADMAN' || props.groupId === null || denied.value) return false
  const groupId = props.groupId
  if (action.kind === 'lesson') {
    const api = props.journalApi, id = notificationNumericId(action.lessonId)
    if (!api || id === null) return false
    const report = await api.getLessonAttendance(id)
    if (!isCurrent() || disposed || api !== props.journalApi || groupId !== props.groupId || report.groupId !== groupId || !report.lessonDate) return false
    selectedDate.value = report.lessonDate; selectedLessonId.value = id
    navigation.goRoot('headman-attendance'); navigation.push(nestedRoute('headman-attendance', journalRouteId, 'detail'))
    return true
  }
  if (action.kind === 'request') {
    const api = props.requestsApi
    if (!api) return false
    const detail = await api.get(action.requestId)
    if (!isCurrent() || disposed || api !== props.requestsApi || groupId !== props.groupId || detail.summary.id !== action.requestId || detail.summary.kind !== action.requestKind) return false
    navigation.goRoot('headman-requests')
    await nextTick()
    return isCurrent() && (requestsScreen.value?.openNotificationRequest(detail) ?? false)
  }
  if (action.kind === 'homework') {
    const api = props.homeworkApi, id = notificationNumericId(action.homeworkId)
    if (!api || !props.journalApi || id === null || !props.homeworkActorUserId) return false
    const homework = await api.getHomework(id)
    if (!isCurrent() || disposed || api !== props.homeworkApi || groupId !== props.groupId || homework.groupId !== groupId) return false
    openHomework()
    await nextTick()
    if (!isCurrent() || !homeworkScreen.value) return false
    await homeworkScreen.value.openDetail(homework)
    return isCurrent()
  }
  if (action.kind === 'homework-week') {
    if (!props.homeworkApi || !props.journalApi || !props.homeworkActorUserId) return false
    openHomework(); await nextTick()
    if (!isCurrent() || !homeworkScreen.value) return false
    homeworkScreen.value.selectRootDate(action.weekStart)
    return true
  }
  if (action.kind === 'group' && action.groupId === String(groupId) && props.groupApi) { openGroup(); return true }
  return false
}
defineExpose({ openNotificationAction })
</script>

<template>
  <MobileShell
    :route="shellRoute"
    :navigation="journalTaskOpen ? null : navigation"
    :nav-items="navigationItems"
    :active-id="route.root === 'headman-map' ? 'headman-more' : route.root"
    :host="host"
    back-label="Назад"
    @back="handleShellBack"
  >
    <template #back="{ visible, onBack }">
      <button
        v-if="visible && route.root !== 'profile' && !groupOpen && !subjectsOpen && !requestsOpen && route.id!==lessonManagementRouteId && !formOpen"
        class="mobile-shell__back"
        aria-label="Назад"
        :class="{ 'headman-homework-back': route.id === homeworkRouteId }"
        type="button"
        @click="onBack"
      >
        <img
          v-if="route.id === homeworkRouteId"
          :src="homeworkBackIcon"
          alt="Назад"
        >
        <template v-else>
          Назад
        </template>
      </button>
      <span
        v-else
        hidden
        aria-hidden="true"
      />
    </template>
    <MapScreen
      v-if="route.id === 'headman-map' && props.mapClient"
      :client="props.mapClient"
      :theme="resolvedTheme"
    />
    <p
      v-else-if="route.id === 'headman-map'"
      class="headman-schedule__state"
      role="status"
    >
      Карта кампуса пока недоступна.
    </p>
    <HeadmanLessonActionsScreen
      v-else-if="route.id === actionLogRouteId && actionLogLesson"
      :api="lessonActionsApi"
      :lesson="actionLogLesson"
      :subject-name="actionLogSubject"
      :offline="offline"
      @error="forwardError"
    />
    <HeadmanHomeScreen
      v-else-if="route.id === 'headman-today'"
      :on-notifications="onNotifications"
      :unread-count="unreadCount"
      :api="journalApi"
      :week-policy-api="api"
      :group-id="groupId"
      :selected-date="selectedDate"
      :group-label="profile?.groupLabel ?? null"
      :subjects-api="subjectsApi"
      :homework-api="homeworkApi"
      :homework-enabled="moreAvailability.homework"
      :log-enabled="lessonActionsApi !== null"
      :offline="offline"
      :role-switch-disabled="homeRoleSwitchDisabled"
      @select-date="selectedDate = $event"
      @role-switch="openHomeRoleSwitch"
      @open-lesson="openHomeLesson"
      @open-homework="openHomework(true)"
      @open-log="openHomeLog"
      @error="forwardError"
    />
    <HeadmanRequestsScreen
      v-else-if="requestsOpen"
      ref="requestsScreen"
      :api="requestsApi"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      :show-back="true"
      @view-change="requestsView = $event"
      @back="navigation.back()"
      @error="forwardError"
    />
    <HeadmanMoreScreen
      v-else-if="route.id === 'headman-more'"
      :availability="moreAvailability"
      @select="selectMore"
    />
    <HeadmanStatsScreen
      v-else-if="statsOpen"
      ref="statsScreen"
      :api="statsApi"
      :journal-api="journalApi"
      :requests-api="requestsApi"
      :group-id="groupId"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :report-download="reportDownload"
      :show-back="false"
      @view-change="statsView = $event"
      @error="forwardError"
    />
    <HeadmanLessonManagementScreen
      v-else-if="route.id===lessonManagementRouteId"
      ref="lessonManagementScreen"
      :journal-api="journalApi"
      :schedule-api="api"
      :actions-api="lessonActionsApi"
      :group-id="groupId"
      :owner="profile ? { userId: profile.userId, sessionId: profile.sessionId } : null"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      :initial-date="selectedDate"
      @view-change="managementView=$event"
      @back="navigation.back()"
      @error="forwardError"
    />
    <HeadmanJournalScreen
      v-else-if="journalOpen"
      ref="journalScreen"
      :api="journalApi"
      :requests-api="requestsApi"
      :subjects-api="subjectsApi"
      :group-id="groupId"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      :report-download="reportDownload"
      :initial-date="selectedDate"
      :initial-lesson-id="selectedLessonId"
      @task-change="journalTaskOpen = $event"
      @error="emit('error', $event)"
    />
    <HeadmanGroupScreen
      v-else-if="groupOpen"
      ref="groupScreen"
      :actor-user-id="profile?.userId ?? null"
      :actor-headman="Boolean(profile?.roles.some((grant) => grant.role === 'HEADMAN' && grant.status === 'ACTIVE' && Number(grant.groupId) === groupId))"
      :api="groupApi"
      :group-id="groupId"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      :report-download="reportDownload"
      @back="navigation.back()"
      @error="emit('error', $event)"
    />
    <HeadmanSubjectsScreen
      v-else-if="subjectsOpen"
      ref="subjectsScreen"
      :api="subjectsApi"
      :group-id="groupId"
      :actor-user-id="profile?.userId ?? null"
      :offline="offline"
      :read-only="readOnly"
      @back="navigation.back()"
      @error="emit('error', $event)"
    />
    <HeadmanHomeworkScreen
      v-else-if="route.id === homeworkRouteId"
      ref="homeworkScreen"
      :subjects-api="subjectsApi"
      :initial-create="homeworkStartCreate"
      :host="host"
      :open-material="openMaterial"
      :api="homeworkApi"
      :journal-api="journalApi"
      :group-id="groupId"
      :user-id="homeworkActorUserId"
      :offline="offline"
      :read-only="readOnly"
      @view-change="homeworkView = $event"
      @error="forwardError"
    />
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
        :snapshot="profileView.snapshot ?? profile"
        :loading="profileView.snapshotStatus === 'loading' && profile === null"
        :error="profileView.snapshotError"
        :theme="resolvedTheme"
        :show-active-role="false"
        :on-retry="loadProfileSnapshot"
        :on-navigate="navigateProfile"
        :on-logout="onLogout"
      />
      <RoleSwitchScreen
        v-else-if="route.id === 'profile/role-switch'"
        :snapshot="profileView.snapshot ?? profile"
        :pending-role="profilePendingRole"
        :error="profileRoleError ?? profileView.snapshotError"
        :loading="profileView.snapshotStatus === 'loading'"
        :offline="offline || (profilePort === null && profileRoleSelect === undefined && onRoleSwitch === undefined)"
        :theme="resolvedTheme"
        :on-back="handleTaskBack"
        :on-select-role="selectProfileRole"
      />
      <AppearanceScreen
        v-else-if="route.id === 'profile/appearance'"
        :theme="themeMode"
        :resolved-theme="resolvedTheme"
        :on-back="handleTaskBack"
        :on-theme-change="changeProfileTheme"
      />
      <SecurityScreen
        v-else-if="route.id === 'profile/security'"
        :owner-key="profile ? `${profile.userId}|${profile.sessionId}|${profile.sessionVersion}|${profile.rolesVersion}` : null"
        :on-recover="onRecover"
        :policy="profileView.snapshot?.passwordPolicy ?? profile?.passwordPolicy ?? DEFAULT_PASSWORD_POLICY"
        :error="profileView.error"
        :busy="profileView.mutationBusy === 'password'"
        :offline="offline || profilePort === null"
        :theme="resolvedTheme"
        :on-back="handleTaskBack"
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
        :on-back="handleTaskBack"
        :on-retry="() => openProfileArea('sessions')"
        :on-logout-all="logoutProfileAll"
      />
      <AccountHistoryScreen
        v-else-if="route.id === 'profile/history'"
        :events="profileView.history"
        :loading="profileView.historyStatus === 'loading'"
        :error="profileView.historyError"
        :next-cursor="profileView.historyNextCursor"
        :theme="resolvedTheme"
        :on-back="handleTaskBack"
        :on-retry="() => openProfileArea('history')"
        :on-load-more="(cursor) => loadMoreProfile('history', cursor)"
      />
    </template>
    <main
      v-else-if="scheduleOpen"
      class="headman-schedule"
      :data-constructor-view="formOpen ? 'editor' : 'list'"
      :aria-busy="loading || formBusy"
    >
      <header :class="formOpen ? 'headman-schedule__child-header' : 'headman-schedule__header'">
        <button
          v-if="formOpen"
          class="headman-schedule__back"
          type="button"
          aria-label="Назад"
          @click="closeForm"
        >
          <MobileIcon name="back" />
        </button>
        <h1>{{ formOpen ? editTarget ? 'Изменить пару' : 'Добавить пару' : 'Конструктор расписания' }}</h1>
        <p
          v-if="!formOpen"
          class="headman-schedule__context"
        >
          Шаблон повторяющихся пар
        </p>
      </header>
      <StudentWarningBlock
        v-if="offline"
        title="Нет подключения"
        message="Просмотр и изменение расписания доступны онлайн."
      />
      <StudentWarningBlock
        v-if="error"
        severity="error"
        title="Расписание недоступно"
        :message="error"
        action-label="Повторить"
        @action="load"
      />
      <p
        v-if="notice"
        class="headman-schedule__state--success"
        role="status"
      >
        {{ notice }}
      </p>
      <p
        v-if="loading"
        role="status"
      >
        Загружаем расписание…
      </p>
      <StudentWarningBlock
        v-else-if="stateMessage"
        :title="denied ? 'Нет доступа' : 'Расписание пока недоступно'"
        :message="stateMessage"
      />
      <template v-else-if="!formOpen">
        <div
          class="headman-schedule__days"
          role="tablist"
          aria-label="День недели"
        >
          <button
            v-for="day in days"
            :key="day.value"
            class="headman-schedule__day"
            :data-selected="selectedDay===day.value"
            type="button"
            role="tab"
            :aria-selected="selectedDay===day.value"
            @click="selectedDay=day.value"
          >
            {{ day.label }}
          </button>
        </div>
        <div
          class="headman-schedule__weeks"
          aria-label="Учебная неделя"
        >
          <button
            v-for="option in parityOptions"
            :key="option.weekType"
            type="button"
            :aria-pressed="constructorWeek === option.weekType"
            @click="constructorWeek = option.weekType"
          >
            {{ option.label }}
          </button>
        </div>
        <section
          v-if="!visibleDayItems.length"
          class="headman-schedule__empty-card"
        >
          <span
            class="headman-schedule__plus"
            aria-hidden="true"
          >+</span><h2>На {{ days.find(d=>d.value===selectedDay)?.accusative }} пар пока нет</h2><p>Добавь пару для выбранной недели или сразу для обеих</p><button
            class="headman-schedule__primary"
            type="button"
            :disabled="offline || readOnly || !headmanOwner || intentRecoveryBlocked"
            @click="openForm"
          >
            Добавить пару
          </button>
        </section>
        <section
          v-if="visibleDayItems.length"
          class="headman-schedule__list"
        >
          <h2>{{ days.find(d=>d.value===selectedDay)?.name }} · {{ parityOptions.find(option => option.weekType === constructorWeek)?.label }}</h2><button
            v-for="item in visibleDayItems"
            :key="item.id"
            type="button"
            class="headman-schedule__slot"
            :disabled="!headmanOwner || offline || formBusy || intentRecoveryBlocked"
            @click="editRecurring(item)"
          >
            <small>{{ item.lessonNumber }} пара</small><strong>{{ assignments.find(a=>a.id===item.assignmentId)?.subjectName || 'Предмет' }}</strong><span><MobileIcon name="clock" />{{ item.startTime?.slice(0,5) }}–{{ item.endTime?.slice(0,5) }}</span><span><MobileIcon name="room" />{{ item.room || 'Аудитория не указана' }}</span><span><MobileIcon name="lesson" />{{ scheduleTypeName(assignments.find(a=>a.id===item.assignmentId)?.lessonType) }}</span><MobileIcon
              name="back"
              class="headman-schedule__chevron"
            />
          </button>
        </section>
        <button
          v-if="visibleDayItems.length"
          class="headman-schedule__primary"
          type="button"
          :disabled="offline || readOnly || !headmanOwner || intentRecoveryBlocked"
          @click="openForm"
        >
          + Добавить пару
        </button>
      </template>
      <section
        v-else-if="formOpen && !denied"
        class="headman-schedule__form"
        :aria-busy="formBusy"
      >
        <p class="headman-schedule__day-summary">
          {{ days.find(d=>d.value===selectedDay)?.name }}<small>{{ editTarget ? 'Изменение будущих пар, история сохранится' : 'Повторяется по выбранным учебным неделям' }}</small>
        </p>
        <fieldset class="headman-schedule__field">
          <legend>Учебные недели</legend><div class="headman-schedule__parity">
            <button
              v-for="option in parityOptions"
              :key="option.weekType"
              type="button"
              :data-selected="selectedWeekTypes.includes(option.weekType)"
              :aria-pressed="selectedWeekTypes.includes(option.weekType)"
              :disabled="formLocked || recurringLocked || offline || readOnly || !headmanOwner"
              @click="toggleConstructorWeek(option.weekType)"
            >
              {{ option.label }}<small>{{ selectedWeekTypes.includes(option.weekType) ? 'Выбрана' : 'Можно добавить' }}</small>
            </button>
          </div><small>Выбери одну или обе недели</small>
        </fieldset>
        <StudentWarningBlock
          v-if="weekPolicyError"
          title="Чётность недоступна"
          :message="weekPolicyError"
          action-label="Повторить"
          @action="loadWeekPolicy"
        />
        <label class="headman-schedule__field headman-schedule__field-card"><span class="headman-schedule__field-caption">Номер пары</span><button
          type="button"
          class="headman-schedule__select"
          :aria-label="lessonNumber ? `Номер пары: ${lessonNumber} пара · ${startTime}–${endTime}` : 'Номер пары: выбери номер пары'"
          :disabled="!!editTarget || formLocked || offline || !headmanOwner"
          @click="slotPickerOpen=!slotPickerOpen"
        >{{ lessonNumber ? `${lessonNumber} пара · ${startTime}–${endTime}` : 'Выбери номер пары' }}<MobileIcon name="chevron-down" /></button></label>
        <div
          v-if="slotPickerOpen && !editTarget"
          class="headman-schedule__slot-picker"
        >
          <button
            v-for="slot in slotCatalog?.slots ?? []"
            :key="slot.lessonNumber"
            :data-slot="slot.lessonNumber"
            type="button"
            :disabled="formLocked || !constructorSlotAvailable(slot.lessonNumber)"
            :aria-pressed="lessonNumber===slot.lessonNumber"
            @click="selectConstructorSlot(slot.lessonNumber)"
          >
            <strong>{{ slot.lessonNumber }} пара</strong><small>{{ slot.startTime.slice(0,5) }}–{{ slot.endTime.slice(0,5) }}</small>
          </button>
        </div>
        <label class="headman-schedule__field headman-schedule__field-card"><span class="headman-schedule__field-caption">Предмет</span><span class="headman-schedule__select-wrap"><select
          v-model.number="constructorSubjectId"
          :disabled="!!editTarget || formLocked || offline || !headmanOwner"
        ><option
          v-for="item in constructorSubjects"
          :key="item.subjectId"
          :value="item.subjectId"
        >{{ item.subjectName }}</option></select><MobileIcon name="chevron-down" /></span></label>
        <label class="headman-schedule__field headman-schedule__field-card"><span class="headman-schedule__field-caption">Тип занятия</span><span class="headman-schedule__select-wrap"><select
          v-model.number="assignmentId"
          :disabled="!!editTarget || formLocked || offline || !headmanOwner"
        ><option
          v-for="item in constructorAssignments"
          :key="item.id"
          :value="item.id"
        >{{ scheduleTypeName(item.lessonType) }}</option></select><MobileIcon name="chevron-down" /></span></label>
        <div class="headman-schedule__field headman-schedule__field-card">
          <span class="headman-schedule__field-caption">Преподаватель</span><strong class="headman-schedule__readout">{{ selectedAssignment?.teacherName || 'Нет действующего назначения' }}</strong>
        </div>
        <label class="headman-schedule__field headman-schedule__field-card"><span class="headman-schedule__field-caption">Аудитория</span><input
          v-model="room"
          type="text"
          maxlength="64"
          :disabled="formLocked || recurringLocked || offline || readOnly || !headmanOwner"
          placeholder="Не указана"
        ></label>
        <StudentWarningBlock
          v-if="formError"
          :severity="recurringCreateIntent || recurringIntent ? 'warning' : 'error'"
          title="Проверь сохранение"
          :message="formError"
        />
        <footer class="headman-schedule__footer">
          <button
            class="headman-schedule__primary"
            type="button"

            :disabled="formBusy || offline || readOnly || !headmanOwner || intentRecoveryBlocked || !weekPolicy || !selectedWeekTypes.length || (!editTarget && !recurringCreateIntent && (!lessonNumber || !selectedAssignment))"
            @click="save"
          >
            {{ formBusy ? 'Сохраняем…' : recurringCreateIntent || recurringIntent && !recurringIntent.rejected ? 'Повторить исходный запрос' : 'Сохранить пару' }}
          </button>
        </footer>
      </section>
    </main>
  </MobileShell>
  <RoleSwitchDialog
    v-if="homeRoleDialogOpen"
    :snapshot="profileView.snapshot ?? profile"
    :pending-role="profilePendingRole"
    :error="profileRoleError ?? profileView.snapshotError"
    :loading="profileView.snapshotStatus === 'loading' && !profile"
    :offline="offline"
    :on-select-role="selectProfileRole"
    @close="homeRoleDialogOpen = false"
  />
</template>

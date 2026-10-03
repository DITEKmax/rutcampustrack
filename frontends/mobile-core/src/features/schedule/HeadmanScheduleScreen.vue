<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import MobileShell from '../../shared/components/MobileShell.vue'
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
import AssistantHomeworkScreen from '../homework/AssistantHomeworkScreen.vue'
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
  api: HeadmanScheduleApi | null
  journalApi?: HeadmanJournalApi | null
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
  profilePort?: ProfilePort | null
  profileRoleSelect?: ((role: ProfileRole, expectedSessionVersion: string) => void | Promise<void>) | undefined
  themeController?: MobileThemeController | null
  selectedDate?: string
}>(), {
  journalApi: null,
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
  profilePort: null,
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
const recurringIntent = shallowRef<RecurringIntent | null>(null)
const lifecyclePreview = shallowRef<{ action: 'UPDATE' | 'DELETE'; input: Omit<HeadmanScheduleUpdateInput, 'expectedRevision'> | null; result: HeadmanLifecyclePreview } | null>(null)
const recurringLocked = computed(() => formBusy.value || (recurringIntent.value !== null && !recurringIntent.value.rejected))
const headmanOwner = computed(() => props.profile?.activeRole === 'HEADMAN' && !props.readOnly && !denied.value)
const formLocked = computed(() => formBusy.value || (createMode.value === 'ONE_OFF' && oneOffIntent.value !== null))
const semester = shallowRef<HeadmanScheduleSemester | null>(null)
const selectedDay = ref(1)
const formOpen = ref(false)
const journalOpen = ref(false)
const requestsOpen = ref(false)
const groupOpen = ref(false)
const subjectsOpen = ref(false)
const statsOpen = ref(false)
const scheduleOpen = ref(false)
const formBusy = ref(false)
const formError = ref<string | null>(null)
const notice = ref<string | null>(null)
const commandKey = ref<string | null>(null)
const assignmentId = ref<number | null>(null)
const weekType = ref<'ALL' | 'ODD' | 'EVEN'>('ALL')
const lessonNumber = ref<number | null>(null)
const startTime = ref('')
const endTime = ref('')
const room = ref('')
const commandFingerprint = ref<string | null>(null)
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
  if (current.id !== scheduleFormRouteId || !retainRecurringBack(denied.value, formBusy.value, recurringIntent.value)) return true
  formError.value = formBusy.value ? 'Дождись ответа сервера перед возвращением назад.'
    : 'Результат изменения ещё не подтверждён. Повтори исходный запрос.'
  return false
})
let stopNavigation = navigation.subscribe(() => {
  const next = navigation.current
  route.value = next
  journalOpen.value = next.root === 'headman-attendance' || next.id === lessonManagementRouteId
  requestsOpen.value = next.id === requestsRouteId
  groupOpen.value = next.id === groupRouteId
  subjectsOpen.value = next.id === subjectsRouteId
  statsOpen.value = next.id === statsRouteId
  scheduleOpen.value = next.id === scheduleRouteId || next.id === scheduleFormRouteId
  formOpen.value = next.id === scheduleFormRouteId
  ensureProfileRoute(next)
})

const days = [
  { value: 1, label: 'Пн' },
  { value: 2, label: 'Вт' },
  { value: 3, label: 'Ср' },
  { value: 4, label: 'Чт' },
  { value: 5, label: 'Пт' },
  { value: 6, label: 'Сб' },
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
  if (area === 'sessions') await runProfile((current) => current.loadSessions())
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
  navigateProfile('role-switch', { preserveHistory: true })
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
  if (!state && !props.profileRoleSelect) {
    await props.onRoleSwitch?.()
    return
  }
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

function openHomeLesson(lesson: HeadmanJournalLesson): void {
  selectedDate.value = lesson.date
  selectedLessonId.value = lesson.id
  navigation.goRoot('headman-attendance')
  navigation.push(nestedRoute('headman-attendance', journalRouteId, 'detail'))
}

function openOneOffLesson(lesson: HeadmanOneOffLesson): void {
  if (!props.journalApi) return
  selectedDate.value = lesson.date
  selectedLessonId.value = lesson.physicalLessonId
  navigation.push(nestedRoute('headman-more', lessonManagementRouteId, 'task'))
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

function openHomework(): void {
  if (!props.homeworkApi || !props.journalApi || props.groupId === null
    || !Number.isSafeInteger(props.homeworkActorUserId) || (props.homeworkActorUserId ?? 0) <= 0) return
  navigation.push(nestedRoute('headman-more', homeworkRouteId, 'task'))
}

function openSchedule(): void {
  if (!props.api || props.groupId === null) return
  navigation.push(nestedRoute('headman-more', scheduleRouteId, 'task'))
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
const selectedAssignment = computed(() => activeAssignments.value.find((item) => item.id === assignmentId.value) ?? null)
const dayItems = computed(() => scheduleItems.value
  .filter((item) => item.dayOfWeek === selectedDay.value)
  .slice()
  .sort((left, right) => (left.lessonNumber ?? 0) - (right.lessonNumber ?? 0)))
const stateMessage = computed(() => {
  if (props.offline) return 'Изменения доступны только при подключении к интернету.'
  if (denied.value) return 'Нет доступа к расписанию этой группы.'
  if (semester.value === null && !loading.value) return 'Нет активного семестра для заполнения расписания.'
  if (activeAssignments.value.length === 0 && !loading.value) return 'Для группы пока нет назначений преподавателей.'
  return null
})

function parityLabel(value: HeadmanScheduleItem['weekType']): string {
  if (value === 'ODD') return '1 неделя'
  if (value === 'EVEN') return '2 неделя'
  return '1 и 2 недели'
}

function assignmentLabel(item: HeadmanScheduleAssignment): string {
  const subject = item.subjectName?.trim() || 'Предмет без названия'
  const teacher = item.teacherName?.trim()
  return teacher ? `${subject} · ${teacher}` : subject
}

function newCommandKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `headman-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function openForm(): void {
  if (denied.value || props.offline || props.readOnly || loading.value || semester.value === null || activeAssignments.value.length === 0) return
  if (recurringIntent.value) { restoreRecurringEditor(recurringIntent.value); return }
  editTarget.value = null
  lifecyclePreview.value = null
  formError.value = null
  notice.value = null
  commandKey.value = newCommandKey()
  commandFingerprint.value = null
  assignmentId.value = activeAssignments.value[0]?.id ?? null
  weekType.value = 'ALL'
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

function validTime(value: string): boolean {
  return /^\d{2}:\d{2}$/.test(value) && Number(value.slice(0, 2)) < 24 && Number(value.slice(3)) < 60
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
  if (!lessonNumber.value || !Number.isSafeInteger(lessonNumber.value) || lessonNumber.value < 1) {
    formError.value = 'Укажи номер пары.'
    return null
  }
  if (!validTime(startTime.value) || !validTime(endTime.value)) {
    formError.value = 'Укажи время начала и конца в формате ЧЧ:ММ.'
    return null
  }
  if (startTime.value >= endTime.value) {
    formError.value = 'Время конца должно быть позже времени начала.'
    return null
  }
  if (createMode.value === 'ONE_OFF') {
    if (!isOneOffDateWithinSemester(oneOffDate.value, moscowToday(), currentSemester.dateFrom, currentSemester.dateTo)) {
      formError.value = 'Выбери дату в текущем семестре, не раньше сегодня.'
      return null
    }
    if (lessonNumber.value > 8 || room.value.trim().length > 64) {
      formError.value = 'Номер разовой пары — от 1 до 8, аудитория — до 64 символов.'
      return null
    }
    return {
      assignmentId: selected.id, groupId: props.groupId, subjectId: selected.subjectId,
      date: oneOffDate.value, lessonNumber: lessonNumber.value,
      startTime: `${startTime.value}:00`, endTime: `${endTime.value}:00`,
      ...(room.value.trim() ? { classroom: room.value.trim() } : {}),
    }
  }
  return {
    assignmentId: selected.id,
    groupId: props.groupId,
    subjectId: selected.subjectId,
    semesterId: currentSemester.id,
    dayOfWeek: selectedDay.value,
    lessonNumber: lessonNumber.value,
    startTime: `${startTime.value}:00`,
    endTime: `${endTime.value}:00`,
    weekType: weekType.value,
    ...(room.value.trim() ? { room: room.value.trim() } : {}),
  }
}

async function save(): Promise<void> {
  if (denied.value) return
  if (editTarget.value) {
    if (recurringIntent.value && !recurringIntent.value.rejected) await applyRecurring()
    else if (lifecyclePreview.value) await applyRecurring()
    else await previewRecurring('UPDATE')
    return
  }
  if (formBusy.value || intentRecoveryBlocked.value || props.offline || props.readOnly || !props.api) return
  formError.value = null
  const api = props.api
  const revision = commandRevision
  const mode = createMode.value
  const firstOneOffAttempt = mode === 'ONE_OFF' && oneOffIntent.value === null
  const scope = oneOffScope()
  const isCurrent = (): boolean => !disposed && revision === commandRevision && api === props.api
  const input = mode === 'ONE_OFF' && oneOffIntent.value ? oneOffIntent.value.input : buildInput()
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
      await api.createScheduleItem(input as HeadmanScheduleCreateInput, key)
      if (!isCurrent()) return
      notice.value = 'Слот сохранён. Расписание обновлено с сервера.'
    }
    commandKey.value = null
    commandFingerprint.value = null
    formBusy.value = false
    closeForm()
    await load()
  } catch (cause) {
    if (!isCurrent()) return
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

function currentRecurringOwner(): RecurringOwner | null {
  const profile = props.profile
  return headmanOwner.value && profile && props.groupId !== null && semester.value?.id
    ? { userId: profile.userId, sessionId: profile.sessionId, groupId: props.groupId, semesterId: semester.value.id } : null
}

function handleScheduleDenied(cause: unknown): boolean {
  const hidden = hideDeniedHeadmanSchedule(cause, {
    denied, busy: formBusy,
    rows: [assignments, scheduleItems, oneOffLessons],
    editor: [editTarget, recurringIntent, lifecyclePreview, oneOffIntent, semester, formError, error, notice, commandKey, commandFingerprint],
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
  weekType.value = item.weekType ?? 'ALL'
  room.value = item.room ?? ''
  navigation.push(nestedRoute('headman-more', scheduleFormRouteId, 'editor'))
}

watch(() => [weekType.value, room.value] as const, () => { lifecyclePreview.value = null }, { flush: 'sync' })

async function previewRecurring(action: 'UPDATE' | 'DELETE'): Promise<void> {
  const api = props.api
  const target = editTarget.value
  const owner = currentRecurringOwner()
  if (!api || !target?.id || !owner || props.offline || recurringLocked.value || intentRecoveryBlocked.value) return
  const revision = commandRevision
  const isCurrent = (): boolean => !disposed && commandRevision === revision && api === props.api && editTarget.value === target
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
  const isCurrent = (): boolean => !disposed && revision === commandRevision && api === props.api
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
    const [nextAssignments, nextItems, nextOneOff] = await Promise.all([
      props.api.listAssignments(props.groupId, current.id),
      props.api.listScheduleItems(props.groupId, current.id),
      current.dateFrom && current.dateTo
        ? props.api.listOneOffLessons(props.groupId, current.dateFrom, current.dateTo)
        : Promise.resolve([]),
    ])
    if (revision !== loadRevision) return
    assignments.value = nextAssignments
    scheduleItems.value = nextItems
    oneOffLessons.value = nextOneOff.filter((lesson) => lesson.semesterId === current.id)
    const owner = currentRecurringOwner()
    if (owner && !editTarget.value) {
      try {
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

watch(() => semester.value?.id, (current, previous) => {
  if (current === previous || previous === undefined) return
  const wasEditing = editTarget.value !== null
  commandRevision += 1
  recurringIntent.value = null
  editTarget.value = null
  lifecyclePreview.value = null
  formBusy.value = false
  if (wasEditing && route.value.id === scheduleFormRouteId) navigation.replace(nestedRoute('headman-more', scheduleRouteId, 'task'))
}, { flush: 'sync' })

watch(
  () => [props.host, formOpen.value, formBusy.value, props.offline, props.readOnly,
    createMode.value, oneOffIntent.value, intentRecoveryBlocked.value, editTarget.value, recurringIntent.value, lifecyclePreview.value, denied.value] as const,
  ([host, open, busy, offline, readOnly]) => {
    if (!host || host.primaryActionOwner !== 'host') return
    host.setPrimaryAction?.(open && !denied.value
      ? { label: busy ? 'Сохраняем…' : editTarget.value ? recurringIntent.value && !recurringIntent.value.rejected ? 'Повторить исходный запрос'
        : lifecyclePreview.value ? 'Подтвердить изменение' : 'Рассчитать изменение' : oneOffIntent.value ? 'Повторить сохранение'
        : createMode.value === 'ONE_OFF' ? 'Создать разовую пару' : 'Сохранить слот',
      disabled: busy || offline || readOnly || intentRecoveryBlocked.value, onInvoke: save }
      : null)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  disposed = true
  commandRevision += 1
  loadRevision += 1
  stopNavigation()
  stopRecurringBackGuard()
  stopTheme()
  props.host?.setPrimaryAction?.(null)
})
</script>

<template>
  <MobileShell
    :route="route"
    :navigation="navigation"
    :nav-items="navigationItems"
    :active-id="route.root === 'headman-map' ? 'headman-more' : route.root"
    :host="host"
    back-label="Назад"
  >
    <template #back="{ visible, onBack }">
      <button
        v-if="visible && route.root !== 'profile'"
        class="mobile-shell__back"
        type="button"
        @click="onBack"
      >
        Назад
      </button>
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
    <HeadmanHomeScreen
      v-else-if="route.id === 'headman-today'"
      :api="journalApi"
      :group-id="groupId"
      :selected-date="selectedDate"
      :offline="offline"
      :role-switch-disabled="homeRoleSwitchDisabled"
      @select-date="selectedDate = $event"
      @role-switch="openHomeRoleSwitch"
      @open-lesson="openHomeLesson"
      @error="forwardError"
    />
    <HeadmanRequestsScreen
      v-else-if="requestsOpen"
      :api="requestsApi"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      :show-back="false"
      @error="forwardError"
    />
    <HeadmanMoreScreen
      v-else-if="route.id === 'headman-more'"
      :availability="moreAvailability"
      @select="selectMore"
    />
    <HeadmanStatsScreen
      v-else-if="statsOpen"
      :api="statsApi"
      :group-id="groupId"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :report-download="reportDownload"
      :show-back="false"
      @error="forwardError"
    />
    <HeadmanJournalScreen
      v-else-if="journalOpen"
      :api="journalApi"
      :group-id="groupId"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      :report-download="reportDownload"
      :initial-date="selectedDate"
      :initial-lesson-id="selectedLessonId"
      @error="emit('error', $event)"
    />
    <HeadmanGroupScreen
      v-else-if="groupOpen"
      :api="groupApi"
      :group-id="groupId"
      :assistant-permissions="assistantPermissions"
      :offline="offline"
      :read-only="readOnly"
      :report-download="reportDownload"
      @error="emit('error', $event)"
    />
    <HeadmanSubjectsScreen
      v-else-if="subjectsOpen"
      :api="subjectsApi"
      :group-id="groupId"
      :actor-user-id="profile?.userId ?? null"
      :offline="offline"
      :read-only="readOnly"
      @error="emit('error', $event)"
    />
    <AssistantHomeworkScreen
      v-else-if="route.id === homeworkRouteId"
      :navigation="navigation"
      :api="homeworkApi"
      :journal-api="journalApi"
      :group-id="groupId"
      :user-id="homeworkActorUserId"
      :offline="offline"
      :read-only="readOnly"
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
        :error="profileView.sessionsError ?? profileView.error"
        :next-cursor="profileView.sessionsNextCursor"
        :busy="profileView.mutationBusy === 'logout-all'"
        :offline="offline || profilePort === null"
        :theme="resolvedTheme"
        :on-back="handleTaskBack"
        :on-retry="() => openProfileArea('sessions')"
        :on-load-more="(cursor) => loadMoreProfile('sessions', cursor)"
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
      aria-labelledby="headman-schedule-title"
    >
      <header class="headman-schedule__header">
        <div>
          <p class="headman-schedule__eyebrow">
            Староста · управление
          </p>
          <h1 id="headman-schedule-title">
            Конструктор расписания
          </h1>
          <p class="headman-schedule__context">
            {{ profile?.roles.find((grant) => grant.role === 'HEADMAN')?.contextLabel || 'Авторизованная группа' }}
          </p>
        </div>
      </header>

      <p
        v-if="offline"
        class="headman-schedule__state"
        role="status"
      >
        Просмотр и изменение расписания доступны только онлайн.
      </p>
      <p
        v-if="error"
        class="headman-schedule__state headman-schedule__state--error"
        role="alert"
      >
        {{ error }}
      </p>
      <p
        v-if="notice"
        class="headman-schedule__state headman-schedule__state--success"
        role="status"
      >
        {{ notice }}
      </p>

      <section
        v-if="loading"
        class="headman-schedule__state"
        aria-live="polite"
      >
        Загружаем семестр, назначения и расписание…
      </section>
      <section
        v-else-if="stateMessage"
        class="headman-schedule__state"
        :data-denied="denied"
      >
        {{ stateMessage }}
      </section>
      <template v-else>
        <div
          class="headman-schedule__days"
          role="tablist"
          aria-label="День недели"
        >
          <button
            v-for="day in days"
            :key="day.value"
            class="headman-schedule__day"
            :data-selected="selectedDay === day.value"
            type="button"
            role="tab"
            :aria-selected="selectedDay === day.value"
            @click="selectedDay = day.value"
          >
            {{ day.label }}
          </button>
        </div>

        <section
          class="headman-schedule__list"
          aria-live="polite"
        >
          <p
            v-if="dayItems.length === 0"
            class="headman-schedule__empty"
          >
            В этот день слотов пока нет.
          </p>
          <article
            v-for="item in dayItems"
            :key="item.id ?? `${item.lessonNumber}-${item.weekType}`"
            class="headman-schedule__slot"
          >
            <div class="headman-schedule__slot-time">
              <strong>{{ item.lessonNumber ?? '—' }} пара</strong>
              <span>{{ item.startTime ?? '—' }}–{{ item.endTime ?? '—' }}</span>
            </div>
            <div class="headman-schedule__slot-copy">
              <strong>{{ assignments.find((candidate) => candidate.id === item.assignmentId)?.subjectName || 'Предмет' }}</strong>
              <span>{{ parityLabel(item.weekType) }}<span v-if="item.room"> · {{ item.room }}</span></span>
              <button
                v-if="headmanOwner"
                class="headman-schedule__secondary"
                type="button"
                :disabled="offline || formBusy || intentRecoveryBlocked"
                @click="editRecurring(item)"
              >
                Изменить слот
              </button>
            </div>
          </article>
        </section>

        <section
          class="headman-schedule__list"
          aria-label="Разовые пары"
          aria-live="polite"
        >
          <h2>Разовые пары</h2>
          <p
            v-if="oneOffLessons.length === 0"
            class="headman-schedule__empty"
          >
            В этом семестре разовых пар пока нет.
          </p>
          <article
            v-for="item in oneOffLessons"
            :key="item.id"
            class="headman-schedule__slot"
          >
            <div class="headman-schedule__slot-time">
              <strong>{{ item.lessonNumber }} пара</strong>
              <span>{{ item.date }}</span>
            </div>
            <div class="headman-schedule__slot-copy">
              <strong>{{ assignments.find((candidate) => candidate.subjectId === item.subjectId)?.subjectName || 'Предмет' }}</strong>
              <span>Разовая<span v-if="item.classroom"> · {{ item.classroom }}</span></span>
              <button
                v-if="journalApi"
                type="button"
                class="headman-schedule__secondary"
                @click="openOneOffLesson(item)"
              >
                Открыть пару
              </button>
            </div>
          </article>
        </section>
        <button
          class="headman-schedule__primary"
          type="button"
          :disabled="offline || readOnly || activeAssignments.length === 0 || intentRecoveryBlocked || formBusy"
          @click="openForm"
        >
          Добавить пару
        </button>
      </template>

      <section
        v-if="formOpen && !denied"
        class="headman-schedule__form"
        aria-labelledby="headman-schedule-form-title"
        :aria-busy="formBusy"
      >
        <div class="headman-schedule__form-header">
          <h2 id="headman-schedule-form-title">
            {{ editTarget ? 'Изменение слота' : createMode === 'ONE_OFF' ? 'Разовая пара' : 'Новый слот' }}
          </h2>
          <button
            class="headman-schedule__secondary"
            type="button"
            :disabled="formBusy"
            @click="closeForm"
          >
            Отмена
          </button>
        </div>
        <template v-if="editTarget">
          <p class="headman-schedule__context">
            {{ assignments.find((item) => item.subjectId === editTarget?.subjectId)?.subjectName || 'Предмет' }} ·
            {{ days.find((day) => day.value === editTarget?.dayOfWeek)?.label }} · {{ editTarget.lessonNumber }} пара ·
            {{ editTarget.startTime }}–{{ editTarget.endTime }}.
            Предмет, день, номер и время изменить нельзя. Изменения касаются будущих пар; история сохранится.
          </p>
          <label class="headman-schedule__field">
            <span>Неделя</span>
            <select
              v-model="weekType"
              :disabled="recurringLocked || offline || readOnly"
            >
              <option value="ODD">1 неделя</option>
              <option value="EVEN">2 неделя</option>
              <option value="ALL">Обе недели</option>
            </select>
          </label>
          <label class="headman-schedule__field">
            <span>Аудитория</span>
            <input
              v-model="room"
              type="text"
              maxlength="64"
              :disabled="recurringLocked || offline || readOnly"
            >
          </label>
          <div class="headman-schedule__field-row">
            <button
              class="headman-schedule__secondary"
              type="button"
              :disabled="recurringLocked || offline || readOnly || intentRecoveryBlocked"
              @click="previewRecurring('UPDATE')"
            >
              Рассчитать сохранение
            </button>
            <button
              class="headman-schedule__secondary"
              type="button"
              :disabled="recurringLocked || offline || readOnly || intentRecoveryBlocked"
              @click="previewRecurring('DELETE')"
            >
              Деактивировать слот
            </button>
          </div>
          <p
            v-if="lifecyclePreview"
            class="headman-schedule__context"
            role="status"
          >
            {{ lifecyclePreview.action === 'DELETE' ? 'Деактивация' : 'Сохранение' }}:
            обновятся {{ lifecyclePreview.result.updatedCount }},
            уберутся {{ lifecyclePreview.result.removedCount }},
            восстановятся {{ lifecyclePreview.result.restoredCount }},
            создадутся {{ lifecyclePreview.result.createdCount }} будущих пар.
            Расчёт сервера, ревизия {{ lifecyclePreview.result.revision }}.
            Подтверди последствия кнопкой ниже.
          </p>
          <p
            v-if="recurringIntent && !recurringIntent.rejected"
            class="headman-schedule__context"
            role="status"
          >
            Проверяем исходный запрос {{ recurringIntent.action === 'DELETE' ? 'деактивации' : 'сохранения' }}.
            Параметры и ключ сохранены для точного повтора после сбоя или перезагрузки.
          </p>
        </template>
        <template v-else>
          <label class="headman-schedule__field">
            <span>Повторение</span>
            <select
              v-model="createMode"
              :disabled="formLocked || offline || readOnly || intentRecoveryBlocked"
            >
              <option value="RECURRING">По расписанию каждую неделю</option>
              <option value="ONE_OFF">Разовая пара</option>
            </select>
          </label>
          <label
            v-if="createMode === 'ONE_OFF'"
            class="headman-schedule__field"
          >
            <span>Дата</span>
            <input
              v-model="oneOffDate"
              type="date"
              :min="semester?.dateFrom && semester.dateFrom > moscowToday() ? semester.dateFrom : moscowToday()"
              :disabled="formLocked || offline || readOnly"
            >
          </label>
          <p
            v-if="oneOffIntent"
            class="headman-schedule__context"
            role="status"
          >
            Повтори сохранение исходной пары, чтобы проверить результат предыдущего запроса.
          </p>
          <label class="headman-schedule__field">
            <span>Предмет и преподаватель</span>
            <select
              v-model.number="assignmentId"
              :disabled="formLocked || offline || readOnly"
            >
              <option
                v-for="item in activeAssignments"
                :key="item.id"
                :value="item.id"
              >
                {{ assignmentLabel(item) }}
              </option>
            </select>
          </label>
          <fieldset
            v-if="createMode === 'RECURRING'"
            class="headman-schedule__field"
          >
            <legend>Неделя</legend>
            <div class="headman-schedule__parity">
              <button
                type="button"
                :data-selected="weekType === 'ODD'"
                :disabled="formLocked || offline || readOnly"
                @click="weekType = 'ODD'"
              >
                1
              </button>
              <button
                type="button"
                :data-selected="weekType === 'EVEN'"
                :disabled="formLocked || offline || readOnly"
                @click="weekType = 'EVEN'"
              >
                2
              </button>
              <button
                type="button"
                :data-selected="weekType === 'ALL'"
                :disabled="formLocked || offline || readOnly"
                @click="weekType = 'ALL'"
              >
                Обе
              </button>
            </div>
          </fieldset>
          <div class="headman-schedule__field-row">
            <label class="headman-schedule__field">
              <span>Номер пары</span>
              <input
                v-model.number="lessonNumber"
                min="1"
                :max="createMode === 'ONE_OFF' ? 8 : 20"
                inputmode="numeric"
                type="number"
                :disabled="formLocked || offline || readOnly"
              >
            </label>
            <label class="headman-schedule__field">
              <span>Аудитория</span>
              <input
                v-model="room"
                type="text"
                autocomplete="off"
                :disabled="formLocked || offline || readOnly"
              >
            </label>
          </div>
          <div class="headman-schedule__field-row">
            <label class="headman-schedule__field">
              <span>Начало</span>
              <input
                v-model="startTime"
                type="time"
                :disabled="formLocked || offline || readOnly"
              >
            </label>
            <label class="headman-schedule__field">
              <span>Конец</span>
              <input
                v-model="endTime"
                type="time"
                :disabled="formLocked || offline || readOnly"
              >
            </label>
          </div>
        </template>
        <p
          v-if="formError"
          class="headman-schedule__form-error"
          role="alert"
        >
          {{ formError }}
        </p>
        <button
          class="headman-schedule__primary"
          type="button"
          :disabled="formBusy || offline || readOnly || intentRecoveryBlocked || (editTarget !== null && !lifecyclePreview && (!recurringIntent || recurringIntent.rejected))"
          @click="save"
        >
          {{ formBusy ? 'Сохраняем…' : editTarget ? recurringIntent && !recurringIntent.rejected ? 'Повторить исходный запрос' : lifecyclePreview?.action === 'DELETE' ? 'Подтвердить деактивацию' : 'Подтвердить сохранение' : oneOffIntent ? 'Повторить сохранение' : createMode === 'ONE_OFF' ? 'Создать разовую пару' : 'Сохранить слот' }}
        </button>
      </section>
    </main>
  </MobileShell>
</template>

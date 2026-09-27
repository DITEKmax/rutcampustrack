<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import type { CampusMapClient } from '../../api/map-client'
import { StudentApi, StudentApiError } from '../../api/student-client'
import type {
  StudentCheckinCommand,
  StudentHomework,
  StudentHomeworkItem,
  StudentSemesterSchedule,
  StudentToday,
  TodayLesson,
} from '../../api/types'
import { CheckinCommandRecovery } from '../../domain/checkin'
import type {
  NotificationHomeworkTarget,
  NotificationLessonTarget,
  NotificationRequestTarget,
  NotificationTarget,
  NotificationTargetIntent,
} from '../../features/notifications/notifications-client'
import AttendanceScreen from '../../features/attendance/AttendanceScreen.vue'
import { findAttendanceLesson, type AttendanceGraphRange, type AttendanceLesson, type AttendanceMode } from '../../features/attendance/attendance-view-model'
import { useAttendance } from '../../features/attendance/use-attendance'
import HomeworkScreen from '../../features/homework/HomeworkScreen.vue'
import type { HeadmanHomeworkApi } from '../../features/homework/headman-homework-client'
import { useHomework } from '../../features/homework/use-homework'
import AccountHistoryScreen from '../../features/profile/AccountHistoryScreen.vue'
import AppearanceScreen from '../../features/profile/AppearanceScreen.vue'
import MoreScreen from '../../features/profile/MoreScreen.vue'
import AssistantActionsScreen from '../../features/headman-group/AssistantActionsScreen.vue'
import type { HeadmanAssistantPermission } from '../../features/headman-group/headman-group-client'
import type { HeadmanJournalApi } from '../../features/headman-journal/headman-journal-client'
import type { HeadmanRequestsApi } from '../../features/headman-requests/headman-requests-client'
import type { HeadmanStatsApi } from '../../features/headman-stats/headman-stats-client'
import type { ReportDownloadPort } from '../report-download-client'
import MapScreen from '../../features/map/MapScreen.vue'
import ProfileScreen from '../../features/profile/ProfileScreen.vue'
import { ProfileState } from '../../features/profile/profile-state'
import RoleSwitchScreen from '../../features/profile/RoleSwitchScreen.vue'
import SecurityScreen from '../../features/profile/SecurityScreen.vue'
import SessionsScreen from '../../features/profile/SessionsScreen.vue'
import { DEFAULT_PASSWORD_POLICY } from '../../features/profile/profile-types'
import type { ProfilePort, ProfileRole, ProfileRoute, ProfileTheme } from '../../features/profile/profile-types'
import TodayScreen from '../../features/today/TodayScreen.vue'
import { useToday } from '../../features/today/use-today'
import ExcuseRequestScreen from '../../features/requests/ExcuseRequestScreen.vue'
import LateCheckinRequestScreen from '../../features/requests/LateCheckinRequestScreen.vue'
import RequestsScreen from '../../features/requests/RequestsScreen.vue'
import RequestTypeScreen from '../../features/requests/RequestTypeScreen.vue'
import { openRequestAttachmentPopup, runRequestAttachmentOpen, type RequestAttachmentPopup } from '../../features/requests/request-attachment-action'
import { requestsSessionGeneration, getOrCreateRequestsDraft, purgeRequestsDrafts, updateRequestsDraft } from '../../features/requests/state'
import { useRequests } from '../../features/requests/use-requests'
import { RequestsError, shouldLoadRequestOptions } from '../../features/requests/requests-controller'
import type { RequestAttachmentViewState, RequestBucket, RequestDetail, RequestFileRef, RequestKind, RequestTypeChoice } from '../../features/requests/types'
import StatisticsScreen from '../../features/statistics/StatisticsScreen.vue'
import { useStatistics } from '../../features/statistics/use-statistics'
import MobileShell from './MobileShell.vue'
import { canRunProfileNetworkAction, profileOwnerStaleMessage } from '../profile-owner-status'
import {
  createMobileNavigationStack,
  nestedRoute,
  rootRoute,
  type MobileBottomNavItems,
  type MobileRoute,
  type MobileRootRouteId,
  type MobileNavigationStack,
} from '../navigation'
import { createStudentNavigationItems } from '../mobile-navigation-items'
import type { MobileHostAdapter } from '../host'
import { studentFeatureScopeIdentity, type StudentFeatureScope } from '../session-owner'
import type { MobileThemeController, MobileThemeResolvedMode } from '../theme'
import { createProfileViewPublication } from './profile-view-publication'

const props = withDefaults(defineProps<{
  api: StudentApi | null
  scope: StudentFeatureScope | null
  ownerKey?: string | null
  offline: boolean
  readOnly?: boolean
  todayFallback?: StudentToday | null
  homeworkFallback?: StudentHomework | null
  semesterSchedule?: StudentSemesterSchedule | null
  updatedAt?: string | null
  host?: MobileHostAdapter | null
  mapClient?: CampusMapClient | null
  profilePort?: ProfilePort | null
  profileRoleSelect?: ((role: ProfileRole, expectedSessionVersion: string) => void | Promise<void>) | undefined
  themeController?: MobileThemeController | null
  acquireCheckinCommand: () => Promise<StudentCheckinCommand>
  openMaterial: (url: string, item: StudentHomeworkItem) => void
  assistantPermissions?: readonly HeadmanAssistantPermission[]
  assistantJournalApi?: HeadmanJournalApi | null
  assistantStatsApi?: HeadmanStatsApi | null
  assistantRequestsApi?: HeadmanRequestsApi | null
  assistantHomeworkApi?: HeadmanHomeworkApi | null
  reportDownload?: ReportDownloadPort | null
  notificationTargetIntent?: NotificationTargetIntent | null
}>(), {
  readOnly: false,
  ownerKey: null,
  todayFallback: null,
  homeworkFallback: null,
  semesterSchedule: null,
  updatedAt: null,
  host: null,
  mapClient: null,
  profilePort: null,
  profileRoleSelect: undefined,
  themeController: null,
  assistantPermissions: () => [],
  assistantJournalApi: null,
  assistantStatsApi: null,
  assistantRequestsApi: null,
  assistantHomeworkApi: null,
  reportDownload: null,
  notificationTargetIntent: null,
})

const emit = defineEmits<{
  ownerError: [error: unknown]
  homeworkLoaded: [homework: StudentHomework]
  clearNotificationTarget: [requestId: number]
}>()

type TargetLoadState =
  | { status: 'loading' }
  | { status: 'unavailable'; message: string }
  | { status: 'error'; message: string }

type HomeworkNotificationState = {
  kind: 'homework'
  requestId: number
  target: NotificationHomeworkTarget
} & (
  | { status: 'loading' }
  | { status: 'available' }
  | { status: 'unavailable'; message: string }
  | { status: 'error'; message: string }
)

type RequestNotificationState = {
  kind: 'request'
  requestId: number
  target: NotificationRequestTarget
} & (TargetLoadState | { status: 'available'; detail: RequestDetail })

type LessonNotificationState = {
  kind: 'lesson'
  requestId: number
  target: NotificationLessonTarget
} & (TargetLoadState | { status: 'available'; lessonDate: string })

type NotificationTargetState = HomeworkNotificationState | RequestNotificationState | LessonNotificationState

/** A disabled query still needs a StudentApi instance for composable setup. */
const inertApi = new StudentApi({ accessToken: () => null })
const api = computed(() => props.api ?? inertApi)
// Keep the composables on an owner-local ref. App-level owner replacement can
// unmount this wrapper before Vue updates its old props; invalidating this ref
// makes every late GET/ACK compare against an empty scope and fail closed.
const scope = shallowRef<StudentFeatureScope | null>(props.scope)
const stopScope = watch(() => props.scope, (value) => {
  scope.value = value
}, { flush: 'sync' })
const offline = computed(() => props.offline || props.api === null)
const navigation: MobileNavigationStack = createMobileNavigationStack(rootRoute('today'))
const route = ref<MobileRoute>(navigation.current)
const homeworkNotificationRoute = nestedRoute('homework', 'homework/notification-target', 'detail')
const requestNotificationRoute = nestedRoute('more', 'more/requests/notification-target', 'detail')
const lessonNotificationRoute = nestedRoute('attendance', 'attendance/notification-target', 'detail')
const notificationTargetRouteIds = new Set<string>([
  homeworkNotificationRoute.id,
  requestNotificationRoute.id,
  lessonNotificationRoute.id,
])
const selectedDate = ref(new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' }))
const attendanceSelectedDate = ref(new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' }))
const attendanceMode = ref<AttendanceMode>('days')
const attendanceGraphRange = ref<AttendanceGraphRange>('days')
const attendanceExpandedSubjectId = ref<string | null>(null)
const attendanceActionLessonId = ref<string | null>(null)
const navItems = computed<MobileBottomNavItems>(() => createStudentNavigationItems({
  homeworkEnabled: true,
  attendanceEnabled: true,
  moreEnabled: true,
  profileEnabled: props.profilePort !== null,
}))
const checkinRecovery = new CheckinCommandRecovery()
const profileState = props.profilePort ? new ProfileState(props.profilePort) : null
const profilePendingRole = ref<ProfileRole | null>(null)
const themeMode = ref<ProfileTheme>(props.themeController?.mode ?? 'system')
const resolvedTheme = ref<MobileThemeResolvedMode>(props.themeController?.resolvedMode ?? 'dark')
let stopTheme = (): void => undefined
let disposed = false

const today = useToday(
  api.value,
  computed(() => props.api !== null),
  offline,
  scope,
)
const homework = useHomework(
  api.value,
  scope,
  {
    offline,
    readOnly: computed(() => props.readOnly),
    fallback: () => props.homeworkFallback,
  },
)
const notificationTargetState = shallowRef<NotificationTargetState | null>(null)
const requests = useRequests(api, scope, {
  offline,
  readOnly: computed(() => props.readOnly),
})
const attendance = useAttendance(api.value, scope, offline)
const statistics = useStatistics(api.value, scope, offline)
const stopRequestsScope = watch(
  () => props.scope,
  (value, previous) => {
    if (previous && (!value || studentFeatureScopeIdentity(previous) !== studentFeatureScopeIdentity(value))) {
      purgeRequestsDrafts(previous.userId ?? undefined)
    }
  },
  { flush: 'sync' },
)

const stopNavigation = navigation.subscribe(() => {
  route.value = navigation.current
  ensureProfileRoute(route.value)
  ensureRequestsRoute(route.value)
})

watch(() => route.value, (value, previous) => {
  ensureProfileRoute(value)
  ensureRequestsRoute(value)
  if (value.id !== 'more/statistics') statistics.closeSubject()
  if (previous && notificationTargetRouteIds.has(previous.id) && !notificationTargetRouteIds.has(value.id)) {
    const wasHomeworkTarget = previous.id === homeworkNotificationRoute.id
    const requestId = notificationTargetState.value?.requestId
    notificationTargetState.value = null
    if (wasHomeworkTarget && homework.range.value !== null) homework.returnToToday()
    if (requestId !== undefined) emit('clearNotificationTarget', requestId)
  }
}, { immediate: true })

const displayToday = computed(() => offline.value
  ? props.todayFallback
  : today.query.data.value ?? props.todayFallback)
const displayHomework = computed(() => offline.value
  ? props.homeworkFallback
  : homework.query.data.value ?? props.homeworkFallback)
const todayError = computed(() => {
  if (displayToday.value) return null
  const value = today.query.error.value
  return value instanceof Error ? value.message : null
})
const homeworkError = computed(() => {
  if (displayHomework.value) return null
  const value = homework.query.error.value
  return value instanceof StudentApiError ? value.problem?.detail ?? value.message : value instanceof Error ? value.message : null
})
const todayLoading = computed(() => !displayToday.value && props.api !== null && !offline.value && today.query.isPending.value)
const homeworkLoading = computed(() => !displayHomework.value && props.api !== null && !offline.value && homework.query.isPending.value)
const ownerIdentity = computed(() => scope.value ? studentFeatureScopeIdentity(scope.value) : null)
const activeHomeworkNotification = computed(() => route.value.id === homeworkNotificationRoute.id
  && notificationTargetState.value?.kind === 'homework'
  ? notificationTargetState.value
  : null)
const activeRequestNotification = computed(() => route.value.id === requestNotificationRoute.id
  && notificationTargetState.value?.kind === 'request'
  ? notificationTargetState.value
  : null)
const requestNotificationView = computed(() => {
  const targetState = activeRequestNotification.value
  if (!targetState) return null
  if (targetState.status === 'available') return { status: targetState.status, detail: targetState.detail } as const
  if (targetState.status === 'unavailable' || targetState.status === 'error') {
    return { status: targetState.status, message: targetState.message } as const
  }
  return { status: 'loading' } as const
})
const activeLessonNotification = computed(() => route.value.id === lessonNotificationRoute.id
  && notificationTargetState.value?.kind === 'lesson'
  ? notificationTargetState.value
  : null)
const focusedNotificationLessonId = computed(() => activeLessonNotification.value?.status === 'available'
  ? activeLessonNotification.value.target.lessonId
  : null)
const homeworkScreenData = computed(() => {
  const targetState = activeHomeworkNotification.value
  if (!targetState) {
    return {
      homework: displayHomework.value,
      loading: homeworkLoading.value,
      error: homeworkError.value,
      unavailableMessage: null as string | null,
      focusItemId: null as string | null,
      focusRequestId: null as number | null,
    }
  }
  if (targetState.status === 'available') {
    const feed = homework.query.data.value
    const dateRangeIsCurrent = feed?.from === targetState.target.lessonDate
      && feed.to === targetState.target.lessonDate
      && homework.range.value?.from === targetState.target.lessonDate
      && homework.range.value.to === targetState.target.lessonDate
    const item = dateRangeIsCurrent
      ? feed.items.find((entry) => entry.id === targetState.target.homeworkId
        && entry.lessonDate === targetState.target.lessonDate)
      : undefined
    if (!dateRangeIsCurrent) {
      return {
        homework: null,
        loading: true,
        error: null,
        unavailableMessage: null,
        focusItemId: null,
        focusRequestId: null,
      }
    }
    if (!item) {
      return {
        homework: null,
        loading: false,
        error: null,
        unavailableMessage: 'Это задание удалено или больше недоступно в твоей учебной группе.',
        focusItemId: null,
        focusRequestId: null,
      }
    }
    return {
      homework: { ...feed, items: [item] },
      loading: false,
      error: null,
      unavailableMessage: null,
      focusItemId: item.id,
      focusRequestId: targetState.requestId,
    }
  }
  return {
    homework: null,
    loading: targetState.status === 'loading',
    error: targetState.status === 'error' ? targetState.message : null,
    unavailableMessage: targetState.status === 'unavailable' ? targetState.message : null,
    focusItemId: null,
    focusRequestId: null,
  }
})

function notificationTargetRoute(target: NotificationTarget): MobileRoute {
  if (target.kind === 'homework') return homeworkNotificationRoute
  if (target.kind === 'request') return requestNotificationRoute
  return lessonNotificationRoute
}

function isCurrentNotificationTargetRequest(request: NotificationTargetIntent): boolean {
  const currentScope = scope.value
  return !disposed
    && props.notificationTargetIntent?.requestId === request.requestId
    && props.notificationTargetIntent.generation === request.generation
    && request.ownerKey === props.ownerKey
    && props.ownerKey !== null
    && props.api !== null
    && !offline.value
    && currentScope?.activeRole === 'STUDENT'
    && currentScope.resetGeneration === request.generation
    && ownerIdentity.value === studentFeatureScopeIdentity(currentScope)
}

function isActiveNotificationTargetRequest(request: NotificationTargetIntent): boolean {
  const state = notificationTargetState.value
  return isCurrentNotificationTargetRequest(request)
    && route.value.id === notificationTargetRoute(request.target).id
    && state?.requestId === request.requestId
    && state.target.kind === request.target.kind
}

function navigateToRequestNotificationTarget(): void {
  if (route.value.id !== requestNotificationRoute.id) {
    if (route.value.id !== requestRoute('overview').id) navigation.push(requestRoute('overview'))
    navigation.push(requestNotificationRoute)
  }
}

function loadHomeworkNotificationTarget(request: NotificationTargetIntent, navigate = true): void {
  if (request.target.kind !== 'homework' || !isCurrentNotificationTargetRequest(request)) return
  const target = request.target
  notificationTargetState.value = { kind: 'homework', requestId: request.requestId, target, status: 'loading' }
  if (navigate) navigation.push(homeworkNotificationRoute)
  void (async () => {
    try {
      const feed = await homework.openDate(target.lessonDate)
      if (!isActiveNotificationTargetRequest(request)) return
      if (!feed.items.some((item) => item.id === target.homeworkId
        && item.lessonDate === target.lessonDate)) {
        notificationTargetState.value = {
          kind: 'homework',
          requestId: request.requestId,
          target,
          status: 'unavailable',
          message: 'Это задание удалено или больше недоступно в твоей учебной группе.',
        }
        return
      }
      notificationTargetState.value = {
        kind: 'homework',
        requestId: request.requestId,
        target,
        status: 'available',
      }
    } catch (error) {
      if (!isActiveNotificationTargetRequest(request)) return
      const unavailable = isUnavailableHomeworkNotificationTarget(error, target.lessonDate)
      notificationTargetState.value = unavailable
        ? {
          kind: 'homework',
          requestId: request.requestId,
          target,
          status: 'unavailable',
          message: 'Это задание удалено или больше недоступно в твоей учебной группе.',
        }
        : {
          kind: 'homework',
          requestId: request.requestId,
          target,
          status: 'error',
          message: 'Не удалось загрузить это задание. Проверь подключение и попробуй ещё раз.',
        }
    }
  })()
}

function loadRequestNotificationTarget(request: NotificationTargetIntent, navigate = true): void {
  if (request.target.kind !== 'request' || !isCurrentNotificationTargetRequest(request)) return
  const target = request.target
  notificationTargetState.value = { kind: 'request', requestId: request.requestId, target, status: 'loading' }
  if (navigate) navigateToRequestNotificationTarget()
  void (async () => {
    try {
      const detail = await requests.loadTargetDetail(target.requestId, target.requestKind)
      if (!isActiveNotificationTargetRequest(request)) return
      notificationTargetState.value = { kind: 'request', requestId: request.requestId, target, status: 'available', detail }
    } catch (error) {
      if (!isActiveNotificationTargetRequest(request)) return
      const unavailable = error instanceof RequestsError && error.code === 'UNAVAILABLE'
        || error instanceof StudentApiError && (error.response.status === 403 || error.response.status === 404)
      if (error instanceof StudentApiError && error.response.status === 401) {
        handleRequestsError(error)
        return
      }
      notificationTargetState.value = unavailable
        ? { kind: 'request', requestId: request.requestId, target, status: 'unavailable', message: 'Эта заявка удалена или больше недоступна.' }
        : { kind: 'request', requestId: request.requestId, target, status: 'error', message: 'Не удалось загрузить заявку. Проверь подключение и попробуй ещё раз.' }
    }
  })()
}

function loadLessonNotificationTarget(request: NotificationTargetIntent, navigate = true): void {
  if (request.target.kind !== 'lesson' || !isCurrentNotificationTargetRequest(request)) return
  const target = request.target
  notificationTargetState.value = { kind: 'lesson', requestId: request.requestId, target, status: 'loading' }
  if (navigate) navigation.push(lessonNotificationRoute)
  void (async () => {
    try {
      if (!scope.value?.semesterId) {
        notificationTargetState.value = {
          kind: 'lesson',
          requestId: request.requestId,
          target,
          status: 'unavailable',
          message: 'Это занятие нельзя найти в текущем учебном семестре.',
        }
        return
      }
      await attendance.query.refetch({ throwOnError: true })
      if (!isActiveNotificationTargetRequest(request)) return
      const currentData = attendance.data.value
      const lesson = currentData ? findAttendanceLesson(currentData, target.lessonId) : null
      if (!lesson) {
        notificationTargetState.value = {
          kind: 'lesson',
          requestId: request.requestId,
          target,
          status: 'unavailable',
          message: 'Это занятие больше не входит в твою доступную посещаемость.',
        }
        return
      }
      attendanceSelectedDate.value = lesson.date
      attendanceMode.value = 'days'
      attendanceActionLessonId.value = null
      attendanceExpandedSubjectId.value = null
      notificationTargetState.value = {
        kind: 'lesson',
        requestId: request.requestId,
        target,
        status: 'available',
        lessonDate: lesson.date,
      }
    } catch (error) {
      if (!isActiveNotificationTargetRequest(request)) return
      const unavailable = error instanceof StudentApiError
        && (error.response.status === 403 || error.response.status === 404)
      if (error instanceof StudentApiError && error.response.status === 401) {
        handleRequestsError(error)
        return
      }
      notificationTargetState.value = unavailable
        ? { kind: 'lesson', requestId: request.requestId, target, status: 'unavailable', message: 'Это занятие больше не входит в твою доступную посещаемость.' }
        : { kind: 'lesson', requestId: request.requestId, target, status: 'error', message: 'Не удалось загрузить занятие. Проверь подключение и попробуй ещё раз.' }
    }
  })()
}

function loadNotificationTarget(request: NotificationTargetIntent, navigate = true): void {
  if (request.target.kind === 'homework') loadHomeworkNotificationTarget(request, navigate)
  else if (request.target.kind === 'request') loadRequestNotificationTarget(request, navigate)
  else loadLessonNotificationTarget(request, navigate)
}

function isUnavailableHomeworkNotificationTarget(error: unknown, targetDate: string): boolean {
  if (!(error instanceof StudentApiError)) return false
  const { response, problem } = error
  if (response.status === 403 || response.status === 404) return true
  if (response.status !== 400
    || problem?.code !== 'INVALID_REQUEST'
    || problem.detail !== 'Диапазон ДЗ должен входить в активный семестр') return false

  const semester = homework.query.data.value?.semester
  return !semester || targetDate < semester.dateFrom || targetDate > semester.dateTo
}

function retryHomeworkFeed(): void {
  const request = props.notificationTargetIntent
  if (activeHomeworkNotification.value?.status === 'error' && request
    && request.target.kind === 'homework'
    && isCurrentNotificationTargetRequest(request)) {
    loadHomeworkNotificationTarget(request, false)
    return
  }
  retryFeed()
}

function returnHomeworkToToday(): void {
  if (route.value.id === homeworkNotificationRoute.id) {
    const requestId = notificationTargetState.value?.requestId
    if (requestId !== undefined) emit('clearNotificationTarget', requestId)
    notificationTargetState.value = null
    homework.returnToToday()
    navigation.goRoot('homework')
    return
  }
  homework.returnToToday()
}

function backFromRequestNotificationTarget(): void {
  const requestId = notificationTargetState.value?.requestId
  if (requestId !== undefined) emit('clearNotificationTarget', requestId)
  notificationTargetState.value = null
  if (route.value.id === requestNotificationRoute.id) navigation.back()
}

function backFromLessonNotificationTarget(): void {
  const requestId = notificationTargetState.value?.requestId
  if (requestId !== undefined) emit('clearNotificationTarget', requestId)
  notificationTargetState.value = null
  if (route.value.id === lessonNotificationRoute.id) navigation.back()
}

function retryNotificationTarget(): void {
  const request = props.notificationTargetIntent
  if (request && isCurrentNotificationTargetRequest(request)) loadNotificationTarget(request, false)
}

watch(() => props.notificationTargetIntent, (request) => {
  if (request) {
    loadNotificationTarget(request)
    return
  }
  const state = notificationTargetState.value
  if (!state) return
  notificationTargetState.value = null
  if (route.value.id === notificationTargetRoute(state.target).id) {
    if (state.kind === 'homework' && homework.range.value !== null) homework.returnToToday()
    navigation.back()
  }
}, { flush: 'sync' })

watch(ownerIdentity, (identity, previous) => {
  if (identity === previous) return
  const requestId = notificationTargetState.value?.requestId
  notificationTargetState.value = null
  if (requestId !== undefined) emit('clearNotificationTarget', requestId)
  statistics.closeSubject()
  attendanceActionLessonId.value = null
  attendanceExpandedSubjectId.value = null
  const todayDate = new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })
  attendanceSelectedDate.value = todayDate
})
watch(
  () => attendance.data.value?.days,
  (days) => {
    if (!days || days.length === 0) return
    if (!days.some((day) => day.date === attendanceSelectedDate.value)) {
      attendanceSelectedDate.value = days[0]!.date
    }
  },
  { immediate: true },
)
const reportedAuthErrors = new Set<unknown>()
const profilePublication = createProfileViewPublication(
  () => profileState?.view ?? null,
  () => disposed,
)
const profileView = profilePublication.view
const publishProfileView = profilePublication.publish
const profileOwnerStatus = computed(() => profileOwnerStaleMessage(route.value, offline.value))
const requestDraft = computed(() => {
  const currentScope = scope.value
  return currentScope?.userId
    ? getOrCreateRequestsDraft(currentScope.userId, requestsSessionGeneration(currentScope))
    : null
})
const requestBucketView = computed(() => requests.activeBucket.value)
const requestHasNextPage = computed(() => requests.hasNextPage.value)
const requestOptions = computed(() => requests.view.options)
const requestLessons = computed(() => requestOptions.value?.lessons ?? [])
const requestReasons = computed(() => requestOptions.value?.reasons ?? [])
const requestFileLimits = computed(() => requestOptions.value?.files ?? null)
const requestBudget = computed(() => requestOptions.value?.budget ?? null)
const requestReadOnly = computed(() => props.readOnly || scope.value?.readOnly === true)
const requestSubmitError = ref<string | null>(null)
const requestCancellingId = ref<string | null>(null)
const requestObjectUrls = new Set<string>()
const requestAttachmentStates = reactive<Record<string, RequestAttachmentViewState>>({})
const requestAttachmentPopups = new Set<RequestAttachmentPopup>()
let requestAttachmentOwnerGeneration = 0

function requestAttachmentStateKey(requestId: string, attachmentId: string): string {
  return requestId + '\u0000' + attachmentId
}

function clearRequestAttachmentStates(): void {
  for (const key of Object.keys(requestAttachmentStates)) delete requestAttachmentStates[key]
}

function releaseRequestObjectUrl(objectUrl: string): void {
  if (!requestObjectUrls.delete(objectUrl)) return
  if (typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(objectUrl)
}

function closeRequestPopup(popup: RequestAttachmentPopup): void {
  if (!requestAttachmentPopups.delete(popup)) return
  try {
    popup.close?.()
  } catch {
    // A popup can disappear between the owner fence and teardown.
  }
}

const stopRequestAttachmentOwner = watch(
  ownerIdentity,
  (identity, previous) => {
    if (identity === previous) return
    requestAttachmentOwnerGeneration += 1
    clearRequestAttachmentStates()
    for (const popup of [...requestAttachmentPopups]) closeRequestPopup(popup)
    for (const objectUrl of [...requestObjectUrls]) releaseRequestObjectUrl(objectUrl)
  },
  { flush: 'sync' },
)

const requestTypeChoices = computed<RequestTypeChoice[]>(() => {
  const options = requestOptions.value
  const excuseAvailable = Boolean(options)
    && requestLessons.value.some((option) => option.excuseEligible === true && (option.pendingRequests?.length ?? 0) === 0)
    && requestReasons.value.length > 0
  const remaining = requestBudget.value?.remaining
  const lateAvailable = Boolean(options)
    && requestLessons.value.some((option) => option.lateCheckinEligible === true && (option.pendingRequests?.length ?? 0) === 0)
    && !(typeof remaining === 'number' && Number.isFinite(remaining) && remaining <= 0)
  return [
    {
      kind: 'EXCUSE',
      label: 'Уважительная причина',
      symbol: '✓',
      available: excuseAvailable,
      reason: options ? (excuseAvailable ? null : 'Подходящие пары или причины недоступны.') : 'Загружаем параметры заявок…',
    },
    {
      kind: 'LATE_CHECKIN',
      label: 'Забыл отметиться',
      symbol: 'н',
      available: lateAvailable,
      reason: options ? (lateAvailable ? null : 'Подходящие пары или попытки недоступны.') : 'Загружаем параметры заявок…',
    },
  ]
})
const requestFormKind = computed<RequestKind | null>(() => {
  if (route.value.id === 'more/requests/excuse') return 'EXCUSE'
  if (route.value.id === 'more/requests/late') return 'LATE_CHECKIN'
  return null
})
const requestAmbiguous = computed(() => {
  const kind = requestFormKind.value
  return kind !== null && requests.view.command?.kind === kind && requests.view.command.status === 'ambiguous'
})

function requestErrorText(value: unknown): string {
  return value instanceof Error && value.message ? value.message : 'Не удалось выполнить действие с заявкой.'
}

function requestAttachmentErrorText(value: unknown): string {
  if (value instanceof StudentApiError) return value.problem?.detail || value.message || 'Не удалось открыть вложение.'
  return requestErrorText(value)
}

function handleRequestsError(value: unknown): void {
  if (disposed) return
  const terminal = terminalAuthError(value)
  if (terminal) emit('ownerError', terminal)
}

function requestRoute(surface: 'overview' | 'type' | 'excuse' | 'late'): MobileRoute {
  if (surface === 'overview') return nestedRoute('more', 'more/requests', 'overview')
  if (surface === 'type') return nestedRoute('more', 'more/requests/type', 'detail')
  return nestedRoute('more', `more/requests/${surface}`, 'editor')
}

function updateRequestDraft(patch: Parameters<typeof updateRequestsDraft>[2]): void {
  const currentScope = scope.value
  if (!currentScope?.userId) return
  updateRequestsDraft(currentScope.userId, requestsSessionGeneration(currentScope), patch)
}

function ensureRequestsRoute(routeValue: MobileRoute): void {
  if (routeValue.root !== 'more' || disposed || !routeValue.id.startsWith('more/requests')) return
  if (routeValue.id === requestNotificationRoute.id) return
  const draft = requestDraft.value
  const bucket = draft?.bucket ?? 'open'
  if (routeValue.id === 'more/requests') {
    requests.selectBucket(bucket)
    const state = requests.bucketView(bucket)
    if (!offline.value && !state.loading && state.page < 0) void requests.load(bucket).catch(handleRequestsError)
    return
  }
  requests.selectBucket('open')
  ensureRequestOptions()
}

function ensureRequestOptions(): void {
  if (!shouldLoadRequestOptions(requests.view.options, requests.view.optionsLoading, offline.value)) return
  void requests.loadOptions().catch(handleRequestsError)
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

async function runProfile<T>(request: () => Promise<T>, options: { rethrow?: boolean } = {}): Promise<T | undefined> {
  if (!profileState || disposed) return undefined
  let pending: Promise<T>
  try {
    pending = request()
    // ProfileState mutates its plain view before the first await. Publish that
    // loading/busy transition as soon as the request has entered the state.
    publishProfileView()
  } catch {
    publishProfileView()
    return undefined
  }
  try {
    return await pending
  } catch (error) {
    // ProfileState retains the typed error for the rendered screen. Read
    // actions stay handled here; mutation callers that expose rejection to a
    // feature screen opt into rethrowing below.
    if (options.rethrow) throw error
    return undefined
  } finally {
    publishProfileView()
    await profileState.waitForAutomaticStaleReload()
    publishProfileView()
  }
}

async function loadProfileSnapshot(): Promise<void> {
  if (!canRunProfileNetworkAction(offline.value)) return
  const loaded = await runProfile(() => profileState!.loadSnapshot())
  if (loaded && !disposed) ensureProfileRoute(route.value)
}

async function openProfileArea(area: 'sessions' | 'history'): Promise<void> {
  if (!profileState || disposed || !canRunProfileNetworkAction(offline.value)) return
  if (!profileState.view.snapshot) {
    await runProfile(() => profileState!.loadSnapshot())
    if (disposed || !profileState.view.snapshot) return
  }
  if (area === 'sessions') {
    await runProfile(() => profileState.loadSessions())
  } else {
    await runProfile(() => profileState.loadHistory())
  }
}

function retryProfileSnapshot(): void {
  void loadProfileSnapshot()
}

function retryProfileArea(area: 'sessions' | 'history'): void {
  void openProfileArea(area)
}

function loadMoreProfile(area: 'sessions' | 'history', cursor: string): void {
  if (!profileState || disposed || !canRunProfileNetworkAction(offline.value)) return
  if (area === 'sessions') {
    void runProfile(() => profileState.loadSessions({ cursor }))
  } else {
    void runProfile(() => profileState.loadHistory({ cursor }))
  }
}

function profileRoute(routeName: ProfileRoute): MobileRoute | null {
  if (routeName === 'role-switch') return nestedRoute('profile', 'profile/role-switch', 'detail')
  if (routeName === 'appearance') return nestedRoute('profile', 'profile/appearance', 'detail')
  if (routeName === 'security') return nestedRoute('profile', 'profile/security', 'detail')
  if (routeName === 'sessions') return nestedRoute('profile', 'profile/sessions', 'detail')
  if (routeName === 'history') return nestedRoute('profile', 'profile/history', 'detail')
  return null
}

function navigateProfile(routeName: ProfileRoute): void {
  if (routeName === 'profile') {
    navigation.goRoot('profile')
    return
  }
  const target = profileRoute(routeName)
  if (!target) return
  navigation.push(target)
}

function backProfile(): void {
  navigation.back()
}

async function selectProfileRole(role: ProfileRole, expectedSessionVersion: string): Promise<void> {
  if (!profileState && !props.profileRoleSelect) return
  profilePendingRole.value = role
  try {
    await runProfile(async () => {
      if (props.profileRoleSelect) await props.profileRoleSelect(role, expectedSessionVersion)
      else await profileState!.selectRole(role)
    }, { rethrow: true })
  } catch (error) {
    if (!props.profileRoleSelect) emit('ownerError', error)
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
  if (!profileState || disposed) return
  await runProfile(() => profileState.changePassword(input), { rethrow: true })
}

async function logoutProfileAll(): Promise<void> {
  if (!profileState || disposed) return
  await runProfile(() => profileState.logoutAll())
}

function ensureProfileRoute(routeValue: MobileRoute): void {
  if (routeValue.root !== 'profile' || !profileState || disposed) return
  if (!profileState.view.snapshot) {
    if (profileState.view.snapshotStatus === 'loading') return
    if (routeValue.id === 'profile/sessions' || routeValue.id === 'profile/history') {
      void openProfileArea(routeValue.id === 'profile/sessions' ? 'sessions' : 'history')
    } else {
      void loadProfileSnapshot()
    }
    return
  }
  if (routeValue.id === 'profile/sessions' && profileState.view.sessionsStatus === 'idle') {
    void openProfileArea('sessions')
  }
  if (routeValue.id === 'profile/history' && profileState.view.historyStatus === 'idle') {
    void openProfileArea('history')
  }
}

function terminalAuthError(value: unknown): unknown | null {
  const status = value instanceof StudentApiError
    ? value.response.status
    : typeof value === 'object' && value !== null && 'status' in value && typeof value.status === 'number'
      ? value.status
      : null
  return status === 401 || status === 403 ? value : null
}

watch(
  [() => today.query.error.value, () => homework.query.error.value],
  ([todayError, homeworkError]) => {
    if (disposed || !scope.value) return
    const homeworkStatus = homeworkError instanceof StudentApiError ? homeworkError.response.status : null
    const targetUnavailable = activeHomeworkNotification.value?.status === 'loading'
      && (homeworkStatus === 403 || homeworkStatus === 404)
    const error = terminalAuthError(todayError) ?? (targetUnavailable ? null : terminalAuthError(homeworkError))
    if (!error || reportedAuthErrors.has(error)) return
    reportedAuthErrors.add(error)
    emit('ownerError', error)
  },
  { flush: 'sync' },
)

watch(
  () => [attendance.query.error.value, statistics.query.error.value, statistics.detailQuery.error.value] as const,
  (errors) => {
    if (disposed || !scope.value) return
    const error = errors.map((value) => terminalAuthError(value)).find((value): value is unknown => value !== null) ?? null
    if (!error || reportedAuthErrors.has(error)) return
    reportedAuthErrors.add(error)
    emit('ownerError', error)
  },
  { flush: 'sync' },
)

watch(
  () => homework.query.data.value,
  (value) => {
    if (value && scope.value && homework.range.value === null) emit('homeworkLoaded', value)
  },
)

function navigateMore(routeName: ProfileRoute): void {
  if (routeName === 'statistics') {
    navigation.push(nestedRoute('more', 'more/statistics', 'overview'))
    return
  }
  if (routeName === 'map') {
    if (!props.mapClient || offline.value) return
    navigation.push(nestedRoute('more', 'more/map', 'overview'))
    return
  }
  if (routeName === 'assistant') {
    if (!props.assistantPermissions?.length || offline.value) return
    navigation.push(nestedRoute('more', 'more/assistant', 'task'))
    return
  }
  if (routeName !== 'requests') return
  requests.selectBucket('open')
  updateRequestDraft({ bucket: 'open', view: 'inbox' })
  navigation.push(requestRoute('overview'))
}

function retryAttendance(): void {
  if (offline.value) return
  void attendance.query.refetch()
}

function setAttendanceMode(mode: AttendanceMode): void {
  attendanceMode.value = mode
  attendanceActionLessonId.value = null
}

function toggleAttendanceSubject(subjectId: string): void {
  attendanceExpandedSubjectId.value = attendanceExpandedSubjectId.value === subjectId ? null : subjectId
}

function toggleAttendanceActions(lessonId: string): void {
  attendanceActionLessonId.value = attendanceActionLessonId.value === lessonId ? null : lessonId
}

function openAttendanceRequest(lesson: AttendanceLesson, option: { kind: 'EXCUSE' | 'LATE_CHECKIN'; enabled: boolean }): void {
  if (offline.value || props.readOnly || !option.enabled) return
  attendanceActionLessonId.value = null
  requests.selectBucket('open')
  requestSubmitError.value = null
  if (option.kind === 'EXCUSE') {
    updateRequestDraft({
      bucket: 'open',
      view: 'excuse',
      excuseLessonIds: [lesson.id],
      excuseReason: null,
      excuseComment: '',
      excuseFiles: [],
    })
    navigation.push(requestRoute('excuse'))
  } else {
    updateRequestDraft({ bucket: 'open', view: 'late', lateLessonId: lesson.id })
    navigation.push(requestRoute('late'))
  }
  ensureRequestOptions()
}

function retryStatistics(): void {
  if (offline.value) return
  void statistics.query.refetch()
}

function openStatisticsSubject(subjectId: string): void {
  statistics.openSubject(subjectId)
}

function backStatistics(): void {
  if (statistics.selectedSubjectId.value !== null) {
    statistics.closeSubject()
    return
  }
  navigation.back()
}

function retryStatisticsDetail(): void {
  if (offline.value) return
  void statistics.detailQuery.refetch()
}

function newRequest(): void {
  if (requestReadOnly.value || requests.view.access !== 'allowed') return
  requests.selectBucket('open')
  requestSubmitError.value = null
  updateRequestDraft({ bucket: 'open', view: 'type' })
  navigation.push(requestRoute('type'))
  ensureRequestOptions()
}

function chooseRequestKind(kind: RequestKind): void {
  if (requestReadOnly.value || requests.view.access !== 'allowed') return
  requestSubmitError.value = null
  updateRequestDraft({ view: kind === 'EXCUSE' ? 'excuse' : 'late' })
  navigation.push(requestRoute(kind === 'EXCUSE' ? 'excuse' : 'late'))
  ensureRequestOptions()
}

function backRequests(): void {
  requestSubmitError.value = null
  if (route.value.id === 'more/requests/type') updateRequestDraft({ view: 'inbox' })
  else if (route.value.id === 'more/requests/excuse' || route.value.id === 'more/requests/late') updateRequestDraft({ view: 'type' })
  navigation.back()
}

function selectRequestBucket(bucket: RequestBucket): void {
  requestSubmitError.value = null
  updateRequestDraft({ bucket, view: 'inbox' })
  requests.selectBucket(bucket)
  if (!offline.value) void requests.load(bucket).catch(handleRequestsError)
}

function retryRequests(): void {
  if (offline.value) return
  void requests.load(requests.view.bucket).catch(handleRequestsError)
}

function loadMoreRequests(): void {
  if (offline.value) return
  void requests.loadMore(requests.view.bucket).catch(handleRequestsError)
}

function cancelRequest(id: string): void {
  if (requestCancellingId.value) return
  requestCancellingId.value = id
  void requests.cancelRequest(id)
    .catch(handleRequestsError)
    .finally(() => {
      if (!disposed) requestCancellingId.value = null
    })
}

function openRequestAttachment(value: { requestId: string; attachment: { id: string } }): void {
  if (offline.value || disposed) return
  const ownerIdentityAtStart = ownerIdentity.value
  const ownerGenerationAtStart = requestAttachmentOwnerGeneration
  const key = requestAttachmentStateKey(value.requestId, value.attachment.id)
  if (requestAttachmentStates[key]?.status === 'pending') return
  runRequestAttachmentOpen({
    ownerIdentity: ownerIdentityAtStart,
    ownerGeneration: ownerGenerationAtStart,
    currentOwnerIdentity: () => ownerIdentity.value,
    currentOwnerGeneration: () => requestAttachmentOwnerGeneration,
    isDisposed: () => disposed,
    openPopup: () => {
      if (typeof window === 'undefined') return null
      return openRequestAttachmentPopup(window)
    },
    download: () => requests.downloadAttachment(value.requestId, value.attachment.id),
    createObjectUrl: (blob) => {
      if (typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') throw new Error('Не удалось подготовить вложение.')
      const objectUrl = URL.createObjectURL(blob)
      requestObjectUrls.add(objectUrl)
      return objectUrl
    },
    releaseObjectUrl: releaseRequestObjectUrl,
    scheduleRelease: (objectUrl) => {
      if (typeof window === 'undefined') {
        releaseRequestObjectUrl(objectUrl)
        return
      }
      window.setTimeout(() => releaseRequestObjectUrl(objectUrl), 60_000)
    },
    navigate: (popup, objectUrl) => {
      popup.location.href = objectUrl
    },
    closePopup: closeRequestPopup,
    onPopupOpened: (popup) => requestAttachmentPopups.add(popup),
    onPopupNavigated: (popup) => requestAttachmentPopups.delete(popup),
    setState: (state) => {
      if (!disposed
        && ownerIdentityAtStart === ownerIdentity.value
        && ownerGenerationAtStart === requestAttachmentOwnerGeneration) requestAttachmentStates[key] = state
    },
    onError: handleRequestsError,
    errorMessage: requestAttachmentErrorText,
  })
}

function updateRequestLessonIds(value: string[]): void {
  updateRequestDraft({ excuseLessonIds: [...value] })
}

function updateRequestReason(value: string | null): void {
  requestSubmitError.value = null
  updateRequestDraft({ excuseReason: value })
}

function updateRequestComment(value: string): void {
  requestSubmitError.value = null
  updateRequestDraft({ excuseComment: value })
}

function updateRequestFiles(value: RequestFileRef[]): void {
  updateRequestDraft({ excuseFiles: value })
}

function updateLateLesson(value: string | null): void {
  requestSubmitError.value = null
  updateRequestDraft({ lateLessonId: value })
}

function abandonRequestCommand(): void {
  const kind = requestFormKind.value
  if (!kind) return
  requests.abandonCommand(kind)
  requestSubmitError.value = null
}

async function submitExcuse(payload: Parameters<typeof requests.submitExcuse>[0]): Promise<void> {
  requestSubmitError.value = null
  try {
    await requests.submitExcuse(payload)
    updateRequestDraft({ view: 'inbox', excuseLessonIds: [], excuseReason: null, excuseComment: '', excuseFiles: [] })
    requests.selectBucket('open')
    void attendance.query.refetch()
    navigation.replace(requestRoute('overview'))
  } catch (error) {
    requestSubmitError.value = requestErrorText(error)
    handleRequestsError(error)
  }
}

async function submitLateCheckin(payload: Parameters<typeof requests.submitLateCheckin>[0]): Promise<void> {
  requestSubmitError.value = null
  try {
    await requests.submitLateCheckin(payload)
    updateRequestDraft({ view: 'inbox', lateLessonId: null })
    requests.selectBucket('open')
    void attendance.query.refetch()
    navigation.replace(requestRoute('overview'))
  } catch (error) {
    requestSubmitError.value = requestErrorText(error)
    handleRequestsError(error)
  }
}

function navigate(next: MobileRootRouteId): void {
  navigation.goRoot(next)
}

function checkin(lesson: TodayLesson): void {
  const currentScope = scope.value
  if (offline.value || props.readOnly || disposed || !currentScope) return
  const requestScope = { ...currentScope }
  const requestIdentity = studentFeatureScopeIdentity(requestScope)
  void checkinRecovery.execute(
    lesson.schedule.id,
    props.acquireCheckinCommand,
    (attempt) => {
      if (disposed || offline.value || props.readOnly || ownerIdentity.value !== requestIdentity) {
        return Promise.reject(new Error('Отметка относится к завершившейся сессии'))
      }
      return today.mutation.mutateAsync({ ...attempt, scope: requestScope })
    },
  ).catch((error: unknown) => {
    if (!disposed) emit('ownerError', error)
  })
}

function complete(item: StudentHomeworkItem, completed: boolean): void {
  if (disposed || offline.value || props.readOnly) return
  void homework.submitCompletion(item.id, completed).catch((error: unknown) => {
    if (!disposed) emit('ownerError', error)
  })
}

function retry(item: StudentHomeworkItem): void {
  if (disposed || offline.value || props.readOnly) return
  void homework.retryCompletion(item.id)?.catch((error: unknown) => {
    if (!disposed) emit('ownerError', error)
  })
}

function retryFeed(): void {
  if (!offline.value) void homework.query.refetch()
}

function openMaterialFromItem(url: string, item: StudentHomeworkItem): void {
  props.openMaterial(url, item)
}

onBeforeUnmount(() => {
  disposed = true
  const currentScope = scope.value
  if (currentScope?.userId) purgeRequestsDrafts(currentScope.userId)
  for (const popup of [...requestAttachmentPopups]) closeRequestPopup(popup)
  for (const objectUrl of [...requestObjectUrls]) releaseRequestObjectUrl(objectUrl)
  clearRequestAttachmentStates()
  scope.value = null
  stopScope()
  stopRequestsScope()
  stopRequestAttachmentOwner()
  stopNavigation()
  stopTheme()
})
</script>

<template>
  <TodayScreen
    v-if="route.root === 'today'"
    :today="displayToday"
    :loading="todayLoading"
    :error="todayError"
    :offline="offline"
    :updated-at="updatedAt"
    :submitting-lesson-id="today.mutation.isPending.value ? today.mutation.variables.value?.lessonId ?? null : null"
    :semester-schedule="semesterSchedule"
    :selected-date="selectedDate"
    :nav-items="navItems"
    :route="route"
    :navigation="navigation"
    :active-id="'today'"
    :host="host"
    :read-only="readOnly || offline"
    @checkin="checkin"
    @select-date="selectedDate = $event"
    @navigate="navigate"
  />
  <HomeworkScreen
    v-else-if="route.root === 'homework'"
    :homework="homeworkScreenData.homework"
    :loading="homeworkScreenData.loading"
    :error="homeworkScreenData.error"
    :focus-item-id="homeworkScreenData.focusItemId"
    :focus-request-id="homeworkScreenData.focusRequestId"
    :unavailable-message="homeworkScreenData.unavailableMessage"
    :nav-items="navItems"
    :offline="offline"
    :read-only="readOnly"
    :updated-at="updatedAt"
    :route="route"
    :navigation="navigation"
    :active-id="'homework'"
    :host="host"
    :owner-key="ownerIdentity"
    :historical="homework.isHistorical.value"
    :can-load-previous="homework.canLoadPrevious.value"
    :is-item-pending="homework.isPending"
    :item-error="homework.itemError"
    @complete="complete"
    @retry="retry"
    @retry-feed="retryHomeworkFeed"
    @open-material="openMaterialFromItem"
    @previous="homework.loadPrevious"
    @return-today="returnHomeworkToToday"
    @navigate="navigate"
    @back="navigation.back()"
  />
  <MobileShell
    v-else-if="route.root === 'attendance'"
    :route="route"
    :navigation="navigation"
    :nav-items="navItems"
    :active-id="'attendance'"
    :host="host"
  >
    <template #back />
    <main
      v-if="activeLessonNotification && activeLessonNotification.status !== 'available'"
      class="attendance-screen"
    >
      <section
        class="attendance-state"
        :class="{ 'attendance-state--error': activeLessonNotification.status === 'error' }"
        :role="activeLessonNotification.status === 'error' ? 'alert' : 'status'"
        aria-live="polite"
      >
        <button
          class="attendance-back-button"
          type="button"
          @click="backFromLessonNotificationTarget"
        >
          Назад к посещаемости
        </button>
        <span
          v-if="activeLessonNotification.status === 'loading'"
          class="attendance-state__spinner"
          aria-hidden="true"
        />
        <h2>
          {{ activeLessonNotification.status === 'loading' ? 'Открываем занятие' : activeLessonNotification.status === 'unavailable' ? 'Занятие недоступно' : 'Не удалось загрузить занятие' }}
        </h2>
        <p v-if="activeLessonNotification.status !== 'loading'">
          {{ activeLessonNotification.message }}
        </p>
        <button
          v-if="activeLessonNotification.status === 'error'"
          class="attendance-state__retry"
          type="button"
          @click="retryNotificationTarget"
        >
          Повторить
        </button>
      </section>
    </main>
    <AttendanceScreen
      v-else
      :state="attendance.state.value"
      :selected-date="attendanceSelectedDate"
      :mode="attendanceMode"
      :graph-range="attendanceGraphRange"
      :expanded-subject-id="attendanceExpandedSubjectId"
      :action-lesson-id="attendanceActionLessonId"
      :terminal="offline || props.readOnly || attendance.terminalReadOnly.value"
      :focused-lesson-id="focusedNotificationLessonId"
      :theme="resolvedTheme"
      @select-date="attendanceSelectedDate = $event"
      @set-mode="setAttendanceMode"
      @set-graph-range="attendanceGraphRange = $event"
      @toggle-subject="toggleAttendanceSubject"
      @toggle-actions="toggleAttendanceActions"
      @open-request="openAttendanceRequest"
      @retry="retryAttendance"
    />
  </MobileShell>
  <MobileShell
    v-else-if="route.root === 'profile'"
    :route="route"
    :navigation="navigation"
    :nav-items="navItems"
    :active-id="'profile'"
    :host="host"
  >
    <template #back />
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
      v-if="route.kind === 'root'"
      :snapshot="profileView.snapshot"
      :loading="profileView.snapshotStatus === 'loading'"
      :error="profileView.snapshotError"
      :theme="resolvedTheme"
      :on-retry="retryProfileSnapshot"
      :on-navigate="navigateProfile"
    />
    <RoleSwitchScreen
      v-else-if="route.id === 'profile/role-switch'"
      :snapshot="profileView.snapshot"
      :pending-role="profilePendingRole"
      :error="profileView.snapshotError"
      :loading="profileView.snapshotStatus === 'loading'"
      :offline="offline"
      :theme="resolvedTheme"
      :on-back="backProfile"
      :on-select-role="selectProfileRole"
    />
    <AppearanceScreen
      v-else-if="route.id === 'profile/appearance'"
      :theme="themeMode"
      :resolved-theme="resolvedTheme"
      :on-back="backProfile"
      :on-theme-change="changeProfileTheme"
    />
    <SecurityScreen
      v-else-if="route.id === 'profile/security'"
      :policy="profileView.snapshot?.passwordPolicy ?? DEFAULT_PASSWORD_POLICY"
      :error="profileView.error"
      :busy="profileView.mutationBusy === 'password'"
      :offline="offline || profileState === null"
      :theme="resolvedTheme"
      :on-back="backProfile"
      :on-change-password="changeProfilePassword"
    />
    <SessionsScreen
      v-else-if="route.id === 'profile/sessions'"
      :sessions="profileView.sessions"
      :loading="profileView.sessionsStatus === 'loading'"
      :error="profileView.sessionsError ?? profileView.error"
      :next-cursor="profileView.sessionsNextCursor"
      :busy="profileView.mutationBusy === 'logout-all'"
      :offline="offline || profileState === null"
      :theme="resolvedTheme"
      :on-back="backProfile"
      :on-retry="() => retryProfileArea('sessions')"
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
      :on-back="backProfile"
      :on-retry="() => retryProfileArea('history')"
      :on-load-more="(cursor) => loadMoreProfile('history', cursor)"
    />
  </MobileShell>
  <MobileShell
    v-else-if="route.root === 'more'"
    :route="route"
    :navigation="navigation"
    :nav-items="navItems"
    :active-id="'more'"
    :host="host"
  >
    <template #back />
    <MoreScreen
      v-if="route.kind === 'root'"
      :theme="resolvedTheme"
      :map-enabled="Boolean(props.mapClient) && !offline"
      :assistant-enabled="Boolean(props.assistantPermissions?.length) && !offline"
      :on-navigate="navigateMore"
    />
    <AssistantActionsScreen
      v-else-if="route.id === 'more/assistant'"
      :permissions="assistantPermissions ?? []"
      :group-id="scope?.groupId ? Number(scope.groupId) : null"
      :journal-api="assistantJournalApi ?? null"
      :stats-api="assistantStatsApi ?? null"
      :requests-api="assistantRequestsApi ?? null"
      :homework-api="assistantHomeworkApi ?? null"
      :user-id="scope?.userId ? Number(scope.userId) : null"
      :offline="offline"
      :read-only="props.readOnly"
      :report-download="reportDownload"
      @error="emit('ownerError', $event)"
    />
    <StatisticsScreen
      v-else-if="route.id === 'more/statistics'"
      :state="statistics.overviewState.value"
      :selected-subject-id="statistics.selectedSubjectId.value"
      :detail-state="statistics.detailState.value"
      :range="statistics.range.value"
      :terminal="offline || props.readOnly"
      :theme="resolvedTheme"
      @open-subject="openStatisticsSubject"
      @set-range="statistics.setRange"
      @set-types="statistics.setTypes"
      @back="backStatistics"
      @retry="retryStatistics"
      @detail-retry="retryStatisticsDetail"
    />
    <MapScreen
      v-else-if="route.id === 'more/map' && props.mapClient"
      :client="props.mapClient"
      :theme="resolvedTheme"
    />
    <RequestsScreen
      v-else-if="route.id === 'more/requests' || route.id === requestNotificationRoute.id"
      :bucket="requests.view.bucket"
      :requests="requestBucketView.requests"
      :loading="requestBucketView.loading"
      :error="requestBucketView.error"
      :offline="offline"
      :read-only="requestReadOnly"
      :access="requests.view.access"
      :cancelling-id="requestCancellingId"
      :has-next-page="requestHasNextPage"
      :loading-more="requestBucketView.loadingMore"
      :attachment-states="requestAttachmentStates"
      :notification-target="requestNotificationView"
      @select-bucket="selectRequestBucket"
      @new-request="newRequest"
      @retry="retryRequests"
      @load-more="loadMoreRequests"
      @cancel="cancelRequest"
      @open-attachment="openRequestAttachment"
      @back-target="backFromRequestNotificationTarget"
      @retry-target="retryNotificationTarget"
    />
    <RequestTypeScreen
      v-else-if="route.id === 'more/requests/type'"
      :choices="requestTypeChoices"
      :busy="requests.view.optionsLoading"
      :error="requests.view.optionsError"
      @back="backRequests"
      @choose="chooseRequestKind"
    />
    <ExcuseRequestScreen
      v-else-if="route.id === 'more/requests/excuse'"
      :access="requests.view.access"
      :lessons="requestLessons"
      :lesson-ids="requestDraft?.excuseLessonIds ?? []"
      :reasons="requestReasons"
      :reason="requestDraft?.excuseReason ?? null"
      :comment="requestDraft?.excuseComment ?? ''"
      :files="requestDraft?.excuseFiles ?? []"
      :file-limits="requestFileLimits"
      :lessons-loading="requests.view.optionsLoading"
      :lessons-error="requests.view.optionsError"
      :offline="offline"
      :submitting="requests.view.mutation === 'submitting'"
      :submit-error="requestSubmitError || requests.view.mutationError"
      :disabled="requestReadOnly"
      :ambiguous="requestAmbiguous"
      @back="backRequests"
      @retry-lessons="() => requests.loadOptions().catch(handleRequestsError)"
      @update:lesson-ids="updateRequestLessonIds"
      @update:reason="updateRequestReason"
      @update:comment="updateRequestComment"
      @update:files="updateRequestFiles"
      @abandon="abandonRequestCommand"
      @submit="submitExcuse"
    />
    <LateCheckinRequestScreen
      v-else-if="route.id === 'more/requests/late'"
      :access="requests.view.access"
      :lessons="requestLessons"
      :lesson-id="requestDraft?.lateLessonId ?? null"
      :budget="requestBudget"
      :lessons-loading="requests.view.optionsLoading"
      :lessons-error="requests.view.optionsError"
      :offline="offline"
      :submitting="requests.view.mutation === 'submitting'"
      :submit-error="requestSubmitError || requests.view.mutationError"
      :disabled="requestReadOnly"
      :ambiguous="requestAmbiguous"
      @back="backRequests"
      @cancel="backRequests"
      @retry-lessons="() => requests.loadOptions().catch(handleRequestsError)"
      @update:lesson-id="updateLateLesson"
      @abandon="abandonRequestCommand"
      @submit="submitLateCheckin"
    />
  </MobileShell>
</template>

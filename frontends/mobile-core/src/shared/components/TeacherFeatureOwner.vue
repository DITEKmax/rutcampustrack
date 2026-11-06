<script setup lang="ts">
import { provideProjectionRevision, useProjectionRefresh } from '../projection-refresh';
import type { NotificationAction } from '../../features/notifications/notifications-v2-models';
import { notificationNumericId } from '../../features/notifications/notifications-actions';
import { computed, inject, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import type { CampusMapClient } from '../../api/map-client';
import MapScreen from '../../features/map/MapScreen.vue';
import AccountHistoryScreen from '../../features/profile/AccountHistoryScreen.vue';
import AppearanceScreen from '../../features/profile/AppearanceScreen.vue';
import BrowserLoginScreen from '../../features/profile/BrowserLoginScreen.vue';
import ProfileScreen from '../../features/profile/ProfileScreen.vue';
import { ProfileState } from '../../features/profile/profile-state';
import RoleSwitchScreen from '../../features/profile/RoleSwitchScreen.vue';
import SecurityScreen from '../../features/profile/SecurityScreen.vue';
import SessionsScreen from '../../features/profile/SessionsScreen.vue';
import { DEFAULT_PASSWORD_POLICY, ProfileRequestError } from '../../features/profile/profile-types';
import type {
  ProfilePort,
  ProfileRole,
  ProfileRoute,
  ProfileSnapshot,
  ProfileTheme,
} from '../../features/profile/profile-types';
import type {
  TeacherApi,
  TeacherJournalQuery,
  TeacherCatalogResponse,
  TeacherSemester,
  TeacherStatsSubjectOption,
} from '../../features/teacher/teacher-client';
import { teacherJournalTypes } from '../../features/teacher/teacher-client';
import type { HeadmanLessonSlot } from '../../features/schedule/headman-schedule-client';
import TeacherManualExcuseScreen from '../../features/teacher/TeacherManualExcuseScreen.vue';
import TeacherGroupSubjectsScreen from '../../features/teacher/TeacherGroupSubjectsScreen.vue';
import TeacherExcuseScreen from '../../features/teacher/TeacherExcuseScreen.vue';
import TeacherAttendanceScreen from '../../features/teacher/TeacherAttendanceScreen.vue';
import TeacherHomeScreen from '../../features/teacher/TeacherHomeScreen.vue';
import TeacherJournalScreen from '../../features/teacher/TeacherJournalScreen.vue';
import TeacherLessonScreen from '../../features/teacher/TeacherLessonScreen.vue';
import TeacherStatsScreen from '../../features/teacher/TeacherStatsScreen.vue';
import {
  activateTeacherStatsRoute,
  clearTeacherStatsRoute,
  hasTeacherStatsRoute,
} from '../../features/teacher/teacher-stats-route';
import MobileShell from './MobileShell.vue';
import MobileIcon from './MobileIcon.vue';
import StudentWarningBlock from './StudentWarningBlock.vue';
import { profileOwnerStaleMessage } from '../profile-owner-status';
import { createMobileNavigationStack, nestedRoute, rootRoute } from '../navigation';
import type { MobileNavigationStack, MobileRootRouteId, MobileRoute } from '../navigation';
import { createTeacherNavigationItems } from '../mobile-navigation-items';
import type { MobileHostAdapter } from '../host';
import {
  createMobileTheme,
  mobileThemeSystemSourceKey,
  type MobileThemeController,
} from '../theme';
import type { ReportDownloadPort } from '../report-download-client';
import { createProfileViewPublication } from './profile-view-publication';
import './teacher-feature-owner.pcss';

const props = withDefaults(
  defineProps<{
    unreadCount?: number | null | undefined;
    onNotifications?: (() => void) | undefined;
    api: TeacherApi | null;
    realtimeRevision?: number;
    semesterId: number | null;
    mapClient?: CampusMapClient | null;
    selectedDate?: string;
    reportDownload?: ReportDownloadPort | null;
    profile?: ProfileSnapshot | null;
    profilePort?: ProfilePort | null;
    onRecover?: (() => void | Promise<void>) | undefined;
    onLogout?: (() => void | Promise<void>) | undefined;
    profileRoleSelect?:
      | ((role: ProfileRole, expectedSessionVersion: string) => void | Promise<void>)
      | undefined;
    host?: MobileHostAdapter | null;
    themeController?: MobileThemeController | null;
    offline?: boolean;
  }>(),
  {
    realtimeRevision: 0,
    unreadCount: null,
    onNotifications: undefined,
    selectedDate: '',
    mapClient: null,
    reportDownload: null,
    profile: null,
    profilePort: null,
    onRecover: undefined,
    onLogout: undefined,
    profileRoleSelect: undefined,
    host: null,
    themeController: null,
    offline: false,
  },
);

const emit = defineEmits<{
  ownerError: [cause: unknown];
}>();

type ProfileArea = 'sessions' | 'history';
type ProfileAccountRoute = Extract<
  ProfileRoute,
  'role-switch' | 'browser-login' | 'appearance' | 'security' | 'sessions' | 'history'
>;

const navigation: MobileNavigationStack = createMobileNavigationStack(
  rootRoute(hasTeacherStatsRoute() ? 'teacher-stats' : 'teacher-home'),
);
const route = ref<MobileRoute>(navigation.current);
const selectedDate = ref(props.selectedDate || moscowToday());
const lessonId = ref<number | null>(null);
const journalQuery = ref<TeacherJournalQuery | null>(null);
const journalVisited = ref(false);
const journalEntry = ref<'taught' | 'history'>('history');
const attendanceQuery = ref<TeacherJournalQuery | null>(null);
const attendanceVisited = ref(false);
const requestId = ref<string | null>(null);
const manualStudentId = ref<number | null>(null);
const manualLessonId = ref<number | null>(null);
const selectedGroup = ref<{ id: number; name: string } | null>(null);
const catalog = shallowRef<TeacherCatalogResponse | null>(null);
const catalogVerified = ref(false);
const currentJournalSubjects = computed(() =>
  catalogVerified.value ? catalog.value?.taughtSubjectOptions ?? [] : [],
);
const semester = shallowRef<TeacherSemester | null>(null);
const effectiveSemesterId = computed(() => semester.value?.id ?? null);
const lessonSlots = shallowRef<readonly HeadmanLessonSlot[]>([]);
const teacherContextError = ref<string | null>(null);
const teacherContextLoading = ref(false);
const semesterChecking = ref(false);
const semesterCheckFailed = ref(false);
const ownerGeneration = ref(0);
const projectionRevision = ref(0);
const todayProjectionRevision = ref(0);
const localContextRevision = ref(0);
const contextRevision = computed(() => props.realtimeRevision + localContextRevision.value);
const calendarOpen = ref(false);
const calendarCloseSignal = ref(0);
const exportOpen = ref(false);
const exportOpenSignal = ref(0);
const exportCloseSignal = ref(0);
const attachmentOpen = ref(false);
const attachmentCloseSignal = ref(0);
const statsToolOpen = ref(false);
const statsToolCloseSignal = ref(0);
const profileState = shallowRef(props.profilePort ? new ProfileState(props.profilePort) : null);
const profilePendingRole = ref<ProfileRole | null>(null);
const profileRoleError = shallowRef<ProfileRequestError | null>(null);
const themeMode = ref<ProfileTheme>(props.themeController?.mode ?? 'system');
const systemThemeSource = inject(mobileThemeSystemSourceKey, undefined);
const scopedTheme = createMobileTheme({
  target: null,
  mode: themeMode.value,
  ...(systemThemeSource === undefined ? {} : { systemSource: systemThemeSource }),
});
const resolvedTheme = ref(scopedTheme.resolvedMode);
const stopScopedTheme = scopedTheme.subscribe(({ resolvedMode }) => {
  resolvedTheme.value = resolvedMode;
});
watch(themeMode, (mode) => scopedTheme.setMode(mode), { flush: 'sync' });
const navItems = computed(() => createTeacherNavigationItems(props.profilePort !== null));
const profilePublication = createProfileViewPublication(
  () => profileState.value?.view ?? null,
  () => disposed,
);
const profileView = profilePublication.view;
const publishProfileView = profilePublication.publish;
const profileOwnerStatus = computed(() => profileOwnerStaleMessage(route.value, props.offline));
function profileIdentity(profile: ProfileSnapshot): string {
  return JSON.stringify([
    profile.userId,
    profile.sessionId,
    profile.sessionVersion,
    profile.rolesVersion,
  ]);
}
const profileOwnerKey = computed(() =>
  props.profile?.activeRole === 'TEACHER' ? profileIdentity(props.profile) : null,
);
const verifiedProfileOwnerKey = computed(() => {
  const ownerKey = profileOwnerKey.value;
  const { snapshot, snapshotStatus } = profileView.value;
  if (
    !ownerKey ||
    !props.profilePort?.isOnline() ||
    props.offline ||
    props.profile?.readOnly ||
    snapshotStatus !== 'ready' ||
    snapshot?.activeRole !== 'TEACHER' ||
    snapshot.readOnly ||
    profileIdentity(snapshot) !== ownerKey
  )
    return null;
  return ownerKey;
});
const browserLoginApproval = computed(() => {
  const port = props.profilePort?.browserLoginApproval;
  if (
    !verifiedProfileOwnerKey.value ||
    !port?.isOnline() ||
    !profileView.value.snapshot?.roles.some(
      (grant) =>
        grant.role === 'TEACHER' && grant.status === 'ACTIVE' && grant.selectable && !grant.readOnly,
    )
  )
    return null;
  return port;
});
const profilePasswordDisabled = computed(
  () => !verifiedProfileOwnerKey.value || profileView.value.mutationBusy !== null,
);
let disposed = false;
let teacherContextAttempt = 0;
let refreshSemesterPending = false;
let contextInFlight: {
  readonly api: TeacherApi;
  readonly admissionSemesterId: number;
  readonly promise: Promise<void>;
} | null = null;
let stopTheme = (): void => undefined;
function dismissOverlay(): boolean {
  if (attachmentOpen.value) {
    attachmentCloseSignal.value += 1;
    return true;
  }
  if (statsToolOpen.value) {
    statsToolCloseSignal.value += 1;
    return true;
  }
  if (calendarOpen.value) {
    calendarCloseSignal.value += 1;
    return true;
  }
  if (exportOpen.value) {
    exportCloseSignal.value += 1;
    return true;
  }
  return false;
}
const stopOverlayBack = navigation.beforeBack(() => !dismissOverlay());
const journalSubjectOptions = computed(() => {
  const options = new Map<string, TeacherStatsSubjectOption>();
  for (const option of [
    ...(catalog.value?.subjectOptions ?? []),
    ...(catalog.value?.taughtSubjectOptions ?? []),
  ]) {
    const key = `${option.groupId}:${option.subjectId}`;
    const previous = options.get(key);
    options.set(
      key,
      previous
        ? { ...option, lessonTypes: [...new Set([...previous.lessonTypes, ...option.lessonTypes])] }
        : option,
    );
  }
  if (journalEntry.value === 'history') return [...options.values()];
  const taughtIds = new Set(
    catalog.value?.taughtSubjectOptions.map((option) => `${option.groupId}:${option.subjectId}`) ??
      [],
  );
  return [...options.values()].filter((option) =>
    taughtIds.has(`${option.groupId}:${option.subjectId}`),
  );
});

const stopNavigation = navigation.subscribe(() => {
  route.value = navigation.current;
  if (route.value.root === 'teacher-attendance') attendanceVisited.value = true;
  if (route.value.id.endsWith('/journal')) journalVisited.value = true;
  ensureProfileRoute(route.value);
});

function resetSemesterScope(): void {
  lessonId.value = null;
  journalQuery.value = null;
  journalVisited.value = false;
  journalEntry.value = 'history';
  attendanceQuery.value = null;
  attendanceVisited.value = false;
  requestId.value = null;
  manualStudentId.value = null;
  manualLessonId.value = null;
  selectedGroup.value = null;
  catalog.value = null;
  catalogVerified.value = false;
  ownerGeneration.value += 1;
  calendarOpen.value = false;
  exportOpen.value = false;
  attachmentOpen.value = false;
  statsToolOpen.value = false;
}

watch(
  () => [props.api, props.semesterId] as const,
  ([api, admissionSemesterId], [previousApi, previousAdmissionSemesterId]) => {
    if (api === previousApi && admissionSemesterId !== null && previousAdmissionSemesterId !== null) {
      void loadTeacherContext(true);
      return;
    }
    ++teacherContextAttempt;
    contextInFlight = null;
    refreshSemesterPending = false;
    resetSemesterScope();
    semester.value = null;
    lessonSlots.value = [];
    teacherContextError.value = null;
    semesterChecking.value = false;
    semesterCheckFailed.value = false;
    selectedDate.value = props.selectedDate || moscowToday();
    navigation.goRoot(hasTeacherStatsRoute() ? 'teacher-stats' : 'teacher-home');
    void loadTeacherContext();
  },
);
watch(
  () => props.selectedDate,
  (date) => {
    const nextDate = date || moscowToday();
    selectedDate.value = semester.value ? clampDate(nextDate, semester.value) : nextDate;
  },
);
watch(
  () =>
    [props.api, props.profilePort, profileOwnerKey.value, props.profile?.readOnly, props.offline] as const,
  (
    [api, profilePort, ownerKey, readOnly, offline],
    [previousApi, previousPort, previousKey, previousReadOnly, wasOffline],
  ) => {
    if (
      api !== previousApi ||
      profilePort !== previousPort ||
      ownerKey !== previousKey ||
      readOnly !== previousReadOnly
    ) {
      profileState.value = profilePort ? new ProfileState(profilePort) : null;
      profileRoleError.value = null;
      profilePendingRole.value = null;
      publishProfileView();
      ensureProfileRoute(route.value);
    } else if (
      !offline &&
      wasOffline &&
      (profileView.value.snapshot || route.value.root === 'profile')
    ) {
      void loadProfileSnapshot();
    }
  },
);

provideProjectionRevision(() => projectionRevision.value);
useProjectionRefresh(() => loadTeacherContext(true), {
  revision: () => contextRevision.value,
  blocked: () => props.offline,
});

function refreshContextOnVisible(): void {
  if (globalThis.document?.visibilityState === 'visible') localContextRevision.value += 1;
}
onMounted(() => globalThis.document?.addEventListener?.('visibilitychange', refreshContextOnVisible));

function changeCalendarOpen(open: boolean): void {
  calendarOpen.value = open;
  if (open && !props.offline) {
    semesterChecking.value = true;
    localContextRevision.value += 1;
  }
}

function loadTeacherContext(refresh = false): Promise<void> {
  const api = props.api;
  const admissionSemesterId = props.semesterId;
  if (!api || admissionSemesterId == null) {
    teacherContextLoading.value = false;
    return Promise.resolve();
  }
  if (
    !refresh &&
    contextInFlight?.api === api &&
    contextInFlight.admissionSemesterId === admissionSemesterId
  ) {
    return contextInFlight.promise;
  }
  refreshSemesterPending ||= refresh;
  if (refresh) catalogVerified.value = false;
  const attempt = ++teacherContextAttempt;
  const refreshSemester = refreshSemesterPending;
  const revision = contextRevision.value;
  const ownsAttempt = () =>
    !disposed &&
    attempt === teacherContextAttempt &&
    api === props.api &&
    admissionSemesterId === props.semesterId;
  const current = () =>
    ownsAttempt() && (!refreshSemester || revision === contextRevision.value);
  teacherContextLoading.value = true;
  teacherContextError.value = null;
  async function loadSemesterAndCatalog(client: TeacherApi): Promise<void> {
    const previous = semester.value;
    // The App's bootstrap ID admits this owner; only the teacher API resolves its current scope.
    let active = previous;
    if (!active || refreshSemester) {
      semesterChecking.value = true;
      semesterCheckFailed.value = false;
      try {
        active = await client.semester();
      } catch (cause) {
        if (current()) {
          semesterChecking.value = false;
          semesterCheckFailed.value = true;
        }
        throw cause;
      }
    }
    if (!current()) return;
    semesterChecking.value = false;
    refreshSemesterPending = false;
    if (previous && previous.id !== active.id) {
      resetSemesterScope();
      selectedDate.value = moscowToday();
      clearTeacherStatsRoute();
      if (route.value.root !== 'profile' && route.value.root !== 'teacher-map')
        navigation.goRoot('teacher-home');
    }
    semester.value = active;
    selectedDate.value = clampDate(selectedDate.value, active);
    if (refreshSemester && previous?.id === active.id) {
      projectionRevision.value += 1;
      // Bounds changes already reload Today through its scope watchers.
      if (previous.dateFrom === active.dateFrom && previous.dateTo === active.dateTo)
        todayProjectionRevision.value += 1;
    }
    if (!catalogVerified.value) {
      const data = await client.catalog(active.id);
      if (!current()) return;
      catalog.value = data;
      catalogVerified.value = true;
    }
  }
  const promise = Promise.allSettled([
    loadSemesterAndCatalog(api),
    lessonSlots.value.length
      ? Promise.resolve()
      : api.lessonSlots().then(({ slots }) => {
          if (current()) lessonSlots.value = slots;
        }),
  ])
    .then((results) => {
      if (!current()) return;
      const rejected = results.find((result) => result.status === 'rejected');
      if (rejected?.status === 'rejected') {
        teacherContextError.value = 'Не удалось загрузить учебный контекст. Попробуй ещё раз.';
        forwardError(rejected.reason);
      }
    })
    .finally(() => {
      if (ownsAttempt()) {
        teacherContextLoading.value = false;
        contextInFlight = null;
      }
    });
  contextInFlight = { api, admissionSemesterId, promise };
  return promise;
}
void loadTeacherContext();

function moscowToday(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' });
}

function clampDate(date: string, { dateFrom, dateTo }: TeacherSemester): string {
  return date < dateFrom ? dateFrom : date > dateTo ? dateTo : date;
}

function bindTheme(controller: MobileThemeController | null): void {
  stopTheme();
  stopTheme = (): void => undefined;
  if (!controller) {
    themeMode.value = 'system';
    return;
  }
  themeMode.value = controller.mode;
  stopTheme = controller.subscribe((snapshot) => {
    themeMode.value = snapshot.mode;
  });
}

watch(() => props.themeController, bindTheme, { immediate: true });
watch(
  () =>
    [
      props.host,
      route.value.kind,
      calendarOpen.value,
      exportOpen.value,
      attachmentOpen.value,
      statsToolOpen.value,
    ] as const,
  ([host, kind, calendar, exporting, attachment, statsTool]) => {
    if (host?.backOwner === 'host')
      host.setBackVisible?.(kind === 'nested' || calendar || exporting || attachment || statsTool);
  },
  { immediate: true, flush: 'post' },
);

async function runProfile<T>(
  request: (state: ProfileState) => Promise<T>,
  options: { rethrow?: boolean } = {},
): Promise<T | undefined> {
  const state = profileState.value;
  if (!state || disposed) return undefined;
  let pending: Promise<T>;
  try {
    pending = request(state);
    publishProfileView();
  } catch {
    publishProfileView();
    return undefined;
  }
  try {
    return await pending;
  } catch (error) {
    if (options.rethrow) throw error;
    return undefined;
  } finally {
    publishProfileView();
    await state.waitForAutomaticStaleReload();
    publishProfileView();
  }
}

async function loadProfileSnapshot(): Promise<void> {
  const loaded = await runProfile((state) => state.loadSnapshot());
  if (loaded && !disposed) ensureProfileRoute(route.value);
}

async function openProfileArea(area: ProfileArea): Promise<void> {
  const state = profileState.value;
  if (!state || disposed) return;
  if (!state.view.snapshot) {
    await runProfile((current) => current.loadSnapshot());
    if (disposed || !state.view.snapshot) return;
  }
  if (area === 'sessions')
    await runProfile((current) => current.loadAllSessions(publishProfileView));
  else await runProfile((current) => current.loadHistory());
}

function retryProfileSnapshot(): void {
  void loadProfileSnapshot();
}

function retryProfileArea(area: ProfileArea): void {
  void openProfileArea(area);
}

function loadMoreProfile(area: ProfileArea, cursor: string): void {
  const state = profileState.value;
  if (!state || disposed) return;
  if (area === 'sessions') void runProfile((current) => current.loadSessions({ cursor }));
  else void runProfile((current) => current.loadHistory({ cursor }));
}

function profileRoute(routeName: ProfileAccountRoute) {
  return nestedRoute('profile', `profile/${routeName}`, 'detail');
}

function navigateProfile(routeName: ProfileRoute): void {
  if (routeName === 'profile') {
    navigation.goRoot('profile');
    return;
  }
  if (routeName === 'browser-login' && !browserLoginApproval.value?.isOnline()) return;
  if (
    routeName === 'role-switch' ||
    routeName === 'browser-login' ||
    routeName === 'appearance' ||
    routeName === 'security' ||
    routeName === 'sessions' ||
    routeName === 'history'
  ) {
    profileRoleError.value = null;
    navigation.push(profileRoute(routeName));
  }
}

function navigateRoot(routeName: MobileRootRouteId): void {
  if (routeName === 'teacher-home') clearTeacherStatsRoute();
  else if (routeName === 'teacher-stats') activateTeacherStatsRoute();
  else if (
    routeName !== 'teacher-attendance' &&
    routeName !== 'teacher-map' &&
    routeName !== 'profile'
  )
    return;
  lessonId.value = null;
  calendarCloseSignal.value += 1;
  exportCloseSignal.value += 1;
  calendarOpen.value = false;
  exportOpen.value = false;
  attachmentOpen.value = false;
  statsToolOpen.value = false;
  navigation.goRoot(routeName);
}

function backRoute(): void {
  if (!dismissOverlay()) navigation.back();
}

function openLesson(nextLessonId: number): void {
  lessonId.value = nextLessonId;
  navigation.push(nestedRoute('teacher-home', 'teacher-home/lesson', 'detail'));
}

function openJournal(
  query: TeacherJournalQuery,
  returnSurface: 'home' | 'stats' = 'home',
  entry: 'taught' | 'history' = 'history',
): void {
  journalEntry.value = entry;
  journalQuery.value = {
    semesterId: query.semesterId,
    groupId: query.groupId,
    subjectId: query.subjectId,
    lessonTypes: returnSurface === 'stats' || query.lessonType ? teacherJournalTypes(query) : [],
    pastOnly: true,
  };
  lessonId.value = null;
  const parent = returnSurface === 'stats' ? 'teacher-stats' : 'teacher-home';
  navigation.push(nestedRoute(parent, `${parent}/journal`, 'detail'));
}

function rememberAttendanceContext(query: TeacherJournalQuery): void {
  attendanceQuery.value = query;
}

function openAttendanceLesson(nextLessonId: number, query: TeacherJournalQuery): void {
  attendanceQuery.value = query;
  lessonId.value = nextLessonId;
  navigation.push(nestedRoute('teacher-attendance', 'teacher-attendance/lesson', 'detail'));
}

function openAttendanceJournal(query: TeacherJournalQuery): void {
  attendanceQuery.value = query;
  journalEntry.value = 'history';
  journalQuery.value = {
    semesterId: query.semesterId,
    groupId: query.groupId,
    subjectId: query.subjectId,
    ...(query.dateFrom !== undefined ? { dateFrom: query.dateFrom } : {}),
    ...(query.dateTo !== undefined ? { dateTo: query.dateTo } : {}),
    lessonTypes: [],
    pastOnly: true,
  };
  lessonId.value = null;
  navigation.push(nestedRoute('teacher-attendance', 'teacher-attendance/journal', 'detail'));
}

function rememberJournalPage(page: number): void {
  if (!Number.isSafeInteger(page) || page < 0) return;
  const activeQuery = journalQuery;
  if (activeQuery.value && (activeQuery.value.page ?? 0) !== page) {
    activeQuery.value = { ...activeQuery.value, page };
  }
}
function openJournalLesson(nextLessonId: number, page: number): void {
  if (!route.value.id.endsWith('/journal')) return;
  if (journalQuery.value) {
    journalQuery.value = { ...journalQuery.value, page };
  }
  lessonId.value = nextLessonId;
  const parent = route.value.root;
  navigation.push(nestedRoute(parent, `${parent}/journal/lesson`, 'detail'));
}

function openGroup(groupId: number, groupName: string): void {
  selectedGroup.value = { id: groupId, name: groupName };
  journalQuery.value = null;
  navigation.push(nestedRoute('teacher-stats', 'teacher-stats/subjects', 'detail'));
  if (!catalog.value) void loadTeacherContext();
}

function openSubject(option: TeacherStatsSubjectOption): void {
  const semesterId = effectiveSemesterId.value;
  if (!semesterId || !selectedGroup.value || option.groupId !== selectedGroup.value.id)
    return;
  openJournal(
    {
      semesterId,
      groupId: option.groupId,
      subjectId: option.subjectId,
      lessonTypes: [],
      pastOnly: true,
    },
    'stats',
    'taught',
  );
}

function updateJournalQuery(query: TeacherJournalQuery): void {
  journalQuery.value = { ...query, pastOnly: true };
}

function openManualExcuse(nextLessonId: number, studentId: number): void {
  const parent = route.value.root;
  if (parent !== 'teacher-home' && parent !== 'teacher-stats' && parent !== 'teacher-attendance')
    return;
  manualLessonId.value = nextLessonId;
  manualStudentId.value = studentId;
  navigation.push(nestedRoute(parent, `${parent}/manual-excuse`, 'task'));
}

function openLessonExport(nextLessonId: number): void {
  if (lessonId.value === nextLessonId) exportOpenSignal.value += 1;
}

function openStatsJournal(query: TeacherJournalQuery): void {
  openJournal(query, 'stats');
}

function openExcuse(nextRequestId: string): void {
  const parent = route.value.root;
  if (parent !== 'teacher-home' && parent !== 'teacher-stats' && parent !== 'teacher-attendance')
    return;
  requestId.value = nextRequestId;
  navigation.push(nestedRoute(parent, `${parent}/excuse`, 'task'));
}

function asProfileError(cause: unknown): ProfileRequestError {
  if (cause instanceof ProfileRequestError) return cause;
  const status =
    typeof cause === 'object' &&
    cause !== null &&
    'status' in cause &&
    typeof cause.status === 'number'
      ? cause.status
      : undefined;
  const code = status === 401 ? 'INVALID_SESSION' : status === 403 ? 'ROLE_NOT_GRANTED' : 'NETWORK';
  return new ProfileRequestError(
    code,
    cause instanceof Error ? cause.message : 'Не удалось сменить роль',
    status,
    cause,
  );
}

async function selectProfileRole(role: ProfileRole, expectedSessionVersion: string): Promise<void> {
  const state = profileState.value;
  if (!state && !props.profileRoleSelect) return;
  profilePendingRole.value = role;
  profileRoleError.value = null;
  try {
    if (props.profileRoleSelect) await props.profileRoleSelect(role, expectedSessionVersion);
    else await runProfile((current) => current.selectRole(role), { rethrow: true });
  } catch (cause) {
    if (!disposed) {
      const typed = asProfileError(cause);
      profileRoleError.value = typed;
      if (typed.code === 'SESSION_VERSION_CONFLICT' || typed.code === 'SESSION_STATE_STALE') {
        await runProfile((current) => current.loadSnapshot());
      }
    }
  } finally {
    if (!disposed) {
      profilePendingRole.value = null;
      publishProfileView();
    }
  }
}

function changeProfileTheme(mode: ProfileTheme): void {
  props.themeController?.setMode(mode);
}

async function changeProfilePassword(input: {
  currentPassword: string;
  newPassword: string;
}): Promise<void> {
  if (profilePasswordDisabled.value || disposed) return;
  await runProfile((state) => state.changePassword(input), { rethrow: true });
}

async function terminateProfileSession(sessionId: string): Promise<void> {
  await runProfile((state) => state.terminateSession(sessionId), { rethrow: true });
}

async function logoutProfileAll(): Promise<void> {
  if (!profileState.value || disposed) return;
  await runProfile((state) => state.logoutAll());
}

function ensureProfileRoute(routeValue: MobileRoute): void {
  const state = profileState.value;
  if (routeValue.root !== 'profile' || !state || disposed || props.offline) return;
  if (!state.view.snapshot) {
    if (state.view.snapshotStatus === 'loading') return;
    if (routeValue.id === 'profile/sessions' || routeValue.id === 'profile/history') {
      void openProfileArea(routeValue.id === 'profile/sessions' ? 'sessions' : 'history');
    } else {
      void loadProfileSnapshot();
    }
    return;
  }
  if (routeValue.id === 'profile/sessions' && state.view.sessionsStatus === 'idle')
    void openProfileArea('sessions');
  if (routeValue.id === 'profile/history' && state.view.historyStatus === 'idle')
    void openProfileArea('history');
}

function forwardError(cause: unknown): void {
  emit('ownerError', cause);
}

onBeforeUnmount(() => {
  disposed = true;
  globalThis.document?.removeEventListener?.('visibilitychange', refreshContextOnVisible);
  lessonId.value = null;
  journalQuery.value = null;
  requestId.value = null;
  stopNavigation();
  stopOverlayBack();
  stopTheme();
  stopScopedTheme();
  scopedTheme.dispose();
});
async function openNotificationAction(
  action: NotificationAction,
  isCurrent: () => boolean,
): Promise<boolean> {
  const api = props.api,
    generation = ownerGeneration.value;
  if (
    !api ||
    props.offline ||
    disposed ||
    action.role !== 'TEACHER' ||
    action.kind !== 'lesson' ||
    !isCurrent()
  )
    return false;
  const id = notificationNumericId(action.lessonId);
  if (id === null) return false;
  await api.lesson(id);
  if (
    !isCurrent() ||
    disposed ||
    api !== props.api ||
    generation !== ownerGeneration.value ||
    props.offline
  )
    return false;
  openLesson(id);
  return true;
}
defineExpose({ openNotificationAction });
</script>

<template>
  <MobileShell
    class="teacher-mobile-shell"
    :data-student-ui="route.root === 'profile' ? '' : undefined"
    :data-theme="resolvedTheme"
    :route="route"
    :navigation="navigation"
    :nav-items="navItems"
    :active-id="route.root"
    :host="host"
    :custom-back="true"
    :host-back-action="backRoute"
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
          :semester="semester"
          :current-journal-subjects="currentJournalSubjects"
          :context-loading="teacherContextLoading"
          :context-error="teacherContextError"
          :semester-checking="semesterChecking"
          :semester-check-failed="semesterCheckFailed"
          :selected-date="selectedDate"
          :realtime-revision="todayProjectionRevision"
          :lesson-slots="lessonSlots"
          :calendar-close-signal="calendarCloseSignal"
          :role-switch-enabled="profilePort !== null"
          @select-date="selectedDate = $event"
          @retry-context="loadTeacherContext()"
          @open-lesson="openLesson"
          @open-journal="openJournal"
          @open-stats="navigateRoot('teacher-stats')"
          @open-role-switch="navigateProfile('role-switch')"
          @error="forwardError"
          @calendar-open="changeCalendarOpen"
        />
      </KeepAlive>
      <TeacherLessonScreen
        v-if="
          route.id === 'teacher-home/lesson' ||
            route.id === 'teacher-attendance/lesson' ||
            route.id.endsWith('/journal/lesson')
        "
        :api="api"
        :lesson-id="lessonId"
        :show-back="host?.backOwner !== 'host'"
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
          :group-name="
            catalog?.groups.find((group) => group.id === journalQuery?.groupId)?.name ?? ''
          "
          :semester="semester"
          :show-back="host?.backOwner !== 'host'"
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
        :realtime-revision="realtimeRevision"
        :offline="offline"
        :show-back="host?.backOwner !== 'host'"
        :attachment-close-signal="attachmentCloseSignal"
        @attachment-open="attachmentOpen = $event"
        @back="backRoute"
        @error="forwardError"
      />
      <TeacherGroupSubjectsScreen
        v-if="route.id === 'teacher-stats/subjects' && selectedGroup && catalog"
        :group-id="selectedGroup.id"
        :group-name="selectedGroup.name"
        :subject-options="catalog.taughtSubjectOptions"
        :show-back="host?.backOwner !== 'host'"
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
            v-if="host?.backOwner !== 'host'"
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
          @action="loadTeacherContext()"
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
        :show-back="host?.backOwner !== 'host'"
        :attachment-close-signal="attachmentCloseSignal"
        @attachment-open="attachmentOpen = $event"
        @back="backRoute"
        @error="forwardError"
      />
      <KeepAlive :key="`stats-${ownerGeneration}`">
        <TeacherStatsScreen
          v-if="route.id === 'teacher-stats'"
          :api="api"
          :semester-id="effectiveSemesterId"
          :report-download="reportDownload"
          :close-tool-signal="statsToolCloseSignal"
          :realtime-revision="projectionRevision"
          @tool-open="statsToolOpen = $event"
          @open-journal="openStatsJournal"
          @open-group="openGroup"
          @error="forwardError"
        />
      </KeepAlive>
      <StudentWarningBlock
        v-if="
          !semester && teacherContextError &&
            (route.id === 'teacher-stats' || route.id === 'teacher-attendance')
        "
        title="Учебный контекст недоступен"
        :message="teacherContextError"
        severity="error"
        action-label="Повторить"
        @action="loadTeacherContext()"
      />
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
          browser-login-page
          :snapshot="profileView.snapshot"
          :browser-login-approval="browserLoginApproval"
          :loading="profileView.snapshotStatus === 'loading'"
          :error="profileView.snapshotError"
          :theme="resolvedTheme"
          :on-retry="retryProfileSnapshot"
          :on-navigate="navigateProfile"
          :on-logout="onLogout"
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
        <BrowserLoginScreen
          v-else-if="route.id === 'profile/browser-login'"
          :port="browserLoginApproval"
          :offline="offline"
          :theme="resolvedTheme"
          :owner-key="browserLoginApproval ? verifiedProfileOwnerKey : null"
          :on-back="backRoute"
        />
        <AppearanceScreen
          v-else-if="route.id === 'profile/appearance'"
          :theme="themeMode"
          :resolved-theme="resolvedTheme"
          theme-unlocked
          :on-back="backRoute"
          :on-theme-change="changeProfileTheme"
        />
        <SecurityScreen
          v-else-if="route.id === 'profile/security'"
          :owner-key="verifiedProfileOwnerKey"
          :disabled="profilePasswordDisabled"
          :policy="profileView.snapshot?.passwordPolicy ?? DEFAULT_PASSWORD_POLICY"
          :error="profileView.error"
          :busy="profileView.mutationBusy === 'password'"
          :offline="offline || profilePort === null"
          :theme="resolvedTheme"
          :on-back="backRoute"
          :on-change-password="changeProfilePassword"
          :on-recover="onRecover"
        />
        <SessionsScreen
          v-else-if="route.id === 'profile/sessions'"
          :sessions="profileView.sessions"
          :loading="profileView.sessionsStatus === 'loading'"
          :error="profileView.sessionsError"
          :busy="profileView.mutationBusy !== null"
          :terminating-session-id="profileView.terminatingSessionId"
          :termination-error="profileView.terminationError"
          :on-terminate-session="
            profilePort?.terminateSession ? terminateProfileSession : undefined
          "
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
          :semester-id="effectiveSemesterId"
          :initial-query="attendanceQuery"
          :active="route.id === 'teacher-attendance'"
          :calendar-close-signal="calendarCloseSignal"
          @calendar-open="calendarOpen = $event"
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

<script setup lang="ts">
import { createDeviceThemeMirror, createDeviceThemeTransport } from '../../shared/foundation';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import {
  AdminAccessApiError,
  type AdminAccessClient,
  type AdminAccessProfile,
  createAdminAuthorityOwner,
  createAdminGenerationReader,
  isAdminPasswordProofFailure,
  shouldRefreshAdminAuthority,
  adminCanNavigate,
  firstAdminRoute,
  AdminMapClient,
  AdminMapScreen,
  AdminMapStatisticsScreen,
  AdminDashboardScreen,
  AdminAttendanceClient,
  AdminDashboardApiError,
  AdminAttendanceScreen,
  AdminSemesterApiError,
  AdminSemesterScreen,
  AdminGroupsApiError,
  AdminGroupsScreen,
  AdminUsersApiError,
  AdminUsersScreen,
  AdminShell,
  AdminMoreScreen,
  AdminNotice,
  type AdminRoleRoute,
  AdminProfileOwner,
  CampusMapClient,
  HeadmanScheduleApiError,
  HeadmanJournalApiError,
  HeadmanRequestsApiError,
  HeadmanHomeworkApiError,
  HeadmanStatsApiError,
  HeadmanGroupApiError,
  HeadmanSubjectsApiError,
  HeadmanScheduleScreen,
  MapScreen,
  NotificationsApiError,
  NotificationsEntryButton,
  NotificationsScreen,
  NotificationsUnreadState,
  ProfileRequestError,
  RoleSwitchScreen,
  StaleSessionGenerationError,
  createAssistantPermissionRefresher,
  isAssistantCapabilityForbiddenError,
  isAssistantPermissionReadForbidden,
  StudentApi,
  StudentApiError,
  StudentFeatureOwner,
  TeacherApi,
  TeacherApiError,
  TeacherFeatureOwner,
  createFixtureTransport,
  createGenerationBoundNotificationsV2Api,
  createGenerationBoundNotificationsRealtime,
  createMobileTheme,
  createMobileThemePreference,
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
  type HeadmanPermissionOption,
  type ProfilePort,
  type ProfileRole,
  type ProfileSnapshot,
  type AdminDashboardClient,
  type AdminSemesterClient,
  type AdminGroupsClient,
  type AdminUsersClient,
  type NotificationsV2Api,
  type NotificationTarget,
  type NotificationAction,
  executeNotificationAction,
  notificationNumericId,
  decodeNotificationLaunch,
  NotificationLaunchOwner,
  type NotificationTargetIntent,
} from '@rct/mobile-core';
import {
  installFixtureTelegramHost,
  TelegramHost,
  createTelegramSystemThemeSource,
} from './telegram';
import { useTmaSession } from './tma-session';
import { createTelegramReportDownloadPort } from './report-download-adapter';
import { TmaAuthError } from './tma-auth';
import { publicPasswordRecoveryUrl } from './password-recovery-link';

const fixtureMode = import.meta.env.VITE_MOBILE_FIXTURE_MODE === 'true';
if (fixtureMode) installFixtureTelegramHost();
const fixtureTransport = fixtureMode ? createFixtureTransport() : undefined;
const nativeFetcher: typeof fetch = (input, init) => globalThis.fetch(input, init);
const host = new TelegramHost();
const sessionOwner = useTmaSession({
  fetcher: fixtureTransport ?? nativeFetcher,
  getInitData: () => host.start(),
});
const reportDownload = computed(() =>
  createTelegramReportDownloadPort(sessionOwner.createReportDownloadClient(currentFetcher()), host),
);
const themePreference = createMobileThemePreference(() =>
  typeof window === 'undefined' ? null : window.localStorage,
);
const theme =
  typeof document === 'undefined'
    ? null
    : createMobileTheme({
        mode: themePreference.read() ?? 'system',
        systemSource: createTelegramSystemThemeSource(host.app),
      });
const resolvedTheme = ref(theme?.resolvedMode ?? 'light');
const stopThemeSnapshot = theme?.subscribe((snapshot) => {
  resolvedTheme.value = snapshot.resolvedMode;
});
const themeMirror = theme
  ? createDeviceThemeMirror({
      theme,
      preference: themePreference,
      transport: createDeviceThemeTransport(fixtureTransport ?? nativeFetcher),
    })
  : null;
const mapClient = new CampusMapClient({
  accessToken: () => sessionOwner.accessToken.value,
  currentGeneration: () => sessionOwner.currentGeneration(),
  onUnauthorized: () => sessionOwner.authenticateFor(sessionOwner.currentGeneration()),
  fetcher: fixtureTransport ?? nativeFetcher,
});
const adminMapClient = shallowRef<AdminMapClient | null>(null);

const api = shallowRef<StudentApi | null>(null);
const teacherApi = shallowRef<TeacherApi | null>(null);
const teacherSemesterId = ref<number | null>(null);
const headmanApi = shallowRef<HeadmanScheduleApi | null>(null);
const headmanJournalApi = shallowRef<HeadmanJournalApi | null>(null);
const headmanLessonActionsApi = computed(() =>
  headmanJournalApi.value ? sessionOwner.createHeadmanLessonActionsApi(currentFetcher()) : null,
);
const headmanRequestsApi = shallowRef<HeadmanRequestsApi | null>(null);
const headmanGroupApi = shallowRef<HeadmanGroupApi | null>(null);
const headmanSubjectsApi = shallowRef<HeadmanSubjectsApi | null>(null);
const headmanStatsApi = shallowRef<HeadmanStatsApi | null>(null);
const headmanHomeworkApi = shallowRef<HeadmanHomeworkApi | null>(null);
const headmanHomeworkActorUserId = ref<number | null>(null);
const assistantJournalApi = shallowRef<HeadmanJournalApi | null>(null);
const assistantStatsApi = shallowRef<HeadmanStatsApi | null>(null);
const assistantRequestsApi = shallowRef<HeadmanRequestsApi | null>(null);
const assistantHomeworkApi = shallowRef<HeadmanHomeworkApi | null>(null);
const assistantPermissionOptions = shallowRef<readonly HeadmanPermissionOption[]>([]);
const assistantPermissions = computed(() =>
  assistantPermissionOptions.value.map((option) => option.code),
);
const assistantPermissionsVerified = ref(false);
const adminAccessApi = shallowRef<AdminAccessClient | null>(null);
const adminAccessActionsApi = shallowRef<AdminAccessClient | null>(null);
const adminAuthority = shallowRef<AdminAccessProfile | null>(null);
const adminAuthorityStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
const adminAuthorityOwner = createAdminAuthorityOwner();
const adminSemesterApi = shallowRef<AdminSemesterClient | null>(null);
const adminAttendanceApi = shallowRef<AdminAttendanceClient | null>(null);
const adminDashboardApi = shallowRef<AdminDashboardClient | null>(null);
const adminUsersApi = shallowRef<AdminUsersClient | null>(null);
const adminGroupsApi = shallowRef<AdminGroupsClient | null>(null);
const notificationsApi = shallowRef<NotificationsV2Api | null>(null);
const notificationRealtimeRevision = ref(0);
const adminDashboardRevision = ref(0);
const notificationUnreadOwner = new NotificationsUnreadState((error) => {
  if (error instanceof NotificationsApiError && error.status === 401) onOwnerError(error);
});
const notificationUnread = shallowRef({ ...notificationUnreadOwner.view });
const stopNotificationUnread = notificationUnreadOwner.subscribe(() => {
  notificationUnread.value = { ...notificationUnreadOwner.view };
});
const notificationContextKey = computed(() =>
  [
    sessionOwner.resetGeneration.value,
    profile.value?.userId,
    profile.value?.activeRole,
    profile.value?.sessionVersion,
    profile.value?.rolesVersion,
    profile.value?.readOnly,
  ].join('|'),
);
const notificationsOpen = ref(false);
const notificationsGeneration = ref<number | null>(null);
const notificationTargetIntent = shallowRef<NotificationTargetIntent | null>(null);
const notificationStartParam =
  new URLSearchParams(window.location.search).get('tgWebAppStartParam') ??
  new URLSearchParams(host.app?.initData ?? '').get('start_param');
const notificationLaunchEncoded = notificationStartParam?.startsWith('n2_')
  ? notificationStartParam.slice(3)
  : null;
const notificationLaunch = new NotificationLaunchOwner(
  decodeNotificationLaunch(notificationLaunchEncoded),
);
const notificationLaunchNotice = ref<string | null>(
  notificationLaunchEncoded && !decodeNotificationLaunch(notificationLaunchEncoded)
    ? 'Ссылка уведомления недействительна.'
    : null,
);
let notificationTargetIntentId = 0;
const studentNotificationOwner = ref<{
  openNotificationAction(action: NotificationAction, current: () => boolean): boolean;
} | null>(null);
const headmanNotificationOwner = ref<{
  openNotificationAction(action: NotificationAction, current: () => boolean): Promise<boolean>;
} | null>(null);
const teacherNotificationOwner = ref<{
  openNotificationAction(action: NotificationAction, current: () => boolean): Promise<boolean>;
} | null>(null);
const session = shallowRef<StudentSession | null>(null);
const scope = shallowRef<StudentFeatureScope | null>(null);
const profile = shallowRef<ProfileSnapshot | null>(null);
watch(
  () =>
    [
      sessionOwner.accessToken.value,
      profile.value?.userId,
      profile.value?.sessionId,
      sessionOwner.resetGeneration.value,
    ] as const,
  ([accessToken, userId, sessionId, generation]) => {
    themeMirror?.setSession(
      accessToken && userId && sessionId
        ? { accessToken, key: `${generation}:${userId}:${sessionId}` }
        : null,
    );
  },
  { immediate: true },
);

const profilePort = shallowRef<ProfilePort | null>(null);
const headmanGroupId = ref<number | null>(null);
const ready = ref(false);
const offline = ref(typeof navigator !== 'undefined' ? !navigator.onLine : false);
const error = ref<string | null>(null);
const logoutPending = ref(false);
const logoutUnconfirmed = ref(false);
const bootstrapping = ref(false);
let authorityBootstrapPending = false;
let bootstrapOperation = 0;
let roleOperation = 0;
let roleOperationGeneration: number | null = null;
const ownerRevision = ref(0);
const authView = ref<
  | 'role'
  | 'student'
  | 'teacher'
  | 'headman'
  | 'map'
  | 'admin-more'
  | 'admin-home'
  | 'admin-attendance'
  | 'admin-map'
  | 'admin-map-statistics'
  | 'admin-semesters'
  | 'admin-users'
  | 'admin-groups'
  | 'admin-profile'
  | 'admin-role-switch'
  | 'signed-out'
>('role');
const pendingRole = ref<ProfileRole | null>(null);
const roleError = shallowRef<ProfileRequestError | null>(null);
const roleLoading = ref(false);

const mapViewVisible = computed(() => authView.value === 'map');
const adminNavigationVisible = ref(true);
const adminTaskOwner = ref<{
  back: () => void;
  openNotificationGroup?: (id: number, current: () => boolean) => Promise<boolean>;
} | null>(null);
const adminRoleReturn = ref<'admin-home' | 'admin-profile'>('admin-home');
const adminMoreViewVisible = computed(
  () => authView.value === 'admin-more' && adminCanNavigate(adminAuthority.value, 'more'),
);
watch(authView, () => {
  adminNavigationVisible.value = true;
});
const adminHomeViewVisible = computed(
  () => authView.value === 'admin-home' && adminCanNavigate(adminAuthority.value, 'home'),
);
const adminAttendanceViewVisible = computed(
  () =>
    authView.value === 'admin-attendance' && adminCanNavigate(adminAuthority.value, 'attendance'),
);
const adminMapViewVisible = computed(
  () => authView.value === 'admin-map' && adminCanNavigate(adminAuthority.value, 'map'),
);
const adminMapStatisticsViewVisible = computed(
  () =>
    authView.value === 'admin-map-statistics' &&
    adminCanNavigate(adminAuthority.value, 'map-statistics'),
);
const adminSemesterViewVisible = computed(
  () => authView.value === 'admin-semesters' && adminCanNavigate(adminAuthority.value, 'semesters'),
);
const adminUsersViewVisible = computed(
  () => authView.value === 'admin-users' && adminCanNavigate(adminAuthority.value, 'users'),
);
const adminGroupsViewVisible = computed(
  () => authView.value === 'admin-groups' && adminCanNavigate(adminAuthority.value, 'groups'),
);
const adminProfileViewVisible = computed(() => authView.value === 'admin-profile');
const adminRoleSwitchViewVisible = computed(() => authView.value === 'admin-role-switch');

const stopAdminAuthority = adminAuthorityOwner.subscribe((snapshot) => {
  adminAuthority.value = snapshot.profile;
  adminAuthorityStatus.value = snapshot.status;
  if (profile.value?.activeRole !== 'ADMIN') return;
  if (snapshot.status === 'loading' || snapshot.status === 'error') {
    adminAccessActionsApi.value = null;
    adminAttendanceApi.value = null;
    adminDashboardApi.value = null;
    adminUsersApi.value = null;
    adminGroupsApi.value = null;
    adminSemesterApi.value = null;
    adminMapClient.value = null;
    ownerRevision.value += 1;
    authView.value = 'admin-profile';
    return;
  }
  if (snapshot.status !== 'ready') return;
  const generation = sessionOwner.currentGeneration();
  const assertAuthority = (): void => {
    if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError();
    adminAuthorityOwner.assertCurrent(snapshot.revision);
  };
  if (snapshot.profile?.assigned) {
    const fetcher: typeof fetch = async (input, init) => {
      assertAuthority();
      const response = await (currentFetcher() ?? globalThis.fetch)(input, init);
      assertAuthority();
      const authorityDenied = await shouldRefreshAdminAuthority(response);
      assertAuthority();
      if (authorityDenied) {
        void refreshAdminAuthority();
        throw new StaleSessionGenerationError();
      }
      return response;
    };
    adminAccessActionsApi.value = sessionOwner.createAdminAccessApi(fetcher, assertAuthority);
    adminDashboardApi.value = sessionOwner.createAdminDashboardApi(fetcher, assertAuthority);
    adminAttendanceApi.value = adminCanNavigate(snapshot.profile, 'attendance')
      ? new AdminAttendanceClient({
          accessToken: () => {
            assertAuthority();
            return sessionOwner.accessToken.value;
          },
          assertCurrent: assertAuthority,
          onUnauthorized: async () => {
            assertAuthority();
            await sessionOwner.authenticateFor(generation);
            assertAuthority();
          },
          fetcher,
        })
      : null;
    adminUsersApi.value = sessionOwner.createAdminUsersApi(fetcher, assertAuthority);
    adminGroupsApi.value = sessionOwner.createAdminGroupsApi(fetcher, assertAuthority);
    adminSemesterApi.value = sessionOwner.createAdminSemesterApi(fetcher, assertAuthority);
    adminMapClient.value = new AdminMapClient({
      accessToken: () => {
        assertAuthority();
        return sessionOwner.accessToken.value;
      },
      currentGeneration: createAdminGenerationReader(
        () => sessionOwner.currentGeneration(),
        () => adminAuthorityOwner.isCurrent(snapshot.revision),
        generation,
      ),
      onUnauthorized: async () => {
        assertAuthority();
        await sessionOwner.authenticateFor(generation);
        assertAuthority();
      },
      fetcher,
    });
  }
  navigateAdmin(firstAdminRoute(snapshot.profile));
});
const adminAccessMessage = computed(() =>
  adminAuthorityStatus.value === 'loading'
    ? 'Проверяем доступ к администрированию…'
    : adminAuthorityStatus.value === 'error'
      ? 'Не удалось подтвердить права. Доступен только твой профиль.'
      : firstAdminRoute(adminAuthority.value) === 'profile'
        ? 'Разделы администрирования недоступны. Ты можешь пользоваться своим профилем.'
        : null,
);
async function refreshAdminAuthority(): Promise<void> {
  await adminAuthorityOwner.refresh();
}
const signedOutTitle = computed(() =>
  logoutUnconfirmed.value
    ? 'Отзыв сессии не подтверждён'
    : logoutPending.value
      ? 'Завершаем сеанс…'
      : 'Сессия завершена',
);
const signedOutMessage = computed(() =>
  logoutUnconfirmed.value
    ? `${error.value ?? 'Не удалось подтвердить отзыв текущей сессии'}. Локальные данные удалены; закрой окно вручную.`
    : logoutPending.value
      ? 'Локальный доступ уже закрыт. Проверяем отзыв сессии на сервере.'
      : 'Ты вышел из аккаунта. Закрой это окно, чтобы завершить работу.',
);
const featureVisible = computed(
  () =>
    api.value !== null ||
    headmanApi.value !== null ||
    teacherApi.value !== null ||
    mapViewVisible.value ||
    adminHomeViewVisible.value ||
    adminAttendanceViewVisible.value ||
    adminMapViewVisible.value ||
    adminMapStatisticsViewVisible.value ||
    adminSemesterViewVisible.value ||
    adminUsersViewVisible.value ||
    adminGroupsViewVisible.value ||
    adminProfileViewVisible.value ||
    adminRoleSwitchViewVisible.value ||
    adminMoreViewVisible.value,
);
const notificationsEntryVisible = computed(
  () =>
    featureVisible.value &&
    profile.value !== null &&
    profile.value.activeRole !== null &&
    authView.value !== 'role',
);
const studentViewVisible = computed(() => authView.value === 'student' && api.value !== null);
const canOpenNotificationTarget = computed(
  () => notificationsEntryVisible.value && notificationsApi.value !== null && !offline.value,
);
const teacherViewVisible = computed(
  () => authView.value === 'teacher' && teacherApi.value !== null,
);
const headmanViewVisible = computed(
  () => authView.value === 'headman' && headmanApi.value !== null,
);
const ownerKey = computed(
  () =>
    `${scope.value ? studentFeatureScopeIdentity(scope.value) : 'tma-unavailable'}|${ownerRevision.value}`,
);

function currentAssistantOwnerKey(): string | null {
  const currentScope = scope.value;
  if (
    authView.value !== 'student' ||
    offline.value ||
    !currentScope ||
    currentScope.activeRole !== 'STUDENT' ||
    currentScope.resetGeneration !== sessionOwner.currentGeneration()
  )
    return null;
  return ownerKey.value;
}

const assistantPermissionRefresher = createAssistantPermissionRefresher<HeadmanPermissionOption>({
  currentGeneration: () => sessionOwner.currentGeneration(),
  currentOwnerKey: currentAssistantOwnerKey,
  load: async (generation, expectedOwnerKey) => {
    const current = session.value;
    if (
      !sessionOwner.isCurrent(generation) ||
      currentAssistantOwnerKey() !== expectedOwnerKey ||
      !current
    ) {
      throw new StaleSessionGenerationError();
    }
    const groupId = current.group?.id ? Number(current.group.id) : null;
    if (groupId === null || !Number.isSafeInteger(groupId) || groupId <= 0) return [];
    const permissions = await sessionOwner
      .createHeadmanGroupApi(currentFetcher())
      .listMyPermissions();
    if (!sessionOwner.isCurrent(generation) || currentAssistantOwnerKey() !== expectedOwnerKey) {
      throw new StaleSessionGenerationError();
    }
    return permissions;
  },
  invalidate: () => {
    assistantPermissionsVerified.value = false;
  },
  apply: (permissions) => {
    assistantPermissionOptions.value = permissions;
    assistantPermissionsVerified.value = true;
  },
});

function sessionIdentity(value: StudentSession): string {
  return JSON.stringify([
    value.user.id,
    value.activeRole,
    value.group?.id ?? null,
    value.semester?.id ?? null,
  ]);
}

function currentFetcher(): typeof fetch | undefined {
  return fixtureTransport;
}

function openNotifications(): void {
  if (!notificationsEntryVisible.value) return;
  notificationTargetIntent.value = null;
  const generation = sessionOwner.currentGeneration();
  notificationsGeneration.value = generation;
  if (!notificationsApi.value) return;
  void notificationUnreadOwner.refresh();
  notificationsOpen.value = true;
}

function closeNotifications(): void {
  notificationsOpen.value = false;
  notificationsGeneration.value = null;
}

async function openNotificationAction(action: NotificationAction): Promise<boolean> {
  return executeNotificationAction(
    action,
    () => ({
      generation: sessionOwner.currentGeneration(),
      userId: profile.value?.userId ?? '',
      role: profile.value?.activeRole ?? null,
      authorityKey: `${notificationContextKey.value}|${ownerKey.value}|${adminAuthorityOwner.snapshot.revision}`,
      online: !offline.value && notificationsEntryVisible.value,
    }),
    {
      async open(target, isCurrent) {
        if (!isCurrent()) return false;
        if (target.role === 'STUDENT') {
          const currentScope = scope.value;
          if (
            !studentViewVisible.value ||
            !currentScope ||
            currentScope.activeRole !== 'STUDENT' ||
            currentScope.resetGeneration !== sessionOwner.currentGeneration()
          )
            return false;
          if (target.kind === 'group' || target.kind === 'homework-week')
            return (
              studentNotificationOwner.value?.openNotificationAction(target, isCurrent) ?? false
            );
          let legacy: NotificationTarget;
          if (target.kind === 'homework') {
            if (!target.lessonDate) return false;
            legacy = {
              kind: 'homework',
              homeworkId: target.homeworkId,
              lessonDate: target.lessonDate,
            };
          } else if (target.kind === 'lesson')
            legacy = { kind: 'lesson', lessonId: target.lessonId };
          else
            legacy = {
              kind: 'request',
              requestId: target.requestId,
              requestKind: target.requestKind,
            };
          notificationTargetIntent.value = {
            requestId: ++notificationTargetIntentId,
            generation: sessionOwner.currentGeneration(),
            ownerKey: ownerKey.value,
            target: legacy,
          };
          return true;
        }
        if (target.role === 'HEADMAN' && headmanViewVisible.value)
          return (
            (await headmanNotificationOwner.value?.openNotificationAction(target, isCurrent)) ??
            false
          );
        if (target.role === 'TEACHER' && teacherViewVisible.value)
          return (
            (await teacherNotificationOwner.value?.openNotificationAction(target, isCurrent)) ??
            false
          );
        if (
          target.role === 'ADMIN' &&
          target.kind === 'group' &&
          adminCanNavigate(adminAuthority.value, 'groups')
        ) {
          const id = notificationNumericId(target.groupId);
          if (id === null) return false;
          navigateAdmin('groups');
          await nextTick();
          if (!isCurrent()) return false;
          return (await adminTaskOwner.value?.openNotificationGroup?.(id, isCurrent)) ?? false;
        }
        return false;
      },
    },
  );
}
watch(
  () =>
    [
      profile.value?.userId,
      profile.value?.activeRole,
      notificationContextKey.value,
      notificationsEntryVisible.value,
      offline.value,
      ownerRevision.value,
    ] as const,
  async ([userId, role, , visible, isOffline]) => {
    if (!visible || isOffline) return;
    await nextTick();
    const result = await notificationLaunch.consume(
      userId ?? null,
      role ?? null,
      openNotificationAction,
    );
    if (result === 'role-required')
      notificationLaunchNotice.value =
        'Для этого уведомления выбери нужную роль через свой профиль.';
    else if (result === 'unavailable')
      notificationLaunchNotice.value =
        'Объект уведомления сейчас недоступен. Ты можешь открыть свою историю уведомлений.';
    else if (result === 'opened') notificationLaunchNotice.value = null;
  },
  { flush: 'post' },
);
watch(
  () => profile.value?.userId,
  (currentUser, previousUser) => {
    if (previousUser && currentUser !== previousUser) {
      notificationLaunch.clear();
      notificationLaunchNotice.value = null;
    }
  },
);

function clearNotificationTargetIntent(requestId: number): void {
  if (notificationTargetIntent.value?.requestId === requestId)
    notificationTargetIntent.value = null;
}

watch(
  () =>
    [
      sessionOwner.resetGeneration.value,
      profile.value?.userId,
      profile.value?.activeRole,
      offline.value,
      notificationContextKey.value,
    ] as const,
  ([generation, userId, role, isOffline]) => {
    notificationUnreadOwner.bind(null, null);
    notificationsApi.value = null;
    if (!userId || !role || isOffline) return;
    const api = createGenerationBoundNotificationsV2Api(
      {
        currentRole: () => profile.value?.activeRole ?? null,
        currentContext: () => notificationContextKey.value,
        currentGeneration: () => sessionOwner.currentGeneration(),
        accessTokenFor: (capturedGeneration) => {
          if (!sessionOwner.isCurrent(capturedGeneration)) throw new StaleSessionGenerationError();
          return sessionOwner.accessToken.value;
        },
        refreshFor: (capturedGeneration) => sessionOwner.authenticateFor(capturedGeneration),
      },
      currentFetcher(),
    );
    notificationsApi.value = api;
    notificationUnreadOwner.bind(`${generation}|${userId}|${role}`, api);
    void notificationUnreadOwner.refresh();
  },
  { immediate: true, flush: 'sync' },
);

watch(
  () =>
    [
      sessionOwner.resetGeneration.value,
      profile.value?.userId,
      profile.value?.activeRole,
      ownerKey.value,
      notificationContextKey.value,
    ] as const,
  (current, previous) => {
    if (
      !previous ||
      (current[0] === previous[0] &&
        current[1] === previous[1] &&
        current[2] === previous[2] &&
        current[3] === previous[3] &&
        current[4] === previous[4])
    )
      return;
    notificationTargetIntent.value = null;
    if (notificationsOpen.value) closeNotifications();
  },
);

watch(offline, (isOffline) => {
  if (isOffline) notificationTargetIntent.value = null;
});

const adminDashboardRealtimeAccess = computed(
  () =>
    profile.value?.activeRole === 'ADMIN' &&
    !profile.value.readOnly &&
    adminAuthorityStatus.value === 'ready' &&
    adminCanNavigate(adminAuthority.value, 'home'),
);
let disposeNotificationsRealtime = (): void => undefined;
watch(
  () =>
    [
      sessionOwner.resetGeneration.value,
      profile.value?.userId,
      profile.value?.activeRole,
      scope.value?.groupId ?? null,
      headmanGroupId.value,
      offline.value,
      adminDashboardRealtimeAccess.value,
      adminAuthority.value?.version ?? null,
      notificationContextKey.value,
    ] as const,
  ([, userId, activeRole, studentGroupId, currentHeadmanGroupId, isOffline, dashboardAccess]) => {
    disposeNotificationsRealtime();
    disposeNotificationsRealtime = (): void => undefined;
    if (fixtureMode || isOffline || !userId || !activeRole) return;
    if (activeRole === 'STUDENT' && scope.value === null) return;
    if (activeRole === 'HEADMAN' && currentHeadmanGroupId === null) return;

    const authorityRevision = adminAuthorityOwner.snapshot.revision;
    const groupId =
      activeRole === 'STUDENT'
        ? studentGroupId
        : activeRole === 'HEADMAN'
          ? String(currentHeadmanGroupId)
          : null;
    disposeNotificationsRealtime = createGenerationBoundNotificationsRealtime({
      owner: {
        currentGeneration: () => sessionOwner.currentGeneration(),
        accessTokenFor: (generation) => {
          if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError();
          return sessionOwner.accessToken.value;
        },
        refreshFor: (generation) => sessionOwner.authenticateFor(generation),
      },
      scope: {
        userId,
        groupId,
        headman: activeRole === 'HEADMAN',
        adminDashboard: dashboardAccess,
      },
      fetcher: currentFetcher() ?? nativeFetcher,
      onAdminDashboardChanged: () => {
        if (adminDashboardRealtimeAccess.value && adminAuthorityOwner.isCurrent(authorityRevision))
          adminDashboardRevision.value += 1;
      },
      onChanged: () => {
        notificationRealtimeRevision.value += 1;
        void notificationUnreadOwner.invalidate();
      },
    }).dispose;
  },
  { immediate: true, flush: 'sync' },
);

function authDenialStatus(cause: unknown): number | null {
  if (cause instanceof TmaAuthError) return cause.status;
  if (cause instanceof NotificationsApiError) return cause.status;
  if (cause instanceof StudentApiError) return cause.response.status;
  if (cause instanceof TeacherApiError) return cause.response.status;
  if (cause instanceof HeadmanSubjectsApiError) return cause.response.status;
  if (cause instanceof HeadmanScheduleApiError) return cause.response.status;
  if (cause instanceof HeadmanJournalApiError) return cause.response.status;
  if (cause instanceof HeadmanRequestsApiError) return cause.response.status;
  if (cause instanceof HeadmanHomeworkApiError) return cause.response.status;
  if (cause instanceof HeadmanStatsApiError) return cause.response.status;
  if (cause instanceof HeadmanGroupApiError) return cause.response.status;
  if (cause instanceof AdminAccessApiError) return cause.response.status;
  if (cause instanceof AdminSemesterApiError) return cause.response.status;
  if (cause instanceof AdminDashboardApiError) return cause.response.status;
  if (cause instanceof AdminGroupsApiError) return cause.response.status;
  if (cause instanceof AdminUsersApiError) return cause.response.status;
  return null;
}

function isAuthDenied(cause: unknown): boolean {
  if (cause instanceof TmaAuthError) return cause.status === 401 || cause.status === 403;
  const status = authDenialStatus(cause);
  if (status === 401) return true;
  if (cause instanceof StudentApiError) {
    const code = cause.problem?.extras?.code;
    return (
      code === 'INVALID_SESSION' || code === 'SESSION_REVOKED' || code === 'ACCOUNT_INVALIDATED'
    );
  }
  return false;
}

function browserIsOffline(): boolean {
  return typeof navigator !== 'undefined' && !navigator.onLine;
}

async function fetchSessionForCurrentGeneration(): Promise<{
  generation: number;
  api: StudentApi;
  session: StudentSession;
}> {
  const generation = sessionOwner.currentGeneration();
  const candidateApi = sessionOwner.createApi(currentFetcher());
  const candidateSession = await candidateApi.getSession();
  if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError();
  return { generation, api: candidateApi, session: candidateSession };
}

async function fetchAssistantCapabilities(
  generation: number,
  studentSession: StudentSession,
): Promise<{
  journalApi: HeadmanJournalApi;
  statsApi: HeadmanStatsApi;
  requestsApi: HeadmanRequestsApi;
  homeworkApi: HeadmanHomeworkApi;
  permissions: readonly HeadmanPermissionOption[];
}> {
  const groupApi = sessionOwner.createHeadmanGroupApi(currentFetcher());
  const journalApi = sessionOwner.createHeadmanJournalApi(currentFetcher());
  const statsApi = sessionOwner.createHeadmanStatsApi(currentFetcher());
  const requestsApi = sessionOwner.createHeadmanRequestsApi(currentFetcher());
  const homeworkApi = sessionOwner.createHeadmanHomeworkApi(currentFetcher());
  const groupId = studentSession.group?.id ? Number(studentSession.group.id) : null;
  let permissions: readonly HeadmanPermissionOption[] = [];
  if (groupId !== null && Number.isSafeInteger(groupId) && groupId > 0) {
    try {
      permissions = await groupApi.listMyPermissions();
    } catch (cause) {
      if (!isAssistantPermissionReadForbidden(cause) || isAuthDenied(cause)) throw cause;
    }
  }
  if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError();
  return { journalApi, statsApi, requestsApi, homeworkApi, permissions };
}

async function fetchProfileForCurrentGeneration(
  reuseValidatedSession = false,
): Promise<{ generation: number; profile: ProfileSnapshot }> {
  const generation = sessionOwner.currentGeneration();
  if (!reuseValidatedSession) await sessionOwner.authenticateFor(generation);
  const candidateProfile = await sessionOwner.getProfileFor(generation);
  if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError();
  return { generation, profile: candidateProfile };
}

function authorizedHeadmanGroupId(value: ProfileSnapshot): number | null {
  if (value.activeRole !== 'HEADMAN') return null;
  const grant = value.roles.find(
    (candidate) =>
      candidate.role === 'HEADMAN' && candidate.status === 'ACTIVE' && candidate.groupId,
  );
  const raw = grant?.groupId;
  if (!raw || !/^\d+$/.test(raw)) return null;
  const groupId = Number(raw);
  return Number.isSafeInteger(groupId) && groupId > 0 ? groupId : null;
}

function positiveSafeHomeworkActorUserId(value: ProfileSnapshot): number | null {
  if (!/^[1-9]\d*$/.test(value.userId)) return null;
  const userId = Number(value.userId);
  return Number.isSafeInteger(userId) && userId > 0 ? userId : null;
}

type TeacherCandidate = {
  generation: number;
  api: TeacherApi;
  semesterId: number;
  profile: ProfileSnapshot;
};

async function fetchTeacherCandidate(
  generation: number,
  value: ProfileSnapshot,
): Promise<TeacherCandidate | null> {
  if (value.activeRole !== 'TEACHER') return null;
  const candidateApi = sessionOwner.createTeacherApi(currentFetcher());
  const semester = await candidateApi.semester();
  if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError();
  return { generation, api: candidateApi, semesterId: semester.id, profile: value };
}

function asProfileError(cause: unknown): ProfileRequestError {
  if (cause instanceof ProfileRequestError) return cause;
  return new ProfileRequestError(
    'UNKNOWN',
    cause instanceof Error ? cause.message : 'Не удалось сменить роль',
  );
}

function invalidateOwnerSynchronously(options: { clearAuth?: boolean } = {}): void {
  if (options.clearAuth !== false) sessionOwner.clear();
  if (roleOperationGeneration !== null && !sessionOwner.isCurrent(roleOperationGeneration)) {
    roleOperation += 1;
    roleOperationGeneration = null;
    pendingRole.value = null;
    roleLoading.value = false;
  }
  teacherApi.value = null;
  teacherSemesterId.value = null;
  headmanApi.value = null;
  headmanJournalApi.value = null;
  headmanRequestsApi.value = null;
  headmanGroupApi.value = null;
  headmanSubjectsApi.value = null;
  headmanStatsApi.value = null;
  headmanHomeworkApi.value = null;
  headmanHomeworkActorUserId.value = null;
  assistantJournalApi.value = null;
  assistantStatsApi.value = null;
  assistantRequestsApi.value = null;
  assistantHomeworkApi.value = null;
  assistantPermissionOptions.value = [];
  assistantPermissionsVerified.value = false;
  adminAccessApi.value = null;
  adminAccessActionsApi.value = null;
  adminMapClient.value = null;
  adminAuthorityOwner.bind(null, null);
  adminSemesterApi.value = null;
  adminAttendanceApi.value = null;
  adminDashboardApi.value = null;
  adminUsersApi.value = null;
  adminGroupsApi.value = null;
  headmanGroupId.value = null;
  scope.value = null;
  api.value = null;
  session.value = null;
  profile.value = null;
  profilePort.value = null;
  ownerRevision.value += 1;
}

function activateMapRole(value: ProfileSnapshot): void {
  invalidateOwnerSynchronously({ clearAuth: false });
  profile.value = value;
  adminSemesterApi.value =
    value.activeRole === 'ADMIN' ? sessionOwner.createAdminSemesterApi(currentFetcher()) : null;
  adminDashboardApi.value =
    value.activeRole === 'ADMIN' ? sessionOwner.createAdminDashboardApi(currentFetcher()) : null;
  adminUsersApi.value =
    value.activeRole === 'ADMIN' ? sessionOwner.createAdminUsersApi(currentFetcher()) : null;
  adminGroupsApi.value =
    value.activeRole === 'ADMIN' ? sessionOwner.createAdminGroupsApi(currentFetcher()) : null;
  const generation = sessionOwner.currentGeneration();
  profilePort.value =
    value.activeRole === 'ADMIN'
      ? sessionOwner.createProfilePort(generation, {
          onInvalidated: (reason) => handleProfileInvalidated(generation, reason),
        })
      : null;
  offline.value = false;
  error.value = null;
  ownerRevision.value += 1;
  authView.value = value.activeRole === 'ADMIN' ? 'admin-profile' : 'map';
  if (value.activeRole === 'ADMIN') {
    adminAccessApi.value = sessionOwner.createAdminAccessApi(currentFetcher());
    adminAuthorityOwner.bind(
      adminAccessApi.value,
      `${sessionOwner.currentGeneration()}:${profile.value?.userId}`,
      profile.value?.userId ?? null,
    );
    void refreshAdminAuthority();
  }
}

function navigateAdmin(route: AdminRoleRoute): void {
  if (profile.value?.activeRole !== 'ADMIN' || !adminCanNavigate(adminAuthority.value, route))
    return;
  adminNavigationVisible.value = true;
  authView.value =
    route === 'more'
      ? 'admin-more'
      : route === 'profile'
        ? 'admin-profile'
        : route === 'home'
          ? 'admin-home'
          : route === 'attendance'
            ? 'admin-attendance'
            : route === 'map'
              ? 'admin-map'
              : route === 'map-statistics'
                ? 'admin-map-statistics'
                : route === 'semesters'
                  ? 'admin-semesters'
                  : route === 'users'
                    ? 'admin-users'
                    : 'admin-groups';
}

function openAdminRoleSwitch(): void {
  if (
    (!adminHomeViewVisible.value && !adminProfileViewVisible.value) ||
    profile.value?.activeRole !== 'ADMIN' ||
    !profilePort.value
  )
    return;
  roleError.value = null;
  error.value = null;
  adminRoleReturn.value = adminProfileViewVisible.value ? 'admin-profile' : 'admin-home';
  authView.value = 'admin-role-switch';
}

function closeAdminRoleSwitch(): void {
  if (profile.value?.activeRole === 'ADMIN') authView.value = adminRoleReturn.value;
}

async function logout(): Promise<void> {
  if (authView.value === 'signed-out') return;
  logoutPending.value = true;
  logoutUnconfirmed.value = false;
  const logoutRequest = sessionOwner.logoutCurrent();
  invalidateOwnerSynchronously({ clearAuth: false });
  authView.value = 'signed-out';
  error.value = null;
  offline.value = false;
  ready.value = true;
  bootstrapping.value = false;
  try {
    await logoutRequest;
    logoutPending.value = false;
    host.close();
  } catch (cause) {
    logoutPending.value = false;
    logoutUnconfirmed.value = true;
    error.value =
      cause instanceof Error
        ? cause.message
        : 'Не удалось подтвердить отзыв текущей сессии. Локальные данные удалены; закрой окно вручную.';
  }
}

async function activateTeacherCandidate(candidate: TeacherCandidate): Promise<void> {
  invalidateOwnerSynchronously({ clearAuth: false });
  if (!sessionOwner.isCurrent(candidate.generation)) throw new StaleSessionGenerationError();
  teacherApi.value = candidate.api;
  teacherSemesterId.value = candidate.semesterId;
  profile.value = candidate.profile;
  profilePort.value = sessionOwner.createProfilePort(candidate.generation, {
    onInvalidated: (reason) => handleProfileInvalidated(candidate.generation, reason),
  });
  offline.value = false;
  error.value = null;
  ownerRevision.value += 1;
  authView.value = 'teacher';
}

function activateHeadmanOwner(value: ProfileSnapshot, generation: number): void {
  invalidateOwnerSynchronously({ clearAuth: false });
  authView.value = 'role';
  if (!sessionOwner.isCurrent(generation)) throw new StaleSessionGenerationError();
  profile.value = value;
  const groupId = authorizedHeadmanGroupId(value);
  if (groupId === null) {
    throw new ProfileRequestError(
      'BOOTSTRAP_SCOPE_DENIED',
      'Для роли старосты не определена учебная группа',
    );
  }
  profile.value = value;
  headmanApi.value = sessionOwner.createHeadmanApi(currentFetcher());
  headmanJournalApi.value = sessionOwner.createHeadmanJournalApi(currentFetcher());
  headmanStatsApi.value = sessionOwner.createHeadmanStatsApi(currentFetcher());
  headmanRequestsApi.value = sessionOwner.createHeadmanRequestsApi(currentFetcher());
  headmanGroupApi.value = sessionOwner.createHeadmanGroupApi(currentFetcher());
  headmanSubjectsApi.value = sessionOwner.createHeadmanSubjectsApi(currentFetcher());
  headmanHomeworkApi.value = sessionOwner.createHeadmanHomeworkApi(currentFetcher());
  headmanHomeworkActorUserId.value = positiveSafeHomeworkActorUserId(value);
  headmanGroupId.value = groupId;
  profilePort.value = sessionOwner.createProfilePort(generation, {
    onInvalidated: (reason) => handleProfileInvalidated(generation, reason),
  });
  offline.value = false;
  error.value = null;
  ownerRevision.value += 1;
  authView.value = 'headman';
}

async function bootstrap(): Promise<void> {
  if (bootstrapping.value || authView.value === 'signed-out') return;
  if (browserIsOffline()) {
    offline.value = true;
    ready.value = true;
    error.value = 'TMA работает только при подключении к интернету';
    return;
  }
  bootstrapping.value = true;
  const operation = ++bootstrapOperation;
  let operationGeneration = sessionOwner.currentGeneration();
  const reuseValidatedSession = authorityBootstrapPending;
  authorityBootstrapPending = false;
  try {
    const candidateProfile = await fetchProfileForCurrentGeneration(reuseValidatedSession);
    profile.value = candidateProfile.profile;
    roleError.value = null;
    if (candidateProfile.profile.activeRole === 'HEADMAN') {
      activateHeadmanOwner(candidateProfile.profile, candidateProfile.generation);
    } else if (candidateProfile.profile.activeRole === 'STUDENT') {
      const candidate = await fetchSessionForCurrentGeneration();
      const assistant = await fetchAssistantCapabilities(candidate.generation, candidate.session);
      if (session.value && sessionIdentity(session.value) !== sessionIdentity(candidate.session)) {
        invalidateOwnerSynchronously({ clearAuth: false });
        profile.value = candidateProfile.profile;
      }
      const nextScope = studentFeatureScope(candidate.session, candidate.generation);
      const sameOwner =
        scope.value !== null &&
        studentFeatureScopeIdentity(scope.value) === studentFeatureScopeIdentity(nextScope);
      // Keep a live owner mounted for same-generation refreshes so an ambiguous
      // check-in retains its command and idempotency key across reconnect.
      const needsFreshOwner = !sameOwner || api.value === null;
      headmanApi.value = null;
      headmanJournalApi.value = null;
      headmanStatsApi.value = null;
      headmanRequestsApi.value = null;
      headmanGroupApi.value = null;
      headmanSubjectsApi.value = null;
      headmanHomeworkApi.value = null;
      headmanHomeworkActorUserId.value = null;
      headmanGroupId.value = null;
      assistantJournalApi.value = assistant.journalApi;
      assistantStatsApi.value = assistant.statsApi;
      assistantRequestsApi.value = assistant.requestsApi;
      assistantHomeworkApi.value = assistant.homeworkApi;
      assistantPermissionOptions.value = assistant.permissions;
      assistantPermissionsVerified.value = true;
      api.value = needsFreshOwner ? candidate.api : api.value;
      session.value = candidate.session;
      scope.value = nextScope;
      if (needsFreshOwner || profilePort.value === null) {
        profilePort.value = sessionOwner.createProfilePort(candidate.generation, {
          onInvalidated: (reason) => handleProfileInvalidated(candidate.generation, reason),
        });
      }
      authView.value = 'student';
      if (needsFreshOwner) ownerRevision.value += 1;
    } else if (candidateProfile.profile.activeRole === 'TEACHER') {
      const candidate = await fetchTeacherCandidate(
        candidateProfile.generation,
        candidateProfile.profile,
      );
      if (!candidate)
        throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна');
      await activateTeacherCandidate(candidate);
    } else if (candidateProfile.profile.activeRole === 'ADMIN') {
      activateMapRole(candidateProfile.profile);
    } else {
      invalidateOwnerSynchronously({ clearAuth: false });
      profile.value = candidateProfile.profile;
      authView.value = 'role';
      roleError.value = null;
    }
    offline.value = false;
    error.value = null;
  } catch (cause) {
    if (
      operation !== bootstrapOperation ||
      !sessionOwner.isCurrent(operationGeneration) ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    if (isAuthDenied(cause)) {
      invalidateOwnerSynchronously();
      operationGeneration = sessionOwner.currentGeneration();
      offline.value = false;
    } else {
      // Keep an existing owner mounted but read-only when the authority check
      // cannot complete; a later retry can reuse its recovery intent.
      offline.value = true;
    }
    error.value = cause instanceof Error ? cause.message : 'Не удалось открыть приложение';
  } finally {
    if (operation === bootstrapOperation) {
      if (sessionOwner.isCurrent(operationGeneration)) ready.value = true;
      bootstrapping.value = false;
      if (authorityBootstrapPending) void bootstrap();
    }
  }
}

async function selectRole(
  role: ProfileRole,
  expectedSessionVersion: string,
  options: { preserveProfileOwner?: boolean } = {},
): Promise<void> {
  if (roleLoading.value || offline.value || !profile.value) return;
  if (role !== 'STUDENT' && role !== 'HEADMAN' && role !== 'TEACHER' && role !== 'ADMIN') return;
  const grant = profile.value.roles.find((candidate) => candidate.role === role);
  if (!grant?.selectable) return;
  roleLoading.value = true;
  pendingRole.value = role;
  roleError.value = null;
  error.value = null;
  const generation = sessionOwner.currentGeneration();
  const operation = ++roleOperation;
  let operationGeneration = generation;
  roleOperationGeneration = generation;
  try {
    const selection = await sessionOwner.selectRoleFor(generation, {
      role,
      expectedSessionVersion,
    });
    if (operation !== roleOperation || !sessionOwner.isCurrent(selection.generation)) return;
    operationGeneration = selection.generation;
    roleOperationGeneration = selection.generation;
    if (options.preserveProfileOwner) {
      invalidateOwnerSynchronously({ clearAuth: false });
      authView.value = 'role';
    }
    profile.value = selection.session;
    if (role === 'HEADMAN') {
      activateHeadmanOwner(selection.session, selection.generation);
    } else if (role === 'TEACHER') {
      const candidate = await fetchTeacherCandidate(selection.generation, selection.session);
      if (!candidate)
        throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна');
      await activateTeacherCandidate(candidate);
    } else if (role === 'ADMIN') {
      activateMapRole(selection.session);
    } else {
      const candidateApi = sessionOwner.createApi(currentFetcher());
      const candidateSession = await candidateApi.getSession();
      if (!sessionOwner.isCurrent(selection.generation)) throw new StaleSessionGenerationError();
      const assistant = await fetchAssistantCapabilities(selection.generation, candidateSession);
      headmanApi.value = null;
      headmanJournalApi.value = null;
      headmanStatsApi.value = null;
      headmanRequestsApi.value = null;
      headmanGroupApi.value = null;
      headmanSubjectsApi.value = null;
      headmanHomeworkApi.value = null;
      headmanHomeworkActorUserId.value = null;
      headmanGroupId.value = null;
      assistantJournalApi.value = assistant.journalApi;
      assistantStatsApi.value = assistant.statsApi;
      assistantRequestsApi.value = assistant.requestsApi;
      assistantHomeworkApi.value = assistant.homeworkApi;
      assistantPermissionOptions.value = assistant.permissions;
      assistantPermissionsVerified.value = true;
      api.value = candidateApi;
      session.value = candidateSession;
      scope.value = studentFeatureScope(candidateSession, selection.generation);
      profilePort.value = sessionOwner.createProfilePort(selection.generation, {
        onInvalidated: (reason) => handleProfileInvalidated(selection.generation, reason),
      });
      authView.value = 'student';
      ownerRevision.value += 1;
    }
    offline.value = false;
  } catch (cause) {
    if (
      operation !== roleOperation ||
      !sessionOwner.isCurrent(operationGeneration) ||
      cause instanceof StaleSessionGenerationError
    )
      return;
    roleError.value = asProfileError(cause);
    error.value = roleError.value.message;
    if (!options.preserveProfileOwner) authView.value = 'role';
    try {
      const current = await sessionOwner.getProfileFor(operationGeneration);
      if (operation !== roleOperation || !sessionOwner.isCurrent(operationGeneration)) return;
      profile.value = current;
    } catch {
      // Keep the current role snapshot visible until the next authenticated retry.
    }
    if (operation !== roleOperation || !sessionOwner.isCurrent(operationGeneration)) return;
    if (options.preserveProfileOwner) throw cause;
  } finally {
    if (operation === roleOperation) {
      roleOperationGeneration = null;
      pendingRole.value = null;
      roleLoading.value = false;
    }
  }
}

function selectProfileRole(role: ProfileRole, expectedSessionVersion: string): Promise<void> {
  return selectRole(role, expectedSessionVersion, { preserveProfileOwner: true });
}

function handleProfileInvalidated(
  generation: number,
  reason:
    | 'logout-all'
    | 'password-changed'
    | 'session-terminated'
    | 'account-invalidated' = 'account-invalidated',
): void {
  if (!sessionOwner.isCurrent(generation)) return;
  invalidateOwnerSynchronously();
  offline.value = false;
  error.value = null;
  if (reason === 'logout-all' || reason === 'password-changed' || reason === 'session-terminated') {
    authView.value = 'signed-out';
    ready.value = true;
    host.close();
    return;
  }
  authView.value = 'role';
  void bootstrap();
}

const stopSessionInvalidation = sessionOwner.subscribeInvalidation(() => {
  // The session owner has already advanced generation and validated the
  // candidate bearer. Drop every mounted owner before starting its replacement.
  invalidateOwnerSynchronously({ clearAuth: false });
  authView.value = 'role';
  offline.value = false;
  error.value = null;
  roleError.value = null;
  authorityBootstrapPending = true;
  if (!bootstrapping.value) void bootstrap();
});

function acquireCheckinCommand(): Promise<StudentCheckinCommand> {
  return host.location();
}

function openPasswordRecovery(): void {
  host.openLink(publicPasswordRecoveryUrl(window.location.origin));
}

function openMaterial(url: string): void {
  try {
    const parsed = new URL(url);
    if ((parsed.protocol !== 'http:' && parsed.protocol !== 'https:') || !parsed.hostname) return;
    host.openLink(url);
  } catch {
    // Unsupported server data remains inert.
  }
}

function onOwnerError(cause: unknown): void {
  if (
    profile.value?.activeRole === 'ADMIN' &&
    authDenialStatus(cause) === 403 &&
    !isAdminPasswordProofFailure((cause as { problem?: unknown } | null)?.problem)
  ) {
    void refreshAdminAuthority();
    return;
  }
  if (isAuthDenied(cause)) {
    invalidateOwnerSynchronously();
    offline.value = false;
  } else if (isAssistantCapabilityForbiddenError(cause)) {
    void assistantPermissionRefresher.refresh('forbidden').catch((refreshError: unknown) => {
      if (isAuthDenied(refreshError)) {
        invalidateOwnerSynchronously();
        offline.value = false;
      }
    });
  }
  error.value =
    cause instanceof StudentApiError
      ? (cause.problem?.detail ?? cause.message)
      : cause instanceof Error
        ? cause.message
        : 'Не удалось выполнить действие';
}

function offlineNow(): void {
  offline.value = true;
  error.value = 'TMA работает только при подключении к интернету';
}

function onlineNow(): void {
  themeMirror?.reconcile();
  if (authView.value === 'signed-out') return;
  // Browser online is only a transport hint. Keep the owner read-only until
  // Telegram authentication and the session identity have been checked.
  offline.value = true;
  error.value = 'Проверяем подключение к Telegram…';
  void bootstrap();
}

function onAssistantForeground(): void {
  if (typeof document === 'undefined' || document.visibilityState !== 'visible') return;
  void assistantPermissionRefresher.refresh('foreground').catch((cause: unknown) => {
    if (isAuthDenied(cause)) {
      invalidateOwnerSynchronously();
      offline.value = false;
    }
  });
}

onMounted(() => {
  window.addEventListener('offline', offlineNow);
  window.addEventListener('online', onlineNow);
  document.addEventListener('visibilitychange', onAssistantForeground);
  void bootstrap();
});

onBeforeUnmount(() => {
  stopAdminAuthority();
  adminAuthorityOwner.dispose();
  stopSessionInvalidation();
  authorityBootstrapPending = false;
  stopNotificationUnread();
  notificationUnreadOwner.bind(null, null);
  disposeNotificationsRealtime();
  disposeNotificationsRealtime = (): void => undefined;
  invalidateOwnerSynchronously();
  window.removeEventListener('offline', offlineNow);
  window.removeEventListener('online', onlineNow);
  document.removeEventListener('visibilitychange', onAssistantForeground);
  stopThemeSnapshot?.();
  themeMirror?.dispose();
  themePreference.dispose();
  theme?.dispose();
});
</script>

<template>
  <p
    v-if="notificationLaunchNotice && notificationsEntryVisible"
    class="notification-launch-notice"
    role="status"
  >
    {{ notificationLaunchNotice }}
    <button
      type="button"
      @click="notificationLaunchNotice = null"
    >
      Закрыть
    </button>
  </p>
  <NotificationsEntryButton
    v-if="
      notificationsEntryVisible &&
      !studentViewVisible &&
      !headmanViewVisible &&
      !teacherViewVisible &&
      !adminHomeViewVisible
    "
    floating
    :count="notificationUnread.count"
    @click="openNotifications"
  />
  <Teleport to="body">
    <NotificationsScreen
      v-if="notificationsOpen && notificationsApi"
      :key="notificationContextKey"
      :api="notificationsApi"
      :context-key="notificationContextKey"
      :unread-owner="notificationUnreadOwner"
      :host="host"
      :offline="offline"
      :realtime-revision="notificationRealtimeRevision"
      :can-open-target="canOpenNotificationTarget"
      :open-action="openNotificationAction"
      @close="closeNotifications"
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
    ref="headmanNotificationOwner"
    :key="`headman-${ownerRevision}`"
    :on-notifications="openNotifications"
    :unread-count="notificationUnread.count"
    :api="headmanApi"
    :journal-api="headmanJournalApi"
    :lesson-actions-api="headmanLessonActionsApi"
    :stats-api="headmanStatsApi"
    :requests-api="headmanRequestsApi"
    :group-api="headmanGroupApi"
    :subjects-api="headmanSubjectsApi"
    :homework-api="headmanHomeworkApi"
    :homework-actor-user-id="headmanHomeworkActorUserId"
    :open-material="openMaterial"
    :profile="profile"
    :profile-port="profilePort"
    :on-logout="logout"
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
    ref="teacherNotificationOwner"
    :key="`teacher-${ownerRevision}`"
    :on-notifications="openNotifications"
    :unread-count="notificationUnread.count"
    :api="teacherApi"
    :semester-id="teacherSemesterId"
    :map-client="mapClient"
    :report-download="reportDownload"
    :profile-port="profilePort"
    :on-recover="openPasswordRecovery"
    :profile-role-select="selectProfileRole"
    :host="host"
    :theme-controller="theme"
    :offline="offline"
    @owner-error="onOwnerError"
  />
  <MapScreen
    v-else-if="mapViewVisible"
    :client="mapClient"
    :theme="resolvedTheme"
  />
  <AdminShell
    v-else-if="adminMoreViewVisible"
    :authority="adminAuthority"
    active="more"
    :host="host"
    @navigate="navigateAdmin"
  >
    <AdminMoreScreen
      :authority="adminAuthority"
      @navigate="navigateAdmin"
    />
  </AdminShell>
  <AdminShell
    v-else-if="adminHomeViewVisible && adminDashboardApi"
    :authority="adminAuthority"
    active="home"
    :task="!adminNavigationVisible"
    :host="host"
    @navigate="navigateAdmin"
    @back="adminTaskOwner?.back()"
  >
    <AdminDashboardScreen
      :key="`admin-home-${ownerRevision}`"
      :on-notifications="openNotifications"
      :unread-count="notificationUnread.count"
      :client="adminDashboardApi"
      :invalidation-revision="adminDashboardRevision"
      :theme="resolvedTheme"
      @owner-error="onOwnerError"
      @open-role-switch="openAdminRoleSwitch"
      @open-attendance="navigateAdmin('attendance')"
    />
  </AdminShell>
  <AdminShell
    v-else-if="adminAttendanceViewVisible && adminAttendanceApi"
    :authority="adminAuthority"
    active="attendance"
    :task="true"
    :host="host"
    @navigate="navigateAdmin"
    @back="navigateAdmin('home')"
  >
    <AdminAttendanceScreen
      :key="`admin-attendance-${ownerRevision}`"
      :online="!offline && !profile?.readOnly"
      :client="adminAttendanceApi"
      :authority="adminAuthority"
      :report-download="reportDownload"
      :show-back="false"
      @back="navigateAdmin('home')"
      @owner-error="onOwnerError"
    />
  </AdminShell>
  <AdminShell
    v-else-if="adminMapStatisticsViewVisible && adminMapClient"
    :authority="adminAuthority"
    active="map-statistics"
    :task="true"
    :host="host"
    @navigate="navigateAdmin"
    @back="navigateAdmin('more')"
  >
    <AdminMapStatisticsScreen
      :authority="adminAuthority"
      :client="adminMapClient"
      :show-back="false"
      :theme="resolvedTheme"
      @back="navigateAdmin('more')"
      @owner-error="onOwnerError"
    />
  </AdminShell>
  <AdminShell
    v-else-if="adminMapViewVisible && adminMapClient"
    :authority="adminAuthority"
    active="map"
    :task="true"
    :host="host"
    @navigate="navigateAdmin"
    @back="navigateAdmin('more')"
  >
    <AdminMapScreen
      :authority="adminAuthority"
      :client="adminMapClient"
      :show-back="false"
      :theme="resolvedTheme"
      @back="navigateAdmin('more')"
      @owner-error="onOwnerError"
    />
  </AdminShell>
  <AdminShell
    v-else-if="adminSemesterViewVisible && adminSemesterApi"
    :authority="adminAuthority"
    active="semesters"
    :task="!adminNavigationVisible"
    :host="host"
    @navigate="navigateAdmin"
    @back="adminTaskOwner?.back()"
  >
    <AdminSemesterScreen
      ref="adminTaskOwner"
      :authority="adminAuthority"
      :client="adminSemesterApi"
      :theme="resolvedTheme"
      @navigation-visibility="adminNavigationVisible = $event"
      @back="navigateAdmin('more')"
      @owner-error="onOwnerError"
    />
  </AdminShell>
  <AdminShell
    v-else-if="adminUsersViewVisible && adminUsersApi"
    :authority="adminAuthority"
    active="users"
    :task="!adminNavigationVisible"
    :host="host"
    @navigate="navigateAdmin"
    @back="adminTaskOwner?.back()"
  >
    <AdminUsersScreen
      ref="adminTaskOwner"
      :report-download="reportDownload"
      :access-client="adminAccessActionsApi"
      :authority="adminAuthority"
      :client="adminUsersApi"
      :theme="resolvedTheme"
      @navigation-visibility="adminNavigationVisible = $event"
      @owner-error="onOwnerError"
    />
  </AdminShell>
  <AdminShell
    v-else-if="adminGroupsViewVisible && adminGroupsApi"
    :authority="adminAuthority"
    active="groups"
    :task="!adminNavigationVisible"
    :host="host"
    @navigate="navigateAdmin"
    @back="adminTaskOwner?.back()"
  >
    <AdminGroupsScreen
      ref="adminTaskOwner"
      :report-download="reportDownload"
      :authority="adminAuthority"
      :client="adminGroupsApi"
      :theme="resolvedTheme"
      @navigation-visibility="adminNavigationVisible = $event"
      @owner-error="onOwnerError"
    />
  </AdminShell>
  <AdminShell
    v-else-if="adminProfileViewVisible && profilePort"
    :authority="adminAuthority"
    active="profile"
    :task="!adminNavigationVisible"
    :host="host"
    @navigate="navigateAdmin"
    @back="adminTaskOwner?.back()"
  >
    <div
      v-if="adminAccessMessage"
      class="admin-shell__access-state"
    >
      <AdminNotice
        tone="warning"
        :title="
          adminAuthorityStatus === 'loading'
            ? 'Проверяем права доступа'
            : adminAuthorityStatus === 'error'
              ? 'Не удалось подтвердить права'
              : 'Административные разделы недоступны'
        "
        :aria-busy="adminAuthorityStatus === 'loading'"
      >
        <p>{{ adminAccessMessage }}</p>
        <template
          v-if="adminAuthorityStatus === 'error'"
          #actions
        >
          <button
            type="button"
            @click="refreshAdminAuthority"
          >
            Повторить
          </button>
        </template>
      </AdminNotice>
    </div>
    <AdminProfileOwner
      ref="adminTaskOwner"
      :key="'admin-profile-' + ownerRevision"
      :profile-port="profilePort"
      :theme-controller="theme"
      :offline="offline"
      :on-logout="logout"
      @navigation-visibility="adminNavigationVisible = $event"
      @open-role-switch="openAdminRoleSwitch"
    />
  </AdminShell>
  <AdminShell
    v-else-if="adminRoleSwitchViewVisible && profile"
    :authority="adminAuthority"
    active="home"
    :task="true"
    :host="host"
    @navigate="navigateAdmin"
    @back="closeAdminRoleSwitch"
  >
    <RoleSwitchScreen
      compact
      :snapshot="profile"
      :pending-role="pendingRole"
      :error="roleError"
      :show-roles-on-error="true"
      :offline="offline"
      :loading="roleLoading || bootstrapping"
      :on-back="closeAdminRoleSwitch"
      :on-select-role="selectProfileRole"
    />
  </AdminShell>
  <StudentFeatureOwner
    v-else-if="studentViewVisible"
    ref="studentNotificationOwner"
    :key="ownerKey"
    :owner-key="ownerKey"
    :api="api"
    :scope="scope"
    :offline="offline"
    :read-only="false"
    :today-fallback="null"
    :homework-fallback="null"
    :semester-starts-on="session?.semester?.startsOn ?? ''"
    :semester-ends-on="session?.semester?.endsOn ?? ''"
    :semester-schedule="null"
    :load-semester-schedule="true"
    :updated-at="null"
    :host="host"
    :profile-port="profilePort"
    :profile-role-select="selectProfileRole"
    :theme-controller="theme"
    :report-download="reportDownload"
    :map-client="mapClient"
    :acquire-checkin-command="acquireCheckinCommand"
    :open-material="openMaterial"
    :notification-target-intent="notificationTargetIntent"
    :on-notifications="openNotifications"
    :unread-count="notificationUnread.count"
    :on-logout="logout"
    :assistant-permissions="assistantPermissions"
    :assistant-permission-options="assistantPermissionsVerified ? assistantPermissionOptions : []"
    :assistant-journal-api="assistantJournalApi"
    :assistant-stats-api="assistantStatsApi"
    :assistant-requests-api="assistantRequestsApi"
    :assistant-homework-api="assistantHomeworkApi"
    @owner-error="onOwnerError"
    @clear-notification-target="clearNotificationTargetIntent"
  />
  <section
    v-if="authView === 'signed-out'"
    class="today-state"
    role="status"
    aria-live="polite"
  >
    <h2>{{ signedOutTitle }}</h2>
    <p>
      {{ signedOutMessage }}
    </p>
    <button
      type="button"
      :disabled="logoutPending"
      @click="host.close()"
    >
      Закрыть
    </button>
  </section>
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
    <h2 v-if="error"> Не удалось открыть приложение </h2>
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

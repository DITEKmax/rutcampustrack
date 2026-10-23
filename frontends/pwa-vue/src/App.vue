<script setup lang="ts">
import { createDeviceThemeMirror, createDeviceThemeTransport } from '../../shared/foundation';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, toRaw, watch } from 'vue';
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
  SemesterSnapshotStore,
  HeadmanScheduleApiError,
  HeadmanJournalApiError,
  HeadmanRequestsApiError,
  HeadmanHomeworkApiError,
  HeadmanStatsApiError,
  HeadmanGroupApiError,
  HeadmanSubjectsApiError,
  HeadmanScheduleScreen,
  MapScreen,
  StudentApi,
  StudentApiError,
  NotificationsApiError,
  NotificationsEntryButton,
  NotificationsScreen,
  NotificationsUnreadState,
  StudentFeatureOwner,
  TeacherApi,
  TeacherApiError,
  TeacherFeatureOwner,
  ProfileRequestError,
  RoleSwitchScreen,
  StudentWarningBlock,
  StaleSessionGenerationError,
  createAssistantPermissionRefresher,
  isAssistantCapabilityForbiddenError,
  isAssistantPermissionReadForbidden,
  commandFromCoordinates,
  createFixtureTransport,
  createGenerationBoundNotificationsV2Api,
  createGenerationBoundNotificationsRealtime,
  createGenerationBoundReportDownloadClient,
  createBrowserReportDownloadPort,
  createMobileTheme,
  createMobileThemePreference,
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
  type HeadmanSubjectsApi,
  type HeadmanStatsApi,
  type HeadmanPermissionOption,
  type AdminSemesterClient,
  type AdminDashboardClient,
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
import { createMoscowDayClock, useOfflineTodayFallback } from './offline-today-fallback';
import type { ProfilePort, ProfileRole, ProfileSnapshot } from '@rct/mobile-core';
import { AuthRequestError } from './auth-client';
import { PwaAuthError, usePwaAuth, type PwaAuthInvalidationReason } from './auth';
import LoginScreen from './LoginScreen.vue';
import PasswordRecoveryScreen from './features/password-recovery/PasswordRecoveryScreen.vue';
import {
  consumePasswordResetFragment,
  isPasswordResetEntryPath,
  passwordRecoveryLoginPath,
} from './features/password-recovery/password-recovery-link';
import type { PasswordResetProof } from './features/password-recovery/password-recovery-link';
import { PwaHostAdapter } from './pwa-host';
import { isOfflineBootstrapRecoveryError } from './bootstrap-policy';
import { createPwaRoleSelection } from './role-flow';
import { assertAuthBffCoherence } from './session-coherence';
import PwaUpdateGate from './PwaUpdateGate.vue';
import { createPwaFetcher } from './pwa-version';
import InstallOffer from './features/install/InstallOffer.vue';
import type { InstallPromptController } from './features/install/install-prompt';
import { createPushController } from './features/push/push-controller';
import { createBrowserPushPort } from './features/push/browser-push';

const props = defineProps<{ installPrompt: InstallPromptController }>();
const installPrompt = props.installPrompt;

const fixtureMode = import.meta.env.VITE_MOBILE_FIXTURE_MODE === 'true';
const fixtureDiagnosticsMode =
  fixtureMode && new URLSearchParams(window.location.search).get('fixtureDiagnostics') === 'true';
const fixtureServiceWorkerBuildEnabled = import.meta.env.PROD;
const fixtureTransport = fixtureMode ? createFixtureTransport() : undefined;
const pwaFetcher = createPwaFetcher();
const requestFetcher: typeof fetch = fixtureTransport ?? pwaFetcher;
const push = createPushController(
  createBrowserPushPort(
    !fixtureMode && import.meta.env.MODE === 'production',
    import.meta.env.BASE_URL,
  ),
  requestFetcher,
);
let currentOnlyGeneration: number | null = null;
const auth = usePwaAuth({
  fetcher: requestFetcher,
  beforeClear: () => push.invalidate(),
  // Disabled queries can still finish an already scheduled retry. A retained
  // offline owner must not turn that background 401 into another rotation.
  canRefresh: (generation) =>
    generation !== currentOnlyGeneration && (!offline.value || bootstrapping.value),
});
const reportDownload = computed(() =>
  createBrowserReportDownloadPort(
    createGenerationBoundReportDownloadClient(
      {
        currentGeneration: () => auth.currentGeneration(),
        accessTokenFor: (generation) => {
          if (!auth.isCurrent(generation)) throw new StaleSessionGenerationError();
          return auth.accessToken.value;
        },
        refreshFor: (generation) => auth.refreshFor(generation),
      },
      currentFetcher(),
      () => {
        const device = auth.deviceSession.value;
        return device ? JSON.stringify([device.userId, device.sessionId, 'HEADMAN']) : null;
      },
    ),
    currentFetcher(),
  ),
);
const mapClient = new CampusMapClient({
  accessToken: () => auth.accessToken.value,
  currentGeneration: () => auth.currentGeneration(),
  onUnauthorized: () => auth.refreshFor(auth.currentGeneration()),
  fetcher: requestFetcher,
});
const adminMapClient = shallowRef<AdminMapClient | null>(null);
const host = new PwaHostAdapter(push);
const themePreference = createMobileThemePreference(() =>
  typeof window === 'undefined' ? null : window.localStorage,
);
const theme =
  typeof document === 'undefined'
    ? null
    : createMobileTheme({ mode: themePreference.read() ?? 'system' });
const resolvedTheme = ref(theme?.resolvedMode ?? 'light');
const stopThemeSnapshot = theme?.subscribe((snapshot) => {
  resolvedTheme.value = snapshot.resolvedMode;
});
const themeMirror = theme
  ? createDeviceThemeMirror({
      theme,
      preference: themePreference,
      transport: createDeviceThemeTransport(requestFetcher),
    })
  : null;
const snapshotStore = new SemesterSnapshotStore();

const api = shallowRef<StudentApi | null>(null);
const teacherApi = shallowRef<TeacherApi | null>(null);
const teacherSemesterId = ref<number | null>(null);
const headmanApi = shallowRef<HeadmanScheduleApi | null>(null);
const headmanJournalApi = shallowRef<HeadmanJournalApi | null>(null);
const headmanLessonActionsApi = computed(() =>
  headmanJournalApi.value ? auth.createHeadmanLessonActionsApi(currentFetcher()) : null,
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
    auth.resetGeneration.value,
    authSnapshot.value?.userId,
    authSnapshot.value?.activeRole,
    authSnapshot.value?.sessionVersion,
    authSnapshot.value?.rolesVersion,
    authSnapshot.value?.readOnly,
  ].join('|'),
);
const notificationsOpen = ref(false);
const notificationsGeneration = ref<number | null>(null);
const notificationTargetIntent = shallowRef<NotificationTargetIntent | null>(null);
const notificationLaunchUrl = new URL(window.location.href);
const notificationLaunchEncoded = notificationLaunchUrl.searchParams.get('notificationAction');
if (notificationLaunchEncoded !== null) {
  notificationLaunchUrl.searchParams.delete('notificationAction');
  window.history.replaceState(window.history.state, '', notificationLaunchUrl);
}
let notificationLaunch = new NotificationLaunchOwner(
  decodeNotificationLaunch(notificationLaunchEncoded),
  notificationLaunchUrl.searchParams.get('pushOwner'),
);
const notificationLaunchRevision = ref(0);
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
const profilePort = shallowRef<ProfilePort | null>(null);
const snapshot = shallowRef<SemesterSnapshot | null>(null);
const semesterSchedule = shallowRef<StudentSemesterSchedule | null>(null);
const onlineSemesterSchedule = shallowRef<StudentSemesterSchedule | null>(null);
const offline = ref(typeof navigator !== 'undefined' ? !navigator.onLine : false);
const readOnly = ref(false);
const directPasswordRecoveryEntry = isPasswordResetEntryPath(
  window.location.pathname,
  import.meta.env.BASE_URL,
);
const directPasswordResetProof = shallowRef<PasswordResetProof | null>(null);
const directPasswordResetLinkError = ref<string | null>(null);
if (directPasswordRecoveryEntry) {
  const linkFragment = window.location.hash;
  try {
    directPasswordResetProof.value = consumePasswordResetFragment(linkFragment, () => {
      window.history.replaceState(
        window.history.state,
        '',
        `${window.location.pathname}${window.location.search}`,
      );
    });
    if (!directPasswordResetProof.value) {
      directPasswordResetLinkError.value = linkFragment
        ? 'Ссылка восстановления повреждена. Запроси новый код.'
        : 'В ссылке нет кода восстановления. Запроси новый код.';
    }
  } catch {
    directPasswordResetLinkError.value = 'Не удалось безопасно открыть ссылку. Запроси новый код.';
  }
}
const passwordRecoveryNotice = ref<string | null>(null);
const sessionReady = ref(false);
const bootstrapping = ref(false);
const bootstrapError = ref<string | null>(null);
const authView = ref<
  | 'login'
  | 'password-recovery'
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
>(directPasswordRecoveryEntry ? 'password-recovery' : 'login');
const authSnapshot = shallowRef<ProfileSnapshot | null>(null);
watch(
  () =>
    [
      auth.accessToken.value,
      authSnapshot.value?.userId,
      authSnapshot.value?.sessionId,
      auth.resetGeneration.value,
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

const authError = shallowRef<ProfileRequestError | null>(null);
const persistedLogoutState = auth.explicitLogoutState();
const logoutPending = ref(false);
const logoutNotice = ref<string | null>(
  persistedLogoutState === 'unconfirmed' || persistedLogoutState === 'pending'
    ? 'Auth не подтвердил отзыв предыдущей сессии. Автоматический вход отключён; войди вручную.'
    : persistedLogoutState === 'confirmed'
      ? 'Ты вышел из аккаунта. Чтобы войти снова, введи данные.'
      : null,
);
const authLoading = ref(false);
const pendingRole = ref<ProfileRole | null>(null);
const fixtureDiagnosticLines = ref<string[]>([]);
const ownerRevision = ref(0);
const cleanupResult = ref<SnapshotCleanupResult | null>(null);
const cleanupRetrying = ref(false);
let cleanupUiGeneration = 0;
let stopAuthInvalidation = (): void => undefined;
let queuedBootstrapAfterInvalidation = false;
let pendingOnlineGeneration: number | null = null;

const moscowDayClock = createMoscowDayClock();
const cachedToday = useOfflineTodayFallback(
  () => snapshot.value?.schedule ?? null,
  moscowDayClock.currentDate,
);
const cachedHomework = computed(() => snapshot.value?.homework ?? null);
const displayedSemesterSchedule = computed(() =>
  offline.value ? semesterSchedule.value : onlineSemesterSchedule.value,
);
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
  if (authSnapshot.value?.activeRole !== 'ADMIN') return;
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
  const generation = auth.currentGeneration();
  const assertAuthority = (): void => {
    if (!auth.isCurrent(generation)) throw new StaleSessionGenerationError();
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
    adminAccessActionsApi.value = auth.createAdminAccessApi(fetcher, assertAuthority);
    adminDashboardApi.value = auth.createAdminDashboardApi(fetcher, assertAuthority);
    adminAttendanceApi.value = adminCanNavigate(snapshot.profile, 'attendance')
      ? new AdminAttendanceClient({
          accessToken: () => {
            assertAuthority();
            return auth.accessToken.value;
          },
          assertCurrent: assertAuthority,
          onUnauthorized: async () => {
            assertAuthority();
            await auth.refreshFor(generation);
            assertAuthority();
          },
          fetcher,
        })
      : null;
    adminUsersApi.value = auth.createAdminUsersApi(fetcher, assertAuthority);
    adminGroupsApi.value = auth.createAdminGroupsApi(fetcher, assertAuthority);
    adminSemesterApi.value = auth.createAdminSemesterApi(fetcher, assertAuthority);
    adminMapClient.value = new AdminMapClient({
      accessToken: () => {
        assertAuthority();
        return auth.accessToken.value;
      },
      currentGeneration: createAdminGenerationReader(
        () => auth.currentGeneration(),
        () => adminAuthorityOwner.isCurrent(snapshot.revision),
        generation,
      ),
      onUnauthorized: async () => {
        assertAuthority();
        await auth.refreshFor(generation);
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
const featureVisible = computed(
  () =>
    api.value !== null ||
    headmanApi.value !== null ||
    snapshot.value !== null ||
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
    authSnapshot.value !== null &&
    authSnapshot.value.activeRole !== null &&
    authView.value !== 'login' &&
    authView.value !== 'role',
);
watch(
  auth.deviceSession,
  (deviceSession) => {
    push.setOwner(deviceSession);
  },
  { flush: 'sync' },
);

function consumePushEntry(): void {
  const url = new URL(window.location.href);
  if (url.searchParams.get('pushNotifications') !== '1') return;
  if (!notificationsEntryVisible.value) return;
  const matches = url.searchParams.get('pushOwner') === authSnapshot.value?.userId;
  url.searchParams.delete('pushNotifications');
  url.searchParams.delete('pushOwner');
  window.history.replaceState(window.history.state, '', url);
  if (matches && !notificationLaunch.pendingRole) openNotifications();
}
watch(notificationsEntryVisible, consumePushEntry);
function onPushMessage(event: MessageEvent<unknown>): void {
  const data = event.data as {
    type?: string;
    userId?: string;
    notificationAction?: unknown;
  } | null;
  if (data?.type !== 'RCT_PUSH_OPEN' || data.userId !== authSnapshot.value?.userId) return;
  const action = decodeNotificationLaunch(
    typeof data.notificationAction === 'string' ? data.notificationAction : null,
  );
  if (!action) {
    openNotifications();
    return;
  }
  notificationLaunch = new NotificationLaunchOwner(action, data.userId);
  notificationLaunchRevision.value++;
}
if ('serviceWorker' in navigator)
  navigator.serviceWorker.addEventListener('message', onPushMessage);
const studentViewVisible = computed(() => authView.value === 'student' && featureVisible.value);
const canOpenNotificationTarget = computed(
  () => notificationsEntryVisible.value && notificationsApi.value !== null && !offline.value,
);
const teacherViewVisible = computed(
  () => authView.value === 'teacher' && teacherApi.value !== null,
);
const headmanViewVisible = computed(
  () => authView.value === 'headman' && headmanApi.value !== null,
);
const headmanGroupId = ref<number | null>(null);
const ownerKey = computed(() =>
  scope.value
    ? `${studentFeatureScopeIdentity(scope.value)}|${ownerRevision.value}`
    : `${snapshot.value?.scopeKey ?? 'offline-read-model'}|${ownerRevision.value}`,
);

function currentFetcher(): typeof fetch {
  return requestFetcher;
}

function currentAssistantOwnerKey(): string | null {
  const currentScope = scope.value;
  if (
    authView.value !== 'student' ||
    offline.value ||
    !currentScope ||
    currentScope.activeRole !== 'STUDENT' ||
    currentScope.resetGeneration !== auth.currentGeneration()
  )
    return null;
  return ownerKey.value;
}

const assistantPermissionRefresher = createAssistantPermissionRefresher<HeadmanPermissionOption>({
  currentGeneration: () => auth.currentGeneration(),
  currentOwnerKey: currentAssistantOwnerKey,
  load: async (generation, expectedOwnerKey) => {
    const current = session.value;
    if (
      !auth.isCurrent(generation) ||
      currentAssistantOwnerKey() !== expectedOwnerKey ||
      !current
    ) {
      throw new StaleSessionGenerationError();
    }
    const groupId = current.group?.id ? Number(current.group.id) : null;
    if (groupId === null || !Number.isSafeInteger(groupId) || groupId <= 0) return [];
    const permissions = await auth.createHeadmanGroupApi(currentFetcher()).listMyPermissions();
    if (!auth.isCurrent(generation) || currentAssistantOwnerKey() !== expectedOwnerKey) {
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

function authDenialStatus(error: unknown): number | null {
  if (error instanceof PwaAuthError) return error.status;
  if (error instanceof AuthRequestError) return error.status ?? null;
  if (error instanceof StudentApiError) return error.response.status;
  if (error instanceof NotificationsApiError) return error.status;
  if (error instanceof TeacherApiError) return error.response.status;
  if (error instanceof HeadmanScheduleApiError) return error.response.status;
  if (error instanceof HeadmanJournalApiError) return error.response.status;
  if (error instanceof HeadmanRequestsApiError) return error.response.status;
  if (error instanceof HeadmanHomeworkApiError) return error.response.status;
  if (error instanceof HeadmanStatsApiError) return error.response.status;
  if (error instanceof HeadmanGroupApiError) return error.response.status;
  if (error instanceof HeadmanSubjectsApiError) return error.response.status;
  if (error instanceof AdminAccessApiError) return error.response.status;
  if (error instanceof AdminSemesterApiError) return error.response.status;
  if (error instanceof AdminDashboardApiError) return error.response.status;
  if (error instanceof AdminGroupsApiError) return error.response.status;
  if (error instanceof AdminUsersApiError) return error.response.status;
  return null;
}

function openNotifications(): void {
  if (!notificationsEntryVisible.value) return;
  notificationTargetIntent.value = null;
  const generation = auth.currentGeneration();
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
      generation: auth.currentGeneration(),
      userId: authSnapshot.value?.userId ?? '',
      role: authSnapshot.value?.activeRole ?? null,
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
            currentScope.resetGeneration !== auth.currentGeneration()
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
            generation: auth.currentGeneration(),
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
      authSnapshot.value?.userId,
      authSnapshot.value?.activeRole,
      notificationContextKey.value,
      notificationsEntryVisible.value,
      offline.value,
      ownerRevision.value,
      notificationLaunchRevision.value,
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
  () => authSnapshot.value?.userId,
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
      auth.resetGeneration.value,
      authSnapshot.value?.userId,
      authSnapshot.value?.activeRole,
      offline.value,
      notificationContextKey.value,
    ] as const,
  ([generation, userId, role, isOffline]) => {
    notificationUnreadOwner.bind(null, null);
    notificationsApi.value = null;
    if (!userId || !role || isOffline) return;
    const api = createGenerationBoundNotificationsV2Api(
      {
        currentRole: () => authSnapshot.value?.activeRole ?? null,
        currentContext: () => notificationContextKey.value,
        currentGeneration: () => auth.currentGeneration(),
        accessTokenFor: (capturedGeneration) => {
          if (!auth.isCurrent(capturedGeneration)) throw new StaleSessionGenerationError();
          return auth.accessToken.value;
        },
        refreshFor: (capturedGeneration) => auth.refreshFor(capturedGeneration),
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
      auth.resetGeneration.value,
      authSnapshot.value?.userId,
      authSnapshot.value?.activeRole,
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
    authSnapshot.value?.activeRole === 'ADMIN' &&
    !authSnapshot.value.readOnly &&
    adminAuthorityStatus.value === 'ready' &&
    adminCanNavigate(adminAuthority.value, 'home'),
);
let disposeNotificationsRealtime = (): void => undefined;
watch(
  () =>
    [
      auth.resetGeneration.value,
      authSnapshot.value?.userId,
      authSnapshot.value?.activeRole,
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
        currentGeneration: () => auth.currentGeneration(),
        accessTokenFor: (generation) => {
          if (!auth.isCurrent(generation)) throw new StaleSessionGenerationError();
          return auth.accessToken.value;
        },
        refreshFor: (generation) => auth.refreshFor(generation),
      },
      scope: {
        userId,
        groupId,
        headman: activeRole === 'HEADMAN',
        adminDashboard: dashboardAccess,
      },
      fetcher: currentFetcher(),
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

function isConfirmedOnlineAuthDenial(error: unknown): boolean {
  if (authDenialStatus(error) === 401) return true;
  if (error instanceof AuthRequestError) {
    return (
      (error.operation === 'current-session' &&
        error.status === 403 &&
        error.problem?.gatewayCurrentRoleDenied === true) ||
      error.code === 'INVALID_SESSION' ||
      error.code === 'SESSION_REVOKED' ||
      error.code === 'ACCOUNT_INVALIDATED' ||
      error.code === 'REFRESH_REJECTED'
    );
  }
  if (error instanceof StudentApiError) {
    const code = error.problem?.extras?.code;
    return (
      code === 'INVALID_SESSION' || code === 'SESSION_REVOKED' || code === 'ACCOUNT_INVALIDATED'
    );
  }
  return false;
}

function persistedScope(value: StudentFeatureScope) {
  return {
    userId: value.userId!,
    activeRole: value.activeRole!,
    groupId: value.groupId,
    semesterId: value.semesterId!,
  };
}

function makeSnapshot(
  value: StudentFeatureScope,
  schedule: StudentSemesterSchedule,
  previous: SemesterSnapshot | null,
  homework: StudentHomework | null | undefined,
  etag: string | null,
): SemesterSnapshot {
  const nextHomework = homework === undefined ? (previous?.homework ?? null) : homework;
  const cachedAt = nextHomework?.serverNow ?? schedule.updatedAt ?? previous?.cachedAt ?? null;
  return {
    ownerId: value.userId!,
    scopeKey: studentOfflineScopeKey(value),
    scope: persistedScope(value),
    schedule,
    etag,
    homework: nextHomework ? structuredClone(toRaw(nextHomework)) : null,
    ...(cachedAt ? { cachedAt } : {}),
  };
}

function isPersistedSnapshot(value: SemesterSnapshot | null): value is SemesterSnapshot {
  if (
    !value ||
    typeof value.ownerId !== 'string' ||
    !value.schedule ||
    typeof value.schedule !== 'object'
  )
    return false;
  const persisted = value.scope;
  if (!persisted || typeof value.scopeKey !== 'string') return false;
  return (
    typeof persisted.userId === 'string' &&
    persisted.userId === value.ownerId &&
    persisted.activeRole === 'STUDENT' &&
    typeof persisted.semesterId === 'string' &&
    persisted.semesterId === value.schedule.semester?.id &&
    (persisted.groupId === null || typeof persisted.groupId === 'string') &&
    value.scopeKey ===
      studentOfflineScopeKey({
        ...persisted,
        sessionId: null,
        sessionVersion: null,
        rolesVersion: null,
        readOnly: true,
        resetGeneration: 0,
      })
  );
}

async function loadOfflineSnapshot(): Promise<boolean> {
  if (authView.value === 'headman' || authView.value === 'teacher') return false;
  const generation = auth.currentGeneration();
  // Do not keep a previous online query model alive while deciding whether a
  // committed snapshot can be used. This also prevents a late query result
  // from becoming the offline view after a reconnect failure.
  scope.value = null;
  api.value = null;
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
  session.value = null;
  authSnapshot.value = null;
  authError.value = null;
  semesterSchedule.value = null;
  onlineSemesterSchedule.value = null;
  snapshot.value = null;
  ownerRevision.value += 1;
  // A known unresolved cleanup keeps every current-realm offline hydration
  // blocked until the user completes the local retry.
  if (snapshotStore.hasUnresolvedCleanup()) return false;
  try {
    const value = await snapshotStore.readCurrent();
    if (!auth.isCurrent(generation)) return false;
    if (!isPersistedSnapshot(value)) return false;
    const persisted = value.scope;
    if (!persisted) return false;
    snapshot.value = value;
    semesterSchedule.value = value.schedule;
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
    };
    api.value = null;
    teacherApi.value = null;
    teacherSemesterId.value = null;
    session.value = null;
    offline.value = true;
    readOnly.value = true;
    authSnapshot.value = null;
    authError.value = null;
    authView.value = 'student';
    ownerRevision.value += 1;
    return true;
  } catch {
    return false;
  }
}

/** Invalidate reactive owner references before Vue unmounts its feature view. */
function invalidateOwnerSynchronously(
  options: { clearAuth?: boolean } = {},
): StudentSession | null {
  const previous = session.value;
  cleanupUiGeneration += 1;
  cleanupRetrying.value = false;
  snapshotStore.invalidatePendingWrites();
  if (options.clearAuth !== false) auth.clear();
  profilePort.value = null;
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
  teacherApi.value = null;
  teacherSemesterId.value = null;
  scope.value = null;
  api.value = null;
  session.value = null;
  semesterSchedule.value = null;
  onlineSemesterSchedule.value = null;
  snapshot.value = null;
  return previous;
}

function observeCleanupResult(result: SnapshotCleanupResult): void {
  if (result.retryRequired) {
    cleanupResult.value = result;
    return;
  }
  if (!snapshotStore.hasUnresolvedCleanup()) cleanupResult.value = null;
}

async function clearOwnerSnapshot(previous: StudentSession | null): Promise<SnapshotCleanupResult> {
  const uiGeneration = cleanupUiGeneration;
  const result = await snapshotStore.clearDetailed(previous?.user.id);
  if (uiGeneration === cleanupUiGeneration) observeCleanupResult(result);
  return result;
}

async function handleProfileInvalidated(
  reason: 'logout-all' | 'password-changed' | 'session-terminated' | 'account-invalidated',
  generation: number,
): Promise<void> {
  if (!auth.isCurrent(generation)) return;
  const previous = invalidateOwnerSynchronously();
  authSnapshot.value = null;
  authError.value = null;
  authView.value = 'login';
  sessionReady.value = true;
  offline.value = false;
  readOnly.value = true;
  bootstrapError.value =
    reason === 'password-changed'
      ? 'Пароль изменён. Войди снова.'
      : reason === 'logout-all'
        ? 'Сеанс завершён на всех устройствах.'
        : 'Сессия больше недоступна.';
  // Publish sign-out before storage I/O; its late completion cannot replace
  // a manual login that has already installed a new authenticated owner.
  await clearOwnerSnapshot(previous);
}

async function retryPendingCleanup(): Promise<void> {
  const pending = cleanupResult.value;
  if (!pending || cleanupRetrying.value) return;
  const uiGeneration = ++cleanupUiGeneration;
  cleanupRetrying.value = true;
  try {
    const result = await snapshotStore.retryCleanup(pending);
    if (uiGeneration === cleanupUiGeneration) observeCleanupResult(result);
  } finally {
    if (uiGeneration === cleanupUiGeneration) cleanupRetrying.value = false;
  }
}

async function handleOnlineAuthDenial(
  error: unknown,
  options: { initialAnonymousSessionRequest?: boolean } = {},
): Promise<void> {
  const hadAuthenticatedOwner =
    authSnapshot.value !== null ||
    auth.accessToken.value !== null ||
    api.value !== null ||
    teacherApi.value !== null ||
    headmanApi.value !== null ||
    snapshot.value !== null;
  const explicitRevocation =
    error instanceof AuthRequestError &&
    ['INVALID_SESSION', 'SESSION_REVOKED', 'ACCOUNT_INVALIDATED', 'REFRESH_REJECTED'].includes(
      error.serverCode ?? '',
    );
  const expectedInitialAnonymousDenial =
    options.initialAnonymousSessionRequest === true &&
    !hadAuthenticatedOwner &&
    !explicitRevocation &&
    error instanceof AuthRequestError &&
    error.operation === 'current-session' &&
    error.status === 401;
  const previous = invalidateOwnerSynchronously();
  await clearOwnerSnapshot(previous);
  offline.value = false;
  readOnly.value = true;
  authSnapshot.value = null;
  authView.value = 'login';
  authError.value = null;
  bootstrapError.value = expectedInitialAnonymousDenial
    ? null
    : authDenialStatus(error) === 401 || hadAuthenticatedOwner || explicitRevocation
      ? 'Сессия завершена или больше недоступна. Войди снова.'
      : error instanceof Error
        ? error.message
        : 'Сессия больше недоступна';
}

async function fetchAuthForCurrentGeneration(
  options: { refresh?: boolean; currentOnly?: boolean } = {},
): Promise<{
  generation: number;
  profile: ProfileSnapshot;
}> {
  const generation = auth.currentGeneration();
  if (!options.currentOnly && (options.refresh !== false || auth.accessToken.value === null))
    await auth.refreshFor(generation);
  const profile = await auth.getSessionFor(generation, {
    revalidate: options.currentOnly === true,
  });
  if (!auth.isCurrent(generation)) throw new StaleSessionGenerationError();
  return { generation, profile };
}

async function fetchStudentCandidate(
  generation: number,
  profile: ProfileSnapshot,
): Promise<{
  generation: number;
  api: StudentApi;
  session: StudentSession;
  profile: ProfileSnapshot;
  assistantJournalApi: HeadmanJournalApi;
  assistantStatsApi: HeadmanStatsApi;
  assistantRequestsApi: HeadmanRequestsApi;
  assistantHomeworkApi: HeadmanHomeworkApi;
  assistantPermissions: readonly HeadmanPermissionOption[];
} | null> {
  if (profile.activeRole !== 'STUDENT') return null;
  const candidateApi = auth.createApi(currentFetcher());
  const candidateSession = await candidateApi.getSession();
  const candidateGroupApi = auth.createHeadmanGroupApi(currentFetcher());
  const candidateJournalApi = auth.createHeadmanJournalApi(currentFetcher());
  const candidateStatsApi = auth.createHeadmanStatsApi(currentFetcher());
  const candidateRequestsApi = auth.createHeadmanRequestsApi(currentFetcher());
  const candidateHomeworkApi = auth.createHeadmanHomeworkApi(currentFetcher());
  const groupId = candidateSession.group?.id ? Number(candidateSession.group.id) : null;
  let permissions: readonly HeadmanPermissionOption[] = [];
  if (groupId !== null && Number.isSafeInteger(groupId) && groupId > 0) {
    try {
      permissions = await candidateGroupApi.listMyPermissions();
    } catch (cause) {
      if (!isAssistantPermissionReadForbidden(cause) || isConfirmedOnlineAuthDenial(cause))
        throw cause;
    }
  }
  assertCandidateCurrent(generation);
  assertAuthBffCoherence(profile, candidateSession);
  return {
    generation,
    api: candidateApi,
    session: candidateSession,
    profile,
    assistantJournalApi: candidateJournalApi,
    assistantStatsApi: candidateStatsApi,
    assistantRequestsApi: candidateRequestsApi,
    assistantHomeworkApi: candidateHomeworkApi,
    assistantPermissions: permissions,
  };
}

type HeadmanCandidate = {
  generation: number;
  api: HeadmanScheduleApi;
  journalApi: HeadmanJournalApi;
  statsApi: HeadmanStatsApi;
  requestsApi: HeadmanRequestsApi;
  groupApi: HeadmanGroupApi;
  subjectsApi: HeadmanSubjectsApi;
  homeworkApi: HeadmanHomeworkApi;
  homeworkActorUserId: number | null;
  profile: ProfileSnapshot;
  groupId: number;
};

function positiveSafeHomeworkActorUserId(profile: ProfileSnapshot): number | null {
  if (!/^[1-9]\d*$/.test(profile.userId)) return null;
  const userId = Number(profile.userId);
  return Number.isSafeInteger(userId) && userId > 0 ? userId : null;
}

function authorizedHeadmanGroupId(profile: ProfileSnapshot): number | null {
  if (profile.activeRole !== 'HEADMAN') return null;
  const grant = profile.roles.find(
    (candidate) =>
      candidate.role === 'HEADMAN' && candidate.status === 'ACTIVE' && candidate.groupId,
  );
  const raw = grant?.groupId;
  if (!raw || !/^\d+$/.test(raw)) return null;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
}

async function fetchHeadmanCandidate(
  generation: number,
  profile: ProfileSnapshot,
): Promise<HeadmanCandidate | null> {
  const groupId = authorizedHeadmanGroupId(profile);
  if (profile.activeRole !== 'HEADMAN' || groupId === null) {
    throw new ProfileRequestError(
      'BOOTSTRAP_SCOPE_DENIED',
      'Для роли старосты не определена учебная группа',
    );
  }
  const candidateApi = auth.createHeadmanApi(currentFetcher());
  const candidateJournalApi = auth.createHeadmanJournalApi(currentFetcher());
  const candidateStatsApi = auth.createHeadmanStatsApi(currentFetcher());
  const candidateRequestsApi = auth.createHeadmanRequestsApi(currentFetcher());
  const candidateGroupApi = auth.createHeadmanGroupApi(currentFetcher());
  const candidateSubjectsApi = auth.createHeadmanSubjectsApi(currentFetcher());
  const candidateHomeworkApi = auth.createHeadmanHomeworkApi(currentFetcher());
  assertCandidateCurrent(generation);
  return {
    generation,
    api: candidateApi,
    journalApi: candidateJournalApi,
    statsApi: candidateStatsApi,
    requestsApi: candidateRequestsApi,
    groupApi: candidateGroupApi,
    subjectsApi: candidateSubjectsApi,
    homeworkApi: candidateHomeworkApi,
    homeworkActorUserId: positiveSafeHomeworkActorUserId(profile),
    profile,
    groupId,
  };
}

type TeacherCandidate = {
  generation: number;
  api: TeacherApi;
  semesterId: number;
  profile: ProfileSnapshot;
};

async function fetchTeacherCandidate(
  generation: number,
  profile: ProfileSnapshot,
): Promise<TeacherCandidate | null> {
  if (profile.activeRole !== 'TEACHER') return null;
  const candidateApi = auth.createTeacherApi(currentFetcher());
  const semester = await candidateApi.semester();
  assertCandidateCurrent(generation);
  return { generation, api: candidateApi, semesterId: semester.id, profile };
}

type StudentCandidate = {
  generation: number;
  api: StudentApi;
  session: StudentSession;
  profile: ProfileSnapshot;
  assistantJournalApi: HeadmanJournalApi;
  assistantStatsApi: HeadmanStatsApi;
  assistantRequestsApi: HeadmanRequestsApi;
  assistantHomeworkApi: HeadmanHomeworkApi;
  assistantPermissions: readonly HeadmanPermissionOption[];
};

async function activateCandidate(candidate: StudentCandidate): Promise<void> {
  const nextScope = studentFeatureScope(candidate.session, candidate.generation);
  const sameOwner =
    scope.value !== null &&
    studentFeatureScopeIdentity(scope.value) === studentFeatureScopeIdentity(nextScope);
  // A snapshot-only shell was mounted with the inert API. It needs one fresh
  // feature owner when authority returns; a live same-generation owner must
  // stay mounted so an ambiguous check-in keeps its command and idempotency
  // key across reconnect/auth refresh.
  const needsFreshOwner = !sameOwner || api.value === null;
  let existing: SemesterSnapshot | null = null;
  try {
    const cleanup = await snapshotStore.clearMismatchedCurrent(studentOfflineScopeKey(nextScope));
    assertCandidateCurrent(candidate.generation);
    if (cleanup) observeCleanupResult(cleanup);
    existing = await snapshotStore.read(
      candidate.session.user.id,
      studentOfflineScopeKey(nextScope),
    );
  } catch {
    // Storage is an offline enhancement. An unavailable IDB/localStorage must
    // not prevent an otherwise authenticated online shell from mounting.
  }
  assertCandidateCurrent(candidate.generation);
  const committedSnapshot = isPersistedSnapshot(existing) ? existing : null;
  if (!sameOwner) {
    snapshot.value = committedSnapshot;
    semesterSchedule.value = committedSnapshot?.schedule ?? null;
  } else if (committedSnapshot) {
    snapshot.value = committedSnapshot;
    semesterSchedule.value = committedSnapshot.schedule;
  }
  let nextSchedule = committedSnapshot?.schedule ?? null;
  let nextEtag = committedSnapshot?.etag ?? null;

  if (candidate.session.semester) {
    const response = await candidate.api.getSemesterSchedule(
      candidate.session.semester.id,
      committedSnapshot?.etag ?? undefined,
    );
    assertCandidateCurrent(candidate.generation);
    if (response.data) {
      nextSchedule = response.data;
      nextEtag = response.etag;
    }
  }

  // The session and schedule have both passed the generation gate before the
  // feature owner is mounted. A 304 keeps the matching owner/scope snapshot.
  if (nextSchedule) {
    const nextSnapshot = makeSnapshot(
      nextScope,
      nextSchedule,
      committedSnapshot,
      undefined,
      nextEtag,
    );
    onlineSemesterSchedule.value = nextSchedule;
    if (committedSnapshot) {
      snapshot.value = committedSnapshot;
      semesterSchedule.value = committedSnapshot.schedule;
    }
    try {
      const switchResult = await snapshotStore.switchToDetailed(candidate.session.user.id);
      assertCandidateCurrent(candidate.generation);
      observeCleanupResult(switchResult);
      await snapshotStore.write(nextSnapshot);
      assertCandidateCurrent(candidate.generation);
      snapshot.value = nextSnapshot;
      semesterSchedule.value = nextSchedule;
    } catch {
      // Continue online when persistence is unavailable. The committed
      // snapshot remains authoritative for the next offline transition.
    }
  } else {
    snapshot.value = null;
    semesterSchedule.value = null;
    onlineSemesterSchedule.value = null;
    try {
      const switchResult = await snapshotStore.switchToDetailed(candidate.session.user.id);
      assertCandidateCurrent(candidate.generation);
      observeCleanupResult(switchResult);
    } catch {
      /* online auth remains usable */
    }
  }

  assertCandidateCurrent(candidate.generation);
  api.value = needsFreshOwner ? candidate.api : api.value;
  assistantJournalApi.value = candidate.assistantJournalApi;
  assistantStatsApi.value = candidate.assistantStatsApi;
  assistantRequestsApi.value = candidate.assistantRequestsApi;
  assistantHomeworkApi.value = candidate.assistantHomeworkApi;
  assistantPermissionOptions.value = candidate.assistantPermissions;
  assistantPermissionsVerified.value = true;
  session.value = candidate.session;
  scope.value = nextScope;
  if (!sameOwner || profilePort.value === null) {
    profilePort.value = auth.createProfilePort(candidate.generation, {
      onInvalidated: (reason) => handleProfileInvalidated(reason, candidate.generation),
      onRefreshAlreadyRotated: () =>
        handleProfileInvalidated('account-invalidated', candidate.generation),
    });
  }
  if (needsFreshOwner) ownerRevision.value += 1;
  offline.value = false;
  readOnly.value = candidate.session.readOnly;
  bootstrapError.value = null;
}

async function activateHeadmanCandidate(candidate: HeadmanCandidate): Promise<void> {
  const previous = invalidateOwnerSynchronously({ clearAuth: false });
  await clearOwnerSnapshot(previous);
  assertCandidateCurrent(candidate.generation);
  headmanApi.value = candidate.api;
  headmanJournalApi.value = candidate.journalApi;
  headmanStatsApi.value = candidate.statsApi;
  headmanRequestsApi.value = candidate.requestsApi;
  headmanGroupApi.value = candidate.groupApi;
  headmanSubjectsApi.value = candidate.subjectsApi;
  headmanHomeworkApi.value = candidate.homeworkApi;
  headmanHomeworkActorUserId.value = candidate.homeworkActorUserId;
  headmanGroupId.value = candidate.groupId;
  profilePort.value = auth.createProfilePort(candidate.generation, {
    onInvalidated: (reason) => handleProfileInvalidated(reason, candidate.generation),
    onRefreshAlreadyRotated: () =>
      handleProfileInvalidated('account-invalidated', candidate.generation),
  });
  authSnapshot.value = candidate.profile;
  offline.value = false;
  readOnly.value = candidate.profile.readOnly;
  bootstrapError.value = null;
  ownerRevision.value += 1;
}

async function activateTeacherCandidate(candidate: TeacherCandidate): Promise<void> {
  invalidateOwnerSynchronously({ clearAuth: false });
  assertCandidateCurrent(candidate.generation);
  teacherApi.value = candidate.api;
  teacherSemesterId.value = candidate.semesterId;
  profilePort.value = auth.createProfilePort(candidate.generation, {
    onInvalidated: (reason) => handleProfileInvalidated(reason, candidate.generation),
    onRefreshAlreadyRotated: () =>
      handleProfileInvalidated('account-invalidated', candidate.generation),
  });
  authSnapshot.value = candidate.profile;
  offline.value = false;
  readOnly.value = candidate.profile.readOnly;
  bootstrapError.value = null;
  ownerRevision.value += 1;
}

async function activateMapRole(profile: ProfileSnapshot, generation: number): Promise<void> {
  assertCandidateCurrent(generation);
  const previous = invalidateOwnerSynchronously({ clearAuth: false });
  await clearOwnerSnapshot(previous);
  assertCandidateCurrent(generation);
  authSnapshot.value = profile;
  adminSemesterApi.value =
    profile.activeRole === 'ADMIN' ? auth.createAdminSemesterApi(currentFetcher()) : null;
  adminDashboardApi.value =
    profile.activeRole === 'ADMIN' ? auth.createAdminDashboardApi(currentFetcher()) : null;
  adminUsersApi.value =
    profile.activeRole === 'ADMIN' ? auth.createAdminUsersApi(currentFetcher()) : null;
  adminGroupsApi.value =
    profile.activeRole === 'ADMIN' ? auth.createAdminGroupsApi(currentFetcher()) : null;
  profilePort.value =
    profile.activeRole === 'ADMIN'
      ? auth.createProfilePort(generation, {
          onInvalidated: (reason) => handleProfileInvalidated(reason, generation),
          onRefreshAlreadyRotated: () =>
            handleProfileInvalidated('account-invalidated', generation),
        })
      : null;
  offline.value = false;
  readOnly.value = profile.readOnly;
  bootstrapError.value = null;
  ownerRevision.value += 1;
  authView.value = profile.activeRole === 'ADMIN' ? 'admin-profile' : 'map';
  if (profile.activeRole === 'ADMIN') {
    adminAccessApi.value = auth.createAdminAccessApi(currentFetcher());
    adminAuthorityOwner.bind(
      adminAccessApi.value,
      `${auth.currentGeneration()}:${authSnapshot.value?.userId}`,
      authSnapshot.value?.userId ?? null,
    );
    void refreshAdminAuthority();
  }
}

function navigateAdmin(route: AdminRoleRoute): void {
  if (authSnapshot.value?.activeRole !== 'ADMIN' || !adminCanNavigate(adminAuthority.value, route))
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
    authSnapshot.value?.activeRole !== 'ADMIN' ||
    !profilePort.value
  )
    return;
  authError.value = null;
  bootstrapError.value = null;
  adminRoleReturn.value = adminProfileViewVisible.value ? 'admin-profile' : 'admin-home';
  authView.value = 'admin-role-switch';
}

function closeAdminRoleSwitch(): void {
  if (authSnapshot.value?.activeRole === 'ADMIN') authView.value = adminRoleReturn.value;
}

function assertCandidateCurrent(generation: number): void {
  if (!auth.isCurrent(generation)) throw new StaleSessionGenerationError();
}

function openPasswordRecovery(): void {
  authError.value = null;
  passwordRecoveryNotice.value = null;
  authView.value = 'password-recovery';
}

function clearDirectPasswordResetProof(): void {
  directPasswordResetProof.value = null;
  directPasswordResetLinkError.value = null;
}

function replacePasswordResetRouteWithLogin(): void {
  if (!directPasswordRecoveryEntry) return;
  window.history.replaceState(
    window.history.state,
    '',
    passwordRecoveryLoginPath(import.meta.env.BASE_URL),
  );
  clearDirectPasswordResetProof();
}

function closePasswordRecovery(): void {
  authView.value = 'login';
  replacePasswordResetRouteWithLogin();
}

function completePasswordRecovery(): void {
  authError.value = null;
  passwordRecoveryNotice.value = 'Пароль обновлён. Войди с новым паролем.';
  authView.value = 'login';
  replacePasswordResetRouteWithLogin();
}

function isPasswordRecoveryView(): boolean {
  return authView.value === 'password-recovery';
}

async function bootstrap(
  options: { refresh?: boolean; currentOnly?: boolean } = {},
): Promise<void> {
  if (isPasswordRecoveryView()) return;
  if (!auth.canAutoBootstrap()) {
    void push.invalidate();
    authView.value = 'login';
    sessionReady.value = true;
    offline.value = false;
    readOnly.value = true;
    return;
  }
  if (bootstrapping.value) return;
  bootstrapping.value = true;
  currentOnlyGeneration = options.currentOnly ? auth.currentGeneration() : null;
  let uncertainAuthRequest = false;
  try {
    const authCandidate = await fetchAuthForCurrentGeneration(options);
    authSnapshot.value = authCandidate.profile;
    authError.value = null;
    if (authCandidate.profile.activeRole === 'HEADMAN') {
      const candidate = await fetchHeadmanCandidate(
        authCandidate.generation,
        authCandidate.profile,
      );
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль старосты недоступна');
      await activateHeadmanCandidate(candidate);
      authView.value = 'headman';
      sessionReady.value = true;
    } else if (authCandidate.profile.activeRole !== 'STUDENT') {
      if (authCandidate.profile.activeRole === 'TEACHER') {
        const candidate = await fetchTeacherCandidate(
          authCandidate.generation,
          authCandidate.profile,
        );
        if (!candidate)
          throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна');
        await activateTeacherCandidate(candidate);
        authView.value = 'teacher';
      } else if (authCandidate.profile.activeRole === 'ADMIN') {
        await activateMapRole(authCandidate.profile, authCandidate.generation);
      } else {
        const previous = invalidateOwnerSynchronously({ clearAuth: false });
        await clearOwnerSnapshot(previous);
        authView.value = 'role';
        offline.value = false;
        readOnly.value = true;
        bootstrapError.value = null;
      }
    } else {
      const candidate = await fetchStudentCandidate(
        authCandidate.generation,
        authCandidate.profile,
      );
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль студента недоступна');
      await activateCandidate(candidate);
      authView.value = 'student';
    }
    sessionReady.value = true;
  } catch (error) {
    uncertainAuthRequest = isOfflineBootstrapRecoveryError(error);
    if (error instanceof StaleSessionGenerationError) {
      return;
    }
    if (isConfirmedOnlineAuthDenial(error)) {
      await handleOnlineAuthDenial(error, { initialAnonymousSessionRequest: true });
    } else if (
      error instanceof AuthRequestError &&
      authSnapshot.value &&
      authView.value === 'role'
    ) {
      authError.value = error;
      bootstrapError.value = error.message;
    } else if (error instanceof ProfileRequestError && error.code === 'SESSION_STATE_STALE') {
      const previous = invalidateOwnerSynchronously();
      await clearOwnerSnapshot(previous);
      authSnapshot.value = null;
      authError.value = error;
      authView.value = 'login';
      offline.value = false;
      readOnly.value = true;
      bootstrapError.value = error.message;
    } else if (
      !featureVisible.value &&
      authView.value !== 'headman' &&
      isOfflineBootstrapRecoveryError(error)
    ) {
      const recovered = await loadOfflineSnapshot();
      if (!recovered) {
        authView.value = 'login';
        bootstrapError.value = error instanceof Error ? error.message : 'Не удалось открыть Today';
        readOnly.value = true;
      }
    } else if (error instanceof ProfileRequestError && !featureVisible.value) {
      authError.value = error;
      bootstrapError.value = error.message;
    } else if (api.value !== null || snapshot.value !== null) {
      // A transient reconnect failure cannot establish remote logout. Keep the
      // currently committed owner mounted so an ambiguous action retains its
      // command and idempotency key for the next successful recheck.
      offline.value = true;
      readOnly.value = true;
      bootstrapError.value =
        error instanceof Error ? error.message : 'Не удалось проверить подключение';
    } else {
      authView.value = 'login';
      bootstrapError.value = error instanceof Error ? error.message : 'Не удалось получить данные';
      readOnly.value = true;
    }
    sessionReady.value = true;
  } finally {
    bootstrapping.value = false;
    currentOnlyGeneration = null;
    const onlineGeneration = pendingOnlineGeneration;
    pendingOnlineGeneration = null;
    if (
      !uncertainAuthRequest &&
      onlineGeneration !== null &&
      onlineGeneration === auth.currentGeneration() &&
      auth.canAutoBootstrap() &&
      !isPasswordRecoveryView() &&
      (typeof navigator === 'undefined' || navigator.onLine !== false)
    ) {
      queuedBootstrapAfterInvalidation = false;
      offline.value = true;
      readOnly.value = true;
      void bootstrap(options.currentOnly ? { currentOnly: true } : {});
    } else if (queuedBootstrapAfterInvalidation) {
      queuedBootstrapAfterInvalidation = false;
      void bootstrap({ refresh: false, ...(options.currentOnly ? { currentOnly: true } : {}) });
    }
  }
}

async function retryConnection(): Promise<void> {
  if (!offline.value || (typeof navigator !== 'undefined' && navigator.onLine === false)) return;
  // The previous refresh may have rotated its cookie before the connection
  // failed. Re-admit with the current bearer; all 401 fallbacks stay blocked
  // throughout this admission, including a queued authority reconciliation.
  await bootstrap({ currentOnly: true });
}

function retryBootstrap(): void {
  void bootstrap();
}

function asProfileError(error: unknown): ProfileRequestError {
  if (error instanceof ProfileRequestError) return error;
  if (error instanceof StudentApiError) {
    return new ProfileRequestError(
      'UNKNOWN',
      error.problem?.detail ?? error.message,
      error.response.status,
      error.problem,
    );
  }
  return new ProfileRequestError(
    'UNKNOWN',
    error instanceof Error ? error.message : 'Не удалось выполнить запрос',
  );
}

async function submitLogin(
  input: { login: string; password: string } | { code: string },
): Promise<void> {
  if (authLoading.value) return;
  authLoading.value = true;
  authError.value = null;
  bootstrapError.value = null;
  try {
    const result = 'code' in input ? await auth.loginOtp(input) : await auth.login(input);
    logoutNotice.value = null;
    const profile = await auth.getSessionFor(result.generation);
    authSnapshot.value = profile;
    if (
      profile.activeRole !== 'STUDENT' &&
      profile.activeRole !== 'HEADMAN' &&
      profile.activeRole !== 'TEACHER' &&
      profile.activeRole !== 'ADMIN'
    ) {
      authView.value = 'role';
      readOnly.value = true;
      sessionReady.value = true;
      return;
    }
    if (profile.activeRole === 'HEADMAN') {
      const candidate = await fetchHeadmanCandidate(result.generation, profile);
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль старосты недоступна');
      await activateHeadmanCandidate(candidate);
      authView.value = 'headman';
    } else if (profile.activeRole === 'TEACHER') {
      const candidate = await fetchTeacherCandidate(result.generation, profile);
      if (!candidate)
        throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна');
      await activateTeacherCandidate(candidate);
      authView.value = 'teacher';
    } else if (profile.activeRole === 'ADMIN') {
      await activateMapRole(profile, result.generation);
    } else {
      const candidate = await fetchStudentCandidate(result.generation, profile);
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль студента недоступна');
      await activateCandidate(candidate);
      authView.value = 'student';
    }
    sessionReady.value = true;
  } catch (error) {
    if (error instanceof StaleSessionGenerationError) return;
    authError.value = asProfileError(error);
    bootstrapError.value = authError.value.message;
    authView.value = 'login';
  } finally {
    authLoading.value = false;
  }
}

async function selectRole(
  role: ProfileRole,
  expectedSessionVersion: string,
  options: { preserveProfileOwner?: boolean } = {},
): Promise<void> {
  const request = createPwaRoleSelection(role, expectedSessionVersion);
  if (!request || authLoading.value || !authSnapshot.value) return;
  authLoading.value = true;
  pendingRole.value = role;
  authError.value = null;
  bootstrapError.value = null;
  // Keep the old token available for the role PUT, while hiding the old
  // feature owner until the response has passed the generation boundary.
  if (!options.preserveProfileOwner) authView.value = 'role';
  const generation = auth.currentGeneration();
  try {
    const selection = await auth.selectRoleFor(generation, request);
    if (options.preserveProfileOwner) {
      invalidateOwnerSynchronously({ clearAuth: false });
      authView.value = 'role';
    }
    authSnapshot.value = selection.session;
    if (selection.session.activeRole === 'HEADMAN') {
      const candidate = await fetchHeadmanCandidate(selection.generation, selection.session);
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль старосты недоступна');
      await activateHeadmanCandidate(candidate);
      authView.value = 'headman';
    } else if (selection.session.activeRole === 'STUDENT') {
      const candidate = await fetchStudentCandidate(selection.generation, selection.session);
      if (!candidate) throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль студента недоступна');
      await activateCandidate(candidate);
      authView.value = 'student';
    } else if (selection.session.activeRole === 'TEACHER') {
      const candidate = await fetchTeacherCandidate(selection.generation, selection.session);
      if (!candidate)
        throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль преподавателя недоступна');
      await activateTeacherCandidate(candidate);
      authView.value = 'teacher';
    } else if (selection.session.activeRole === 'ADMIN') {
      await activateMapRole(selection.session, selection.generation);
    } else {
      throw new ProfileRequestError(
        'ROLE_NOT_GRANTED',
        'Выбранная роль недоступна в этом приложении',
      );
    }
    sessionReady.value = true;
  } catch (error) {
    if (error instanceof StaleSessionGenerationError) return;
    authError.value = asProfileError(error);
    bootstrapError.value = authError.value.message;
    // A version conflict belongs to the old generation. Read the new
    // authoritative snapshot before rendering another selection attempt.
    if (
      authError.value.code === 'SESSION_VERSION_CONFLICT' ||
      authError.value.code === 'SESSION_STATE_STALE' ||
      authError.value.status === 503
    ) {
      try {
        const current = await auth.getSessionFor(generation);
        authSnapshot.value = current;
      } catch (refreshError) {
        if (isConfirmedOnlineAuthDenial(refreshError)) await handleOnlineAuthDenial(refreshError);
      }
    }
    if (!options.preserveProfileOwner) authView.value = 'role';
    else throw error;
  } finally {
    pendingRole.value = null;
    authLoading.value = false;
  }
}

function selectProfileRole(role: ProfileRole, expectedSessionVersion: string): Promise<void> {
  return selectRole(role, expectedSessionVersion, { preserveProfileOwner: true });
}

async function logout(): Promise<void> {
  if (logoutPending.value) return;
  const previous = invalidateOwnerSynchronously({ clearAuth: false });
  logoutPending.value = true;
  logoutNotice.value = null;
  authSnapshot.value = null;
  authError.value = null;
  bootstrapError.value = null;
  authView.value = 'login';
  sessionReady.value = true;
  offline.value = false;
  readOnly.value = true;

  const logoutRequest = auth.logout(async () => {
    await clearOwnerSnapshot(previous);
  });
  const logoutGeneration = auth.currentGeneration();
  let logoutFailure: unknown = null;
  try {
    await logoutRequest;
  } catch (cause) {
    logoutFailure = cause;
  } finally {
    if (auth.isCurrent(logoutGeneration)) {
      authSnapshot.value = null;
      authError.value = null;
      bootstrapError.value = null;
      logoutNotice.value =
        auth.explicitLogoutState() === 'unconfirmed'
          ? `${
              logoutFailure instanceof PwaAuthError
                ? logoutFailure.message
                : 'Не удалось подтвердить отзыв текущей сессии'
            }. Локальный доступ закрыт; автоматический вход отключён. Войди вручную, чтобы продолжить.`
          : logoutFailure
            ? 'Сессия отозвана, но локальная очистка не завершилась. Автоматический вход отключён; повтори вход вручную.'
            : 'Ты вышел из аккаунта. Чтобы войти снова, введи данные.';
      authView.value = 'login';
      sessionReady.value = true;
      offline.value = false;
      readOnly.value = true;
    }
    logoutPending.value = false;
  }
}

function handleExternalInvalidation(reason: PwaAuthInvalidationReason = 'external'): void {
  if (reason === 'authority-changed') {
    // The auth layer has already advanced the generation and installed the
    // validated candidate token. Detach the old owner synchronously, then
    // reconcile that token/session without asking refresh to rotate again.
    const wasHeadman =
      authView.value === 'headman' ||
      headmanApi.value !== null ||
      authSnapshot.value?.activeRole === 'HEADMAN';
    const wasTeacher =
      authView.value === 'teacher' ||
      teacherApi.value !== null ||
      authSnapshot.value?.activeRole === 'TEACHER';
    invalidateOwnerSynchronously({ clearAuth: false });
    authSnapshot.value = null;
    authError.value = null;
    bootstrapError.value = null;
    authView.value = wasHeadman ? 'headman' : wasTeacher ? 'teacher' : 'student';
    sessionReady.value = false;
    offline.value = true;
    readOnly.value = true;
    if (bootstrapping.value) {
      queuedBootstrapAfterInvalidation = true;
      return;
    }
    void bootstrap({ refresh: false });
    return;
  }
  const previous = invalidateOwnerSynchronously({ clearAuth: false });
  authSnapshot.value = null;
  authError.value = null;
  bootstrapError.value = 'Сессия завершена в другой вкладке';
  replacePasswordResetRouteWithLogin();
  authView.value = 'login';
  sessionReady.value = true;
  offline.value = false;
  readOnly.value = true;
  void clearOwnerSnapshot(previous);
}

async function persistHomework(homework: StudentHomework): Promise<void> {
  const currentScope = scope.value;
  const currentSession = session.value;
  const currentSchedule = semesterSchedule.value;
  const currentSnapshot = snapshot.value;
  if (
    !currentScope ||
    !currentSession ||
    !currentSchedule ||
    !currentSnapshot ||
    offline.value ||
    readOnly.value
  )
    return;
  if (auth.currentGeneration() !== currentScope.resetGeneration) return;
  try {
    const nextSnapshot = makeSnapshot(
      currentScope,
      currentSchedule,
      currentSnapshot,
      homework,
      currentSnapshot.etag,
    );
    await snapshotStore.write(nextSnapshot);
    assertCandidateCurrent(currentScope.resetGeneration);
    if (
      offline.value ||
      readOnly.value ||
      scope.value !== currentScope ||
      session.value !== currentSession ||
      semesterSchedule.value !== currentSchedule ||
      snapshot.value !== currentSnapshot
    )
      return;
    snapshot.value = nextSnapshot;
  } catch {
    // The online feature remains authoritative in memory; keep the previous
    // committed snapshot when storage rejects this candidate.
  }
}

async function readPosition(): Promise<GeolocationPosition | GeolocationPositionError | null> {
  if (!navigator.geolocation) return null;
  return new Promise((resolve) =>
    navigator.geolocation.getCurrentPosition(resolve, resolve, {
      enableHighAccuracy: true,
      timeout: 10_000,
      maximumAge: 0,
    }),
  );
}

function acquireCheckinCommand(): Promise<StudentCheckinCommand> {
  if (fixtureMode) return Promise.resolve(fixtureCheckinCommand());
  return readPosition().then((result) =>
    result && 'coords' in result
      ? commandFromCoordinates(result)
      : unavailableCommand(unavailableReason(result)),
  );
}

/** Fixture-only browser control; production always uses the real GPS API. */
function fixtureCheckinCommand(): StudentCheckinCommand {
  const scenario = new URLSearchParams(window.location.search).get('fixtureGeo');
  return scenario === 'coordinates'
    ? { geo: { kind: 'COORDINATES', latitude: 55.75, longitude: 37.62 } }
    : unavailableCommand('POSITION_UNAVAILABLE');
}

function openMaterial(url: string): void {
  // HomeworkScreen already validates this boundary. Revalidate at the shell
  // edge so an adapter callback can never open a relative or non-HTTP target.
  try {
    const parsed = new URL(url);
    if ((parsed.protocol !== 'http:' && parsed.protocol !== 'https:') || !parsed.hostname) return;
    window.open(url, '_blank', 'noopener,noreferrer');
  } catch {
    // Unsupported server data remains inert.
  }
}

function onOwnerError(error: unknown): void {
  if (
    authSnapshot.value?.activeRole === 'ADMIN' &&
    authDenialStatus(error) === 403 &&
    !isAdminPasswordProofFailure((error as { problem?: unknown } | null)?.problem)
  ) {
    void refreshAdminAuthority();
    return;
  }
  if (isConfirmedOnlineAuthDenial(error)) {
    void handleOnlineAuthDenial(error);
    return;
  }
  if (isAssistantCapabilityForbiddenError(error)) {
    void assistantPermissionRefresher.refresh('forbidden').catch((cause: unknown) => {
      if (isConfirmedOnlineAuthDenial(cause)) void handleOnlineAuthDenial(cause);
    });
  }
  if (error instanceof StudentApiError)
    bootstrapError.value = error.problem?.detail ?? error.message;
  else if (error instanceof Error) bootstrapError.value = error.message;
}

function goOffline(): void {
  pendingOnlineGeneration = null;
  offline.value = true;
  readOnly.value = true;
}

function goOnline(): void {
  themeMirror?.reconcile();
  // Reconnect must verify the current session before restoring mutation
  // authority. Bootstrap mounts a fresh bound owner only after getSession and
  // schedule have passed the generation gate.
  offline.value = true;
  readOnly.value = true;
  if (bootstrapping.value) {
    pendingOnlineGeneration = auth.currentGeneration();
    return;
  }
  pendingOnlineGeneration = null;
  void bootstrap();
}

function onAssistantForeground(): void {
  if (typeof document === 'undefined' || document.visibilityState !== 'visible') return;
  void assistantPermissionRefresher.refresh('foreground').catch((cause: unknown) => {
    if (isConfirmedOnlineAuthDenial(cause)) void handleOnlineAuthDenial(cause);
  });
}

const onServiceWorkerControllerChange = (): void => {
  void refreshFixtureDiagnostics();
};

async function refreshFixtureDiagnostics(): Promise<void> {
  if (!fixtureDiagnosticsMode) return;
  const lines = [
    `SW build flag: ${fixtureServiceWorkerBuildEnabled}`,
    `Origin: ${window.location.origin}`,
  ];
  if (!('serviceWorker' in navigator)) {
    fixtureDiagnosticLines.value = [...lines, 'Service worker API: unavailable'];
    return;
  }
  try {
    const registration = await navigator.serviceWorker.getRegistration();
    if (!registration) {
      fixtureDiagnosticLines.value = [
        ...lines,
        'Registration: none',
        `Controller: ${navigator.serviceWorker.controller ? 'present' : 'none'}`,
      ];
      return;
    }
    const worker = registration.installing ?? registration.waiting ?? registration.active;
    lines.push(`Registration: ${worker?.state ?? 'none'} (${registration.scope})`);
    lines.push(`Controller: ${navigator.serviceWorker.controller?.state ?? 'none'}`);
    const response = await fetch(`${import.meta.env.BASE_URL}sw-assets.js`, { cache: 'no-store' });
    const source = await response.text();
    const match = source.match(/=\s*(\[[\s\S]*\]);?\s*$/);
    const serializedAssets = match?.[1];
    if (!response.ok || !serializedAssets) throw new Error('Generated precache list unavailable');
    const assets = JSON.parse(serializedAssets) as unknown;
    if (!Array.isArray(assets) || assets.some((asset) => typeof asset !== 'string'))
      throw new Error('Generated precache list is invalid');
    const cacheNames = await caches.keys();
    const cacheName = cacheNames.find((name) => name === 'rct-student-pwa-v3');
    if (!cacheName) {
      fixtureDiagnosticLines.value = [...lines, 'Precache: rct-student-pwa-v3 missing'];
      return;
    }
    const cache = await caches.open(cacheName);
    const matches = await Promise.all(assets.map((asset) => cache.match(asset)));
    const missing = assets.filter((asset, index) => !matches[index]);
    lines.push(`Precache assets: ${assets.length - missing.length}/${assets.length}`);
    if (missing.length > 0) lines.push(`Missing: ${missing.join(', ')}`);
    fixtureDiagnosticLines.value = lines;
  } catch (error) {
    fixtureDiagnosticLines.value = [
      ...lines,
      `Diagnostics error: ${error instanceof Error ? error.message : 'unknown'}`,
    ];
  }
}

onMounted(() => {
  moscowDayClock.start();
  stopAuthInvalidation = auth.subscribeInvalidation(handleExternalInvalidation);
  window.addEventListener('offline', goOffline);
  window.addEventListener('online', goOnline);
  document.addEventListener('visibilitychange', onAssistantForeground);
  if (fixtureDiagnosticsMode && 'serviceWorker' in navigator)
    navigator.serviceWorker.addEventListener('controllerchange', onServiceWorkerControllerChange);
  if (!directPasswordRecoveryEntry) void bootstrap();
  void refreshFixtureDiagnostics();
});

onBeforeUnmount(() => {
  stopAdminAuthority();
  adminAuthorityOwner.dispose();
  if ('serviceWorker' in navigator)
    navigator.serviceWorker.removeEventListener('message', onPushMessage);
  moscowDayClock.stop();
  stopNotificationUnread();
  notificationUnreadOwner.bind(null, null);
  disposeNotificationsRealtime();
  disposeNotificationsRealtime = (): void => undefined;
  stopAuthInvalidation();
  auth.dispose();
  invalidateOwnerSynchronously();
  window.removeEventListener('offline', goOffline);
  window.removeEventListener('online', goOnline);
  document.removeEventListener('visibilitychange', onAssistantForeground);
  if (fixtureDiagnosticsMode && 'serviceWorker' in navigator)
    navigator.serviceWorker.removeEventListener(
      'controllerchange',
      onServiceWorkerControllerChange,
    );
  stopThemeSnapshot?.();
  themeMirror?.dispose();
  themePreference.dispose();
  theme?.dispose();
});
</script>

<template>
  <PwaUpdateGate :enabled="!fixtureMode" />
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
  <InstallOffer
    v-if="studentViewVisible && authSnapshot?.activeRole === 'STUDENT' && session"
    :controller="installPrompt"
    :eligible="studentViewVisible && authSnapshot?.activeRole === 'STUDENT' && session !== null"
  />
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
  <section
    v-if="authView === 'login' && logoutPending"
    class="today-state"
    role="status"
    aria-live="polite"
    aria-busy="true"
  >
    <span
      class="today-state__spinner"
      aria-hidden="true"
    />
    Выполняется выход из аккаунта…
  </section>
  <template v-else-if="authView === 'login'">
    <p
      v-if="logoutNotice"
      class="today-state today-state--error"
      role="alert"
      aria-live="polite"
    >
      {{ logoutNotice }}
    </p>
    <LoginScreen
      :loading="authLoading || bootstrapping"
      :error="authError"
      :notice="passwordRecoveryNotice"
      @submit="submitLogin"
      @submit-otp="submitLogin"
      @open-recovery="openPasswordRecovery"
    />
  </template>
  <PasswordRecoveryScreen
    v-else-if="authView === 'password-recovery'"
    :initial-proof="directPasswordResetProof"
    :initial-proof-error="directPasswordResetLinkError"
    :fetcher="requestFetcher"
    @cancel="closePasswordRecovery"
    @completed="completePasswordRecovery"
    @proof-consumed="clearDirectPasswordResetProof"
  />
  <RoleSwitchScreen
    :theme="resolvedTheme"
    v-else-if="authView === 'role'"
    :snapshot="authSnapshot"
    :pending-role="pendingRole"
    :error="authError"
    :loading="authLoading || bootstrapping"
    :on-select-role="selectRole"
  />
  <HeadmanScheduleScreen
    v-else-if="headmanViewVisible"
    ref="headmanNotificationOwner"
    :key="`headman-${ownerRevision}`"
    :report-download="reportDownload"
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
    :profile="authSnapshot"
    :profile-port="profilePort"
    :on-logout="logout"
    :on-recover="openPasswordRecovery"
    :profile-role-select="selectProfileRole"
    :group-id="headmanGroupId"
    :offline="offline"
    :read-only="readOnly"
    :host="host"
    :map-client="mapClient"
    :theme-controller="theme"
    @error="onOwnerError"
  />
  <TeacherFeatureOwner
    v-else-if="teacherViewVisible"
    ref="teacherNotificationOwner"
    :key="`teacher-${ownerRevision}`"
    :report-download="reportDownload"
    :on-notifications="openNotifications"
    :unread-count="notificationUnread.count"
    :api="teacherApi"
    :semester-id="teacherSemesterId"
    :map-client="mapClient"
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
      :online="!offline && !readOnly"
      :client="adminAttendanceApi"
      :authority="adminAuthority"
      :report-download="reportDownload"
      :show-back="host.backOwner === 'product'"
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
      :show-back="host.backOwner === 'product'"
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
      :show-back="host.backOwner === 'product'"
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
    v-else-if="adminRoleSwitchViewVisible && authSnapshot"
    :authority="adminAuthority"
    active="home"
    :task="true"
    :host="host"
    @navigate="navigateAdmin"
    @back="closeAdminRoleSwitch"
  >
    <RoleSwitchScreen
      :theme="resolvedTheme"
      compact
      :snapshot="authSnapshot"
      :pending-role="pendingRole"
      :error="authError"
      :show-roles-on-error="true"
      :offline="offline"
      :loading="authLoading || bootstrapping"
      :on-back="closeAdminRoleSwitch"
      :on-select-role="selectProfileRole"
    />
  </AdminShell>
  <StudentFeatureOwner
    v-else-if="studentViewVisible"
    ref="studentNotificationOwner"
    :key="ownerKey"
    :report-download="reportDownload"
    :owner-key="ownerKey"
    :api="api"
    :scope="scope"
    :offline="offline"
    :read-only="readOnly"
    :today-fallback="cachedToday"
    :homework-fallback="cachedHomework"
    :semester-starts-on="session?.semester?.startsOn ?? ''"
    :semester-ends-on="session?.semester?.endsOn ?? ''"
    :semester-schedule="displayedSemesterSchedule"
    :updated-at="snapshot?.cachedAt ?? null"
    :host="host"
    :profile-port="profilePort"
    :profile-role-select="selectRole"
    :assistant-permissions="assistantPermissions"
    :assistant-permission-options="assistantPermissionsVerified ? assistantPermissionOptions : []"
    :assistant-journal-api="assistantJournalApi"
    :assistant-stats-api="assistantStatsApi"
    :assistant-requests-api="assistantRequestsApi"
    :assistant-homework-api="assistantHomeworkApi"
    :map-client="mapClient"
    :theme-controller="theme"
    :acquire-checkin-command="acquireCheckinCommand"
    :open-material="openMaterial"
    :notification-target-intent="notificationTargetIntent"
    :on-notifications="openNotifications"
    :unread-count="notificationUnread.count"
    :on-logout="logout"
    :on-recover="openPasswordRecovery"
    :on-retry-connection="retryConnection"
    @homework-loaded="persistHomework"
    @owner-error="onOwnerError"
    @clear-notification-target="clearNotificationTargetIntent"
  />
  <button
    v-if="authView === 'teacher' || authView === 'headman' || mapViewVisible"
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
  <StudentWarningBlock
    v-if="(studentViewVisible || teacherViewVisible || headmanViewVisible) && bootstrapError"
    severity="error"
    title="Не удалось выполнить действие"
    :message="bootstrapError"
  />
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

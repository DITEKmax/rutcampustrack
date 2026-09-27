<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import AccountHistoryScreen from '../../features/profile/AccountHistoryScreen.vue'
import AppearanceScreen from '../../features/profile/AppearanceScreen.vue'
import ProfileScreen from '../../features/profile/ProfileScreen.vue'
import { ProfileState } from '../../features/profile/profile-state'
import SecurityScreen from '../../features/profile/SecurityScreen.vue'
import SessionsScreen from '../../features/profile/SessionsScreen.vue'
import { DEFAULT_PASSWORD_POLICY } from '../../features/profile/profile-types'
import type { ProfilePort, ProfileRoute, ProfileTheme } from '../../features/profile/profile-types'
import { profileOwnerStaleMessage } from '../profile-owner-status'
import { createMobileNavigationStack, nestedRoute, rootRoute } from '../navigation'
import type { MobileNavigationStack, MobileRoute } from '../navigation'
import type { MobileThemeController, MobileThemeResolvedMode } from '../theme'
import { createProfileViewPublication } from './profile-view-publication'

const props = withDefaults(defineProps<{
  profilePort: ProfilePort | null
  themeController?: MobileThemeController | null
  offline?: boolean
  onLogout?: (() => void | Promise<void>) | undefined
}>(), {
  themeController: null,
  offline: false,
  onLogout: undefined,
})

type ProfileArea = 'sessions' | 'history'
type ProfileAccountRoute = Extract<ProfileRoute, 'appearance' | 'security' | 'sessions' | 'history'>

const navigation: MobileNavigationStack = createMobileNavigationStack(rootRoute('profile'))
const route = ref<MobileRoute>(navigation.current)
const profileState = shallowRef(props.profilePort ? new ProfileState(props.profilePort) : null)
const themeMode = ref<ProfileTheme>(props.themeController?.mode ?? 'system')
const resolvedTheme = ref<MobileThemeResolvedMode>(props.themeController?.resolvedMode ?? 'dark')
const logoutBusy = ref(false)
let disposed = false
let stopTheme = (): void => undefined

const profilePublication = createProfileViewPublication(
  () => profileState.value?.view ?? null,
  () => disposed,
)
const profileView = profilePublication.view
const publishProfileView = profilePublication.publish
const profileOwnerStatus = computed(() => profileOwnerStaleMessage(route.value, props.offline))

const stopNavigation = navigation.subscribe(() => {
  route.value = navigation.current
  ensureProfileRoute(route.value)
})

watch(
  () => props.profilePort,
  (profilePort) => {
    profileState.value = profilePort ? new ProfileState(profilePort) : null
    publishProfileView()
    navigation.goRoot('profile')
  },
)

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
  await runProfile((state) => state.loadSnapshot())
}

async function openProfileArea(area: ProfileArea): Promise<void> {
  const state = profileState.value
  if (!state || disposed) return
  if (!state.view.snapshot) {
    await runProfile((current) => current.loadSnapshot())
    if (disposed || !state.view.snapshot) return
  }
  if (area === 'sessions') await runProfile((current) => current.loadSessions())
  else await runProfile((current) => current.loadHistory())
}

function retryProfileSnapshot(): void {
  void loadProfileSnapshot()
}

function retryProfileArea(area: ProfileArea): void {
  void openProfileArea(area)
}

async function loadMoreProfile(area: ProfileArea, cursor: string): Promise<void> {
  if (area === 'sessions') await runProfile((state) => state.loadSessions({ cursor }))
  else await runProfile((state) => state.loadHistory({ cursor }))
}

function backRoute(): void {
  navigation.back()
}

function navigateProfile(routeName: ProfileRoute): void {
  const routes: Record<ProfileAccountRoute, `profile/${string}`> = {
    appearance: 'profile/appearance',
    security: 'profile/security',
    sessions: 'profile/sessions',
    history: 'profile/history',
  }
  if (routeName === 'profile') {
    navigation.goRoot('profile')
    return
  }
  if (!(routeName in routes)) return
  navigation.push(nestedRoute('profile', routes[routeName as ProfileAccountRoute], 'detail'))
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
  await runProfile((state) => state.logoutAll(), { rethrow: true })
}

async function logout(): Promise<void> {
  if (!props.onLogout || logoutBusy.value || disposed) return
  logoutBusy.value = true
  try {
    await props.onLogout()
  } finally {
    if (!disposed) logoutBusy.value = false
  }
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

onMounted(() => { void loadProfileSnapshot() })

onBeforeUnmount(() => {
  disposed = true
  stopNavigation()
  stopTheme()
})
</script>

<template>
  <div class="admin-profile-owner">
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
      :show-role-switch="false"
      :on-retry="retryProfileSnapshot"
      :on-navigate="navigateProfile"
      :on-logout="logout"
      :logout-busy="logoutBusy"
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
      :error="profileView.sessionsError ?? profileView.error"
      :next-cursor="profileView.sessionsNextCursor"
      :busy="profileView.mutationBusy === 'logout-all'"
      :offline="offline || profilePort === null"
      :theme="resolvedTheme"
      :on-back="backRoute"
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
      :on-back="backRoute"
      :on-retry="() => retryProfileArea('history')"
      :on-load-more="(cursor) => loadMoreProfile('history', cursor)"
    />
  </div>
</template>

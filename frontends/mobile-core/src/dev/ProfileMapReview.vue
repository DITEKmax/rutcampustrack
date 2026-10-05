<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { CampusMapClient } from '../api/map-client'
import MapScreen from '../features/map/MapScreen.vue'
import ProfileScreen from '../features/profile/ProfileScreen.vue'
import MoreScreen from '../features/profile/MoreScreen.vue'
import RoleSwitchScreen from '../features/profile/RoleSwitchScreen.vue'
import AppearanceScreen from '../features/profile/AppearanceScreen.vue'
import SecurityScreen from '../features/profile/SecurityScreen.vue'
import SessionsScreen from '../features/profile/SessionsScreen.vue'
import AccountHistoryScreen from '../features/profile/AccountHistoryScreen.vue'
import { ProfileState } from '../features/profile/profile-state'
import { DEFAULT_PASSWORD_POLICY, ProfileRequestError, type ProfilePort, type ProfileRole, type ProfileRoute, type ProfileTheme } from '../features/profile/profile-types'
import iconAsset from '../features/profile/assets/more-map.svg'
import MobileShell from '../shared/components/MobileShell.vue'
import type { MobileHostAdapter } from '../shared/host'
import { createStudentNavigationItems } from '../shared/mobile-navigation-items'
import { nestedRoute, rootRoute, type MobileRootRouteId } from '../shared/navigation'
import { profileMapScenarios, profileFixture, sessionsFixture, historyFixture, mapFixture, mapPlanFixture } from './profile-map-fixtures'
import './profile-map-review.pcss'

const query = new URLSearchParams(window.location.search)
const scenario = query.get('state') ?? 'profile'
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const controls = query.get('controls') === '1'
const catalog = query.get('catalog') === '1'
const owner = ref(0)
const offline = ref(scenario.endsWith('-offline'))
const active = ref<ProfileRoute>(scenario.startsWith('map') ? 'map' : scenario.startsWith('roles') ? 'role-switch' : scenario.startsWith('security') ? 'security' : scenario.startsWith('sessions') ? 'sessions' : scenario.startsWith('history') ? 'history' : scenario === 'appearance' ? 'appearance' : scenario === 'more' ? 'requests' : 'profile')
const isMore = ref(scenario === 'more')
const theme = ref<ProfileTheme>('dark')
const pendingRole = ref<ProfileRole | null>(null)
const status = ref('')
const mapRequests = ref(0)
const opens = ref(0)
const hostBackVisible = ref(false)
let hostBack: (() => void) | null = null
let disposed = false
let fail = scenario.endsWith('-error')
const timers = new Map<number, () => void>()
function delay(ms = 90): Promise<void> {
  return new Promise((resolve) => { const id = window.setTimeout(() => { timers.delete(id); resolve() }, ms); timers.set(id, resolve) })
}
const port: ProfilePort = {
  async getSnapshot() {
    if (scenario === 'profile-loading') await delay(60000)
    else await delay()
    if (scenario === 'profile-error' && fail) throw new ProfileRequestError('NETWORK', 'Не удалось связаться с сервером. Попробуй ещё раз.')
    const snapshot = profileFixture(owner.value, scenario === 'profile-long')
    if (scenario === 'roles-readonly') snapshot.roles = snapshot.roles.map((grant) => grant.role === 'HEADMAN' ? { ...grant, readOnly: true, selectable: true, status: 'EXPELLED' } : grant)
    return snapshot
  },
  async selectRole(input) {
    await delay(scenario === 'roles-pending' ? 1800 : 200)
    if (scenario === 'roles-error' && fail) throw new ProfileRequestError('ROLE_NOT_SELECTABLE', 'Эта роль больше недоступна. Обнови список ролей.')
    const snapshot = profileFixture(owner.value)
    snapshot.activeRole = input.role
    snapshot.sessionVersion = String(Number(input.expectedSessionVersion) + 1)
    return { accessToken: 'synthetic-review', expiresIn: 3600, session: snapshot }
  },
  async listSessions(input) {
    await delay()
    if (scenario === 'sessions-error' && fail) throw new ProfileRequestError('NETWORK', 'Список сеансов не загрузился. Повтори попытку.')
    return { items: scenario === 'sessions-empty' ? [] : input?.cursor ? [{ ...sessionsFixture[1]!, sessionId: 'cursor-session', clientLabel: null }] : sessionsFixture, nextCursor: input?.cursor || scenario === 'sessions-empty' ? null : 'sessions-cursor' }
  },
  async listHistory(input) {
    await delay()
    if (scenario === 'history-error' && fail) throw new ProfileRequestError('INVALID_CURSOR', 'Не удалось загрузить страницу истории. Обнови список.')
    return { items: scenario === 'history-empty' ? [] : input?.cursor ? [{ id: 'more-event', type: 'ROLE_CHANGED' as const, occurredAt: '2026-10-02T09:00:00Z' }] : historyFixture, nextCursor: input?.cursor || scenario === 'history-empty' ? null : 'history-cursor' }
  },
  async changePassword() {
    await delay(scenario === 'security-race' ? 2500 : 250)
    if (scenario === 'security-error' || scenario === 'security-race') throw new ProfileRequestError('CURRENT_PASSWORD_INVALID', 'Текущий пароль неверен.')
  },
  async logoutAll() { await delay(200) },
  onInvalidated(reason) { status.value = 'ProfileState: ' + reason + ' · аккаунт и ресурсы очищены' },
  recoverPassword() { status.value = 'Fixture: переход к восстановлению пароля запрошен' },
  setTheme(value) { theme.value = value },
  isOnline: () => !offline.value,
}
const state = reactive(new ProfileState(port))
async function run(action: () => Promise<unknown>): Promise<void> { try { await action() } catch { /* Actual ProfileState owns the failure. */ } }
async function initialize(): Promise<void> {
  await run(() => state.loadSnapshot())
  if (!state.view.snapshot || disposed) return
  if (active.value === 'sessions') await run(() => state.loadSessions())
  if (active.value === 'history') await run(() => state.loadHistory())
}
if (!catalog) void initialize()
async function navigate(route: ProfileRoute): Promise<void> {
  isMore.value = false
  active.value = route
  if (route === 'sessions') await run(() => state.loadSessions())
  if (route === 'history') await run(() => state.loadHistory())
}
function back(): void { if (active.value === 'map') { isMore.value = true; active.value = 'requests' } else active.value = 'profile' }
function navigateRoot(route: MobileRootRouteId): void { isMore.value = route === 'more'; active.value = 'profile' }
async function selectRole(role: ProfileRole): Promise<void> {
  pendingRole.value = role
  try { await state.selectRole(role) } finally { pendingRole.value = null }
}
async function retry(): Promise<void> { fail = false; await initialize() }
async function replaceOwner(): Promise<void> { owner.value += 1; state.invalidate(); await initialize() }
const navItems = createStudentNavigationItems({ homeworkEnabled: true, attendanceEnabled: true, moreEnabled: true, profileEnabled: true })
const route = computed(() => isMore.value ? rootRoute('more') : active.value === 'profile' ? rootRoute('profile') : active.value === 'map' ? nestedRoute('more', 'more/map', 'task') : nestedRoute('profile', `profile/${active.value}`, 'task'))
const host: MobileHostAdapter = { backOwner: shell === 'tma' ? 'host' : 'product', primaryActionOwner: 'product', subscribeBack(listener) { hostBack = listener; return () => { hostBack = null } }, setBackVisible(visible) { hostBackVisible.value = visible } }
function scenarioLink(id: string): string { return new URLSearchParams({ state: id, shell, controls: '1' }).toString() }
function invokeHostBack(): void { hostBack?.() }
function response(data: unknown, code = 200): Response { return new Response(JSON.stringify(data), { status: code, headers: { 'Content-Type': 'application/json' } }) }
async function iconPng(): Promise<Blob> {
  const img = new Image()
  img.src = iconAsset
  await img.decode()
  const canvas = document.createElement('canvas')
  canvas.width = 24; canvas.height = 24
  canvas.getContext('2d')?.drawImage(img, 0, 0, 24, 24)
  return new Promise((resolve, reject) => canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('Fixture PNG unavailable')), 'image/png'))
}
const fetcher: typeof fetch = async (input, init) => {
  mapRequests.value += 1
  await delay(scenario === 'map-loading' ? 60000 : 80)
  if (init?.signal?.aborted) throw new DOMException('Aborted', 'AbortError')
  const path = String(input)
  if (scenario === 'map-error' && fail) return response({ title: 'Карта недоступна' }, 503)
  if (path.endsWith('/opens')) { if (scenario === 'map-usage-error') return response({ title: 'Ошибка регистрации' }, 503); opens.value += 1; return new Response(null, { status: 204 }) }
  if (path.includes('/assets/png/')) return new Response(await iconPng(), { headers: { 'Content-Type': 'image/png' } })
  if (path.includes('/assets/svg/')) return fetch(iconAsset)
  if (path.endsWith('/manifest')) return response(mapFixture(scenario))
  const match = path.match(/buildings\/([^/]+)\/floors\/([^/]+)/u)
  const plan = mapPlanFixture(scenario, match?.[1] ?? 'building-5', match?.[2] ?? '3')
  return plan ? response({ plan }) : new Response(null, { status: 204 })
}
const mapClient = new CampusMapClient({ accessToken: () => 'synthetic-review', currentGeneration: () => owner.value, fetcher })
const previousFont = document.documentElement.style.fontSize
if (query.get('root20') === '1') document.documentElement.style.fontSize = '20px'
document.title = (profileMapScenarios.find((item) => item[0] === scenario)?.[1] ?? scenario) + ' · ' + shell.toUpperCase() + ' · API fixture'
onBeforeUnmount(() => { disposed = true; for (const [id, resolve] of timers) { window.clearTimeout(id); resolve() }; timers.clear(); state.invalidate(); document.documentElement.style.fontSize = previousFont })
</script>

<template>
  <main
    v-if="catalog"
    class="profile-map-review-catalog"
  >
    <h1>Карта и профиль · API fixture</h1><p>Реальные Vue / ProfileState / CampusMapClient. Данные, API и Telegram host синтетические. Для проверки ready-форматов используется существующая иконка, не схема кампуса.</p><a
      v-for="item in profileMapScenarios"
      :key="item[0]"
      :href="'?' + scenarioLink(item[0])"
    >{{ item[1] }}</a>
  </main>
  <MobileShell
    v-else
    :route="route"
    :nav-items="navItems"
    :host="host"
    :custom-back="true"
    @navigate="navigateRoot"
    @back="back"
  >
    <MoreScreen
      v-if="isMore"
      :map-enabled="true"
      :assistant-enabled="true"
      :on-navigate="navigate"
      :on-notifications="() => status = 'Fixture: уведомления'"
      :on-logout="() => { state.invalidate(); status = 'Fixture: текущий выход' }"
    />
    <MapScreen
      v-else-if="active === 'map'"
      :key="owner"
      :client="mapClient"
      :offline="offline"
      :on-back="back"
    />
    <ProfileScreen
      v-else-if="active === 'profile'"
      :snapshot="state.view.snapshot"
      :loading="state.view.snapshotStatus === 'loading'"
      :error="state.view.snapshotError"
      :on-retry="retry"
      :on-navigate="navigate"
    />
    <RoleSwitchScreen
      v-else-if="active === 'role-switch'"
      :snapshot="state.view.snapshot"
      :loading="state.view.snapshotStatus === 'loading'"
      :pending-role="pendingRole"
      :offline="offline"
      :error="state.view.error"
      :show-roles-on-error="true"
      :on-back="back"
      :on-select-role="selectRole"
    />
    <AppearanceScreen
      v-else-if="active === 'appearance'"
      :theme="theme"
      resolved-theme="dark"
      :on-back="back"
      :on-theme-change="value => { theme = value }"
    />
    <SecurityScreen
      v-else-if="active === 'security'"
      :owner-key="String(owner)"
      :policy="state.view.snapshot?.passwordPolicy ?? DEFAULT_PASSWORD_POLICY"
      :error="state.view.error"
      :busy="state.view.mutationBusy === 'password'"
      :offline="offline"
      :on-back="back"
      :on-recover="shell === 'pwa' ? port.recoverPassword : undefined"
      :on-change-password="input => state.changePassword(input)"
    />
    <SessionsScreen
      v-else-if="active === 'sessions'"
      :sessions="state.view.sessions"
      :loading="state.view.sessionsStatus === 'loading'"
      :error="state.view.sessionsError ?? state.view.error"
      :next-cursor="state.view.sessionsNextCursor"
      :busy="state.view.mutationBusy === 'logout-all'"
      :offline="offline"
      :on-back="back"
      :on-retry="retry"
      :on-load-more="cursor => run(() => state.loadSessions({ cursor }))"
      :on-logout-all="() => run(() => state.logoutAll())"
    />
    <AccountHistoryScreen
      v-else-if="active === 'history'"
      :events="state.view.history"
      :loading="state.view.historyStatus === 'loading'"
      :error="state.view.historyError"
      :next-cursor="state.view.historyNextCursor"
      :on-back="back"
      :on-retry="retry"
      :on-load-more="cursor => run(() => state.loadHistory({ cursor }))"
    />
    <aside
      v-if="controls"
      class="profile-map-review-controls"
      aria-label="Управление API fixture"
    >
      <p>API fixture · owner {{ owner }} · map requests {{ mapRequests }} · opens {{ opens }} · theme preference {{ theme }} · dark display</p>
      <p role="status">
        {{ status }}
      </p>
      <button
        type="button"
        @click="replaceOwner"
      >
        Сменить owner
      </button><button
        type="button"
        @click="state.invalidate()"
      >
        Отозвать аккаунт
      </button><button
        type="button"
        @click="offline = !offline"
      >
        {{ offline ? 'Онлайн' : 'Офлайн' }}
      </button><button
        type="button"
        @click="invokeHostBack"
      >
        Host Back (симуляция)
      </button>
    </aside>
  </MobileShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import AdminMapScreen from '../features/admin-map/AdminMapScreen.vue'
import AdminMapStatisticsScreen from '../features/admin-map/AdminMapStatisticsScreen.vue'
import AdminDashboardScreen from '../features/admin-dashboard/AdminDashboardScreen.vue'
import AdminMoreScreen from '../features/admin-dashboard/AdminMoreScreen.vue'
import RoleSwitchScreen from '../features/profile/RoleSwitchScreen.vue'
import { ProfileRequestError, type ProfileRole } from '../features/profile/profile-types'
import AdminProfileOwner from '../shared/components/AdminProfileOwner.vue'
import AdminShell from '../shared/components/AdminShell.vue'
import AdminNotice from '../shared/components/AdminNotice.vue'
import type { MobileHostAdapter } from '../shared/host'
import { createAdminAuthorityOwner } from '../shared/admin-authority'
import { firstAdminRoute, type AdminRoleRoute } from '../shared/admin-navigation'
import { createMobileTheme } from '../shared/theme'
import { adminShellStates, createAdminShellFixtures } from './admin-shell-fixtures'
import './admin-shell-review.pcss'

const query = new URLSearchParams(window.location.search)
const state = query.get('state') ?? 'overview-today'
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const fixtures = createAdminShellFixtures(state)
const profileStates = ['profile', 'profile-error', 'appearance', 'security', 'sessions', 'history']
const view = ref<'home' | 'more' | 'profile' | 'roles' | 'map' | 'map-statistics'>(state.startsWith('map-') ? 'map' : state.startsWith('roles') ? 'roles' : profileStates.includes(state) ? 'profile' : state.startsWith('more') ? 'more' : 'home')
const authorityOwner = createAdminAuthorityOwner()
const authority = shallowRef(fixtures.authority as typeof fixtures.authority | null)
const authorityStatus = ref('loading')
let authorityInitialized = false
const unsubscribeAuthority = authorityOwner.subscribe(value => {
  authority.value = value.profile
  authorityStatus.value = value.status
  if (value.status === 'ready') {
    authorityInitialized = true
    const route = firstAdminRoute(value.profile)
    if (state.startsWith('authority-')) view.value = route === 'home' ? 'home' : 'profile'
  } else if (state.startsWith('authority-')) view.value = 'profile'
})
authorityOwner.bind(fixtures.access, 'synthetic-admin', '1')
const visible = ref(true)
const profileOwner = ref<InstanceType<typeof AdminProfileOwner> | null>(null)
const pendingRole = ref<ProfileRole | null>(null)
const roleError = ref<ProfileRequestError | null>(null)
const hostBackVisible = ref(false)
const hostBack = ref<(() => void) | null>(null)
const active = computed(() => view.value === 'map' ? 'map' : view.value === 'map-statistics' ? 'map-statistics' : view.value === 'profile' ? 'profile' : view.value === 'more' ? 'more' : 'home')
const theme = createMobileTheme({ mode: 'dark', lockedMode: 'dark' })
const originalRootFont = document.documentElement.style.fontSize
if (query.get('rootFont') === '20') document.documentElement.style.fontSize = '20px'
const host: MobileHostAdapter = {
  backOwner: shell === 'tma' ? 'host' : 'product', primaryActionOwner: 'product',
  setBackVisible: value => { hostBackVisible.value = value },
  subscribeBack(listener) { hostBack.value = listener; return () => { hostBack.value = null } },
}
function navigate(route: AdminRoleRoute): void {
  visible.value = true
  if (route === 'home' || route === 'more' || route === 'profile' || route === 'map' || route === 'map-statistics') view.value = route
  else if (route === 'users' || route === 'groups' || route === 'semesters') window.location.href = `admin-${route === 'semesters' ? 'semester' : route}-review.html?state=list&shell=${shell}&capture=${query.get('capture') ?? '0'}`
}
function back(): void {
  if (view.value === 'map' || view.value === 'map-statistics') { view.value = 'more'; return }
  if (view.value === 'roles') { view.value = 'home'; return }
  profileOwner.value?.back()
}
async function selectRole(role: ProfileRole, expectedSessionVersion: string): Promise<void> {
  pendingRole.value = role
  roleError.value = null
  try { await fixtures.profile.selectRole({ role, expectedSessionVersion }) }
  catch (cause) { roleError.value = cause instanceof ProfileRequestError ? cause : new ProfileRequestError('NETWORK', 'Не удалось сменить роль.') }
  finally { pendingRole.value = null }
}
let observer: MutationObserver | null = null
let initialized = false
let mapTargetFilled = false
let disposed = false
let fontsReady = false
function tryReady(): void {
  if (disposed || !fontsReady) return
  const main = document.querySelector('main')
  if (!main) return
  if (!['ready','error'].includes(authorityStatus.value) && state !== 'authority-loading') return
  if (state === 'authority-revoked' && authority.value?.assigned) return
  if (state.startsWith('map-') && !mapTargetFilled) {
    const building = main.querySelector<HTMLInputElement>('[data-field="building-code"]')
    const floor = main.querySelector<HTMLInputElement>('[data-field="floor-code"]')
    if (!building || !floor) return
    mapTargetFilled = true
    for (const input of [building, floor]) { input.value = '1'; input.dispatchEvent(new Event('input', { bubbles: true })) }
    void nextTick(tryReady); return
  }
  if (state.startsWith('map-') && authority.value?.permissions.includes('DATA_DELETE') && main.querySelectorAll('.admin-map-format').length !== 2) return
  if (!initialized) {
    if (state === 'overview-weeks') {
      const button = Array.from(main.querySelectorAll<HTMLButtonElement>('button')).find(item => item.textContent?.trim() === 'По неделям')
      if (!button) return
      initialized = true; button.click(); void nextTick(tryReady); return
    }
    if (state === 'overview-point') {
      const chart = main.querySelector<HTMLElement>('[data-admin-chart-points]')
      if (!chart) return
      initialized = true; chart.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true })); void nextTick(tryReady); return
    }
    if (state === 'overview-metric-options' || state === 'overview-coverage-empty' || state === 'overview-coverage-partial' || state === 'overview-geo-zero') {
      const trigger = main.querySelector<HTMLButtonElement>('[data-admin-metric-trigger]')
      if (!trigger) return
      initialized = true; trigger.click()
      void nextTick(async () => {
        await nextTick()
        if (state !== 'overview-metric-options') {
          const code = state === 'overview-geo-zero' ? 'geoMarks' : 'mapOpens'
          main.querySelector<HTMLButtonElement>(`[data-admin-metric-option="${code}"]`)?.click()
        }
        await nextTick(); tryReady()
      })
      return
    }
    if (state === 'overview-activity-retry') {
      const notice = Array.from(main.querySelectorAll<HTMLElement>('.admin-notice')).find(item => item.textContent?.includes('Активность не загрузилась'))
      const button = notice?.querySelector<HTMLButtonElement>('button')
      if (!button || fixtures.activityCounters().requests !== 1) return
      initialized = true; button.click(); void nextTick(tryReady); return
    }
    if (state === 'more-map-statistics') {
      const button = Array.from(main.querySelectorAll<HTMLButtonElement>('button')).find(item => item.textContent?.trim() === 'Статистика карты')
      if (!button) return
      initialized = true; button.click(); void nextTick(tryReady); return
    }
    if (state === 'overview-all-time') {
      const button = Array.from(main.querySelectorAll<HTMLButtonElement>('button')).find(item => item.textContent?.trim() === 'Всё время')
      if (!button) return
      initialized = true; button.click(); void nextTick(tryReady); return
    }
    if (['appearance','security','sessions','history'].includes(state)) {
      const label: Record<string,string> = { appearance: 'Оформление', security: 'Безопасность', sessions: 'Активные сеансы', history: 'История аккаунта' }
      const button = Array.from(main.querySelectorAll<HTMLButtonElement>('button')).find(item => item.textContent?.trim() === label[state])
      if (!button) return
      initialized = true; button.click(); void nextTick(tryReady); return
    }
    if (state === 'map-delete' || state === 'map-proof-revoked') {
      const button = Array.from(main.querySelectorAll<HTMLButtonElement>('button')).find(item => item.getAttribute('aria-label') === 'Удалить PNG' && !item.disabled)
      if (!button) return
      initialized = true; button.click(); void nextTick(tryReady); return
    }
    initialized = true
    if (state === 'roles-pending'  || state === 'roles-error') { void selectRole('STUDENT', fixtures.snapshot.sessionVersion); return }
  }
  if (state === 'map-delete' && !main.querySelector('[role="dialog"]')) return
  if (state === 'map-proof-revoked') {
    if (authority.value?.permissions.includes('DATA_DELETE')) {
      const password = main.querySelector<HTMLInputElement>('input[type="password"]')
      if (!password) return
      password.value = 'synthetic-review-only'; password.dispatchEvent(new Event('input', { bubbles: true }))
      authority.value = { ...authority.value, mode: 'LIMITED', permissions: ['CAMPUS_MAP_MANAGE'], version: authority.value.version + 1 }
      void nextTick(tryReady); return
    }
    if (main.querySelector('[role="dialog"]')) return
  }
  if (state.startsWith('map-') && main.querySelector('.admin-map-state')?.textContent?.includes('Загружаем')) return
  if (state.startsWith('overview') && !['overview-loading', 'overview-activity-loading'].includes(state) && main.getAttribute('aria-busy') === 'true') return
  if (['appearance','security','sessions','history'].includes(state) && main.classList.contains('profile-page')) return
  if (main.querySelector('.profile-state')?.textContent?.includes('Загружаем')) return
  if (state === 'overview-metric-options' && (!main.querySelector('dialog[open]') || main.querySelectorAll('[data-admin-metric-option]').length !== 5)) return
  if (state === 'overview-weeks' && main.querySelector('[data-admin-chart-points]')?.getAttribute('data-admin-chart-points') !== '12') return
  if (state === 'overview-point' && !main.querySelector('[data-admin-chart-readout]')) return
  if (state === 'overview-activity-error' && !main.textContent?.includes('Активность не загрузилась')) return
  if (state === 'overview-activity-loading' && !main.textContent?.includes('Загружаем активность')) return
  if (state === 'overview-activity-retry' && (fixtures.activityCounters().requests !== 2 || fixtures.activityCounters().successes !== 1 || !main.querySelector('[data-admin-chart-points]'))) return
  if (state === 'overview-coverage-empty' && (!main.querySelector('[data-admin-activity-metric="mapOpens"][data-state="INSUFFICIENT_HISTORY"]') || main.querySelectorAll('.admin-activity-chart__point').length !== 0)) return
  if (state === 'overview-coverage-partial' && (!main.textContent?.includes('За часть периодов данных нет') || !main.querySelector('.admin-activity-chart__point'))) return
  if (state === 'overview-geo-zero' && (main.querySelector('[data-admin-activity-metric="geoMarks"] dd span')?.textContent?.trim() !== '—' || main.querySelector('[data-admin-activity-metric="positiveMarks"] dd span')?.textContent?.trim() !== '0' || main.querySelector('.admin-activity-chart__point'))) return
  if (state === 'more-map-statistics' && (view.value !== 'map-statistics' || !main.textContent?.includes('Статистика карты') || main.textContent?.includes('Загружаем'))) return
  if (state === 'overview-all-time' && !Array.from(main.querySelectorAll('button')).some(button => button.textContent?.trim() === 'Всё время' && button.getAttribute('aria-pressed') === 'true')) return
  if (state.startsWith('authority-') && state !== 'authority-limited' && !document.querySelector('.admin-shell__access-state .admin-notice[data-tone="warning"] h2')) return
  if (state === 'authority-error' && !document.querySelector('.admin-shell__access-state .admin-notice__actions button')) return
  if (state.startsWith('map-') && (main.textContent?.includes('Загружаем файл') || [...main.querySelectorAll<HTMLImageElement>('.admin-map-preview')].some(image => !image.complete))) return
  document.documentElement.dataset.adminShellReady = 'true'
  document.documentElement.dataset.adminShellState = state
  document.documentElement.dataset.adminShellHeading = main.querySelector('h1')?.textContent?.trim() ?? ''
}
onMounted(() => {
  document.title = `Администратор · ${state} · ${shell.toUpperCase()} · синтетический API`
  observer = new MutationObserver(tryReady)
  observer.observe(document.getElementById('app')!, { childList: true, subtree: true, attributes: true }); document.getElementById('app')!.addEventListener('load', tryReady, true); document.getElementById('app')!.addEventListener('error', tryReady, true)
  void Promise.all([document.fonts.load('400 16px "Onest Variable"', 'Сменить роль ABC123'), document.fonts.load('600 16px "Onest Variable"', 'Сменить роль ABC123')]).then(() => {
    fontsReady = document.fonts.check('400 16px "Onest Variable"', 'Сменить роль ABC123') && document.fonts.check('600 16px "Onest Variable"', 'Сменить роль ABC123')
    if (!disposed) { document.documentElement.dataset.adminShellFontsReady = String(fontsReady); tryReady() }
  })
  void authorityOwner.refresh().then(async () => {
    if (state === 'authority-revoked' && authorityInitialized) await authorityOwner.refresh()
    await nextTick(); tryReady()
  })
  void nextTick(tryReady)
})
onBeforeUnmount(() => { disposed = true; observer?.disconnect(); document.getElementById('app')?.removeEventListener('load', tryReady, true); document.getElementById('app')?.removeEventListener('error', tryReady, true); unsubscribeAuthority(); authorityOwner.dispose(); theme.dispose(); document.documentElement.style.fontSize = originalRootFont; delete document.documentElement.dataset.adminShellFontsReady; delete document.documentElement.dataset.adminShellReady })
</script>

<template>
  <aside
    v-if="query.get('capture') !== '1'"
    class="admin-shell-review-catalog"
  >
    <p>Actual Vue · синтетический API · {{ shell.toUpperCase() }}</p>
    <a
      v-for="id in adminShellStates"
      :key="id"
      :href="`?state=${id}&shell=${shell}`"
    >{{ id }}</a>
    <button
      v-if="hostBackVisible"
      type="button"
      @click="hostBack?.()"
    >
      Назад хоста
    </button>
  </aside>
  <AdminShell
    :authority="authority"
    :active="active"
    :task="view === 'roles' || !visible"
    :host="host"
    @navigate="navigate"
    @back="back"
  >
    <div
      v-if="view === 'profile' && (authorityStatus !== 'ready' || !authority?.assigned || firstAdminRoute(authority) === 'profile')"
      class="admin-shell__access-state"
    >
      <AdminNotice
        tone="warning"
        :title="authorityStatus === 'loading' ? 'Проверяем права доступа' : authorityStatus === 'error' ? 'Не удалось подтвердить права' : 'Административные разделы недоступны'"
        :aria-busy="authorityStatus === 'loading'"
      >
        <p v-if="authorityStatus === 'loading'">
          Проверяем доступ к администрированию…
        </p>
        <p v-else-if="authorityStatus === 'error'">
          Доступен только твой профиль. Попробуй проверить права ещё раз.
        </p>
        <p v-else>
          Ты можешь пользоваться своим профилем.
        </p>
        <template
          v-if="authorityStatus === 'error'"
          #actions
        >
          <button
            type="button"
            @click="authorityOwner.refresh()"
          >
            Попробовать ещё раз
          </button>
        </template>
      </AdminNotice>
    </div>
    <AdminDashboardScreen
      v-if="view === 'home'"
      :client="fixtures.dashboard"
      @open-role-switch="view = 'roles'"
    />
    <AdminMapScreen
      v-else-if="view === 'map'"
      :client="fixtures.map"
      :authority="authority"
      :show-back="host.backOwner === 'product'"
      @back="back"
    />
    <AdminMapStatisticsScreen
      v-else-if="view === 'map-statistics'"
      :client="fixtures.map"
      :authority="authority"
      :show-back="host.backOwner === 'product'"
      @back="back"
    />
    <AdminMoreScreen
      v-else-if="view === 'more'"
      :authority="authority"
      @navigate="navigate"
    />
    <AdminProfileOwner
      v-else-if="view === 'profile'"
      ref="profileOwner"
      :profile-port="fixtures.profile"
      :theme-controller="theme"
      @navigation-visibility="visible = $event"
      @open-role-switch="view = 'roles'"
    />
    <RoleSwitchScreen
      v-else
      compact
      :snapshot="fixtures.snapshot"
      :pending-role="pendingRole"
      :error="roleError"
      show-roles-on-error
      :on-back="back"
      :on-select-role="selectRole"
    />
  </AdminShell>
</template>

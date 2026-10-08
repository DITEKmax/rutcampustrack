<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, shallowRef } from 'vue'
import AdminMapScreen from '../features/admin-map/AdminMapScreen.vue'
import AdminMapStatisticsScreen from '../features/admin-map/AdminMapStatisticsScreen.vue'
import AdminShell from '../shared/components/AdminShell.vue'
import type { MobileHostAdapter } from '../shared/host'
import { createMobileTheme } from '../shared/theme'
import { adminMapStates, createAdminMapFixtures } from './admin-map-fixtures'
import './admin-map-review.pcss'
const query = new URLSearchParams(window.location.search)
const state = query.get('state') ?? 'full'
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const fixtures = createAdminMapFixtures(state)
const authority = shallowRef(fixtures.authority)
const theme = createMobileTheme({ mode: 'dark', lockedMode: 'dark' })
const host: MobileHostAdapter = { backOwner: shell === 'tma' ? 'host' : 'product', primaryActionOwner: 'product', setBackVisible() {}, subscribeBack() { return () => {} } }
const rootFont = document.documentElement.style.fontSize
if (query.get('rootFont') === '20') document.documentElement.style.fontSize = '20px'
let observer: MutationObserver | undefined
let initialized = false
let disposed = false
let fontsReady = false
function fill(field: string, value: string): void { const element = document.querySelector<HTMLInputElement>(`[data-field="${field}"]`); if (element) { element.value = value; element.dispatchEvent(new Event('input', { bubbles: true })) } }
function click(label: string): boolean { const button = Array.from(document.querySelectorAll<HTMLButtonElement>('main button')).find(item => item.textContent?.trim() === label && !item.disabled); if (!button) return false; button.click(); return true }
function file(): boolean { const input = document.querySelector<HTMLInputElement>('input[type="file"][accept*="png"]'); if (!input || input.disabled) return false; const transfer = new DataTransfer(); transfer.items.add(new File(['synthetic-map-only'], 'корпус-1-этаж-1.png', { type: 'image/png' })); input.files = transfer.files; input.dispatchEvent(new Event('change', { bubbles: true })); return true }
function ready(): void {
  if (disposed || !fontsReady) return
  const main = document.querySelector('main')
  if (!main || main.textContent?.includes('Загружаем')) return
  if (!initialized) {
    if (state.startsWith('combined-') || ['partial-failure', 'unknown', 'unknown-building'].includes(state)) {
      if (!main.querySelector('[data-field="building-code"]')) return
      initialized = true; fill('building-code', ['combined-existing', 'unknown'].includes(state) ? '1' : '9'); fill('floor-code', '2'); void nextTick(() => { click('Добавить этаж'); void nextTick(ready) }); return
    }
    if (state === 'floating-file' || state === 'target-switch') {
      if (!file()) return
      initialized = true
      if (state === 'target-switch') { const select = main.querySelector<HTMLSelectElement>('select'); if (select) { select.value = '30'; select.dispatchEvent(new Event('change', { bubbles: true })) } }
      void nextTick(ready); return
    }
    if (state === 'delete' || state === 'proof-revoked') { if (!click('Удалить этаж со всеми версиями…')) return; initialized = true; void nextTick(ready); return }
    if (state === 'statistics-filter') { const select = main.querySelector<HTMLSelectElement>('select'); if (!select) return; initialized = true; select.value = '30'; select.dispatchEvent(new Event('change', { bubbles: true })); void nextTick(ready); return }
    initialized = true
  }
  if (state === 'delete' && !main.querySelector('[role="dialog"]')) return
  if (state === 'proof-revoked' && authority.value.permissions.includes('DATA_DELETE')) {
    const password = main.querySelector<HTMLInputElement>('input[type="password"]'); if (!password) return
    password.value = 'synthetic-proof'; password.dispatchEvent(new Event('input', { bubbles: true }))
    authority.value = { ...authority.value, mode: 'LIMITED', permissions: ['CAMPUS_MAP_MANAGE'], version: 2 }; void nextTick(ready); return
  }
  if (state === 'proof-revoked' && main.querySelector('[role="dialog"]')) return
  if (state === 'combined-pending' && !main.textContent?.includes('Корпус 9 создан')) return
  if (['combined-new', 'combined-existing'].includes(state) && !main.textContent?.includes('Этаж 2 добавлен')) return
  if (state === 'partial-failure' && !main.textContent?.includes('не добавлен')) return
  if (state.startsWith('unknown') && !main.textContent?.includes('Исход создания')) return
  document.documentElement.dataset.adminMapReady = 'true'
  document.documentElement.dataset.adminMapState = state
  document.documentElement.dataset.adminMapHeading = main.querySelector('h1')?.textContent?.trim() ?? ''
}
onMounted(() => {
  document.title = `Карта · ${state} · ${shell.toUpperCase()} · синтетический API`
  observer = new MutationObserver(ready); observer.observe(document.getElementById('app')!, { childList: true, subtree: true, attributes: true })
  void Promise.all([document.fonts.load('400 16px "Onest Variable"', 'Корпус 123'), document.fonts.load('600 16px "Onest Variable"', 'Этаж 123')]).then(() => { fontsReady = document.fonts.check('400 16px "Onest Variable"', 'Корпус 123') && document.fonts.check('600 16px "Onest Variable"', 'Этаж 123'); if (!disposed) { document.documentElement.dataset.adminMapFontsReady = String(fontsReady); ready() } })
})
onUnmounted(() => { disposed = true; observer?.disconnect(); theme.dispose(); document.documentElement.style.fontSize = rootFont; delete document.documentElement.dataset.adminMapReady; delete document.documentElement.dataset.adminMapFontsReady })
</script>

<template>
  <aside
    v-if="query.get('capture') !== '1'"
    class="admin-map-review-catalog"
  >
    <p>Actual Vue · синтетический API · {{ shell.toUpperCase() }}</p><a
      v-for="id in adminMapStates"
      :key="id"
      :href="`?state=${id}&shell=${shell}`"
    >{{ id }}</a>
  </aside>
  <AdminShell
    :authority="authority"
    active="more"
    task
    :host="host"
  >
    <AdminMapStatisticsScreen
      v-if="state.startsWith('statistics')"
      :client="fixtures.client"
      :authority="authority"
      :show-back="shell === 'pwa'"
    />
    <AdminMapScreen
      v-else
      :client="fixtures.client"
      :authority="authority"
      :show-back="shell === 'pwa'"
    />
  </AdminShell>
</template>

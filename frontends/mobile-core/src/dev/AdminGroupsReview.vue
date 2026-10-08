<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import AdminGroupsScreen from '../features/admin-groups/AdminGroupsScreen.vue'
import AdminRoleNavigation from '../features/admin-semester/AdminRoleNavigation.vue'
import { adminGroupsAuthority, adminGroupsStates, createAdminGroupsFixture } from './admin-groups-fixtures'
import './admin-academic-review.pcss'
const query = new URLSearchParams(location.search), state = query.get('state') ?? 'active', tma = query.get('shell') === 'tma'
const client = createAdminGroupsFixture(state), navigationVisible = ref(true), screen = ref<InstanceType<typeof AdminGroupsScreen> | null>(null)
const authority = ref(adminGroupsAuthority(state))
const originalFont = document.documentElement.style.fontSize
if (query.get('rootFont') === '20') document.documentElement.style.fontSize = '20px'
let disposed = false
async function waitFor(condition: () => boolean): Promise<void> { for (let frame = 0; frame < 600 && !disposed; frame++) { if (condition()) { await nextTick(); return }; await new Promise<void>(resolve => requestAnimationFrame(() => resolve())) }; throw new Error('Group fixture did not settle: ' + state) }
function click(selector: string): void { const button = document.querySelector<HTMLElement>(selector); if (!button) throw new Error('Missing group action: ' + selector); button.click() }
function input(selector: string, value: string): void { const field = document.querySelector<HTMLInputElement | HTMLSelectElement>(selector); if (!field) throw new Error('Missing group field: ' + selector); field.value = value; field.dispatchEvent(new Event(field instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true })) }
function chooseState(event: Event): void { const url = new URL(location.href); url.searchParams.set('state', (event.target as HTMLSelectElement).value); location.href = url.toString() }
onMounted(async () => {
  try {
    if (state === 'missing-authority') { await waitFor(() => document.body.textContent?.includes('текущими правами') === true); if (document.documentElement.dataset.adminFixtureRequests !== '0') throw new Error('Missing authority sent a request') }
    else if (['error', 'forbidden'].includes(state)) await waitFor(() => !!document.querySelector('[role="alert"]'))
    else if (state === 'empty') await waitFor(() => !!document.querySelector('.admin-groups-empty'))
    else {
      await waitFor(() => document.querySelectorAll('.admin-groups-item').length === 3)
      if (state === 'revoked') { input('#admin-groups-search-input', 'УИТ'); document.querySelector('#admin-groups-search-input')!.dispatchEvent(new Event('change', { bubbles: true })); authority.value = null; await waitFor(() => document.documentElement.dataset.adminFixtureReadSettled === '2'); if (document.querySelector('.admin-groups-item')) throw new Error('Late groups survived revocation') }
      if (['draft', 'archive', 'restored'].includes(state)) { click(`.admin-groups-tabs__item:nth-child(${state === 'draft' ? 2 : 3})`); await waitFor(() => document.querySelector('.admin-groups-tabs__item[aria-selected="true"]')?.textContent?.includes(state === 'draft' ? 'Черновики' : 'Архив') === true && document.querySelector('.admin-groups-item')?.getAttribute('data-group-id') === (state === 'draft' ? '25' : '32')) }
      if (state === 'filters') { click('.admin-groups-filterbar button'); await waitFor(() => !!document.querySelector('[data-admin-groups-view="filters"]')) }
      if (state.startsWith('filter-') || ['filtered-course', 'filtered-program', 'sorted'].includes(state)) { click('.admin-groups-filterbar button'); await waitFor(() => !!document.querySelector('[data-admin-groups-view="filters"]')); const kind = state.includes('course') ? 'course' : state.includes('program') ? 'program' : 'sort'; click('[aria-labelledby="admin-group-' + kind + '-label admin-group-' + kind + '-value"]'); await waitFor(() => !!document.querySelector('[data-filter-options="' + kind + '"]')); if (!state.endsWith('options')) { const selector = '[data-filter-options="' + kind + '"] button'; click(selector + ':nth-child(' + (state === 'sorted' ? 7 : 4) + ')'); await nextTick(); click('.admin-groups-form button[type="submit"]'); await waitFor(() => document.documentElement.dataset.adminFixtureRegistryQuery?.includes(state === 'sorted' ? 'sort=programType%2Cdesc' : state === 'filtered-course' ? 'course=3' : 'educationLevel=BACHELOR') === true && document.querySelector('.admin-groups-item') !== null) } }
      if (['create', 'created', 'create-twice'].includes(state)) { click('.admin-groups-management button'); await waitFor(() => !!document.querySelector('.admin-groups-form')); input('.admin-groups-form label:nth-child(1) input', 'УИТ'); input('.admin-groups-form label:nth-child(2) input', '311'); input('.admin-groups-form label:nth-child(3) input', '5'); if (state === 'created' || state === 'create-twice') { click('.admin-groups-form button[type="submit"]'); await waitFor(() => !!document.querySelector('.admin-groups-state--success') && document.querySelector('.admin-groups-item')?.getAttribute('data-group-id') === '90') } }
      if (state === 'create-twice') { click('.admin-groups-management button'); await waitFor(() => !!document.querySelector('.admin-groups-form')); input('.admin-groups-form label:nth-child(1) input', 'УИТ'); input('.admin-groups-form label:nth-child(2) input', '312'); input('.admin-groups-form label:nth-child(3) input', '5'); if (document.querySelector<HTMLButtonElement>('.admin-groups-form button[type="submit"]')?.disabled) throw new Error('Second creation remains disabled'); click('.admin-groups-form button[type="submit"]'); await waitFor(() => document.querySelector('.admin-groups-item')?.getAttribute('data-group-id') === '91') }
      if (['expanded', 'actions-expanded', 'headman', 'headman-preview', 'headman-unchanged', 'headman-conflict', 'restored', 'archive-confirm', 'archived'].includes(state)) { click('.admin-groups-item__trigger'); await waitFor(() => !!document.querySelector('.admin-groups-item__details')); if (state !== 'expanded') { click('.admin-groups-item__actions-toggle'); await nextTick(); if (state === 'actions-expanded') { await waitFor(() => document.querySelectorAll('.admin-groups-item__action-options button').length === 3) } else if (state.startsWith('headman')) { click('.admin-groups-item__action-options button'); await waitFor(() => !!document.querySelector('.admin-groups-headman select')); if (state !== 'headman') { input('.admin-groups-headman select', state === 'headman-unchanged' ? '11' : '12'); await waitFor(() => document.querySelector('.admin-groups-headman__preview')?.textContent?.includes(state === 'headman-unchanged' ? 'Изменений не будет' : 'Помощников к отзыву') === true); if (state === 'headman-unchanged' && document.querySelector('.admin-groups-headman button[type="submit"]')) throw new Error('Unchanged headman offered a mutation'); if (state === 'headman-conflict') { click('.admin-groups-headman button[type="submit"]'); await waitFor(() => !!document.querySelector('[role="alert"]')) } } } else if (state === 'restored') { click('.admin-groups-item__action-options button'); await waitFor(() => !!document.querySelector('.admin-groups-state--success')) } else { click('.admin-groups-item__action-options button:last-child'); await waitFor(() => !!document.querySelector('[data-admin-groups-view="archive-confirm"]')); if (state === 'archived') { click('[data-admin-groups-view="archive-confirm"] .admin-groups-action'); await waitFor(() => !!document.querySelector('.admin-groups-state--success')) } } } }
      if (state.startsWith('promotion')) { click('.admin-groups-management button:last-child'); await waitFor(() => !!document.querySelector('.admin-groups-promotion__details')); if (['promotion-confirm', 'promotion-result', 'promotion-history', 'promotion-stale'].includes(state)) { click('.admin-groups-promotion__confirm'); await waitFor(() => !!document.querySelector('dialog[open]')); if (state !== 'promotion-confirm') { click('.admin-groups-promotion__dialog-confirm'); await waitFor(() => state === 'promotion-stale' ? !!document.querySelector('.admin-groups-promotion__error') : !!document.querySelector('.admin-groups-promotion__result')) } } }
    }
    await nextTick(); document.documentElement.dataset.adminFixtureReady = 'true'
  } catch (cause) { document.documentElement.dataset.adminFixtureError = cause instanceof Error ? cause.message : String(cause) }
})
onBeforeUnmount(() => { disposed = true; document.documentElement.style.fontSize = originalFont; delete document.documentElement.dataset.adminFixtureReady })
</script>
<template>
  <div
    class="admin-academic-review"
    :data-back-owner="tma ? 'host' : 'product'"
  >
    <aside
      v-if="query.get('capture') !== '1'"
      class="admin-academic-review__toolbar"
    >
      <label>Состояние <select
        :value="state"
        @change="chooseState"
      ><option
        v-for="item in adminGroupsStates"
        :key="item"
      >{{ item }}</option></select></label><button
        v-if="tma && !navigationVisible"
        type="button"
        @click="screen?.back()"
      >
        Telegram BackButton
      </button>
    </aside>
    <AdminGroupsScreen
      ref="screen"
      :client="client"
      :authority="authority"
      @navigation-visibility="navigationVisible = $event"
    />
    <AdminRoleNavigation
      v-if="navigationVisible"
      active="groups"
      :authority="authority"
    />
  </div>
</template>

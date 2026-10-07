<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import HeadmanRequestsScreen, { type HeadmanRequestsView } from '../features/headman-requests/HeadmanRequestsScreen.vue'
import MobileShell from '../shared/components/MobileShell.vue'
import { createHeadmanNavigationItems } from '../shared/mobile-navigation-items'
import { createMobileNavigationStack, nestedRoute, rootRoute } from '../shared/navigation'
import type { MobileHostAdapter } from '../shared/host'
import { createRequestsReviewApi } from './headman-requests-review-fixtures'
import './headman-requests-review.pcss'
const query = new URLSearchParams(window.location.search), state = query.get('state') ?? 'queue', shell = query.get('shell') ?? 'pwa'
const logs = ref<string[]>([]), api = createRequestsReviewApi(state, (line) => { logs.value.push(line) }), screen = ref<InstanceType<typeof HeadmanRequestsScreen> | null>(null)
const navigation = createMobileNavigationStack(rootRoute('headman-requests')), route = ref(navigation.current), hostVisible = ref(false)
const stopRoute = navigation.subscribe(() => { route.value = navigation.current })
const stopBack = navigation.beforeBack(() => !screen.value?.back())
const host: MobileHostAdapter = { backOwner: shell === 'tma' ? 'host' : 'product', primaryActionOwner: 'product', setBackVisible: (value) => { hostVisible.value = value }, subscribeBack: (listener) => { window.addEventListener('requests-review-host-back', listener); return () => window.removeEventListener('requests-review-host-back', listener) } }
function hostBack(): void { window.dispatchEvent(new Event('requests-review-host-back')) }
function viewChange(view: HeadmanRequestsView): void { if (view === 'queue' || view === 'archive') navigation.goRoot('headman-requests'); else navigation.replace(nestedRoute('headman-requests', `headman-requests/${view}`, view === 'detail' ? 'detail' : 'editor')); document.documentElement.dataset.requestsReviewView = view }
const initialFont = document.documentElement.style.fontSize
if (query.get('font') === '20') document.documentElement.style.fontSize = '20px'
let disposed = false
async function waitFor(predicate: () => boolean): Promise<void> { for (let frame = 0; frame < 900 && !disposed; frame++) { if (predicate()) return; await new Promise<void>((resolve) => requestAnimationFrame(() => resolve())) } throw new Error(`Fixture did not settle: ${state}`) }
function click(selector: string): void { const element = document.querySelector<HTMLButtonElement>(selector); if (!element || element.disabled) throw new Error(`Unavailable fixture action ${selector}`); element.click() }
onMounted(async () => {
  try {
    await waitFor(() => document.querySelector('main[aria-busy="false"]') !== null)
    if (state === 'archive') { click('.headman-requests__tabs button:last-child'); await waitFor(() => document.querySelector('main[data-request-view="archive"][aria-busy="false"]') !== null) }
    if (['detail', 'approve', 'reject', 'late', 'readonly', 'unknown', 'transferred', 'exclude', 'excluded', 'partial-approve', 'stale-selection'].includes(state)) {
      click(`[data-request-id="${state === 'late' ? 'late-ilya' : 'excuse-maria'}"]`)
      await waitFor(() => document.querySelector('main[data-request-view="detail"][aria-busy="false"]') !== null)
      if (['exclude', 'excluded', 'partial-approve', 'stale-selection'].includes(state)) {
        click('.headman-requests__exclude'); await waitFor(() => document.querySelector('[data-request-view="exclude"]') !== null)
        if (state !== 'exclude') { click('.headman-requests__exclusion-confirm .headman-requests__danger'); await waitFor(() => document.querySelector('[data-request-view="detail"]') !== null) }
        if (state === 'partial-approve' || state === 'stale-selection') { click('.headman-requests__actions .headman-requests__primary'); await waitFor(() => document.querySelector('main[aria-busy="false"]') !== null) }
      }
      if (['approve', 'unknown', 'transferred'].includes(state)) { click('.headman-requests__primary'); await waitFor(() => document.querySelector('main[data-request-view="approve"][aria-busy="false"]') !== null) }
      if (state === 'reject') { click('.headman-requests__danger'); await waitFor(() => document.querySelector('main[data-request-view="reject"][aria-busy="false"]') !== null) }
      if (state === 'unknown') { click('.headman-requests__primary'); await waitFor(() => document.querySelector('main[aria-busy="false"] [data-severity="error"]') !== null) }
      if (state === 'late') { click('.headman-requests__primary'); await waitFor(() => document.querySelector('main[data-request-view="approve"][aria-busy="false"]') !== null) }
    }
    if (state === 'paging') { click('.headman-requests__pagination button:last-child'); await waitFor(() => document.querySelector('main[aria-busy="false"] .headman-requests__pagination')?.textContent?.includes('4 из 4') === true) }
    await document.fonts.ready
    await waitFor(() => !document.querySelector('.request-server-preview[aria-busy="true"]'))
    await nextTick()
    document.documentElement.dataset.requestsReviewReady = 'true'
    document.documentElement.dataset.requestsReviewState = state
    document.documentElement.dataset.requestsReviewRevision = query.get('revision') ?? 'working-tree'
    document.documentElement.dataset.requestsReviewRun = query.get('run') ?? 'unassigned'
  } catch (cause) { document.documentElement.dataset.requestsReviewError = cause instanceof Error ? cause.message : String(cause) }
})
onBeforeUnmount(() => { disposed = true; stopRoute(); stopBack(); document.documentElement.style.fontSize = initialFont })
</script>
<template>
  <MobileShell
    :route="route"
    :navigation="navigation"
    :nav-items="createHeadmanNavigationItems()"
    :host="host"
  >
    <template #back>
      <span hidden />
    </template>
    <HeadmanRequestsScreen
      ref="screen"
      :api="api"
      :show-back="false"
      :offline="state === 'offline'"
      :read-only="state === 'readonly'"
      @view-change="viewChange"
    />
  </MobileShell>
  <pre
    hidden
    data-test="requests-review-log"
  >{{ logs.join('\n') }}</pre>
  <button
    v-if="shell === 'tma' && hostVisible && query.get('controls') === '1'"
    type="button"
    @click="hostBack"
  >
    Host Back
  </button>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import HeadmanHomeScreen from '../features/headman-home/HeadmanHomeScreen.vue'
import HeadmanLessonActionsScreen from '../features/headman-journal/HeadmanLessonActionsScreen.vue'
import HeadmanJournalScreen from '../features/headman-journal/HeadmanJournalScreen.vue'
import MobileShell from '../shared/components/MobileShell.vue'
import { createHeadmanNavigationItems } from '../shared/mobile-navigation-items'
import { nestedRoute, rootRoute, type MobileRootRouteId } from '../shared/navigation'
import type { MobileHostAdapter } from '../shared/host'
import { createHeadmanReviewApis, headmanReviewStates } from './headman-fixtures'
import './headman-review.pcss'

const query = new URLSearchParams(window.location.search)
const state = query.get('state') ?? 'today-current'
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const apis = createHeadmanReviewApis(state)
const view = ref(state.startsWith('action-log') ? 'log' : state.startsWith('today') ? 'today' : 'journal')
const task = ref(false)
const lessonId = ref<number | null>(state === 'today-past' ? 20 : 30)
const journal = ref<InstanceType<typeof HeadmanJournalScreen> | null>(null)
const date = ref('2026-10-06')
const hostBackVisible = ref(false)
const offline = state === 'offline'
const route = computed(() => view.value === 'log' ? nestedRoute('headman-today','headman-today/actions','detail') : task.value ? nestedRoute('headman-attendance','headman-attendance/reason','editor') : rootRoute(view.value === 'today' ? 'headman-today' : 'headman-attendance'))
const navItems = createHeadmanNavigationItems()
const host: MobileHostAdapter = {
  backOwner: shell === 'tma' ? 'host' : 'product',
  primaryActionOwner: 'product',
  setBackVisible: (visible) => { hostBackVisible.value = visible },
  subscribeBack: (listener) => {
    window.addEventListener('headman-review-host-back', listener)
    return () => window.removeEventListener('headman-review-host-back', listener)
  },
}
function hostBack(): void { window.dispatchEvent(new Event('headman-review-host-back')) }
function navigate(id: MobileRootRouteId): void {
  if (id === 'headman-today') view.value = 'today'
  if (id === 'headman-attendance') view.value = 'journal'
}
const originalFont = document.documentElement.style.fontSize
if (query.get('font') === '20') document.documentElement.style.fontSize = '20px'
let observer: MutationObserver | null = null
let autoActionDone = false
function tryReady(): void {
  const main = document.querySelector('.headman-home, .headman-journal, .headman-actions')
  const heading = main?.querySelector('h1')?.textContent?.trim()
  if (!main || main.getAttribute('aria-busy') === 'true') return
  const baseReady = view.value === 'log' ? Boolean(document.querySelector('.headman-actions:not([aria-busy="true"])')) : offline ? Boolean(main.querySelector('[role="status"]'))
    : state === 'today-empty' ? Boolean(main.querySelector('.headman-home__state'))
    : view.value === 'today' ? Boolean(main.querySelector('.headman-home__lesson'))
    : Boolean(main.querySelector('[data-user-id="2"]') || (state === 'reason-form' || state === 'manual-new-empty') && main.querySelector('[data-user-id="4"] .headman-journal__excuse-form') || (state === 'student-ticket-link' || state.startsWith('ticket-preview')) && main.querySelector('.headman-journal__linked-request h2'))
  if (!baseReady) return
  if (!autoActionDone) {
    let action: HTMLElement | null = null
    if (state === 'today-past') action = main.querySelector('.headman-home__pager button[aria-label="Предыдущая пара"]:not(:disabled)')
    if (state === 'today-homework-expanded') action = main.querySelector('.headman-home__homework-toggle:not(:disabled)')
    if ((state === 'reason-form' || state === 'manual-new-empty')) action = main.querySelector('[data-user-id="4"] .headman-journal__picker button:nth-child(2)')
    if (state === 'student-ticket-link' || state.startsWith('ticket-preview')) action = main.querySelector('[data-user-id="2"] .headman-journal__ticket-link')
    if (state === 'manual-file') action = main.querySelector('[data-user-id="2"] .headman-journal__reason-edit')
    if (state === 'journal-tools-dialog') action = main.querySelector('.mobile-toolbar button:first-child')
    if (state.startsWith('weekly-download')) action = main.querySelector('.mobile-toolbar button:last-child')
    if (state.startsWith('clear-confirm')) action = main.querySelector('[data-user-id="2"] .headman-journal__current')
    if (['today-past','today-homework-expanded','reason-form','manual-new-empty','manual-file','student-ticket-link'].includes(state) || state.startsWith('clear-confirm') || state.startsWith('ticket-preview') || (state === 'journal-tools-dialog' || state.startsWith('weekly-download'))) {
      if (!action) return
      autoActionDone = true
      action.click()
      void nextTick(tryReady)
      return
    }
    autoActionDone = true
  }
  if (((state === 'reason-form' || state === 'manual-new-empty') || state === 'manual-file') && heading !== 'Причина «у»') return
  if ((state.startsWith('clear-confirm') || (state === 'journal-tools-dialog' || state.startsWith('weekly-download'))) && !main.querySelector('dialog[open]')) return
  if ((state === 'student-ticket-link' || state.startsWith('ticket-preview')) && (heading !== 'Информация о заявке' || !main.querySelector('.headman-journal__linked-request:not([aria-busy="true"]) h2'))) return
  if (state === 'student-ticket-link' || state.startsWith('ticket-preview')) {
    const preview = main.querySelector('.request-server-preview')
    if (!preview || preview.getAttribute('aria-busy') === 'true') return
    if (state !== 'ticket-preview-expired' && state !== 'ticket-preview-unsupported' && !state.startsWith('ticket-preview-pdf')) {
      const image = preview.querySelector('img')
      if (!(image instanceof HTMLImageElement) || !image.complete || image.naturalWidth === 0) return
    }
  }
  if (state === 'manual-file') { const preview = main.querySelector('.headman-journal__manual-preview'); if (!preview || preview.getAttribute('aria-busy') === 'true' || !preview.querySelector('[data-pdf-ready="true"] canvas,img')) return }
  if (state.startsWith('ticket-preview-pdf') && !main.querySelector('.request-server-preview [data-pdf-ready="true"] canvas')) return
  if (['weekly-download-expanded','weekly-download-multiple','weekly-download-none'].includes(state)) { const dropdown = main.querySelector('dialog[open] .headman-journal__weekly-dropdown'); if (!(dropdown instanceof HTMLDetailsElement)) return; if (!dropdown.open) { dropdown.open = true; return } }
  if (state === 'weekly-download-multiple' || state === 'weekly-download-none') {
    const checks = Array.from(main.querySelectorAll<HTMLInputElement>('.headman-journal__weekly-week input'))
    if (!checks.length) return
    const expected = state === 'weekly-download-none' ? [] : [0, checks.length - 1]
    const mismatch = checks.findIndex((checkbox,index) => checkbox.checked !== expected.includes(index))
    if (mismatch >= 0) { checks[mismatch]!.click(); return }
  }
  if (state === 'action-log-marks' || state === 'action-log-homework' || state === 'action-log-geo-unavailable') {
    const label = state === 'action-log-homework' ? 'ДЗ' : 'Отметки'
    const button = Array.from(main.querySelectorAll<HTMLButtonElement>('.headman-actions__filters button')).find(item=>item.textContent?.trim() === label)
    if (!button) return
    if (button.getAttribute('aria-pressed') !== 'true') { button.click(); return }
  }
  const actualLessonId = view.value === 'today' ? main.getAttribute('data-lesson-id') : String(lessonId.value ?? 30)
  if (state === 'today-past' && actualLessonId !== '20') return
  if (document.documentElement.dataset.headmanReady === 'true'
    && document.documentElement.dataset.headmanHeading === heading
    && document.documentElement.dataset.headmanLesson === actualLessonId) return
  document.documentElement.dataset.headmanReady = 'true'
  document.documentElement.dataset.headmanState = state
  document.documentElement.dataset.headmanHeading = heading ?? ''
  document.documentElement.dataset.headmanGroup = '4'
  document.documentElement.dataset.headmanLesson = actualLessonId ?? ''
}
onMounted(() => {
  document.title = `Староста · ${state} · ${shell.toUpperCase()} · синтетический API`
  observer = new MutationObserver(tryReady)
  document.addEventListener('load', tryReady, true)
  observer.observe(document.getElementById('app')!, { childList: true, attributes: true, subtree: true })
  tryReady()
})
onBeforeUnmount(() => { observer?.disconnect(); document.removeEventListener('load', tryReady, true); document.documentElement.style.fontSize = originalFont; delete document.documentElement.dataset.headmanReady })
</script>

<template>
  <aside
    v-if="query.get('catalog') === '1'"
    class="headman-review-catalog"
  >
    <p>Actual Vue · синтетические API · {{ shell.toUpperCase() }} · Telegram host simulated</p>
    <a
      v-for="id in headmanReviewStates"
      :key="id"
      :href="`?state=${id}&shell=${shell}&catalog=1`"
    >{{ id }}</a>
    <button
      v-if="shell === 'tma' && hostBackVisible"
      type="button"
      @click="hostBack"
    >
      Назад хоста
    </button>
  </aside>
  <MobileShell
    :route="route"
    :nav-items="navItems"
    :host="host"
    @navigate="navigate"
    @back="view === 'log' ? view = 'today' : journal?.back()"
  >
    <HeadmanLessonActionsScreen
      v-if="view === 'log'"
      :api="apis.actions"
      :lesson="{id:30,groupId:4,subjectId:1,date:'2026-10-06',status:'ACTIVE',lessonNumber:3,startTime:'18:00:00',endTime:'19:30:00',room:'А-401',lessonType:'LECTURE',occurrenceRevision:'1',current:true,transferOperationId:null,transferState:null}"
      subject-name="Информационные системы"
    />
    <HeadmanHomeScreen
      v-else-if="view === 'today'"
      :week-policy-api="apis.schedule"
      :api="apis.journal"
      :subjects-api="apis.subjects"
      :homework-api="apis.homework"
      :group-id="4"
      group-label="ИУ6-42"
      :selected-date="date"
      :offline="offline"
      :homework-enabled="true"
      :log-enabled="true"
      @select-date="date = $event"
      @open-lesson="lessonId = $event.id; view = 'journal'"
      @open-log="view = 'log'"
    />
    <HeadmanJournalScreen
      v-else
      ref="journal"
      :api="apis.journal"
      :requests-api="apis.requests"
      :subjects-api="apis.subjects"
      :group-id="4"
      :initial-date="date"
      :initial-lesson-id="lessonId"
      :offline="offline"
      @task-change="task = $event"
    />
  </MobileShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { syntheticAttachmentResponse } from './attachment-preview-fixture'
import { HeadmanStatsApi, type HeadmanStatsQuery, type HeadmanStatsTrendQuery } from '../features/headman-stats/headman-stats-client'
import HeadmanStatsScreen from '../features/headman-stats/HeadmanStatsScreen.vue'
import { HeadmanJournalApi } from '../features/headman-journal/headman-journal-client'
import { HeadmanRequestsApi } from '../features/headman-requests/headman-requests-client'
import MobileShell from '../shared/components/MobileShell.vue'
import { createHeadmanNavigationItems } from '../shared/mobile-navigation-items'
import { nestedRoute } from '../shared/navigation'
import type { MobileHostAdapter } from '../shared/host'
import { headmanStatsFixtureWeekPolicy, headmanStatsFixtureResponse, headmanStatsFixtureTrend, headmanStatsFixtureDetail, headmanStatsFixtureLessons, HEADMAN_STATS_REVIEW_STATES } from './headman-stats-fixtures'

const params = new URLSearchParams(window.location.search)
const state = params.get('state') ?? 'overview-autumn'
const shell = params.get('shell') === 'tma' ? 'tma' : 'pwa'
const screen = ref<InstanceType<typeof HeadmanStatsScreen> | null>(null)
const offline = ref(state === 'offline')
const failures = ref(state === 'error' || state === 'subject-error' || state === 'history-error')
const requests = ref(0)
const lessons = headmanStatsFixtureLessons(state)
let markSequence = 1
const person = ref(false)
const navItems = createHeadmanNavigationItems()
const route = computed(() => nestedRoute('headman-more', 'headman-more/statistics', person.value ? 'detail' : 'overview'))
const host: MobileHostAdapter = { backOwner: shell === 'tma' ? 'host' : 'product', primaryActionOwner: 'product', subscribeBack: (callback) => { hostBack = callback; return () => { hostBack = null } }, setBackVisible: (visible) => { document.documentElement.dataset.headmanStatsHostBackVisible = String(visible) } }
let hostBack: (() => void) | null = null
const previousFont = document.documentElement.style.fontSize
if (params.get('root20') === '1') document.documentElement.style.fontSize = '20px'
function json(value: unknown, status = 200): Response { return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } }) }
// Synthetic server adapter uses the real Api class and Vue screen; no backend/Telegram persistence is claimed.
const fetcher: typeof fetch = async (input, init) => {
  const url = new URL(String(input), window.location.origin)
  requests.value += 1
  await new Promise((resolve) => window.setTimeout(resolve, 30))
  if (failures.value && (state === 'error' || url.pathname.endsWith('/trend'))) return json({ detail: 'Не удалось связаться с сервером. Попробуй ещё раз.' }, 503)
  if (url.pathname.endsWith('/academic-week-policy')) return json(headmanStatsFixtureWeekPolicy(url.searchParams.get('date')!))
  if (url.pathname.endsWith('/semesters')) return json({ groupId: 2, activeSemesterId: state === 'overview-spring' ? 8 : 7, semesters: [{ id: 7, name: 'Осенний семестр 2026', dateFrom: '2026-09-01', dateTo: '2027-01-31', active: state !== 'overview-spring', archived: false }, { id: 8, name: 'Весенний семестр 2027', dateFrom: '2027-02-01', dateTo: '2027-06-30', active: state === 'overview-spring', archived: false }] })
  if (url.pathname.endsWith('/overview')) return json({ groupId: 2, membership: { active: 27, total: 30 }, today: { date: '2026-10-06', present: { numerator: 21, denominator: 27, percent: 21 / 27 * 100 }, presentOrExcused: { numerator: 23, denominator: 27, percent: 23 / 27 * 100 } } })
  if (url.pathname.endsWith('/lessons')) {
    if (state === 'history-error' && failures.value) return json({ detail: 'Не удалось загрузить историю. Попробуй ещё раз.' }, 503)
    const subjectId = url.searchParams.get('subjectId'); const selectedTypes = url.searchParams.getAll('lessonTypes'); const filtered = lessons.filter(lesson => (!subjectId || lesson.subjectId === Number(subjectId)) && (!selectedTypes.length || selectedTypes.includes(lesson.lessonType)))
    return json({ page: 0, size: 50, totalElements: filtered.length, totalPages: filtered.length ? 1 : 0, hasPrevious: false, hasNext: false, items: filtered })
  }
  const mark = /^\/api\/attendance\/lessons\/(\d+)\/students\/(\d+)$/.exec(url.pathname)
  if (mark) {
    const index = lessons.findIndex((lesson) => lesson.lessonId === Number(mark[1])); const lesson = lessons[index]
    if (!lesson || Number(mark[2]) !== 11) return json({ detail: 'Нет доступа к отметке.' }, 403)
    if (init?.method === 'DELETE') {
      if (lesson.status !== url.searchParams.get('expectedStatus') || lesson.markRevision !== url.searchParams.get('expectedRevision')) return json({ detail: 'Отметка изменилась.' }, 409)
      lessons[index] = { ...lesson, status: null, source: null, markRevision: null }
      return new Response(null, { status: 204 })
    }
    if (init?.method === 'PUT') { const body = init.body instanceof FormData ? JSON.parse(await (init.body.get('request') as Blob).text()) : JSON.parse(String(init.body)); const status = body.status; if (body.expectedStatus !== (lesson.status ?? 'EMPTY') || body.expectedRevision !== (lesson.markRevision ?? 'EMPTY')) return json({ detail: 'Отметка изменилась. Обнови историю.' }, 409); if (status !== 'PRESENT' && status !== 'EXCUSED' && status !== 'ABSENT') return json({ detail: 'Некорректный статус' }, 400); lessons[index] = { ...lesson, status, source: 'HEADMAN_MANUAL', markRevision: 'fixture-mark-' + lesson.lessonId + '-v' + ++markSequence }; return json({ lessonId: lesson.lessonId, userId: 11, status, timestamp: '2026-10-06T08:35:00Z' }) }
  }
  if (url.pathname.includes('/attachments/')) return syntheticAttachmentResponse()
  if (url.pathname.startsWith('/api/attendance/requests/')) return json({ summary: { id: 'b4b8c43c-0221-4138-8295-61620c64f01d', kind: 'EXCUSE', status: 'APPROVED', studentId: 11, studentName: 'Александрова Мария', reason: 'Болезнь', comment: 'Справка за дни отсутствия', hasAttachments: true }, lessons: [], attachments: [{id:'fixture-file',name:'Учебный образец.png',contentType:'image/png',state:'AVAILABLE',size:28000,expiresAt:null}] })
  if (url.pathname.endsWith('/query')) return json(headmanStatsFixtureResponse(state, JSON.parse(String(init?.body)) as HeadmanStatsQuery))
  if (url.pathname.endsWith('/trend')) return json(headmanStatsFixtureTrend(state, JSON.parse(String(init?.body)) as HeadmanStatsTrendQuery, url.pathname.includes('/students/'), Number(/students\/(\d+)\//.exec(url.pathname)?.[1] ?? '11')))
  const student = /\/students\/(\d+)\/detail$/.exec(url.pathname)
  if (student) return json(headmanStatsFixtureDetail(state, Number(student[1]), url.searchParams.has('semesterId') ? Number(url.searchParams.get('semesterId')) : undefined))
  return json({ detail: 'Fixture: неизвестная операция' }, 404)
}
const api = new HeadmanStatsApi({ accessToken: () => 'synthetic-review-token', fetcher })
const journalApi = new HeadmanJournalApi({ accessToken: () => 'synthetic-review-token', fetcher })
const requestsApi = new HeadmanRequestsApi({ accessToken: () => 'synthetic-review-token', fetcher })
let initialized = false
let historyAction = false
let stateStep = 0
function clickSelector(selector: string): boolean { const button = document.querySelector<HTMLButtonElement>(selector); if (!button || button.disabled) return false; button.click(); return true }
function clickText(selector: string, text: string): boolean { const button = [...document.querySelectorAll<HTMLButtonElement>(selector)].find(item => item.textContent?.trim() === text); if (!button || button.disabled) return false; button.click(); return true }
const toolButton = (label: string) => () => Boolean(screen.value?.ready) && clickSelector(`nav[aria-label="Инструменты статистики"] button[aria-label="${label}"]`)
const listButton = (label: string) => () => clickSelector(`button[aria-haspopup="listbox"][aria-label="${label}"]`)
const subjectScope = () => clickText('.mobile-tools-sheet[open] .headman-stats__exclusive button', 'По предмету и типу')
const stateActions: Record<string, (() => boolean)[]> = {
  'sort-options': [toolButton('Сортировка'), listButton('Порядок')],
  'export-options': [toolButton('Выгрузка'), listButton('Формат выгрузки')],
  'subject-filters': [toolButton('Фильтры'), subjectScope],
  'subject-filter-options': [toolButton('Фильтры'), subjectScope, listButton('Предмет фильтра')],
  'subject-sort-options': [toolButton('Сортировка'), listButton('Порядок')],
  'subject-export-options': [toolButton('Выгрузка'), listButton('Формат выгрузки')],
  'personal-filters': [toolButton('Фильтры')],
  'personal-subject-filter': [toolButton('Фильтры'), subjectScope],
  'personal-subject-options': [toolButton('Фильтры'), subjectScope, listButton('Предмет фильтра')],
  'trend-week-current': [() => clickText('.headman-stats__blocks--trend button', 'Неделя')],
  'trend-week-earliest': [() => clickText('.headman-stats__blocks--trend button', 'Неделя'), () => { const button = document.querySelector<HTMLButtonElement>('.headman-stats__week button[aria-label="Предыдущая неделя"]'); if (!button || !screen.value?.ready) return false; if (button.disabled) return true; button.click(); return false }],
  'trend-week': [() => clickText('.headman-stats__blocks--trend button', 'Неделя'), () => Boolean(screen.value?.ready) && clickSelector('.headman-stats__week button[aria-label="Предыдущая неделя"]')],
  'trend-subject': [() => clickText('.headman-stats__blocks--trend button', 'Предмет'), listButton('Предмет динамики'), () => clickText('[role="option"]', 'Высшая математика'), () => clickSelector('.headman-stats__analytics button[data-lesson-type="LECTURE"]')],
  'trend-subject-options': [() => clickText('.headman-stats__blocks--trend button', 'Предмет'), listButton('Предмет динамики')],
  'trend-export-options': [listButton('Формат графика')],
}
function advanceState(): void { const action = stateActions[state]?.[stateStep]; if (initialized && action?.()) stateStep += 1 }

function ensureHistoryAction(): void {
  if (historyAction) return
  const selector = state === 'history-confirmation' ? '.headman-stats-history__choice--excused' : state === 'history-ticket-preview' ? '.headman-stats-history__ticket' : null
  const button = selector ? document.querySelector<HTMLButtonElement>(selector) : null
  if (button && !button.disabled) { historyAction = true; button.click() }
}
function updateReadiness(): void {
  ensureHistoryAction()
  advanceState()
  let ready = Boolean(screen.value?.ready) && stateStep === (stateActions[state]?.length ?? 0)
  if (state.startsWith('trend-week')) ready &&= [1,2].includes(Number(document.querySelector<HTMLElement>('.headman-stats__week small[data-parity-number]')?.dataset.parityNumber))
  if (state === 'subject-error') ready = Boolean(document.querySelector('.headman-stats-mobile__table') && document.querySelector('.student-warning'))
  if (state === 'history-error') ready = Boolean(document.querySelector('.headman-stats-history .student-warning'))
  if (state === 'error' || state === 'offline') ready = Boolean(document.querySelector('.student-warning'))
  if (state.startsWith('history')) ready &&= Boolean(document.querySelector('.headman-stats-history:not([aria-busy="true"])'))
  if (state.endsWith('-tools') && state !== 'tickets-tools') ready &&= Boolean(document.querySelector('.mobile-tools-sheet[open]'))
  if (state === 'tickets-tools' || state === 'tickets-unavailable' || state === 'trend-page') ready &&= Boolean(document.querySelector('.headman-stats__analytics'))
  if (state === 'history-confirmation') ready &&= Boolean(document.querySelector('.headman-stats-history dialog[open]'))
  if (state === 'history-ticket-preview') {
    const img = document.querySelector<HTMLImageElement>('.request-server-preview img')
    ready &&= Boolean(img?.complete && img.naturalWidth > 0)
  }
  if (document.documentElement.dataset.headmanStatsFixtureReady !== String(ready)) document.documentElement.dataset.headmanStatsFixtureReady = String(ready)
}
const actionObserver = new MutationObserver(updateReadiness)
document.addEventListener('load', updateReadiness, true)
actionObserver.observe(document.documentElement, { childList: true, subtree: true, attributes: true })

watch(() => screen.value?.ready, updateReadiness, {flush:'post'})
watch(() => screen.value?.catalogReady, (ready) => {
  updateReadiness()
  if (!ready || initialized) return
  initialized = true
  if (state.endsWith('-tools')) screen.value?.openTool(state === 'tickets-tools' ? 'tickets' : state.replace('-tools','') as 'filters' | 'sort' | 'export')
  if (state === 'trend-page' || state.startsWith('trend-') || state === 'tickets-unavailable') screen.value?.openTool(state === 'tickets-unavailable' ? 'tickets' : 'trend')
  if (state.startsWith('subject')) { screen.value?.openBlock('subject'); if (!['subject-catalog','subject-filters','subject-filter-options'].includes(state)) screen.value?.selectSubject('12') }
  if (state === 'subject-single') screen.value?.setMobileTypes(['LECTURE'])
  if (state === 'subject-none') screen.value?.setMobileTypes([])
  if (state === 'days-point') { screen.value?.openBlock('subject'); screen.value?.selectSubject('12'); screen.value?.setChartRange('days') }
  if (state === 'person-detail' || state.startsWith('personal-') || state.startsWith('history')) { person.value = true; screen.value?.openStudentDetail(11, new MouseEvent('click')); if (state.startsWith('history')) screen.value?.openHistory() }
}, { flush: 'post' })
watch(requests, () => { document.documentElement.dataset.headmanStatsFixtureRequests = String(requests.value) })
document.documentElement.dataset.headmanStatsFixtureState = state
document.documentElement.dataset.headmanStatsFixtureSource = 'actual-vue-synthetic-api'
document.title = `Статистика старосты · ${state} · ${shell.toUpperCase()} · API fixture`
function back(): void { screen.value?.back() }
function href(id: string): string { return '?' + new URLSearchParams({ state: id, shell }).toString() }
function invokeHostBack(): void { hostBack?.() }
onBeforeUnmount(() => { actionObserver.disconnect(); document.removeEventListener('load', updateReadiness, true); document.documentElement.style.fontSize = previousFont })
</script>
<template>
  <MobileShell
    :route="route"
    :nav-items="navItems"
    :host="host"
    :custom-back="shell === 'pwa'"
    @back="back"
  >
    <template #back>
      <span aria-hidden="true" />
    </template>
    <HeadmanStatsScreen
      ref="screen"
      :api="api"
      :journal-api="journalApi"
      :requests-api="requestsApi"
      :group-id="2"
      :offline="offline"
      :show-back="shell === 'pwa'"
      @back="back"
      @view-change="person = $event !== 'overview'"
    />
  </MobileShell>
  <aside v-if="params.get('controls') === '1'">
    <p>API fixture, actual Vue · {{ shell }} · запросы {{ requests }}</p><a
      v-for="id in HEADMAN_STATS_REVIEW_STATES"
      :key="id"
      :href="href(id)"
    >{{ id }} </a><button
      type="button"
      @click="failures = false"
    >
      Восстановить сервер
    </button><button
      type="button"
      @click="invokeHostBack"
    >
      Host Back
    </button>
  </aside>
</template>

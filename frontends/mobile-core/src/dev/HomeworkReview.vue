<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { StudentApi } from '../api/student-client'
import type { StudentHomework, StudentHomeworkItem } from '../api/types'
import HomeworkScreen from '../features/homework/HomeworkScreen.vue'
import { homeworkScopeIdentity, useHomework, type StudentHomeworkQueryScope } from '../features/homework/use-homework'
import { createStudentNavigationItems } from '../shared/mobile-navigation-items'
import type { MobileHostAdapter } from '../shared/host'
import { createHomeworkReviewScenarios } from './homework-fixtures'
import './homework-review.pcss'

const query = new URLSearchParams(window.location.search)
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const scenarios = createHomeworkReviewScenarios()
const scenario = scenarios.find((value) => value.id === query.get('state')) ?? scenarios[0]!
const catalog = query.get('catalog') === '1'
const navItems = createStudentNavigationItems({ homeworkEnabled: true, attendanceEnabled: true, moreEnabled: true, profileEnabled: true })
const offline = ref(scenario.offline ?? false)
const unavailable = scenario.unavailable ?? null
const scope = ref<StudentHomeworkQueryScope | null>(unavailable ? null : {
  userId: 'review-student', activeRole: 'STUDENT', groupId: 'review-group', semesterId: 'semester-review',
  sessionId: 'review-session', sessionVersion: '1', rolesVersion: '1', readOnly: scenario.readOnly ?? false, resetGeneration: 0,
})
const authoritative = structuredClone(scenario.feed)
let failedCommand = false
let feedFailed = scenario.feedError ?? false
const status = ref('')
const requests = ref(0)
const host: MobileHostAdapter = { backOwner: shell === 'tma' ? 'host' : 'product', primaryActionOwner: 'product' }
const timers = new Map<number, () => void>()
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => { const timer = window.setTimeout(() => { timers.delete(timer); resolve() }, ms); timers.set(timer, resolve) })
}
function response(value: unknown, code = 200): Response { return new Response(JSON.stringify(value), { status: code, headers: { 'Content-Type': 'application/json' } }) }
// Only this fixture owns the synthetic server state. No real API or database is contacted.
const fetcher: typeof fetch = async (input, init) => {
  const url = new URL(String(input), window.location.origin)
  if (init?.method === 'PUT') {
    requests.value += 1
    const id = decodeURIComponent(url.pathname.split('/').at(-2) ?? '')
    const desired = (JSON.parse(String(init.body)) as { completed: boolean }).completed
    if (scenario.commandFailure && !failedCommand) {
      failedCommand = true
      return response({ title: 'Отметка не сохранена', detail: 'Связь прервалась. Повтори ту же отметку, чтобы уточнить результат.' }, 503)
    }
    await delay(scenario.holdCommand ? 60000 : 1400)
    const item = authoritative?.items.find((entry) => entry.id === id)
    if (!item) return response({ title: 'Задание не найдено' }, 404)
    item.completed = desired
    item.completedAt = desired ? item.completedAt ?? '2026-09-01T09:31:00Z' : null
    return response({ id, completed: item.completed, completedAt: item.completedAt })
  }
  if (scenario.loading) await delay(60000)
  if (feedFailed) return response({ title: 'Задания не загружены', detail: 'Не удалось связаться с сервером. Попробуй ещё раз.' }, 503)
  const snapshot: StudentHomework = structuredClone(authoritative ?? { semester: { id: 'semester-review', name: 'Осень 2026', dateFrom: '2026-08-20', dateTo: '2026-12-31' }, from: '2026-09-01', to: '2026-12-31', serverNow: '2026-09-01T09:30:00Z', items: [] })
  const from = url.searchParams.get('from')
  const to = url.searchParams.get('to')
  if (from && to) {
    snapshot.from = from; snapshot.to = to
    snapshot.items = snapshot.items.filter((entry) => entry.lessonDate >= from && entry.lessonDate <= to)
  }
  return response(snapshot)
}
const api = new StudentApi({ accessToken: () => 'synthetic-review-token', fetcher })
const controller = useHomework(api, scope, { offline, readOnly: scenario.readOnly ?? false, fallback: scenario.feed, initialRange: scenario.initialRange ?? null, retry: false })
const currentFeed = computed(() => controller.query.data.value ?? scenario.feed)
const pending = computed(() => currentFeed.value?.items.filter((entry) => controller.isPending(entry.id)).map((entry) => entry.id) ?? [])
const errors = computed(() => Object.fromEntries(currentFeed.value?.items.map((entry) => [entry.id, controller.itemError(entry.id)]) ?? []))
const previousFont = document.documentElement.style.fontSize
if (scenario.enlargedText || query.get('root20') === '1') document.documentElement.style.fontSize = '20px'
document.title = `${scenario.label} · ${shell.toUpperCase()} · API fixture`
function complete(item: StudentHomeworkItem, desired: boolean): void { void controller.submitCompletion(item.id, desired).catch(() => undefined) }
function retry(item: StudentHomeworkItem): void { void controller.retryCompletion(item.id)?.catch(() => undefined) }
function retryFeed(): void { feedFailed = false; void controller.query.refetch() }
function material(url: string): void { status.value = `API fixture: внешний материал ${url}` }
function href(id: string): string { return `?${new URLSearchParams({ state: id, shell })}` }
watch(requests, (count) => { document.documentElement.dataset.homeworkFixtureCommands = String(count) }, { immediate: true })
onMounted(() => {
  if (scenario.initialCommand === undefined) return
  const stop = watch(controller.query.data, (data) => {
    if (!data?.items[0]) return
    complete(data.items[0], scenario.initialCommand!)
    stop()
  }, { flush: 'post' })
})
onBeforeUnmount(() => {
  document.documentElement.style.fontSize = previousFont
  delete document.documentElement.dataset.homeworkFixtureCommands
  for (const [timer, resolve] of timers) { window.clearTimeout(timer); resolve() }
  timers.clear()
})
</script>
<template>
  <main
    v-if="catalog"
    class="homework-review-catalog"
  >
    <h1>Задания · {{ shell.toUpperCase() }}</h1>
    <p>Синтетические API fixtures. Настоящие Vue-компоненты и useHomework. Сервер и БД не вызываются.</p>
    <ul>
      <li
        v-for="item in scenarios"
        :key="item.id"
      >
        <a :href="href(item.id)">{{ item.label }}</a> · {{ item.id }}
      </li>
    </ul>
  </main>
  <template v-else>
    <HomeworkScreen
      :homework="currentFeed"
      :loading="controller.query.isLoading.value"
      :error="controller.query.error.value?.message ?? null"
      :nav-items="navItems"
      :host="host"
      :owner-key="homeworkScopeIdentity(scope)"
      :offline="offline"
      :read-only="scenario.readOnly ?? false"
      :unavailable-message="unavailable"
      :updated-at="scenario.offline ? '2026-09-01T09:00:00Z' : null"
      :historical="controller.isHistorical.value"
      :can-load-previous="controller.canLoadPrevious.value"
      :pending-ids="pending"
      :item-errors="errors"
      :focus-item-id="scenario.expandedId ?? null"
      :focus-request-id="scenario.expandedId ? 1 : null"
      @complete="complete"
      @retry="retry"
      @retry-feed="retryFeed"
      @previous="controller.loadPrevious()"
      @return-today="controller.returnToToday()"
      @open-material="material"
      @navigate="status = 'API fixture: навигация без перехода'"
    />
    <p
      v-if="status"
      class="homework-review-status"
      role="status"
    >
      {{ status }}
    </p>
  </template>
</template>

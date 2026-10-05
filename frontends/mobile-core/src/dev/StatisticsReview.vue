<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { StudentApi } from '../api/student-client'
import StatisticsScreen from '../features/statistics/StatisticsScreen.vue'
import { useStatistics } from '../features/statistics/use-statistics'
import MobileShell from '../shared/components/MobileShell.vue'
import StudentWarningBlock from '../shared/components/StudentWarningBlock.vue'
import type { MobileHostAdapter } from '../shared/host'
import { createStudentNavigationItems } from '../shared/mobile-navigation-items'
import { nestedRoute } from '../shared/navigation'
import { studentFeatureScopeIdentity, type StudentFeatureScope } from '../shared/session-owner'
import {
  createStatisticsReviewScenarios, STATISTICS_FIXTURE_SUBJECT, statisticsFixtureDetail, statisticsFixtureOverview, statisticsFixtureSemester,
} from './statistics-fixtures'
import './statistics-review.pcss'

const query = new URLSearchParams(window.location.search)
const shell = query.get('shell') === 'tma' ? 'tma' : 'pwa'
const scenarios = createStatisticsReviewScenarios()
const scenario = scenarios.find((item) => item.id === query.get('state')) ?? scenarios[0]!
const semester = statisticsFixtureSemester(scenario)
const catalog = query.get('catalog') === '1'
const diagnostics = query.get('controls') === '1' || query.get('diagnostics') === '1'
const offline = ref(scenario.offline ?? false)
const owner = ref(0)
function ownerScope(): StudentFeatureScope {
  return { userId: 'review-student-' + owner.value, activeRole: 'STUDENT', groupId: 'review-group',
    semesterId: 'semester-review', sessionId: 'review-session-' + owner.value, sessionVersion: String(owner.value + 1),
    rolesVersion: '1', readOnly: false, resetGeneration: owner.value }
}
const scope = ref<StudentFeatureScope | null>(scenario.unavailable || catalog ? null : ownerScope())
const requests = ref(0)
const lastRequestTypes = ref('')
const returnedCards = ref('')
const status = ref('')
const hostBackVisible = ref(false)
let hostBack: (() => void) | null = null
let overviewFailed = scenario.overviewError ?? false
let detailFailed = scenario.detailError ?? false
let filterFailed = scenario.filterError ?? false
let raceArmed = false
const timers = new Map<number, () => void>()
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    const timer = window.setTimeout(() => { timers.delete(timer); resolve() }, ms)
    timers.set(timer, resolve)
  })
}
function response(value: unknown, code = 200): Response {
  return new Response(JSON.stringify(value), { status: code, headers: { 'Content-Type': 'application/json' } })
}
// This server adapter is synthetic. No real HTTP transport, backend, database or Telegram API is called.
const fetcher: typeof fetch = async (input, init) => {
  const url = new URL(String(input), window.location.origin)
  const isDetail = url.pathname.startsWith('/api/v1/student/statistics/subjects/')
  if (init?.method && init.method !== 'GET' || !isDetail && url.pathname !== '/api/v1/student/statistics') {
    return response({ title: 'Fixture: неизвестная операция' }, 404)
  }
  requests.value += 1
  const requestOwner = owner.value
  const types = url.searchParams.getAll('types')
  lastRequestTypes.value = isDetail ? types.join(',') || 'ALL' : 'overview'
  if (scenario.ownerRace && raceArmed && requestOwner === 0 && isDetail) await delay(1800)
  else if (isDetail && scenario.detailLoading || !isDetail && scenario.overviewLoading) await delay(60000)
  else if (isDetail && types.length > 0 && scenario.filterLoading) await delay(60000)
  else await delay(80)
  if (scenario.forbidden) return response({ title: 'Статистика недоступна', detail: 'У тебя нет доступа к статистике.' }, 403)
  if (!isDetail && overviewFailed || isDetail && detailFailed || isDetail && types.length > 0 && filterFailed) {
    return response({ title: 'Статистика не загружена', detail: 'Не удалось связаться с сервером. Попробуй ещё раз.' }, 503)
  }
  if (!isDetail) return response(statisticsFixtureOverview(scenario, requestOwner))
  const subjectId = decodeURIComponent(url.pathname.slice('/api/v1/student/statistics/subjects/'.length))
  const detail = statisticsFixtureDetail(scenario, subjectId, url.searchParams.get('range') === 'days' ? 'days' : 'weeks', types, requestOwner)
  returnedCards.value = detail.typeCards.map((card) => card.type).join(',')
  return response(detail)
}
const api = new StudentApi({ accessToken: () => 'synthetic-review-token', fetcher })
const controller = useStatistics(api, scope, offline)
const navItems = createStudentNavigationItems({ homeworkEnabled: true, attendanceEnabled: true, moreEnabled: true, profileEnabled: true })
const route = computed(() => nestedRoute('more', 'more/statistics', controller.selectedSubjectId.value ? 'detail' : 'overview'))
const host: MobileHostAdapter = {
  backOwner: shell === 'tma' ? 'host' : 'product', primaryActionOwner: 'product',
  subscribeBack: (listener) => { hostBack = listener; return () => { hostBack = null } },
  setBackVisible: (visible) => { hostBackVisible.value = visible },
}
const previousFont = document.documentElement.style.fontSize
if (scenario.enlargedText || query.get('root20') === '1') document.documentElement.style.fontSize = '20px'
document.title = scenario.label + ' · ' + shell.toUpperCase() + ' · API fixture'

// Initialize through actual controller after its first successful ALL response, only once.
let opened = false
let selected = false
watch(controller.query.data, (data) => {
  if (opened || !scenario.subject || !data?.subjects[0]) return
  opened = true
  controller.setRange(scenario.initialRange ?? 'weeks')
  controller.openSubject(STATISTICS_FIXTURE_SUBJECT)
}, { flush: 'post' })
watch(controller.detailState, (state) => {
  if (selected || state.status !== 'ready') return
  selected = true
  if (scenario.initialTypes !== undefined) controller.setTypes(scenario.initialTypes)
}, { flush: 'post' })
function back(): void {
  if (controller.selectedSubjectId.value !== null) controller.closeSubject()
  else status.value = 'API fixture: возврат в «Ещё»'
}
function retryOverview(): void { overviewFailed = false; void controller.query.refetch() }
function retryDetail(): void {
  detailFailed = false; filterFailed = false
  controller.retryDetail()
}
function replaceOwner(): void {
  owner.value += 1
  scope.value = ownerScope()
  status.value = 'API fixture: owner заменён, прежние ответы должны быть отброшены'
}
function startRace(): void {
  raceArmed = true
  controller.setTypes(['LECTURE'])
  // Start the request under owner 0, then change identity before its late result.
  void delay(150).then(replaceOwner)
}
function href(id: string): string { return '?' + new URLSearchParams({ state: id, shell }).toString() }
function invokeHostBack(): void { hostBack?.() }
const ownerKey = computed(() => scope.value ? studentFeatureScopeIdentity(scope.value) : 'scope-unavailable')
watch([requests, lastRequestTypes, returnedCards, ownerKey, controller.detailState, hostBackVisible], () => {
  const data = document.documentElement.dataset
  data.statisticsFixtureSource = 'statistics-graph-corrections-v1'
  data.statisticsFixtureSemesterStartsOn = semester.startsOn
  data.statisticsFixtureSemesterEndsOn = semester.endsOn
  data.statisticsFixtureRequests = String(requests.value)
  data.statisticsFixtureTypes = lastRequestTypes.value
  data.statisticsFixtureReturnedCards = returnedCards.value
  data.statisticsFixtureOwner = ownerKey.value
  data.statisticsFixtureSelectedTypes = controller.detailState.value.status === 'ready' ? controller.detailState.value.data.selectedTypes.join(',') : ''
  data.statisticsFixtureHostBack = String(hostBackVisible.value)
  data.statisticsFixtureShell = shell
}, { immediate: true })
onBeforeUnmount(() => {
  document.documentElement.style.fontSize = previousFont
  for (const key of Object.keys(document.documentElement.dataset)) if (key.startsWith('statisticsFixture')) delete document.documentElement.dataset[key]
  for (const [timer, resolve] of timers) { window.clearTimeout(timer); resolve() }
  timers.clear()
})
</script>

<template>
  <main
    v-if="catalog"
    class="statistics-review-catalog"
  >
    <h1>Статистика · {{ shell.toUpperCase() }}</h1>
    <p>Синтетические ответы StudentApi, настоящие Vue-компоненты и useStatistics. Сервер, БД и native Telegram не вызываются.</p>
    <ul>
      <li
        v-for="item in scenarios"
        :key="item.id"
      >
        <a :href="href(item.id)">{{ item.label }}</a> · {{ item.id }}
      </li>
    </ul>
  </main>
  <MobileShell
    v-else
    :route="route"
    :nav-items="navItems"
    :host="host"
    @back="back"
    @navigate="status = 'API fixture: навигация без перехода'"
  >
    <template #back>
      <span
        hidden
        aria-hidden="true"
      />
    </template>
    <div
      v-if="scenario.unavailable"
      class="statistics-review-unavailable"
    >
      <StudentWarningBlock
        :title="scenario.unavailable === 'semester' ? 'Семестр ещё не выбран' : 'Сессия завершена'"
        :message="scenario.unavailable === 'semester' ? 'Статистика появится, когда у группы будет текущий семестр.' : 'Войди снова, чтобы увидеть свою статистику.'"
      />
    </div>
    <StatisticsScreen
      v-else
      :state="controller.overviewState.value"
      :selected-subject-id="controller.selectedSubjectId.value"
      :detail-state="controller.detailState.value"
      :range="controller.range.value"
      :semester-starts-on="semester.startsOn"
      :semester-ends-on="semester.endsOn"
      :terminal="offline"
      @open-subject="controller.openSubject"
      @set-range="controller.setRange"
      @set-types="controller.setTypes"
      @back="back"
      @retry="retryOverview"
      @detail-retry="retryDetail"
    />
    <aside
      v-if="diagnostics"
      class="statistics-review-diagnostics"
      aria-label="Управление API fixture"
    >
      <p>API fixture · {{ requests }} запросов · {{ lastRequestTypes }} · owner {{ owner }}</p>
      <button
        v-if="shell === 'tma'"
        type="button"
        :disabled="!hostBackVisible"
        @click="invokeHostBack"
      >
        Host Back (симуляция)
      </button>
      <button
        type="button"
        @click="replaceOwner"
      >
        Сменить owner
      </button>
      <button
        v-if="scenario.ownerRace"
        type="button"
        @click="startRace"
      >
        Поздний ответ → смена owner
      </button>
    </aside>
    <p
      v-if="status"
      class="statistics-review-status"
      role="status"
    >
      {{ status }}
    </p>
  </MobileShell>
</template>

<script setup lang="ts">
defineOptions({ name: 'AttendanceStatisticsHarness' })
import { computed, onBeforeUnmount, ref } from 'vue'
import AttendanceScreen from '../../src/features/attendance/AttendanceScreen.vue'
import StatisticsScreen from '../../src/features/statistics/StatisticsScreen.vue'
import { attendanceFixture, attendanceReady, statisticsDetailProjection, statisticsFixture } from './fixtures'
import type {
  AttendanceGraphRange,
  AttendanceLesson,
  AttendanceMode,
  AttendanceReadState,
  AttendanceRequestOption,
  AttendanceViewModel,
} from '../../src/features/attendance/attendance-view-model'
import type {
  StatisticsGraphRange,
  StatisticsLessonType,
  StatisticsOverviewData,
  StatisticsReadState,
  StatisticsSubjectDetailData,
} from '../../src/features/statistics/statistics-view-model'

const query = new URLSearchParams(window.location.search)
const fixtureId = query.get('fixture') ?? '4593-142'
const screenName = query.get('screen') ?? (fixtureId.startsWith('4603') || fixtureId.startsWith('4798') ? 'statistics' : 'attendance')
const requestedStatus = query.get('status')
const themePreference = query.get('theme') ?? 'system'

function readState<T>(requested: string | null, readyData: T): AttendanceReadState<T> {
  if (requested === 'loading') return { status: 'loading' }
  if (requested === 'empty') return { status: 'empty' }
  if (requested === 'offline') return { status: 'offline' }
  if (requested === 'forbidden') return { status: 'forbidden', reason: 'У тебя нет доступа к этому разделу.' }
  if (requested === 'error') return { status: 'error', code: 'HARNESS_READ', message: 'Сервис временно недоступен.', retryable: true }
  return { status: 'ready', data: readyData }
}

function statisticsReadState<T>(requested: string | null, readyData: T): StatisticsReadState<T> {
  if (requested === 'loading') return { status: 'loading' }
  if (requested === 'empty') return { status: 'empty' }
  if (requested === 'offline') return { status: 'offline' }
  if (requested === 'forbidden') return { status: 'forbidden', reason: 'У тебя нет доступа к этому разделу.' }
  if (requested === 'error') return { status: 'error', code: 'HARNESS_READ', message: 'Сервис временно недоступен.', retryable: true }
  return { status: 'ready', data: readyData }
}

const attendance = attendanceFixture(fixtureId)
const attendanceState = ref<AttendanceReadState<AttendanceViewModel>>(readState(requestedStatus, attendance.state.status === 'ready' ? attendance.state.data : {
  metrics: { present: { count: 0, percent: null }, presentOrExcused: { count: 0, percent: null }, excused: { count: 0, percent: null }, absent: { count: 0, percent: null }, held: 0, planned: 0 }, days: [], subjects: [], graph: { days: [], weeks: [] },
}))
const attendanceMode = ref<AttendanceMode>(attendance.mode)
const attendanceRange = ref<AttendanceGraphRange>(attendance.graphRange)
const selectedDate = ref(attendance.selectedDate)
const expandedSubjectId = ref<string | null>(attendance.expandedSubjectId)
const actionLessonId = ref<string | null>(attendance.actionLessonId)
const requestLessonId = ref<string | null>(attendance.requestLessonId)
const requestOptionId = ref<string | null>(attendance.requestOptionId)

function selectAttendanceDate(date: string): void {
  selectedDate.value = date
  actionLessonId.value = null
}

function setAttendanceMode(mode: AttendanceMode): void {
  attendanceMode.value = mode
  actionLessonId.value = null
}

function toggleAttendanceSubject(subjectId: string): void {
  expandedSubjectId.value = expandedSubjectId.value === subjectId ? null : subjectId
}

function toggleAttendanceActions(lessonId: string): void {
  actionLessonId.value = actionLessonId.value === lessonId ? null : lessonId
}

function openAttendanceRequest(lesson: AttendanceLesson, option: AttendanceRequestOption): void {
  requestLessonId.value = lesson.id
  requestOptionId.value = option.id
  actionLessonId.value = null
}

function closeAttendanceRequest(): void {
  requestLessonId.value = null
  requestOptionId.value = null
}

const statistics = statisticsFixture(fixtureId)
const statisticsReady = statistics.state.status === 'ready' ? statistics.state.data : null
const statisticsState = ref<StatisticsReadState<StatisticsOverviewData>>(statisticsReadState(requestedStatus, statisticsReady ?? {
  metrics: { present: { count: 0, percent: null }, presentOrExcused: { count: 0, percent: null }, excused: { count: 0, percent: null }, absent: { count: 0, percent: null }, held: 0, planned: 0 }, ownRank: { position: null, participantCount: 0, available: false }, semesterSeries: [], subjects: [],
}))
const selectedSubjectId = ref<string | null>(statistics.selectedSubjectId)
const statisticsDetailState = ref<StatisticsReadState<StatisticsSubjectDetailData> | null>(statistics.detailState && statistics.detailState.status === 'ready'
  ? statisticsReadState(requestedStatus, statistics.detailState.data)
  : null)
const statisticsRange = ref<StatisticsGraphRange>('weeks')

function setStatisticsTypes(types: readonly StatisticsLessonType[]): void {
  const current = statisticsDetailState.value
  if (!current || current.status !== 'ready' || types.length === 0) return
  const nextData = statisticsDetailProjection(current.data, types, statisticsRange.value)
  if (!nextData) return
  statisticsDetailState.value = { status: 'ready', data: nextData }
}

function setStatisticsRange(range: StatisticsGraphRange): void {
  statisticsRange.value = range
  const current = statisticsDetailState.value
  if (!current || current.status !== 'ready') return
  const nextData = statisticsDetailProjection(current.data, current.data.selectedTypes, range)
  if (!nextData) return
  statisticsDetailState.value = { status: 'ready', data: nextData }
}

function openStatisticsSubject(subjectId: string): void {
  selectedSubjectId.value = subjectId
  if (statisticsDetailState.value === null && statisticsReady) {
    const subject = statisticsReady.subjects.find((candidate) => candidate.id === subjectId)
    if (subject) statisticsDetailState.value = { status: 'empty' }
  }
}

function backFromStatistics(): void {
  selectedSubjectId.value = null
}

const systemDark = ref(window.matchMedia('(prefers-color-scheme: dark)').matches)
const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
const onMediaChange = (event: MediaQueryListEvent): void => { systemDark.value = event.matches }
mediaQuery.addEventListener('change', onMediaChange)
onBeforeUnmount(() => mediaQuery.removeEventListener('change', onMediaChange))

const theme = computed<'dark' | 'light'>(() => themePreference === 'light' || (themePreference !== 'dark' && !systemDark.value) ? 'light' : 'dark')
const terminal = query.get('terminal') === 'true'
const showDock = query.get('dock') !== 'false'
const activeScreen = computed(() => screenName === 'statistics' ? 'statistics' : 'attendance')

const requestedRootFont = Number(query.get('rootFont'))
if (requestedRootFont === 16 || requestedRootFont === 20 || requestedRootFont === 24) {
  document.documentElement.style.fontSize = `${requestedRootFont}px`
}
</script>

<template>
  <div
    class="harness-root"
    :data-screen="activeScreen"
    :data-fixture="fixtureId"
    :data-theme="theme"
  >
    <AttendanceScreen
      v-if="activeScreen === 'attendance'"
      :state="attendanceState"
      :selected-date="selectedDate"
      :mode="attendanceMode"
      :graph-range="attendanceRange"
      :expanded-subject-id="expandedSubjectId"
      :action-lesson-id="actionLessonId"
      :request-lesson-id="requestLessonId"
      :request-option-id="requestOptionId"
      :theme="theme"
      :terminal="terminal"
      :show-dock="showDock"
      @select-date="selectAttendanceDate"
      @set-mode="setAttendanceMode"
      @set-graph-range="attendanceRange = $event"
      @toggle-subject="toggleAttendanceSubject"
      @toggle-actions="toggleAttendanceActions"
      @open-request="openAttendanceRequest"
      @back="closeAttendanceRequest"
      @retry="attendanceState = readState(null, attendanceReady)"
    />

    <StatisticsScreen
      v-else
      :state="statisticsState"
      :selected-subject-id="selectedSubjectId"
      :detail-state="statisticsDetailState"
      :range="statisticsRange"
      :theme="theme"
      :terminal="terminal"
      :show-dock="showDock"
      @open-subject="openStatisticsSubject"
      @set-range="setStatisticsRange"
      @set-types="setStatisticsTypes"
      @back="backFromStatistics"
      @retry="statisticsState = statisticsReadState(null, statisticsReady ?? { metrics: { present: { count: 0, percent: null }, presentOrExcused: { count: 0, percent: null }, excused: { count: 0, percent: null }, absent: { count: 0, percent: null }, held: 0, planned: 0 }, ownRank: { position: null, participantCount: 0, available: false }, semesterSeries: [], subjects: [] })"
      @detail-retry="statisticsDetailState = statistics.detailState && statistics.detailState.status === 'ready' ? statisticsReadState(null, statistics.detailState.data) : { status: 'empty' }"
    />
  </div>
</template>

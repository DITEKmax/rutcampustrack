<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import type {
  TeacherApi,
  TeacherJournalQuery,
} from '../../features/teacher/teacher-client'
import TeacherExcuseScreen from '../../features/teacher/TeacherExcuseScreen.vue'
import TeacherHomeScreen from '../../features/teacher/TeacherHomeScreen.vue'
import TeacherJournalScreen from '../../features/teacher/TeacherJournalScreen.vue'
import TeacherLessonScreen from '../../features/teacher/TeacherLessonScreen.vue'
import TeacherStatsScreen from '../../features/teacher/TeacherStatsScreen.vue'
import {
  activateTeacherStatsRoute,
  clearTeacherStatsRoute,
  hasTeacherStatsRoute,
} from '../../features/teacher/teacher-stats-route'

const props = withDefaults(defineProps<{
  api: TeacherApi | null
  semesterId: number | null
  selectedDate?: string
}>(), {
  selectedDate: '',
})

const emit = defineEmits<{
  ownerError: [cause: unknown]
}>()

type Surface = 'home' | 'lesson' | 'journal' | 'excuse' | 'stats'

const surface = ref<Surface>(hasTeacherStatsRoute() ? 'stats' : 'home')
const selectedDate = ref(props.selectedDate || moscowToday())
const lessonId = ref<number | null>(null)
const journalQuery = ref<TeacherJournalQuery | null>(null)
const journalReturnSurface = ref<'home' | 'stats'>('home')
const requestId = ref<string | null>(null)

watch(
  () => [props.api, props.semesterId, props.selectedDate] as const,
  ([, , nextDate]) => {
    surface.value = hasTeacherStatsRoute() ? 'stats' : 'home'
    lessonId.value = null
    journalQuery.value = null
    journalReturnSurface.value = 'home'
    requestId.value = null
    if (nextDate) selectedDate.value = nextDate
  },
)

function moscowToday(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })
}

function openLesson(nextLessonId: number): void {
  lessonId.value = nextLessonId
  surface.value = 'lesson'
}

function openJournal(query: TeacherJournalQuery, returnSurface: 'home' | 'stats' = 'home'): void {
  journalQuery.value = query
  journalReturnSurface.value = returnSurface
  surface.value = 'journal'
}

function openStatsJournal(query: TeacherJournalQuery): void {
  openJournal(query, 'stats')
}

function openExcuse(nextRequestId: string): void {
  requestId.value = nextRequestId
  surface.value = 'excuse'
}

function openStats(): void {
  activateTeacherStatsRoute()
  surface.value = 'stats'
}

function backHome(): void {
  clearTeacherStatsRoute()
  surface.value = 'home'
  lessonId.value = null
  journalQuery.value = null
  journalReturnSurface.value = 'home'
  requestId.value = null
}

function backFromJournal(): void {
  const returnSurface = journalReturnSurface.value
  journalReturnSurface.value = 'home'
  journalQuery.value = null
  surface.value = returnSurface
  if (returnSurface === 'home') clearTeacherStatsRoute()
}

function forwardError(cause: unknown): void {
  emit('ownerError', cause)
}

onBeforeUnmount(() => {
  surface.value = 'home'
  lessonId.value = null
  journalQuery.value = null
  journalReturnSurface.value = 'home'
  requestId.value = null
})
</script>

<template>
  <TeacherHomeScreen
    v-if="surface === 'home'"
    :api="api"
    :semester-id="semesterId"
    :selected-date="selectedDate"
    @select-date="selectedDate = $event"
    @open-lesson="openLesson"
    @open-journal="openJournal"
    @open-stats="openStats"
    @error="forwardError"
  />
  <TeacherLessonScreen
    v-else-if="surface === 'lesson'"
    :api="api"
    :lesson-id="lessonId"
    @back="backHome"
    @open-excuse="openExcuse"
    @error="forwardError"
  />
  <TeacherJournalScreen
    v-else-if="surface === 'journal'"
    :api="api"
    :query="journalQuery"
    @back="backFromJournal"
    @open-excuse="openExcuse"
    @error="forwardError"
  />
  <TeacherExcuseScreen
    v-else-if="surface === 'excuse'"
    :api="api"
    :request-id="requestId"
    @back="backHome"
    @error="forwardError"
  />
  <TeacherStatsScreen
    v-else
    :api="api"
    :semester-id="semesterId"
    @back="backHome"
    @open-journal="openStatsJournal"
    @error="forwardError"
  />
</template>

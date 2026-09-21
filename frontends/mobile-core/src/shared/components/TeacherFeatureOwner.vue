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

type Surface = 'home' | 'lesson' | 'journal' | 'excuse'

const surface = ref<Surface>('home')
const selectedDate = ref(props.selectedDate || moscowToday())
const lessonId = ref<number | null>(null)
const journalQuery = ref<TeacherJournalQuery | null>(null)
const requestId = ref<string | null>(null)

watch(
  () => [props.api, props.semesterId, props.selectedDate] as const,
  ([, , nextDate]) => {
    surface.value = 'home'
    lessonId.value = null
    journalQuery.value = null
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

function openJournal(query: TeacherJournalQuery): void {
  journalQuery.value = query
  surface.value = 'journal'
}

function openExcuse(nextRequestId: string): void {
  requestId.value = nextRequestId
  surface.value = 'excuse'
}

function backHome(): void {
  surface.value = 'home'
  lessonId.value = null
  journalQuery.value = null
  requestId.value = null
}

function forwardError(cause: unknown): void {
  emit('ownerError', cause)
}

onBeforeUnmount(() => {
  surface.value = 'home'
  lessonId.value = null
  journalQuery.value = null
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
    @back="backHome"
    @open-excuse="openExcuse"
    @error="forwardError"
  />
  <TeacherExcuseScreen
    v-else
    :api="api"
    :request-id="requestId"
    @back="backHome"
    @error="forwardError"
  />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  TeacherApiError,
  type TeacherApi,
  type TeacherAssignment,
  type TeacherDayLesson,
  type TeacherJournalQuery,
} from './teacher-client'
import './teacher-screen.pcss'

const props = withDefaults(defineProps<{
  api: TeacherApi | null
  semesterId: number | null
  selectedDate?: string
}>(), {
  selectedDate: '',
})

const emit = defineEmits<{
  'select-date': [date: string]
  'open-lesson': [lessonId: number]
  'open-journal': [query: TeacherJournalQuery]
  'open-stats': []
  error: [cause: unknown]
}>()

const assignments = ref<readonly TeacherAssignment[]>([])
const lessons = ref<readonly TeacherDayLesson[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
let revision = 0

interface TeacherJournalContext {
  readonly key: string
  readonly semesterId: number
  readonly groupId: number
  readonly groupName: string
  readonly subjectId: number
  readonly subjectName: string
  readonly lessonType: string
  readonly lessons: readonly TeacherDayLesson[]
}

const journalContexts = computed<readonly TeacherJournalContext[]>(() => {
  const contexts = new Map<string, {
    semesterId: number
    groupId: number
    groupName: string
    subjectId: number
    subjectName: string
    lessonType: string
    lessons: TeacherDayLesson[]
  }>()
  for (const lesson of lessons.value) {
    if (lesson.cancelled) continue
    const key = `${lesson.semesterId}:${lesson.groupId}:${lesson.subjectId}:${lesson.lessonType.toLocaleLowerCase('ru-RU')}`
    const context = contexts.get(key) ?? {
      semesterId: lesson.semesterId,
      groupId: lesson.groupId,
      groupName: lesson.groupName,
      subjectId: lesson.subjectId,
      subjectName: lesson.subjectName,
      lessonType: lesson.lessonType,
      lessons: [],
    }
    context.lessons.push(lesson)
    contexts.set(key, context)
  }
  return [...contexts.entries()]
    .map(([key, context]) => ({ key, ...context, lessons: [...context.lessons].sort(compareLessons) }))
    .sort((left, right) => left.groupName.localeCompare(right.groupName, 'ru')
      || left.subjectName.localeCompare(right.subjectName, 'ru')
      || left.lessonType.localeCompare(right.lessonType, 'ru')
      || left.key.localeCompare(right.key))
})

watch(
  () => [props.api, props.semesterId, props.selectedDate] as const,
  () => { void load() },
  { immediate: true },
)

onBeforeUnmount(() => { revision += 1 })

async function load(): Promise<void> {
  const api = props.api
  const semesterId = props.semesterId
  const date = props.selectedDate
  const current = ++revision
  if (!api || semesterId === null || date === '') {
    assignments.value = []
    lessons.value = []
    return
  }
  loading.value = true
  error.value = null
  try {
    const [assignmentData, day] = await Promise.all([
      api.assignments(semesterId, date, date),
      api.day(semesterId, date),
    ])
    if (current !== revision) return
    assignments.value = assignmentData
    lessons.value = day.lessons
  } catch (cause) {
    if (current !== revision) return
    error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось получить расписание преподавателя.'
    emit('error', cause)
  } finally {
    if (current === revision) loading.value = false
  }
}

function formatTime(value: string | null): string {
  return value?.slice(0, 5) ?? '—'
}

function compareLessons(left: TeacherDayLesson, right: TeacherDayLesson): number {
  return left.date.localeCompare(right.date)
    || (left.startsAt ?? '').localeCompare(right.startsAt ?? '')
    || left.lessonNumber - right.lessonNumber
    || left.groupName.localeCompare(right.groupName, 'ru')
    || left.id - right.id
}

function lessonLabel(lesson: TeacherDayLesson): string {
  return `${lesson.subjectName}, ${lesson.groupName}`
}

function statusLabel(lesson: TeacherDayLesson): string {
  if (lesson.cancelled) return 'отменена'
  if (lesson.moved) return 'перенесена'
  if (lesson.oneOff) return 'разовая'
  return lesson.status === 'ACTIVE' ? 'сейчас' : lesson.status === 'CLOSED' ? 'завершена' : 'запланирована'
}

function openLesson(lesson: TeacherDayLesson): void {
  if (!lesson.cancelled) emit('open-lesson', lesson.id)
}

function openJournal(context: TeacherJournalContext): void {
  emit('open-journal', {
    semesterId: context.semesterId,
    groupId: context.groupId,
    subjectId: context.subjectId,
    lessonType: context.lessonType,
    page: 0,
    pageSize: 100,
  })
}
</script>

<template>
  <main
    class="teacher-screen"
    aria-labelledby="teacher-home-title"
    :aria-busy="loading"
  >
    <header class="teacher-screen__header">
      <div>
        <p class="teacher-screen__muted">
          Преподаватель
        </p>
        <h1 id="teacher-home-title">
          Мои пары
        </h1>
      </div>
      <span class="teacher-screen__meta">
        {{ journalContexts.length }} {{ journalContexts.length === 1 ? 'журнал' : 'журнала' }}
      </span>
      <button class="teacher-screen__secondary" type="button" @click="emit('open-stats')">
        Статистика
      </button>
    </header>

    <div class="teacher-screen__toolbar">
      <label
        class="teacher-screen__muted"
        for="teacher-day"
      >День</label>
      <input
        id="teacher-day"
        class="teacher-screen__date"
        type="date"
        :value="selectedDate"
        @input="emit('select-date', ($event.target as HTMLInputElement).value)"
      >
    </div>

    <section
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем назначения и расписание…
    </section>
    <section
      v-else-if="error"
      class="teacher-screen__state teacher-screen__state--error"
      role="alert"
    >
      {{ error }}
    </section>
    <section
      v-else-if="lessons.length === 0"
      class="teacher-screen__state"
      role="status"
    >
      На выбранный день собственных пар нет.
    </section>
    <ol
      v-else
      class="teacher-screen__list"
      aria-label="Расписание преподавателя"
    >
      <li
        v-for="lesson in lessons"
        :key="lesson.id"
        class="teacher-screen__lesson"
        :class="{ 'teacher-screen__lesson--cancelled': lesson.cancelled }"
      >
        <button
          class="teacher-screen__lesson-button"
          type="button"
          :disabled="lesson.cancelled"
          @click="openLesson(lesson)"
        >
          <span class="teacher-screen__lesson-head">
            <span class="teacher-screen__lesson-title">{{ lessonLabel(lesson) }}</span>
            <span class="teacher-screen__meta">{{ formatTime(lesson.startsAt) }}–{{ formatTime(lesson.endsAt) }}</span>
          </span>
          <span class="teacher-screen__lesson-meta">
            <span aria-hidden="true">◷ {{ lesson.lessonNumber }}</span>
            <span aria-label="Тип занятия">• {{ lesson.lessonType }}</span>
            <span aria-label="Аудитория">⌂ {{ lesson.room ?? 'аудитория не указана' }}</span>
          </span>
          <span class="teacher-screen__lesson-badges">
            <span
              v-if="lesson.cancelled"
              class="teacher-screen__badge teacher-screen__badge--cancelled"
            >{{ statusLabel(lesson) }}</span>
            <span
              v-else-if="lesson.moved"
              class="teacher-screen__badge teacher-screen__badge--moved"
            >{{ statusLabel(lesson) }}</span>
            <span
              v-else-if="lesson.oneOff"
              class="teacher-screen__badge teacher-screen__badge--oneoff"
            >{{ statusLabel(lesson) }}</span>
            <span
              v-else
              class="teacher-screen__badge"
            >{{ statusLabel(lesson) }}</span>
          </span>
        </button>
      </li>
    </ol>

    <section
      v-if="journalContexts.length > 0"
      class="teacher-screen__card"
      aria-labelledby="teacher-journal-context-title"
    >
      <div class="teacher-screen__lesson">
        <h2 id="teacher-journal-context-title">
          Журналы по группе, предмету и типу
        </h2>
        <p class="teacher-screen__muted">
          Выбери один контекст: занятия разных групп не смешиваются в одном журнале.
        </p>
        <ul class="teacher-screen__list">
          <li
            v-for="context in journalContexts"
            :key="context.key"
          >
            <button
              class="teacher-screen__secondary teacher-screen__journal-context"
              type="button"
              @click="openJournal(context)"
            >
              <strong>{{ context.groupName }} · {{ context.subjectName }}</strong>
              <span>{{ context.lessonType }} · {{ context.lessons.length }} занятий</span>
            </button>
          </li>
        </ul>
      </div>
    </section>

    <section
      v-if="assignments.length > 0"
      class="teacher-screen__card"
      aria-labelledby="teacher-assignment-title"
    >
      <div class="teacher-screen__lesson">
        <h2 id="teacher-assignment-title">
          Назначения на этот день
        </h2>
        <p class="teacher-screen__muted">
          Группа, предмет и тип занятия остаются отдельными назначениями.
        </p>
        <ul class="teacher-screen__list">
          <li
            v-for="assignment in assignments"
            :key="assignment.id"
            class="teacher-screen__lesson-meta"
          >
            <span>{{ assignment.groupName }}</span>
            <span>{{ assignment.subjectName }}</span>
            <span>{{ assignment.lessonType }}</span>
          </li>
        </ul>
      </div>
    </section>
  </main>
</template>

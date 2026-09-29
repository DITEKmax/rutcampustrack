<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { moscowDate } from '../../domain/homework'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  HeadmanJournalApiError,
  type HeadmanJournalApi,
  type HeadmanJournalLesson,
} from '../headman-journal/headman-journal-client'
import './headman-home-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanJournalApi | null
  groupId: number | null
  selectedDate?: string
  offline?: boolean
}>(), {
  selectedDate: '',
  offline: false,
})

const emit = defineEmits<{
  openLesson: [lesson: HeadmanJournalLesson]
  selectDate: [date: string]
  error: [cause: unknown]
}>()

function todayIso(): string {
  const date = moscowDate(new Date().toISOString())
  return date ?? new Date().toISOString().slice(0, 10)
}

const selectedDate = ref(props.selectedDate || todayIso())
const lessons = ref<readonly HeadmanJournalLesson[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
let loadRevision = 0
let disposed = false

function dateLabel(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('ru-RU', { dateStyle: 'full', timeZone: 'Europe/Moscow' }).format(date)
}

function lessonStatus(status: HeadmanJournalLesson['status']): string {
  switch (status) {
    case 'PLANNED': return 'Запланирована'
    case 'ACTIVE': return 'Идёт'
    case 'CLOSED': return 'Завершена'
    case 'CANCELLED': return 'Отменена'
    case 'TRANSFERRED': return 'Перенесена'
    case 'UNSUPPORTED': return 'Статус недоступен'
  }
}

function lessonTimeRange(start: string | null, end: string | null): string {
  if (start && end) return `${start.slice(0, 5)}–${end.slice(0, 5)}`
  if (start) return `С ${start.slice(0, 5)}`
  if (end) return `До ${end.slice(0, 5)}`
  return 'Время не указано'
}

function selectDate(value: string): void {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value === selectedDate.value) return
  selectedDate.value = value
  emit('selectDate', value)
}

function shiftDate(delta: number): void {
  const date = new Date(`${selectedDate.value}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + delta)
  selectDate(date.toISOString().slice(0, 10))
}

async function load(): Promise<void> {
  const revision = ++loadRevision
  const api = props.api
  const groupId = props.groupId
  const date = selectedDate.value
  error.value = null
  lessons.value = []
  if (props.offline || !api || groupId === null) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await api.listLessons(groupId, date, date)
    if (disposed || revision !== loadRevision || api !== props.api || groupId !== props.groupId) return
    lessons.value = result.slice().sort((left, right) =>
      (left.lessonNumber ?? Number.MAX_SAFE_INTEGER) - (right.lessonNumber ?? Number.MAX_SAFE_INTEGER))
  } catch (cause) {
    if (disposed || revision !== loadRevision || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof HeadmanJournalApiError && (cause.response.status === 401 || cause.response.status === 403)
      ? 'Нет доступа к расписанию этой группы.'
      : cause instanceof Error ? cause.message : 'Не удалось загрузить занятия.'
    emit('error', cause)
  } finally {
    if (!disposed && revision === loadRevision) loading.value = false
  }
}

watch(
  () => [props.api, props.groupId, props.offline, selectedDate.value] as const,
  () => { void load() },
  { immediate: true },
)

watch(() => props.selectedDate, (value) => {
  if (value && value !== selectedDate.value) selectedDate.value = value
})

onBeforeUnmount(() => {
  disposed = true
  loadRevision += 1
})
</script>

<template>
  <main
    class="headman-home"
    aria-labelledby="headman-home-title"
    :aria-busy="loading"
  >
    <header class="headman-home__header">
      <div>
        <p class="headman-home__eyebrow">
          Староста · группа
        </p>
        <h1 id="headman-home-title">
          Сегодня
        </h1>
        <p class="headman-home__date">
          {{ dateLabel(selectedDate) }}
        </p>
      </div>
      <div
        class="headman-home__date-controls"
        aria-label="Выбор даты"
      >
        <button
          type="button"
          aria-label="Предыдущий день"
          @click="shiftDate(-1)"
        >
          Назад
        </button>
        <input
          :value="selectedDate"
          aria-label="Выбранная дата"
          type="date"
          @change="selectDate(($event.target as HTMLInputElement).value)"
        >
        <button
          type="button"
          aria-label="Следующий день"
          @click="shiftDate(1)"
        >
          Далее
        </button>
      </div>
    </header>

    <p
      v-if="offline"
      class="headman-home__state"
      role="status"
    >
      Расписание этой группы доступно при подключении к интернету.
    </p>
    <p
      v-else-if="groupId === null"
      class="headman-home__state headman-home__state--error"
      role="alert"
    >
      Не удалось определить группу старосты.
    </p>
    <p
      v-else-if="!api"
      class="headman-home__state headman-home__state--error"
      role="alert"
    >
      Расписание недоступно: источник данных не подключён.
    </p>
    <p
      v-else-if="loading"
      class="headman-home__state"
      role="status"
    >
      Загружаем занятия…
    </p>
    <p
      v-else-if="error"
      class="headman-home__state headman-home__state--error"
      role="alert"
    >
      {{ error }}
      <button
        type="button"
        @click="load"
      >
        Повторить
      </button>
    </p>
    <section
      v-else
      aria-labelledby="headman-home-lessons-title"
    >
      <h2
        id="headman-home-lessons-title"
        class="headman-home__section-title"
      >
        Занятия группы
      </h2>
      <p
        v-if="lessons.length === 0"
        class="headman-home__empty"
      >
        На эту дату сервер не вернул занятий.
      </p>
      <ul
        v-else
        class="headman-home__lessons"
      >
        <li
          v-for="lesson in lessons"
          :key="lesson.id"
        >
          <button
            class="headman-home__lesson"
            type="button"
            @click="emit('openLesson', lesson)"
          >
            <span class="headman-home__lesson-time">{{ lessonTimeRange(lesson.startTime, lesson.endTime) }}</span>
            <span class="headman-home__lesson-copy">
              <strong>Пара {{ lesson.lessonNumber ?? '—' }}<span v-if="lesson.room"> · {{ lesson.room }}</span></strong>
              <span>{{ lessonStatus(lesson.status) }}</span>
            </span>
            <span class="headman-home__lesson-action">Открыть журнал</span>
          </button>
        </li>
      </ul>
    </section>
  </main>
</template>

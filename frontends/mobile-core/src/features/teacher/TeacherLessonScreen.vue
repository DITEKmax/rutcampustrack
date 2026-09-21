<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import {
  TeacherApiError,
  type TeacherApi,
  type TeacherLessonResponse,
  type TeacherRosterEntry,
} from './teacher-client'
import './teacher-screen.pcss'

const props = defineProps<{
  api: TeacherApi | null
  lessonId: number | null
}>()

const emit = defineEmits<{
  back: []
  'open-excuse': [requestId: string]
  error: [cause: unknown]
}>()

const state = ref<TeacherLessonResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
let revision = 0

watch(
  () => [props.api, props.lessonId] as const,
  () => { void load() },
  { immediate: true },
)

onBeforeUnmount(() => { revision += 1 })

async function load(): Promise<void> {
  const api = props.api
  const lessonId = props.lessonId
  const current = ++revision
  if (!api || lessonId === null) {
    state.value = null
    return
  }
  loading.value = true
  error.value = null
  try {
    const value = await api.lesson(lessonId)
    if (current !== revision) return
    state.value = value
  } catch (cause) {
    if (current !== revision) return
    error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось открыть пару.'
    emit('error', cause)
  } finally {
    if (current === revision) loading.value = false
  }
}

function statusClass(entry: TeacherRosterEntry): string {
  if (entry.pendingTicket) return 'teacher-screen__status--pending'
  if (!entry.recordPresent || entry.autoAbsent) return 'teacher-screen__status--auto'
  if (entry.status === 'present') return 'teacher-screen__status--present'
  if (entry.status === 'excused') return 'teacher-screen__status--excused'
  if (entry.status === 'absent') return 'teacher-screen__status--absent'
  return ''
}

function statusLabel(entry: TeacherRosterEntry): string {
  let label: string
  if (!entry.recordPresent) label = 'нет записи'
  else if (entry.autoAbsent) label = 'автоматически отмечено как отсутствующий'
  else if (entry.source === 'NO_TICKET') label = 'уважительная причина без заявления'
  else if (entry.status === 'present') label = 'присутствовал'
  else if (entry.status === 'excused') label = 'уважительная причина'
  else if (entry.status === 'absent') label = 'отсутствовал'
  else label = entry.status ?? 'нет данных'
  return entry.pendingTicket ? `${label}; заявление ожидает решения` : label
}

function displaySymbol(entry: TeacherRosterEntry): string {
  if (!entry.recordPresent) return '·'
  return entry.symbol ?? '·'
}

function canOpenExcuse(entry: TeacherRosterEntry): boolean {
  return entry.ticketId !== null && (entry.pendingTicket || entry.status === 'excused' || entry.symbol === 'у')
}

function openExcuse(entry: TeacherRosterEntry): void {
  if (canOpenExcuse(entry) && entry.ticketId !== null) emit('open-excuse', entry.ticketId)
}

function formatTime(value: string | null): string {
  return value?.slice(0, 5) ?? '—'
}
</script>

<template>
  <main
    class="teacher-screen"
    aria-labelledby="teacher-lesson-title"
    :aria-busy="loading"
  >
    <button
      class="teacher-screen__back"
      type="button"
      @click="emit('back')"
    >
      ← Назад
    </button>
    <section
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем roster пары…
    </section>
    <section
      v-else-if="error"
      class="teacher-screen__state teacher-screen__state--error"
      role="alert"
    >
      {{ error }}
    </section>
    <template v-else-if="state">
      <header class="teacher-screen__header">
        <div>
          <p class="teacher-screen__muted">
            {{ state.lesson.groupName }}
          </p>
          <h1 id="teacher-lesson-title">
            {{ state.lesson.subjectName }}
          </h1>
          <p class="teacher-screen__meta">
            {{ state.lesson.lessonType }} · {{ formatTime(state.lesson.startsAt) }}–{{ formatTime(state.lesson.endsAt) }} · {{ state.lesson.room ?? 'аудитория не указана' }}
          </p>
        </div>
        <span
          v-if="state.lesson.cancelled"
          class="teacher-screen__badge teacher-screen__badge--cancelled"
        >отменена</span>
      </header>

      <section
        class="teacher-screen__lesson"
        aria-labelledby="teacher-roster-title"
      >
        <div class="teacher-screen__lesson-head">
          <h2 id="teacher-roster-title">
            Состав группы
          </h2>
          <span class="teacher-screen__muted">{{ state.roster.length }} студентов</span>
        </div>
        <ul class="teacher-screen__roster">
          <li
            v-for="entry in state.roster"
            :key="entry.studentId"
            class="teacher-screen__roster-row"
          >
            <span class="teacher-screen__student">
              <strong>{{ entry.displayName }}</strong>
              <small class="teacher-screen__cell-note">{{ statusLabel(entry) }}</small>
            </span>
            <span class="teacher-screen__lesson-meta">
              <span
                class="teacher-screen__status"
                :class="statusClass(entry)"
                :title="statusLabel(entry)"
              >{{ displaySymbol(entry) }}</span>
              <button
                v-if="canOpenExcuse(entry)"
                class="teacher-screen__disclosure"
                type="button"
                aria-label="Открыть заявление об уважительной причине"
                @click="openExcuse(entry)"
              >
                у
              </button>
            </span>
          </li>
        </ul>
      </section>
    </template>
    <section
      v-else
      class="teacher-screen__state"
      role="status"
    >
      Выбери конкретную пару.
    </section>
  </main>
</template>

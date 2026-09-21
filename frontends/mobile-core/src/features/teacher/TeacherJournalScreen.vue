<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  TeacherApiError,
  type TeacherApi,
  type TeacherDayLesson,
  type TeacherJournalCell,
  type TeacherJournalQuery,
  type TeacherJournalResponse,
} from './teacher-client'
import './teacher-screen.pcss'

const props = defineProps<{
  api: TeacherApi | null
  query: TeacherJournalQuery | null
}>()

const emit = defineEmits<{
  back: []
  'open-excuse': [requestId: string]
  error: [cause: unknown]
}>()

const state = ref<TeacherJournalResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const page = ref(0)
let revision = 0

const orderedLessons = computed(() => state.value?.lessons ?? [])

watch(
  () => [props.api, queryKey(props.query)] as const,
  () => {
    page.value = props.query?.page ?? 0
    void load()
  },
  { immediate: true },
)

onBeforeUnmount(() => { revision += 1 })

async function load(): Promise<void> {
  const api = props.api
  const query = props.query
  const current = ++revision
  if (!api || !query) {
    state.value = null
    return
  }
  loading.value = true
  error.value = null
  try {
    const value = await api.journal({
      ...query,
      page: page.value,
      pageSize: query.pageSize ?? 100,
    })
    if (current !== revision) return
    state.value = value
  } catch (cause) {
    if (current !== revision) return
    error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось открыть журнал.'
    emit('error', cause)
  } finally {
    if (current === revision) loading.value = false
  }
}

function queryKey(query: TeacherJournalQuery | null): string {
  if (!query) return ''
  return [query.semesterId, query.groupId, query.subjectId, query.lessonType].join(':')
}

function changePage(delta: number): void {
  const current = state.value
  if (loading.value || !current) return
  const next = current.page + delta
  if (next < 0 || (delta > 0 && !current.hasMore)) return
  page.value = next
  void load()
}

function cellFor(studentId: number, lessonId: number): TeacherJournalCell | null {
  const student = state.value?.students.find((item) => item.studentId === studentId)
  return student?.cells.find((cell) => cell.lessonId === lessonId) ?? null
}

function cellLabel(cell: TeacherJournalCell | null): string {
  if (!cell) return 'нет строки roster для этой пары'
  let label: string
  if (!cell.recordPresent) label = 'нет записи'
  else if (cell.autoAbsent) label = 'автоматически отмечено как отсутствующий'
  else if (cell.source === 'NO_TICKET') label = 'уважительная причина без заявления'
  else label = cell.status ?? 'нет данных'
  return cell.pendingTicket ? `${label}; заявление ожидает решения` : label
}

function cellSymbol(cell: TeacherJournalCell | null): string {
  if (!cell) return '—'
  if (!cell.recordPresent) return '·'
  return cell.symbol ?? '·'
}

function cellClass(cell: TeacherJournalCell | null): string {
  if (!cell) return 'teacher-screen__grid-cell--auto'
  if (cell.pendingTicket) return 'teacher-screen__grid-cell--pending'
  if (!cell.recordPresent || cell.autoAbsent) return 'teacher-screen__grid-cell--auto'
  return ''
}

function openExcuse(cell: TeacherJournalCell | null): void {
  if (cell?.ticketId) emit('open-excuse', cell.ticketId)
}

function canOpenExcuse(cell: TeacherJournalCell | null): boolean {
  return Boolean(cell?.ticketId && (cell.pendingTicket || cell.status === 'excused' || cell.symbol === 'у'))
}

function shortLesson(lesson: TeacherDayLesson): string {
  return `${lesson.date.slice(5)} · ${lesson.lessonType}`
}
</script>

<template>
  <main
    class="teacher-screen"
    aria-labelledby="teacher-journal-title"
    :aria-busy="loading"
  >
    <button
      class="teacher-screen__back"
      type="button"
      @click="emit('back')"
    >
      ← Назад
    </button>
    <header class="teacher-screen__header">
      <div>
        <p class="teacher-screen__muted">
          Конкретные занятия
        </p>
        <h1 id="teacher-journal-title">
          Журнал группы
        </h1>
      </div>
    </header>
    <section
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем журнал…
    </section>
    <section
      v-else-if="error"
      class="teacher-screen__state teacher-screen__state--error"
      role="alert"
    >
      {{ error }}
    </section>
    <section
      v-else-if="!state || state.students.length === 0"
      class="teacher-screen__state"
      role="status"
    >
      Для выбранных занятий строк журнала нет.
    </section>
    <section
      v-else
      class="teacher-screen__grid-wrap"
      aria-label="Журнал посещаемости"
    >
      <table class="teacher-screen__grid">
        <thead>
          <tr>
            <th scope="col">
              Студент
            </th>
            <th
              v-for="lesson in orderedLessons"
              :key="lesson.id"
              scope="col"
            >
              {{ lesson.groupName }}<br>{{ shortLesson(lesson) }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="student in state.students"
            :key="student.studentId"
          >
            <th scope="row">
              {{ student.displayName }}
            </th>
            <td
              v-for="lesson in orderedLessons"
              :key="lesson.id"
            >
              <button
                class="teacher-screen__grid-cell"
                :class="cellClass(cellFor(student.studentId, lesson.id))"
                :title="cellLabel(cellFor(student.studentId, lesson.id))"
                :disabled="!canOpenExcuse(cellFor(student.studentId, lesson.id))"
                type="button"
                @click="openExcuse(cellFor(student.studentId, lesson.id))"
              >
                {{ cellSymbol(cellFor(student.studentId, lesson.id)) }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <nav
        v-if="state.page > 0 || state.hasMore"
        class="teacher-screen__pagination"
        aria-label="Страницы журнала"
      >
        <button
          class="teacher-screen__secondary"
          type="button"
          :disabled="loading || state.page === 0"
          @click="changePage(-1)"
        >
          ← Ранее
        </button>
        <span class="teacher-screen__muted">Страница {{ state.page + 1 }}</span>
        <button
          class="teacher-screen__secondary"
          type="button"
          :disabled="loading || !state.hasMore"
          @click="changePage(1)"
        >
          Позже →
        </button>
      </nav>
    </section>
  </main>
</template>

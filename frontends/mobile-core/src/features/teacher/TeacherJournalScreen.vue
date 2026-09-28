<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import {
  TeacherApiError,
  teacherJournalTypes,
  type TeacherApi,
  type TeacherDayLesson,
  type TeacherExportFormat,
  type TeacherExportFormatCode,
  type TeacherJournalCell,
  type TeacherJournalQuery,
  type TeacherJournalResponse,
  toTeacherJournalReportRequest,
} from './teacher-client'
import './teacher-screen.pcss'

const props = defineProps<{
  api: TeacherApi | null
  query: TeacherJournalQuery | null
  selectedLessonId?: number | null
  reportDownload?: ReportDownloadPort | null
}>()

const emit = defineEmits<{
  back: []
  'open-lesson': [lessonId: number, page: number]
  'open-excuse': [requestId: string]
  error: [cause: unknown]
}>()

const state = ref<TeacherJournalResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const formats = ref<readonly TeacherExportFormat[]>([])
const selectedFormat = ref<TeacherExportFormatCode>('docx')
const formatsLoading = ref(false)
const exportLoading = ref(false)
const exportError = ref<string | null>(null)
const exportStatus = ref<string | null>(null)
const page = ref(0)
let revision = 0
let exportRevision = 0
let formatsRevision = 0
const exportObjectUrls = new Set<string>()

const orderedLessons = computed(() => state.value?.lessons ?? [])
const selectedLesson = computed(() => orderedLessons.value.find((lesson) => lesson.id === props.selectedLessonId) ?? null)

watch(
  () => [props.api, queryKey(props.query)] as const,
  () => {
    exportRevision += 1
    formatsRevision += 1
    page.value = props.query?.page ?? 0
    state.value = null
    formats.value = []
    error.value = null
    exportError.value = null
    exportStatus.value = null
    exportLoading.value = false
    void load()
    void loadFormats()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  revision += 1
  exportRevision += 1
  formatsRevision += 1
  for (const url of exportObjectUrls) URL.revokeObjectURL(url)
  exportObjectUrls.clear()
})

async function load(): Promise<void> {
  const api = props.api
  const query = props.query
  const current = ++revision
  if (!api || !query) {
    state.value = null
    loading.value = false
    error.value = null
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

async function loadFormats(): Promise<void> {
  const api = props.api
  const current = ++formatsRevision
  if (!api || !props.query) {
    formats.value = []
    formatsLoading.value = false
    return
  }
  formatsLoading.value = true
  try {
    const value = await api.journalExportFormats()
    if (current !== formatsRevision || api !== props.api || !props.query) return
    formats.value = value
    if (!value.some((format) => format.code === selectedFormat.value)) {
      selectedFormat.value = value[0]?.code ?? 'docx'
    }
  } catch (cause) {
    if (current !== formatsRevision) return
    exportError.value = cause instanceof TeacherApiError
      ? cause.message
      : 'Не удалось получить список форматов выгрузки.'
    emit('error', cause)
  } finally {
    if (current === formatsRevision) formatsLoading.value = false
  }
}

async function exportJournal(): Promise<void> {
  const api = props.api
  const reportDownload = props.reportDownload ?? null
  const query = props.query
  if (!api || !query || loading.value || exportLoading.value || !canExport.value) return
  const current = ++exportRevision
  const context = queryKey(query)
  const format = selectedFormat.value
  exportLoading.value = true
  exportError.value = null
  exportStatus.value = null
  try {
    if (reportDownload) {
      const result = await reportDownload.download(toTeacherJournalReportRequest(query, format), () => current === exportRevision
        && api === props.api && reportDownload === (props.reportDownload ?? null) && queryKey(props.query) === context
        && selectedFormat.value === format)
      if (result === 'stale' || current !== exportRevision || api !== props.api
        || reportDownload !== (props.reportDownload ?? null) || queryKey(props.query) !== context) return
      if (result === 'unsupported') {
        exportError.value = 'Скачивание файлов недоступно в этой версии Telegram. Обнови Telegram до версии 8.0 или новее.'
      } else {
        exportStatus.value = result === 'accepted'
          ? 'Telegram принял запрос на скачивание; проверь завершение в Telegram.'
          : 'Скачивание отменено.'
      }
      return
    }

    const file = await api.exportJournal({
      semesterId: query.semesterId,
      groupId: query.groupId,
      subjectId: query.subjectId,
      lessonTypes: teacherJournalTypes(query),
      ...(query.dateFrom !== undefined ? { dateFrom: query.dateFrom } : {}),
      ...(query.dateTo !== undefined ? { dateTo: query.dateTo } : {}),
      format,
    })
    if (current !== exportRevision || api !== props.api || queryKey(props.query) !== context) return
    const url = URL.createObjectURL(file.blob)
    exportObjectUrls.add(url)
    const link = document.createElement('a')
    link.href = url
    link.download = file.filename
    link.click()
    window.setTimeout(() => {
      exportObjectUrls.delete(url)
      URL.revokeObjectURL(url)
    }, 60_000)
  } catch (cause) {
    if (current !== exportRevision || cause instanceof StaleSessionGenerationError) return
    exportError.value = cause instanceof TeacherApiError
      ? cause.message
      : cause instanceof Error ? cause.message : 'Не удалось скачать журнал. Попробуй ещё раз.'
    emit('error', cause)
  } finally {
    if (current === exportRevision) exportLoading.value = false
  }
}

const canExport = computed(() => Boolean(
  props.api && props.query && state.value && state.value.totalLessons > 0
  && !loading.value && !formatsLoading.value && !exportLoading.value
  && formats.value.some((format) => format.code === selectedFormat.value),
))

function queryKey(query: TeacherJournalQuery | null): string {
  if (!query) return ''
  return [query.semesterId, query.groupId, query.subjectId, teacherJournalTypes(query).slice().sort().join(','), query.dateFrom ?? '', query.dateTo ?? ''].join(':')
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

function cellStatusClass(cell: TeacherJournalCell | null): string {
  if (!cell) return 'teacher-screen__status--auto'
  if (cell.pendingTicket) return 'teacher-screen__status--pending'
  if (cell.autoAbsent || !cell.recordPresent) return 'teacher-screen__status--auto'
  if (cell.status === 'excused' || cell.symbol === 'у') return 'teacher-screen__status--excused'
  if (cell.status === 'absent' || cell.symbol === 'н') return 'teacher-screen__status--absent'
  if (cell.status === 'present' || cell.symbol === 'б') return 'teacher-screen__status--present'
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
    :aria-busy="loading || exportLoading"
  >
    <button
      class="teacher-screen__back"
      type="button"
      @click="emit('back')"
    >
      {{ props.selectedLessonId != null ? '← К занятиям' : '← Назад' }}
    </button>
    <header class="teacher-screen__header">
      <div>
        <p class="teacher-screen__muted">
          {{ props.selectedLessonId ? 'Студенты и отметки' : 'Конкретные занятия' }}
        </p>
        <h1 id="teacher-journal-title">
          {{ props.selectedLessonId ? 'Посещаемость пары' : 'Журнал группы' }}
        </h1>
      </div>
    </header>
    <section
      v-if="props.query"
      class="teacher-screen__export"
      aria-label="Выгрузка журнала"
    >
      <label
        class="teacher-screen__export-field"
        for="teacher-journal-export-format"
      >
        <span class="teacher-screen__muted">Формат файла</span>
        <select
          id="teacher-journal-export-format"
          v-model="selectedFormat"
          class="teacher-screen__date"
          :disabled="loading || formatsLoading || exportLoading || formats.length === 0"
        >
          <option
            v-for="format in formats"
            :key="format.code"
            :value="format.code"
          >
            {{ format.label }}
          </option>
        </select>
      </label>
      <button
        class="teacher-screen__primary teacher-screen__export-button"
        type="button"
        :disabled="!canExport"
        @click="exportJournal"
      >
        {{ exportLoading ? 'Готовим файл…' : 'Скачать журнал' }}
      </button>
      <p class="teacher-screen__muted teacher-screen__export-message">
        Выгрузка включает весь выбранный период и все типы занятий; у ещё не начавшихся пар отметок нет.
      </p>
      <p
        v-if="exportLoading"
        class="teacher-screen__muted teacher-screen__export-message"
        role="status"
      >
        Собираем полный журнал на сервере…
      </p>
      <p
        v-else-if="exportError"
        class="teacher-screen__state teacher-screen__state--error teacher-screen__export-message"
        role="alert"
      >
        {{ exportError }}
      </p>
      <p
        v-else-if="exportStatus"
        class="teacher-screen__muted teacher-screen__export-message"
        role="status"
      >
        {{ exportStatus }}
      </p>
      <p
        v-else-if="state && state.totalLessons === 0 && !loading"
        class="teacher-screen__muted teacher-screen__export-message"
        role="status"
      >
        В выбранном контексте нет занятий для выгрузки.
      </p>
    </section>
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
      v-else-if="!state || state.lessons.length === 0"
      class="teacher-screen__state"
      role="status"
    >
      Для выбранного периода занятий нет.
    </section>
    <section
      v-else-if="props.selectedLessonId !== undefined && props.selectedLessonId !== null"
      class="teacher-screen__lesson-detail"
    >
      <p
        v-if="!selectedLesson"
        class="teacher-screen__state"
        role="status"
      >
        Этой пары нет на текущей странице журнала.
      </p>
      <template v-else>
        <article class="teacher-screen__lesson">
          <strong class="teacher-screen__lesson-title">{{ shortLesson(selectedLesson) }}</strong>
          <span class="teacher-screen__lesson-meta">{{ selectedLesson.groupName }} · {{ selectedLesson.subjectName }}</span>
          <span class="teacher-screen__lesson-meta">Занятие {{ selectedLesson.lessonNumber }}<template v-if="selectedLesson.room"> · {{ selectedLesson.room }}</template></span>
        </article>
        <ul
          v-if="state.students.length > 0"
          class="teacher-screen__roster"
          aria-label="Студенты и посещаемость"
        >
          <li
            v-for="student in state.students"
            :key="student.studentId"
            class="teacher-screen__roster-row teacher-journal__student-row"
          >
            <div class="teacher-journal__student-info">
              <strong class="teacher-screen__student">{{ student.displayName }}</strong>
              <span class="teacher-screen__muted">{{ cellLabel(cellFor(student.studentId, selectedLesson.id)) }}</span>
              <span
                v-if="cellFor(student.studentId, selectedLesson.id)?.excuseReason"
                class="teacher-journal__reason"
              >
                Уважительная причина: {{ cellFor(student.studentId, selectedLesson.id)?.excuseReason }}
              </span>
            </div>
            <button
              class="teacher-screen__status"
              :class="cellStatusClass(cellFor(student.studentId, selectedLesson.id))"
              type="button"
              :disabled="!canOpenExcuse(cellFor(student.studentId, selectedLesson.id))"
              :aria-label="`${student.displayName}: ${cellLabel(cellFor(student.studentId, selectedLesson.id))}`"
              @click="openExcuse(cellFor(student.studentId, selectedLesson.id))"
            >
              {{ cellSymbol(cellFor(student.studentId, selectedLesson.id)) }}
            </button>
          </li>
        </ul>
        <p
          v-else
          class="teacher-screen__state"
          role="status"
        >
          В списке группы нет студентов.
        </p>
      </template>
    </section>
    <section
      v-else
      class="teacher-screen__lesson-list"
      aria-label="Занятия группы"
    >
      <button
        v-for="lesson in orderedLessons"
        :key="lesson.id"
        class="teacher-screen__lesson teacher-screen__lesson-button teacher-journal__lesson"
        type="button"
        @click="emit('open-lesson', lesson.id, state.page)"
      >
        <span class="teacher-screen__lesson-title">{{ shortLesson(lesson) }} · {{ lesson.lessonNumber }}-я пара</span>
        <span class="teacher-screen__lesson-meta">{{ lesson.groupName }} · {{ lesson.subjectName }}</span>
        <span class="teacher-screen__lesson-meta">
          {{ lesson.room ?? 'Аудитория не указана' }} · {{ lesson.cancelled ? 'Отменена' : lesson.status === 'CLOSED' ? 'Завершена' : lesson.status === 'PLANNED' ? 'Запланирована' : 'Проведена' }}
        </span>
      </button>
      <p
        v-if="state.lessons.length === 0"
        class="teacher-screen__state"
        role="status"
      >
        Занятий на этой странице нет.
      </p>
    </section>
    <nav
      v-if="props.selectedLessonId == null && state && (state.page > 0 || state.hasMore)"
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
      <span class="teacher-screen__muted">{{ state.totalLessons }} занятий · страница {{ state.page + 1 }}</span>
      <button
        class="teacher-screen__secondary"
        type="button"
        :disabled="loading || !state.hasMore"
        @click="changePage(1)"
      >
        Позже →
      </button>
    </nav>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import HeadmanStatsSelect from '../headman-stats/HeadmanStatsSelect.vue'
import TeacherLessonCard from './TeacherLessonCard.vue'
import TeacherRoster from './TeacherRoster.vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import {
  TeacherApiError,
  teacherJournalTypes,
  type TeacherApi,

  type TeacherExportFormat,
  type TeacherExportFormatCode,

  type TeacherJournalQuery,
  type TeacherJournalResponse,
  toTeacherJournalReportRequest,
} from './teacher-client'
import './teacher-screen.pcss'

const props = withDefaults(defineProps<{
  api: TeacherApi | null
  query: TeacherJournalQuery | null
  selectedLessonId?: number | null
  reportDownload?: ReportDownloadPort | null
  showBack?: boolean
  embedded?: boolean
  showExport?: boolean
}>(), { showBack: true, embedded: false, showExport: true, selectedLessonId: null, reportDownload: null })

const emit = defineEmits<{
  back: []
  'open-lesson': [lessonId: number, page: number]
  'open-excuse': [requestId: string]
  'change-page': [page: number]
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
  [() => props.api, () => queryKey(props.query)],
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
    page.value = value.page
    emit('change-page',value.page)
  } catch (cause) {
    if (current !== revision || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось открыть журнал.'
    emit('error', cause)
  } finally {
    if (current === revision) loading.value = false
  }
}

async function loadFormats(): Promise<void> {
  const api = props.api
  const current = ++formatsRevision
  if (!api || !props.query || !props.showExport) {
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

const rosterRows = computed(() => state.value?.students.map(student => ({ studentId: student.studentId, displayName: student.displayName, attendance: student.cells.find(cell => cell.lessonId === selectedLesson.value?.id) ?? null })) ?? [])
watch(() => [selectedFormat.value,props.reportDownload] as const, () => { exportRevision += 1; exportLoading.value = false; exportError.value = null; exportStatus.value = null })
function changeFormat(value: string): void { if (formats.value.some(format => format.code === value)) selectedFormat.value = value as TeacherExportFormatCode }
</script>

<template>
  <component
    :is="embedded ? 'section' : 'main'"
    :class="embedded ? 'teacher-journal-embedded' : 'teacher-screen'"
    :aria-labelledby="embedded ? undefined : 'teacher-journal-title'"
    :aria-label="embedded ? 'Занятия группы' : undefined"
    :aria-busy="loading || exportLoading"
  >
    <header
      v-if="!embedded"
      class="teacher-screen__detail-header"
    >
      <button
        v-if="showBack"
        class="teacher-screen__icon-button"
        type="button"
        aria-label="Назад"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button><h1 id="teacher-journal-title">
        {{ selectedLessonId != null ? 'Пара' : 'Журнал группы' }}
      </h1>
    </header>
    <section
      v-if="showExport && query && !loading && !error && state && state.totalLessons > 0"
      class="teacher-screen__export"
      aria-label="Выгрузка журнала"
    >
      <div
        class="teacher-screen__export-field"
        data-teacher-action="export-select"
      >
        <span class="teacher-screen__muted">Формат файла</span><HeadmanStatsSelect
          label="Формат журнала"
          :model-value="selectedFormat"
          :options="formats.map(format => ({value: format.code,label: format.label}))"
          :disabled="formatsLoading || exportLoading || !formats.length"
          @update:model-value="changeFormat"
        />
      </div>
      <button
        class="teacher-screen__icon-button"
        type="button"
        data-teacher-action="export-download"
        aria-label="Скачать журнал за выбранный период"
        :disabled="!canExport"
        @click="exportJournal"
      >
        <MobileIcon name="download" />
      </button>
      <p
        v-if="exportLoading"
        class="teacher-screen__muted teacher-screen__export-message"
        role="status"
      >
        Готовим журнал…
      </p>
      <StudentWarningBlock
        v-else-if="exportError"
        class="teacher-screen__export-message"
        title="Не удалось скачать журнал"
        :message="exportError"
        severity="error"
      />
      <p
        v-else-if="exportStatus"
        class="teacher-screen__state teacher-screen__state--success teacher-screen__export-message"
        role="status"
      >
        {{ exportStatus }}
      </p>
    </section>
    <p
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем занятия…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Журнал недоступен"
      :message="error"
      severity="error"
      action-label="Повторить"
      @action="load"
    />
    <p
      v-else-if="!state || !state.lessons.length"
      class="teacher-screen__state"
      role="status"
    >
      В выбранном периоде пар нет.
    </p>
    <section
      v-else-if="selectedLessonId != null"
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
        <TeacherLessonCard
          :lesson="selectedLesson"
          show-date
          :interactive="false"
        /><h2 class="teacher-screen__section-title">
          Посещаемость
        </h2><TeacherRoster
          :rows="rosterRows"
          @open-excuse="emit('open-excuse',$event)"
        />
      </template>
    </section>
    <ol
      v-else
      class="teacher-screen__list"
      aria-label="Занятия группы"
    >
      <li
        v-for="lesson in orderedLessons"
        :key="lesson.id"
      >
        <TeacherLessonCard
          :lesson="lesson"
          show-date
          :compact="embedded"
          @open="emit('open-lesson',$event,state.page)"
        />
      </li>
    </ol>
    <nav
      v-if="selectedLessonId == null && !loading && !error && state && state.lessons.length && (state.page > 0 || state.hasMore)"
      class="teacher-screen__pagination"
      aria-label="Страницы занятий"
    >
      <button
        class="teacher-screen__icon-button"
        type="button"
        aria-label="Предыдущая страница"
        :disabled="state.page === 0"
        @click="changePage(-1)"
      >
        <MobileIcon name="back" />
      </button><span class="teacher-screen__muted">Страница {{ state.page + 1 }}</span><button
        class="teacher-screen__icon-button"
        type="button"
        aria-label="Следующая страница"
        :disabled="!state.hasMore"
        @click="changePage(1)"
      >
        <MobileIcon
          name="back"
          class="teacher-screen__next"
        />
      </button>
    </nav>
  </component>
</template>

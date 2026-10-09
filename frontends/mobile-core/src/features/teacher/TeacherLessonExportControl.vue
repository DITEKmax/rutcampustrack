<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import { TeacherApi, toTeacherJournalReportRequest, type TeacherDayLesson, type TeacherExportFormat, type TeacherExportFormatCode } from './teacher-client'
import './teacher-lesson-export-control.pcss'

const props = withDefaults(defineProps<{ api: TeacherApi | null; lesson: TeacherDayLesson; reportDownload?: ReportDownloadPort | null; openSignal?: number; closeSignal?: number; anchor?: HTMLElement | null; statusRoot?: HTMLElement | null }>(), { reportDownload: null, openSignal: 0, closeSignal: 0, anchor: null, statusRoot: null })
const emit = defineEmits<{ open: [value: boolean]; error: [cause: unknown] }>()
const panel = ref<HTMLElement | null>(null)
const open = ref(false)
const formats = ref<readonly TeacherExportFormat[]>([])
const preparing = ref(false)
const formatsLoading = ref(false)
const error = ref<string | null>(null)
const status = ref<string | null>(null)
let revision = 0
let disposed = false
const urls = new Map<string, ReturnType<typeof setTimeout>>()
const context = () => JSON.stringify([props.lesson.id, props.lesson.semesterId, props.lesson.groupId, props.lesson.subjectId, props.lesson.lessonType, props.lesson.date])

function close(restoreFocus = true): void {
  if (!open.value) return
  open.value = false
  emit('open', false)
  if (restoreFocus) void nextTick(() => { if (props.anchor?.isConnected) props.anchor.focus() })
}

function show(): void {
  if (open.value) { close(); return }
  void loadFormats()
}

async function loadFormats(): Promise<void> {
  const api = props.api
  if (!api || preparing.value || formatsLoading.value) return
  const current = ++revision
  const selectedContext = context()
  open.value = true
  error.value = null
  emit('open', true)
  formatsLoading.value = true
  try {
    const result = await api.journalExportFormats()
    if (disposed || current !== revision || api !== props.api || selectedContext !== context()) return
    formats.value = result
    await nextTick()
    panel.value?.querySelector<HTMLButtonElement>('button')?.focus()
  } catch (cause) {
    if (disposed || current !== revision || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось получить форматы файла.'
    emit('error', cause)
  } finally { if (current === revision) formatsLoading.value = false }
}

async function download(format: TeacherExportFormatCode): Promise<void> {
  const api = props.api
  if (!api || preparing.value) return
  const current = ++revision
  const selectedContext = context()
  const port = props.reportDownload
  const lesson = props.lesson
  const query = { semesterId: lesson.semesterId, groupId: lesson.groupId, subjectId: lesson.subjectId, lessonTypes: [lesson.lessonType], dateFrom: lesson.date, dateTo: lesson.date, lessonId: String(lesson.id), pastOnly: false } as const
  const isCurrent = () => !disposed && current === revision && api === props.api && port === props.reportDownload && selectedContext === context()
  close()
  preparing.value = true
  error.value = null
  status.value = null
  try {
    if (port) {
      const result = await port.download(toTeacherJournalReportRequest(query, format), isCurrent)
      if (!isCurrent() || result === 'stale') return
      if (result === 'unsupported') error.value = 'Скачивание недоступно в этой версии Telegram.'
      else status.value = result === 'accepted' ? 'Telegram принял запрос на скачивание. Завершение проверь в Telegram.' : 'Скачивание отменено.'
      return
    }
    const file = await api.exportJournal({ ...query, format })
    if (!isCurrent()) return
    const url = URL.createObjectURL(file.blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.filename
    link.click()
    urls.set(url, setTimeout(() => { URL.revokeObjectURL(url); urls.delete(url) }, 60000))
    status.value = 'Файл подготовлен. Скачивание передано браузеру.'
  } catch (cause) {
    if (!isCurrent() || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось подготовить файл пары.'
    emit('error', cause)
  } finally { if (current === revision) preparing.value = false }
}

function outside(event: PointerEvent): void {
  if (event.target instanceof Node && !panel.value?.contains(event.target) && !props.anchor?.contains(event.target)) close(false)
}
function keydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && open.value) { event.preventDefault(); event.stopPropagation(); close() }
}
function viewportChanged(): void { close(false) }
watch(() => props.openSignal, () => { void show() })
watch(() => props.closeSignal, () => close())
watch([() => props.api, context, () => props.reportDownload], () => { revision += 1; close(false); preparing.value = false; formatsLoading.value = false; error.value = null; status.value = null; formats.value = [] })
onMounted(() => {
  if (typeof document === 'undefined' || typeof window === 'undefined') return
  document.addEventListener('pointerdown', outside)
  document.addEventListener('keydown', keydown)
  window.addEventListener('resize', viewportChanged)
  window.addEventListener('scroll', viewportChanged, true)
})
onBeforeUnmount(() => {
  disposed = true
  revision += 1
  close(false)
  if (typeof document !== 'undefined') {
    document.removeEventListener('pointerdown', outside)
    document.removeEventListener('keydown', keydown)
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', viewportChanged)
    window.removeEventListener('scroll', viewportChanged, true)
  }
  for (const [url, timer] of urls) { clearTimeout(timer); URL.revokeObjectURL(url) }
  urls.clear()
})
</script>

<template>
  <section
    v-if="open"
    ref="panel"
    class="teacher-lesson-export__popup"
    aria-label="Формат файла пары"
  >
    <p
      v-if="formatsLoading"
      role="status"
    >
      Загружаем форматы…
    </p>
    <button
      v-for="format in formats"
      :key="format.code"
      type="button"
      :disabled="preparing"
      @click="download(format.code)"
    >
      {{ format.label }}
    </button>
    <StudentWarningBlock
      v-if="error"
      title="Форматы недоступны"
      :message="error"
      severity="error"
    />
  </section>
  <Teleport
    v-if="statusRoot && (preparing || error || status)"
    :to="statusRoot"
  >
    <aside
      class="teacher-lesson-export__status"
      :class="{ 'teacher-lesson-export__status--error': error && !preparing }"
      aria-live="polite"
    >
      <div
        v-if="preparing"
        role="progressbar"
        aria-label="Подготовка файла пары"
        class="teacher-lesson-export__preparing"
      >
        Готовим файл пары…
      </div>
      <StudentWarningBlock
        v-else-if="error"
        title="Файл не подготовлен"
        :message="error"
        severity="error"
        action-label="Повторить"
        @action="loadFormats"
      />
      <p
        v-else
        role="status"
      >
        {{ status }}
      </p>
    </aside>
  </Teleport>
</template>

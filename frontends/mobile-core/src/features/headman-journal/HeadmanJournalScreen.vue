<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { moscowDate } from '../../domain/homework'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { openRequestAttachmentPopup, runRequestAttachmentOpen, type RequestAttachmentPopup } from '../requests/request-attachment-action'
import type { RequestAttachmentViewState } from '../requests/types'
import {
  HeadmanJournalApiError,
  type HeadmanJournalApi,
  type HeadmanJournalAttendanceStatus,
  type HeadmanJournalExcuseType,
  type HeadmanJournalLesson,
  type HeadmanJournalReport,
  type HeadmanJournalRosterEntry,
} from './headman-journal-client'
import './headman-journal-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanJournalApi | null
  groupId: number | null
  offline?: boolean
  readOnly?: boolean
}>(), {
  offline: false,
  readOnly: false,
})

const emit = defineEmits<{
  error: [cause: unknown]
}>()

const todayIso = (): string => {
  const value = moscowDate(new Date().toISOString())
  if (value === null) throw new Error('Не удалось определить дату журнала.')
  return value
}
const selectedDate = ref(todayIso())
const lessons = shallowRef<readonly HeadmanJournalLesson[]>([])
const report = shallowRef<HeadmanJournalReport | null>(null)
const selectedLessonId = ref<number | null>(null)
const loadingLessons = ref(false)
const loadingReport = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const pendingUserId = ref<number | null>(null)
const rowErrors = ref<Record<number, string>>({})
const expandedUserIds = ref<ReadonlySet<number>>(new Set())
const excuseUserId = ref<number | null>(null)
const excuseType = ref<HeadmanJournalExcuseType>('OTHER')
const excuseComment = ref('')
const excuseFile = ref<File | null>(null)
const attachmentStates = ref<Readonly<Record<number, RequestAttachmentViewState>>>({})
const attachmentOwnerIdentity = ref<string | null>(props.api ? 'headman-journal' : null)
const attachmentOwnerGeneration = ref(0)
let lessonsRevision = 0
let reportRevision = 0
let attachmentDisposed = false
const attachmentPopups = new Set<RequestAttachmentPopup>()
const attachmentObjectUrls = new Set<string>()

const excuseTypes = [
  { value: 'ILLNESS', label: 'Болезнь' },
  { value: 'MEDICAL_EXAMINATION', label: 'Медицинское обследование' },
  { value: 'COMPETITION_PARTICIPATION', label: 'Участие в соревновании' },
  { value: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства' },
  { value: 'SUMMONS', label: 'Повестка' },
  { value: 'UNIVERSITY_ORDER', label: 'Распоряжение университета' },
  { value: 'EXEMPTION', label: 'Освобождение' },
  { value: 'OTHER', label: 'Другая причина' },
] as const satisfies readonly { value: HeadmanJournalExcuseType; label: string }[]

const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const entries = computed(() => report.value?.entries ?? [])
const busy = computed(() => loadingLessons.value || loadingReport.value)

function formatDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', weekday: 'long' }).format(date)
}

function formatShortDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short' }).format(date)
}

function formatTime(value: string | null): string {
  return value?.slice(0, 5) ?? '—'
}

function statusLabel(status: HeadmanJournalAttendanceStatus): string {
  switch (status) {
    case 'PRESENT': return 'Был'
    case 'ABSENT': return 'Не был'
    case 'EXCUSED': return 'Уважительная причина'
    case 'CANCELLED': return 'Пара отменена'
    case 'EMPTY': return 'Нет отметки'
    case 'UNSUPPORTED': return 'Неподдерживаемый статус'
  }
}

function statusSymbol(status: HeadmanJournalAttendanceStatus): string {
  switch (status) {
    case 'PRESENT': return '+'
    case 'ABSENT': return 'н'
    case 'EXCUSED': return 'у'
    case 'CANCELLED': return '—'
    case 'EMPTY': return '·'
    case 'UNSUPPORTED': return '?'
  }
}

function lessonStatusLabel(status: HeadmanJournalLesson['status']): string {
  switch (status) {
    case 'ACTIVE': return 'идёт'
    case 'CLOSED': return 'закрыта'
    case 'CANCELLED': return 'отменена'
    case 'PLANNED': return 'запланирована'
    case 'UNSUPPORTED': return 'неподдерживаемый статус'
  }
}

function shiftDate(delta: number): void {
  const date = new Date(`${selectedDate.value}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + delta)
  selectedDate.value = date.toISOString().slice(0, 10)
}

function setToday(): void {
  selectedDate.value = todayIso()
}

function selectLesson(lessonId: number): void {
  if (selectedLessonId.value === lessonId && report.value?.lessonId === lessonId) return
  selectedLessonId.value = lessonId
  void loadReport(lessonId)
}

function clearRowError(userId: number): void {
  if (!(userId in rowErrors.value)) return
  const next = { ...rowErrors.value }
  delete next[userId]
  rowErrors.value = next
}

function setRowError(userId: number, message: string): void {
  rowErrors.value = { ...rowErrors.value, [userId]: message }
}

function isExpanded(userId: number): boolean {
  return expandedUserIds.value.has(userId)
}

function toggleExpanded(userId: number): void {
  const next = new Set(expandedUserIds.value)
  if (next.has(userId)) next.delete(userId)
  else next.add(userId)
  expandedUserIds.value = next
}

function canWrite(entry: HeadmanJournalRosterEntry): boolean {
  return Boolean(props.api?.writable && entry.editable && !props.offline && !props.readOnly && selectedLessonId.value !== null)
}

function writeUnavailableMessage(entry: HeadmanJournalRosterEntry): string {
  if (props.offline) return 'Изменение отметки доступно только онлайн.'
  if (props.readOnly) return 'Текущая сессия открыта только для чтения.'
  if (!entry.editable) return 'Сервер запретил изменение этой отметки.'
  return 'Изменение отметки пока недоступно.'
}

function excuseTypeLabel(value: string | null): string | null {
  return excuseTypes.find((item) => item.value === value)?.label ?? value
}

function formatFileSize(value: number | null): string {
  if (value === null) return ''
  if (value < 1024) return `${value} Б`
  return `${Math.ceil(value / 1024)} КБ`
}

function attachmentState(entry: HeadmanJournalRosterEntry): RequestAttachmentViewState | null {
  return attachmentStates.value[entry.userId] ?? null
}

function releaseAttachmentObjectUrl(url: string): void {
  attachmentObjectUrls.delete(url)
  if (typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(url)
}

function closeAttachmentPopup(popup: RequestAttachmentPopup): void {
  try { popup.close?.() } catch { /* Browser may have closed it already. */ }
  attachmentPopups.delete(popup)
}

function openAttachment(entry: HeadmanJournalRosterEntry): void {
  const lessonId = selectedLessonId.value
  const api = props.api
  if (props.offline || !api || lessonId === null || !entry.attachmentId || attachmentState(entry)?.status === 'pending') return
  const ownerIdentityAtStart = attachmentOwnerIdentity.value
  const ownerGenerationAtStart = attachmentOwnerGeneration.value
  runRequestAttachmentOpen({
    ownerIdentity: ownerIdentityAtStart,
    ownerGeneration: ownerGenerationAtStart,
    currentOwnerIdentity: () => attachmentOwnerIdentity.value,
    currentOwnerGeneration: () => attachmentOwnerGeneration.value,
    isDisposed: () => attachmentDisposed,
    openPopup: () => typeof window === 'undefined' ? null : openRequestAttachmentPopup(window),
    download: () => api.downloadAttachment(lessonId, entry.userId),
    createObjectUrl: (blob) => {
      if (typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') throw new Error('Не удалось подготовить вложение.')
      const url = URL.createObjectURL(blob)
      attachmentObjectUrls.add(url)
      return url
    },
    releaseObjectUrl: releaseAttachmentObjectUrl,
    scheduleRelease: (url) => {
      if (typeof window === 'undefined') {
        releaseAttachmentObjectUrl(url)
        return
      }
      window.setTimeout(() => releaseAttachmentObjectUrl(url), 60_000)
    },
    navigate: (popup, url) => { popup.location.href = url },
    closePopup: closeAttachmentPopup,
    onPopupOpened: (popup) => attachmentPopups.add(popup),
    onPopupNavigated: (popup) => attachmentPopups.delete(popup),
    setState: (state) => {
      if (!attachmentDisposed
        && ownerIdentityAtStart === attachmentOwnerIdentity.value
        && ownerGenerationAtStart === attachmentOwnerGeneration.value) {
        attachmentStates.value = { ...attachmentStates.value, [entry.userId]: state }
      }
    },
    onError: (cause) => emit('error', cause),
    errorMessage: (cause) => cause instanceof Error ? cause.message : 'Не удалось открыть вложение.',
  })
}

function openExcuseForm(entry: HeadmanJournalRosterEntry): void {
  if (!canWrite(entry)) {
    notice.value = writeUnavailableMessage(entry)
    return
  }
  excuseUserId.value = entry.userId
  excuseType.value = excuseTypes.some((item) => item.value === entry.excuseType)
    ? entry.excuseType as HeadmanJournalExcuseType
    : 'OTHER'
  excuseComment.value = entry.comment ?? ''
  excuseFile.value = null
  notice.value = null
}

function closeExcuseForm(): void {
  if (pendingUserId.value !== null) return
  excuseUserId.value = null
  excuseComment.value = ''
  excuseFile.value = null
}

function chooseExcuseFile(event: Event): void {
  const input = event.target as HTMLInputElement
  excuseFile.value = input.files?.[0] ?? null
}

async function submitExcuse(entry: HeadmanJournalRosterEntry): Promise<void> {
  if (pendingUserId.value !== null || excuseUserId.value !== entry.userId || !canWrite(entry)) return
  const lessonId = selectedLessonId.value
  const api = props.api
  if (!api || lessonId === null) return
  if (Array.from(excuseComment.value).length > 1000) {
    setRowError(entry.userId, 'Комментарий не может быть длиннее 1000 символов.')
    return
  }
  pendingUserId.value = entry.userId
  notice.value = null
  clearRowError(entry.userId)
  try {
    await api.mark(lessonId, entry.userId, {
      status: 'EXCUSED',
      excuseType: excuseType.value,
      ...(excuseComment.value.trim() ? { comment: excuseComment.value } : {}),
      ...(excuseFile.value ? { file: excuseFile.value } : {}),
    })
    closeExcuseForm()
    await loadReport(lessonId)
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) return
    const message = cause instanceof Error ? cause.message : 'Не удалось сохранить причину.'
    setRowError(entry.userId, message)
    emit('error', cause)
  } finally {
    if (pendingUserId.value === entry.userId) pendingUserId.value = null
  }
}

async function clearExcuse(entry: HeadmanJournalRosterEntry): Promise<void> {
  if (pendingUserId.value !== null
    || (excuseUserId.value !== null && excuseUserId.value !== entry.userId)
    || !canWrite(entry)) return
  pendingUserId.value = entry.userId
  notice.value = null
  clearRowError(entry.userId)
  try {
    await clearEntry(entry)
    closeExcuseForm()
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) return
    const message = cause instanceof Error ? cause.message : 'Не удалось снять отметку.'
    setRowError(entry.userId, message)
    emit('error', cause)
  } finally {
    if (pendingUserId.value === entry.userId) pendingUserId.value = null
  }
}

async function applyStatus(entry: HeadmanJournalRosterEntry, status: 'PRESENT' | 'ABSENT'): Promise<void> {
  if (pendingUserId.value !== null) return
  if (!canWrite(entry)) {
    notice.value = writeUnavailableMessage(entry)
    return
  }
  const lessonId = selectedLessonId.value
  const api = props.api
  if (!api || lessonId === null) return
  pendingUserId.value = entry.userId
  notice.value = null
  clearRowError(entry.userId)
  try {
    if (entry.status === status && isExpanded(entry.userId)) {
      await clearEntry(entry)
    } else {
      await api.mark(lessonId, entry.userId, { status })
      await loadReport(lessonId)
    }
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) return
    const message = cause instanceof Error ? cause.message : 'Не удалось сохранить отметку.'
    setRowError(entry.userId, message)
    emit('error', cause)
  } finally {
    if (pendingUserId.value === entry.userId) pendingUserId.value = null
  }
}

async function clearEntry(entry: HeadmanJournalRosterEntry): Promise<void> {
  const lessonId = selectedLessonId.value
  const api = props.api
  if (!api || lessonId === null) return
  await api.clear(lessonId, entry.userId)
  // A row is updated only after the server ACK. The fresh GET also carries
  // any server-side source, reason, or editability changes.
  await loadReport(lessonId)
}

function onStatusClick(entry: HeadmanJournalRosterEntry, status: 'PRESENT' | 'ABSENT' | 'EXCUSED'): void {
  if (status === 'EXCUSED') {
    if (entry.status === 'EXCUSED' && isExpanded(entry.userId)) {
      void clearExcuse(entry)
      return
    }
    if (entry.status !== 'EMPTY' && !isExpanded(entry.userId)) {
      toggleExpanded(entry.userId)
      return
    }
    openExcuseForm(entry)
    return
  }
  if (entry.status !== 'EMPTY' && !isExpanded(entry.userId)) {
    toggleExpanded(entry.userId)
    return
  }
  void applyStatus(entry, status)
}

async function loadReport(lessonId: number): Promise<void> {
  const revision = ++reportRevision
  attachmentOwnerGeneration.value += 1
  attachmentStates.value = {}
  loadingReport.value = true
  error.value = null
  notice.value = null
  report.value = null
  excuseUserId.value = null
  excuseFile.value = null
  expandedUserIds.value = new Set()
  const api = props.api
  if (!api || props.offline) {
    loadingReport.value = false
    return
  }
  try {
    const next = await api.getLessonAttendance(lessonId)
    if (revision !== reportRevision || selectedLessonId.value !== lessonId) return
    report.value = next
  } catch (cause) {
    if (revision !== reportRevision || selectedLessonId.value !== lessonId) return
    if (cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить состав группы.'
    emit('error', cause)
  } finally {
    if (revision === reportRevision) loadingReport.value = false
  }
}

async function loadLessons(): Promise<void> {
  const revision = ++lessonsRevision
  reportRevision += 1
  loadingLessons.value = true
  loadingReport.value = false
  error.value = null
  notice.value = null
  lessons.value = []
  report.value = null
  selectedLessonId.value = null
  expandedUserIds.value = new Set()
  const api = props.api
  if (!api || props.groupId === null || props.offline) {
    loadingLessons.value = false
    return
  }
  try {
    const next = await api.listLessons(props.groupId, selectedDate.value, selectedDate.value)
    if (revision !== lessonsRevision) return
    lessons.value = next
    const first = next[0]
    if (first) {
      selectedLessonId.value = first.id
      await loadReport(first.id)
    }
  } catch (cause) {
    if (revision !== lessonsRevision) return
    if (cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof HeadmanJournalApiError && (cause.response.status === 401 || cause.response.status === 403)
      ? 'Журнал недоступен для текущей роли.'
      : cause instanceof Error ? cause.message : 'Не удалось загрузить пары.'
    emit('error', cause)
  } finally {
    if (revision === lessonsRevision) loadingLessons.value = false
  }
}

watch(
  () => [props.api, props.groupId, props.offline, selectedDate.value] as const,
  () => { void loadLessons() },
  { immediate: true },
)

watch(
  () => [props.api, props.groupId, selectedLessonId.value] as const,
  () => {
    attachmentOwnerGeneration.value += 1
    attachmentOwnerIdentity.value = props.api ? `headman-journal-${attachmentOwnerGeneration.value}` : null
    attachmentStates.value = {}
  },
  { flush: 'sync', immediate: true },
)

onBeforeUnmount(() => {
  attachmentDisposed = true
  lessonsRevision += 1
  reportRevision += 1
  attachmentOwnerGeneration.value += 1
  for (const popup of attachmentPopups) closeAttachmentPopup(popup)
  for (const url of attachmentObjectUrls) releaseAttachmentObjectUrl(url)
})
</script>

<template>
  <main
    class="headman-journal"
    :aria-busy="busy"
    aria-labelledby="headman-journal-title"
  >
    <header class="headman-journal__header">
      <div>
        <p class="headman-journal__eyebrow">
          Староста · журнал
        </p>
        <h1 id="headman-journal-title">
          Посещаемость группы
        </h1>
        <p class="headman-journal__date">
          {{ formatDate(selectedDate) }}
        </p>
      </div>
      <button
        class="headman-journal__today"
        type="button"
        :disabled="selectedDate === todayIso()"
        @click="setToday"
      >
        Сегодня
      </button>
    </header>

    <nav
      class="headman-journal__date-nav"
      aria-label="Выбор даты"
    >
      <button
        type="button"
        aria-label="Предыдущий день"
        @click="shiftDate(-1)"
      >
        ←
      </button>
      <output>{{ formatShortDate(selectedDate) }}</output>
      <button
        type="button"
        aria-label="Следующий день"
        @click="shiftDate(1)"
      >
        →
      </button>
    </nav>

    <p
      v-if="offline"
      class="headman-journal__state"
      role="status"
    >
      Журнал доступен только при подключении к интернету.
    </p>
    <p
      v-else-if="error"
      class="headman-journal__state headman-journal__state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="notice"
      class="headman-journal__state headman-journal__state--notice"
      role="status"
    >
      {{ notice }}
    </p>

    <section
      class="headman-journal__lessons"
      aria-labelledby="headman-journal-lessons-title"
    >
      <h2 id="headman-journal-lessons-title">
        Пары за день
      </h2>
      <p
        v-if="loadingLessons"
        class="headman-journal__state"
        role="status"
      >
        Загружаем пары…
      </p>
      <p
        v-else-if="lessons.length === 0"
        class="headman-journal__empty"
      >
        На эту дату сервер не вернул пар.
      </p>
      <div
        v-else
        class="headman-journal__lesson-list"
        role="list"
      >
        <button
          v-for="lesson in lessons"
          :key="lesson.id"
          class="headman-journal__lesson"
          :data-selected="selectedLessonId === lesson.id"
          type="button"
          role="listitem"
          :aria-pressed="selectedLessonId === lesson.id"
          @click="selectLesson(lesson.id)"
        >
          <span class="headman-journal__lesson-number">
            {{ lesson.lessonNumber ?? '—' }}
          </span>
          <span class="headman-journal__lesson-copy">
            <strong>{{ formatTime(lesson.startTime) }}–{{ formatTime(lesson.endTime) }}</strong>
            <span>Пара {{ lesson.lessonNumber ?? '—' }} · {{ lessonStatusLabel(lesson.status) }}</span>
            <span v-if="lesson.room">Аудитория {{ lesson.room }}</span>
          </span>
        </button>
      </div>
    </section>

    <section
      v-if="selectedLesson"
      class="headman-journal__roster"
      aria-labelledby="headman-journal-roster-title"
    >
      <header class="headman-journal__section-header">
        <div>
          <h2 id="headman-journal-roster-title">
            Состав группы
          </h2>
          <p>
            {{ formatShortDate(selectedLesson.date) }} · пара {{ selectedLesson.lessonNumber ?? '—' }}
          </p>
        </div>
        <span
          v-if="report?.editable"
          class="headman-journal__server-flag"
        >
          Изменения разрешены сервером
        </span>
      </header>

      <p
        v-if="loadingReport"
        class="headman-journal__state"
        role="status"
      >
        Загружаем состав и отметки…
      </p>
      <p
        v-else-if="report && report.entries.length === 0"
        class="headman-journal__empty"
      >
        Сервер вернул пустой состав группы.
      </p>
      <ol
        v-else-if="report"
        class="headman-journal__roster-list"
      >
        <li
          v-for="entry in entries"
          :key="entry.userId"
          class="headman-journal__row"
          :data-status="entry.status.toLowerCase()"
        >
          <div class="headman-journal__row-main">
            <span class="headman-journal__row-name">{{ entry.displayName }}</span>
            <span class="headman-journal__row-meta">
              <span class="headman-journal__badge">
                {{ statusSymbol(entry.status) }} · {{ statusLabel(entry.status) }}
              </span>
              <span v-if="entry.source">Источник: {{ entry.source }}</span>
              <span v-if="entry.excuseReason">Причина: {{ entry.excuseReason }}</span>
              <span v-if="entry.excuseType">Тип причины: {{ excuseTypeLabel(entry.excuseType) }}</span>
              <button
                v-if="entry.status === 'EXCUSED' && canWrite(entry)"
                class="headman-journal__reason-edit"
                type="button"
                @click="openExcuseForm(entry)"
              >
                Изменить причину
              </button>
              <span v-if="entry.attachmentId">
                <button
                  class="headman-journal__attachment"
                  type="button"
                  :disabled="offline || attachmentState(entry)?.status === 'pending'"
                  :aria-busy="attachmentState(entry)?.status === 'pending'"
                  @click="openAttachment(entry)"
                >
                  {{ attachmentState(entry)?.status === 'pending' ? 'Открываем…' : attachmentState(entry)?.status === 'error' ? 'Повторить файл' : 'Открыть файл' }}
                </button>
                <span v-if="entry.attachmentName">
                  {{ entry.attachmentName }}<span v-if="entry.attachmentSize !== null"> ({{ formatFileSize(entry.attachmentSize) }})</span>
                </span>
                <span
                  v-if="attachmentState(entry)?.status === 'error'"
                  class="headman-journal__attachment-error"
                  role="alert"
                >
                  {{ attachmentState(entry)?.error || 'Не удалось открыть вложение.' }}
                </span>
              </span>
            </span>
          </div>

          <div
            v-if="canWrite(entry) && (entry.status === 'EMPTY' || isExpanded(entry.userId))"
            class="headman-journal__picker"
            role="group"
            :aria-label="`Изменить отметку: ${entry.displayName}`"
          >
            <button
              type="button"
              :disabled="pendingUserId !== null"
              :aria-label="`Отметить «был»: ${entry.displayName}`"
              @click="onStatusClick(entry, 'PRESENT')"
            >
              +
            </button>
            <button
              type="button"
              :disabled="pendingUserId !== null"
              :aria-label="`Отметить «не был»: ${entry.displayName}`"
              @click="onStatusClick(entry, 'ABSENT')"
            >
              н
            </button>
            <button
              type="button"
              :disabled="pendingUserId !== null"
              :aria-label="`Отметить «уважительная причина»: ${entry.displayName}`"
              @click="onStatusClick(entry, 'EXCUSED')"
            >
              у
            </button>
          </div>
          <button
            v-else
            class="headman-journal__current"
            type="button"
            :disabled="pendingUserId !== null || !canWrite(entry)"
            :aria-label="`Открыть изменение отметки: ${entry.displayName}`"
            @click="toggleExpanded(entry.userId)"
          >
            {{ statusSymbol(entry.status) }}
          </button>

          <form
            v-if="excuseUserId === entry.userId"
            class="headman-journal__excuse-form"
            @submit.prevent="submitExcuse(entry)"
          >
            <label>
              <span>Причина</span>
              <select v-model="excuseType">
                <option
                  v-for="item in excuseTypes"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label>
              <span>Комментарий</span>
              <textarea
                v-model="excuseComment"
                maxlength="1000"
                rows="3"
              />
            </label>
            <label>
              <span>Файл (необязательно)</span>
              <input
                type="file"
                @change="chooseExcuseFile"
              >
            </label>
            <p v-if="excuseFile">
              Выбран файл: {{ excuseFile.name }}
            </p>
            <div class="headman-journal__excuse-actions">
              <button
                type="submit"
                :disabled="pendingUserId !== null"
              >
                {{ pendingUserId === entry.userId ? 'Сохраняем…' : 'Сохранить причину' }}
              </button>
              <button
                type="button"
                :disabled="pendingUserId !== null"
                @click="closeExcuseForm"
              >
                Отмена
              </button>
              <button
                type="button"
                :disabled="pendingUserId !== null"
                @click="clearExcuse(entry)"
              >
                Снять отметку
              </button>
            </div>
          </form>

          <span
            v-if="pendingUserId === entry.userId"
            class="headman-journal__row-status"
            role="status"
          >
            Сохраняем…
          </span>
          <span
            v-else-if="rowErrors[entry.userId]"
            class="headman-journal__row-status headman-journal__row-status--error"
            role="alert"
          >
            {{ rowErrors[entry.userId] }}
          </span>
          <span
            v-else-if="entry.status !== 'CANCELLED' && entry.status !== 'UNSUPPORTED' && !canWrite(entry)"
            class="headman-journal__row-status"
          >
            {{ writeUnavailableMessage(entry) }}
          </span>
        </li>
      </ol>
    </section>
  </main>
</template>

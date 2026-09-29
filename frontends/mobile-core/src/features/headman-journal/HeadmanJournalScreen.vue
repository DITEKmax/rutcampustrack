<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { moscowDate } from '../../domain/homework'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import { openRequestAttachmentPopup, runRequestAttachmentOpen, type RequestAttachmentPopup } from '../requests/request-attachment-action'
import type { RequestAttachmentViewState } from '../requests/types'
import {
  HeadmanJournalApiError,
  createHeadmanLessonTransferRequest,
  type HeadmanJournalApi,
  type HeadmanJournalAttendanceStatus,
  type HeadmanJournalExcuseType,
  type HeadmanJournalLesson,
  type HeadmanJournalReport,
  type HeadmanJournalRosterEntry,
  type HeadmanLessonTransferErrorCode,
  type HeadmanLessonTransferRequest,
  type HeadmanLessonTransferResponse,
  type HeadmanWeeklyExportOptions,
  toHeadmanWeeklyReportRequest,
} from './headman-journal-client'
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client'
import './headman-journal-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanJournalApi | null
  groupId: number | null
  assistantPermissions?: readonly HeadmanAssistantPermission[] | null
  offline?: boolean
  readOnly?: boolean
  reportDownload?: ReportDownloadPort | null
  initialDate?: string
  initialLessonId?: number | null
}>(), {
  assistantPermissions: null,
  offline: false,
  readOnly: false,
  reportDownload: null,
  initialDate: '',
  initialLessonId: null,
})

const emit = defineEmits<{
  error: [cause: unknown]
}>()

const todayIso = (): string => {
  const value = moscowDate(new Date().toISOString())
  if (value === null) throw new Error('Не удалось определить дату журнала.')
  return value
}
const selectedDate = ref(props.initialDate || todayIso())
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
const cancelReason = ref('')
const lessonActionBusy = ref(false)
type LessonActionConfirmation = {
  action: 'cancel' | 'restore' | 'transfer'
  api: HeadmanJournalApi
  groupId: number | null
  lessonId: number
  lessonDate: string
  lessonNumber: number | null
  permissions: readonly HeadmanAssistantPermission[] | null
  reason: string | null
  transferRequest: HeadmanLessonTransferRequest | null
}
type TransferOperationPhase = 'submitting' | 'pending' | 'checking' | 'uncertain' | 'completed' | 'error'
type TransferOperation = {
  readonly key: string
  readonly api: HeadmanJournalApi
  readonly groupId: number
  readonly sourceLessonId: number
  readonly sourceDate: string
  readonly targetDate: string | null
  readonly targetLessonNumber: number | null
  readonly request: HeadmanLessonTransferRequest | null
  readonly operationId: string | null
  readonly response: HeadmanLessonTransferResponse | null
  readonly phase: TransferOperationPhase
  readonly message: string | null
}
const lessonActionConfirmationDialog = ref<HTMLDialogElement | null>(null)
const pendingLessonActionConfirmation = shallowRef<LessonActionConfirmation | null>(null)
const transferTaskOpen = ref(false)
const transferTargetDate = ref('')
const transferTargetLessonNumber = ref<number | null>(null)
const transferTargetLessons = shallowRef<readonly HeadmanJournalLesson[]>([])
const transferTargetLessonsDate = ref<string | null>(null)
const transferTargetLoading = ref(false)
const transferTargetError = ref<string | null>(null)
const transferOperations = shallowRef<readonly TransferOperation[]>([])
const attachmentStates = ref<Readonly<Record<number, RequestAttachmentViewState>>>({})
const attachmentOwnerIdentity = ref<string | null>(props.api ? 'headman-journal' : null)
const attachmentOwnerGeneration = ref(0)
const weeklyExportOptions = shallowRef<HeadmanWeeklyExportOptions | null>(null)
const weeklyExportFormatCode = ref('')
const selectedWeeklyStarts = ref<readonly string[]>([])
const loadingWeeklyOptions = ref(false)
const weeklyExportBusy = ref(false)
const weeklyExportError = ref<string | null>(null)
const weeklyExportStatus = ref<string | null>(null)
let lessonsRevision = 0
let reportRevision = 0
let lessonActionRevision = 0
let transferTargetRevision = 0
let transferContextGeneration = 0
const transferRunRevisions = new Map<string, number>()
let weeklyOptionsRevision = 0
let weeklyExportRevision = 0
let weeklyExportAbort: AbortController | null = null
let activeLessonsLoad: Promise<void> = Promise.resolve()
let preferredLessonAfterDateChange: number | null = null
let disposed = false
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
const busy = computed(() => loadingLessons.value || loadingReport.value || loadingWeeklyOptions.value || weeklyExportBusy.value)
const selectedWeeklyFormat = computed(() => weeklyExportOptions.value?.formats
  .find((format) => format.code === weeklyExportFormatCode.value) ?? null)
const selectedWeeklyCount = computed(() => selectedWeeklyStarts.value.length)
const transferTargetDateMin = computed(() => shiftIsoDate(todayIso(), 1))
const transferTargetSlots = computed(() => Array.from({ length: 8 }, (_, index) => index + 1))
const availableTransferTargetSlots = computed(() => transferTargetSlots.value.filter((slot) => !isTransferTargetSlotUnavailable(slot)))
const selectedTransferOperation = computed(() => selectedLesson.value ? transferOperationForLesson(selectedLesson.value.id) : null)
const selectedServerTransferState = computed(() => selectedLesson.value?.transferState ?? null)
const canSubmitTransfer = computed(() => Boolean(transferTaskOpen.value && selectedLesson.value
  && canStartTransfer(selectedLesson.value) && !lessonActionBusy.value
  && transferTargetLessonsDate.value === transferTargetDate.value && !transferTargetLoading.value
  && transferTargetError.value === null && transferTargetLessonNumber.value !== null
  && availableTransferTargetSlots.value.includes(transferTargetLessonNumber.value)))

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
    case 'TRANSFERRED': return 'перенесена'
    case 'PLANNED': return 'запланирована'
    case 'UNSUPPORTED': return 'неподдерживаемый статус'
  }
}

function shiftDate(delta: number): void {
  selectedDate.value = shiftIsoDate(selectedDate.value, delta)
}

function shiftIsoDate(value: string, delta: number): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  date.setUTCDate(date.getUTCDate() + delta)
  return date.toISOString().slice(0, 10)
}

function setToday(): void {
  selectedDate.value = todayIso()
}

function selectLesson(lessonId: number): void {
  if (selectedLessonId.value === lessonId && report.value?.lessonId === lessonId) return
  selectedLessonId.value = lessonId
  resumeTransferStatusForSelectedLesson()
  if (canViewReport()) void loadReport(lessonId)
  else report.value = null
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

function hasAssistantPermission(permission: HeadmanAssistantPermission): boolean {
  return props.assistantPermissions === null || props.assistantPermissions.includes(permission)
}

function canViewReport(): boolean {
  return hasAssistantPermission('MARK_ATTENDANCE') || hasAssistantPermission('VIEW_STATS')
}

function canExportWeekly(): boolean {
  return Boolean(props.api && props.groupId !== null && !props.offline
    && (props.assistantPermissions === null || props.assistantPermissions.includes('VIEW_STATS')))
}

function weekForDate(options: HeadmanWeeklyExportOptions, date: string) {
  return options.weeks.find((week) => date >= week.weekStart && date <= week.weekEnd) ?? null
}

function resetWeeklySelectionForDate(): void {
  const options = weeklyExportOptions.value
  if (!options) {
    selectedWeeklyStarts.value = []
    return
  }
  const selected = weekForDate(options, selectedDate.value)
  selectedWeeklyStarts.value = selected ? [selected.weekStart] : []
}

function toggleWeeklyWeek(weekStart: string, checked: boolean): void {
  const options = weeklyExportOptions.value
  if (!options?.weeks.some((week) => week.weekStart === weekStart)) return
  const next = new Set(selectedWeeklyStarts.value)
  if (checked) next.add(weekStart)
  else next.delete(weekStart)
  selectedWeeklyStarts.value = options.weeks
    .filter((week) => next.has(week.weekStart))
    .map((week) => week.weekStart)
  invalidateWeeklyExport('Недели изменились. Запусти скачивание для нового выбора.')
}

function invalidateWeeklyExport(message?: string): void {
  weeklyExportRevision += 1
  weeklyExportAbort?.abort()
  weeklyExportAbort = null
  weeklyExportBusy.value = false
  weeklyExportError.value = null
  weeklyExportStatus.value = null
  if (message) weeklyExportStatus.value = message
}

async function loadWeeklyExportOptions(): Promise<void> {
  const revision = ++weeklyOptionsRevision
  invalidateWeeklyExport()
  weeklyExportOptions.value = null
  weeklyExportFormatCode.value = ''
  selectedWeeklyStarts.value = []
  if (!canExportWeekly()) {
    loadingWeeklyOptions.value = false
    return
  }
  const api = props.api
  loadingWeeklyOptions.value = true
  weeklyExportError.value = null
  try {
    const options = await api!.getWeeklyExportOptions()
    if (disposed || revision !== weeklyOptionsRevision || api !== props.api || !canExportWeekly()) return
    weeklyExportOptions.value = options
    weeklyExportFormatCode.value = options.formats[0]?.code ?? ''
    resetWeeklySelectionForDate()
  } catch (cause) {
    if (disposed || revision !== weeklyOptionsRevision || cause instanceof StaleSessionGenerationError) return
    weeklyExportError.value = cause instanceof Error
      ? cause.message
      : 'Не удалось загрузить список недель и форматов.'
    emit('error', cause)
  } finally {
    if (revision === weeklyOptionsRevision) loadingWeeklyOptions.value = false
  }
}

async function downloadWeeklyExport(): Promise<void> {
  const api = props.api
  const reportDownload = props.reportDownload
  const options = weeklyExportOptions.value
  const format = selectedWeeklyFormat.value
  if (!api || !options || !format || !canExportWeekly() || weeklyExportBusy.value) return
  const weekStarts = options.weeks
    .filter((week) => selectedWeeklyStarts.value.includes(week.weekStart))
    .map((week) => week.weekStart)
  if (weekStarts.length === 0) {
    weeklyExportError.value = 'Выбери хотя бы одну неделю.'
    weeklyExportStatus.value = null
    return
  }
  invalidateWeeklyExport()
  const revision = weeklyExportRevision
  const controller = new AbortController()
  weeklyExportAbort = controller
  weeklyExportBusy.value = true
  try {
    if (reportDownload) {
      const request = toHeadmanWeeklyReportRequest(options.weeks, weekStarts, format)
      const isCurrent = (): boolean => !disposed && revision === weeklyExportRevision
        && api === props.api && reportDownload === props.reportDownload
        && options === weeklyExportOptions.value && canExportWeekly()
        && selectedWeeklyFormat.value?.code === format.code
        && JSON.stringify(selectedWeeklyStarts.value) === JSON.stringify(weekStarts)
      const result = await reportDownload.download(request, isCurrent)
      if (result === 'stale' || !isCurrent()) return
      if (result === 'unsupported') {
        weeklyExportStatus.value = 'Скачивание файлов недоступно в этой версии Telegram. Обнови Telegram до версии 8.0 или новее.'
      } else {
        weeklyExportStatus.value = result === 'accepted'
          ? 'Telegram принял запрос на скачивание; проверь завершение в Telegram.'
          : 'Скачивание отменено.'
      }
      return
    }

    const downloaded = await api.downloadWeeklyExport(weekStarts, format, controller.signal)
    if (disposed || revision !== weeklyExportRevision || api !== props.api || !canExportWeekly()) return
    if (typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function' || typeof document === 'undefined') {
      throw new Error('Скачивание файла недоступно в этом приложении.')
    }
    const url = URL.createObjectURL(downloaded.blob)
    attachmentObjectUrls.add(url)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = downloaded.filename
    anchor.rel = 'noopener'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => releaseAttachmentObjectUrl(url), 60_000)
    weeklyExportStatus.value = `Файл ${downloaded.filename} подготовлен.`
  } catch (cause) {
    if (disposed || revision !== weeklyExportRevision || cause instanceof StaleSessionGenerationError
      || (typeof DOMException !== 'undefined' && cause instanceof DOMException && cause.name === 'AbortError')) return
    if (cause instanceof HeadmanJournalApiError && cause.response.status === 403) {
      weeklyExportError.value = 'Для выгрузки требуется действующее право VIEW_STATS.'
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status === 413) {
      weeklyExportError.value = cause.message || 'Файл слишком большой. Выбери меньше недель или другой доступный формат; данные не обрезаны.'
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status === 422) {
      weeklyExportError.value = 'Выбранная неделя больше недоступна. Обнови список и выбери неделю из него.'
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status >= 500) {
      weeklyExportError.value = 'Не удалось сформировать журнал. Проверь подключение и попробуй ещё раз.'
    } else {
      weeklyExportError.value = cause instanceof Error ? cause.message : 'Не удалось скачать журнал.'
    }
    emit('error', cause)
  } finally {
    if (revision === weeklyExportRevision) {
      weeklyExportBusy.value = false
      weeklyExportAbort = null
    }
  }
}

function canCancelLessons(): boolean {
  return Boolean(props.api && !props.offline && !props.readOnly && hasAssistantPermission('CANCEL_LESSONS'))
}

function isHeadmanContext(): boolean {
  return props.assistantPermissions === null
}

function isTransferOperationActive(operation: TransferOperation): boolean {
  return operation.phase === 'submitting' || operation.phase === 'pending'
    || operation.phase === 'checking' || operation.phase === 'uncertain'
}

function transferOperationForLesson(lessonId: number): TransferOperation | null {
  for (let index = transferOperations.value.length - 1; index >= 0; index -= 1) {
    const operation = transferOperations.value[index]
    if (operation?.api === props.api && operation.groupId === props.groupId && operation.sourceLessonId === lessonId) {
      return operation
    }
  }
  return null
}

function isLessonTransferLocked(lesson: HeadmanJournalLesson | null): boolean {
  if (!lesson) return false
  if (lesson.status === 'TRANSFERRED' || lesson.transferState === 'PENDING') return true
  return transferOperations.value.some((operation) => operation.api === props.api
    && operation.groupId === props.groupId && isTransferOperationActive(operation)
    && (operation.sourceLessonId === lesson.id
      || (operation.targetDate === lesson.date && operation.targetLessonNumber === lesson.lessonNumber)))
}

function isTransferSourceEligible(lesson: HeadmanJournalLesson): boolean {
  return lesson.current && lesson.status === 'PLANNED' && lesson.date > todayIso()
    && lesson.occurrenceRevision !== null && /^[1-9][0-9]*$/.test(lesson.occurrenceRevision)
    && lesson.transferState !== 'PENDING'
}

function canStartTransfer(lesson: HeadmanJournalLesson): boolean {
  return Boolean(isHeadmanContext() && canCancelLessons() && props.groupId !== null && isTransferSourceEligible(lesson)
    && !isLessonTransferLocked(lesson))
}

function isTransferTargetSlotUnavailable(slot: number): boolean {
  const source = selectedLesson.value
  const targetDate = transferTargetDate.value
  if (!transferTaskOpen.value || !source || transferTargetDateProblem(targetDate) !== null
    || transferTargetLessonsDate.value !== targetDate || transferTargetLoading.value
    || transferTargetError.value !== null) return true
  if (source.date === targetDate && source.lessonNumber === slot) return true
  if (transferTargetLessons.value.some((lesson) => lesson.current && lesson.id !== source.id
    && lesson.lessonNumber === slot && lesson.status !== 'CANCELLED' && lesson.status !== 'TRANSFERRED')) return true
  return transferOperations.value.some((operation) => operation.api === props.api
    && operation.groupId === props.groupId && isTransferOperationActive(operation)
    && operation.targetDate === targetDate && operation.targetLessonNumber === slot)
}

function transferTargetSlotLabel(slot: number): string {
  if (selectedLesson.value?.date === transferTargetDate.value && selectedLesson.value.lessonNumber === slot) {
    return `${slot}-я пара · исходное место`
  }
  if (transferTargetLessons.value.some((lesson) => lesson.current && lesson.id !== selectedLesson.value?.id
    && lesson.lessonNumber === slot && lesson.status !== 'CANCELLED' && lesson.status !== 'TRANSFERRED')) return `${slot}-я пара · занято`
  if (transferOperations.value.some((operation) => operation.api === props.api
    && operation.groupId === props.groupId && isTransferOperationActive(operation)
    && operation.targetDate === transferTargetDate.value && operation.targetLessonNumber === slot)) {
    return `${slot}-я пара · ожидает переноса`
  }
  return `${slot}-я пара`
}

function transferTargetDateProblem(date: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return 'Выбери будущую дату.'
  const parsed = new Date(`${date}T12:00:00Z`)
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
    return 'Дата переноса указана некорректно.'
  }
  if (date <= todayIso()) return 'Выбери будущую дату.'
  if (parsed.getUTCDay() === 0) return 'Перенос возможен только на учебный день с понедельника по субботу.'
  return null
}

function transferErrorMessage(cause: unknown): string {
  if (!(cause instanceof HeadmanJournalApiError)) {
    return cause instanceof Error ? cause.message : 'Не удалось подтвердить перенос пары.'
  }
  const problem = cause.problem
  const code = typeof problem === 'object' && problem !== null && 'errorCode' in problem
    ? problem.errorCode
    : null
  const knownCode = code === 'TARGET_DATA_CONFLICT' || code === 'SOURCE_STATE_CONFLICT'
    || code === 'SCOPE_MISMATCH' || code === 'INVALID_SNAPSHOT'
    || code === 'DEPENDENCY_UNAVAILABLE' || code === 'ARCHIVED_SEMESTER'
    ? code as HeadmanLessonTransferErrorCode
    : null
  if (knownCode) return transferErrorCodeMessage(knownCode)
  if (cause.response.status === 401) return 'Сеанс истёк. Войди снова и проверь состояние переноса.'
  if (cause.response.status === 403) return 'У тебя нет прав на перенос этой пары.'
  if (cause.response.status === 404) return 'Пара не найдена. Обнови журнал и выбери её ещё раз.'
  if (cause.response.status === 409) return 'Пара или выбранное место в расписании изменились. Обнови журнал и выбери свободное место.'
  if (cause.response.status >= 500 || cause.response.status === 408 || cause.response.status === 429) {
    return 'Сервер не подтвердил запрос. Повтори его тем же запросом или проверь состояние операции.'
  }
  return cause.message
}

function transferErrorCodeMessage(code: HeadmanLessonTransferErrorCode): string {
  switch (code) {
    case 'TARGET_DATA_CONFLICT': return 'Выбранная дата или пара уже заняты. Обнови список и выбери другое место.'
    case 'SOURCE_STATE_CONFLICT': return 'Исходная пара изменилась. Обнови журнал перед новым переносом.'
    case 'SCOPE_MISMATCH': return 'Перенос этой пары в выбранную дату недоступен.'
    case 'INVALID_SNAPSHOT': return 'Данные пары изменились. Обнови журнал и начни перенос заново.'
    case 'DEPENDENCY_UNAVAILABLE': return 'Перенос ожидает проверки сервиса. Проверь состояние операции позже.'
    case 'ARCHIVED_SEMESTER': return 'Семестр закрыт. Перенос в нём недоступен.'
  }
}

function transferPhaseMessage(operation: TransferOperation): string {
  if (operation.message) return operation.message
  switch (operation.phase) {
    case 'submitting': return 'Отправляем запрос на перенос…'
    case 'pending': return 'Перенос принят и ожидает завершения.'
    case 'checking': return 'Проверяем состояние переноса…'
    case 'uncertain': return 'Ответ не получен. Повтор запроса безопасен: будет использован тот же ключ и payload.'
    case 'completed': return 'Перенос завершён.'
    case 'error': return 'Перенос завершился ошибкой.'
  }
}

function serverTransferStateMessage(state: HeadmanJournalLesson['transferState']): string {
  switch (state) {
    case 'PENDING': return 'Перенос выполняется. Проверь его состояние перед другими действиями с парой.'
    case 'COMPLETED': return 'Сервер подтвердил завершение переноса. Проверь дату операции, чтобы открыть новую пару.'
    case 'ERROR': return 'Сервер сохранил ошибку переноса. Обнови журнал перед новым действием.'
    case null: return ''
  }
}

function resumeTransferStatusForSelectedLesson(): void {
  const lesson = selectedLesson.value
  if (!lesson?.transferOperationId || !isHeadmanContext() || props.offline || props.readOnly) return
  const operation = transferOperationForLesson(lesson.id)
  if (operation?.operationId === lesson.transferOperationId
    && (operation.phase === 'checking' || operation.phase === 'submitting' || operation.phase === 'completed')) return
  resumeTransferForSelectedLesson(false)
}

function canRunTransferOperation(operation: TransferOperation): boolean {
  return !disposed && operation.api === props.api && operation.groupId === props.groupId
    && selectedDate.value === operation.sourceDate && selectedLessonId.value === operation.sourceLessonId
    && selectedLesson.value?.id === operation.sourceLessonId && !props.offline && !props.readOnly
    && isHeadmanContext()
}

function replaceTransferOperation(key: string, patch: Partial<TransferOperation>): void {
  const index = transferOperations.value.findIndex((operation) => operation.key === key)
  if (index < 0) return
  const next = [...transferOperations.value]
  const current = next[index]
  if (!current) return
  next[index] = { ...current, ...patch }
  transferOperations.value = next
}

async function loadTransferTargetLessons(): Promise<void> {
  const revision = ++transferTargetRevision
  const api = props.api
  const groupId = props.groupId
  const source = selectedLesson.value
  const date = transferTargetDate.value
  transferTargetLessons.value = []
  transferTargetLessonsDate.value = null
  transferTargetError.value = null
  transferTargetLessonNumber.value = null
  if (!transferTaskOpen.value || !api || groupId === null || !source || !canStartTransfer(source)) {
    transferTargetLoading.value = false
    return
  }
  const dateProblem = transferTargetDateProblem(date)
  if (dateProblem !== null) {
    transferTargetError.value = dateProblem
    transferTargetLoading.value = false
    return
  }
  transferTargetLoading.value = true
  try {
    const next = await api.listLessons(groupId, date, date)
    if (disposed || revision !== transferTargetRevision || api !== props.api || groupId !== props.groupId
      || selectedDate.value !== source.date || selectedLessonId.value !== source.id
      || transferTargetDate.value !== date || !transferTaskOpen.value) return
    transferTargetLessons.value = next
    transferTargetLessonsDate.value = date
    transferTargetLoading.value = false
    transferTargetLessonNumber.value = transferTargetSlots.value.find((slot) => !isTransferTargetSlotUnavailable(slot)) ?? null
  } catch (cause) {
    if (disposed || revision !== transferTargetRevision || cause instanceof StaleSessionGenerationError) return
    transferTargetError.value = transferErrorMessage(cause)
    emit('error', cause)
  } finally {
    if (revision === transferTargetRevision) transferTargetLoading.value = false
  }
}

function openTransferTask(): void {
  const lesson = selectedLesson.value
  if (!lesson || !canStartTransfer(lesson) || lessonActionBusy.value) return
  transferTargetDate.value = lesson.date
  transferTargetLessonNumber.value = null
  transferTaskOpen.value = true
}

function closeTransferTask(): void {
  transferTaskOpen.value = false
  transferTargetDate.value = ''
  transferTargetLessonNumber.value = null
  transferTargetLessons.value = []
  transferTargetLessonsDate.value = null
  transferTargetError.value = null
}

function requestTransferSelectedLesson(): void {
  const api = props.api
  const lesson = selectedLesson.value
  const groupId = props.groupId
  const targetDate = transferTargetDate.value
  const targetLessonNumber = transferTargetLessonNumber.value
  if (!api || groupId === null || !lesson || !canSubmitTransfer.value || targetLessonNumber === null
    || lesson.occurrenceRevision === null) return
  try {
    if (!globalThis.crypto || typeof globalThis.crypto.randomUUID !== 'function') {
      throw new Error('Безопасный ключ запроса недоступен в этой версии браузера.')
    }
    const transferRequest = createHeadmanLessonTransferRequest(
      targetDate,
      targetLessonNumber,
      lesson.occurrenceRevision,
      globalThis.crypto.randomUUID(),
    )
    openLessonActionConfirmation({
      action: 'transfer',
      api,
      groupId,
      lessonId: lesson.id,
      lessonDate: lesson.date,
      lessonNumber: lesson.lessonNumber,
      permissions: assistantPermissionSnapshot(),
      reason: null,
      transferRequest,
    })
  } catch (cause) {
    notice.value = cause instanceof Error ? cause.message : 'Не удалось подготовить запрос переноса.'
  }
}

function assistantPermissionSnapshot(): readonly HeadmanAssistantPermission[] | null {
  return props.assistantPermissions === null ? null : [...props.assistantPermissions].sort()
}

function assistantPermissionsMatch(snapshot: readonly HeadmanAssistantPermission[] | null): boolean {
  const current = assistantPermissionSnapshot()
  return snapshot === null || current === null
    ? snapshot === current
    : snapshot.length === current.length && snapshot.every((permission, index) => permission === current[index])
}

function isLessonActionConfirmationCurrent(confirmation: LessonActionConfirmation): boolean {
  const lesson = selectedLesson.value
  if (lessonActionBusy.value || !lesson || lesson.id !== confirmation.lessonId
    || selectedLessonId.value !== confirmation.lessonId || props.api !== confirmation.api
    || props.groupId !== confirmation.groupId || props.offline || props.readOnly
    || !canCancelLessons() || !assistantPermissionsMatch(confirmation.permissions)) return false

  if (confirmation.action === 'cancel') {
    return lesson.status !== 'CANCELLED' && confirmation.reason !== null
      && cancelReason.value.trim() === confirmation.reason && !isLessonTransferLocked(lesson)
  }
  if (confirmation.action === 'restore') return lesson.status === 'CANCELLED' && !isLessonTransferLocked(lesson)
  const transferRequest = confirmation.transferRequest
  return transferRequest !== null && transferTaskOpen.value && canStartTransfer(lesson)
    && transferTargetDate.value === transferRequest.targetDate
    && transferTargetLessonNumber.value === transferRequest.targetLessonNumber
    && transferTargetLessonsDate.value === transferRequest.targetDate
    && !isTransferTargetSlotUnavailable(transferRequest.targetLessonNumber)
}

const lessonActionConfirmationCurrent = computed(() => {
  const confirmation = pendingLessonActionConfirmation.value
  return confirmation !== null && isLessonActionConfirmationCurrent(confirmation)
})

function closeLessonActionConfirmation(): void {
  pendingLessonActionConfirmation.value = null
  const dialog = lessonActionConfirmationDialog.value
  if (dialog?.open) dialog.close()
}

function invalidateLessonActionConfirmation(): void {
  if (pendingLessonActionConfirmation.value !== null) closeLessonActionConfirmation()
}

function openLessonActionConfirmation(confirmation: LessonActionConfirmation): void {
  const dialog = lessonActionConfirmationDialog.value
  if (!dialog || dialog.open || !isLessonActionConfirmationCurrent(confirmation)) return
  pendingLessonActionConfirmation.value = confirmation
  dialog.showModal()
}

function canManageExcuses(): boolean {
  return hasAssistantPermission('MANAGE_EXCUSES')
}

function canWrite(entry: HeadmanJournalRosterEntry): boolean {
  const required = entry.status === 'EXCUSED' ? 'MANAGE_EXCUSES' : 'MARK_ATTENDANCE'
  return Boolean(props.api?.writable && entry.editable && !props.offline && !props.readOnly
    && selectedLessonId.value !== null && selectedLesson.value !== null
    && selectedLesson.value.status !== 'TRANSFERRED'
    && hasAssistantPermission(required))
}

function canWriteExcuse(entry: HeadmanJournalRosterEntry): boolean {
  return canManageExcuses() && canWrite(entry)
}

function requestCancelSelectedLesson(): void {
  const api = props.api
  const lesson = selectedLesson.value
  if (lessonActionBusy.value || !api || !lesson || lesson.status === 'CANCELLED'
    || !canCancelLessons() || isLessonTransferLocked(lesson)) return
  const reason = cancelReason.value.trim()
  if (!reason) {
    notice.value = 'Укажи причину отмены пары.'
    return
  }
  if (Array.from(reason).length > 512) {
    notice.value = 'Причина отмены не может быть длиннее 512 символов.'
    return
  }
  openLessonActionConfirmation({
    action: 'cancel',
    api,
    groupId: props.groupId,
    lessonId: lesson.id,
    lessonDate: lesson.date,
    lessonNumber: lesson.lessonNumber,
    permissions: assistantPermissionSnapshot(),
    reason,
    transferRequest: null,
  })
}

function requestRestoreSelectedLesson(): void {
  const api = props.api
  const lesson = selectedLesson.value
  if (lessonActionBusy.value || !api || !lesson || lesson.status !== 'CANCELLED'
    || !canCancelLessons() || isLessonTransferLocked(lesson)) return
  openLessonActionConfirmation({
    action: 'restore',
    api,
    groupId: props.groupId,
    lessonId: lesson.id,
    lessonDate: lesson.date,
    lessonNumber: lesson.lessonNumber,
    permissions: assistantPermissionSnapshot(),
    reason: null,
    transferRequest: null,
  })
}

function transferOperationKey(requestKey: string): string {
  return `request:${requestKey}`
}

function beginConfirmedTransfer(confirmation: LessonActionConfirmation): void {
  const request = confirmation.transferRequest
  const api = confirmation.api
  const groupId = confirmation.groupId
  const lesson = selectedLesson.value
  if (confirmation.action !== 'transfer' || !request || groupId === null || !lesson
    || lesson.id !== confirmation.lessonId || !canStartTransfer(lesson)) return
  const operation: TransferOperation = {
    key: transferOperationKey(request.requestKey),
    api,
    groupId,
    sourceLessonId: lesson.id,
    sourceDate: lesson.date,
    targetDate: request.targetDate,
    targetLessonNumber: request.targetLessonNumber,
    request,
    operationId: null,
    response: null,
    phase: 'submitting',
    message: null,
  }
  closeTransferTask()
  transferOperations.value = [...transferOperations.value, operation]
  void runTransferOperation(operation, 'submit')
}

async function confirmLessonAction(): Promise<void> {
  const confirmation = pendingLessonActionConfirmation.value
  if (!confirmation) return
  if (!isLessonActionConfirmationCurrent(confirmation)) {
    closeLessonActionConfirmation()
    return
  }
  closeLessonActionConfirmation()
  if (confirmation.action === 'transfer') {
    beginConfirmedTransfer(confirmation)
  } else if (confirmation.action === 'cancel' && confirmation.reason !== null) {
    await cancelSelectedLesson(confirmation.api, confirmation.lessonId, confirmation.reason)
  } else {
    await restoreSelectedLesson(confirmation.api, confirmation.lessonId)
  }
}

async function runTransferOperation(operation: TransferOperation, mode: 'submit' | 'status'): Promise<void> {
  if (!canRunTransferOperation(operation)) return
  if ((mode === 'submit' && operation.request === null)
    || (mode === 'status' && operation.operationId === null)) return
  const runRevision = (transferRunRevisions.get(operation.key) ?? 0) + 1
  transferRunRevisions.set(operation.key, runRevision)
  const contextGeneration = transferContextGeneration
  const isCurrentRun = (): boolean => transferRunRevisions.get(operation.key) === runRevision
    && transferContextGeneration === contextGeneration && canRunTransferOperation(operation)
  replaceTransferOperation(operation.key, {
    phase: mode === 'submit' ? 'submitting' : 'checking',
    message: null,
  })
  try {
    const response = mode === 'submit'
      ? await operation.api.transferLesson(operation.sourceLessonId, operation.request!)
      : await operation.api.getLessonTransfer(operation.operationId!)
    if (!isCurrentRun()) return
    if (response.sourceLessonId !== String(operation.sourceLessonId)
      || (operation.operationId !== null && response.operationId !== operation.operationId)
      || (operation.targetDate !== null && response.targetDate !== operation.targetDate)) {
      throw new Error('Сервер вернул состояние другого переноса.')
    }
    await applyTransferResponse(operation, response, isCurrentRun)
  } catch (cause) {
    if (!isCurrentRun()) return
    if (cause instanceof StaleSessionGenerationError) {
      replaceTransferOperation(operation.key, {
        phase: operation.operationId === null ? 'uncertain' : 'pending',
        message: 'Сеанс изменился. После входа проверь состояние операции переноса.',
      })
      return
    }
    if (mode === 'status') {
      replaceTransferOperation(operation.key, {
        phase: 'pending',
        message: `Состояние пока не получено. ${transferErrorMessage(cause)} Проверь его ещё раз.`,
      })
      emit('error', cause)
      return
    }
    const definitive = cause instanceof HeadmanJournalApiError
      && cause.response.status >= 400 && cause.response.status < 500
      && cause.response.status !== 401 && cause.response.status !== 408 && cause.response.status !== 429
    replaceTransferOperation(operation.key, {
      phase: definitive ? 'error' : 'uncertain',
      message: transferErrorMessage(cause),
    })
    emit('error', cause)
  }
}

async function applyTransferResponse(
  operation: TransferOperation,
  response: HeadmanLessonTransferResponse,
  isCurrentRun: () => boolean,
): Promise<void> {
  if (!isCurrentRun()) return
  if (response.state === 'PENDING') {
    replaceTransferOperation(operation.key, {
      operationId: response.operationId,
      targetDate: response.targetDate,
      response,
      phase: 'pending',
      message: null,
    })
    await pollTransferStatus(operation, response, isCurrentRun)
    return
  }
  if (response.state === 'ERROR') {
    replaceTransferOperation(operation.key, {
      operationId: response.operationId,
      targetDate: response.targetDate,
      response,
      phase: 'error',
      message: response.errorCode ? transferErrorCodeMessage(response.errorCode) : 'Перенос завершился ошибкой.',
    })
    return
  }
  const completedOperation = { ...operation, targetDate: response.targetDate }
  replaceTransferOperation(operation.key, {
    operationId: response.operationId,
    targetDate: response.targetDate,
    response,
    phase: 'completed',
    message: 'Перенос завершён. Журнал обновляется на новой дате.',
  })
  await reloadTransferredLesson(completedOperation, response, isCurrentRun)
}

async function pollTransferStatus(
  operation: TransferOperation,
  accepted: HeadmanLessonTransferResponse,
  isCurrentRun: () => boolean,
): Promise<void> {
  for (let attempt = 0; attempt < 8; attempt += 1) {
    await new Promise<void>((resolve) => setTimeout(resolve, 750))
    if (!isCurrentRun()) return
    try {
      const response = await operation.api.getLessonTransfer(accepted.operationId)
      if (!isCurrentRun()) return
      if (response.sourceLessonId !== String(operation.sourceLessonId)
        || response.operationId !== accepted.operationId
        || response.targetDate !== accepted.targetDate) {
        throw new Error('Сервер вернул состояние другого переноса.')
      }
      if (response.state === 'PENDING') {
        replaceTransferOperation(operation.key, {
          operationId: response.operationId,
          response,
          phase: 'pending',
          message: null,
        })
        continue
      }
      await applyTransferResponse(operation, response, isCurrentRun)
      return
    } catch (cause) {
      if (!isCurrentRun()) return
      replaceTransferOperation(operation.key, {
        operationId: accepted.operationId,
        phase: 'pending',
        message: `Не удалось проверить состояние. ${transferErrorMessage(cause)} Проверь его ещё раз.`,
      })
      emit('error', cause)
      return
    }
  }
  if (isCurrentRun()) {
    replaceTransferOperation(operation.key, {
      operationId: accepted.operationId,
      phase: 'pending',
      message: 'Перенос ещё выполняется. Проверь состояние пары через журнал.',
    })
  }
}

async function reloadTransferredLesson(
  operation: TransferOperation,
  response: HeadmanLessonTransferResponse,
  isCurrentRun: () => boolean,
): Promise<void> {
  const targetDate = response.targetDate
  if (!isCurrentRun()) return
  const targetLessonId = Number(response.targetLessonId)
  const preferredId = Number.isSafeInteger(targetLessonId) && targetLessonId > 0 ? targetLessonId : null
  transferTaskOpen.value = false
  if (selectedDate.value === targetDate) {
    activeLessonsLoad = loadLessons(preferredId)
  } else {
    preferredLessonAfterDateChange = preferredId
    selectedDate.value = targetDate
  }
  const reload = activeLessonsLoad
  await reload
  if (props.api !== operation.api || props.groupId !== operation.groupId || selectedDate.value !== targetDate) return
  const targetVisible = preferredId === null || lessons.value.some((lesson) => lesson.id === preferredId)
  notice.value = targetVisible
    ? 'Перенос завершён. Журнал обновлён на новой дате.'
    : 'Перенос завершён, но новая пара не появилась в списке. Обнови журнал ещё раз.'
}

function resumeTransferForSelectedLesson(retryUncertain: boolean): void {
  const lesson = selectedLesson.value
  const api = props.api
  const groupId = props.groupId
  if (!lesson || !api || groupId === null || !isHeadmanContext() || props.offline || props.readOnly) return
  let operation = transferOperationForLesson(lesson.id)
  if (lesson.transferOperationId) {
    if (!operation) {
      const key = `operation:${lesson.transferOperationId}`
      operation = {
        key,
        api,
        groupId,
        sourceLessonId: lesson.id,
        sourceDate: lesson.date,
        targetDate: null,
        targetLessonNumber: null,
        request: null,
        operationId: lesson.transferOperationId,
        response: null,
        phase: 'pending',
        message: null,
      }
      transferOperations.value = [...transferOperations.value, operation]
    } else if (operation.operationId !== lesson.transferOperationId) {
      transferRunRevisions.set(operation.key, (transferRunRevisions.get(operation.key) ?? 0) + 1)
      operation = {
        ...operation,
        operationId: lesson.transferOperationId,
        targetDate: null,
        targetLessonNumber: null,
        response: null,
        phase: 'pending',
        message: null,
      }
      replaceTransferOperation(operation.key, operation)
    }
  }
  if (!operation || !canRunTransferOperation(operation)) return
  if (retryUncertain && operation.operationId === null && operation.request !== null) {
    void runTransferOperation(operation, 'submit')
  } else if (operation.operationId !== null) {
    void runTransferOperation(operation, 'status')
  }
}

async function cancelSelectedLesson(api: HeadmanJournalApi, lessonId: number, reason: string): Promise<void> {
  if (lessonActionBusy.value || api !== props.api || selectedLessonId.value !== lessonId
    || !canCancelLessons() || isLessonTransferLocked(selectedLesson.value)) return
  const revision = ++lessonActionRevision
  lessonActionBusy.value = true
  error.value = null
  notice.value = null
  try {
    await api.cancelLesson(lessonId, reason)
    if (disposed || revision !== lessonActionRevision) return
    notice.value = 'Пара отменена. Список обновлён с сервера.'
    cancelReason.value = ''
    await loadLessons()
  } catch (cause) {
    if (disposed || revision !== lessonActionRevision || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось отменить пару.'
    emit('error', cause)
  } finally {
    if (!disposed && revision === lessonActionRevision) lessonActionBusy.value = false
  }
}

async function restoreSelectedLesson(api: HeadmanJournalApi, lessonId: number): Promise<void> {
  if (lessonActionBusy.value || api !== props.api || selectedLessonId.value !== lessonId
    || !canCancelLessons() || isLessonTransferLocked(selectedLesson.value)) return
  const revision = ++lessonActionRevision
  lessonActionBusy.value = true
  error.value = null
  notice.value = null
  try {
    await api.restoreLesson(lessonId)
    if (disposed || revision !== lessonActionRevision) return
    notice.value = 'Пара восстановлена. Список обновлён с сервера.'
    await loadLessons()
  } catch (cause) {
    if (disposed || revision !== lessonActionRevision || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось восстановить пару.'
    emit('error', cause)
  } finally {
    if (!disposed && revision === lessonActionRevision) lessonActionBusy.value = false
  }
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
  if (props.offline || !api || lessonId === null || !entry.attachmentId || !canManageExcuses()
    || attachmentState(entry)?.status === 'pending') return
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
  if (!canWriteExcuse(entry)) {
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
  if (pendingUserId.value !== null || excuseUserId.value !== entry.userId || !canWriteExcuse(entry)) return
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
    || !canWriteExcuse(entry)) return
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
    if (!canWriteExcuse(entry)) {
      notice.value = 'Для уважительной причины нужно право «Обрабатывать уважительные причины».'
      return
    }
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

async function loadLessons(preferredLessonId: number | null = null): Promise<void> {
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
    const wantsInitialLesson = props.initialLessonId !== null && selectedDate.value === props.initialDate
    const requestedLesson = wantsInitialLesson
      ? next.find((lesson) => lesson.id === props.initialLessonId) ?? null
      : null
    const preferredLesson = preferredLessonId === null
      ? null
      : next.find((lesson) => lesson.id === preferredLessonId) ?? null
    if (wantsInitialLesson && requestedLesson === null) {
      error.value = 'Выбранная пара недоступна на этой дате. Вернись в «Сегодня» и выбери занятие ещё раз.'
      return
    }
    if (preferredLessonId !== null && preferredLesson === null) {
      error.value = 'Перенос завершён, но новая пара не появилась в списке. Обнови журнал ещё раз.'
      return
    }
    const first = requestedLesson ?? preferredLesson ?? next[0]
    if (first && canViewReport()) {
      selectedLessonId.value = first.id
      resumeTransferStatusForSelectedLesson()
      await loadReport(first.id)
    } else if (first) {
      selectedLessonId.value = first.id
      resumeTransferStatusForSelectedLesson()
      report.value = null
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
  () => [props.api, props.groupId, props.offline, selectedDate.value, props.assistantPermissions] as const,
  () => {
    const preferredId = preferredLessonAfterDateChange
    preferredLessonAfterDateChange = null
    activeLessonsLoad = loadLessons(preferredId)
  },
  { immediate: true, flush: 'sync' },
)

watch(
  () => [
    props.api,
    props.groupId,
    props.offline,
    props.readOnly,
    assistantPermissionSnapshot()?.join('\u0000') ?? null,
    selectedDate.value,
    selectedLessonId.value,
  ] as const,
  (next, previous) => {
    transferContextGeneration += 1
    transferTargetRevision += 1
    transferTargetLoading.value = false
    const ownerChanged = next[0] !== previous[0] || next[1] !== previous[1]
    if (ownerChanged) {
      transferOperations.value = []
      transferRunRevisions.clear()
    } else {
      transferOperations.value = transferOperations.value.map((operation) => {
        if (!isTransferOperationActive(operation)) return operation
        return {
          ...operation,
          phase: operation.operationId === null ? 'uncertain' : 'pending',
          message: 'Контекст журнала изменился. Выбери исходную пару, чтобы безопасно проверить перенос.',
        }
      })
    }
    closeTransferTask()
  },
  { flush: 'sync' },
)

watch(
  () => [transferTaskOpen.value, transferTargetDate.value, selectedDate.value, selectedLessonId.value, props.api, props.groupId] as const,
  () => { void loadTransferTargetLessons() },
  { flush: 'sync' },
)

watch(
  () => [props.api, props.groupId, props.offline, props.assistantPermissions, props.reportDownload] as const,
  () => { void loadWeeklyExportOptions() },
  { immediate: true, flush: 'sync' },
)

watch(
  () => selectedDate.value,
  () => {
    invalidateWeeklyExport()
    resetWeeklySelectionForDate()
  },
  { flush: 'sync' },
)

watch(
  () => [
    props.api,
    props.groupId,
    props.offline,
    props.readOnly,
    props.assistantPermissions,
    assistantPermissionSnapshot()?.join('\u0000'),
    selectedDate.value,
    selectedLessonId.value,
    selectedLesson.value?.id,
    selectedLesson.value?.status,
    transferTargetDate.value,
    transferTargetLessonNumber.value,
    transferTargetLessonsDate.value,
    cancelReason.value.trim(),
    canCancelLessons(),
  ] as const,
  invalidateLessonActionConfirmation,
  { flush: 'sync' },
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
  disposed = true
  closeLessonActionConfirmation()
  lessonActionRevision += 1
  attachmentDisposed = true
  lessonsRevision += 1
  reportRevision += 1
  weeklyOptionsRevision += 1
  invalidateWeeklyExport()
  weeklyExportAbort?.abort()
  weeklyExportOptions.value = null
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

    <section
      v-if="canExportWeekly()"
      class="headman-journal__weekly-export"
      aria-labelledby="headman-journal-weekly-export-title"
      :aria-busy="loadingWeeklyOptions || weeklyExportBusy"
    >
      <h2 id="headman-journal-weekly-export-title">
        Скачать недельный журнал
      </h2>
      <p
        v-if="loadingWeeklyOptions"
        class="headman-journal__state"
        role="status"
      >
        Загружаем серверный список недель и форматов…
      </p>
      <p
        v-else-if="weeklyExportError && !weeklyExportOptions"
        class="headman-journal__state headman-journal__state--error"
        role="alert"
      >
        {{ weeklyExportError }}
      </p>
      <div
        v-else-if="weeklyExportOptions"
        class="headman-journal__weekly-export-controls"
      >
        <p class="headman-journal__weekly-export-context">
          {{ weeklyExportOptions.semesterName }} · {{ selectedWeeklyCount }} выбрано
        </p>
        <fieldset
          class="headman-journal__weekly-weeks"
          :disabled="weeklyExportBusy"
        >
          <legend>Недели для выгрузки</legend>
          <label
            v-for="week in weeklyExportOptions.weeks"
            :key="week.weekStart"
            class="headman-journal__weekly-week"
          >
            <input
              type="checkbox"
              :checked="selectedWeeklyStarts.includes(week.weekStart)"
              :aria-label="`${week.label}, ${formatShortDate(week.weekStart)}–${formatShortDate(week.weekEnd)}${week.current ? ', текущая неделя' : ''}`"
              @change="toggleWeeklyWeek(week.weekStart, ($event.target as HTMLInputElement).checked)"
            >
            <span>
              {{ week.label }} · {{ formatShortDate(week.weekStart) }}–{{ formatShortDate(week.weekEnd) }}<span v-if="week.current"> · текущая</span>
            </span>
          </label>
        </fieldset>
        <label class="headman-journal__weekly-format">
          <span>Формат файла</span>
          <select
            v-model="weeklyExportFormatCode"
            :disabled="weeklyExportBusy"
          >
            <option
              v-for="format in weeklyExportOptions.formats"
              :key="format.code"
              :value="format.code"
            >
              {{ format.label }}
            </option>
          </select>
        </label>
        <p class="headman-journal__weekly-export-context">
          Общий предел — 20 МиБ на файл; PDF и PNG могут иметь меньший технический предел. Большой отчёт не обрезается.
        </p>
        <button
          class="headman-journal__weekly-download"
          type="button"
          :disabled="weeklyExportBusy || selectedWeeklyCount === 0 || !selectedWeeklyFormat"
          :aria-busy="weeklyExportBusy"
          @click="downloadWeeklyExport"
        >
          {{ weeklyExportBusy ? 'Готовим файл…' : `Скачать${selectedWeeklyCount > 1 ? ` (${selectedWeeklyCount} недели)` : ''}` }}
        </button>
        <p
          v-if="weeklyExportError"
          class="headman-journal__state headman-journal__state--error"
          role="alert"
        >
          {{ weeklyExportError }}
        </p>
        <p
          v-if="weeklyExportStatus"
          class="headman-journal__state"
          role="status"
        >
          {{ weeklyExportStatus }}
        </p>
      </div>
    </section>

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
      v-else-if="groupId === null"
      class="headman-journal__state headman-journal__state--error"
      role="alert"
    >
      Не удалось определить группу старосты.
    </p>
    <p
      v-else-if="!api"
      class="headman-journal__state headman-journal__state--error"
      role="alert"
    >
      Журнал недоступен: источник данных не подключён.
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
        v-else-if="!offline && api && groupId !== null && lessons.length === 0"
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
          v-if="report?.editable && selectedLesson.status !== 'TRANSFERRED'"
          class="headman-journal__server-flag"
        >
          Изменения разрешены сервером
        </span>
      </header>

      <section
        v-if="canCancelLessons()"
        class="headman-journal__lesson-actions"
        aria-labelledby="headman-journal-lesson-actions-title"
      >
        <h3 id="headman-journal-lesson-actions-title">
          Управление парой
        </h3>
        <template v-if="selectedLesson.status === 'CANCELLED'">
          <p>Пара отменена сервером.</p>
          <button
            type="button"
            :disabled="lessonActionBusy || offline || readOnly || isLessonTransferLocked(selectedLesson)"
            @click="requestRestoreSelectedLesson"
          >
            {{ lessonActionBusy ? 'Восстанавливаем…' : 'Восстановить пару' }}
          </button>
        </template>
        <template v-else-if="selectedLesson.status === 'TRANSFERRED'">
          <p>Исходная пара перенесена. История переноса доступна ниже.</p>
        </template>
        <template v-else>
          <label>
            <span>Причина отмены</span>
            <input
              v-model="cancelReason"
              type="text"
              maxlength="512"
              :disabled="lessonActionBusy || offline || readOnly || isLessonTransferLocked(selectedLesson)"
              placeholder="Например, преподаватель болен"
            >
          </label>
          <button
            type="button"
            :disabled="lessonActionBusy || offline || readOnly || !cancelReason.trim() || isLessonTransferLocked(selectedLesson)"
            @click="requestCancelSelectedLesson"
          >
            {{ lessonActionBusy ? 'Отменяем…' : 'Отменить пару' }}
          </button>
          <button
            v-if="canStartTransfer(selectedLesson) && !transferTaskOpen"
            type="button"
            :disabled="lessonActionBusy || offline || readOnly"
            @click="openTransferTask"
          >
            Перенести пару
          </button>
        </template>

        <section
          v-if="transferTaskOpen"
          class="headman-journal__transfer-task"
          aria-labelledby="headman-journal-transfer-title"
        >
          <h4 id="headman-journal-transfer-title">
            Новая дата и номер пары
          </h4>
          <p>
            {{ formatShortDate(selectedLesson.date) }} · пара {{ selectedLesson.lessonNumber ?? '—' }}
          </p>
          <label>
            <span>Новая дата</span>
            <input
              v-model="transferTargetDate"
              type="date"
              :min="transferTargetDateMin"
              :disabled="lessonActionBusy || offline || readOnly"
            >
          </label>
          <label>
            <span>Номер пары</span>
            <select
              v-model.number="transferTargetLessonNumber"
              :disabled="lessonActionBusy || offline || readOnly || transferTargetLoading || transferTargetLessonsDate !== transferTargetDate || transferTargetError !== null"
            >
              <option :value="null" disabled>
                Выбери свободное место
              </option>
              <option
                v-for="slot in transferTargetSlots"
                :key="slot"
                :value="slot"
                :disabled="isTransferTargetSlotUnavailable(slot)"
              >
                {{ transferTargetSlotLabel(slot) }}
              </option>
            </select>
          </label>
          <p
            v-if="transferTargetLoading"
            role="status"
          >
            Проверяем расписание на выбранную дату…
          </p>
          <div
            v-else-if="transferTargetError"
            class="headman-journal__transfer-error"
            role="alert"
          >
            {{ transferTargetError }}
            <button type="button" @click="void loadTransferTargetLessons()">
              Проверить дату ещё раз
            </button>
          </div>
          <p
            v-else-if="transferTargetLessonsDate === transferTargetDate && availableTransferTargetSlots.length === 0"
            role="status"
          >
            На эту дату нет свободных мест для переноса.
          </p>
          <div class="headman-journal__transfer-task-actions">
            <button
              type="button"
              :disabled="!canSubmitTransfer"
              @click="requestTransferSelectedLesson"
            >
              Продолжить
            </button>
            <button
              type="button"
              class="headman-journal__transfer-secondary"
              @click="closeTransferTask"
            >
              Закрыть
            </button>
          </div>
        </section>

        <section
          v-if="isHeadmanContext() && (selectedTransferOperation || selectedServerTransferState !== null)"
          class="headman-journal__transfer-status"
          aria-labelledby="headman-journal-transfer-status-title"
        >
          <h4 id="headman-journal-transfer-status-title">
            Состояние переноса
          </h4>
          <p role="status">
            {{ selectedTransferOperation ? transferPhaseMessage(selectedTransferOperation) : serverTransferStateMessage(selectedServerTransferState) }}
          </p>
          <p v-if="selectedTransferOperation?.targetDate || selectedTransferOperation?.response?.targetDate">
            Новая дата: {{ formatShortDate(selectedTransferOperation?.targetDate ?? selectedTransferOperation?.response?.targetDate ?? '') }}
          </p>
          <button
            v-if="selectedTransferOperation?.operationId || selectedLesson.transferOperationId"
            type="button"
            :disabled="offline || readOnly || lessonActionBusy || selectedTransferOperation?.phase === 'submitting' || selectedTransferOperation?.phase === 'checking'"
            @click="resumeTransferForSelectedLesson(false)"
          >
            Проверить состояние
          </button>
          <button
            v-if="selectedTransferOperation?.phase === 'uncertain' && selectedTransferOperation.request"
            type="button"
            :disabled="offline || readOnly || lessonActionBusy"
            @click="resumeTransferForSelectedLesson(true)"
          >
            Повторить тот же запрос
          </button>
        </section>
      </section>

      <dialog
        ref="lessonActionConfirmationDialog"
        aria-labelledby="headman-journal-lesson-action-confirmation-title"
        aria-describedby="headman-journal-lesson-action-confirmation-detail"
        @close="pendingLessonActionConfirmation = null"
      >
        <h2 id="headman-journal-lesson-action-confirmation-title">
          {{ pendingLessonActionConfirmation?.action === 'cancel' ? 'Отменить пару?' : pendingLessonActionConfirmation?.action === 'restore' ? 'Восстановить пару?' : 'Перенести пару?' }}
        </h2>
        <p id="headman-journal-lesson-action-confirmation-detail">
          <template v-if="pendingLessonActionConfirmation?.action === 'cancel'">
            {{ formatShortDate(pendingLessonActionConfirmation.lessonDate) }} · пара {{ pendingLessonActionConfirmation.lessonNumber ?? '—' }}.
            Причина: {{ pendingLessonActionConfirmation.reason }}.
            Отмена удалит отметки посещаемости, архивирует домашние задания и пересчитает статистику.
          </template>
          <template v-else-if="pendingLessonActionConfirmation?.action === 'restore'">
            {{ formatShortDate(pendingLessonActionConfirmation.lessonDate) }} · пара {{ pendingLessonActionConfirmation.lessonNumber ?? '—' }}.
            Удалённые отметки посещаемости не вернутся.
          </template>
          <template v-else-if="pendingLessonActionConfirmation?.action === 'transfer' && pendingLessonActionConfirmation.transferRequest">
            {{ formatShortDate(pendingLessonActionConfirmation.lessonDate) }} · пара {{ pendingLessonActionConfirmation.lessonNumber ?? '—' }} →
            {{ formatShortDate(pendingLessonActionConfirmation.transferRequest.targetDate) }} · пара {{ pendingLessonActionConfirmation.transferRequest.targetLessonNumber }}.
            После подтверждения отметки, вложения и связанные домашние задания перейдут на новую дату.
          </template>
        </p>
        <button
          type="button"
          @click="closeLessonActionConfirmation"
        >
          Назад
        </button>
        <button
          type="button"
          :disabled="lessonActionBusy || !lessonActionConfirmationCurrent"
          @click="void confirmLessonAction()"
        >
          {{ pendingLessonActionConfirmation?.action === 'cancel' ? 'Отменить пару' : pendingLessonActionConfirmation?.action === 'restore' ? 'Восстановить пару' : 'Подтвердить перенос' }}
        </button>
      </dialog>

      <p
        v-if="canViewReport() && loadingReport"
        class="headman-journal__state"
        role="status"
      >
        Загружаем состав и отметки…
      </p>
      <p
        v-else-if="canViewReport() && report && report.entries.length === 0"
        class="headman-journal__empty"
      >
        Сервер вернул пустой состав группы.
      </p>
      <ol
        v-else-if="canViewReport() && report"
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
              <span v-if="entry.attachmentId && canManageExcuses()">
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
              v-if="canManageExcuses()"
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
